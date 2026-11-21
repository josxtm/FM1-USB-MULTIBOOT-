package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class AppTransferSimulationTest {
    @Test fun fragmentedApprovedTransferCompletesWithoutPhysicalCommands() {
        val outcome = AppTransferSimulation().run(diagnostic(), chunkBytes = 37)
        assertEquals(AppTransferSimulation.Result.COMPLETE, outcome.result)
        assertEquals(diagnostic().size, outcome.bytesTransferred)
    }

    @Test fun cancellationAndTransportFailureStopAtChunkBoundary() {
        val simulation = AppTransferSimulation()
        val cancelled = simulation.run(diagnostic(), chunkBytes = 64, cancelAtChunk = 2)
        assertEquals(AppTransferSimulation.Result.CANCELLED, cancelled.result)
        assertEquals(128, cancelled.bytesTransferred)
        val failed = simulation.run(diagnostic(), chunkBytes = 64, failAtChunk = 3)
        assertEquals(AppTransferSimulation.Result.TRANSPORT_ERROR, failed.result)
        assertEquals(192, failed.bytesTransferred)
    }

    @Test fun invalidImageIsRejectedBeforeAnyTransfer() {
        val outcome = AppTransferSimulation().run(diagnostic().copyOfRange(0, 100))
        assertEquals(AppTransferSimulation.Result.INVALID_IMAGE, outcome.result)
        assertEquals(0, outcome.bytesTransferred)
    }
}
