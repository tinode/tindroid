package com.example.testapp.livedata.extensions

import com.example.testapp.client.ChatClient
import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.core.internal.coroutines.DispatcherProvider
import com.example.testapp.livedata.logic.LogicRegistry
import com.example.testapp.livedata.state.ChatClientStateCalls
import com.example.testapp.livedata.state.global.GlobalMutableState
import com.example.testapp.livedata.state.global.GlobalState
import com.example.testapp.livedata.state.querychannels.QueryChannelsState
import com.example.testapp.livedata.state.StateRegistry
import com.example.testapp.livedata.state.channel.ChannelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [StateRegistry] instance that contains all state objects exposed in offline plugin.
 * The instance is being initialized after connecting the user!
 *
 * @throws IllegalArgumentException If the state was not initialized yet.
 */
public val ChatClient.state: StateRegistry
    @Throws(IllegalArgumentException::class)
    get() = requireNotNull(StateRegistry.get()) {
        "LivaData plugin must be configured in ChatClient. You must provide LivaDataPluginFactory as a " +
            "PluginFactory to be able to use LogicRegistry and StateRegistry from the SDK"
    }

/**
 * [GlobalState] instance that contains information about the current user, unreads, etc.
 */
public val ChatClient.globalState: GlobalState
    get() = GlobalMutableState.getOrCreate()

/**
 * [LogicRegistry] instance that contains all objects responsible for handling logic in offline plugin.
 */
internal val ChatClient.logic: LogicRegistry
    get() = requireNotNull(LogicRegistry.get()) {
        "LivaData plugin must be configured in ChatClient. You must provide LivaDataPluginFactory as a " +
                "PluginFactory to be able to use LogicRegistry and StateRegistry from the SDK"
    }

/**
 * Intermediate class to request ChatClient class as states
 *
 * @return [ChatClientStateCalls]
 */
internal fun ChatClient.requestsAsState(scope: CoroutineScope): ChatClientStateCalls =
    ChatClientStateCalls(this, state, scope)


/**
 * Performs [ChatClient.queryChannels] under the hood and returns [QueryChannelsState] associated with the query.
 * The [QueryChannelsState] cannot be created before connecting the user therefore, the method returns a StateFlow
 * that emits a null when the user has not been connected yet and the new value every time the user changes.
 *
 * @param request The request's parameters combined into [QueryChannelsRequest] class.
 * @param coroutineScope The [CoroutineScope] used for executing the request.
 *
 * @return A StateFlow object that emits a null when the user has not been connected yet and the new [QueryChannelsState] when the user changes.
 */
@JvmOverloads
public fun ChatClient.queryChannelsAsState(
    request: QueryChannelsRequest,
    coroutineScope: CoroutineScope = CoroutineScope(DispatcherProvider.IO),
): StateFlow<QueryChannelsState?> {
    return getStateOrNull(coroutineScope) {
        requestsAsState(coroutineScope).queryChannels(request)
    }
}

/**
 * Performs [ChatClient.queryChannel] with watch = true under the hood and returns [ChannelState] associated with the query.
 * The [ChannelState] cannot be created before connecting the user therefore, the method returns a StateFlow
 * that emits a null when the user has not been connected yet and the new value every time the user changes.
 *
 * @param cid The full channel id, i.e. "messaging:123"
 * @param messageLimit The number of messages that will be initially loaded.
 * @param coroutineScope The [CoroutineScope] used for executing the request.
 *
 * @return A StateFlow object that emits a null when the user has not been connected yet and the new [ChannelState] when the user changes.
 */
@JvmOverloads
public fun ChatClient.watchChannelAsState(
    cid: String,
    messageLimit: Int,
    coroutineScope: CoroutineScope = CoroutineScope(DispatcherProvider.IO),
): StateFlow<ChannelState?> {
    return getStateOrNull(coroutineScope) {
        requestsAsState(coroutineScope).watchChannel(cid, messageLimit)
    }
}

/**
 * Provides an ease-of-use piece of functionality that checks if the user is available or not. If it's not, we don't emit
 * any state, but rather return an empty StateFlow.
 *
 * If the user is set, we fetch the state using the provided operation and provide it to the user.
 */
private fun <T> ChatClient.getStateOrNull(
    coroutineScope: CoroutineScope,
    producer: () -> T,
): StateFlow<T?> {
    return globalState.user.map { it?.id }.distinctUntilChanged().map { userId ->
        if (userId == null) {
            null
        } else {
            producer()
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, null)
}
