package com.josxtm.fm1ramloader

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Model-only tests. They never claim a physical FM-1 nor access Android USB. */
class UsbcRamTransportTest {
    private val evidence = UsbcLabEvidence(
        independentlyObservedColdRom = true, flashBackupVerified = true,
        recoveryProcedureVerified = true, usbPowerAndRoleQualified = true,
        explicitOneShotConsent = true
    )
    private fun fakeApp(size: Int = 1024): ByteArray {
        val image = ByteArray(size) { (it % 199).toByte() }
        "APP1".toByteArray(Charsets.US_ASCII).copyInto(image, size - 8)
        image[size - 2] = 1
        image[size - 1] = 1
        val crc = crc16(image, size - 8)
        image[size - 4] = crc.toByte()
        image[size - 3] = (crc ushr 8).toByte()
        return image
    }

    @Test fun imageRequiresPinnedDigestBoardAndCrc() {
        val bytes = fakeApp()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertTrue(UsbcRamImage.checks(bytes, digest).all { it.second })
        assertFalse(UsbcRamImage.approved(bytes))
        bytes[10] = (bytes[10].toInt() xor 3).toByte()
        assertFalse(UsbcRamImage.checks(bytes, digest).all { it.second })
    }

    @Test fun onlyTwoAuditedApp1HashesAreAccepted() {
        assertEquals(64, UsbcRamImage.HARDWARE_PINNED_SHA256.length)
        assertEquals(64, UsbcRamImage.LAB_PINNED_SHA256.length)
        assertNotEquals(UsbcRamImage.HARDWARE_PINNED_SHA256, UsbcRamImage.LAB_PINNED_SHA256)
        assertNull(UsbcRamImage.profile(fakeApp()))
        assertFalse(UsbcRamImage.approved(fakeApp()))
    }

    @Test fun evidenceAndImageGateSendNoUsbCommands() {
        val image = fakeApp()
        val r = FakeRom()
        val session = UsbcRamTransport(r, { true }, approveImage = { true })
        assertEquals(UsbcRamResult.BLOCKED, session.launch(image, UsbcLabEvidence()).result)
        assertTrue(r.operations.isEmpty())
        assertEquals(UsbcRamResult.BLOCKED, session.launch(image, evidence).result)
        val second = FakeRom()
        assertEquals(UsbcRamResult.IMAGE_REJECTED,
            UsbcRamTransport(second, { true }).launch(image, evidence).result)
        assertTrue(second.operations.isEmpty())
    }

    @Test fun approvedOneShotOnlySendsRomRamCommands() {
        val original = fakeApp(1544)
        val remote = FakeRom()
        val progress = mutableListOf<Int>()
        val session = UsbcRamTransport(remote, { true }, { done, _ -> progress += done },
            approveImage = { it.contentEquals(original) })
        val result = session.launch(original, evidence)
        assertEquals(UsbcRamResult.JUMP_ACKNOWLEDGED, result.result)
        assertEquals(4, result.blocks)
        assertEquals(original.size, result.bytes)
        assertArrayEquals(original, remote.ram.toByteArray())
        assertEquals(listOf(512, 1024, 1536, 1544), progress)
        assertEquals(listOf("0012", "FC40", "FB06", "FB06", "FB06", "FB06", "FB08"), remote.operations)
        assertEquals(UsbcRamResult.BLOCKED, session.launch(original, evidence).result)
    }

    @Test fun residentAndAmbiguousProbeNeverReachRamWrite() {
        for (mode in listOf("resident", "ambiguous")) {
            val remote = FakeRom(mode)
            val outcome = UsbcRamTransport(remote, { true }, approveImage = { true })
                .launch(fakeApp(), evidence)
            assertEquals(if (mode == "resident") UsbcRamResult.RESIDENT_PRESENT
                         else UsbcRamResult.PROBE_AMBIGUOUS, outcome.result)
            assertEquals(listOf("0012", "FC40"), remote.operations)
        }
    }

