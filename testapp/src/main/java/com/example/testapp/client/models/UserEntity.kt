package com.example.testapp.client.models

public sealed interface UserEntity {

    public var user: User

    public fun getUserId(): String {
        return user.id
    }
}
