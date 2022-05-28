package com.example.testapp.components.message.list.adapter.viewholder.internal

import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.example.testapp.components.common.extensions.internal.streamThemeInflater
import com.example.testapp.components.common.internal.LongClickFriendlyLinkMovementMethod
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.MessageListItemPayloadDiff
import com.example.testapp.components.message.list.adapter.MessageListListenerContainer
import com.example.testapp.components.message.list.adapter.internal.DecoratedBaseMessageItemViewHolder
import com.example.testapp.components.message.list.adapter.viewholder.decorator.Decorator
import com.example.testapp.components.transformer.ChatMessageTextTransformer
import com.example.testapp.databinding.TinuiItemMessagePlainTextBinding

internal class MessagePlainTextViewHolder(
    parent: ViewGroup,
    decorators: List<Decorator>,
    private val listeners: MessageListListenerContainer?,
    private val messageTextTransformer: ChatMessageTextTransformer,
    internal val binding: TinuiItemMessagePlainTextBinding =
        TinuiItemMessagePlainTextBinding.inflate(
            parent.streamThemeInflater,
            parent,
            false
        ),
) : DecoratedBaseMessageItemViewHolder<MessageListItem.MessageItem>(binding.root, decorators) {

    init {
        binding.run {
            listeners?.let { container ->
                root.setOnClickListener {
                    container.messageClickListener.onMessageClick(data.message)
                }
//                reactionsView.setReactionClickListener {
//                    container.reactionViewClickListener.onReactionViewClick(data.message)
//                }
//                footnote.setOnThreadClickListener {
//                    container.threadClickListener.onThreadClick(data.message)
//                }
                root.setOnLongClickListener {
                    container.messageLongClickListener.onMessageLongClick(data.message)
                    true
                }
                avatarView.setOnClickListener {
                    container.userClickListener.onUserClick(data.message.user)
                }
                LongClickFriendlyLinkMovementMethod.set(
                    textView = messageText,
                    longClickTarget = root,
                    onLinkClicked = container.linkClickListener::onLinkClick
                )
            }
        }
    }

    override fun bindData(data: MessageListItem.MessageItem, diff: MessageListItemPayloadDiff?) {
        super.bindData(data, diff)
        val textUnchanged = diff?.text == false
        val mentionsUnchanged = diff?.mentions == false
        if (textUnchanged && mentionsUnchanged) return

        with(binding) {
            messageTextTransformer.transformAndApply(messageText, data)
            messageContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                horizontalBias = if (data.isTheirs) 0f else 1f
            }
        }
    }
}
