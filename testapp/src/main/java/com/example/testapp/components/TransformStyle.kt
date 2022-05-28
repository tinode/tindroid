package com.example.testapp.components

import com.example.testapp.components.avatar.AvatarStyle
import com.example.testapp.components.channel.list.ChannelListViewStyle
import com.example.testapp.components.message.list.MessageListItemStyle
import com.example.testapp.components.message.list.MessageListViewStyle
import com.example.testapp.components.message.list.ScrollButtonViewStyle
import com.example.testapp.components.search.SearchInputViewStyle
import com.example.testapp.components.search.list.SearchResultListViewStyle

public object TransformStyle {
    public var avatarStyleTransformer: StyleTransformer<AvatarStyle> = noopTransformer()
    public var chatListStyleTransformer: StyleTransformer<ChannelListViewStyle> = noopTransformer()
    public var messageListStyleTransformer: StyleTransformer<MessageListViewStyle> = noopTransformer()
    public var messageListItemStyleTransformer: StyleTransformer<MessageListItemStyle> = noopTransformer()
//    public var messageInputStyleTransformer: StyleTransformer<MessageInputViewStyle> = noopTransformer()
    public var scrollButtonStyleTransformer: StyleTransformer<ScrollButtonViewStyle> = noopTransformer()
//    public var viewReactionsStyleTransformer: StyleTransformer<ViewReactionsViewStyle> = noopTransformer()
//    public var editReactionsStyleTransformer: StyleTransformer<EditReactionsViewStyle> = noopTransformer()
//    public var channelActionsDialogStyleTransformer: StyleTransformer<ChannelActionsDialogViewStyle> = noopTransformer()
//    public var giphyViewHolderStyleTransformer: StyleTransformer<GiphyViewHolderStyle> = noopTransformer()
//    public var mediaAttachmentStyleTransformer: StyleTransformer<MediaAttachmentViewStyle> = noopTransformer()
//    public var messageReplyStyleTransformer: StyleTransformer<MessageReplyStyle> = noopTransformer()
//    public var fileAttachmentStyleTransformer: StyleTransformer<FileAttachmentViewStyle> = noopTransformer()
//    public var suggestionListStyleTransformer: StyleTransformer<SuggestionListViewStyle> = noopTransformer()
//    public var messageListHeaderStyleTransformer: StyleTransformer<MessageListHeaderViewStyle> = noopTransformer()
//    public var mentionListViewStyleTransformer: StyleTransformer<MentionListViewStyle> = noopTransformer()
    public var searchInputViewStyleTransformer: StyleTransformer<SearchInputViewStyle> = noopTransformer()
    public var searchResultListViewStyleTransformer: StyleTransformer<SearchResultListViewStyle> = noopTransformer()
//    public var typingIndicatorViewStyleTransformer: StyleTransformer<TypingIndicatorViewStyle> = noopTransformer()
//    public var pinnedMessageListViewStyleTransformer: StyleTransformer<PinnedMessageListViewStyle> = noopTransformer()

    private fun <T> noopTransformer() = StyleTransformer<T> { it }
}

public fun interface StyleTransformer<T> {
    public fun transform(source: T): T
}
