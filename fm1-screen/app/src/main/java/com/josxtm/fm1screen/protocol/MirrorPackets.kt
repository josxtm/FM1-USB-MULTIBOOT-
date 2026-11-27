// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.protocol

const val SIDE = 240
private const val MAX_SYSEX = 4096

enum class Wire(val title: String) {
    AUTO("Auto"), BASE("fm1-base"), BIG2("Big Screen V2"), BIG1("Big Screen V1")
}

data class Patch(val x: Int, val y: Int, val width: Int, val height: Int, val rgb565: ShortArray)

data class PanelState(
    val heldButtons: Int, val heldKeys: Int, val encoderSteps: List<Int>,
    val master: Int, val litButtons: Int, val litKeys: Int, val playRed: Boolean,
    val dimButtons: Int, val dimKeys: Int,
)

sealed interface MirrorEvent {
    data class Hello(val version: Int, val enabled: Boolean) : MirrorEvent
    data class Pixels(val patch: Patch, val sequence: Int? = null) : MirrorEvent
    data class Panel(val state: PanelState) : MirrorEvent
    data class Ack(val sequence: Int, val operation: Int, val ok: Boolean) : MirrorEvent
    data object Sync : MirrorEvent
    data object Invalid : MirrorEvent
}

class SysExFramer(private val receive: (ByteArray) -> Unit) {
    private val buffer = ByteArray(MAX_SYSEX)
    private var length = 0

    fun feed(bytes: ByteArray, offset: Int, count: Int) {
        require(offset >= 0 && count in 0..bytes.size && offset <= bytes.size - count)
        for (index in offset until offset + count) {
            val value = bytes[index].toInt() and 255
            when {
                value >= 0xF8 -> Unit
                value == 0xF0 -> {
                    buffer[0] = bytes[index]
                    length = 1
                }
                length == 0 -> Unit
                length == buffer.size -> length = 0
                value == 0xF7 -> {
                    buffer[length++] = bytes[index]
                    val message = buffer.copyOf(length)
                    length = 0
                    receive(message)
                }
                value >= 0x80 -> length = 0
                else -> buffer[length++] = bytes[index]
            }
        }
    }
}

object MirrorPackets {
    private fun ByteArray.u(index: Int) = this[index].toInt() and 255
    private fun ByteArray.le7(index: Int) = u(index) or (u(index + 1) shl 7)
    private fun ByteArray.le16(index: Int) = u(index) or (u(index + 1) shl 8)
    private fun ByteArray.le32(index: Int) = le16(index) or (le16(index + 2) shl 16)

    fun identify(message: ByteArray): Wire? {
        if (message.size < 6 || message.size > MAX_SYSEX ||
            message.u(0) != 0xF0 || message.u(message.lastIndex) != 0xF7) return null
        if ((1 until message.lastIndex).any { message.u(it) >= 128 }) return null
        return when {
            message.u(1) == 0x7D && message.u(2) == 0x42 && message.u(3) == 0x53 -> Wire.BIG2
            message.u(1) == 0x7D && message.u(2) == 0x46 && message.u(3) == 0x53 -> Wire.BIG1
            message.u(1) == 0x00 && message.u(2) == 0x32 &&
                message.size >= 10 && (message.u(3) and 3) == 1 &&
                ((message.u(3) ushr 2) or ((message.u(4) and 7) shl 5)) in 0x50..0x54 -> Wire.BASE
            else -> null
        }
    }

    fun decode(wire: Wire, message: ByteArray): MirrorEvent? {
        if (identify(message) != wire) return null
        return when (wire) {
            Wire.BIG1 -> decodeBig1(message)
            Wire.BIG2 -> decodeBig2(message)
            Wire.BASE -> decodeBase(message)
            Wire.AUTO -> null
        }
    }

    fun request(wire: Wire, refresh: Boolean): ByteArray = when (wire) {
        Wire.BASE -> basePacket(0x50, intArrayOf(if (refresh) 2 else 1))
        Wire.BIG1 -> byteArrayOf(0xF0.toByte(), 0x7D, 0x46, 0x53, if (refresh) 1 else 0, 0xF7.toByte())
        Wire.BIG2 -> byteArrayOf(0xF0.toByte(), 0x7D, 0x42, 0x53, 0, 2, if (refresh) 1 else 0, 0xF7.toByte())
        Wire.AUTO -> error("Select a concrete protocol")
    }

