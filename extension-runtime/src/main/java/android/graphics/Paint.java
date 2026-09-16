package android.graphics;

import java.awt.Color;
import java.awt.Font;

/**
 * Dareader-owned Paint backed by AWT font/color state. Members grow on
 * extension demand; rendering happens through {@link Canvas}.
 */
public class Paint {
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

    public Font awtFont() {
        return new Font(typeface.family(), typeface.style(), Math.max(1, Math.round(textSize)));
    }

    public Color awtColor() {
        return new Color(color, true);
    }
}
