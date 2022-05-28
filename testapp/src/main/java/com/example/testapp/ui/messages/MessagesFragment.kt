package com.example.testapp.ui.messages

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.testapp.client.models.Channel
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.Event
import com.example.testapp.components.message.list.viewmodel.MessageListViewModel.State
import com.example.testapp.components.message.list.viewmodel.bindView
import com.example.testapp.components.message.list.viewmodel.factory.MessageListViewModelFactory
import com.example.testapp.databinding.FragmentMessagesBinding
import com.example.testapp.ui.SharedViewModel

public class MessagesFragment(private val channel: Channel) : Fragment() {

    public companion object {
        public fun newInstance(channel: Channel): MessagesFragment = MessagesFragment(channel)
    }

    private val viewModelShared: SharedViewModel by activityViewModels()
    private lateinit var binding: FragmentMessagesBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentMessagesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cid = checkNotNull(channel.id) {
            "Specifying a channel id is required when starting MessagesFragment"
        }

        // Step 1 - Create three separate ViewModels for the views so it's easy
        //          to customize them individually
        val factory = MessageListViewModelFactory(cid)
//        val messageListHeaderViewModel: MessageListHeaderViewModel by viewModels { factory }
        val messageListViewModel: MessageListViewModel by viewModels { factory }
//        val messageInputViewModel: MessageInputViewModel by viewModels { factory }

        // Step 2 - Bind the view and ViewModels, they are loosely coupled so it's easy to customize
//        messageListHeaderViewModel.bindView(binding.messageListHeaderView, this)
        messageListViewModel.bindView(binding.messageListView, this)
//        messageInputViewModel.bindView(binding.messageInputView, this)

        // Step 5 - Handle navigate up state
        messageListViewModel.state.observe(viewLifecycleOwner) { state ->
            if (state is State.NavigateUp) {
                // remove channel from state
                viewModelShared.select(null)
            }
        }

        // Step 6 - Handle back button behaviour correctly when you're in a thread
        val backHandler = {
            messageListViewModel.onEvent(Event.BackButtonPressed)
        }
//        binding.messageListHeaderView.setBackButtonClickListener(backHandler)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            backHandler()
        }
    }

}