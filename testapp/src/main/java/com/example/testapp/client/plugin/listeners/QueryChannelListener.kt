package com.example.testapp.client.plugin.listeners

import com.example.testapp.client.api.models.QueryChannelRequest
import com.example.testapp.client.models.Channel
import com.example.testapp.core.client.utils.Result

/**
 * Listener of [ChatClient.queryChannel] requests.
 */
public interface QueryChannelListener {
    /**
     * Run precondition for the request. If it returns [Result.isSuccess] then the request is run otherwise it returns
     * [Result.error] and no request is made.
     */
    public suspend fun onQueryChannelPrecondition(
        channelId: String,
        request: QueryChannelRequest,
    ): Result<Unit>

    /**
     * Runs side effect before the request is launched.
     *
     * @param channelId Id of the requested channel.
     * @param request [QueryChannelRequest] which is going to be used for the request.
     */
    public suspend fun onQueryChannelRequest(
        channelId: String,
        request: QueryChannelRequest,
    )

    /**
     * Runs this function on the result of the request.
     */
    public suspend fun onQueryChannelResult(
        result: Result<Channel>,
        channelId: String,
        request: QueryChannelRequest,
    )
}
