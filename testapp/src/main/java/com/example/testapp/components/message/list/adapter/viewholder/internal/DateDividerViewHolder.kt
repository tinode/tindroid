package com.example.testapp.components.message.list.adapter.viewholder.internal

import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat.setTint
import com.example.testapp.components.common.extensions.internal.dpToPxPrecise
import com.example.testapp.components.common.extensions.internal.streamThemeInflater
import com.example.testapp.components.message.list.MessageListItemStyle
import com.example.testapp.components.message.list.adapter.MessageListItem
import com.example.testapp.components.message.list.adapter.MessageListItemPayloadDiff
import com.example.testapp.components.message.list.adapter.internal.DecoratedBaseMessageItemViewHolder
import com.example.testapp.components.message.list.adapter.viewholder.decorator.Decorator
import com.example.testapp.databinding.TinuiItemDateDividerBinding
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel


internal class DateDividerViewHolder(
    parent: ViewGroup,
    decorators: List<Decorator>,
    private val style: MessageListItemStyle,
    internal val binding: TinuiItemDateDividerBinding = TinuiItemDateDividerBinding.inflate(
        parent.streamThemeInflater,
        parent,
        false
    ),
) : DecoratedBaseMessageItemViewHolder<MessageListItem.DateSeparatorItem>(binding.root, decorators) {

    override fun bindData(data: MessageListItem.DateSeparatorItem, diff: MessageListItemPayloadDiff?) {
        super.bindData(data, diff)

        binding.dateLabel.text =
            DateUtils.getRelativeTimeSpanString(
                data.date.time,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

        style.textStyleDateSeparator.apply(binding.dateLabel)

        binding.dateLabel.background = ShapeAppearanceModel.Builder().setAllCornerSizes(DEFAULT_CORNER_RADIUS).build()
            .let(::MaterialShapeDrawable).apply { setTint(style.dateSeparatorBackgroundColor) }
    }

    private companion object {
        private val DEFAULT_CORNER_RADIUS = 16.dpToPxPrecise()
    }
}
