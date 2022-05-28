package com.example.testapp.components.channel.list

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import com.example.testapp.R
import com.example.testapp.common.style.TextStyle
import com.example.testapp.components.TransformStyle
import com.example.testapp.components.common.extensions.internal.use
import com.example.testapp.components.common.extensions.internal.getColorCompat
import com.example.testapp.components.common.extensions.internal.getColorOrNull
import com.example.testapp.components.common.extensions.internal.getDimension
import com.example.testapp.components.common.extensions.internal.getDrawableCompat

/**
 * Style for [ChannelListView].
 * Use this class together with [TransformStyle.chatListStyleTransformer] to change [ChannelListView] styles programmatically.
 *
 * @property optionsIcon Icon for chat's options. Default value is [R.drawable.tinui_ic_more].
 * @property deleteIcon Icon for deleting chat option. Default value is [R.drawable.tinui_ic_delete].
 * @property optionsEnabled Enables/disables chat's options. Enabled by default
 * @property deleteEnabled Enables/disables delete chat option. Enabled by default
 * @property swipeEnabled Enables/disables swipe on chat list item. Enabled by default
 * @property backgroundLayoutColor Background color for [ChannelViewHolder]. Default value is [R.color.tinui_white_smoke].
 * @property channelTitleText Appearance for chat's title, displayed in [ChannelViewHolder]
 * @property lastMessageText Appearance for last message text, displayed in [ChannelViewHolder]
 * @property lastMessageDateText Appearance for last message date text displayed in [ChannelViewHolder]
 * @property indicatorSentIcon Icon for indicating message sent status in [ChannelViewHolder]. Default value is [R.drawable.tinui_ic_check_single].
 * @property indicatorReadIcon Icon for indicating message read status in [ChannelViewHolder]. Default value is [R.drawable.tinui_ic_check_double].
 * @property indicatorPendingSyncIcon Icon for indicating sync pending status in [ChannelViewHolder]. Default value is [R.drawable.tinui_ic_clock].
 * @property foregroundLayoutColor Foreground color for [ChannelViewHolder]. Default value is [R.color.tinui_white_snow].
 * @property unreadMessageCounterText Appearance for message counter text, displayed in [ChannelViewHolder]
 * @property unreadMessageCounterBackgroundColor Background color for message counter, displayed in [ChannelViewHolder]. Default value is [R.color.tinui_accent_red].
 * @property mutedChannelIcon Icon for muted chat, displayed in [ChannelViewHolder]. Default value is [R.drawable.tinui_ic_mute_black].
 * @property itemSeparator Items' separator. Default value is [R.drawable.tinui_divider].
 * @property loadingView Loading view. Default value is [R.layout.tinui_default_loading_view].
 * @property emptyStateView Empty state view. Default value is [R.layout.tinui_channel_list_empty_state_view].
 * @property loadingMoreView Loading more view. Default value is [R.layout.tinui_channel_list_loading_more_view].
 * @property edgeEffectColor Color applied to the [ChannelListView] edge effect. Pass null if you want to use default [android.R.attr.colorEdgeEffect]. Default value is null.
 * @property showChannelDeliveryStatusIndicator Flag if we need to show the delivery indicator or not.
 */
