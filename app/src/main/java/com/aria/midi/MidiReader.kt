package com.aria.midi

import java.io.DataInputStream
import java.io.File
import java.io.InputStream

/** Нота с реальным временем и каналом. channel = 9 → перкуссия. */
data class TimedNote(
    val note: Int,
    val startMs: Long,
    val durationMs: Int,
    val velocity: Int,
    val channel: Int,
) {
    val isDrum: Boolean get() = channel == 9
}

object MidiReader {

    data class Parsed(
        val notes: List<Int>,
        val timedNotes: List<TimedNote>,
        val trackCount: Int,
        val ticksPerBeat: Int,
        val tempoBpm: Int,
        val avgNoteMs: Int,
        val totalMs: Long,
        val drumCount: Int,
        val tempoChanges: Int,
    )

    private const val DEFAULT_TEMPO_US = 500_000
    private const val MAX_PLAY_MS = 15 * 60 * 1000

    private data class TempoEvent(val tick: Long, val usPerBeat: Int)

    fun parse(file: File): Parsed = file.inputStream().buffered().use { parse(it) }

    fun parse(input: InputStream): Parsed {
        val data = DataInputStream(input)
        val mthd = ByteArray(4)
        data.readFully(mthd)
        require(String(mthd) == "MThd") { "not a MIDI file" }

        val headerLen = data.readInt()
        require(headerLen >= 6) { "bad header len" }
        data.readShort()
        val trackCount = data.readShort().toInt()
        val division = data.readShort().toInt()
        repeat(headerLen - 6) { data.readByte() }

        val ticksPerBeat = if (division and 0x8000 == 0 && division > 0) division else 480

        val allNotes = mutableListOf<Int>()
        val allTimedTicks = mutableListOf<RawNote>()
        val allTempos = mutableListOf<TempoEvent>()

        repeat(trackCount) {
            val mtrk = ByteArray(4)
            try { data.readFully(mtrk) } catch (_: Exception) { return@repeat }
            if (String(mtrk) != "MTrk") return@repeat
            val trackLen = data.readInt()
            val trackBytes = ByteArray(trackLen)
            data.readFully(trackBytes)

            val r = parseTrack(trackBytes)
            allNotes.addAll(r.notes)
            allTimedTicks.addAll(r.rawNotes)
            allTempos.addAll(r.tempos)
        }

        // Сортируем tempo map по тику. Добавляем начальный темп, если его нет.
        val tempoMap = buildTempoMap(allTempos)

        // Конвертируем тики в мс с учётом tempo map
        val converted = allTimedTicks.map { rn ->
            TimedNote(
                note = rn.note,
                startMs = ticksToMs(rn.startTick, tempoMap, ticksPerBeat),
                durationMs = (
                        ticksToMs(rn.endTick, tempoMap, ticksPerBeat) -
                                ticksToMs(rn.startTick, tempoMap, ticksPerBeat)
                        ).toInt().coerceAtLeast(20),
                velocity = rn.velocity,
                channel = rn.channel,
            )
        }.sortedBy { it.startMs }

        val totalMs = if (converted.isNotEmpty())
            converted.last().startMs + converted.last().durationMs
        else 0L

        val tempoBpm = (60_000_000.0 / (tempoMap.firstOrNull()?.usPerBeat ?: DEFAULT_TEMPO_US))
            .toInt().coerceIn(20, 400)

        val avgMs = if (converted.isNotEmpty())
            (totalMs / converted.size).toInt().coerceIn(50, 2000)
        else 350

        val drumCount = converted.count { it.isDrum }
        val trimmed = allNotes.take(50_000)

        return Parsed(
            notes = trimmed,
            timedNotes = converted,
            trackCount = trackCount,
            ticksPerBeat = ticksPerBeat,
            tempoBpm = tempoBpm,
            avgNoteMs = avgMs,
            totalMs = totalMs.coerceAtMost(MAX_PLAY_MS.toLong()),
            drumCount = drumCount,
            tempoChanges = tempoMap.size,
        )
    }

    private data class RawNote(
        val note: Int,
        val startTick: Long,
        val endTick: Long,
        val velocity: Int,
        val channel: Int,
    )

    private data class TrackResult(
        val notes: List<Int>,
        val rawNotes: List<RawNote>,
        val tempos: List<TempoEvent>,
    )

