package com.example.testapp.common.utils.extensions

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.example.testapp.core.internal.InternalTinUiApi

@InternalTinUiApi
public fun ConstraintLayout.updateConstraints(actions: ConstraintSet.() -> Unit) {
    val set = ConstraintSet()
    set.clone(this)
    set.actions()
    set.applyTo(this)
}

@InternalTinUiApi
public fun ConstraintSet.constrainViewToParentBySide(view: View, side: Int) {
    connect(view.id, side, ConstraintSet.PARENT_ID, side)
}

@InternalTinUiApi
public fun ConstraintSet.constrainViewStartToEndOfView(startView: View, endView: View) {
    connect(startView.id, ConstraintSet.START, endView.id, ConstraintSet.END)
}

@InternalTinUiApi
public fun ConstraintSet.constrainViewEndToEndOfView(startView: View, endView: View) {
    connect(startView.id, ConstraintSet.END, endView.id, ConstraintSet.END)
}

@InternalTinUiApi
public fun ConstraintSet.horizontalChainInParent(vararg views: View) {
    createHorizontalChain(
        ConstraintSet.PARENT_ID,
        ConstraintSet.LEFT,
        ConstraintSet.PARENT_ID,
        ConstraintSet.RIGHT,
        views.map(View::getId).toIntArray(),
        null,
        ConstraintSet.CHAIN_SPREAD
    )
}

@InternalTinUiApi
public fun ConstraintSet.verticalChainInParent(vararg views: View) {
    createVerticalChain(
        ConstraintSet.PARENT_ID,
        ConstraintSet.TOP,
        ConstraintSet.PARENT_ID,
        ConstraintSet.BOTTOM,
        views.map(View::getId).toIntArray(),
        null,
        ConstraintSet.CHAIN_SPREAD
    )
}
