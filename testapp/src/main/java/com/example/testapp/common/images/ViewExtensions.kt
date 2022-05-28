package com.example.testapp.common.images

import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.example.testapp.core.internal.InternalTinUiApi
import com.example.testapp.common.images.TinuiImageLoader.ImageTransformation

@InternalTinUiApi
public fun ImageView.load(
    data: Any?,
    @DrawableRes placeholderResId: Int? = null,
    transformation: ImageTransformation = ImageTransformation.None,
    onStart: () -> Unit = {},
    onComplete: () -> Unit = {},
) {
    TinuiImageLoader.instance().load(
        target = this,
        data = data,
        placeholderResId = placeholderResId,
        transformation = transformation,
        onStart = onStart,
        onComplete = onComplete
    )
}

@InternalTinUiApi
public fun ImageView.load(
    data: Any?,
    placeholderDrawable: Drawable,
    transformation: ImageTransformation = ImageTransformation.None,
    onStart: () -> Unit = {},
    onComplete: () -> Unit = {},
) {
    TinuiImageLoader.instance().load(
        target = this,
        data = data,
        placeholderDrawable = placeholderDrawable,
        transformation = transformation,
        onStart = onStart,
        onComplete = onComplete
    )
}

@InternalTinUiApi
public fun ImageView.loadVideoThumbnail(
    uri: Uri?,
    @DrawableRes placeholderResId: Int? = null,
    transformation: ImageTransformation = ImageTransformation.None,
    onStart: () -> Unit = {},
    onComplete: () -> Unit = {},
) {
    TinuiImageLoader.instance().loadVideoThumbnail(
        target = this,
        uri = uri,
        placeholderResId = placeholderResId,
        transformation = transformation,
        onStart = onStart,
        onComplete = onComplete
    )
}
