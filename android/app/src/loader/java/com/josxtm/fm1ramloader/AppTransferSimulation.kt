package com.josxtm.fm1ramloader

/** Software-only fragmented transfer model; it never opens USB or sends 0x25/0x30. */
internal class AppTransferSimulation {
    enum class Result { COMPLETE, CANCELLED, TRANSPORT_ERROR, INVALID_IMAGE }
    data class Outcome(val result: Result, val bytesTransferred: Int)

    fun run(source: ByteArray, chunkBytes: Int = AppSlot.BLOCK_BYTES,
            cancelAtChunk: Int? = null, failAtChunk: Int? = null): Outcome {
        require(chunkBytes in 1..AppSlot.BLOCK_BYTES)
        val inspection = ApprovedApp.inspect(source)
        val approved = inspection.approved ?: return Outcome(Result.INVALID_IMAGE, 0)
        var transferred = 0
        var chunk = 0
        while (transferred < approved.size) {
            if (cancelAtChunk == chunk) return Outcome(Result.CANCELLED, transferred)
            if (failAtChunk == chunk) return Outcome(Result.TRANSPORT_ERROR, transferred)
            val bytes = approved.chunk(transferred, chunkBytes)
            require(bytes.isNotEmpty() && bytes.size <= chunkBytes)
            transferred += bytes.size
            chunk++
        }
        return Outcome(Result.COMPLETE, transferred)
    }
}
