package com.example.testapp.components.search.internal

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.testapp.client.ChatClient
import com.example.testapp.client.models.Message
import com.example.testapp.components.common.extensions.internal.asMention
import com.example.testapp.components.common.extensions.internal.context
import com.example.testapp.components.common.extensions.internal.streamThemeInflater
import com.example.testapp.components.message.preview.MessagePreviewStyle
import com.example.testapp.components.search.internal.SearchResultListAdapter.MessagePreviewViewHolder
import com.example.testapp.components.search.list.SearchResultListView.SearchResultSelectedListener
import com.example.testapp.databinding.TinuiItemMentionListBinding
import com.example.testapp.livedata.extensions.globalState
import com.example.testapp.livedata.state.global.GlobalState

internal class SearchResultListAdapter(
    private val globalState: GlobalState = ChatClient.instance().globalState,
) : ListAdapter<Message, MessagePreviewViewHolder>(MessageDiffCallback) {

    private var searchResultSelectedListener: SearchResultSelectedListener? = null

    var messagePreviewStyle: MessagePreviewStyle? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessagePreviewViewHolder {
        return TinuiItemMentionListBinding
            .inflate(parent.streamThemeInflater, parent, false)
            .let { binding ->
                messagePreviewStyle?.let(binding.root::styleView)
                MessagePreviewViewHolder(binding)
            }
    }

    override fun onBindViewHolder(holder: MessagePreviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setSearchResultSelectedListener(searchResultSelectedListener: SearchResultSelectedListener?) {
        this.searchResultSelectedListener = searchResultSelectedListener
    }

    inner class MessagePreviewViewHolder(
        private val binding: TinuiItemMentionListBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var message: Message

        init {
            binding.root.setOnClickListener {
                searchResultSelectedListener?.onSearchResultSelected(message)
            }
        }

        internal fun bind(message: Message) {
            this.message = message
            binding.root.setMessage(message, globalState.user.value?.asMention(context))
        }
    }

    private object MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            // Comparing only properties used by the ViewHolder
            return oldItem.id == newItem.id &&
                oldItem.createdAt == newItem.createdAt &&
                oldItem.createdLocallyAt == newItem.createdLocallyAt &&
                oldItem.text == newItem.text &&
                oldItem.user == newItem.user
        }
    }
}
