package com.metitem.casapic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metitem.casapic.model.HistogramData

@Composable
fun HistogramWidget(data: HistogramData, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xFF1A1A1A))) {
        Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            val maxVal = maxOf(
                data.r.maxOrNull() ?: 1,
                data.g.maxOrNull() ?: 1,
                data.b.maxOrNull() ?: 1,
                1
            ).toFloat()
            val w = size.width
            val h = size.height
            val barW = (w / 256f).coerceAtLeast(0.5f)
            for (i in 0 until 256) {
                val x = i * barW
                val rH = (data.r[i] / maxVal) * h
                val gH = (data.g[i] / maxVal) * h
                val bH = (data.b[i] / maxVal) * h
                if (rH > 0f) drawRect(
                    color = Color(1f, 0.2f, 0.2f, 0.7f),
                    topLeft = Offset(x, h - rH),
                    size = Size(barW, rH)
                )
                if (gH > 0f) drawRect(
                    color = Color(0.2f, 0.85f, 0.2f, 0.7f),
                    topLeft = Offset(x, h - gH),
                    size = Size(barW, gH)
                )
                if (bH > 0f) drawRect(
                    color = Color(0.3f, 0.5f, 1f, 0.7f),
                    topLeft = Offset(x, h - bH),
                    size = Size(barW, bH)
                )
            }
        }
    }
}
