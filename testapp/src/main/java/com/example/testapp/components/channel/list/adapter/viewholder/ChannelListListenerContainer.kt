package com.example.testapp.components.channel.list.adapter.viewholder

import com.example.testapp.components.channel.list.ChannelListView

public sealed interface ChannelListListenerContainer {
    public val channelClickListener: ChannelListView.ChannelClickListener
    public val channelLongClickListener: ChannelListView.ChannelLongClickListener
    public val deleteClickListener: ChannelListView.ChannelClickListener
    public val moreOptionsClickListener: ChannelListView.ChannelClickListener
    public val userClickListener: ChannelListView.UserClickListener
    public val swipeListener: ChannelListView.SwipeListener
}
