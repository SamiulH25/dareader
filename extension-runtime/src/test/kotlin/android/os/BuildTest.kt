package android.os

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildTest {

    @Test
    fun `version fields are present and plausible`() {
        assertTrue(Build.VERSION.SDK_INT >= 30, "SDK_INT must be a modern API level")
        assertEquals("14", Build.VERSION.RELEASE)
        assertTrue(Build.MANUFACTURER.isNotEmpty())
        assertTrue(Build.MODEL.isNotEmpty())
    }
}
