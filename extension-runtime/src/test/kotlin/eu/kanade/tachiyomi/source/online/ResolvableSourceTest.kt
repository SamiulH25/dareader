package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Linkage shape check: extensions must be able to implement the ported interface. */
class ResolvableSourceTest {

    private class UriSource : ResolvableSource {
        override val id = 1L
        override val name = "Uri"
        override val supportsLatest = false

        override fun getUriType(uri: String) =
            when {
                uri.contains("/manga/") -> UriType.Manga
                uri.contains("/chapter/") -> UriType.Chapter
                else -> UriType.Unknown
            }

        override suspend fun getManga(uri: String): SManga? =
            if (getUriType(uri) == UriType.Manga) {
                SManga.create().also { it.url = uri }
            } else {
                null
            }

        override suspend fun getChapter(uri: String): SChapter? = null

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = SMangaUpdate(manga, chapters)

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    @Test
    fun `uri typing dispatches to manga and chapter`() = runBlocking {
        val source = UriSource()

        assertEquals(UriType.Manga, source.getUriType("https://site/manga/1"))
        assertEquals(UriType.Chapter, source.getUriType("https://site/chapter/2"))
        assertEquals(UriType.Unknown, source.getUriType("https://site/other"))
        assertEquals("https://site/manga/1", source.getManga("https://site/manga/1")?.url)
        assertNull(source.getManga("https://site/other"))
    }
}
