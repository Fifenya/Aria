package com.aria.midi

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import java.util.Random

enum class Instrument {
    PIANO, STRINGS, BRASS, ORGAN, BELL
}

object Synth {

    fun render(
        notes: List<TimedNote>,
        sampleRate: Int,
        gain: Float = 0.9f,
    ): FloatArray {
        if (notes.isEmpty()) return FloatArray(0)
        val sorted = notes.sortedBy { it.startMs }
        val totalMs = sorted.last().startMs + sorted.last().durationMs
        val totalSamples = (totalMs * sampleRate / 1000).toInt().coerceAtLeast(sampleRate)
        val stereo = FloatArray(totalSamples * 2)

        for (tn in sorted) {
            val startSample = (tn.startMs * sampleRate / 1000).toInt()
            val durSamples = (tn.durationMs.toLong() * sampleRate / 1000).toInt()
                .coerceAtLeast(64)
            val endSample = (startSample + durSamples).coerceAtMost(totalSamples)
            if (startSample >= totalSamples) continue
            val len = endSample - startSample
            val vol = gain * (tn.velocity / 127f).coerceIn(0.05f, 1f)

            val mono = if (tn.isDrum) {
                drum(tn.note, len, vol, sampleRate)
            } else {
                pitch(tn.note, len, vol, sampleRate, instrumentFor(tn))
            }

            val pan = if (tn.isDrum) 0f else ((tn.note - 60) / 24f).coerceIn(-1f, 1f)
            val panL = (1f - pan) * 0.5f + 0.5f
            val panR = (1f + pan) * 0.5f + 0.5f

            for (i in 0 until len) {
                stereo[(startSample + i) * 2]     += mono[i] * panL
                stereo[(startSample + i) * 2 + 1] += mono[i] * panR
            }
        }
        return stereo
    }

    /** Мягкий клип через tanh — сжимает пики, сохраняет динамику. */
    fun softClip(stereo: FloatArray, drive: Float = 1.2f) {
        for (i in stereo.indices) {
            stereo[i] = tanh(stereo[i] * drive)
        }
    }

    /** Лёгкий stereo-chorus: 14 мс базы, LFO ±3 мс, каналы в противофазе. */
    fun applyChorus(stereo: FloatArray, sampleRate: Int, mix: Float = 0.15f) {
        val n = stereo.size / 2
        if (n < 2) return
        val baseDelay = (sampleRate * 0.014f).toInt().coerceAtLeast(1)
        val depth = (sampleRate * 0.003f).toInt().coerceAtLeast(1)
        val size = baseDelay + depth + 2
        val bufL = FloatArray(size)
        val bufR = FloatArray(size)
        var wl = 0
        var wr = 0
        val lfoHz = 0.35

        for (i in 0 until n) {
            val lfo = sin(2.0 * PI * lfoHz * i / sampleRate).toFloat()
            val dL = baseDelay + (depth * lfo).toInt()
            val dR = baseDelay - (depth * lfo).toInt()

            bufL[wl] = stereo[i * 2]
            bufR[wr] = stereo[i * 2 + 1]

            val rL = ((wl - dL) % size + size) % size
            val rR = ((wr - dR) % size + size) % size

            val delayedL = bufL[rL]
            val delayedR = bufR[rR]

            stereo[i * 2]     = stereo[i * 2] * (1f - mix) + delayedL * mix
            stereo[i * 2 + 1] = stereo[i * 2 + 1] * (1f - mix) + delayedR * mix

            wl = (wl + 1) % size
            wr = (wr + 1) % size
        }
    }

    // ---------- Пресеты ----------

    private data class Params(
        val attackMs: Float,
        val decayMs: Float,
        val sustain: Float,
        val releaseMs: Float,
        val harmonics: FloatArray,        // амплитуды обертонов 1..N
        val harmonicDecayRatio: Float,    // во сколько раз быстрее гаснут верхние
        val fmRatio: Float,
        val fmDepth: Float,
        val fmDecayMs: Float,
        val vibratoHz: Float,
        val vibratoCents: Float,
        val detuneCents: FloatArray,
        val subLevel: Float,              // суб-октава
        val lpMultiplier: Float,          // cutoff = f0 * multiplier
        val lpMaxHz: Float,
    )

