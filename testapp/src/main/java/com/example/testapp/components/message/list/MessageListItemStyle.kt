package com.example.testapp.components.message.list

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.annotation.StyleableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.testapp.R
import com.example.testapp.common.style.TextStyle
import com.example.testapp.components.TransformStyle
import com.example.testapp.components.common.extensions.internal.dpToPxPrecise
import com.example.testapp.components.common.extensions.internal.getColorCompat
import com.example.testapp.components.common.extensions.internal.getDimension
import com.example.testapp.components.common.extensions.internal.getDrawableCompat

/**
 * Style for view holders used inside [MessageListView].
 * Use this class together with [TransformStyle.messageListItemStyleTransformer] to change styles programmatically.
 *
 * @property messageBackgroundColorMine Background color for message sent by the current user. Default value is [R.color.tinui_grey_gainsboro].
 * @property messageBackgroundColorTheirs Background color for message sent by other user. Default value is [R.color.tinui_white].
 * @property messageLinkTextColorMine Color for links sent by the current user. Default value is [R.color.tinui_accent_blue].
 * @property messageLinkTextColorTheirs Color for links sent by other user. Default value is [R.color.tinui_accent_blue].
 * @property messageLinkBackgroundColorMine Background color for message with link, sent by the current user. Default value is [R.color.tinui_blue_alice].
 * @property messageLinkBackgroundColorTheirs Background color for message with link, sent by other user. Default value is [R.color.tinui_blue_alice].
 * @property linkDescriptionMaxLines Max lines for link's description. Default value is 5.
 * @property textStyleMine Appearance for message text sent by the current user.
 * @property textStyleTheirs Appearance for message text sent by other user.
 * @property textStyleUserName Appearance for user name text.
 * @property textStyleMessageDate Appearance for message date text.
 * @property textStyleThreadCounter Appearance for thread counter text.
 * @property textStyleLinkTitle Appearance for link.
 * @property textStyleLinkDescription Appearance for link's description text.
 * @property dateSeparatorBackgroundColor Background color for data separator. Default value is [R.color.tinui_overlay_dark].
 * @property textStyleDateSeparator Appearance for date separator text.
 * @property reactionsViewStyle Style for [ViewReactionsView].
 * @property editReactionsViewStyle Style for [EditReactionsView].
 * @property iconIndicatorSent Icon for message's sent status. Default value is [R.drawable.tinui_ic_check_single].
 * @property iconIndicatorRead Icon for message's read status. Default value is [R.drawable.tinui_ic_check_double].
 * @property iconIndicatorPendingSync Icon for message's pending status. Default value is [R.drawable.tinui_ic_clock].
 * @property iconOnlyVisibleToYou Icon for message's pending status. Default value is [R.drawable.tinui_ic_icon_eye_off].
 * @property textStyleMessageDeleted Appearance for message deleted text.
 * @property messageDeletedBackground Background color for deleted message. Default value is [R.color.tinui_grey_whisper].
 * @property messageStrokeColorMine Stroke color for message sent by the current user. Default value is [MESSAGE_STROKE_COLOR_MINE].
 * @property messageStrokeWidthMine Stroke width for message sent by the current user. Default value is [MESSAGE_STROKE_WIDTH_MINE].
 * @property messageStrokeColorTheirs Stroke color for message sent by other user. Default value is [MESSAGE_STROKE_COLOR_THEIRS].
 * @property messageStrokeWidthTheirs Stroke width for message sent by other user. Default value is [MESSAGE_STROKE_WIDTH_THEIRS].
 * @property textStyleSystemMessage Appearance for system message text.
 * @property textStyleErrorMessage Appearance for error message text.
 * @property messageStartMargin Margin for messages in the left side. Default value is 48dp.
 * @property messageEndMargin Margin for messages in the right side. Default value is 0dp.
 * @property messageMaxWidthFactorMine Factor used to compute max width for message sent by the current user. Should be in <0.75, 1> range.
 * @property messageMaxWidthFactorTheirs Factor used to compute max width for message sent by other user. Should be in <0.75, 1> range.
 * @property showMessageDeliveryStatusIndicator Flag if we need to show the delivery indicator or not.
 * @property iconFailedMessage Icon for message failed status. Default value is [R.drawable.tinui_ic_warning].
 * @property iconBannedMessage Icon for message when the current user is banned. Default value is [R.drawable.tinui_ic_warning].
 */
