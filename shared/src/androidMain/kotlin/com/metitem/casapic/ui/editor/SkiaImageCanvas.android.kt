package com.metitem.casapic.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.metitem.casapic.db.Photo_edits

@Composable
actual fun SkiaImageCanvas(
    filePath: String,
    editStack: List<Photo_edits>,
    modifier: Modifier
) {
    // Android: display image via Coil; edit overlay rendering is future work
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = filePath,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