    private val PIANO = Params(
        attackMs = 2f, decayMs = 1200f, sustain = 0.2f, releaseMs = 400f,
        harmonics = floatArrayOf(1f, 0.45f, 0.22f, 0.11f, 0.05f),
        harmonicDecayRatio = 4f,
        fmRatio = 2f, fmDepth = 2.5f, fmDecayMs = 60f,
        vibratoHz = 0f, vibratoCents = 0f,
        detuneCents = floatArrayOf(0f),
        subLevel = 0.2f,
        lpMultiplier = 8f, lpMaxHz = 5000f,
    )

    private val STRINGS = Params(
        attackMs = 120f, decayMs = 150f, sustain = 0.85f, releaseMs = 250f,
        harmonics = floatArrayOf(1f, 0.55f, 0.35f, 0.2f, 0.12f, 0.07f),
        harmonicDecayRatio = 2f,
        fmRatio = 1f, fmDepth = 0f, fmDecayMs = 0f,
        vibratoHz = 5.5f, vibratoCents = 8f,
        detuneCents = floatArrayOf(-7f, 0f, 7f),
        subLevel = 0.25f,
        lpMultiplier = 6f, lpMaxHz = 4500f,
    )

    private val BRASS = Params(
        attackMs = 40f, decayMs = 200f, sustain = 0.7f, releaseMs = 180f,
        harmonics = floatArrayOf(1f, 0.85f, 0.7f, 0.5f, 0.3f, 0.15f),
        harmonicDecayRatio = 1.5f,
        fmRatio = 1f, fmDepth = 3f, fmDecayMs = 250f,
        vibratoHz = 0f, vibratoCents = 0f,
        detuneCents = floatArrayOf(-4f, 4f),
        subLevel = 0.15f,
        lpMultiplier = 10f, lpMaxHz = 5500f,
    )

    private val ORGAN = Params(
        attackMs = 5f, decayMs = 0f, sustain = 1f, releaseMs = 60f,
        harmonics = floatArrayOf(1f, 0.5f, 0.35f, 0.2f, 0.12f, 0.08f, 0.05f),
        harmonicDecayRatio = 1f,
        fmRatio = 1f, fmDepth = 0f, fmDecayMs = 0f,
        vibratoHz = 0f, vibratoCents = 0f,
        detuneCents = floatArrayOf(-2.5f, 2.5f),
        subLevel = 0.3f,
        lpMultiplier = 12f, lpMaxHz = 6000f,
    )

    private val BELL = Params(
        attackMs = 1f, decayMs = 2000f, sustain = 0f, releaseMs = 500f,
        harmonics = floatArrayOf(1f, 0.6f, 0.4f, 0.25f),
        harmonicDecayRatio = 2.5f,
        fmRatio = 3.5f, fmDepth = 4f, fmDecayMs = 400f,
        vibratoHz = 0f, vibratoCents = 0f,
        detuneCents = floatArrayOf(0f),
        subLevel = 0f,
        lpMultiplier = 15f, lpMaxHz = 8000f,
    )

    private fun paramsFor(inst: Instrument): Params = when (inst) {
        Instrument.PIANO -> PIANO
        Instrument.STRINGS -> STRINGS
        Instrument.BRASS -> BRASS
        Instrument.ORGAN -> ORGAN
        Instrument.BELL -> BELL
    }

    private fun instrumentFor(tn: TimedNote): Instrument = when {
        tn.isDrum -> Instrument.PIANO
        tn.channel in 3..4 -> Instrument.STRINGS
        tn.channel in 5..6 -> Instrument.BRASS
        tn.channel in 7..8 -> Instrument.ORGAN
        tn.channel in 10..12 -> Instrument.BELL
        else -> Instrument.PIANO
    }

    // ---------- Питчевый голос ----------

