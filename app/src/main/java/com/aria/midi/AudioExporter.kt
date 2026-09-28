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

    fun export(
        notes: List<TimedNote>,
        outputDir: File,
        baseName: String = "aria_export",
        withReverb: Boolean = true,
        onProgress: ((Float) -> Unit)? = null,
    ): ExportResult {
        outputDir.mkdirs()
        if (notes.isEmpty()) return ExportResult(null, null, SAMPLE_RATE, 0L)

        onProgress?.invoke(0.05f)
        val sorted = notes.sortedBy { it.startMs }

        // 1. Синтез
        var stereo = Synth.render(sorted, SAMPLE_RATE, gain = 0.85f)
        onProgress?.invoke(0.4f)

        // 2. Chorus (лёгкий, не мыльный)
        Synth.applyChorus(stereo, SAMPLE_RATE, mix = 0.15f)
        onProgress?.invoke(0.55f)

        // 3. Reverb (опционально)
        if (withReverb) {
            stereo = Reverb(SAMPLE_RATE).process(stereo)
        }
        onProgress?.invoke(0.7f)

        // 4. Fade-out и soft-clip
        applyFadeOut(stereo, SAMPLE_RATE, 250)
        Synth.softClip(stereo, drive = 1.1f)
        normalize(stereo, 0.9f)
        onProgress?.invoke(0.8f)

        val stamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val wavFile = File(outputDir, "${baseName}_$stamp.wav")
        val m4aFile = File(outputDir, "${baseName}_$stamp.m4a")

        val wav = try {
            WavWriter.write(wavFile, stereo, SAMPLE_RATE)
            wavFile
        } catch (_: Exception) { null }
        onProgress?.invoke(0.9f)

        val m4a = if (AacEncoder.encode(m4aFile, stereo, SAMPLE_RATE)) m4aFile else null
        onProgress?.invoke(1f)

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