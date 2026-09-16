package android.util;

/** Minimal dareader-owned Log stub routing to stderr. */
public final class Log {
    private Log() {}

    public static int d(String tag, String msg) {
        System.err.println("D/" + tag + ": " + msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.err.println("I/" + tag + ": " + msg);
        return 0;
    }

    public static int v(String tag, String msg) {
        System.err.println("V/" + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        System.err.println("W/" + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.err.println("E/" + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        System.err.println("E/" + tag + ": " + msg + " " + tr);
        return 0;
    }

    public static int wtf(String tag, String msg) {
        System.err.println(tag + ": " + msg);
        return 0;
    }
}
