// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.capture

import android.graphics.Bitmap
import android.media.Image
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
            val converter = YuvFrame()
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
                            val image = checkNotNull(codec.getInputImage(index)) { "Codificador sin planos YUV" }
                            converter.copy(latest, image)
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

    /** Reuses the RGB and planar YUV buffers. An immutable LCD bitmap is converted once even when repeated. */
    private class YuvFrame {
        private val widthHalf = SIDE / 2
        private val rgb = IntArray(SIDE * SIDE)
        private val y = ByteArray(SIDE * SIDE)
        private val u = ByteArray(widthHalf * widthHalf)
        private val v = ByteArray(widthHalf * widthHalf)
        private var source: Bitmap? = null

        fun copy(bitmap: Bitmap, image: Image) {
            if (source !== bitmap) {
                bitmap.getPixels(rgb, 0, SIDE, 0, 0, SIDE, SIDE)
                for (row in 0 until SIDE) for (x in 0 until SIDE) {
                    val index = row * SIDE + x
                    val pixel = rgb[index]
                    val r = pixel ushr 16 and 255
                    val g = pixel ushr 8 and 255
                    val b = pixel and 255
                    y[index] = ((((66 * r + 129 * g + 25 * b + 128) shr 8)) + 16).toByte()
                    if (row % 2 == 0 && x % 2 == 0) {
                        val c = row / 2 * widthHalf + x / 2
                        u[c] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
                        v[c] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
                    }
                }
                source = bitmap
            }
            val planes = image.planes
            write(planes[0], y, SIDE)
            write(planes[1], u, widthHalf)
            write(planes[2], v, widthHalf)
        }

        private fun write(plane: Image.Plane, pixels: ByteArray, width: Int) {
            val buffer = plane.buffer
            for (row in 0 until width) {
                val dest = row * plane.rowStride
                val from = row * width
                if (plane.pixelStride == 1) {
                    buffer.position(dest)
                    buffer.put(pixels, from, width)
                } else {
                    for (x in 0 until width) buffer.put(dest + x * plane.pixelStride, pixels[from + x])
                }
            }
        }
    }

    companion object {
        private const val FPS = 30L
        private const val MAX_US = 20L * 60 * 1_000_000
    }
}
