package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal dareader-owned PreferenceScreen stub. Exists so
 * ConfigurableSource.setupPreferenceScreen links; members grow on demand
 * when a loaded extension actually builds a prefs screen at runtime.
 *
 * <p>Holds an ordered child list with add/remove/find; a store assigned via
 * {@link #setSharedPreferences} is propagated to current and future
 * children so values persist in the host-chosen (source-scoped) store.
 */
public class PreferenceScreen extends Preference {
    private final List<Preference> preferences = new ArrayList<>();

    public PreferenceScreen(Context context) {
        super(context);
    }

    public boolean addPreference(Preference preference) {
        SharedPreferences override = currentOverride();
        if (override != null && !(preference instanceof PreferenceScreen)) {
            preference.setSharedPreferences(override);
        }
        return preferences.add(preference);
    }

    public boolean removePreference(Preference preference) {
        return preferences.remove(preference);
    }

    @SuppressWarnings("unchecked")
    public <T extends Preference> T findPreference(CharSequence key) {
        if (key == null) return null;
        for (Preference preference : preferences) {
            if (key.equals(preference.getKey())) return (T) preference;
            if (preference instanceof PreferenceScreen) {
                T found = ((PreferenceScreen) preference).findPreference(key);
                if (found != null) return found;
            }
        }
        return null;
    }

    public int getPreferenceCount() {
        return preferences.size();
    }

    public Preference getPreference(int index) {
        return preferences.get(index);
    }

    @Override
    public void setSharedPreferences(SharedPreferences sharedPreferences) {
        super.setSharedPreferences(sharedPreferences);
        for (Preference preference : preferences) {
            if (!(preference instanceof PreferenceScreen)) {
                preference.setSharedPreferences(sharedPreferences);
            }
        }
    }

    private SharedPreferences currentOverride() {
        try {
            return super.getSharedPreferences();
        } catch (Exception e) {
            return null;
        }
    }
}
