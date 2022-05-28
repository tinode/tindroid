package com.example.testapp.client.clientstate

internal sealed class SocketState {
    object Idle : SocketState()
    object Pending : SocketState()
    object Connected : SocketState()
    object Disconnected : SocketState()
}
