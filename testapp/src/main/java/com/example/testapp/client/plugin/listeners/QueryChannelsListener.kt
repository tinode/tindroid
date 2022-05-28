package com.example.testapp.client.plugin.listeners

import com.example.testapp.client.api.models.QueryChannelsRequest
import com.example.testapp.client.models.Channel
import com.example.testapp.core.client.utils.Result


/**
 * Listener of [ChatClient.queryChannels] requests.
 */
public interface QueryChannelsListener {

    /**
     * Run precondition for the request. If it returns [Result.success] then the request is run otherwise it returns
     * [Result.error] and no request is made.
     *
     * @param request [QueryChannelRequest] which is going to be used for the request.
     *
     * @return [Result.success] if precondition passes otherwise [Result.error]
     */
    public suspend fun onQueryChannelsPrecondition(
        request: QueryChannelsRequest,
    ): Result<Unit>

    /**
     * Runs side effect before the request is launched.
     *
     * @param request [QueryChannelsRequest] which is going to be used for the request.
     */
    public suspend fun onQueryChannelsRequest(request: QueryChannelsRequest)

    /**
     * Runs this function on the [Result] of this [QueryChannelsRequest].
     */
    public suspend fun onQueryChannelsResult(result: Result<List<Channel>>, request: QueryChannelsRequest)
}
