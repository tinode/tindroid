package co.tinode.tinodesdk.model;

/*
 * Interface that allows merging of objects.
 */
public interface Mergeable {
    // Merges this with |another|.
    // Returns the total number of modified fields.
    boolean merge(Mergeable another);
}
