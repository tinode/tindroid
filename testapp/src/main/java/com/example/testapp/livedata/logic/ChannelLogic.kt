package com.example.testapp.livedata.logic

import com.example.testapp.client.api.models.QueryChannelRequest
import com.example.testapp.client.events.ChatEvent
import com.example.testapp.client.events.NewMessageEvent
import com.example.testapp.client.models.Channel
import com.example.testapp.client.models.Member
import com.example.testapp.client.models.ChannelUserRead
import com.example.testapp.client.models.Message
import com.example.testapp.client.plugin.listeners.QueryChannelListener
import com.example.testapp.client.utils.SyncStatus
import com.example.testapp.core.client.errors.ChatError
import com.example.testapp.livedata.state.channel.ChannelMutableState
import com.example.testapp.livedata.state.global.GlobalMutableState
import com.example.testapp.logger.ChatLogger
import com.example.testapp.core.client.utils.Result
import com.example.testapp.core.client.utils.onError
import com.example.testapp.core.client.utils.onSuccess
import com.example.testapp.core.client.utils.onSuccessSuspend
import com.example.testapp.livedata.extensions.NEVER
import com.example.testapp.livedata.extensions.wasCreatedBeforeOrAt
import com.example.testapp.livedata.model.ChannelData
import com.example.testapp.livedata.state.channel.ChannelState
import com.example.testapp.livedata.utils.Event
import java.lang.Long.max
import java.util.*

/**
 * This class contains all the logic to manipulate and modify the state of the corresponding channel.
 *
 * @property mutableState [ChannelMutableState] Mutable state instance of the channel.
 * @property globalMutableState [GlobalMutableState] Global state of the SDK.
 * @property repos [RepositoryFacade] that interact with data sources.
 * @property userPresence [Boolean] true if user presence is enabled, false otherwise.
 */
