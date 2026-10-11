package com.metitem.casapic.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.db.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoPagingSource(
    private val db: CasaPicDatabase,
    private val folderId: Long?,
    private val albumId: Long?,
    private val showHidden: Boolean,
    private val query: String
) : PagingSource<Int, Photos>() {

    private val showHiddenLong get() = if (showHidden) 1L else 0L

    private val changeListener = Query.Listener { invalidate() }

    init {
        if (albumId != null) {
            db.casaPicQueries.countPhotosInAlbum(albumId).addListener(changeListener)
        } else {
            db.casaPicQueries.countPhotosAll(showHiddenLong).addListener(changeListener)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Photos>): Int? =
        state.anchorPosition?.let { anchor ->
            (state.closestPageToPosition(anchor)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(state.config.pageSize))
                ?.coerceAtLeast(0)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Photos> =
        runCatching {
            val offset = (params.key ?: 0).toLong()
            val limit = params.loadSize.toLong()
            val items = withContext(Dispatchers.Default) {
                if (albumId != null) {
                    db.casaPicQueries.getPhotosInAlbumPaged(albumId, limit, offset).executeAsList()
                } else if (query.isBlank()) {
                    if (folderId != null)
                        db.casaPicQueries.getPhotosForFolder(folderId, showHiddenLong, limit, offset).executeAsList()
                    else
                        db.casaPicQueries.getPhotosAll(showHiddenLong, limit, offset).executeAsList()
                } else {
                    if (folderId != null)
                        db.casaPicQueries.searchPhotosInFolder(query, folderId, showHiddenLong, limit, offset).executeAsList()
                    else
                        db.casaPicQueries.searchPhotos(query, showHiddenLong, limit, offset).executeAsList()
                }
            }
            LoadResult.Page(
                data = items,
                prevKey = if (offset == 0L) null else (offset - limit).toInt().coerceAtLeast(0),
                nextKey = if (items.size < limit.toInt()) null else (offset + limit).toInt()
            )
        }.getOrElse { LoadResult.Error(it) }
}
