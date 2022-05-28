package com.example.testapp.livedata.state

import com.example.testapp.client.api.models.FilterObject
import com.example.testapp.client.models.User
import com.example.testapp.core.internal.InternalTinUiApi
import com.example.testapp.livedata.state.channel.ChannelMutableState
import com.example.testapp.livedata.state.channel.ChannelState
import com.example.testapp.livedata.state.querychannels.QueryChannelsMutableState
import com.example.testapp.livedata.state.querychannels.QueryChannelsState
import com.example.testapp.logger.ChatLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

@InternalTinUiApi
/** Registry of all state objects exposed in offline plugin. */
public class StateRegistry internal constructor(
    private val userStateFlow: StateFlow<User?>,
    private var latestUsers: StateFlow<Map<String, User>>,
    internal val job: Job,
    @property:InternalTinUiApi public val scope: CoroutineScope,
) {
    private var queryChannels: ConcurrentHashMap<FilterObject, QueryChannelsMutableState> =
        ConcurrentHashMap()
    private val channels: ConcurrentHashMap<String, ChannelMutableState> = ConcurrentHashMap()

    public fun queryChannels(filter: FilterObject): QueryChannelsState {
        return queryChannels.getOrPut(filter) {
            QueryChannelsMutableState(filter, scope, latestUsers)
        }
    }

    /**
     * Returns [ChannelState] that represents a state of particular channel.
     *
     * @param channelType The channel type. ie p2p.
     * @param channelId The channel id. ie user_123.
     *
     * @return [ChannelState] object.
     */
    public fun channel(channelId: String): ChannelState {
        return channels.getOrPut(channelId) {
            ChannelMutableState(
                channelId,
                scope,
                userStateFlow,
                latestUsers
            )
        }
    }

    internal fun getActiveChannelStates(): List<ChannelState> = channels.values.toList()

    /**
     * Clear state of all state objects.
     */
    public fun clear() {
        job.cancelChildren()
        queryChannels.clear()
        channels.clear()
        instance = null
    }

    internal companion object {
        private var instance: StateRegistry? = null

        private val logger = ChatLogger.get("StateRegistry")

        /**
         * Creates and returns a new instance of StateRegistry.
         *
         * @param job A background job cancelled after calling [clear].
         * @param scope A scope for new coroutines.
         * @param userStateFlow The state flow that provides the user once it is set.
         * @param latestUsers Latest users of the SDK.
         *
         * @return Instance of [StateRegistry].
         *
         * @throws IllegalStateException if instance is not null.
         */
        internal fun create(
            job: Job,
            scope: CoroutineScope,
            userStateFlow: StateFlow<User?>,
            latestUsers: StateFlow<Map<String, User>>,
        ): StateRegistry {
            if (instance != null) {
                logger.logE(
                    "StateRegistry instance is already created. " +
                            "Avoid creating multiple instances to prevent ambiguous state. Use StateRegistry.get()"
                )
            }
            return StateRegistry(
                job = job,
                scope = scope,
                userStateFlow = userStateFlow,
                latestUsers = latestUsers,
            ).also { stateRegistry ->
                instance = stateRegistry
            }
        }

        /**
         * Gets the current Singleton of StateRegistry. If the initialization is not set yet, it throws exception.
         *
         * @return Singleton instance of [StateRegistry].
         *
         * @throws IllegalArgumentException if instance is null.
         */
        @Throws(IllegalArgumentException::class)
        internal fun get(): StateRegistry = requireNotNull(instance) {
            "Offline plugin must be configured in ChatClient. You must provide StreamOfflinePluginFactory as a " +
                    "PluginFactory to be able to use LogicRegistry and StateRegistry from the SDK"
        }
    }
}
