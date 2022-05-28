package com.example.testapp.client.api.models

public open class QueryChannelRequest : ChannelRequest<QueryChannelRequest> {
    override var watch: Boolean = false
    public val messages: MutableMap<String, Any> = mutableMapOf()

    public open fun withMessages(limit: Int): QueryChannelRequest {
        val messages: MutableMap<String, Any> = HashMap()
        messages[KEY_LIMIT] = limit
        this.messages.putAll(messages)
        return this
    }

    /**
     * Returns limit of messages for a requested channel.
     */
    public fun messagesLimit(): Int {
        return messages[KEY_LIMIT] as? Int ?: 0
    }

    private companion object {
        private const val KEY_LIMIT = "limit"
    }
}
