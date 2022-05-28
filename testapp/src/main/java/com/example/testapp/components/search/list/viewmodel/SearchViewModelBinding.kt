package com.example.testapp.components.search.list.viewmodel

import androidx.lifecycle.LifecycleOwner
import com.example.testapp.livedata.utils.EventObserver
import com.example.testapp.components.search.list.SearchResultListView

/**
 * Binds [SearchResultListView] with [SearchViewModel], updating the view's state based on
 * data provided by the ViewModel, and propagating view events to the ViewModel as needed.
 *
 * This function sets listeners on the view and ViewModel. Call this method
 * before setting any additional listeners on these objects yourself.
 */
@JvmName("bind")
public fun SearchViewModel.bindView(view: SearchResultListView, lifecycleOwner: LifecycleOwner) {
    state.observe(lifecycleOwner) { state ->
        when {
            state.isLoading -> {
                view.showLoading()
            }
            else -> {
                view.showMessages(state.query, state.results)
                view.setPaginationEnabled(!state.isLoadingMore && state.results.isNotEmpty())
            }
        }
    }
    errorEvents.observe(
        lifecycleOwner,
        EventObserver {
            view.showError()
        }
    )
    view.setLoadMoreListener(::loadMore)
}
