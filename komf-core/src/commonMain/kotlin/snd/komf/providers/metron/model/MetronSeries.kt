package snd.komf.providers.metron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import snd.komf.model.ProviderSeriesId
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class MetronSeriesId(val value: Int) {
    override fun toString() = value.toString()
}

fun ProviderSeriesId.toMetronSeriesId() = MetronSeriesId(value.toInt())

@Serializable
data class MetronPage<T>(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T>,
)

@Serializable
data class MetronGenericItem(
    val id: Int,
    val name: String,
)

@Serializable
data class MetronSeriesListItem(
    val id: MetronSeriesId,
    // Metron renders this as "<name> (<year began>)"
    @SerialName("series")
    val displayName: String,
    @SerialName("year_began")
    val yearBegan: Int? = null,
    @SerialName("year_end")
    val yearEnd: Int? = null,
    val volume: Int? = null,
    @SerialName("issue_count")
    val issueCount: Int? = null,
    val publisher: MetronGenericItem? = null,
    @SerialName("series_type")
    val seriesType: MetronGenericItem? = null,
    @SerialName("cv_id")
    val cvId: Int? = null,
)

@Serializable
data class MetronSeries(
    val id: MetronSeriesId,
    val name: String,
    @SerialName("sort_name")
    val sortName: String? = null,
    @SerialName("alt_names")
    val altNames: List<String> = emptyList(),
    val language: String? = null,
    val volume: Int? = null,
    @SerialName("series_type")
    val seriesType: MetronGenericItem? = null,
    val status: String? = null,
    val publisher: MetronGenericItem? = null,
    val imprint: MetronGenericItem? = null,
    @SerialName("year_began")
    val yearBegan: Int? = null,
    @SerialName("year_end")
    val yearEnd: Int? = null,
    val desc: String? = null,
    @SerialName("issue_count")
    val issueCount: Int? = null,
    val genres: List<MetronGenericItem> = emptyList(),
    @SerialName("cv_id")
    val cvId: Int? = null,
    @SerialName("gcd_id")
    val gcdId: Int? = null,
    @SerialName("resource_url")
    val resourceUrl: String,
)
