package android.content

import dareader.ext.android.StoreContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextStubTest {

    @Test
    fun `mode and service constants match Android values`() {
        assertEquals(0, Context.MODE_PRIVATE)
        assertEquals(4, Context.MODE_MULTI_PROCESS)
        assertEquals(32768, Context.MODE_APPEND)
        assertEquals("connectivity", Context.CONNECTIVITY_SERVICE)
        assertEquals("layout_inflater", Context.LAYOUT_INFLATER_SERVICE)
        assertEquals("window", Context.WINDOW_SERVICE)
    }

    @Test
    fun `system service lookups are null, not link errors`() {
        val context: Context = StoreContext()

        assertNull(context.getSystemService(Context.CONNECTIVITY_SERVICE))
        assertNull(context.getSystemService(Context::class.java))
    }
}
