package com.example.testapp.client.extensions

import co.tinode.tinsdk.*
import co.tinode.tinsdk.model.ServerMessage
import co.tinode.tinui.media.VxCard
import com.example.testapp.client.api.models.QueryChannelRequest
import com.example.testapp.client.models.User
import com.example.testapp.common.utils.extensions.then
import com.example.testapp.components.common.extensions.internal.cast
import com.example.testapp.core.internal.InternalTinUiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@InternalTinUiApi
public fun Tinode.connectUser(
    user: User,
): PromisedReply<ServerMessage<*, *, *, *>> {
    if (serverHost.isNullOrBlank()) {
        throw IllegalAccessException("Tinode server host not set")
    }
    authToken?.let {
        setAutoLoginToken(it)
    }
    return connect(null, false, false).then {
        onSuccess {
            if (isAuthenticated.not()) {
                return@onSuccess loginBasic(user.username, user.password)
            }
            null
        }
    }.then {
        onSuccess { msg: ServerMessage<*, *, *, *>? ->
            // msg could be null if earlier login has succeeded.
            if (msg != null && msg.ctrl.code >= 300 &&
                msg.ctrl.text.contains("validate credentials")
            ) {
                throw IllegalArgumentException("invalidate credentials")
            } else {
                setAutoLoginToken(authToken)
            }
            null
        }
        onFailure<Exception> {
            null
        }
    }
}

@InternalTinUiApi
public fun Tinode.subscribeMeTopic() {
    val me: MeTopic<VxCard> = getOrCreateMeTopic()
    if (me.isAttached.not()) {
        me.subscribe(
            null, me.metaGetBuilder
                .withCred()
                .withDesc()
                .withSub()
                .withTags()
                .build()
        )
    }
}

@InternalTinUiApi
public suspend fun Tinode.subscribeTopic(topicName: String, request: QueryChannelRequest): ComTopic<VxCard?> {
    val topic: ComTopic<VxCard?> = getTopic(topicName).cast()
    if (topic.isAttached.not()) {
        return runCatching {
            topic.subscribe(
                null, topic.metaGetBuilder
                    .withDesc()
                    .withSub()
                    .withDel().apply {
                        if (request.messagesLimit() > 0) {
                            withLaterData(request.messagesLimit())
                        }
                        if (topic.isOwner) {
                            withTags()
                        }
                    }.build()
            ).result
        }.let {
            if (it.isFailure) {
                throw IllegalStateException("Can't Subscribe to topic: $topicName")
            } else {
                topic
            }
        }
    }
    return topic
}
