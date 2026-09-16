package android.os;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Minimal dareader-owned Bundle stub: a typed string-keyed map covering the
 * accessors extensions commonly use. Values never leave the process — there
 * is no parcelling.
 */
public class Bundle {
    private final Map<String, Object> values = new HashMap<>();

    public Bundle() {}

    public Bundle(Bundle other) {
        if (other != null) values.putAll(other.values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public void clear() {
        values.clear();
    }

    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public void remove(String key) {
        values.remove(key);
    }

    public void putAll(Bundle other) {
        if (other != null) values.putAll(other.values);
    }

    public void putString(String key, String value) {
        values.put(key, value);
    }

    public void putInt(String key, int value) {
        values.put(key, value);
    }

    public void putLong(String key, long value) {
        values.put(key, value);
    }

    public void putFloat(String key, float value) {
        values.put(key, value);
    }

    public void putDouble(String key, double value) {
        values.put(key, value);
    }

    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    public void putStringArrayList(String key, ArrayList<String> value) {
        values.put(key, value);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Integer ? (Integer) value : defaultValue;
    }

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        Object value = values.get(key);
        return value instanceof Long ? (Long) value : defaultValue;
    }

    public float getFloat(String key) {
        return getFloat(key, 0f);
    }

    public float getFloat(String key, float defaultValue) {
        Object value = values.get(key);
        return value instanceof Float ? (Float) value : defaultValue;
    }

    public double getDouble(String key) {
        return getDouble(key, 0d);
    }

    public double getDouble(String key, double defaultValue) {
        Object value = values.get(key);
        return value instanceof Double ? (Double) value : defaultValue;
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    public ArrayList<String> getStringArrayList(String key) {
        Object value = values.get(key);
        if (!(value instanceof ArrayList)) return null;
        @SuppressWarnings("unchecked")
        ArrayList<String> list = (ArrayList<String>) value;
        return list;
    }
}
