package com.example.testapp.components.message.list.internal

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.example.testapp.components.common.extensions.internal.createStreamThemeWrapper
import com.example.testapp.components.common.extensions.internal.streamThemeInflater
import com.example.testapp.components.message.list.ScrollButtonViewStyle
import com.example.testapp.databinding.TinuiScrollButtonViewBinding

internal class ScrollButtonView : FrameLayout {

    private lateinit var scrollButtonViewStyle: ScrollButtonViewStyle
    private var unreadCount: Int = 0
    private val binding: TinuiScrollButtonViewBinding =
        TinuiScrollButtonViewBinding.inflate(streamThemeInflater, this)

    constructor(context: Context) : super(context.createStreamThemeWrapper())

    constructor(context: Context, attrs: AttributeSet?) : super(context.createStreamThemeWrapper(), attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context.createStreamThemeWrapper(),
        attrs,
        defStyleAttr
    )

    fun setScrollButtonViewStyle(scrollButtonViewStyle: ScrollButtonViewStyle) {
        this.scrollButtonViewStyle = scrollButtonViewStyle
        binding.scrollActionButton.rippleColor = scrollButtonViewStyle.scrollButtonRippleColor
        binding.scrollActionButton.setImageDrawable(scrollButtonViewStyle.scrollButtonIcon)
        binding.scrollActionButton.backgroundTintList = ColorStateList.valueOf(scrollButtonViewStyle.scrollButtonColor)
        binding.scrollActionButton.compatElevation = scrollButtonViewStyle.scrollButtonElevation
        scrollButtonViewStyle.scrollButtonBadgeTextStyle.apply(binding.unreadCountTextView)
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        binding.scrollActionButton.setOnClickListener(listener)
    }

    fun setUnreadCount(unreadCount: Int) {
        setUnreadCountValue(unreadCount)
        setUnreadCountTextViewVisible(unreadCount > 0)
        if (scrollButtonViewStyle.scrollButtonEnabled && scrollButtonViewStyle.scrollButtonUnreadEnabled) {
            setUnreadCountValue(unreadCount)
            setUnreadCountTextViewVisible(unreadCount > 0)
        } else {
            setUnreadCountTextViewVisible(false)
        }
    }

    private fun setUnreadCountValue(unreadCount: Int) {
        if (this.unreadCount != unreadCount) {
            this.unreadCount = unreadCount
            binding.unreadCountTextView.text = formatUnreadCount(unreadCount)
        }
    }

    private fun setUnreadCountTextViewVisible(isVisible: Boolean) {
        binding.unreadCountTextView.isVisible = isVisible
    }

    private fun formatUnreadCount(unreadCount: Int): CharSequence {
        return if (unreadCount > MAX_UNREAD_VALUE) "$MAX_UNREAD_VALUE+" else unreadCount.toString()
    }

    private companion object {
        private const val MAX_UNREAD_VALUE = 999
    }
}
