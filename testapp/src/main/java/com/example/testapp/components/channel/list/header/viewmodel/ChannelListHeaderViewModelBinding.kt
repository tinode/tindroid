package com.example.testapp.components.channel.list.header.viewmodel

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.example.testapp.components.channel.list.header.ChannelListHeaderView
import com.example.testapp.livedata.model.ConnectionState

/**
 * Binds [ChannelListHeaderView] with [ChannelListHeaderViewModel], updating the view's state
 * based on data provided by the ViewModel, and propagating view events to the ViewModel as needed.
 *
 * This function sets listeners on the view and ViewModel. Call this method
 * before setting any additional listeners on these objects yourself.
 */
@JvmName("bind")
public fun ChannelListHeaderViewModel.bindView(view: ChannelListHeaderView, lifecycleOwner: LifecycleOwner) {
    with(view) {
        currentUser.observe(lifecycleOwner) { user ->
            user?.let(::setUser)
        }
        connectionState.observe(lifecycleOwner) { connectionState ->
            Log.d("connectionState", connectionState.toString())
            when (connectionState) {
                ConnectionState.CONNECTED -> showOnlineTitle()
                ConnectionState.CONNECTING -> showConnectingTitle()
                ConnectionState.OFFLINE -> showOfflineTitle()
            }
        }
    }
}