    private fun buildTempoMap(events: List<TempoEvent>): List<TempoEvent> {
        val sorted = events.sortedBy { it.tick }
        if (sorted.isEmpty() || sorted.first().tick > 0) {
            return listOf(TempoEvent(0L, DEFAULT_TEMPO_US)) + sorted
        }
        return sorted
    }

    /** Точный перевод тика в миллисекунды через tempo map. */
    private fun ticksToMs(tick: Long, map: List<TempoEvent>, ticksPerBeat: Int): Long {
        var ms = 0.0
        var lastTick = 0L
        var lastTempo = DEFAULT_TEMPO_US

        for (ev in map) {
            if (ev.tick >= tick) break
            val dt = ev.tick - lastTick
            ms += dt * lastTempo / 1000.0 / ticksPerBeat
            lastTick = ev.tick
            lastTempo = ev.usPerBeat
        }
        ms += (tick - lastTick) * lastTempo / 1000.0 / ticksPerBeat
        return ms.toLong()
    }

    private fun parseTrack(bytes: ByteArray): TrackResult {
        val notes = mutableListOf<Int>()
        val rawNotes = mutableListOf<RawNote>()
        val tempos = mutableListOf<TempoEvent>()
        var i = 0
        var runningStatus = -1
        var currentTick = 0L

        // Активные ноты: (channel, note) → (startTick, velocity)
        val active = HashMap<Pair<Int, Int>, Pair<Long, Int>>()

        while (i < bytes.size) {
            var delta = 0
            while (i < bytes.size) {
                val b = bytes[i].toInt() and 0xFF
                i++
                delta = (delta shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) break
            }
            currentTick += delta

            if (i >= bytes.size) break
            var status = bytes[i].toInt() and 0xFF

            val isDataByte = status < 0x80
            if (isDataByte) {
                if (runningStatus < 0) break
                status = runningStatus
            } else {
                i++
                if (status < 0xF0) runningStatus = status
            }

            val type = status and 0xF0
            val channel = status and 0x0F

            when (type) {
                0x80, 0x90 -> {
                    if (i + 1 >= bytes.size) break
                    val note = bytes[i].toInt() and 0xFF
                    val vel = bytes[i + 1].toInt() and 0xFF
                    i += 2
                    val isOn = type == 0x90 && vel > 0
                    val key = channel to note

                    if (isOn) {
                        active[key] = currentTick to vel
                        notes.add(note)
                    } else {
                        val start = active.remove(key)
                        if (start != null) {
                            rawNotes.add(
                                RawNote(
                                    note = note,
                                    startTick = start.first,
                                    endTick = currentTick,
                                    velocity = start.second,
                                    channel = channel,
                                )
                            )
                        }
                    }
                }
                0xA0, 0xB0, 0xE0 -> i += 2
                0xC0, 0xD0 -> i += 1
                0xF0 -> {
                    when (status) {
                        0xF0, 0xF7 -> {
                            var len = 0
                            while (i < bytes.size) {
                                val b = bytes[i].toInt() and 0xFF
                                i++
                                len = (len shl 7) or (b and 0x7F)
                                if (b and 0x80 == 0) break
                            }
                            i += len
                        }
                        0xFF -> {
                            if (i >= bytes.size) break
                            val metaType = bytes[i].toInt() and 0xFF
                            i++
                            var len = 0
                            while (i < bytes.size) {
                                val b = bytes[i].toInt() and 0xFF
                                i++
                                len = (len shl 7) or (b and 0x7F)
                                if (b and 0x80 == 0) break
                            }
                            if (metaType == 0x51 && len == 3 && i + 3 <= bytes.size) {
                                val tempo =
                                    ((bytes[i].toInt() and 0xFF) shl 16) or
                                    ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                                    (bytes[i + 2].toInt() and 0xFF)
                                tempos.add(TempoEvent(currentTick, tempo))
                            }
                            i += len
                        }
                        else -> {}
                    }
                }
                else -> break
            }
        }

        // Закрываем висящие ноты
        for ((key, start) in active) {
            rawNotes.add(
                RawNote(
                    note = key.second,
                    startTick = start.first,
                    endTick = currentTick.coerceAtLeast(start.first + 1),
                    velocity = start.second,
                    channel = key.first,
                )
            )
        }

        return TrackResult(notes, rawNotes, tempos)
    }
}