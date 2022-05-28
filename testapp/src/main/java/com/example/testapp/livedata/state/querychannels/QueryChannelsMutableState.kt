package com.example.testapp.livedata.state.querychannels

import android.util.Log
import com.example.testapp.client.api.models.FilterObject
import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.client.models.Channel
import com.example.testapp.client.models.User
import com.example.testapp.livedata.extensions.updateUsers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*


internal class QueryChannelsMutableState(
    override val filter: FilterObject,
//    override val sort: QuerySort<Chat>,
    scope: CoroutineScope,
    latestUsers: StateFlow<Map<String, User>>,
) : QueryChannelsState {

    //    internal val queryChannelsSpec: QueryChannelsSpec = QueryChannelsSpec(filter, sort)
    internal val _channels = MutableStateFlow<Map<String, Channel>>(emptyMap())
    internal val _loading = MutableStateFlow(false)

    //    internal val _loadingMore = MutableStateFlow(false)
    internal val _sortedChannels =
        _channels.combine(latestUsers) { channelMap, userMap ->
            channelMap.values.updateUsers(userMap)
        }
//            .map { it.sortedWith(sort.comparator) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    internal val _currentRequest = MutableStateFlow<QueryChannelsRequest?>(null)

    //    internal val _recoveryNeeded: MutableStateFlow<Boolean> = MutableStateFlow(false)
    internal val channelsOffset: MutableStateFlow<Int> = MutableStateFlow(0)

    /** Instance of [ChatEventHandler] that handles logic of event handling for this [QueryChannelsMutableState]. */
//    override var chatEventHandler: ChatEventHandler? = null

//    override val recoveryNeeded: StateFlow<Boolean> = _recoveryNeeded

    /**
     * Non-nullable property of [ChatEventHandler] to ensure we always have some handler to handle events. Returns
     * handler set by user or default one if there is no.
     */
//    internal val eventHandler: ChatEventHandler
//        get() = chatEventHandler ?: DefaultChatEventHandler(_sortedChannels)

    override val currentRequest: StateFlow<QueryChannelsRequest?> = _currentRequest
    override val loading: StateFlow<Boolean> = _loading
    override val channels: StateFlow<List<Channel>> = _sortedChannels
    override val channelsStateData: StateFlow<ChannelsStateData> =
        _loading.combine(_sortedChannels) { loading: Boolean, channels: List<Channel> ->
            when {
                loading -> ChannelsStateData.Loading
                channels.isEmpty() -> ChannelsStateData.OfflineNoResults
                else -> ChannelsStateData.Result(channels)
            }
        }.stateIn(scope, SharingStarted.Eagerly, ChannelsStateData.NoQueryActive)

//    override val nextPageRequest: StateFlow<QueryChannelsRequest?> =
//        currentRequest.combine(channelsOffset) { currentRequest, currentOffset ->
//            currentRequest?.copy(offset = currentOffset)
//        }.stateIn(scope, SharingStarted.Eagerly, null)
}

internal fun QueryChannelsState.toMutableState(): QueryChannelsMutableState =
    this as QueryChannelsMutableState
