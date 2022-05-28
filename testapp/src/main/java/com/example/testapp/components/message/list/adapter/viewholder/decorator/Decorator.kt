package com.example.testapp.components.message.list.adapter.viewholder.decorator

import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.viewholder.BaseMessageItemViewHolder

internal interface Decorator {

    fun <T : MessageListItem> decorate(
        viewHolder: BaseMessageItemViewHolder<T>,
        data: T,
    )
}
