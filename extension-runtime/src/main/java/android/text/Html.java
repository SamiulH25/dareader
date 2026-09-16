package android.text;

/** Dareader-owned Html backed by jsoup text extraction. */
public final class Html {
    private Html() {}

    public static Spanned fromHtml(String source, int flags) {
        return new SpannableString(org.jsoup.Jsoup.parseBodyFragment(source).text());
    }
}
