package com.example.testapp.components.channel.list.viewmodel.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.testapp.client.api.models.FilterObject
import com.example.testapp.components.channel.list.viewmodel.ChannelListViewModel

/**
 * Creates a channels view model factory.
 *
 * @param filter How to filter the channels.
 * @param sort How to sort the channels, defaults to last_updated.
 * @param limit How many channels to return.
 * @param memberLimit The number of members per channel.
 * @param messageLimit The number of messages to fetch for each channel.
 * @param chatEventHandlerFactory The instance of [ChatEventHandlerFactory] that will be used to create [ChatEventHandler].
 *
 * @see Filters
 * @see QuerySort
 */
public class ChannelListViewModelFactory @JvmOverloads constructor(
    private val filter: FilterObject? = null,
//    private val sort: QuerySort<Chat> = ChannelListViewModel.DEFAULT_SORT,
    private val messageLimit: Int = 1,
    private val memberLimit: Int = 30,
//    private val chatEventHandlerFactory: ChatEventHandlerFactory = ChatEventHandlerFactory(),
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == ChannelListViewModel::class.java) {
            "ChannelListViewModelFactory can only create instances of ChannelListViewModel"
        }

        @Suppress("UNCHECKED_CAST")
        return ChannelListViewModel(
            filter = filter,
//            sort = sort,
            messageLimit = messageLimit,
            memberLimit = memberLimit,
//            chatEventHandlerFactory = chatEventHandlerFactory,
        ) as T
    }
}
