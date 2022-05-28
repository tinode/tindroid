package com.example.testapp.components.message.list.adapter.viewholder.decorator.internal

import com.example.testapp.components.common.extensions.internal.dpToPxPrecise
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.viewholder.internal.MessagePlainTextViewHolder
import com.example.testapp.components.message.list.background.MessageBackgroundFactory


internal class BackgroundDecorator(
    private val messageBackgroundFactory: MessageBackgroundFactory,
) : BaseDecorator() {

//    /**
//     * Decorates the background of the custom attachments message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateCustomAttachmentsMessage(
//        viewHolder: CustomAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.messageContainer.background =
//            messageBackgroundFactory.textAndAttachmentMessageBackground(
//                viewHolder.binding.messageContainer.context,
//                data
//            )
//    }

//    /**
//     * Decorates the background of the Giphy attachment.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyAttachmentMessage(
//        viewHolder: GiphyAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.messageContainer.background =
//            messageBackgroundFactory.textAndAttachmentMessageBackground(
//                viewHolder.binding.messageContainer.context,
//                data
//            )
//    }

//    /**
//     * Decorates the background of the file attachments message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateFileAttachmentsMessage(
//        viewHolder: FileAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.messageContainer.background =
//            messageBackgroundFactory.fileAttachmentsMessageBackground(
//                viewHolder.binding.messageContainer.context,
//                data
//            )
//    }
//
//    /**
//     * Decorates the background of the image attachments message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateImageAttachmentsMessage(
//        viewHolder: ImageAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.messageContainer.background =
//            messageBackgroundFactory.imageAttachmentMessageBackground(
//                viewHolder.binding.messageContainer.context,
//                data
//            )
//    }
//
//    /**
//     * Decorates the background of the deleted message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateDeletedMessage(
//        viewHolder: MessageDeletedViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.messageContainer.background =
//            messageBackgroundFactory.deletedMessageBackground(
//                viewHolder.binding.messageContainer.context,
//                data
//            )
//    }

    /**
     * Decorates the background of the plain text message.
     *
     * @param viewHolder The holder to decorate.
     * @param data The item that holds all the information.
     */
    override fun decoratePlainTextMessage(
        viewHolder: MessagePlainTextViewHolder,
        data: MessageListItem.MessageItem,
    ) {
        viewHolder.binding.messageContainer.background =
            messageBackgroundFactory.plainTextMessageBackground(
                viewHolder.binding.messageContainer.context,
                data
            )
    }

//    /**
//     * Decorates the background of the ephemeral Giphy message.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyMessage(
//        viewHolder: GiphyViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.cardView.background =
//            messageBackgroundFactory.giphyAppearanceModel(viewHolder.binding.cardView.context)
//    }
//
//    /**
//     * Decorates the background of the message container.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateLinkAttachmentsMessage(
//        viewHolder: LinkAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        viewHolder.binding.messageContainer.background =
//            messageBackgroundFactory.linkAttachmentMessageBackground(
//                viewHolder.binding.messageContainer.context,
//                data
//            )
//    }

    companion object {
        internal val DEFAULT_CORNER_RADIUS = 16.dpToPxPrecise()
    }
}
