package dareader.ext.trust

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionTrustTest {

    private lateinit var dir: java.nio.file.Path
    private var previous: String? = null

    @BeforeTest
    fun isolateTrustStore() {
        dir = Files.createTempDirectory("dareader-trust-test-")
        previous = System.getProperty("dareader.trust.dir")
        System.setProperty("dareader.trust.dir", dir.toString())
    }

    @AfterTest
    fun restoreTrustStore() {
        if (previous == null) System.clearProperty("dareader.trust.dir")
        else System.setProperty("dareader.trust.dir", previous)
    }

    @Test
    fun matchesStoreKeyIgnoresCaseAndSeparators() {
        val hash = "ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12"
        assertTrue(matchesStoreKey(listOf(hash), hash.uppercase()))
        assertTrue(matchesStoreKey(listOf(hash), "AB:12:CD:34:EF:56:AB:12:CD:34:EF:56:AB:12:CD:34:EF:56:AB:12:CD:34:EF:56:AB:12:CD:34:EF:56:AB:12"))
        assertFalse(matchesStoreKey(listOf(hash), "00".repeat(32)))
        assertFalse(matchesStoreKey(listOf(hash), ""))
        assertFalse(matchesStoreKey(emptyList(), hash))
    }

    @Test
    fun isTrustedViaStoreKeyWithoutPin() {
        val hash = "ab12cd34".repeat(8)
        assertTrue(isTrusted("com.example.ext", 1L, listOf(hash), hash))
        assertFalse(isTrusted("com.example.ext", 1L, listOf(hash), "ff".repeat(32)))
    }

    @Test
    fun pinGrantsTrustForExactPackageVersionAndCert() {
        val hash = "12ab34cd".repeat(8)
        assertFalse(isTrusted("com.example.ext", 7L, listOf(hash), "ff".repeat(32)))
        pin("com.example.ext", 7L, listOf(hash))
        assertTrue(isTrusted("com.example.ext", 7L, listOf(hash), "ff".repeat(32)))
    }

    @Test
    fun pinDoesNotBleedAcrossVersionCertOrPackage() {
        val hash = "12ab34cd".repeat(8)
        val otherHash = "99bb88aa".repeat(8)
        pin("com.example.ext", 7L, listOf(hash))
        // A fresh read of the same file-backed store still trusts the pinned triple.
        assertTrue(loadPins().any { it.pkg == "com.example.ext" })
        assertTrue(isTrusted("com.example.ext", 7L, listOf(hash), ""))
        assertFalse(isTrusted("com.example.ext", 8L, listOf(hash), ""))
        assertFalse(isTrusted("com.example.ext", 7L, listOf(otherHash), ""))
        assertFalse(isTrusted("com.example.other", 7L, listOf(hash), ""))
    }
}
