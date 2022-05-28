package com.example.testapp.components.transformer

import android.text.util.Linkify
import android.widget.TextView
import com.example.testapp.components.message.list.adapter.MessageListItem

/**
 * AutoLinkable implementation of [ChatMessageTextTransformer] that makes [TextView] links clickable after applying the transformer.
 *
 * By default our SDK text views don't have `android:autoLink`.
 */
public class AutoLinkableTextTransformer(public val transformer: (textView: TextView, messageItem: MessageListItem.MessageItem) -> Unit) :
    ChatMessageTextTransformer {

    override fun transformAndApply(textView: TextView, messageItem: MessageListItem.MessageItem) {
        transformer(textView, messageItem)
        Linkify.addLinks(textView, Linkify.ALL)
    }
}
