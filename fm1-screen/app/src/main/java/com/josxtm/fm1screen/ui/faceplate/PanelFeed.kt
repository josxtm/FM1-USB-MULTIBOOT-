// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui.faceplate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import com.josxtm.fm1screen.data.FirmwareProfile
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.SIDE
import com.josxtm.fm1screen.performance.PerformanceMonitor
import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.performance.Stage
import com.josxtm.fm1screen.protocol.FrameTiming
import com.josxtm.fm1screen.protocol.FrameUpdate
import com.josxtm.fm1screen.protocol.LatestFrame
import java.nio.ShortBuffer
import com.josxtm.fm1screen.transport.PanelOutput
import com.josxtm.fm1screen.protocol.ControlOrigin
import com.josxtm.fm1screen.protocol.controlOrigin

enum class Scaling(val title: String) { AUTO("Automático"), PIXEL("Píxel perfecto"), SMOOTH("Suavizado"), ZOOM("Zoom manual") }

object LedLevel {
    fun alpha(level: Int): Float = when {
        level >= 176 -> 1f
        level >= 40 -> 0.38f
        else -> 0f
    }

    fun lit(level: Int) = level >= 176
}

class PanelLights {
    var version by mutableIntStateOf(0)
    val keys = IntArray(27)
    val buttons = IntArray(14)
    var playGreen = 0
    var heldKeys = 0
    var heldLabels = 0
    var screenKeys = 0
    var screenButtons = 0
    var hasTelemetry = false

    fun keyDown(index: Int) = (heldKeys or screenKeys) and (1 shl index) != 0
    fun labelDown(index: Int) = (heldLabels or screenButtons) and (1 shl index) != 0
    fun keyOrigin(index: Int) = controlOrigin(heldKeys and (1 shl index) != 0,
        screenKeys and (1 shl index) != 0, hasTelemetry)
    fun buttonOrigin(index: Int) = controlOrigin(heldLabels and (1 shl index) != 0,
        screenButtons and (1 shl index) != 0, hasTelemetry)
}

class KnobFeed {
    val turns = IntArray(7)
    var master = 0
    var known = false
    var version by mutableIntStateOf(0)
}

class PanelFeed(private val performance: PerformanceMonitor) {
    lateinit var transport: PanelOutput
    var bitmap = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        private set
    var image by mutableStateOf(bitmap.asImageBitmap())
        private set
    var frames by mutableIntStateOf(0)
        private set
    var hasImage by mutableStateOf(false)
        private set
    var canControl by mutableStateOf(false)
        private set
    var scaling by mutableStateOf(Scaling.AUTO)
    var presentationMode by mutableStateOf(PresentationMode.BALANCED)
    val filter get() = if (presentationMode != PresentationMode.ULTRA && scaling == Scaling.SMOOTH)
        FilterQuality.Low else FilterQuality.None
    val pixelPerfect get() = scaling == Scaling.PIXEL
    val lights = PanelLights()
    val knobs = KnobFeed()
    var names by mutableStateOf(ButtonNames.STANDARD)
        private set
    private var profile by mutableStateOf<FirmwareProfile?>(null)
    private val fingers = IntArray(27)
    private val framePixels = ShortArray(SIDE * SIDE)
    private val frameBuffer = ShortBuffer.wrap(framePixels)
    private var lastDrawn = -1
    private var timing: FrameTiming? = null
    var skippedOnUi = 0L
        private set
    var draws = 0
        private set

    fun profile(newProfile: FirmwareProfile?) {
        profile = newProfile
        names = ButtonNames(newProfile?.buttons)
    }

    fun padLabel(index: Int): String = profile?.pads?.get(index) ?: NOTE_NAMES[index]
    fun knobLabel(knob: PanelKnob): String = profile?.knobs?.get(knob.ordinal) ?: knob.label

    fun knobOrigin(knob: PanelKnob): ControlOrigin = when {
        !knobs.known -> ControlOrigin.UNKNOWN
        knob == PanelKnob.MASTER -> ControlOrigin.DEVICE
        else -> ControlOrigin.DERIVED
    }

    fun setControls(enabled: Boolean) {
        if (canControl && !enabled) releaseAll()
        canControl = enabled
    }

    fun consume(mailbox: LatestFrame): FrameUpdate? = mailbox.consume(framePixels)

