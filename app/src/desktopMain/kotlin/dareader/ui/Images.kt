package dareader.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Source-aware image loading: every fetch goes through the extension's own
 * OkHttp client (cookies, User-Agent, Referer), unlike generic Coil setups
 * that drop source headers.
 *
 * Pipeline per key (resolved image URL):
 * memory (byte-capped LRU) → disk (~/.cache/dareader/covers) → network
 * (bounded concurrency). Failures propagate so callers can show error/retry
 * UI instead of spinning forever.
 */
object PageImages {
    private const val MAX_MEM_BYTES = 96L * 1024 * 1024
    private const val MAX_FILES = 1000
    private const val MAX_CONCURRENT_FETCHES = 6

    private data class Entry(val bitmap: ImageBitmap, val bytes: Long)

    private val lock = Any()
    private val mem = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private var memBytes = 0L
    private val permits = Semaphore(MAX_CONCURRENT_FETCHES)

    val diskDir: Path = run {
        val base = System.getenv("XDG_CACHE_HOME")?.ifBlank { null }
            ?: (System.getProperty("user.home") + "/.cache")
        Path.of(base, "dareader", "covers")
    }

    /**
     * Resolves lazy image URLs (APP-01): many sources leave [Page.imageUrl]
     * null and expect the host to call [HttpSource.getImageUrl] first.
     * The resolved URL is written back onto the page, so the fetch below
     * always has a concrete URL and never hits the `!!` in `imageRequest`.
     */
    suspend fun page(source: HttpSource, page: Page): ImageBitmap {
        if (page.imageUrl == null) {
            page.imageUrl = withContext(Dispatchers.IO) { source.getImageUrl(page) }
        }
        val key = page.imageUrl ?: "${page.url}#${page.index}"
        return cached(key) {
            source.getImage(page).use { it.body.bytes() }
        }
    }

    suspend fun url(source: HttpSource, url: String): ImageBitmap =
        cached(url) {
            source.client.newCall(GET(url, source.headers)).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body.bytes()
            }
        }

    private suspend fun cached(key: String, fetch: suspend () -> ByteArray): ImageBitmap {
        synchronized(lock) { mem[key]?.let { return it.bitmap } }
        val diskBytes = withContext(Dispatchers.IO) { diskRead(key) }
        if (diskBytes != null) {
            val bitmap = decodeOrNull(diskBytes)
            if (bitmap != null) {
                memPut(key, bitmap)
                return bitmap
            }
            withContext(Dispatchers.IO) { diskDelete(key) }
        }
        permits.acquire()
        try {
            val bytes = withContext(Dispatchers.IO) { fetch() }
            check(bytes.isNotEmpty()) { "empty image body" }
            withContext(Dispatchers.IO) { diskWrite(key, bytes) }
            val bitmap = decode(bytes)
            memPut(key, bitmap)
            return bitmap
        } finally {
            permits.release()
        }
    }

    private fun memPut(key: String, bitmap: ImageBitmap) {
        val size = bitmap.width.toLong() * bitmap.height * 4
        synchronized(lock) {
            mem.remove(key)?.let { memBytes -= it.bytes }
            if (size > MAX_MEM_BYTES) return
            mem[key] = Entry(bitmap, size)
            memBytes += size
            val iter = mem.entries.iterator()
            while (memBytes > MAX_MEM_BYTES && iter.hasNext()) {
                val eldest = iter.next()
                memBytes -= eldest.value.bytes
                iter.remove()
            }
        }
    }

    private fun decode(bytes: ByteArray): ImageBitmap =
        Image.makeFromEncoded(bytes).toComposeImageBitmap()

    private fun decodeOrNull(bytes: ByteArray): ImageBitmap? =
        runCatching { decode(bytes) }.getOrNull()

    private fun diskFile(key: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = buildString(digest.size * 2) { digest.forEach { append("%02x".format(it)) } }
        return diskDir.resolve(name)
    }

    private fun diskRead(key: String): ByteArray? = runCatching {
        val file = diskFile(key)
        if (!Files.isRegularFile(file)) return null
        Files.readAllBytes(file).takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun diskDelete(key: String) {
        runCatching { Files.deleteIfExists(diskFile(key)) }
    }

    private fun diskWrite(key: String, bytes: ByteArray) {
        runCatching {
            Files.createDirectories(diskDir)
            val file = diskFile(key)
            val tmp = Files.createTempFile(diskDir, "cover", ".part")
            try {
                Files.write(tmp, bytes)
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: UnsupportedOperationException) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(tmp)
            }
            trimDisk()
        }
    }

    private fun trimDisk() {
        runCatching {
            Files.list(diskDir).use { stream ->
                val files = stream.filter { Files.isRegularFile(it) }.toList()
                if (files.size > MAX_FILES) {
                    files.sortedBy { Files.getLastModifiedTime(it).toMillis() }
                        .take(files.size - MAX_FILES)
                        .forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }
}
