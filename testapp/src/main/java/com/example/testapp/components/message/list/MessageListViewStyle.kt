package com.example.testapp.components.message.list

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.core.content.res.use
import com.example.testapp.R
import com.example.testapp.common.style.TextStyle
import com.example.testapp.components.TransformStyle
import com.example.testapp.components.common.extensions.internal.dpToPx
import com.example.testapp.components.common.extensions.internal.getColorCompat
import com.example.testapp.components.common.extensions.internal.getDimension
import com.example.testapp.components.common.extensions.internal.getDrawableCompat

/**
 * Style for [MessageListView].
 * Use this class together with [TransformStyle.messageListStyleTransformer] to change [MessageListView] styles programmatically.
 *
 * @property scrollButtonViewStyle Style for [ScrollButtonView].
 * @property scrollButtonBehaviour - On new messages always scroll to bottom or count new messages. Default - Count messages.
 * @property itemStyle Style for message list view holders.
 * @property giphyViewHolderStyle Style for [GiphyViewHolder].
 * @property replyMessageStyle Styles messages that are replies.
 * @property reactionsEnabled Enables/disables reactions feature. Enabled by default.
 * @property backgroundColor [MessageListView] background color. Default value is [R.color.tinui_white_snow].
 * @property replyIcon Icon for reply option. Default value is [R.drawable.tinui_ic_arrow_curve_left_grey].
 * @property replyEnabled Enables/disables reply feature. Enabled by default.
 * @property threadReplyIcon Icon for thread option. Default value is [R.drawable.tinui_ic_thread_reply].
 * @property threadsEnabled Enables/disables threads feature. Enabled by default.
 * @property retryIcon Icon for retry option. Default value is [R.drawable.tinui_ic_send].
 * @property copyIcon Icon for copy option. Default value is [R.drawable.tinui_ic_copy].
 * @property editMessageEnabled Enables/disables edit message feature. Enabled by default.
 * @property editIcon Icon for edit message option. Default value is [R.drawable.tinui_ic_edit].
 * @property flagIcon Icon for flag message option. Default value is [R.drawable.tinui_ic_flag].
 * @property flagEnabled Enables/disables "flag message" option.
 * @property pinIcon Icon for pin message option. Default value is [R.drawable.tinui_ic_pin].
 * @property unpinIcon Icon for unpin message option. Default value is [R.drawable.tinui_ic_unpin].
 * @property pinMessageEnabled Enables/disables pin message feature. Disabled by default.
 * @property muteIcon Icon for mute option. Default value is [R.drawable.tinui_ic_mute].
 * @property unmuteIcon Icon for the unmute option. Default value is [R.drawable.tinui_ic_umnute].
 * @property muteEnabled Enables/disables "mute user" option.
 * @property blockIcon Icon for block option. Default value is [R.drawable.tinui_ic_user_block].
 * @property blockEnabled Enables/disables "block user" option.
 * @property deleteIcon Icon for delete message option. Default value is [R.drawable.tinui_ic_delete].
 * @property deleteMessageEnabled Enables/disables delete message feature. Enabled by default.
 * @property copyTextEnabled Enables/disables copy text feature. Enabled by default.
 * @property retryMessageEnabled Enables/disables retry failed message feature. Enabled by default.
 * @property deleteConfirmationEnabled Enables/disables showing confirmation dialog before deleting message. Enabled by default.
 * @property flagMessageConfirmationEnabled Enables/disables showing confirmation dialog before flagging message. Disabled by default.
 * @property messageOptionsText Text appearance of message option items.
 * @property warningMessageOptionsText Text appearance of warning message option items.
 * @property messageOptionsBackgroundColor Background color of message options. Default value is [R.color.tinui_white].
 * @property userReactionsBackgroundColor Background color of user reactions card. Default value is [R.color.tinui_white].
 * @property userReactionsTitleText Text appearance of of user reactions card title.
 * @property optionsOverlayDimColor Overlay dim color. Default value is [R.color.tinui_literal_transparent].
 * @property emptyViewTextStyle Style for the text displayed in the empty view when no data is present.
 * @property loadingView Layout for the loading view. Default value is [R.layout.tinui_default_loading_view].
 * @property messagesStart Messages start at the bottom or top of the screen. Default: bottom.
 * @property threadMessagesStart Thread messages start at the bottom or top of the screen. Default: bottom.
 */
