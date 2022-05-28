package com.example.testapp.livedata.event

import androidx.annotation.VisibleForTesting
import com.example.testapp.client.ChatClient
import com.example.testapp.client.events.*
import com.example.testapp.client.models.User
import com.example.testapp.client.utils.observable.Disposable
import com.example.testapp.livedata.logic.LogicRegistry
import com.example.testapp.livedata.state.StateRegistry
import com.example.testapp.livedata.state.global.GlobalMutableState
import com.example.testapp.logger.ChatLogger
import com.example.testapp.livedata.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.InputMismatchException

internal class EventHandlerImpl(
    private val recoveryEnabled: Boolean,
    private val client: ChatClient,
    private val logic: LogicRegistry,
    private val state: StateRegistry,
    private val mutableGlobalState: GlobalMutableState,
//    private val repos: RepositoryFacade,
//    private val syncManager: SyncManager,
) {
    private var logger = ChatLogger.get("EventHandler")

    private var eventSubscription: Disposable = EMPTY_DISPOSABLE
    private var initJob: Deferred<*>? = null

    internal fun initialize(user: User, scope: CoroutineScope) {
        initJob = scope.async {
//            syncManager.updateAllReadStateForDate(user.id, Date())
//            syncManager.loadSyncStateForUser(user.id)
//            replayEventsForAllChannels(user)
        }
    }

    /**
     * Start listening to chat events.
     */
    internal fun startListening(scope: CoroutineScope) {
        if (eventSubscription.isDisposed) {
            eventSubscription = client.subscribe {
                scope.async {
                    initJob?.join()
                    handleEvents(listOf(it))
                }
            }
        }
    }

    /**
     * Stop listening for events.
     */
    internal fun stopListening() {
        eventSubscription.dispose()
    }

    /**
     * Handle events from the SDK. Don't use this directly as this only be called then new events arrive from the SDK.
     */
    @VisibleForTesting
    internal suspend fun handleEvent(event: ChatEvent) {
        handleConnectEvents(listOf(event))
        handleEventsInternal(listOf(event), isFromSync = false)
    }

    /**
     * Replay all the events for the active channels in the SDK. Use this to sync the data of the active channels.
     */
    internal suspend fun replayEventsForActiveChannels() {

    }

    private fun activeChannelsCid(): List<String> {
        return logic.getActiveChannelsLogic().map { it.channelId }
    }

    private suspend fun replayEventsForAllChannels(user: User) {

    }

    private suspend fun handleEvents(events: List<ChatEvent>) {
        handleConnectEvents(events)
        handleEventsInternal(events, isFromSync = false)
    }

    private suspend fun handleConnectEvents(sortedEvents: List<ChatEvent>) {
        // send out the connect events
        sortedEvents.forEach { event ->
            // connection events are never send on the recovery endpoint, so handle them 1 by 1
            when (event) {
                is DisconnectedEvent -> {
                    mutableGlobalState._connectionState.value = ConnectionState.OFFLINE
                }
                is ConnectedEvent -> {
                    logger.logI("Received ConnectedEvent, marking the domain as online and initialized")
                    updateCurrentUser(event.me)

                    mutableGlobalState._connectionState.value = ConnectionState.CONNECTED
                    mutableGlobalState._initialized.value = true

                    if (recoveryEnabled) {
//                        syncManager.connectionRecovered()
                    }

                    // 4. recover missing events
                    val activeChannelCids = activeChannelsCid()
                    if (activeChannelCids.isNotEmpty()) {
//                        replayEventsForChannels(activeChannelCids)
                    }
                }
//                is HealthEvent -> {
//                    syncManager.retryFailedEntities()
//                }

                is ConnectingEvent -> {
                    mutableGlobalState._connectionState.value = ConnectionState.CONNECTING
                }

                else -> Unit // Ignore other events
            }
        }
    }


    private suspend fun handleEventsInternal(events: List<ChatEvent>, isFromSync: Boolean) {
        events.forEach { chatEvent ->
            logger.logD("Received event: $chatEvent")
        }

        val sortedEvents = events.sortedBy { it.createdAt }
//        updateOfflineStorageFromEvents(sortedEvents, isFromSync)

        // step 3 - forward the events to the active channels
        sortedEvents.filterIsInstance<TopicEvent>()
            .groupBy { it.topic }
            .forEach { (channelId, eventList) ->
                if (logic.isActiveChannel(channelId)) {
                    logic.channel(channelId).handleEvents(eventList)
                }
            }

        // mark all read applies to all channels
//        sortedEvents.filterIsInstance<MarkAllReadEvent>().firstOrNull()?.let { markAllRead ->
//            handleChannelControllerEvent(markAllRead)
//        }

        // mutes are user related, so they have to be propagated to all channels
//        sortedEvents.filterIsInstance<NotificationChannelMutesUpdatedEvent>().lastOrNull()?.let { event ->
//            handleChannelControllerEvent(event)
//        }

        // User presence change applies to all active channels with that user
//        sortedEvents.find { it is UserPresenceChangedEvent }?.let { userPresenceChanged ->
//            val event = userPresenceChanged as UserPresenceChangedEvent
//
//            state.getActiveChannelStates()
//                .filter { channelState ->
//                    channelState.members.value
//                        .map { member -> member.user.id }
//                        .contains(event.user.id)
//                }
//                .forEach { channelState ->
//                    logic.channel(channelState.channelId)
//                        .handleEvent(userPresenceChanged)
//                }
//        }

        // only afterwards forward to the queryRepo since it borrows some data from the channel
        // queryRepo mainly monitors for the notification added to channel event
        logic.getActiveQueryChannelsLogic().forEach { queryChannelsLogic ->
            queryChannelsLogic.handleEvents(events)
        }
    }

    private fun handleChannelControllerEvent(event: ChatEvent) {
        logic.getActiveChannelsLogic().forEach { channelLogic ->
            channelLogic.handleEvent(event)
        }
    }

    private suspend fun updateCurrentUser(me: User) {
        val currentUser = mutableGlobalState.user.value
        if (currentUser?.id?.isNotBlank() == true && me.id != currentUser.id) {
            throw InputMismatchException("received connect event for user with id ${me.id} while for user configured has id ${currentUser.id}. Looks like there's a problem in the user set")
        }

        mutableGlobalState.run {
            _user.value = me
//            _mutedUsers.value = me.mutes
//            _channelMutes.value = me.channelMutes
//            _totalUnreadCount.value = me.totalUnreadCount
//            _channelUnreadCount.value = me.unreadChannels
//            _banned.value = me.banned
        }

//        repos.insertCurrentUser(me)
    }

    companion object {
        val EMPTY_DISPOSABLE = object : Disposable {
            override val isDisposed: Boolean = true
            override fun dispose() {}
        }
    }
}