public data class MessageListItemStyle(
    @ColorInt public val messageBackgroundColorMine: Int?,
    @ColorInt public val messageBackgroundColorTheirs: Int?,
    @ColorInt public val messageLinkTextColorMine: Int?,
    @ColorInt public val messageLinkTextColorTheirs: Int?,
    @ColorInt public val messageLinkBackgroundColorMine: Int,
    @ColorInt public val messageLinkBackgroundColorTheirs: Int,
    public val linkDescriptionMaxLines: Int,
    public val textStyleMine: TextStyle,
    public val textStyleTheirs: TextStyle,
    public val textStyleUserName: TextStyle,
    public val textStyleMessageDate: TextStyle,
//    public val textStyleThreadCounter: TextStyle,
//    public val threadSeparatorTextStyle: TextStyle,
    public val textStyleLinkLabel: TextStyle,
    public val textStyleLinkTitle: TextStyle,
    public val textStyleLinkDescription: TextStyle,
    @ColorInt public val dateSeparatorBackgroundColor: Int,
    public val textStyleDateSeparator: TextStyle,
//    public val reactionsViewStyle: ViewReactionsViewStyle,
//    public val editReactionsViewStyle: EditReactionsViewStyle,
    public val iconIndicatorSent: Drawable,
    public val iconIndicatorRead: Drawable,
    public val iconIndicatorPendingSync: Drawable,
    public val iconOnlyVisibleToYou: Drawable,
    public val textStyleMessageDeleted: TextStyle,
    @ColorInt public val messageDeletedBackground: Int,
    @ColorInt public val messageStrokeColorMine: Int,
    @Px public val messageStrokeWidthMine: Float,
    @ColorInt public val messageStrokeColorTheirs: Int,
    @Px public val messageStrokeWidthTheirs: Float,
    public val textStyleSystemMessage: TextStyle,
    public val textStyleErrorMessage: TextStyle,
    public val pinnedMessageIndicatorTextStyle: TextStyle,
    public val pinnedMessageIndicatorIcon: Drawable,
    @ColorInt public val pinnedMessageBackgroundColor: Int,
    @Px public val messageStartMargin: Int,
    @Px public val messageEndMargin: Int,
    public val messageMaxWidthFactorMine: Float,
    public val messageMaxWidthFactorTheirs: Float,
    public val showMessageDeliveryStatusIndicator: Boolean,
    public val iconFailedMessage: Drawable,
    public val iconBannedMessage: Drawable,
) {

    @ColorInt
    public fun getStyleTextColor(isMine: Boolean): Int? {
        return if (isMine) textStyleMine.colorOrNull() else textStyleTheirs.colorOrNull()
    }

    @ColorInt
    public fun getStyleLinkTextColor(isMine: Boolean): Int? {
        return if (isMine) messageLinkTextColorMine else messageLinkTextColorTheirs
    }

    internal companion object {
        internal const val VALUE_NOT_SET = Integer.MAX_VALUE

        internal val DEFAULT_LINK_BACKGROUND_COLOR = R.color.tinui_blue_alice

        internal val DEFAULT_TEXT_COLOR = R.color.tinui_text_color_primary
        internal val DEFAULT_TEXT_SIZE = R.dimen.tinui_text_medium
        internal const val DEFAULT_TEXT_STYLE = Typeface.NORMAL

        internal val DEFAULT_TEXT_COLOR_USER_NAME = R.color.tinui_text_color_secondary
        internal val DEFAULT_TEXT_SIZE_USER_NAME = R.dimen.tinui_text_small

        internal val DEFAULT_TEXT_COLOR_DATE = R.color.tinui_text_color_secondary
        internal val DEFAULT_TEXT_SIZE_DATE = R.dimen.tinui_text_small

        internal val DEFAULT_TEXT_COLOR_THREAD_COUNTER = R.color.tinui_accent_blue
        internal val DEFAULT_TEXT_SIZE_THREAD_COUNTER = R.dimen.tinui_text_small

        internal val DEFAULT_TEXT_COLOR_LINK_DESCRIPTION = R.color.tinui_text_color_secondary
        internal val DEFAULT_TEXT_SIZE_LINK_DESCRIPTION = R.dimen.tinui_text_small

        internal val DEFAULT_TEXT_COLOR_DATE_SEPARATOR = R.color.tinui_white
        internal val DEFAULT_TEXT_SIZE_DATE_SEPARATOR = R.dimen.tinui_text_small

        internal val MESSAGE_STROKE_COLOR_MINE = R.color.tinui_literal_transparent
        internal const val MESSAGE_STROKE_WIDTH_MINE: Float = 0f
        internal val MESSAGE_STROKE_COLOR_THEIRS = R.color.tinui_grey_whisper
        internal val MESSAGE_STROKE_WIDTH_THEIRS: Float = 1.dpToPxPrecise()

        private const val BASE_MESSAGE_MAX_WIDTH_FACTOR = 1
        private const val DEFAULT_MESSAGE_MAX_WIDTH_FACTOR = 0.75f
    }

    internal class Builder(private val attributes: TypedArray, private val context: Context) {
        @ColorInt
        private var messageBackgroundColorMine: Int = VALUE_NOT_SET

        @ColorInt
        private var messageBackgroundColorTheirs: Int = VALUE_NOT_SET

        @ColorInt
        private var messageLinkTextColorMine: Int = VALUE_NOT_SET

        @ColorInt
        private var messageLinkTextColorTheirs: Int = VALUE_NOT_SET

        private var reactionsEnabled: Boolean = true

        private var linkDescriptionMaxLines: Int = 5

        fun messageBackgroundColorMine(
            @StyleableRes messageBackgroundColorMineStyleableId: Int,
            @ColorInt defaultValue: Int = VALUE_NOT_SET,
        ) = apply {
            messageBackgroundColorMine = attributes.getColor(messageBackgroundColorMineStyleableId, defaultValue)
        }

        fun messageBackgroundColorTheirs(
            @StyleableRes messageBackgroundColorTheirsId: Int,
            @ColorInt defaultValue: Int = VALUE_NOT_SET,
        ) = apply {
            messageBackgroundColorTheirs = attributes.getColor(messageBackgroundColorTheirsId, defaultValue)
        }

        fun messageLinkTextColorMine(
            @StyleableRes messageLinkTextColorMineId: Int,
            @ColorInt defaultValue: Int = VALUE_NOT_SET,
        ) = apply {
            messageLinkTextColorMine = attributes.getColor(messageLinkTextColorMineId, defaultValue)
        }

        fun messageLinkTextColorTheirs(
            @StyleableRes messageLinkTextColorTheirsId: Int,
            @ColorInt defaultValue: Int = VALUE_NOT_SET,
        ) = apply {
            messageLinkTextColorTheirs = attributes.getColor(messageLinkTextColorTheirsId, defaultValue)
        }

//        fun reactionsEnabled(
//            @StyleableRes reactionsEnabled: Int,
//            defaultValue: Boolean = true,
//        ) = apply {
//            this.reactionsEnabled = attributes.getBoolean(reactionsEnabled, defaultValue)
//        }

        fun linkDescriptionMaxLines(
            maxLines: Int,
            defaultValue: Int = 5,
        ) = apply {
            this.linkDescriptionMaxLines = attributes.getInt(maxLines, defaultValue)
        }

        fun build(): MessageListItemStyle {
            val linkBackgroundColorMine =
                attributes.getColor(
                    R.styleable.MessageListView_tinui_MessageLinkBackgroundColorMine,
                    context.getColorCompat(DEFAULT_LINK_BACKGROUND_COLOR)
                )
            val linkBackgroundColorTheirs =
                attributes.getColor(
                    R.styleable.MessageListView_tinui_MessageLinkBackgroundColorTheirs,
                    context.getColorCompat(DEFAULT_LINK_BACKGROUND_COLOR)
                )

            val mediumTypeface = ResourcesCompat.getFont(context, R.font.roboto_medium) ?: Typeface.DEFAULT
            val boldTypeface = ResourcesCompat.getFont(context, R.font.roboto_bold) ?: Typeface.DEFAULT_BOLD

            val textStyleMine = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeMine,
                    context.getDimension(DEFAULT_TEXT_SIZE)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorMine,
                    context.getColorCompat(DEFAULT_TEXT_COLOR)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsMine,
                    R.styleable.MessageListView_tinui_MessageTextFontMine,
                    mediumTypeface
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleMine, DEFAULT_TEXT_STYLE)
                .build()

            val textStyleTheirs = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeTheirs,
                    context.getDimension(DEFAULT_TEXT_SIZE)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorTheirs,
                    context.getColorCompat(DEFAULT_TEXT_COLOR)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsTheirs,
                    R.styleable.MessageListView_tinui_MessageTextFontTheirs,
                    mediumTypeface
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleTheirs, DEFAULT_TEXT_STYLE)
                .build()

            val textStyleUserName = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeUserName,
                    context.getDimension(DEFAULT_TEXT_SIZE_USER_NAME)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorUserName,
                    context.getColorCompat(DEFAULT_TEXT_COLOR_USER_NAME)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsUserName,
                    R.styleable.MessageListView_tinui_MessageTextFontUserName
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleUserName, DEFAULT_TEXT_STYLE)
                .build()

            val textStyleMessageDate = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeDate,
                    context.getDimension(DEFAULT_TEXT_SIZE_DATE)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorDate,
                    context.getColorCompat(DEFAULT_TEXT_COLOR_DATE)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsDate,
                    R.styleable.MessageListView_tinui_MessageTextFontDate
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleDate, DEFAULT_TEXT_STYLE)
                .build()