    fun paint(update: FrameUpdate?) {
        val timestamp = update?.timing
        val started = System.nanoTime()
        // A composed frame and the MP4 encoder can hold the previous bitmap concurrently.
        // Once displayed, each native bitmap is kept unchanged.
        val next = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.RGB_565)
        if (timestamp != null) {
            frameBuffer.rewind()
            next.copyPixelsFromBuffer(frameBuffer)
            performance.record(Stage.BITMAP, System.nanoTime() - started)
        }
        show(next, timestamp != null, timestamp)
    }

    private fun show(next: Bitmap, valid: Boolean, timestamp: FrameTiming? = null) {
        if (frames != lastDrawn && timing != null) skippedOnUi++
        timing = timestamp
        bitmap = next
        image = next.asImageBitmap()
        hasImage = valid
        frames++
    }

    fun demo() {
        val next = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(next)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0xFF10231C.toInt())
        val swatches = intArrayOf(0xFFEB626B.toInt(), 0xFFEFD77E.toInt(), 0xFF77D6A8.toInt(), 0xFF83B8E9.toInt())
        swatches.forEachIndexed { i, color ->
            paint.color = color
            canvas.drawRect(16f + i * 53, 151f, 60f + i * 53, 174f, paint)
        }
        paint.color = 0xFFE8FFF2.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = 42f
        canvas.drawText("DEMO", 120f, 101f, paint)
        paint.textSize = 14f
        canvas.drawText("SIN SEÑAL FM-1", 120f, 129f, paint)
        show(next, true)
    }

    fun panel(state: PanelState?) {
        val previousKeys = lights.heldKeys
        val previousButtons = lights.heldLabels
        val previousGreen = lights.playGreen
        val hadTelemetry = lights.hasTelemetry
        lights.hasTelemetry = state != null
        lights.heldKeys = state?.heldKeys ?: 0
        lights.heldLabels = state?.heldButtons ?: 0
        lights.playGreen = if (state != null && !state.playRed && state.litButtons and (1 shl PanelLabel.PLAY.ordinal) != 0) 255 else 0
        var changed = previousKeys != lights.heldKeys || previousButtons != lights.heldLabels ||
            previousGreen != lights.playGreen || hadTelemetry != lights.hasTelemetry
        fun level(lit: Int, dim: Int, index: Int) = when {
            lit and (1 shl index) != 0 -> 255
            dim and (1 shl index) != 0 -> 40
            else -> 0
        }
        for (i in lights.keys.indices) {
            val value = level(state?.litKeys ?: 0, state?.dimKeys ?: 0, i)
            if (lights.keys[i] != value) {
                changed = true
                lights.keys[i] = value
            }
        }
        for (i in lights.buttons.indices) {
            val value = level(state?.litButtons ?: 0, state?.dimButtons ?: 0, i)
            if (lights.buttons[i] != value) {
                changed = true
                lights.buttons[i] = value
            }
        }
        if (state?.playRed == true && lights.buttons[PanelLabel.PLAY.ordinal] == 0) {
            lights.buttons[PanelLabel.PLAY.ordinal] = 255
            changed = true
        }
        if (changed) lights.version++
        if (state == null) {
            knobs.turns.fill(0)
            knobs.master = 0
            knobs.known = false
        } else {
            // fm1-base reports relative steps. The pointer starts at the session's origin.
            knobs.known = true
            state.encoderSteps.forEachIndexed { i, step ->
                knobs.turns[i] = (knobs.turns[i] + step) % 20
            }
            knobs.master = state.master * 1023 / 4096
        }
        knobs.version++
    }

    fun keyDown(index: Int) {
        if (!canControl) return
        fingers[index]++
        if (fingers[index] != 1) return
        lights.screenKeys = lights.screenKeys or (1 shl index)
        lights.version++
        transport.key(index, true)
    }

    fun keyUp(index: Int) {
        if (fingers[index] == 0) return
        fingers[index]--
        if (fingers[index] != 0) return
        lights.screenKeys = lights.screenKeys and (1 shl index).inv()
        lights.version++
        transport.key(index, false)
    }

    fun holds(label: PanelLabel) = lights.screenButtons and (1 shl label.ordinal) != 0
    fun button(label: PanelLabel, down: Boolean) {
        if (!canControl || label == PanelLabel.SAVE || holds(label) == down) return
        val bit = 1 shl label.ordinal
        lights.screenButtons = if (down) lights.screenButtons or bit else lights.screenButtons and bit.inv()
        lights.version++
        transport.button(label.ordinal, down)
    }

    fun turn(knob: PanelKnob, delta: Int) {
        if (canControl && knob != PanelKnob.MASTER && delta != 0) transport.turn(knob.ordinal, delta)
    }

    fun master(value: Int) {
        if (canControl) transport.master(value.coerceIn(0, 1023) * 4096 / 1023)
    }

    fun releaseAll() {
        if (lights.screenKeys != 0 || lights.screenButtons != 0) {
            fingers.fill(0)
            lights.screenKeys = 0
            lights.screenButtons = 0
            lights.version++
        }
        if (::transport.isInitialized) transport.releaseControls()
    }

    fun beginDraw(): Long = if (frames != lastDrawn && timing != null) System.nanoTime() else 0L

    fun drawn(started: Long) {
        if (started == 0L) return
        val stamp = timing ?: return
        val now = System.nanoTime()
        lastDrawn = frames
        draws++
        performance.record(Stage.CANVAS, now - started)
        performance.record(Stage.INTERNAL_LATEST, now - stamp.lastReceivedNanos)
        performance.record(Stage.INTERNAL_OLDEST, now - stamp.firstReceivedNanos)
    }

    companion object {
        private val NOTE_NAMES = List(27) { index ->
            val midi = 53 + index
            listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")[midi % 12] + (midi / 12 - 1)
        }
    }
}
