package com.example.testapp.client

import androidx.lifecycle.*
import com.example.testapp.core.internal.coroutines.DispatcherProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal class TinodeLifecycleObserver(private val handler: LifecycleHandler) : DefaultLifecycleObserver {
    private var recurringResumeEvent = false
    @Volatile private var isObserving = false

    fun observe() {
        if (isObserving.not()) {
            isObserving = true
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(DispatcherProvider.Main) {
                ProcessLifecycleOwner.get()
                    .lifecycle
                    .addObserver(this@TinodeLifecycleObserver)
            }
        }
    }

    fun dispose() {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(DispatcherProvider.Main) {
            ProcessLifecycleOwner.get()
                .lifecycle
                .removeObserver(this@TinodeLifecycleObserver)
        }
        isObserving = false
        recurringResumeEvent = false
    }

    override fun onResume(owner: LifecycleOwner) {
        // ignore event when we just started observing the lifecycle
        if (recurringResumeEvent) {
            handler.resume()
        }
        recurringResumeEvent = true
    }

    override fun onStop(owner: LifecycleOwner) {
        handler.stopped()
    }
}

internal interface LifecycleHandler {
    fun resume()
    fun stopped()
}
