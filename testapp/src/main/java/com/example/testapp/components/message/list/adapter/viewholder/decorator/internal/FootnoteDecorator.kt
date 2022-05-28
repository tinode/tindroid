package com.example.testapp.components.message.list.adapter.viewholder.decorator.internal

import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import com.example.testapp.R
import com.example.testapp.client.utils.SyncStatus
import com.example.testapp.common.utils.DateFormatter
import com.example.testapp.common.utils.extensions.updateConstraints
import com.example.testapp.common.utils.formatTime
import com.example.testapp.components.common.extensions.getCreatedAtOrNull
import com.example.testapp.components.common.extensions.getUpdatedAtOrNull
import com.example.testapp.components.common.extensions.internal.setStartDrawable
import com.example.testapp.components.common.extensions.isDeleted
import com.example.testapp.components.message.list.DeletedMessageListItemPredicate
import com.example.testapp.components.message.list.MessageListItemStyle
import com.example.testapp.components.message.list.MessageListView
import com.example.testapp.components.message.list.MessageListViewStyle
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.view.internal.FootnoteView
import com.example.testapp.components.message.list.adapter.viewholder.internal.MessagePlainTextViewHolder

internal class FootnoteDecorator(
    private val dateFormatter: DateFormatter,
    private val isDirectMessage: () -> Boolean,
    private val listViewStyle: MessageListViewStyle,
    private val deletedMessageListItemPredicate: MessageListView.MessageListItemPredicate,
) : BaseDecorator() {

//    /**
//     * Decorates the footnote of the message containing custom attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateCustomAttachmentsMessage(
//        viewHolder: CustomAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupFootnote(
//        viewHolder.binding.footnote,
//        viewHolder.binding.root,
//        viewHolder.binding.threadGuideline,
//        viewHolder.binding.messageContainer,
//        data,
//    )

//    /**
//     * Decorates the footnote of the Giphy attachment.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyAttachmentMessage(
//        viewHolder: GiphyAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupFootnote(
//            viewHolder.binding.footnote,
//            viewHolder.binding.root,
//            viewHolder.binding.threadGuideline,
//            viewHolder.binding.messageContainer,
//            data,
//        )
//    }

//    /**
//     * Decorates the footnote of the message containing file attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateFileAttachmentsMessage(
//        viewHolder: FileAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupFootnote(
//            viewHolder.binding.footnote,
//            viewHolder.binding.root,
//            viewHolder.binding.threadGuideline,
//            viewHolder.binding.messageContainer,
//            data,
//        )
//    }

//    /**
//     * Decorates the footnote of the message containing image attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateImageAttachmentsMessage(
//        viewHolder: ImageAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupFootnote(
//        viewHolder.binding.footnote,
//        viewHolder.binding.root,
//        viewHolder.binding.threadGuideline,
//        viewHolder.binding.messageContainer,
//        data,
//    )

    /**
     * Decorates the footnote of the plain text message.
     *
     * @param viewHolder The holder to decorate.
     * @param data The item that holds all the information.
     */
    override fun decoratePlainTextMessage(
        viewHolder: MessagePlainTextViewHolder,
        data: MessageListItem.MessageItem,
    ) = setupFootnote(
        viewHolder.binding.footnote,
        viewHolder.binding.root,
        viewHolder.binding.threadGuideline,
        viewHolder.binding.messageContainer,
        data,
    )

//    /**
//     * Decorates the footnote of the ephemeral Giphy message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyMessage(
//        viewHolder: GiphyViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupSimpleFootnoteWithRootConstraints(
//            viewHolder.binding.footnote,
//            viewHolder.binding.root,
//            viewHolder.binding.cardView,
//            data
//        )
//        with(viewHolder.binding.footnote) {
//            applyGravity(data.isMine)
//            hideStatusIndicator()
//        }
//    }
//
//    /**
//     * Decorates the footnote of the link attachment message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateLinkAttachmentsMessage(
//        viewHolder: LinkAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupFootnote(
//        viewHolder.binding.footnote,
//        viewHolder.binding.root,
//        viewHolder.binding.threadGuideline,
//        viewHolder.binding.messageContainer,
//        data,
//    )
//
//    /**
//     * Decorates the footnote of the deleted message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateDeletedMessage(
//        viewHolder: MessageDeletedViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupSimpleFootnote(viewHolder.binding.footnote, data)
//    }

    private fun setupFootnote(
        footnoteView: FootnoteView,
        root: ConstraintLayout,
        threadGuideline: View,
        anchorView: View,
        data: MessageListItem.MessageItem,
    ) {
        val isSimpleFootnoteMode = data.message.replyCount == 0 //|| data.isThreadMode
        if (isSimpleFootnoteMode) {
            setupSimpleFootnoteWithRootConstraints(footnoteView, root, anchorView, data)
        } else {
//            setupThreadFootnote(footnoteView, root, threadGuideline, data)
        }
        footnoteView.applyGravity(data.isMine)
    }

    private fun setupSimpleFootnoteWithRootConstraints(
        footnoteView: FootnoteView,
        root: ConstraintLayout,
        anchorView: View,
        data: MessageListItem.MessageItem,
    ) {
        root.updateConstraints {
            clear(footnoteView.id, ConstraintSet.TOP)
            connect(footnoteView.id, ConstraintSet.TOP, anchorView.id, ConstraintSet.BOTTOM)
        }
        setupSimpleFootnote(footnoteView, data)
    }

    private fun setupSimpleFootnote(footnoteView: FootnoteView, data: MessageListItem.MessageItem) {
        footnoteView.showSimpleFootnote()
        setupMessageFooterLabel(footnoteView.footerTextLabel, data, listViewStyle.itemStyle)
        setupMessageFooterTime(footnoteView, data)
        setupDeliveryStateIndicator(footnoteView, data)
    }

//    private fun setupThreadFootnote(
//        footnoteView: FootnoteView,
//        root: ConstraintLayout,
//        threadGuideline: View,
//        data: MessageListItem.MessageItem,
//    ) {
//        if (!listViewStyle.threadsEnabled) {
//            return
//        }
//        root.updateConstraints {
//            clear(footnoteView.id, ConstraintSet.TOP)
//            connect(footnoteView.id, ConstraintSet.TOP, threadGuideline.id, ConstraintSet.BOTTOM)
//        }
//        footnoteView.showThreadRepliesFootnote(
//            data.isMine,
//            data.message.replyCount,
//            data.message.threadParticipants,
//            listViewStyle.itemStyle
//        )
//    }

    private fun setupMessageFooterLabel(
        textView: TextView,
        data: MessageListItem.MessageItem,
        style: MessageListItemStyle,
    ) {
        when {
            data.isBottomPosition() && !isDirectMessage() && data.isTheirs -> {
                textView.text = data.message.user.name
                textView.isVisible = true
                style.textStyleUserName.apply(textView)
            }

            data.isBottomPosition() &&
                    data.message.isDeleted() &&
                    deletedMessageListItemPredicate == DeletedMessageListItemPredicate.VisibleToAuthorOnly -> {
                showOnlyVisibleToYou(textView, style)
            }

//            data.isBottomPosition() && data.message.isEphemeral() -> {
//                showOnlyVisibleToYou(textView, style)
//            }

            else -> {
                textView.isVisible = false
            }
        }
    }

    /**
     * Shows the "Only visible to you" message.
     *
     * @param textView Where the message is displayed.
     * @param style [MessageListItemStyle] The style of the message. The left icon style is defined there.
     */
    private fun showOnlyVisibleToYou(textView: TextView, style: MessageListItemStyle) {
        textView.apply {
            isVisible = true
            text = context.getString(R.string.tinui_message_list_ephemeral_message)
            setStartDrawable(style.iconOnlyVisibleToYou)
            compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.tinui_spacing_small)
        }
    }

    private fun setupMessageFooterTime(
        footnoteView: FootnoteView,
        data: MessageListItem.MessageItem
    ) {
        val createdAt = data.message.getCreatedAtOrNull()
        val updatedAt = data.message.getUpdatedAtOrNull()

        when {
            data.isNotBottomPosition() || createdAt == null -> footnoteView.hideTimeLabel()
//            data.message.isGiphyNotEphemeral() && updatedAt != null -> footnoteView.showTime(
//                dateFormatter.formatTime(
//                    updatedAt
//                ),
//                listViewStyle.itemStyle
//            )
            else -> footnoteView.showTime(
                dateFormatter.formatTime(createdAt),
                listViewStyle.itemStyle
            )
        }
    }

    private fun setupDeliveryStateIndicator(
        footnoteView: FootnoteView,
        data: MessageListItem.MessageItem
    ) {
        val status = data.message.syncStatus
        when {
            !listViewStyle.itemStyle.showMessageDeliveryStatusIndicator -> Unit
            data.isNotBottomPosition() -> footnoteView.hideStatusIndicator()
            data.isTheirs -> footnoteView.hideStatusIndicator()
//            data.message.isEphemeral() -> footnoteView.hideStatusIndicator()
            data.message.isDeleted() -> footnoteView.hideStatusIndicator()
            else -> when (status) {
                SyncStatus.FAILED, SyncStatus.DRAFT, SyncStatus.UNDEFINED,
                SyncStatus.DELETED_HARD, SyncStatus.DELETED_SOFT,
                SyncStatus.DELETED_SYNCED -> footnoteView.hideStatusIndicator()
                SyncStatus.SENDING, SyncStatus.QUEUED -> footnoteView.showStatusIndicator(
                    listViewStyle.itemStyle.iconIndicatorPendingSync
                )
                SyncStatus.SYNCED -> {
                    if (data.isMessageRead) footnoteView.showStatusIndicator(listViewStyle.itemStyle.iconIndicatorRead)
                    else footnoteView.showStatusIndicator(listViewStyle.itemStyle.iconIndicatorSent)
                }
            }
        }
    }
}
