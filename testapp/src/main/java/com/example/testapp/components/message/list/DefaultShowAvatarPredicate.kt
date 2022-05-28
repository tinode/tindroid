package com.example.testapp.components.message.list

import com.example.testapp.components.message.list.adapter.MessageListItem

internal class DefaultShowAvatarPredicate : MessageListView.ShowAvatarPredicate {
    override fun shouldShow(messageItem: MessageListItem.MessageItem): Boolean {
        return messageItem.positions.contains(MessageListItem.Position.BOTTOM) && messageItem.isTheirs
    }
}
