package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

/** Minimal dareader-owned stub; see Preference. */
public class ListPreference extends Preference {
    private CharSequence[] entries = new CharSequence[0];
    private CharSequence[] entryValues = new CharSequence[0];
    private String value;

    public ListPreference(Context context) {
        super(context);
    }

    public String getValue() {
        String persisted = getPersistedString(null);
        if (persisted != null) return persisted;
        if (value != null) return value;
        Object def = getDefaultValue();
        return def instanceof String ? (String) def : null;
    }

    public void setValue(String value) {
        this.value = value;
        if (value != null) persistString(value);
        notifyChanged(value);
    }

    public int findIndexOfValue(String value) {
        if (value == null) return -1;
        for (int i = 0; i < entryValues.length; i++) {
            if (value.equals(entryValues[i] == null ? null : entryValues[i].toString())) return i;
        }
        return -1;
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public CharSequence[] getEntryValues() {
        return entryValues;
    }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries == null ? new CharSequence[0] : entries;
    }

    public void setEntryValues(CharSequence[] entryValues) {
        this.entryValues = entryValues == null ? new CharSequence[0] : entryValues;
    }

    @Override
    public void setSharedPreferences(SharedPreferences sharedPreferences) {
        super.setSharedPreferences(sharedPreferences);
        // A value assigned before the host attached the (source-scoped) store
        // must not stay behind in the previous store: carry it, or the
        // default, over unless the new store already holds this key.
        if (getKey() == null || sharedPreferences.contains(getKey())) return;
        String pending = value != null ? value
            : (getDefaultValue() instanceof String ? (String) getDefaultValue() : null);
        if (pending != null) sharedPreferences.edit().putString(getKey(), pending).apply();
    }
}
