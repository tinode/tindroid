package com.example.testapp.client.events

import co.tinode.tinsdk.Tinode
import com.example.testapp.client.clientstate.DisconnectCause
import com.example.testapp.client.models.Message
import com.example.testapp.client.models.User
import com.example.testapp.core.client.errors.ChatError
import java.util.Date

public sealed class ChatEvent {
    public abstract val type: String
    public abstract val createdAt: Date
}

public sealed class TopicEvent : ChatEvent() {
    public abstract val topic: String
}

public sealed interface UserEvent {
    public val user: User
}

public sealed interface HasMessage {
    public val message: Message
}

public sealed interface HasOwnUser {
    public val me: User
}

/**
 * Interface that marks a [ChatEvent] as having the information about unread counts.
 */
public sealed interface HasUnreadCounts {
    public val totalUnreadCount: Int
}

public sealed class MeEvent : TopicEvent() {
    public override val topic: String = Tinode.TOPIC_ME
}

/**
 * Triggered when a user channels subscriptions has updated
 */
public data class UserChannelsUpdatedEvent(
    override val type: String,
    override val createdAt: Date,
) : MeEvent()

/**
 * Triggered when a new message is added on a channel.
 */
public data class NewMessageEvent(
    override val type: String,
    override val createdAt: Date,
    override val topic: String,
    override val user: User,
    override val message: Message,
    override val totalUnreadCount: Int = 0,
) : TopicEvent(), UserEvent, HasMessage, HasUnreadCounts

/**
 * Triggered when a user is updated
 */
public data class UserUpdatedEvent(
    override val type: String,
    override val createdAt: Date,
    override val user: User,
) : ChatEvent(), UserEvent

/**
 * Triggered when a user gets login to the WS
 */
public data class UserLoginEvent(
    override val type: String,
    override val createdAt: Date,
) : ChatEvent()

/**
 * Triggered when a user gets connected to the WS
 */
public data class ConnectedEvent(
    override val type: String,
    override val createdAt: Date,
    override val me: User,
) : ChatEvent(), HasOwnUser

/**
 * Triggered when a user is connecting to the WS
 */
public data class ConnectingEvent(
    override val type: String,
    override val createdAt: Date,
) : ChatEvent()

/**
 * Triggered when a user gets disconnected to the WS
 */
public data class DisconnectedEvent(
    override val type: String,
    override val createdAt: Date,
    val disconnectCause: DisconnectCause = DisconnectCause.NetworkNotAvailable,
) : ChatEvent()

/**
 * Triggered when WS connection emits error
 */
public data class ErrorEvent(
    override val type: String,
    override val createdAt: Date,
    val error: ChatError,
) : ChatEvent()