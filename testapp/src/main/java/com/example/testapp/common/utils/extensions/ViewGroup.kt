package com.example.testapp.common.utils.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.testapp.core.internal.InternalTinUiApi

@InternalTinUiApi
public inline val ViewGroup.inflater: LayoutInflater
    get() = LayoutInflater.from(context)
