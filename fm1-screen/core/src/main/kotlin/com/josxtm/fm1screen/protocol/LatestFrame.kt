// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.protocol

/** One bounding span per LCD row. Two separated edits may transfer their unchanged middle. */
internal class DirtyRows {
    private val start = IntArray(SIDE) { SIDE }
    private val end = IntArray(SIDE)

    fun include(row: Int, from: Int, until: Int) {
        start[row] = minOf(start[row], from)
        end[row] = maxOf(end[row], until)
    }

    fun all() {
        start.fill(0)
        end.fill(SIDE)
    }

    fun clear() {
        start.fill(SIDE)
        end.fill(0)
    }

    fun copy(source: ShortArray, target: ShortArray, alsoDirty: DirtyRows? = null): Int {
        var copied = 0
        for (row in 0 until SIDE) {
            val from = start[row]
            val until = end[row]
            if (from >= until) continue
            val offset = row * SIDE
            source.copyInto(target, offset + from, offset + from, offset + until)
            alsoDirty?.include(row, from, until)
            copied += until - from
        }
        return copied * Short.SIZE_BYTES
    }
}

data class FrameTiming(val firstReceivedNanos: Long, val lastReceivedNanos: Long, val publishedNanos: Long)
data class FrameUpdate(val timing: FrameTiming?, val copiedBytes: Int)

/**
 * Bounded, single-slot handoff. Incremental pixels are merged before a presentation can be superseded.
 * The producer keeps ownership of the decoder's framebuffer and the consumer keeps its own RGB565 array.
 */
class LatestFrame {
    private val staging = ShortArray(SIDE * SIDE)
    private val dirty = DirtyRows()
    private var pending = false
    private var timing: FrameTiming? = null
    private var superseded = 0L
    private var copied = 0L

    @Synchronized
    fun offer(source: FrameBuffer, received: FrameTiming): Boolean {
        copied += source.copyDirty(staging, dirty)
        timing = timing?.let { previous ->
            superseded++
            // Preserve the oldest change still awaiting presentation, even when the latest stamp is replaced.
            received.copy(firstReceivedNanos = minOf(received.firstReceivedNanos, previous.firstReceivedNanos))
        } ?: received
        return schedule()
    }

    @Synchronized
    fun clear(): Boolean {
        if (timing != null) superseded++
        timing = null
        staging.fill(0)
        dirty.all()
        return schedule()
    }

    @Synchronized
    fun consume(destination: ShortArray): FrameUpdate? {
        if (!pending) return null
        require(destination.size == staging.size)
        val bytes = dirty.copy(staging, destination)
        copied += bytes
        dirty.clear()
        val update = FrameUpdate(timing, bytes)
        pending = false
        timing = null
        return update
    }

    private fun schedule(): Boolean {
        val needed = !pending
        pending = true
        return needed
    }

    @Synchronized fun superseded() = superseded
    @Synchronized fun copiedBytes() = copied
}
