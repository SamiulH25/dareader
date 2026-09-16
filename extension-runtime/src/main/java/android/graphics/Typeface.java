package android.graphics;

/** Dareader-owned Typeface mapping onto logical AWT fonts (headless-safe). */
public class Typeface {
    public static final Typeface DEFAULT = new Typeface("SansSerif", java.awt.Font.PLAIN);
    public static final Typeface DEFAULT_BOLD = new Typeface("SansSerif", java.awt.Font.BOLD);

    private final String family;
    private final int style;

    Typeface() {
        this("SansSerif", java.awt.Font.PLAIN);
    }

    private Typeface(String family, int style) {
        this.family = family;
        this.style = style;
    }

    String family() {
        return family;
    }

    int style() {
        return style;
    }
}
