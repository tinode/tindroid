package com.example.testapp.logger

import com.example.testapp.core.client.errors.ChatError

internal class ChatSilentLogger : ChatLogger {

    override fun getLevel(): ChatLogLevel {
        return ChatLogLevel.NOTHING
    }

    override fun logE(tag: Any, throwable: Throwable) {
        // silent
    }

    override fun logE(tag: Any, message: String, throwable: Throwable) {
        // silent
    }

    override fun logE(tag: Any, chatError: ChatError) {
        // silent
    }

    override fun logE(tag: Any, message: String, chatError: ChatError) {
        // silent
    }

    override fun logI(tag: Any, message: String) {
        // silent
    }

    override fun logD(tag: Any, message: String) {
        // silent
    }

    override fun logW(tag: Any, message: String) {
        // silent
    }

    override fun logE(tag: Any, message: String) {
        // silent
    }
}
