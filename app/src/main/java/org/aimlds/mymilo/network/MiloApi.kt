package org.aimlds.mymilo.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.aimlds.mymilo.data.SettingEntity
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ── DTOs (mirror the MyMilo server API, v0.31.0+) ──────────

data class ChatMessageDto(val role: String, val content: String)

data class ChatRequest(
    val model: String = "auto",
    val messages: List<ChatMessageDto>,
    val session_id: String? = null,
    /** Server v0.41.0+: run this skill on purpose for the turn. */
    val skill: String? = null,
)

data class ChatChoice(val message: ChatMessageDto?)
data class SourceDto(
    val type: String,
    val title: String,
    val url: String? = null,
    val detail: String? = null,
)
data class ChatResponse(
    val choices: List<ChatChoice>?,
    val session_id: String?,
    val active_skill: String?,
    val routed_model: String?,
    /** v0.40.0+: what the turn used (skill, memory, web, market…). */
    val sources: List<SourceDto>?,
)

data class SessionDto(val id: String, val title: String?, val updated_at: Double?)
data class SessionListResponse(val sessions: List<SessionDto>?)
data class SessionDetailResponse(val id: String, val messages: List<ChatMessageDto>?)

data class SyncSessionDto(
    val id: String,
    val title: String?,
    val updated_at: Double?,
    val messages: List<ChatMessageDto>?,
)
data class SyncSessionsResponse(val sessions: List<SyncSessionDto>?, val server_time: Double?)
data class SyncPushRequest(val sessions: List<SyncSessionDto>)
data class SyncPushResponse(val imported: Int?)

data class SkillDto(
    val name: String,
    val description: String?,
    val triggers: List<String>?,
    val content: String?,
    val category: String? = null,
    val blurb: String? = null,
    val example: String? = null,
)
data class SkillBundleResponse(
    val version: String?,
    val hash: String?,
    val skills: List<SkillDto>?,
)

data class ClientConfigResponse(
    val version: String?,
    val sync_interval_seconds: Int?,
    val skills_bundle_version: String?,
    /** Server v0.42.0+: Support button destination ("" hides it). */
    val support_url: String? = null,
)

interface MiloService {
    @POST("/v1/chat/completions")
    suspend fun chat(@Body req: ChatRequest): ChatResponse

    @GET("/v1/sessions")
    suspend fun listSessions(): SessionListResponse

    @GET("/v1/sessions/{id}")
    suspend fun getSession(@retrofit2.http.Path("id") id: String): SessionDetailResponse

    @GET("/v1/sync/sessions")
    suspend fun syncSessions(@Query("since") since: Double): SyncSessionsResponse

    @POST("/v1/sync/push")
    suspend fun syncPush(@Body req: SyncPushRequest): SyncPushResponse

    @GET("/v1/skills/bundle")
    suspend fun skillsBundle(): SkillBundleResponse

    @GET("/v1/client/config")
    suspend fun clientConfig(): ClientConfigResponse
}

/**
 * API client for the MyMilo server. Used only when the phone goes
 * online: heavy models, cloud models, search tools live server-side.
 */
class MiloApiClient(private val context: Context) {

    @Volatile
    private var service: MiloService? = null
    @Volatile
    private var builtFor: String = ""

    private suspend fun settings() = (context.applicationContext as org.aimlds.mymilo.MiloApp).db.settings()

    suspend fun serverUrl(): String {
        val saved = settings().get("server_url")
        if (saved == null) return "https://mymilo-api.aimlds.org"
        // v0.3.0: migrate the old browser host (behind the Access
        // sign-in wall) to the API host — build-1 installs saved the
        // old address and would keep failing forever otherwise.
        if (saved.trimEnd('/') == "https://mymilo.aimlds.org") {
            settings().put(
                SettingEntity("server_url", "https://mymilo-api.aimlds.org")
            )
            service = null
            return "https://mymilo-api.aimlds.org"
        }
        return saved
    }

    suspend fun token(): String = settings().get("device_token") ?: ""

    suspend fun setServerAndToken(url: String, token: String) {
        val s = settings()
        s.put(SettingEntity("server_url", url))
        s.put(SettingEntity("device_token", token))
        service = null // rebuild with new base URL
    }

    suspend fun clearToken() {
        settings().put(SettingEntity("device_token", ""))
    }

    suspend fun service(): MiloService {
        val base = serverUrl().trimEnd('/') + "/"
        service?.let { if (builtFor == base) return it }
        val tok = token()
        val auth = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", "Bearer $tok")
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(req)
        }
        val http = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(base)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val svc = retrofit.create(MiloService::class.java)
        service = svc
        builtFor = base
        return svc
    }
}
