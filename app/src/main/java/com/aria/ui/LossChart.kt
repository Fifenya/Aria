package com.aria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

    // Считаем min/max из истории
    var minV = Float.MAX_VALUE
    var maxV = 0f
    if (history.isNotEmpty()) {
        minV = history[0]; maxV = history[0]
        for (v in history) {
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
    }

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
                color = c.accent, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
            )
            Text(
                "elapsed " + formatElapsed(elapsedMs),
                color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            )
        }

        Spacer(Modifier.height(4.dp))

        if (history.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(c.panel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "no data",
                    color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                )
            }
        } else {
            val normalized = remember(history) { normalize(history) }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(c.panel),
            ) {
                // Кривая
                LossCanvas(
                    normalized = normalized,
                    running = running,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 46.dp, top = 12.dp, end = 8.dp, bottom = 16.dp),
                )

                // Y: лучший (min loss) сверху
                Text(
                    "%.4f".format(minV),
                    color = c.accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 3.dp, top = 6.dp),
                )
                // Y: худший (max loss) снизу
                Text(
                    "%.4f".format(maxV),
                    color = c.dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 3.dp, bottom = 12.dp),
                )

                // X: old / new
                Text(
                    "old",
                    color = c.dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, bottom = 2.dp),
                )
                Text(
                    "new",
                    color = c.dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(3.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "samples ${history.size}",
                color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            )
            Text(
                "best " + if (bestLoss < Float.MAX_VALUE) "%.4f".format(bestLoss) else "—",
                color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun LossCanvas(
    normalized: List<Float>,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalAriaColors.current
    val lineColor = if (running) c.accent else c.dim
    val gridColor = c.line

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pad = 2f

        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
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

        val lastX = pad + (normalized.size - 1) * step
        val lastY = pad + (h - pad * 2) * (1f - normalized.last())
        drawCircle(lineColor, radius = 2.5f, center = Offset(lastX, lastY))
    }
}

/** Нормализует loss в [0, 1], где 1 — лучший (минимум). */
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