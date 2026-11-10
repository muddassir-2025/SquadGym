package com.muddassir.squadgym.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.data.remote.dto.ChartPointDto
import kotlin.math.roundToInt

/**
 * Interactive attendance/member bar chart drawn with Canvas.
 *
 * No chart library: full control over the premium look, tap-to-select, and zero
 * extra dependency weight. Closed gym days are dimmed rather than hidden.
 */
@Composable
fun BarChart(
    points: List<ChartPointDto>,
    modifier: Modifier = Modifier,
    height: Dp = 186.dp,
    selectedIndex: Int? = null,
    onSelect: (Int) -> Unit = {},
) {
    val colors = SquadGymTheme.colors
    if (points.isEmpty()) {
        EmptyHint("No data for this range yet.", modifier)
        return
    }

    val maxValue = (points.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val selected = selectedIndex?.takeIf { it in points.indices }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = (selected?.let { points[it].value } ?: points.maxOf { it.value }).toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = selected?.let { points[it].label } ?: "peak",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val slot = size.width / points.size
                        val index = (offset.x / slot).toInt().coerceIn(0, points.lastIndex)
                        onSelect(index)
                    }
                },
        ) {
            val gap = size.width / points.size
            val barWidth = (gap * 0.58f).coerceAtLeast(4f)
            val chartHeight = size.height - 8f

            // Horizontal guides.
            repeat(4) { line ->
                val y = chartHeight * (line / 3f)
                drawLine(
                    color = colors.chartGrid,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            points.forEachIndexed { index, point ->
                val ratio = point.value / maxValue.toFloat()
                val barHeight = (chartHeight * ratio * 0.92f).coerceAtLeast(if (point.value == 0) 2f else 6f)
                val left = index * gap + (gap - barWidth) / 2f
                val top = chartHeight - barHeight
                val isSelected = selected == index
                val base = when {
                    !point.isGymOpen -> colors.chartBarMuted.copy(alpha = 0.5f)
                    isSelected -> colors.chartBar
                    else -> colors.chartBar.copy(alpha = 0.35f)
                }

                drawRoundRect(
                    color = base,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val labelEvery = when {
                points.size <= 8 -> 1
                points.size <= 16 -> 2
                else -> 5
            }
            points.forEachIndexed { index, point ->
                val show = index % labelEvery == 0 || index == points.lastIndex
                Text(
                    text = if (show) point.weekday.ifBlank { point.label } else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Thin sparkline used in the member home for the weekly rhythm. */
@Composable
fun MiniTrendBars(values: List<Int>, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val colors = SquadGymTheme.colors
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val gap = size.width / values.size
        val barWidth = (gap * 0.6f).coerceAtLeast(2f)
        values.forEachIndexed { index, value ->
            val barHeight = (size.height * (value / max.toFloat())).coerceAtLeast(2f)
            drawRoundRect(
                color = if (value > 0) colors.chartBar else colors.chartBarMuted,
                topLeft = Offset(index * gap + (gap - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
fun ChartLegend(modifier: Modifier = Modifier, items: List<Pair<String, androidx.compose.ui.graphics.Color>>) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .then(Modifier),
                ) {
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        drawCircle(color = color, radius = size.minDimension / 2f)
                    }
                }
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun percentLabel(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}%" else "$rounded%"
}
