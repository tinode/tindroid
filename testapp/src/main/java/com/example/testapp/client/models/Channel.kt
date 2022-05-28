package com.example.testapp.client.models

import android.graphics.Bitmap
import co.tinode.tinsdk.ComTopic
import co.tinode.tinsdk.Topic.TopicType
import co.tinode.tinui.media.VxCard
import java.util.*

/**
 * Channel is where conversations take place between two or more chat users.
 * It contains a list of messages and have a list of the member users that are participating in the conversation.
 *
 * @param id Channel's unique ID.
 * @param type Type of the channel.
 * @param name Channel's name.
 * @param image Channel's image ref.
 * @param imageBitmap Channel's image bitmap.
 * @param lastMessageAt Date of the last message sent.
 * @param createdAt Date/time of creation.
 * @param deletedAt Date/time of deletion.
 * @param updatedAt Date/time of the last update.
 * @param memberCount Number of members in the channel.
 * @param messages The list of channel's messages.
 * @param reads The list of read states.
 * @param members The list of channel's members.
 * @param unreadCount The number of unread messages for the current user.
 * @param hidden Whether this channel is hidden by current user or not.
 */
public data class Channel(
    /** the chat id, this field is the only required field */
    var id: String = "",
    var type: String = "",
    var name: String = "",
    override var image: String = "",
    override var imageBitmap: Bitmap? = null,
    var lastMessageAt: Date? = null,
    var createdAt: Date? = null,
    var deletedAt: Date? = null,
    var updatedAt: Date? = null,
    var memberCount: Int = 0,
    var messages: List<Message> = mutableListOf(),
    var members: List<Member> = mutableListOf(),
    var reads: List<ChannelUserRead> = mutableListOf(),
    var unreadCount: Int? = null,
    var hidden: Boolean? = null,
    var banded: Boolean? = null,
    var trust: ChannelTrust? = null,
    var staff: Boolean = false,
) : AvatarEntity {
    /**
     * Whether a channel contains unread messages or not.
     */
    val hasUnread: Boolean
        get() = unreadCount?.let { it > 0 } ?: false

    /**
     * Determines the last updated date/time.
     * Returns either [lastMessageAt] or [createdAt].
     */
    val lastUpdated: Date?
        get() = lastMessageAt?.takeIf { createdAt == null || it.after(createdAt) } ?: createdAt

    public companion object {
        public fun fromComTopic(topic: ComTopic<VxCard?>): Channel {
            val activeSups = topic.subscriptions?.filter { sub -> sub.user != null && sub.deleted == null }
            val members = mutableListOf<Member>()
            val reads = mutableListOf<ChannelUserRead>()
            activeSups?.forEach { sub ->
                val member = Member(User.fromSubscription(sub))
                members.add(member)
                val read = ChannelUserRead(member.user, sub.read,
                    (topic.seq - sub.read).coerceAtLeast(0)
                )
                reads.add(read)
            }
            val type = when (topic.topicType) {
                TopicType.GRP -> "grp"
                TopicType.P2P -> "p2p"
                else -> null
            } ?: throw IllegalStateException("Channel type has to be either group or p2p but found ${topic.topicType}")

            return Channel(
                id = topic.name,
                name = topic.pub?.fn ?: "",
                type = type,
                memberCount = members.size,
                members = members,
                reads = reads,
                image = topic.pub?.photoRef ?: "",
                imageBitmap = topic.pub?.bitmap,
                lastMessageAt = topic.touched,
                createdAt = topic.created,
                updatedAt = topic.updated,
                unreadCount = topic.unreadCount,
                hidden = topic.isArchived,
                banded = !topic.isJoiner,
                trust = ChannelTrust(
                    verified = topic.isTrustedVerified,
                    staff = topic.isTrustedStaff,
                    danger = topic.isTrustedDanger
                ),
            )
        }
    }
}
