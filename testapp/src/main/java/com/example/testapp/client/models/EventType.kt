package com.example.testapp.client.models

/**
 * https://getstream.io/chat/docs/js/#event_object
 */
public object EventType {

    public const val ME_LOGIN: String = "me.login"
    public const val ME_DESCRIPTION: String = "me.description"
    public const val ME_SUBSCRIPTIONS: String = "me.subscriptions"

    public const val MESSAGE_NEW: String = "message.new"

    /**
     * Local
     */
    public const val CONNECTION_CONNECTING: String = "connection.connecting"
    public const val CONNECTION_DISCONNECTED: String = "connection.disconnected"
    public const val CONNECTION_ERROR: String = "connection.error"
    /**
     * Unknown
     */
    public const val UNKNOWN: String = "unknown_event"
}
