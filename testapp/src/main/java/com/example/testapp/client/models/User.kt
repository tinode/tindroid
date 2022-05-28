package com.example.testapp.client.models

import android.graphics.Bitmap
import co.tinode.tinsdk.model.PrivateType
import co.tinode.tinsdk.model.Subscription
import co.tinode.tinui.media.VxCard
import co.tinode.tinsdk.User as TUser

/**
 * The only required field on the User data class is the user id.
 */
public data class User(
    /** the user id, this field is the only required field */
    var id: String = "",
    var name: String = "",
    override var image: String = "",
    override var imageBitmap: Bitmap? = null,
    var online: Boolean = false,
    var username: String = "",
    var password: String = "",
    private var desc: VxCard? = null,
    private var priv: PrivateType? = null
) : AvatarEntity {
    public companion object {
        public fun fromTUser(user: TUser<VxCard?>): User {
            return User(
                id = user.uid,
                name = user.pub?.fn ?: "",
                image = user.pub?.photoRef ?: "",
                imageBitmap = user.pub?.bitmap,
                desc = user.pub,
                priv = null,
            )
        }

        public fun fromSubscription(sub: Subscription<VxCard?, PrivateType?>): User {
            return User(
                id = sub.user,
                name = sub.pub?.fn ?: "",
                online = sub.online ?: false,
                image = sub.pub?.photoRef ?: "",
                imageBitmap = sub.pub?.bitmap,
                desc = sub.pub,
                priv = sub.priv,
            )
        }
    }
}