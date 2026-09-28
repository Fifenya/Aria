package com.aria.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aria.midi.AudioExporter
import com.aria.midi.MidiPlayer
import com.aria.midi.TimedNote
import com.aria.stats.StatsCollector
import com.aria.train.ModelSummary
import com.aria.train.TrainState
import com.aria.train.TrainerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import java.util.Locale

/** Единый запрос на воспроизведение для сгенерированных мелодий. */
data class PlaybackRequest(
    val notes: List<Int>,
    val noteMs: Int,
    val source: String,
    val tempoBpm: Int,
)

class MainActivity : ComponentActivity() {

    private lateinit var statsCollector: StatsCollector
    private lateinit var trainer: TrainerService
    private lateinit var midiPlayer: MidiPlayer
    private val activityScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentTrainState: TrainState = TrainState()

    private val requestLegacyStorage = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ничего */ }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ничего */ }

    private val importMidi = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val targetDir = File(trainer.midiFolderPath()).apply { mkdirs() }
            val name = "user_${System.currentTimeMillis()}.mid"
            val target = File(targetDir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            trainer.rescanMidiFolder()
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statsCollector = StatsCollector(applicationContext)

        ensureStoragePermission()
        ensureNotificationsPermission()

        val external = resolveExternalRoot()
        val internal = filesDir
        trainer = TrainerService(external, internal)
        midiPlayer = MidiPlayer()

        val prefs = getSharedPreferences("aria", Context.MODE_PRIVATE)

        setContent {
            var themeIdx by remember { mutableStateOf(prefs.getInt("theme", 0)) }
            val theme = AriaPresets.getOrElse(themeIdx) { AriaGreen }

            var gen by remember {
                mutableStateOf(
                    GenSettings(
                        temperature = prefs.getFloat("temp", 0.8f),
                        noteMs = prefs.getInt("noteMs", 350),
                        length = prefs.getInt("length", 40),
                    )
                )
            }

            var showSettings by remember { mutableStateOf(false) }
            var showFaq by remember { mutableStateOf(false) }
            var showLibrary by remember { mutableStateOf(false) }
            var showPrompt by remember { mutableStateOf(false) }
            var lastPlayback by remember { mutableStateOf<PlaybackRequest?>(null) }
            var lastTimedNotes by remember { mutableStateOf<List<TimedNote>>(emptyList()) }

            var importedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
            var createdFiles by remember { mutableStateOf<List<File>>(emptyList()) }

            var modelSummary by remember { mutableStateOf(trainer.summary()) }

            // Тикаем 1 раз в секунду, чтобы таймер обучения в UI обновлялся
            var elapsedTick by remember { mutableStateOf(0L) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    elapsedTick = System.currentTimeMillis()
                }
            }

            fun refreshLibrary() {
                importedFiles = trainer.listImportedMidi()
                createdFiles = trainer.listCreatedMidi()
                modelSummary = trainer.summary()
            }

            fun playFrom(req: PlaybackRequest) {
                lastPlayback = req
                lastTimedNotes = req.notes.mapIndexed { i, n ->
                    TimedNote(
                        note = n,
                        startMs = i.toLong() * req.noteMs,
                        durationMs = req.noteMs,
                        velocity = 80,
                        channel = 0,
                    )
                }
                midiPlayer.play(
                    notes = req.notes,
                    noteMs = req.noteMs,
                    volume = 0.35f,
                    source = req.source,
                    tempoBpm = req.tempoBpm,
                )
            }

            val stats by statsCollector.stats.collectAsStateWithLifecycle()
            val trainState by trainer.state.collectAsStateWithLifecycle()
            val lossHistory by trainer.lossHistory.collectAsStateWithLifecycle()
            val playerState by midiPlayer.state.collectAsStateWithLifecycle()
            val logLines = remember { mutableStateListOf<String>() }

            SideEffect { currentTrainState = trainState }

            LaunchedEffect(Unit) {
                trainer.log.collect { line ->
                    logLines.add(line)
                    if (logLines.size > 200) logLines.removeAt(0)
                    if (line.contains("imported ") ||
                        line.contains("mask updated") ||
                        line.contains("restored")
                    ) {
                        modelSummary = trainer.summary()
                    }
                }
            }

            LaunchedEffect(Unit) {
                statsCollector.start(this)
                val perm = if (hasStoragePermission()) "granted" else "denied"
                logLines.add("[${now()}] permission: $perm")
                logLines.add("[${now()}] ckpt:   ${trainer.modelFilePath()}")
                logLines.add("[${now()}] mirror: ${trainer.mirrorFilePath()}")
                logLines.add("[${now()}] midi:   ${trainer.midiFolderPath()}")
                logLines.add(
                    "[${now()}] model:  ${trainer.modelInfo.label()} " +
                            "(${trainer.modelInfo.params} params)"
                )
                if (logLines.size > 200) logLines.removeAt(0)
            }

            LaunchedEffect(trainState.running) {
                if (trainState.running) return@LaunchedEffect
                while (true) {
                    delay(5000)
                    logLines.add("[${now()}] Aria idle…")
                    if (logLines.size > 200) logLines.removeAt(0)
                }
            }

            LaunchedEffect(showLibrary) { if (showLibrary) refreshLibrary() }

            CompositionLocalProvider(LocalAriaColors provides theme) {
                AriaScreen(
                    stats = stats,
                    trainState = trainState,
                    lossHistory = lossHistory,
                    elapsedMs = trainer.trainingElapsedMs(),
                    elapsedTick = elapsedTick,
                    playerState = playerState,
                    summary = modelSummary,
                    logLines = logLines,
                    showLibrary = showLibrary,
                    importedFiles = importedFiles,
                    createdFiles = createdFiles,

                    onOpenSettings = { showSettings = true },
                    onOpenFaq = { showFaq = true },
                    onOpenPrompt = { showPrompt = true },

                    onToggleTrain = {
                        if (trainState.running) trainer.stopTraining()
                        else trainer.start(activityScope)
                    },
                    onImportMidi = { importMidi.launch(arrayOf("*/*")) },

                    onSave = {
                        if (!hasStoragePermission()) {
                            logLines.add("[${now()}] storage permission required — opening settings")
                            if (logLines.size > 200) logLines.removeAt(0)
                            requestStoragePermission()
                        } else {
                            val saved = trainer.saveNow()
                            if (!saved) {
                                logLines.add("[${now()}] save: no changes since last checkpoint")
                                if (logLines.size > 200) logLines.removeAt(0)
                            }
                        }
                    },

                    onToggleLibrary = { showLibrary = !showLibrary },
                    onLibraryDismiss = { showLibrary = false },
                    onLibraryRefresh = { refreshLibrary() },

                    onExport = {
                        val notes = lastTimedNotes
                        if (notes.isEmpty()) {
                            logLines.add("[${now()}] export: nothing to export")
                            if (logLines.size > 200) logLines.removeAt(0)
                        } else {
                            activityScope.launch(Dispatchers.IO) {
                                try {
                                    val outDir = File(trainer.outputsDir(), "audio")
                                        .apply { mkdirs() }
                                    val result = AudioExporter.export(
                                        notes = notes,
                                        outputDir = outDir,
                                        baseName = "aria_export",
                                        withReverb = true,
                                    )
                                    withContext(Dispatchers.Main) {
                                        logLines.add(
                                            "[${now()}] exported ${result.durationMs / 1000}s  " +
                                                    "wav=${result.wav?.name ?: "—"}  " +
                                                    "m4a=${result.m4a?.name ?: "—"}"
                                        )
                                        if (logLines.size > 200) logLines.removeAt(0)
                                    }
                                    refreshLibrary()
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        logLines.add("[${now()}] export failed: ${e.message}")
                                        if (logLines.size > 200) logLines.removeAt(0)
                                    }
                                }
                            }
                        }
                    },

                    onLibrarySelectFile = { file ->
                        val parsed = trainer.parseMidiFile(file)
                        if (parsed != null && parsed.timedNotes.isNotEmpty()) {
                            lastTimedNotes = parsed.timedNotes
                            lastPlayback = null
                            midiPlayer.playTimed(
                                notes = parsed.timedNotes,
                                volume = 0.35f,
                                source = file.name,
                                tempoBpm = parsed.tempoBpm,
                                singleNoteMs = 0,
                                hasDrums = parsed.drumCount > 0,
                                drumCount = parsed.drumCount,
                                tempoChanges = parsed.tempoChanges,
                            )
                            logLines.add(
                                "[${now()}] ▶ ${file.name}  ${parsed.timedNotes.size}n  " +
                                        "${parsed.tempoBpm}BPM  " +
                                        "len=${parsed.totalMs / 1000}s  " +
                                        "tempo×${parsed.tempoChanges}  " +
                                        "drums=${parsed.drumCount}"
                            )
                            if (logLines.size > 200) logLines.removeAt(0)
                        } else {
                            logLines.add("[${now()}] cannot play ${file.name}")
                            if (logLines.size > 200) logLines.removeAt(0)
                        }
                        showLibrary = false
                    },

                    onLibraryDeleteFile = { file ->
                        trainer.deleteMidiFile(file)
                        refreshLibrary()
                    },

                    onLibraryGenerateNew = {
                        val notes = trainer.generate(
                            length = gen.length,
                            temperature = gen.temperature,
                        )
                        val req = PlaybackRequest(
                            notes = notes,
                            noteMs = gen.noteMs,
                            source = "aria · generated",
                            tempoBpm = 120,
                        )
                        playFrom(req)
                        trainer.saveMelodyAsMidi(
                            notes = notes, tempoBpm = 120, noteMs = gen.noteMs,
                        )
                        logLines.add(
                            "[${now()}] ▶ generated ${notes.size}n  " +
                                    "(T=${fmt2(gen.temperature)}, ${gen.noteMs}ms)"
                        )
                        if (logLines.size > 200) logLines.removeAt(0)
                        refreshLibrary()
                    },

                    onPlayerPlayPause = {
                        if (playerState.playing) {
                            midiPlayer.togglePause()
                        } else if (playerState.totalMs > 0) {
                            midiPlayer.resume()
                        } else lastPlayback?.let { playFrom(it) }
                    },
                    onPlayerStop = { midiPlayer.stop() },
                    onPlayerToggleLoop = { midiPlayer.setLoop(!playerState.loop) },
                )

                if (showPrompt) {
                    PromptDialog(
                        onGenerate = { text ->
                            val tag = trainer.sanitizeTag(text)
                            val results = trainer.generateVariations(text, count = 3)
                            results.mapIndexed { i, r ->
                                VariationItem(
                                    index = i, notes = r.notes,
                                    temperature = r.params.temperature,
                                    noteMs = r.params.noteMs,
                                    label = "v${i + 1}", tag = tag,
                                )
                            }
                        },
                        onPlay = { v ->
                            val req = PlaybackRequest(
                                notes = v.notes, noteMs = v.noteMs,
                                source = "aria · ${v.label}", tempoBpm = 120,
                            )
                            playFrom(req)
                            logLines.add(
                                "[${now()}] ▶ ${v.label}  " +
                                        "T=${fmt2(v.temperature)}  ${v.notes.size}n  " +
                                        "${v.noteMs}ms"
                            )
                            if (logLines.size > 200) logLines.removeAt(0)
                        },
                        onSave = { v ->
                            trainer.saveMelodyAsMidi(
                                notes = v.notes, tempoBpm = 120,
                                noteMs = v.noteMs, tag = v.tag,
                            )
                            refreshLibrary()
                        },
                        onDismiss = { showPrompt = false },
                    )
                }

                if (showSettings) {
                    SettingsDialog(
                        current = theme,
                        gen = gen,
                        onSelect = { newTheme ->
                            themeIdx = AriaPresets.indexOf(newTheme)
                            prefs.edit().putInt("theme", themeIdx).apply()
                        },
                        onGenChange = { newGen ->
                            gen = newGen
                            prefs.edit()
                                .putFloat("temp", newGen.temperature)
                                .putInt("noteMs", newGen.noteMs)
                                .putInt("length", newGen.length)
                                .apply()
                        },
                        onDismiss = { showSettings = false },
                    )
                }

                FaqOverlay(visible = showFaq, onDismiss = { showFaq = false })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationHelper.clear(this)
        try { trainer.rescanMidiFolder() } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        NotificationHelper.showStatus(this, currentTrainState, trainer.modelInfo.label())
    }

    override fun onDestroy() {
        statsCollector.stop()
        trainer.pause()
        midiPlayer.stop()
        activityScope.cancel()
        super.onDestroy()
    }

    private fun hasStoragePermission(): Boolean {
        val flagOK = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (!flagOK) return false
        return try {
            val target = File(
                Environment.getExternalStorageDirectory(),
                "Projects/Aria models"
            )
            target.mkdirs()
            val probe = File(target, ".aria_probe")
            probe.writeText("ok"); probe.delete()
            true
        } catch (_: Exception) { false }
    }

    private fun ensureStoragePermission() {
        if (!hasStoragePermission()) requestStoragePermission()
    }

    private fun ensureNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {}
            }
        } else {
            requestLegacyStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun resolveExternalRoot(): File {
        val external = File(
            Environment.getExternalStorageDirectory(),
            "Projects/Aria models"
        )
        return try {
            if (!external.exists()) external.mkdirs()
            val probe = File(external, ".aria_write_test")
            probe.writeText("ok"); probe.delete()
            external
        } catch (_: Exception) {
            File(filesDir, "Aria models external fallback").apply { mkdirs() }
        }
    }

    private fun now(): String = LocalTime.now().withNano(0).toString()
    private fun fmt2(v: Float): String = String.format(Locale.US, "%.2f", v)
}

