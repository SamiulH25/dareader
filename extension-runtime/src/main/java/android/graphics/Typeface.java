package android.graphics;

/** Dareader-owned Typeface mapping onto logical AWT fonts (headless-safe). */
public class Typeface {
    public static final Typeface DEFAULT = new Typeface("SansSerif", java.awt.Font.PLAIN);
    public static final Typeface DEFAULT_BOLD = new Typeface("SansSerif", java.awt.Font.BOLD);
    public static final Typeface SANS_SERIF = new Typeface("SansSerif", java.awt.Font.PLAIN);
    public static final Typeface SERIF = new Typeface("Serif", java.awt.Font.PLAIN);
    public static final Typeface MONOSPACE = new Typeface("Monospaced", java.awt.Font.PLAIN);

    private final String family;
    private final int style;

    Typeface() {
        this("SansSerif", java.awt.Font.PLAIN);
    }

    private Typeface(String family, int style) {
        this.family = family;
        this.style = style;
    }

    public static Typeface create(String familyName, int style) {
        return new Typeface(familyName == null || familyName.isEmpty() ? "SansSerif" : familyName, style);
    }

    public static Typeface create(Typeface family, int style) {
        return new Typeface(family == null ? "SansSerif" : family.family, style);
    }

    public int getStyle() {
        return style;
    }

    String family() {
        return family;
    }

    int style() {
        return style;
    }
}
