package com.example.testapp.components.search

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import com.example.testapp.R
import com.example.testapp.components.TransformStyle
import com.example.testapp.components.common.extensions.internal.use
import com.example.testapp.components.common.extensions.internal.getColorCompat
import com.example.testapp.components.common.extensions.internal.getDimension
import com.example.testapp.components.common.extensions.internal.getDrawableCompat

/**
 * @property textColor Color value of the search input text.
 * @property hintColor Color value of the search input hint.
 * @property searchIconDrawable Drawable of search icon visible on the right side of the SearchInputView.
 * @property clearInputDrawable Drawable of clear input icon visible on the left side of the SearchInputView.
 * @property backgroundDrawable Drawable used as the view's background.
 * @property containerBackgroundColor Color of the container background.
 * @property hintText Hint text.
 * @property textSize The size of the text in the input.
 * @property searchInputHeight The height of the root container.
 */
public data class SearchInputViewStyle(
    @ColorInt val textColor: Int,
    @ColorInt val hintColor: Int,
    val searchIconDrawable: Drawable,
    val clearInputDrawable: Drawable,
    val backgroundDrawable: Drawable,
    @ColorInt val containerBackgroundColor: Int,
    val hintText: String,
    val textSize: Int,
    val searchInputHeight: Int,
) {
    internal companion object {
        operator fun invoke(context: Context, attrs: AttributeSet?): SearchInputViewStyle {
            context.obtainStyledAttributes(
                attrs,
                R.styleable.SearchInputView,
                R.attr.tinui_SearchInputViewStyle,
                R.style.tinui_SearchInputView,
            ).use { a ->
                val searchIcon = a.getDrawable(R.styleable.SearchInputView_tinui_SearchInputViewSearchIcon)
                    ?: context.getDrawableCompat(R.drawable.tinui_ic_search)!!

                val clearIcon = a.getDrawable(R.styleable.SearchInputView_tinui_SearchInputViewClearInputIcon)
                    ?: context.getDrawableCompat(R.drawable.tinui_ic_clear)!!

                val backgroundDrawable = a.getDrawable(R.styleable.SearchInputView_tinui_SearchInputViewBackground)
                    ?: context.getDrawableCompat(R.drawable.tinui_shape_search_view_background)!!

                val containerBackground = a.getColor(
                    R.styleable.SearchInputView_tinui_SearchInputViewContainerBackground,
                    context.getColorCompat(R.color.tinui_white)
                )

                val textColor = a.getColor(
                    R.styleable.SearchInputView_tinui_SearchInputViewTextColor,
                    context.getColorCompat(R.color.tinui_text_color_primary)
                )

                val hintColor = a.getColor(
                    R.styleable.SearchInputView_tinui_SearchInputViewHintColor,
                    context.getColorCompat(R.color.tinui_text_color_primary)
                )

                val hintText = a.getText(R.styleable.SearchInputView_tinui_SearchInputViewHintText)?.toString()
                    ?: context.getString(R.string.tinui_search_input_hint)

                val textSize = a.getDimensionPixelSize(
                    R.styleable.SearchInputView_tinui_SearchInputViewTextSize,
                    context.getDimension(R.dimen.tinui_text_medium)
                )

                val searchInputHeight = a.getDimensionPixelSize(
                    R.styleable.SearchInputView_tinui_SearchInputViewHeight,
                    context.getDimension(R.dimen.tinui_search_input_height)
                )

                return SearchInputViewStyle(
                    searchIconDrawable = searchIcon,
                    clearInputDrawable = clearIcon,
                    backgroundDrawable = backgroundDrawable,
                    containerBackgroundColor = containerBackground,
                    textColor = textColor,
                    hintColor = hintColor,
                    hintText = hintText,
                    textSize = textSize,
                    searchInputHeight = searchInputHeight
                ).let(TransformStyle.searchInputViewStyleTransformer::transform)
            }
        }
    }
}
