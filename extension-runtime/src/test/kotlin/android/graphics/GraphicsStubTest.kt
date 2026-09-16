package android.graphics

import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphicsStubTest {

    private fun argb(bitmap: Bitmap, x: Int, y: Int): Int = bitmap.image().getRGB(x, y)

    private fun paint(color: Int) = Paint().also { it.setColor(color) }

    @Test
    fun `restore undoes a save-scoped translate`() {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())

        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val white = 0xFFFFFFFF.toInt()

        canvas.save()
        canvas.translate(10f, 0f)
        canvas.drawRect(0f, 0f, 10f, 10f, paint(red))

        assertEquals(red, argb(bitmap, 15, 5), "red rect must use the save-scoped translate")
        assertEquals(white, argb(bitmap, 5, 5), "no draw must happen left of the translate")

        canvas.restore()
        canvas.translate(0f, 10f)
        canvas.drawRect(0f, 0f, 10f, 10f, paint(blue))

        assertEquals(blue, argb(bitmap, 5, 15), "blue rect must use only the post-restore translate")
        assertEquals(white, argb(bitmap, 15, 15), "restore must drop the earlier translate")
    }

    @Test
    fun `drawText paints inside the measured bounds`() {
        val bitmap = Bitmap.createBitmap(60, 24, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())

        val paint = paint(0xFF000000.toInt()).also { it.setTextSize(16f) }
        val text = "Wg"
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, 2f, 18f, paint)

        assertTrue(paint.measureText(text) > 0f, "measureText must report a positive width")
        assertTrue(bounds.width() > 0 && bounds.height() > 0, "bounds must be non-empty: $bounds")

        val painted =
            (0 until 24).sumOf { y ->
                (0 until 60).count { x -> argb(bitmap, x, y) != 0xFFFFFFFF.toInt() }
            }
        assertTrue(painted > 0, "drawText must change pixels")
    }

    @Test
    fun `scaled bitmap has the requested dimensions`() {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createScaledBitmap(source, 32, 16, true)

        assertEquals(32, scaled.getWidth())
        assertEquals(16, scaled.getHeight())
        assertEquals(8, source.getWidth())
    }

    @Test
    fun `jpeg compression produces decodable bytes`() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF336699.toInt())

        val out = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))

        val decoded = ImageIO.read(out.toByteArray().inputStream())
        assertEquals(4, decoded.width)
        assertEquals(4, decoded.height)
    }

    @Test
    fun `typeface factory keeps the requested style`() {
        assertEquals(java.awt.Font.BOLD, Typeface.create("Serif", java.awt.Font.BOLD).getStyle())
        assertEquals(java.awt.Font.PLAIN, Typeface.SERIF.getStyle())
        assertEquals("Monospaced", Typeface.MONOSPACE.family())
    }
}
