package com.monito.expense.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class PieSlice(val value: Float, val color: Color, val label: String)

/** Donut chart with the total in the middle, replacing the MPAndroidChart pie of the original app. */
@Composable
fun PieChart(slices: List<PieSlice>, centerText: String, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .padding(8.dp),
        ) {
            val stroke = size.minDimension * 0.17f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total > 0f) {
                var start = -90f
                for (s in slices) {
                    val sweep = 360f * (s.value / total)
                    if (sweep <= 0f) continue
                    drawArc(s.color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        Text(
            centerText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.4f),
        )
    }
}
