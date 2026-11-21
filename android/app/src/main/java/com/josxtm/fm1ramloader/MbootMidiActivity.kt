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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.Closeable

/**
 * Hardware command surface is intentionally read-only: exactly one static SysEx 0x12.
 * No generic command sender, backup or RAM execution in comp8.
 */
class MbootMidiActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: MidiManager
    private var device: MidiDevice? = null
    private var input: MidiInputPort? = null
    private var output: MidiOutputPort? = null
    private var generation = 0
    private var status by mutableStateOf("Sin consultar")
    private var identity by mutableStateOf<MbootIdentityProtocol.Identity?>(null)
    private var running by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(MidiManager::class.java)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF65E8CD))) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("FM1 · M-BOOTLOADER MIDI", style = MaterialTheme.typography.headlineMedium)
                        Text("Comp7 · Identificación 0x12 de solo lectura. Nunca borra ni escribe Flash.")
                        Text("1. Desconecta el cargador. 2. Enciende el FM1 manteniendo PLAY. " +
                            "3. En su menú selecciona MIDI MODE. 4. Conecta la tablet por USB-C. " +
                            "5. Pulsa Consultar.")
                        Text("Estado: $status")
                        identity?.let { info ->
                            Text(info.summary)
                            Text("Protocolo v1 · Carga RAM anunciada: " +
                                if (info.ramApps) "SÍ (no probada)" else "NO")
                            Text("Compatibilidad m-bootloader 2.x: " +
                                if (info.compatible) "compatible por versión (no autoriza carga)" else "incompatible")
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
                        OutlinedButton(onClick = { stop(); finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Volver")
                        }
                    }
                }
            }
        }
    }

    private fun identify() {
        stop()
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
            return
        }
        val token = ++generation
        val info = candidates.single()
        running = true
        status = "Abriendo el dispositivo USB-MIDI…"
        manager.openDevice(info, { opened ->
            if (token != generation || isDestroyed) {
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
                                if (token != generation) return@runOnUiThread
                                val parsed = runCatching { MbootIdentityProtocol.decodeFrame(frame) }
                                if (parsed.isSuccess) {
                                    identity = parsed.getOrThrow()
                                    status = "Consulta completada · ${identity?.version} · SIN escritura Flash"
                                    closeConnection()
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
        handler.postDelayed({
            if (token == generation && running) fail(token, "Tiempo agotado: sin identidad 0x12")
        }, 8000)
    }

    private fun fail(token: Int, reason: String) {
        if (token != generation) return
        status = reason
        closeConnection()
    }

    private fun closeConnection() {
        running = false
        generation++
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { device?.close() }
        input = null
        output = null
        device = null
    }

    private fun stop() {
        if (running) status = "Consulta cancelada"
        closeConnection()
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }
}
