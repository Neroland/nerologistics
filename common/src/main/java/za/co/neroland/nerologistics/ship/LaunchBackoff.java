package za.co.neroland.nerologistics.ship;

/**
 * Exponential retry backoff for a port whose launch was denied: the {@code level}-th consecutive denial
 * waits {@code base × 2^level} ticks, capped at {@code max}. Pure maths, unit-tested.
 */
public final class LaunchBackoff {

    /** Level beyond which the doubling cannot grow the delay any further (2^30 overflows int). */
    public static final int MAX_LEVEL = 30;

    private LaunchBackoff() {
    }

    /** Ticks to wait before the next attempt after the {@code level}-th consecutive denial (0-based). */
    public static long delayTicks(int level, int baseTicks, int maxTicks) {
        long base = Math.max(1, baseTicks);
        long max = Math.max(base, maxTicks);
        int shift = Math.max(0, Math.min(MAX_LEVEL, level));
        long delay = base << shift;
        return Math.min(max, delay);
    }

    /** The next level after a denial, saturating so the stored value never overflows. */
    public static int nextLevel(int level) {
        return Math.min(MAX_LEVEL, Math.max(0, level) + 1);
    }
}
