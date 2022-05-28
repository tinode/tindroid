package com.example.testapp.livedata.plugin.listener

import com.example.testapp.client.api.models.QueryChannelRequest
import com.example.testapp.client.models.Channel
import com.example.testapp.client.plugin.listeners.QueryChannelListener
import com.example.testapp.livedata.logic.LogicRegistry
import com.example.testapp.core.client.utils.Result

internal class QueryChannelListenerImpl(private val logic: LogicRegistry) : QueryChannelListener {

    override suspend fun onQueryChannelPrecondition(
        channelId: String,
        request: QueryChannelRequest,
    ): Result<Unit> =
        logic.channel(channelId).onQueryChannelPrecondition(channelId, request)

    override suspend fun onQueryChannelRequest(
        channelId: String,
        request: QueryChannelRequest,
    ) {
        logic.channel(channelId).onQueryChannelRequest(channelId, request)
    }

    override suspend fun onQueryChannelResult(
        result: Result<Channel>,
        channelId: String,
        request: QueryChannelRequest,
    ) {
        logic.channel(channelId).onQueryChannelResult(result, channelId, request)
    }
}
