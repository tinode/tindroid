package co.tinode.tindroid.mention.mentions;

import android.widget.EditText;

import org.apache.commons.lang3.StringUtils;

/**
 * Contains helper methods to determine if a search is valid.
 */
class MentionCheckerLogic {

    private final EditText editText;

    /* Default limit of 13 characters to evaluate after the '@' symbol. */
    @SuppressWarnings("WeakerAccess")
    protected int maxCharacters = 13;

    MentionCheckerLogic(final EditText editText) {
        this.editText = editText;
    }

    /**
     * A user may type an '@' and keep typing words without choosing a mention. This method is used
     * to prevent evaluating all the characters after @ for valid mentions. A default limit of
     * 13 character is set. However, you could configure it to any number of characters.
     *
     * @param maxCharacters int     The maximum number of characters to considered after the
     *                      '@' symbol as a query. The default is 13 characters.
     */
    void setMaxCharacters(final int maxCharacters) {
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("Maximum number of characters must be greater " +
                    "than 0.");
        }
        this.maxCharacters = maxCharacters;
    }

    /**
     * Takes the string typed by the user after the '@' symbol and checks the following rules:
     * 1. The length of the searched string is within the <code>maxCharacters</code> limit.
     * 2. An alphanumeric character is after the '@' symbol.
     * 3. If there are no characters before the '@' symbol.
     * <p/>
     * If these rules are satisfied, then the search text is valid.
     *
     * @return String   A valid query that satisfies the three rules above.
     */
    String doMentionCheck() {
        String queryToken = "";

        // perform a search if the {@link EditText} has an '@' symbol.
        if (StringUtils.contains(editText.getText(), "@")) {
            final int cursorPosition = editText.getSelectionStart();
            final String allTextBeforeCursor = editText.getText().toString().substring(0, cursorPosition);
            final String providedSearchText = StringUtils.substringAfterLast(allTextBeforeCursor, "@");

            // check search text is within <code>maxCharacters</code> and begins with a
            // alpha numeric char.
            if (searchIsWithinMaxChars(providedSearchText, maxCharacters)
                    && searchBeginsWithAlphaNumericChar(providedSearchText)) {

                final int atSymbolPosition = StringUtils.lastIndexOf(allTextBeforeCursor, "@");

                // check if search text is first in the view or has a space beforehand if there are
                // more characters in the view.
                if (atSymbolPosition == 0
                        || spaceBeforeAtSymbol(allTextBeforeCursor, atSymbolPosition)) {
                    queryToken = providedSearchText;
                }
            }
        }

        return queryToken;
    }

    /**
     * If there is text before the '@' symbol, then check if it a white space. This is to prevent
     * performing a mention when the user is typing an email.
     *
     * @param currentTextBeforeCursor String   This is all the text that has been typed into the
     *                                {@link EditText}, before the current cursor location.
     * @param atSymbolPosition        String   The location of the '@' symbol in the
     *                                {@link EditText}.
     * @return true or false
     */
    private boolean spaceBeforeAtSymbol(final String currentTextBeforeCursor, final int atSymbolPosition) {
        if (atSymbolPosition > 0) {
            final char charBeforeAtSymbol = currentTextBeforeCursor.charAt(atSymbolPosition - 1);
            if (Character.isWhitespace(charBeforeAtSymbol)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the number of characters after the '@' symbol is within <code>maxCharacters</code>.
     *
     * @param providedSearchText String  The text after the '@' symbol entered by the user.
     * @param maxCharacters      int     The maximum number of characters that should be used
     *                           as a search query. The default is 13 characters, but this
     *                           value is configurable.
     * @return true or false
     */
    private boolean searchIsWithinMaxChars(final String providedSearchText, final int maxCharacters) {
        return (providedSearchText.length() >= 1 && providedSearchText.length() <= maxCharacters);
    }

    /**
     * Checks if the starting character in the search text is a letter or digit.
     *
     * @param providedSearchText String  The text after the '@' symbol entered by the user.
     * @return true or false
     */
    private boolean searchBeginsWithAlphaNumericChar(final String providedSearchText) {
        return Character.isLetterOrDigit(providedSearchText.charAt(0));
    }

    /**
     * Return true if the position of the cursor in {@link EditText} is on a word that
     * starts with an '@' sign.
     *
     * @return true or false
     */
    boolean currentWordStartsWithAtSign() {
        final int start = editText.getSelectionStart();
        final int end = editText.getSelectionEnd();

        if (start == end) {
            //Multiple text is not highlighted
            if (editText.length() >= start) {
                String text = editText.getText().toString().substring(0, start);
                text = StringUtils.substringAfterLast(text, " ");
                if (StringUtils.startsWith(text, "@")) {
                    return true;
                }
            }
        }
        return false;
    }
}
