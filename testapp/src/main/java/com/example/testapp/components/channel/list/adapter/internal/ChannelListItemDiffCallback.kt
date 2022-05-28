package com.example.testapp.components.channel.list.adapter.internal

import androidx.recyclerview.widget.DiffUtil
import com.example.testapp.components.channel.list.adapter.ChannelListItem
import com.example.testapp.components.common.extensions.internal.cast
import com.example.testapp.components.common.extensions.internal.diff
import com.example.testapp.components.common.extensions.internal.safeCast

internal object ChannelListItemDiffCallback : DiffUtil.ItemCallback<ChannelListItem>() {
    override fun areItemsTheSame(oldItem: ChannelListItem, newItem: ChannelListItem): Boolean {
        if (oldItem::class != newItem::class) {
            return false
        }

        return when (oldItem) {
            is ChannelListItem.ChannelItem -> {
                oldItem.channel.id == newItem.safeCast<ChannelListItem.ChannelItem>()?.channel?.id
            }

            else -> true
        }
    }

    override fun areContentsTheSame(oldItem: ChannelListItem, newItem: ChannelListItem): Boolean {
        // this is only called if areItemsTheSame returns true, so they must be the same class
        return when (oldItem) {
            is ChannelListItem.ChannelItem -> {
                oldItem
                    .channel
                    .diff(newItem.cast<ChannelListItem.ChannelItem>().channel)
                    .hasDifference()
                    .not()
            }

            else -> true
        }
    }

    override fun getChangePayload(oldItem: ChannelListItem, newItem: ChannelListItem): Any {
        // only called if their contents aren't the same, so they must be channel items and not loading items
        return oldItem
            .cast<ChannelListItem.ChannelItem>()
            .channel
            .diff(newItem.cast<ChannelListItem.ChannelItem>().channel)
    }
}
