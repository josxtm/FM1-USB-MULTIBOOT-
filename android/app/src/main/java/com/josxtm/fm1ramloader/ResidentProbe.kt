package com.josxtm.fm1ramloader

import java.nio.ByteBuffer

internal const val MFLR_IDENTIFY = 0xfc40

internal data class ResidentLoader(val version: String, val address: Long) {
    val knownSlot: Boolean get() = address == 0x01c20000L || address == 0x01c28000L
}

internal fun parseResidentLoader(reply: ByteArray): ResidentLoader? {
    if (reply.size != 16 || reply[0] != 0xfc.toByte() || reply[1] != 0x40.toByte() ||
        !reply.copyOfRange(2, 6).contentEquals("MFLR".toByteArray(Charsets.US_ASCII))) return null
    val version = reply.copyOfRange(6, 9).toString(Charsets.US_ASCII).trimEnd('\u0000')
    if (version.isEmpty() || version.any { it !in ' '..'~' }) return null
    return ResidentLoader(version, Integer.toUnsignedLong(ByteBuffer.wrap(reply, 10, 4).int))
}

/** Only the closed simulator can know that a refused probe came from the clean-ROM fixture. */
internal fun checkSimulatedResident(bot: BotSession, knownCleanFixture: Boolean, log: (String) -> Unit) {
    val reply = try {
        bot.command(RomProtocol.identifyResidentCdb(), readSize = 16)
    } catch (error: UsbProblem) {
        if (error.reason == UsbFailure.REFUSED && knownCleanFixture) {
            log("SIM · 0xFC40 rechazado por la ROM limpia del simulador")
            return
        }
        throw UsbProblem(UsbFailure.POLICY, "SIM · presencia de cargador no resuelta")
    }
    val resident = parseResidentLoader(reply)
        ?: throw UsbProblem(UsbFailure.POLICY, "SIM · respuesta de cargador sin identificar")
    val location = if (resident.knownSlot) "0x${hex(resident.address)}" else "dirección no verificada"
    log("SIM · MFLR ${resident.version} residente · $location")
    throw UsbProblem(UsbFailure.POLICY, "SIM · carga bloqueada por un cargador residente")
}
