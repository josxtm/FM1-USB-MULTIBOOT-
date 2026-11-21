package com.josxtm.fm1ramloader

internal internal enum class ExecutionMethod { RAM, XIP, FLASH, UNKNOWN }
internal enum class RamPlanStatus { APPROVED_FOR_SOFTWARE_SIMULATION, REJECTED, NOT_EVIDENCED }

internal data class RamPlan(
    val status: RamPlanStatus,
    val method: ExecutionMethod,
    val warnings: List<String>
)

/** Conservative planner; approval here is only for software simulation. */
internal object RamExecutionPlanner {
    private const val BSS_START = 0x01C16800L
    private const val STACK_START = 0x01C1E000L

    fun plan(image: ByteArray, method: ExecutionMethod = ExecutionMethod.RAM): RamPlan {
        val inspection = ApprovedApp.inspect(image)
        val warnings = mutableListOf<String>()
        if (method != ExecutionMethod.RAM) {
            warnings += "$method no está habilitado en esta fase"
            return RamPlan(RamPlanStatus.NOT_EVIDENCED, method, warnings)
        }
        if (inspection.approved == null) {
            warnings += "APP1 rechazado por formato, CRC, SHA-256 o rango"
            return RamPlan(RamPlanStatus.REJECTED, method, warnings)
        }
        warnings += "Dirección y BSS proceden del artefacto validado, no de una medición del bootloader"
        warnings += "No autoriza 0x25, 0x30 ni ejecución física"
        return RamPlan(RamPlanStatus.APPROVED_FOR_SOFTWARE_SIMULATION, method, warnings)
    }

    fun reservedRanges(): List<LongRange> = listOf(
        BSS_START until STACK_START,
        0x01C1F800L until 0x01C20000L
    ).map { it.first..(it.last) }
}
