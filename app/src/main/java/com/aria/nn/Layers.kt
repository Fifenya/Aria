package com.aria.nn

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.sqrt
import kotlin.random.Random

class Linear(val inDim: Int, val outDim: Int, rng: Random) {
    val w = randn(outDim * inDim, sqrt(1f / inDim), rng)
    val b = FloatArray(outDim)
    val gw = FloatArray(outDim * inDim)
    val gb = FloatArray(outDim)

    fun forward(x: FloatArray): FloatArray {
        val y = FloatArray(outDim)
        for (o in 0 until outDim) {
            var s = b[o]
            val wo = o * inDim
            for (i in 0 until inDim) s += w[wo + i] * x[i]
            y[o] = s
        }
        return y
    }

    fun backward(x: FloatArray, dy: FloatArray): FloatArray {
        val dx = FloatArray(inDim)
        for (o in 0 until outDim) {
            val g = dy[o]
            if (g == 0f) continue
            val wo = o * inDim
            gb[o] += g
            for (i in 0 until inDim) {
                gw[wo + i] += g * x[i]
                dx[i] += g * w[wo + i]
            }
        }
        return dx
    }

    fun zeroGrad() { gw.fill(0f); gb.fill(0f) }
    fun params() = listOf(w, b)
    fun grads() = listOf(gw, gb)

    fun save(out: DataOutputStream) {
        w.forEach { out.writeFloat(it) }
        b.forEach { out.writeFloat(it) }
    }

    fun load(inp: DataInputStream) {
        for (i in w.indices) w[i] = inp.readFloat()
        for (i in b.indices) b[i] = inp.readFloat()
    }
}

class Embedding(val vocab: Int, val dim: Int, rng: Random) {
    val w = randn(vocab * dim, 0.1f, rng)
    val gw = FloatArray(vocab * dim)

    fun forward(idx: Int): FloatArray {
        val out = FloatArray(dim)
        val off = idx * dim
        System.arraycopy(w, off, out, 0, dim)
        return out
    }

    fun backward(idx: Int, dy: FloatArray) {
        val off = idx * dim
        for (i in 0 until dim) gw[off + i] += dy[i]
    }

    fun zeroGrad() { gw.fill(0f) }
    fun params() = listOf(w)
    fun grads() = listOf(gw)

    fun save(out: DataOutputStream) { w.forEach { out.writeFloat(it) } }
    fun load(inp: DataInputStream) { for (i in w.indices) w[i] = inp.readFloat() }
}

class RnnCell(val inDim: Int, val hidden: Int, rng: Random) {
    val wx = randn(hidden * inDim, sqrt(1f / inDim), rng)
    val wh = randn(hidden * hidden, sqrt(1f / hidden), rng)
    val b = FloatArray(hidden)
    val gwx = FloatArray(hidden * inDim)
    val gwh = FloatArray(hidden * hidden)
    val gb = FloatArray(hidden)

    fun step(x: FloatArray, hPrev: FloatArray): FloatArray {
        val h = FloatArray(hidden)
        for (i in 0 until hidden) {
            var s = b[i]
            val wi = i * inDim
            for (j in 0 until inDim) s += wx[wi + j] * x[j]
            val hi = i * hidden
            for (j in 0 until hidden) s += wh[hi + j] * hPrev[j]
            h[i] = tanh(s)
        }
        return h
    }

    fun backwardStep(
        x: FloatArray,
        hPrev: FloatArray,
        h: FloatArray,
        dy: FloatArray,
    ): Pair<FloatArray, FloatArray> {
        val dz = FloatArray(hidden) { dy[it] * tanhPrime(h[it]) }
        val dx = FloatArray(inDim)
        val dhPrev = FloatArray(hidden)

        for (i in 0 until hidden) {
            val g = dz[i]
            if (g == 0f) continue
            gb[i] += g
            val wi = i * inDim
            for (j in 0 until inDim) {
                gwx[wi + j] += g * x[j]
                dx[j] += g * wx[wi + j]
            }
            val hi = i * hidden
            for (j in 0 until hidden) {
                gwh[hi + j] += g * hPrev[j]
                dhPrev[j] += g * wh[hi + j]
            }
        }
        return dx to dhPrev
    }

    fun zeroGrad() { gwx.fill(0f); gwh.fill(0f); gb.fill(0f) }
    fun params() = listOf(wx, wh, b)
    fun grads() = listOf(gwx, gwh, gb)

    fun save(out: DataOutputStream) {
        wx.forEach { out.writeFloat(it) }
        wh.forEach { out.writeFloat(it) }
        b.forEach { out.writeFloat(it) }
    }

    fun load(inp: DataInputStream) {
        for (i in wx.indices) wx[i] = inp.readFloat()
        for (i in wh.indices) wh[i] = inp.readFloat()
        for (i in b.indices) b[i] = inp.readFloat()
    }
}