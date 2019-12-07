package co.tinode.tinodesdk.model;

/*
 * Interface that allows merging.
 */
public interface Mergeable {
    // Merges this with |another|.
    // Returns the total number of modified fields.
    int merge(Mergeable another);
}
