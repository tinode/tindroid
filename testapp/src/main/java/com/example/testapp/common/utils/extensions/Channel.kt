package com.example.testapp.common.utils.extensions

import com.example.testapp.client.ChatClient
import com.example.testapp.client.models.Channel
import com.example.testapp.core.internal.InternalTinUiApi


@InternalTinUiApi
public fun Channel.isDirectMessaging(): Boolean {
    return members.size == 2 && includesCurrentUser()
}

private fun Channel.includesCurrentUser(): Boolean {
    val currentUserId = ChatClient.instance().getCurrentUser()?.id ?: return false
    return members.any { it.user.id == currentUserId }
}
