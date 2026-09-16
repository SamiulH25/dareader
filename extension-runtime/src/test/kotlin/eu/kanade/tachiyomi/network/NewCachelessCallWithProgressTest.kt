package eu.kanade.tachiyomi.network

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

class NewCachelessCallWithProgressTest {

    private class RecordingListener : ProgressListener {
        val updates = mutableListOf<Triple<Long, Long, Boolean>>()

        override fun update(
            bytesRead: Long,
            contentLength: Long,
            done: Boolean,
        ) {
            updates += Triple(bytesRead, contentLength, done)
        }
    }

    @Test
    fun `resume request sends a range header and offsets progress on 206`() = runBlocking {
        val seenRange = AtomicReference<String?>(null)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/img") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            seenRange.set(range)
            val bytes = "abcdef".toByteArray()
            val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
            val slice = bytes.copyOfRange(start, bytes.size)
            exchange.responseHeaders.add("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
            exchange.sendResponseHeaders(206, slice.size.toLong())
            exchange.responseBody.use { it.write(slice) }
        }
        server.start()

        try {
            val client = OkHttpClient()
            val listener = RecordingListener()
            val call =
                client.newCachelessCallWithProgress(
                    Request.Builder().url("http://127.0.0.1:${server.address.port}/img").build(),
                    listener,
                    existingSize = 3L,
                )

            call.execute().use { response ->
                assertEquals(206, response.code)
                response.body.source().use { it.readByteArray() }
            }

            assertEquals("bytes=3-", seenRange.get())
            val (bytesRead, contentLength, done) = listener.updates.last()
            assertEquals(6L, bytesRead) // 3 resumed + 3 received
            assertEquals(6L, contentLength) // 3 content + 3 prior bytes
            assertEquals(true, done)
        } finally {
            server.stop(0)
        }
    }
}
