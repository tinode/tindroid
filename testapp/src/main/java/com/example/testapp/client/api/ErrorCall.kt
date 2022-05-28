package com.example.testapp.client.api

import com.example.testapp.core.client.call.Call
import com.example.testapp.core.client.utils.Result
import com.example.testapp.core.client.errors.ChatError
import com.example.testapp.core.internal.coroutines.DispatcherProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal class ErrorCall<T : Any>(private val e: ChatError) : Call<T> {
    override fun cancel() {
        // Not supported
    }

    override fun execute(): Result<T> {
        return Result(e)
    }

    override fun enqueue(callback: Call.Callback<T>) {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(DispatcherProvider.Main) {
            callback.onResult(Result(e))
        }
    }
}
