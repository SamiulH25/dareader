package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpSourceUrlTest {

    private val source = object : HttpSource() {
        override val baseUrl = "https://example.com"
        override val lang = "en"
        override val name = "Example"
        override val supportsLatest = false
    }

    private fun mangaUrl(full: String): String {
        val manga = SManga.create()
        manga.url = "unused"
        with(source) { manga.setUrlWithoutDomain(full) }
        return manga.url
    }

    private fun chapterUrl(full: String): String {
        val chapter = SChapter.create()
        chapter.url = "unused"
        with(source) { chapter.setUrlWithoutDomain(full) }
        return chapter.url
    }

    @Test
    fun stripsSchemeAndDomainKeepingPathQueryAndFragment() {
        assertEquals(
            "/manga/one?x=1#top",
            mangaUrl("https://example.com/manga/one?x=1#top"),
        )
    }

    @Test
    fun stripsDomainForChapterUrls() {
        assertEquals("/read/5", chapterUrl("https://example.com/read/5"))
    }

    @Test
    fun keepsBarePathUnchanged() {
        assertEquals("/already/bare", mangaUrl("/already/bare"))
    }

    @Test
    fun returnsOriginalWhenUnparseable() {
        // '{' is illegal in a URI path, so parsing fails and the input is kept.
        val orig = "https://example.com/{bad}"
        assertEquals(orig, mangaUrl(orig))
    }
}
