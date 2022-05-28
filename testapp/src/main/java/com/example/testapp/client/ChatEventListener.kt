package com.example.testapp.client

import com.example.testapp.client.events.ChatEvent

public fun interface ChatEventListener<EventT : ChatEvent> {
    public fun onEvent(event: EventT)
}