public data class ChannelListViewStyle(
    public val optionsIcon: Drawable,
    public val deleteIcon: Drawable,
    public val optionsEnabled: Boolean,
    public val deleteEnabled: Boolean,
    public val swipeEnabled: Boolean,
    @ColorInt public val backgroundColor: Int,
    @ColorInt public val backgroundLayoutColor: Int,
    public val channelTitleText: TextStyle,
    public val lastMessageText: TextStyle,
    public val lastMessageDateText: TextStyle,
    public val indicatorSentIcon: Drawable,
    public val indicatorReadIcon: Drawable,
    public val indicatorPendingSyncIcon: Drawable,
    @ColorInt public val foregroundLayoutColor: Int,
    public val unreadMessageCounterText: TextStyle,
    @ColorInt public val unreadMessageCounterBackgroundColor: Int,
    public val mutedChannelIcon: Drawable,
    public val verifiedChannelIcon: Drawable,
    public val staffChannelIcon: Drawable,
    public val dangerChannelIcon: Drawable,
    public val itemSeparator: Drawable,
    @LayoutRes public val loadingView: Int,
    @LayoutRes public val emptyStateView: Int,
    @LayoutRes public val loadingMoreView: Int,
    @ColorInt public val edgeEffectColor: Int?,
    public val showChannelDeliveryStatusIndicator: Boolean
) {

    internal companion object {
        operator fun invoke(context: Context, attrs: AttributeSet?): ChannelListViewStyle {
            context.obtainStyledAttributes(
                attrs,
                R.styleable.ChannelListView,
                R.attr.tinui_ChannelListViewStyle,
                R.style.tinui_ChannelListView,
            ).use { a ->
                val optionsIcon = a.getDrawable(R.styleable.ChannelListView_tinui_ChannelOptionsIcon)
                    ?: context.getDrawableCompat(R.drawable.tinui_ic_more)!!

                val deleteIcon = a.getDrawable(R.styleable.ChannelListView_tinui_ChannelDeleteIcon)
                    ?: context.getDrawableCompat(R.drawable.tinui_ic_delete)!!

                val moreOptionsEnabled = a.getBoolean(
                    R.styleable.ChannelListView_tinui_ChannelOptionsEnabled,
                    false
                )

                val deleteEnabled = a.getBoolean(
                    R.styleable.ChannelListView_tinui_ChannelDeleteEnabled,
                    true
                )

                val swipeEnabled = a.getBoolean(
                    R.styleable.ChannelListView_tinui_SwipeEnabled,
                    true
                )

                val backgroundColor = a.getColor(
                    R.styleable.ChannelListView_tinui_ChannelListBackgroundColor,
                    context.getColorCompat(R.color.tinui_white)
                )

                val backgroundLayoutColor = a.getColor(
                    R.styleable.ChannelListView_tinui_BackgroundLayoutColor,
                    context.getColorCompat(R.color.tinui_white_smoke)
                )

                val channelTitleText = TextStyle.Builder(a)
                    .size(
                        R.styleable.ChannelListView_tinui_ChannelTitleTextSize,
                        context.getDimension(R.dimen.tinui_channel_item_title)
                    )
                    .color(
                        R.styleable.ChannelListView_tinui_ChannelTitleTextColor,
                        context.getColorCompat(R.color.tinui_text_color_primary)
                    )
                    .font(
                        R.styleable.ChannelListView_tinui_ChannelTitleFontAssets,
                        R.styleable.ChannelListView_tinui_ChannelTitleTextFont
                    )
                    .style(
                        R.styleable.ChannelListView_tinui_ChannelTitleTextStyle,
                        Typeface.BOLD
                    )
                    .build()

                val lastMessageText = TextStyle.Builder(a)
                    .size(
                        R.styleable.ChannelListView_tinui_LastMessageTextSize,
                        context.getDimension(R.dimen.tinui_channel_item_message)
                    )
                    .color(
                        R.styleable.ChannelListView_tinui_LastMessageTextColor,
                        context.getColorCompat(R.color.tinui_text_color_secondary)
                    )
                    .font(
                        R.styleable.ChannelListView_tinui_LastMessageFontAssets,
                        R.styleable.ChannelListView_tinui_LastMessageTextFont
                    )
                    .style(
                        R.styleable.ChannelListView_tinui_LastMessageTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val lastMessageDateText = TextStyle.Builder(a)
                    .size(
                        R.styleable.ChannelListView_tinui_LastMessageDateTextSize,
                        context.getDimension(R.dimen.tinui_channel_item_message_date)
                    )
                    .color(
                        R.styleable.ChannelListView_tinui_LastMessageDateTextColor,
                        context.getColorCompat(R.color.tinui_text_color_secondary)
                    )
                    .font(
                        R.styleable.ChannelListView_tinui_LastMessageDateFontAssets,
                        R.styleable.ChannelListView_tinui_LastMessageDateTextFont
                    )
                    .style(
                        R.styleable.ChannelListView_tinui_LastMessageDateTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val showChannelDeliveryStatusIndicator = a.getBoolean(
                    R.styleable.ChannelListView_tinui_ShowChannelDeliveryStatusIndicator,
                    true
                )

                val indicatorSentIcon = a.getDrawable(R.styleable.ChannelListView_tinui_IndicatorSentIcon)
                    ?: context.getDrawableCompat(R.drawable.tinui_ic_check_single)!!

                val indicatorReadIcon = a.getDrawable(R.styleable.ChannelListView_tinui_IndicatorReadIcon)
                    ?: context.getDrawableCompat(R.drawable.tinui_ic_check_double)!!

                val indicatorPendingSyncIcon =
                    a.getDrawable(R.styleable.ChannelListView_tinui_IndicatorPendingSyncIcon)
                        ?: context.getDrawableCompat(R.drawable.tinui_ic_clock)!!

                val foregroundLayoutColor = a.getColor(
                    R.styleable.ChannelListView_tinui_ForegroundLayoutColor,
                    context.getColorCompat(R.color.tinui_white_snow)
                )

                val unreadMessageCounterText = TextStyle.Builder(a)
                    .size(
                        R.styleable.ChannelListView_tinui_UnreadMessageCounterTextSize,
                        context.getDimension(R.dimen.tinui_text_small)
                    )
                    .color(
                        R.styleable.ChannelListView_tinui_UnreadMessageCounterTextColor,
                        context.getColorCompat(R.color.tinui_literal_white)
                    )
                    .font(
                        R.styleable.ChannelListView_tinui_UnreadMessageCounterFontAssets,
                        R.styleable.ChannelListView_tinui_UnreadMessageCounterTextFont
                    )
                    .style(
                        R.styleable.ChannelListView_tinui_UnreadMessageCounterTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val unreadMessageCounterBackgroundColor = a.getColor(
                    R.styleable.ChannelListView_tinui_UnreadMessageCounterBackgroundColor,
                    context.getColorCompat(R.color.tinui_accent_red)
                )

                val mutedChannelIcon = a.getDrawable(
                    R.styleable.ChannelListView_tinui_MutedChannelIcon
                ) ?: context.getDrawableCompat(R.drawable.tinui_ic_mute_black)!!

                val verifiedChannelIcon = a.getDrawable(
                    R.styleable.ChannelListView_tinui_VerifiedChannelIcon
                ) ?: context.getDrawableCompat(R.drawable.tinui_ic_verified)!!

                val staffChannelIcon = a.getDrawable(
                    R.styleable.ChannelListView_tinui_StaffChannelIcon
                ) ?: context.getDrawableCompat(R.drawable.tinui_ic_verified_user)!!

                val dangerChannelIcon = a.getDrawable(
                    R.styleable.ChannelListView_tinui_DangerChannelIcon
                ) ?: context.getDrawableCompat(R.drawable.tinui_ic_danger)!!

                val itemSeparator = a.getDrawable(
                    R.styleable.ChannelListView_tinui_ChannelsItemSeparatorDrawable
                ) ?: context.getDrawableCompat(R.drawable.tinui_divider)!!

                val loadingView = a.getResourceId(
                    R.styleable.ChannelListView_tinui_LoadingView,
                    R.layout.tinui_default_loading_view,
                )

                val emptyStateView = a.getResourceId(
                    R.styleable.ChannelListView_tinui_EmptyStateView,
                    R.layout.tinui_channel_list_empty_state_view,
                )

                val loadingMoreView = a.getResourceId(
                    R.styleable.ChannelListView_tinui_LoadingMoreView,
                    R.layout.tinui_channel_list_loading_more_view,
                )

                val edgeEffectColor = a.getColorOrNull(R.styleable.ChannelListView_tinui_EdgeEffectColor)

                return ChannelListViewStyle(
                    optionsIcon = optionsIcon,
                    deleteIcon = deleteIcon,
                    optionsEnabled = moreOptionsEnabled,
                    deleteEnabled = deleteEnabled,
                    swipeEnabled = swipeEnabled,
                    backgroundColor = backgroundColor,
                    backgroundLayoutColor = backgroundLayoutColor,
                    channelTitleText = channelTitleText,
                    lastMessageText = lastMessageText,
                    lastMessageDateText = lastMessageDateText,
                    indicatorSentIcon = indicatorSentIcon,
                    indicatorReadIcon = indicatorReadIcon,
                    indicatorPendingSyncIcon = indicatorPendingSyncIcon,
                    foregroundLayoutColor = foregroundLayoutColor,
                    unreadMessageCounterText = unreadMessageCounterText,
                    unreadMessageCounterBackgroundColor = unreadMessageCounterBackgroundColor,
                    mutedChannelIcon = mutedChannelIcon,
                    verifiedChannelIcon = verifiedChannelIcon,
                    staffChannelIcon = staffChannelIcon,
                    dangerChannelIcon = dangerChannelIcon,
                    itemSeparator = itemSeparator,
                    loadingView = loadingView,
                    emptyStateView = emptyStateView,
                    loadingMoreView = loadingMoreView,
                    edgeEffectColor = edgeEffectColor,
                    showChannelDeliveryStatusIndicator = showChannelDeliveryStatusIndicator
                ).let(TransformStyle.chatListStyleTransformer::transform)
            }
        }
    }
}
