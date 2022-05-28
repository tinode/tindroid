package com.example.testapp.components.message.list.adapter

import com.example.testapp.client.models.ChannelUserRead
import com.example.testapp.client.models.Message
import com.example.testapp.client.models.User
import java.util.Date

/**
 * [MessageListItem] represents elements that are displayed in a [MessageListView].
 * There are the following subclasses of the [MessageListItem] available:
 * - [DateSeparatorItem]
 * - [MessageItem]
 * - [TypingItem]
 * - [LoadingMoreIndicatorItem]
 */
public sealed class MessageListItem {

    public fun getStableId(): Long {
        return when (this) {
            is TypingItem -> TYPING_ITEM_STABLE_ID
//            is ThreadSeparatorItem -> THREAD_SEPARATOR_ITEM_STABLE_ID
            is MessageItem -> message.id.hashCode().toLong()
            is DateSeparatorItem -> date.time
            is LoadingMoreIndicatorItem -> LOADING_MORE_INDICATOR_STABLE_ID
//            is ThreadPlaceholderItem -> THREAD_PLACEHOLDER_STABLE_ID
        }
    }

    public data class DateSeparatorItem(
        val date: Date,
    ) : MessageListItem()

    public data class MessageItem(
        val message: Message,
        val positions: List<Position> = listOf(),
        val isMine: Boolean = false,
        val messageReadBy: List<ChannelUserRead> = listOf(),
        val isMessageRead: Boolean = true,
    ) : MessageListItem() {
        public val isTheirs: Boolean
            get() = !isMine

        public fun isBottomPosition(): Boolean {
            return Position.BOTTOM in positions
        }

        public fun isNotBottomPosition(): Boolean {
            return !isBottomPosition()
        }
    }

    public data class TypingItem(
        val users: List<User>,
    ) : MessageListItem()

//    public data class ThreadSeparatorItem(
//        val date: Date,
//        val messageCount: Int,
//    ) : MessageListItem()

    public object LoadingMoreIndicatorItem : MessageListItem()

//    public object ThreadPlaceholderItem : MessageListItem()

    public enum class Position {
        TOP,
        MIDDLE,
        BOTTOM,
    }

    private companion object {
        private const val TYPING_ITEM_STABLE_ID = 1L
//        private const val THREAD_SEPARATOR_ITEM_STABLE_ID = 2L
        private const val LOADING_MORE_INDICATOR_STABLE_ID = 3L
//        private const val THREAD_PLACEHOLDER_STABLE_ID = 4L
    }
}
