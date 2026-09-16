package android.app;

import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;

/** Minimal dareader-owned Activity stub (extension UrlActivity entry points). */
public class Activity extends ContextWrapper {
    public Activity() {
        super(new dareader.ext.android.StoreContext());
    }

    protected void onCreate(Bundle savedInstanceState) {}

    public void finish() {}

    public Intent getIntent() {
        return new Intent();
    }
}
