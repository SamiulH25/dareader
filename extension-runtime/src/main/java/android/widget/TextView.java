package android.widget;

import android.text.TextWatcher;
import android.view.View;

/** Minimal dareader-owned TextView stub. */
public class TextView extends View {
    public void addTextChangedListener(TextWatcher watcher) {}

    public CharSequence getError() {
        return null;
    }

    public void setError(CharSequence error) {}
}
