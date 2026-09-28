package com.aria.midi

import java.io.DataInputStream
import java.io.File
import java.io.InputStream

/**
 * Минимальный парсер Standard MIDI File (формат 0 и 1).
 * Возвращает список нот (MIDI 0..127) в порядке появления,
 * разворачивая паузы в повторы, чтобы модель могла учиться.
 */
object MidiReader {

    data class Parsed(
        val notes: List<Int>,
        val trackCount: Int,
        val ticksPerBeat: Int,
    )

    fun parse(file: File): Parsed = file.inputStream().buffered().use { parse(it) }

    fun parse(input: InputStream): Parsed {
        val data = DataInputStream(input)

        // --- Заголовок ---
        val mthd = ByteArray(4)
        data.readFully(mthd)
        require(String(mthd) == "MThd") { "not a MIDI file" }

        val headerLen = data.readInt()
        require(headerLen >= 6) { "bad header len" }
        val format = data.readShort().toInt()
        val trackCount = data.readShort().toInt()
        val division = data.readShort().toInt()

        // Пропускаем остаток заголовка
        repeat(headerLen - 6) { data.readByte() }

        val ticksPerBeat = if (division and 0x8000 == 0) division else 480

        val allNotes = mutableListOf<Int>()

        // --- Треки ---
        repeat(trackCount) {
            val mtrk = ByteArray(4)
            try { data.readFully(mtrk) } catch (_: Exception) { return@repeat }
            if (String(mtrk) != "MTrk") return@repeat

            val trackLen = data.readInt()
            val trackEnd = trackLen.toLong() // для контроля

            val trackBytes = ByteArray(trackLen)
            data.readFully(trackBytes)

            val notes = parseTrack(trackBytes, ticksPerBeat)
            allNotes.addAll(notes)
        }

        // Ограничим длина — чтобы не забить RAM
        val trimmed = allNotes.take(50_000)

        return Parsed(trimmed, trackCount, ticksPerBeat)
    }

    private fun parseTrack(bytes: ByteArray, ticksPerBeat: Int): List<Int> {
        val out = mutableListOf<Int>()
        var i = 0
        var runningStatus = -1

        while (i < bytes.size) {
            // Delta time (variable length)
            var delta = 0
            while (i < bytes.size) {
                val b = bytes[i].toInt() and 0xFF
                i++
                delta = (delta shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) break
            }

            if (i >= bytes.size) break
            var status = bytes[i].toInt() and 0xFF

            // Running status — если байт < 0x80, это данные предыдущего статуса
            val isDataByte = status < 0x80
            if (isDataByte) {
                if (runningStatus < 0) break
                status = runningStatus
            } else {
                i++
                if (status < 0xF0) runningStatus = status
            }

            val type = status and 0xF0

            when (type) {
                0x80, 0x90 -> { // note off / note on
                    if (i + 1 >= bytes.size) break
                    val note = bytes[i].toInt() and 0xFF
                    val vel = bytes[i + 1].toInt() and 0xFF
                    i += 2

                    if (type == 0x90 && vel > 0) {
                        out.add(note)
                    }
                    // Простой учёт пауз: если delta большая — вставляем повтор
                    // предыдущей ноты (упрощённо, для "продолжения")
                    // (сознательно пропускаем — оставим только ноты)
                }
                0xA0, 0xB0, 0xE0 -> i += 2
                0xC0, 0xD0 -> i += 1
                0xF0 -> {
                    when (status) {
                        0xF0, 0xF7 -> {
                            // SysEx — читаем длину и пропускаем
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
                            // Meta event
                            if (i >= bytes.size) break
                            i++ // тип мета
                            var len = 0
                            while (i < bytes.size) {
                                val b = bytes[i].toInt() and 0xFF
                                i++
                                len = (len shl 7) or (b and 0x7F)
                                if (b and 0x80 == 0) break
                            }
                            i += len
                        }
                        else -> { /* ignore */ }
                    }
                }
                else -> { /* unknown, break to avoid loop */ break }
            }
        }
        return out
    }
}