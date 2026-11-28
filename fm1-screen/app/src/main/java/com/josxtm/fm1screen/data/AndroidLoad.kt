// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.data

import android.os.Debug
import android.os.Process
import android.os.SystemClock

data class AndroidLoad(
    val cpuPercent: Double? = null, val javaHeapBytes: Long = 0, val nativeHeapBytes: Long = 0,
    val allocatedBytesPerSecond: Long? = null, val gcCount: Long? = null, val gcMillis: Long? = null,
)

class AndroidLoadSampler {
    private var lastTime = SystemClock.elapsedRealtime()
    private var lastCpu = Process.getElapsedCpuTime()
    private var lastAllocated: Long? = null

    fun sample(): AndroidLoad {
        val now = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        val elapsed = (now - lastTime).coerceAtLeast(1)
        val runtime = Runtime.getRuntime()
        val art = Debug.getRuntimeStats()
        val allocated = art["art.gc.bytes-allocated"]?.toLongOrNull()
        val change = lastAllocated?.let { previous -> allocated?.let { (it - previous).coerceAtLeast(0) } }
        return AndroidLoad(
            cpuPercent = (cpu - lastCpu) * 100.0 / elapsed,
            javaHeapBytes = runtime.totalMemory() - runtime.freeMemory(),
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
            allocatedBytesPerSecond = change?.let { it * 1000 / elapsed },
            gcCount = art["art.gc.gc-count"]?.toLongOrNull(),
            gcMillis = art["art.gc.gc-time"]?.toLongOrNull(),
        ).also {
            lastTime = now
            lastCpu = cpu
            lastAllocated = allocated
        }
    }
}
