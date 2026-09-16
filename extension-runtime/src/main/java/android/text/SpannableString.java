package android.text;

/** Minimal dareader-owned SpannableString: immutable text with span bookkeeping. */
public class SpannableString implements Spannable {
    private final CharSequence text;
    private final SpanSet spans = new SpanSet();

    public SpannableString(CharSequence text) {
        this.text = text == null ? "" : text;
    }

    public static SpannableString valueOf(CharSequence text) {
        return new SpannableString(text);
    }

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
        return text.toString();
    }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {
        spans.set(what, start, end, flags, length());
    }

    @Override
    public void removeSpan(Object what) {
        spans.remove(what);
    }

    @Override
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        return spans.getSpans(start, end, type);
    }

    @Override
    public int getSpanStart(Object tag) {
        return spans.spanStart(tag);
    }

    @Override
    public int getSpanEnd(Object tag) {
        return spans.spanEnd(tag);
    }

    @Override
    public int getSpanFlags(Object tag) {
        return spans.spanFlags(tag);
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class<?> type) {
        return spans.nextTransition(start, limit, type);
    }
}
