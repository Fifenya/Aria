package com.aria.train

import com.aria.data.Melodies
import com.aria.data.PromptParser
import com.aria.midi.MidiReader
import com.aria.midi.MidiWriter
import com.aria.nn.AriaNet
import com.aria.nn.Checkpoint
import com.aria.nn.ModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TrainState(
    val epoch: Long = 0,
    val loss: Float = 0f,
    val bestLoss: Float = Float.MAX_VALUE,
    val running: Boolean = false,
    val step: Long = 0,
)

data class ModelSummary(
    val label: String,
    val params: Long,
    val vocab: Int,
    val alive: Int,
    val embDim: Int,
    val hidden: Int,
    val melodies: Int,
    val tokenCount: Long,
)

class TrainerService(
    private val storageDir: File,
    private val internalRoot: File,
) {

    companion object {
        const val MODEL_VERSION = 2
        const val EMB_DIM = 16
        const val HIDDEN = 32
        const val LOSS_HISTORY_SIZE = 200
    }

    // ---------- Папки ----------
    private val midiFolder = File(storageDir, "midi").apply { mkdirs() }
    private val midiTracker = File(midiFolder, ".imported")

    // Автоимпорт ДО создания сети
    private val initialMidiLog: List<String> =
        Melodies.loadAllFromFolder(midiFolder, midiTracker)

    // ---------- Сеть ----------
    private val net = AriaNet(
        vocab = Melodies.VOCAB_SIZE,
        embDim = EMB_DIM,
        hidden = HIDDEN,
        lr = 0.001f,
    )

    @Volatile
    private var mask: BooleanArray = Melodies.buildMask()

    val modelInfo: ModelInfo = ModelInfo(
        name = "aria",
        version = MODEL_VERSION,
        params = net.paramCount(),
    )

    // ---------- Чекпойнты ----------
    private val internalDir = File(internalRoot, "Aria models").apply { mkdirs() }
    private val ckptFile = File(internalDir, modelInfo.fileName())
    private val ckptBackup = File(internalDir, modelInfo.fileName() + ".bak")

    private val mirrorDir = storageDir.apply { mkdirs() }
    private val mirrorFile = File(mirrorDir, modelInfo.fileName())

    // ---------- Лог ----------
    private val logFile: File = run {
        val stamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        File(mirrorDir, "log_$stamp.txt").also {
            try { it.writeText("# aria  ${modelInfo.label()}  ($stamp)\n") } catch (_: Exception) {}
        }
    }

    private fun writeFile(line: String) {
        try { logFile.appendText(line + "\n") } catch (_: Exception) {}
    }

    // ---------- Состояние ----------
    private val _state = MutableStateFlow(TrainState())
    val state: StateFlow<TrainState> = _state.asStateFlow()

    /** История loss для графика. Скользящее окно LOSS_HISTORY_SIZE точек. */
    private val _lossHistory = MutableStateFlow<List<Float>>(emptyList())
    val lossHistory: StateFlow<List<Float>> = _lossHistory.asStateFlow()

    private val _log = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val log: SharedFlow<String> = _log.asSharedFlow()

    private val _samples = MutableStateFlow<List<Int>>(emptyList())
    val samples: StateFlow<List<Int>> = _samples.asStateFlow()

    private var job: Job? = null
    private var lastCkptEpoch: Long = -1L

    @Volatile
    private var stopRequested = false

    private var runId: Long = 0L

    @Volatile
    private var trainingStartedAtMs: Long = 0L

    @Volatile
    private var trainingStoppedAtMs: Long = 0L

    /** Сколько миллисекунд идёт текущий прогон. После остановки — замирает. */
    fun trainingElapsedMs(): Long {
        val start = trainingStartedAtMs
        if (start <= 0L) return 0L
        val stopped = trainingStoppedAtMs
        val end = if (stopped > 0L) stopped else System.currentTimeMillis()
        return (end - start).coerceAtLeast(0L)
    }

    // ---------- INIT ----------
    init {
        writeFile("[${now()}] model:  ${modelInfo.label()}  (${modelInfo.params} params)")
        writeFile("[${now()}] ckpt:   ${ckptFile.absolutePath}")
        writeFile("[${now()}] mirror: ${mirrorFile.absolutePath}")
        writeFile("[${now()}] midi:   ${midiFolder.absolutePath}")
        writeFile("[${now()}] log:    ${logFile.absolutePath}")

        _log.tryEmit("[${now()}] ${modelInfo.label()}  (${modelInfo.params} params)")
        _log.tryEmit("[${now()}] midi folder: ${midiFolder.absolutePath}")

        initialMidiLog.forEach { raw ->
            val line = "[${now()}] $raw"
            writeFile(line); _log.tryEmit(line)
        }

        mask = Melodies.buildMask()
        val alive = mask.count { it }
        writeFile("[${now()}] mask: $alive notes alive out of ${mask.size}")
        _log.tryEmit("[${now()}] vocab=${mask.size}  alive=$alive  melodies=${Melodies.raw.size}")

        val mirrorOK = try {
            val probe = File(mirrorDir, ".aria_probe")
            probe.writeText("ok"); probe.delete(); true
        } catch (_: Exception) { false }

        if (!mirrorOK) {
            val m = "[${now()}] mirror not writable — internal only"
            writeFile(m); _log.tryEmit(m)
        }

        if (!ckptFile.exists() && mirrorFile.exists()) {
            try {
                mirrorFile.copyTo(ckptFile, overwrite = true)
                writeFile("[${now()}] migrated checkpoint from mirror")
            } catch (_: Exception) {}
        }

        if (ckptFile.exists()) {
            try {
                val meta = Checkpoint.load(ckptFile, net)
                val safeBest = if (meta.bestLoss.isNaN() ||
                    meta.bestLoss.isInfinite() || meta.bestLoss <= 0f
                ) Float.MAX_VALUE else meta.bestLoss

                _state.value = TrainState(
                    epoch = meta.epoch, loss = safeBest, bestLoss = safeBest,
                    running = false, step = meta.step,
                )
                lastCkptEpoch = meta.epoch
                val line = "restored  epoch=${meta.epoch} best=${fmt4(safeBest)}"
                writeFile("[${now()}] $line")
                _log.tryEmit("[${now()}] $line")
            } catch (e: Exception) {
                try {
                    val broken = File(
                        ckptFile.parentFile,
                        ckptFile.name + ".incompatible_${System.currentTimeMillis()}"
                    )
                    ckptFile.renameTo(broken)
                    val m = "[${now()}] old checkpoint incompatible → reset"
                    writeFile(m); _log.tryEmit(m)
                } catch (_: Exception) {
                    val m = "[${now()}] load failed: ${e.message}"
                    writeFile(m); _log.tryEmit(m)
                }
            }
        }
    }

    // ---------- SUMMARY ----------

    fun summary(): ModelSummary {
        var tokens = 0L
        Melodies.raw.forEach { m -> tokens += m.size }
        return ModelSummary(
            label = modelInfo.label(),
            params = modelInfo.params,
            vocab = Melodies.VOCAB_SIZE,
            alive = mask.count { it },
            embDim = EMB_DIM,
            hidden = HIDDEN,
            melodies = Melodies.raw.size,
            tokenCount = tokens,
        )
    }

    // ---------- RESCAN MIDI ----------

    fun rescanMidiFolder(): Int {
        val lines = Melodies.loadAllFromFolder(midiFolder, midiTracker)
        var newFiles = 0
        lines.forEach { raw ->
            val line = "[${now()}] $raw"
            writeFile(line); _log.tryEmit(line)
            if (raw.startsWith("imported ")) newFiles++
        }

        if (newFiles > 0) {
            val oldAlive = mask.count { it }
            val newMask = Melodies.buildMask()
            val newAlive = newMask.count { it }
            mask = newMask
            val m = "[${now()}] mask updated: alive $oldAlive→$newAlive, " +
                    "melodies=${Melodies.raw.size}"
            writeFile(m); _log.tryEmit(m)
        }
        return newFiles
    }

    // ---------- TRAIN ----------

    fun start(scope: CoroutineScope, epochsPerTick: Int = 4, tickDelayMs: Long = 16L) {
        if (job?.isActive == true) return
        stopRequested = false
        val myId = ++runId
        trainingStartedAtMs = System.currentTimeMillis()
        trainingStoppedAtMs = 0L

        job = scope.launch(Dispatchers.Default) {
            try {
                _state.value = _state.value.copy(running = true)
                val alive = mask.count { it }
                val startLine = "[${now()}] training started  " +
                        "(vocab=${mask.size}, alive=$alive, epoch=${_state.value.epoch})"
                writeFile(startLine); _log.tryEmit(startLine)

                var localEpoch = _state.value.epoch
                var localBest = _state.value.bestLoss
                var consecutiveNaN = 0
                val nanLimit = 200

                while (isActive && !stopRequested && runId == myId) {
                    var sumLoss = 0f
                    var nanCount = 0
                    var broke = false

                    outer@ for (rep in 0 until epochsPerTick) {
                        for (melody in Melodies.raw) {
                            if (stopRequested || !isActive || runId != myId) {
                                broke = true; break@outer
                            }
                            try {
                                val enc = Melodies.encode(melody)
                                if (enc.size < 2) continue
                                val l = net.trainStep(enc, mask, clipNorm = 1f)
                                if (l.isNaN() || l.isInfinite()) nanCount++
                                sumLoss += l
                            } catch (e: Exception) {
                                val m = "[${now()}] trainStep failed: " +
                                        "${e.javaClass.simpleName}: ${e.message}"
                                writeFile(m); _log.tryEmit(m)
                                broke = true; break@outer
                            }
                        }
                        localEpoch++
                    }
                    if (broke) break

                    val avg = sumLoss / (epochsPerTick * Melodies.raw.size)

                    if (avg.isNaN() || avg.isInfinite()) {
                        consecutiveNaN++
                        if (consecutiveNaN >= nanLimit) {
                            val m = "[${now()}] DIVERGED: $consecutiveNaN NaN ticks — " +
                                    "rolling back to last good checkpoint"
                            writeFile(m); _log.tryEmit(m)
                            try {
                                if (ckptBackup.exists()) {
                                    Checkpoint.load(ckptBackup, net)
                                    localEpoch = lastCkptEpoch
                                    _state.value = TrainState(
                                        epoch = localEpoch, loss = 0f,
                                        bestLoss = localBest, running = true,
                                        step = net.step,
                                    )
                                }
                            } catch (_: Exception) {}
                            consecutiveNaN = 0
                        }
                    } else consecutiveNaN = 0

                    if (!avg.isNaN() && !avg.isInfinite() && avg < localBest) {
                        localBest = avg
                    }

                    // Обновляем историю loss только на адекватных значениях
                    if (!avg.isNaN() && !avg.isInfinite()) {
                        val newHist = _lossHistory.value.toMutableList()
                        newHist.add(avg)
                        if (newHist.size > LOSS_HISTORY_SIZE) {
                            newHist.removeAt(0)
                        }
                        _lossHistory.value = newHist
                    }

                    _state.value = TrainState(
                        epoch = localEpoch,
                        loss = if (avg.isNaN() || avg.isInfinite()) _state.value.loss else avg,
                        bestLoss = localBest, running = true, step = net.step,
                    )

                    if (localEpoch % 20L == 0L) {
                        val lossStr = if (avg.isNaN() || avg.isInfinite()) "nan" else fmt4(avg)
                        val line = "[${now()}] epoch=$localEpoch loss=$lossStr"
                        writeFile(line)
                        if (localEpoch % 100L == 0L) _log.tryEmit(line)
                    }

                    if (localEpoch % 200L == 0L) {
                        val seed = if (mask[60]) 60
                        else mask.indexOfFirst { it }.coerceAtLeast(0)
                        val sampleIdx = net.sample(seed, 32, 0.8f, mask)
                        val notes = sampleIdx.toList()
                        _samples.value = notes
                        val line = "[${now()}] sample: $notes"
                        writeFile(line)
                        if (localEpoch % 500L == 0L) _log.tryEmit(line)
                    }

                    if (localEpoch - lastCkptEpoch >= 500L) {
                        saveCheckpoint(localEpoch, localBest)
                    }

                    delay(tickDelayMs)
                }
            } catch (e: CancellationException) {
                // Нормальная остановка
            } catch (e: Exception) {
                val m = "[${now()}] training crashed: " +
                        "${e.javaClass.simpleName}: ${e.message}"
                writeFile(m); _log.tryEmit(m)
            } finally {
                if (runId == myId) {
                    _state.value = _state.value.copy(running = false)
                    stopRequested = false
                    job = null
                }
            }
        }
    }

    fun stopTraining() {
        stopRequested = true
        runId++
        val j = this.job
        this.job = null
        j?.cancel()
        _state.value = _state.value.copy(running = false)
        trainingStoppedAtMs = System.currentTimeMillis()

        val epochToSave = _state.value.epoch
        val bestToSave = _state.value.bestLoss
        if (epochToSave > 0) {
            Thread {
                try { saveCheckpoint(epochToSave, bestToSave) } catch (_: Exception) {}
            }.also { it.isDaemon = true; it.start() }
        }

        val line = "[${now()}] training stopped"
        writeFile(line); _log.tryEmit(line)
    }

    fun pause() = stopTraining()

    // ---------- SAVE ----------

    fun saveNow(): Boolean {
        val epoch = _state.value.epoch
        val best = _state.value.bestLoss

        if (epoch <= 0) {
            val msg = "[${now()}] nothing to save yet"
            writeFile(msg); _log.tryEmit(msg)
            return false
        }
        if (epoch == lastCkptEpoch && ckptFile.exists()) {
            val msg = "[${now()}] model unchanged (epoch=$epoch), skipping save"
            writeFile(msg); _log.tryEmit(msg)
            return false
        }
        saveCheckpoint(epoch, best)
        return true
    }

    private fun saveCheckpoint(epoch: Long, best: Float) {
        if (best.isNaN() || best.isInfinite()) {
            val m = "[${now()}] refuse to save: bestLoss is NaN/Inf"
            writeFile(m); _log.tryEmit(m)
            return
        }
        val safeBest = if (best <= 0f) Float.MAX_VALUE else best

        try {
            if (ckptFile.exists()) {
                ckptBackup.delete()
                ckptFile.copyTo(ckptBackup, overwrite = true)
            }
            Checkpoint.save(ckptFile, net, modelInfo, epoch, safeBest)
            lastCkptEpoch = epoch
            val line = "[${now()}] saved  epoch=$epoch  (internal)"
            writeFile(line); _log.tryEmit(line)
        } catch (e: Exception) {
            val m = "[${now()}] internal save failed: ${e.message}"
            writeFile(m); _log.tryEmit(m)
            return
        }

        try {
            mirrorDir.mkdirs()
            ckptFile.copyTo(mirrorFile, overwrite = true)
            writeFile("[${now()}] mirrored → ${mirrorFile.absolutePath}")
        } catch (e: Exception) {
            val m = "[${now()}] mirror failed: ${e.message}"
            writeFile(m); _log.tryEmit(m)
        }
    }

    // ---------- ГЕНЕРАЦИЯ ----------

    fun generate(length: Int = 40, temperature: Float = 0.8f): List<Int> {
        val seed = if (mask[60]) 60 else mask.indexOfFirst { it }.coerceAtLeast(0)
        return net.sample(seed, length, temperature, mask).toList()
    }

    data class PromptResult(
        val notes: List<Int>,
        val params: PromptParser.GenParams,
    )

    fun generateFromPrompt(text: String): PromptResult {
        val params = PromptParser.parse(text)
        val seed = if (params.seedNote in 0 until mask.size && mask[params.seedNote]) {
            params.seedNote
        } else if (mask[60]) 60 else mask.indexOfFirst { it }.coerceAtLeast(0)

        val idx = net.sample(seed, params.length, params.temperature, mask)
        val notes = idx.toList()

        val line = "[${now()}] prompt=\"$text\"  mood=${params.mood}  " +
                "T=${fmt2(params.temperature)}  len=${params.length}  " +
                "noteMs=${params.noteMs}  seed=$seed"
        writeFile(line); _log.tryEmit(line)

        return PromptResult(notes, params)
    }

    fun generateVariations(text: String, count: Int = 3): List<PromptResult> {
        val base = PromptParser.parse(text)
        val results = mutableListOf<PromptResult>()

        for (i in 0 until count) {
            val delta = when (i) { 0 -> -0.2f; 1 -> 0f; else -> 0.2f }
            val t = (base.temperature + delta).coerceIn(0.3f, 2.0f)
            val seedNote = (base.seedNote + i * 3).coerceIn(0, 127)
            val seed = if (seedNote in 0 until mask.size && mask[seedNote]) {
                seedNote
            } else if (mask[60]) 60 else mask.indexOfFirst { it }.coerceAtLeast(0)

            val idx = net.sample(seed, base.length, t, mask)
            results.add(
                PromptResult(
                    notes = idx.toList(),
                    params = base.copy(temperature = t, seedNote = seed),
                )
            )
        }

        val line = "[${now()}] variations: $count for \"$text\"  (mood=${base.mood})"
        writeFile(line); _log.tryEmit(line)
        return results
    }

    fun saveMelodyAsMidi(
        notes: List<Int>,
        tempoBpm: Int = 120,
        noteMs: Int = 0,
        tag: String = "",
    ): File? {
        if (notes.isEmpty()) return null
        return try {
            val outputsDir = File(mirrorDir, "outputs").apply { mkdirs() }
            val stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val prefix = if (tag.isBlank()) "aria" else "aria_$tag"
            val out = File(outputsDir, "${prefix}_$stamp.mid")
            MidiWriter.write(out, notes, tempoBpm, noteMs)

            val line = "[${now()}] midi exported → ${out.name}  " +
                    "notes=${notes.size}  noteMs=$noteMs"
            writeFile(line); _log.tryEmit(line)
            out
        } catch (e: Exception) {
            val m = "[${now()}] midi export failed: ${e.message}"
            writeFile(m); _log.tryEmit(m)
            null
        }
    }

    fun sanitizeTag(input: String, maxLen: Int = 24): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
        )
        val sb = StringBuilder()
        for (ch in input.lowercase(Locale.ROOT)) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                map.containsKey(ch) -> sb.append(map[ch])
                else -> sb.append('_')
            }
        }
        return sb.toString()
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(maxLen)
            .ifEmpty { "aria" }
    }

    // ---------- БИБЛИОТЕКА MIDI ----------

    fun listImportedMidi(): List<File> {
        return midiFolder.listFiles { f ->
            f.isFile && !f.name.startsWith(".")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun listCreatedMidi(): List<File> {
        val dir = outputsDir()
        return dir.listFiles { f ->
            f.isFile && f.name.endsWith(".mid", true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteMidiFile(file: File): Boolean {
        return try {
            if (file.parentFile?.absolutePath == midiFolder.absolutePath) {
                if (midiTracker.exists()) {
                    val lines = midiTracker.readLines()
                        .filter { !it.startsWith("${file.name}\t") }
                    try { midiTracker.writeText(lines.joinToString("\n")) } catch (_: Exception) {}
                }
            }
            file.delete()
        } catch (_: Exception) { false }
    }

    fun parseMidiFile(file: File): MidiReader.Parsed? {
        return try {
            MidiReader.parse(file).takeIf { it.notes.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    fun midiFolderPath(): String = midiFolder.absolutePath

    // ---------- ПУТИ ----------

    fun modelFilePath(): String = ckptFile.absolutePath
    fun mirrorFilePath(): String = mirrorFile.absolutePath
    fun logFilePath(): String = logFile.absolutePath
    fun outputsDir(): File = File(mirrorDir, "outputs").apply { mkdirs() }

    // ---------- UTIL ----------

    private fun now(): String = java.time.LocalTime.now().withNano(0).toString()
    private fun fmt4(v: Float): String = String.format(Locale.US, "%.4f", v)
    private fun fmt2(v: Float): String = String.format(Locale.US, "%.2f", v)
}