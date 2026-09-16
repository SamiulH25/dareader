package dareader.ext.store

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.util.lang.Hash
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreIndexCacheTest {

    private lateinit var cacheDir: Path
    private var previous: String? = null

    @BeforeTest
    fun isolateCache() {
        cacheDir = Files.createTempDirectory("dareader-store-cache-")
        previous = System.getProperty("dareader.store.cache.dir")
        System.setProperty("dareader.store.cache.dir", cacheDir.toString())
    }

    @AfterTest
    fun restoreCache() {
        if (previous == null) {
            System.clearProperty("dareader.store.cache.dir")
        } else {
            System.setProperty("dareader.store.cache.dir", previous)
        }
    }

    @Test
    fun `revalidation reuses the cached index on 304`() {
        val etag = "\"v1\""
        val body = """{"name":"Test Store","badgeLabel":"T"}"""
        val hits = AtomicInteger(0)
        val seenIfNoneMatch = AtomicReference<String?>(null)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/index.json") { exchange ->
            hits.incrementAndGet()
            val ifNoneMatch = exchange.requestHeaders.getFirst("If-None-Match")
            seenIfNoneMatch.set(ifNoneMatch)
            if (ifNoneMatch == etag) {
                exchange.sendResponseHeaders(304, -1)
                exchange.close()
            } else {
                exchange.responseHeaders.add("ETag", etag)
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()

        try {
            val client = OkHttpClient()
            val url = "http://127.0.0.1:${server.address.port}/index.json"

            val first = fetchStore(client, url)
            assertEquals("Test Store", first.name)
            assertEquals(1, hits.get())
            assertNull(seenIfNoneMatch.get(), "first fetch must be unconditional")

            val second = fetchStore(client, url)
            assertEquals("Test Store", second.name)
            assertEquals(etag, seenIfNoneMatch.get(), "second fetch must revalidate with the stored ETag")
            assertEquals(2, hits.get())
            assertTrue(
                Files.isRegularFile(cacheDir.resolve(Hash.sha256(url))),
                "index body must be cached on disk",
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `cache serves the index when the refetch fails`() {
        val body = """{"name":"Cached Store"}"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/index.json") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val client = OkHttpClient()
        val url = "http://127.0.0.1:${server.address.port}/index.json"
        try {
            assertEquals("Cached Store", fetchStore(client, url).name)
        } finally {
            server.stop(0)
        }

        // Server is gone; the on-disk copy still decodes.
        val cached = Files.readAllBytes(cacheDir.resolve(Hash.sha256(url)))
        assertEquals("Cached Store", decodeStore(cached).name)
    }
}
