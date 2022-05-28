package com.example.testapp.components.message.list.viewmodel

import androidx.lifecycle.LifecycleOwner
import com.example.testapp.components.message.list.DeletedMessageListItemPredicate
import com.example.testapp.components.message.list.MessageListView
import com.example.testapp.components.message.list.state.DeletedMessageVisibility
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.MuteUser
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.BlockUser
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.UnmuteUser
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.EndRegionReached
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.LastMessageRead
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.RetryMessage
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.DeleteMessage
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.ReplyMessage
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event.DownloadAttachment
import com.example.testapp.livedata.utils.EventObserver

/**
 * Binds [MessageListView] with [MessageListViewModel], updating the view's state
 * based on data provided by the ViewModel, and forwarding View events to the ViewModel.
 *
 * This function sets listeners on the view and ViewModel. Call this method
 * before setting any additional listeners on these objects yourself.
 */
@JvmName("bind")
public fun MessageListViewModel.bindView(view: MessageListView, lifecycleOwner: LifecycleOwner) {

    view.deletedMessageListItemPredicateLiveData.observe(lifecycleOwner) { messageListItemPredicate ->
        if (messageListItemPredicate != null) {
            val deletedMessageVisibility = when (messageListItemPredicate) {
                DeletedMessageListItemPredicate.NotVisibleToAnyone ->
                    DeletedMessageVisibility.ALWAYS_HIDDEN
                DeletedMessageListItemPredicate.VisibleToAuthorOnly ->
                    DeletedMessageVisibility.VISIBLE_FOR_CURRENT_USER
                else -> DeletedMessageVisibility.ALWAYS_VISIBLE
            }

            setDeletedMessageVisibility(deletedMessageVisibility)
        }
    }

    channel.observe(lifecycleOwner) {
        view.init(it)
    }
    view.setEndRegionReachedHandler { onEvent(EndRegionReached) }
    view.setLastMessageReadHandler { onEvent(LastMessageRead) }
    view.setMessageDeleteHandler { onEvent(DeleteMessage(it, hard = false)) }
//    view.setThreadStartHandler { onEvent(ThreadModeEntered(it)) }
//    view.setMessageFlagHandler { onEvent(FlagMessage(it, view::handleFlagMessageResult)) }
    view.setMessagePinHandler { onEvent(MessageListViewModel.Event.PinMessage(it)) }
    view.setMessageUnpinHandler { onEvent(MessageListViewModel.Event.UnpinMessage(it)) }
//    view.setGiphySendHandler { message, giphyAction ->
//        onEvent(GiphyActionSelected(message, giphyAction))
//    }
    view.setMessageRetryHandler { onEvent(RetryMessage(it)) }
//    view.setMessageReactionHandler { message, reactionType ->
//        onEvent(MessageReaction(message, reactionType, enforceUnique = true))
//    }
    view.setUserMuteHandler { onEvent(MuteUser(it)) }
    view.setUserUnmuteHandler { onEvent(UnmuteUser(it)) }
    view.setUserBlockHandler { user, cid -> onEvent(BlockUser(user, cid)) }
    view.setMessageReplyHandler { cid, message -> onEvent(ReplyMessage(cid, message)) }
    view.setAttachmentDownloadHandler { downloadAttachmentCall -> onEvent(DownloadAttachment(downloadAttachmentCall)) }
    view.setReplyMessageClickListener { messageId -> onEvent(MessageListViewModel.Event.ShowMessage(messageId)) }

    state.observe(lifecycleOwner) { state ->
        when (state) {
            is MessageListViewModel.State.Loading -> {
                view.hideEmptyStateView()
                view.showLoadingView()
            }
            is MessageListViewModel.State.Result -> {
                if (state.messageListItem.items.isEmpty()) {
                    view.showEmptyStateView()
                } else {
                    view.hideEmptyStateView()
                }
                view.displayNewMessages(state.messageListItem)
                view.hideLoadingView()
            }
            MessageListViewModel.State.NavigateUp -> Unit // Not handled here
        }
    }
    loadMoreLiveData.observe(lifecycleOwner, view::setLoadingMore)
    targetMessage.observe(lifecycleOwner, view::scrollToMessage)

//    view.setAttachmentReplyOptionClickHandler { result ->
//        onEvent(MessageListViewModel.Event.ReplyAttachment(result.cid, result.messageId))
//    }
//    view.setAttachmentShowInChatOptionClickHandler { result ->
//        onEvent(MessageListViewModel.Event.ShowMessage(result.messageId))
//    }
//    view.setAttachmentDeleteOptionClickHandler { result ->
//        onEvent(
//            MessageListViewModel.Event.RemoveAttachment(
//                result.messageId,
//                result.toAttachment()
//            )
//        )
//    }
    errorEvents.observe(
        lifecycleOwner,
        EventObserver {
            view.showError(it)
        }
    )
}
