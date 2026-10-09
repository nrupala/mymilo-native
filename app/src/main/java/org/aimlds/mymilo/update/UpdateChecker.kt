package org.aimlds.mymilo.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    /** GitHub release number (build-N). */
    val releaseNumber: Int,
    val apkUrl: String,
    val releaseUrl: String,
    val notes: String,
)

/**
 * Auto-update for the sideloaded app: checks the public GitHub
 * releases of nrupala/mymilo-native for a newer build, downloads
 * the APK, and hands it to the system installer. Android requires
 * the user's tap in the installer — that's the platform rule for
 * non-Play apps; everything before that tap is automatic.
 */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/nrupala/mymilo-native/releases/latest"

    /** versionCode is 500 + release number (see app/build.gradle.kts). */
    fun currentReleaseNumber(): Int =
        org.aimlds.mymilo.BuildConfig.BUILD_NUMBER - 500

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url(LATEST_URL)
            .header("User-Agent", "MyMilo-App")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val tag = json.optString("tag_name") // build-N
            val n = tag.removePrefix("build-").toIntOrNull()
                ?: return@withContext null
            if (n <= currentReleaseNumber()) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == "app-release.apk") {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            UpdateInfo(
                releaseNumber = n,
                apkUrl = apkUrl ?: return@withContext null,
                releaseUrl = json.optString("html_url"),
                notes = json.optString("body").take(500),
            )
        }
    }

    suspend fun download(context: Context, info: UpdateInfo): File? =
        withContext(Dispatchers.IO) {
            // Persistent app storage (filesDir), NOT cacheDir: a
            // downloaded update must survive until the user chooses
            // to install it (build-10 fix — updates used to strand
            // in the cache with no way back to them).
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val out = File(dir, "mymilo-build${info.releaseNumber}.apk")
            val client = OkHttpClient()
            val req = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", "MyMilo-App")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    out.writeBytes(bytes)
                    out
                }
            } catch (e: Exception) {
                null
            }
        }

    /** The downloaded APK for a build, if it is still on disk. */
    fun downloadedFile(context: Context, releaseNumber: Int): File? {
        val f = File(
            File(context.filesDir, "updates"),
            "mymilo-build$releaseNumber.apk",
        )
        return if (f.exists() && f.length() > 1_000_000) f else null
    }

    /** Open the system installer (or the allow-installs settings first). */
    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return
        }
        val uri = FileProvider.getUriForFile(
            context, "org.aimlds.mymilo.files", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
