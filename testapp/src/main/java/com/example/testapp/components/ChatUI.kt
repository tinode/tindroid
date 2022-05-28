package com.example.testapp.components

import android.content.Context
import com.example.testapp.common.style.ChatFonts
import com.example.testapp.common.style.ChatFontsImpl
import com.example.testapp.common.style.ChatStyle
import com.example.testapp.common.images.ImageHeadersProvider
import com.example.testapp.common.images.TinuiImageLoader
import com.example.testapp.common.utils.DateFormatter
import com.example.testapp.components.avatar.AvatarBitmapFactory
import com.example.testapp.components.transformer.AutoLinkableTextTransformer
import com.example.testapp.components.transformer.ChatMessageTextTransformer
import com.example.testapp.components.utils.lazyVar

/**
 * ChatUI handles any configuration for the Chat UI elements.
 *
 * @see ChatMarkdown
 * @see ChatFonts
 * @see ImageHeadersProvider
 */
public object ChatUI {
    internal lateinit var appContext: Context

    public var style: ChatStyle = ChatStyle()

    /**
     * Provides HTTP headers for image loading requests.
     */
    public var imageHeadersProvider: ImageHeadersProvider
        set(value) {
            TinuiImageLoader.instance().imageHeadersProvider = value
        }
        get() = TinuiImageLoader.instance().imageHeadersProvider

    /**
     * Allows setting default fonts used by UI components.
     */
    public var fonts: ChatFonts by lazyVar { ChatFontsImpl(style, appContext) }

    /**
     * Allows intercepting and providing custom bitmap displayed with AvatarView.
     */
    public var avatarBitmapFactory: AvatarBitmapFactory by lazyVar { AvatarBitmapFactory(appContext) }

    /**
     * Allows formatting date-time objects as strings.
     */
    public var dateFormatter: DateFormatter by lazyVar { DateFormatter.from(appContext) }

    /**
     * Allows customising the message text's format or style.
     */
    public var messageTextTransformer: ChatMessageTextTransformer by lazyVar {
        AutoLinkableTextTransformer { textView, messageItem ->
            // Customize the transformer if needed
            textView.text = messageItem.message.text
        }
    }
}
