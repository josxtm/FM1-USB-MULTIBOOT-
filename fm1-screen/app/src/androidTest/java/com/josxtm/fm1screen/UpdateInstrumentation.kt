// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.util.Log
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.Settings
import com.josxtm.fm1screen.data.ColorRole
import com.josxtm.fm1screen.data.ProfileLibrary
import com.josxtm.fm1screen.data.ThemeLibrary
import com.josxtm.fm1screen.ui.theme.Rgb
import com.josxtm.fm1screen.update.StableRelease
import com.josxtm.fm1screen.update.sha256Hex
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UpdateInstrumentation : Instrumentation() {
    private lateinit var options: Bundle
    // Shell commands do not need an accessibility service; avoid competing with adb uiautomator dump.
    private val shellAutomation by lazy { getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY) }
    private val prefs get() = targetContext.getSharedPreferences("preferences", 0)
    private val updatePrefs get() = targetContext.getSharedPreferences("updates", 0)
    private val values = mapOf("link" to "BLE", "wire" to "AUTO", "presentation" to "BALANCED",
        "scaling" to "AUTO", "page" to "screen", "deck" to "keys", "knobs" to "kn", "profile" to "upgrade-fixture", "theme" to "Tema 028")

    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        options = arguments
        start()
    }

    override fun onStart() {
        super.onStart()
        try {
            when (options.getString("step")) {
                "seed" -> seed()
                "install" -> install()
                "verify" -> verify()
                else -> error("Missing test step")
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", "FM1_UPDATE_PASS") })
        } catch (error: Throwable) {
            Log.e("FM1UpgradeTest", "Instrumentation step failed: ${options.getString("step")}", error)
            sendStatus(0, Bundle().apply { putString("error", error.stackTraceToString()) })
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("error", error.stackTraceToString()) })
        }
    }

    private fun seed() {
        val library = ThemeLibrary(targetContext)
        var theme = library.original.copy(name = "Tema 028")
        check(ColorRole.entries.size == 15)
        ColorRole.entries.forEachIndexed { index, role ->
            theme = role.set(theme, Rgb.hex("#1122%02x".format(index + 10))!!)
        }
        library.store(theme)
        library.storeCustomPreset(theme)
        val profiles = ProfileLibrary(targetContext)
        profiles.save(profiles.list().first().copy(id = "upgrade-fixture", name = "Perfil de prueba"))
        val edit = prefs.edit().putBoolean("autoProfile", false).putBoolean("dynamicPanel", true)
            .putBoolean("stayAwake", false)
        values.forEach { (key, value) -> edit.putString(key, value) }
        check(edit.commit())
        check(updatePrefs.edit().putBoolean("automatic", false).commit())
        val names = listOf("preferences", "profiles", "themes", "updates")
        val snapshot = File(targetContext.filesDir, "update-test-snapshot").apply { mkdirs() }
        for (name in names) {
            val store = targetContext.getSharedPreferences(name, 0)
            // A synchronous write drains the library's outstanding apply() operations.
            check(store.edit().putBoolean("fixtureCommitted", true).commit())
            File(targetContext.applicationInfo.dataDir, "shared_prefs/$name.xml")
                .copyTo(File(snapshot, "$name.xml"), overwrite = true)
        }
        verify()
    }

    private fun verify() {
        val snapshot = File(targetContext.filesDir, "update-test-snapshot")
        check(snapshot.listFiles()?.size == 4)
        for (saved in snapshot.listFiles().orEmpty()) {
            val current = File(targetContext.applicationInfo.dataDir, "shared_prefs/${saved.name}")
            check(saved.readBytes().contentEquals(current.readBytes())) { "Changed data: ${saved.name}" }
        }
        check(ThemeLibrary(targetContext).list().any { it.name == "Tema 028" })
        check(ProfileLibrary(targetContext).list().any { it.id == "upgrade-fixture" })
    }

    private fun install() {
        if (options.getString("requireIcon") == "true") {
            check(targetContext.applicationInfo.loadIcon(targetContext.packageManager) is AdaptiveIconDrawable)
        }
        val app = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val pkg = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val certificate = pkg.signingInfo!!.apkContentsSigners.single().toByteArray().sha256Hex()
        val expected = StableRelease("", pkg.versionName!!, pkg.versionCode + 1, "", 29, "",
            options.getString("sha256")!!, certificate, options.getString("size")!!.toLong(), Instant.EPOCH, listOf("Prueba"))
        val wrong = File(targetContext.cacheDir, "wrong.apk")
        for ((asset, message) in listOf("wrong-id.apk" to "Paquete o versión incorrectos",
            "wrong-signature.apk" to "El certificado de la APK no coincide con el instalado")) {
            shellRead(asset) { input -> wrong.outputStream().use { input.copyTo(it) } }
            val failure = runCatching { app.updates.verifyPackage(wrong, expected) }.exceptionOrNull()
            check(failure is IOException && failure.message == message) { "Wrong rejection: $failure" }
        }
        wrong.delete()

        runOnMainSync {
            app.updates.download(expected, { _, receive -> shellRead("upgrade.apk", receive) }, { false }) {
                error("La descarga no debe interrumpir una sesión activa")
            }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        var ready = false
        while (!ready && System.nanoTime() < deadline) {
            Thread.sleep(100)
            runOnMainSync { ready = app.updates.ready }
        }
        check(ready) { app.updates.status }

        val launched = CountDownLatch(1)
        runOnMainSync {
            app.updates.download(expected, { _, _ -> error("APK should already be verified") }, { true }) { intent ->
                check(intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                launched.countDown()
            }
        }
        check(launched.await(45, TimeUnit.SECONDS)) { app.updates.status }
        ParcelFileDescriptor.AutoCloseInputStream(shellAutomation.executeShellCommand(
            "appops set ${app.packageName} REQUEST_INSTALL_PACKAGES allow",
        )).use { it.readBytes() }
        check(app.packageManager.canRequestPackageInstalls())
        runOnMainSync {
            app.updates.download(expected, { _, _ -> error("APK should already be verified") }, { true }) { intent ->
                check(intent.action == Intent.ACTION_INSTALL_PACKAGE)
                app.startActivity(intent)
            }
        }
        Thread.sleep(120_000)
        error("Android did not replace the running package")
    }

    private fun shellRead(name: String, receive: (java.io.InputStream) -> Unit) {
        ParcelFileDescriptor.AutoCloseInputStream(
            shellAutomation.executeShellCommand("cat /data/local/tmp/fm1-$name"),
        ).use(receive)
    }
}
