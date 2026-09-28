package com.aria.midi

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import java.util.Random

data class PlayerState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    val currentIndex: Int = 0,
    val totalNotes: Int = 0,
    val currentNote: Int = -1,
    val loop: Boolean = false,
    val volume: Float = 0.35f,
    val source: String = "",
    val tempoBpm: Int = 120,
    val noteMs: Int = 0,
    val currentMs: Long = 0L,
    val totalMs: Long = 0L,
    val hasDrums: Boolean = false,
    val drumCount: Int = 0,
    val tempoChanges: Int = 1,
    /** Полная последовательность нот — для отображения списка NOTES. */
    val noteSequence: List<Int> = emptyList(),
)

class MidiPlayer(private val sampleRate: Int = 44100) {

    @Volatile private var playing = false
    @Volatile private var paused = false
    @Volatile private var stopRequested = false

    private var thread: Thread? = null
    private var track: AudioTrack? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentTimed: List<TimedNote> = emptyList()
    private var currentVolume: Float = 0.35f
    private var loopEnabled: Boolean = false
    private var currentSource: String = ""
    private var currentTempoBpm: Int = 120
    private var currentNoteMs: Int = 0

    fun setLoop(enabled: Boolean) {
        loopEnabled = enabled
        _state.value = _state.value.copy(loop = enabled)
    }