public data class MessageListViewStyle(
    public val scrollButtonViewStyle: ScrollButtonViewStyle,
    public val scrollButtonBehaviour: MessageListView.NewMessagesBehaviour,
    public val itemStyle: MessageListItemStyle,
//    public val giphyViewHolderStyle: GiphyViewHolderStyle,
//    public val replyMessageStyle: MessageReplyStyle,
//    public val reactionsEnabled: Boolean,
    @ColorInt public val backgroundColor: Int,
//    val replyIcon: Int,
    val replyEnabled: Boolean,
//    val threadReplyIcon: Int,
//    val threadsEnabled: Boolean,
    val retryIcon: Int,
    val copyIcon: Int,
    val editMessageEnabled: Boolean,
    val editIcon: Int,
    val flagIcon: Int,
    val flagEnabled: Boolean,
    val pinIcon: Int,
    val unpinIcon: Int,
    val pinMessageEnabled: Boolean,
    val muteIcon: Int,
    val unmuteIcon: Int,
    val muteEnabled: Boolean,
    val blockIcon: Int,
    val blockEnabled: Boolean,
    val deleteIcon: Int,
    val deleteMessageEnabled: Boolean,
    val copyTextEnabled: Boolean,
    val retryMessageEnabled: Boolean,
    val deleteConfirmationEnabled: Boolean,
    val flagMessageConfirmationEnabled: Boolean,
    val messageOptionsText: TextStyle,
    val warningMessageOptionsText: TextStyle,
    @ColorInt val messageOptionsBackgroundColor: Int,
//    @ColorInt val userReactionsBackgroundColor: Int,
//    val userReactionsTitleText: TextStyle,
    @ColorInt val optionsOverlayDimColor: Int,
    val emptyViewTextStyle: TextStyle,
    @LayoutRes public val loadingView: Int,
    public val messagesStart: Int,
//    public val threadMessagesStart: Int,
) {

    internal companion object {
        private val DEFAULT_BACKGROUND_COLOR = R.color.tinui_white_snow
        private val DEFAULT_SCROLL_BUTTON_ELEVATION = 3.dpToPx().toFloat()

        private fun emptyViewStyle(context: Context, typedArray: TypedArray): TextStyle {
            return TextStyle.Builder(typedArray)
                .color(
                    R.styleable.MessageListView_tinui_EmptyStateTextColor,
                    context.getColorCompat(R.color.tinui_text_color_primary)
                )
                .size(
                    R.styleable.MessageListView_tinui_EmptyStateTextSize,
                    context.getDimension(R.dimen.tinui_text_medium)
                )
                .font(
                    R.styleable.MessageListView_tinui_EmptyStateTextFontAssets,
                    R.styleable.MessageListView_tinui_EmptyStateTextFont,
                )
                .style(
                    R.styleable.MessageListView_tinui_EmptyStateTextStyle,
                    Typeface.NORMAL
                )
                .build()
        }

        operator fun invoke(context: Context, attrs: AttributeSet?): MessageListViewStyle {
            context.obtainStyledAttributes(
                attrs,
                R.styleable.MessageListView,
                R.attr.tinui_MessageListStyle,
                R.style.tinui_MessageList
            ).use { attributes ->
                val scrollButtonViewStyle = ScrollButtonViewStyle.Builder(context, attributes)
                    .scrollButtonEnabled(
                        R.styleable.MessageListView_tinui_ScrollButtonEnabled,
                        true
                    )
                    .scrollButtonUnreadEnabled(
                        R.styleable.MessageListView_tinui_ScrollButtonUnreadEnabled,
                        true
                    )
                    .scrollButtonColor(
                        R.styleable.MessageListView_tinui_ScrollButtonColor,
                        context.getColorCompat(R.color.tinui_white)
                    )
                    .scrollButtonRippleColor(
                        R.styleable.MessageListView_tinui_ScrollButtonRippleColor,
                        context.getColorCompat(R.color.tinui_white_smoke)
                    )
                    .scrollButtonBadgeColor(
                        R.styleable.MessageListView_tinui_ScrollButtonBadgeColor,
                        context.getColorCompat(R.color.tinui_accent_blue)
                    )
                    .scrollButtonElevation(
                        R.styleable.MessageListView_tinui_ScrollButtonElevation,
                        DEFAULT_SCROLL_BUTTON_ELEVATION
                    )
                    .scrollButtonIcon(
                        R.styleable.MessageListView_tinui_ScrollButtonIcon,
                        context.getDrawableCompat(R.drawable.tinui_ic_down)
                    ).build()

                val scrollButtonBehaviour = MessageListView.NewMessagesBehaviour.parseValue(
                    attributes.getInt(
                        R.styleable.MessageListView_tinui_NewMessagesBehaviour,
                        MessageListView.NewMessagesBehaviour.COUNT_UPDATE.value
                    )
                )

//                val reactionsEnabled = attributes.getBoolean(
//                    R.styleable.MessageListView_tinui_ReactionsEnabled,
//                    true
//                )

                val backgroundColor = attributes.getColor(
                    R.styleable.MessageListView_tinui_BackgroundColor,
                    context.getColorCompat(DEFAULT_BACKGROUND_COLOR)
                )

                val itemStyle = MessageListItemStyle.Builder(attributes, context)
                    .messageBackgroundColorMine(R.styleable.MessageListView_tinui_MessageBackgroundColorMine)
                    .messageBackgroundColorTheirs(R.styleable.MessageListView_tinui_MessageBackgroundColorTheirs)
                    .messageLinkTextColorMine(R.styleable.MessageListView_tinui_MessageLinkColorMine)
                    .messageLinkTextColorTheirs(R.styleable.MessageListView_tinui_MessageLinkColorTheirs)
//                    .reactionsEnabled(R.styleable.MessageListView_tinui_ReactionsEnabled)
                    .linkDescriptionMaxLines(R.styleable.MessageListView_tinui_LinkDescriptionMaxLines)
                    .build()

//                val giphyViewHolderStyle = GiphyViewHolderStyle(context = context, attributes = attributes)
//                val replyMessageStyle = MessageReplyStyle(context = context, attributes = attributes)

//                val replyIcon = attributes.getResourceId(
//                    R.styleable.MessageListView_tinui_ReplyOptionIcon,
//                    R.drawable.tinui_ic_arrow_curve_left_grey
//                )

                val replyEnabled = attributes.getBoolean(R.styleable.MessageListView_tinui_ReplyEnabled, true)

//                val threadReplyIcon = attributes.getResourceId(
//                    R.styleable.MessageListView_tinui_ThreadReplyOptionIcon,
//                    R.drawable.tinui_ic_thread_reply,
//                )

                val retryIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_RetryOptionIcon,
                    R.drawable.tinui_ic_send,
                )

                val copyIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_CopyOptionIcon,
                    R.drawable.tinui_ic_copy,
                )

                val editIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_EditOptionIcon,
                    R.drawable.tinui_ic_edit,
                )

                val flagIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_FlagOptionIcon,
                    R.drawable.tinui_ic_flag,
                )

                val muteIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_MuteOptionIcon,
                    R.drawable.tinui_ic_mute
                )

                val unmuteIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_UnmuteOptionIcon,
                    R.drawable.tinui_ic_umnute,
                )

                val blockIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_BlockOptionIcon,
                    R.drawable.tinui_ic_user_block,
                )

                val deleteIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_DeleteOptionIcon,
                    R.drawable.tinui_ic_delete,
                )

                val flagEnabled = attributes.getBoolean(R.styleable.MessageListView_tinui_FlagMessageEnabled, true)

                val pinIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_PinOptionIcon,
                    R.drawable.tinui_ic_pin,
                )

                val unpinIcon = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_UnpinOptionIcon,
                    R.drawable.tinui_ic_unpin,
                )

                val pinMessageEnabled =
                    attributes.getBoolean(R.styleable.MessageListView_tinui_PinMessageEnabled, false)

                val muteEnabled = attributes.getBoolean(R.styleable.MessageListView_tinui_MuteUserEnabled, true)

                val blockEnabled = attributes.getBoolean(R.styleable.MessageListView_tinui_BlockUserEnabled, true)

                val copyTextEnabled =
                    attributes.getBoolean(R.styleable.MessageListView_tinui_CopyMessageActionEnabled, true)

                val retryMessageEnabled =
                    attributes.getBoolean(R.styleable.MessageListView_tinui_RetryMessageEnabled, true)

                val deleteConfirmationEnabled =
                    attributes.getBoolean(R.styleable.MessageListView_tinui_DeleteConfirmationEnabled, true)

                val flagMessageConfirmationEnabled =
                    attributes.getBoolean(R.styleable.MessageListView_tinui_FlagMessageConfirmationEnabled, false)
                val deleteMessageEnabled =
                    attributes.getBoolean(R.styleable.MessageListView_tinui_DeleteMessageEnabled, true)

                val editMessageEnabled =
                    attributes.getBoolean(R.styleable.MessageListView_tinui_EditMessageEnabled, true)

