package com.josxtm.fm1ramloader

/**
 * Deterministic, transport-independent m-bootloader 2.4 test double.
 * It accepts exactly the read-only identity query and has no generic opcode
 * or physical USB/MIDI transport surface.
 */
internal class MbootIdentityEmulator(
    private val scenario: Scenario = Scenario.VALID,
    private val version: String = "2.4-395e6cc"
) {
    enum class Scenario {
        VALID, WRONG_JEDEC, INCOMPATIBLE_VERSION, BAD_CHECKSUM,
        CORRUPT_PAYLOAD, TIMEOUT, DISCONNECTED
    }

    data class Exchange(val status: Status, val frame: ByteArray? = null, val delayMs: Long = 0)
    enum class Status { RESPONSE, TIMEOUT, DISCONNECTED }

    fun exchange(request: ByteArray, delayMs: Long = 0): Exchange {
        require(request.contentEquals(MbootIdentityProtocol.identityQuery())) {
            "El emulador solo acepta la consulta fija 0x12"
        }
        return when (scenario) {
            Scenario.TIMEOUT -> Exchange(Status.TIMEOUT, delayMs = delayMs)
            Scenario.DISCONNECTED -> Exchange(Status.DISCONNECTED, delayMs = delayMs)
            else -> Exchange(Status.RESPONSE, frame = frame(), delayMs = delayMs)
        }
    }

    private fun frame(): ByteArray {
        val body = ByteArray(29 + version.length)
        "MBOOT".toByteArray(Charsets.US_ASCII).copyInto(body)
        body[5] = 1
        body[6] = 1
        body[7] = 1
        put32(body, 10, 1_048_576)
        put32(body, 14, if (scenario == Scenario.WRONG_JEDEC) 0x123456 else 0x856014)
        val reportedVersion = if (scenario == Scenario.INCOMPATIBLE_VERSION) "1.0" else version
        require(reportedVersion.length == version.length || scenario == Scenario.INCOMPATIBLE_VERSION)
        reportedVersion.toByteArray(Charsets.US_ASCII).copyInto(body, 28)
        body[body.lastIndex] = 0

        val raw = ByteArray(7 + body.size)
        raw[1] = 0x59
        raw[2] = MbootIdentityProtocol.QUERY.toByte()
        raw[3] = body.size.toByte()
        raw[4] = (body.size ushr 8).toByte()
        body.copyInto(raw, 6)
        raw[raw.lastIndex] = body.sumOf { it.toInt() and 255 }.inv().toByte()
        if (scenario == Scenario.BAD_CHECKSUM) raw[raw.lastIndex] = (raw[raw.lastIndex] + 1).toByte()
        if (scenario == Scenario.CORRUPT_PAYLOAD) raw[20] = (raw[20].toInt() xor 1).toByte()
        return MbootIdentityProtocol.packFrame(raw)
    }

    private fun put32(out: ByteArray, at: Int, value: Long) {
        for (i in 0 until 4) out[at + i] = (value ushr (8 * i)).toByte()
    }
}
