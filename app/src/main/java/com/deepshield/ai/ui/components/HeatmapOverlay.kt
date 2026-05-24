package com.deepshield.ai.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.deepshield.ai.ui.theme.*

/**
 * Canvas-based heatmap overlay that renders Grad-CAM attention data.
 * Draws colored rectangles over image regions based on suspicion scores.
 *
 * Color coding:
 *  - Red (#FF0000) = highly suspicious (score > 0.7)
 *  - Yellow/Orange (#FFAA00) = moderate suspicion (0.4 - 0.7)
 *  - Blue (#0066FF) = low suspicion (< 0.4)
 */
@Composable
fun HeatmapOverlay(
    heatmapData: Array<FloatArray>, // 2D grid of suspicion scores (0-1)
    modifier: Modifier = Modifier,
    alpha: Float = 0.45f,
    showGrid: Boolean = false
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val rows = heatmapData.size
        if (rows == 0) return@Canvas
        val cols = heatmapData[0].size
        if (cols == 0) return@Canvas

        val cellWidth = size.width / cols
        val cellHeight = size.height / rows

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val score = heatmapData[row][col].coerceIn(0f, 1f)

                // Color interpolation based on suspicion score
                val color = when {
                    score > 0.7f -> {
                        // Red zone — highly suspicious
                        Color(
                            red = 1f,
                            green = (1f - score) * 0.3f,
                            blue = 0f,
                            alpha = alpha * score
                        )
                    }
                    score > 0.4f -> {
                        // Yellow/Orange zone — moderate
                        val t = (score - 0.4f) / 0.3f
                        Color(
                            red = 1f,
                            green = 0.67f - t * 0.37f,
                            blue = 0f,
                            alpha = alpha * score
                        )
                    }
                    score > 0.1f -> {
                        // Blue zone — low suspicion
                        val t = (score - 0.1f) / 0.3f
                        Color(
                            red = 0f,
                            green = 0.4f * t,
                            blue = 1f,
                            alpha = alpha * score
                        )
                    }
                    else -> Color.Transparent
                }

                // Draw cell
                drawRect(
                    color = color,
                    topLeft = Offset(col * cellWidth, row * cellHeight),
                    size = Size(cellWidth, cellHeight)
                )

                // Optional grid lines
                if (showGrid && score > 0.1f) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.05f),
                        topLeft = Offset(col * cellWidth, row * cellHeight),
                        size = Size(cellWidth, cellHeight),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Generates a demo heatmap data grid for testing/demo purposes.
 * Creates a realistic pattern with high suspicion around face regions.
 */
fun generateDemoHeatmap(rows: Int = 14, cols: Int = 14): Array<FloatArray> {
    return Array(rows) { row ->
        FloatArray(cols) { col ->
            // Create realistic face-region pattern
            val centerRow = rows / 2f
            val centerCol = cols / 2f
            val distFromCenter = kotlin.math.sqrt(
                ((row - centerRow) * (row - centerRow) +
                 (col - centerCol) * (col - centerCol)).toDouble()
            ).toFloat()

            val maxDist = kotlin.math.sqrt(
                (centerRow * centerRow + centerCol * centerCol).toDouble()
            ).toFloat()

            // Higher scores near center (face area)
            val baseScore = (1f - distFromCenter / maxDist).coerceIn(0f, 1f)

            // Add some randomness for realism
            val noise = (kotlin.math.sin((row * 7 + col * 13).toDouble()) * 0.15f).toFloat()

            (baseScore * 0.8f + noise).coerceIn(0f, 1f)
        }
    }
}
