package com.example.testapp.components.channel.list.adapter

import com.example.testapp.client.models.Channel


public sealed class ChannelListItem {
    public data class ChannelItem(val channel: Channel) : ChannelListItem()
    public object LoadingMoreItem : ChannelListItem()
}
