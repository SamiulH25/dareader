package dareader.ui

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSettingsTest {

    @TempDir
    lateinit var dataDir: Path

    @Test
    fun `mode and auto-advance persist to reader json`() {
        val previous = System.getProperty("dareader.data.dir")
        System.setProperty("dareader.data.dir", dataDir.toString())
        try {
            ReaderSettings.setMode(ReaderMode.PAGED)
            ReaderSettings.setAutoAdvance(false)

            assertEquals(
                """{"mode":"PAGED","autoAdvance":false}""",
                Files.readString(dataDir.resolve("reader.json")),
            )
        } finally {
            if (previous == null) {
                System.clearProperty("dareader.data.dir")
            } else {
                System.setProperty("dareader.data.dir", previous)
            }
        }
    }
}
