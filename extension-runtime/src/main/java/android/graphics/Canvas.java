package android.graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;

/**
 * Dareader-owned Canvas over a {@link Bitmap}'s AWT graphics. The transform
 * stack is real: save() snapshots the transform, restore() reinstates it.
 */
public class Canvas {
    private final Graphics2D graphics;
    private final int width;
    private final int height;
    private final ArrayDeque<AffineTransform> savedTransforms = new ArrayDeque<>();

    public Canvas(Bitmap bitmap) {
        this.graphics = bitmap.image().createGraphics();
        this.width = bitmap.image().getWidth();
        this.height = bitmap.image().getHeight();
        this.graphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
    }

    public void drawColor(int color) {
        graphics.setBackground(new Color(color, true));
        graphics.clearRect(0, 0, width, height);
    }

    /** Snapshots the current transform; returns the new stack depth. */
    public int save() {
        savedTransforms.push(graphics.getTransform());
        return savedTransforms.size();
    }

    /** Restores the transform captured by the matching save(). */
    public void restore() {
        AffineTransform saved = savedTransforms.poll();
        if (saved != null) graphics.setTransform(saved);
    }

    public void translate(float dx, float dy) {
        graphics.translate(dx, dy);
    }

    public void scale(float sx, float sy) {
        graphics.scale(sx, sy);
    }

    public void rotate(float degrees) {
        graphics.rotate(Math.toRadians(degrees));
    }

    public void clipRect(float left, float top, float right, float bottom) {
        graphics.clip(new Rectangle2D.Float(left, top, right - left, bottom - top));
    }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        graphics.setColor(paint.awtColor());
        graphics.fill(new Rectangle2D.Float(left, top, right - left, bottom - top));
    }

    public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
        graphics.setColor(paint.awtColor());
        graphics.draw(new Line2D.Float(startX, startY, stopX, stopY));
    }

    public void drawText(String text, float x, float y, Paint paint) {
        graphics.setColor(paint.awtColor());
        graphics.setFont(paint.awtFont());
        graphics.drawString(text, x, y);
    }

    public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        graphics.drawImage(bitmap.image(), Math.round(left), Math.round(top), null);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Graphics2D graphics() {
        return graphics;
    }
}
