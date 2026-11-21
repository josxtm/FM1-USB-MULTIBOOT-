package com.josxtm.fm1ramloader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var usb: AndroidUsb
    private val worker = Executors.newSingleThreadExecutor()
    private var bytes: ByteArray? = null
    private var inspection by mutableStateOf<Inspection?>(null)
    private var fileSize by mutableStateOf<Int?>(null)
    private var imageState by mutableStateOf("Selecciona un archivo APP1")
    private var operationState by mutableStateOf("Carga física bloqueada")
    private var busy by mutableStateOf(false)
    private var progress by mutableFloatStateOf(0f)
    private var lines by mutableStateOf<List<String>>(emptyList())
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) choose { contentResolver.openInputStream(uri) }
    }
    private val saveLog = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) export(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = AndroidUsb(this, ::record)
        record("FM1 RAM Loader · carga física bloqueada")
        setContent {
            LoaderScreen(
                usb = usb.panel,
                image = inspection,
                fileSize = fileSize,
                imageState = imageState,
                operation = operationState,
                progress = progress,
                busy = busy,
                lines = lines,
                detect = usb::refresh,
                selectUsb = usb::select,
                connect = usb::connect,
                connectExclusive = usb::connectExclusive,
                disconnect = usb::disconnect,
                selectFile = { filePicker.launch(arrayOf("*/*")) },
                useReference = { choose { assets.open(AppSlot.DIAGNOSTIC) } },
                validate = ::validate,
                simulate = ::simulate,
                export = { saveLog.launch("fm1-ram-loader.log.txt") }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::usb.isInitialized) usb.refresh()
    }

    private fun choose(open: () -> InputStream?) {
        if (busy) return
        busy = true
        bytes = null
        inspection = null
        fileSize = null
        progress = 0f
        operationState = "Carga física bloqueada"
        imageState = "Leyendo archivo"
        worker.execute {
            val result = runCatching {
                checkNotNull(open()).use(::readBounded)
            }
            updateUi {
                busy = false
                val data = result.getOrNull()
                when {
                    data == null -> {
                        imageState = "No se pudo leer el archivo"
                        record("APP1 · lectura fallida")
                    }
                    data.size > AppSlot.MAX_BYTES -> {
                        imageState = "Archivo mayor que la ventana RAM"
                        record("APP1 · tamaño fuera de rango")
                    }
                    else -> {
                        bytes = data
                        fileSize = data.size
                        imageState = "Archivo leído · falta validar"
                        record("APP1 · ${data.size} bytes leídos")
                    }
                }
            }
        }
    }

    private fun validate() {
        val data = bytes ?: return
        if (busy) return
        busy = true
        imageState = "Validando formato, CRC y SHA-256"
        worker.execute {
            val result = ApprovedApp.inspect(data)
            updateUi {
                inspection = result
                busy = false
                val approved = result.approved != null
                imageState = if (approved) "Archivo aprobado para simulación" else "Archivo rechazado"
                record(if (approved) "APP1 · huella y tráiler verificados" else {
                    "APP1 · rechazado · " + result.checks.filterNot { it.second }.joinToString { it.first }
                })
            }
        }
    }

    private fun simulate() {
        val app = inspection?.approved ?: return
        if (busy) return
        busy = true
        progress = 0f
        operationState = "Simulación en curso"
        record("SIM · inicio · ${app.size} B")
        worker.execute {
            val result = runCatching {
                RamSimulation().run(app, { done, total ->
                    updateUi { progress = done.toFloat() / total }
                }, { line -> updateUi { record(line) } })
            }
            updateUi {
                busy = false
                operationState = when (result.getOrNull()) {
                    SimulationResult.START_ACKNOWLEDGED -> "Simulación completada"
                    SimulationResult.START_UNCONFIRMED -> "Simulación · salto sin confirmación"
                    null -> {
                        val failure = result.exceptionOrNull() as? UsbProblem
                        (failure?.reason?.label ?: "Error interno de simulación").also { record("SIM · $it") }
                    }
                }
            }
        }
    }

    private fun export(uri: Uri) {
        val text = lines.joinToString("\n", postfix = "\n")
        worker.execute {
            val written = runCatching {
                checkNotNull(contentResolver.openOutputStream(uri)).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            updateUi { record(if (written) "Consola · registro exportado" else "Consola · no se pudo exportar") }
        }
    }

    private fun record(message: String) {
        lines = (lines + "${LocalTime.now().format(clock)}  $message").takeLast(200)
    }

    private fun updateUi(block: () -> Unit) = runOnUiThread { if (!isDestroyed) block() }

    override fun onDestroy() {
        usb.close()
        worker.shutdownNow()
        super.onDestroy()
    }
}

private fun readBounded(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    val limit = AppSlot.MAX_BYTES + 1
    while (output.size() < limit) {
        val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        check(count > 0)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
