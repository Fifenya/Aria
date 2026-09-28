package com.aria.nn

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.pow
import kotlin.math.sqrt

class Adam(
    private val params: List<FloatArray>,
    private val grads: List<FloatArray>,
    val lr: Float = 0.01f,
) {
    private val m = params.map { FloatArray(it.size) }
    private val v = params.map { FloatArray(it.size) }
    var t: Int = 0
        private set
    private val b1 = 0.9f
    private val b2 = 0.999f
    private val eps = 1e-8f

    fun step() {
        t++
        val bc1 = 1f - b1.toDouble().pow(t.toDouble()).toFloat()
        val bc2 = 1f - b2.toDouble().pow(t.toDouble()).toFloat()
        for (p in params.indices) {
            val par = params[p]
            val gr = grads[p]
            val mP = m[p]
            val vP = v[p]
            for (i in par.indices) {
                mP[i] = b1 * mP[i] + (1 - b1) * gr[i]
                vP[i] = b2 * vP[i] + (1 - b2) * gr[i] * gr[i]
                val mHat = mP[i] / bc1
                val vHat = vP[i] / bc2
                par[i] -= lr * mHat / (sqrt(vHat) + eps)
            }
        }
    }

    fun save(out: DataOutputStream) {
        out.writeInt(t)
        m.forEach { arr -> arr.forEach { out.writeFloat(it) } }
        v.forEach { arr -> arr.forEach { out.writeFloat(it) } }
    }

    fun load(inp: DataInputStream) {
        t = inp.readInt()
        m.forEach { arr -> for (i in arr.indices) arr[i] = inp.readFloat() }
        v.forEach { arr -> for (i in arr.indices) arr[i] = inp.readFloat() }
    }
}