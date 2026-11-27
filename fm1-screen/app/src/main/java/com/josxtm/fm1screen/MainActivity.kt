// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.josxtm.fm1screen.capture.Recording
import com.josxtm.fm1screen.data.FirmwareProfile
import com.josxtm.fm1screen.data.ProfileLibrary
import com.josxtm.fm1screen.data.ThemeLibrary
import com.josxtm.fm1screen.protocol.PanelState
import com.josxtm.fm1screen.protocol.Wire
import com.josxtm.fm1screen.transport.LinkKind
import com.josxtm.fm1screen.transport.MidiTransport
import com.josxtm.fm1screen.transport.MirrorStats
import com.josxtm.fm1screen.transport.Target
import com.josxtm.fm1screen.transport.TransportListener
import com.josxtm.fm1screen.ui.Settings
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

class MainActivity : ComponentActivity(), TransportListener {
    private val prefs by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private val main = Handler(Looper.getMainLooper())
    private val disk = Executors.newSingleThreadExecutor()
    private lateinit var transport: MidiTransport
    private lateinit var themeLibrary: ThemeLibrary
    private lateinit var profileLibrary: ProfileLibrary
    private lateinit var protector: ScreenProtector
    private val incomingLock = Any()
    private var pendingFrame: IntArray? = null
    private var framePosted = false
    private var watch = false
    private var foreground = false
    private var dead = false
    private var uriReader: ((String) -> Unit)? = null
    private var uriWriter: ((OutputStream) -> Unit)? = null
    private var recording: Recording? = null
    private var video: File? = null
    private val feed = PanelFeed()
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
    var recognized by mutableStateOf(false)
        private set
    var connectionText by mutableStateOf("Buscando FM-1 por USB-C")
        private set
    var mirrorStats by mutableStateOf(MirrorStats())
        private set
    var rendersPerSecond by mutableStateOf(0)
        private set
    var viewOnly by mutableStateOf(true)
        private set
    var demo by mutableStateOf(false)
        private set
    var streaming by mutableStateOf(false)
        private set
    var stayAwake by mutableStateOf(true)
        private set
    var logs by mutableStateOf(emptyList<String>())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var recordingActive by mutableStateOf(false)
        private set
    var videoReady by mutableStateOf(false)
        private set

