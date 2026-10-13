package snd.komf.providers.metron

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.util.encodeBase64
import snd.komf.ktor.ThroughputLimiter
import snd.komf.model.Image
import snd.komf.providers.metron.model.MetronArcId
import snd.komf.providers.metron.model.MetronIssue
import snd.komf.providers.metron.model.MetronIssueId
import snd.komf.providers.metron.model.MetronIssueListItem
import snd.komf.providers.metron.model.MetronPage
import snd.komf.providers.metron.model.MetronSeries
import snd.komf.providers.metron.model.MetronSeriesId
import snd.komf.providers.metron.model.MetronSeriesListItem

private const val baseUrl = "https://metron.cloud/api"

class MetronClient(
    private val ktor: HttpClient,
    apiKey: String,
    // Metron's API allows 20 requests a minute per account. Covers are served from a static host and are not counted
    private val rateLimiter: ThroughputLimiter,
) {
    // An API token from the Metron account page, or "username:password" for Basic auth, which Metron still accepts
    private val authorization = if (apiKey.contains(':')) "Basic ${apiKey.encodeBase64()}" else "Bearer $apiKey"

    suspend fun searchSeries(name: String): List<MetronSeriesListItem> {
        rateLimiter.acquire()
        return ktor.get("$baseUrl/series/") {
            header(HttpHeaders.Authorization, authorization)
            parameter("name", name)
        }.body<MetronPage<MetronSeriesListItem>>().results
    }

    suspend fun getSeries(id: MetronSeriesId): MetronSeries {
        rateLimiter.acquire()
        return ktor.get("$baseUrl/series/$id/") {
            header(HttpHeaders.Authorization, authorization)
        }.body()
    }

    suspend fun getSeriesIssues(id: MetronSeriesId, firstPageOnly: Boolean = false): List<MetronIssueListItem> {
        return getAllPages("$baseUrl/series/$id/issue_list/", firstPageOnly)
    }

    suspend fun findIssues(seriesId: MetronSeriesId, number: String): List<MetronIssueListItem> {
        rateLimiter.acquire()
        return ktor.get("$baseUrl/issue/") {
            header(HttpHeaders.Authorization, authorization)
            parameter("series_id", seriesId.value)
            parameter("number", number)
        }.body<MetronPage<MetronIssueListItem>>().results
    }

    suspend fun getIssue(id: MetronIssueId): MetronIssue {
        rateLimiter.acquire()
        return ktor.get("$baseUrl/issue/$id/") {
            header(HttpHeaders.Authorization, authorization)
        }.body()
    }

    suspend fun getArcIssues(id: MetronArcId): List<MetronIssueListItem> {
        return getAllPages("$baseUrl/arc/$id/issue_list/", false)
    }

    suspend fun getCover(url: String): Image {
        val bytes: ByteArray = ktor.get(url).body()
        return Image(bytes)
    }

    private suspend fun getAllPages(url: String, firstPageOnly: Boolean): List<MetronIssueListItem> {
        val results = mutableListOf<MetronIssueListItem>()
        var nextUrl: String? = url
        while (nextUrl != null) {
            rateLimiter.acquire()
            val page: MetronPage<MetronIssueListItem> = ktor.get(nextUrl) {
                header(HttpHeaders.Authorization, authorization)
            }.body()
            results.addAll(page.results)
            nextUrl = if (firstPageOnly) null else page.next
        }
        return results
    }
}