    fun releaseMirror() = basePacket(0x50, intArrayOf(0))

    // Public entry points only expose documented panel operations.
    fun button(sequence: Int, index: Int, down: Boolean): ByteArray {
        require(index in 0..13)
        return panel(sequence, 1, index, if (down) 1 else 0)
    }

    fun key(sequence: Int, index: Int, down: Boolean): ByteArray {
        require(index in 0..26)
        return panel(sequence, 2, index, if (down) 1 else 0)
    }

    fun encoder(sequence: Int, index: Int, delta: Int): ByteArray {
        require(index in 0..6 && delta in -127..127 && delta != 0)
        return panel(sequence, 3, index, delta and 255)
    }

    fun master(sequence: Int, position: Int): ByteArray {
        require(position in 0..4096 || position == 0xFFFF)
        return panel(sequence, 4, position and 255, position ushr 8)
    }

    fun releasePanel(sequence: Int) = panel(sequence, 0, 0, 0)

    private fun panel(sequence: Int, operation: Int, a: Int, b: Int) =
        basePacket(0x54, intArrayOf(sequence and 255, operation, a, b, 0))

    private fun basePacket(command: Int, body: IntArray): ByteArray {
        val raw = intArrayOf(0, 0x59, command, body.size, 0, 0) + body + (body.sum().inv() and 255)
        val output = ByteArray((raw.size * 8 + 6) / 7 + 2)
        output[0] = 0xF0.toByte()
        output[output.lastIndex] = 0xF7.toByte()
        var bits = 0
        var accumulator = 0
        var index = 1
        for (value in raw) {
            accumulator = accumulator or (value shl bits)
            bits += 8
            while (bits >= 7) {
                output[index++] = (accumulator and 127).toByte()
                accumulator = accumulator ushr 7
                bits -= 7
            }
        }
        if (bits != 0) output[index] = accumulator.toByte()
        return output
    }

    private fun unpackBase(message: ByteArray): ByteArray? {
        val output = ByteArray((message.size - 2) * 7 / 8)
        var bits = 0
        var accumulator = 0
        var index = 0
        for (i in 1 until message.lastIndex) {
            accumulator = accumulator or (message.u(i) shl bits)
            bits += 7
            if (bits >= 8) {
                output[index++] = accumulator.toByte()
                accumulator = accumulator ushr 8
                bits -= 8
            }
        }
        return output.takeIf {
            accumulator == 0 && message.size - 2 == (output.size * 8 + 6) / 7
        }
    }

    private fun rectangle(x: Int, y: Int, w: Int, h: Int) =
        x in 0 until SIDE && y in 0 until SIDE && w in 1..(SIDE - x) && h in 1..(SIDE - y)

    private fun decodeBig1(m: ByteArray): MirrorEvent? {
        if (m.u(4) != 1) return null
        if (m.size !in 14..70 || (m.lastIndex - 9) % 4 != 0) return MirrorEvent.Invalid
        val y = m.le7(5)
        val x = m.le7(7)
        if (!rectangle(x, y, 1, 1)) return MirrorEvent.Invalid
        val pixels = ShortArray(SIDE - x)
        var written = 0
        for (i in 9 until m.lastIndex step 4) {
            val count = m.u(i)
            if (count == 0 || count > pixels.size - written || m.u(i + 3) > 3) return MirrorEvent.Invalid
            val color = m.le7(i + 1) or (m.u(i + 3) shl 14)
            pixels.fill(color.toShort(), written, written + count)
            written += count
        }
        return MirrorEvent.Pixels(Patch(x, y, written, 1, pixels.copyOf(written)))
    }

