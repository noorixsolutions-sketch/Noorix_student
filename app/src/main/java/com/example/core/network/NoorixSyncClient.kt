package com.example.core.network

import com.example.data.local.OfflineSyncQueueEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real sync transport: uploads pending learning events to the Noorix sync
 * endpoint and reports exactly what the server acknowledged.
 *
 * No sync server exists yet: the default base URL (BuildConfig, from
 * `.env.example`) is intentionally unroutable, so uploads fail fast and
 * honestly. Callers must treat failure as "stay queued" — see
 * [com.example.domain.repository.LearningRepository.syncNow].
 */
class NoorixSyncClient(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient()
) {

    data class UploadResult(
        val acceptedEventIds: List<String>,
        val serverHlc: String?
    )

    /**
     * POSTs one batch. Success carries ONLY the server-acknowledged event ids;
     * anything else (transport error, non-200, malformed body) is a failure and
     * nothing may be marked synced.
     */
    fun uploadBatch(
        studentId: String,
        items: List<OfflineSyncQueueEntity>
    ): Result<UploadResult> = runCatching {
        val payload = JSONObject().apply {
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
        }
        val request = Request.Builder()
            .url(baseUrl.trim().trimEnd('/') + "/v1/sync/batch")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("sync server answered HTTP ${response.code}")
            }
            val body = JSONObject(response.body?.string() ?: "{}")
            val acceptedArray = body.optJSONArray("acceptedEventIds")
                ?: throw IOException("sync response missing acceptedEventIds")
            val accepted = (0 until acceptedArray.length()).map { acceptedArray.getString(it) }
            UploadResult(
                acceptedEventIds = accepted,
                serverHlc = body.optString("serverHlc").takeIf { it.isNotEmpty() }
            )
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
