package com.example.testapp.components.message.list.adapter.viewholder

import com.example.testapp.common.utils.DateFormatter
import com.example.testapp.components.message.list.MessageListView
import com.example.testapp.components.message.list.MessageListViewStyle
import com.example.testapp.components.message.list.adapter.viewholder.decorator.Decorator
import com.example.testapp.components.message.list.adapter.viewholder.decorator.DecoratorProvider
import com.example.testapp.components.message.list.adapter.viewholder.decorator.internal.*
import com.example.testapp.components.message.list.adapter.viewholder.decorator.internal.BackgroundDecorator
import com.example.testapp.components.message.list.adapter.viewholder.decorator.internal.GapDecorator
import com.example.testapp.components.message.list.adapter.viewholder.decorator.internal.MaxPossibleWidthDecorator
import com.example.testapp.components.message.list.adapter.viewholder.decorator.internal.MessageContainerMarginDecorator
import com.example.testapp.components.message.list.adapter.viewholder.decorator.internal.TextDecorator
import com.example.testapp.components.message.list.background.MessageBackgroundFactory

/**
 * Provides all decorators that will be used in MessageListView items.
 *
 * @param dateFormatter [DateFormatter]. Formats the dates in the messages.
 * @param isDirectMessage Checks if the message is direct of not. Used in the footnote.
 * @param messageListViewStyle [MessageListViewStyle] The style of the MessageListView and its items.
 * @param showAvatarPredicate [MessageListView.ShowAvatarPredicate] Checks if should show the avatar or not accordingly with the provided logic.
 * @param messageBackgroundFactory [MessageBackgroundFactory] Factory that customizes the background of messages.
 * @param deletedMessageListItemPredicate [MessageListView.MessageListItemPredicate] Predicate to hide or show the the deleted message accordingly to the logic provided.
 * @param isCurrentUserBanned Checks if the current user is banned inside the channel. Used for failed icon indicator.
 */
internal class MessageListItemDecoratorProvider(
    dateFormatter: DateFormatter,
    isDirectMessage: () -> Boolean,
    messageListViewStyle: MessageListViewStyle,
    showAvatarPredicate: MessageListView.ShowAvatarPredicate,
    messageBackgroundFactory: MessageBackgroundFactory,
    deletedMessageListItemPredicate: MessageListView.MessageListItemPredicate,
    isCurrentUserBanned: () -> Boolean
) : DecoratorProvider {

    private val messageListDecorators = listOfNotNull<Decorator>(
        BackgroundDecorator(messageBackgroundFactory),
        TextDecorator(messageListViewStyle.itemStyle),
        GapDecorator(),
        MaxPossibleWidthDecorator(messageListViewStyle.itemStyle),
        MessageContainerMarginDecorator(messageListViewStyle.itemStyle),
        AvatarDecorator(showAvatarPredicate),
        FailedIndicatorDecorator(messageListViewStyle.itemStyle, isCurrentUserBanned),
//        ReactionsDecorator(messageListViewStyle.itemStyle).takeIf { messageListViewStyle.reactionsEnabled },
//        ReplyDecorator(messageListViewStyle.replyMessageStyle),
        FootnoteDecorator(dateFormatter, isDirectMessage, messageListViewStyle, deletedMessageListItemPredicate),
//        PinIndicatorDecorator(messageListViewStyle.itemStyle).takeIf { messageListViewStyle.pinMessageEnabled },
    )

    override val decorators: List<Decorator> = messageListDecorators
}