//            val textStyleThreadCounter = TextStyle.Builder(attributes)
//                .size(
//                    R.styleable.MessageListView_tinui_MessageTextSizeThreadCounter,
//                    context.getDimension(DEFAULT_TEXT_SIZE_THREAD_COUNTER)
//                )
//                .color(
//                    R.styleable.MessageListView_tinui_MessageTextColorThreadCounter,
//                    context.getColorCompat(DEFAULT_TEXT_COLOR_THREAD_COUNTER)
//                )
//                .font(
//                    R.styleable.MessageListView_tinui_MessageTextFontAssetsThreadCounter,
//                    R.styleable.MessageListView_tinui_MessageTextFontThreadCounter,
//                    mediumTypeface
//                )
//                .style(R.styleable.MessageListView_tinui_MessageTextStyleThreadCounter, DEFAULT_TEXT_STYLE)
//                .build()
//
//            val textStyleThreadSeparator = TextStyle.Builder(attributes)
//                .size(
//                    R.styleable.MessageListView_tinui_MessageTextSizeThreadSeparator,
//                    context.getDimension(DEFAULT_TEXT_SIZE_THREAD_COUNTER)
//                )
//                .color(
//                    R.styleable.MessageListView_tinui_MessageTextColorThreadSeparator,
//                    context.getColorCompat(DEFAULT_TEXT_COLOR_THREAD_COUNTER)
//                )
//                .font(
//                    R.styleable.MessageListView_tinui_MessageTextFontAssetsThreadSeparator,
//                    R.styleable.MessageListView_tinui_MessageTextFontThreadSeparator,
//                    mediumTypeface
//                )
//                .style(R.styleable.MessageListView_tinui_MessageTextStyleThreadSeparator, DEFAULT_TEXT_STYLE)
//                .build()

            val textStyleLinkTitle = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeLinkTitle,
                    context.getDimension(DEFAULT_TEXT_SIZE)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorLinkTitle,
                    context.getColorCompat(DEFAULT_TEXT_COLOR)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsLinkTitle,
                    R.styleable.MessageListView_tinui_MessageTextFontLinkTitle,
                    boldTypeface
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleLinkTitle, DEFAULT_TEXT_STYLE)
                .build()

            val textStyleLinkDescription = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeLinkDescription,
                    context.getDimension(DEFAULT_TEXT_SIZE_LINK_DESCRIPTION)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorLinkDescription,
                    context.getColorCompat(DEFAULT_TEXT_COLOR_LINK_DESCRIPTION)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsLinkDescription,
                    R.styleable.MessageListView_tinui_MessageTextFontLinkDescription,
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleLinkDescription, DEFAULT_TEXT_STYLE)
                .build()

            val textStyleLinkLabel = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeLinkLabel,
                    context.getDimension(DEFAULT_TEXT_SIZE_LINK_DESCRIPTION)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorLinkLabel,
                    context.getColorCompat(DEFAULT_TEXT_COLOR_LINK_DESCRIPTION)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsLinkLabel,
                    R.styleable.MessageListView_tinui_MessageTextFontLinkLabel,
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleLinkLabel, DEFAULT_TEXT_STYLE)
                .build()

            val dateSeparatorBackgroundColor =
                attributes.getColor(
                    R.styleable.MessageListView_tinui_DateSeparatorBackgroundColor,
                    context.getColorCompat(R.color.tinui_overlay_dark)
                )

            val textStyleDateSeparator = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeDateSeparator,
                    context.getDimension(DEFAULT_TEXT_SIZE_DATE_SEPARATOR)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorDateSeparator,
                    context.getColorCompat(DEFAULT_TEXT_COLOR_DATE_SEPARATOR)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsDateSeparator,
                    R.styleable.MessageListView_tinui_MessageTextFontDateSeparator,
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleDateSeparator, DEFAULT_TEXT_STYLE)
                .build()

