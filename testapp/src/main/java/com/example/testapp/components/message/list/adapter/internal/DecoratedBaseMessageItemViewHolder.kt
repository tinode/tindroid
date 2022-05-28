package com.example.testapp.components.message.list.adapter.internal

import android.view.View
import androidx.annotation.CallSuper
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.MessageListItemPayloadDiff
import com.example.testapp.components.message.list.adapter.viewholder.BaseMessageItemViewHolder
import com.example.testapp.components.message.list.adapter.viewholder.decorator.Decorator

internal abstract class DecoratedBaseMessageItemViewHolder<T : MessageListItem>(
    itemView: View,
    private val decorators: List<Decorator>,
) : BaseMessageItemViewHolder<T>(itemView) {
    @CallSuper
    override fun bindData(data: T, diff: MessageListItemPayloadDiff?) {
        decorators.forEach { it.decorate(this, data) }
    }
}