    @Test fun interruptedTransferNeverJumpsOrRetries() {
        val remote = FakeRom("short-payload")
        val outcome = UsbcRamTransport(remote, { true }, approveImage = { true })
            .launch(fakeApp(), evidence)
        assertEquals(UsbcRamResult.TRANSFER_FAILED, outcome.result)
        assertFalse("FB08" in remote.operations)
        assertEquals(1, remote.operations.count { it == "FB06" })
    }

    @Test fun jumpWithoutAckIsUnconfirmedAndNotRepeated() {
        val remote = FakeRom("no-jump-ack")
        val outcome = UsbcRamTransport(remote, { true }, approveImage = { true })
            .launch(fakeApp(), evidence)
        assertEquals(UsbcRamResult.JUMP_UNCONFIRMED, outcome.result)
        assertEquals(1, remote.operations.count { it == "FB08" })
    }

    @Test fun disconnectBeforeFirstRequestPreventsUsbIo() {
        val remote = FakeRom()
        val outcome = UsbcRamTransport(remote, { false }, approveImage = { true })
            .launch(fakeApp(), evidence)
        assertEquals(UsbcRamResult.ROM_UNQUALIFIED, outcome.result)
        assertTrue(remote.operations.isEmpty())
    }

    private class FakeRom(private val fault: String = "") : RomConnection {
        val operations = mutableListOf<String>()
        val ram = ArrayList<Byte>()
        private var last = ""
        private var tag = 0
        private var length = 0
        private var dataStage = 0
        private var expectOut = 0

        override fun claim(force: Boolean) = true
        override fun release() = true
        override fun close() = Unit

        override fun bulkOut(data: ByteArray, timeoutMs: Int): Int {
            if (expectOut != 0) {
                val n = expectOut
                expectOut = 0
                if (fault == "short-payload") return data.size - 1
                assertEquals(n, data.size)
                ram.addAll(RomProtocol.transform(data).toList())
                return data.size
            }
            assertEquals(31, data.size)
            val cbw = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(RomProtocol.CBW, cbw.int)
            tag = cbw.int
            length = cbw.int
            val flags = cbw.get().toInt() and 255
            cbw.get()
            val cdbSize = cbw.get().toInt() and 255
            val cdb = data.copyOfRange(15, 31)
            last = if (cdbSize == 6 && cdb[0] == 0x12.toByte()) "0012"
                else "%02X%02X".format(cdb[0].toInt() and 255, cdb[1].toInt() and 255)
            assertTrue(last in listOf("0012", "FC40", "FB06", "FB08"))
            operations += last
            dataStage = 0
            expectOut = if (last == "FB06") length else 0
            if (expectOut != 0) {
                assertEquals(0, flags)
                assertTrue(length in 1..512)
            }
            return data.size
        }

        override fun bulkIn(data: ByteArray, timeoutMs: Int): Int {
            if (last == "FB08" && fault == "no-jump-ack") return -1
            if (last == "0012" && dataStage++ == 0) {
                val result = ByteArray(36)
                "WL82".toByteArray(Charsets.US_ASCII).copyInto(result, 8)
                "UBOOT1.00".toByteArray(Charsets.US_ASCII).copyInto(result, 16)
                result.copyInto(data)
                return 36
            }
            if (last == "FB08" && dataStage++ == 0) {
                data[0] = 0xfb.toByte()
                data[1] = 0x08
                return 16
            }
            if (last == "FC40" && fault == "resident" && dataStage++ == 0) {
                data[0] = 0xfc.toByte()
                data[1] = 0x40
                "MFLR".toByteArray().copyInto(data, 2)
                return 16
            }
            if (last == "FC40" && fault == "ambiguous" && dataStage++ == 0) {
                return 16
            }
            val status = if (last == "FC40" && fault != "resident" && fault != "ambiguous") 1 else 0
            val residue = if (status == 1) 16 else 0
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(RomProtocol.CSW).putInt(tag).putInt(residue).put(status.toByte())
            return 13
        }
    }
}
