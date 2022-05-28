package com.example.testapp.client.api.models

/**
 * Request body class for querying channels.
 *
 * @property filter [FilterObject] conditions used by backend to filter queries response.
 */
public data class QueryChannelsRequest(
    public val filter: FilterObject,
) : ChannelRequest<QueryChannelsRequest> {
    override var watch: Boolean = true
}