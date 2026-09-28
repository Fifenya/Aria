package com.aria.midi

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ExportResult(
    val wav: File?,
    val m4a: File?,
    val sampleRate: Int,
    val durationMs: Long,
)

object AudioExporter {

    private const val SAMPLE_RATE = 44100

    /**
     * Экспортирует мелодию в WAV и/или M4A (AAC).
     * baseName — префикс (без расширения).
     */
    fun export(
        notes: List<TimedNote>,
        outputDir: File,
        baseName: String = "aria_export",
        withReverb: Boolean = true,
    ): ExportResult {
        outputDir.mkdirs()
        if (notes.isEmpty()) return ExportResult(null, null, SAMPLE_RATE, 0L)

        val sorted = notes.sortedBy { it.startMs }

        // 1. Синтез в стерео
        var stereo = Synth.render(sorted, SAMPLE_RATE, gain = 0.85f)

        // 2. Reverb
        if (withReverb) {
            stereo = Reverb(SAMPLE_RATE).process(stereo)
        }

        // 3. Финальный fade-out 250 мс
        applyFadeOut(stereo, SAMPLE_RATE, 250)

        // 4. Нормализация под пик 0.95
        normalize(stereo, 0.95f)

        // 5. Имена файлов
        val stamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val wavFile = File(outputDir, "${baseName}_$stamp.wav")
        val m4aFile = File(outputDir, "${baseName}_$stamp.m4a")

        // 6. WAV
        val wav = try {
            WavWriter.write(wavFile, stereo, SAMPLE_RATE)
            wavFile
        } catch (_: Exception) { null }

        // 7. AAC
        val m4a = if (AacEncoder.encode(m4aFile, stereo, SAMPLE_RATE)) m4aFile else null

        val durationMs = (stereo.size / 2L * 1000L) / SAMPLE_RATE
        return ExportResult(wav, m4a, SAMPLE_RATE, durationMs)
    }

    private fun normalize(buf: FloatArray, target: Float) {
        var maxAbs = 0f
        for (v in buf) { val a = kotlin.math.abs(v); if (a > maxAbs) maxAbs = a }
        if (maxAbs > 0f && maxAbs != target) {
            val sc = target / maxAbs
            for (i in buf.indices) buf[i] *= sc
        }
    }

    private fun applyFadeOut(buf: FloatArray, sampleRate: Int, ms: Int) {
        val n = sampleRate * ms / 1000
        if (n <= 0 || n > buf.size / 2) return
        val total = buf.size / 2
        val start = total - n
        for (i in 0 until n) {
            val g = 1f - i.toFloat() / n
            buf[(start + i) * 2]     *= g
            buf[(start + i) * 2 + 1] *= g
        }
    }
}