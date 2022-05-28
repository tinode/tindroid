package com.example.testapp.client.clientstate

import com.example.testapp.client.models.User


internal sealed class UserState {
    object NotSet : UserState()
    class UserSet(val user: User) : UserState()
//    sealed class Anonymous : UserState() {
//        object Pending : Anonymous()
//        class AnonymousUserSet(val anonymousUser: User) : Anonymous()
//    }

    internal fun userOrError(): User = when (this) {
        is UserSet -> user
//        is Anonymous.AnonymousUserSet -> anonymousUser
        else -> error("This state doesn't contain user!")
    }
}
