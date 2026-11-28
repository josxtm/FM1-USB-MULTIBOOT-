// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.data

import android.os.Debug
import android.os.Process
import android.os.SystemClock

data class AndroidLoad(
    val cpuPercent: Double? = null, val javaHeapBytes: Long = 0, val nativeHeapBytes: Long = 0,
    val normalizedCpuPercent: Double? = null,
    val allocatedBytesPerSecond: Long? = null, val gcCount: Long? = null, val gcMillis: Long? = null,
)

class AndroidLoadSampler {
    private var lastTime: Long? = null
    private var lastCpu = Process.getElapsedCpuTime()
    private var lastAllocated: Long? = null

    fun sample(): AndroidLoad {
        val now = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        val elapsed = lastTime?.let { (now - it).coerceAtLeast(1) }
        val oneCore = elapsed?.let { (cpu - lastCpu) * 100.0 / it }
        val runtime = Runtime.getRuntime()
        val art = Debug.getRuntimeStats()
        val allocated = art["art.gc.bytes-allocated"]?.toLongOrNull()
        val change = lastAllocated?.let { previous -> allocated?.let { (it - previous).coerceAtLeast(0) } }
        return AndroidLoad(
            cpuPercent = oneCore,
            normalizedCpuPercent = oneCore?.div(runtime.availableProcessors().coerceAtLeast(1))?.coerceIn(0.0, 100.0),
            javaHeapBytes = runtime.totalMemory() - runtime.freeMemory(),
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
            allocatedBytesPerSecond = if (elapsed != null) change?.let { it * 1000 / elapsed } else null,
            gcCount = art["art.gc.gc-count"]?.toLongOrNull(),
            gcMillis = art["art.gc.gc-time"]?.toLongOrNull(),
        ).also {
            lastTime = now
            lastCpu = cpu
            lastAllocated = allocated
        }
    }
}
