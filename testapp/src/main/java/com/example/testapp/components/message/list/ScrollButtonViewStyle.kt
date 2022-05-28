package com.example.testapp.components.message.list

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.StyleableRes
import com.example.testapp.R
import com.example.testapp.common.style.TextStyle
import com.example.testapp.components.TransformStyle
import com.example.testapp.components.common.extensions.internal.getColorCompat
import com.example.testapp.components.common.extensions.internal.getDimension

/**
 * Style for [ScrollButtonView].
 *
 * @property scrollButtonEnabled Enables/disables view which allows to scroll to the latest messages. Default value is true.
 * @property scrollButtonUnreadEnabled Enables/disables unread label. Default value is enabled.
 * @property scrollButtonColor [ScrollButtonView] background color. Default value is [R.color.tinui_white].
 * @property scrollButtonRippleColor [ScrollButtonView] ripple color. Default value is [R.color.tinui_white_smoke].
 * @property scrollButtonBadgeColor Unread label background color. Default value is [R.color.tinui_accent_blue].
 * @property scrollButtonIcon [ScrollButtonView] icon. Default value is [R.drawable.tinui_ic_down].
 * @property scrollButtonBadgeTextStyle Appearance for unread label.
 */
public data class ScrollButtonViewStyle(
    public val scrollButtonEnabled: Boolean,
    public val scrollButtonUnreadEnabled: Boolean,
    @ColorInt public val scrollButtonColor: Int,
    @ColorInt public val scrollButtonRippleColor: Int,
    @ColorInt public val scrollButtonBadgeColor: Int,
    public val scrollButtonElevation: Float,
    public val scrollButtonIcon: Drawable?,
    public val scrollButtonBadgeTextStyle: TextStyle,
) {

    internal class Builder(private val context: Context, private val attrs: TypedArray) {
        private var scrollButtonEnabled: Boolean = false
        private var scrollButtonUnreadEnabled: Boolean = false
        @ColorInt private var scrollButtonColor: Int = 0
        @ColorInt private var scrollButtonRippleColor: Int = 0
        @ColorInt private var scrollButtonBadgeColor: Int = 0
        private var scrollButtonIcon: Drawable? = null
        private var scrollButtonElevation: Float = 0F

        fun scrollButtonEnabled(
            @StyleableRes scrollButtonEnabledStyleableId: Int,
            defaultValue: Boolean,
        ) = apply {
            scrollButtonEnabled = attrs.getBoolean(scrollButtonEnabledStyleableId, defaultValue)
        }

        fun scrollButtonUnreadEnabled(
            @StyleableRes scrollButtonUnreadEnabledStyleableId: Int,
            defaultValue: Boolean,
        ) = apply {
            scrollButtonUnreadEnabled = attrs.getBoolean(scrollButtonUnreadEnabledStyleableId, defaultValue)
        }

        fun scrollButtonColor(
            @StyleableRes scrollButtonColorStyleableId: Int,
            @ColorInt defaultValue: Int,
        ) = apply {
            scrollButtonColor = attrs.getColor(scrollButtonColorStyleableId, defaultValue)
        }

        fun scrollButtonRippleColor(
            @StyleableRes scrollButtonRippleColorStyleableId: Int,
            @ColorInt defaultColor: Int,
        ) = apply {
            scrollButtonRippleColor = attrs.getColor(scrollButtonRippleColorStyleableId, defaultColor)
        }

        fun scrollButtonBadgeColor(
            @StyleableRes scrollButtonBadgeColorStyleableId: Int,
            @ColorInt defaultColor: Int,
        ) = apply {
            scrollButtonBadgeColor = attrs.getColor(scrollButtonBadgeColorStyleableId, defaultColor)
        }

        fun scrollButtonIcon(
            @StyleableRes scrollButtonIconStyleableId: Int,
            defaultIcon: Drawable?,
        ) = apply {
            scrollButtonIcon = attrs.getDrawable(scrollButtonIconStyleableId) ?: defaultIcon
        }

        fun scrollButtonElevation(
            @StyleableRes scrollButtonElevation: Int,
            defaultElevation: Float,
        ) = apply {
            this.scrollButtonElevation = attrs.getDimension(scrollButtonElevation, defaultElevation)
        }

        fun build(): ScrollButtonViewStyle {
            val scrollButtonBadgeTextStyle = TextStyle.Builder(attrs)
                .size(
                    R.styleable.MessageListView_tinui_ScrollButtonBadgeTextSize,
                    context.getDimension(R.dimen.tinui_scroll_button_unread_badge_text_size)
                )
                .color(
                    R.styleable.MessageListView_tinui_ScrollButtonBadgeTextColor,
                    context.getColorCompat(R.color.tinui_literal_white)
                )
                .font(
                    R.styleable.MessageListView_tinui_ScrollButtonBadgeFontAssets,
                    R.styleable.MessageListView_tinui_ScrollButtonBadgeTextFont,
                )
                .style(R.styleable.MessageListView_tinui_ScrollButtonBadgeTextStyle, Typeface.BOLD)
                .build()

            return ScrollButtonViewStyle(
                scrollButtonEnabled = scrollButtonEnabled,
                scrollButtonUnreadEnabled = scrollButtonUnreadEnabled,
                scrollButtonColor = scrollButtonColor,
                scrollButtonRippleColor = scrollButtonRippleColor,
                scrollButtonBadgeColor = scrollButtonBadgeColor,
                scrollButtonIcon = scrollButtonIcon,
                scrollButtonBadgeTextStyle = scrollButtonBadgeTextStyle,
                scrollButtonElevation = scrollButtonElevation
            ).let(TransformStyle.scrollButtonStyleTransformer::transform)
        }
    }
}
