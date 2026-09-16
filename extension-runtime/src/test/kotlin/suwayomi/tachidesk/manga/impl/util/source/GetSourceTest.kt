package suwayomi.tachidesk.manga.impl.util.source

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetSourceTest {

    private fun fakeSource(id: Long, name: String) = object : Source {
        override val id = id
        override val name = name
        override val supportsLatest = false
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            MangasPage(emptyList(), false)
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = SMangaUpdate(manga, chapters)
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    @AfterTest
    fun clearRegistry() {
        GetSource.unregisterAllSources()
    }

    @Test
    fun registerFindAndUnregisterRoundTrip() {
        val a = fakeSource(1L, "A")
        val b = fakeSource(2L, "B")
        GetSource.register("com.example.a", listOf(a))
        GetSource.register("com.example.b", listOf(b))

        assertEquals(a, GetSource.findById(1L))
        assertEquals(b, GetSource.findById(2L))
        assertNull(GetSource.findById(99L))
        assertEquals(setOf(a, b), GetSource.allSources().toSet())

        GetSource.unregister("com.example.a")
        assertNull(GetSource.findById(1L))
        assertEquals(listOf(b), GetSource.allSources())
    }

    @Test
    fun unregisterAllClearsAndNotifies() {
        var notified = 0
        GetSource.onUnregisterAll { notified++ }
        GetSource.register("com.example.a", listOf(fakeSource(1L, "A")))
        GetSource.unregisterAllSources()
        assertTrue(GetSource.allSources().isEmpty())
        assertTrue(notified >= 1)
        // Listener leaks across tests would double-fire; scope is acceptable here
        // since notify-on-clear is the asserted contract.
    }
}
