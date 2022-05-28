package com.example.testapp.components.search.list

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import com.example.testapp.R
import com.example.testapp.common.style.TextStyle
import com.example.testapp.components.TransformStyle
import com.example.testapp.components.common.extensions.internal.use
import com.example.testapp.components.common.extensions.internal.getColorCompat
import com.example.testapp.components.common.extensions.internal.getDimension
import com.example.testapp.components.common.extensions.internal.getDrawableCompat
import com.example.testapp.components.message.preview.MessagePreviewStyle

/**
 * Style for [SearchResultListView].
 * Use this class together with [TransformStyle.searchResultListViewStyleTransformer] to change [SearchResultListView] styles programmatically.
 *
 * @property backgroundColor Background color for search results list. Default value is [R.color.tinui_white].
 * @property searchInfoBarBackground Background for search info bar. Default value is [R.drawable.tinui_bg_gradient].
 * @property searchInfoBarTextStyle Appearance for text displayed in search info bar.
 * @property emptyStateIcon Icon for empty state view. Default value is [R.drawable.tinui_ic_search_empty].
 * @property emptyStateTextStyle Appearance for empty state text.
 * @property progressBarIcon Animated progress drawable. Default value is [R.drawable.tinui_rotating_indeterminate_progress_gradient].
 * @property messagePreviewStyle Style for single search result item.
 */
public data class SearchResultListViewStyle(
    @ColorInt public val backgroundColor: Int,
    public val searchInfoBarBackground: Drawable,
    public val searchInfoBarTextStyle: TextStyle,
    public val emptyStateIcon: Drawable,
    public val emptyStateTextStyle: TextStyle,
    public val progressBarIcon: Drawable,
    public val messagePreviewStyle: MessagePreviewStyle,
) {
    internal companion object {
        operator fun invoke(context: Context, attrs: AttributeSet?): SearchResultListViewStyle {
            context.obtainStyledAttributes(
                attrs,
                R.styleable.SearchResultListView,
                R.attr.tinui_SearchResultListViewStyle,
                R.style.tinui_SearchResultListView,
            ).use { a ->
                val backgroundColor =
                    a.getColor(
                        R.styleable.SearchResultListView_tinui_SearchResultListBackground,
                        context.getColorCompat(R.color.tinui_white)
                    )

                val searchInfoBarBackground =
                    a.getDrawable(R.styleable.SearchResultListView_tinui_SearchResultListSearchInfoBarBackground)
                        ?: context.getDrawableCompat(R.drawable.tinui_bg_gradient)!!
                val searchInfoBarTextStyle = TextStyle.Builder(a)
                    .size(
                        R.styleable.SearchResultListView_tinui_SearchResultListSearchInfoBarTextSize,
                        context.getDimension(R.dimen.tinui_text_small)
                    )
                    .color(
                        R.styleable.SearchResultListView_tinui_SearchResultListSearchInfoBarTextColor,
                        context.getColorCompat(R.color.tinui_text_color_primary)
                    )
                    .font(
                        R.styleable.SearchResultListView_tinui_SearchResultListSearchInfoBarTextFontAssets,
                        R.styleable.SearchResultListView_tinui_SearchResultListSearchInfoBarTextFont
                    )
                    .style(
                        R.styleable.SearchResultListView_tinui_SearchResultListSearchInfoBarTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val emptyStateIcon =
                    a.getDrawable(R.styleable.SearchResultListView_tinui_SearchResultListEmptyStateIcon)
                        ?: context.getDrawableCompat(R.drawable.tinui_ic_search_empty)!!
                val emptyStateTextStyle = TextStyle.Builder(a)
                    .size(
                        R.styleable.SearchResultListView_tinui_SearchResultListEmptyStateTextSize,
                        context.getDimension(R.dimen.tinui_text_medium)
                    )
                    .color(
                        R.styleable.SearchResultListView_tinui_SearchResultListEmptyStateTextColor,
                        context.getColorCompat(R.color.tinui_text_color_secondary)
                    )
                    .font(
                        R.styleable.SearchResultListView_tinui_SearchResultListEmptyStateTextFontAssets,
                        R.styleable.SearchResultListView_tinui_SearchResultListEmptyStateTextFont
                    )
                    .style(
                        R.styleable.SearchResultListView_tinui_SearchResultListEmptyStateTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val progressBarIcon =
                    a.getDrawable(R.styleable.SearchResultListView_tinui_SearchResultListProgressBarIcon)
                        ?: context.getDrawableCompat(R.drawable.tinui_rotating_indeterminate_progress_gradient)!!

                val senderTextStyle = TextStyle.Builder(a)
                    .size(
                        R.styleable.SearchResultListView_tinui_SearchResultListSenderNameTextSize,
                        context.getDimension(R.dimen.tinui_channel_item_title)
                    )
                    .color(
                        R.styleable.SearchResultListView_tinui_SearchResultListSenderNameTextColor,
                        context.getColorCompat(R.color.tinui_text_color_primary)
                    )
                    .font(
                        R.styleable.SearchResultListView_tinui_SearchResultListSenderNameTextFontAssets,
                        R.styleable.SearchResultListView_tinui_SearchResultListSenderNameTextFont
                    )
                    .style(
                        R.styleable.SearchResultListView_tinui_SearchResultListSenderNameTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val messageTextStyle = TextStyle.Builder(a)
                    .size(
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTextSize,
                        context.getDimension(R.dimen.tinui_channel_item_message)
                    )
                    .color(
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTextColor,
                        context.getColorCompat(R.color.tinui_text_color_secondary)
                    )
                    .font(
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTextFontAssets,
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTextFont
                    )
                    .style(
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                val messageTimeTextStyle = TextStyle.Builder(a)
                    .size(
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTimeTextSize,
                        context.getDimension(R.dimen.tinui_channel_item_message)
                    )
                    .color(
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTimeTextColor,
                        context.getColorCompat(R.color.tinui_text_color_secondary)
                    )
                    .font(
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTimeTextFontAssets,
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTimeTextFont
                    )
                    .style(
                        R.styleable.SearchResultListView_tinui_SearchResultListMessageTimeTextStyle,
                        Typeface.NORMAL
                    )
                    .build()

                return SearchResultListViewStyle(
                    backgroundColor = backgroundColor,
                    searchInfoBarBackground = searchInfoBarBackground,
                    searchInfoBarTextStyle = searchInfoBarTextStyle,
                    emptyStateIcon = emptyStateIcon,
                    emptyStateTextStyle = emptyStateTextStyle,
                    progressBarIcon = progressBarIcon,
                    messagePreviewStyle = MessagePreviewStyle(
                        messageSenderTextStyle = senderTextStyle,
                        messageTextStyle = messageTextStyle,
                        messageTimeTextStyle = messageTimeTextStyle,
                    ),
                ).let(TransformStyle.searchResultListViewStyleTransformer::transform)
            }
        }
    }
}
