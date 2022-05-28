package com.example.testapp.components.common.extensions

import com.example.testapp.client.models.Message
import java.util.Date

public fun Message.isDeleted(): Boolean = deletedAt != null

//public fun Message.isFailed(): Boolean {
//    return this.syncStatus == SyncStatus.FAILED_PERMANENTLY || this.type == ModelType.message_error
//}

//public fun Message.isInThread(): Boolean = !parentId.isNullOrEmpty()

//public fun Message.hasNoAttachments(): Boolean = attachments.isEmpty()
//
//public fun Message.isRegular(): Boolean = type == ModelType.message_regular
//
//public fun Message.isEphemeral(): Boolean = type == ModelType.message_ephemeral
//
//public fun Message.isSystem(): Boolean = type == ModelType.message_system
//
//public fun Message.isError(): Boolean = type == ModelType.message_error
//
//public fun Message.isGiphyEphemeral(): Boolean = isEphemeral() && command == ModelType.attach_giphy
//
//public fun Message.isGiphyNotEphemeral(): Boolean = isEphemeral().not() && command == ModelType.attach_giphy

public fun Message.getCreatedAtOrNull(): Date? = createdAt ?: createdLocallyAt

public fun Message.getUpdatedAtOrNull(): Date? = updatedAt ?: updatedLocallyAt

public fun Message.getCreatedAtOrThrow(): Date = checkNotNull(getCreatedAtOrNull()) {
    "a message needs to have a non null value for either createdAt or createdLocallyAt"
}

public fun Message.isReply(): Boolean = replyTo != null

public fun Message.hasText(): Boolean = text.isNotEmpty()
