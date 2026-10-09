package org.aimlds.mymilo.assistant

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-activity trail for the assistant session, surfaced in the
 * app drawer so invocation failures are visible and reportable
 * instead of silent (build-8 lesson: a session that dies quietly
 * teaches nothing). SharedPreferences — safe from any thread,
 * no Room involvement.
 */
object AssistantDiag {
    private const val PREFS = "assistant_diag"
    private const val KEY = "last_event"

    fun record(context: Context, event: String) {
        try {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, "$ts — $event")
                .apply()
        } catch (e: Exception) {
            // Diagnostics must never break the feature.
        }
    }

    fun last(context: Context): String =
        try {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "")
                .orEmpty()
        } catch (e: Exception) {
            ""
        }
}
