package dareader.ext.android;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * File-backed SharedPreferences on top of {@link Preferences} (JDK-backed,
 * per-user, no native deps). Replaces the earlier in-memory map so extension
 * settings and cookies survive restarts.
 */
public class JvmSharedPreferences implements SharedPreferences {
    private final Preferences prefs;

    public JvmSharedPreferences(String storeName) {
        this.prefs = Preferences.userRoot().node("dareader/" + storeName.replace('/', '_'));
    }

    @Override
    public Map<String, ?> getAll() {
        try {
            Map<String, Object> out = new HashMap<>();
            Set<String> keys = new HashSet<>(Arrays.asList(prefs.keys()));
            for (String key : keys) {
                if (key.endsWith("#n")) {
                    String base = key.substring(0, key.length() - 2);
                    int count = prefs.getInt(key, -1);
                    if (count >= 0) {
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < count; i++) {
                            String v = prefs.get(base + "#" + i, null);
                            if (v != null) set.add(v);
                        }
                        out.put(base, set);
                    }
                    continue;
                }
                if (key.matches(".*#\\d+")) continue; // string-set fragments
                String raw = prefs.get(key, null);
                if (raw != null) out.put(key, coerce(raw));
            }
            return Collections.unmodifiableMap(out);
        } catch (BackingStoreException e) {
            return Collections.emptyMap();
        }
    }

    /** Best-effort typed value matching what the typed getters would return. */
    static Object coerce(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(raw);
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }

    @Override
    public String getString(String key, String defValue) {
        return prefs.get(key, defValue);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        int count = prefs.getInt(key + "#n", -1);
        if (count < 0) return defValues;
        Set<String> out = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String v = prefs.get(key + "#" + i, null);
            if (v != null) out.add(v);
        }
        return out;
    }

    @Override
    public int getInt(String key, int defValue) {
        return prefs.getInt(key, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        return prefs.getLong(key, defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return prefs.getFloat(key, defValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return prefs.getBoolean(key, defValue);
    }

    @Override
    public boolean contains(String key) {
        try {
            for (String k : prefs.keys()) {
                if (k.equals(key) || k.startsWith(key + "#")) return true;
            }
            return false;
        } catch (BackingStoreException e) {
            return false;
        }
    }

    @Override
    public Editor edit() {
        return new JvmEditor();
    }

    private class JvmEditor implements Editor {
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Set<String>> sets = new HashMap<>();
        private final Map<String, Integer> ints = new HashMap<>();
        private final Map<String, Long> longs = new HashMap<>();
        private final Map<String, Float> floats = new HashMap<>();
        private final Map<String, Boolean> bools = new HashMap<>();
        private final List<String> removals = new ArrayList<>();
        private boolean clear = false;

        @Override
        public Editor putString(String key, String value) {
            // Android semantics: putting null removes the key.
            if (value == null) return remove(key);
            strings.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            if (values == null) return remove(key);
            sets.put(key, new HashSet<>(values));
            return this;
        }
        @Override
        public Editor putInt(String key, int value) { ints.put(key, value); return this; }
        @Override
        public Editor putLong(String key, long value) { longs.put(key, value); return this; }
        @Override
        public Editor putFloat(String key, float value) { floats.put(key, value); return this; }
        @Override
        public Editor putBoolean(String key, boolean value) { bools.put(key, value); return this; }
        @Override
        public Editor remove(String key) { removals.add(key); return this; }
        @Override
        public Editor clear() { clear = true; return this; }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            if (clear) {
                try {
                    prefs.clear();
                } catch (BackingStoreException ignored) {
                }
            }
            for (String key : removals) {
                try {
                    for (String k : prefs.keys()) {
                        if (k.equals(key) || k.startsWith(key + "#")) prefs.remove(k);
                    }
                } catch (BackingStoreException ignored) {
                }
            }
            strings.forEach(prefs::put);
            ints.forEach(prefs::putInt);
            longs.forEach(prefs::putLong);
            floats.forEach(prefs::putFloat);
            bools.forEach(prefs::putBoolean);
            for (Map.Entry<String, Set<String>> e : sets.entrySet()) {
                List<String> values = new ArrayList<>(e.getValue());
                prefs.putInt(e.getKey() + "#n", values.size());
                for (int i = 0; i < values.size(); i++) {
                    prefs.put(e.getKey() + "#" + i, values.get(i));
                }
            }
            try {
                prefs.flush();
            } catch (BackingStoreException ignored) {
            }
        }
    }
}
