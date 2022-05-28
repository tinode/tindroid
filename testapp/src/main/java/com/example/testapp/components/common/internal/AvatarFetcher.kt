package com.example.testapp.components.common.internal

import android.graphics.drawable.BitmapDrawable
import coil.bitmap.BitmapPool
import coil.decode.DataSource
import coil.decode.Options
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.PixelSize
import coil.size.Size
import com.example.testapp.client.extensions.getUsersExcludingCurrent
import com.example.testapp.components.ChatUI
import com.example.testapp.components.avatar.internal.Avatar

internal class AvatarFetcher : Fetcher<Avatar> {

    override suspend fun fetch(
        pool: BitmapPool,
        data: Avatar,
        size: Size,
        options: Options,
    ): FetchResult {
        val targetSize = size.let { if (it is PixelSize) it.width else 0 }
        val resources = options.context.resources
        return DrawableResult(
            BitmapDrawable(
                resources,
                when (data) {
                    is Avatar.UserAvatar -> {
                        ChatUI.avatarBitmapFactory.createUserBitmapInternal(
                            data.user,
                            data.avatarStyle,
                            targetSize
                        )
                    }
                    is Avatar.ChannelAvatar -> {
                        ChatUI.avatarBitmapFactory.createChatBitmapInternal(
                            data.channel,
                            data.channel.getUsersExcludingCurrent(),
                            data.avatarStyle,
                            targetSize
                        )
                    }
                    else -> null
                }
            ),
            false,
            DataSource.MEMORY
        )
    }

    override fun key(data: Avatar): String? = when (data) {
        is Avatar.UserAvatar -> ChatUI.avatarBitmapFactory.userBitmapKey(data.user)
        is Avatar.ChannelAvatar -> ChatUI.avatarBitmapFactory.chatBitmapKey(data.channel)
        else -> null
    }
}
