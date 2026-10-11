package com.metitem.casapic.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metitem.casapic.ui.components.ThumbnailImage
import com.metitem.casapic.viewmodel.SlideshowViewModel

@Composable
fun SlideshowScreen(
    viewModel: SlideshowViewModel,
    onBack: () -> Unit
) {
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val photo = viewModel.currentPhoto()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen image
        if (photo != null) {
            ThumbnailImage(
                thumbnailPath = photo.thumbnail_path,
                filePath = photo.file_path,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos", color = Color.White)
            }
        }

        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("✕ Exit", color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (photo != null) {
                Text(
                    photo.file_name,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${currentIndex + 1} of ${viewModel.photoCount}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = viewModel::prev) {
                    Text("◄", color = Color.White, fontSize = 20.sp)
                }
                TextButton(
                    onClick = { if (isPlaying) viewModel.pause() else viewModel.play() }
                ) {
                    Text(
                        if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = viewModel::next) {
                    Text("►", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}
