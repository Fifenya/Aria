package com.aria.midi

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

object AacEncoder {

    private const val MIME = "audio/mp4a-latm"
    private const val BITRATE = 192_000

    /**
     * stereo interleaved FloatArray [-1..1] → .m4a AAC файл.
     * Возвращает true, если получилось.
     */
    fun encode(out: File, stereo: FloatArray, sampleRate: Int): Boolean {
        if (stereo.isEmpty()) return false

        val channels = 2
        val pcmShorts = ShortArray(stereo.size) {
            (stereo[it].coerceIn(-1f, 1f) * 32767f).toInt()
                .coerceIn(-32768, 32767).toShort()
        }

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            out.parentFile?.mkdirs()
            if (out.exists()) out.delete()

            val format = MediaFormat.createAudioFormat(MIME, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384 * 4)
            }

            codec = MediaCodec.createEncoderByType(MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val info = MediaCodec.BufferInfo()
            var pcmOffset = 0
            val bytesPerSamplePair = 4   // 2 channels × 2 bytes

            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val remainingShorts = pcmShorts.size - pcmOffset
                        val maxShorts = inBuf.capacity() / 2
                        val toWrite = minOf(remainingShorts, maxShorts)

                        if (toWrite > 0) {
                            val sb = inBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                            sb.put(pcmShorts, pcmOffset, toWrite)
                            pcmOffset += toWrite

                            val ptsUs = (pcmOffset.toLong() * 1_000_000L) /
                                    (sampleRate * channels)
                            codec.queueInputBuffer(inIdx, 0, toWrite * 2, ptsUs, 0)
                        } else {
                            codec.queueInputBuffer(
                                inIdx, 0, 0,
                                (pcmOffset.toLong() * 1_000_000L) / (sampleRate * channels),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEOS = true
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }
                        if (info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                }
            }

            return true
        } catch (_: Exception) {
            try { if (out.exists()) out.delete() } catch (_: Exception) {}
            return false
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}