package com.example.testapp.components.common.extensions.internal

import android.content.Context
import com.example.testapp.R
import com.example.testapp.client.ChatClient
import com.example.testapp.client.models.User

internal fun User.isCurrentUser(): Boolean {
    return id == ChatClient.instance().getCurrentUser()?.id
}

internal fun User.asMention(context: Context): String =
    context.getString(R.string.tinui_mention, name)
