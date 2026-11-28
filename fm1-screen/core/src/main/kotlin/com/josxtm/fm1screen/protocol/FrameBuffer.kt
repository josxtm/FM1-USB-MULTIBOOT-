// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.protocol

class FrameBuffer {
    private val pixels = ShortArray(SIDE * SIDE)
    private val known = BooleanArray(SIDE * SIDE)
    private val dirty = DirtyRows()
    var covered = 0
        private set

    fun apply(patch: Patch): Boolean {
        require(patch.x in 0 until SIDE && patch.y in 0 until SIDE)
        require(patch.width in 1..(SIDE - patch.x) && patch.height in 1..(SIDE - patch.y))
        require(patch.rgb565.size == patch.width * patch.height)
        var changed = false
        for (row in 0 until patch.height) {
            val start = (patch.y + row) * SIDE + patch.x
            var first = patch.width
            var last = -1
            for (column in 0 until patch.width) {
                val index = start + column
                val value = patch.rgb565[row * patch.width + column]
                val seen = known[index]
                if (seen && pixels[index] == value) continue
                pixels[index] = value
                if (!seen) {
                    known[index] = true
                    covered++
                }
                first = minOf(first, column)
                last = column
            }
            if (last >= 0) {
                dirty.include(patch.y + row, patch.x + first, patch.x + last + 1)
                changed = true
            }
        }
        return changed
    }

    internal fun copyDirty(destination: ShortArray, pending: DirtyRows): Int =
        dirty.copy(pixels, destination, pending)

    internal fun clearDirty() = dirty.clear()

    fun freeze() = ScreenFrame(pixels.copyOf())

    fun snapshot(): IntArray = IntArray(pixels.size) { argb(pixels[it].toInt() and 0xFFFF) }

    fun forgetCoverage() {
        known.fill(false)
        covered = 0
    }

    fun reset() {
        pixels.fill(0)
        dirty.clear()
        forgetCoverage()
    }

    companion object {
        private const val OPAQUE = -0x1000000

        fun argb(color: Int): Int {
            val r = color ushr 11 and 31
            val g = color ushr 5 and 63
            val b = color and 31
            return OPAQUE or (((r shl 3) or (r ushr 2)) shl 16) or
                (((g shl 2) or (g ushr 4)) shl 8) or ((b shl 3) or (b ushr 2))
        }
    }
}

/** Immutable RGB565 snapshot. Adapters can reuse their own rendering buffer. */
class ScreenFrame internal constructor(private val pixels: ShortArray) {
    fun copyRgb565To(destination: ShortArray) {
        require(destination.size == pixels.size)
        pixels.copyInto(destination)
    }
}
