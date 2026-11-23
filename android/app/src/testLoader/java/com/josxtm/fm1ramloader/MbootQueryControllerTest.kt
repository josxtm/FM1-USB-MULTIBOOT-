package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class MbootQueryControllerTest {
    private class FakeTimer : MbootQueryTimer {
        var delay = -1L
        var cancelled = 0
        var task: (() -> Unit)? = null
        override fun schedule(delayMs: Long, task: () -> Unit): MbootQueryCancellation {
            delay = delayMs
            this.task = task
            return MbootQueryCancellation { cancelled++ }
        }
        fun elapse() { task?.invoke() }
    }

    @Test fun timeoutIsDeterministicAndCancelsTheAlarm() {
        val timer = FakeTimer()
        val results = mutableListOf<MbootQueryController.Result>()
        val controller = MbootQueryController(timer) { results += it }
        controller.start(8000)
        assertEquals(8000L, timer.delay)
        timer.elapse()
        assertEquals(listOf(MbootQueryController.Result.TIMEOUT), results)
        assertEquals(1, timer.cancelled)
        assertFalse(controller.response(1))
    }

    @Test fun cancellationSuppressesLaterTimeout() {
        val timer = FakeTimer()
        val results = mutableListOf<MbootQueryController.Result>()
        val controller = MbootQueryController(timer) { results += it }
        val token = controller.start()
        controller.cancel()
        timer.elapse()
        assertEquals(listOf(MbootQueryController.Result.CANCELLED), results)
        assertFalse(controller.response(token))
    }

    @Test fun disconnectInvalidatesTheActiveGeneration() {
        val timer = FakeTimer()
        val results = mutableListOf<MbootQueryController.Result>()
        val controller = MbootQueryController(timer) { results += it }
        val token = controller.start()
        controller.disconnected()
        assertEquals(listOf(MbootQueryController.Result.DISCONNECTED), results)
        assertFalse(controller.response(token))
    }

    @Test fun staleResponseCannotCompleteAReplacementQuery() {
        val timer = FakeTimer()
        val results = mutableListOf<MbootQueryController.Result>()
        val controller = MbootQueryController(timer) { results += it }
        val old = controller.start()
        val current = controller.start()
        assertFalse(controller.response(old))
        assertTrue(controller.response(current))
        assertEquals(listOf(MbootQueryController.Result.CANCELLED,
            MbootQueryController.Result.RESPONSE), results)
    }
}
