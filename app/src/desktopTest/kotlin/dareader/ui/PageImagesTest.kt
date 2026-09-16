package dareader.ui

import com.sun.net.httpserver.HttpServer
import dareader.ui.PageImages.DiskEntry
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageImagesTest {

    @TempDir
    lateinit var cacheRoot: Path

    private fun pngBytes(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawColor(0xFF00FF00.toInt())
        val out = java.io.ByteArrayOutputStream()
        assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out))
        return out.toByteArray()
    }

    private class TestHttpSource(private val base: String) : HttpSource() {
        override val name = "Test"
        override val lang = "en"
        override val baseUrl = base
        override val supportsLatest = false
    }

    @Test
    fun `concurrent loads of one url fetch once`() = runBlocking {
        val previous = System.getProperty("dareader.cache.dir")
        System.setProperty("dareader.cache.dir", cacheRoot.toString())
        val hits = AtomicInteger(0)
        val png = pngBytes()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/img.png") { exchange ->
            hits.incrementAndGet()
            Thread.sleep(150)
            exchange.sendResponseHeaders(200, png.size.toLong())
            exchange.responseBody.use { it.write(png) }
        }
        server.start()

        try {
            val url = "http://127.0.0.1:${server.address.port}/img.png"
            // HttpSource.headers resolves NetworkHelper through the DI shim.
            dareader.ext.di.DareaderGraph.ensureCore()
            val source = TestHttpSource(url)
            val (first, second) =
                coroutineScope {
                    val a = async { PageImages.url(source, url) }
                    val b = async { PageImages.url(source, url) }
                    a.await() to b.await()
                }

            assertEquals(1, hits.get(), "single-flight must collapse both loads into one fetch")
            assertEquals(4, first.width)
            assertEquals(4, second.width)
        } finally {
            server.stop(0)
            if (previous == null) {
                System.clearProperty("dareader.cache.dir")
            } else {
                System.setProperty("dareader.cache.dir", previous)
            }
        }
    }

    @Test
    fun `eviction selector drops oldest entries until under the cap`() {
        val entries =
            listOf(
                DiskEntry(Path.of("/cache/a"), 40, 1L),
                DiskEntry(Path.of("/cache/b"), 30, 2L),
                DiskEntry(Path.of("/cache/c"), 50, 3L),
            )

        assertTrue(PageImages.selectForEviction(entries, 200).isEmpty())
        assertEquals(listOf(Path.of("/cache/a")), PageImages.selectForEviction(entries, 100))
        assertEquals(
            listOf(Path.of("/cache/a"), Path.of("/cache/b")),
            PageImages.selectForEviction(entries, 60),
        )
    }
}
