package dareader.ui

import dareader.ext.manager.Installed
import dareader.ext.store.NetworkExtensionStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionsScreenTest {

    private fun storeExtension(versionCode: Long) =
        NetworkExtensionStore.Extension(
            name = "Example",
            packageName = "com.example.ext",
            resources = NetworkExtensionStore.Resources(apkUrl = "https://example.com/app.apk"),
            versionCode = versionCode,
            versionName = "1.$versionCode",
        )

    private fun installed(versionCode: Long) =
        Installed(
            pkg = "com.example.ext",
            mainClass = "com.example.Source",
            versionName = "1.$versionCode",
            versionCode = versionCode,
            sources = emptyList(),
        )

    @Test
    fun `update is offered only for newer store builds`() {
        assertFalse(updateAvailable(null, storeExtension(2)), "absent installs are installs, not updates")
        assertFalse(updateAvailable(installed(2), storeExtension(2)), "same build needs no update")
        assertFalse(updateAvailable(installed(3), storeExtension(2)), "older store build is not an update")
        assertTrue(updateAvailable(installed(1), storeExtension(2)), "newer store build offers update")
    }
}
