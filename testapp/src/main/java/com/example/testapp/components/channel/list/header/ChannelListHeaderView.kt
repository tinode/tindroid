package com.example.testapp.components.channel.list.header

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.example.testapp.R
import com.example.testapp.client.models.User
import com.example.testapp.common.style.TextStyle
import com.example.testapp.components.common.extensions.internal.createStreamThemeWrapper
import com.example.testapp.components.common.extensions.internal.getColorCompat
import com.example.testapp.components.common.extensions.internal.getDimension
import com.example.testapp.components.common.extensions.internal.streamThemeInflater
import com.example.testapp.core.internal.InternalTinUiApi
import com.example.testapp.databinding.TinuiChannelListHeaderViewBinding

/**
 * A component that shows the title of the channels list, the current connection status,
 * the avatar of the current user, and provides an action button which can be used to create a new conversation.
 * It is designed to be displayed at the top of the channels screen of your app.
 */
public class ChannelListHeaderView : ConstraintLayout {

    public constructor(context: Context) : super(context.createStreamThemeWrapper()) {
        init(null)
    }

    public constructor(context: Context, attrs: AttributeSet?) : super(context.createStreamThemeWrapper(), attrs) {
        init(attrs)
    }

    public constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context.createStreamThemeWrapper(),
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    public constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context.createStreamThemeWrapper(),
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        init(attrs)
    }

    private val binding = TinuiChannelListHeaderViewBinding.inflate(streamThemeInflater, this, true)

    private fun init(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.ChannelListHeaderView,
            R.attr.tinui_ChannelListHeaderStyle,
            R.style.tinui_ChannelListHeader,
        ).use { typedArray ->
            configUserAvatar(typedArray)
            configOnlineTitle(typedArray)
            configOfflineTitleContainer(typedArray)
            configActionButton(typedArray)
        }
    }

    private fun configUserAvatar(typedArray: TypedArray) {
        val showAvatar = typedArray.getBoolean(R.styleable.ChannelListHeaderView_tinui_ShowUserAvatar, true)
        binding.userAvatar.apply {
            isInvisible = !showAvatar
            isClickable = showAvatar
        }
    }

    private fun configOnlineTitle(typedArray: TypedArray) {
        getOnlineTitleTextStyle(typedArray).apply(binding.onlineTextView)
    }

    private fun configOfflineTitleContainer(typedArray: TypedArray) {
        getOfflineTitleTextStyle(typedArray).apply(binding.offlineTextView)

        binding.offlineProgressBar.apply {
            isVisible =
                typedArray.getBoolean(R.styleable.ChannelListHeaderView_tinui_ShowOfflineProgressBar, true)
            indeterminateTintList = getProgressBarTint(typedArray)
        }
    }

    private fun getProgressBarTint(typedArray: TypedArray) =
        typedArray.getColorStateList(R.styleable.ChannelListHeaderView_tinui_OfflineProgressBarTint)
            ?: ContextCompat.getColorStateList(context, R.color.tinui_accent_blue)

    private fun configActionButton(typedArray: TypedArray) {
        binding.actionButton.apply {
            val showActionButton =
                typedArray.getBoolean(R.styleable.ChannelListHeaderView_tinui_ShowActionButton, true)

            isInvisible = !showActionButton
            isClickable = showActionButton

            val drawable = typedArray.getDrawable(R.styleable.ChannelListHeaderView_tinui_ActionButtonIcon)
                ?: ContextCompat.getDrawable(context, R.drawable.tinui_ic_pen)
            setImageDrawable(drawable)
            backgroundTintList =
                typedArray.getColorStateList(R.styleable.ChannelListHeaderView_tinui_ActionBackgroundTint)
                    ?: ContextCompat.getColorStateList(context, R.color.tinui_icon_button_background_selector)
        }
    }

    private fun getOnlineTitleTextStyle(typedArray: TypedArray): TextStyle {
        return TextStyle.Builder(typedArray)
            .size(
                R.styleable.ChannelListHeaderView_tinui_OnlineTitleTextSize,
                context.getDimension(R.dimen.tinui_text_large)
            )
            .color(
                R.styleable.ChannelListHeaderView_tinui_OnlineTitleTextColor,
                context.getColorCompat(R.color.tinui_text_color_primary)
            )
            .font(
                R.styleable.ChannelListHeaderView_tinui_OnlineTitleFontAssets,
                R.styleable.ChannelListHeaderView_tinui_OnlineTitleTextFont
            )
            .style(
                R.styleable.ChannelListHeaderView_tinui_OnlineTitleTextStyle,
                Typeface.BOLD
            ).build()
    }

    private fun getOfflineTitleTextStyle(typedArray: TypedArray): TextStyle {
        return TextStyle.Builder(typedArray)
            .size(
                R.styleable.ChannelListHeaderView_tinui_OfflineTitleTextSize,
                context.getDimension(R.dimen.tinui_text_large)
            )
            .color(
                R.styleable.ChannelListHeaderView_tinui_OfflineTitleTextColor,
                context.getColorCompat(R.color.tinui_text_color_primary)
            )
            .font(
                R.styleable.ChannelListHeaderView_tinui_OfflineTitleFontAssets,
                R.styleable.ChannelListHeaderView_tinui_OfflineTitleTextFont
            )
            .style(
                R.styleable.ChannelListHeaderView_tinui_OfflineTitleTextStyle,
                Typeface.BOLD
            ).build()
    }

    /**
     * Sets [User] to bind user information with the avatar in the header.
     *
     * @param user A user that will represent the avatar in the header.
     */
    public fun setUser(user: User) {
        binding.userAvatar.setUserData(user)
    }

    /**
     * Sets the title that is shown on the header when the client's network state is online.
     *
     * @param title A title that indicates the online network state.
     */
    public fun setOnlineTitle(title: String) {
        binding.onlineTextView.text = title
    }

    /**
     * Shows the title that indicates the network state is online.
     */
    public fun showOnlineTitle() {
        binding.offlineTitleContainer.isVisible = false
        binding.onlineTextView.isVisible = true
    }

    /**
     * Shows the title that indicates the network state is offline.
     */
    public fun showOfflineTitle() {
        binding.offlineTitleContainer.isVisible = true
        binding.offlineProgressBar.isVisible = false
        binding.onlineTextView.isVisible = false

        binding.offlineTextView.text = resources.getString(R.string.tinui_channel_list_header_offline)
    }

    /**
     * Shows the title that indicates the network state is connecting.
     */
    public fun showConnectingTitle() {
        binding.offlineTitleContainer.isVisible = true
        binding.offlineProgressBar.isVisible = true
        binding.onlineTextView.isVisible = false

        binding.offlineTextView.text = resources.getString(R.string.tinui_channel_list_header_disconnected)
    }

    /**
     * Sets a click listener for the left button in the header represented by the avatar of
     * the current user.
     */
    public fun setOnUserAvatarClickListener(listener: UserAvatarClickListener) {
        binding.userAvatar.setOnClickListener { listener.onUserAvatarClick() }
    }

    /**
     * Sets a click listener for the right button in the header.
     */
    public fun setOnActionButtonClickListener(listener: ActionButtonClickListener) {
        binding.actionButton.setOnClickListener { listener.onClick() }
    }

    @InternalTinUiApi
    public fun setOnUserAvatarLongClickListener(listener: () -> Unit) {
        binding.userAvatar.setOnLongClickListener {
            listener()
            true
        }
    }

    /**
     * Click listener for the left button in the header represented by the avatar of
     * the current user.
     */
    public fun interface UserAvatarClickListener {
        public fun onUserAvatarClick()
    }

    /**
     * Click listener for the right button in the header.
     */
    public fun interface ActionButtonClickListener {
        public fun onClick()
    }
}
