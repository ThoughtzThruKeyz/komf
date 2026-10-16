package snd.komf.mediaserver.metadata

import snd.komf.mediaserver.model.MediaServerBookId
import snd.komf.model.BookMetadata
import snd.komf.model.BookStoryArc
import snd.komf.model.SeriesMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataMergerTest {
    private val short = "Short-lived series starring Anya Sofía Corazón."
    private val long = "Araña. Anya Corazon. Last in a nine-hundred-year line of hunters, touched by blood and magic."
    private val book = MediaServerBookId("1")

    private fun merger(preferLongerSummary: Boolean) =
        MetadataMerger(mergeTags = false, mergeGenres = false, preferLongerSummary = preferLongerSummary)

    @Test
    fun `keeps the first provider's summary by default`() {
        val merged = merger(false).mergeSeriesMetadata(SeriesMetadata(summary = short), SeriesMetadata(summary = long))
        assertEquals(short, merged.summary)
    }

    @Test
    fun `keeps the longer series summary when asked`() {
        val merger = merger(true)
        assertEquals(long, merger.mergeSeriesMetadata(SeriesMetadata(summary = short), SeriesMetadata(summary = long)).summary)
        assertEquals(long, merger.mergeSeriesMetadata(SeriesMetadata(summary = long), SeriesMetadata(summary = short)).summary)
        assertEquals(short, merger.mergeSeriesMetadata(SeriesMetadata(summary = short), SeriesMetadata()).summary)
        assertEquals(long, merger.mergeSeriesMetadata(SeriesMetadata(summary = " "), SeriesMetadata(summary = long)).summary)
    }

    @Test
    fun `keeps the longer book summary when asked`() {
        val merged = merger(true).mergeBookMetadata(
            mapOf(book to BookMetadata(summary = short)),
            mapOf(book to BookMetadata(summary = long)),
        )
        assertEquals(long, merged[book]?.summary)
    }

    @Test
    fun `keeps story arcs when merging books`() {
        val arcs = listOf(BookStoryArc("Chapter One", 2))
        val merged = merger(false).mergeBookMetadata(
            mapOf(book to BookMetadata(title = "Issue", storyArcs = arcs)),
            mapOf(book to BookMetadata(title = "Other")),
        )
        assertEquals(arcs, merged[book]?.storyArcs)

        val fromSecond = merger(false).mergeBookMetadata(
            mapOf(book to BookMetadata(title = "Other")),
            mapOf(book to BookMetadata(title = "Issue", storyArcs = arcs)),
        )
        assertEquals(arcs, fromSecond[book]?.storyArcs)
    }
}
