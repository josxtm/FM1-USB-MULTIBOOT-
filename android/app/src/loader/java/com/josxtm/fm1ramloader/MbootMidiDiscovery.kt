package com.josxtm.fm1ramloader

import android.media.midi.MidiDeviceInfo

/** Read-only discovery policy; it never opens a device or sends a MIDI command. */
internal object MbootMidiDiscovery {
    fun candidates(devices: Array<MidiDeviceInfo>): List<MidiDeviceInfo> = devices.filter { info ->
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty()
        val product = info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT).orEmpty()
        info.type == MidiDeviceInfo.TYPE_USB && info.inputPortCount > 0 && info.outputPortCount > 0 &&
            (name.contains("FM-1 Update", true) || product.contains("FM-1 Update", true))
    }
}
