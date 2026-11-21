package com.josxtm.fm1ramloader

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal enum class UsbFailure(val label: String) {
    DISCONNECTED("USB desconectado"),
    TIMEOUT("Tiempo de espera USB agotado"),
    TRANSFER("Transferencia USB incompleta"),
    RESPONSE("Respuesta USB inválida"),
    REFUSED("La ROM rechazó el comando"),
    IDENTITY("Identidad ROM incompatible"),
    POLICY("Operación fuera del perfil permitido")
}

internal class UsbProblem(val reason: UsbFailure, detail: String = reason.label) : Exception(detail)

internal interface BulkWire {
    val inPacketSize: Int
    fun write(bytes: ByteArray, offset: Int, length: Int): Int
    fun read(buffer: ByteArray): Int
}

internal object RomProtocol {
    const val CBW = 0x43425355
    const val CSW = 0x53425355
    const val RAM_WRITE = 0xFB06
    const val RAM_JUMP = 0xFB08
    const val INQUIRY = 0x12

    fun inquiryCdb(): ByteArray = byteArrayOf(INQUIRY.toByte(), 0, 0, 0, 36, 0)

    fun inquiryOnly(bytes: ByteArray): Boolean =
        bytes.size == 31 && bytes.contentEquals(wrapper(inquiryCdb(), le32(bytes, 4), 36, true))

    fun wrapper(cdb: ByteArray, tag: Int, size: Int, input: Boolean): ByteArray =
        ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(CBW).putInt(tag).putInt(size)
            .put(if (input && size > 0) 0x80.toByte() else 0)
            .put(0).put(cdb.size.toByte()).put(cdb).array()

    fun parseInquiry(data: ByteArray): String {
        val isRom = data.size == 36 && data[0].toInt() and 0x1f == 0 &&
            data.copyOfRange(8, 16).toString(Charsets.US_ASCII).trim(' ', '\u0000') == "WL82" &&
            data.copyOfRange(16, 32).toString(Charsets.US_ASCII).trim(' ', '\u0000') == "UBOOT1.00"
        if (!isRom) throw UsbProblem(UsbFailure.IDENTITY)
        return "WL82 / UBOOT1.00"
    }

    // M-Flasher src/jl.rs, MIT. The CRC stream restarts at each ROM block.
    private val crcStream = intArrayOf(
        0xC3, 0xCF, 0xC0, 0xE8, 0xCE, 0xD2, 0xB0, 0xAE,
        0xC4, 0xE3, 0xA3, 0xAC, 0xD3, 0xF1, 0xC1, 0xD6
    )

    fun transform(bytes: ByteArray): ByteArray {
        var crc = crc16(byteArrayOf(-1, -1), seed = 0xffff)
        return ByteArray(bytes.size) { i ->
            crc = crc16Byte(crc, crcStream[i % crcStream.size])
            (bytes[i].toInt() xor crc).toByte()
        }
    }

    fun writeCdb(address: Long, payload: ByteArray): ByteArray {
        if (!AppSlot.contains(address, payload.size) || payload.size > AppSlot.BLOCK_BYTES ||
            payload.size % 2 != 0 || (address - AppSlot.START) % AppSlot.BLOCK_BYTES != 0L)
            throw UsbProblem(UsbFailure.POLICY)
        val crc = crc16(payload)
        return vendorCdb().putShort(RAM_WRITE.toShort()).putInt(address.toInt())
            .putShort(payload.size.toShort()).put(0).put(crc.toByte()).put((crc ushr 8).toByte()).array()
    }

    fun jumpCdb(): ByteArray = vendorCdb().putShort(RAM_JUMP.toShort())
        .putInt(AppSlot.START.toInt()).putShort(1).array()

    private fun vendorCdb(): ByteBuffer = ByteBuffer.wrap(ByteArray(16) { 0xff.toByte() })
}

internal fun le32(bytes: ByteArray, index: Int): Int =
    ByteBuffer.wrap(bytes, index, 4).order(ByteOrder.LITTLE_ENDIAN).int

internal class BotSession(private val wire: BulkWire, initialTag: Int = 0) {
    private var tag = initialTag

    fun inquiry(): String = RomProtocol.parseInquiry(command(RomProtocol.inquiryCdb(), readSize = 36))

    fun command(cdb: ByteArray, payload: ByteArray = ByteArray(0), readSize: Int = 0): ByteArray {
        require(cdb.size in 1..16 && readSize in 0..512 && payload.size <= 512 &&
            (payload.isEmpty() || readSize == 0))
        val transfer = ++tag
        val cbw = RomProtocol.wrapper(cdb, transfer, maxOf(payload.size, readSize), readSize > 0)
        if (wire.write(cbw, 0, cbw.size) != cbw.size) throw UsbProblem(UsbFailure.TRANSFER, "CBW incompleto")

        var sent = 0
        while (sent < payload.size) {
            val remaining = payload.size - sent
            val count = wire.write(payload, sent, remaining)
            if (count !in 1..remaining) throw UsbProblem(UsbFailure.TRANSFER)
            sent += count
        }

        val data = ByteArray(maxOf(wire.inPacketSize, readSize, 13))
        if (readSize != 0) {
            val length = wire.read(data)
            if (length == 13 && le32(data, 0) == RomProtocol.CSW) {
                checkStatus(data, length, transfer)
                throw UsbProblem(UsbFailure.TRANSFER, "CSW recibido antes de los datos")
            }
            if (length != readSize) throw UsbProblem(UsbFailure.TRANSFER, "Datos IN de $length B, esperados $readSize B")
        }

        val status = ByteArray(maxOf(wire.inPacketSize, 13))
        checkStatus(status, wire.read(status), transfer)
        return data.copyOf(readSize)
    }

    private fun checkStatus(bytes: ByteArray, length: Int, expectedTag: Int) {
        if (length != 13) throw UsbProblem(UsbFailure.RESPONSE, "CSW de $length B, esperados 13 B")
        if (le32(bytes, 0) != RomProtocol.CSW) throw UsbProblem(UsbFailure.RESPONSE, "Firma CSW incorrecta")
        if (le32(bytes, 4) != expectedTag) throw UsbProblem(UsbFailure.RESPONSE, "Etiqueta CSW distinta de la consulta")
        if (bytes[12] == 1.toByte()) throw UsbProblem(UsbFailure.REFUSED)
        val code = bytes[12].toInt() and 255
        val residue = Integer.toUnsignedLong(le32(bytes, 8))
        if (code != 0 || residue != 0L) throw UsbProblem(UsbFailure.RESPONSE, "CSW con estado $code y residuo $residue")
    }
}
