package com.josxtm.fm1ramloader

import org.junit.Assert.*
import org.junit.Test

class RamExecutionPlannerTest {
    @Test fun approvedImageIsOnlyApprovedForSimulation() {
        val plan = RamExecutionPlanner.plan(diagnostic())
        assertEquals(RamPlanStatus.APPROVED_FOR_SOFTWARE_SIMULATION, plan.status)
        assertTrue(plan.warnings.any { it.contains("No autoriza") })
    }

    @Test fun xipAndFlashRemainUnevidenced() {
        for (method in listOf(ExecutionMethod.XIP, ExecutionMethod.FLASH, ExecutionMethod.UNKNOWN)) {
            assertEquals(RamPlanStatus.NOT_EVIDENCED,
                RamExecutionPlanner.plan(diagnostic(), method).status)
        }
    }

    @Test fun invalidImageIsRejected() {
        assertEquals(RamPlanStatus.REJECTED,
            RamExecutionPlanner.plan(diagnostic().copyOf(100)).status)
    }
}