    fun setVolume(v: Float) {
        currentVolume = v.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = currentVolume)
    }

    /** Простой вход: список нот, играется равномерно. Для генерации. */
    fun play(
        notes: List<Int>,
        noteMs: Int = 350,
        volume: Float = 0.35f,
        source: String = "",
        tempoBpm: Int = 120,
    ) {
        val timed = notes.mapIndexed { i, n ->
            TimedNote(
                note = n,
                startMs = i.toLong() * noteMs,
                durationMs = noteMs,
                velocity = 80,
                channel = 0,
            )
        }
        stopInternal()
        if (timed.isEmpty()) return

        currentTimed = timed
        currentNoteMs = noteMs
        currentVolume = volume
        currentSource = source
        currentTempoBpm = tempoBpm

        val totalMs = timed.last().startMs + timed.last().durationMs

        _state.value = PlayerState(
            playing = true, paused = false,
            currentIndex = 0, totalNotes = timed.size,
            currentNote = timed.first().note,
            loop = loopEnabled, volume = volume,
            source = source, tempoBpm = tempoBpm, noteMs = noteMs,
            currentMs = 0L, totalMs = totalMs,
            hasDrums = false, drumCount = 0, tempoChanges = 1,
            noteSequence = timed.map { it.note },
        )

        startTimedThread()
    }

    /** Полный вход: ноты с реальным таймингом из MIDI. */
    fun playTimed(
        notes: List<TimedNote>,
        volume: Float = 0.35f,
        source: String = "",
        tempoBpm: Int = 120,
        singleNoteMs: Int = 0,
        hasDrums: Boolean = false,
        drumCount: Int = 0,
        tempoChanges: Int = 1,
    ) {
        stopInternal()
        if (notes.isEmpty()) return

        currentTimed = notes.sortedBy { it.startMs }
        currentNoteMs = singleNoteMs
        currentVolume = volume
        currentSource = source
        currentTempoBpm = tempoBpm

        val totalMs = currentTimed.last().startMs + currentTimed.last().durationMs

        _state.value = PlayerState(
            playing = true, paused = false,
            currentIndex = 0, totalNotes = currentTimed.size,
            currentNote = currentTimed.first().note,
            loop = loopEnabled, volume = volume,
            source = source, tempoBpm = tempoBpm, noteMs = singleNoteMs,
            currentMs = 0L, totalMs = totalMs,
            hasDrums = hasDrums, drumCount = drumCount,
            tempoChanges = tempoChanges,
            noteSequence = currentTimed.map { it.note },
        )

        startTimedThread()
    }

    fun pause() {
        if (!playing) return
        paused = true
        _state.value = _state.value.copy(paused = true)
        try { track?.pause() } catch (_: Exception) {}
    }

    fun resume() {
        if (!playing || !paused) return
        paused = false
        _state.value = _state.value.copy(paused = false)
        try { track?.play() } catch (_: Exception) {}
    }

    fun togglePause() { if (paused) resume() else pause() }

    fun stop() {
        stopRequested = true
        playing = false
        paused = false
        try { thread?.join(500) } catch (_: Exception) {}
        thread = null
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
        stopRequested = false
        _state.value = PlayerState(
            playing = false, paused = false,
            currentIndex = 0, totalNotes = 0, currentNote = -1,
            loop = loopEnabled, volume = currentVolume,
            source = currentSource, tempoBpm = currentTempoBpm,
            noteMs = currentNoteMs,
            currentMs = 0L, totalMs = _state.value.totalMs,
            hasDrums = _state.value.hasDrums, drumCount = _state.value.drumCount,
            tempoChanges = _state.value.tempoChanges,
            noteSequence = _state.value.noteSequence,
        )
    }

    private fun stopInternal() {
        stopRequested = true
        try { thread?.join(500) } catch (_: Exception) {}
        thread = null
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
        playing = false
        paused = false
        stopRequested = false
    }

    fun isPlaying(): Boolean = playing && !paused

    private fun startTimedThread() {
        playing = true
        paused = false
        stopRequested = false

        thread = Thread {
            try {
                // 1. Общий стерео-буфер
                val totalMs = currentTimed.last().startMs + currentTimed.last().durationMs
                val totalSamples = (totalMs * sampleRate / 1000).toInt().coerceAtLeast(sampleRate)
                val buf = FloatArray(totalSamples)

                for (tn in currentTimed) {
                    if (stopRequested) return@Thread
                    val startSample = (tn.startMs * sampleRate / 1000).toInt()
                    val durSamples = (tn.durationMs.toLong() * sampleRate / 1000).toInt()
                        .coerceAtLeast(64)
                    val endSample = (startSample + durSamples).coerceAtMost(totalSamples)
                    if (startSample >= totalSamples) continue
                    val len = endSample - startSample
                    val vol = currentVolume * (tn.velocity / 127f)
                    val synth = if (tn.isDrum) {
                        synthDrum(tn.note, len, vol)
                    } else {
                        synthNote(tn.note, len, vol)
                    }
                    for (i in 0 until len) buf[startSample + i] += synth[i]
                }

                // 2. Нормализация
                var maxAbs = 0f
                for (v in buf) { val a = abs(v); if (a > maxAbs) maxAbs = a }
                if (maxAbs > 1f) {
                    val sc = 1f / maxAbs
                    for (i in buf.indices) buf[i] *= sc
                }

                // 3. Выбор режима: STATIC для коротких, STREAM с большим буфером для длинных
                val bytesNeeded = buf.size * 4
                val staticMax = 4 * 1024 * 1024
                val useStatic = bytesNeeded <= staticMax

                val t = if (useStatic) {
                    val b = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bytesNeeded)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    try { b.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING) } catch (_: Exception) {}
                    b
                } else {
                    val minBufBytes = AudioTrack.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
                    ).coerceAtLeast(sampleRate * 4)
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBufBytes * 8)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                }

                track = t
                try { t.play() } catch (_: Exception) {}

                if (useStatic) {
                    val startWall = System.currentTimeMillis()
                    while (!stopRequested && t.playState != AudioTrack.PLAYSTATE_STOPPED) {
                        while (paused && !stopRequested) Thread.sleep(40)
                        if (stopRequested) break
                        val elapsed = System.currentTimeMillis() - startWall
                        updateProgress(elapsed.coerceAtMost(totalMs), totalMs)
                        Thread.sleep(60)
                    }
                    if (loopEnabled && !stopRequested) {
                        try { t.stop() } catch (_: Exception) {}
                        try { t.release() } catch (_: Exception) {}
                        track = null
                        startTimedThread()
                        return@Thread
                    }
                } else {
                    val chunk = sampleRate
                    do {
                        var offset = 0
                        while (offset < buf.size && !stopRequested) {
                            while (paused && !stopRequested) Thread.sleep(40)
                            if (stopRequested) break
                            val toWrite = minOf(chunk, buf.size - offset)
                            val written = try {
                                t.write(buf, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                            } catch (_: Exception) { -1 }
                            if (written <= 0) break
                            offset += written
                            val currentMs = offset.toLong() * 1000L / sampleRate
                            updateProgress(currentMs, totalMs)
                        }
                    } while (loopEnabled && !stopRequested)
                }

                try { t.stop() } catch (_: Exception) {}
                try { t.release() } catch (_: Exception) {}
                track = null
            } catch (_: Exception) {
            } finally {
                playing = false
                paused = false
                _state.value = _state.value.copy(
                    playing = false, paused = false,
                    currentIndex = 0, currentNote = -1,
                    currentMs = 0L,
                )
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun updateProgress(currentMs: Long, totalMs: Long) {
        val notes = currentTimed
        var idx = 0
        for (j in notes.indices) {
            if (notes[j].startMs <= currentMs) idx = j else break
        }
        _state.value = _state.value.copy(
            currentIndex = idx,
            currentNote = notes.getOrNull(idx)?.note ?: -1,
            currentMs = currentMs,
            totalMs = totalMs,
            playing = true,
            paused = false,
        )
    }

    private fun midiToHz(note: Int): Double = 440.0 * 2.0.pow((note - 69) / 12.0)

    private fun synthNote(note: Int, samples: Int, volume: Float): FloatArray {
        val f = midiToHz(note)
        val buf = FloatArray(samples)
        val attack = (sampleRate * 0.008).toInt().coerceAtLeast(1)
        val decay = (sampleRate * 0.35).toInt().coerceAtLeast(1)

        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate
            val s = sin(2 * PI * f * t) * 1.00 +
                    sin(2 * PI * f * 2 * t) * 0.30 +
                    sin(2 * PI * f * 3 * t) * 0.12
            val env = when {
                i < attack -> i.toFloat() / attack
                else -> exp(-((i - attack).toDouble() / decay).coerceAtLeast(0.0)).toFloat()
            }
            buf[i] = (s / 1.6 * env * volume).toFloat()
        }
        return buf
    }

    private fun synthDrum(note: Int, samples: Int, volume: Float): FloatArray {
        val buf = FloatArray(samples)
        val rng = Random(note.toLong() * 31L + samples)

        val isKick = note in 35..36
        val isSnare = note in 38..40
        val isHat = note in 42..44 || note == 46
        val isTom = note in 41..50 && !isSnare && !isHat && !isKick

        val decaySamples = when {
            isKick -> (sampleRate * 0.18).toInt()
            isSnare -> (sampleRate * 0.10).toInt()
            isHat -> (sampleRate * 0.035).toInt()
            isTom -> (sampleRate * 0.20).toInt()
            else -> (sampleRate * 0.08).toInt()
        }.coerceAtLeast(1)

        val toneFreq = when {
            isKick -> 55.0
            isSnare -> 180.0
            isTom -> 440.0 * 2.0.pow((note.coerceIn(41, 50) + 20 - 69) / 12.0)
            else -> 0.0
        }
        val noiseMix = when {
            isKick -> 0.05f
            isSnare -> 0.75f
            isHat -> 1.0f
            isTom -> 0.15f
            else -> 0.5f
        }

        for (i in 0 until samples) {
            val env = exp(-i.toDouble() / decaySamples).toFloat()
            val noise = (rng.nextFloat() * 2f - 1f)
            val t = i.toDouble() / sampleRate
            val tone = if (toneFreq > 0) sin(2 * PI * toneFreq * t) else 0.0
            val s = (noise * noiseMix + tone * (1f - noiseMix))
            buf[i] = (s * env * volume).toFloat()
        }
        return buf
    }
}