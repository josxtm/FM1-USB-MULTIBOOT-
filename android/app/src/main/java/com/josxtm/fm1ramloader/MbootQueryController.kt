package com.josxtm.fm1ramloader

/** Small, Android-free lifecycle model used to test timeout/cancel/disconnect races. */
internal interface MbootQueryTimer {
    fun schedule(delayMs: Long, task: () -> Unit): MbootQueryCancellation
}

internal fun interface MbootQueryCancellation {
    fun cancel()
}

internal class MbootQueryController(
    private val timer: MbootQueryTimer,
    private val onFinished: (Result) -> Unit
) {
    enum class Result { RESPONSE, TIMEOUT, CANCELLED, DISCONNECTED, ERROR }

    private var generation = 0
    private var active = false
    private var alarm: MbootQueryCancellation? = null

    fun start(timeoutMs: Long = 8_000): Int {
        cancelInternal()
        val token = ++generation
        active = true
        alarm = timer.schedule(timeoutMs) { finish(token, Result.TIMEOUT) }
        return token
    }

    fun isActive(token: Int): Boolean = active && token == generation

    fun response(token: Int): Boolean = finish(token, Result.RESPONSE)

    fun error(token: Int): Boolean = finish(token, Result.ERROR)

    fun cancel() {
        if (active) finish(generation, Result.CANCELLED)
    }

    fun disconnected() {
        if (active) finish(generation, Result.DISCONNECTED)
    }

    private fun finish(token: Int, result: Result): Boolean {
        if (!active || token != generation) return false
        active = false
        alarm?.cancel()
        alarm = null
        onFinished(result)
        return true
    }

    private fun cancelInternal() {
        if (active) finish(generation, Result.CANCELLED)
        else alarm?.cancel().also { alarm = null }
    }
}
