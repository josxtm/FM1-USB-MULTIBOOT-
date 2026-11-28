// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import android.view.PixelCopy
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.josxtm.fm1screen.capture.Recording
import com.josxtm.fm1screen.capture.CaptureKind
import com.josxtm.fm1screen.capture.writePng
import com.josxtm.fm1screen.capture.saveMedia
import com.josxtm.fm1screen.data.AndroidLoad
import com.josxtm.fm1screen.data.AndroidLoadSampler
import com.josxtm.fm1screen.data.FirmwareProfile
import com.josxtm.fm1screen.data.ProfileJson
import com.josxtm.fm1screen.data.ProfileLibrary
import com.josxtm.fm1screen.data.ThemeLibrary
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.PanelUpdates
import com.josxtm.fm1screen.performance.PerformanceMonitor
import com.josxtm.fm1screen.performance.HealthAnalysis
import com.josxtm.fm1screen.performance.HealthFinding
import com.josxtm.fm1screen.performance.HealthSample
import com.josxtm.fm1screen.performance.Percentiles
import com.josxtm.fm1screen.performance.PresentationMode
import com.josxtm.fm1screen.performance.Stage
import com.josxtm.fm1screen.protocol.FrameBuffer
import com.josxtm.fm1screen.protocol.FrameTiming
import com.josxtm.fm1screen.protocol.LatestFrame
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.DeviceIdentity
import com.josxtm.fm1screen.transport.LinkKind
import com.josxtm.fm1screen.transport.MidiCategory
import com.josxtm.fm1screen.transport.MidiPorts
import com.josxtm.fm1screen.transport.MidiIngress
import com.josxtm.fm1screen.transport.MidiTransport
import com.josxtm.fm1screen.transport.MirrorScope
import com.josxtm.fm1screen.transport.StreamState
import com.josxtm.fm1screen.transport.MirrorStats
import com.josxtm.fm1screen.transport.Target
import com.josxtm.fm1screen.transport.TransportListener
import com.josxtm.fm1screen.ui.Settings
import com.josxtm.fm1screen.ui.SettingsGear
import com.josxtm.fm1screen.ui.QuickCapture
import com.josxtm.fm1screen.ui.faceplate.FaceplateUi
import com.josxtm.fm1screen.ui.faceplate.FaceplateView
import com.josxtm.fm1screen.ui.faceplate.PanelChip
import com.josxtm.fm1screen.ui.faceplate.PanelFeed
import com.josxtm.fm1screen.ui.faceplate.Scaling
import com.josxtm.fm1screen.ui.faceplate.ScreenProtector
import com.josxtm.fm1screen.ui.theme.Theme
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.Executors

data class MidiLogEntry(val category: MidiCategory, val line: String)

class MainActivity : ComponentActivity(), TransportListener {
    private val prefs by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private val main = Handler(Looper.getMainLooper())
    private val disk = Executors.newSingleThreadExecutor()
    private lateinit var transport: MidiTransport
    private lateinit var themeLibrary: ThemeLibrary
    private lateinit var profileLibrary: ProfileLibrary
    private lateinit var protector: ScreenProtector
    private val mailbox = LatestFrame()
    private val performanceMonitor = PerformanceMonitor()
    private val loadSampler = AndroidLoadSampler()
    private val diagnosticsWorker = Executors.newSingleThreadExecutor()
    private var diagnosticsSamplePending = false
    private var diagnosticsVisible = false
    private data class AnalysisRun(val started: Long, val lost: Long, val reconnects: Int,
                                   val samples: MutableList<HealthSample> = mutableListOf())
    private var analysis: AnalysisRun? = null
    private var sessionStarted = 0L
    var analysisRemaining by mutableStateOf<Int?>(null)
        private set
    var analysisFinding by mutableStateOf<HealthFinding?>(null)
        private set

