package com.example.testapp.common.utils.extensions

import android.content.ContextWrapper
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.example.testapp.core.internal.InternalTinUiApi

/**
 * Ensures the context being accessed in a View can be cast to Activity.
 */
@InternalTinUiApi
public val View.activity: AppCompatActivity?
    get() {
        var context = context
        while (context is ContextWrapper) {
            if (context is AppCompatActivity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

@InternalTinUiApi
public fun View.showToast(@StringRes resId: Int) {
    Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
}

@InternalTinUiApi
public fun View.showToast(text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
