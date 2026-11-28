package com.josxtm.fm1screen

import com.josxtm.fm1screen.data.ColorRole
import com.josxtm.fm1screen.data.FirmwareProfile
import com.josxtm.fm1screen.data.ProfileJson
import com.josxtm.fm1screen.data.ThemeLibrary
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.DeviceIdentity
import com.josxtm.fm1screen.ui.faceplate.ButtonNames
import com.josxtm.fm1screen.ui.faceplate.FaceplateLayout
import com.josxtm.fm1screen.ui.faceplate.KeybedGeometry
import com.josxtm.fm1screen.ui.faceplate.PSize
import com.josxtm.fm1screen.ui.faceplate.PanelLabel
import com.josxtm.fm1screen.ui.faceplate.PanelKnob
import com.josxtm.fm1screen.ui.theme.Rgb
import com.josxtm.fm1screen.ui.theme.ThemeTokens
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProfileThemeTest {
    private val assets = File("../assets")

    @Test fun bundledProfilesValidateAndRoundTrip() {
        val files = File(assets, "profiles").listFiles().orEmpty()
        assertTrue(files.isNotEmpty())
        files.forEach {
            val p = ProfileJson.decode(JSONObject(it.readText()))
            assertEquals(27, p.pads.size)
            assertEquals(p, ProfileJson.decode(ProfileJson.encode(p)))
        }
        val frasil = ProfileJson.decode(JSONObject(File(assets, "profiles/frasil.json").readText()))
        assertEquals(Wire.BASE, frasil.wire)
    }

    @Test fun invalidProfilesRejectBadSizeAndLabels() {
        val json = JSONObject(File(assets, "profiles/frasil.json").readText())
        assertThrows(IllegalArgumentException::class.java) {
            ProfileJson.decode(JSONObject(json.toString()).put("pads", JSONArray()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProfileJson.decode(JSONObject(json.toString()).put("resolution", JSONArray(listOf(240, 241))))
        }
    }

    @Test fun colorRolesMixIndependentlyAndOriginalCanBeRestored() {
        val mint = ThemeTokens(File(assets, "tokens.json").readText()).presets.first { it.name == "Mint" }
        val mixed = ColorRole.PAD.set(ColorRole.BODY.set(mint, Rgb.hex("CCCCCC")!!), Rgb.hex("#007C78")!!)
        assertEquals(mint.button, mixed.button)
        assertEquals(mint.knob, mixed.knob)
        assertEquals(mint.led, mixed.led)
        assertEquals(mint.copy(body = mixed.body), ColorRole.PAD.reset(mixed, mint))
        assertEquals(mixed, ThemeLibrary.decode(ThemeLibrary.encode(mixed)))
    }

    @Test fun bundledThemeRoundTripsAndKeepsTheOriginalButtons() {
        val file = File(assets, "themes/silver-turquoise.json")
        val theme = ThemeLibrary.decode(JSONObject(file.readText()))
        assertEquals("#C7CED3", theme.body.hex)
        assertEquals("#267C75", theme.key.hex)
        assertEquals("#376A60", theme.button.hex)
        assertEquals("#FFFFFF", theme.mark.hex)
        assertEquals(theme, ThemeLibrary.decode(ThemeLibrary.encode(theme)))
    }

    @Test fun legacyThemeMigratesAndKeepsIndependentActiveColors() {
        val file = File(assets, "themes/silver-turquoise.json")
        val fields = JSONObject(file.readText())
        val stored = fields.getJSONObject("colors")
        for (role in listOf(ColorRole.PAD_ACTIVE, ColorRole.PAD_TEXT, ColorRole.BUTTON_ACTIVE)) stored.remove(role.name)
        val legacy = ThemeLibrary.decode(fields)
        assertEquals(legacy.hi, legacy.padActive)
        assertEquals(legacy.buttonHi, legacy.buttonActive)
        val newColor = Rgb.hex("#1982C4")!!
        for (role in listOf(ColorRole.PAD_ACTIVE, ColorRole.PAD_TEXT, ColorRole.BUTTON_ACTIVE)) {
            val edited = role.set(legacy, newColor)
            assertEquals(newColor, role.get(edited))
            assertEquals(legacy, role.reset(edited, legacy))
            assertEquals(edited, ThemeLibrary.decode(ThemeLibrary.encode(edited)))
        }
    }

    @Test fun physicalLayoutHasEveryVerifiedControl() {
        val panel = FaceplateLayout.full(FaceplateLayout.Kind.PAD_LANDSCAPE)
        assertTrue(panel.displaySize < panel.keybed.w)
        val geometry = KeybedGeometry(PSize(panel.keybed.w, panel.keybed.h), KeybedGeometry.Layout.Row)
        assertEquals((0..26).toSet(), geometry.keys.map { it.index }.toSet())
        assertEquals(12, PanelLabel.GRID.size)
        assertEquals(8, PanelKnob.entries.size)
        assertEquals("OCT+", ButtonNames.STANDARD.label(PanelLabel.OCT_UP))
    }

    @Test fun updateAndUnknownDevicesCannotReceiveCommands() {
        assertTrue(DeviceIdentity.allowsRequests("FM-1_47030401"))
        assertTrue(DeviceIdentity.allowsRequests("FM-1 MIDI"))
        assertTrue(DeviceIdentity.allowsRequests("FRASIL FM-1"))
        assertTrue(DeviceIdentity.allowsRequests("Polly FM-1"))
        assertTrue(DeviceIdentity.allowsRequests("LOOM-8 FM-1"))
        assertFalse(DeviceIdentity.allowsRequests("FM-1 Update"))
        assertFalse(DeviceIdentity.allowsRequests("Generic USB MIDI"))
        assertTrue(DeviceIdentity.isLoader("WL80UBOOT"))
    }
}
