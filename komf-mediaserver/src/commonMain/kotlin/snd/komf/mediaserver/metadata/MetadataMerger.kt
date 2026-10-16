package snd.komf.mediaserver.metadata

import snd.komf.mediaserver.model.MediaServerBookId
import snd.komf.model.BookMetadata
import snd.komf.model.SeriesMetadata

private val whitespaceRegex = "\\s+".toRegex()

class MetadataMerger(
    private val mergeTags: Boolean,
    private val mergeGenres: Boolean,
    private val preferLongerSummary: Boolean = false,
) {
    fun mergeSeriesMetadata(
        originalSeriesMetadata: SeriesMetadata,
        newSeriesMetadata: SeriesMetadata,
    ): SeriesMetadata {
        return SeriesMetadata(
            status = originalSeriesMetadata.status ?: newSeriesMetadata.status,
            titles = originalSeriesMetadata.titles + newSeriesMetadata.titles,
            summary = mergeSummary(originalSeriesMetadata.summary, newSeriesMetadata.summary),
            publisher = originalSeriesMetadata.publisher ?: newSeriesMetadata.publisher,
            alternativePublishers = originalSeriesMetadata.alternativePublishers.ifEmpty { newSeriesMetadata.alternativePublishers },
            readingDirection = originalSeriesMetadata.readingDirection ?: newSeriesMetadata.readingDirection,
            ageRating = originalSeriesMetadata.ageRating ?: newSeriesMetadata.ageRating,
            language = originalSeriesMetadata.language ?: newSeriesMetadata.language,
            genres = mergeGenres(originalSeriesMetadata.genres, newSeriesMetadata.genres),
            tags = mergeTags(originalSeriesMetadata.tags, newSeriesMetadata.tags),
            totalBookCount = originalSeriesMetadata.totalBookCount ?: newSeriesMetadata.totalBookCount,
            authors = originalSeriesMetadata.authors.ifEmpty { newSeriesMetadata.authors },
            characters = originalSeriesMetadata.characters.ifEmpty { newSeriesMetadata.characters },
            teams = originalSeriesMetadata.teams.ifEmpty { newSeriesMetadata.teams },
            releaseDate = originalSeriesMetadata.releaseDate ?: newSeriesMetadata.releaseDate,
            links = (originalSeriesMetadata.links + newSeriesMetadata.links).distinctBy { it.label },
            score = originalSeriesMetadata.score ?: newSeriesMetadata.score,
            thumbnail = originalSeriesMetadata.thumbnail ?: newSeriesMetadata.thumbnail,
        )
    }

    fun mergeBookMetadata(
        originalBookMetadata: Map<MediaServerBookId, BookMetadata?>,
        newBookMetadata: Map<MediaServerBookId, BookMetadata?>,
    ): Map<MediaServerBookId, BookMetadata?> = (originalBookMetadata.asSequence() + newBookMetadata.asSequence()).distinct()
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, values) -> mergeBookMetadata(values) }

    private fun mergeBookMetadata(metadata: Collection<BookMetadata?>): BookMetadata? {
        return metadata.filterNotNull().reduceOrNull { old, new ->
            BookMetadata(
                title = old.title ?: new.title,
                summary = mergeSummary(old.summary, new.summary),
                number = old.number ?: new.number,
                numberSort = old.numberSort ?: new.numberSort,
                releaseDate = old.releaseDate ?: new.releaseDate,
                authors = old.authors.ifEmpty { new.authors },
                tags = old.tags.ifEmpty { new.tags },
                characters = old.characters.ifEmpty { new.characters },
                teams = old.teams.ifEmpty { new.teams },
                isbn = old.isbn ?: new.isbn,
                links = old.links + new.links,
                chapters = old.chapters.ifEmpty { new.chapters },
                storyArcs = old.storyArcs?.ifEmpty { null } ?: new.storyArcs,
                startChapter = old.startChapter ?: new.startChapter,
                endChapter = old.endChapter ?: new.endChapter,
                thumbnail = old.thumbnail ?: new.thumbnail,
            )
        }
    }

    private fun mergeSummary(old: String?, new: String?): String? {
        if (old.isNullOrBlank()) return new
        if (new.isNullOrBlank() || !preferLongerSummary) return old
        return if (wordCount(new) > wordCount(old)) new else old
    }

    private fun wordCount(text: String) = text.split(whitespaceRegex).count { it.isNotBlank() }

    private fun mergeTags(old: Collection<String>, new: Collection<String>): Collection<String> {
        return if (mergeTags) (old + new).toSet().sorted()
        else old.ifEmpty { new }
    }

    private fun mergeGenres(old: Collection<String>, new: Collection<String>): Collection<String> {
        return if (mergeGenres) (old + new).toSet().sorted()
        else old.ifEmpty { new }
    }
}
