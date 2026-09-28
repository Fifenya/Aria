package com.aria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aria.midi.PlayerState

@Composable
fun NoteListPanel(
    playerState: PlayerState,
    modifier: Modifier = Modifier,
) {
    val c = LocalAriaColors.current
    val listState = rememberLazyListState()
    val notes = playerState.noteSequence

    // Автопрокрутка к текущей ноте, только если она ушла за границу видимости
    LaunchedEffect(playerState.currentIndex) {
        if (notes.isNotEmpty() && playerState.currentIndex in notes.indices) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val isVisible = visible.any { it.index == playerState.currentIndex }
            if (!isVisible) {
                listState.scrollToItem(playerState.currentIndex)
            }
        }
    }

    Column(
        modifier
            .background(c.bg)
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NOTES", color = c.accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            if (notes.isNotEmpty()) {
                Text(
                    "${playerState.currentIndex + 1}/${notes.size}",
                    color = c.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                )
            }
        }

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

        if (notes.isEmpty()) {
            Text(
                "—",
                color = c.dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(notes) { idx, note ->
                    val active = idx == playerState.currentIndex && playerState.playing
                    NoteRow(
                        index = idx + 1,
                        note = note,
                        active = active,
                        c = c,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    index: Int,
    note: Int,
    active: Boolean,
    c: AriaColors,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) c.panel else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            color = if (active) c.accent else c.dim,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.width(28.dp),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            noteName(note),
            color = if (active) c.accent else c.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun noteName(midi: Int): String {
    val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
    val idx = ((midi % 12) + 12) % 12
    val octave = midi / 12 - 1
    return "${names[idx]}$octave"
}