    private fun decodeBig2(m: ByteArray): MirrorEvent? = when (m.u(4)) {
        1 -> if (m.size == 12 && m.u(5) == 2 && m.le7(6) == SIDE &&
            m.le7(8) == SIDE && m.u(10) == 0) MirrorEvent.Hello(2, true) else MirrorEvent.Invalid
        2 -> {
            if (m.size !in 15..256) MirrorEvent.Invalid
            else {
                val x = m.le7(5)
                val y = m.le7(7)
                val w = m.le7(9)
                val h = m.le7(11)
                if (!rectangle(x, y, w, h)) MirrorEvent.Invalid
                else expand(m, 13, m.lastIndex, w * h, true)?.let {
                    MirrorEvent.Pixels(Patch(x, y, w, h, it))
                } ?: MirrorEvent.Invalid
            }
        }
        3 -> if (m.size == 6) MirrorEvent.Sync else MirrorEvent.Invalid
        else -> null
    }

    private fun decodeBase(m: ByteArray): MirrorEvent {
        val data = unpackBase(m) ?: return MirrorEvent.Invalid
        if (data.size < 7 || data.u(0) != 0 || data.u(1) != 0x59) return MirrorEvent.Invalid
        val size = data.le16(3) or (data.u(5) shl 16)
        if (size != data.size - 7) return MirrorEvent.Invalid
        val b = data.copyOfRange(6, data.lastIndex)
        if ((b.sumOf { it.toInt() and 255 }.inv() and 255) != data.u(data.lastIndex)) return MirrorEvent.Invalid
        return when (data.u(2)) {
            0x50 -> if (size == 4 && b.u(0) >= 1 && b.u(1) in 0..1 &&
                b.u(2) == SIDE - 1 && b.u(3) == SIDE - 1) MirrorEvent.Hello(b.u(0), b.u(1) == 1)
                else MirrorEvent.Invalid
            0x51, 0x52 -> {
                if (size < 6) return MirrorEvent.Invalid
                val x = b.u(0)
                val y = b.u(1)
                val w = b.u(2)
                val h = b.u(3)
                if (!rectangle(x, y, w, h)) return MirrorEvent.Invalid
                val pixels = if (data.u(2) == 0x51) expand(b, 5, b.size, w * h, false)
                    else if (size == 7) ShortArray(w * h) { ((b.u(5) shl 8) or b.u(6)).toShort() } else null
                if (pixels == null) MirrorEvent.Invalid
                else MirrorEvent.Pixels(Patch(x, y, w, h, pixels), b.u(4))
            }
            0x53 -> if ((size == 21 || size >= 27) && b.le16(13) <= 4096) {
                val lights = b.le16(15)
                MirrorEvent.Panel(PanelState(b.le16(0), b.le32(2), (6..12).map { b[it].toInt() },
                    b.le16(13), lights and 0x7FFF, b.le32(17), lights and 0x8000 != 0,
                    if (size >= 27) b.le16(21) else 0, if (size >= 27) b.le32(23) else 0))
            } else MirrorEvent.Invalid
            0x54 -> if (size == 3 && b.u(2) in 0..1) MirrorEvent.Ack(b.u(0), b.u(1), b.u(2) == 1)
                else MirrorEvent.Invalid
            else -> MirrorEvent.Invalid
        }
    }

    private fun expand(data: ByteArray, start: Int, end: Int, total: Int, big2: Boolean): ShortArray? {
        val result = ShortArray(total)
        val cache = IntArray(64)
        var index = start
        var position = 0
        var color = 0
        while (index < end) {
            val op = data.u(index++)
            val count: Int
            if (big2) {
                count = when {
                    op < 64 -> { color = cache[op]; 1 }
                    op < 96 -> (op and 31) + 1
                    op < 112 && index < end -> (((op and 15) shl 7) or data.u(index++)) + 33
                    op in 112..115 && index + 1 < end -> {
                        color = ((op and 3) shl 14) or (data.u(index++) shl 7) or data.u(index++)
                        val hash = ((color ushr 11) * 3 + ((color ushr 5) and 63) * 5 + (color and 31) * 7) and 63
                        cache[hash] = color
                        1
                    }
                    else -> return null
                }
            } else {
                count = when {
                    op < 128 -> op + 1
                    op < 192 -> { color = cache[op and 63]; 1 }
                    index + 1 < end -> {
                        color = (data.u(index++) shl 8) or data.u(index++)
                        cache[((color * 40503) ushr 10) and 63] = color
                        (op and 63) + 1
                    }
                    else -> return null
                }
            }
            if (count > total - position) return null
            result.fill(color.toShort(), position, position + count)
            position += count
        }
        return result.takeIf { position == total }
    }
}
