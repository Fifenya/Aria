package com.aria.nn

import kotlin.math.ln
import kotlin.random.Random

class AriaModel(
    val vocab: Int = 128,
    val embDim: Int = 16,
    val hidden: Int = 32,
    seed: Long = 42L,
) {
    private val rng = Random(seed)
    val emb = Embedding(vocab, embDim, rng)
    val rnn = RnnCell(embDim, hidden, rng)
    val out = Linear(hidden, vocab, rng)

    fun zeroGrad() { emb.zeroGrad(); rnn.zeroGrad(); out.zeroGrad() }
    fun allParams() = emb.params() + rnn.params() + out.params()
    fun allGrads() = emb.grads() + rnn.grads() + out.grads()

    fun trainSequence(seq: IntArray, mask: BooleanArray): Float {
        val n = seq.size
        if (n < 2) return 0f

        val embeds = Array(n) { emb.forward(seq[it]) }
        val hiddens = Array(n) { FloatArray(hidden) }
        val logitsArr = Array(n) { FloatArray(vocab) }

        var hPrev = FloatArray(hidden)
        for (t in 0 until n) {
            val hNew = rnn.step(embeds[t], hPrev)
            hiddens[t] = hNew
            logitsArr[t] = out.forward(hNew)
            hPrev = hNew
        }

        var loss = 0f
        var dhNext = FloatArray(hidden)

        for (t in n - 2 downTo 0) {
            val target = seq[t + 1]
            val probs = softmaxMasked(logitsArr[t], mask)
            loss += -ln(probs[target].coerceAtLeast(1e-9f))

            val dlogits = probs.copyOf()
            dlogits[target] -= 1f
            for (i in dlogits.indices) if (!mask[i]) dlogits[i] = 0f

            val dh = out.backward(hiddens[t], dlogits)
            for (i in 0 until hidden) dh[i] += dhNext[i]

            val hPrevT = if (t > 0) hiddens[t - 1] else FloatArray(hidden)
            val (dx, dhPrev) = rnn.backwardStep(embeds[t], hPrevT, hiddens[t], dh)
            dhNext = dhPrev
            emb.backward(seq[t], dx)
        }
        return loss / (n - 1)
    }

    fun sample(
        seedIdx: Int, length: Int, temperature: Float,
        mask: BooleanArray, rng: Random,
    ): IntArray {
        var h = FloatArray(hidden)
        var cur = seedIdx
        val result = IntArray(length)
        result[0] = cur
        for (t in 1 until length) {
            val e = emb.forward(cur)
            h = rnn.step(e, h)
            val logits = out.forward(h)
            for (i in logits.indices) logits[i] /= temperature
            val probs = softmaxMasked(logits, mask)

            var r = rng.nextFloat()
            var idx = cur
            for (i in probs.indices) {
                if (!mask[i]) continue
                r -= probs[i]
                if (r <= 0f) { idx = i; break }
            }
            result[t] = idx
            cur = idx
        }
        return result
    }
}