package android.os

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundleTest {

    @Test
    fun `typed values round-trip with defaults for missing keys`() {
        val bundle = Bundle()
        bundle.putString("s", "x")
        bundle.putInt("i", 7)
        bundle.putLong("l", 8L)
        bundle.putFloat("f", 1.5f)
        bundle.putDouble("d", 2.5)
        bundle.putBoolean("b", true)

        assertEquals("x", bundle.getString("s"))
        assertEquals(7, bundle.getInt("i"))
        assertEquals(8L, bundle.getLong("l"))
        assertEquals(1.5f, bundle.getFloat("f"))
        assertEquals(2.5, bundle.getDouble("d"))
        assertTrue(bundle.getBoolean("b"))

        assertEquals("fallback", bundle.getString("missing", "fallback"))
        assertEquals(42, bundle.getInt("missing", 42))
        assertNull(bundle.getString("missing"))
        assertFalse(bundle.containsKey("missing"))
        assertTrue(bundle.containsKey("s"))
    }

    @Test
    fun `copy constructor is independent and remove works`() {
        val source = Bundle()
        source.putString("k", "v")
        source.putStringArrayList("list", arrayListOf("a", "b"))

        val copy = Bundle(source)
        source.remove("k")

        assertEquals("v", copy.getString("k"))
        assertNull(source.getString("k"))
        assertEquals(listOf("a", "b"), copy.getStringArrayList("list"))
        assertNull(copy.getStringArrayList("missing"))
    }
}
