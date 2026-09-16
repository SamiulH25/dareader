package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Set;

/**
 * Minimal dareader-owned Preference stub. Members grow on demand driven by
 * NoSuchMethodErrors from loaded extensions (never speculatively).
 *
 * <p>Values persist in a {@link SharedPreferences} store: an explicitly
 * assigned one (see {@link PreferenceScreen#setSharedPreferences}, used by
 * the host to bind the source-scoped {@code source_<id>} store) or, failing
 * that, the default shared preferences of the construction context.
 */
public class Preference {
    private final Context context;
    private String key;
    private CharSequence title;
    private CharSequence summary;
    private Object defaultValue;
    private SharedPreferences sharedPreferences;
    private OnPreferenceChangeListener listener;

    public Preference(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public void setTitle(CharSequence title) {
        this.title = title;
    }

    public CharSequence getTitle() {
        return title;
    }

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    public void setSummary(CharSequence summary) {
        this.summary = summary;
    }

    public CharSequence getSummary() {
        return summary;
    }

    public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) {
        this.listener = listener;
    }

    public OnPreferenceChangeListener getOnPreferenceChangeListener() {
        return listener;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    /** Overrides the store values persist in; propagated to children by {@link PreferenceScreen}. */
    public void setSharedPreferences(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    public SharedPreferences getSharedPreferences() {
        if (sharedPreferences != null) return sharedPreferences;
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    protected void notifyChanged(Object newValue) {
        if (listener != null) listener.onPreferenceChange(this, newValue);
    }

    protected boolean persistString(String value) {
        if (key == null) return false;
        getSharedPreferences().edit().putString(key, value).apply();
        return true;
    }

    protected String getPersistedString(String fallback) {
        if (key == null) return fallback;
        SharedPreferences prefs = getSharedPreferences();
        return prefs.contains(key) ? prefs.getString(key, fallback) : fallback;
    }

    protected boolean persistStringSet(Set<String> value) {
        if (key == null) return false;
        getSharedPreferences().edit().putStringSet(key, value).apply();
        return true;
    }

    protected Set<String> getPersistedStringSet(Set<String> fallback) {
        if (key == null) return fallback;
        SharedPreferences prefs = getSharedPreferences();
        return prefs.contains(key) ? prefs.getStringSet(key, fallback) : fallback;
    }

    protected boolean persistBoolean(boolean value) {
        if (key == null) return false;
        getSharedPreferences().edit().putBoolean(key, value).apply();
        return true;
    }

    protected boolean getPersistedBoolean(boolean fallback) {
        if (key == null) return fallback;
        SharedPreferences prefs = getSharedPreferences();
        return prefs.contains(key) ? prefs.getBoolean(key, fallback) : fallback;
    }
}
