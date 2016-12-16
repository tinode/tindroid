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

    private Thread currentThread = null;

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
        boolean result;
        try {
            currentThread = Thread.currentThread();
            Thread.sleep(getNextDelay());
            result = true;
        } catch (InterruptedException e) {
            // Per Java spec calling it here even if it seems to be counterintuitive.
            currentThread.interrupt();
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

    public boolean wakeUp() {
        if (currentThread != null) {
            currentThread.interrupt();
        }
        return currentThread != null;
    }
}
