package android.text;

/** Minimal dareader-owned SpannableStringBuilder: mutable text with span bookkeeping. */
public class SpannableStringBuilder implements Spannable {
    private final StringBuilder text = new StringBuilder();
    private final SpanSet spans = new SpanSet();

    public SpannableStringBuilder() {}

    public SpannableStringBuilder(CharSequence text) {
        append(text);
    }

    public SpannableStringBuilder append(CharSequence text) {
        this.text.append(text == null ? "" : text);
        return this;
    }

    public SpannableStringBuilder append(char c) {
        text.append(c);
        return this;
    }

    public SpannableStringBuilder replace(int start, int end, CharSequence replacement) {
        text.replace(start, end, replacement == null ? "" : replacement.toString());
        spans.dropBeyond(text.length());
        return this;
    }

    public SpannableStringBuilder delete(int start, int end) {
        text.delete(start, end);
        spans.dropBeyond(text.length());
        return this;
    }

    public SpannableStringBuilder clear() {
        text.setLength(0);
        spans.clear();
        return this;
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
