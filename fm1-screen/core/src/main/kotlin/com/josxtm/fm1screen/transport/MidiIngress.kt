// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.transport

/** Copies Android MIDI's ephemeral chunks into a bounded queue. Loss is delivered before the next chunk. */
class MidiIngress(private val maxBytes: Int = 65_536, private val maxChunks: Int = 512) {
    sealed interface Entry {
        data class Chunk(val bytes: ByteArray, val receivedNanos: Long) : Entry
        data class Loss(val bytes: Long) : Entry
    }

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
            pending.addLast(Entry.Chunk(bytes.copyOfRange(offset, offset + count), receivedNanos))
            queuedBytes += count
        }
        if (scheduled) return false
        scheduled = true
        return true
    }

    @Synchronized
    fun take(): Entry? {
        if (lost != 0L) return Entry.Loss(lost).also { lost = 0 }
        val chunk = pending.removeFirstOrNull()
        if (chunk == null) scheduled = false else queuedBytes -= chunk.bytes.size
        return chunk
    }
}
