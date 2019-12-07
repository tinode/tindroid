package co.tinode.tindroid.mention.mentions;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Mentions class contains a Builder through which you could set the text highlight color,
 * query listener or add mentions. Pass in an {@link EditText} view to the builder and the library
 * will setup the ability to '@' mention.
 */
@SuppressWarnings("WeakerAccess")
public class Mentions {

    /**
     * {@link Context}.
     */
    protected final Context context;

    /**
     * {@link EditText} to setup @ mentions.
     */
    protected final EditText editText;

    /**
     * Notifies client of queries determined to be valid by {@link MentionCheckerLogic}.
     */
    protected QueryListener queryListener;

    /**
     * Notifies client when to display and hide a suggestions drop down.
     */
    protected SuggestionsListener suggestionsListener;

    /**
     * Helper class that determines whether a query after @ is valid or not.
     */
    protected final MentionCheckerLogic mentionCheckerLogic;

    /**
     * Helper class for inserting and highlighting mentions.
     */
    protected final MentionInsertionLogic mentionInsertionLogic;

    /**
     * Pass in your {@link EditText} to give it the ability to @ mention.
     *
     * @param context  Context     Although not used in the library, it passed for future use.
     * @param editText EditText    The EditText that will have @ mention capability.
     */
    private Mentions(final Context context, final EditText editText) {
        this.context = context;
        this.editText = editText;

        // instantiate helper classes
        this.mentionCheckerLogic = new MentionCheckerLogic(editText);
        this.mentionInsertionLogic = new MentionInsertionLogic(editText);
    }

    /**
     * This Builder allows you to configure mentions by setting up a highlight color, max
     * number of words to search or by pre-populating mentions.
     */
    public static class Builder {

        /**
         * Mention instance.
         */
        private final Mentions mentionsLib;

        /**
         * Builder which allows you configure mentions. A {@link Context} and {@link EditText} is
         * required by the Builder.
         *
         * @param context  Context     Context
         * @param editText EditText    The {@link EditText} view to which you want to add the
         *                 ability to mention.
         */
        public Builder(final Context context, final EditText editText) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            } else if (editText == null) {
                throw new IllegalArgumentException("EditText must not be null.");
            }
            this.mentionsLib = new Mentions(context, editText);
        }

        /**
         * The {@link EditText} may have some text and mentions already in it. This method is used
         * to pre-populate the existing {@link Mentionable}s and highlight them.
         *
         * @param mentions List<Mentionable>   An array of {@link Mentionable}s that are
         *                 currently in the {@link EditText}.
         */
        public Builder addMentions(final List<? extends Mentionable> mentions) {
            mentionsLib.mentionInsertionLogic.addMentions(mentions);
            return this;
        }

        /**
         * Set a color to highlight the mentions' text. The default color is orange.
         *
         * @param color int     The color to use to highlight a {@link Mentionable}'s text.
         */
        public Builder highlightColor(final int color) {
            mentionsLib.mentionInsertionLogic.setTextHighlightColor(color);
            return this;
        }

        /**
         * Set the maximum number of characters the user may have typed until the search text
         * is invalid.
         *
         * @param maxCharacters int     The number of characters within which anything typed
         *                      after the '@' symbol will be evaluated.
         */
        public Builder maxCharacters(final int maxCharacters) {
            mentionsLib.mentionCheckerLogic.setMaxCharacters(maxCharacters);
            return this;
        }

        /**
         * Set a listener that will provide you with a valid token.
         *
         * @param queryListener QueryListener   The listener to set to be notified of a valid
         *                      query.
         */
        public Builder queryListener(final QueryListener queryListener) {
            mentionsLib.queryListener = queryListener;
            return this;
        }

        /**
         * Set a listener to notify you whether you should hide or display a drop down with
         * {@link Mentionable}s.
         *
         * @param suggestionsListener SuggestionsListener     The listener for display
         *                            suggestions.
         */
        public Builder suggestionsListener(final SuggestionsListener suggestionsListener) {
            mentionsLib.suggestionsListener = suggestionsListener;
            return this;
        }

        /**
         * Builds and returns a {@link Mentions} object.
         */
        public Mentions build() {
            mentionsLib.hookupInternalTextWatcher();
            mentionsLib.hookupOnClickListener();
            return mentionsLib;
        }

    }

    /**
     * You may be pre-loading text with {@link Mentionable}s. In order to highlight and make those
     * {@link Mentionable}s recognizable by the library, you may add them by using this method.
     *
     * @param mentionables List<? extends Mentionable>  Any pre-existing mentions that you
     *                     want to add to the library.
     */
    public void addMentions(final List<? extends Mentionable> mentionables) {
        mentionInsertionLogic.addMentions(mentionables);
    }

    /**
     * Returns an array with all the inserted {@link Mentionable}s in the {@link EditText}.
     *
     * @return List<Mentionable>    An array containing all the inserted {@link Mentionable}s.
     */
    public List<Mentionable> getInsertedMentions() {
        return new ArrayList<>(mentionInsertionLogic.getMentions());
    }

    /**
     * If the user sets the cursor after an '@', then perform a mention.
     */
    private void hookupOnClickListener() {
        editText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mentionCheckerLogic.currentWordStartsWithAtSign()) {
                    String query = mentionCheckerLogic.doMentionCheck();
                    queryReceived(query);
                } else {
                    suggestionsListener.displaySuggestions(false);
                }
            }
        });
    }

    /**
     * Set a {@link TextWatcher} for mentions.
     */
    private void hookupInternalTextWatcher() {
        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                mentionInsertionLogic.checkIfProgrammaticallyClearedEditText(charSequence, start,
                        count, after);
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                mentionInsertionLogic.updateInternalMentionsArray(start, before, count);
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = mentionCheckerLogic.doMentionCheck();
                queryReceived(query);
            }
        });
    }

    /**
     * Insert a mention the user has chosen in the {@link EditText} and notify the user
     * to hide the suggestions list.
     *
     * @param mentionable Mentionable     A {@link Mentionable} chosen by the user to display
     *                    and highlight in the {@link EditText}.
     */
    public void insertMention(final Mentionable mentionable) {
        mentionInsertionLogic.insertMention(mentionable);
        suggestionsListener.displaySuggestions(false);
    }

    /**
     * If the user typed a query that was valid then return it. Otherwise, notify you to close
     * a suggestions list.
     *
     * @param query String      A valid query.
     */
    public void queryReceived(final String query) {
        if (queryListener != null && StringUtils.isNotBlank(query)) {
            queryListener.onQueryReceived(query);
        } else {
            suggestionsListener.displaySuggestions(false);
        }
    }

}
