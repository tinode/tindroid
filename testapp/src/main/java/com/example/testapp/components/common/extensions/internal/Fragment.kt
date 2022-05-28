package com.example.testapp.components.common.extensions.internal

import androidx.fragment.app.Fragment

internal inline fun <reified T> Fragment.findListener(): T? {
    return (parentFragment as? T) ?: (activity as? T)
}
