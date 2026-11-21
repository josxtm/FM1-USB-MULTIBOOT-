package com.josxtm.fm1ramloader

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class RomProbeTest {
    private val pair = BulkPair(UsbPipe(0x81, true, 64), UsbPipe(0x02, true, 64))

    @Test fun opensClaimsAndReleasesAfterOnlyOneInquiry() {
        val connection = FakeRom()
        val log = mutableListOf<String>()
        assertEquals("WL82 / UBOOT1.00", probe(connection, log = log::add))
        assertEquals(listOf("claim:false", "out", "in", "in", "release", "close"), connection.calls)
        assertTrue(log.any { it.startsWith("Bulk IN 0x81 · datos · resultado 36/64") })
        assertTrue(log.any { it.startsWith("Bulk IN 0x81 · CSW · resultado 13/64") })
    }

    @Test fun distinguishesNullOpenFromPermissionDeniedAndDetachDuringOpen() {
        assertEquals(RomFault.OPEN, failure { probe(null) }.fault)
        val denied = failure {
            probeRom(pair, false, { false }, { RomLink.NO_PERMISSION }, { error("open must not run") }, {})
        }
        assertEquals(RomFault.PERMISSION, denied.fault)
        assertEquals("openDevice", denied.stage)
        var attached = true
        val detached = failure {
            probeRom(pair, false, { false }, { if (attached) RomLink.READY else RomLink.DETACHED },
                { attached = false; null }, {})
        }
        assertEquals(RomFault.DETACHED, detached.fault)
    }

    @Test fun ordinaryClaimFailureDoesNotForceOrSendAnything() {
        val connection = FakeRom(normalClaim = false)
        val error = failure { probe(connection) }
        assertEquals(RomFault.CLAIM, error.fault)
        assertEquals("claimInterface(false)", error.stage)
        assertEquals(listOf("claim:false", "close"), connection.calls)
    }

    @Test fun confirmedExclusiveMayTryOnceAfterAnotherFailedNormalClaim() {
        val connection = FakeRom(normalClaim = false)
        assertEquals("WL82 / UBOOT1.00", probe(connection, exclusive = true, allow = true))
        assertEquals(listOf("claim:false", "claim:true", "out", "in", "in", "release", "close"), connection.calls)
        val newlyAvailable = FakeRom()
        probe(newlyAvailable, exclusive = true, allow = true)
        assertFalse("claim:true" in newlyAvailable.calls)
    }

    @Test fun storageGuardBlocksForceAndExclusiveRejectionClosesWithoutTransfer() {
        val blocked = FakeRom(normalClaim = false)
        assertEquals(RomFault.EXCLUSIVE_BLOCKED, failure { probe(blocked, exclusive = true) }.fault)
        assertEquals(listOf("claim:false", "close"), blocked.calls)
        val refused = FakeRom(normalClaim = false, forcedClaim = false)
        assertEquals(RomFault.EXCLUSIVE_CLAIM, failure { probe(refused, exclusive = true, allow = true) }.fault)
        assertEquals(listOf("claim:false", "claim:true", "close"), refused.calls)
    }

    @Test fun reenumerationAfterClaimClosesSessionBeforeBulkOut() {
        val connection = FakeRom()
        val error = failure {
            probe(connection, link = { if ("claim:false" in connection.calls) RomLink.DETACHED else RomLink.READY })
        }
        assertEquals(RomFault.DETACHED, error.fault)
        assertEquals(listOf("claim:false", "release", "close"), connection.calls)
    }

    @Test fun negativeBulkResultNamesTheStageAndDoesNotGuessErrno() {
        val connection = FakeRom(failRead = true)
        val error = failure { probe(connection) }
        assertEquals(RomFault.TRANSFER, error.fault)
        assertEquals("Bulk IN 0x81 · datos", error.stage)
        assertTrue(error.message.orEmpty().contains("-1 sin errno"))
        assertEquals(listOf("claim:false", "out", "in", "release", "close"), connection.calls)
    }

    @Test fun rejectsStaleCswTagAndClosesEvenIfReleaseFails() {
        val connection = FakeRom(wrongTag = true, releaseFails = true)
        val log = mutableListOf<String>()
        val error = failure { probe(connection, log = log::add) }
        assertEquals(RomFault.PROTOCOL, error.fault)
        assertTrue(error.message.orEmpty().contains("Etiqueta CSW"))
        assertEquals(listOf("release", "close"), connection.calls.takeLast(2))
        assertTrue(log.contains("releaseInterface() → IllegalStateException"))
    }

    private fun probe(
        connection: FakeRom?, exclusive: Boolean = false, allow: Boolean = false,
        link: () -> RomLink = { RomLink.READY }, log: (String) -> Unit = {}
    ) = probeRom(pair, exclusive, { allow }, link, { connection }, log)

    private fun failure(block: () -> Unit): RomProbeError = assertThrows(RomProbeError::class.java, block)

    private class FakeRom(
        private val normalClaim: Boolean = true,
        private val forcedClaim: Boolean = true,
        private val failRead: Boolean = false,
        private val wrongTag: Boolean = false,
        private val releaseFails: Boolean = false
    ) : RomConnection {
        val calls = mutableListOf<String>()
        private var tag = 0

        override fun claim(force: Boolean): Boolean {
            calls += "claim:$force"
            return if (force) forcedClaim else normalClaim
        }

        override fun bulkOut(data: ByteArray, timeoutMs: Int): Int {
            assertFalse("out" in calls)
            assertTrue(RomProtocol.inquiryOnly(data))
            calls += "out"
            tag = le32(data, 4)
            assertEquals(3000, timeoutMs)
            return data.size
        }

        override fun bulkIn(data: ByteArray, timeoutMs: Int): Int {
            assertEquals(5000, timeoutMs)
            val first = "in" !in calls
            calls += "in"
            if (failRead) return -1
            if (first) {
                "WL82".toByteArray().copyInto(data, 8)
                "UBOOT1.00".toByteArray().copyInto(data, 16)
                return 36
            }
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(RomProtocol.CSW)
                .putInt(if (wrongTag) tag + 1 else tag).putInt(0).put(0)
            return 13
        }

        override fun release(): Boolean {
            calls += "release"
            check(!releaseFails)
            return true
        }

        override fun close() { calls += "close" }
    }
}
