package com.metitem.casapic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ThumbnailImage(
    thumbnailPath: String?,
    filePath: String,
    modifier: Modifier = Modifier
)
