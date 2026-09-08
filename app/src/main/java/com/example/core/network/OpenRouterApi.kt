package com.example.core.network

import retrofit2.http.GET
import retrofit2.http.Header

data class OpenRouterModelResponse(
    val data: List<OpenRouterModel>
)

data class OpenRouterModel(
    val id: String,
    val name: String
)

interface OpenRouterApi {
    @GET("api/v1/models")
    suspend fun getModels(
        @Header("Authorization") authHeader: String
    ): OpenRouterModelResponse
}
