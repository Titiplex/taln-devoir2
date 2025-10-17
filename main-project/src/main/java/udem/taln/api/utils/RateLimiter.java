package udem.taln.api.utils;

/**
 * Rate limiter to avoid mistral 429 errors.
 */
public final class RateLimiter {
    private final long intervalNs;
    private long nextAllowed;

    public RateLimiter(double permitsPerSecond) {
        this.intervalNs = (long) (1_000_000_000L / Math.max(permitsPerSecond, 0.0001));
        this.nextAllowed = System.nanoTime();
    }

    public synchronized void acquire() {
        long now = System.nanoTime();
        if (now < nextAllowed) {
            long sleepNs = nextAllowed - now;
            try {
                Thread.sleep(sleepNs / 1_000_000L, (int) (sleepNs % 1_000_000L));
            } catch (InterruptedException ignored) {
            }
            now = System.nanoTime();
        }
        nextAllowed = now + intervalNs;
    }
}
