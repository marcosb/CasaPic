package com.metitem.casapic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
actual fun ThumbnailImage(
    thumbnailPath: String?,
    filePath: String,
    modifier: Modifier
) {
    val source = if (!thumbnailPath.isNullOrEmpty()) thumbnailPath else filePath
    AsyncImage(
        model = source,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
