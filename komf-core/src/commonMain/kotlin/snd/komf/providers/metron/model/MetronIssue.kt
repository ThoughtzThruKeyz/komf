package snd.komf.providers.metron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import snd.komf.model.ProviderBookId
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class MetronIssueId(val value: Int) {
    override fun toString() = value.toString()
}

fun ProviderBookId.toMetronIssueId() = MetronIssueId(id.toInt())

@JvmInline
@Serializable
value class MetronArcId(val value: Int) {
    override fun toString() = value.toString()
}

@Serializable
data class MetronIssueListItem(
    val id: MetronIssueId,
    val number: String? = null,
    // Metron renders this as "<series name> (<year began>) #<number>"
    val issue: String? = null,
    @SerialName("cover_date")
    val coverDate: String? = null,
    @SerialName("store_date")
    val storeDate: String? = null,
    val image: String? = null,
)

@Serializable
data class MetronIssue(
    val id: MetronIssueId,
    val publisher: MetronGenericItem? = null,
    val imprint: MetronGenericItem? = null,
    val series: MetronIssueSeries? = null,
    val number: String? = null,
    @SerialName("alt_number")
    val altNumber: String? = null,
    // collection title, set for trade paperbacks and other collected editions
    val title: String? = null,
    // story titles
    val name: List<String> = emptyList(),
    @SerialName("cover_date")
    val coverDate: String? = null,
    @SerialName("store_date")
    val storeDate: String? = null,
    val rating: MetronGenericItem? = null,
    val isbn: String? = null,
    val upc: String? = null,
    val page: Int? = null,
    val desc: String? = null,
    val image: String? = null,
    val arcs: List<MetronArcRef> = emptyList(),
    val credits: List<MetronCredit> = emptyList(),
    val characters: List<MetronGenericItem> = emptyList(),
    val teams: List<MetronGenericItem> = emptyList(),
    @SerialName("cv_id")
    val cvId: Int? = null,
    @SerialName("resource_url")
    val resourceUrl: String,
)

@Serializable
data class MetronIssueSeries(
    val id: MetronSeriesId,
    val name: String,
    val volume: Int? = null,
    @SerialName("year_began")
    val yearBegan: Int? = null,
)

@Serializable
data class MetronArcRef(
    val id: MetronArcId,
    val name: String,
)

@Serializable
data class MetronCredit(
    val id: Int,
    val creator: String,
    val role: List<MetronGenericItem> = emptyList(),
)
