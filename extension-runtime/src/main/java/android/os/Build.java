package android.os;

/** Minimal dareader-owned Build stub. */
public final class Build {
    private Build() {}

    public static final String MANUFACTURER = "dareader";

    public static final String MODEL = "desktop";

    public static final class VERSION {
        private VERSION() {}

        /** Matches {@link #RELEASE}; extensions gate features on this. */
        public static final int SDK_INT = 34;

        public static final String RELEASE = "14";
    }
}
