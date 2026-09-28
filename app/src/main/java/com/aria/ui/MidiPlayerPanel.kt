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
import androidx.compose.ui.text.style.TextOverflow
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

    Column(modifier.background(c.bg).padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PLAYER", color = c.accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text(
                if (state.loop) "LOOP" else "ONCE",
                color = if (state.loop) c.accent else c.dim,
                fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            )
        }

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        // Источник
        Text(
            state.source.ifBlank { "—" },
            color = if (state.playing) c.text else c.dim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))

        // Метаданные
        if (state.totalNotes > 0 || state.noteMs > 0 || state.tempoBpm > 0) {
            Text(
                buildString {
                    if (state.tempoBpm > 0) append("${state.tempoBpm}BPM")
                    if (state.tempoChanges > 1) append("×${state.tempoChanges}")
                    if (state.noteMs > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${state.noteMs}ms")
                    }
                    if (state.totalNotes > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${state.totalNotes}n")
                    }
                    if (state.hasDrums) {
                        if (isNotEmpty()) append(" · ")
                        append("D:${state.drumCount}")
                    }
                },
                color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            )
        }

        Spacer(Modifier.height(6.dp))

        // Прогресс-бар
        val progress = if (state.totalMs > 0L)
            (state.currentMs.toFloat() / state.totalMs).coerceIn(0f, 1f)
        else if (state.totalNotes > 0)
            (state.currentIndex.toFloat() / state.totalNotes).coerceIn(0f, 1f)
        else 0f

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

        // Время + текущая нота
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                if (state.currentNote >= 0) noteName(state.currentNote) else "—",
                color = if (state.playing && !state.paused) c.accent else c.dim,
                fontFamily = FontFamily.Monospace, fontSize = 14.sp,
            )
            Text(
                "${formatMs(state.currentMs)}/${formatMs(state.totalMs)}",
                color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Кнопки
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AriaIconButton(
                onClick = onPlayPause,
                modifier = Modifier.weight(1f).height(36.dp),
            ) {
                if (state.playing && !state.paused) IconPause(c.accent, size = 16.dp)
                else IconPlay(c.accent, size = 16.dp)
            }
            AriaIconButton(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(36.dp),
            ) { IconStop(c.accent, size = 16.dp) }
            AriaIconButton(
                onClick = onToggleLoop,
                modifier = Modifier.weight(1f).height(36.dp),
            ) { IconLoop(if (state.loop) c.accent else c.dim, size = 16.dp) }
        }
    }
}

private fun noteName(midi: Int): String {
    val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
    val idx = ((midi % 12) + 12) % 12
    val octave = midi / 12 - 1
    return "${names[idx]}$octave"
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}