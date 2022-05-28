package com.example.testapp.livedata.state.global

import com.example.testapp.client.models.TypingEvent
import com.example.testapp.client.models.User
import com.example.testapp.core.client.errors.ChatError
import com.example.testapp.livedata.model.ConnectionState
import com.example.testapp.livedata.utils.Event
import kotlinx.coroutines.flow.StateFlow

/**
 * Global state of the application.
 */
public sealed interface GlobalState {
    /** The current user on the state object */
    public val user: StateFlow<User?>

    /** if the client connection has been initialized */
    public val initialized: StateFlow<Boolean>

    /**
     * StateFlow<ConnectionState> that indicates if we are currently online, connecting of offline.
     */
    public val connectionState: StateFlow<ConnectionState>

    /**
     * the number of unread channels for the current user.
     */
    public val channelUnreadCount: StateFlow<Int>

    /**
     * The total unread message count for the current user.
     * Depending on your app you'll want to show this or the channelUnreadCount.
     */
    public val totalUnreadCount: StateFlow<Int>

    /**
     * The error event state flow object is triggered when errors in the underlying components occur.
     * The following example shows how to observe these errors
     *
     *  repo.errorEvent.collect {
     *       // create a toast
     *   }
     */
    public val errorEvents: StateFlow<Event<ChatError>>

    /**
     * Updates about currently typing users in active channels. See [TypingEvent].
     */
    public val typingUpdates: StateFlow<TypingEvent>

    /**
     * If the user is online or not.
     *
     * @return True if the user is online otherwise False.
     */
    public fun isOnline(): Boolean

    /**
     * If the user is offline or not.
     *
     * @return True if the user is offline otherwise False.
     */
    public fun isOffline(): Boolean

    /**
     * If connection is in connecting state.
     *
     * @return True if the connection is in connecting state.
     */
    public fun isConnecting(): Boolean

    /**
     * If domain state is initialized or not.
     *
     * @return True if initialized otherwise False.
     */
    public fun isInitialized(): Boolean


    /**
     * Clears the state of [GlobalState].
     */
    public fun clearState()
}