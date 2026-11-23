package com.josxtm.fm1ramloader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ink = Color(0xFF080C0B)
private val panelColor = Color(0xFF121A18)
private val edge = Color(0xFF354743)
private val teal = Color(0xFF60E9CC)
private val muted = Color(0xFFADBFBB)
private val warn = Color(0xFFFFB99B)
private val palette = darkColorScheme(primary = teal, onPrimary = ink, surface = panelColor,
    onSurface = Color(0xFFEAF5F2), background = ink, onBackground = Color(0xFFEAF5F2), outline = edge)

@Composable
internal fun LoaderScreen(
    usb: UsbPanel, image: Inspection?, fileSize: Int?, imageState: String, operation: String,
    progress: Float, busy: Boolean, lines: List<String>, detect: () -> Unit, selectUsb: (Int) -> Unit,
    connect: () -> Unit, connectExclusive: (Int) -> Unit, disconnect: () -> Unit,
    selectFile: () -> Unit, useReference: () -> Unit,
    validate: () -> Unit, simulate: () -> Unit, export: () -> Unit,
    openLibrary: () -> Unit, openMidi: () -> Unit
) {
    MaterialTheme(colorScheme = palette) {
        Surface(color = ink, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                val wide = maxWidth >= 850.dp
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("FM1 RAM LOADER", color = teal, fontFamily = FontFamily.Monospace,
                        fontSize = 31.sp, fontWeight = FontWeight.Bold)
                    Text("Simulación · carga física bloqueada", color = muted, fontSize = 17.sp)
                    OutlinedButton(openLibrary) { Text("MicroSD · Biblioteca y exportación de firmwares") }
                    OutlinedButton(openMidi) { Text("M-BOOTLOADER 2.4 · Identificar por USB-MIDI (solo lectura)") }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            DevicePane(Modifier.weight(1f), usb, detect, selectUsb, connect, connectExclusive, disconnect)
                            ImagePane(Modifier.weight(1.13f), image, fileSize, imageState, busy, selectFile, useReference, validate)
                            OperationPane(Modifier.weight(0.9f), operation, progress, image?.approved != null && !busy, simulate)
                        }
                    } else {
                        DevicePane(Modifier.fillMaxWidth(), usb, detect, selectUsb, connect, connectExclusive, disconnect)
                        ImagePane(Modifier.fillMaxWidth(), image, fileSize, imageState, busy, selectFile, useReference, validate)
                        OperationPane(Modifier.fillMaxWidth(), operation, progress, image?.approved != null && !busy, simulate)
                    }
                    ConsolePane(lines, export)
                }
            }
        }
    }
}

@Composable
private fun DevicePane(
    modifier: Modifier, usb: UsbPanel, detect: () -> Unit, select: (Int) -> Unit,
    connect: () -> Unit, connectExclusive: (Int) -> Unit, disconnect: () -> Unit
) = Panel("Dispositivo", modifier) {
    Text(usb.state, color = teal)
    val device = usb.selected
    Value("Modo", device?.mode?.label ?: if (device != null) "ROM candidata" else "—")
    Value("VID:PID", device?.ids ?: "—")
    Value("Permiso", usb.permission)
    Value("Identidad", usb.identity ?: "Sin confirmar")
    Value("Memoria", "Sin consultar")
    if (usb.devices.size > 1) {
        Column {
            usb.devices.forEachIndexed { index, candidate ->
                FilterChip(usb.selection == candidate.id, { select(candidate.id) },
                    label = { Text("${index + 1}. ${candidate.mode?.label ?: "ROM candidata"} ${candidate.ids}") },
                    enabled = !usb.busy)
            }
        }
    }
    OutlinedButton(detect, enabled = !usb.busy, modifier = Modifier.fillMaxWidth()) { Text("Detectar FM1") }
    Button(connect, enabled = device != null && !usb.busy, modifier = Modifier.fillMaxWidth()) { Text("Conectar") }
    usb.exclusiveAttempt?.let { attempt ->
        var confirm by remember(attempt) { mutableStateOf(false) }
        OutlinedButton({ confirm = true }, enabled = !usb.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Reintentar con acceso exclusivo")
        }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Consulta USB exclusiva") },
            text = { Text("Android puede desvincular temporalmente el controlador de almacenamiento. " +
                "Continúa solo si el FM‑1 está en ROM y su almacenamiento no está montado ni en uso.") },
            confirmButton = { TextButton({ confirm = false; connectExclusive(attempt) }) { Text("Consultar una vez") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancelar") } }
        )
    }
    OutlinedButton(disconnect, enabled = device != null, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
}

@Composable
private fun ImagePane(
    modifier: Modifier, image: Inspection?, fileSize: Int?, status: String,
    busy: Boolean, pick: () -> Unit, reference: () -> Unit, validate: () -> Unit
) = Panel("Programa APP1", modifier) {
    Text(status, color = if (image != null && image.approved == null) warn else teal)
    Value("Tamaño", fileSize?.let { "$it bytes" } ?: "—")
    Value("Rango", if (image?.approved != null)
        "0x${hex(AppSlot.START)}–0x${hex(AppSlot.START + image.size - 1)}" else "—")
    image?.let {
        it.checks.forEach { (label, passed) ->
            Text("${if (passed) "✓" else "×"} $label", color = if (passed) muted else warn)
        }
        Text("SHA-256", color = muted, fontSize = 13.sp)
        Text(it.sha256.chunked(16).chunked(2).joinToString("\n") { part -> part.joinToString(" ") },
            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
    OutlinedButton(pick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar archivo APP1") }
    OutlinedButton(reference, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Usar diagnóstico de Fase 1") }
    Button(validate, enabled = !busy && fileSize != null, modifier = Modifier.fillMaxWidth()) { Text("Validar archivo") }
}

@Composable
private fun OperationPane(
    modifier: Modifier, operation: String, progress: Float, canSimulate: Boolean, simulate: () -> Unit
) = Panel("Operaciones", modifier) {
    Text(operation, color = teal)
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = teal, trackColor = edge)
    Text("${(progress * 100).toInt()} % · simulación", color = muted, fontFamily = FontFamily.Monospace)
    Button(simulate, enabled = canSimulate, modifier = Modifier.fillMaxWidth()) { Text("Simular carga") }
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Cargar en RAM") }
    Text("Pendiente de validación en el FM‑1 físico.", color = muted)
}

@Composable
private fun ConsolePane(lines: List<String>, export: () -> Unit) = Panel("Consola USB", Modifier.fillMaxWidth()) {
    val list = rememberLazyListState()
    LaunchedEffect(lines.lastOrNull()) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    LazyColumn(Modifier.fillMaxWidth().height(160.dp).background(ink).padding(10.dp), state = list,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(lines) { line -> Text(line, fontFamily = FontFamily.Monospace, color = muted, fontSize = 13.sp) }
    }
    OutlinedButton(export, enabled = lines.isNotEmpty()) { Text("Exportar registro") }
}

@Composable
private fun Value(label: String, value: String) {
    Text("$label · $value", color = muted, fontSize = 15.sp)
}

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.background(panelColor, shape).border(1.dp, edge, shape).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(1.dp))
        content()
    }
}
