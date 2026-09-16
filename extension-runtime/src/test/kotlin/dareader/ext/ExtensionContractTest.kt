package dareader.ext

import dareader.ext.pkg.ExtensionManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionContractTest {

    @Test
    fun libVersionParsedFromVersionName() {
        assertEquals(1.6, ExtensionContract.libVersionFromVersionName("1.6.3"))
        assertEquals(1.7, ExtensionContract.libVersionFromVersionName("1.7.0"))
        assertNull(ExtensionContract.libVersionFromVersionName("not-a-version"))
        assertNull(ExtensionContract.libVersionFromVersionName(""))
    }

    @Test
    fun libVersionRangeAccepts1_3Through1_7() {
        assertTrue(ExtensionContract.isSupportedLibVersion(1.3))
        assertTrue(ExtensionContract.isSupportedLibVersion(1.6))
        assertTrue(ExtensionContract.isSupportedLibVersion(1.7))
        assertFalse(ExtensionContract.isSupportedLibVersion(1.2))
        assertFalse(ExtensionContract.isSupportedLibVersion(1.8))
    }

    private fun manifest(
        sourceClass: String? = null,
        sourceFactory: String? = null,
        extensionLib: String? = "1.6",
    ) = ExtensionManifest(
        packageName = "eu.kanade.tachiyomi.extension.en.test",
        versionName = "1.6.0",
        versionCode = 42L,
        label = "Test",
        isExtension = true,
        sourceClass = sourceClass,
        sourceFactory = sourceFactory,
        extensionName = "Test",
        extensionLib = extensionLib,
        contentWarning = 0,
        signed = true,
    )

    @Test
    fun judgeAcceptsSingleSourceClass() {
        val verdict = manifest(sourceClass = "eu.kanade.Test").judge()
        assertTrue(verdict.startsWith("accepted"), verdict)
    }

    @Test
    fun judgeAcceptsFactoryOnlyExtension() {
        val m = manifest(sourceFactory = "eu.kanade.TestFactory")
        assertTrue(m.entryIsFactory)
        assertEquals("eu.kanade.TestFactory", m.entryClass)
        val verdict = m.judge()
        assertTrue(verdict.startsWith("accepted"), verdict)
    }

    @Test
    fun judgePrefersSourceClassOverFactory() {
        val m = manifest(sourceClass = "eu.kanade.Test", sourceFactory = "eu.kanade.TestFactory")
        assertFalse(m.entryIsFactory)
        assertEquals("eu.kanade.Test", m.entryClass)
        assertTrue(m.judge().startsWith("accepted"))
    }

    @Test
    fun judgeRejectsMissingEntry() {
        val verdict = manifest().judge()
        assertTrue(verdict.startsWith("rejected"), verdict)
    }

    @Test
    fun judgeRejectsUnsigned() {
        val verdict = manifest(sourceClass = "eu.kanade.Test").copy(signed = false).judge()
        assertTrue(verdict.startsWith("rejected"), verdict)
    }

    @Test
    fun judgeAcceptsLib17AndRejectsLib18() {
        assertTrue(manifest(sourceClass = "eu.kanade.Test", extensionLib = "1.7").judge().startsWith("accepted"))
        assertTrue(manifest(sourceClass = "eu.kanade.Test", extensionLib = "1.8").judge().startsWith("rejected"))
    }
}
