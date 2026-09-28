package com.aria.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun MidiLibraryPopup(
    visible: Boolean,
    imported: List<File>,
    created: List<File>,
    onSelectFile: (File) -> Unit,
    onDeleteFile: (File) -> Unit,
    onGenerateNew: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAriaColors.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
        modifier = modifier,
    ) {
        Surface(
            color = c.panel,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.line, RoundedCornerShape(14.dp)),
        ) {
            Column(Modifier.padding(12.dp)) {
                // Заголовок
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "MIDI LIBRARY",
                        color = c.accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    AriaIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        IconClose(c.accent, size = 14.dp)
                    }
                }

                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (created.isEmpty() && imported.isEmpty()) {
                        Text(
                            "empty — nothing yet",
                            color = c.dim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }

                    if (created.isNotEmpty()) {
                        SectionLabel("CREATED", c)
                        created.forEach { f ->
                            FileRow(f, c, onSelectFile, onDeleteFile)
                        }
                    }

                    if (created.isNotEmpty() && imported.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                    }

                    if (imported.isNotEmpty()) {
                        SectionLabel("IMPORTED", c)
                        imported.forEach { f ->
                            FileRow(f, c, onSelectFile, onDeleteFile)
                        }
                    }
                }

                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AriaIconButton(
                        onClick = onGenerateNew,
                        modifier = Modifier.weight(1f).height(36.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            IconNote(c.accent, size = 14.dp)
                            Text(
                                "New",
                                color = c.accent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    AriaIconButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f).height(36.dp),
                    ) {
                        Text(
                            "Refresh",
                            color = c.accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, c: AriaColors) {
    Text(
        text,
        color = c.dim,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun FileRow(
    file: File,
    c: AriaColors,
    onSelect: (File) -> Unit,
    onDelete: (File) -> Unit,
) {
    val bytes = file.length()
    val sizeText = if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.bg)
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { onSelect(file) },
                    onLongPress = { onDelete(file) },
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            file.name,
            color = c.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            sizeText,
            color = c.dim,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
    }
}