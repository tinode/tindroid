package com.example.testapp.components.channel.list.viewmodel

import android.util.Log
import androidx.lifecycle.*
import com.example.testapp.client.ChatClient
import com.example.testapp.client.api.models.FilterObject
import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.client.models.Channel
import com.example.testapp.client.models.Filters
import com.example.testapp.client.models.TypingEvent
import com.example.testapp.common.utils.extensions.defaultChannelListFilter
import com.example.testapp.core.client.errors.ChatError
import com.example.testapp.livedata.extensions.globalState
import com.example.testapp.livedata.extensions.queryChannelsAsState
import com.example.testapp.livedata.state.global.GlobalState
import com.example.testapp.livedata.state.querychannels.ChannelsStateData
import com.example.testapp.livedata.state.querychannels.QueryChannelsState
import com.example.testapp.livedata.utils.Event
import com.example.testapp.logger.ChatLogger
import com.example.testapp.logger.TaggedLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel class for [ChannelListView].
 * Responsible for keeping the channels list up to date.
 * Can be bound to the view using [ChannelListViewModel.bindView] function.
 *
 * @param filter Filter for querying channels, should never be empty.
 * @param sort Defines the ordering of the channels.
 * @param messageLimit The number of messages to fetch for each channel.
 * @param memberLimit The number of members to fetch per channel.
 * @param chatEventHandlerFactory The instance of [ChatEventHandlerFactory] that will be used to create [ChatEventHandler].
 */
