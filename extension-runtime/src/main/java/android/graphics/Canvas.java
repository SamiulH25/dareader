package android.graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Dareader-owned Canvas over a {@link Bitmap}'s AWT graphics. Translate-only
 * transform stack; save/restore balance without nesting semantics extensions
 * don't rely on.
 */
public class Canvas {
    private final Graphics2D graphics;
    private final int width;
    private final int height;

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

    public int save() {
        return graphics.getTransform().hashCode();
    }

    public void restore() {}

    public void translate(float dx, float dy) {
        graphics.translate(dx, dy);
    }

    public Graphics2D graphics() {
        return graphics;
    }
}
