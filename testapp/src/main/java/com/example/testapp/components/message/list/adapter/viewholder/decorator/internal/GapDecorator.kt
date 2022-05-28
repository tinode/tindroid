package com.example.testapp.components.message.list.adapter.viewholder.decorator.internal

import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.view.internal.GapView
import com.example.testapp.components.message.list.adapter.viewholder.internal.MessagePlainTextViewHolder


internal class GapDecorator : BaseDecorator() {

//    /**
//     * Decorates the gap of the message containing custom attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateCustomAttachmentsMessage(
//        viewHolder: CustomAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupGapView(viewHolder.binding.gapView, data)
//
//    /**
//     * Decorates the gap of the Giphy attachment.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyAttachmentMessage(
//        viewHolder: GiphyAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupGapView(viewHolder.binding.gapView, data)
//
//    /**
//     * Decorates the gap of the message containing file attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateFileAttachmentsMessage(
//        viewHolder: FileAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupGapView(viewHolder.binding.gapView, data)
//
//    /**
//     * Decorates the gap of the message containing image attachments.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateImageAttachmentsMessage(
//        viewHolder: ImageAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupGapView(viewHolder.binding.gapView, data)
//
//    override fun decorateDeletedMessage(
//        viewHolder: MessageDeletedViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupGapView(viewHolder.binding.gapView, data)

    override fun decoratePlainTextMessage(
        viewHolder: MessagePlainTextViewHolder,
        data: MessageListItem.MessageItem,
    ) = setupGapView(viewHolder.binding.gapView, data)

//    override fun decorateGiphyMessage(
//        viewHolder: GiphyViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupGapView(viewHolder.binding.gapView, data)
//
//    /**
//     * Decorates the gap of the link attachment message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateLinkAttachmentsMessage(
//        viewHolder: LinkAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = setupGapView(viewHolder.binding.gapView, data)

    private fun setupGapView(gapView: GapView, data: MessageListItem.MessageItem) {
        if (data.positions.contains(MessageListItem.Position.TOP)) {
            gapView.showBigGap()
        } else {
            gapView.showSmallGap()
        }
    }
}
