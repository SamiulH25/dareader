package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

/** Minimal dareader-owned stub; see Preference. */
public class EditTextPreference extends Preference {
    private String text;

    public EditTextPreference(Context context) {
        super(context);
    }

    public String getText() {
        String persisted = getPersistedString(null);
        if (persisted != null) return persisted;
        if (text != null) return text;
        Object def = getDefaultValue();
        return def instanceof String ? (String) def : null;
    }

    public void setText(String text) {
        this.text = text;
        if (text != null) persistString(text);
        notifyChanged(text);
    }

    @Override
    public void setSharedPreferences(SharedPreferences sharedPreferences) {
        super.setSharedPreferences(sharedPreferences);
        if (getKey() == null || sharedPreferences.contains(getKey())) return;
        String pending = text != null ? text
            : (getDefaultValue() instanceof String ? (String) getDefaultValue() : null);
        if (pending != null) sharedPreferences.edit().putString(getKey(), pending).apply();
    }

    public void setOnBindEditTextListener(OnBindEditTextListener listener) {}

    public interface OnBindEditTextListener {
        void onBindEditText(android.widget.EditText editText);
    }
}
