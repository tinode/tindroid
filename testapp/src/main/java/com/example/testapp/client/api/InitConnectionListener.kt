package com.example.testapp.client.api

import com.example.testapp.client.models.User
import com.example.testapp.core.client.errors.ChatError

internal abstract class InitConnectionListener {

    open fun onSuccess(data: ConnectionData) {
    }

    open fun onError(error: ChatError) {
    }

    data class ConnectionData(val user: User)
}
