package android.graphics;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Dareader-owned Paint backed by AWT font/color state. Members grow on
 * extension demand; rendering happens through {@link Canvas}.
 */
public class Paint {
    private static final BufferedImage METRICS_IMAGE =
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    int color = 0xFF000000;
    float textSize = 12f;
    boolean antiAlias = false;
    Typeface typeface = Typeface.DEFAULT;

    public void setAntiAlias(boolean aa) {
        this.antiAlias = aa;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setTextSize(float textSize) {
        this.textSize = textSize;
    }

    public Typeface setTypeface(Typeface typeface) {
        this.typeface = typeface;
        return typeface;
    }

    public float measureText(String text) {
        if (text == null || text.isEmpty()) return 0f;
        Graphics2D graphics = METRICS_IMAGE.createGraphics();
        try {
            graphics.setFont(awtFont());
            return graphics.getFontMetrics().stringWidth(text);
        } finally {
            graphics.dispose();
        }
    }

    public void getTextBounds(String text, int start, int end, Rect bounds) {
        if (text == null) {
            bounds.set(0, 0, 0, 0);
            return;
        }
        String slice = text.substring(Math.max(0, start), Math.min(text.length(), Math.max(start, end)));
        Graphics2D graphics = METRICS_IMAGE.createGraphics();
        try {
            graphics.setFont(awtFont());
            java.awt.FontMetrics metrics = graphics.getFontMetrics();
            bounds.set(0, -metrics.getAscent(), metrics.stringWidth(slice), metrics.getDescent());
        } finally {
            graphics.dispose();
        }
    }

    public Font awtFont() {
        return new Font(typeface.family(), typeface.style(), Math.max(1, Math.round(textSize)));
    }

    public Color awtColor() {
        return new Color(color, true);
    }
}
