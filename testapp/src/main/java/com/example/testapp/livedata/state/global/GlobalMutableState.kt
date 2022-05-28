package com.example.testapp.livedata.state.global

import com.example.testapp.client.models.TypingEvent
import com.example.testapp.client.models.User
import com.example.testapp.core.client.errors.ChatError
import com.example.testapp.livedata.model.ConnectionState
import com.example.testapp.livedata.utils.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class GlobalMutableState private constructor() : GlobalState {

    internal val _initialized = MutableStateFlow(false)
    internal val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    internal val _totalUnreadCount = MutableStateFlow(0)
    internal val _channelUnreadCount = MutableStateFlow(0)
    internal val _errorEvent = MutableStateFlow(Event(ChatError()))


    internal val _typingChannels = MutableStateFlow(TypingEvent("", emptyList()))

    internal val _user = MutableStateFlow<User?>(null)

    override val user: StateFlow<User?> = _user

    override val initialized: StateFlow<Boolean> = _initialized

    override val connectionState: StateFlow<ConnectionState> = _connectionState

    override val totalUnreadCount: StateFlow<Int> = _totalUnreadCount

    override val channelUnreadCount: StateFlow<Int> = _channelUnreadCount

    override val errorEvents: StateFlow<Event<ChatError>> = _errorEvent

    override val typingUpdates: StateFlow<TypingEvent> = _typingChannels


    override fun isOnline(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    override fun isOffline(): Boolean = _connectionState.value == ConnectionState.OFFLINE

    override fun isConnecting(): Boolean = _connectionState.value == ConnectionState.CONNECTING

    override fun isInitialized(): Boolean {
        return _initialized.value
    }

    internal companion object {
        private var instance: GlobalMutableState? = null

        /**
         * Gets the singleton of [GlobalMutableState] or creates it in the first call.
         */
        internal fun getOrCreate(): GlobalMutableState {
            return instance ?: GlobalMutableState().also { globalState ->
                instance = globalState
            }
        }

        /**
         * Gets the current Singleton of GlobalState. If the initialization is not done yet, it returns null.
         */
        @Throws(IllegalArgumentException::class)
        internal fun get(): GlobalMutableState = requireNotNull(instance) {
            "LiveData plugin must be configured in ChatClient. You must provide LivaDataPluginFactory as a " +
                    "PluginFactory to be able to use GlobalState from the SDK"
        }
    }

    override fun clearState() {
        _initialized.value = false
        _connectionState.value = ConnectionState.OFFLINE
        _totalUnreadCount.value = 0
        _channelUnreadCount.value = 0

        _user.value = null
    }
}

internal fun GlobalState.toMutableState(): GlobalMutableState = this as GlobalMutableState