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
    public static final int MODE_PRIVATE = 0;

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

    public Object getSystemService(String name) {
        return null;
    }

    public Resources getResources() {
        return new Resources();
    }

    public AssetManager getAssets() {
        return new AssetManager();
    }
}