internal class ChannelLogic(
    private val mutableState: ChannelMutableState,
    private val globalMutableState: GlobalMutableState,
    private val userPresence: Boolean,
) : QueryChannelListener {

    private val logger = ChatLogger.get("Query channel request")

    val channelId: String
        get() = mutableState.channelId

    private fun loadingStateByRequest(request: QueryChannelRequest) = when {
//        request.isFilteringNewerMessages() -> mutableState._loadingNewerMessages
//        request.filteringOlderMessages() -> mutableState._loadingOlderMessages
        else -> mutableState._loading
    }

    override suspend fun onQueryChannelPrecondition(
        channelId: String,
        request: QueryChannelRequest,
    ): Result<Unit> {
        val loader = loadingStateByRequest(request)
        return if (loader.value) {
            logger.logI("Another request to load messages is in progress. Ignoring this request.")
            Result.error(ChatError("Another request to load messages is in progress. Ignoring this request."))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun onQueryChannelRequest(channelId: String, request: QueryChannelRequest) {
//        runChannelQueryOffline(request)
        loadingStateByRequest(request).value = true
    }

    override suspend fun onQueryChannelResult(
        result: Result<Channel>,
        channelId: String,
        request: QueryChannelRequest,
    ) {
        result.onSuccessSuspend { channel ->
            // first thing here needs to be updating configs otherwise we have a race with receiving events
            storeStateForChannel(channel)
        }
            .onSuccess { channel ->
                mutableState.recoveryNeeded = false
                if (request.messagesLimit() > channel.messages.size) {
//                    if (request.isFilteringNewerMessages()) {
//                        mutableState._endOfNewerMessages.value = true
//                    } else {
                        mutableState._endOfOlderMessages.value = true
//                    }
                }
                updateDataFromChannel(channel)
                loadingStateByRequest(request).value = false
            }
            .onError { error ->
                logger.logW("Temporary failure calling channel.watch for channel ${mutableState.channelId}. Marking the channel as needing recovery. Error was $error")
                mutableState.recoveryNeeded = true
                globalMutableState._errorEvent.value = Event(error)
            }
    }

    internal fun setHidden(hidden: Boolean) {
        mutableState._hidden.value = hidden
    }

    private suspend fun storeStateForChannel(channel: Channel) {
//        val users = channel.users().associateBy { it.id }.toMutableMap()
//        val configs: MutableCollection<ChannelConfig> =
//            mutableSetOf(ChannelConfig(channel.type, channel.config))
//        channel.messages.forEach { message ->
//            message.enrichWithCid(channel.cid)
//            users.putAll(message.users().associateBy { it.id })
//        }
//        repos.storeStateForChannels(
//            configs = configs,
//            users = users.values.toList(),
//            channels = listOf(channel),
//            messages = channel.messages
//        )
    }


    internal fun updateDataFromChannel(c: Channel) {
        // Update all the flow objects based on the channel
        updateChannelData(c)
//        setWatcherCount(c.watcherCount)

//        mutableState._read.value?.lastMessageSeenDate = c.lastMessageAt

        updateReads(c.reads)

        // there are some edge cases here, this code adds to the members, watchers and messages
        // this means that if the offline sync went out of sync things go wrong
        setMembers(c.members)
//        setWatchers(c.watchers)
//        upsertMessages(c.messages)
        mutableState.lastMessageAt.value = c.lastMessageAt
//        mutableState._channelConfig.value = c.config
    }

    private fun setMembers(members: List<Member>) {
        mutableState._members.value = (mutableState._members.value + members.associateBy(Member::getUserId))
    }

    internal fun updateReads(reads: List<ChannelUserRead>) {
        globalMutableState.user.value?.let { currentUser ->
            val currentUserId = currentUser.id
            val previousUserIdToReadMap = mutableState._reads.value
            val incomingUserIdToReadMap = reads.associateBy(ChannelUserRead::getUserId).toMutableMap()

            /**
             * It's possible that the data coming back from the online channel query has a last read date that's
             * before what we've last pushed to the UI. We want to ignore this, as it will cause an unread state
             * to show in the channel list.
             */
            incomingUserIdToReadMap[currentUserId]?.let { incomingUserRead ->
                // the previous last Read date that is most current
                val previousLastRead =
                    mutableState._read.value?.lastRead ?: previousUserIdToReadMap[currentUserId]?.lastRead

                // Use AFTER to determine if the incoming read is more current.
                // This prevents updates if it's BEFORE or EQUAL TO the previous Read.
                val shouldUpdateByIncoming = previousLastRead == null || incomingUserRead.lastRead?.let {
                    it >= previousLastRead
                } == true

                if (shouldUpdateByIncoming) {
                    mutableState._read.value = incomingUserRead
                    mutableState._unreadCount.value = incomingUserRead.unreadMessages
                } else {
                    // if the previous Read was more current, replace the item in the update map
                    incomingUserIdToReadMap[currentUserId] = ChannelUserRead(currentUser, previousLastRead)
                }
            }

            // always post the newly updated map
            mutableState._reads.value = (previousUserIdToReadMap + incomingUserIdToReadMap)
        }
    }

    private fun updateRead(read: ChannelUserRead) = updateReads(listOf(read))

    internal fun updateChannelData(channel: Channel) {
        mutableState._channelData.value = ChannelData(channel)
    }

    /**
     * Returns the state of Channel. Useful to check how it the state of the channel of the [ChannelLogic]
     *
     * @return [ChannelState]
     */
    internal fun state(): ChannelState {
        return mutableState
    }

    /**
     * Updates [ChannelMutableState._messages] with new messages.
     * The message will by only updated if its creation/update date is newer than the one stored in the StateFlow.
     *
     * @param messages The list of messages to update.
     */
    private fun parseMessages(messages: List<Message>): Map<String, Message> {
        val currentMessages = mutableState._messages.value
        return currentMessages + messages.filter {
                newMessage -> isMessageNewerThanCurrent(currentMessages[newMessage.id], newMessage)
        }.associateBy(Message::id)
    }

    private fun isMessageNewerThanCurrent(currentMessage: Message?, newMessage: Message): Boolean {
        return if (newMessage.syncStatus == SyncStatus.SYNCED) {
            (currentMessage?.lastUpdateTime() ?: NEVER.time) <= newMessage.lastUpdateTime()
        } else {
            (currentMessage?.lastLocalUpdateTime() ?: NEVER.time) <= newMessage.lastLocalUpdateTime()
        }
    }

    private fun updateLastMessageAtByNewMessages(newMessages: Collection<Message>) {
        if (newMessages.isEmpty()) {
            return
        }
        val newLastMessageAt =
            newMessages.mapNotNull { it.createdAt ?: it.createdLocallyAt }.maxOfOrNull(Date::getTime) ?: return
        mutableState.lastMessageAt.value = when (val currentLastMessageAt = mutableState.lastMessageAt.value) {
            null -> Date(newLastMessageAt)
            else -> max(currentLastMessageAt.time, newLastMessageAt).let(::Date)
        }
    }

    private fun Message.lastUpdateTime(): Long = listOfNotNull(
        createdAt,
        updatedAt,
        deletedAt,
    ).map { it.time }
        .maxOrNull()
        ?: NEVER.time

    private fun Message.lastLocalUpdateTime(): Long = listOfNotNull(
        createdLocallyAt,
        updatedLocallyAt,
        deletedAt,
    ).map { it.time }
        .maxOrNull()
        ?: NEVER.time


    internal fun upsertMessage(message: Message) = upsertMessages(listOf(message))

    internal fun upsertMessages(messages: List<Message>) {
        val newMessages = parseMessages(messages)
        updateLastMessageAtByNewMessages(newMessages.values)
        mutableState._messages.value = newMessages
    }

    private fun upsertEventMessage(message: Message) {
        // make sure we don't lose ownReactions
        getMessage(message.id)?.let {
//            message.ownReactions = it.ownReactions
        }
        upsertMessages(listOf(message))
    }

    /**
     * Returns message stored in [ChannelMutableState] if exists and wasn't hidden.
     *
     * @param messageId The id of the message.
     *
     * @return [Message] if exists and wasn't hidden, null otherwise.
     */
    internal fun getMessage(messageId: String): Message? {
        val copy = mutableState.messageList.value
        var message = copy.firstOrNull { it.id == messageId }

        if (mutableState.hideMessagesBefore != null) {
            if (message != null && message.wasCreatedBeforeOrAt(mutableState.hideMessagesBefore)) {
                message = null
            }
        }

        return message
    }


    /**
     * Handles events received from the socket.
     *
     * @see [handleEvent]
     */
    internal fun handleEvents(events: List<ChatEvent>) {
        for (event in events) {
            handleEvent(event)
        }
    }

    /**
     * Handles event received from the socket.
     * Responsible for synchronizing [ChannelMutableState].
     */
    internal fun handleEvent(event: ChatEvent) {
        when (event) {
            is NewMessageEvent -> {
                upsertEventMessage(event.message)
//                incrementUnreadCountIfNecessary(event.message)
                setHidden(false)
            }
            else -> Unit
        }
    }

    fun toChannel(): Channel = mutableState.toChannel()

}
