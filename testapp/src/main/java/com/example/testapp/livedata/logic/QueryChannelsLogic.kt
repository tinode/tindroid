package com.example.testapp.livedata.logic

import com.example.testapp.client.ChatClient
import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.client.events.ChatEvent
import com.example.testapp.client.events.UserChannelsUpdatedEvent
import com.example.testapp.client.models.Channel
import com.example.testapp.core.client.call.await
import com.example.testapp.core.client.errors.ChatError
import com.example.testapp.livedata.extensions.logic
import com.example.testapp.livedata.state.global.GlobalMutableState
import com.example.testapp.livedata.state.querychannels.QueryChannelsMutableState
import com.example.testapp.livedata.state.querychannels.QueryChannelsState
import com.example.testapp.logger.ChatLogger
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.testapp.core.client.utils.Result
import com.example.testapp.livedata.utils.Event

internal class QueryChannelsLogic(
    private val mutableState: QueryChannelsMutableState,
    private val client: ChatClient,
    private val globalState: GlobalMutableState,
) {

    private val logger = ChatLogger.get("QueryChannelsLogic")

    /**
     * Returns the state of Channel. Useful to check how it the state of the channel of the [QueryChannelsLogic]
     *
     * @return [QueryChannelsState]
     */
    internal fun state(): QueryChannelsState {
        return mutableState
    }

    /**
     * Adds a new channel to the query.
     *
     * @param channel [Channel]
     */
    internal suspend fun addChannel(channel: Channel) {
        addChannels(listOf(channel))
        client.logic.channel(channel.id).updateDataFromChannel(channel)
    }

    private suspend fun addChannels(channels: List<Channel>) {
        mutableState._channels.value = mutableState._channels.value + channels.map { it.id to it }
    }

    suspend fun onQueryChannelsPrecondition(request: QueryChannelsRequest): Result<Unit> {
        return if (mutableState._loading.value) {
            logger.logI("Another request to load channels is in progress. Ignoring this request.")
            Result.error(ChatError("Another request to load messages is in progress. Ignoring this request."))
        } else {
            Result.success(Unit)
        }
    }

    suspend fun onQueryChannelsRequest(request: QueryChannelsRequest) {
        mutableState._currentRequest.value = request

        val loading = mutableState._loading

        if (loading.value) {
            logger.logI("Another query channels request is in progress. Ignoring this request.")
            return
        }

        loading.value = true
    }

    suspend fun onQueryChannelsResult(
        result: Result<List<Channel>>,
        request: QueryChannelsRequest
    ) {
        onOnlineQueryResult(result, request, globalState)
        if (result.isSuccess) {
            updateOnlineChannels(result.data())
        }
        mutableState._loading.value = false
    }

    internal suspend fun runQueryOnline(request: QueryChannelsRequest): Result<List<Channel>> {
        return client.queryChannelsInternal(request).await()
            .also { onQueryChannelsResult(it, request) }
    }

    private suspend fun onOnlineQueryResult(
        result: Result<List<Channel>>,
        request: QueryChannelsRequest,
        globalState: GlobalMutableState,
    ) {
        if (result.isSuccess) {
//            mutableState._recoveryNeeded.value = false

            // store the results in the database
            val channelsResponse = result.data().toSet()
//            mutableState._endOfChannels.value = true
            // first things first, store the configs
            logger.logI("api call returned ${channelsResponse.size} channels")
        } else {
            logger.logI("Query with filter ${request.filter} failed, marking it as recovery needed")
//            mutableState._recoveryNeeded.value = true
            globalState._errorEvent.value = Event(result.error())
        }
    }

    internal fun loadingForCurrentRequest(): MutableStateFlow<Boolean> {
        return mutableState._loading
    }

    /**
     * Updates the state based on the channels collection we received from the API.
     *
     * @param channels The list of channels to update.
     */
    internal suspend fun updateOnlineChannels(
        channels: List<Channel>,
    ) {
        mutableState.channelsOffset.value += channels.size
        channels.forEach { client.logic.channel(it.id).updateDataFromChannel(it) }
        addChannels(channels)
    }

    internal suspend fun removeChannel(cid: String) =
        removeChannels(listOf(cid))

    private suspend fun removeChannels(cidList: List<String>) {
        mutableState._channels.value = mutableState._channels.value - cidList
    }

    /**
     * Handles events received from the socket.
     *
     * @see [handleEvent]
     */
    internal suspend fun handleEvents(events: List<ChatEvent>) {
        for (event in events) {
            handleEvent(event)
        }
    }

    /**
     * Handles event received from the socket.
     * Responsible for synchronizing [QueryChannelsMutableState].
     */
    internal suspend fun handleEvent(event: ChatEvent) {
        // update the info for that channel from the channel repo
        logger.logI("Received channel event $event")

        if (event is UserChannelsUpdatedEvent) {
            mutableState._currentRequest.value?.let {
                val result = runQueryOnline(it)
                if (result.isSuccess) {
                    updateOnlineChannels(result.data())
                }
            }
        }

//        val cachedChannel = if (event is CidEvent) {
//            channelRepository.selectChannelWithoutMessages(event.cid)
//        } else null
//
//        when (val handlingResult = mutableState.eventHandler.handleChatEvent(event, mutableState.filter, cachedChannel)) {
//            is EventHandlingResult.Add -> addChannel(handlingResult.channel)
//            is EventHandlingResult.Remove -> removeChannel(handlingResult.cid)
//            is EventHandlingResult.Skip -> Unit
//        }
//
//        if (event is MarkAllReadEvent) {
//            refreshAllChannels()
//        }
//
//        if (event is CidEvent) {
//            // skip events that are typically not impacting the query channels overview
//            if (event is UserStartWatchingEvent || event is UserStopWatchingEvent) {
//                return
//            }
//            refreshChannel(event.cid)
//        }
//
//        if (event is UserPresenceChangedEvent) {
//            refreshMembersStateForUser(event.user)
//        }
    }

}
