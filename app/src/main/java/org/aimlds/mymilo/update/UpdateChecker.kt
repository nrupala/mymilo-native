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

    // Retired in v0.14.2: the self-updater needed the
    // install-packages permission, and Android's safety
    // check blocks file-installed apps that carry it.
    // Updates arrive through the Play Store now. This
    // check stays as a quiet no-op so the drawer simply
    // never offers an in-app update.
    suspend fun check(): UpdateInfo? = null

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

    /** Open the system installer (or the allow-installs settings first).
     *  Returns false when the installer could not be opened, so the
     *  caller can say so in plain words instead of crashing. */
    fun install(context: Context, file: File): Boolean {
        return false // retired with check() in v0.14.2
        @Suppress("UNREACHABLE_CODE")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val settings = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settings)
                return true
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
            true
        } catch (e: Exception) {
            false
        }
    }
}
