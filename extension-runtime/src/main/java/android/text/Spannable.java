package android.text;

/** Minimal dareader-owned Spannable stub. */
public interface Spannable extends Spanned {
    void setSpan(Object what, int start, int end, int flags);

    void removeSpan(Object what);
}
