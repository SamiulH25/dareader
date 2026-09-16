package dareader.ext.pkg

import org.xml.sax.SAXException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApkInfoTest {

    private fun manifest(
        signed: Boolean = true,
        isExtension: Boolean = true,
        versionName: String = "1.4.0",
        sourceClass: String? = "com.example.Source",
        lib: String? = "1.4",
    ) = ExtensionManifest(
        packageName = "com.example",
        versionName = versionName,
        versionCode = 1L,
        label = null,
        isExtension = isExtension,
        sourceClass = sourceClass,
        sourceFactory = null,
        extensionName = null,
        extensionLib = lib,
        contentWarning = 0,
        signed = signed,
    )

    @Test
    fun `accepted manifest passes every gate`() {
        val m = manifest()
        assertNull(m.rejectionReason)
        assertTrue(m.accepted)
        assertTrue(m.judge().startsWith("accepted"))
        m.requireAccepted() // must not throw
    }

    @Test
    fun `unsigned manifest is rejected and requireAccepted throws`() {
        val m = manifest(signed = false)
        assertFalse(m.accepted)
        assertEquals("rejected: unsigned", m.judge())
        assertFailsWith<IllegalStateException> { m.requireAccepted() }
    }

    @Test
    fun `factory-only manifest is accepted`() {
        val m = manifest(sourceClass = null).copy(sourceFactory = "com.example.Factory")
        assertTrue(m.accepted)
    }

    @Test
    fun `meta-data parsing extracts name value pairs`() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
              <application>
                <meta-data android:name="tachiyomi.extension.class" android:value=".Ext"/>
                <meta-data android:name="tachiyomi.extension.lib" android:value="1.4"/>
              </application>
            </manifest>
        """.trimIndent()

        val meta = parseManifestMetaData(xml)

        assertEquals(".Ext", meta["tachiyomi.extension.class"])
        assertEquals("1.4", meta["tachiyomi.extension.lib"])
    }

    @Test
    fun `doctype and external entities are rejected`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE manifest [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <manifest package="&xxe;"/>
        """.trimIndent()

        assertFailsWith<SAXException> { parseManifestMetaData(xml) }
    }
}
