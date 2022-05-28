package com.example.testapp.client.models

import co.tinode.tinsdk.ComTopic
import co.tinode.tinsdk.Storage
import co.tinode.tinsdk.model.Drafty
import co.tinode.tinsdk.model.MsgServerData
import co.tinode.tinui.db.StoredMessage
import co.tinode.tinui.media.VxCard
import com.example.testapp.client.utils.SyncStatus
import com.example.testapp.core.internal.InternalTinUiApi
import java.util.Date

public data class Message(
    /**
     * The unique string identifier of the message. This is set on the client
     * side when the message is added.
     */
    var id: String = "",

    /**
     * Channel unique identifier in <type>:<id> format
     */
    var cid: String = "",

    /**
     * Server-issued sequential ID for the message. The seq are guaranteed to be unique within a topic.
     */
    var seq: Int? = 0,

    /**
     * The text of this message
     */
    var text: String = "",

    /**
     * The message text formatted as Drafty
     */
    val drafty: Drafty = Drafty(),

//    /**
//     * The ID of the parent message, if the message is a thread reply
//     */
//    var parentId: String? = null,

    /**
     * Contains provided slash command
     */
    var command: String? = null,

    /**
     * The list of message attachments
     */
//    var attachments: MutableList<Attachment> = mutableListOf(),

    /**
     * The list of user mentioned in the message
     */
    var mentionedUsersIds: MutableList<String> = mutableListOf(),

    /**
     * The list of user mentioned in the message
     */
    var mentionedUsers: MutableList<User> = mutableListOf(),

    /**
     * The number of replies to this message
     */
    var replyCount: Int = 0,

    /**
     * A mapping between reaction type and the count, ie like:10, heart:4
     */
//    var reactionCounts: MutableMap<String, Int> = mutableMapOf(),

    /**
     * A mapping between reaction type and the reaction score, ie like:10, heart:4
     */
//    var reactionScores: MutableMap<String, Int> = mutableMapOf(),

    /**
     * If the message has been synced to the servers, default is synced
     */
    var syncStatus: SyncStatus = SyncStatus.SYNCED,

    /**
     * Contains type of the message. Can be one of the following: regular, ephemeral,
     * error, reply, system, deleted.
     */
    var type: String = "",

    /**
     * List of the latest reactions to this message
     */
//    var latestReactions: MutableList<Reaction> = mutableListOf(),

    /**
     * List of reactions of authenticated user to this message
     */
//    var ownReactions: MutableList<Reaction> = mutableListOf(),

    /**
     * When the message was created
     */
    var createdAt: Date? = null,

    /**
     * When the message was updated
     */
    var updatedAt: Date? = null,

    /**
     * When the message was deleted
     */
    var deletedAt: Date? = null,

    /**
     * When the message was updated locally
     */
    var updatedLocallyAt: Date? = null,

    /**
     * When the message was created locally
     */
    var createdLocallyAt: Date? = null,

    /**
     * The user who sent the message
     */
    var user: User = User(),

    /**
     * All the custom data provided for this message
     */
    var extraData: MutableMap<String, Any> = mutableMapOf(),

    /**
     * Whether message is silent or not
     */
    var silent: Boolean = false,

    /**
     * If the message was sent by shadow banned user
     */
    var shadowed: Boolean = false,

    /**
     * Mapping with translations. Key `language` contains the original language key.
     * Other keys contain translations.
     */
    val i18n: Map<String, String> = mapOf(),

//    /**
//     * Whether thread reply should be shown in the channel as well
//     */
//    var showInChannel: Boolean = false,

    @property:InternalTinUiApi
    var channelInfo: ChannelInfo? = null,

    /**
     * Contains quoted message
     */
    var replyTo: Message? = null,

    /**
     * The ID of the quoted message, if the message is a quoted reply.
     */
    var replyMessageId: String? = null,

    /**
     * Whether message is pinned or not
     */
    var pinned: Boolean = false,

//    /**
//     * Date when the message got pinned
//     */
//    var pinnedAt: Date? = null,
//
//    /**
//     * Date when pinned message expires
//     */
//    var pinExpires: Date? = null,
//
//    /**
//     * Contains user who pinned the message
//     */
//    var pinnedBy: User? = null,
//
//    /**
//     * The list of users who participate in thread
//     */
//    var threadParticipants: List<User> = emptyList(),
) {
    public companion object {
        public fun fromStorageMessage(message: Storage.Message, user: User): Message {
            return Message(
                id = message.dbId.toString(),
                cid = message.topic,
                seq = message.seqId,
                text = message.content.txt,
                drafty = message.content,
//                parentId = parentId,
//                command = command,
//                attachments = attachments,
//                mentionedUsersIds = mentionedUsersIds,
//                mentionedUsers = mentionedUsers,
//                replyCount = replyCount,
//                reactionCounts = reactionCounts,
//                reactionScores = reactionScores,
                syncStatus = SyncStatus.fromInt(message.status)!!,
//                following = following,
                type = "regular",
//                latestReactions = latestReactions,
//                ownReactions = ownReactions,
                createdAt = if (message is StoredMessage) message.ts else null,
                updatedAt = null,
//                deletedAt = deletedAt,
//                updatedLocallyAt = updatedLocallyAt,
//                createdLocallyAt = createdLocallyAt,
                user = user,
//                extraData = extraData,
//                silent = silent,
//                shadowed = shadowed,
//                i18n = i18n,
//                showInChannel = showInChannel,
//                channelInfo = channelInfo,
//                replyTo = replyTo,
//                replyMessageId = replyMessageId,
//                pinned = pinned,
//                pinnedAt = pinnedAt,
//                pinExpires = pinExpires,
//                pinnedBy = pinnedBy,
//                threadParticipants = threadParticipants,
            )
        }

        public fun fromMsgServerData(message: MsgServerData, user: User): Message {
            return Message(
                id = message.seq.toString(),
                cid = message.topic,
                seq = message.seq,
                text = message.content.txt,
                drafty = message.content,
//                parentId = parentId,
//                command = command,
//                attachments = attachments,
//                mentionedUsersIds = mentionedUsersIds,
//                mentionedUsers = mentionedUsers,
//                replyCount = replyCount,
//                reactionCounts = reactionCounts,
//                reactionScores = reactionScores,
                syncStatus = SyncStatus.SYNCED,
//                following = following,
                type = "regular",
//                latestReactions = latestReactions,
//                ownReactions = ownReactions,
                createdAt = message.ts,
                updatedAt = null,
//                deletedAt = deletedAt,
//                updatedLocallyAt = updatedLocallyAt,
//                createdLocallyAt = createdLocallyAt,
                user = user,
//                extraData = extraData,
//                silent = silent,
//                shadowed = shadowed,
//                i18n = i18n,
//                showInChannel = showInChannel,
//                channelInfo = channelInfo,
//                replyTo = replyTo,
//                replyMessageId = replyMessageId,
//                pinned = pinned,
//                pinnedAt = pinnedAt,
//                pinExpires = pinExpires,
//                pinnedBy = pinnedBy,
//                threadParticipants = threadParticipants,
            )
        }
    }
}
