package android.app;

import android.content.Context;
import android.content.ContextWrapper;

/**
 * Minimal dareader-owned Application stub backed by in-memory preferences.
 * Extends ContextWrapper because extensions reference that type.
 */
public class Application extends ContextWrapper {
    public Application() {
        super(new dareader.ext.android.StoreContext());
    }
}
