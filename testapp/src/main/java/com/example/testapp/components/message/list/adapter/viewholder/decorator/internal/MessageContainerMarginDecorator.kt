package com.example.testapp.components.message.list.adapter.viewholder.decorator.internal

import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.example.testapp.components.message.list.MessageListItemStyle
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.viewholder.internal.MessagePlainTextViewHolder

internal class MessageContainerMarginDecorator(
    private val style: MessageListItemStyle,
) : BaseDecorator() {

//    /**
//     * Decorates the message container of the custom attachments message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateCustomAttachmentsMessage(
//        viewHolder: CustomAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.run { configMargins(messageContainer, footnote, style) }
//    }
//
//    /**
//     * Decorates the message container of the Giphy attachment message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyAttachmentMessage(
//        viewHolder: GiphyAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.run { configMargins(messageContainer, footnote, style) }
//    }
//
//    /**
//     * Decorates the message container of the file attachments message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateFileAttachmentsMessage(
//        viewHolder: FileAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.run { configMargins(messageContainer, footnote, style) }
//    }
//
//    /**
//     * Decorates the message container of the image attachments message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateImageAttachmentsMessage(
//        viewHolder: ImageAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.run { configMargins(messageContainer, footnote, style) }
//    }

    /**
     * Decorates the message container of the image attachments message.
     *
     * @param viewHolder The holder to decorate.
     * @param data The item that holds all the information.
     */
    override fun decoratePlainTextMessage(
        viewHolder: MessagePlainTextViewHolder,
        data: MessageListItem.MessageItem,
    ) {
        viewHolder.binding.run { configMargins(messageContainer, footnote, style) }
    }

//    /**
//     * Decorates the message container of the deleted message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateDeletedMessage(
//        viewHolder: MessageDeletedViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.run { configMargins(messageContainer, footnote, style) }
//    }
//
//    /**
//     * Does nothing for the ephemeral Giphy message.
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
//     * Decorates the message container of the link attachments message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateLinkAttachmentsMessage(
//        viewHolder: LinkAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.run { configMargins(messageContainer, footnote, style) }
//    }

    private fun configMargins(messageContainer: View, footnote: View, style: MessageListItemStyle) {
        messageContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = style.messageStartMargin
            marginEnd = style.messageEndMargin
        }

        footnote.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = style.messageStartMargin
            marginEnd = style.messageEndMargin
        }
    }
}
