package com.example.testapp.client.models

/**
 * used to authenticate socket connection
 */
public data class UserBasicCredentials(
    public val username: String,
    public val password: String
)