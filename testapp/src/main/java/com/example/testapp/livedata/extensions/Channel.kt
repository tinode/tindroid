package com.example.testapp.livedata.extensions

import com.example.testapp.client.models.Channel
import com.example.testapp.client.models.Member
import com.example.testapp.client.models.User

/**
 * Returns all users including watchers of a channel that are associated with it.
 */
internal fun Channel.users(): List<User> {
    return members.map(Member::user)
//        read.map(ChannelUserRead::user) +
//        createdBy +
//        messages.flatMap { it.users() } +
//        watchers
}

//internal val Chat.lastMessage: Message?
//    get() = messages.lastOrNull()

//internal fun Chat.updateLastMessage(message: Message) {
//    val createdAt = message.createdAt ?: message.createdLocallyAt
//    val messageCreatedAt =
//        checkNotNull(createdAt) { "created at cant be null, be sure to set message.createdAt" }
//
//    val updateNeeded = message.id == lastMessage?.id
//    val newLastMessage = lastMessageAt == null || messageCreatedAt.after(lastMessageAt)
//    if (newLastMessage || updateNeeded) {
//        lastMessageAt = messageCreatedAt
//        messages = messages + message
//    }
//}

internal fun Channel.setMember(userId: String, member: Member?) {
    if (member == null) {
        members.firstOrNull { it.user.id == userId }?.also { foundMember ->
            members = members - foundMember
            memberCount--
        }
    } else {
        members = members + member
        memberCount++
    }
}

//internal fun Chat.updateReads(newRead: ChannelUserRead) {
//    val oldRead = read.firstOrNull { it.user == newRead.user }
//    read = if (oldRead != null) {
//        read - oldRead + newRead
//    } else {
//        read + newRead
//    }
//}

/**
 * Increments channel's unread for the specific user.
 *
 * @param currentUserId The id of the user that should have the unread count incremented for this Channel.
 * @param lastMessageSeenDate The Date of the last message that the SDK is aware of.
 */
//internal fun Chat.incrementUnreadCount(currentUserId: String, lastMessageSeenDate: Date?) {
//    read.firstOrNull { it.user.id == currentUserId }
//        ?.let {
//            it.lastMessageSeenDate = lastMessageSeenDate
//            it.unreadMessages++
//        }
//}

//internal fun Collection<Chat>.applyPagination(pagination: AnyChannelPaginationRequest): List<Chat> {
//    return asSequence().sortedWith(pagination.sort.comparator)
//        .drop(pagination.channelOffset)
//        .take(pagination.channelLimit)
//        .toList()
//}

/** Updates collection of channels with more recent data of [users]. */
internal fun Collection<Channel>.updateUsers(users: Map<String, User>) = map { it.updateUsers(users) }

/**
 * Updates a channel with more recent data of [users]. It updates messages, members, watchers, createdBy and
 * pinnedMessages of channel instance.
 */
internal fun Channel.updateUsers(users: Map<String, User>): Channel {
    return if (users().map(User::id).any(users::containsKey)) {
        copy(
//            messages = messages.updateUsers(users),
            members = members.updateUsers(users).toList(),
//            watchers = watchers.updateUsers(users),
//            createdBy = users[createdBy.id] ?: createdBy,
//            pinnedMessages = pinnedMessages.updateUsers(users),
        )
    } else {
        this
    }
}
