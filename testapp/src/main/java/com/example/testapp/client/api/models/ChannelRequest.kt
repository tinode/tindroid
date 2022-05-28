package com.example.testapp.client.api.models

@Suppress("UNCHECKED_CAST")
internal interface ChannelRequest<T : ChannelRequest<T>> {
    var watch: Boolean

    fun withWatch(): T {
        watch = true
        return this as T
    }

    fun noWatch(): T {
        watch = false
        return this as T
    }
}
