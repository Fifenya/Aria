package com.aria.nn

import kotlin.math.exp

internal fun randn(size: Int, scale: Float, rng: kotlin.random.Random): FloatArray =
    FloatArray(size) { (rng.nextFloat() * 2f - 1f) * scale }

internal fun tanh(x: Float): Float {
    if (x.isNaN()) return 0f
    if (x > 20f) return 1f
    if (x < -20f) return -1f
    val e = exp(2f * x)
    return (e - 1f) / (e + 1f)
}

internal fun tanhPrime(t: Float): Float = 1f - t * t

internal fun softmax(logits: FloatArray): FloatArray {
    var max = logits[0]
    for (i in 1 until logits.size) if (logits[i] > max) max = logits[i]
    if (max.isNaN() || max.isInfinite()) {
        val u = 1f / logits.size
        return FloatArray(logits.size) { u }
    }
    var sum = 0f
    val out = FloatArray(logits.size)
    for (i in logits.indices) {
        out[i] = exp(logits[i] - max); sum += out[i]
    }
    if (sum <= 0f || sum.isNaN()) {
        val u = 1f / logits.size
        return FloatArray(logits.size) { u }
    }
    for (i in out.indices) out[i] /= sum
    return out
}

/** Softmax с маской: ноты, которых нет в датасете, получают вероятность 0. */
internal fun softmaxMasked(logits: FloatArray, mask: BooleanArray): FloatArray {
    require(logits.size == mask.size)
    var max = Float.NEGATIVE_INFINITY
    for (i in logits.indices) if (mask[i] && logits[i] > max) max = logits[i]
    if (max == Float.NEGATIVE_INFINITY || max.isNaN() || max.isInfinite()) {
        val u = 1f / logits.size
        return FloatArray(logits.size) { u }
    }
    var sum = 0f
    val out = FloatArray(logits.size)
    for (i in logits.indices) {
        if (mask[i]) {
            out[i] = exp(logits[i] - max); sum += out[i]
        } else out[i] = 0f
    }
    if (sum <= 0f || sum.isNaN()) {
        val u = 1f / logits.size
        return FloatArray(logits.size) { u }
    }
    for (i in out.indices) out[i] /= sum
    return out
}