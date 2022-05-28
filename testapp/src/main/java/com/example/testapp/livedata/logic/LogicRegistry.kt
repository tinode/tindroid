package com.example.testapp.livedata.logic

import com.example.testapp.client.ChatClient
import com.example.testapp.client.api.models.FilterObject
import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.livedata.state.channel.toMutableState
import com.example.testapp.livedata.state.StateRegistry
import com.example.testapp.livedata.state.global.GlobalMutableState
import com.example.testapp.livedata.state.global.toMutableState
import com.example.testapp.livedata.state.querychannels.toMutableState
import com.example.testapp.logger.ChatLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry-container for logic objects related to:
 * 1. Query channels
 * 2. Query channel
 * 3. Query thread
 */
internal class LogicRegistry internal constructor(
    private val stateRegistry: StateRegistry,
    private val globalState: GlobalMutableState,
    private val userPresence: Boolean,
    private val client: ChatClient,
) {

    private val queryChannels: ConcurrentHashMap<FilterObject, QueryChannelsLogic> =
        ConcurrentHashMap()
    private val channels: ConcurrentHashMap<String, ChannelLogic> = ConcurrentHashMap()

    fun queryChannels(filter: FilterObject): QueryChannelsLogic {
        return queryChannels.getOrPut(filter) {
            QueryChannelsLogic(
                stateRegistry.queryChannels(filter).toMutableState(),
                client,
                GlobalMutableState.get().toMutableState()
            )
        }
    }

    /** Returns [QueryChannelsLogic] accordingly to [QueryChannelsRequest]. */
    fun queryChannels(queryChannelsRequest: QueryChannelsRequest): QueryChannelsLogic =
        queryChannels(queryChannelsRequest.filter)

    /** Returns [ChannelLogic] by channelType and channelId combination. */
    fun channel(channelId: String): ChannelLogic {
        return channels.getOrPut(channelId) {
            ChannelLogic(
                mutableState = stateRegistry.channel(channelId).toMutableState(),
                globalMutableState = globalState,
                userPresence = userPresence
            )
        }
    }

    /**
     * Returns a list of [QueryChannelsLogic] for all, active query channel requests.
     *
     * @return List of [QueryChannelsLogic].
     */
    fun getActiveQueryChannelsLogic(): List<QueryChannelsLogic> = queryChannels.values.toList()

    /**
     * Checks if the channel is active by checking if [ChannelLogic] exists.
     *
     * @param channelId The channel id. ie 123.
     *
     * @return True if the channel is active.
     */
    fun isActiveChannel(channelId: String): Boolean =
        channels.containsKey(channelId)

    /**
     * Returns a list of [ChannelLogic] for all, active channel requests.
     *
     * @return List of [ChannelLogic].
     */
    fun getActiveChannelsLogic(): List<ChannelLogic> = channels.values.toList()

    /**
     * Clears all stored logic objects.
     */
    fun clear() {
        queryChannels.clear()
        channels.clear()
//        threads.clear()
        instance = null
    }

    internal companion object {
        private var instance: LogicRegistry? = null

        private val logger = ChatLogger.get("LogicRegistry")

        /**
         * Creates and returns new instance of LogicRegistry.
         *
         * @param stateRegistry [StateRegistry].
         * @param globalState [GlobalMutableState] state of the SDK.
         * @param userPresence True if userPresence should be enabled, false otherwise.
         * @param repos [RepositoryFacade] to interact with local data sources.
         * @param client An instance of [ChatClient].
         *
         * @return Instance of [LogicRegistry].
         *
         * @throws IllegalStateException if instance is not null.
         */
        internal fun create(
            stateRegistry: StateRegistry,
            globalState: GlobalMutableState,
            userPresence: Boolean,
//            repos: RepositoryFacade,
            client: ChatClient,
        ): LogicRegistry {
            if (instance != null) {
                logger.logE(
                    "LogicRegistry instance is already created. " +
                        "Avoid creating multiple instances to prevent ambiguous state. Use LogicRegistry.get()"
                )
            }
            return LogicRegistry(stateRegistry, globalState, userPresence, client).also { logicRegistry ->
                instance = logicRegistry
            }
        }

        /**
         * Gets the current Singleton of LogicRegistry. If the initialization is not set yet, it throws exception.
         *
         * @return Singleton instance of [LogicRegistry].
         *
         * @throws IllegalArgumentException if instance is null.
         */
        @Throws(IllegalArgumentException::class)
        internal fun get(): LogicRegistry = requireNotNull(instance) {
            "Offline plugin must be configured in ChatClient. You must provide StreamOfflinePluginFactory as a " +
                "PluginFactory to be able to use LogicRegistry and StateRegistry from the SDK"
        }
    }
}
