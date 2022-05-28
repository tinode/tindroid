package com.example.testapp.components.message.list.adapter.viewholder.decorator.internal

import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.example.testapp.components.avatar.AvatarView
import com.example.testapp.components.message.list.MessageListView
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.viewholder.internal.MessagePlainTextViewHolder


internal class AvatarDecorator(
    private val showAvatarPredicate: MessageListView.ShowAvatarPredicate,
) : BaseDecorator() {

//    /**
//     * Decorates the avatar of the custom attachments message, based on the message owner.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateCustomAttachmentsMessage(
//        viewHolder: CustomAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        controlVisibility(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine)
//        setupAvatar(getAvatarView(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine), data)
//    }
//
//    /**
//     * Decorates the avatar of the Giphy attachment, based on the message owner.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateGiphyAttachmentMessage(
//        viewHolder: GiphyAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        controlVisibility(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine)
//        setupAvatar(getAvatarView(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine), data)
//    }
//
//    /**
//     * Decorates the avatar of the file attachments message, based on the message owner.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateFileAttachmentsMessage(
//        viewHolder: FileAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        controlVisibility(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine)
//        setupAvatar(getAvatarView(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine), data)
//    }
//
//    /**
//     * Decorates the avatar of the image attachments message, based on the message owner.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateImageAttachmentsMessage(
//        viewHolder: ImageAttachmentViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        controlVisibility(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine)
//        setupAvatar(getAvatarView(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine), data)
//    }

    /**
     * Decorates the avatar of the plain text message, based on the message owner.
     *
     * @param viewHolder The holder to decorate.
     * @param data The item that holds all the information.
     */
    override fun decoratePlainTextMessage(
        viewHolder: MessagePlainTextViewHolder,
        data: MessageListItem.MessageItem,
    ) {
        controlVisibility(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine)
        setupAvatar(getAvatarView(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine), data)
    }

//    /**
//     * Decorates the avatar of the link attachment message, based on the message owner.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateLinkAttachmentsMessage(
//        viewHolder: LinkAttachmentsViewHolder,
//        data: MessageListItem.MessageItem,
//    ) {
//        controlVisibility(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine)
//        setupAvatar(getAvatarView(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine), data)
//    }
//
//    /**
//     * Does nothing for ephemeral Giphy message, as it doesn't contain an avatar.
//     */
//    override fun decorateGiphyMessage(
//        viewHolder: GiphyViewHolder,
//        data: MessageListItem.MessageItem,
//    ) = Unit
//
//    /**
//     * Decorates the avatar of the deleted message, based on the message owner.
//     *
//     * @param viewHolder The holder to decorate.
//     * @param data The item that holds all the information.
//     */
//    override fun decorateDeletedMessage(viewHolder: MessageDeletedViewHolder, data: MessageListItem.MessageItem) {
//        controlVisibility(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine)
//        setupAvatar(getAvatarView(viewHolder.binding.avatarMineView, viewHolder.binding.avatarView, data.isMine), data)
//    }

    private fun setupAvatar(avatarView: AvatarView, data: MessageListItem.MessageItem) {
        val shouldShow = showAvatarPredicate.shouldShow(data)

        avatarView.isVisible = shouldShow

        if (shouldShow) {
            avatarView.setUserData(data.message.user)
        }
    }

    private fun getAvatarView(myAvatar: AvatarView, theirAvatar: AvatarView, isMine: Boolean): AvatarView {
        return if (isMine) myAvatar else theirAvatar
    }

    private fun controlVisibility(myAvatar: AvatarView, theirAvatar: AvatarView, isMine: Boolean) {
        theirAvatar.isVisible = !isMine
        myAvatar.isVisible = isMine
    }
}