    var thermalStatus by mutableStateOf<Int?>(null)
        private set
    private val panelUpdates = PanelUpdates()
    private val showPanel = Runnable {
        val state = panelUpdates.take()
        if (state != null && !demo && !dead && dynamicPanel && foreground && connected) feed.panel(state)
    }
    private lateinit var displayClock: Choreographer
    var uiDiscardedFrames by mutableStateOf(0L)
        private set
    var transferBytes by mutableStateOf(0L)
        private set
    var performanceStages by mutableStateOf(emptyMap<Stage, Percentiles>())
        private set
    var rxDepth by mutableStateOf(MidiIngress.Depth())
        private set
    var androidLoad by mutableStateOf(AndroidLoad())
        private set
    var presentation by mutableStateOf(PresentationMode.ULTRA)
        private set
    private var watch = false
    private var foreground = false
    @Volatile private var dead = false
    private var uriReader: ((String) -> Unit)? = null
    private var uriWriter: ((OutputStream) -> Unit)? = null
    private var recording: Recording? = null
    private var video: File? = null
    private val feed = PanelFeed(performanceMonitor)
    private val drawLatest = Choreographer.FrameCallback {
        val started = System.nanoTime()
        val update = feed.consume(mailbox) ?: return@FrameCallback
        if (!demo && !dead) {
            update.timing?.let { performanceMonitor.record(Stage.UI_QUEUE, started - it.publishedNanos) }
            feed.paint(update)
            if (update.timing == null) recording?.stop() else recording?.update(feed.bitmap)
        }
    }
    val faceplate = FaceplateUi()

    var theme by mutableStateOf<Theme?>(null)
        private set
    var themes by mutableStateOf(emptyList<Theme>())
        private set
    var profiles by mutableStateOf(emptyList<FirmwareProfile>())
        private set
    var profile by mutableStateOf<FirmwareProfile?>(null)
        private set
    var autoProfile by mutableStateOf(true)
        private set
    var deviceList by mutableStateOf(emptyList<Target>())
        private set
    var protocol by mutableStateOf(Wire.AUTO)
        private set
    var link by mutableStateOf(LinkKind.USB)
        private set
    var connected by mutableStateOf(false)
        private set
    var midiPorts by mutableStateOf(MidiPorts())
        private set
    var connectionText by mutableStateOf("Buscando FM-1 por USB-C")
        private set
    var mirrorStats by mutableStateOf(MirrorStats())
        private set
    var rendersPerSecond by mutableStateOf(0)
        private set
    var viewOnly by mutableStateOf(true)
        private set
    var dynamicPanel by mutableStateOf(false)
        private set
    fun selectDynamicPanel(value: Boolean) {
        if (dynamicPanel == value) return
        cancelAnalysis()
        dynamicPanel = value
        prefs.edit().putBoolean("dynamicPanel", value).apply()
        clearPanel()
        updateTelemetry()
    }
    var demo by mutableStateOf(false)
        private set
    var streaming by mutableStateOf(false)
        private set
    var stayAwake by mutableStateOf(true)
        private set
    var logs by mutableStateOf(emptyList<MidiLogEntry>())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var recordingActive by mutableStateOf(false)
        private set
    private var exportingMedia = false
    var videoReady by mutableStateOf(false)
        private set

