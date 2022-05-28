package com.example.testapp.client.api

import com.example.testapp.logger.ChatLogger

public class ChatClientConfig(
    public val apiKey: String,
    public var hostUrl: String,
    public var useTls: Boolean,
    public var appName: String,
    public val loggerConfig: ChatLogger.Config,
)