    private fun pitch(
        note: Int,
        samples: Int,
        vol: Float,
        sampleRate: Int,
        inst: Instrument,
    ): FloatArray {
        val p = paramsFor(inst)
        val f0 = midiToHz(note)
        val buf = FloatArray(samples)

        // Огибающая
        var aS = (sampleRate * p.attackMs / 1000f).toInt().coerceAtLeast(1)
        var dS = (sampleRate * p.decayMs / 1000f).toInt()
        var rS = (sampleRate * p.releaseMs / 1000f).toInt().coerceAtLeast(1)

        val totalEnv = aS + dS + rS
        if (samples < totalEnv && totalEnv > 0) {
            val k = samples.toFloat() / totalEnv
            aS = (aS * k).toInt().coerceAtLeast(1)
            dS = (dS * k).toInt()
            rS = (rS * k).toInt().coerceAtLeast(1)
        }
        val sS = (samples - aS - dS - rS).coerceAtLeast(0)

        // FM-огибающая (для пиано, brass, bell)
        val fmDecayS = (sampleRate * p.fmDecayMs / 1000.0).coerceAtLeast(1.0)

        // Нормализация гармоник
        var harmSum = 0f
        for (w in p.harmonics) harmSum += w
        if (harmSum <= 0f) harmSum = 1f

        // Детюн-голоса
        val voices = p.detuneCents.map { c -> f0 * 2.0.pow(c / 1200.0) }

        // Lowpass фильтр этой ноты — cutoff зависит от f0
        val cutoff = (f0 * p.lpMultiplier).coerceAtMost(p.lpMaxHz.toDouble())
        val lpAlpha = (1.0 - exp(-2.0 * PI * cutoff / sampleRate)).toFloat()
        var lpZ = 0f

        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate

            // Vibrato
            val vibRatio = if (p.vibratoHz > 0f) {
                val c = p.vibratoCents * sin(2.0 * PI * p.vibratoHz * t).toFloat()
                2.0.pow(c / 1200.0)
            } else 1.0

            // Сумма гармоник по всем голосам
            var s = 0.0
            for (vf in voices) {
                val f = vf * vibRatio
                for (k in p.harmonics.indices) {
                    // Верхние гармоники гаснут быстрее
                    val hDecayMs = p.decayMs * (
                            if (p.harmonicDecayRatio > 1f)
                                1f / (1f + k * (p.harmonicDecayRatio - 1f) / 3f)
                            else 1f
                            )
                    val hDecayS = (sampleRate * hDecayMs / 1000.0).coerceAtLeast(1.0)
                    val hEnv = exp(-i / hDecayS)
                    s += sin(2.0 * PI * f * (k + 1) * t) * p.harmonics[k] * hEnv
                }
            }
            s /= voices.size
            s /= harmSum

            // FM-слой — «удар молоточка» / «медный окрас» / «резонанс»
            if (p.fmDepth > 0f && p.fmDecayMs > 0f) {
                val fmEnv = exp(-i / fmDecayS)
                val mod = sin(2.0 * PI * f0 * p.fmRatio * t) * p.fmDepth * fmEnv
                val carrier = sin(2.0 * PI * f0 * t + mod)
                s = s * 0.55 + carrier * 0.45
            }

            // Суб-октава для «тела»
            if (p.subLevel > 0f) {
                s += sin(2.0 * PI * (f0 * 0.5) * t) * p.subLevel
            }

            // ADSR
            val env = when {
                i < aS -> i.toFloat() / aS
                i < aS + dS -> {
                    val k = (i - aS).toFloat() / dS.coerceAtLeast(1)
                    1f - k * (1f - p.sustain)
                }
                i < aS + dS + sS -> p.sustain
                else -> {
                    val k = (i - aS - dS - sS).toFloat() / rS.coerceAtLeast(1)
                    p.sustain * (1f - k).coerceAtLeast(0f)
                }
            }

            // Lowpass
            val raw = (s * env * vol).toFloat()
            lpZ += lpAlpha * (raw - lpZ)
            buf[i] = lpZ
        }
        return buf
    }

    // ---------- Перкуссия ----------

    private fun drum(note: Int, samples: Int, vol: Float, sampleRate: Int): FloatArray {
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
            isTom -> midiToHz(note.coerceIn(41, 50) + 20)
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
            val noise = rng.nextFloat() * 2f - 1f
            val t = i.toDouble() / sampleRate
            val tone = if (toneFreq > 0) sin(2.0 * PI * toneFreq * t) else 0.0
            val s = noise * noiseMix + tone.toFloat() * (1f - noiseMix)
            buf[i] = (s * env * vol).toFloat()
        }
        return buf
    }

    private fun midiToHz(note: Int): Double = 440.0 * 2.0.pow((note - 69) / 12.0)
}