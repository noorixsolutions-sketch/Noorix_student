package com.example.domain.parent

import android.content.Context

/**
 * Parent-gate storage — ADDITIVE.
 *
 * Holds the parent PIN and the "encouragement waiting" flag that lights the
 * 💛 badge on the bubble (§3 + §11 Goal Architecture). Deliberately simple
 * SharedPreferences: the gate is a household-trust boundary, not a
 * cryptographic boundary (the audit chain and evidence store are separate).
 */
object ParentPrefs {

    private const val FILE = "parent_gate"
    private const val KEY_PIN = "parent_pin"
    private const val KEY_MESSAGE_WAITING = "parent_message_waiting"

    const val DEFAULT_PIN = "1234"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getPin(context: Context): String =
        prefs(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN, pin).apply()
    }

    fun checkPin(context: Context, pin: String): Boolean = getPin(context) == pin

    fun isMessageWaiting(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MESSAGE_WAITING, false)

    fun setMessageWaiting(context: Context, waiting: Boolean) {
        prefs(context).edit().putBoolean(KEY_MESSAGE_WAITING, waiting).apply()
    }
}
