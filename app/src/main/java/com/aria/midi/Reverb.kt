package com.aria.midi

import kotlin.math.abs

/**
 * Простой stereo-reverb: два задержания с feedback, разведённых по каналам.
 * Работает на interleaved stereo FloatArray (L, R, L, R...).
 */
class Reverb(
    private val sampleRate: Int,
    private val roomSize: Float = 0.65f,
    private val damping: Float = 0.35f,
    private val mix: Float = 0.28f,
) {

    // Times tuned to не конфликтовать по фазе между L/R
    private val tap1L = (sampleRate * 0.031f).toInt().coerceAtLeast(1)
    private val tap2L = (sampleRate * 0.067f).toInt().coerceAtLeast(1)
    private val tap1R = (sampleRate * 0.037f).toInt().coerceAtLeast(1)
    private val tap2R = (sampleRate * 0.073f).toInt().coerceAtLeast(1)

    private val buf1L = FloatArray(tap1L)
    private val buf2L = FloatArray(tap2L)
    private val buf1R = FloatArray(tap1R)
    private val buf2R = FloatArray(tap2R)

    private var p1L = 0; private var p2L = 0
    private var p1R = 0; private var p2R = 0

    private var lpL1 = 0f; private var lpL2 = 0f
    private var lpR1 = 0f; private var lpR2 = 0f

    fun process(stereo: FloatArray): FloatArray {
        val n = stereo.size / 2
        val out = FloatArray(stereo.size)
        val fb = roomSize.coerceIn(0f, 0.92f)
        val dp = damping.coerceIn(0f, 0.95f)
        val wet = mix.coerceIn(0f, 0.9f)
        val dry = 1f - wet

        for (i in 0 until n) {
            val l = stereo[i * 2]
            val r = stereo[i * 2 + 1]

            // Читаем хвосты
            val t1L = buf1L[p1L]
            val t2L = buf2L[p2L]
            val t1R = buf1R[p1R]
            val t2R = buf2R[p2R]

            // Однополюсный ФНЧ на хвосте (damping)
            lpL1 = t1L * (1f - dp) + lpL1 * dp
            lpL2 = t2L * (1f - dp) + lpL2 * dp
            lpR1 = t1R * (1f - dp) + lpR1 * dp
            lpR2 = t2R * (1f - dp) + lpR2 * dp

            // Записываем вход + feedback от хвостов
            buf1L[p1L] = l + (lpL1 + lpR1 * 0.3f) * fb
            buf2L[p2L] = l + (lpL2 + lpR2 * 0.3f) * fb * 0.7f
            buf1R[p1R] = r + (lpR1 + lpL1 * 0.3f) * fb
            buf2R[p2R] = r + (lpR2 + lpL2 * 0.3f) * fb * 0.7f

            p1L = (p1L + 1) % tap1L
            p2L = (p2L + 1) % tap2L
            p1R = (p1R + 1) % tap1R
            p2R = (p2R + 1) % tap2R

            val wl = (t1L + t2L * 0.6f) * wet
            val wr = (t1R + t2R * 0.6f) * wet

            out[i * 2]     = l * dry + wl
            out[i * 2 + 1] = r * dry + wr
        }

        // Нормализация, если накопилось больше единицы
        var maxAbs = 0f
        for (v in out) { val a = abs(v); if (a > maxAbs) maxAbs = a }
        if (maxAbs > 1f) {
            val sc = 1f / maxAbs
            for (i in out.indices) out[i] *= sc
        }
        return out
    }
}