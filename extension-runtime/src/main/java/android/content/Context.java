package android.content;

import android.content.res.AssetManager;
import android.content.res.Resources;
import java.io.File;

/**
 * Minimal dareader-owned Context stub. Upstream Suwayomi ships the full AOSP
 * Context (plus Robolectric shadows); we only model what extensions touch:
 * scoped SharedPreferences plus cache/files dirs, app context, system
 * services, and resources/assets.
 *
 * <p>Directory methods are concrete (not abstract) so out-of-tree Context
 * subclasses keep linking; the default backing is XDG-based
 * ({@code ~/.cache/dareader} for cache, app data dir for files).
 */
public abstract class Context {
    // File-mode constants (standard Android values).
    public static final int MODE_PRIVATE = 0;
    public static final int MODE_WORLD_READABLE = 1;
    public static final int MODE_WORLD_WRITEABLE = 2;
    public static final int MODE_MULTI_PROCESS = 4;
    public static final int MODE_ENABLE_WRITE_AHEAD_LOGGING = 8;
    public static final int MODE_NO_LOCALIZED_COLLATORS = 16;
    public static final int MODE_APPEND = 32768;

    // Service-name constants (standard Android values). getSystemService
    // always returns null on the JVM, but the fields must link for extensions
    // that pass Context.X_SERVICE.
    public static final String LAYOUT_INFLATER_SERVICE = "layout_inflater";
    public static final String ACTIVITY_SERVICE = "activity";
    public static final String WINDOW_SERVICE = "window";
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String ALARM_SERVICE = "alarm";
    public static final String POWER_SERVICE = "power";
    public static final String DOWNLOAD_SERVICE = "download";
    public static final String INPUT_METHOD_SERVICE = "input_method";
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final String WIFI_SERVICE = "wifi";
    public static final String AUDIO_SERVICE = "audio";
    public static final String TELEPHONY_SERVICE = "phone";
    public static final String LOCATION_SERVICE = "location";
    public static final String VIBRATOR_SERVICE = "vibrator";
    public static final String SENSOR_SERVICE = "sensor";
    public static final String STORAGE_SERVICE = "storage";
    public static final String UI_MODE_SERVICE = "uimode";
    public static final String USB_SERVICE = "usb";
    public static final String JOB_SCHEDULER_SERVICE = "jobscheduler";
    public static final String SEARCH_SERVICE = "search";

    public abstract SharedPreferences getSharedPreferences(String name, int mode);

    public File getCacheDir() {
        return dareader.ext.android.StoreContext.cacheDir();
    }

    public File getFilesDir() {
        return dareader.ext.android.StoreContext.filesDir();
    }

    public Context getApplicationContext() {
        return this;
    }

    /** AndroidCompat: there are no system services on the JVM; always null. */
    public Object getSystemService(String name) {
        return null;
    }

    /** AndroidCompat: there are no system services on the JVM; always null. */
    public <T> T getSystemService(Class<T> serviceClass) {
        return null;
    }

    public Resources getResources() {
        return new Resources();
    }

    public AssetManager getAssets() {
        return new AssetManager();
    }
}
