package com.example.testapp.components.message.list.adapter

import com.example.testapp.components.message.list.MessageListView.LinkClickListener
import com.example.testapp.components.message.list.MessageListView.MessageClickListener
import com.example.testapp.components.message.list.MessageListView.MessageLongClickListener
import com.example.testapp.components.message.list.MessageListView.MessageRetryListener
import com.example.testapp.components.message.list.MessageListView.UserClickListener
//import com.example.testapp.components.message.list.MessageListView.AttachmentClickListener
//import com.example.testapp.components.message.list.MessageListView.AttachmentDownloadClickListener
//import com.example.testapp.components.message.list.MessageListView.GiphySendListener
//import com.example.testapp.components.message.list.MessageListView.ThreadClickListener
//import com.example.testapp.components.message.list.MessageListView.ReactionViewClickListener

public sealed interface MessageListListenerContainer {
    public val messageClickListener: MessageClickListener
    public val messageLongClickListener: MessageLongClickListener
    public val messageRetryListener: MessageRetryListener
//    public val threadClickListener: ThreadClickListener
//    public val attachmentClickListener: AttachmentClickListener
//    public val attachmentDownloadClickListener: AttachmentDownloadClickListener
//    public val reactionViewClickListener: ReactionViewClickListener
    public val userClickListener: UserClickListener
//    public val giphySendListener: GiphySendListener
    public val linkClickListener: LinkClickListener
}
