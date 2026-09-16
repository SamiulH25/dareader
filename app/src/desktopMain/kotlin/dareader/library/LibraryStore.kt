package dareader.library

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.displayTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Plain, lateinit-free copy of the [SManga] fields the UI persists.
 * Captured at call time via safe accessors so a half-initialized
 * extension model can never leak into storage.
 */
data class MangaRef(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val author: String? = null,
)

data class LibraryEntry(
    val sourceId: Long,
    val manga: MangaRef,
    val addedAt: Long,
)

data class HistoryEntry(
    val sourceId: Long,
    val manga: MangaRef,
    val at: Long,
)

private data class ChapterState(val read: Boolean = false, val page: Int = 0)

/**
 * File-backed library / history / reading-progress store.
 *
 * Persists `library.json`, `history.json` and `progress.json` under [dataDir]
 * with atomic tmp+move writes. Missing or corrupt files hydrate to empty
 * state and never throw. All state is guarded by a single lock; synchronous
 * getters serve the in-memory maps hydrated at init.
 *
 * Pure Kotlin + coroutines + kotlinx.serialization-json DOM (no generated
 * serializers, no Compose, no dependency on UI or the extension loader).
 */
class LibraryStore(dataDir: Path) {
    private val lock = ReentrantLock()
    private val dir: Path = dataDir
    private val libraryFile: Path = dir.resolve("library.json")
    private val historyFile: Path = dir.resolve("history.json")
    private val progressFile: Path = dir.resolve("progress.json")

    private val libraryByUrl = LinkedHashMap<String, LibraryEntry>()
    private val historyList = ArrayList<HistoryEntry>()
    private val chapters = HashMap<Pair<String, String>, ChapterState>()

    private val _entries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val entries: StateFlow<List<LibraryEntry>> = _entries.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    init {
        lock.withLock {
            runCatching { Files.createDirectories(dir) }
            hydrate()
        }
    }

    fun toggleInLibrary(sourceId: Long, manga: SManga) {
        lock.withLock {
            val snapshot = snapshotOf(manga)
            if (libraryByUrl.remove(snapshot.url) == null) {
                libraryByUrl[snapshot.url] = LibraryEntry(
                    sourceId = sourceId,
                    manga = snapshot,
                    addedAt = System.currentTimeMillis(),
                )
            }
            persistLibrary()
            _entries.value = libraryByUrl.values.toList()
        }
    }

    fun isInLibrary(mangaUrl: String): Boolean =
        lock.withLock { libraryByUrl.containsKey(mangaUrl) }

    fun markChapterRead(mangaUrl: String, chapterUrl: String, read: Boolean) {
        lock.withLock {
            val key = mangaUrl to chapterUrl
            chapters[key] = (chapters[key] ?: ChapterState()).copy(read = read)
            persistProgress()
        }
    }

    fun isChapterRead(mangaUrl: String, chapterUrl: String): Boolean =
        lock.withLock { chapters[mangaUrl to chapterUrl]?.read ?: false }

    fun saveProgress(mangaUrl: String, chapterUrl: String, pageIndex: Int) {
        lock.withLock {
            val key = mangaUrl to chapterUrl
            chapters[key] = (chapters[key] ?: ChapterState()).copy(page = pageIndex.coerceAtLeast(0))
            persistProgress()
        }
    }

    fun getProgress(mangaUrl: String, chapterUrl: String): Int =
        lock.withLock { chapters[mangaUrl to chapterUrl]?.page ?: 0 }

    fun recordHistory(sourceId: Long, manga: SManga) {
        lock.withLock {
            val snapshot = snapshotOf(manga)
            historyList.removeAll { it.manga.url == snapshot.url }
            historyList.add(
                0,
                HistoryEntry(
                    sourceId = sourceId,
                    manga = snapshot,
                    at = System.currentTimeMillis(),
                ),
            )
            persistHistory()
            _history.value = historyList.toList()
        }
    }

    fun clearHistory() {
        lock.withLock {
            historyList.clear()
            persistHistory()
            _history.value = emptyList()
        }
    }

    // -- hydration (corrupt/missing -> empty, never throws) --

