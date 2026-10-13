package com.home.tiles

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class BeamRelease(val tag: String, val url: String, val size: Long, val sha256: String, val notes: String)

/** Public releases only; updates never install silently or accept another signing key. */
internal object Updates {
    private const val API = "https://api.github.com/repos/tonisaf/beam-launcher/releases/latest"
    private const val DAY = 24 * 60 * 60 * 1000L
    private const val MAX_APK = 80 * 1024 * 1024L
    var busy by mutableStateOf(false); private set
    var release by mutableStateOf<BeamRelease?>(null); private set
    var error by mutableStateOf(false); private set
    var checked by mutableStateOf(false); private set
    var progress by mutableStateOf(-1); private set
    var ready by mutableStateOf<File?>(null); private set
    var installError by mutableStateOf(false); private set

    private fun prefs(context: Context) = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    fun autoEnabled(context: Context) = prefs(context).getBoolean("auto", true)
    fun setAuto(context: Context, value: Boolean) { prefs(context).edit().putBoolean("auto", value).apply() }
    fun snooze(context: Context) { release?.let { prefs(context).edit().putString("snoozed", it.tag).apply() } }
    fun shouldOffer(context: Context) = release != null && prefs(context).getString("snoozed", null) != release?.tag
    fun version(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    suspend fun check(context: Context, automatic: Boolean = false): Boolean {
        if (busy) return false
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val elapsed = now - prefs.getLong("checkedAt", 0)
        if (automatic && (!autoEnabled(context) || elapsed in 0 until DAY)) return false
        busy = true; error = false; installError = false; progress = -1
        try {
            val result = runInterruptible(Dispatchers.IO) {
                val json = JSONObject(read(API, 1024 * 1024L).toString(Charsets.UTF_8))
                if (json.optBoolean("draft") || json.optBoolean("prerelease") || !newerRelease(json.getString("tag_name"), version(context))) null
                else {
                    val assets = json.getJSONArray("assets")
                    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .single { it.getString("name") == "app-release.apk" && it.getString("state") == "uploaded" }
                    val url = apk.getString("browser_download_url")
                    require(validUpdateUrl(url))
                    val size = apk.getLong("size"); require(size in 1..MAX_APK)
                    val digest = updateDigest(apk.optString("digest")) ?: error("Missing SHA-256")
                    BeamRelease(json.getString("tag_name"), url, size, digest, json.optString("body").take(4000))
                }
            }
            if (result != release) ready = null
            release = result; checked = true
            prefs.edit().putLong("checkedAt", now).apply()
            return true
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = true; prefs.edit().putLong("checkedAt", now - DAY + 60 * 60 * 1000L).apply(); return false }
        finally { busy = false }
    }

    suspend fun download(context: Context) {
        val selected = release ?: return
        if (busy) return
        busy = true; error = false; installError = false; progress = 0; ready = null
        try {
            ready = runInterruptible(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val partial = File(dir, "update.part")
                val target = File(dir, "app-release.apk")
                target.delete()
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    transfer(selected.url, selected.size) { connection ->
                        connection.inputStream.use { input -> partial.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            val deadline = System.nanoTime() + 180_000_000_000L
                            while (true) {
                                require(!Thread.currentThread().isInterrupted && System.nanoTime() < deadline)
                                val count = input.read(buffer); if (count < 0) break
                                total += count; require(total <= selected.size)
                                output.write(buffer, 0, count); digest.update(buffer, 0, count)
                                progress = (total * 100 / selected.size).toInt()
                            }
                            require(total == selected.size)
                        } }
                    }
                    require(digest.digest().joinToString("") { "%02x".format(it) } == selected.sha256)
                    verifyApk(context, partial)
                    require(partial.renameTo(target)); target
                } finally { partial.delete() }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = true }
        finally { busy = false; progress = -1 }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, file: File) {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")
        val current = pm.getPackageInfo(context.packageName, flags)
        require(candidate.packageName == context.packageName)
        val nextCode = if (android.os.Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        val oldCode = if (android.os.Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong()
        require(nextCode > oldCode)
        val signatures = current.signatures?.map { it.toCharsString() }?.toSet() ?: error("No installed signature")
        require(signatures.isNotEmpty() && candidate.signatures?.map { it.toCharsString() }?.toSet() == signatures)
    }

    fun install(context: Context): Boolean { return try {
        val file = ready ?: return false
        verifyApk(context, file)
        val permissionScreen = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        error = false; installError = false
        if (!context.packageManager.canRequestPackageInstalls() && permissionScreen.resolveActivity(context.packageManager) != null) {
            context.startActivity(permissionScreen)
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        true
    } catch (failure: Exception) {
        android.util.Log.w("BeamUpdates", "Cannot open installer", failure)
        installError = true; error = true; false
    } }

    private fun read(url: String, limit: Long): ByteArray = transfer(url, limit) { connection ->
        connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            val deadline = System.nanoTime() + 30_000_000_000L
            while (true) {
                require(!Thread.currentThread().isInterrupted && System.nanoTime() < deadline)
                val count = input.read(buffer); if (count < 0) break
                require(output.size() + count <= limit); output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun <T> transfer(url: String, limit: Long, block: (HttpURLConnection) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Beam-Launcher")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            require(connection.responseCode == 200)
            require(connection.url.protocol == "https")
            require(connection.contentLengthLong <= limit)
            return block(connection)
        } finally { connection.disconnect() }
    }
}
