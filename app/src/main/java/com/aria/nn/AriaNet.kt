package com.aria.nn

import kotlin.math.sqrt
import kotlin.random.Random

class AriaNet(
    val vocab: Int = 128,
    embDim: Int = 16,
    hidden: Int = 32,
    lr: Float = 0.001f,
    seed: Long = 42L,
) {
    val model = AriaModel(vocab, embDim, hidden, seed)
    val opt = Adam(model.allParams(), model.allGrads(), lr)
    private val sampleRng = Random(seed + 1)

    var step: Long = 0
        private set

    internal fun restoreStep(s: Long) { step = s }

    fun paramCount(): Long {
        var t = 0L
        model.allParams().forEach { t += it.size }
        return t
    }

    fun trainStep(seq: IntArray, mask: BooleanArray, clipNorm: Float = 1f): Float {
        model.zeroGrad()
        val loss = model.trainSequence(seq, mask)
        if (loss.isNaN() || loss.isInfinite()) { step++; return loss }
        if (!clipGrads(model.allGrads(), clipNorm)) { step++; return loss }
        opt.step()
        step++
        return loss
    }

    private fun clipGrads(grads: List<FloatArray>, maxNorm: Float): Boolean {
        var sq = 0f
        grads.forEach { a -> a.forEach { sq += it * it } }
        if (sq.isNaN() || sq.isInfinite()) return false
        val norm = sqrt(sq)
        if (norm > maxNorm && norm > 0f) {
            val sc = maxNorm / norm
            grads.forEach { a -> for (i in a.indices) a[i] *= sc }
        }
        return true
    }

    fun sample(
        seedIdx: Int, length: Int,
        temperature: Float = 0.8f, mask: BooleanArray,
    ): IntArray = model.sample(seedIdx, length, temperature, mask, sampleRng)
}