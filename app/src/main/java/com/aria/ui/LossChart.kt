package com.aria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LossChart(
    history: List<Float>,
    elapsedMs: Long,
    bestLoss: Float,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalAriaColors.current

    Column(
        modifier
            .background(c.bg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "LOSS HISTORY",
                color = c.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Text(
                "elapsed " + formatElapsed(elapsedMs),
                color = c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }

        Spacer(Modifier.height(4.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(c.panel),
        ) {
            if (history.isEmpty()) {
                Text(
                    "no data",
                    color = c.dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LossCanvas(history = history, running = running, modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "min ${"%.4f".format(history.minOrNull() ?: 0f)}",
                color = c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
            Text(
                "best ${if (bestLoss < Float.MAX_VALUE) "%.4f".format(bestLoss) else "—"}",
                color = c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun LossCanvas(
    history: List<Float>,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalAriaColors.current
    val lineColor = if (running) c.accent else c.dim
    val gridColor = c.line

    // Кэшируем нормализацию, чтобы не пересчитывать на каждой рекомпозиции без нужды
    val normalized = remember(history) { normalize(history) }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pad = 4f

        // Горизонтальные grid-линии (3 штуки)
        val gridStroke = 1f
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = gridStroke,
            )
        }

        if (normalized.size < 2) return@Canvas

        val step = (w - pad * 2) / (normalized.size - 1).toFloat()
        val path = Path()
        normalized.forEachIndexed { i, v ->
            val x = pad + i * step
            val y = pad + (h - pad * 2) * (1f - v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )

        // Точка на конце — текущее значение
        val lastX = pad + (normalized.size - 1) * step
        val lastY = pad + (h - pad * 2) * (1f - normalized.last())
        drawCircle(
            color = lineColor,
            radius = 2.5f,
            center = Offset(lastX, lastY),
        )
    }
}

/** Нормализует loss в [0, 1], где 1 — минимум (лучшее значение) сверху. */
private fun normalize(history: List<Float>): List<Float> {
    if (history.isEmpty()) return emptyList()
    var min = history[0]
    var max = history[0]
    for (v in history) {
        if (v < min) min = v
        if (v > max) max = v
    }
    val range = (max - min).coerceAtLeast(1e-6f)
    return history.map { (it - min) / range }
}

private fun formatElapsed(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}