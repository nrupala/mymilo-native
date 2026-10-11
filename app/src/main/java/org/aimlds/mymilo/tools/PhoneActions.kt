package org.aimlds.mymilo.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Phone actions (v0.12.0) — Milo acting on the phone itself:
 * place a call, send a text, open an app. Parsing is anchored
 * verbs only ("call …", "text … saying …", "open …"), so
 * ordinary chat is never mistaken for a command; anything
 * ambiguous falls back to a normal chat. Resolution (contacts,
 * installed apps) happens on-device; nothing is uploaded.
 * Execution always passes through the chat confirmation card —
 * Milo prepares, the user taps.
 */
object PhoneActions {

    sealed interface Parsed {
        data class Call(val target: String) : Parsed
        data class Text(val target: String, val body: String) : Parsed
        data class Open(val app: String) : Parsed
    }

    data class Candidate(
        val label: String,
        val sub: String,
        val number: String? = null,
        val pkg: String? = null,
    )

    // ── Parsing ─────────────────────────────────────────────

    fun parse(message: String): Parsed? {
        val text = message.trim()
        if (text.endsWith("?")) return null // a question, not a command
        Regex(
            """^send\s+(?:a\s+)?(?:text|message|sms)\s+to\s+(.+?)""" +
                """(?:\s+saying\s+|\s*:\s*|\s+that\s+)(.*)$""",
            RegexOption.IGNORE_CASE,
        ).find(text)?.let {
            return Parsed.Text(
                it.groupValues[1].trim(),
                it.groupValues[2].trim(),
            )
        }
        Regex(
            """^(?:text|sms)\s+(.+?)(?:\s+saying\s+|\s*:\s+)(.*)$""",
            RegexOption.IGNORE_CASE,
        ).find(text)?.let {
            return Parsed.Text(
                it.groupValues[1].trim(),
                it.groupValues[2].trim(),
            )
        }
        Regex(
            """^(?:text|sms)\s+(.+)$""",
            RegexOption.IGNORE_CASE,
        ).find(text)?.let {
            return Parsed.Text(it.groupValues[1].trim().trimEnd('.', ','), "")
        }
        Regex(
            """^(?:call|phone|dial)\s+(.+)$""",
            RegexOption.IGNORE_CASE,
        ).find(text)?.let {
            return Parsed.Call(it.groupValues[1].trim().trimEnd('.'))
        }
        Regex(
            """^(?:open|launch|start)\s+(.+?)(?:\s+app)?$""",
            RegexOption.IGNORE_CASE,
        ).find(text)?.let {
            return Parsed.Open(it.groupValues[1].trim())
        }
        return null
    }

    /** A target that is just a phone number needs no contacts. */
    fun looksLikeNumber(s: String): Boolean {
        val cleaned = s.replace(Regex("""[\s\-().]"""), "")
        return cleaned.matches(Regex("""\+?\d{7,15}"""))
    }

    fun normalizeNumber(s: String): String =
        s.replace(Regex("""[\s\-().]"""), "")

    fun hasPermission(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) ==
            PackageManager.PERMISSION_GRANTED

    // ── Resolution ──────────────────────────────────────────

    fun findContacts(ctx: Context, name: String): List<Candidate> {
        val out = mutableListOf<Candidate>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        try {
            ctx.contentResolver.query(
                uri, projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"), null,
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(projection[0])
                val numIdx = cursor.getColumnIndex(projection[1])
                val seen = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    val display = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numIdx) ?: continue
                    if (!seen.add(normalizeNumber(number))) continue
                    out.add(Candidate(display, number, number = number))
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        val lower = name.lowercase()
        return out.sortedWith(
            compareByDescending<Candidate> {
                when {
                    it.label.equals(name, ignoreCase = true) -> 3
                    it.label.lowercase().startsWith(lower) -> 2
                    else -> 1
                }
            },
        ).take(6)
    }

    fun findApps(ctx: Context, name: String): List<Candidate> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        } catch (e: Exception) {
            return emptyList()
        }
        val lower = name.lowercase()
        val matches = mutableListOf<Triple<Int, String, String>>()
        val seen = mutableSetOf<String>()
        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (!seen.add(pkg)) continue
            val label = info.loadLabel(pm).toString()
            val tier = when {
                label.equals(name, ignoreCase = true) -> 3
                label.lowercase().startsWith(lower) -> 2
                label.lowercase().contains(lower) -> 1
                else -> 0
            }
            if (tier > 0) matches.add(Triple(tier, label, pkg))
        }
        return matches
            .sortedWith(
                compareByDescending<Triple<Int, String, String>> { it.first }
                    .thenBy { it.second.lowercase() },
            )
            .take(6)
            .map { Candidate(it.second, "App", pkg = it.third) }
    }

    // ── Execution intents / sending ─────────────────────────

    fun callIntent(number: String): Intent =
        Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))

    fun dialIntent(number: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))

    fun smsComposerIntent(number: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", body)
        }

    fun launchIntent(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLaunchIntentForPackage(pkg)
}
