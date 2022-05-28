package com.example.testapp.common.style

public class ChatStyle {
    public var defaultTextStyle: TextStyle? = null
    public fun hasDefaultFont(): Boolean = defaultTextStyle?.hasFont() == true
}
