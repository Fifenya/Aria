package com.aria.midi

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

data class PlayerState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    val currentIndex: Int = 0,
    val totalNotes: Int = 0,
    val currentNote: Int = -1,
    val loop: Boolean = false,
    val volume: Float = 0.35f,
)

class MidiPlayer(private val sampleRate: Int = 44100) {

    @Volatile private var playing = false
    @Volatile private var paused = false
    @Volatile private var stopRequested = false

    private var thread: Thread? = null
    private var track: AudioTrack? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentNotes: List<Int> = emptyList()
    private var currentNoteMs: Int = 350
    private var currentVolume: Float = 0.35f
    private var loopEnabled: Boolean = false

    fun setLoop(enabled: Boolean) {
        loopEnabled = enabled
        _state.value = _state.value.copy(loop = enabled)
    }

    fun setVolume(v: Float) {
        currentVolume = v.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = currentVolume)
    }

    /** Всегда стартует новое воспроизведение (заменяет текущее). */
    fun play(notes: List<Int>, noteMs: Int = 400, volume: Float = 0.35f) {
        stopInternal()
        if (notes.isEmpty()) return

        currentNotes = notes
        currentNoteMs = noteMs
        currentVolume = volume

        _state.value = PlayerState(
            playing = true,
            paused = false,
            currentIndex = 0,
            totalNotes = notes.size,
            currentNote = notes.firstOrNull() ?: -1,
            loop = loopEnabled,
            volume = volume,
        )

        startThread()
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

    fun togglePause() {
        if (paused) resume() else pause()
    }

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
            playing = false,
            paused = false,
            currentIndex = 0,
            totalNotes = 0,
            currentNote = -1,
            loop = loopEnabled,
            volume = currentVolume,
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

    private fun startThread() {
        playing = true
        paused = false
        stopRequested = false

        thread = Thread {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            ).coerceAtLeast(sampleRate / 4)

            val t = AudioTrack.Builder()
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
                .setBufferSizeInBytes(minBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track = t
            try { t.play() } catch (_: Exception) {}

            try {
                val notes = currentNotes
                val samplesPerNote = sampleRate * currentNoteMs / 1000

                do {
                    for (idx in notes.indices) {
                        if (stopRequested) return@Thread

                        // Пауза: ждём, но не блокируем остановку
                        while (paused && !stopRequested) Thread.sleep(40)
                        if (stopRequested) return@Thread

                        val note = notes[idx]
                        _state.value = _state.value.copy(
                            currentIndex = idx,
                            currentNote = note,
                            playing = true,
                            paused = false,
                        )

                        val buf = synthNote(note, samplesPerNote, currentVolume)
                        var offset = 0
                        while (offset < buf.size && !stopRequested) {
                            while (paused && !stopRequested) Thread.sleep(40)
                            if (stopRequested) break

                            val written = try {
                                t.write(buf, offset, buf.size - offset, AudioTrack.WRITE_BLOCKING)
                            } catch (_: Exception) { -1 }

                            if (written < 0) break
                            offset += written
                        }
                    }
                } while (loopEnabled && !stopRequested)
            } catch (_: Exception) {
                // silent
            } finally {
                try { t.stop() } catch (_: Exception) {}
                try { t.release() } catch (_: Exception) {}
                track = null
                playing = false
                paused = false
                _state.value = _state.value.copy(
                    playing = false,
                    paused = false,
                    currentIndex = 0,
                    currentNote = -1,
                )
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun midiToHz(note: Int): Double =
        440.0 * 2.0.pow((note - 69) / 12.0)

    private fun synthNote(note: Int, samples: Int, volume: Float): FloatArray {
        val f = midiToHz(note)
        val buf = FloatArray(samples)
        val attack = (sampleRate * 0.01).toInt()
        val decay = (sampleRate * 0.15).toInt()

        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate
            val s = sin(2 * PI * f * t) * 1.00 +
                    sin(2 * PI * f * 2 * t) * 0.35 +
                    sin(2 * PI * f * 3 * t) * 0.15

            val env = when {
                i < attack -> i.toFloat() / attack
                else -> exp(-((i - attack).toDouble() / decay).coerceAtLeast(0.0)).toFloat()
            }

            buf[i] = (s / 1.5 * env * volume).toFloat()
        }
        return buf
    }
}