package co.tinode.tinodesdk;

import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * A very simple thanable promise. It has no facility for execution. It can only be
 * resolved/rejected externally by calling resolve/reject. Once resolved/rejected it will call
 * listener's onSuccess/onFailure. Depending on results returned or thrown by the handler, it will
 * update the next promise in chain: will either resolve/reject it immediately, or make it
 * resolve/reject together with the promise returned by the handler.
 * <p>
 * Usage:
 * <p>
 * Create a PromisedReply P1, assign onSuccess/onFailure listeners by calling thenApply. thenApply returns
 * another P2 promise (mNextPromise), which can then be assigned its own listeners.
 * <p>
 * Alternatively, one can use a blocking call getResult. It will block until the promise is either
 * resolved or rejected.
 * <p>
 * The promise can be created in either WAITING or RESOLVED state by using an appropriate constructor.
 * <p>
 * The onSuccess/onFailure handlers will be called:
 * <p>
 * a. Called at the time of resolution when P1 is resolved through P1.resolve(T) if at the time of
 * calling thenApply the promise is in WAITING state,
 * b. Called immediately on thenApply if at the time of calling thenApply the promise is already
 * in RESOLVED or REJECTED state,
 * <p>
 * thenApply creates and returns a promise P2 which will be resolved/rejected in the following
 * manner:
 * <p>
 * A. If P1 is resolved:
 * 1. If P1.onSuccess returns a resolved promise P3, P2 is resolved immediately on
 * return from onSuccess using the result from P3.
 * 2. If P1.onSuccess returns a rejected promise P3, P2 is rejected immediately on
 * return from onSuccess using the throwable from P3.
 * 2. If P1.onSuccess returns null, P2 is resolved immediately using result from P1.
 * 3. If P1.onSuccess returns an unresolved promise P3, P2 is resolved together with P3.
 * 4. If P1.onSuccess throws an exception, P2 is rejected immediately on catching the exception.
 * 5. If P1.onSuccess is null, P2 is resolved immediately using result from P1.
 * <p>
 * B. If P1 is rejected:
 * 1. If P1.onFailure returns a resolved promise P3, P2 is resolved immediately on return from
 * onFailure using the result from P3.
 * 2. If P1.onFailure returns null, P2 is resolved immediately using null as a result.
 * 3. If P1.onFailure returns an unresolved promise P3, P2 is resolved together with P3.
 * 4. If P1.onFailure throws an exception, P2 is rejected immediately on catching the exception.
 * 5. If P1.onFailure is null, P2 is rejected immediately using the throwable from P1.
 * 5.1 If P2.onFailure is null, and P2.mNextPromise is null, an exception is re-thrown.
 *
 */
public class PromisedReply<T> {
    private static final String TAG = "PromisedReply";

    private enum State {WAITING, RESOLVED, REJECTED}

    private T mResult = null;
    private Exception mException = null;

    private volatile State mState = State.WAITING;

    private SuccessListener<T> mSuccess = null;
    private FailureListener<T> mFailure = null;

    private PromisedReply<T> mNextPromise = null;

    private final CountDownLatch mDoneSignal;

    /**
     * Create promise in a WAITING state.
     */
    public PromisedReply() {
        mDoneSignal = new CountDownLatch(1);
    }

    /**
     * Create a promise in a RESOLVED state
     *
     * @param result result used for resolution of the promise.
     */
    public PromisedReply(T result) {
        mResult = result;
        mState = State.RESOLVED;
        mDoneSignal = new CountDownLatch(0);
    }

    /**
     * Create a promise in a REJECTED state
     *
     * @param err Exception used for rejecting the promise.
     */
    public <E extends Exception> PromisedReply(E err) {
        mException = err;
        mState = State.REJECTED;
        mDoneSignal = new CountDownLatch(0);
    }

