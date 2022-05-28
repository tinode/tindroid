package com.example.testapp.components.message.list.adapter.viewholder.decorator.internal

import android.widget.TextView
import com.example.testapp.common.style.setTextStyle
import com.example.testapp.components.message.list.MessageListItemStyle
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.viewholder.internal.MessagePlainTextViewHolder

internal class TextDecorator(private val style: MessageListItemStyle) : BaseDecorator() {

//    /**
//     * Decorates the text of the message containing custom attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateCustomAttachmentsMessage(
//        viewHolder: CustomAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupTextView(viewHolder.binding.messageText, data)
//
//    /**
//     * Decorates the text of the Giphy attachment.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyAttachmentMessage(
//        viewHolder: GiphyAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupTextView(viewHolder.binding.messageText, data)
//
//    /**
//     * Decorates the text of the message containing file attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateFileAttachmentsMessage(
//        viewHolder: FileAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupTextView(viewHolder.binding.messageText, data)
//
//    /**
//     * Decorates the text of the message containing image attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateImageAttachmentsMessage(
//        viewHolder: ImageAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupTextView(viewHolder.binding.messageText, data)

    /**
     * Decorates the text of the plain text message.
     *
     * @param viewHolder The holder to decorate.
     * @param data The item that holds all the information.
     */
    override fun decoratePlainTextMessage(
        viewHolder: MessagePlainTextViewHolder,
        data: MessageListItem.MessageItem,
    ) = setupTextView(viewHolder.binding.messageText, data)

//    /**
//     * Does nothing for the ephemeral Giphy message as it can't contain text.
//     */
//    override fun decorateGiphyMessage(
//        viewHolder: GiphyViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = Unit
//
//    /**
//     * Decorates the text of the message containing file attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateLinkAttachmentsMessage(
//        viewHolder: LinkAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupTextView(viewHolder.binding.messageText, data)

    private fun setupTextView(textView: TextView, data: MessageListItem.MessageItem) {
        val textStyle = if (data.isMine) style.textStyleMine else style.textStyleTheirs
        textView.setTextStyle(textStyle)

        style.getStyleLinkTextColor(data.isMine)?.let { linkTextColor ->
            textView.setLinkTextColor(linkTextColor)
        }
    }
}
