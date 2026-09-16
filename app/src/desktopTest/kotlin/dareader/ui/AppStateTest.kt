package dareader.ui

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateTest {

    @TempDir
    lateinit var dataDir: Path

    private fun fakeSource() =
        object : Source {
            override val id = 1L
            override val name = "Fake"
            override val supportsLatest = false

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

    private fun manga() =
        SManga.create().also {
            it.url = "/m/a"
            it.title = "A"
        }

    @Test
    fun `back returns to the section the flow came from`() {
        val state = AppState(dataDir)
        val source = fakeSource()
        val browse = Screen.Browse(listOf(source))
        val detail = Screen.Detail(source, listOf(source), manga())

        state.navigateRoot(browse)
        state.navigate(detail)
        assertEquals(detail, state.screen)
        assertEquals(browse, state.rootScreen())

        state.goBack()
        assertEquals(browse, state.screen, "back must return to the browse flow's origin")

        state.goBack()
        assertEquals(browse, state.screen, "an empty stack must leave the screen put")
    }

    @Test
    fun `rail navigation clears the back stack`() {
        val state = AppState(dataDir)
        val source = fakeSource()
        state.navigateRoot(Screen.Browse(listOf(source)))
        state.navigate(Screen.Detail(source, listOf(source), manga()))

        state.navigateRoot(Screen.Extensions)

        assertEquals(Screen.Extensions, state.screen)
        assertEquals(Screen.Extensions, state.rootScreen())
        state.goBack()
        assertEquals(Screen.Extensions, state.screen, "a rail switch must not leave entries behind")
    }

    private class CountingSource : Source {
        var detailCalls = 0
        override val id = 9L
        override val name = "Counting"
        override val supportsLatest = false

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
        ): SMangaUpdate {
            detailCalls++
            return SMangaUpdate(manga, chapters)
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    @Test
    fun `detail fetches are reused within the ttl`() =
        runBlocking {
            val state = AppState(dataDir)
            val source = CountingSource()
            val manga = manga()

            state.detail(source, manga)
            state.detail(source, manga)

            assertEquals(1, source.detailCalls, "reader and detail must share one fetch")
        }

    @Test
    fun `detail refetches once the cached value is stale`() =
        runBlocking {
            val state = AppState(dataDir)
            state.detailTtlMillis = 0
            val source = CountingSource()
            val manga = manga()

            state.detail(source, manga)
            state.detail(source, manga)

            assertEquals(2, source.detailCalls)
        }

    @Test
    fun `store index fetch is one request and caches the signing key`() =
        runBlocking {
            val hits = java.util.concurrent.atomic.AtomicInteger(0)
            val body = """{"name":"Test Store","signingKey":"AB:CD","extensionList":{"extensions":[]}}"""
            val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/index.json") { exchange ->
                hits.incrementAndGet()
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            try {
                val state = AppState(dataDir)
                val url = "http://127.0.0.1:${server.address.port}/index.json"

                val index = state.fetchStoreIndex(url)

                assertEquals(1, hits.get(), "one index request per load")
                assertEquals("Test Store", index?.name)
                assertEquals("AB:CD", state.storeKey(url), "signing key cached for installs")
            } finally {
                server.stop(0)
            }
        }
}
