package com.example.testapp.core.client.call

import com.example.testapp.core.internal.coroutines.DispatcherProvider
import com.example.testapp.core.client.utils.Result
import com.example.testapp.core.client.utils.flatMap
import com.example.testapp.core.client.utils.onErrorSuspend
import com.example.testapp.core.client.utils.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class WithPreconditionCall<T : Any>(
    private val originalCall: Call<T>,
    private val scope: CoroutineScope,
    private val precondition: suspend () -> Result<Unit>,
) : Call<T> {
    private var job: Job? = null

    override fun execute(): Result<T> = runBlocking {
        val preconditionResult = precondition.invoke()
        return@runBlocking preconditionResult.flatMap { originalCall.execute() }
    }

    override fun enqueue(callback: Call.Callback<T>) {
        job = scope.launch {
            precondition.invoke()
                .onSuccess { originalCall.enqueue(callback) }
                .onErrorSuspend {
                    withContext(DispatcherProvider.Main) {
                        callback.onResult(Result.error(it))
                    }
                }
        }
    }

    override fun cancel() {
        job?.cancel()
    }
}
