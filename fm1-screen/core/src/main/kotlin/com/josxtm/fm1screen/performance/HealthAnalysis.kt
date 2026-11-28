// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.performance

enum class HealthLevel { OK, WARNING, ERROR, UNKNOWN }

fun healthLevel(value: Double?, warning: Double, error: Double): HealthLevel = when {
    value == null || !value.isFinite() -> HealthLevel.UNKNOWN
    value > error -> HealthLevel.ERROR
    value > warning -> HealthLevel.WARNING
    else -> HealthLevel.OK
}

data class HealthSample(val cpu: Double?, val queue: Double, val p95: Double?, val hot: Boolean)
data class HealthFinding(val action: String, val reason: String)

/** Recommendations only. Counters compare start and end. Samples represent real Android work. */
object HealthAnalysis {
    fun evaluate(samples: List<HealthSample>, dynamic: Boolean, newLostBytes: Boolean,
                 newReconnects: Boolean, noResponse: Boolean, recoveryWithPartialImage: Boolean): HealthFinding {
        if (newLostBytes || newReconnects) return HealthFinding("Revisar la conexión",
            "La entrada registró bytes descartados o reconexiones durante la observación.")
        if (noResponse || recoveryWithPartialImage) return HealthFinding("Resincronizar pantalla",
            "La sesión declaró un fallo de respuesta o una recuperación con el LCD incompleto.")
        val cpu = samples.mapNotNull { it.cpu }
        val latency = samples.mapNotNull { it.p95 }
        fun sustained(values: List<Double>, threshold: Double) =
            values.size >= 10 && values.count { it > threshold } * 2 >= values.size
        val pressured = sustained(cpu, 70.0) || samples.count { it.hot } >= 5
        if (dynamic && (pressured || sustained(samples.map { it.queue }, 75.0))) {
            return HealthFinding("Probar con el panel dinámico desactivado", "Se observó carga Android o presión sostenida en la cola.")
        }
        if (pressured) return HealthFinding("Probar ECO", "Se observó carga Android sostenida o estado térmico elevado.")
        if (latency.size < 10 || cpu.size < 10) return HealthFinding(
            "Datos insuficientes", "Una imagen estática puede ser normal. Cambia de pantalla en el FM-1 para obtener más muestras.")
        if (sustained(latency, 50.0) && sustained(samples.map { it.queue }, 75.0)) return HealthFinding(
            "Revisar la conexión", "La cola alta coincide con una latencia interna elevada.")
        if (sustained(cpu, 40.0)) return HealthFinding("Probar BALANCED", "La carga Android se mantiene por encima del 40 % normalizado.")
        return HealthFinding("Probar ULTRA LOW LATENCY", "Las muestras muestran margen de CPU para priorizar la presentación.")
    }
}