    val scaling get() = feed.scaling
    val hasImage get() = feed.hasImage
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
    private val savePng = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png"), ::saveTo)
    private val saveMp4 = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"), ::saveTo)
    private val saveText = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), ::saveTo)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        updateProfile(profiles.firstOrNull { it.id == prefs.getString("profile", "") })
        stayAwake = prefs.getBoolean("stayAwake", true)
        applyScreenFlag()
        transport = MidiTransport(this, this, prefs.getString("usbDevice", null))
        feed.transport = transport
        transport.use(link)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val active = theme ?: originalTheme
                val chip = when {
                    demo -> PanelChip.DEMO
                    feed.hasImage -> PanelChip.LINKED
                    connected -> PanelChip.IN_USE
                    else -> PanelChip.SEARCHING
                }
                val inset = if (faceplate.displayAlone) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                FaceplateView(feed, faceplate, active, chip, protector, modifier = inset,
                    openSheet = { faceplate.sheetOpen = true })
                if (faceplate.sheetOpen) Settings(this)
                androidx.compose.runtime.LaunchedEffect(faceplate.displayAlone) { bars(faceplate.displayAlone) }
                androidx.activity.compose.BackHandler(faceplate.displayAlone) { faceplate.displayAlone = false }
            }
        }
        main.postDelayed(metrics, 1000)
    }

    private val metrics = object : Runnable {
        private var last = 0
        override fun run() {
            rendersPerSecond = feed.draws - last
            last = feed.draws
            if (!dead) main.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        configure()
    }

    override fun onPause() {
        foreground = false
        feed.releaseAll()
        transport.pause()
        prefs.edit().putString("page", faceplate.page.raw).putString("deck", faceplate.deck.raw)
            .putString("knobs", faceplate.knobSet.raw).apply()
        super.onPause()
    }

    override fun onDestroy() {
        dead = true
        main.removeCallbacks(metrics)
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
        if (stayAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun selectScaling(value: Scaling) {
        feed.scaling = value
        prefs.edit().putString("scaling", value.name).apply()
    }

    fun selectLink(kind: LinkKind) {
        link = kind
        deviceList = emptyList()
        prefs.edit().putString("link", kind.name).apply()
        transport.use(kind)
    }

    fun scanBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) permission.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    fun selectTarget(target: Target) {
        if (link == LinkKind.USB) prefs.edit().putString("usbDevice", target.id).apply()
        updateProfileForDevice(target.name)
        transport.select(target)
    }

    fun selectProtocol(wire: Wire) {
        protocol = wire
        prefs.edit().putString("wire", wire.name).apply()
        configure()
    }

    fun selectProfile(next: FirmwareProfile?) {
        autoProfile = next == null
        prefs.edit().putBoolean("autoProfile", autoProfile).putString("profile", next?.id).apply()
        updateProfile(next)
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

    fun setWatching(value: Boolean) {
        watch = value
        streaming = value
        configure()
    }

    fun changeViewOnly(value: Boolean) {
        viewOnly = value
        configure()
    }

    fun changeDemo(value: Boolean) {
        demo = value
        recording?.stop()
        if (value) {
            feed.panel(null)
            feed.demo()
        } else {
            feed.paint(null)
        }
        configure()
    }

    private fun configure() {
        if (!::transport.isInitialized) return
        val remote = connected && recognized && !viewOnly && !demo && mirrorStats.wire == Wire.BASE && mirrorStats.protocolVersion >= 2
        feed.setControls(remote && watch && foreground)
        transport.configure(protocol, watch && !demo && foreground, !viewOnly && !demo && foreground)
    }

    override fun frame(pixels: IntArray?, receivedNanos: Long) {
        val post = synchronized(incomingLock) {
            pendingFrame = pixels
            if (framePosted) false else {
                framePosted = true
                true
            }
        }
        if (!post) return
        main.post {
            val latest = synchronized(incomingLock) {
                framePosted = false
                pendingFrame
            }
            if (!demo && !dead) {
                feed.paint(latest)
                if (latest == null) recording?.stop() else recording?.update(feed.bitmap)
            }
        }
    }

    override fun panel(state: PanelState?) {
        main.post { if (!demo && !dead) feed.panel(state) }
    }

    override fun stats(stats: MirrorStats) {
        main.post {
            if (dead) return@post
            val changed = mirrorStats.wire != stats.wire || mirrorStats.protocolVersion != stats.protocolVersion
            mirrorStats = stats
            if (changed) {
                val remote = connected && recognized && !viewOnly && !demo && watch && foreground &&
                    stats.wire == Wire.BASE && stats.protocolVersion >= 2
                feed.setControls(remote)
            }
        }
    }

    override fun devices(devices: List<Target>) { main.post { if (!dead) deviceList = devices } }

    override fun connection(details: String, connected: Boolean, recognized: Boolean) {
        main.post {
            if (dead) return@post
            connectionText = details
            this.connected = connected
            this.recognized = recognized
            if (connected) updateProfileForDevice(details.substringBefore(" · "))
            if (!connected) feed.setControls(false)
        }
    }

    override fun log(line: String) {
        main.post {
            if (dead) return@post
            val entry = "${Instant.now()}  $line"
            logs = (logs + entry).takeLast(200)
            if (!disk.isShutdown) disk.execute {
                val file = File(filesDir, "diagnostico-previo.txt")
                if (file.length() > 128 * 1024) file.writeText("")
                file.appendText("$entry\n")
            }
        }
    }

    fun exportLogs() {
        val current = logs.joinToString("\n")
        uriWriter = { stream ->
            val previous = File(filesDir, "diagnostico-previo.txt")
            if (previous.exists()) previous.inputStream().use { it.copyTo(stream) }
            else stream.write(current.toByteArray())
        }
        saveText.launch("FM1-SCREEN-diagnostico.txt")
    }

    fun clearMessage() { message = null }

    fun selectTheme(next: Theme) {
        theme = next
        prefs.edit().putString("theme", next.name).apply()
    }

    fun previewTheme(next: Theme) { theme = next }

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
            saveTheme(newTheme)
        }
        openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    fun exportTheme(value: Theme) = exportJson("FM1-SCREEN-tema.json", ThemeLibrary.encode(value))

    fun importProfile() {
        uriReader = {
            val imported = FirmwareProfile.decode(JSONObject(it))
            profileLibrary.save(imported)
            profiles = profileLibrary.list()
            selectProfile(imported)
        }
        openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    fun exportProfile() { profile?.let { exportJson("FM1-SCREEN-${it.id}.json", it.encode()) } }

    private fun exportJson(name: String, json: JSONObject) {
        val content = json.toString(2).toByteArray()
        uriWriter = { it.write(content) }
        saveJson.launch(name)
    }

    fun png() {
        if (!hasImage) return
        val bitmap = feed.bitmap
        uriWriter = { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        savePng.launch("FM1-SCREEN-${if (demo) "DEMO-" else ""}${System.currentTimeMillis()}.png")
    }

    fun record() {
        val previous = recording
        if (previous != null) {
            previous.stop()
            return
        }
        if (!hasImage || demo) return
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

    fun exportVideo() {
        val file = video ?: return
        uriWriter = { out -> file.inputStream().use { it.copyTo(out) } }
        saveMp4.launch("FM1-SCREEN-${System.currentTimeMillis()}.mp4")
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
