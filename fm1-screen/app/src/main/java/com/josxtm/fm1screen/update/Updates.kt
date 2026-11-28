// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.josxtm.fm1screen.BuildConfig
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class Updates(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val folder = File(app.cacheDir, "updates")
    private var cancel: AtomicBoolean? = null
    private var downloaded: File? by mutableStateOf(null)
    val ready get() = downloaded != null
    private var installerOpen = false
    private var closed = false

    var automatic by mutableStateOf(prefs.getBoolean("automatic", true))
        private set
    var lastCheck by mutableStateOf(prefs.getLong("lastCheck", 0L))
        private set
    var release by mutableStateOf<StableRelease?>(null)
        private set
    var dismissed by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    var received by mutableStateOf(0L)
        private set
    var status by mutableStateOf("Sin comprobar")
        private set

    init {
        worker.execute {
            val now = System.currentTimeMillis()
            folder.listFiles()?.filter { now - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        }
    }

    fun changeAutomatic(value: Boolean) {
        automatic = value
        prefs.edit().putBoolean("automatic", value).apply()
        if (value) check(automaticRequest = true)
    }

    fun browserUnavailable() {
        status = "Android no pudo abrir el historial"
    }

    fun dismiss() {
        dismissed = true
        release?.let { prefs.edit().putInt("dismissedBuild", it.code).apply() }
    }

    fun check(automaticRequest: Boolean = false) {
        if (busy || downloaded != null) return
        val now = System.currentTimeMillis()
        if (automaticRequest && (!automatic || now - lastCheck in 0 until 6 * 60 * 60 * 1000L)) return
        lastCheck = now
        prefs.edit().putLong("lastCheck", now).apply()
        busy = true
        status = "Buscando actualizaciones…"
        worker.execute {
            val result = runCatching {
                StableChannel.find(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT, installedCertificate())
            }
            post {
                busy = false
                result.onSuccess { candidate ->
                    release = candidate
                    dismissed = candidate?.code == prefs.getInt("dismissedBuild", 0)
                    status = if (candidate == null) "Sin actualizaciones compatibles" else "Actualización disponible"
                }.onFailure { status = describe(it, "No se pudo comprobar la actualización") }
            }
        }
    }

    fun download(installWhenReady: () -> Boolean, onReady: (Intent) -> Unit) {
        val candidate = release ?: return
        download(candidate, { target, receive ->
            val connection = StableChannel.connect(target.apkUrl)
            try {
                connection.inputStream.use(receive)
            } finally {
                connection.disconnect()
            }
        }, installWhenReady, onReady)
    }

    internal fun download(
        candidate: StableRelease,
        open: (StableRelease, (InputStream) -> Unit) -> Unit,
        installWhenReady: () -> Boolean,
        onReady: (Intent) -> Unit,
    ) {
        if (busy || installerOpen) return
        if (downloaded?.isFile == true) {
            if (installWhenReady()) openInstaller(onReady)
            else status = "APK verificada. Detén la duplicación para instalarla"
            return
        }
        val cancelled = AtomicBoolean(false)
        cancel = cancelled
        received = 0L
        busy = true
        downloading = true
        status = "Descargando APK…"
        worker.execute {
            var file: File? = null
            val result = runCatching {
                if (!folder.isDirectory && !folder.mkdirs()) throw IOException("No se pudo crear la carpeta temporal")
                val target = File.createTempFile("fm1-", ".apk", folder)
                file = target
                open(candidate) { input ->
                    receiveApk(input, target, candidate, cancelled::get) { bytes ->
                        post { received = bytes }
                    }
                }
                verifyPackage(target, candidate)
                target
            }
            if (result.isFailure || cancelled.get()) file?.delete()
            handler.post {
                if (closed) {
                    file?.delete()
                    return@post
                }
                busy = false
                downloading = false
                cancel = null
                if (cancelled.get()) {
                    file?.delete()
                    status = "Descarga cancelada"
                } else {
                    result.onSuccess {
                        downloaded = it
                        if (installWhenReady()) openInstaller(onReady)
                        else status = "APK verificada. Instálala cuando termines la sesión"
                    }.onFailure { status = describe(it, "No se pudo verificar o descargar la APK") }
                }
            }
        }
    }

    fun cancelDownload() {
        cancel?.set(true)
        status = "Cancelando descarga…"
    }

    private fun openInstaller(launch: (Intent) -> Unit) {
        val apk = downloaded ?: return
        try {
            if (!app.packageManager.canRequestPackageInstalls()) {
                status = "Autoriza este origen en Android y vuelve a pulsar Descargar e instalar"
                launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")))
                return
            }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
            installerOpen = true
            status = "Confirma la actualización en el instalador de Android"
            launch(Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true))
        } catch (_: RuntimeException) {
            installerOpen = false
            status = "Android no pudo abrir el instalador o sus permisos"
        }
    }

    fun activityResult() {
        if (!installerOpen) {
            if (downloaded != null && app.packageManager.canRequestPackageInstalls()) {
                status = "APK verificada. Pulsa Descargar e instalar para continuar"
            }
            return
        }
        installerOpen = false
        downloaded?.delete()
        downloaded = null
        status = "Instalación cancelada o rechazada por Android. Puedes reintentar"
    }

    fun close() {
        closed = true
        cancel?.set(true)
        if (!installerOpen) downloaded?.delete()
        worker.shutdown()
    }

    internal fun verifyPackage(file: File, candidate: StableRelease) {
        val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("Android no reconoce la APK o su firma")
        if (info.packageName != app.packageName || info.longVersionCode != candidate.code.toLong() ||
            info.longVersionCode <= BuildConfig.VERSION_CODE || info.versionName != candidate.version) {
            throw IOException("Paquete o versión incorrectos")
        }
        if (info.applicationInfo?.minSdkVersion != candidate.minSdk || candidate.minSdk > Build.VERSION.SDK_INT) {
            throw IOException("La APK no es compatible con este Android")
        }
        val certificate = certificateOf(info)
        if (certificate != installedCertificate() || certificate != candidate.certificate) {
            throw IOException("El certificado de la APK no coincide con el instalado")
        }
    }

    private fun installedCertificate(): String =
        certificateOf(app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES))

    private fun certificateOf(info: PackageInfo): String =
        info.signingInfo?.apkContentsSigners?.singleOrNull()?.toByteArray()?.sha256Hex()
            ?: throw IOException("El paquete debe tener un único certificado de firma")

    private fun post(block: () -> Unit) {
        handler.post { if (!closed) block() }
    }

    private fun describe(error: Throwable, fallback: String): String = when (error) {
        is UnknownHostException -> "Sin acceso a GitHub. Comprueba la conexión"
        is SocketTimeoutException -> "Tiempo de espera agotado. Puedes reintentar"
        is CancellationException -> "Descarga cancelada"
        is IOException -> error.message ?: fallback
        else -> fallback
    }
}
