package dareader.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    private const val MAX_DISK_BYTES = 256L * 1024 * 1024
    private const val MAX_CONCURRENT_FETCHES = 6

    private data class Entry(val bitmap: ImageBitmap, val bytes: Long)

    /** Disk-cache entry for eviction; pure data so the selector stays testable. */
    internal data class DiskEntry(val path: Path, val bytes: Long, val lastModified: Long)

    private val lock = Any()
    private val mem = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private var memBytes = 0L
    private val permits = Semaphore(MAX_CONCURRENT_FETCHES)

    /** Owns in-flight fetches so a cancelled waiter cannot cancel shared work. */
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap>>()
    private val diskBytes = AtomicLong(-1L)

    val diskDir: Path = run {
        System.getProperty("dareader.cache.dir")?.takeIf { it.isNotBlank() }?.let {
            return@run Path.of(it).resolve("covers")
        }
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
        val diskCached = withContext(Dispatchers.IO) { diskRead(key) }
        if (diskCached != null) {
            val bitmap = decodeOrNull(diskCached)
            if (bitmap != null) {
                memPut(key, bitmap)
                return bitmap
            }
            withContext(Dispatchers.IO) { diskDelete(key) }
        }
        // Single-flight: concurrent loads of one key share a single fetch.
        val deferred =
            inFlight.computeIfAbsent(key) {
                fetchScope.async { fetchDecodeAndCache(key, fetch) }
            }
        try {
            return deferred.await()
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private suspend fun fetchDecodeAndCache(key: String, fetch: suspend () -> ByteArray): ImageBitmap {
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
            val previous = runCatching { Files.size(file) }.getOrDefault(0L)
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
            if (diskBytes.get() < 0) diskBytes.set(scanDiskBytes())
            val total = diskBytes.get() + bytes.size - previous
            diskBytes.set(total)
            if (total > MAX_DISK_BYTES) trimDisk()
        }
    }

    /** Oldest-first eviction until [entries] fit under [maxBytes]; pure for tests. */
    internal fun selectForEviction(entries: List<DiskEntry>, maxBytes: Long): List<Path> {
        var total = entries.sumOf { it.bytes }
        if (total <= maxBytes) return emptyList()
        val evicted = mutableListOf<Path>()
        for (entry in entries.sortedBy { it.lastModified }) {
            evicted.add(entry.path)
            total -= entry.bytes
            if (total <= maxBytes) break
        }
        return evicted
    }

    private fun scanDiskBytes(): Long =
        runCatching {
            Files.list(diskDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum()
            }
        }.getOrDefault(0L)

    private fun trimDisk() {
        runCatching {
            Files.createDirectories(diskDir)
            val entries =
                Files.list(diskDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .map { DiskEntry(it, Files.size(it), Files.getLastModifiedTime(it).toMillis()) }
                        .toList()
                }
            selectForEviction(entries, MAX_DISK_BYTES).forEach { Files.deleteIfExists(it) }
            diskBytes.set(scanDiskBytes())
        }
    }
}
