package com.example.testapp.components.channel.list.header.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.example.testapp.client.ChatClient
import com.example.testapp.client.models.User
import com.example.testapp.livedata.extensions.globalState
import com.example.testapp.livedata.model.ConnectionState
import com.example.testapp.livedata.state.global.GlobalState

/**
 * ViewModel class for [ChannelListHeaderView].
 * Responsible for updating current user information.
 * Can be bound to the view using [ChannelListHeaderViewModel.bindView] function.
 *
 * @param globalState Global state of OfflinePlugin. Contains information
 * such as the current user, connection state, unread counts etc.
 */
public class ChannelListHeaderViewModel @JvmOverloads constructor(
    globalState: GlobalState = ChatClient.instance().globalState,
) : ViewModel() {

    /**
     * The user who is currently logged in.
     */
    public val currentUser: LiveData<User?> = globalState.user.asLiveData()

    /**
     * The state of the connection for the user currently logged in.
     */
    public val connectionState: LiveData<ConnectionState> = globalState.connectionState.asLiveData()
}
