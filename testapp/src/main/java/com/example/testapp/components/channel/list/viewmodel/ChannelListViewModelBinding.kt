package com.example.testapp.components.channel.list.viewmodel

import android.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import com.example.testapp.components.channel.list.ChannelListView
import com.example.testapp.components.channel.list.adapter.ChannelListItem
import com.example.testapp.livedata.utils.EventObserver

/**
 * Binds [ChannelListView] with [ChannelListViewModel], updating the view's state based on
 * data provided by the ViewModel, and propagating view events to the ViewModel as needed.
 *
 * This function sets listeners on the view and ViewModel. Call this method
 * before setting any additional listeners on these objects yourself.
 */
@JvmName("bind")
public fun ChannelListViewModel.bindView(
    view: ChannelListView,
    lifecycleOwner: LifecycleOwner,
) {
    state.observe(lifecycleOwner) { channelState ->
        if (channelState.isLoading) {
            view.showLoadingView()
        } else {
            view.hideLoadingView()
            channelState
                .channels
                .map(ChannelListItem::ChannelItem)
                .let(view::setChannels)
        }
    }

//    paginationState.observe(lifecycleOwner) { paginationState ->
//        view.setPaginationEnabled(false)
//
//        if (paginationState.loadingMore) {
//            view.showLoadingMore()
//        } else {
//            view.hideLoadingMore()
//        }
//    }

    view.setOnEndReachedListener {
        onAction(ChannelListViewModel.Action.ReachedEndOfList)
    }

    view.setChannelDeleteClickListener {
        AlertDialog.Builder(view.context)
            .setTitle("Delete Chat")
            .setMessage("Are you sure you want to delete this chat?")
            .setPositiveButton("Delete") { dialog, _ ->
                dialog.dismiss()
                deleteChannel(it)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    view.setChannelLeaveClickListener { channel ->
        leaveChannel(channel)
    }

    errorEvents.observe(
        lifecycleOwner,
        EventObserver {
            view.showError(it)
        }
    )
}
