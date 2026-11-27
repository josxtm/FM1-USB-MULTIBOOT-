// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.capture

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.josxtm.fm1screen.protocol.SIDE
import java.io.File
import kotlin.concurrent.thread

/** Only the incoming display is recorded. One latest frame is shared between the receiver and the encoder. */
class Recording(
    private val path: File,
    first: Bitmap,
    private val finished: (Result<File>) -> Unit,
) {
    @Volatile private var active = true
    @Volatile private var latest = first

    init {
        thread(name = "FM1-MP4") {
            val result = runCatching { encode(); path }
            if (result.isFailure) path.delete()
            finished(result)
        }
    }

    fun update(frame: Bitmap) { latest = frame }
    fun stop() { active = false }

    private fun encode() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, SIDE, SIDE).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS.toInt())
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var started = false
        var codecStarted = false
        try {
            val container = MediaMuxer(path.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = container
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            codecStarted = true
            val info = MediaCodec.BufferInfo()
            val pixels = IntArray(SIDE * SIDE)
            val start = System.nanoTime()
            var track = -1
            var next = 0L
            var inputEnded = false
            var outputEnded = false
            var endDeadline = Long.MAX_VALUE
            while (!outputEnded && System.nanoTime() < endDeadline) {
                val elapsed = (System.nanoTime() - start) / 1000L
                val videoDone = (!active || elapsed >= MAX_US) && next > 0
                if (!inputEnded && (videoDone || elapsed >= next * 1_000_000L / FPS)) {
                    val index = codec.dequeueInputBuffer(5000L)
                    if (index >= 0) {
                        val stamp = next * 1_000_000L / FPS
                        if (videoDone) {
                            codec.queueInputBuffer(index, 0, 0, stamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                            endDeadline = System.nanoTime() + 4_000_000_000L
                        } else {
                            latest.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)
                            val image = checkNotNull(codec.getInputImage(index)) { "Codificador sin planos YUV" }
                            val planes = image.planes
                            for (y in 0 until SIDE) for (x in 0 until SIDE) {
                                val rgb = pixels[y * SIDE + x]
                                val r = rgb ushr 16 and 255
                                val g = rgb ushr 8 and 255
                                val b = rgb and 255
                                val luma = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                                planes[0].buffer.put(y * planes[0].rowStride + x * planes[0].pixelStride, luma.toByte())
                                if (x % 2 == 0 && y % 2 == 0) {
                                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                                    planes[1].buffer.put(y / 2 * planes[1].rowStride + x / 2 * planes[1].pixelStride, u.toByte())
                                    planes[2].buffer.put(y / 2 * planes[2].rowStride + x / 2 * planes[2].pixelStride, v.toByte())
                                }
                            }
                            codec.queueInputBuffer(index, 0, SIDE * SIDE * 3 / 2, stamp, 0)
                            next++
                            if (elapsed > (next + FPS) * 1_000_000L / FPS) next = elapsed * FPS / 1_000_000L
                        }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, if (inputEnded) 10_000L else 3000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = container.addTrack(codec.outputFormat)
                        container.start()
                        started = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> if (!inputEnded) Thread.sleep(3)
                    else -> if (index >= 0) {
                        val data = checkNotNull(codec.getOutputBuffer(index))
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(started)
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            container.writeSampleData(track, data, info)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            check(outputEnded && started) { "El codificador no finalizó el MP4" }
            container.stop()
            started = false
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (codecStarted) runCatching { codec.stop() }
            codec.release()
        }
    }

    companion object {
        private const val FPS = 30L
        private const val MAX_US = 20L * 60 * 1_000_000
    }
}
