package com.example.testapp.ui.chats

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.testapp.R
import com.example.testapp.client.models.Channel
import com.example.testapp.components.channel.list.viewmodel.ChannelListViewModel
import com.example.testapp.components.channel.list.viewmodel.bindView
import com.example.testapp.components.channel.list.viewmodel.factory.ChannelListViewModelFactory
import com.example.testapp.databinding.FragmentChatsBinding
import com.example.testapp.ui.SharedViewModel

public class ChatsFragment : Fragment() {

    public companion object {
        public fun newInstance(): ChatsFragment = ChatsFragment()
    }

    private val viewModelShared: SharedViewModel by activityViewModels()
    private lateinit var binding: FragmentChatsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentChatsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewModel: ChannelListViewModel by viewModels { ChannelListViewModelFactory() }
        viewModel.bindView(binding.channelListView, this)
        binding.channelListView.setChannelItemClickListener { channel ->
            Log.e("TestApplication", "Click ${channel.id}")
            // TODO - start channel activity
            viewModelShared.select(channel)
        }
    }
}