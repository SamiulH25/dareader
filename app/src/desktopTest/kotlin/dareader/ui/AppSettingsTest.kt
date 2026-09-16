package dareader.ui

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsTest {

    @TempDir
    lateinit var dataDir: Path

    @Test
    fun `language matching keeps the preference, language-agnostic sources and no filter`() {
        assertTrue(sourceMatchesLanguage("ja", null), "no preference shows every source")
        assertTrue(sourceMatchesLanguage("en", "en"), "exact language matches")
        assertTrue(sourceMatchesLanguage("EN", "en"), "language codes are case-insensitive")
        assertTrue(sourceMatchesLanguage("all", "en"), "language-agnostic sources stay visible")
        assertFalse(sourceMatchesLanguage("ja", "en"), "other languages are filtered out")
    }

    @Test
    fun `language choice persists, including clearing to all`() {
        val previous = System.getProperty("dareader.data.dir")
        System.setProperty("dareader.data.dir", dataDir.toString())
        try {
            AppSettings.setLanguage("fr")
            assertEquals("""{"language":"fr"}""", Files.readString(dataDir.resolve("settings.json")))

            AppSettings.setLanguage(null)
            assertEquals("""{"language":null}""", Files.readString(dataDir.resolve("settings.json")))
        } finally {
            if (previous == null) {
                System.clearProperty("dareader.data.dir")
            } else {
                System.setProperty("dareader.data.dir", previous)
            }
        }
    }
}
