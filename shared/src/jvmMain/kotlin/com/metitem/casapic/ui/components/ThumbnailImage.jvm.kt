package com.metitem.casapic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
actual fun ThumbnailImage(
    thumbnailPath: String?,
    filePath: String,
    modifier: Modifier
) {
    // Load from thumbnail cache (fast path) or fall back to the original file
    val source = if (!thumbnailPath.isNullOrEmpty()) thumbnailPath else filePath
    AsyncImage(
        model = source,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
