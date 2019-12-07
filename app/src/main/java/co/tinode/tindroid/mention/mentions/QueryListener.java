package co.tinode.tindroid.mention.mentions;

/**
 * Listener to receive the valid query to use to search for mentions.
 */
public interface QueryListener {

    /**
     * Returns a valid search text that you could use to search for mentions that contain
     * the query.
     *
     * @param query String  A valid search text entered by the user after the '@' symbol.
     */
    void onQueryReceived(final String query);

}