public class ChannelListViewModel(
    private val filter: FilterObject? = null,
//    private val sort: QuerySort<Chat> = DEFAULT_SORT,
    private val messageLimit: Int = 1,
    private val memberLimit: Int = 30,
//    private val chatEventHandlerFactory: ChatEventHandlerFactory = ChatEventHandlerFactory(),
    private val chatClient: ChatClient = ChatClient.instance(),
    private val globalState: GlobalState = chatClient.globalState
) : ViewModel() {

    /**
     * Represents the current state containing channel list
     * information that is a product of multiple sources.
     */
    private val stateMerger = MediatorLiveData<State>()

    /**
     * Represents the current state containing channel list information.
     */
    public val state: LiveData<State> = stateMerger

    /**
     * Updates about currently typing users in active channels. See [TypingEvent].
     */
    public val typingEvents: LiveData<TypingEvent>
        get() = globalState.typingUpdates.asLiveData()

    /**
     * Represents the current pagination state that is a product
     * of multiple sources.
     */
    private val paginationStateMerger = MediatorLiveData<PaginationState>()

    /**
     * Represents the current pagination state by containing
     * information about the loading state and if we have
     * reached the end of all available channels.
     */
    public val paginationState: LiveData<PaginationState> = Transformations.distinctUntilChanged(paginationStateMerger)

    /**
     * Used to update and emit error events.
     */
    private val _errorEvents: MutableLiveData<Event<ErrorEvent>> = MutableLiveData()

    /**
     * Emits error events.
     */
    public val errorEvents: LiveData<Event<ErrorEvent>> = _errorEvents

    /**
     * The logger used to print information, warnings, errors, etc. to log.
     */
    private val logger: TaggedLogger = ChatLogger.get("ChannelListViewModel")

    /**
     * Filters the requested channels.
     */
    private val filterLiveData: LiveData<FilterObject?> =
        filter?.let(::MutableLiveData) ?: globalState.user.map(Filters::defaultChannelListFilter)
            .asLiveData()

    /**
     * Represents the current state of the channels query.
     */
    private var queryChannelsState: StateFlow<QueryChannelsState?> = MutableStateFlow(null)

    init {
        stateMerger.addSource(filterLiveData) { filter ->
            if (filter != null) {
                initData(filter)
            }
        }
    }


    private fun initData(filterObject: FilterObject) {
        stateMerger.value = INITIAL_STATE
        init(filterObject)
    }

    /**
     * Initializes this ViewModel. It makes the initial query to request channels
     * and starts to observe state changes.
     */
    private fun init(filterObject: FilterObject) {
        queryChannelsState = chatClient.queryChannelsAsState(QueryChannelsRequest(filterObject), viewModelScope)
        viewModelScope.launch {
            queryChannelsState.filterNotNull().collectLatest { queryChannelsState ->
//                queryChannelsState.chatEventHandler = chatEventHandlerFactory.chatEventHandler(queryChannelsState.channels)
                stateMerger.addSource(queryChannelsState.channelsStateData.asLiveData()) { channelsState ->
                    stateMerger.value = handleChannelStateNews(channelsState)
                    Log.d("s", stateMerger.value.toString())
                }
//                stateMerger.addSource(globalState.channelMutes.asLiveData()) { channelMutes ->
//                    val state = stateMerger.value
//
//                    if (state?.channels?.isNotEmpty() == true) {
//                        stateMerger.value = state.copy(channels = parseMutedChannels(state.channels, channelMutes))
//                    } else {
//                        stateMerger.value = state?.copy()
//                    }
//                }

//                paginationStateMerger.addSource(queryChannelsState.loadingMore.asLiveData()) { loadingMore ->
//                    setPaginationState { copy(loadingMore = loadingMore) }
//                }
//                paginationStateMerger.addSource(queryChannelsState.endOfChannels.asLiveData()) { endOfChannels ->
//                    setPaginationState { copy(endOfChannels = endOfChannels) }
//                }
            }
        }
    }

    /**
     * Handles update about [ChannelsStateData] changes and emit new [State].
     *
     * @param channelState Current state of the channels query.
     * @param channelMutes List of muted channels.
     *
     * @return New [State] after handling channels state changes.
     */
    private fun handleChannelStateNews(
        channelState: ChannelsStateData,
//        channelMutes: List<ChannelMute>,
    ): State {
        return when (channelState) {
            is ChannelsStateData.NoQueryActive,
            is ChannelsStateData.Loading,
            -> State(isLoading = true, emptyList())
            is ChannelsStateData.OfflineNoResults -> State(
                isLoading = false,
                channels = emptyList(),
            )
            is ChannelsStateData.Result -> State(
                isLoading = false,
                channels = channelState.channels,
//                chats = parseMutedChannels(channelState.channels, channelMutes),
            )
        }
    }

    public fun onAction(action: Action) {
        when (action) {
            is Action.ReachedEndOfList -> requestMoreChannels()
        }
    }

    /**
     * Removes the current user from the channel.
     *
     * @param channel The channel that the current user will leave.
     */
    public fun leaveChannel(channel: Channel) {
//        chatClient.getCurrentUser()?.let { user ->
//            chatClient.removeMembers(channel.type, channel.id, listOf(user.id)).enqueue(
//                onError = { chatError ->
//                    logger.logE("Could not leave channel with id: ${channel.id}. Error: ${chatError.message}. Cause: ${chatError.cause?.message}")
//                    _errorEvents.postValue(Event(ErrorEvent.LeaveChannelError(chatError)))
//                }
//            )
//        }
    }

    /**
     * Deletes a channel.
     *
     * @param channel Channel to be deleted.
     */
    public fun deleteChannel(channel: Channel) {
//        chatClient.channel(channel.cid).delete().enqueue(
//            onError = { chatError ->
//                logger.logE("Could not delete channel with id: ${channel.id}. Error: ${chatError.message}. Cause: ${chatError.cause?.message}")
//                _errorEvents.postValue(Event(ErrorEvent.DeleteChannelError(chatError)))
//            }
//        )
    }

    public fun hideChannel(channel: Channel) {
//        val keepHistory = true
//        chatClient.hideChannel(channelType, channelId, !keepHistory).enqueue(
//            onError = { chatError ->
//                logger.logE("Could not hide channel with id: ${channel.id}. Error: ${chatError.message}. Cause: ${chatError.cause?.message}")
//                _errorEvents.postValue(Event(ErrorEvent.HideChannelError(chatError)))
//            }
//        )
    }

    public fun markAllRead() {
//        chatClient.markAllRead().enqueue(
//            onError = { chatError ->
//                logger.logE("Could not mark all messages as read. Error: ${chatError.message}. Cause: ${chatError.cause?.message}")
//            }
//        )
    }

    private fun requestMoreChannels() {
//        filterLiveData.value?.let { filter ->
//            queryChannelsState?.nextPageRequest?.value?.let {
//                viewModelScope.launch {
//                    chatClient.queryChannels(it).enqueue(
//                        onError = { chatError ->
//                            logger.logE("Could not load more channels. Error: ${chatError.message}. Cause: ${chatError.cause?.message}")
//                        }
//                    )
//                }
//            }
//        }
    }

    private fun setPaginationState(reducer: PaginationState.() -> PaginationState) {
        paginationStateMerger.value = reducer(paginationStateMerger.value ?: PaginationState())
    }

    public data class State(val isLoading: Boolean, val channels: List<Channel>)

//    private fun parseMutedChannels(
//        channels: List<Chat>,
//        channelMutes: List<ChannelMute>,
//    ): List<Chat> {
//        val mutedChannelsIds = channelMutes.map { channelMute -> channelMute.channel.id }.toSet()
//        return channels.map { channel ->
//            when {
//                channel.isMuted != channel.id in mutedChannelsIds ->
//                    channel.copy(extraData = channel.extraData.clone(EXTRA_DATA_MUTED, !channel.isMuted))
//
//                else -> channel
//            }
//        }
//    }

    private fun <K, V> Map<K, V>.clone(changeKey: K, changeValue: V): MutableMap<K, V> {
        val originalMap = this

        return mutableMapOf<K, V>().apply {
            putAll(originalMap)
            put(changeKey, changeValue)
        }
    }

    public data class PaginationState(
        val loadingMore: Boolean = false,
        val endOfChannels: Boolean = false,
    )

    public sealed class Action {
        public object ReachedEndOfList : Action()
    }

    public sealed class ErrorEvent(public open val chatError: ChatError) {
        public data class LeaveChannelError(override val chatError: ChatError) : ErrorEvent(chatError)
        public data class DeleteChannelError(override val chatError: ChatError) : ErrorEvent(chatError)
        public data class HideChannelError(override val chatError: ChatError) : ErrorEvent(chatError)
    }

    public companion object {
//        @JvmField
//        public val DEFAULT_SORT: QuerySort<Chat> = QuerySort.desc("last_updated")

        private val INITIAL_STATE: State = State(isLoading = true, channels = emptyList())
    }
}
