package snd.komf.mediaserver.kavita.model.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import snd.komf.mediaserver.kavita.model.KavitaSeries
import snd.komf.mediaserver.kavita.model.KavitaSeriesId

@Serializable
data class KavitaSeriesUpdateRequest(
    val id: KavitaSeriesId,
    // Null leaves the name unchanged; Kavita only renames on a non-empty value.
    val name: String? = null,
    val localizedName: String? = null,
    val sortName: String,
    val coverImageLocked: Boolean,
    val nameLocked: Boolean,
    val sortNameLocked: Boolean,
    val localizedNameLocked: Boolean,

    // Kavita's UpdateSeries overwrites all of these from every request: a
    // missing id becomes 0 and a missing override is cleared. They are copied
    // from the current series (see withCurrentExternalIds) so updates keep them.
    val aniListId: Long? = null,
    val malId: Long? = null,
    val hardcoverId: Long? = null,
    val metronId: Long? = null,
    val comicVineId: String? = null,
    val mangaBakaId: Long? = null,
    val cbrId: Long? = null,
    val metadataProviderOverride: JsonElement? = null,
) {
    fun withCurrentExternalIds(series: KavitaSeries) = copy(
        aniListId = series.aniListId,
        malId = series.malId,
        hardcoverId = series.hardcoverId,
        metronId = series.metronId,
        comicVineId = series.comicVineId,
        mangaBakaId = series.mangaBakaId,
        cbrId = series.cbrId,
        metadataProviderOverride = series.metadataProviderOverride,
    )
}
