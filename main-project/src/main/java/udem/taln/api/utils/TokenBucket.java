package udem.taln.api.utils;

public final class TokenBucket {
    private final long capacity;
    private final double refillPerNs;
    private double tokens;
    private long last;

    public TokenBucket(double permitsPerSecond, long capacity) {
        this.capacity = Math.max(1, capacity);
        this.refillPerNs = Math.max(permitsPerSecond, 0.0001) / 1_000_000_000d;
        this.tokens = this.capacity;
        this.last = System.nanoTime();
    }

    public synchronized void acquire() {
        long now = System.nanoTime();
        double delta = (now - last) * refillPerNs;
        tokens = Math.min(capacity, tokens + delta);
        if (tokens < 1.0) {
            long waitNs = (long) Math.ceil((1.0 - tokens) / refillPerNs);
            try {
                long ms = waitNs / 1_000_000L, ns = waitNs % 1_000_000L;
                Thread.sleep(ms, (int) ns);
            } catch (InterruptedException ignored) {
            }
            now = System.nanoTime();
            // refill after sleep
            delta = (now - last) * refillPerNs;
            tokens = Math.min(capacity, tokens + delta);
        }
        tokens -= 1.0;
        last = now;
    }
}
