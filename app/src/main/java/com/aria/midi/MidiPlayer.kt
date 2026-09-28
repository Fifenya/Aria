package com.aria.midi

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

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
                val totalMs = currentTimed.last().startMs + currentTimed.last().durationMs

                // 1. Рендер всего буфера (stereo interleaved)
                val stereo = Synth.render(currentTimed, sampleRate, gain = currentVolume)

                // 2. Chorus — «оркестровая» ширина
                Synth.applyChorus(stereo, sampleRate, mix = 0.25f)

                // 3. Нормализация
                var maxAbs = 0f
                for (v in stereo) { val a = abs(v); if (a > maxAbs) maxAbs = a }
                if (maxAbs > 1f) {
                    val sc = 1f / maxAbs
                    for (i in stereo.indices) stereo[i] *= sc
                }

                // 4. STATIC для коротких, STREAM для длинных
                val bytesNeeded = stereo.size * 4
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
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setBufferSizeInBytes(bytesNeeded)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    try {
                        b.write(stereo, 0, stereo.size, AudioTrack.WRITE_BLOCKING)
                    } catch (_: Exception) {}
                    b
                } else {
                    val minBufBytes = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_FLOAT,
                    ).coerceAtLeast(sampleRate * 8)
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
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBufBytes * 4)
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
                    // STREAM: пишем по 1 сек stereo (2 × sampleRate floats)
                    val chunkFloats = sampleRate * 2
                    do {
                        var offset = 0
                        while (offset < stereo.size && !stopRequested) {
                            while (paused && !stopRequested) Thread.sleep(40)
                            if (stopRequested) break
                            val toWrite = minOf(chunkFloats, stereo.size - offset)
                            val written = try {
                                t.write(stereo, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                            } catch (_: Exception) { -1 }
                            if (written <= 0) break
                            offset += written
                            val frames = offset / 2
                            val currentMs = frames.toLong() * 1000L / sampleRate
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
}