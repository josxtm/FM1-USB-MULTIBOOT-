// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Original outline artwork. All paths share a 24-unit viewport and stroke. */
internal enum class Glyph(pathData: String) {
    BODY("M3 4h18v16H3z"),
    PANEL("M3 4h18v16H3z M7 8h10v8H7z"),
    PAD("M4 4h6v7H4z M14 4h6v7h-6z M4 14h6v6H4z M14 14h6v6h-6z"),
    BUTTON("M3 8h18v8H3z M7 11h10"),
    KNOB("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M12 12l4-5"),
    MARK("M4 16a8 8 0 1 1 16 0 M12 12l5-6 M12 12v5"),
    TEXT("M4 5h16 M12 5v14 M8 19h8"),
    LABEL("M3 5h18v8H3z M6 9h12 M6 17h12"),
    NAVIGATION("M4 12h.1 M12 12h.1 M20 12h.1 M3 7l-2 5 2 5 M21 7l2 5-2 5"),
    BEZEL("M3 3h18v18H3z M6 6h12v12H6z"),
    ACTIVE("M12 3v4 M5 6l3 3 M19 6l-3 3 M8 14v-3a2 2 0 0 1 4 0v3h5v4l-3 3H9l-5-6z"),
    PAD_ACTIVE("M3 3h8v8H3z M14 3h7v8h-7z M3 14h8v7H3z M14 17l2 3 5-6"),
    PAD_TEXT("M3 3h18v18H3z M7 7h10 M12 7v10"),
    BUTTON_ACTIVE("M3 7h18v10H3z M8 12l3 3 5-6"),
    GEAR("M10 3h4l.8 2.7 2 .9 2.6-1.2 2 3.5-2 1.9v2.4l2 1.9-2 3.5-2.6-1.2-2 .9L14 21h-4l-.8-2.7-2-.9-2.6 1.2-2-3.5 2-1.9v-2.4l-2-1.9 2-3.5 2.6 1.2 2-.9L10 3z M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z"),
    CLOSE("M6 6l12 12M18 6L6 18"),
    USB("M12 3v15m-3-12 3-3 3 3M12 16l-5-3V9m5 5 5-3V8m-2-2h4v4h-4z M12 18a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z"),
    CHIP("M6 6h12v12H6z M9 9h6v6H9z M9 3v3m6-3v3m-6 12v3m6-3v3M3 9h3m-3 6h3m12-6h3m-3 6h3"),
    CAMERA("M3 7h5l1.5-2h5L16 7h5v12H3z M12 10a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z"),
    UNDO("M9 5L4 10l5 5 M4 10h9a6 6 0 0 1 6 6v3"),
    SCREEN("M3 4h18v13H3z M12 17v3m-5 0h10"),
    BOLT("M13 2L4 14h7l-1 8 10-13h-8l1-7z"),
    PALETTE("M12 3a9 9 0 0 0 0 18h1a2 2 0 0 0 1.5-3.3 1.5 1.5 0 0 1 1.2-2.4H18a3 3 0 0 0 3-3A9 9 0 0 0 12 3z M7 11h.1m2-4h.1m4-1h.1m4 2h.1"),
    ACTIVITY("M2 12h5l3-7 3 14 3-7h6"),
    INFO("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M12 11v5m0-8h.01"),
    CARET("M6 9l6 6 6-6"),
    UP("M6 15l6-6 6 6"),
    CHECK("M5 12l5 5 9-10"),
    REFRESH("M20 8a8 8 0 0 0-14-2l-2 3m0-5v5h5m-5 7a8 8 0 0 0 14 2l2-3m0 5v-5h-5"),
    SEARCH("M10 4a6 6 0 1 0 0 12 6 6 0 0 0 0-12z M14.5 14.5 20 20"),
    IMPORT("M12 3v12m-4-4 4 4 4-4M4 15v5h16v-5"),
    EXPORT("M12 16V4m-4 4 4-4 4 4M4 15v5h16v-5"),
    FULL_VIEW("M2 4h20v16H2z M8 7h6v5H8z M5 8h.1m12 0h2m-2 3h2M5 15v2m3-2v2m3-2v2m3-2v2m3-2v2m2-2v2"),
    CONTROLS_VIEW("M2 4h20v16H2z M5 8h7v8H5z M15 8h4m-4 4h4m-4 4h4"),
    BIG_VIEW("M2 4h20v16H2z M8 7h8v10H8z"),
    MAXIMIZE("M4 9V4h5m6 0h5v5m0 6v5h-5m-6 0H4v-5"),
    BALANCE("M12 4v15m-6 1h12M4 8h16M5 8l-3 6h6zm14 0-3 6h6z"),
    LEAF("M20 4c-2 9-6 15-12 14-3-.5-4.5-3-3-6 2-5 8-5 15-8z M4 21l11-11"),
    FILE("M6 3h8l4 4v14H6z M14 3v5h4M9 12h6m-6 4h6"),
    LINK("M10 7l2-2a4 4 0 0 1 6 6l-2 2m-2 4-2 2a4 4 0 0 1-6-6l2-2m1 4 6-6");

    val outline = PathParser().parsePathString(pathData).toPath()
}

@Composable
internal fun SettingsIcon(glyph: Glyph, color: Color, dimension: Dp = 24.dp) {
    Canvas(Modifier.size(dimension)) {
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(glyph.outline, color, style = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
