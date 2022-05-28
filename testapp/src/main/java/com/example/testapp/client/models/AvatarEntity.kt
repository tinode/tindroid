package com.example.testapp.client.models

import android.graphics.Bitmap

public sealed interface AvatarEntity {
    public var image: String
    public var imageBitmap: Bitmap?

    public fun loadImage() {

    }
}
