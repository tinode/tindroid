package com.example.testapp.client.utils.observable

import co.tinode.tinsdk.Tinode
import co.tinode.tinsdk.Tinode.EventListener
import co.tinode.tinsdk.model.*
import co.tinode.tinui.media.VxCard
import com.example.testapp.client.ChatClient
import com.example.testapp.client.ChatEventListener
import com.example.testapp.client.events.*
import com.example.testapp.client.extensions.toChannelInfo
import com.example.testapp.client.models.Channel
import com.example.testapp.client.models.EventType
import com.example.testapp.client.models.Message
import com.example.testapp.client.models.User
import com.example.testapp.components.common.extensions.internal.cast
import java.util.*

internal class ChatEventsObservable(
    private val socket: Tinode,
    private var client: ChatClient,
) {

    private var subscriptions = setOf<EventSubscription>()
    private var eventsMapper = EventsMapper(this)

    private fun onNext(event: ChatEvent) {
        subscriptions.forEach { subscription ->
            if (!subscription.isDisposed) {
                subscription.onNext(event)
            }
        }
        when (event) {
            is ConnectedEvent -> {
                client.callConnectionListener(event, null)
            }
            is ErrorEvent -> {
                client.callConnectionListener(null, event.error)
            }
            else -> Unit // Ignore other events
        }
        subscriptions = subscriptions.filterNot(Disposable::isDisposed).toSet()
        checkIfEmpty()
    }

    private fun checkIfEmpty() {
        if (subscriptions.isEmpty()) {
            socket.removeListener(eventsMapper)
        }
    }

    fun subscribe(
        filter: (ChatEvent) -> Boolean = { true },
        listener: ChatEventListener<ChatEvent>,
    ): Disposable {
        return addSubscription(SubscriptionImpl(filter, listener))
    }

    fun subscribeSingle(
        filter: (ChatEvent) -> Boolean = { true },
        listener: ChatEventListener<ChatEvent>,
    ): Disposable {
        return addSubscription(
            SubscriptionImpl(filter, listener).apply {
                afterEventDelivered = this::dispose
            }
        )
    }

    private fun addSubscription(subscription: EventSubscription): Disposable {
        if (subscriptions.isEmpty()) {
            // add listener to socket events only once
            socket.addListener(eventsMapper)
        }

        subscriptions = subscriptions + subscription

        return subscription
    }

    /**
     * Maps methods of [EventListener] to events of [ChatEventsObservable]
     */
    private class EventsMapper(private val observable: ChatEventsObservable) : EventListener() {

        val client = observable.client
        val socket = observable.client.socket

        override fun onConnect(code: Int, reason: String?, params: MutableMap<String, Any>?) {
            observable.onNext(ConnectingEvent(EventType.CONNECTION_CONNECTING, Date()))
        }

        override fun onLogin(code: Int, text: String?) {
            observable.onNext(UserLoginEvent(EventType.ME_LOGIN, Date()))
        }

        override fun onDisconnect(byServer: Boolean, code: Int, reason: String?) {
            observable.onNext(DisconnectedEvent(EventType.CONNECTION_DISCONNECTED, Date()))
        }

        override fun onCtrlMessage(ctrl: MsgServerCtrl?) {
//            observable.onNext(event)
        }

        override fun onMetaMessage(meta: MsgServerMeta<*, *, *, *>?) {
//            observable.onNext(event)
            if (meta == null) {
                return
            }
            if (meta.desc != null && meta.topic == Tinode.TOPIC_ME) {
                runCatching {
                    User.fromTUser(socket.getUser(socket.myId))
                }.getOrNull()?.let { sUser ->
                    client.getCurrentUser()?.let { user ->
                        observable.onNext(
                            ConnectedEvent(
                                EventType.ME_DESCRIPTION,
                                Date(),
                                sUser.copy(username = user.username)
                            )
                        )
                    }
                }
            }
            if (meta.sub != null && meta.topic == Tinode.TOPIC_ME) {
                observable.onNext(UserChannelsUpdatedEvent(EventType.ME_SUBSCRIPTIONS, Date()))
            }
            if (meta.del != null && meta.topic == Tinode.TOPIC_ME) {
//                routeMetaDel(meta.del.clear, meta.del.delseq)
            }
            if (meta.tags != null && meta.topic == Tinode.TOPIC_ME) {
//                routeMetaTags(meta.tags)
            }
        }

        override fun onDataMessage(data: MsgServerData?) {
//            observable.onNext(event)
            if (data == null) {
                return
            }
            val user = socket.getUser<VxCard>(data.from)?.let { tuser ->
                User.fromTUser(tuser)
            }
            val channel = socket.getTopic(data.topic)?.let { topic ->
                Channel.fromComTopic(topic.cast())
            }
            if (user != null && channel != null) {
                observable.onNext(
                    NewMessageEvent(
                        type = EventType.MESSAGE_NEW,
                        createdAt = Date(),
                        topic = data.topic,
                        user = user,
                        message = Message.fromMsgServerData(data, user).apply { channelInfo = channel.toChannelInfo() },
                        totalUnreadCount = 0
                    )
                )
            }
        }

        override fun onInfoMessage(info: MsgServerInfo?) {
//            observable.onNext(event)
        }

        override fun onPresMessage(pres: MsgServerPres?) {
//            observable.onNext(event)
        }
    }
}
