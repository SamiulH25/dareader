package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The ported legacy base class must actually drive selector-based parsing. */
@Suppress("DEPRECATION")
class ParsedHttpSourceTest {

    private class TestSource : ParsedHttpSource() {
        override val name = "Parsed Fixture"
        override val lang = "en"
        override val baseUrl = "https://example.com"
        override val supportsLatest = false

        override fun popularMangaSelector() = "div.manga"

        override fun popularMangaFromElement(element: Element) =
            SManga.create().also {
                it.url = element.select("a").attr("href")
                it.title = element.select("a").text()
            }

        override fun popularMangaNextPageSelector(): String? = "a.next"

        override fun searchMangaSelector() = popularMangaSelector()

        override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

        override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

        override fun latestUpdatesSelector() = popularMangaSelector()

        override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

        override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

        override fun mangaDetailsParse(document: Document) = SManga.create()

        override fun chapterListSelector() = "li.chapter"

        override fun chapterFromElement(element: Element) =
            SChapter.create().also { it.url = element.select("a").attr("href") }

        override fun pageListParse(document: Document): List<Page> = emptyList()

        override fun imageUrlParse(document: Document) = "https://example.com/img.png"

        fun popular(response: Response) = popularMangaParse(response)

        fun imageUrl(response: Response) = imageUrlParse(response)
    }

    private fun response(html: String): Response =
        Response.Builder()
            .request(GET("https://example.com/popular"))
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(html.toResponseBody("text/html".toMediaType()))
            .build()

    @Test
    fun `popular parsing maps elements and detects the next page`() {
        val source = TestSource()
        val html =
            """
            <html><body>
              <div class="manga"><a href="/m/one">One</a></div>
              <div class="manga"><a href="/m/two">Two</a></div>
              <a class="next" href="?page=2">Next</a>
            </body></html>
            """.trimIndent()

        val page = source.popular(response(html))

        assertEquals(listOf("/m/one", "/m/two"), page.mangas.map { it.url })
        assertEquals(listOf("One", "Two"), page.mangas.map { it.title })
        assertTrue(page.hasNextPage)
    }

    @Test
    fun `missing next selector means no next page`() {
        val source = TestSource()
        val html = """<html><body><div class="manga"><a href="/m/one">One</a></div></body></html>"""

        val page = source.popular(response(html))

        assertEquals(listOf("/m/one"), page.mangas.map { it.url })
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `image url parse uses the document overload`() {
        val source = TestSource()

        assertEquals(
            "https://example.com/img.png",
            source.imageUrl(response("""<html><body><img src="/img.png"/></body></html>""")),
        )
    }
}
