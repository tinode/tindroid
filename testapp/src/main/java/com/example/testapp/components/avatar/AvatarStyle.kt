package com.example.testapp.components.avatar

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.Px
import com.example.testapp.R
import com.example.testapp.common.style.TextStyle
import com.example.testapp.components.TransformStyle
import com.example.testapp.components.common.extensions.internal.use
import com.example.testapp.components.common.extensions.internal.dpToPx
import com.example.testapp.components.common.extensions.internal.getColorCompat
import com.example.testapp.components.common.extensions.internal.getDimension
import com.example.testapp.components.common.extensions.internal.getEnum

public data class AvatarStyle(
    @Px public val avatarBorderWidth: Int,
    @ColorInt public val avatarBorderColor: Int,
    public val avatarInitialText: TextStyle,
    public val onlineIndicatorEnabled: Boolean,
    public val onlineIndicatorPosition: AvatarView.OnlineIndicatorPosition,
    @ColorInt public val onlineIndicatorColor: Int,
    @ColorInt public val onlineIndicatorBorderColor: Int,
    public val avatarShape: AvatarView.AvatarShape,
    @Px public val borderRadius: Float
) {

    internal companion object {
        operator fun invoke(context: Context, attrs: AttributeSet?): AvatarStyle {
            context.obtainStyledAttributes(
                attrs,
                R.styleable.AvatarView,
                0,
                0,
            ).use {
                val avatarBorderWidth = it.getDimensionPixelSize(
                    R.styleable.AvatarView_tinui_AvatarBorderWidth,
                    context.getDimension(R.dimen.tinui_avatar_border_width)
                )
                val avatarBorderColor = it.getColor(
                    R.styleable.AvatarView_tinui_AvatarBorderColor,
                    context.getColorCompat(R.color.tinui_black)
                )
                val avatarInitialText = TextStyle.Builder(it)
                    .size(
                        R.styleable.AvatarView_tinui_AvatarTextSize,
                        context.getDimension(R.dimen.tinui_avatar_initials)
                    )
                    .color(
                        R.styleable.AvatarView_tinui_AvatarTextColor,
                        context.getColorCompat(R.color.tinui_white)
                    )
                    .font(
                        R.styleable.AvatarView_tinui_AvatarTextFontAssets,
                        R.styleable.AvatarView_tinui_AvatarTextFont
                    )
                    .style(
                        R.styleable.AvatarView_tinui_AvatarTextStyle,
                        Typeface.BOLD
                    )
                    .build()
                val onlineIndicatorEnabled = it.getBoolean(
                    R.styleable.AvatarView_tinui_AvatarOnlineIndicatorEnabled,
                    false
                )
                val onlineIndicatorPosition = it.getEnum(
                    R.styleable.AvatarView_tinui_AvatarOnlineIndicatorPosition,
                    AvatarView.OnlineIndicatorPosition.TOP_END
                )
                val onlineIndicatorColor =
                    it.getColor(R.styleable.AvatarView_tinui_AvatarOnlineIndicatorColor, Color.GREEN)
                val onlineIndicatorBorderColor =
                    it.getColor(
                        R.styleable.AvatarView_tinui_AvatarOnlineIndicatorBorderColor,
                        context.getColorCompat(R.color.tinui_white)
                    )

                val avatarShape =
                    it.getEnum(R.styleable.AvatarView_tinui_AvatarShape,
                        AvatarView.AvatarShape.CIRCLE
                    )

                val borderRadius =
                    it.getDimensionPixelSize(
                        R.styleable.AvatarView_tinui_AvatarBorderRadius,
                        4.dpToPx()
                    ).toFloat()

                return AvatarStyle(
                    avatarBorderWidth = avatarBorderWidth,
                    avatarBorderColor = avatarBorderColor,
                    avatarInitialText = avatarInitialText,
                    onlineIndicatorEnabled = onlineIndicatorEnabled,
                    onlineIndicatorPosition = onlineIndicatorPosition,
                    onlineIndicatorColor = onlineIndicatorColor,
                    onlineIndicatorBorderColor = onlineIndicatorBorderColor,
                    avatarShape = avatarShape,
                    borderRadius = borderRadius,
                ).let(TransformStyle.avatarStyleTransformer::transform)
            }
        }
    }
}
