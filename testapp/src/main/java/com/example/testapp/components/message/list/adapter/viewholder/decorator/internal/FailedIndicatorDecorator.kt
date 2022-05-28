package com.example.testapp.components.message.list.adapter.viewholder.decorator.internal

import android.widget.ImageView
import androidx.core.view.isVisible
import com.example.testapp.components.message.list.MessageListItemStyle
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.viewholder.internal.MessagePlainTextViewHolder

internal class FailedIndicatorDecorator(
    private val listViewStyle: MessageListItemStyle,
    private val isCurrentUserBanned: () -> Boolean,
) : BaseDecorator() {

//    /**
//     * Decorates the visibility of the "failed" section of the message containing
//     * custom attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateCustomAttachmentsMessage(
//        viewHolder: CustomAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupFailedIndicator(viewHolder.binding.deliveryFailedIcon, data)
//    }
//
//    /**
//     * Decorates the visibility of the "failed" section of the Giphy attachment.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyAttachmentMessage(
//        viewHolder: GiphyAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupFailedIndicator(viewHolder.binding.deliveryFailedIcon, data)
//    }
//
//    /**
//     * Decorates the visibility of the "failed" section of the message containing
//     * file attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateFileAttachmentsMessage(
//        viewHolder: FileAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupFailedIndicator(viewHolder.binding.deliveryFailedIcon, data)
//    }
//
//    /**
//     * Decorates the visibility of the "failed" section of the message containing
//     * image attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateImageAttachmentsMessage(
//        viewHolder: ImageAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupFailedIndicator(viewHolder.binding.deliveryFailedIcon, data)
//    }

    /**
     * Decorates the visibility of the "failed" section of the plain text message.
     *
     * @param viewHolder The holder to decorate.
     * @param data The item that holds all the information.
     */
    override fun decoratePlainTextMessage(
        viewHolder: MessagePlainTextViewHolder,
        data: MessageListItem.MessageItem,
    ) {
        setupFailedIndicator(viewHolder.binding.deliveryFailedIcon, data)
    }

//    /**
//     * Does nothing for deleted messages as they can't contain the "failed" section.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateDeletedMessage(
//        viewHolder: MessageDeletedViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = Unit
//
//    /**
//     * Does nothing for ephemeral Giphy messages as they can't contain the "failed" section.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyMessage(
//        viewHolder: GiphyViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = Unit
//
//    /**
//     * Decorates the visibility of the "failed" section of the link attachment message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateLinkAttachmentsMessage(
//        viewHolder: LinkAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        setupFailedIndicator(viewHolder.binding.deliveryFailedIcon, data)
//    }

    private fun setupFailedIndicator(
        deliveryFailedIcon: ImageView,
        data: MessageListItem.MessageItem,
    ) {
        val isFailed = false//data.isMine && data.message.syncStatus == SyncStatus.FAILED_PERMANENTLY
        val isBanned = isFailed && isCurrentUserBanned()
        when {
            isBanned -> deliveryFailedIcon.setImageDrawable(listViewStyle.iconBannedMessage)
            isFailed -> deliveryFailedIcon.setImageDrawable(listViewStyle.iconFailedMessage)
        }
        deliveryFailedIcon.isVisible = isFailed || isBanned
    }
}
