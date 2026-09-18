package com.example.domain.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the user's OpenRouter cloud configuration (API key + model id)
 * securely using EncryptedSharedPreferences.
 */
object OpenRouterPrefs {
    private const val FILE = "openrouter_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL_ID = "model_id"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(context: Context): Pair<String, String> {
        val prefs = getPrefs(context)
        return (prefs.getString(KEY_API_KEY, "") ?: "") to
            (prefs.getString(KEY_MODEL_ID, "") ?: "")
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context)
            .edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun saveModelId(context: Context, modelId: String) {
        getPrefs(context)
            .edit()
            .putString(KEY_MODEL_ID, modelId.trim())
            .apply()
    }

    fun clear(context: Context) {
        getPrefs(context)
            .edit()
            .remove(KEY_API_KEY)
            .remove(KEY_MODEL_ID)
            .apply()
    }
}
