package org.aimlds.mymilo.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A direct call to a vault source (OpenRouter, OpenCode Zen, or
 * any OpenAI-compatible endpoint the user named) — the phone
 * talks to that provider with the user's own key, straight from
 * the vault. Nothing routes through Aetheris on this path, and
 * the chat is labeled with where it really went.
 */
object DirectChat {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * One chat completion. Returns the reply text.
     * Throws with a plain message on any failure.
     */
    suspend fun chat(
        baseUrl: String,
        token: String,
        model: String,
        messages: List<ChatMessageDto>,
    ): String = withContext(Dispatchers.IO) {
        val msgs = JSONArray()
        for (m in messages) {
            msgs.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", msgs)
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        val text = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            val hint = try {
                JSONObject(text).optJSONObject("error")
                    ?.optString("message") ?: ""
            } catch (e: Exception) {
                ""
            }
            throw Exception(
                "HTTP ${resp.code}" + if (hint.isNotBlank()) ": $hint" else ""
            )
        }
        val json = JSONObject(text)
        val msg = json.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")
        val content = msg?.optString("content")?.trim().orEmpty()
        if (content.isNotBlank()) return@withContext content
        // Reasoning models may answer in reasoning_content when
        // content is empty (his standing rule: read both fields).
        val reasoning = msg?.optString("reasoning_content")?.trim().orEmpty()
        if (reasoning.isNotBlank()) return@withContext reasoning
        throw Exception("The source replied with an empty answer.")
    }
}
