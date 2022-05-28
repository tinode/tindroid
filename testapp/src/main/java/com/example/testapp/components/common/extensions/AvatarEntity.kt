package com.example.testapp.components.common.extensions

import android.content.Context
import android.graphics.Bitmap
import com.example.testapp.client.models.AvatarEntity
import com.example.testapp.common.images.TinuiImageLoader

public suspend fun AvatarEntity.loadBitmapImage(context: Context): Bitmap? =
    imageBitmap ?: TinuiImageLoader.instance().loadAsBitmap(context, image)