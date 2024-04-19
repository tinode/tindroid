package co.tinode.tinodesdk;

import java.util.Random;

/**
 * Exponential backoff for reconnects.
 */
public class ExpBackoff {
    // Minimum delay = 1000ms, expected ~1500ms;
    private static final int BASE_SLEEP_MS = 1000;
    // Maximum delay 2^10 ~ 2000 seconds ~ 34 min.
    private static final int MAX_SHIFT = 10;

    private final Random random = new Random();
    private int attempt;

    @SuppressWarnings("WeakerAccess")
    public ExpBackoff() {
        this.attempt = 0;
    }

    private Thread currentThread = null;

    /**
     * Increment attempt counter and return time to sleep in milliseconds
     * @return time to sleep in milliseconds
     */
    @SuppressWarnings("WeakerAccess")
    public long getNextDelay() {
        if (attempt > MAX_SHIFT) {
            attempt = MAX_SHIFT;
        }

        long delay = (long) BASE_SLEEP_MS * (1L << attempt) + random.nextInt(BASE_SLEEP_MS * (1 << attempt));
        attempt++;

        return delay;
    }

    /**
     * Pause the current thread for the appropriate number of milliseconds.
     * This method cannot be synchronized!
     *
     * @return false if the sleep was interrupted, true otherwise
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    public boolean doSleep() {
        boolean result;
        try {
            currentThread = Thread.currentThread();
            Thread.sleep(getNextDelay());
            result = true;
        } catch (InterruptedException e) {
            result = false;
        } finally {
            currentThread = null;
        }
        return result;
    }

    public void reset() {
        this.attempt = 0;
    }

    public int getAttemptCount() {
        return attempt;
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    synchronized public boolean wakeUp() {
        reset();
        if (currentThread != null) {
            currentThread.interrupt();
            return true;
        }
        return false;
    }
}
