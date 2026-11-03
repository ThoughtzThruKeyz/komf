package snd.komf.providers.metron

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import snd.komf.model.BookQualifier
import snd.komf.model.Image
import snd.komf.model.MatchQuery
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.providers.CoreProviders
import snd.komf.providers.CoreProviders.METRON
import snd.komf.providers.MetadataProvider
import snd.komf.providers.metron.model.MetronArcId
import snd.komf.providers.metron.model.MetronIssueId
import snd.komf.providers.metron.model.MetronSeriesId
import snd.komf.providers.metron.model.MetronSeriesListItem
import snd.komf.providers.metron.model.toMetronIssueId
import snd.komf.providers.metron.model.toMetronSeriesId
import snd.komf.util.NameSimilarityMatcher
import snd.komf.util.compareImages
import kotlin.math.floor
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

class MetronMetadataProvider(
    private val client: MetronClient,
    private val mapper: MetronMetadataMapper,
    private val nameMatcher: NameSimilarityMatcher,
    private val fetchSeriesCovers: Boolean,
    private val fetchBookCovers: Boolean,
) : MetadataProvider {
    // Issues of one series usually share arcs, so each arc's issue list is fetched once per update
    private val arcIssuesCache = Cache.Builder<MetronArcId, List<MetronIssueId>>()
        .expireAfterWrite(30.minutes)
        .build()
    // first issue's cover url per series, "" when it has none, so search results and identify don't ask Metron again
    private val coverUrlCache = Cache.Builder<MetronSeriesId, String>()
        .expireAfterWrite(30.minutes)
        .build()
    private val startYearRegex = "\\((?<startYear>\\d{4})(-\\d{4})?\\)".toRegex()
    private val displayYearRegex = "\\s\\(\\d{4}\\)$".toRegex()
    private val nonAlphanumericRegex = "[^\\p{L}\\p{N}]+".toRegex()
    private val maxSearchPages = 3
    private val maxSearchResults = 10
    private val whitespaceRegex = "\\s+".toRegex()
    private val apostrophes = setOf('\'', '\u2019')

    override fun providerName() = METRON

    override suspend fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val metronSeriesId = seriesId.toMetronSeriesId()
        val series = client.getSeries(metronSeriesId)
        val issues = client.getSeriesIssues(metronSeriesId)
        val coverUrl = issues.firstNotNullOfOrNull { it.image }
        coverUrlCache.put(metronSeriesId, coverUrl ?: "")
        val cover = if (fetchSeriesCovers) coverUrl?.let { getCover(it) } else null
        return mapper.toSeriesMetadata(series, issues, cover)
    }

    override suspend fun getSeriesCover(seriesId: ProviderSeriesId): Image? {
        // Metron has no series image, the first issue's cover stands in for it
        val metronSeriesId = seriesId.toMetronSeriesId()
        val coverUrl = coverUrlCache.get(metronSeriesId) {
            client.getSeriesIssues(metronSeriesId, firstPageOnly = true).firstNotNullOfOrNull { it.image } ?: ""
        }
        return coverUrl.ifEmpty { null }?.let { getCover(it) }
    }

    override suspend fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val issue = client.getIssue(bookId.toMetronIssueId())
        val storyArcs = issue.arcs.map { arc ->
            arc.name to arcIssuesCache.get(arc.id) { client.getArcIssues(arc.id).map { it.id } }
        }
        val cover = if (fetchBookCovers) issue.image?.let { getCover(it) } else null
        return mapper.toBookMetadata(issue, storyArcs, cover)
    }

    override suspend fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> {
        return findSeries(removeParentheses(seriesName), extractYear(seriesName))
            // each result's cover costs a Metron request when Komelia shows it
            .take(minOf(limit, maxSearchResults))
            .map { mapper.toSeriesSearchResult(it) }
    }

    override suspend fun matchSeriesMetadata(matchQuery: MatchQuery): ProviderSeriesMetadata? {
        val seriesName = removeParentheses(matchQuery.seriesName)
        val startYear = matchQuery.startYear ?: extractYear(matchQuery.seriesName)

        val results = findSeries(seriesName, startYear)
            .filter { resultMatchFilter(matchQuery, it) }

        val match = if (results.size > 1) {
            logger.info { "Multiple series matches: ${results.joinToString { "\"${it.displayName}\" id=${it.id}" }}" }
            if (matchQuery.bookQualifier == null) return null

            logger.info { "Attempting to match using cover of book number: ${matchQuery.bookQualifier.number} name: \"${matchQuery.bookQualifier.name}\"" }
            results.firstOrNull { matchesBookCover(it.id, matchQuery.bookQualifier) }
        } else results.firstOrNull()

        return match?.let { getSeriesMetadata(ProviderSeriesId(it.id.toString())) }
    }

    // Metron lists every series containing the searched words alphabetically, so the series named exactly that can be
    // several pages in for a common word. Results are ranked exact name first, then names starting with it
    private suspend fun findSeries(name: String, startYear: Int?): List<MetronSeriesListItem> {
        val query = name.take(400)
        if (query.isBlank()) return emptyList()

        val results = search(query, query, startYear)
            .ifEmpty { searchByLongestWord(query, startYear) }
        return results.sortedBy { nameRank(query, it) }
    }

    // Metron only finds a series when every searched word appears in its name as typed, so "Marvels Voices" finds
    // nothing for "Marvel's Voices". Search the longest word alone and keep the series whose name has all the words
    // once punctuation is ignored
    private suspend fun searchByLongestWord(query: String, startYear: Int?): List<MetronSeriesListItem> {
        val words = query.split(whitespaceRegex).filter { it.isNotBlank() }
        if (words.size < 2) return emptyList()
        val longestWord = words.filter { word -> word.none { it in apostrophes } }.maxByOrNull { it.length }
            ?: return emptyList()

        val queryWords = normalizeName(query).split(' ')
        return search(longestWord, query, startYear).filter { series ->
            val nameWords = normalizeName(series.displayName.replace(displayYearRegex, "")).split(' ').toSet()
            queryWords.all { it in nameWords }
        }
    }

    private suspend fun search(term: String, query: String, startYear: Int?): List<MetronSeriesListItem> {
        return searchPages(term, query, startYear)
            // the year may come from a file or folder name and be wrong
            .ifEmpty { if (startYear != null) searchPages(term, query, null) else emptyList() }
    }

    private suspend fun searchPages(term: String, query: String, startYear: Int?): List<MetronSeriesListItem> {
        val results = mutableListOf<MetronSeriesListItem>()
        var page = client.searchSeries(term, startYear)
        var pagesRead = 1
        while (true) {
            results.addAll(page.results)
            val next = page.next
            if (next == null || pagesRead == maxSearchPages || results.any { nameRank(query, it) == 0 }) break
            page = client.getSeriesPage(next)
            pagesRead++
        }
        return results
    }

    private fun nameRank(query: String, series: MetronSeriesListItem): Int {
        val name = normalizeName(series.displayName.replace(displayYearRegex, ""))
        val normalizedQuery = normalizeName(query)
        return when {
            name == normalizedQuery -> 0
            name.startsWith(normalizedQuery) -> 1
            else -> 2
        }
    }

    // "Marvel's Voices: Spider-Verse" -> "marvels voices spider verse"
    private fun normalizeName(name: String) = name.lowercase()
        .filterNot { it in apostrophes }
        .replace(nonAlphanumericRegex, " ")
        .trim()

    override suspend fun findSeriesId(provider: CoreProviders, seriesId: ProviderSeriesId): ProviderSeriesId? {
        if (provider != CoreProviders.COMIC_VINE) return null
        val comicVineId = seriesId.value.toIntOrNull() ?: return null
        return client.findSeriesByComicVineId(comicVineId).firstOrNull()?.let { ProviderSeriesId(it.id.toString()) }
    }

    private suspend fun matchesBookCover(seriesId: MetronSeriesId, qualifier: BookQualifier): Boolean {
        val qualifierImage = qualifier.cover ?: return false
        val start = qualifier.number.start
        val number = if (floor(start) == start) start.toInt().toString() else start.toString()

        val issueImage = client.findIssues(seriesId, number).firstNotNullOfOrNull { it.image } ?: return false
        logger.info { "matching cover of issue #$number of Metron series $seriesId" }
        val issueCover = getCover(issueImage) ?: return false
        return compareImages(qualifierImage.bytes, issueCover.bytes)
    }

    private fun extractYear(seriesName: String): Int? {
        return startYearRegex.find(seriesName)?.groups?.get("startYear")?.value?.toIntOrNull()
    }

    private fun removeParentheses(seriesName: String): String {
        return seriesName.replace("[(\\[{]([^)\\]}]+)[)\\]}]".toRegex(), "").trim()
    }

    private fun resultMatchFilter(matchQuery: MatchQuery, result: MetronSeriesListItem): Boolean {
        val startYear = matchQuery.startYear ?: extractYear(matchQuery.seriesName)
        val seriesName = removeParentheses(matchQuery.seriesName)
        val resultName = result.displayName.replace(displayYearRegex, "")

        if (!nameMatcher.matches(seriesName, resultName)) return false
        if (startYear == null || result.yearBegan == null) return true
        return startYear == result.yearBegan
    }

    private suspend fun getCover(url: String): Image? {
        return try {
            client.getCover(url)
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                logger.error { "Entry has cover url but there was no image. Skipping cover retrieval \"$url\"" }
                null
            } else throw e
        }
    }
}
