package dareader.library

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryStoreTest {

    @TempDir
    lateinit var dataDir: Path

    private fun manga(
        url: String,
        title: String,
    ): SManga =
        SManga.create().also {
            it.url = url
            it.title = title
        }

    @Test
    fun `library and history survive a reload`() {
        val store = LibraryStore(dataDir)
        store.toggleInLibrary(7L, manga("/m/one", "One"))
        store.recordHistory(7L, manga("/m/one", "One"))

        val reloaded = LibraryStore(dataDir)

        assertEquals(listOf("/m/one"), reloaded.entries.value.map { it.manga.url })
        assertEquals(listOf("/m/one"), reloaded.history.value.map { it.manga.url })
        assertEquals(7L, reloaded.entries.value.single().sourceId)
        assertEquals("One", reloaded.entries.value.single().manga.title)
    }

    @Test
    fun `corrupt files hydrate empty and the store stays writable`() {
        Files.writeString(dataDir.resolve("library.json"), "{ not json")
        Files.writeString(dataDir.resolve("progress.json"), "]]")

        val store = LibraryStore(dataDir)
        assertTrue(store.entries.value.isEmpty())

        store.toggleInLibrary(1L, manga("/m/x", "X"))

        assertEquals(listOf("/m/x"), LibraryStore(dataDir).entries.value.map { it.manga.url })
    }

    @Test
    fun `history dedupes repeated opens and keeps the newest first`() {
        val store = LibraryStore(dataDir)
        store.recordHistory(1L, manga("/m/a", "A"))
        store.recordHistory(1L, manga("/m/b", "B"))
        store.recordHistory(1L, manga("/m/a", "A"))

        // Re-opening A moves it back to the front; B is older.
        assertEquals(listOf("/m/a", "/m/b"), store.history.value.map { it.manga.url })
    }

    @Test
    fun `progress and read marks round-trip across reload`() {
        val store = LibraryStore(dataDir)
        store.markChapterRead(1L, "/m/a", "/m/a/1", true)
        store.saveProgress(1L, "/m/a", "/m/a/1", 12)

        val reloaded = LibraryStore(dataDir)

        assertTrue(reloaded.isChapterRead(1L, "/m/a", "/m/a/1"))
        assertEquals(12, reloaded.getProgress(1L, "/m/a", "/m/a/1"))
        assertEquals(mapOf(MangaKey(1L, "/m/a") to 1), reloaded.readCounts.value)
    }

    @Test
    fun `two sources with the same manga url stay independent`() {
        val store = LibraryStore(dataDir)
        store.toggleInLibrary(1L, manga("/m/a", "A"))
        store.toggleInLibrary(2L, manga("/m/a", "A"))

        assertEquals(setOf(1L, 2L), store.entries.value.map { it.sourceId }.toSet())

        // Untoggling one source must not remove the other's entry.
        store.toggleInLibrary(1L, manga("/m/a", "A"))
        assertEquals(listOf(2L), store.entries.value.map { it.sourceId })

        store.markChapterRead(1L, "/m/a", "/m/a/1", true)
        assertTrue(store.isChapterRead(1L, "/m/a", "/m/a/1"))
        assertFalse(store.isChapterRead(2L, "/m/a", "/m/a/1"))

        store.saveProgress(2L, "/m/a", "/m/a/1", 7)
        assertEquals(0, store.getProgress(1L, "/m/a", "/m/a/1"))
        assertEquals(7, store.getProgress(2L, "/m/a", "/m/a/1"))
        assertEquals(mapOf(MangaKey(1L, "/m/a") to 1), store.readCounts.value)
    }

    @Test
    fun `legacy progress entries are adopted by the first source that touches them`() {
        // Legacy shape: records without a sourceId field.
        Files.writeString(
            dataDir.resolve("progress.json"),
            """[{"mangaUrl":"/m/a","chapterUrl":"/m/a/1","read":"true","page":"9"}]""",
        )

        val store = LibraryStore(dataDir)

        assertEquals(9, store.getProgress(5L, "/m/a", "/m/a/1"))
        assertTrue(store.isChapterRead(5L, "/m/a", "/m/a/1"))
        // Adoption is persisted under the new owner.
        assertEquals(9, LibraryStore(dataDir).getProgress(5L, "/m/a", "/m/a/1"))
    }

    @Test
    fun `bulk read toggle persists once and bumps the revision`() {
        val store = LibraryStore(dataDir)
        val before = store.progressRevision.value

        store.markChaptersRead(1L, "/m/a", listOf("/m/a/1", "/m/a/2", "/m/a/3"), true)

        assertEquals(before + 1, store.progressRevision.value)
        assertTrue(store.isChapterRead(1L, "/m/a", "/m/a/2"))
        assertEquals(mapOf(MangaKey(1L, "/m/a") to 3), store.readCounts.value)

        val reloaded = LibraryStore(dataDir)
        assertEquals(3, reloaded.readCounts.value[MangaKey(1L, "/m/a")])
        assertTrue(reloaded.isChapterRead(1L, "/m/a", "/m/a/3"))
    }

    @Test
    fun `clearHistory empties and persists`() {
        val store = LibraryStore(dataDir)
        store.recordHistory(1L, manga("/m/a", "A"))

        store.clearHistory()

        assertTrue(store.history.value.isEmpty())
        assertTrue(LibraryStore(dataDir).history.value.isEmpty())
    }
}