//                val threadsEnabled = attributes.getBoolean(R.styleable.MessageListView_tinui_ThreadsEnabled, true)

                val messageOptionsText = TextStyle.Builder(attributes)
                    .size(
                        R.styleable.MessageListView_tinui_MessageOptionsTextSize,
                        context.getDimension(R.dimen.tinui_text_medium)
                    )
                    .color(
                        R.styleable.MessageListView_tinui_MessageOptionsTextColor,
                        context.getColorCompat(R.color.tinui_text_color_primary)
                    )
                    .font(
                        R.styleable.MessageListView_tinui_MessageOptionsTextFontAssets,
                        R.styleable.MessageListView_tinui_MessageOptionsTextFont
                    )
                    .style(
                        R.styleable.MessageListView_tinui_MessageOptionsTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val warningMessageOptionsText = TextStyle.Builder(attributes)
                    .size(
                        R.styleable.MessageListView_tinui_WarningMessageOptionsTextSize,
                        context.getDimension(R.dimen.tinui_text_medium)
                    )
                    .color(
                        R.styleable.MessageListView_tinui_WarningMessageOptionsTextColor,
                        context.getColorCompat(R.color.tinui_accent_red)
                    )
                    .font(
                        R.styleable.MessageListView_tinui_WarningMessageOptionsTextFontAssets,
                        R.styleable.MessageListView_tinui_WarningMessageOptionsTextFont
                    )
                    .style(
                        R.styleable.MessageListView_tinui_WarningMessageOptionsTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val messageOptionsBackgroundColor = attributes.getColor(
                    R.styleable.MessageListView_tinui_MessageOptionBackgroundColor,
                    context.getColorCompat(R.color.tinui_white)
                )
//
//                val userReactionsBackgroundColor = attributes.getColor(
//                    R.styleable.MessageListView_tinui_UserReactionsBackgroundColor,
//                    context.getColorCompat(R.color.tinui_white)
//                )

//                val userReactionsTitleText = TextStyle.Builder(attributes)
//                    .size(
//                        R.styleable.MessageListView_tinui_UserReactionsTitleTextSize,
//                        context.getDimension(R.dimen.tinui_text_large)
//                    )
//                    .color(
//                        R.styleable.MessageListView_tinui_UserReactionsTitleTextColor,
//                        context.getColorCompat(R.color.tinui_text_color_primary)
//                    )
//                    .font(
//                        R.styleable.MessageListView_tinui_UserReactionsTitleTextFontAssets,
//                        R.styleable.MessageListView_tinui_UserReactionsTitleTextFont
//                    )
//                    .style(
//                        R.styleable.MessageListView_tinui_UserReactionsTitleTextStyle,
//                        Typeface.BOLD
//                    )
//                    .build()

                val optionsOverlayDimColor = attributes.getColor(
                    R.styleable.MessageListView_tinui_OptionsOverlayDimColor,
                    context.getColorCompat(R.color.tinui_literal_transparent)
                )

                val emptyViewTextStyle = emptyViewStyle(context, attributes)

                val loadingView = attributes.getResourceId(
                    R.styleable.MessageListView_tinui_MessageListLoadingView,
                    R.layout.tinui_default_loading_view,
                )

                val messagesStart = attributes.getInt(
                    R.styleable.MessageListView_tinui_MessagesStart,
                    MessageListView.MessagesStart.BOTTOM.value,
                )

                val threadMessagesStart = attributes.getInt(
                    R.styleable.MessageListView_tinui_ThreadMessagesStart,
                    MessageListView.MessagesStart.BOTTOM.value,
                )

                return MessageListViewStyle(
                    scrollButtonViewStyle = scrollButtonViewStyle,
                    scrollButtonBehaviour = scrollButtonBehaviour,
//                    reactionsEnabled = reactionsEnabled,
                    itemStyle = itemStyle,
//                    giphyViewHolderStyle = giphyViewHolderStyle,
//                    replyMessageStyle = replyMessageStyle,
                    backgroundColor = backgroundColor,
//                    replyIcon = replyIcon,
                    replyEnabled = replyEnabled,
//                    threadReplyIcon = threadReplyIcon,
                    retryIcon = retryIcon,
                    copyIcon = copyIcon,
                    editIcon = editIcon,
                    flagIcon = flagIcon,
                    flagEnabled = flagEnabled,
                    pinIcon = pinIcon,
                    unpinIcon = unpinIcon,
                    pinMessageEnabled = pinMessageEnabled,
                    muteIcon = muteIcon,
                    unmuteIcon = unmuteIcon,
                    muteEnabled = muteEnabled,
                    blockIcon = blockIcon,
                    blockEnabled = blockEnabled,
                    deleteIcon = deleteIcon,
                    copyTextEnabled = copyTextEnabled,
                    retryMessageEnabled = retryMessageEnabled,
                    deleteConfirmationEnabled = deleteConfirmationEnabled,
                    flagMessageConfirmationEnabled = flagMessageConfirmationEnabled,
                    deleteMessageEnabled = deleteMessageEnabled,
                    editMessageEnabled = editMessageEnabled,
//                    threadsEnabled = threadsEnabled,
                    messageOptionsText = messageOptionsText,
                    warningMessageOptionsText = warningMessageOptionsText,
                    messageOptionsBackgroundColor = messageOptionsBackgroundColor,
//                    userReactionsBackgroundColor = userReactionsBackgroundColor,
//                    userReactionsTitleText = userReactionsTitleText,
                    optionsOverlayDimColor = optionsOverlayDimColor,
                    emptyViewTextStyle = emptyViewTextStyle,
                    loadingView = loadingView,
                    messagesStart = messagesStart,
//                    threadMessagesStart = threadMessagesStart,
                ).let(TransformStyle.messageListStyleTransformer::transform)
            }
        }
    }
}
