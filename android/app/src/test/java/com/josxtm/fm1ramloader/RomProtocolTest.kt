package com.josxtm.fm1ramloader

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class RomProtocolTest {
    @Test fun matchesIndependentCrcStreamAndMixedEndianCommandVector() {
        val plain = ByteArray(16) { it.toByte() }
        val scrambled = RomProtocol.transform(plain)
        assertArrayEquals(bytes("2fa583cf4ac45bede6abbcad2587daf8"), scrambled)
        assertArrayEquals(plain, RomProtocol.transform(scrambled))
        assertArrayEquals(bytes("fb0601c30000001000964bffffffffff"), RomProtocol.writeCdb(AppSlot.START, scrambled))
        assertArrayEquals(bytes("fb0801c300000001ffffffffffffffff"), RomProtocol.jumpCdb())
        assertEquals(0x31c3, crc16("123456789".toByteArray()))
    }

    @Test fun deniesBlocksOutsideTheAppSlotAndUnalignedBlocks() {
        for (address in listOf(AppSlot.START - 512, AppSlot.START + 2, AppSlot.END, Long.MAX_VALUE)) {
            assertFailure(UsbFailure.POLICY) { RomProtocol.writeCdb(address, ByteArray(512)) }
        }
        assertFailure(UsbFailure.POLICY) { RomProtocol.writeCdb(AppSlot.START, ByteArray(513)) }
        assertFailure(UsbFailure.POLICY) { RomProtocol.writeCdb(AppSlot.START, ByteArray(511)) }
    }

    @Test fun hardwareWriteGuardAllowsOnlyCanonicalInquiryCbw() {
        val valid = RomProtocol.wrapper(RomProtocol.inquiryCdb(), 42, 36, true)
        assertTrue(RomProtocol.inquiryOnly(valid))
        for (index in listOf(0, 8, 12, 13, 14, 15, 19, 30)) {
            val changed = valid.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() }
            assertFalse(RomProtocol.inquiryOnly(changed))
        }
        for (code in listOf(0xfb00, 0xfb01, 0xfb04, 0xfb06, 0xfb08, 0x2a00)) {
            val cdb = bytes(hex(code.toLong(), 4))
            assertFalse(RomProtocol.inquiryOnly(RomProtocol.wrapper(cdb, 1, 36, true)))
        }
        assertFalse(RomProtocol.inquiryOnly(ByteArray(512)))
    }

    @Test fun validatesInquiryAndRejectsMalformedOrRefusedStatus() {
        val good = ByteArray(36).apply {
            "WL82".toByteArray().copyInto(this, 8)
            "UBOOT1.00".toByteArray().copyInto(this, 16)
        }
        val valid = status(1)
        assertEquals("WL82 / UBOOT1.00", BotSession(Responses(good, valid)).inquiry())
        val other = good.copyOf().apply { this[8] = 0 }
        assertFailure(UsbFailure.IDENTITY) { BotSession(Responses(other, valid)).inquiry() }
        assertFailure(UsbFailure.TRANSFER) { BotSession(Responses(good.copyOf(12), valid)).inquiry() }
        assertFailure(UsbFailure.REFUSED) { BotSession(Responses(status(1, code = 1))).inquiry() }
        assertFailure(UsbFailure.RESPONSE) { BotSession(Responses(good, status(2))).inquiry() }
        assertFailure(UsbFailure.RESPONSE) { BotSession(Responses(good, status(1, residue = 1))).inquiry() }
        assertFailure(UsbFailure.RESPONSE) { BotSession(Responses(good, valid.copyOf(12))).inquiry() }
    }

    @Test fun fragmentedSimulationUploadsEveryBlockAndJumpsExactlyOnce() {
        val wire = SimulatedRom(fragmentSize = 63)
        val progress = mutableListOf<Int>()
        val image = approved()
        val result = RamSimulation(wire).run(image, { sent, total ->
            assertEquals(image.size, total)
            progress.add(sent)
        }, {})
        assertEquals(SimulationResult.START_ACKNOWLEDGED, result)
        assertEquals(18, progress.size)
        assertEquals(512, progress.first())
        assertEquals(8956, progress.last())
        assertEquals(1, wire.jumps)
        assertEquals(listOf(0x12) + List(18) { 0xfb06 } + 0xfb08, wire.commands)
    }

    @Test fun transferFaultsPreventJump() {
        val cases = listOf(
            SimulatedRom.Fault.DETACH_ON_WRITE to UsbFailure.DISCONNECTED,
            SimulatedRom.Fault.TIMEOUT_ON_WRITE to UsbFailure.TIMEOUT,
            SimulatedRom.Fault.INVALID_STATUS to UsbFailure.RESPONSE
        )
        for ((fault, outcome) in cases) {
            val wire = SimulatedRom(fault = fault)
            assertFailure(outcome) { RamSimulation(wire).run(approved(), { _, _ -> }, {}) }
            assertEquals(0, wire.jumps)
        }
    }

    @Test fun disconnectAfterJumpStaysUnconfirmedWithoutRetry() {
        val wire = SimulatedRom(fault = SimulatedRom.Fault.DETACH_ON_JUMP)
        val outcome = RamSimulation(wire).run(approved(), { _, _ -> }, {})
        assertEquals(SimulationResult.START_UNCONFIRMED, outcome)
        assertEquals(1, wire.jumps)
        assertEquals(1, wire.commands.count { it == 0xfb08 })
        val invalid = SimulatedRom(fault = SimulatedRom.Fault.BAD_JUMP_ECHO)
        assertFailure(UsbFailure.RESPONSE) { RamSimulation(invalid).run(approved(), { _, _ -> }, {}) }
    }

    private fun approved() = checkNotNull(ApprovedApp.inspect(diagnostic()).approved)
    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun status(tag: Int, residue: Int = 0, code: Int = 0): ByteArray =
        ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(RomProtocol.CSW).putInt(tag).putInt(residue).put(code.toByte()).array()

    private class Responses(vararg items: ByteArray) : BulkWire {
        private val responses = ArrayDeque(items.toList())
        override val inPacketSize = 64
        override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
            assertTrue(RomProtocol.inquiryOnly(bytes))
            return length
        }
        override fun read(buffer: ByteArray): Int {
            val response = responses.removeFirst()
            response.copyInto(buffer)
            return response.size
        }
    }

    private fun assertFailure(reason: UsbFailure, body: () -> Unit) {
        try {
            body()
            fail("Expected $reason")
        } catch (error: UsbProblem) {
            assertEquals(reason, error.reason)
        }
    }
}
