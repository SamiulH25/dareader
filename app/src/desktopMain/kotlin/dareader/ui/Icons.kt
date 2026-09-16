package dareader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The desktop shell's icon set, drawn as 24dp stroke vectors.
 *
 * `material-icons-core` is not on the desktop classpath and the extended
 * artifact is a large dependency for eight glyphs, so the handful of shapes
 * the rail, app bars and lists need live here. Strokes are black; [Icon]
 * tints the rendered pixels, so callers pick the color as usual.
 */
object DareaderIcons {
    private fun icon(name: String, builder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = builder,
        ).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx, cy - r)
        arcTo(r, r, 0f, true, true, cx, cy + r)
        arcTo(r, r, 0f, true, true, cx, cy - r)
        close()
    }

    /** Library: a shelf of books. */
    val Library: ImageVector = icon("dareader.library") {
        moveTo(5f, 4.5f)
        lineTo(5f, 19.5f)
        moveTo(9.5f, 4.5f)
        lineTo(9.5f, 19.5f)
        moveTo(14f, 5f)
        lineTo(18.5f, 6.2f)
        lineTo(18.5f, 19.5f)
        moveTo(3.5f, 19.5f)
        lineTo(20.5f, 19.5f)
    }

    /** History: a clock. */
    val History: ImageVector = icon("dareader.history") {
        circle(12f, 12f, 8f)
        moveTo(12f, 7.2f)
        lineTo(12f, 12.4f)
        lineTo(15.8f, 14.4f)
    }

    /** Browse: a magnifier. */
    val Browse: ImageVector = icon("dareader.browse") {
        circle(10.6f, 10.6f, 6.4f)
        moveTo(15.4f, 15.4f)
        lineTo(20f, 20f)
    }

    /** Store: a shopping bag. */
    val Store: ImageVector = icon("dareader.store") {
        moveTo(5.6f, 8f)
        lineTo(18.4f, 8f)
        lineTo(17.2f, 19.6f)
        lineTo(6.8f, 19.6f)
        close()
        moveTo(9f, 8f)
        lineTo(9f, 6.6f)
        arcTo(3f, 3f, 0f, false, true, 15f, 6.6f)
        lineTo(15f, 8f)
    }

    /** Extensions: a four-tile apps grid. */
    val Extensions: ImageVector = icon("dareader.extensions") {
        moveTo(4.5f, 4.5f)
        lineTo(10f, 4.5f)
        lineTo(10f, 10f)
        lineTo(4.5f, 10f)
        close()
        moveTo(14f, 4.5f)
        lineTo(19.5f, 4.5f)
        lineTo(19.5f, 10f)
        lineTo(14f, 10f)
        close()
        moveTo(4.5f, 14f)
        lineTo(10f, 14f)
        lineTo(10f, 19.5f)
        lineTo(4.5f, 19.5f)
        close()
        moveTo(14f, 14f)
        lineTo(19.5f, 14f)
        lineTo(19.5f, 19.5f)
        lineTo(14f, 19.5f)
        close()
    }

    /** More: sliders. */
    val More: ImageVector = icon("dareader.more") {
        moveTo(4f, 7.5f)
        lineTo(20f, 7.5f)
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 16.5f)
        lineTo(20f, 16.5f)
        moveTo(9f, 5.5f)
        lineTo(9f, 9.5f)
        moveTo(15.5f, 10f)
        lineTo(15.5f, 14f)
        moveTo(8f, 14.5f)
        lineTo(8f, 18.5f)
    }

    /** Back: a leading chevron with a shaft. */
    val Back: ImageVector = icon("dareader.back") {
        moveTo(19f, 12f)
        lineTo(5.5f, 12f)
        moveTo(11.5f, 5.5f)
        lineTo(5f, 12f)
        lineTo(11.5f, 18.5f)
    }

    /** Check: a tick for read chapters. */
    val Check: ImageVector = icon("dareader.check") {
        moveTo(5f, 12.5f)
        lineTo(10f, 17.5f)
        lineTo(19f, 6.5f)
    }
}
