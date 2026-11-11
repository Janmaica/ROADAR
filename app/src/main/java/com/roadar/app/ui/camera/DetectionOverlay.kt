package com.roadar.app.ui.camera

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.roadar.app.ai.DetectionFrame
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DetectionOverlay(
    frame: DetectionFrame?,
    modifier: Modifier = Modifier
) {
    if (frame == null || frame.detections.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val scale = max(
            size.width / frame.frameWidth,
            size.height / frame.frameHeight
        )
        val offsetX = (size.width - frame.frameWidth * scale) / 2f
        val offsetY = (size.height - frame.frameHeight * scale) / 2f
        val strokeWidth = 2.dp.toPx()
        val labelPadding = 5.dp.toPx()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 12.dp.toPx()
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(230, 93, 26)
        }
        val textHeight = textPaint.descent() - textPaint.ascent()
        val labelHeight = textHeight + labelPadding * 2
        val labelGap = 2.dp.toPx()
        val placedLabels = mutableListOf<RectF>()

        frame.detections.forEach { detection ->
            val left = offsetX + detection.left * scale
            val top = offsetY + detection.top * scale
            val right = offsetX + detection.right * scale
            val bottom = offsetY + detection.bottom * scale
            drawRect(
                color = Color(0xFFFF7A24),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = strokeWidth)
            )

            val label = "${(detection.confidence * 100).roundToInt()}% pothole"
            val labelWidth = textPaint.measureText(label) + labelPadding * 2
            val labelLeft = left.coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
            var labelTop = (top - labelHeight).coerceAtLeast(0f)
            var placementAttempts = 0
            while (
                placementAttempts <= placedLabels.size &&
                placedLabels.any { existing ->
                    existing.left < labelLeft + labelWidth &&
                        existing.right > labelLeft &&
                        existing.top < labelTop + labelHeight &&
                        existing.bottom > labelTop
                }
            ) {
                labelTop = if (labelTop + labelHeight * 2 + labelGap <= size.height) {
                    labelTop + labelHeight + labelGap
                } else {
                    (labelTop - labelHeight - labelGap).coerceAtLeast(0f)
                }
                placementAttempts += 1
            }
            placedLabels += RectF(
                labelLeft,
                labelTop,
                labelLeft + labelWidth,
                labelTop + labelHeight
            )
            drawContext.canvas.nativeCanvas.drawRoundRect(
                labelLeft,
                labelTop,
                labelLeft + labelWidth,
                labelTop + labelHeight,
                4.dp.toPx(),
                4.dp.toPx(),
                backgroundPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                labelLeft + labelPadding,
                labelTop + labelPadding - textPaint.ascent(),
                textPaint
            )
        }
    }
}
