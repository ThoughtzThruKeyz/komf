package snd.komf.providers.metron

import kotlinx.datetime.LocalDate
import snd.komf.model.Author
import snd.komf.model.AuthorRole.COLORIST
import snd.komf.model.AuthorRole.COVER
import snd.komf.model.AuthorRole.EDITOR
import snd.komf.model.AuthorRole.INKER
import snd.komf.model.AuthorRole.LETTERER
import snd.komf.model.AuthorRole.PENCILLER
import snd.komf.model.AuthorRole.TRANSLATOR
import snd.komf.model.AuthorRole.WRITER
import snd.komf.model.BookMetadata
import snd.komf.model.BookRange
import snd.komf.model.BookStoryArc
import snd.komf.model.Image
import snd.komf.model.ProviderBookId
import snd.komf.model.ProviderBookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.Publisher
import snd.komf.model.ReleaseDate
import snd.komf.model.SeriesBook
import snd.komf.model.SeriesMetadata
import snd.komf.model.SeriesSearchResult
import snd.komf.model.SeriesStatus
import snd.komf.model.SeriesTitle
import snd.komf.model.WebLink
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.CoreProviders
import snd.komf.providers.MetadataConfigApplier
import snd.komf.providers.SeriesMetadataConfig
import snd.komf.providers.metron.model.MetronIssue
import snd.komf.providers.metron.model.MetronIssueId
import snd.komf.providers.metron.model.MetronIssueListItem
import snd.komf.providers.metron.model.MetronSeries
import snd.komf.providers.metron.model.MetronSeriesId
import snd.komf.providers.metron.model.MetronSeriesListItem

class MetronMetadataMapper(
    private val seriesMetadataConfig: SeriesMetadataConfig,
    private val bookMetadataConfig: BookMetadataConfig,
) {

    fun toSeriesSearchResult(series: MetronSeriesListItem): SeriesSearchResult {
        return SeriesSearchResult(
            // the list endpoint has no page link, and Metron's pages are addressed by slug rather than id
            url = null,
            imageUrl = coverKey(series.id),
            title = seriesTitle(series.displayName, series.publisher?.name),
            provider = CoreProviders.METRON,
            resultId = series.id.toString()
        )
    }

    fun toSeriesSearchResult(series: MetronSeries): SeriesSearchResult {
        val yearBegan = series.yearBegan?.let { " ($it)" } ?: ""
        return SeriesSearchResult(
            url = series.resourceUrl,
            imageUrl = coverKey(series.id),
            title = seriesTitle("${series.name}$yearBegan", series.publisher?.name),
            provider = CoreProviders.METRON,
            resultId = series.id.toString()
        )
    }

    fun toSeriesMetadata(
        series: MetronSeries,
        issues: List<MetronIssueListItem>,
        cover: Image?
    ): ProviderSeriesMetadata {
        val status = series.status?.let { toSeriesStatus(it) }
        val metadata = SeriesMetadata(
            status = status,
            title = SeriesTitle(series.name, null, null),
            titles = listOf(SeriesTitle(series.name, null, null)) +
                    series.altNames.filter { it.isNotBlank() }.map { SeriesTitle(it, null, null) },
            summary = series.desc?.ifBlank { null }?.trim(),
            publisher = series.publisher?.name?.let { Publisher(it) },
            language = series.language?.ifBlank { null },
            genres = series.genres.map { it.name },
            // An ongoing series' issue count only says how many issues are out so far
            totalBookCount = if (status != null && status != SeriesStatus.ONGOING) series.issueCount else null,
            releaseDate = ReleaseDate(series.yearBegan, null, null),
            links = listOf(WebLink("Metron", series.resourceUrl)),
            thumbnail = cover
        )
        val providerMetadata = ProviderSeriesMetadata(
            id = ProviderSeriesId(series.id.toString()),
            metadata = metadata,
            externalIds = series.cvId?.let { mapOf(CoreProviders.COMIC_VINE to ProviderSeriesId(it.toString())) }
                ?: emptyMap(),
            books = issues.map {
                val number = it.number?.toDoubleOrNull()
                SeriesBook(
                    id = ProviderBookId(it.id.toString()),
                    number = number?.let { BookRange(number, number) },
                    name = it.issue,
                    type = null,
                    edition = null
                )
            }
        )
        return MetadataConfigApplier.apply(providerMetadata, seriesMetadataConfig)
    }

    fun toBookMetadata(
        issue: MetronIssue,
        storyArcs: List<Pair<String, List<MetronIssueId>>>,
        cover: Image?
    ): ProviderBookMetadata {
        val number = issue.number?.toDoubleOrNull()

        val metadata = BookMetadata(
            title = issue.title?.ifBlank { null }
                ?: issue.name.filter { it.isNotBlank() }.ifEmpty { null }?.joinToString("; "),
            summary = issue.desc?.ifBlank { null }?.trim(),
            number = number?.let { BookRange(it, it) },
            numberSort = number,
            releaseDate = (issue.storeDate ?: issue.coverDate)?.let { LocalDate.parse(it) },
            authors = getAuthors(issue),
            isbn = issue.isbn?.ifBlank { null },
            links = listOf(WebLink("Metron", issue.resourceUrl)),
            storyArcs = storyArcs.mapNotNull { (name, arcIssues) ->
                val index = arcIssues.indexOf(issue.id)
                if (index == -1) null
                else BookStoryArc(name = name, number = index + 1)
            },
            thumbnail = cover,
        )

        val providerMetadata = ProviderBookMetadata(
            id = ProviderBookId(issue.id.toString()),
            metadata = metadata
        )
        return MetadataConfigApplier.apply(providerMetadata, bookMetadataConfig)
    }

    private fun toSeriesStatus(status: String): SeriesStatus? {
        return when (status.lowercase()) {
            "ongoing" -> SeriesStatus.ONGOING
            "completed" -> SeriesStatus.COMPLETED
            "cancelled", "canceled" -> SeriesStatus.ABANDONED
            "hiatus" -> SeriesStatus.HIATUS
            else -> null
        }
    }

    private fun getAuthors(issue: MetronIssue): List<Author> {
        return issue.credits
            .flatMap { credit -> credit.role.map { credit.creator to it.name.lowercase() } }
            .flatMap { (name, role) ->
                when (role) {
                    "writer", "script", "story", "plot", "plotter", "scripter" -> listOf(Author(name, WRITER))
                    "penciller", "penciler", "breakdowns", "layouts" -> listOf(Author(name, PENCILLER))
                    "inker", "finishes", "embellisher", "ink assists" -> listOf(Author(name, INKER))
                    "colorist", "colourist", "color separations", "color assists", "color flats" ->
                        listOf(Author(name, COLORIST))

                    "letterer" -> listOf(Author(name, LETTERER))
                    "cover" -> listOf(Author(name, COVER))
                    "editor" -> listOf(Author(name, EDITOR))
                    "translator" -> listOf(Author(name, TRANSLATOR))
                    "artist", "illustrator", "painter" -> listOf(Author(name, PENCILLER), Author(name, INKER))
                    else -> emptyList()
                }
            }
            .distinct()
    }

    // Metron's search has no images. Komelia fetches every result's cover through getSeriesCover but only shows it
    // when the result has an imageUrl, which it uses as the cache key, so give each result a stable one
    private fun coverKey(id: MetronSeriesId) = "https://metron.cloud/api/series/$id/"

    private fun seriesTitle(displayName: String, publisher: String?): String {
        return publisher?.let { "$displayName ($it)" } ?: displayName
    }
}
