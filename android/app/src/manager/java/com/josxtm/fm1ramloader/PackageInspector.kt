package com.josxtm.fm1ramloader

import java.util.zip.CRC32

internal data class PackageInfo(val container: String, val identity: String, val layout: String, val crc: String)

/** Read-only header inspection. Package bytes are retained unmodified. */
internal object PackageInspector {
    fun inspect(raw: ByteArray, file: String): PackageInfo {
        require(raw.size in 16..MAX_ASSET)
        if (raw.copyOfRange(0, 4).contentEquals("SLOT".encodeToByteArray())) return slot(raw)
        if (raw.copyOfRange(raw.size - 8, raw.size - 4).contentEquals("APP1".encodeToByteArray()))
            error("APP1 de RAM fuera de la biblioteca musical")
        if (file.endsWith(".fwsc", true)) {
            require(raw.size >= 0x1000) { "FWSC truncado" }
            var blocks = 20
            while ((blocks + 1) * 48 < raw.size && raw[blocks * 48 + 47] == 0x7d.toByte()) blocks++
            val identity = buildString {
                for (i in 0 until blocks) {
                    val value = raw[i * 48 + 47].toInt() and 255
                    if (value == 0x7d) break
                    append(((value - i - 1) and 255).toChar())
                }
            }
            require(identity.startsWith("FM-1_") && identity.length <= 64 &&
                identity.all { it.code in 32..126 }) { "Identidad FWSC ajena a FM-1" }
            val image = ByteArray(raw.size - blocks)
            for (i in 0 until blocks) raw.copyInto(image, i * 47, i * 48, i * 48 + 47)
            raw.copyInto(image, blocks * 47, blocks * 48)
            return ufw(image, "FWSC/UFW", identity)
        }
        require(file.endsWith(".ufw", true) || file.endsWith(".bin", true)) { "Extensión no admitida" }
        return ufw(raw, "UFW", "")
    }

    private fun ufw(raw: ByteArray, container: String, identity: String): PackageInfo {
        require(raw.size >= 64) { "UFW truncado" }
        val head = enc(raw, 0, 64)
        require(crc16(head, 2, 64) == u16(head, 0)) { "CRC16 de cabecera UFW incorrecto" }
        val count = u16(head, 8)
        val end = 64 + count * 80
        require(count in 1..256 && end <= raw.size) { "Tabla UFW fuera de límites" }
        require(crc16(raw, 64, end) == u16(head, 2)) { "CRC16 de tabla UFW incorrecto" }
        val names = HashSet<String>()
        val entries = ArrayList<String>()
        var flash = false
        for (index in 0 until count) {
            val e = enc(raw, 64 + index * 80, 80)
            val kind = u16(e, 0)
            val offset = u32(e, 8)
            val size = u32(e, 12)
            require(offset <= raw.size && size <= raw.size - offset && (size == 0L || offset >= end)) {
                "Región UFW fuera del archivo"
            }
            val name = e.copyOfRange(64, 80).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.US_ASCII)
            require(validBaseName(name) && names.add(name)) { "Nombre UFW inválido o repetido" }
            require(crc16(raw, offset.toInt(), (offset + size).toInt()) == u16(e, 4)) {
                "CRC16 incorrecto: $name"
            }
            if (name == "flash.bin") {
                require(kind == 0 && size > 0 && size <= MAX_ASSET) { "Región flash.bin incorrecta" }
                flash = true
            }
            entries += "$name @$offset ($size B)"
        }
        require(flash || names.any { it == "ota.bin" }) { "UFW carece de flash.bin y ota.bin" }
        return PackageInfo(container, identity, entries.joinToString(" · ").take(900), "CRC16 cabecera, tabla y archivos")
    }

    private fun slot(raw: ByteArray): PackageInfo {
        require(raw.size >= 512 && raw[4] == 1.toByte() && raw[5] == 1.toByte() &&
            u16(raw, 6) == 512 && u32(raw, 12) == 0x856014L) { "SLOT fuera del perfil FM-1/Puya" }
        val count = u32(raw, 8)
        require(count in 1..251 && 512 + count * 4096 == raw.size.toLong()) { "Tamaño SLOT incorrecto" }
        require(crc32(raw, 0, 508) == u32(raw, 508) && crc32(raw, 512, raw.size) == u32(raw, 112)) {
            "CRC32 SLOT incorrecto"
        }
        val sectors = (0..255).filter { raw[16 + it / 8].toInt() and (1 shl (it % 8)) != 0 }
        require(sectors.size.toLong() == count && sectors.first() == 4 && sectors.last() < 255) {
            "Mapa SLOT inválido o sector protegido"
        }
        require(!raw.copyOfRange(512, 527).contentEquals("FM1-VIRTUAL-5Y\u0000".encodeToByteArray())) {
            "Fixture sintético, fuera de la biblioteca musical"
        }
        return PackageInfo("SLOT v1", "FM-1 · JEDEC 856014",
            sectors.joinToString(", ") { "0x" + (it * 4096).toString(16) }.take(900), "CRC32 cabecera y datos")
    }

    private fun u16(data: ByteArray, at: Int) = (data[at].toInt() and 255) or ((data[at + 1].toInt() and 255) shl 8)
    private fun u32(data: ByteArray, at: Int) = u16(data, at).toLong() or (u16(data, at + 2).toLong() shl 16)
    private fun crc32(data: ByteArray, from: Int, to: Int) = CRC32().apply { update(data, from, to - from) }.value

    internal fun crc16(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var state = 0
        for (i in from until to) {
            state = state xor ((data[i].toInt() and 255) shl 8)
            repeat(8) { state = ((state shl 1) xor if (state and 0x8000 == 0) 0 else 0x1021) and 65535 }
        }
        return state
    }

    internal fun enc(data: ByteArray, from: Int, count: Int): ByteArray {
        var state = 65535
        return ByteArray(count) { i ->
            val byte = (data[from + i].toInt() xor state).toByte()
            state = ((state shl 1) xor if (state and 0x8000 == 0) 0 else 0x1021) and 65535
            byte
        }
    }
}
