package com.example.testapp.components.channel.list.adapter.viewholder

import com.example.testapp.common.utils.ListenerDelegate
import com.example.testapp.components.channel.list.ChannelListView.ChannelClickListener
import com.example.testapp.components.channel.list.ChannelListView.ChannelLongClickListener
import com.example.testapp.components.channel.list.ChannelListView.SwipeListener
import com.example.testapp.components.channel.list.ChannelListView.UserClickListener
import com.example.testapp.components.channel.list.SwipeViewHolder

internal class ChannelListListenerContainerImpl(
    channelClickListener: ChannelClickListener = ChannelClickListener.DEFAULT,
    channelLongClickListener: ChannelLongClickListener = ChannelLongClickListener.DEFAULT,
    deleteClickListener: ChannelClickListener = ChannelClickListener.DEFAULT,
    moreOptionsClickListener: ChannelClickListener = ChannelClickListener.DEFAULT,
    userClickListener: UserClickListener = UserClickListener.DEFAULT,
    swipeListener: SwipeListener = SwipeListener.DEFAULT,
) : ChannelListListenerContainer {

    override var channelClickListener: ChannelClickListener by ListenerDelegate(channelClickListener) { realListener ->
        ChannelClickListener { chat ->
            realListener().onClick(chat)
        }
    }

    override var channelLongClickListener: ChannelLongClickListener by ListenerDelegate(
        channelLongClickListener
    ) { realListener ->
        ChannelLongClickListener { chat ->
            realListener().onLongClick(chat)
        }
    }

    override var deleteClickListener: ChannelClickListener by ListenerDelegate(deleteClickListener) { realListener ->
        ChannelClickListener { chat ->
            realListener().onClick(chat)
        }
    }

    override var moreOptionsClickListener: ChannelClickListener by ListenerDelegate(
        moreOptionsClickListener
    ) { realListener ->
        ChannelClickListener { chat ->
            realListener().onClick(chat)
        }
    }

    override var userClickListener: UserClickListener by ListenerDelegate(userClickListener) { realListener ->
        UserClickListener { user ->
            realListener().onClick(user)
        }
    }

    override var swipeListener: SwipeListener by ListenerDelegate(swipeListener) { realListener ->
        object : SwipeListener {
            override fun onSwipeStarted(viewHolder: SwipeViewHolder, adapterPosition: Int, x: Float?, y: Float?) {
                realListener().onSwipeStarted(viewHolder, adapterPosition, x, y)
            }

            override fun onSwipeChanged(
                viewHolder: SwipeViewHolder,
                adapterPosition: Int,
                dX: Float,
                totalDeltaX: Float,
            ) {
                realListener().onSwipeChanged(viewHolder, adapterPosition, dX, totalDeltaX)
            }

            override fun onSwipeCompleted(
                viewHolder: SwipeViewHolder,
                adapterPosition: Int,
                x: Float?,
                y: Float?,
            ) {
                realListener().onSwipeCompleted(viewHolder, adapterPosition, x, y)
            }

            override fun onSwipeCanceled(
                viewHolder: SwipeViewHolder,
                adapterPosition: Int,
                x: Float?,
                y: Float?,
            ) {
                realListener().onSwipeCanceled(viewHolder, adapterPosition, x, y)
            }

            override fun onRestoreSwipePosition(viewHolder: SwipeViewHolder, adapterPosition: Int) {
                realListener().onRestoreSwipePosition(viewHolder, adapterPosition)
            }
        }
    }
}
