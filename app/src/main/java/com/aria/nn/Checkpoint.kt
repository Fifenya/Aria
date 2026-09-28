package com.aria.nn

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Бинарный чекпойнт Aria.
 *
 *   magic "ARIA" | format_version
 *   name_len | name | model_version | params
 *   vocab | emb_dim | hidden
 *   epoch | best_loss | step
 *   embedding | rnn | out | adam
 */
object Checkpoint {
    private const val MAGIC = "ARIA"
    private const val FORMAT_VERSION = 2

    data class Meta(
        val info: ModelInfo,
        val epoch: Long,
        val bestLoss: Float,
        val step: Long,
    )

    fun save(
        file: File,
        net: AriaNet,
        info: ModelInfo,
        epoch: Long,
        bestLoss: Float,
    ) {
        file.parentFile?.mkdirs()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeBytes(MAGIC)
            out.writeInt(FORMAT_VERSION)

            // model info
            out.writeInt(info.name.length)
            info.name.forEach { out.writeChar(it.code) }
            out.writeInt(info.version)
            out.writeLong(info.params)

            // arch
            out.writeInt(net.model.vocab)
            out.writeInt(net.model.embDim)
            out.writeInt(net.model.hidden)

            // progress
            out.writeLong(epoch)
            out.writeFloat(bestLoss)
            out.writeLong(net.step)

            // weights
            net.model.emb.save(out)
            net.model.rnn.save(out)
            net.model.out.save(out)

            // optimizer
            net.opt.save(out)
        }
    }

    fun load(file: File, net: AriaNet): Meta {
        DataInputStream(file.inputStream().buffered()).use { inp ->
            val magic = ByteArray(4)
            inp.readFully(magic)
            require(String(magic) == MAGIC) { "not an aria checkpoint" }
            val fmt = inp.readInt()
            require(fmt == FORMAT_VERSION) { "unsupported format version $fmt" }

            val nameLen = inp.readInt()
            val nameChars = CharArray(nameLen) { inp.readChar() }
            val name = String(nameChars)
            val modelVersion = inp.readInt()
            val params = inp.readLong()
            val info = ModelInfo(name, modelVersion, params)

            val vocab = inp.readInt()
            val embDim = inp.readInt()
            val hidden = inp.readInt()
            require(vocab == net.model.vocab) { "vocab mismatch: $vocab vs ${net.model.vocab}" }
            require(embDim == net.model.embDim) { "embDim mismatch" }
            require(hidden == net.model.hidden) { "hidden mismatch" }

            val epoch = inp.readLong()
            val bestLoss = inp.readFloat()
            val step = inp.readLong()

            net.model.emb.load(inp)
            net.model.rnn.load(inp)
            net.model.out.load(inp)

            net.opt.load(inp)

            net.restoreStep(step)
            return Meta(info, epoch, bestLoss, step)
        }
    }
}