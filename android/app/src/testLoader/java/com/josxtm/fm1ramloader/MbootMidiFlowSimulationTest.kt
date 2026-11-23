package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class MbootMidiFlowSimulationTest {
    private class Timer : MbootQueryTimer {
        var task: (() -> Unit)? = null
        override fun schedule(delayMs: Long, task: () -> Unit): MbootQueryCancellation {
            this.task = task
            return MbootQueryCancellation { this.task = null }
        }
    }

    @Test fun queryControllerAndFragmentedMidiTransportCompleteTogether() {
        val timer = Timer()
        val results = mutableListOf<MbootQueryController.Result>()
        val controller = MbootQueryController(timer) { results += it }
        val token = controller.start()
        val exchange = MbootIdentityEmulator().exchange(MbootIdentityProtocol.identityQuery())
        val collector = MbootIdentityCollector()
        var frame: ByteArray? = null
        requireNotNull(exchange.frame).asList().chunked(2).forEach {
            frame = collector.receive(it.toByteArray()) ?: frame
        }
        val identity = MbootIdentityProtocol.decodeFrame(requireNotNull(frame))
        assertTrue(identity.compatible)
        assertTrue(controller.response(token))
        assertEquals(listOf(MbootQueryController.Result.RESPONSE), results)
    }

    @Test fun transportDisconnectCompletesAsDisconnectAndSuppressesTimer() {
        val timer = Timer()
        val results = mutableListOf<MbootQueryController.Result>()
        val controller = MbootQueryController(timer) { results += it }
        controller.start()
        controller.disconnected()
        timer.task?.invoke()
        assertEquals(listOf(MbootQueryController.Result.DISCONNECTED), results)
    }
}
