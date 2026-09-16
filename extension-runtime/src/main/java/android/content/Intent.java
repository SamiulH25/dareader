package android.content;

/** Minimal dareader-owned Intent stub. */
public class Intent {
    public Intent() {}

    public Intent setAction(String action) {
        return this;
    }

    public Intent putExtra(String name, String value) {
        return this;
    }

    public android.net.Uri getData() {
        return null;
    }
}
