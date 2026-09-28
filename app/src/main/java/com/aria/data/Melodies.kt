package com.aria.data

import com.aria.midi.MidiReader
import java.io.File

object Melodies {

    /** Размер MIDI-словаря: все 128 возможных нот. Никогда не меняется. */
    const val VOCAB_SIZE = 128

    private val builtIn: List<IntArray> = listOf(
        intArrayOf(60,62,64,65,67,69,71,72,71,69,67,65,64,62,60),
        intArrayOf(60,60,67,67,69,69,67,65,65,64,64,62,62,60),
        intArrayOf(67,67,69,67,72,71,67,67,69,67,74,72),
        intArrayOf(60,64,67,72,67,64,60,64,67,72,76,72,67,64),
        intArrayOf(62,64,65,67,69,70,71,72,71,70,69,67,65,64,62),
        intArrayOf(72,71,69,67,65,64,62,60,62,64,65,67,69,71,72),
        intArrayOf(60,62,63,65,67,68,70,72,70,68,67,65,63,62,60),
        intArrayOf(67,69,71,72,74,76,77,79,77,76,74,72,71,69,67),
    )

    private val extra = mutableListOf<IntArray>()

    val raw: List<IntArray> get() = builtIn + extra

    fun addMelody(notes: IntArray) {
        if (notes.size < 4) return
        extra.add(notes)
    }

    fun clearExtra() { extra.clear() }
    fun extraCount(): Int = extra.size
    fun builtInCount(): Int = builtIn.size

    /** Маска «живых» нот: какие MIDI-ноты встречаются в датасете. */
    fun buildMask(): BooleanArray {
        val mask = BooleanArray(VOCAB_SIZE)
        raw.forEach { m -> m.forEach { n -> if (n in 0 until VOCAB_SIZE) mask[n] = true } }
        return mask
    }

    /** Фильтруем ноты в диапазон 0..127. Индекс в тензоре = сама MIDI-нота. */
    fun encode(melody: IntArray): IntArray {
        val out = IntArray(melody.size)
        var j = 0
        for (n in melody) if (n in 0 until VOCAB_SIZE) out[j++] = n
        return if (j == melody.size) out else out.copyOf(j)
    }

    fun loadAllFromFolder(dir: File, trackerFile: File): List<String> {
        val lines = mutableListOf<String>()
        if (!dir.exists()) { dir.mkdirs(); lines.add("midi folder created: ${dir.absolutePath}") }

        val seen = mutableMapOf<String, Long>()
        if (trackerFile.exists()) {
            try {
                trackerFile.readLines().forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size == 2) parts[1].toLongOrNull()?.let { seen[parts[0]] = it }
                }
            } catch (_: Exception) {}
        }

        val files = dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".mid", true) || f.name.endsWith(".midi", true))
        } ?: emptyArray()

        if (files.isEmpty()) {
            lines.add("midi folder empty: ${dir.absolutePath}")
            return lines
        }

        var addedNotes = 0; var newFiles = 0; var skipped = 0
        for (f in files) {
            val size = f.length()
            if (seen[f.name] == size) { skipped++; continue }
            try {
                val parsed = MidiReader.parse(f)
                if (parsed.notes.isEmpty()) { lines.add("midi empty: ${f.name}"); continue }
                var i = 0; var chunks = 0
                while (i < parsed.notes.size) {
                    val end = minOf(i + 64, parsed.notes.size)
                    val slice = parsed.notes.subList(i, end).toIntArray()
                    if (slice.size >= 4) { addMelody(slice); addedNotes += slice.size; chunks++ }
                    i = end
                }
                seen[f.name] = size; newFiles++
                lines.add("imported ${f.name}  notes=${parsed.notes.size}  " +
                        "chunks=$chunks  tracks=${parsed.trackCount}  " +
                        "tempo=${parsed.tempoBpm}  avg=${parsed.avgNoteMs}ms")
            } catch (e: Exception) {
                lines.add("failed ${f.name}: ${e.message}")
            }
        }

        try {
            trackerFile.writeText(seen.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        } catch (_: Exception) {}

        if (newFiles > 0) {
            lines.add("midi import done: new=$newFiles skipped=$skipped " +
                    "added=$addedNotes notes  melodies=${raw.size}")
        } else lines.add("midi import: nothing new (skipped=$skipped)")

        return lines
    }
}