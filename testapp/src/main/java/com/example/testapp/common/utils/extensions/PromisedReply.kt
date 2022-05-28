package com.example.testapp.common.utils.extensions

import androidx.annotation.Nullable
import co.tinode.tinsdk.PromisedReply
import java.lang.Exception

public fun <T> PromisedReply<T>.then(block: PromisedReplyListenerHelper<T>.() -> Unit): PromisedReply<T> {
    val listener = PromisedReplyListenerHelper<T>()
    listener.block()
    return this.thenApply(listener)
}

private typealias Listener<T, D> = (result: T) -> PromisedReply<D>?

public class PromisedReplyListenerHelper<T> : PromisedReply.SuccessListener<T>,
    PromisedReply.FailureListener<T> {

    private var onSuccessListener: Listener<T, T>? = null

    public fun onSuccess(@Nullable listener: Listener<T, T>) {
        onSuccessListener = listener
    }

    override fun onSuccess(result: T): PromisedReply<T>? {
        return onSuccessListener?.invoke(result)
    }

    private var onFailureListener: Listener<Exception, T>? = null

    public fun <E : Exception?> onFailure(@Nullable listener: Listener<Exception, T>) {
        onFailureListener = listener
    }

    override fun <E : Exception> onFailure(err: E): PromisedReply<T>? {
        return onFailureListener?.invoke(err)
    }
}