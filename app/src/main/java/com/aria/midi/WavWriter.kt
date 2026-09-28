package com.aria.midi

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {

    /** stereo — interleaved L, R, L, R... в [-1..1]. */
    fun write(out: File, stereo: FloatArray, sampleRate: Int) {
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val dataSize = stereo.size * bytesPerSample
        val byteRate = sampleRate * channels * bytesPerSample

        out.parentFile?.mkdirs()
        FileOutputStream(out).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)                    // subchunk1 size
            header.putShort(1)                   // PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((channels * bytesPerSample).toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())

            val data = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (f in stereo) {
                val clamped = f.coerceIn(-1f, 1f)
                val s = (clamped * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                data.putShort(s)
            }
            fos.write(data.array())
            fos.flush()
        }
    }
}