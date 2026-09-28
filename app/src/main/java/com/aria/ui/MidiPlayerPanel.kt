package com.aria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aria.midi.PlayerState

@Composable
fun MidiPlayerPanel(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onToggleLoop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAriaColors.current

    Column(
        modifier
            .background(c.bg)
            .padding(12.dp),
    ) {
        // Заголовок
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PLAYER",
                color = c.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Text(
                if (state.loop) "LOOP" else "ONCE",
                color = if (state.loop) c.accent else c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        // Прогресс-бар
        val progress = if (state.totalNotes > 0) {
            (state.currentIndex.toFloat() / state.totalNotes).coerceIn(0f, 1f)
        } else 0f

        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(c.accent),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Текущая нота + индекс
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                if (state.currentNote >= 0) noteName(state.currentNote) else "—",
                color = if (state.playing && !state.paused) c.accent else c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
            Text(
                "${state.currentIndex}/${state.totalNotes}",
                color = c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Кнопки
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AriaIconButton(
                onClick = onPlayPause,
                modifier = Modifier.weight(1f).height(36.dp),
            ) {
                if (state.playing && !state.paused) {
                    IconPause(c.accent, size = 16.dp)
                } else {
                    IconPlay(c.accent, size = 16.dp)
                }
            }
            AriaIconButton(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(36.dp),
            ) {
                IconStop(c.accent, size = 16.dp)
            }
            AriaIconButton(
                onClick = onToggleLoop,
                modifier = Modifier.weight(1f).height(36.dp),
            ) {
                IconLoop(
                    if (state.loop) c.accent else c.dim,
                    size = 16.dp,
                )
            }
        }
    }
}

/** MIDI 60 → C4, 69 → A4, 72 → C5 */
private fun noteName(midi: Int): String {
    val names = arrayOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B",
    )
    val idx = ((midi % 12) + 12) % 12
    val octave = midi / 12 - 1
    return "${names[idx]}$octave"
}