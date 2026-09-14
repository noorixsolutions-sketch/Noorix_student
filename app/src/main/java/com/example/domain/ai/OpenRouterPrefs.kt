package com.example.domain.ai

import android.content.Context

/**
 * Persists the user's OpenRouter cloud configuration (API key + model id)
 * in SharedPreferences.
 *
 * v1 limitation: the key is stored in plaintext, matching the existing
 * ParentPrefs PIN precedent. A future pass should move it to EncryptedSharedPreferences.
 */
object OpenRouterPrefs {
    private const val FILE = "openrouter_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL_ID = "model_id"

    fun load(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return (prefs.getString(KEY_API_KEY, "") ?: "") to
            (prefs.getString(KEY_MODEL_ID, "") ?: "")
    }

    fun saveApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun saveModelId(context: Context, modelId: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_ID, modelId.trim())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_API_KEY)
            .remove(KEY_MODEL_ID)
            .apply()
    }
}
