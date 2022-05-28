package com.example.testapp.components.message.list.adapter

import com.example.testapp.common.utils.ListenerDelegate
import com.example.testapp.components.message.list.MessageListView
//import com.example.testapp.components.message.list.MessageListView.AttachmentClickListener
//import com.example.testapp.components.message.list.MessageListView.AttachmentDownloadClickListener
//import com.example.testapp.components.message.list.MessageListView.GiphySendListener
//import com.example.testapp.components.message.list.MessageListView.ReactionViewClickListener
import com.example.testapp.components.message.list.MessageListView.LinkClickListener
import com.example.testapp.components.message.list.MessageListView.MessageClickListener
import com.example.testapp.components.message.list.MessageListView.MessageLongClickListener
import com.example.testapp.components.message.list.MessageListView.MessageRetryListener
import com.example.testapp.components.message.list.MessageListView.UserClickListener


internal class MessageListListenerContainerImpl(
    messageClickListener: MessageClickListener = MessageClickListener(EmptyFunctions.ONE_PARAM),
    messageLongClickListener: MessageLongClickListener = MessageLongClickListener(EmptyFunctions.ONE_PARAM),
    messageRetryListener: MessageRetryListener = MessageRetryListener(EmptyFunctions.ONE_PARAM),
//    threadClickListener: ThreadClickListener = ThreadClickListener(EmptyFunctions.ONE_PARAM),
//    attachmentClickListener: AttachmentClickListener = AttachmentClickListener(EmptyFunctions.TWO_PARAM),
//    attachmentDownloadClickListener: AttachmentDownloadClickListener = AttachmentDownloadClickListener(EmptyFunctions.ONE_PARAM),
//    reactionViewClickListener: ReactionViewClickListener = ReactionViewClickListener(EmptyFunctions.ONE_PARAM),
    userClickListener: UserClickListener = UserClickListener(EmptyFunctions.ONE_PARAM),
//    giphySendListener: GiphySendListener = GiphySendListener(EmptyFunctions.TWO_PARAM),
    linkClickListener: LinkClickListener = LinkClickListener(EmptyFunctions.ONE_PARAM),
) : MessageListListenerContainer {
    private object EmptyFunctions {
        val ONE_PARAM: (Any) -> Unit = { _ -> Unit }
        val TWO_PARAM: (Any, Any) -> Unit = { _, _ -> Unit }
    }

    override var messageClickListener: MessageClickListener by ListenerDelegate(
        messageClickListener
    ) { realListener ->
        MessageClickListener { message ->
            realListener().onMessageClick(message)
        }
    }

    override var messageLongClickListener: MessageLongClickListener by ListenerDelegate(
        messageLongClickListener
    ) { realListener ->
        MessageLongClickListener { message ->
            realListener().onMessageLongClick(message)
        }
    }

    override var messageRetryListener: MessageRetryListener by ListenerDelegate(
        messageRetryListener
    ) { realListener ->
        MessageRetryListener { message ->
            realListener().onRetryMessage(message)
        }
    }

//    override var threadClickListener: ThreadClickListener by ListenerDelegate(
//        threadClickListener
//    ) { realListener ->
//        ThreadClickListener { message ->
//            realListener().onThreadClick(message)
//        }
//    }
//
//    override var attachmentClickListener: AttachmentClickListener by ListenerDelegate(
//        attachmentClickListener
//    ) { realListener ->
//        AttachmentClickListener { message, attachment ->
//            realListener().onAttachmentClick(message, attachment)
//        }
//    }
//
//    override var attachmentDownloadClickListener: AttachmentDownloadClickListener by ListenerDelegate(
//        attachmentDownloadClickListener
//    ) { realListener ->
//        AttachmentDownloadClickListener { attachment ->
//            realListener().onAttachmentDownloadClick(attachment)
//        }
//    }

//    override var reactionViewClickListener: ReactionViewClickListener by ListenerDelegate(
//        reactionViewClickListener
//    ) { realListener ->
//        ReactionViewClickListener { message ->
//            realListener().onReactionViewClick(message)
//        }
//    }

    override var userClickListener: UserClickListener by ListenerDelegate(
        userClickListener
    ) { realListener ->
        UserClickListener { user ->
            realListener().onUserClick(user)
        }
    }

//    override var giphySendListener: GiphySendListener by ListenerDelegate(
//        giphySendListener
//    ) { realListener ->
//        GiphySendListener { message, action ->
//            realListener().onGiphySend(message, action)
//        }
//    }

    override var linkClickListener: LinkClickListener by ListenerDelegate(
        linkClickListener
    ) { realListener ->
        LinkClickListener { url ->
            realListener().onLinkClick(url)
        }
    }
}
