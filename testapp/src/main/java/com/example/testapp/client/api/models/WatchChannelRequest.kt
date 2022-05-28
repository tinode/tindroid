package com.example.testapp.client.api.models

public class WatchChannelRequest : QueryChannelRequest() {

    init {
        watch = true
    }

    override fun withMessages(limit: Int): WatchChannelRequest {
        return super.withMessages(limit) as WatchChannelRequest
    }
}