    val scaling get() = feed.scaling
    val hasImage get() = feed.hasImage
    val hasPanelTelemetry get() = feed.lights.hasTelemetry
    val originalTheme get() = themeLibrary.original

    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) transport.scanBle() else message = "Permiso de Bluetooth MIDI denegado"
    }
    private val openJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val reader = uriReader
        uriReader = null
        if (uri != null && reader != null) {
            disk.execute {
                val result = runCatching {
                    requireNotNull(contentResolver.openInputStream(uri)).use(::readBounded)
                }
                main.post {
                    result.onSuccess { runCatching { reader(it) }.onFailure { ex -> message = ex.localizedMessage ?: "JSON inválido" } }
                        .onFailure { message = it.localizedMessage ?: "No se pudo leer el archivo" }
                }
            }
        }
    }
    private val saveJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json"), ::saveTo)
    private val saveText = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), ::saveTo)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayClock = Choreographer.getInstance()
        dynamicPanel = prefs.getBoolean("dynamicPanel", false)
        presentation = enumValue(prefs.getString("presentation", null), PresentationMode.ULTRA)
        feed.presentationMode = presentation
        enableEdgeToEdge()
        themeLibrary = ThemeLibrary(this)
        profileLibrary = ProfileLibrary(this)
        protector = ScreenProtector(null).apply { set(false) }
        themes = themeLibrary.list()
        theme = themes.firstOrNull { it.name == prefs.getString("theme", "") } ?: originalTheme
        profiles = profileLibrary.list()
        autoProfile = prefs.getBoolean("autoProfile", true)
        protocol = enumValue(prefs.getString("wire", null), Wire.AUTO)
        link = enumValue(prefs.getString("link", null), LinkKind.USB)
        feed.scaling = enumValue(prefs.getString("scaling", null), Scaling.AUTO)
        faceplate.page = FaceplateUi.Page.of(prefs.getString("page", null)) ?: FaceplateUi.Page.FULL
        faceplate.deck = FaceplateUi.Deck.of(prefs.getString("deck", null)) ?: FaceplateUi.Deck.BUTTONS
        faceplate.knobSet = FaceplateUi.KnobSet.of(prefs.getString("knobs", null)) ?: FaceplateUi.KnobSet.MAIN
        if (!autoProfile) updateProfile(profiles.firstOrNull { it.id == prefs.getString("profile", "") })
        stayAwake = prefs.getBoolean("stayAwake", true)
        applyScreenFlag()
        transport = MidiTransport(this, this, prefs.getString("usbDevice", null), performanceMonitor)
        feed.transport = transport
        transport.setPresentationMode(presentation)
        transport.use(link)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("fm1.demo", false)) changeDemo(true)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val active = theme ?: originalTheme
                val chip = when {
                    demo -> PanelChip.DEMO
                    feed.hasImage -> PanelChip.LINKED
                    connected -> PanelChip.IN_USE
                    else -> PanelChip.SEARCHING
                }
                val safeArea = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                Box(Modifier.fillMaxSize()) {
                    val inset = if (faceplate.displayAlone) Modifier else safeArea
                    val panelSemantics = if (faceplate.sheetOpen) Modifier.semantics { hideFromAccessibility() } else Modifier
                    FaceplateView(feed, faceplate, active, chip, protector, modifier = inset.then(panelSemantics))
                    Settings(this@MainActivity, active, faceplate.sheetOpen) {
                        faceplate.sheetOpen = false
                        clearMessage()
                    }
                    if (!faceplate.displayAlone && !faceplate.sheetOpen) {
                        Box(Modifier.align(Alignment.TopEnd).then(safeArea).padding(end = 4.dp)) {
                            androidx.compose.foundation.layout.Row {
                                QuickCapture(this@MainActivity, active)
                                SettingsGear(active) { faceplate.sheetOpen = true }
                            }
                        }
                    }
                }
                androidx.compose.runtime.LaunchedEffect(faceplate.displayAlone) { bars(faceplate.displayAlone) }
                androidx.activity.compose.BackHandler(faceplate.displayAlone && !faceplate.sheetOpen) {
                    faceplate.displayAlone = false
                }
            }
        }
        main.postDelayed(metrics, 1000)
    }

    private val metrics = object : Runnable {
        private var last = 0
        private var lastAt = SystemClock.elapsedRealtime()
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            uiDiscardedFrames = mailbox.superseded() + feed.skippedOnUi
            transferBytes = mailbox.copiedBytes()
            rendersPerSecond = ((feed.draws - last) * 1000L / (now - lastAt).coerceAtLeast(1)).toInt()
            last = feed.draws
            lastAt = now
            rxDepth = performanceMonitor.ingress
            if ((diagnosticsVisible || analysis != null) && !diagnosticsSamplePending) {
                diagnosticsSamplePending = true
                val power = getSystemService(PowerManager::class.java)
                val mode = presentation
                val sessionAtSample = sessionStarted
                diagnosticsWorker.execute {
                    val stages = performanceMonitor.snapshot()
                    val load = loadSampler.sample()
                    val temp = power?.currentThermalStatus
                    main.post {
                        diagnosticsSamplePending = false
                        if (!dead && presentation == mode && sessionStarted == sessionAtSample) {
                            performanceStages = stages
                            androidLoad = load
                            thermalStatus = temp
                            collectAnalysis(now)
                        }
                    }
                }
            }
            if (!dead) main.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        updateTelemetry()
        applyScreenFlag()
        restoreBluetooth()
        configure()
    }

    override fun onPause() {
        foreground = false
        cancelAnalysis()
        updateTelemetry()
        applyScreenFlag()
        clearPanel()
        feed.releaseAll()
        transport.pause()
        prefs.edit().putString("page", faceplate.page.raw).putString("deck", faceplate.deck.raw)
            .putString("knobs", faceplate.knobSet.raw).apply()
        super.onPause()
    }

    override fun onDestroy() {
        dead = true
        main.removeCallbacks(metrics)
        main.removeCallbacks(showPanel)
        diagnosticsWorker.shutdown()
        displayClock.removeFrameCallback(drawLatest)
        recording?.stop()
        transport.close()
        disk.shutdown()
        super.onDestroy()
    }

    private fun bars(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    fun keepScreen(value: Boolean) {
        stayAwake = value
        prefs.edit().putBoolean("stayAwake", value).apply()
        applyScreenFlag()
    }

    private fun applyScreenFlag() {
        if (stayAwake && foreground && streaming && requestBlocked == null &&
            mirrorStats.state != StreamState.NO_RESPONSE && mirrorStats.state != StreamState.BLOCKED) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun observeDiagnostics(visible: Boolean) { diagnosticsVisible = visible }

    val canAnalyze get() = streaming && connected && !demo && mirrorStats.wire != null
    val queueOccupancy get() = 100.0 * maxOf(
        rxDepth.bytes.toDouble() / MidiIngress.LIMIT_BYTES,
        rxDepth.chunks.toDouble() / MidiIngress.LIMIT_CHUNKS,
    )

    fun startAnalysis() {
        if (!canAnalyze || analysis != null) return
        analysisFinding = null
        analysis = AnalysisRun(SystemClock.elapsedRealtime(), mirrorStats.lostBytes, midiPorts.reconnections)
        analysisRemaining = 30
    }

    fun cancelAnalysis() {
        analysis = null
        analysisRemaining = null
    }

    private fun collectAnalysis(now: Long) {
        val run = analysis ?: return
        val left = (30 - ((now - run.started) / 1000).toInt()).coerceAtLeast(0)
        val latency = performanceStages[Stage.INTERNAL_LATEST]
            ?.takeIf { it.count >= 10 && (mirrorStats.sinceGraphicsMs ?: Long.MAX_VALUE) <= 2_000 }?.p95Ms
        run.samples.add(HealthSample(androidLoad.normalizedCpuPercent, queueOccupancy, latency,
            (thermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE))
        analysisRemaining = left
        if (left > 0) return
        analysisFinding = HealthAnalysis.evaluate(run.samples, dynamicPanel, mirrorStats.lostBytes > run.lost,
            midiPorts.reconnections > run.reconnects, mirrorStats.state == StreamState.NO_RESPONSE,
            mirrorStats.state == StreamState.RECOVERING && mirrorStats.coverage < 240 * 240)
        cancelAnalysis()
    }

    fun selectPresentation(value: PresentationMode) {
        if (presentation == value) return
        cancelAnalysis()
        presentation = value
        feed.presentationMode = value
        performanceMonitor.clearSamples()
        performanceStages = emptyMap()
        transport.setPresentationMode(value)
        prefs.edit().putString("presentation", value.name).apply()
    }

    fun selectScaling(value: Scaling) {
        feed.scaling = value
        prefs.edit().putString("scaling", value.name).apply()
    }

    fun selectLink(kind: LinkKind) {
        if (link == kind) return
        setWatching(false)
        link = kind
        deviceList = emptyList()
        prefs.edit().putString("link", kind.name).apply()
        transport.use(kind)
        restoreBluetooth()
    }

    private fun restoreBluetooth() {
        if (link != LinkKind.BLE || !::transport.isInitialized) return
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val id = prefs.getString("bleDevice", null) ?: return
        val name = prefs.getString("bleName", null) ?: return
        transport.restoreBle(Target(id, name, DeviceIdentity.scope(name), "Último dispositivo BLE"))
    }

    fun scanBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) permission.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    fun selectTarget(target: Target) {
        setWatching(false)
        if (link == LinkKind.USB) prefs.edit().putString("usbDevice", target.id).apply()
        else prefs.edit().putString("bleDevice", target.id).putString("bleName", target.name).apply()
        updateProfileForDevice(target.name)
        transport.select(target)
    }

    fun selectProtocol(wire: Wire) {
        if (protocol != wire) {
            cancelAnalysis()
            viewOnly = true
        }
        protocol = wire
        if (watch && !midiPorts.scope.allows(wire)) {
            setWatching(false)
        }
        prefs.edit().putString("wire", wire.name).apply()
        configure()
    }

    fun selectProfile(next: FirmwareProfile?) {
        autoProfile = next == null
        prefs.edit().putBoolean("autoProfile", autoProfile).putString("profile", next?.id).apply()
        updateProfile(next)
        if (next == null && connected) updateProfileForDevice(connectionText.substringBefore(" · "))
        if (next != null && next.wire != Wire.AUTO) selectProtocol(next.wire)
    }

    private fun updateProfile(next: FirmwareProfile?) {
        profile = next
        faceplate.firmwareName = next?.name
        feed.profile(next)
    }

    private fun updateProfileForDevice(name: String) {
        if (!autoProfile) return
        updateProfile(profiles.singleOrNull { candidate -> candidate.deviceNames.any { it.equals(name, true) } })
    }

    val requestBlocked: String?
        get() = when {
            demo -> "Desactiva el modo DEMO para solicitar la pantalla"
            !connected || !midiPorts.rxOpen -> "Conecta un puerto MIDI del FM-1"
            mirrorStats.identity.packageId?.startsWith("ota-", ignoreCase = true) == true -> "El equipo anuncia un cargador"
            midiPorts.hyp1 -> "HYP-1 · LCD por SysEx sin verificar"
            midiPorts.scope == MirrorScope.NONE -> "Identidad sin un protocolo autorizado · escucha pasiva"
            !midiPorts.txAvailable -> "El dispositivo no anuncia un puerto MIDI IN para enviarle la solicitud"
            !midiPorts.txOpen -> "Puerto MIDI IN del dispositivo no disponible. Cierra otros visores MIDI y vuelve a conectar."
            !midiPorts.scope.allows(protocol) -> "Felucca · selecciona Auto o Big Screen V1"
            else -> null
        }

    fun setWatching(value: Boolean) {
        if (value && requestBlocked != null) return
        if (watch != value) sessionStarted++
        watch = value
        streaming = value
        applyScreenFlag()
        if (!value) {
            cancelAnalysis()
            viewOnly = true
        }
        configure()
    }

    val controlBlocked: String?
        get() = when {
            requestBlocked != null -> requestBlocked
            !watch -> "Activa Solicitar duplicación"
            !foreground -> "FM1 SCREEN debe estar en primer plano"
            mirrorStats.wire != Wire.BASE || mirrorStats.protocolVersion < 2 ->
                "Control remoto requiere una sesión de panel fm1-base v2 o posterior"
            mirrorStats.state == StreamState.RECOVERING || mirrorStats.state == StreamState.NO_RESPONSE ->
                "Esperando recuperar el panel"
            else -> null
        }

    val canAuthorizeControl get() = controlBlocked == null

    fun reopenPorts() = transport.reopenPorts()

    fun refreshPicture() {
        if (watch && requestBlocked == null) transport.refreshPicture()
    }

    fun changeViewOnly(value: Boolean) {
        if (!value && !canAuthorizeControl) return
        viewOnly = value
        configure()
    }

    fun changeDemo(value: Boolean) {
        demo = value
        updateTelemetry()
        applyScreenFlag()
        recording?.stop()
        if (value) {
            viewOnly = true
            clearPanel()
            feed.demo()
        } else {
            feed.paint(null)
        }
        configure()
    }

    private fun configure() {
        if (!::transport.isInitialized) return
        val remote = !viewOnly && canAuthorizeControl
        feed.setControls(remote)
        transport.configure(protocol, watch && !demo && foreground, remote)
    }

    override fun frameBuffer(source: FrameBuffer?, timing: FrameTiming?) {
        if (dead) return
        val newCallback = if (source != null && timing != null) mailbox.offer(source, timing) else mailbox.clear()
        if (newCallback) displayClock.postFrameCallback(drawLatest)
    }

    private fun updateTelemetry() {
        if (::transport.isInitialized) transport.setPanelTelemetry(dynamicPanel && foreground && connected && !demo)
    }

    private fun clearPanel() {
        panelUpdates.clear()
        feed.panel(null)
    }

    override fun panel(state: PanelState?) {
        if (dead) return
        if (state == null) {
            panelUpdates.clear()
            main.post { if (!dead) feed.panel(null) }
        } else if (panelUpdates.offer(state)) {
            // Coalesce telemetry separately from the Choreographer LCD path.
            main.postDelayed(showPanel, 33)
        }
    }

    override fun stats(stats: MirrorStats) {
        main.post {
            if (dead) return@post
            val wasAuthorized = canAuthorizeControl
            val identityChanged = mirrorStats.identity != stats.identity
            val stateChanged = mirrorStats.state != stats.state
            mirrorStats = stats
            if (stateChanged) applyScreenFlag()
            if (identityChanged && autoProfile && stats.identity.atlatl) {
                updateProfile(profiles.firstOrNull { it.id == "skiff-atlatl" })
            }
            if (wasAuthorized != canAuthorizeControl) configure()
        }
    }

    override fun ports(status: MidiPorts) {
        main.post {
            if (!dead) {
                midiPorts = status
                applyScreenFlag()
            }
        }
    }

    override fun devices(devices: List<Target>) { main.post { if (!dead) deviceList = devices } }

    override fun connection(details: String, connected: Boolean) {
        main.post {
            if (dead) return@post
            connectionText = details
            if (this.connected != connected) sessionStarted++
            this.connected = connected
            updateTelemetry()
            applyScreenFlag()
            if (connected) updateProfileForDevice(details.substringBefore(" · "))
            if (!connected) {
                if (analysis != null) {
                    analysisFinding = HealthFinding("Revisar la conexión", "El enlace MIDI se desconectó durante la observación.")
                }
                cancelAnalysis()
                viewOnly = true
                clearPanel()
                feed.setControls(false)
                if (autoProfile) updateProfile(null)
                configure()
            }
        }
    }

    override fun controlSuspended(reason: String) {
        main.post {
            if (!dead) {
                changeViewOnly(true)
                message = reason
            }
        }
    }

    override fun log(line: String) = midiLog(MidiCategory.SESSION, line)

    override fun midiLog(category: MidiCategory, line: String) {
        main.post {
            if (dead) return@post
            val entry = "${Instant.now()}  [${category.title}] $line"
            logs = (logs + MidiLogEntry(category, entry)).takeLast(240)
            if (!disk.isShutdown) disk.execute {
                val file = File(filesDir, "diagnostico-previo.txt")
                if (file.length() > 1024 * 1024) {
                    val rotated = File(filesDir, "diagnostico-anterior.txt")
                    rotated.delete()
                    if (!file.renameTo(rotated)) file.writeText("")
                }
                file.appendText("$entry\n")
            }
        }
    }

    private fun logSnapshot(): (OutputStream) -> Unit {
        val summary = buildString {
            appendLine("FM1 SCREEN v1.0 · comp. %03d".format(BuildConfig.COMPILATION))
            appendLine("Captura UTC: ${Instant.now()}")
            appendLine("Transporte: ${link.title} | Conexión: $connectionText | Puertos: $midiPorts")
            appendLine("Perfil: ${profile?.name ?: "desconocido"} | Automático: $autoProfile | DEMO: $demo")
            appendLine("Bloqueo de solicitud: ${requestBlocked ?: "ninguno"} | Control: ${controlBlocked ?: "elegible"}")
            appendLine("Diagnóstico: $mirrorStats | Dibujados/s: $rendersPerSecond | Presentaciones omitidas: $uiDiscardedFrames")
            appendLine("Rendimiento: ${presentation.title} | Cola: $rxDepth | Copias RGB565 parciales: $transferBytes B")
            appendLine("Proceso Android: $androidLoad | Térmico: ${thermalStatus ?: "sin muestra"}")
            analysisFinding?.let { appendLine("Análisis: ${it.action} · ${it.reason}") }
            appendLine("Latencias internas en ms, desde callback MIDI hasta registrar el dibujo. Sin medida física extremo a extremo.")
            performanceStages.forEach { (stage, stats) -> appendLine("${stage.title}: $stats") }
            appendLine()
        }
        val current = logs.joinToString("\n") { it.line }
        return { stream ->
            stream.write(summary.toByteArray())
            val files = listOf("diagnostico-anterior.txt", "diagnostico-previo.txt")
                .map { File(filesDir, it) }.filter { it.exists() }
            if (files.isEmpty()) stream.write(current.toByteArray())
            else files.forEach { file -> file.inputStream().use { it.copyTo(stream) } }
        }
    }

    fun exportLogs() {
        uriWriter = logSnapshot()
        saveText.launch("FM1-SCREEN-diagnostico.txt")
    }

    fun shareLogs() {
        val snapshot = logSnapshot()
        disk.execute {
            val result = runCatching {
                val folder = File(cacheDir, "diagnostics").apply { mkdirs() }
                val file = File(folder, "FM1-SCREEN-diagnostico.txt")
                file.outputStream().use(snapshot)
                FileProvider.getUriForFile(this, "$packageName.diagnostics", file)
            }
            main.post {
                if (!dead) result.onSuccess { uri ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("Diagnóstico MIDI", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Compartir diagnóstico MIDI"))
                }.onFailure { message = it.localizedMessage ?: "No se pudo compartir el diagnóstico" }
            }
        }
    }

    fun clearMessage() { message = null }

    fun selectTheme(next: Theme) {
        theme = next
        prefs.edit().putString("theme", next.name).apply()
    }

    fun previewTheme(next: Theme) { theme = next }

    private val themeUndo = ArrayDeque<Theme>()
    var canUndoTheme by mutableStateOf(false)
        private set

    fun applyCustomTheme(previous: Theme, value: Theme) {
        if (previous != value) rememberTheme(previous)
        selectTheme(themeLibrary.storeCustomPreset(value))
        themes = themeLibrary.list()
    }

    fun restoreTheme() {
        theme?.takeIf { it != originalTheme }?.let(::rememberTheme)
        selectTheme(originalTheme)
    }

    private fun rememberTheme(previous: Theme) {
        if (themeUndo.size == 12) themeUndo.removeFirst()
        themeUndo.addLast(previous)
        canUndoTheme = true
    }

    fun undoTheme() {
        val target = themeUndo.removeLastOrNull() ?: return
        canUndoTheme = themeUndo.isNotEmpty()
        if (target.name == "CUSTOM") {
            selectTheme(themeLibrary.storeCustomPreset(target))
            themes = themeLibrary.list()
        } else selectTheme(target)
    }

    fun isCustomTheme(name: String) = themeLibrary.isCustom(name)

    fun saveTheme(value: Theme, replace: String? = null) {
        runCatching {
            val saved = themeLibrary.store(value, replace)
            themes = themeLibrary.list()
            selectTheme(saved)
        }.onFailure { message = it.localizedMessage ?: "No se pudo guardar el tema" }
    }

    fun removeTheme(value: Theme) {
        themeLibrary.remove(value)
        themes = themeLibrary.list()
        if (theme?.name == value.name) selectTheme(originalTheme)
    }

    fun importTheme() {
        uriReader = {
            val newTheme = ThemeLibrary.decode(JSONObject(it))
            val preset = themes.firstOrNull { existing -> existing.name.equals(newTheme.name, true) &&
                !isCustomTheme(existing.name) }
            when {
                preset?.name == "CUSTOM" -> applyCustomTheme(theme ?: originalTheme, newTheme.copy(name = "CUSTOM"))
                preset == newTheme -> selectTheme(newTheme)
                preset != null -> saveTheme(newTheme.copy(name = "${newTheme.name.take(28)} importado"))
                else -> saveTheme(newTheme)
            }
        }
        openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    fun exportTheme(value: Theme) = exportJson("FM1-SCREEN-tema.json", ThemeLibrary.encode(value))

    fun importProfile() {
        uriReader = {
            val imported = ProfileJson.decode(JSONObject(it))
            profileLibrary.save(imported)
            profiles = profileLibrary.list()
            selectProfile(imported)
        }
        openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    fun exportProfile() { profile?.let { exportJson("FM1-SCREEN-${it.id}.json", ProfileJson.encode(it)) } }

    private fun exportJson(name: String, json: JSONObject) {
        val content = json.toString(2).toByteArray()
        uriWriter = { it.write(content) }
        saveJson.launch(name)
    }

    internal fun png(kind: CaptureKind, share: Boolean = false) {
        if (exportingMedia || !hasImage && kind != CaptureKind.INTERFACE) return
        exportingMedia = true
        val name = "FM1-SCREEN-${if (demo) "DEMO-" else ""}${kind.suffix}-${System.currentTimeMillis()}.png"
        if (kind != CaptureKind.INTERFACE) {
            val bitmap = feed.bitmap
            val scale = if (kind == CaptureKind.ENLARGED_LCD) 4 else 1
            queueMedia(name, "image/png", share) { bitmap.writePng(it, scale) }
            return
        }
        val decor = window.decorView
        if (decor.width <= 0 || decor.height <= 0) {
            exportingMedia = false
            return
        }
        val result = runCatching {
            val ratio = minOf(1.0, kotlin.math.sqrt(8_000_000.0 / (decor.width.toLong() * decor.height)))
            val snapshot = Bitmap.createBitmap((decor.width * ratio).toInt().coerceAtLeast(1),
                (decor.height * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, snapshot, { status ->
                    if (dead || status != PixelCopy.SUCCESS) {
                        snapshot.recycle()
                        exportingMedia = false
                        if (!dead) message = "No se pudo capturar la interfaz"
                    } else queueMedia(name, "image/png", share, cleanup = snapshot::recycle) { snapshot.writePng(it, 1) }
                }, main)
            } catch (ex: Exception) {
                snapshot.recycle()
                throw ex
            }
        }
        result.onFailure { exportingMedia = false; message = it.localizedMessage ?: "No se pudo capturar la interfaz" }
    }

    private fun queueMedia(name: String, mime: String, share: Boolean, cleanup: () -> Unit = {},
                           write: (OutputStream) -> Unit) {
        disk.execute {
            val result = runCatching {
                if (!share) {
                    saveMedia(contentResolver, name, mime, write)
                    null
                } else {
                    val folder = File(cacheDir, "media").apply { mkdirs() }
                    folder.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }
                        ?.forEach { it.delete() }
                    val file = File(folder, name)
                    try {
                        file.outputStream().use(write)
                        FileProvider.getUriForFile(this, "$packageName.diagnostics", file)
                    } catch (ex: Exception) {
                        file.delete()
                        throw ex
                    }
                }
            }
            cleanup()
            main.post {
                exportingMedia = false
                if (dead) return@post
                result.onSuccess { uri ->
                    if (uri == null) message = "Archivo guardado"
                    else startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Compartir archivo"))
                }.onFailure { message = it.localizedMessage ?: "Error al exportar el archivo" }
            }
        }
    }

    fun record() {
        val previous = recording
        if (previous != null) {
            previous.stop()
            return
        }
        if (!hasImage || demo && !BuildConfig.DEBUG || exportingMedia) return
        videoReady = false
        video?.delete()
        video = null
        val output = File(cacheDir, "fm1-screen-${System.currentTimeMillis()}.mp4")
        recordingActive = true
        recording = Recording(output, feed.bitmap) { result ->
            main.post {
                recording = null
                recordingActive = false
                result.onSuccess {
                    video = it
                    videoReady = true
                }.onFailure { message = it.localizedMessage ?: "No se pudo grabar MP4" }
            }
        }
    }

    fun exportVideo(share: Boolean = false) {
        val file = video ?: return
        if (exportingMedia) return
        exportingMedia = true
        queueMedia("FM1-SCREEN-${if (demo) "DEMO-" else ""}${System.currentTimeMillis()}.mp4",
            "video/mp4", share) { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private fun saveTo(uri: Uri?) {
        val writer = uriWriter
        uriWriter = null
        if (uri == null || writer == null) return
        disk.execute {
            val result = runCatching { requireNotNull(contentResolver.openOutputStream(uri, "wt")).use(writer) }
            main.post { message = result.fold({ "Archivo guardado" }, { it.localizedMessage ?: "Error al guardar" }) }
        }
    }

    companion object {
        private inline fun <reified E : Enum<E>> enumValue(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default

        private fun readBounded(input: InputStream): String {
            val buffer = ByteArray(65_537)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read <= 0) break
                count += read
            }
            val bytes = buffer.copyOf(count)
            require(bytes.size <= 65_536) { "El JSON supera 64 KiB" }
            return bytes.decodeToString(throwOnInvalidSequence = true)
        }
    }
}
