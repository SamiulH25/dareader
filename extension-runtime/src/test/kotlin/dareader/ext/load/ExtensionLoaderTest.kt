package dareader.ext.load

import dareader.ext.testing.ExtensionFixture
import org.junit.jupiter.api.io.TempDir
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End-to-end loader coverage: real jar -> child-first classloader -> registry. */
class ExtensionLoaderTest {

    @TempDir
    lateinit var workDir: Path

    @Test
    fun `loads a runtime-built extension jar and unregisters on close`() {
        val jar = ExtensionFixture.buildSourceJar(workDir)

        val ext = loadExtensionSources(jar, ExtensionFixture.SOURCE_CLASS, ExtensionFixture.PKG)
        try {
            assertEquals(listOf(ExtensionFixture.FAKE_SOURCE_ID), ext.sources.map { it.id })
            assertEquals("Fixture", ext.sources.single().name)
            assertNotNull(GetSource.findById(ExtensionFixture.FAKE_SOURCE_ID))
        } finally {
            ext.close()
        }

        assertNull(GetSource.findById(ExtensionFixture.FAKE_SOURCE_ID))
        assertTrue(GetSource.allSources().isEmpty())
    }

    @Test
    fun `close is idempotent`() {
        val jar = ExtensionFixture.buildSourceJar(workDir)

        val ext = loadExtensionSources(jar, ExtensionFixture.SOURCE_CLASS, ExtensionFixture.PKG)
        ext.close()
        ext.close()

        assertTrue(GetSource.allSources().isEmpty())
    }

    @Test
    fun `entry scan prefers a source factory`() {
        val jar = ExtensionFixture.buildFactoryJar(workDir.resolve("factory"))

        assertEquals("fixture.TestFactory", findExtensionEntryClass(jar))
    }

    @Test
    fun `entry scan falls back to a single source implementation`() {
        val jar = ExtensionFixture.buildSourceJar(workDir.resolve("single"))

        assertEquals(ExtensionFixture.SOURCE_CLASS, findExtensionEntryClass(jar))
    }

    @Test
    fun `entry scan skips a helper that cannot link`() {
        val jar = ExtensionFixture.buildSourceJar(workDir.resolve("unlinkable"))
        val helper =
            java.util.jar.JarFile(jar.toFile()).use { it.getEntry("fixture/UnlinkableSource.class") }
        assertNotNull(helper, "fixture jar must contain the unlinkable helper")

        assertEquals(ExtensionFixture.SOURCE_CLASS, findExtensionEntryClass(jar))
    }
}
