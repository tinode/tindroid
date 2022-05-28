package com.example.testapp.livedata.state

import com.example.testapp.client.ChatClient
import com.example.testapp.client.api.models.QueryChannelRequest
import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.client.api.models.WatchChannelRequest
import com.example.testapp.core.client.call.launch
import com.example.testapp.livedata.state.channel.ChannelState
import com.example.testapp.livedata.state.querychannels.QueryChannelsState
import kotlinx.coroutines.CoroutineScope

/**
 * Adapter for [ChatClient] that wraps some of it's request.
 */
internal class ChatClientStateCalls(
    private val chatClient: ChatClient,
    private val state: StateRegistry,
    private val scope: CoroutineScope
) {
    /** Reference request of the channels query. */
    internal fun queryChannels(request: QueryChannelsRequest): QueryChannelsState {
        chatClient.queryChannels(request).launch(scope)
        return state.queryChannels(request.filter)
    }

    /** Reference request of the channel query. */
    private fun queryChannel(
        channelId: String,
        request: QueryChannelRequest,
    ): ChannelState {
        chatClient.queryChannel(channelId, request).launch(scope)
        return state.channel(channelId)
    }

    /** Reference request of the watch channel query. */
    internal fun watchChannel(channelId: String, messageLimit: Int): ChannelState {
        val request = WatchChannelRequest().apply { withMessages(messageLimit) }
        return queryChannel(channelId, request)
    }

//    /** Reference request of the get thread replies query. */
//    internal fun getReplies(messageId: String, messageLimit: Int): ThreadState {
//        chatClient.getReplies(messageId, messageLimit).launch(scope)
//        return state.thread(messageId)
//    }
}
