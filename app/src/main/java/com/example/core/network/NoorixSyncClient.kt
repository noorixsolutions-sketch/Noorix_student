package com.example.core.network

import com.example.data.local.OfflineSyncQueueEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class SyncEventPayloadDto(
    val eventId: String,
    val eventType: String,
    val hlcTimestamp: String,
    val physicalMs: Long,
    val payloadJson: String,
    val retryCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class SyncBatchRequestDto(
    val studentId: String,
    val events: List<SyncEventPayloadDto>
)

@JsonClass(generateAdapter = true)
data class SyncBatchResponseDto(
    val acceptedEventIds: List<String>? = null,
    val serverHlc: String? = null
)

/**
 * Retrofit service interface for syncing immutable learning event logs from Room
 * to the remote server configured via `NOORIX_SYNC_BASE_URL` in `.env`.
 */
interface NoorixSyncApiService {
    @POST("sync/batch")
    suspend fun uploadLearningEventsBatch(
        @Body request: SyncBatchRequestDto,
        @Header("X-Sync-Signature") signature: String,
        @Header("Authorization") authorization: String? = null
    ): SyncBatchResponseDto
}

/**
 * Real sync transport backed by Retrofit: uploads pending learning events to the
 * Noorix sync endpoint (`BuildConfig.NOORIX_SYNC_BASE_URL`) and reports the exact
 * event IDs acknowledged by the server.
 */
class NoorixSyncClient(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val apiServiceOverride: NoorixSyncApiService? = null
) {

    data class UploadResult(
        val acceptedEventIds: List<String>,
        val serverHlc: String?
    )

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val apiService: NoorixSyncApiService by lazy {
        apiServiceOverride ?: testApiServiceOverride ?: Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NoorixSyncApiService::class.java)
    }

    /**
     * Coroutine-native batch upload via the Retrofit [NoorixSyncApiService].
     * Success carries ONLY the server-acknowledged event IDs; any transport error,
     * non-2xx HTTP status, or missing `acceptedEventIds` fails and leaves events queued.
     */
    suspend fun uploadBatchSuspend(
        studentId: String,
        items: List<OfflineSyncQueueEntity>,
        authToken: String? = null
    ): Result<UploadResult> = runCatching {
        val eventDtos = items.map { item ->
            SyncEventPayloadDto(
                eventId = item.eventId,
                eventType = item.eventType,
                hlcTimestamp = item.hlcTimestamp,
                physicalMs = item.physicalMs,
                payloadJson = item.payloadJson,
                retryCount = item.retryCount
            )
        }
        val requestDto = SyncBatchRequestDto(
            studentId = studentId,
            events = eventDtos
        )

        val canonicalJson = buildCanonicalPayloadString(studentId, items)
        val signature = computeHmacSha256(canonicalJson, studentId)
        val authHeader = authToken?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

        val response = apiService.uploadLearningEventsBatch(
            request = requestDto,
            signature = signature,
            authorization = authHeader
        )

        val accepted = response.acceptedEventIds
            ?: throw IOException("sync response missing acceptedEventIds")

        UploadResult(
            acceptedEventIds = accepted,
            serverHlc = response.serverHlc?.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Synchronous wrapper delegating to [uploadBatchSuspend].
     */
    fun uploadBatch(
        studentId: String,
        items: List<OfflineSyncQueueEntity>,
        authToken: String? = null
    ): Result<UploadResult> = runBlocking {
        uploadBatchSuspend(studentId, items, authToken)
    }

    private fun buildCanonicalPayloadString(
        studentId: String,
        items: List<OfflineSyncQueueEntity>
    ): String {
        return JSONObject().apply {
            put("studentId", studentId)
            put("events", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().apply {
                        put("eventId", item.eventId)
                        put("eventType", item.eventType)
                        put("hlcTimestamp", item.hlcTimestamp)
                        put("physicalMs", item.physicalMs)
                        put("payloadJson", item.payloadJson)
                    })
                }
            })
        }.toString()
    }

    private fun computeHmacSha256(data: String, key: String): String {
        return try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            val secretKey = javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        @Volatile
        var testApiServiceOverride: NoorixSyncApiService? = null

        fun normalizeBaseUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isBlank() || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
                return "https://sync.noorix.example/v1/"
            }
            return if (trimmed.endsWith("/v1")) {
                "$trimmed/"
            } else {
                "$trimmed/v1/"
            }
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
