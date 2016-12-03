package co.tinode.tinodesdk;

import java.util.Random;

/**
 * Exponential backoff for reconnects.
 */
public class ExpBackoff {
    // Minimum delay = 1 second
    private static final long BASE_SLEEP_MS = 1000;
    // Maximum delay 2^11 = 2000 seconds
    private static final int MAX_SHIFT = 11;

    private final Random random = new Random();
    private int attempt;

    public ExpBackoff() {
        this.attempt = 0;
    }

    /**
     * Increment attempt counter and return time to sleep in milliseconds
     * @return time to sleep in milliseconds
     */
    public long getNextDelay() {
        if (attempt > MAX_SHIFT) {
            attempt = MAX_SHIFT;
        }

        long delay = BASE_SLEEP_MS * Math.max(1, random.nextInt(1 << attempt));
        attempt++;

        return delay;
    }

    /**
     * Pause the current thread for the appropriate number of milliseconds
     * @return false if the sleep was interrupted, true otherwise
     */
    public boolean doSleep() {
        try {
            Thread.sleep(getNextDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    public void reset() {
        this.attempt = 0;
    }

    public int getAttemptCount() {
        return attempt;
    }
}
