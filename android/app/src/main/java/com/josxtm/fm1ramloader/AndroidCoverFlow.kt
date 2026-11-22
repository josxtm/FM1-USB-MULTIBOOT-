package com.josxtm.fm1ramloader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Compact tablet Cover Flow. It only changes selection or opens inspection. */
@Composable
internal fun AndroidCoverFlow(
    items: List<LibraryItem>, selected: Int,
    onPrevious: () -> Unit, onNext: () -> Unit, onInspect: () -> Unit
) {
    if (items.isEmpty()) return
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val animatedDrag by animateFloatAsState(dragDistance, label = "cover-flow-drag")
    val safeSelected = selected.coerceIn(0, items.lastIndex)
    val center = items[safeSelected]
    val previous = items[(safeSelected - 1 + items.size) % items.size]
    val next = items[(safeSelected + 1) % items.size]
    val dragModifier = Modifier
        .fillMaxWidth()
        .pointerInput(items.size, selected) {
            detectDragGestures(
                onDragEnd = {
                    when {
                        dragDistance < -80f -> onNext()
                        dragDistance > 80f -> onPrevious()
                    }
                    dragDistance = 0f
                },
                onDragCancel = { dragDistance = 0f },
                onDrag = { change, amount ->
                    dragDistance += amount.x
                }
            )
        }
    Row(modifier = dragModifier.height(220.dp), horizontalArrangement = Arrangement.Center) {
        when (items.size) {
            1 -> CoverCard(center, Modifier.width(300.dp).graphicsLayer {
                translationX = animatedDrag
            }, prominent = true)
            2 -> {
                val adjacent = items[if (safeSelected == 0) 1 else 0]
                CoverCard(center, Modifier.width(260.dp).graphicsLayer {
                    translationX = animatedDrag
                }, prominent = true)
                CoverCard(adjacent, Modifier.width(170.dp).graphicsLayer {
                    alpha = 0.55f; scaleX = 0.82f; scaleY = 0.82f
                    translationX = animatedDrag + 12f
                })
            }
            else -> {
                CoverCard(previous, Modifier.width(170.dp).graphicsLayer {
                    alpha = 0.55f; scaleX = 0.82f; scaleY = 0.82f
                    translationX = animatedDrag - 12f
                })
                CoverCard(center, Modifier.width(260.dp).graphicsLayer {
                    translationX = animatedDrag
                }, prominent = true)
                CoverCard(next, Modifier.width(170.dp).graphicsLayer {
                    alpha = 0.55f; scaleX = 0.82f; scaleY = 0.82f
                    translationX = animatedDrag + 12f
                })
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPrevious, modifier = Modifier.weight(1f)) { Text("‹ Anterior") }
        Button(onClick = onInspect, modifier = Modifier.weight(1f)) { Text("PLAY · inspeccionar") }
        OutlinedButton(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Siguiente ›") }
    }
}

@Composable
private fun CoverCard(item: LibraryItem, modifier: Modifier, prominent: Boolean = false) {
    val color = if (prominent) Color(0xFF182F38) else Color(0xFF101B22)
    Box(modifier.padding(8.dp).clip(RoundedCornerShape(14.dp)).background(color).padding(14.dp)) {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.entry.label, style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                color = Color(0xFF65E8CD), maxLines = 2)
            Text("v${item.entry.version}", color = Color.White)
            Text(item.assessment.format.name, color = Color(0xFFB0C5C9))
            Text(if (item.assessment.integrityVerified) "SHA ✓" else "SHA pendiente",
                color = if (item.assessment.integrityVerified) Color(0xFF65E8CD) else Color(0xFFFFC857))
            if (prominent) Text("UNVERIFIED · no ejecutable", color = Color(0xFFFFC857))
        }
    }
}
