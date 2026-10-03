package snd.komf.mediaserver.config

import kotlinx.serialization.Serializable
import snd.komf.mediaserver.metadata.PublisherTagNameConfig
import snd.komf.model.MediaType
import snd.komf.model.MediaType.MANGA
import snd.komf.model.ReadingDirection
import snd.komf.model.UpdateMode
import snd.komf.model.UpdateMode.API

@Serializable
data class KomgaConfig(
    val baseUri: String = "http://localhost:25600",
    val komgaUser: String = "admin@example.org",
    val komgaPassword: String = "admin",
    val thumbnailSizeLimit: Long = 1048575,
    val eventListener: EventListenerConfig = EventListenerConfig(),
    val metadataUpdate: MetadataUpdateConfig = MetadataUpdateConfig(),
    val notifications: MediaServerNotificationsConfig = MediaServerNotificationsConfig(),
)

@Serializable
data class KavitaConfig(
    val baseUri: String = "http://localhost:5000",
    val apiKey: String = "",
    val eventListener: EventListenerConfig = EventListenerConfig(enabled = false),
    val metadataUpdate: MetadataUpdateConfig = MetadataUpdateConfig(),
    val notifications: MediaServerNotificationsConfig = MediaServerNotificationsConfig(),
)

/**
 * Where this server's notifications go. Each list replaces the matching global
 * one (notifications.discord.webhooks, notifications.apprise.urls) for this
 * server only: left unset, the global list is used; set to an empty list, this
 * server sends nothing there. Templates, embed color and cover settings stay
 * global; templates can tell servers apart with the mediaServer variable.
 */
@Serializable
data class MediaServerNotificationsConfig(
    val discord: MediaServerDiscordTargets = MediaServerDiscordTargets(),
    val apprise: MediaServerAppriseTargets = MediaServerAppriseTargets(),
)

@Serializable
data class MediaServerDiscordTargets(
    val webhooks: List<String>? = null,
)

@Serializable
data class MediaServerAppriseTargets(
    val urls: List<String>? = null,
)

@Serializable
data class EventListenerConfig(
    val enabled: Boolean = false,
    val metadataLibraryFilter: Collection<String> = emptyList(),
    val metadataSeriesExcludeFilter: Collection<String> = emptySet(),
    val notificationsLibraryFilter: Collection<String> = emptyList(),
)

@Serializable
data class MetadataUpdateConfig(
    val default: MetadataProcessingConfig = MetadataProcessingConfig(),
    val library: Map<String, MetadataProcessingConfig> = emptyMap(),
)

@Serializable
data class MetadataProcessingConfig(
    val libraryType: MediaType = MANGA,
    val aggregate: Boolean = false,
    val mergeTags: Boolean = false,
    val mergeGenres: Boolean = false,

    val bookCovers: Boolean = false,
    val seriesCovers: Boolean = false,
    val overrideExistingCovers: Boolean = true,
    var lockCovers: Boolean = true,
    val updateModes: List<UpdateMode> = listOf(API),
    val overrideComicInfo: Boolean = false,

    val postProcessing: MetadataPostProcessingConfig = MetadataPostProcessingConfig()
)

@Serializable
data class MetadataPostProcessingConfig(
    val seriesTitle: Boolean = false,
    val seriesTitleLanguage: String? = "en",
    val alternativeSeriesTitles: Boolean = false,
    val alternativeSeriesTitleLanguages: List<String> = listOf("en", "ja", "ja-ro"),
    val fallbackToAltTitle: Boolean = false,

    val orderBooks: Boolean = false,
    val readingDirectionValue: ReadingDirection? = null,
    val languageValue: String? = null,

    val scoreTagName: String? = null,
    val originalPublisherTagName: String? = null,
    val publisherTagNames: List<PublisherTagNameConfig> = emptyList(),
)

@Serializable
data class DatabaseConfig(
    val file: String = "./database.sqlite"
)
