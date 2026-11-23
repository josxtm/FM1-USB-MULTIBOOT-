package com.josxtm.fm1ramloader

import java.security.MessageDigest

internal object AppSlot {
    const val START = 0x01C30000L
    const val END = 0x01C7FD80L
    const val MAX_BYTES = (END - START).toInt()
    const val BLOCK_BYTES = 512
    const val DIAGNOSTIC = "fm1-usb-diagnostics-ram-experimental.bin"
    const val SHA256 = "81b8bca62a007fc4b6e55450e603e6e29a593414c8d5022280a5f4231f58377e"

    fun contains(address: Long, size: Int): Boolean =
        address >= START && address < END && size > 0 && size.toLong() <= END - address
}

internal fun crc16(data: ByteArray, end: Int = data.size, seed: Int = 0): Int {
    var crc = seed
    for (i in 0 until end) crc = crc16Byte(crc, data[i].toInt() and 255)
    return crc
}

internal fun crc16Byte(state: Int, byte: Int): Int {
    var crc = state xor (byte shl 8)
    repeat(8) {
        crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 0xffff
    }
    return crc
}

internal data class Inspection(
    val size: Int,
    val board: Int?,
    val sha256: String,
    val checks: List<Pair<String, Boolean>>,
    val approved: ApprovedApp?
)

internal class ApprovedApp private constructor(private val data: ByteArray) {
    val size: Int get() = data.size
    fun chunk(offset: Int, limit: Int = AppSlot.BLOCK_BYTES): ByteArray =
        data.copyOfRange(offset, minOf(offset + limit, data.size))

    companion object {
        fun inspect(source: ByteArray): Inspection {
            val bytes = source.copyOf()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val sizeOk = bytes.size >= 16 && bytes.size % 2 == 0 && AppSlot.contains(AppSlot.START, bytes.size)
            val footer = bytes.size - 8
            val magicOk = footer >= 0 && bytes.copyOfRange(footer, footer + 4).contentEquals("APP1".toByteArray())
            val board = if (magicOk) bytes[footer + 6].toInt() and 255 else null
            val formatOk = magicOk && bytes[footer + 7] == 1.toByte()
            val crcOk = magicOk && crc16(bytes, footer) == le16(bytes, footer + 4)
            val checks = listOf(
                "Formato APP1 v1" to formatOk,
                "Modelo FM-1 (placa 1)" to (board == 1),
                "CRC16-XMODEM" to crcOk,
                "SHA-256 aprobado" to (hash == AppSlot.SHA256),
                "Tamaño y rango RAM" to sizeOk
            )
            return Inspection(bytes.size, board, hash, checks,
                if (checks.all { it.second }) ApprovedApp(bytes) else null)
        }
    }
}

internal fun le16(bytes: ByteArray, index: Int): Int =
    (bytes[index].toInt() and 255) or ((bytes[index + 1].toInt() and 255) shl 8)

internal fun hex(value: Long, digits: Int = 8): String = value.toString(16).uppercase().padStart(digits, '0')