    /**
     * Returns a new PromisedReply that is completed when all of the given PromisedReply complete.
     * It is rejected if any one is rejected. If resolved, the result is an array or values returned by each input promise.
     * If rejected, the result if the exception which rejected one of the input promises.
     *
     * @param waitFor promises to wait for.
     * @return PromisedReply which is resolved when all inputs are resolved or rejected when any one is rejected.
     */
    public static <T> PromisedReply<T[]> allOf(PromisedReply<T>[] waitFor) {
        final PromisedReply<T[]> done = new PromisedReply<>();
        // Create a separate thread and wait for all promises to resolve.
        new Thread(() -> {
            for (PromisedReply p : waitFor) {
                if (p != null) {
                    try {
                        p.mDoneSignal.await();
                        if (p.mState == State.REJECTED) {
                            done.reject(p.mException);
                        }
                    } catch (InterruptedException ex) {
                        try {
                            done.reject(ex);
                        } catch (Exception ignored) {}
                        return;
                    } catch (Exception ignored) {
                        return;
                    }
                }
            }

            ArrayList<T> result = new ArrayList<>();
            for (PromisedReply<T> p : waitFor) {
                if (p != null) {
                    result.add(p.mResult);
                } else {
                    result.add(null);
                }
            }

            // If it throws then nothing we can do about it.
            try {
                // noinspection unchecked
                done.resolve((T[]) result.toArray());
            } catch (Exception ignored) {}
        }).start();
        return done;
    }

    /**
     * Call SuccessListener.onSuccess or FailureListener.onFailure when the
     * promise is resolved or rejected. The call will happen on the thread which
     * called resolve() or reject().
     *
     * @param success called when the promise is resolved
     * @param failure called when the promise is rejected
     * @return promise for chaining
     */
    public PromisedReply<T> thenApply(SuccessListener<T> success, FailureListener<T> failure) {
        synchronized (this) {

            if (mNextPromise != null) {
                throw new IllegalStateException("Multiple calls to thenApply are not supported");
            }

            mSuccess = success;
            mFailure = failure;
            mNextPromise = new PromisedReply<>();
            try {
                switch (mState) {
                    case RESOLVED:
                        callOnSuccess(mResult);
                        break;

                    case REJECTED:
                        callOnFailure(mException);
                        break;

                    case WAITING:
                        break;
                }
            } catch (Exception e) {
                mNextPromise = new PromisedReply<>(e);
            }

            return mNextPromise;
        }
    }

    /**
     * Calls SuccessListener.onSuccess when the promise is resolved. The call will happen on the
     * thread which called resolve().
     *
     * @param success called when the promise is resolved
     * @return promise for chaining
     */
    public PromisedReply<T> thenApply(SuccessListener<T> success) {
        return thenApply(success, null);
    }

    /**
     * Call onFailure when the promise is rejected. The call will happen on the
     * thread which called reject()
     *
     * @param failure called when the promise is rejected
     * @return promise for chaining
     */
    public PromisedReply<T> thenCatch(FailureListener<T> failure) {
        return thenApply(null, failure);
    }

