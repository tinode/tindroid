package com.example.testapp.components.message.preview

import com.example.testapp.common.style.TextStyle

/**
 * Style for [MessagePreviewView] used by [MentionListView] and [SearchResultListView].
 *
 * @property messageSenderTextStyle Appearance for message sender text.
 * @property messageTextStyle Appearance for message text.
 * @property messageTimeTextStyle Appearance for message time text.
 */
public data class MessagePreviewStyle(
    val messageSenderTextStyle: TextStyle,
    val messageTextStyle: TextStyle,
    val messageTimeTextStyle: TextStyle,
)
