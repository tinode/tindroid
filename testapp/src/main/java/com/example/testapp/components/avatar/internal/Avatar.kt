package com.example.testapp.components.avatar.internal

import co.tinode.tinsdk.model.PrivateType
import co.tinode.tinsdk.model.Subscription
import co.tinode.tinui.media.VxCard
import com.example.testapp.client.models.Channel
import com.example.testapp.client.models.User
import com.example.testapp.components.avatar.AvatarStyle

internal sealed class Avatar(open val avatarStyle: AvatarStyle) {
    data class UserAvatar(
        val user: User,
        override val avatarStyle: AvatarStyle
    ) : Avatar(avatarStyle)

    data class ChannelAvatar(
        val channel: Channel,
        override val avatarStyle: AvatarStyle
    ) : Avatar(avatarStyle)

    data class SubscriptionAvatar(
        val sub: Subscription<VxCard, PrivateType>,
        override val avatarStyle: AvatarStyle
    ) : Avatar(avatarStyle)
}
