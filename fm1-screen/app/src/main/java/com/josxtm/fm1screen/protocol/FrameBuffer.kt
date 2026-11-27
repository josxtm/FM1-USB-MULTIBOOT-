// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.protocol

class FrameBuffer {
    private val pixels = IntArray(SIDE * SIDE) { OPAQUE }
    private val known = BooleanArray(SIDE * SIDE)
    var covered = 0
        private set

    fun apply(patch: Patch) {
        require(patch.x in 0 until SIDE && patch.y in 0 until SIDE)
        require(patch.width in 1..(SIDE - patch.x) && patch.height in 1..(SIDE - patch.y))
        require(patch.rgb565.size == patch.width * patch.height)
        for (row in 0 until patch.height) {
            val start = (patch.y + row) * SIDE + patch.x
            for (column in 0 until patch.width) {
                val index = start + column
                pixels[index] = argb(patch.rgb565[row * patch.width + column].toInt() and 65535)
                if (!known[index]) {
                    known[index] = true
                    covered++
                }
            }
        }
    }

    fun snapshot(): IntArray = pixels.copyOf()

    fun reset() {
        pixels.fill(OPAQUE)
        known.fill(false)
        covered = 0
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
