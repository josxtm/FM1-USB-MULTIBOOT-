// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.transport

import java.util.Locale

object DeviceIdentity {
    private val fm1 = Regex("""^FM[- ]?1(?:_[0-9A-F]{2,12}|[ ._-]+MIDI)?$""", RegexOption.IGNORE_CASE)
    private val atlatl = Regex("FM-1_742[0-9A-F]{2,8}")
    private val verifiedProducts = setOf(
        "FRASIL FM-1", "FM1 COMMUNITY", "M-VAVE FM-1", "FM-1 BIG SCREEN", "LOOM-8 FM-1", "POLLY FM-1",
    )

    fun scope(name: String, vendor: Int? = null, product: Int? = null): MirrorScope {
        if (isLoader(name) || isHyp1(name)) return MirrorScope.NONE
        val normalized = name.trim().uppercase(Locale.ROOT)
        if ((normalized == "FELUCCA" || atlatl.matches(normalized)) && vendor == 0x1209 && product == 0x0001) {
            return MirrorScope.FELUCCA
        }
        return if (fm1.matches(normalized) || normalized in verifiedProducts) MirrorScope.FM1 else MirrorScope.NONE
    }

    fun allowsRequests(name: String) = scope(name) != MirrorScope.NONE

    fun isHyp1(name: String) = name.trim().uppercase(Locale.ROOT) in setOf("HYP-1 FM-1", "REDTAILWORKS HYP-1 FM-1")

    fun isLoader(name: String) = listOf("update", "download", "boot", "loader", "rescue").any {
        name.contains(it, ignoreCase = true)
    }
}
