package com.aria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class AriaColors(
    val name: String,
    val bg: Color,
    val panel: Color,
    val accent: Color,
    val text: Color,
    val dim: Color,
    val line: Color,
)

val AriaGreen = AriaColors(
    name = "Green",
    bg = Color(0xFF0D0D0D),
    panel = Color(0xFF111111),
    accent = Color(0xFF7CFC7C),
    text = Color(0xFFA0A0A0),
    dim = Color(0xFF666666),
    line = Color(0xFF222222),
)

val AriaBlue = AriaColors(
    name = "Blue",
    bg = Color(0xFF0A0F1A),
    panel = Color(0xFF0F1622),
    accent = Color(0xFF6FB8FF),
    text = Color(0xFF9FB0C8),
    dim = Color(0xFF5A6878),
    line = Color(0xFF1E2838),
)

val AriaPurple = AriaColors(
    name = "Purple",
    bg = Color(0xFF0D0A17),
    panel = Color(0xFF141020),
    accent = Color(0xFFB98CFF),
    text = Color(0xFFB0A0C8),
    dim = Color(0xFF6F6088),
    line = Color(0xFF241E36),
)

val AriaAmber = AriaColors(
    name = "Amber",
    bg = Color(0xFF120D06),
    panel = Color(0xFF1A1208),
    accent = Color(0xFFFFB454),
    text = Color(0xFFC8A888),
    dim = Color(0xFF806A50),
    line = Color(0xFF2E2214),
)

val AriaMono = AriaColors(
    name = "Mono",
    bg = Color(0xFF0D0D0D),
    panel = Color(0xFF161616),
    accent = Color(0xFFE8E8E8),
    text = Color(0xFFA8A8A8),
    dim = Color(0xFF707070),
    line = Color(0xFF2A2A2A),
)

val AriaPresets = listOf(AriaGreen, AriaBlue, AriaPurple, AriaAmber, AriaMono)

val LocalAriaColors = compositionLocalOf { AriaGreen }

@Composable
fun AriaIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalAriaColors.current

    val gestureModifier = Modifier.pointerInput(onClick, onLongClick) {
        detectTapGestures(
            onTap = { onClick() },
            onLongPress = { onLongClick?.invoke() },
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.panel)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}