package com.metitem.casapic.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.metitem.casapic.db.Photo_edits

@Composable
expect fun SkiaImageCanvas(
    filePath: String,
    editStack: List<Photo_edits>,
    modifier: Modifier
)
