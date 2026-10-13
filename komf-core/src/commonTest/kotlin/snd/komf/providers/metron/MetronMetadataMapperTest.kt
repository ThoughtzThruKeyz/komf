package snd.komf.providers.metron

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import snd.komf.model.Author
import snd.komf.model.AuthorRole
import snd.komf.model.BookRange
import snd.komf.model.BookStoryArc
import snd.komf.model.SeriesStatus
import snd.komf.providers.BookMetadataConfig
import snd.komf.providers.SeriesMetadataConfig
import snd.komf.providers.metron.model.MetronIssue
import snd.komf.providers.metron.model.MetronIssueId
import snd.komf.providers.metron.model.MetronIssueListItem
import snd.komf.providers.metron.model.MetronPage
import snd.komf.providers.metron.model.MetronSeries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetronMetadataMapperTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapper = MetronMetadataMapper(SeriesMetadataConfig(), BookMetadataConfig())

    private fun series(status: String) = json.decodeFromString<MetronSeries>(
        """
        {
          "id": 2311,
          "name": "Saga",
          "sort_name": "Saga",
          "alt_names": [""],
          "language": "en",
          "volume": 1,
          "series_type": {"id": 2, "name": "Ongoing Series"},
          "status": "$status",
          "publisher": {"id": 4, "name": "Image"},
          "imprint": null,
          "year_began": 2012,
          "year_end": null,
          "desc": "An epic space opera. ",
          "issue_count": 72,
          "genres": [{"id": 9, "name": "Science Fiction"}],
          "associated": [],
          "cv_id": 48466,
          "gcd_id": null,
          "resource_url": "https://metron.cloud/series/saga-2012/",
          "modified": "2025-01-02T10:00:00.000000-05:00"
        }
        """
    )

    private val issues = json.decodeFromString<MetronPage<MetronIssueListItem>>(
        """
        {
          "count": 2,
          "next": null,
          "previous": null,
          "results": [
            {
              "id": 36860,
              "series": {"id": 2311, "name": "Saga", "volume": 1, "year_began": 2012},
              "number": "1",
              "issue": "Saga (2012) #1",
              "cover_date": "2012-03-01",
              "store_date": "2012-03-14",
              "image": "https://static.metron.cloud/media/issue/2019/01/21/saga-1.jpg",
              "cover_hash": "c4b0b0e0",
              "modified": "2024-05-01T10:00:00.000000-04:00"
            },
            {
              "id": 36861,
              "series": {"id": 2311, "name": "Saga", "volume": 1, "year_began": 2012},
              "number": "1.MU",
              "issue": "Saga (2012) #1.MU",
              "cover_date": "2012-04-01",
              "store_date": null,
              "image": null,
              "cover_hash": "",
              "modified": "2024-05-01T10:00:00.000000-04:00"
            }
          ]
        }
        """
    ).results

    @Test
    fun `maps every Metron series status`() {
        assertEquals(SeriesStatus.ONGOING, mapper.toSeriesMetadata(series("Ongoing"), issues, null).metadata.status)
        assertEquals(SeriesStatus.COMPLETED, mapper.toSeriesMetadata(series("Completed"), issues, null).metadata.status)
        assertEquals(SeriesStatus.ABANDONED, mapper.toSeriesMetadata(series("Cancelled"), issues, null).metadata.status)
        assertEquals(SeriesStatus.HIATUS, mapper.toSeriesMetadata(series("Hiatus"), issues, null).metadata.status)
    }

    @Test
    fun `maps series fields and issue list`() {
        val result = mapper.toSeriesMetadata(series("Completed"), issues, null)
        val metadata = result.metadata

        assertEquals("2311", result.id.value)
        assertEquals("Saga", metadata.title?.name)
        assertEquals(listOf("Saga"), metadata.titles.map { it.name })
        assertEquals("An epic space opera.", metadata.summary)
        assertEquals("Image", metadata.publisher?.name)
        assertEquals("en", metadata.language)
        assertEquals(listOf("Science Fiction"), metadata.genres.toList())
        assertEquals(72, metadata.totalBookCount)
        assertEquals(2012, metadata.releaseDate?.year)
        assertEquals("https://metron.cloud/series/saga-2012/", metadata.links.single().url)

        assertEquals(listOf("36860", "36861"), result.books.map { it.id.id })
        assertEquals(BookRange(1.0, 1.0), result.books[0].number)
        assertNull(result.books[1].number)
    }

    @Test
    fun `leaves total book count unset while a series is ongoing`() {
        assertNull(mapper.toSeriesMetadata(series("Ongoing"), issues, null).metadata.totalBookCount)
    }

    @Test
    fun `maps issue fields, credits and story arcs`() {
        val issue = json.decodeFromString<MetronIssue>(
            """
            {
              "id": 36860,
              "publisher": {"id": 4, "name": "Image"},
              "imprint": null,
              "series": {"id": 2311, "name": "Saga", "sort_name": "Saga", "volume": 1, "year_began": 2012,
                         "series_type": {"id": 2, "name": "Ongoing Series"}, "genres": [], "language": "en"},
              "number": "1",
              "alt_number": "",
              "title": "",
              "name": ["Chapter One"],
              "cover_date": "2012-03-01",
              "store_date": "2012-03-14",
              "foc_date": null,
              "price": "2.99",
              "price_currency": "USD",
              "rating": {"id": 5, "name": "Mature"},
              "sku": "",
              "isbn": "",
              "upc": "70985301725000111",
              "page": 44,
              "desc": "Hazel is born.",
              "image": "https://static.metron.cloud/media/issue/2019/01/21/saga-1.jpg",
              "cover_hash": "c4b0b0e0",
              "average_rating": null,
              "rating_count": 0,
              "arcs": [{"id": 77, "name": "Chapter One", "modified": "2024-05-01T10:00:00.000000-04:00"}],
              "credits": [
                {"id": 1, "creator": "Brian K. Vaughan", "role": [{"id": 1, "name": "Writer"}]},
                {"id": 2, "creator": "Fiona Staples", "role": [{"id": 3, "name": "Artist"}, {"id": 6, "name": "Cover"}]},
                {"id": 3, "creator": "Fonografiks", "role": [{"id": 7, "name": "Letterer"}]},
                {"id": 4, "creator": "Someone", "role": [{"id": 20, "name": "Production"}]}
              ],
              "characters": [],
              "teams": [],
              "universes": [],
              "reprints": [],
              "variants": [],
              "cv_id": 316425,
              "gcd_id": null,
              "resource_url": "https://metron.cloud/issue/saga-2012-1/",
              "modified": "2024-05-01T10:00:00.000000-04:00"
            }
            """
        )
        val arcs = listOf(
            "Chapter One" to listOf(MetronIssueId(36859), MetronIssueId(36860)),
            "Arc without this issue" to listOf(MetronIssueId(1)),
        )

        val metadata = mapper.toBookMetadata(issue, arcs, null).metadata

        assertEquals("Chapter One", metadata.title)
        assertEquals("Hazel is born.", metadata.summary)
        assertEquals(BookRange(1.0, 1.0), metadata.number)
        assertEquals(1.0, metadata.numberSort)
        assertEquals(LocalDate(2012, 3, 14), metadata.releaseDate)
        assertNull(metadata.isbn)
        assertEquals(
            listOf(
                Author("Brian K. Vaughan", AuthorRole.WRITER),
                Author("Fiona Staples", AuthorRole.PENCILLER),
                Author("Fiona Staples", AuthorRole.INKER),
                Author("Fiona Staples", AuthorRole.COVER),
                Author("Fonografiks", AuthorRole.LETTERER),
            ),
            metadata.authors
        )
        assertEquals(listOf(BookStoryArc("Chapter One", 2)), metadata.storyArcs)
        assertEquals("https://metron.cloud/issue/saga-2012-1/", metadata.links.single().url)
    }
}
