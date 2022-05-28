package com.example.testapp.livedata.state.channel

import com.example.testapp.client.models.*
import com.example.testapp.core.internal.InternalTinUiApi
import com.example.testapp.livedata.model.ChannelData
import com.example.testapp.livedata.extensions.updateUsers
import com.example.testapp.livedata.extensions.wasCreatedAfter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.util.Date

@InternalTinUiApi
internal class ChannelMutableState(
    override val channelId: String,
    private val scope: CoroutineScope,
    private val userFlow: StateFlow<User?>,
    latestUsers: StateFlow<Map<String, User>>,
) : ChannelState {

//    override val cid: String = "%s:%s".format(channelType, channelId)

    internal val _messages = MutableStateFlow<Map<String, Message>>(emptyMap())
    internal val _watcherCount = MutableStateFlow(0)
//    internal val _typing = MutableStateFlow<Map<String, ChatEvent>>(emptyMap())
    internal val _reads = MutableStateFlow<Map<String, ChannelUserRead>>(emptyMap())
    internal val _read = MutableStateFlow<ChannelUserRead?>(null)
    internal val _endOfNewerMessages = MutableStateFlow(false)
    internal val _endOfOlderMessages = MutableStateFlow(false)
    internal val _loading = MutableStateFlow(false)
    internal val _hidden = MutableStateFlow(false)
    internal val _muted = MutableStateFlow(false)
    internal val _watchers = MutableStateFlow<Map<String, User>>(emptyMap())
    internal val _members = MutableStateFlow<Map<String, Member>>(emptyMap())
    internal val _loadingOlderMessages = MutableStateFlow(false)
    internal val _loadingNewerMessages = MutableStateFlow(false)
    internal val _channelData = MutableStateFlow<ChannelData?>(null)
    internal val _oldMessages = MutableStateFlow<Map<String, Message>>(emptyMap())
    internal val lastMessageAt = MutableStateFlow<Date?>(null)
    internal val _repliedMessage = MutableStateFlow<Message?>(null)
    internal val _unreadCount = MutableStateFlow(0)

    internal var hideMessagesBefore: Date? = null

    /** The raw message list updated by recent users value. */
    internal val messageList: StateFlow<List<Message>> =
        _messages.combine(latestUsers) { messageMap, userMap -> messageMap.values.updateUsers(userMap) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** a list of messages sorted by message.createdAt */
    private val sortedVisibleMessages: StateFlow<List<Message>> =
        messagesTransformation(messageList).stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _messagesState: StateFlow<MessagesState> =
        _loading.combine(sortedVisibleMessages) { loading: Boolean, messages: List<Message> ->
            when {
                loading -> MessagesState.Loading
                messages.isEmpty() -> MessagesState.OfflineNoResults
                else -> MessagesState.Result(messages)
            }
        }.stateIn(scope, SharingStarted.Eagerly, MessagesState.NoQueryActive)

    private fun messagesTransformation(messages: Flow<Collection<Message>>): StateFlow<List<Message>> {
        return messages.map { messageCollection ->
            messageCollection.asSequence()
//                .filter { it.parentId == null || it.showInChannel }
                .filter { it.user.id == userFlow.value?.id || !it.shadowed }
                .filter { hideMessagesBefore == null || it.wasCreatedAfter(hideMessagesBefore) }
                .sortedBy { it.createdAt ?: it.createdLocallyAt }
                .toList()
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    internal var lastMarkReadEvent: Date? = null
    internal var lastKeystrokeAt: Date? = null
    internal var lastStartTypingEvent: Date? = null

    internal val sortedMessages: StateFlow<List<Message>> = messageList.map {
        it.sortedBy { message -> message.createdAt ?: message.createdLocallyAt }
            .filter { message -> hideMessagesBefore == null || message.wasCreatedAfter(hideMessagesBefore) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Channel configuration data. */
//    internal val channelConfig: MutableStateFlow<Config> = MutableStateFlow(Config())
    override val repliedMessage: StateFlow<Message?> = _repliedMessage

    override val messages: StateFlow<List<Message>> = sortedVisibleMessages

    override val messagesState: StateFlow<MessagesState> = _messagesState
    override val oldMessages: StateFlow<List<Message>> = messagesTransformation(_oldMessages.map { it.values })
    override val watcherCount: StateFlow<Int> = _watcherCount
    override val watchers: StateFlow<List<User>> =
        _watchers.combine(latestUsers) { watcherMap, userMap -> watcherMap.values.updateUsers(userMap) }
//            .map { it.sortedBy(User::createdAt) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val typing: StateFlow<TypingEvent> = userFlow
        .filterNotNull()
//        .flatMapConcat { currentUser ->
//            _typing.map { typingMap ->
//                currentUser to typingMap
//            }
//        }
        .map { (currentUser, typingMap) ->
//            val userList = typingMap.values
//                .sortedBy(ChatEvent::createdAt)
//                .mapNotNull { event ->
//                    when (event) {
//                        is TypingStartEvent -> event.user.takeIf { user -> user != currentUser }
//                        else -> null
//                    }
//                }

            TypingEvent(channelId, emptyList())
        }
        .stateIn(scope, SharingStarted.Eagerly, TypingEvent(channelId, emptyList()))

    override val reads: StateFlow<List<ChannelUserRead>> = _reads
        .map { it.values.sortedBy(ChannelUserRead::lastRead) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val read: StateFlow<ChannelUserRead?> = _read

    override val unreadCount: StateFlow<Int?> = _unreadCount

    override val members: StateFlow<List<Member>> = _members
        .combine(latestUsers) { membersMap, usersMap -> membersMap.values.updateUsers(usersMap) }
        .map { it.sortedBy(Member::createdAt) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val channelData: StateFlow<ChannelData> =
        _channelData.filterNotNull().combine(latestUsers) { channelData, users ->
            if (users.containsKey(channelData.createdBy.id)) {
                channelData.copy(createdBy = users[channelData.createdBy.id] ?: channelData.createdBy)
            } else {
                channelData
            }
        }
            .stateIn(scope, SharingStarted.Eagerly, ChannelData(channelId = channelId))

    override val hidden: StateFlow<Boolean> = _hidden
    override val muted: StateFlow<Boolean> = _muted
    override val loading: StateFlow<Boolean> = _loading
    override val loadingOlderMessages: StateFlow<Boolean> = _loadingOlderMessages
    override val loadingNewerMessages: StateFlow<Boolean> = _loadingNewerMessages
    override val endOfOlderMessages: StateFlow<Boolean> = _endOfOlderMessages
    override val endOfNewerMessages: StateFlow<Boolean> = _endOfNewerMessages
    override var recoveryNeeded: Boolean = false

    override fun toChannel(): Channel {
        // recreate a channel object from the various observables.
        val channelData = channelData.value

        val messages = sortedMessages.value
        val members = members.value
        val watchers = watchers.value
        val reads = _reads.value.values.toList()
        val watcherCount = _watcherCount.value

        val channel = channelData.toChannel(messages, members, reads, watchers, watcherCount)
//        channel.config = channelConfig.value
//        channel.unreadCount = _unreadCount.value
//        channel.lastMessageAt =
//            lastMessageAt.value ?: messages.lastOrNull()?.let { it.createdAt ?: it.createdLocallyAt }
//        channel.hidden = _hidden.value

        return channel
    }
}

@InternalTinUiApi
internal fun ChannelState.toMutableState(): ChannelMutableState = this as ChannelMutableState
