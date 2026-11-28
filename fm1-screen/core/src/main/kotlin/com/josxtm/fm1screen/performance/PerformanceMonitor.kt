// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.performance

import com.josxtm.fm1screen.transport.MidiIngress
import kotlin.math.ceil

enum class PresentationMode(val title: String, val batchMs: Long, val intervalMs: Long) {
    ULTRA("ULTRA LOW LATENCY", 0, 8),
    BALANCED("BALANCED", 12, 16),
    ECO("ECO", 50, 66),
}

enum class Stage(val title: String) {
    MIDI_TIMESTAMP("Marca MIDI → callback"), MIDI_CALLBACK("Callback RX"), RX_QUEUE("Cola RX"),
    SYSEX("Ensamblado MIDI/SysEx"), SYSEX_SPAN("Primer → último fragmento gráfico"), DECODE("Decodificación de bloques"), FRAMEBUFFER("Aplicar región RGB565"),
    COALESCE("Agrupar bloques hasta publicar"), UI_QUEUE("Publicación → turno de Android"),
    BITMAP("Copiar RGB565 al bitmap"), CANVAS("Registrar dibujo LCD en Canvas"),
    INTERNAL_LATEST("RX más reciente → Canvas"), INTERNAL_OLDEST("RX más antigua → Canvas"),
}

data class Percentiles(val count: Int, val p50Ms: Double, val p95Ms: Double, val p99Ms: Double)

/** Fixed rolling windows. Sorting is done only when taking a diagnostic snapshot. */
class PerformanceMonitor {
    private val windows = Array(Stage.entries.size) { LatencyWindow() }

    @Volatile var ingress = MidiIngress.Depth()

    fun record(stage: Stage, elapsedNanos: Long) {
        if (elapsedNanos >= 0) windows[stage.ordinal].add(elapsedNanos)
    }

    fun clearSamples() = windows.forEach { it.reset() }

    fun snapshot(): Map<Stage, Percentiles> =
        Stage.entries.mapNotNull { stage -> windows[stage.ordinal].snapshot()?.let { stage to it } }.toMap()

    private class LatencyWindow {
        private val samples = LongArray(512)
        private var cursor = 0
        private var size = 0

        @Synchronized
        fun add(nanos: Long) {
            samples[cursor] = nanos
            cursor = (cursor + 1) % samples.size
            if (size < samples.size) size++
        }

        @Synchronized fun reset() { cursor = 0; size = 0 }

        @Synchronized private fun copy() = samples.copyOf(size)

        fun snapshot(): Percentiles? {
            val sorted = copy()
            if (sorted.isEmpty()) return null
            sorted.sort()
            fun percentile(q: Double) = sorted[(ceil(sorted.size * q).toInt() - 1)] / 1_000_000.0
            return Percentiles(sorted.size, percentile(0.50), percentile(0.95), percentile(0.99))
        }
    }
}
