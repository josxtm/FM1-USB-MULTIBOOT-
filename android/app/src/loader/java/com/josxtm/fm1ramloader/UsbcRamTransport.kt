package com.josxtm.fm1ramloader

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Fase USB-C 2: protocolo de RAM ROM, sin comandos de borrado ni de Flash.
 *
 * El transporte se construye sobre una conexión que ya haya superado la
 * cualificación física independiente. Nunca prueba alimentación USB-C.
 * La aplicación mantiene el botón físico deshabilitado hasta completar
 * esa cualificación y fijar el hash de la imagen de esta fase.
 */
internal data class UsbcLabEvidence(
    val independentlyObservedColdRom: Boolean = false,
    val flashBackupVerified: Boolean = false,
    val recoveryProcedureVerified: Boolean = false,
    val usbPowerAndRoleQualified: Boolean = false,
    val explicitOneShotConsent: Boolean = false,
    val exclusiveClaimAllowed: Boolean = false
) {
    fun complete(): Boolean = independentlyObservedColdRom && flashBackupVerified &&
        recoveryProcedureVerified && usbPowerAndRoleQualified && explicitOneShotConsent
}

internal enum class UsbcRamResult {
    BLOCKED, ROM_UNQUALIFIED, RESIDENT_PRESENT, PROBE_AMBIGUOUS,
    IMAGE_REJECTED, TRANSFER_FAILED, JUMP_ACKNOWLEDGED, JUMP_UNCONFIRMED
}

internal data class UsbcRamReport(
    val result: UsbcRamResult, val blocks: Int, val bytes: Int, val detail: String
)

/**
 * SHA-256 de un APP1 explícitamente aprobado para hardware.
 * NO se usa un valor sacado del usuario ni un hash autoaprendido.
 * Se rellenará después de auditar el artefacto pi32v2 exacto de CI.
 */
internal object UsbcRamImage {
    const val HARDWARE_PINNED_SHA256 = "628cf2976fefbcbac55b3f0dcb403d9eafd7e1b46409d0467dc82ab6f8e11270"
    const val LAB_PINNED_SHA256 = "fcc660d34017a48a85a4ffe96b2317876b79268d778d2835e9377e50f11041d4"
    private const val MAX_IMAGE = 32768
    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun checks(image: ByteArray, pinnedSha256: String = HARDWARE_PINNED_SHA256): List<Pair<String, Boolean>> {
        val len = image.size
        val footer = len - 8
        val magic = len >= 16 && image.copyOfRange(footer, footer + 4)
            .contentEquals("APP1".toByteArray(Charsets.US_ASCII))
        return listOf(
            "Identificador SHA-256 fijado en la compilación" to
                (pinnedSha256.length == 64 && pinnedSha256.all { it in '0'..'9' || it in 'a'..'f' } &&
                    digest(image) == pinnedSha256),
            "Tamaño par y máximo de 32 KiB" to (len in 16..MAX_IMAGE && len % 2 == 0),
            "Entrada fija ROM y ventana RAM" to AppSlot.contains(AppSlot.START, len),
            "Tráiler APP1" to magic,
            "Placa FM1 y formato v1" to (magic && image[footer + 6] == 1.toByte() && image[footer + 7] == 1.toByte()),
            "CRC16-XMODEM" to (magic && crc16(image, footer) == le16(image, footer + 4))
        )
    }

    fun profile(image: ByteArray): String? = when {
        checks(image, LAB_PINNED_SHA256).all { it.second } -> "HOST LAB"
        checks(image, HARDWARE_PINNED_SHA256).all { it.second } -> "READONLY"
        else -> null
    }

    fun approved(image: ByteArray): Boolean = profile(image) != null
}

/**
 * Interfaz de bytes limitada para CDB generados internamente. Este objeto
 * no permite pasar CDB, direcciones ni firmware arbitrarios desde la UI.
 */
