package org.aimlds.mymilo

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * The crash trap (v0.14.3). Added after a launch crash
 * reached a real phone with no way to see WHY: the app
 * died, Android showed its "this app has a bug" dialog,
 * and the actual error stayed locked inside the phone.
 *
 * This installs a default uncaught-exception handler as
 * the very first thing the app does. Any fatal error is
 * written (with its full cause chain) to a small file in
 * app storage before the system dialog appears. The
 * next launch checks the file first: if a report is
 * waiting, the app shows it on screen - readable,
 * copyable - instead of starting up blind.
 *
 * A native (C++) crash cannot be caught this way; if
 * the file stays empty while the app still dies, that
 * absence is itself the clue.
 */
object CrashLog {

    private const val FILE_NAME = "crash-log.txt"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(appCtx, "uncaught on thread " + thread.name, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, where: String, error: Throwable) {
        try {
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            val entry = StringBuilder()
                .append("=== MyMilo crash report ===\n")
                .append("when: ").append(Date().toString()).append("\n")
                .append("where: ").append(where).append("\n")
                .append("app: ").append(BuildConfig.VERSION_NAME)
                .append(" (build ").append(BuildConfig.BUILD_NUMBER)
                .append(")\n")
                .append("phone: ").append(android.os.Build.MODEL)
                .append(" / Android ").append(android.os.Build.VERSION.RELEASE)
                .append("\n")
                .append(sw.toString())
                .append("\n")
                .toString()
            File(context.filesDir, FILE_NAME).appendText(entry)
        } catch (e: Throwable) {
            // The crash log must never crash.
        }
    }

    /** The latest report text, or "" when there is none. */
    fun read(context: Context): String {
        return try {
            val f = File(context.filesDir, FILE_NAME)
            if (f.exists()) f.readText().takeLast(12000) else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (e: Throwable) {
            // nothing to clear is fine
        }
    }
}
