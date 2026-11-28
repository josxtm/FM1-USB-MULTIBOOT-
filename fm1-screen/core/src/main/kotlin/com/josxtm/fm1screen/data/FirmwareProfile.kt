// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.data

import com.josxtm.fm1screen.protocol.Wire

data class FirmwareProfile(
    val id: String,
    val name: String,
    val version: String,
    val wire: Wire,
    val deviceNames: List<String>,
    val pads: List<String?>,
    val buttons: List<String?>,
    val knobs: List<String?>,
    val evidence: String,
    val source: String,
    val limits: String,
) {
    init {
        require(id.matches(ID_PATTERN)) { "Identificador de perfil inválido" }
        fun text(value: String, max: Int) =
            require(value.isNotBlank() && value.length <= max && value.none(Char::isISOControl))
        text(name, 40)
        text(version, 64)
        text(evidence, 100)
        text(source, 300)
        text(limits, 500)
        require(deviceNames.size <= 32)
        deviceNames.forEach { text(it, 80) }
        listOf(pads to 27, buttons to 14, knobs to 8).forEach { (items, count) ->
            require(items.size == count) { "Cantidad de etiquetas inválida" }
            items.filterNotNull().forEach { text(it, 18) }
        }
    }

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,47}")
    }
}
