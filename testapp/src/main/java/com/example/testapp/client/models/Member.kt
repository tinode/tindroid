package com.example.testapp.client.models

import java.util.*

/**
 * Represents a chat member.
 */
public data class Member(
    /**
     * The user who is a member of the channel.
     */
    override var user: User,

    /**
     * When the user became a member.
     */
    var createdAt: Date? = null,

    /**
     * When the membership data was last updated.
     */
    var updatedAt: Date? = null,
) : UserEntity
