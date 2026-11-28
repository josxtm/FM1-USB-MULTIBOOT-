package com.josxtm.fm1screen

import com.josxtm.fm1screen.performance.HealthAnalysis
import com.josxtm.fm1screen.performance.HealthLevel
import com.josxtm.fm1screen.performance.HealthSample
import com.josxtm.fm1screen.performance.healthLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthAnalysisTest {
    @Test fun thresholdsDistinguishExactBoundariesAndMissingSamples() {
        assertEquals(HealthLevel.UNKNOWN, healthLevel(null, 20.0, 50.0))
        assertEquals(HealthLevel.OK, healthLevel(20.0, 20.0, 50.0))
        assertEquals(HealthLevel.WARNING, healthLevel(50.0, 20.0, 50.0))
        assertEquals(HealthLevel.ERROR, healthLevel(50.1, 20.0, 50.0))
        assertEquals(HealthLevel.OK, healthLevel(25.0, 25.0, 75.0))
        assertEquals(HealthLevel.WARNING, healthLevel(75.0, 25.0, 75.0))
    }

    @Test fun anIdleDisplayDoesNotImplyTransportFailure() {
        assertEquals("Datos insuficientes",
            HealthAnalysis.evaluate(List(30) { HealthSample(2.0, 0.0, null, false) },
                false, false, false, false, false).action)
    }

    @Test fun sufficientSamplesProduceOnlyTheMostRelevantRecommendation() {
        fun action(cpu: Double, queue: Double, p95: Double, dynamic: Boolean = false, lost: Boolean = false) =
            HealthAnalysis.evaluate(List(29) { HealthSample(cpu, queue, p95, false) },
                dynamic, lost, false, false, false).action
        assertEquals("Revisar la conexión", action(72.0, 80.0, 55.0, true, lost = true))
        assertEquals("Probar con el panel dinámico desactivado", action(72.0, 80.0, 55.0, true))
        assertEquals("Probar ECO", action(72.0, 4.0, 20.0))
        assertEquals("Probar BALANCED", action(47.0, 4.0, 30.0))
        assertEquals("Probar ULTRA LOW LATENCY", action(11.0, 4.0, 14.0))
        assertEquals("Resincronizar pantalla", HealthAnalysis.evaluate(emptyList(), false, false, false,
            noResponse = true, recoveryWithPartialImage = false).action)
    }
}
