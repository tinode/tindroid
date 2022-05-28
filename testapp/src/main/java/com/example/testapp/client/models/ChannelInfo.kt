package com.example.testapp.client.models

import com.example.testapp.core.internal.InternalTinUiApi

/**
 * A [Channel] object that contains less information.
 * Used only internally.
 *
 * @param id Channel's unique ID.
 * @param type Type of the channel.
 * @param memberCount Number of members in the channel.
 * @param name Channel's name.
 * @param image Channel's image.
 */
@InternalTinUiApi
public data class ChannelInfo(
    val id: String? = null,
    val type: String? = null,
    val memberCount: Int = 0,
    val name: String? = null,
    val image: String? = null,
)
