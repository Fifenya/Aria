package com.aria.midi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import java.util.Random

/**
 * Многоголосый стерео-синтезатор.
 * Возвращает FloatArray interleaved stereo (L, R, L, R...).
 */
object Synth {

    private const val ATTACK_MS = 8
    private const val DECAY_MS = 350

    fun render(
        notes: List<TimedNote>,
        sampleRate: Int,
        gain: Float = 0.9f,
    ): FloatArray {
        if (notes.isEmpty()) return FloatArray(0)

        val totalMs = notes.last().startMs + notes.last().durationMs
        val totalSamples = (totalMs * sampleRate / 1000).toInt().coerceAtLeast(sampleRate)
        val stereo = FloatArray(totalSamples * 2)

        for (tn in notes) {
            val startSample = (tn.startMs * sampleRate / 1000).toInt()
            val durSamples = (tn.durationMs.toLong() * sampleRate / 1000).toInt()
                .coerceAtLeast(32)
            val endSample = (startSample + durSamples).coerceAtMost(totalSamples)
            if (startSample >= totalSamples) continue
            val len = endSample - startSample
            val vol = gain * (tn.velocity / 127f).coerceIn(0.05f, 1f)

            val mono = if (tn.isDrum) {
                drum(note = tn.note, samples = len, volume = vol, sampleRate = sampleRate)
            } else {
                notePitch(note = tn.note, samples = len, volume = vol, sampleRate = sampleRate)
            }

            // Панорама: низкие — влево, высокие — вправо
            val pan = if (tn.isDrum) 0f else {
                ((tn.note - 60) / 24f).coerceIn(-1f, 1f)
            }
            val panL = (1f - pan) * 0.5f + 0.5f
            val panR = (1f + pan) * 0.5f + 0.5f

            for (i in 0 until len) {
                stereo[(startSample + i) * 2]     += mono[i] * panL
                stereo[(startSample + i) * 2 + 1] += mono[i] * panR
            }
        }

        return stereo
    }

    /** Питчевая нота: 3 гармоники + атака + экспоненциальный спад. */
    private fun notePitch(note: Int, samples: Int, volume: Float, sampleRate: Int): FloatArray {
        val f = 440.0 * 2.0.pow((note - 69) / 12.0)
        val buf = FloatArray(samples)
        val attack = (sampleRate * ATTACK_MS / 1000).coerceAtLeast(1)
        val decay = (sampleRate * DECAY_MS / 1000).coerceAtLeast(1)

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

    /** Перкуссия: kick/snare/hihat/tom, шум+тон по GM-ноте. */
    private fun drum(note: Int, samples: Int, volume: Float, sampleRate: Int): FloatArray {
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