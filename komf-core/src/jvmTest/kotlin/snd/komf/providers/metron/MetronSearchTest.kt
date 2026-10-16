package snd.komf.providers.metron

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import snd.komf.ktor.intervalLimiter
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.SeriesMetadataConfig
import snd.komf.util.NameSimilarityMatcher.Companion.nameSimilarityMatcher
import snd.komf.util.NameSimilarityMatcher.NameMatchingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class MetronSearchTest {
    private data class Series(val id: Int, val name: String, val year: Int)

    // Answers /series/ like Metron: every word of "name" must appear in the series name as typed,
    // optional exact year_began, sorted by name, [pageSize] a page
    private fun provider(catalog: List<Series>, pageSize: Int = 100, requests: MutableList<String>): MetronMetadataProvider {
        val engine = MockEngine { request ->
            requests.add(request.url.encodedPathAndQuery)
            val params = request.url.parameters
            val words = params["name"]!!.split(" ").filter { it.isNotBlank() }
            val year = params["year_began"]?.toInt()
            val page = params["page"]?.toInt() ?: 1
            val matches = catalog
                .filter { series -> words.all { series.name.contains(it, ignoreCase = true) } }
                .filter { year == null || it.year == year }
                .sortedBy { it.name.lowercase() }
            val pageItems = matches.drop((page - 1) * pageSize).take(pageSize)
            val next = if (page * pageSize < matches.size)
                "\"https://metron.cloud/api/series/?${request.url.encodedQuery.replace(Regex("&?page=\\d+"), "")}&page=${page + 1}\""
            else "null"
            val results = pageItems.joinToString(",") {
                """{"id":${it.id},"series":"${it.name} (${it.year})","year_began":${it.year},"publisher":{"id":1,"name":"Marvel"}}"""
            }
            respond(
                """{"count":${matches.size},"next":$next,"previous":null,"results":[$results]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = MetronClient(
            ktor = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
            apiKey = "token",
            rateLimiter = intervalLimiter(1000, 1.seconds),
        )
        return MetronMetadataProvider(
            client = client,
            mapper = MetronMetadataMapper(SeriesMetadataConfig(), BookMetadataConfig()),
            nameMatcher = nameSimilarityMatcher(NameMatchingMode.CLOSEST_MATCH),
            fetchSeriesCovers = false,
            fetchBookCovers = false,
        )
    }

    private fun search(provider: MetronMetadataProvider, name: String) =
        runBlocking { provider.searchSeries(name, 20) }.map { it.title }

    @Test
    fun `finds a series whose name has an apostrophe the search lacks`() {
        val requests = mutableListOf<String>()
        val provider = provider(
            listOf(
                Series(1, "Marvel's Voices: Spider-Verse", 2023),
                Series(2, "Spider-Verse", 2023),
                Series(3, "Edge of Spider-Verse", 2023),
            ),
            requests = requests
        )

        assertEquals(
            listOf("Marvel's Voices: Spider-Verse (2023) (Marvel)"),
            search(provider, "Marvels Voices Spider-Verse (2023)")
        )
    }

    @Test
    fun `reads further pages for the exact name and lists it first`() {
        val requests = mutableListOf<String>()
        val catalog = (1..150).map { Series(it, "Alpha Saga $it", 2000) } + Series(999, "Saga", 2012)
        val provider = provider(catalog, requests = requests)

        val results = search(provider, "Saga")

        assertEquals("Saga (2012) (Marvel)", results.first())
        assertEquals(2, requests.size)
    }

    @Test
    fun `filters by the year in the name`() {
        val requests = mutableListOf<String>()
        val provider = provider(
            listOf(Series(1, "Saga", 2012), Series(2, "Saga", 1976), Series(3, "Saga of the Swamp Thing", 1982)),
            requests = requests
        )

        assertEquals(listOf("Saga (2012) (Marvel)"), search(provider, "Saga (2012)"))
        assertEquals(listOf("/api/series/?name=Saga&year_began=2012"), requests)
    }

    @Test
    fun `finds nothing when Metron does not have the series`() {
        val requests = mutableListOf<String>()
        val provider = provider(
            listOf(Series(1, "Watchmen", 1986), Series(2, "Absolute Batman", 2024), Series(3, "Before Watchmen: Ozymandias", 2012)),
            requests = requests
        )

        assertEquals(emptyList(), search(provider, "Absolute Watchmen (2005)"))
    }
}
