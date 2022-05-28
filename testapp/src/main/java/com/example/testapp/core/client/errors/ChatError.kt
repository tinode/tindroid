package com.example.testapp.core.client.errors

public open class ChatError(
    public val message: String? = null,
    public val cause: Throwable? = null
)
