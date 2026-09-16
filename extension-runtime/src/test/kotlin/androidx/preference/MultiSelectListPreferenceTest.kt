package androidx.preference

import dareader.ext.android.JvmSharedPreferences
import dareader.ext.android.StoreContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiSelectListPreferenceTest {

    private fun freshStore(name: String): JvmSharedPreferences {
        val store = JvmSharedPreferences(name)
        store.edit().clear().commit()
        return store
    }

    private fun preference(store: JvmSharedPreferences? = null): MultiSelectListPreference =
        MultiSelectListPreference(StoreContext()).also {
            it.setKey("genres")
            if (store != null) it.setSharedPreferences(store)
        }

    @Test
    fun `values round-trip through the attached store`() {
        val store = freshStore("multi_select_round_trip")
        val first = preference(store)
        first.setEntries(arrayOf("Action", "Drama"))
        first.setEntryValues(arrayOf("a", "d"))
        first.setValues(setOf("a", "d"))

        val reloaded = preference(store)
        reloaded.setEntries(arrayOf("Action", "Drama"))
        reloaded.setEntryValues(arrayOf("a", "d"))

        assertEquals(setOf("a", "d"), reloaded.getValues())
        assertEquals(0, reloaded.findIndexOfValue("a"))
        assertEquals(1, reloaded.findIndexOfValue("d"))
        assertEquals(-1, reloaded.findIndexOfValue("x"))
    }

    @Test
    fun `values assigned before the store attaches carry over`() {
        val store = freshStore("multi_select_carry_over")
        val pref = preference()
        pref.setValues(setOf("x"))

        pref.setSharedPreferences(store)

        assertTrue(store.contains("genres"))
        assertEquals(setOf("x"), pref.getValues())
    }

    @Test
    fun `unset preference returns empty selection`() {
        val pref = preference(freshStore("multi_select_empty"))

        assertEquals(emptySet(), pref.getValues())
    }
}
