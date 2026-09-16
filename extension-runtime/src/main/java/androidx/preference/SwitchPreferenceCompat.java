package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

/** Minimal dareader-owned stub; see Preference. */
public class SwitchPreferenceCompat extends Preference {
    private Boolean checked;

    public SwitchPreferenceCompat(Context context) {
        super(context);
    }

    public boolean isChecked() {
        if (getKey() != null && getSharedPreferences().contains(getKey())) {
            return getPersistedBoolean(false);
        }
        if (checked != null) return checked;
        Object def = getDefaultValue();
        return def instanceof Boolean ? (Boolean) def : false;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        persistBoolean(checked);
        notifyChanged(checked);
    }

    @Override
    public void setSharedPreferences(SharedPreferences sharedPreferences) {
        super.setSharedPreferences(sharedPreferences);
        if (getKey() == null || sharedPreferences.contains(getKey())) return;
        Boolean pending = checked != null ? checked
            : (getDefaultValue() instanceof Boolean ? (Boolean) getDefaultValue() : null);
        if (pending != null) sharedPreferences.edit().putBoolean(getKey(), pending).apply();
    }
}
