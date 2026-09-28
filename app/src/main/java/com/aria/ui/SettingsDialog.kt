package com.aria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GenSettings(
    val temperature: Float = 0.8f,
    val noteMs: Int = 350,
    val length: Int = 40,
)

@Composable
fun SettingsDialog(
    current: AriaColors,
    gen: GenSettings,
    onSelect: (AriaColors) -> Unit,
    onGenChange: (GenSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAriaColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        title = {
            Text(
                "Settings",
                color = c.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Theme", color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                AriaPresets.forEach { preset ->
                    val selected = preset.name == current.name
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) c.bg else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) c.accent else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect(preset) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(preset.accent),
                        )
                        Text(
                            preset.name,
                            color = c.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("Generation", color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)

                SliderRow(
                    label = "temperature",
                    value = "%.2f".format(gen.temperature),
                    sliderValue = gen.temperature,
                    range = 0.3f..2.0f,
                    onChange = { onGenChange(gen.copy(temperature = it)) },
                )

                SliderRow(
                    label = "note ms",
                    value = "${gen.noteMs}",
                    sliderValue = gen.noteMs.toFloat(),
                    range = 100f..1000f,
                    onChange = { onGenChange(gen.copy(noteMs = it.toInt())) },
                )

                SliderRow(
                    label = "length",
                    value = "${gen.length}",
                    sliderValue = gen.length.toFloat(),
                    range = 16f..128f,
                    onChange = { onGenChange(gen.copy(length = it.toInt())) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = c.accent, fontFamily = FontFamily.Monospace)
            }
        },
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val c = LocalAriaColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text(value, color = c.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Slider(
            value = sliderValue,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.line,
            ),
        )
    }
}