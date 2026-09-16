package android.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpannableStringTest {

    private class Marker

    @Test
    fun `spans are set, queried and removed`() {
        val text = SpannableString("hello world")
        val span = Marker()

        text.setSpan(span, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        assertEquals(0, text.getSpanStart(span))
        assertEquals(5, text.getSpanEnd(span))
        assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE, text.getSpanFlags(span))
        assertEquals(listOf(span), text.getSpans(3, 8, Marker::class.java).toList())
        assertTrue(text.getSpans(6, 9, Marker::class.java).isEmpty())

        text.removeSpan(span)
        assertEquals(-1, text.getSpanStart(span))
        assertEquals(0, text.getSpanFlags(span))
    }

    @Test
    fun `setSpan validates bounds and replaces an existing span`() {
        val text = SpannableString("0123456789")
        val span = Marker()

        assertFailsWith<IndexOutOfBoundsException> { text.setSpan(span, 0, 99, 0) }
        assertFailsWith<IndexOutOfBoundsException> { text.setSpan(span, 5, 2, 0) }
        assertFailsWith<IndexOutOfBoundsException> { text.setSpan(span, -1, 2, 0) }

        text.setSpan(span, 1, 4, 0)
        text.setSpan(span, 2, 6, 0)
        assertEquals(2, text.getSpanStart(span))
        assertEquals(1, text.getSpans(0, 10, Marker::class.java).size)
    }

    @Test
    fun `nextSpanTransition walks span boundaries`() {
        val text = SpannableString("0123456789")
        text.setSpan(Marker(), 2, 5, 0)

        assertEquals(2, text.nextSpanTransition(0, 10, Marker::class.java))
        assertEquals(5, text.nextSpanTransition(2, 10, Marker::class.java))
        assertEquals(10, text.nextSpanTransition(5, 10, Marker::class.java))
    }

    @Test
    fun `builder keeps text and spans consistent across edits`() {
        val builder = SpannableStringBuilder("ab")
        val span = Marker()
        builder.setSpan(span, 1, 2, 0)

        builder.append("cd")
        assertEquals("abcd", builder.toString())
        assertEquals(1, builder.getSpanStart(span))

        builder.delete(2, 4)
        assertEquals("ab", builder.toString())
        assertEquals(2, builder.getSpanEnd(span))

        builder.clear()
        assertEquals("", builder.toString())
        assertEquals(-1, builder.getSpanStart(span))
    }

    @Test
    fun `html extraction returns a real spannable`() {
        val spanned = Html.fromHtml("<p>Hello <b>world</b></p>", 0) as Spannable

        assertEquals("Hello world", spanned.toString())

        val span = Marker()
        spanned.setSpan(span, 0, 5, 0)
        assertEquals(5, spanned.getSpanEnd(span))
        assertTrue(spanned.getSpans(6, 11, Marker::class.java).isEmpty())
    }
}
