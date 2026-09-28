package com.aria.midi

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Пишет Standard MIDI File (SMF) формата 0.
 *
 * Формат файла:
 *   "MThd" | len=6 | format=0 | tracks=1 | division=480
 *   "MTrk" | len | delta_time note_on note_off ... | end_of_track
 *
 * Каждая нота — отдельное событие: note_on → через N тиков note_off.
 * Velocity фиксированный (80), канал 0 (по умолчанию piano).
 */
object MidiWriter {

    private const val TICKS_PER_BEAT = 480
    private const val VELOCITY = 80
    private const val CHANNEL = 0

    /**
     * @param file     куда писать
     * @param notes    список MIDI-нот (60 = C4)
     * @param tempoBpm темп в BPM
     * @param noteMs   длительность ноты в миллисекундах (по умолчанию считается из темпа)
     */
    fun write(
        file: File,
        notes: List<Int>,
        tempoBpm: Int = 120,
        noteMs: Int = 0,
    ) {
        require(notes.isNotEmpty()) { "notes is empty" }

        val bytes = buildSmf(notes, tempoBpm, noteMs)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    /** Возвращает готовый SMF-файл как массив байт. */
    fun buildSmf(notes: List<Int>, tempoBpm: Int = 120, noteMs: Int = 0): ByteArray {
        val track = buildTrack(notes, tempoBpm, noteMs)

        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        // --- MThd ---
        dos.writeBytes("MThd")
        dos.writeInt(6)               // длина заголовка
        dos.writeShort(0)             // format 0
        dos.writeShort(1)             // 1 трек
        dos.writeShort(TICKS_PER_BEAT)

        // --- MTrk ---
        dos.writeBytes("MTrk")
        dos.writeInt(track.size)
        dos.write(track)

        dos.flush()
        return out.toByteArray()
    }

    private fun buildTrack(notes: List<Int>, tempoBpm: Int, noteMs: Int): ByteArray {
        val buf = ByteArrayOutputStream()

        // --- Meta: Tempo ---
        val usPerBeat = 60_000_000 / tempoBpm.coerceIn(20, 400)
        buf.write(0x00)                       // delta = 0
        buf.write(0xFF)                       // meta
        buf.write(0x51)                       // tempo
        buf.write(0x03)                       // длина 3
        buf.write((usPerBeat shr 16) and 0xFF)
        buf.write((usPerBeat shr 8) and 0xFF)
        buf.write(usPerBeat and 0xFF)

        // --- Meta: Track name ---
        val name = "Aria"
        buf.write(0x00)
        buf.write(0xFF)
        buf.write(0x03)
        writeVarLen(buf, name.length)
        buf.write(name.toByteArray(Charsets.US_ASCII))

        // --- Meta: Time signature 4/4 ---
        buf.write(0x00)
        buf.write(0xFF)
        buf.write(0x58)
        buf.write(0x04)
        buf.write(0x04)   // 4/4
        buf.write(0x02)   // 2^2 = 4
        buf.write(0x18)   // 24 MIDI clocks per metronome
        buf.write(0x08)   // 8 32nd notes per quarter

        // --- Ноты ---
        // Длительность ноты в тиках: либо из noteMs, либо дефолт — четверть
        val ticksPerNote: Int = if (noteMs > 0) {
            val usPerBeatLocal = 60_000_000 / tempoBpm.coerceIn(20, 400)
            ((noteMs.toLong() * TICKS_PER_BEAT) / (usPerBeatLocal / 1000)).toInt()
                .coerceAtLeast(1)
        } else {
            TICKS_PER_BEAT / 2    // восьмая
        }

        for (i in notes.indices) {
            val note = notes[i].coerceIn(0, 127)

            // note_on с delta=0
            buf.write(0x00)
            buf.write(0x90 or CHANNEL)   // note_on, channel 0
            buf.write(note)
            buf.write(VELOCITY)

            // note_off через ticksPerNote
            writeVarLen(buf, ticksPerNote)
            buf.write(0x80 or CHANNEL)   // note_off, channel 0
            buf.write(note)
            buf.write(0x00)              // velocity off
        }

        // --- End of track ---
        buf.write(0x00)
        buf.write(0xFF)
        buf.write(0x2F)
        buf.write(0x00)

        return buf.toByteArray()
    }

    /** Записывает число в формате variable-length quantity (как требует SMF). */
    private fun writeVarLen(out: ByteArrayOutputStream, value: Int) {
        var v = value and 0x0FFFFFFF
        val buf = IntArray(4)
        var idx = 0
        buf[idx++] = v and 0x7F
        while (v ushr 7 != 0) {
            v = v ushr 7
            buf[idx++] = (v and 0x7F) or 0x80
        }
        for (i in idx - 1 downTo 0) {
            out.write(buf[i])
        }
    }
}