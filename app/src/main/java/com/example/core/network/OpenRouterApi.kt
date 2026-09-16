package com.example.core.network

import retrofit2.http.GET
import retrofit2.http.Header

data class OpenRouterModelResponse(
    val data: List<OpenRouterModel>
)

data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null
)

data class OpenRouterModel(
    val id: String,
    val name: String,
    val pricing: OpenRouterPricing? = null
) {
    /**
     * True for models that are free to call: the `:free` id suffix that
     * OpenRouter reserves for free models, or explicitly zero pricing.
     */
    val isFree: Boolean
        get() {
            if (id.endsWith(":free")) return true
            val p = pricing ?: return false
            return (p.prompt?.toDoubleOrNull() ?: 0.0) == 0.0 &&
                (p.completion?.toDoubleOrNull() ?: 0.0) == 0.0
        }

    val displayName: String get() = name.ifBlank { id }
}

interface OpenRouterApi {
    /**
     * The public catalogue endpoint — no key required, so the free-models
     * picker can load on a fresh install. A key is only attached when the
     * user has entered one (some models are key-gated).
     */
    @GET("api/v1/models")
    suspend fun getModels(
        @Header("Authorization") authHeader: String? = null
    ): OpenRouterModelResponse
}
