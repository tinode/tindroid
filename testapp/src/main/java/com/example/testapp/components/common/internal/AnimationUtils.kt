package com.example.testapp.components.common.internal

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import com.example.testapp.R
import com.example.testapp.components.common.extensions.internal.getColorCompat

private const val HIGHLIGHT_ANIM_DURATION = 3000L

internal fun View.animateHighlight(
    highlightColor: Int = context.getColorCompat(R.color.tinui_highlight),
): ValueAnimator {
    val endColor = ColorUtils.setAlphaComponent(highlightColor, 0)
    return ValueAnimator
        .ofObject(ArgbEvaluator(), highlightColor, highlightColor, highlightColor, endColor)
        .apply {
            duration = HIGHLIGHT_ANIM_DURATION
            addUpdateListener {
                setBackgroundColor(it.animatedValue as Int)
            }
            doOnEnd {
                setBackgroundColor(endColor)
            }
            start()
        }
}
