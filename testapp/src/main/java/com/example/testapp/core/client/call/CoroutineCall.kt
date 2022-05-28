package com.example.testapp.core.client.call

import com.example.testapp.core.client.utils.Result
import com.example.testapp.core.internal.InternalTinUiApi
import com.example.testapp.core.internal.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@InternalTinUiApi
public class CoroutineCall<T : Any>(
    private val scope: CoroutineScope,
    private val suspendingTask: suspend CoroutineScope.() -> Result<T>,
) : Call<T> {

    private var job: Job? = null

    internal suspend fun awaitImpl(): Result<T> {
        return withContext(scope.coroutineContext) {
            suspendingTask()
        }
    }

    override fun cancel() {
        job?.cancel()
    }

    override fun execute(): Result<T> {
        return runBlocking(block = suspendingTask)
    }

    override fun enqueue(callback: Call.Callback<T>) {
        job = scope.launch {
            val result = suspendingTask()
            withContext(DispatcherProvider.Main) {
                callback.onResult(result)
            }
        }
    }
}
