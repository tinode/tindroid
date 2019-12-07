package co.tinode.tindroid.mention.mentions;

/**
 * Listener which informs you whether to show or hide a suggestions drop down.
 */
public interface SuggestionsListener {

    /**
     * Informs to either display or hide a suggestions drop down.
     *
     * @param display true if you should display a suggestions drop down or false if it should be
     *                hidden.
     */
    void displaySuggestions(final boolean display);
}
