package dareader.ext.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmSharedPreferencesTest {

    private fun freshStore(name: String): JvmSharedPreferences {
        val prefs = JvmSharedPreferences(name)
        prefs.edit().clear().commit()
        return prefs
    }

    @Test
    fun `typed values round-trip`() {
        val prefs = freshStore("test_typed")
        prefs
            .edit()
            .putString("s", "x")
            .putInt("i", 7)
            .putLong("l", 8L)
            .putFloat("f", 1.5f)
            .putBoolean("b", true)
            .commit()

        assertEquals("x", prefs.getString("s", null))
        assertEquals(7, prefs.getInt("i", 0))
        assertEquals(8L, prefs.getLong("l", 0L))
        assertEquals(1.5f, prefs.getFloat("f", 0f))
        assertTrue(prefs.getBoolean("b", false))
        assertTrue(prefs.contains("s"))
        assertFalse(prefs.contains("missing"))
    }

    @Test
    fun `string sets round-trip`() {
        val prefs = freshStore("test_sets")
        prefs.edit().putStringSet("set", setOf("a", "b")).commit()

        assertEquals(setOf("a", "b"), prefs.getStringSet("set", emptySet()))
        assertTrue(prefs.contains("set"))
    }

    @Test
    fun `putting a null string removes the key like Android`() {
        val prefs = freshStore("test_null_string")
        prefs.edit().putString("k", "v").commit()

        prefs.edit().putString("k", null).commit()

        assertFalse(prefs.contains("k"))
        assertNull(prefs.getString("k", null))
    }

    @Test
    fun `putting a null set removes the key like Android`() {
        val prefs = freshStore("test_null_set")
        prefs.edit().putStringSet("k", setOf("v")).commit()

        prefs.edit().putStringSet("k", null).commit()

        assertFalse(prefs.contains("k"))
    }

    @Test
    fun `remove drops the key and its set fragments`() {
        val prefs = freshStore("test_remove")
        prefs.edit().putStringSet("set", setOf("a", "b")).putString("s", "v").commit()

        prefs.edit().remove("set").remove("s").commit()

        assertFalse(prefs.contains("set"))
        assertFalse(prefs.contains("s"))
        assertTrue(prefs.getStringSet("set", emptySet()).isEmpty())
    }
}
