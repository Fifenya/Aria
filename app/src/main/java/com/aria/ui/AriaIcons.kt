package com.aria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun IconPlay(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.30f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.82f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.cornerPathEffect(3.dp.toPx()),
            ),
        )
    }
}

@Composable
fun IconPause(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val barW = w * 0.14f
        val barH = h * 0.60f
        val top = (h - barH) / 2f
        val r = CornerRadius(barW / 2f, barW / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.30f, top),
            size = Size(barW, barH),
            cornerRadius = r,
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.56f, top),
            size = Size(barW, barH),
            cornerRadius = r,
            style = Stroke(width = stroke),
        )
    }
}

@Composable
fun IconStop(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f, h * 0.28f),
            size = Size(w * 0.44f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = stroke),
        )
    }
}

@Composable
fun IconNote(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(w * 0.62f, h * 0.20f),
            end = Offset(w * 0.62f, h * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.62f, h * 0.20f),
            end = Offset(w * 0.84f, h * 0.30f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawOval(
            color = color,
            topLeft = Offset(w * 0.34f, h * 0.60f),
            size = Size(w * 0.30f, h * 0.22f),
            style = Stroke(width = stroke),
        )
    }
}

@Composable
fun IconSliders(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val knobR = w * 0.08f
        val cap = StrokeCap.Round

        drawLine(color, Offset(w * 0.18f, h * 0.30f), Offset(w * 0.82f, h * 0.30f), stroke, cap)
        drawCircle(color, knobR, Offset(w * 0.36f, h * 0.30f))

        drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), stroke, cap)
        drawCircle(color, knobR, Offset(w * 0.66f, h * 0.50f))

        drawLine(color, Offset(w * 0.18f, h * 0.70f), Offset(w * 0.82f, h * 0.70f), stroke, cap)
        drawCircle(color, knobR, Offset(w * 0.48f, h * 0.70f))
    }
}

@Composable
fun IconSave(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val cap = StrokeCap.Round

        drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.62f), stroke, cap)
        drawLine(color, Offset(w * 0.32f, h * 0.46f), Offset(w * 0.5f, h * 0.62f), stroke, cap)
        drawLine(color, Offset(w * 0.68f, h * 0.46f), Offset(w * 0.5f, h * 0.62f), stroke, cap)
        drawLine(color, Offset(w * 0.24f, h * 0.80f), Offset(w * 0.76f, h * 0.80f), stroke, cap)
    }
}

@Composable
fun IconFaq(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val cap = StrokeCap.Round

        drawCircle(
            color = color,
            radius = w * 0.42f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke),
        )

        val path = Path().apply {
            moveTo(w * 0.36f, h * 0.40f)
            quadraticBezierTo(w * 0.50f, h * 0.24f, w * 0.62f, h * 0.40f)
            quadraticBezierTo(w * 0.68f, h * 0.54f, w * 0.50f, h * 0.58f)
            lineTo(w * 0.50f, h * 0.64f)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = cap))

        drawCircle(
            color = color,
            radius = stroke * 0.75f,
            center = Offset(w * 0.50f, h * 0.76f),
        )
    }
}

@Composable
fun IconClose(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val cap = StrokeCap.Round

        drawLine(color, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f), stroke, cap)
        drawLine(color, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), stroke, cap)
    }
}

@Composable
fun IconLoop(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val r = w * 0.30f
        val cx = w * 0.5f
        val cy = h * 0.5f

        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        val atr = Path().apply {
            moveTo(cx + r * 0.80f, cy - r * 0.65f)
            lineTo(cx + r * 1.15f, cy - r * 0.25f)
            lineTo(cx + r * 0.60f, cy - r * 0.20f)
            close()
        }
        drawPath(atr, color)

        val atl = Path().apply {
            moveTo(cx - r * 0.80f, cy + r * 0.65f)
            lineTo(cx - r * 1.15f, cy + r * 0.25f)
            lineTo(cx - r * 0.60f, cy + r * 0.20f)
            close()
        }
        drawPath(atl, color)
    }
}

@Composable
fun IconSparkle(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val outer = w * 0.42f
        val inner = w * 0.14f
        val path = Path().apply {
            moveTo(cx, cy - outer)
            quadraticBezierTo(cx + inner * 0.6f, cy - inner * 0.6f, cx + outer, cy)
            quadraticBezierTo(cx + inner * 0.6f, cy + inner * 0.6f, cx, cy + outer)
            quadraticBezierTo(cx - inner * 0.6f, cy + inner * 0.6f, cx - outer, cy)
            quadraticBezierTo(cx - inner * 0.6f, cy - inner * 0.6f, cx, cy - outer)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun IconSend(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.dp.toPx()
        val cap = StrokeCap.Round

        drawLine(color, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), stroke, cap)
        drawLine(color, Offset(w * 0.60f, h * 0.30f), Offset(w * 0.80f, h * 0.50f), stroke, cap)
        drawLine(color, Offset(w * 0.60f, h * 0.70f), Offset(w * 0.80f, h * 0.50f), stroke, cap)
    }
}