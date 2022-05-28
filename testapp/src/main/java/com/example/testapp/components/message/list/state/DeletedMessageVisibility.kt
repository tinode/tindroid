package com.example.testapp.components.message.list.state

/**
 * Intended to be used for regulating visibility of deleted messages
 * and filtering them out accordingly.
 */
public enum class DeletedMessageVisibility {

    /**
     * No deleted messages are visible.
     */
    ALWAYS_HIDDEN,

    /**
     * Deleted messages from the current user are visible,
     * ones from other users are not.
     */
    VISIBLE_FOR_CURRENT_USER,

    /**
     * All deleted messages are visible, regardless of the
     * user who authored them.
     */
    ALWAYS_VISIBLE
}
