package com.example.testapp.components.message.list.internal

import com.example.testapp.components.message.list.MessageListView
import com.example.testapp.components.message.list.adapter.MessageListItem

internal object HiddenMessageListItemPredicate : MessageListView.MessageListItemPredicate {

    override fun predicate(item: MessageListItem): Boolean {
        return true
    }
}
