package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Minimal dareader-owned stub; see Preference. */
public class MultiSelectListPreference extends Preference {
    private CharSequence[] entries = new CharSequence[0];
    private CharSequence[] entryValues = new CharSequence[0];
    private Set<String> values = Collections.emptySet();

    public MultiSelectListPreference(Context context) {
        super(context);
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries == null ? new CharSequence[0] : entries;
    }

    public CharSequence[] getEntryValues() {
        return entryValues;
    }

    public void setEntryValues(CharSequence[] entryValues) {
        this.entryValues = entryValues == null ? new CharSequence[0] : entryValues;
    }

    /** Selected entry values; persisted in the attached store once a key exists. */
    @SuppressWarnings("unchecked")
    public Set<String> getValues() {
        Set<String> persisted = getPersistedStringSet(null);
        if (persisted != null) return persisted;
        if (!values.isEmpty()) return values;
        Object def = getDefaultValue();
        if (def instanceof Set) {
            Set<String> copy = new HashSet<>();
            for (Object item : (Set<Object>) def) {
                if (item != null) copy.add(item.toString());
            }
            return copy;
        }
        return values;
    }

    public void setValues(Set<String> values) {
        this.values = values == null ? Collections.emptySet() : new HashSet<>(values);
        persistStringSet(this.values);
        notifyChanged(this.values);
    }

    public int findIndexOfValue(String value) {
        if (value == null) return -1;
        for (int i = 0; i < entryValues.length; i++) {
            if (value.equals(entryValues[i] == null ? null : entryValues[i].toString())) return i;
        }
        return -1;
    }

    @Override
    public void setSharedPreferences(SharedPreferences sharedPreferences) {
        super.setSharedPreferences(sharedPreferences);
        // Values assigned before the host attached the (source-scoped) store
        // must not stay behind in the previous store: carry them over unless
        // the new store already holds this key.
        if (getKey() == null || sharedPreferences.contains(getKey())) return;
        if (!values.isEmpty()) {
            sharedPreferences.edit().putStringSet(getKey(), values).apply();
        }
    }
}
