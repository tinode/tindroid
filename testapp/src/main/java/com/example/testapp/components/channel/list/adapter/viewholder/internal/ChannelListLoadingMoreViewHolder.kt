package com.example.testapp.components.channel.list.adapter.viewholder.internal

import android.view.ViewGroup
import com.example.testapp.client.models.Channel
import com.example.testapp.components.channel.list.ChannelListViewStyle
import com.example.testapp.components.channel.list.adapter.ChannelListPayloadDiff
import com.example.testapp.components.channel.list.adapter.viewholder.BaseChannelListItemViewHolder
import com.example.testapp.components.common.extensions.internal.streamThemeInflater

internal class ChannelListLoadingMoreViewHolder(
    parent: ViewGroup,
    style: ChannelListViewStyle,
) : BaseChannelListItemViewHolder(parent.streamThemeInflater.inflate(style.loadingMoreView, parent, false)) {

    override fun bind(channel: Channel, diff: ChannelListPayloadDiff): Unit = Unit
}
