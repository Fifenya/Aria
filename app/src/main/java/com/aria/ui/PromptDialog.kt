package com.aria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Одна вариация — набор нот + её параметры + тег промпта. */
data class VariationItem(
    val index: Int,
    val notes: List<Int>,
    val temperature: Float,
    val noteMs: Int,
    val label: String,
    val tag: String,
)

@Composable
fun PromptDialog(
    onGenerate: (String) -> List<VariationItem>,
    onPlay: (VariationItem) -> Unit,
    onSave: (VariationItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAriaColors.current

    var text by remember { mutableStateOf("") }
    var variations by remember { mutableStateOf<List<VariationItem>>(emptyList()) }
    var playingIndex by remember { mutableStateOf(-1) }

    val presets = listOf(
        "sad slow piano",
        "happy fast dance",
        "dark mysterious",
        "epic cinematic battle",
        "playful light childish",
        "calm peaceful lullaby",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (variations.isEmpty()) "Describe your music" else "Variations",
                    color = c.accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                )
                if (variations.isNotEmpty()) {
                    Text(
                        "×",
                        color = c.dim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable { variations = emptyList() }
                            .padding(4.dp),
                    )
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (variations.isEmpty()) {
                    // ---------- ФАЗА 1: ввод ----------
                    Text(
                        "напиши что хочешь услышать (по-русски или по-английски)",
                        color = c.dim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.bg)
                            .border(1.dp, c.line, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .heightIn(min = 60.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                "грустный медленный пиано…",
                                color = c.dim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = TextStyle(
                                color = c.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            ),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        "или нажми пресет:",
                        color = c.dim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                    presets.forEach { preset ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.bg)
                                .clickable { text = preset }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                preset,
                                color = c.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                } else {
                    // ---------- ФАЗА 2: вариации ----------
                    Text(
                        "три варианта — разная температура и стартовая нота",
                        color = c.dim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )

                    HorizontalDivider(color = c.line)

                    variations.forEach { v ->
                        VariationRow(
                            v = v,
                            playing = playingIndex == v.index,
                            onPlay = {
                                playingIndex = v.index
                                onPlay(v)
                            },
                            onSave = { onSave(v) },
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Кнопка «ещё три»
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.bg)
                            .clickable {
                                playingIndex = -1
                                variations = onGenerate(text)
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconSparkle(c.accent, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "generate again",
                            color = c.accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (variations.isEmpty()) {
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            playingIndex = -1
                            variations = onGenerate(text.trim())
                        }
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconSparkle(
                            if (text.isNotBlank()) c.accent else c.dim,
                            size = 16.dp,
                        )
                        Text(
                            "Generate",
                            color = if (text.isNotBlank()) c.accent else c.dim,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = c.dim, fontFamily = FontFamily.Monospace)
                }
            }
        },
        dismissButton = {
            if (variations.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = c.dim, fontFamily = FontFamily.Monospace)
                }
            }
        },
    )
}

@Composable
private fun VariationRow(
    v: VariationItem,
    playing: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
) {
    val c = LocalAriaColors.current

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.bg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${v.label}  ·  T=${"%.2f".format(v.temperature)}",
                color = c.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Text(
                "${v.notes.size} notes",
                color = c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Play
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, c.line, RoundedCornerShape(8.dp))
                    .pointerInput(v.index) {
                        detectTapGestures(onTap = { onPlay() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (playing) IconPause(c.accent, size = 14.dp)
                    else IconPlay(c.accent, size = 14.dp)
                    Text(
                        if (playing) "playing" else "play",
                        color = c.accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }

            // Save
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, c.line, RoundedCornerShape(8.dp))
                    .pointerInput(v.index) {
                        detectTapGestures(onTap = { onSave() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconSave(c.accent, size = 14.dp)
                    Text(
                        "save",
                        color = c.accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}