package com.metitem.casapic.paging

import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * KMP-compatible equivalent of androidx.paging.compose.LazyPagingItems.
 * Built on PagingDataPresenter from paging-common so it works on Compose Desktop (JVM)
 * without the Android-only paging-compose artifact.
 */
class LazyPagingItems<T : Any> internal constructor(
    private val flow: Flow<PagingData<T>>
) {
    private val noLoadState = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = false),
        prepend = LoadState.NotLoading(endOfPaginationReached = false),
        append  = LoadState.NotLoading(endOfPaginationReached = false)
    )

    var loadState: CombinedLoadStates by mutableStateOf(
        CombinedLoadStates(
            refresh   = LoadState.NotLoading(endOfPaginationReached = false),
            prepend   = LoadState.NotLoading(endOfPaginationReached = false),
            append    = LoadState.NotLoading(endOfPaginationReached = false),
            source    = noLoadState,
            mediator  = null
        )
    )
        private set

    var itemCount by mutableIntStateOf(0)
        private set

    private val presenter = object : PagingDataPresenter<T>(
        mainContext = Dispatchers.Main,
        cachedPagingData = null
    ) {
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
            itemCount = size
        }
    }

    init {
        presenter.addLoadStateListener { states ->
            loadState = states
        }
    }

    operator fun get(index: Int): T? = presenter[index]

    fun peek(index: Int): T? = presenter.peek(index)

    internal suspend fun collectPagingData() {
        flow.collectLatest { pagingData ->
            presenter.collectFrom(pagingData)
        }
    }
}

@Composable
fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItems(): LazyPagingItems<T> {
    val lazyPagingItems = remember(this) { LazyPagingItems(this) }
    LaunchedEffect(lazyPagingItems) {
        lazyPagingItems.collectPagingData()
    }
    return lazyPagingItems
}

/** Stable key mapping for use with LazyVerticalGrid items. */
fun <T : Any> LazyPagingItems<T>.itemKey(key: (T) -> Any): (Int) -> Any =
    { index -> peek(index)?.let(key) ?: index }

/** LazyGridScope items extension that mirrors the paging-compose API. */
fun <T : Any> LazyGridScope.items(
    items: LazyPagingItems<T>,
    key: ((index: Int) -> Any)? = null,
    itemContent: @Composable (T?) -> Unit
) {
    items(
        count = items.itemCount,
        key = key
    ) { index ->
        itemContent(items[index])
    }
}
