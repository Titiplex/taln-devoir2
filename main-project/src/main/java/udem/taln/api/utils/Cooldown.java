package udem.taln.api.utils;

public final class Cooldown {
    private int strikes = 0;
    private final int maxStrikes;
    private final long cooldownMs;

    public Cooldown(int maxStrikes, long cooldownMs) {
        this.maxStrikes = Math.max(1, maxStrikes);
        this.cooldownMs = cooldownMs;
    }

    public synchronized void on429() {
        strikes++;
        if (strikes >= maxStrikes) {
            try {
                Thread.sleep(cooldownMs);
            } catch (InterruptedException ignored) {
            }
            strikes = 0;
        }
    }

    public synchronized void reset() {
        strikes = 0;
    }
}
