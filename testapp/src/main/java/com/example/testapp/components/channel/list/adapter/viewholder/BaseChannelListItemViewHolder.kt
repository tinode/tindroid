package com.example.testapp.components.channel.list.adapter.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.testapp.client.models.Channel
import com.example.testapp.components.channel.list.adapter.ChannelListPayloadDiff

public abstract class BaseChannelListItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    public abstract fun bind(channel: Channel, diff: ChannelListPayloadDiff)
}
