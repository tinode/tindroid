package com.example.testapp.components.channel.list.adapter.viewholder.internal

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import com.example.testapp.R
import com.example.testapp.client.ChatClient
import com.example.testapp.client.models.Channel
import com.example.testapp.common.utils.extensions.isDirectMessaging
import com.example.testapp.components.channel.list.ChannelListView
import com.example.testapp.components.channel.list.ChannelListViewStyle
import com.example.testapp.components.channel.list.SwipeViewHolder
import com.example.testapp.components.channel.list.adapter.ChannelListPayloadDiff
import com.example.testapp.components.common.extensions.internal.context
import com.example.testapp.components.common.extensions.internal.getDimension
import com.example.testapp.components.common.extensions.internal.streamThemeInflater
import com.example.testapp.components.utils.isRtlLayout
import com.example.testapp.databinding.TinuiChannelListItemBackgroundViewBinding
import com.example.testapp.databinding.TinuiChannelListItemForegroundViewBinding
import com.example.testapp.databinding.TinuiChannelsListItemViewBinding
import com.example.testapp.livedata.extensions.globalState
import com.example.testapp.livedata.state.global.GlobalState

import kotlin.math.absoluteValue

internal class ChannelViewHolder @JvmOverloads constructor(
    parent: ViewGroup,
    private val channelClickListener: ChannelListView.ChannelClickListener,
    private val channelLongClickListener: ChannelListView.ChannelLongClickListener,
    private val channelDeleteListener: ChannelListView.ChannelClickListener,
    private val channelMoreOptionsListener: ChannelListView.ChannelClickListener,
    private val userClickListener: ChannelListView.UserClickListener,
    private val swipeListener: ChannelListView.SwipeListener,
    private val style: ChannelListViewStyle,
    private val binding: TinuiChannelsListItemViewBinding = TinuiChannelsListItemViewBinding.inflate(
        parent.streamThemeInflater,
        parent,
        false
    ),
    private val globalState: GlobalState = ChatClient.instance().globalState,
) : SwipeViewHolder(binding.root) {
    private val currentUser = globalState.user

    private var optionsCount = 1

    private val menuItemWidth = context.getDimension(R.dimen.tinui_channel_list_item_option_icon_width).toFloat()
    private val optionsMenuWidth
        get() = menuItemWidth * optionsCount

    private lateinit var channel: Channel

    init {
        binding.apply {
            itemBackgroundView.apply {
                moreOptionsImageView.setOnClickListener {
                    channelMoreOptionsListener.onClick(channel)
                    swipeListener.onSwipeCanceled(this@ChannelViewHolder, absoluteAdapterPosition)
                }
                deleteImageView.setOnClickListener {
                    channelDeleteListener.onClick(channel)
                }

                applyStyle(style)
            }

            itemForegroundView.apply {
                avatarView.setOnClickListener {
                    when {
                        channel.isDirectMessaging() -> currentUser.value?.let(userClickListener::onClick)
                        else -> channelClickListener.onClick(channel)
                    }
                }
                root.apply {
                    setOnClickListener {
                        if (!swiping) {
                            channelClickListener.onClick(channel)
                        }
                    }
                    setOnLongClickListener {
                        if (!swiping) {
                            channelLongClickListener.onLongClick(channel)
                        } else true // consume if we're swiping
                    }
                    doOnNextLayout {
                        setSwipeListener(root, swipeListener)
                    }
                }

                applyStyle(style)
            }
        }
    }

    override fun bind(channel: Channel, diff: ChannelListPayloadDiff) {
        this.channel = channel

        configureForeground(diff, channel)
        configureBackground()

        listener?.onRestoreSwipePosition(this, absoluteAdapterPosition)
    }

    override fun getSwipeView(): View {
        return binding.itemForegroundView.root
    }

    /**
     * The position whe the swipe view is swiped
     */
    override fun getOpenedX(): Float {
        val isRtl = context.isRtlLayout

        return if (isRtl) optionsMenuWidth else -optionsMenuWidth
    }

    /**
     * The default position of swipe view
     */
    override fun getClosedX(): Float {
        return 0f
    }

    /**
     * Whether the swipe view is swiped or not.
     */
    override fun isSwiped(): Boolean {
        val swipeLimit = getOpenedX().absoluteValue / 2
        val swipe = getSwipeView().x.absoluteValue

        return swipe >= swipeLimit
    }

    /**
     * The range of the swipe
     */
    override fun getSwipeDeltaRange(): ClosedFloatingPointRange<Float> {
        val isRtl = context.isRtlLayout

        return if (isRtl) {
            getClosedX()..getOpenedX()
        } else {
            getOpenedX()..getClosedX()
        }
    }

    override fun isSwipeEnabled(): Boolean {
        return optionsCount > 0 && style.swipeEnabled
    }

    private fun configureBackground() {
        configureBackgroundButtons()
    }

    private fun configureBackgroundButtons() {
        var optionsCount = 0

        binding.itemBackgroundView.moreOptionsImageView.apply {
            if (style.optionsEnabled) {
                isVisible = true
                optionsCount++
            } else {
                isVisible = false
            }
        }
        binding.itemBackgroundView.deleteImageView.apply {
            val canDeleteChannel = true // channel.members.isCurrentUserOwnerOrAdmin()
            if (canDeleteChannel && style.deleteEnabled) {
                isVisible = true
                optionsCount++
            } else {
                isVisible = false
            }
        }

        this.optionsCount = optionsCount
    }

    private fun configureForeground(diff: ChannelListPayloadDiff, channel: Channel) {
        binding.itemForegroundView.apply {
            diff.run {
                if (nameChanged) {
                    configureChannelNameLabel()
                }

                if (avatarViewChanged) {
                    configureAvatarView()
                }

//                val lastMessage = channel.getLastMessage()
//                if (lastMessageChanged) {
//                    configureLastMessageLabelAndTimestamp(lastMessage)
//                }
//
//                if (readStateChanged) {
//                    configureCurrentUserLastMessageStatus(lastMessage)
//                }
//
                if (unreadCountChanged) {
                    configureUnreadCountBadge()
                }

                verifiedStatusImageView.isVisible = channel.trust?.verified ?: false
                staffStatusImageView.isVisible = channel.trust?.staff ?: false
                dangerStatusImageView.isVisible = channel.trust?.danger ?: false
//
//                muteIcon.isVisible = channel.isMuted
            }
        }
    }

    private fun TinuiChannelListItemForegroundViewBinding.configureChannelNameLabel() {
//        channelNameLabel.text = ChannelUI.channelNameFormatter.formatChannelName(
//            channel = channel,
//            currentUser = ChannelClient.instance().getCurrentUser()
//        )
        channelNameLabel.text = channel.name
    }

    private fun TinuiChannelListItemForegroundViewBinding.configureAvatarView() {
        avatarView.setChannelData(channel)
    }

//    private fun TinuiChannelListItemForegroundViewBinding.configureLastMessageLabelAndTimestamp(
//        lastMessage: Message?,
//    ) {
//        lastMessageLabel.isVisible = lastMessage.isNotNull()
//        lastMessageTimeLabel.isVisible = lastMessage.isNotNull()
//
//        lastMessage ?: return
//
//        lastMessageLabel.text = channel.getLastMessagePreviewText(context, channel.isDirectMessaging())
//        lastMessageTimeLabel.text = ChannelUI.dateFormatter.formatDate(lastMessage.getCreatedAtOrThrow())
//    }

    private fun TinuiChannelListItemForegroundViewBinding.configureUnreadCountBadge() {
        val count = channel.unreadCount ?: 0

        val haveUnreadMessages = count > 0
        unreadCountBadge.isVisible = haveUnreadMessages

        if (!haveUnreadMessages) {
            return
        }

        unreadCountBadge.text = if (count > 99) {
            "99+"
        } else {
            count.toString()
        }
    }

//    private fun TinuiChannelListItemForegroundViewBinding.configureCurrentUserLastMessageStatus(
//        lastMessage: Message?,
//    ) {
//        messageStatusImageView.isVisible = lastMessage != null && style.showChannelDeliveryStatusIndicator
//
//        if (lastMessage == null || !style.showChannelDeliveryStatusIndicator) return
//
//        // read - if the last message doesn't belong to current user, or if channel reads indicates it
//        // delivered - if the last message belongs to the current user and reads indicate it wasn't read
//        // pending - if the sync status says it's pending
//
//        val currentUserSentLastMessage = lastMessage.user.id == globalState.user.value?.id
//        val lastMessageByCurrentUserWasRead = channel.isMessageRead(lastMessage)
//        when {
//            !currentUserSentLastMessage || lastMessageByCurrentUserWasRead -> {
//                messageStatusImageView.setImageDrawable(style.indicatorReadIcon)
//            }
//
//            currentUserSentLastMessage && !lastMessageByCurrentUserWasRead -> {
//                messageStatusImageView.setImageDrawable(style.indicatorSentIcon)
//            }
//
//            else -> determineLastMessageSyncStatus(lastMessage)
//        }
//    }

//    private fun TinuiChannelListItemForegroundViewBinding.determineLastMessageSyncStatus(message: Storage.Message) {
//        when (message.status) {
//            BaseDb.Status.DRAFT.value, BaseDb.Status.QUEUED.value -> {
//                messageStatusImageView.setImageDrawable(style.indicatorPendingSyncIcon)
//            }
//
//            BaseDb.Status.SYNCED.value -> {
//                messageStatusImageView.setImageDrawable(style.indicatorSentIcon)
//            }
//
//            BaseDb.Status.FAILED.value -> {
//                // no direction on this yet
//            }
//        }
//    }

    private fun TinuiChannelListItemBackgroundViewBinding.applyStyle(style: ChannelListViewStyle) {
        root.setBackgroundColor(style.backgroundLayoutColor)
        deleteImageView.setImageDrawable(style.deleteIcon)
        moreOptionsImageView.setImageDrawable(style.optionsIcon)
    }

    private fun TinuiChannelListItemForegroundViewBinding.applyStyle(style: ChannelListViewStyle) {
        root.backgroundTintList = ColorStateList.valueOf(style.foregroundLayoutColor)
        style.channelTitleText.apply(channelNameLabel)
        style.lastMessageText.apply(lastMessageLabel)
        style.lastMessageDateText.apply(lastMessageTimeLabel)
        style.unreadMessageCounterText.apply(unreadCountBadge)
        unreadCountBadge.backgroundTintList = ColorStateList.valueOf(style.unreadMessageCounterBackgroundColor)
        muteIcon.setImageDrawable(style.mutedChannelIcon)
        verifiedStatusImageView.setImageDrawable(style.verifiedChannelIcon)
        staffStatusImageView.setImageDrawable(style.staffChannelIcon)
        dangerStatusImageView.setImageDrawable(style.dangerChannelIcon)
    }
}
