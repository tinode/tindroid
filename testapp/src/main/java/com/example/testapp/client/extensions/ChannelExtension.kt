package com.example.testapp.client.extensions

import com.example.testapp.client.ChatClient
import com.example.testapp.client.models.Channel
import com.example.testapp.client.models.ChannelInfo
import com.example.testapp.client.models.User
import com.example.testapp.core.internal.InternalTinUiApi

/**
 * Returns a list of users that are members of the channel excluding the currently
 * logged in user.
 *
 * @param currentUser The currently logged in user.
 * @return The list of users in the channel without the current user.
 */
@InternalTinUiApi
public fun Channel.getUsersExcludingCurrent(
    currentUser: User? = ChatClient.instance().getCurrentUser(),
): List<User> {
    val users = members.map { it.user }
    val currentUserId = currentUser?.id
    return if (currentUserId != null) {
        users.filterNot { it.id == currentUserId }
    } else {
        users
    }
}


public fun Channel.toChannelInfo(): ChannelInfo =
    ChannelInfo(id = id, name = name, memberCount = members.size, type = type, image = image)

