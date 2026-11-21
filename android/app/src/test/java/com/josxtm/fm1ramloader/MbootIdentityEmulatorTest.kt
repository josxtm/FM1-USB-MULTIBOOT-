package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class MbootIdentityEmulatorTest {
    @Test fun validIdentityCanBeFragmentedThroughCollector() {
        val exchange = MbootIdentityEmulator().exchange(MbootIdentityProtocol.identityQuery(), 12)
        assertEquals(MbootIdentityEmulator.Status.RESPONSE, exchange.status)
        assertEquals(12, exchange.delayMs)
        val frame = requireNotNull(exchange.frame)
        val collector = MbootIdentityCollector()
        var result: ByteArray? = null
        frame.asList().chunked(3).forEach { result = collector.receive(it.toByteArray()) ?: result }
        assertEquals("2.4-395e6cc", MbootIdentityProtocol.decodeFrame(requireNotNull(result)).version)
    }

    @Test fun emulatorRejectsEveryNonIdentityRequest() {
        assertThrows(IllegalArgumentException::class.java) {
            MbootIdentityEmulator().exchange(byteArrayOf(0xf0.toByte(), 0xf7.toByte()))
        }
    }

    @Test fun malformedScenariosNeverDecodeAsIdentity() {
        listOf(
            MbootIdentityEmulator.Scenario.WRONG_JEDEC,
            MbootIdentityEmulator.Scenario.INCOMPATIBLE_VERSION,
            MbootIdentityEmulator.Scenario.BAD_CHECKSUM,
            MbootIdentityEmulator.Scenario.CORRUPT_PAYLOAD
        ).forEach { scenario ->
            val frame = requireNotNull(MbootIdentityEmulator(scenario)
                .exchange(MbootIdentityProtocol.identityQuery()).frame)
            assertThrows(IllegalArgumentException::class.java) {
                MbootIdentityProtocol.decodeFrame(frame)
            }
        }
    }

    @Test fun timeoutAndDisconnectHaveNoFrame() {
        for (scenario in listOf(MbootIdentityEmulator.Scenario.TIMEOUT,
            MbootIdentityEmulator.Scenario.DISCONNECTED)) {
            val exchange = MbootIdentityEmulator(scenario)
                .exchange(MbootIdentityProtocol.identityQuery())
            assertNull(exchange.frame)
            assertNotEquals(MbootIdentityEmulator.Status.RESPONSE, exchange.status)
        }
    }
}