internal class UsbcRamTransport(
    private val connection: RomConnection,
    private val linkValid: () -> Boolean,
    private val progress: (Int, Int) -> Unit = { _, _ -> },
    // Internal seam for hermetic tests; the physical Android backend always uses the default.
    private val approveImage: (ByteArray) -> Boolean = UsbcRamImage::approved
) {
    private var used = false
    private var tag = 0

    private data class Answer(val data: ByteArray, val status: Int, val residue: Long)

    fun launch(image: ByteArray, evidence: UsbcLabEvidence): UsbcRamReport {
        if (used) return UsbcRamReport(UsbcRamResult.BLOCKED, 0, 0, "Sesión ya consumida")
        used = true
        if (!evidence.complete())
            return UsbcRamReport(UsbcRamResult.BLOCKED, 0, 0, "Faltan verificaciones externas")
        if (!approveImage(image))
            return UsbcRamReport(UsbcRamResult.IMAGE_REJECTED, 0, 0, "Imagen no aprobada")
        var blocks = 0
        var uploaded = 0
        var jumpSent = false
        var uploadAttempted = false
        try {
            val inquiry = exchange(RomProtocol.inquiryCdb(), 36)
            requireSuccess(inquiry, 36)
            RomProtocol.parseInquiry(inquiry.data)

            // Únicamente una negativa precisa de FC40, junto a la evidencia
            // independiente de ROM fría, permite continuar.
            val probe = exchange(RomProtocol.identifyResidentCdb(), 16, refusalAllowed = true)
            if (probe.data.size == 16 && probe.data.copyOfRange(2, 6)
                    .contentEquals("MFLR".toByteArray(Charsets.US_ASCII)))
                return UsbcRamReport(UsbcRamResult.RESIDENT_PRESENT, 0, 0, "Cargador residente")
            if (probe.status != 1 || probe.residue != 16L || probe.data.isNotEmpty())
                return UsbcRamReport(UsbcRamResult.PROBE_AMBIGUOUS, 0, 0, "FC40 no demuestra ROM limpia")

            while (uploaded < image.size) {
                ensureActive()
                val raw = image.copyOfRange(uploaded, minOf(uploaded + 512, image.size))
                val encoded = RomProtocol.transform(raw)
                uploadAttempted = true
                val response = exchange(RomProtocol.writeCdb(AppSlot.START + uploaded, encoded), 0, encoded)
                requireSuccess(response, 0)
                uploaded += raw.size
                blocks++
                progress(uploaded, image.size)
            }
            if (!approveImage(image))
                throw UsbProblem(UsbFailure.POLICY, "La imagen cambió antes del salto")

            // Un solo salto. La ROM puede desmontar USB antes de confirmar.
            ensureActive()
            jumpSent = true
            val jumped = try { exchange(RomProtocol.jumpCdb(), 16) }
                         catch (_: Exception) {
                             return UsbcRamReport(UsbcRamResult.JUMP_UNCONFIRMED,
                                 blocks, uploaded, "Sin acuse del salto; no reintentar")
                         }
            return if (jumped.status == 0 && jumped.residue == 0L &&
                jumped.data.size == 16 &&
                jumped.data[0] == 0xfb.toByte() && jumped.data[1] == 0x08.toByte())
                UsbcRamReport(UsbcRamResult.JUMP_ACKNOWLEDGED,
                    blocks, uploaded, "La ROM confirmó el salto; verificar LCD físicamente")
            else UsbcRamReport(UsbcRamResult.JUMP_UNCONFIRMED,
                blocks, uploaded, "Respuesta de salto ambigua; no reintentar")
        } catch (e: Exception) {
            return UsbcRamReport(if (jumpSent) UsbcRamResult.JUMP_UNCONFIRMED
                else if (!uploadAttempted) UsbcRamResult.ROM_UNQUALIFIED
                else UsbcRamResult.TRANSFER_FAILED,
                blocks, uploaded, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun ensureActive() {
        if (!linkValid()) throw UsbProblem(UsbFailure.DISCONNECTED)
    }

    private fun exactOut(bytes: ByteArray) {
        ensureActive()
        val n = connection.bulkOut(bytes, 3000)
        if (n != bytes.size) throw UsbProblem(UsbFailure.TRANSFER, "Bulk OUT incompleto: $n/${bytes.size}")
        ensureActive()
    }

    private fun readUsb(capacity: Int): ByteArray {
        ensureActive()
        val buffer = ByteArray(maxOf(64, capacity))
        val length = connection.bulkIn(buffer, 5000)
        if (length !in 1..buffer.size)
            throw UsbProblem(UsbFailure.TRANSFER, "Bulk IN incompleto: $length")
        ensureActive()
        return buffer.copyOf(length)
    }

    private fun exchange(cdb: ByteArray, readSize: Int, outbound: ByteArray = ByteArray(0),
                         refusalAllowed: Boolean = false): Answer {
        require(cdb.size in 1..16 && readSize in 0..36 && outbound.size <= 512 &&
            (outbound.isEmpty() || readSize == 0))
        val currentTag = ++tag
        val cbw = RomProtocol.wrapper(cdb, currentTag, maxOf(readSize, outbound.size), readSize > 0)
        exactOut(cbw)
        if (outbound.isNotEmpty()) exactOut(outbound)
        var data = ByteArray(0)
        var csw = readUsb(if (readSize == 0) 13 else readSize)
        if (readSize > 0) {
            if (!isCsw(csw)) {
                if (csw.size != readSize) throw UsbProblem(UsbFailure.TRANSFER, "Datos IN ${csw.size}/$readSize")
                data = csw
                csw = readUsb(13)
            } else if (!refusalAllowed) {
                throw UsbProblem(UsbFailure.TRANSFER, "CSW antes de datos IN")
            }
        }
        if (!isCsw(csw) || csw.size != 13) throw UsbProblem(UsbFailure.RESPONSE, "CSW inválido")
        val buffer = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN)
        buffer.int
        if (buffer.int != currentTag) throw UsbProblem(UsbFailure.RESPONSE, "Tag CSW incorrecto")
        val residue = Integer.toUnsignedLong(buffer.int)
        val code = buffer.get().toInt() and 255
        if (code > 1 || (code == 1 && !refusalAllowed))
            throw UsbProblem(UsbFailure.REFUSED, "CSW status $code")
        return Answer(data, code, residue)
    }

    private fun isCsw(bytes: ByteArray): Boolean =
        bytes.size == 13 && le32(bytes, 0) == RomProtocol.CSW

    private fun requireSuccess(response: Answer, expected: Int) {
        if (response.status != 0 || response.residue != 0L || response.data.size != expected)
            throw UsbProblem(UsbFailure.RESPONSE, "Respuesta BOT no confirmada")
    }
}
