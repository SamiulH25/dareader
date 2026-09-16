package dareader.ext.manager

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dareader.ext.testing.ExtensionFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.io.TempDir
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionManagerTest {

    @TempDir
    lateinit var workDir: Path

    private val client = OkHttpClient()

    private fun manager(trust: (TrustRequest) -> Boolean = { true }) =
        ExtensionManager(workDir, client, trust)

    private fun stage(pkg: String, jar: Path) {
        val dir = workDir.resolve("extensions").resolve(pkg)
        Files.createDirectories(dir)
        Files.copy(jar, dir.resolve("extension.jar"), StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(
            dir.resolve("meta.json"),
            """{"pkg":"$pkg","mainClass":"${ExtensionFixture.SOURCE_CLASS}","versionName":"1.0","versionCode":1}""",
        )
    }

    private fun stageBroken(pkg: String, jar: Path) {
        val dir = workDir.resolve("extensions").resolve(pkg)
        Files.createDirectories(dir)
        Files.copy(jar, dir.resolve("extension.jar"), StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(
            dir.resolve("meta.json"),
            """{"pkg":"$pkg","mainClass":"${ExtensionFixture.BROKEN_SOURCE_CLASS}","versionName":"1.0","versionCode":1}""",
        )
    }

    private fun pkgJar() = workDir.resolve("extensions").resolve(ExtensionFixture.PKG).resolve("extension.jar")

    /** Trust pins are process-global; point them at a per-test dir. */
    private fun withTrustDir(block: () -> Unit) {
        val previous = System.getProperty("dareader.trust.dir")
        System.setProperty("dareader.trust.dir", workDir.resolve("trust").toString())
        try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty("dareader.trust.dir")
            } else {
                System.setProperty("dareader.trust.dir", previous)
            }
        }
    }

    private fun respond(exchange: HttpExchange, bytes: ByteArray) {
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun `rescan loads staged packages and skips broken ones`() {
        val fixtureJar = ExtensionFixture.buildSourceJar(workDir.resolve("fixture"))
        stage(ExtensionFixture.PKG, fixtureJar)
        stageBroken("com.example.broken", fixtureJar)

        val manager = manager()
        runBlocking { manager.rescan() }

        assertEquals(listOf(ExtensionFixture.PKG), manager.installed.value.map { it.pkg })
        assertNotNull(GetSource.findById(ExtensionFixture.FAKE_SOURCE_ID))
        manager.close()
    }

    @Test
    fun `uninstall removes staged files and registry entries`() {
        stage(ExtensionFixture.PKG, ExtensionFixture.buildSourceJar(workDir.resolve("fixture")))
        val manager = manager()
        runBlocking { manager.rescan() }

        runBlocking { manager.uninstall(ExtensionFixture.PKG) }

        assertTrue(manager.installed.value.isEmpty())
        assertNull(GetSource.findById(ExtensionFixture.FAKE_SOURCE_ID))
        assertFalse(Files.exists(workDir.resolve("extensions").resolve(ExtensionFixture.PKG)))
    }

    @Test
    fun `close unregisters every source but leaves staged files`() {
        stage(ExtensionFixture.PKG, ExtensionFixture.buildSourceJar(workDir.resolve("fixture")))
        val manager = manager()
        runBlocking { manager.rescan() }

        manager.close()
        manager.close()

        assertTrue(manager.installed.value.isEmpty())
        assertNull(GetSource.findById(ExtensionFixture.FAKE_SOURCE_ID))
        assertTrue(Files.isRegularFile(pkgJar()))
    }

    @Test
    fun `concurrent install and uninstall of one package stay consistent`() {
        val fixtureJar = ExtensionFixture.buildSourceJar(workDir.resolve("fixture"))
        val incoming = Files.copy(fixtureJar, workDir.resolve("incoming.jar"))
        val manager = manager()
        val meta =
            StagedMeta(
                pkg = ExtensionFixture.PKG,
                mainClass = ExtensionFixture.SOURCE_CLASS,
                versionName = "1.0",
                versionCode = 1L,
            )

        val results =
            runBlocking {
                val install = async(Dispatchers.IO) { runCatching { manager.stageAndLoad(meta, incoming) } }
                val uninstall = async(Dispatchers.IO) { runCatching { manager.uninstall(ExtensionFixture.PKG) } }
                listOf(install.await(), uninstall.await())
            }
        assertTrue(
            results.any { it.isSuccess },
            "both operations failed: ${results.map { it.exceptionOrNull() }}",
        )

        val installed = manager.installed.value.any { it.pkg == ExtensionFixture.PKG }
        val jarPresent = Files.isRegularFile(pkgJar())
        assertEquals(installed, jarPresent)
        assertEquals(installed, GetSource.findById(ExtensionFixture.FAKE_SOURCE_ID) != null)
        manager.close()
    }

    @Test
    fun `jar install pins the digest and reuses it for identical bytes`() {
        val sourceJar = Files.readAllBytes(ExtensionFixture.buildSourceJar(workDir.resolve("fixture")))
        val otherJar = Files.readAllBytes(ExtensionFixture.buildFactoryJar(workDir.resolve("fixture-other")))

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/source.jar") { respond(it, sourceJar) }
        server.createContext("/other.jar") { respond(it, otherJar) }
        server.start()

        try {
            withTrustDir {
                var prompts = 0
                val manager = manager { prompts++; true }
                val base = "http://127.0.0.1:${server.address.port}"
                try {
                    runBlocking {
                        manager.installFromJarUrl("$base/source.jar", ExtensionFixture.PKG, "1.0", 1L, "1.4")
                    }
                    assertEquals(1, prompts)
                    assertNotNull(GetSource.findById(ExtensionFixture.FAKE_SOURCE_ID))

                    runBlocking {
                        manager.installFromJarUrl("$base/source.jar", ExtensionFixture.PKG, "1.0", 1L, "1.4")
                    }
                    assertEquals(1, prompts, "identical bytes must reuse the pinned digest")

                    runBlocking {
                        manager.installFromJarUrl("$base/other.jar", ExtensionFixture.PKG, "1.0", 1L, "1.4")
                    }
                    assertEquals(2, prompts, "different bytes must re-prompt")
                } finally {
                    manager.close()
                }
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `jar install rejects metadata outside the lib window`() {
        val manager = manager()

        val error =
            runCatching {
                runBlocking {
                    manager.installFromJarUrl("http://127.0.0.1:9/never.jar", ExtensionFixture.PKG, "2.0", 1L, "2.0")
                }
            }.exceptionOrNull()

        assertTrue(
            error is IllegalStateException && error.message.orEmpty().startsWith("rejected: lib"),
            "expected a lib-window rejection, got $error",
        )
    }
}
