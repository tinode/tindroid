package com.example.testapp.livedata.model

import com.example.testapp.client.models.*
import java.util.Date

/**
 * A class that only stores the channel data and not all the other channel state
 * Using this prevents code bugs and issues caused by confusing the channel data vs the full channel object
 */
public data class ChannelData(
    var channelId: String,

    /** created by user */
    var createdBy: User = User(),
    /** when the channel was created */
    var createdAt: Date? = null,
    /** when the channel was updated */
    var updatedAt: Date? = null,
    /** when the channel was deleted */
    var deletedAt: Date? = null,
    /** channel member count */
    var memberCount: Int = 0,
) {

    /** create a ChannelData object from a Channel object */
    public constructor(c: Channel) : this(c.id) {
//        frozen = c.frozen
//        cooldown = c.cooldown
        createdAt = c.createdAt
        updatedAt = c.updatedAt
        deletedAt = c.deletedAt
        memberCount = c.memberCount
//        extraData = c.extraData
//
//        createdBy = c.createdBy
//        team = c.team
    }

    /** convert a channelEntity into a channel object */
    internal fun toChannel(messages: List<Message>, members: List<Member>, reads: List<ChannelUserRead>, watchers: List<User>, watcherCount: Int): Channel {
        val c = Channel(memberCount = memberCount)
//        c.type = type
        c.id = channelId
//        c.cid = cid
//        c.frozen = frozen
//        c.createdAt = createdAt
//        c.updatedAt = updatedAt
//        c.deletedAt = deletedAt
//        c.extraData = extraData
//        c.lastMessageAt = messages.lastOrNull()?.let { it.createdAt ?: it.createdLocallyAt }
//        c.createdBy = createdBy

//        c.messages = messages
        c.members = members

//        c.watchers = watchers
//        c.watcherCount = watcherCount

        c.reads = reads

        return c
    }
}