    /**
     * Call FinalListener.onFinally when the promise is completed. The call will happen on the
     * thread which completed the promise: called either resolve() or reject().
     *
     * @param finished called when the promise is completed either way.
     */
    public void thenFinally(final FinalListener finished) {
        thenApply(new SuccessListener<>() {
            @Override
            public PromisedReply<T> onSuccess(T result) {
                finished.onFinally();
                return null;
            }
        }, new FailureListener<>() {
            @Override
            public <E extends Exception> PromisedReply<T> onFailure(E err) {
                finished.onFinally();
                return null;
            }
        });
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isResolved() {
        return mState == State.RESOLVED;
    }

    @SuppressWarnings("unused")
    public boolean isRejected() {
        return mState == State.REJECTED;
    }

    @SuppressWarnings({"WeakerAccess"})
    public boolean isDone() {
        return mState == State.RESOLVED || mState == State.REJECTED;
    }


    /**
     * Make this promise resolved.
     *
     * @param result results of resolution.
     * @throws Exception if anything goes wrong during resolution.
     */
    public void resolve(final T result) throws Exception {
        synchronized (this) {
            if (mState == State.WAITING) {
                mState = State.RESOLVED;

                mResult = result;
                try {
                    callOnSuccess(result);
                } finally {
                    mDoneSignal.countDown();
                }
            } else {
                mDoneSignal.countDown();
                throw new IllegalStateException("Promise is already completed");
            }
        }
    }

    /**
     * Make this promise rejected.
     *
     * @param err reason for rejecting this promise.
     * @throws Exception if anything goes wrong during rejection.
     */
    public void reject(final Exception err) throws Exception {
        synchronized (this) {
            if (mState == State.WAITING) {
                mState = State.REJECTED;

                mException = err;
                try {
                    callOnFailure(err);
                } finally {
                    mDoneSignal.countDown();
                }
            } else {
                mDoneSignal.countDown();
                throw new IllegalStateException("Promise is already completed");
            }
        }
    }

    /**
     * Wait for promise resolution.
     *
     * @return true if the promise was resolved, false otherwise
     * @throws InterruptedException if waiting was interrupted
     */
    public boolean waitResult() throws InterruptedException {
        // Wait for the promise to resolve
        mDoneSignal.await();
        return isResolved();
    }

    /**
     * A blocking call which returns the result of the execution. It will return
     * <b>after</b> thenApply is called. It can be safely called multiple times on
     * the same instance.
     *
     * @return result of the execution (what was passed to {@link #resolve(Object)})
     * @throws Exception if the promise was rejected or waiting was interrupted.
     */
    public T getResult() throws Exception {
        // Wait for the promise to resolve
        mDoneSignal.await();

        return switch (mState) {
            case RESOLVED -> mResult;
            case REJECTED -> throw mException;
            default -> throw new IllegalStateException("Promise cannot be in WAITING state");
        };

    }

    private void callOnSuccess(final T result) throws Exception {
        PromisedReply<T> ret;
        try {
            ret = (mSuccess != null ? mSuccess.onSuccess(result) : null);
        } catch (Exception e) {
            handleFailure(e);
            return;
        }
        // If it throws, let it fly.
        handleSuccess(ret);
    }

    private void callOnFailure(final Exception err) throws Exception {
        if (mFailure != null) {
            // Try to recover
            try {
                handleSuccess(mFailure.onFailure(err));
            } catch (Exception ex) {
                handleFailure(ex);
            }
        } else {
            // Pass to the next handler
            handleFailure(err);
        }
    }

    private void handleSuccess(PromisedReply<T> ret) throws Exception {
        if (mNextPromise == null) {
            if (ret != null && ret.mState == State.REJECTED) {
                throw ret.mException;
            }
            return;
        }

        if (ret == null) {
            mNextPromise.resolve(mResult);
        } else if (ret.mState == State.RESOLVED) {
            mNextPromise.resolve(ret.mResult);
        } else if (ret.mState == State.REJECTED) {
            mNextPromise.reject(ret.mException);
        } else {
            // Next promise will be called when ret is completed
            ret.insertNextPromise(mNextPromise);
        }
    }

    private void handleFailure(Exception e) throws Exception {
        if (mNextPromise != null) {
            mNextPromise.reject(e);
        } else {
            throw e;
        }
    }

    private void insertNextPromise(PromisedReply<T> next) {
        synchronized (this) {
            if (mNextPromise != null) {
                next.insertNextPromise(mNextPromise);
            }
            mNextPromise = next;
        }
    }

    public static abstract class SuccessListener<U> {
        /**
         * Callback to execute when the promise is successfully resolved.
         *
         * @param result result of the call.
         * @return new promise to pass to the next handler in the chain or null to use the same result.
         * @throws Exception thrown if handler want to call the next failure handler in chain.
         */
        public abstract PromisedReply<U> onSuccess(U result) throws Exception;
    }

    public static abstract class FailureListener<U> {
        /**
         * Callback to execute when the promise is rejected.
         *
         * @param err Exception which caused promise to fail.
         * @return new promise to pass to the next success handler in the chain.
         * @throws Exception thrown if handler want to call the next failure handler in chain.
         */
        public abstract <E extends Exception> PromisedReply<U> onFailure(E err) throws Exception;
    }

    public static abstract class FinalListener {
        public abstract void onFinally();
    }
}
