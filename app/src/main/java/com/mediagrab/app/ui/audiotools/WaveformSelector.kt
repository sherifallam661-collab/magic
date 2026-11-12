package com.mediagrab.app.ui.audiotools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private enum class DragHandle { START, END }

@Composable
fun WaveformSelector(
    amplitudes: List<Float>,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChanged: (newStartMs: Long, newEndMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val handleColor = MaterialTheme.colorScheme.tertiary

    val safeDuration = durationMs.coerceAtLeast(1L)
    val startFraction = (startMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val endFraction = (endMs.toFloat() / safeDuration).coerceIn(startFraction, 1f)

    var activeHandle by remember { mutableStateOf<DragHandle?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .pointerInput(durationMs, startMs, endMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val touchFraction = (offset.x / width).coerceIn(0f, 1f)
                        val distStart = abs(touchFraction - startFraction)
                        val distEnd = abs(touchFraction - endFraction)
                        activeHandle = if (distStart <= distEnd) DragHandle.START else DragHandle.END
                    },
                    onDragEnd = { activeHandle = null },
                    onDragCancel = { activeHandle = null },
                    onDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val touchFraction = (change.position.x / width).coerceIn(0f, 1f)
                        val touchMs = (touchFraction * safeDuration).toLong()
                        val minGapMs = 500L.coerceAtMost(safeDuration / 4)

                        when (activeHandle) {
                            DragHandle.START -> {
                                val clamped = touchMs.coerceIn(0L, (endMs - minGapMs).coerceAtLeast(0L))
                                onRangeChanged(clamped, endMs)
                            }
                            DragHandle.END -> {
                                val clamped = touchMs.coerceIn((startMs + minGapMs).coerceAtMost(safeDuration), safeDuration)
                                onRangeChanged(startMs, clamped)
                            }
                            null -> Unit
                        }
                    }
                )
            }
    ) {
        val barCount = amplitudes.size.coerceAtLeast(1)
        val slotWidth = size.width / barCount
        val barWidth = (slotWidth * 0.6f).coerceAtLeast(2f)
        val centerY = size.height / 2f

        amplitudes.forEachIndexed { index, amp ->
            val fraction = index.toFloat() / barCount
            val inRange = fraction in startFraction..endFraction
            val barHeight = (amp * size.height * 0.85f).coerceAtLeast(6f)
            val x = index * slotWidth + (slotWidth - barWidth) / 2f
            drawRoundRect(
                color = if (inRange) activeColor else inactiveColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        // Start handle line & thumb
        val startX = startFraction * size.width
        drawLine(
            color = handleColor,
            start = Offset(startX, 0f),
            end = Offset(startX, size.height),
            strokeWidth = 5f
        )
        drawCircle(
            color = handleColor,
            radius = 12f,
            center = Offset(startX, 14f)
        )

        // End handle line & thumb
        val endX = endFraction * size.width
        drawLine(
            color = handleColor,
            start = Offset(endX, 0f),
            end = Offset(endX, size.height),
            strokeWidth = 5f
        )
        drawCircle(
            color = handleColor,
            radius = 12f,
            center = Offset(endX, size.height - 14f)
        )
    }
}
