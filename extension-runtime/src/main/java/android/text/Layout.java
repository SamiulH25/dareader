package android.text;

import android.graphics.Canvas;

/** Minimal dareader-owned Layout stub. */
public abstract class Layout {
    public enum Alignment { ALIGN_NORMAL }

    public abstract void draw(Canvas canvas);

    public abstract int getHeight();
}
