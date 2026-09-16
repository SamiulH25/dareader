package android.text;

import java.util.ArrayList;
import java.util.List;

/** Package-private span bookkeeping shared by the minimal Spannable implementations. */
final class SpanSet {
    static final class Span {
        final Object what;
        final int start;
        final int end;
        final int flags;

        Span(Object what, int start, int end, int flags) {
            this.what = what;
            this.start = start;
            this.end = end;
            this.flags = flags;
        }
    }

    private final List<Span> spans = new ArrayList<>();

    void set(Object what, int start, int end, int flags, int textLength) {
        if (what == null) throw new IllegalArgumentException("span must not be null");
        if (start < 0) throw new IndexOutOfBoundsException("span start " + start + " < 0");
        if (start > end) throw new IndexOutOfBoundsException("span start " + start + " > end " + end);
        if (end > textLength) throw new IndexOutOfBoundsException("span end " + end + " > length " + textLength);
        remove(what);
        spans.add(new Span(what, start, end, flags));
    }

    void remove(Object what) {
        spans.removeIf(span -> span.what == what);
    }

    void clear() {
        spans.clear();
    }

    /** Drops spans that no longer fit the (shrunk) text. */
    void dropBeyond(int textLength) {
        spans.removeIf(span -> span.end > textLength);
    }

    @SuppressWarnings("unchecked")
    <T> T[] getSpans(int start, int end, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Span span : spans) {
            if (span.start <= end && span.end >= start && (type == null || type.isInstance(span.what))) {
                out.add((T) span.what);
            }
        }
        T[] array = (T[]) java.lang.reflect.Array.newInstance(type == null ? Object.class : type, out.size());
        return out.toArray(array);
    }

    int spanStart(Object tag) {
        Span span = find(tag);
        return span == null ? -1 : span.start;
    }

    int spanEnd(Object tag) {
        Span span = find(tag);
        return span == null ? -1 : span.end;
    }

    int spanFlags(Object tag) {
        Span span = find(tag);
        return span == null ? 0 : span.flags;
    }

    int nextTransition(int start, int limit, Class<?> type) {
        int best = limit;
        for (Span span : spans) {
            if (type != null && !type.isInstance(span.what)) continue;
            if (span.start > start && span.start < best) best = span.start;
            if (span.end > start && span.end < best) best = span.end;
        }
        return best;
    }

    private Span find(Object tag) {
        for (Span span : spans) {
            if (span.what == tag) return span;
        }
        return null;
    }
}
