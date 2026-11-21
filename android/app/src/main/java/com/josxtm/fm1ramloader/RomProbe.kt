package com.josxtm.fm1ramloader

import java.io.Closeable
import java.util.concurrent.ThreadLocalRandom

internal enum class RomLink { READY, DETACHED, NO_PERMISSION }

internal enum class RomFault(val label: String) {
    DETACHED("Dispositivo desconectado o reenumerado"),
    PERMISSION("Permiso USB ausente para esta conexión"),
    INTERFACE("Interfaz ROM ausente o ambigua"),
    ENDPOINTS("Par Bulk IN/OUT ausente, ambiguo o inválido"),
    OPEN("Android no abrió el dispositivo USB"),
    CLAIM("Android rechazó la reclamación de interfaz"),
    EXCLUSIVE_BLOCKED("Acceso exclusivo bloqueado por la política USB"),
    EXCLUSIVE_CLAIM("Android rechazó la reclamación exclusiva"),
    TRANSFER("Falló una transferencia Bulk"),
    PROTOCOL("Falló la validación INQUIRY/BOT"),
    API("Error de la API USB de Android")
}

internal class RomProbeError(val fault: RomFault, val stage: String, detail: String? = null) :
    Exception("$stage · ${detail ?: fault.label}")

internal interface RomConnection : Closeable {
    fun claim(force: Boolean): Boolean
    fun release(): Boolean
    fun bulkOut(data: ByteArray, timeoutMs: Int): Int
    fun bulkIn(data: ByteArray, timeoutMs: Int): Int
}

/** One INQUIRY per connection. There is no real-device transport for any other SCSI or ROM operation. */
internal fun probeRom(
    pair: BulkPair,
    exclusive: Boolean,
    exclusiveAllowed: () -> Boolean,
    link: () -> RomLink,
    open: () -> RomConnection?,
    log: (String) -> Unit
): String {
    fun requireLink(stage: String) {
        when (link()) {
            RomLink.READY -> Unit
            RomLink.DETACHED -> throw RomProbeError(RomFault.DETACHED, stage)
            RomLink.NO_PERMISSION -> throw RomProbeError(RomFault.PERMISSION, stage)
        }
    }

    fun <T> call(stage: String, operation: () -> T): T = try {
        operation()
    } catch (_: SecurityException) {
        throw RomProbeError(RomFault.PERMISSION, stage)
    } catch (error: RuntimeException) {
        throw RomProbeError(RomFault.API, stage, error.javaClass.simpleName)
    }

    requireLink("openDevice")
    val connection = call("openDevice", open)
    log("openDevice() → ${if (connection == null) "null" else "conexión abierta"}")
    if (connection == null) {
        requireLink("openDevice")
        throw RomProbeError(RomFault.OPEN, "openDevice")
    }
    var claimed = false
    try {
        requireLink("claimInterface")
        claimed = call("claimInterface(false)") { connection.claim(false) }
        log("claimInterface(force=false) → $claimed")
        if (!claimed) {
            requireLink("claimInterface")
            if (!exclusive) throw RomProbeError(RomFault.CLAIM, "claimInterface(false)")
            if (!exclusiveAllowed()) throw RomProbeError(RomFault.EXCLUSIVE_BLOCKED, "claimInterface(true)")
            requireLink("claimInterface(true)")
            claimed = call("claimInterface(true)") { connection.claim(true) }
            log("claimInterface(force=true) → $claimed")
            if (!claimed) {
                requireLink("claimInterface(true)")
                throw RomProbeError(RomFault.EXCLUSIVE_CLAIM, "claimInterface(true)")
            }
        }

        val wire = object : BulkWire {
            override val inPacketSize = pair.input.packetSize
            private var inquirySent = false
            private var reads = 0

            override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
                val stage = "Bulk OUT ${pair.output.label} · CBW"
                if (inquirySent || offset != 0 || length != bytes.size || !RomProtocol.inquiryOnly(bytes))
                    throw RomProbeError(RomFault.PROTOCOL, stage, "CBW fuera del perfil INQUIRY")
                inquirySent = true
                return transfer(stage, length, 3000) { connection.bulkOut(bytes, it) }
            }

            override fun read(buffer: ByteArray): Int {
                if (!inquirySent || reads >= 2) throw RomProbeError(RomFault.PROTOCOL, "Bulk IN")
                val stage = "Bulk IN ${pair.input.label} · ${if (reads++ == 0) "datos" else "CSW"}"
                return transfer(stage, buffer.size, 5000) { connection.bulkIn(buffer, it) }
            }

            private fun transfer(stage: String, size: Int, timeoutMs: Int, io: (Int) -> Int): Int {
                requireLink(stage)
                val start = System.nanoTime()
                val count = call(stage) { io(timeoutMs) }
                val elapsed = (System.nanoTime() - start) / 1_000_000
                log("$stage · resultado $count/$size B · $elapsed ms (límite $timeoutMs ms)")
                requireLink(stage)
                if (count < 0) throw RomProbeError(RomFault.TRANSFER, stage, "Android devolvió $count sin errno")
                return count
            }
        }
        return try {
            BotSession(wire, ThreadLocalRandom.current().nextInt()).inquiry()
        } catch (failure: UsbProblem) {
            throw RomProbeError(RomFault.PROTOCOL, "INQUIRY/BOT", failure.message)
        }
    } finally {
        try {
            if (claimed) {
                val released = runCatching { connection.release() }
                log("releaseInterface() → ${released.getOrNull() ?: released.exceptionOrNull()?.javaClass?.simpleName}")
            }
        } finally {
            val closed = runCatching { connection.close() }
            log("close() → ${closed.fold({ "conexión cerrada" }, { it.javaClass.simpleName })}")
        }
    }
}
