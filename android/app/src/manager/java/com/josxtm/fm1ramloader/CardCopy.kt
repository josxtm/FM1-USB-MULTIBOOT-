package com.josxtm.fm1ramloader

internal enum class CopyPhase(val title: String) {
    PREPARING("Preparando"), SPACE("Comprobando espacio"), COPYING("Copiando"),
    VERIFYING("Verificando archivo"), INDEX("Actualizando catálogo"),
    INDEX_VERIFY("Verificando índice"), DONE("Finalizado"), ERROR("Error"), STOPPED("Interrumpido")
}

internal data class CopyProgress(
    val phase: CopyPhase = CopyPhase.PREPARING,
    val file: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val written: Long = 0,
    val bytes: Long = 0,
    val reused: Long = 0,
    val fileWritten: Long = 0,
    val fileBytes: Long = 0,
    val detail: String = ""
) {
    val fraction get() = if (bytes == 0L) 0f else ((written + reused).toDouble() / bytes).coerceIn(0.0, 1.0).toFloat()
}

internal class CopyCancelled : java.io.IOException("Copia interrumpida")

// The caller stops offering cancellation at publication. The active slot remains available
// throughout staging, even if the process dies during the alternate slot write.
internal class CardCopy(
    private val report: (CopyProgress) -> Unit = {},
    private val cancelled: () -> Boolean = { false }
) {
    private var progress = CopyProgress()

    fun plan(total: Int, bytes: Long) {
        progress = CopyProgress(total = total, bytes = bytes)
        report(progress)
    }

    fun check() {
        if (cancelled() || Thread.currentThread().isInterrupted) throw CopyCancelled()
    }

    fun phase(phase: CopyPhase, item: CardRecord? = null) {
        progress = if (item == null) progress.copy(phase = phase)
        else progress.copy(phase = phase, file = "${item.name} · ${item.version}", fileWritten = 0, fileBytes = item.size)
        report(progress)
    }

    fun written(n: Int) {
        progress = progress.copy(written = progress.written + n, fileWritten = progress.fileWritten + n)
        report(progress)
    }

    fun verified(reused: Long) {
        progress = progress.copy(completed = progress.completed + 1, reused = progress.reused + reused)
        report(progress)
    }

    fun failed(error: Exception) {
        progress = progress.copy(phase = if (error is CopyCancelled) CopyPhase.STOPPED else CopyPhase.ERROR,
            detail = error.message.orEmpty())
        report(progress)
    }
}