@Composable
fun AriaScreen(
    stats: com.aria.stats.AriaStats,
    trainState: TrainState,
    lossHistory: List<Float>,
    elapsedMs: Long,
    elapsedTick: Long,
    playerState: com.aria.midi.PlayerState,
    summary: ModelSummary,
    logLines: List<String>,
    showLibrary: Boolean,
    importedFiles: List<File>,
    createdFiles: List<File>,

    onOpenSettings: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenPrompt: () -> Unit,

    onToggleTrain: () -> Unit,
    onImportMidi: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,

    onToggleLibrary: () -> Unit,
    onLibraryDismiss: () -> Unit,
    onLibraryRefresh: () -> Unit,
    onLibrarySelectFile: (File) -> Unit,
    onLibraryDeleteFile: (File) -> Unit,
    onLibraryGenerateNew: () -> Unit,

    onPlayerPlayPause: () -> Unit,
    onPlayerStop: () -> Unit,
    onPlayerToggleLoop: () -> Unit,
) {
    val c = LocalAriaColors.current

    val trainProgress by animateFloatAsState(
        targetValue = if (trainState.running) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "trainProgress",
    )

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(c.bg)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                // Шапка
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconHarp(c.accent, size = 22.dp)
                        Text(
                            "ARIA  v0.9.0",
                            color = c.accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AriaIconButton(
                            onClick = onOpenFaq,
                            modifier = Modifier.size(40.dp),
                        ) { IconFaq(c.accent, size = 20.dp) }
                        AriaIconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(40.dp),
                        ) { IconSliders(c.accent, size = 20.dp) }
                    }
                }

                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))

                // Лог
                val scrollState = rememberScrollState()
                LaunchedEffect(logLines.size) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Column(Modifier.weight(1f).verticalScroll(scrollState)) {
                    logLines.forEach {
                        Text(
                            it,
                            color = c.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }

                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 6.dp))

                // График обучения
                // (elapsedTick используется как ключ, чтобы перекомпоновка раз в секунду)
                val liveElapsed = if (trainState.running) elapsedMs + 0L else elapsedMs
                LossChart(
                    history = lossHistory,
                    elapsedMs = liveElapsed,
                    bestLoss = trainState.bestLoss,
                    running = trainState.running,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 6.dp))

                // Кнопки
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AriaIconButton(
                        onClick = onToggleTrain,
                        onLongClick = onImportMidi,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { IconPlayStop(progress = trainProgress, color = c.accent) }

                    AriaIconButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { IconSave(c.accent) }

                    AriaIconButton(
                        onClick = onToggleLibrary,
                        onLongClick = onOpenPrompt,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { IconNote(c.accent) }
                }
            }

            Column(Modifier.width(150.dp).fillMaxHeight()) {
                StatsPanel(
                    stats = stats,
                    trainState = trainState,
                    summary = summary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f),
                )

                HorizontalDivider(color = c.line)

                NoteListPanel(
                    playerState = playerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                HorizontalDivider(color = c.line)

                MidiPlayerPanel(
                    state = playerState,
                    onPlayPause = onPlayerPlayPause,
                    onStop = onPlayerStop,
                    onToggleLoop = onPlayerToggleLoop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        MidiLibraryPopup(
            visible = showLibrary,
            imported = importedFiles,
            created = createdFiles,
            onSelectFile = onLibrarySelectFile,
            onDeleteFile = onLibraryDeleteFile,
            onGenerateNew = onLibraryGenerateNew,
            onExport = onExport,
            onRefresh = onLibraryRefresh,
            onDismiss = onLibraryDismiss,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 170.dp, bottom = 84.dp),
        )
    }
}