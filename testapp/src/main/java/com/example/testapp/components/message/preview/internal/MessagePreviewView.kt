package com.example.testapp.components.message.preview.internal

import android.content.Context
import android.text.Html
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.widget.FrameLayout
import com.example.testapp.R
import com.example.testapp.client.models.Message
import com.example.testapp.components.common.extensions.internal.bold
import com.example.testapp.components.common.extensions.internal.createStreamThemeWrapper
import com.example.testapp.components.common.extensions.internal.singletonList
import com.example.testapp.components.common.extensions.internal.streamThemeInflater
import com.example.testapp.components.message.preview.MessagePreviewStyle
import com.example.testapp.databinding.TinuiMessagePreviewItemBinding

internal class MessagePreviewView : FrameLayout {

    private val binding = TinuiMessagePreviewItemBinding.inflate(streamThemeInflater, this, true)

    constructor(context: Context) : super(context.createStreamThemeWrapper()) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context.createStreamThemeWrapper(), attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context.createStreamThemeWrapper(),
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        parseAttrs(attrs)
    }

    private fun parseAttrs(attrs: AttributeSet?) {
        attrs ?: return
    }

    fun styleView(messagePreviewStyle: MessagePreviewStyle) {
        messagePreviewStyle.run {
            messageSenderTextStyle.apply(binding.senderNameLabel)
            messageTextStyle.apply(binding.messageLabel)
            messageTimeTextStyle.apply(binding.messageTimeLabel)
        }
    }

    fun setMessage(message: Message, currentUserMention: String? = null) {
        binding.avatarView.setUserData(message.user)
        binding.senderNameLabel.text = formatChannelName(message)
        binding.messageLabel.text = formatMessagePreview(message, currentUserMention)
//        binding.messageTimeLabel.text = ChatUI.dateFormatter.formatDate(message.createdAt ?: message.createdLocallyAt)
    }

    private fun formatChannelName(message: Message): CharSequence {
        val channel = message.channelInfo
        return if (channel?.name != null && channel.memberCount > 2) {
            Html.fromHtml(
                context.getString(
                    R.string.tinui_message_preview_sender,
                    message.user.name,
                    channel.name,
                )
            )
        } else {
            message.user.name.bold()
        }
    }

    private fun formatMessagePreview(message: Message, currentUserMention: String?): CharSequence {
        val attachmentsText = "" // message.getAttachmentsText()

        val previewText = message.text.trim().let {
            if (currentUserMention != null) {
                // bold mentions of the current user
                it.bold(currentUserMention.singletonList(), ignoreCase = true)
            } else {
                it
            }
        }

        return listOf(previewText, attachmentsText)
            .filterNot { it.isNullOrEmpty() }
            .joinTo(SpannableStringBuilder(), " ")
    }
}
