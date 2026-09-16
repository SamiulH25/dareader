package android.os;

/** Minimal dareader-owned SystemClock stub (monotonic ms, rate-limit use). */
public final class SystemClock {
    private SystemClock() {}

    public static long elapsedRealtime() {
        return System.nanoTime() / 1_000_000L;
    }
}
