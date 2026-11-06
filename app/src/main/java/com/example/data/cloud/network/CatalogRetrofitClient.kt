package com.example.data.cloud.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit client configured for fetching Noorix catalog index files with Moshi serialization
 * and ETag conditional GET caching.
 */
class CatalogRetrofitClient(
    private val client: OkHttpClient = defaultHttpClient(),
    private val apiServiceOverride: CatalogApiService? = null
) {
    companion object {
        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val apiService: CatalogApiService by lazy {
        apiServiceOverride ?: Retrofit.Builder()
            // Base URL is required by Retrofit; @Url overrides this per request
            .baseUrl("https://drive.google.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CatalogApiService::class.java)
    }
}
