package com.josxtm.fm1ramloader

import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.Closeable

/**
 * Hardware command surface is intentionally read-only: exactly one static SysEx 0x12.
 * No generic command sender, backup or RAM execution in comp12.
 */
class MbootMidiActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingFailure: String? = null
    private val queryController = MbootQueryController(
        object : MbootQueryTimer {
            override fun schedule(delayMs: Long, task: () -> Unit): MbootQueryCancellation {
                handler.postDelayed(task, delayMs)
                return MbootQueryCancellation { handler.removeCallbacks(task) }
            }
        },
        onFinished = { result -> finishQuery(result) }
    )
    private lateinit var manager: MidiManager
    private var device: MidiDevice? = null
    private var midiInfo: MidiDeviceInfo? = null
    private var input: MidiInputPort? = null
    private var output: MidiOutputPort? = null

    private val midiCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (midiInfo?.id == info.id) {
                runOnUiThread {
                    record("Dispositivo MIDI retirado por Android")
                    queryController.disconnected()
                }
            }
        }
    }
    private var status by mutableStateOf("Sin consultar")
    private var identity by mutableStateOf<MbootIdentityProtocol.Identity?>(null)
    private var running by mutableStateOf(false)
    private var appVersion by mutableStateOf("desconocida")
    private var history by mutableStateOf(listOf<DiagnosticEvent>())
    private val exportReport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(DiagnosticReport.render(appVersion, "USB-MIDI", identity, history).toByteArray(Charsets.UTF_8))
            } ?: error("No se pudo abrir el informe")
            record("Informe técnico exportado (sin UID ni claves)")
        }.onFailure { record("Error al exportar informe técnico") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(MidiManager::class.java)
        appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "desconocida"
        record("Actividad MIDI abierta · APK $appVersion")
        manager.registerDeviceCallback(midiCallback, handler)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF65E8CD))) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("FM1 · M-BOOTLOADER MIDI", style = MaterialTheme.typography.headlineMedium)
                        Text("FM1 Manager · $appVersion · Identificación 0x12 de solo lectura. Nunca borra ni escribe Flash.")
                        Text("1. Desconecta el cargador. 2. Enciende el FM1 manteniendo PLAY. " +
                            "3. En su menú selecciona MIDI MODE. 4. Conecta la tablet por USB-C. " +
                            "5. Pulsa Consultar.")
                        Text("Estado: $status")
                        if (history.isNotEmpty()) {
                            Text("Historial de sesión", style = MaterialTheme.typography.titleMedium)
                            history.takeLast(4).forEach { Text("${it.time} · ${it.message}") }
                        }
                        identity?.let { info ->
                            Text(info.summary)
                            Text("Protocolo v1 · Carga RAM anunciada: " +
                                if (info.ramApps) "SÍ (no probada)" else "NO")
                            Text("Compatibilidad m-bootloader 2.x: " +
                                if (info.compatible) "compatible preliminar por protocolo (no autoriza carga)" else "incompatible")
                            Text("Identificación verificada por estructura, no autenticación criptográfica.")
                        }
                        Button(onClick = ::identify, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                            Text(if (running) "Consultando…" else "Detectar y consultar m-bootloader")
                        }
                        OutlinedButton(onClick = ::stop, enabled = running, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancelar consulta")
                        }
                        Text("Copia completa, carga APP1 y programación Flash: bloqueadas. " +
                            "Solo se emite el mensaje de consulta 0x12 después de seleccionar MIDI MODE.")
                        OutlinedButton(onClick = { exportReport.launch("fm1-midi-report.txt") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Exportar informe técnico")
                        }
                        OutlinedButton(onClick = { stop(); finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Volver")
                        }
                    }
                }
            }
        }
    }

    private fun record(message: String) {
        val time = Instant.now().toString()
        history = (history + DiagnosticEvent(time, message)).takeLast(32)
    }

    private fun identify() {
        stop()
        record("Inicio de detección USB-MIDI; solo consulta 0x12")
        identity = null
        val candidates = manager.devices.filter { info ->
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty()
            val product = info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT).orEmpty()
            info.type == MidiDeviceInfo.TYPE_USB && info.inputPortCount > 0 && info.outputPortCount > 0 &&
                (name.contains("FM-1 Update", true) || product.contains("FM-1 Update", true))
        }
        if (candidates.size != 1) {
            status = "Detectados ${candidates.size} dispositivos FM-1 Update. " +
                "Entra en MIDI MODE y deja solo un FM1 conectado."
            record("Detección ambigua: ${candidates.size} dispositivos candidatos")
            return
        }
        val info = candidates.single()
        midiInfo = info
        val token = queryController.start()
        pendingFailure = null
        running = true
        status = "Abriendo el dispositivo USB-MIDI · consulta 0x12 de solo lectura…"
        record("Dispositivo candidato seleccionado; abriendo puerto")
        manager.openDevice(info, { opened ->
            if (!queryController.isActive(token) || isDestroyed) {
                runCatching { opened?.close() }
                return@openDevice
            }
            if (opened == null) {
                fail(token, "Android no pudo abrir el puerto MIDI")
                return@openDevice
            }
            device = opened
            val collector = MbootIdentityCollector()
            val receiver = object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    val result = runCatching { collector.receive(msg, offset, count) }
                    if (result.isFailure) {
                        runOnUiThread { fail(token, "Respuesta MIDI inválida") }
                    } else {
                        result.getOrNull()?.let { frame ->
                            runOnUiThread {
                                if (!queryController.isActive(token)) return@runOnUiThread
                                val parsed = runCatching { MbootIdentityProtocol.decodeFrame(frame) }
                                if (parsed.isSuccess) {
                                    identity = parsed.getOrThrow()
                                    queryController.response(token)
                                } else fail(token, "Respuesta incompatible: ${parsed.exceptionOrNull()?.message}")
                            }
                        }
                    }
                }
            }
            output = opened.openOutputPort(0)
            input = opened.openInputPort(0)
            if (input == null || output == null) {
                fail(token, "El FM1 no ofrece los puertos MIDI necesarios")
                return@openDevice
            }
            try {
                output!!.connect(receiver)
                val query = MbootIdentityProtocol.identityQuery()
                input!!.send(query, 0, query.size)
                status = "Consulta 0x12 enviada; esperando respuesta…"
            } catch (e: Exception) {
                fail(token, "Error al consultar MIDI: ${e.javaClass.simpleName}")
                return@openDevice
            }
        }, handler)
    }

    private fun fail(token: Int, reason: String) {
        if (!queryController.isActive(token)) return
        pendingFailure = reason
        queryController.error(token)
    }

    private fun finishQuery(result: MbootQueryController.Result) {
        record("Consulta finalizada: ${result.name}")
        status = when (result) {
            MbootQueryController.Result.RESPONSE ->
                "Consulta completada · ${identity?.version} · SIN escritura Flash"
            MbootQueryController.Result.TIMEOUT -> "Tiempo agotado: sin identidad 0x12; conexión cerrada"
            MbootQueryController.Result.CANCELLED -> "Consulta cancelada; conexión MIDI cerrada"
            MbootQueryController.Result.DISCONNECTED -> "Dispositivo MIDI desconectado; sesión cerrada"
            MbootQueryController.Result.ERROR -> pendingFailure ?: "Error MIDI; conexión cerrada"
        }
        pendingFailure = null
        closeConnection()
    }

    private fun closeConnection() {
        running = false
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { device?.close() }
        input = null
        output = null
        device = null
        midiInfo = null
    }

    private fun stop() {
        if (running) queryController.cancel() else closeConnection()
    }

    override fun onDestroy() {
        manager.unregisterDeviceCallback(midiCallback)
        stop()
        super.onDestroy()
    }
}
