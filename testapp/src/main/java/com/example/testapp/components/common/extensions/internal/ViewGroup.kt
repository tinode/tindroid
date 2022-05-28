package com.example.testapp.components.common.extensions.internal

import android.view.LayoutInflater
import android.view.ViewGroup

internal val ViewGroup.streamThemeInflater: LayoutInflater
    get() = LayoutInflater.from(context.createStreamThemeWrapper())
