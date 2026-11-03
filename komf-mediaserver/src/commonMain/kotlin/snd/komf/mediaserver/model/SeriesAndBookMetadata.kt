package snd.komf.mediaserver.model

import snd.komf.model.BookMetadata
import snd.komf.model.ProviderSeriesId
import snd.komf.model.ProviderSeriesMetadata
import snd.komf.model.SeriesMetadata
import snd.komf.providers.CoreProviders

data class SeriesAndBookMetadata(
    val seriesMetadata: SeriesMetadata,
    val bookMetadata: Map<MediaServerBook, BookMetadata?>,
    // the matched series' ids, on the provider that matched it and on any other provider it links to
    val seriesIds: Map<CoreProviders, ProviderSeriesId> = emptyMap(),
)

fun ProviderSeriesMetadata.seriesIds(provider: CoreProviders): Map<CoreProviders, ProviderSeriesId> =
    externalIds + (provider to id)
