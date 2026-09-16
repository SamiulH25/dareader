package dareader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dareader.ext.store.defaultHttpClient
import eu.kanade.tachiyomi.network.GET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Memory+disk cached extension-icon loader. Mirrors the [PageImages]
 * pipeline (memory LRU → disk → network, bounded concurrency); failures
 * propagate so the composable below can render a placeholder box instead.
 */
object ExtensionIcons {
    private const val MAX_MEM_BYTES = 16L * 1024 * 1024
    private const val MAX_FILES = 500
    private const val MAX_CONCURRENT_FETCHES = 4

    private data class Entry(val bitmap: ImageBitmap, val bytes: Long)

    private val lock = Any()
    private val mem = LinkedHashMap<String, Entry>(32, 0.75f, true)
    private var memBytes = 0L
    private val permits = Semaphore(MAX_CONCURRENT_FETCHES)

    val diskDir: Path = run {
        val base = System.getenv("XDG_CACHE_HOME")?.ifBlank { null }
            ?: (System.getProperty("user.home") + "/.cache")
        Path.of(base, "dareader", "icons")
    }

    private val defaultClient: OkHttpClient by lazy { defaultHttpClient() }

    suspend fun get(iconUrl: String, client: OkHttpClient = defaultClient): ImageBitmap {
        synchronized(lock) { mem[iconUrl]?.let { return it.bitmap } }
        val diskBytes = withContext(Dispatchers.IO) { diskRead(iconUrl) }
        if (diskBytes != null) {
            val bitmap = decodeOrNull(diskBytes)
            if (bitmap != null) {
                memPut(iconUrl, bitmap)
                return bitmap
            }
            withContext(Dispatchers.IO) { diskDelete(iconUrl) }
        }
        permits.acquire()
        try {
            val bytes = withContext(Dispatchers.IO) {
                client.newCall(GET(iconUrl)).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    response.body.bytes()
                }
            }
            check(bytes.isNotEmpty()) { "empty icon body" }
            withContext(Dispatchers.IO) { diskWrite(iconUrl, bytes) }
            val bitmap = decode(bytes)
            memPut(iconUrl, bitmap)
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
            val tmp = Files.createTempFile(diskDir, "icon", ".part")
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

/**
 * Extension icon with a placeholder box while loading or on failure.
 */
@Composable
fun ExtensionIcon(
    iconUrl: String?,
    modifier: Modifier = Modifier.size(48.dp),
    client: OkHttpClient? = null,
    fallbackText: String = "?",
) {
    if (iconUrl.isNullOrBlank()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(fallbackText, style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val http = remember(client) { client ?: defaultHttpClient() }
    var bitmap by remember(iconUrl) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(iconUrl) { mutableStateOf(false) }
    LaunchedEffect(iconUrl, http) {
        failed = false
        bitmap = runCatching { ExtensionIcons.get(iconUrl, http) }
            .onFailure { failed = true }
            .getOrNull()
    }
    val loaded = bitmap
    if (loaded != null && !failed) {
        Image(loaded, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(fallbackText, style = MaterialTheme.typography.titleMedium)
        }
    }
}
