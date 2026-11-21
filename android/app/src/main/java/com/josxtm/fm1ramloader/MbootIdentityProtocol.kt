package com.josxtm.fm1ramloader

import java.io.ByteArrayOutputStream

/**
 * m-bootloader SPEC.md §11.6. Read-only identity operation ONLY.
 * Never add a generic send(command) API: stock firmware may interpret a one-bit
 * corruption of 0x23 as flash erase 0x21.
 */
internal object MbootIdentityProtocol {
    const val QUERY = 0x12
    private const val MAX_SYSEX_BYTES = 192

    data class Identity(val version: String, val capacity: Long, val jedec: Long,
                        val ramApps: Boolean, val kind: Int) {
        val summary: String
            get() = "m-bootloader $version · Flash ${capacity / 1024} KiB · JEDEC 0x${jedec.toString(16).uppercase()}"
    }

    // Fixed, command-specific query: 00 59 12 00 00 00 FF, then 8-to-7 bitstream.
    fun identityQuery(): ByteArray = packFrame(byteArrayOf(0, 0x59, QUERY.toByte(), 0, 0, 0, 0xff.toByte()))

    internal fun packFrame(decoded: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xf0)
        var acc = 0
        var bits = 0
        for (b in decoded) {
            acc = acc or ((b.toInt() and 0xff) shl bits)
            bits += 8
            while (bits >= 7) {
                out.write(acc and 0x7f)
                acc = acc ushr 7
                bits -= 7
            }
        }
        if (bits > 0) out.write(acc and 0x7f)
        out.write(0xf7)
        return out.toByteArray()
    }

    fun decodeFrame(frame: ByteArray): Identity {
        require(frame.size in 10..MAX_SYSEX_BYTES && frame[0] == 0xf0.toByte() &&
            frame.last() == 0xf7.toByte()) { "Trama SysEx inválida" }
        var acc = 0
        var bits = 0
        val raw = ByteArrayOutputStream()
        for (i in 1 until frame.lastIndex) {
            val b = frame[i].toInt() and 0xff
            require(b < 0x80) { "Datos SysEx fuera de 7 bits" }
            acc = acc or (b shl bits)
            bits += 7
            while (bits >= 8) {
                raw.write(acc and 0xff)
                acc = acc ushr 8
                bits -= 8
            }
        }
        val decoded = raw.toByteArray()
        require(decoded.size >= 7 && decoded[0] == 0.toByte() && decoded[1] == 0x59.toByte() &&
            decoded[5] == 0.toByte()) { "Cabecera M-UPGRADE desconocida" }
        val bodyLength = (decoded[3].toInt() and 0xff) or
            ((decoded[4].toInt() and 0xff) shl 8)
        require(bodyLength <= 96 && decoded.size == bodyLength + 7 &&
            ((decoded[6 + bodyLength].toInt() + (6 until 6 + bodyLength).sumOf {
                decoded[it].toInt() and 0xff
            }) and 0xff) == 0xff) { "Longitud o checksum incorrectos" }
        require(decoded[2] == QUERY.toByte()) { "Respuesta MIDI inesperada" }
        val body = decoded.copyOfRange(6, 6 + bodyLength)
        require(body.size in 29..64 &&
            body.copyOfRange(0, 5).contentEquals("MBOOT".toByteArray(Charsets.US_ASCII)) &&
            body[5] == 1.toByte() && body[6] == 1.toByte() &&
            body[26] == 0.toByte() && body[27] == 0.toByte()) { "No es m-bootloader de protocolo v1" }
        require(body.last() == 0.toByte()) { "Versión sin terminación" }
        val version = body.copyOfRange(28, body.size - 1).toString(Charsets.US_ASCII)
        require(version.isNotBlank() && version.all { it.code in 0x20..0x7e }) {
            "Versión inválida"
        }
        val capacity = le32u(body, 10)
        val jedec = le32u(body, 14)
        require(capacity == 1_048_576L && jedec == 0x856014L) {
            "Flash distinta al FM1 de referencia"
        }
        return Identity(version, capacity, jedec, body[7].toInt() and 1 != 0, 1)
    }

    private fun le32u(bytes: ByteArray, at: Int): Long =
        (0..3).fold(0L) { result, i -> result or ((bytes[at + i].toLong() and 0xff) shl (8 * i)) }
}

/** Never concatenate unbounded MIDI data or accept a second concurrent SysEx message. */
internal class MbootIdentityCollector {
    private val buffer = ByteArrayOutputStream()
    private var receiving = false

    fun receive(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size): ByteArray? {
        require(offset >= 0 && count >= 0 && offset + count <= bytes.size)
        for (i in offset until offset + count) {
            val b = bytes[i].toInt() and 0xff
            if (b == 0xf0) {
                buffer.reset()
                buffer.write(b)
                receiving = true
            } else if (receiving && b == 0xf7) {
                buffer.write(b)
                receiving = false
                return buffer.toByteArray()
            } else if (receiving && b < 0x80) {
                if (buffer.size() >= 190) {
                    receiving = false
                    buffer.reset()
                    throw IllegalArgumentException("Respuesta SysEx excesiva")
                }
                buffer.write(b)
            } else if (receiving && b >= 0x80 && b < 0xf8) {
                receiving = false
                buffer.reset()
                throw IllegalArgumentException("Estado MIDI inesperado dentro de SysEx")
            }
        }
        return null
    }
}
