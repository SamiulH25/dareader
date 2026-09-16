package android.text;

/** Dareader-owned Html backed by jsoup text extraction. */
public final class Html {
    private Html() {}

    public static Spanned fromHtml(String source, int flags) {
        final String text = org.jsoup.Jsoup.parseBodyFragment(source).text();
        return new Spanned() {
            @Override
            public int length() {
                return text.length();
            }

            @Override
            public char charAt(int index) {
                return text.charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return text.subSequence(start, end);
            }

            @Override
            public String toString() {
                return text;
            }
        };
    }
}
