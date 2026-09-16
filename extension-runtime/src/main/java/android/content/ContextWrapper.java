package android.content;

import android.content.res.AssetManager;
import android.content.res.Resources;
import java.io.File;

/** Minimal dareader-owned ContextWrapper; delegates to a base context. */
public class ContextWrapper extends Context {
    protected final Context base;

    public ContextWrapper(Context base) {
        this.base = base;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return base.getSharedPreferences(name, mode);
    }

    @Override
    public File getCacheDir() {
        return base.getCacheDir();
    }

    @Override
    public File getFilesDir() {
        return base.getFilesDir();
    }

    @Override
    public Context getApplicationContext() {
        return base.getApplicationContext();
    }

    @Override
    public Object getSystemService(String name) {
        return base.getSystemService(name);
    }

    @Override
    public Resources getResources() {
        return base.getResources();
    }

    @Override
    public AssetManager getAssets() {
        return base.getAssets();
    }

    public String getPackageName() {
        return "dareader";
    }

    public void startActivity(Intent intent) {}
}
