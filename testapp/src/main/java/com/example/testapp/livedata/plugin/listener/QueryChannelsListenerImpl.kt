package com.example.testapp.livedata.plugin.listener

import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.client.models.Channel
import com.example.testapp.client.plugin.listeners.QueryChannelsListener
import com.example.testapp.core.client.errors.ChatError
import com.example.testapp.logger.ChatLogger
import com.example.testapp.core.client.utils.Result
import com.example.testapp.livedata.logic.LogicRegistry
import com.example.testapp.livedata.plugin.LiveDataPlugin


/**
 * [QueryChannelsListener] implementation for [LiveDataPlugin].
 * Handles querying the channel offline and managing local state updates.
 *
 * @param logic [LogicRegistry] provided by the [LiveDataPlugin].
 */
internal class QueryChannelsListenerImpl(private val logic: LogicRegistry) : QueryChannelsListener {

    private val logger = ChatLogger.get("QueryChannelsLogic")

    override suspend fun onQueryChannelsPrecondition(request: QueryChannelsRequest): Result<Unit> {
        return logic.queryChannels(request).run {
            onQueryChannelsPrecondition(request)
        }
    }

    override suspend fun onQueryChannelsRequest(request: QueryChannelsRequest) {
        logic.queryChannels(request).run {
            onQueryChannelsRequest(request)
        }
    }

    override suspend fun onQueryChannelsResult(
        result: Result<List<Channel>>,
        request: QueryChannelsRequest
    ) {
        logic.queryChannels(request).run {
            onQueryChannelsResult(result, request)
        }
    }
}