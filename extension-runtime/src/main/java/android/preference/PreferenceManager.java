package android.preference;

import android.content.Context;
import android.content.SharedPreferences;

/** Minimal dareader-owned stub delegating to the named store. */
public final class PreferenceManager {
    private PreferenceManager() {}

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context.getSharedPreferences("default", Context.MODE_PRIVATE);
    }
}
