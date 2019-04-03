package co.tinode.tinodesdk;

/**
 * Exception thrown when certain non-idempotent operatons are already in progress, such as login.
 */
public class InProgressException extends IllegalStateException {
}