    private fun hydrate() {
        libraryByUrl.clear()
        readArray(libraryFile).forEach { element ->
            val entry = runCatching {
                val obj = element.jsonObject
                val manga = obj.get("manga")?.jsonObject?.toSnapshot() ?: return@runCatching null
                LibraryEntry(
                    sourceId = obj.stringOrNull("sourceId")?.toLongOrNull() ?: return@runCatching null,
                    manga = manga,
                    addedAt = obj.stringOrNull("addedAt")?.toLongOrNull() ?: 0L,
                )
            }.getOrNull()
            if (entry != null) libraryByUrl[entry.manga.url] = entry
        }
        _entries.value = libraryByUrl.values.toList()

        historyList.clear()
        readArray(historyFile).forEach { element ->
            val entry = runCatching {
                val obj = element.jsonObject
                val manga = obj.get("manga")?.jsonObject?.toSnapshot() ?: return@runCatching null
                HistoryEntry(
                    sourceId = obj.stringOrNull("sourceId")?.toLongOrNull() ?: return@runCatching null,
                    manga = manga,
                    at = obj.stringOrNull("at")?.toLongOrNull() ?: 0L,
                )
            }.getOrNull()
            if (entry != null) historyList.add(entry)
        }
        _history.value = historyList.toList()

        chapters.clear()
        readArray(progressFile).forEach { element ->
            val parsed = runCatching {
                val obj = element.jsonObject
                val mangaUrl = obj.stringOrNull("mangaUrl") ?: return@runCatching null
                val chapterUrl = obj.stringOrNull("chapterUrl") ?: return@runCatching null
                val read = obj.stringOrNull("read")?.toBooleanStrictOrNull() ?: false
                val page = obj.stringOrNull("page")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                Triple(mangaUrl, chapterUrl, ChapterState(read = read, page = page))
            }.getOrNull() ?: return@forEach
            chapters[parsed.first to parsed.second] = parsed.third
        }
    }

    // -- persistence (atomic tmp+move, never throws; call with lock held) --

    private fun persistLibrary() {
        val array = buildJsonArray {
            libraryByUrl.values.forEach { entry ->
                add(
                    buildJsonObject {
                        put("sourceId", entry.sourceId.toString())
                        put("manga", entry.manga.toJson())
                        put("addedAt", entry.addedAt.toString())
                    },
                )
            }
        }
        writeAtomic(libraryFile, prettyJson.encodeToString(JsonArray.serializer(), array))
    }

    private fun persistHistory() {
        val array = buildJsonArray {
            historyList.forEach { entry ->
                add(
                    buildJsonObject {
                        put("sourceId", entry.sourceId.toString())
                        put("manga", entry.manga.toJson())
                        put("at", entry.at.toString())
                    },
                )
            }
        }
        writeAtomic(historyFile, prettyJson.encodeToString(JsonArray.serializer(), array))
    }

    private fun persistProgress() {
        val array = buildJsonArray {
            chapters.forEach { (key, state) ->
                add(
                    buildJsonObject {
                        put("mangaUrl", key.first)
                        put("chapterUrl", key.second)
                        put("read", state.read.toString())
                        put("page", state.page.toString())
                    },
                )
            }
        }
        writeAtomic(progressFile, prettyJson.encodeToString(JsonArray.serializer(), array))
    }

    private fun writeAtomic(target: Path, text: String) {
        runCatching {
            Files.createDirectories(target.parent ?: dir)
            val tmp = target.resolveSibling("${target.fileName}.tmp")
            Files.writeString(
                tmp,
                text,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    tmp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: Exception) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun readArray(file: Path): JsonArray =
        runCatching {
            if (!Files.isRegularFile(file)) return JsonArray(emptyList())
            prettyJson.parseToJsonElement(Files.readString(file, Charsets.UTF_8)).jsonArray
        }.getOrDefault(JsonArray(emptyList()))
}

private val prettyJson = Json { prettyPrint = true }

private fun snapshotOf(manga: SManga): MangaRef =
    MangaRef(
        url = runCatching { manga.url }.getOrNull() ?: "",
        title = manga.displayTitle(),
        thumbnail = runCatching { manga.thumbnail_url }.getOrNull(),
        author = runCatching { manga.author }.getOrNull(),
    )

private fun MangaRef.toJson(): JsonObject =
    buildJsonObject {
        put("url", url)
        put("title", title)
        put("thumbnail", thumbnail)
        put("author", author)
    }

private fun JsonObject.toSnapshot(): MangaRef? {
    val url = stringOrNull("url") ?: return null
    return MangaRef(
        url = url,
        title = stringOrNull("title") ?: "Untitled",
        thumbnail = stringOrNull("thumbnail") ?: stringOrNull("thumbnail_url"),
        author = stringOrNull("author"),
    )
}

/** Null-safe string read: absent, null-literal or non-primitive -> null. */
private fun JsonObject.stringOrNull(key: String): String? {
    val element = get(key) ?: return null
    if (element is JsonNull) return null
    return runCatching { element.jsonPrimitive.content }.getOrNull()
}
