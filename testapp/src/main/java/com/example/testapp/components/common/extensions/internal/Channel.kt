package com.example.testapp.components.common.extensions.internal

import com.example.testapp.client.extensions.getUsersExcludingCurrent
import com.example.testapp.client.models.Channel
import com.example.testapp.components.channel.list.adapter.ChannelListPayloadDiff

internal fun Channel.diff(other: Channel): ChannelListPayloadDiff {
    val usersChanged = getUsersExcludingCurrent() != other.getUsersExcludingCurrent()
    return ChannelListPayloadDiff(
        nameChanged = name != other.name,
        avatarViewChanged = usersChanged,
        usersChanged = usersChanged,
        readStateChanged = lastMessageAt != other.lastMessageAt,
        lastMessageChanged = false,
        unreadCountChanged = unreadCount != other.unreadCount,
        trustChanged = trust != other.trust
    )
}
