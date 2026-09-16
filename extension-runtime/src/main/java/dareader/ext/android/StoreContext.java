package dareader.ext.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Named file-backed SharedPreferences stores backing the Application stub. */
public class StoreContext extends Context {
    private final Map<String, SharedPreferences> stores = new ConcurrentHashMap<>();

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return stores.computeIfAbsent(name, JvmSharedPreferences::new);
    }

    /** Shared cache backing: {@code $XDG_CACHE_HOME/dareader}, else {@code ~/.cache/dareader}. */
    public static File cacheDir() {
        String xdg = System.getenv("XDG_CACHE_HOME");
        File base = (xdg != null && !xdg.isEmpty())
            ? new File(xdg)
            : new File(System.getProperty("user.home"), ".cache");
        File dir = new File(base, "dareader");
        dir.mkdirs();
        return dir;
    }

    /** Shared app-files backing: {@code $XDG_DATA_HOME/dareader}, else {@code ~/.local/share/dareader}. */
    public static File filesDir() {
        String xdg = System.getenv("XDG_DATA_HOME");
        File base = (xdg != null && !xdg.isEmpty())
            ? new File(xdg)
            : new File(new File(System.getProperty("user.home"), ".local"), "share");
        File dir = new File(base, "dareader");
        dir.mkdirs();
        return dir;
    }
}