//            val reactionsViewStyle = ViewReactionsViewStyle.Companion.Builder(attributes, context)
//                .bubbleBorderColorMine(R.styleable.MessageListView_tinui_MessageReactionsBubbleBorderColorMine)
//                .bubbleBorderColorTheirs(R.styleable.MessageListView_tinui_MessageReactionsBubbleBorderColorTheirs)
//                .bubbleBorderWidthMine(R.styleable.MessageListView_tinui_MessageReactionsBubbleBorderWidthMine)
//                .bubbleBorderWidthTheirs(R.styleable.MessageListView_tinui_MessageReactionsBubbleBorderWidthTheirs)
//                .bubbleColorMine(R.styleable.MessageListView_tinui_MessageReactionsBubbleColorMine)
//                .bubbleColorTheirs(R.styleable.MessageListView_tinui_MessageReactionsBubbleColorTheirs)
//                .build()
//
//            val editReactionsViewStyle = EditReactionsViewStyle.Builder(attributes, context)
//                .bubbleColorMine(R.styleable.MessageListView_tinui_EditReactionsBubbleColorMine)
//                .bubbleColorTheirs(R.styleable.MessageListView_tinui_EditReactionsBubbleColorTheirs)
//                .reactionsColumns(R.styleable.MessageListView_tinui_EditReactionsColumns)
//                .build()

            val showMessageDeliveryStatusIndicator = attributes.getBoolean(
                R.styleable.MessageListView_tinui_ShowMessageDeliveryStatusIndicator,
                true
            )

            val iconIndicatorSent = attributes.getDrawable(
                R.styleable.MessageListView_tinui_IconIndicatorSent
            ) ?: context.getDrawableCompat(R.drawable.tinui_ic_check_single)!!
            val iconIndicatorRead = attributes.getDrawable(
                R.styleable.MessageListView_tinui_IconIndicatorRead
            ) ?: context.getDrawableCompat(R.drawable.tinui_ic_check_double)!!
            val iconIndicatorPendingSync = attributes.getDrawable(
                R.styleable.MessageListView_tinui_IconIndicatorPendingSync
            ) ?: context.getDrawableCompat(R.drawable.tinui_ic_clock)!!

            val iconOnlyVisibleToYou = attributes.getDrawable(
                R.styleable.MessageListView_tinui_IconOnlyVisibleToYou
            ) ?: context.getDrawableCompat(R.drawable.tinui_ic_icon_eye_off)!!

            val messageDeletedBackground =
                attributes.getColor(
                    R.styleable.MessageListView_tinui_DeletedMessageBackgroundColor,
                    context.getColorCompat(R.color.tinui_grey_whisper)
                )

            val textStyleMessageDeleted = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_MessageTextSizeMessageDeleted,
                    context.getDimension(DEFAULT_TEXT_SIZE)
                )
                .color(
                    R.styleable.MessageListView_tinui_MessageTextColorMessageDeleted,
                    context.getColorCompat(R.color.tinui_text_color_secondary)
                )
                .font(
                    R.styleable.MessageListView_tinui_MessageTextFontAssetsMessageDeleted,
                    R.styleable.MessageListView_tinui_MessageTextFontMessageDeleted,
                )
                .style(R.styleable.MessageListView_tinui_MessageTextStyleMessageDeleted, Typeface.ITALIC)
                .build()

            val messageStrokeColorMine = attributes.getColor(
                R.styleable.MessageListView_tinui_MessageStrokeColorMine,
                context.getColorCompat(MESSAGE_STROKE_COLOR_MINE)
            )
            val messageStrokeWidthMine =
                attributes.getDimension(
                    R.styleable.MessageListView_tinui_MessageStrokeWidthMine,
                    MESSAGE_STROKE_WIDTH_MINE
                )
            val messageStrokeColorTheirs =
                attributes.getColor(
                    R.styleable.MessageListView_tinui_MessageStrokeColorTheirs,
                    context.getColorCompat(
                        MESSAGE_STROKE_COLOR_THEIRS
                    )
                )
            val messageStrokeWidthTheirs =
                attributes.getDimension(
                    R.styleable.MessageListView_tinui_MessageStrokeWidthTheirs,
                    MESSAGE_STROKE_WIDTH_THEIRS
                )

            val textStyleSystemMessage = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_SystemMessageTextSize,
                    context.getDimension(R.dimen.tinui_text_small)
                )
                .color(
                    R.styleable.MessageListView_tinui_SystemMessageTextColor,
                    context.getColorCompat(R.color.tinui_text_color_secondary)
                )
                .font(
                    R.styleable.MessageListView_tinui_SystemMessageTextFontAssets,
                    R.styleable.MessageListView_tinui_SystemMessageTextFont,
                )
                .style(R.styleable.MessageListView_tinui_SystemMessageTextStyle, Typeface.BOLD)
                .build()

            val textStyleErrorMessage = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_ErrorMessageTextSize,
                    context.getDimension(R.dimen.tinui_text_small)
                )
                .color(
                    R.styleable.MessageListView_tinui_ErrorMessageTextColor,
                    context.getColorCompat(R.color.tinui_text_color_secondary)
                )
                .font(
                    R.styleable.MessageListView_tinui_ErrorMessageTextFontAssets,
                    R.styleable.MessageListView_tinui_ErrorMessageTextFont,
                )
                .style(R.styleable.MessageListView_tinui_ErrorMessageTextStyle, Typeface.BOLD)
                .build()

            val pinnedMessageIndicatorTextStyle = TextStyle.Builder(attributes)
                .size(
                    R.styleable.MessageListView_tinui_PinnedMessageIndicatorTextSize,
                    context.getDimension(R.dimen.tinui_text_small)
                )
                .color(
                    R.styleable.MessageListView_tinui_PinnedMessageIndicatorTextColor,
                    context.getColorCompat(R.color.tinui_text_color_secondary)
                )
                .font(
                    R.styleable.MessageListView_tinui_PinnedMessageIndicatorTextFontAssets,
                    R.styleable.MessageListView_tinui_PinnedMessageIndicatorTextFont,
                )
                .style(R.styleable.MessageListView_tinui_PinnedMessageIndicatorTextStyle, Typeface.NORMAL)
                .build()

            val pinnedMessageIndicatorIcon = attributes.getDrawable(
                R.styleable.MessageListView_tinui_PinnedMessageIndicatorIcon
            ) ?: context.getDrawableCompat(R.drawable.tinui_ic_pin)!!

            val pinnedMessageBackgroundColor = attributes.getColor(
                R.styleable.MessageListView_tinui_PinnedMessageBackgroundColor,
                context.getColorCompat(R.color.tinui_highlight)
            )

            val messageStartMargin = attributes.getDimension(
                R.styleable.MessageListView_tinui_MessageStartMargin,
                context.getDimension(R.dimen.tinui_message_viewholder_avatar_missing_margin).toFloat()
            ).toInt()

            val messageEndMargin = attributes.getDimension(
                R.styleable.MessageListView_tinui_MessageEndMargin,
                context.getDimension(R.dimen.tinui_message_viewholder_avatar_missing_margin).toFloat()
            ).toInt()

            val messageMaxWidthFactorMine = attributes.getFraction(
                R.styleable.MessageListView_tinui_MessageMaxWidthFactorMine,
                BASE_MESSAGE_MAX_WIDTH_FACTOR,
                BASE_MESSAGE_MAX_WIDTH_FACTOR,
                DEFAULT_MESSAGE_MAX_WIDTH_FACTOR,
            )

            val messageMaxWidthFactorTheirs = attributes.getFraction(
                R.styleable.MessageListView_tinui_MessageMaxWidthFactorTheirs,
                BASE_MESSAGE_MAX_WIDTH_FACTOR,
                BASE_MESSAGE_MAX_WIDTH_FACTOR,
                DEFAULT_MESSAGE_MAX_WIDTH_FACTOR,
            )

            val iconFailedMessage = attributes.getDrawable(R.styleable.MessageListView_tinui_IconFailedIndicator)
                ?: ContextCompat.getDrawable(context, R.drawable.tinui_ic_warning)!!
            val iconBannedMessage = attributes.getDrawable(R.styleable.MessageListView_tinui_IconBannedIndicator)
                ?: ContextCompat.getDrawable(context, R.drawable.tinui_ic_warning)!!

            return MessageListItemStyle(
                messageBackgroundColorMine = messageBackgroundColorMine.nullIfNotSet(),
                messageBackgroundColorTheirs = messageBackgroundColorTheirs.nullIfNotSet(),
                messageLinkTextColorMine = messageLinkTextColorMine.nullIfNotSet(),
                messageLinkTextColorTheirs = messageLinkTextColorTheirs.nullIfNotSet(),
                messageLinkBackgroundColorMine = linkBackgroundColorMine,
                messageLinkBackgroundColorTheirs = linkBackgroundColorTheirs,
                linkDescriptionMaxLines = linkDescriptionMaxLines,
                textStyleMine = textStyleMine,
                textStyleTheirs = textStyleTheirs,
                textStyleUserName = textStyleUserName,
                textStyleMessageDate = textStyleMessageDate,
//                textStyleThreadCounter = textStyleThreadCounter,
//                threadSeparatorTextStyle = textStyleThreadSeparator,
                textStyleLinkTitle = textStyleLinkTitle,
                textStyleLinkDescription = textStyleLinkDescription,
                textStyleLinkLabel = textStyleLinkLabel,
                dateSeparatorBackgroundColor = dateSeparatorBackgroundColor,
                textStyleDateSeparator = textStyleDateSeparator,
//                reactionsViewStyle = reactionsViewStyle,
//                editReactionsViewStyle = editReactionsViewStyle,
                iconIndicatorSent = iconIndicatorSent,
                iconIndicatorRead = iconIndicatorRead,
                iconIndicatorPendingSync = iconIndicatorPendingSync,
                iconOnlyVisibleToYou = iconOnlyVisibleToYou,
                messageDeletedBackground = messageDeletedBackground,
                textStyleMessageDeleted = textStyleMessageDeleted,
                messageStrokeColorMine = messageStrokeColorMine,
                messageStrokeWidthMine = messageStrokeWidthMine,
                messageStrokeColorTheirs = messageStrokeColorTheirs,
                messageStrokeWidthTheirs = messageStrokeWidthTheirs,
                textStyleSystemMessage = textStyleSystemMessage,
                textStyleErrorMessage = textStyleErrorMessage,
                pinnedMessageIndicatorTextStyle = pinnedMessageIndicatorTextStyle,
                pinnedMessageIndicatorIcon = pinnedMessageIndicatorIcon,
                pinnedMessageBackgroundColor = pinnedMessageBackgroundColor,
                messageStartMargin = messageStartMargin,
                messageEndMargin = messageEndMargin,
                messageMaxWidthFactorMine = messageMaxWidthFactorMine,
                messageMaxWidthFactorTheirs = messageMaxWidthFactorTheirs,
                showMessageDeliveryStatusIndicator = showMessageDeliveryStatusIndicator,
                iconFailedMessage = iconFailedMessage,
                iconBannedMessage = iconBannedMessage,
            ).let(TransformStyle.messageListItemStyleTransformer::transform)
                .also { style -> style.checkMessageMaxWidthFactorsRange() }
        }

        private fun Int.nullIfNotSet(): Int? {
            return if (this == VALUE_NOT_SET) null else this
        }

        private fun MessageListItemStyle.checkMessageMaxWidthFactorsRange() {
            require(messageMaxWidthFactorMine in 0.75..1.0) { "messageMaxWidthFactorMine cannot be lower than 0.75 and greater than 1! Current value: $messageMaxWidthFactorMine" }
            require(messageMaxWidthFactorTheirs in 0.75..1.0) { "messageMaxWidthFactorTheirs cannot be lower than 0.75 and greater than 1! Current value: $messageMaxWidthFactorTheirs" }
        }
    }
}
