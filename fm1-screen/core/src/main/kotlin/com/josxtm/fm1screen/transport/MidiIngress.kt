// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.transport

/** Copies Android MIDI's ephemeral chunks into a bounded queue. Loss is delivered before the next chunk. */
class MidiIngress @JvmOverloads constructor(
    private val maxBytes: Int = LIMIT_BYTES, private val maxChunks: Int = LIMIT_CHUNKS, private val nanoTime: (() -> Long)? = null,
) {
    sealed interface Entry {
        data class Chunk(val bytes: ByteArray, val receivedNanos: Long, val queuedNanos: Long) : Entry
        data class Loss(val bytes: Long) : Entry
    }

    data class Depth(val chunks: Int = 0, val bytes: Int = 0, val peakChunks: Int = 0, val peakBytes: Int = 0)

    companion object {
        const val LIMIT_BYTES = 65_536
        const val LIMIT_CHUNKS = 512
    }

    private var peakChunks = 0
    private var peakBytes = 0
    private val pending = ArrayDeque<Entry.Chunk>()
    private var queuedBytes = 0
    private var lost = 0L
    private var scheduled = false

    /** True only when the caller needs to schedule a drain. */
    @Synchronized
    fun offer(bytes: ByteArray, offset: Int, count: Int, receivedNanos: Long): Boolean {
        if (count <= 0 || offset < 0 || count > bytes.size || offset > bytes.size - count) return false
        if (count > maxBytes - queuedBytes || pending.size >= maxChunks) {
            lost += queuedBytes.toLong() + count
            pending.clear()
            queuedBytes = 0
        } else {
            val copy = bytes.copyOfRange(offset, offset + count)
            pending.addLast(Entry.Chunk(copy, receivedNanos, nanoTime?.invoke() ?: receivedNanos))
            queuedBytes += count
            peakChunks = maxOf(peakChunks, pending.size)
            peakBytes = maxOf(peakBytes, queuedBytes)
        }
        if (scheduled) return false
        scheduled = true
        return true
    }

    @Synchronized fun depth() = Depth(pending.size, queuedBytes, peakChunks, peakBytes)

    @Synchronized
    fun take(): Entry? {
        if (lost != 0L) return Entry.Loss(lost).also { lost = 0 }
        val chunk = pending.removeFirstOrNull()
        if (chunk == null) scheduled = false else queuedBytes -= chunk.bytes.size
        return chunk
    }
}
