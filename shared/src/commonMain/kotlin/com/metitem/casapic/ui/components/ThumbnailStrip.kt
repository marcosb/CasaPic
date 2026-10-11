package com.metitem.casapic.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metitem.casapic.db.Photos

@Composable
fun ThumbnailStrip(
    photos: List<Photos>,
    currentIndex: Int,
    onPhotoClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (photos.isNotEmpty()) {
            listState.animateScrollToItem(
                index = currentIndex.coerceIn(0, photos.lastIndex),
                scrollOffset = -280
            )
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        modifier = modifier
    ) {
        itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
            val isSelected = index == currentIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(60.dp)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Gray.copy(alpha = 0.4f)
                    )
                    .clickable { onPhotoClick(photo.id) }
            ) {
                ThumbnailImage(
                    thumbnailPath = photo.thumbnail_path,
                    filePath = photo.file_path,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
