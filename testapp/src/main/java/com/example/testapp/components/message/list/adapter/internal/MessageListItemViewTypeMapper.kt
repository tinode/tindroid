package com.example.testapp.components.message.list.adapter.internal

import com.example.testapp.client.models.Message
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.DATE_DIVIDER
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.CUSTOM_ATTACHMENTS
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.ERROR_MESSAGE
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.FILE_ATTACHMENTS
//import com.example.testapp.components.message.list.adapter.MessageListItemViewType.GIPHY
//import com.example.testapp.components.message.list.adapter.MessageListItemViewType.GIPHY_ATTACHMENT
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.IMAGE_ATTACHMENT
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.LINK_ATTACHMENTS
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.LOADING_INDICATOR
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.MESSAGE_DELETED
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.PLAIN_TEXT
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.SYSTEM_MESSAGE
//import com.example.testapp.components.message.list.adapter.MessageListItemViewType.THREAD_PLACEHOLDER
//import com.example.testapp.components.message.list.adapter.MessageListItemViewType.THREAD_SEPARATOR
import com.example.testapp.components.message.list.adapter.MessageListItemViewType.TYPING_INDICATOR

internal object MessageListItemViewTypeMapper {

    fun getViewTypeValue(messageListItem: MessageListItem/*, attachmentFactoryManager: AttachmentFactoryManager*/): Int {
        return when (messageListItem) {
            is MessageListItem.DateSeparatorItem -> DATE_DIVIDER
            is MessageListItem.LoadingMoreIndicatorItem -> LOADING_INDICATOR
//            is MessageListItem.ThreadSeparatorItem -> THREAD_SEPARATOR
            is MessageListItem.MessageItem -> messageItemToViewType(messageListItem/*, attachmentFactoryManager*/)
            is MessageListItem.TypingItem -> TYPING_INDICATOR
//            is MessageListItem.ThreadPlaceholderItem -> THREAD_PLACEHOLDER
        }
    }

    /**
     * Transforms the given [messageItem] to the type of the message we should show in the list.
     *
     * @param messageItem The message item that holds all the information required to generate a message type.
     * @param attachmentFactoryManager A manager for the registered custom attachment factories.
     * @return The [Int] message type.
     */
    private fun messageItemToViewType(
        messageItem: MessageListItem.MessageItem,
//        attachmentFactoryManager: AttachmentFactoryManager,
    ): Int {
        val message = messageItem.message

//        val (linksAndGiphy, _) = message.attachments.partition { attachment -> attachment.hasLink() }
//        val containsGiphy = linksAndGiphy.any { attachment -> attachment.type == ModelType.attach_giphy }
//        val hasAttachments = message.attachments.isNotEmpty()
//
//        val containsOnlyLinks = message.containsOnlyLinkAttachments()

        return when {
//            attachmentFactoryManager.canHandle(message) -> CUSTOM_ATTACHMENTS
//            message.isError() -> ERROR_MESSAGE
//            message.isSystem() -> SYSTEM_MESSAGE
            message.deletedAt != null -> MESSAGE_DELETED
//            message.isGiphyEphemeral() -> GIPHY
//            containsGiphy -> GIPHY_ATTACHMENT
//            containsOnlyLinks -> LINK_ATTACHMENTS
//            message.isImageAttachment() -> IMAGE_ATTACHMENT
//            hasAttachments -> FILE_ATTACHMENTS
            else -> PLAIN_TEXT
        }
    }

//    /**
//     * Checks if the message contains only image attachments (Can also optionally contain links).
//     */
//    private fun Message.isImageAttachment(): Boolean {
//        return attachments.isNotEmpty() &&
//            attachments.any { it.isImage() } &&
//            attachments.all { it.isImage() || it.hasLink() }
//    }

//    /**
//     * Checks if all attachments are link attachments.
//     */
//    private fun Message.containsOnlyLinkAttachments(): Boolean {
//        if (this.attachments.isEmpty()) return false
//
//        return this.attachments.all { attachment -> attachment.hasLink() }
//        return false
//    }
}
