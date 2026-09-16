package eu.kanade.tachiyomi.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressResponseBodyTest {

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
    fun `existing size offsets both counters`() {
        val listener = RecordingListener()
        val body =
            ProgressResponseBody(
                "abcdef".toResponseBody("application/octet-stream".toMediaType()),
                listener,
                existingSize = 100,
            )

        body.source().use { it.readByteArray() }

        val (bytesRead, contentLength, done) = listener.updates.last()
        assertEquals(106L, bytesRead)
        assertEquals(106L, contentLength)
        assertTrue(done)
    }

    @Test
    fun `missing content length stays unknown`() {
        val listener = RecordingListener()
        val unknownLength =
            object : ResponseBody() {
                override fun contentType() = null

                override fun contentLength() = -1L

                override fun source() = Buffer().writeUtf8("abc")
            }
        val body = ProgressResponseBody(unknownLength, listener, existingSize = 5)

        body.source().use { it.readByteArray() }

        assertEquals(8L, listener.updates.last().first)
        assertEquals(-1L, listener.updates.last().second)
    }
}
