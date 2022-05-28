package com.example.testapp.common.utils.extensions

import com.example.testapp.client.api.models.FilterObject
import com.example.testapp.client.models.Filters
import com.example.testapp.client.models.User
import com.example.testapp.core.internal.InternalTinUiApi

@InternalTinUiApi
public fun Filters.defaultChannelListFilter(user: User?): FilterObject? {
    return if (user == null) {
        null
    } else {
        and(
            or(
                eq("type", "p2p"),
                eq("type", "grp"),
            ),
            eq("hidden", false),
            eq("banded", false),
        )
    }
}
