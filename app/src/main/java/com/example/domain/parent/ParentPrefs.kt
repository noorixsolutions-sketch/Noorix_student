package com.example.domain.parent

import android.content.Context
import java.security.MessageDigest

/**
 * Parent-gate storage — ADDITIVE.
 *
 * Holds the parent PIN (hashed) and the "encouragement waiting" flag that lights the
 * 💛 badge on the bubble (§3 + §11 Goal Architecture). Deliberately simple
 * SharedPreferences: the gate is a household-trust boundary, not a
 * cryptographic boundary (the audit chain and evidence store are separate).
 */
object ParentPrefs {

    private const val FILE = "parent_gate"
    private const val KEY_PIN_HASH = "parent_pin_hash"
    private const val KEY_MESSAGE_WAITING = "parent_message_waiting"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun hasPinSet(context: Context): Boolean =
        prefs(context).contains(KEY_PIN_HASH)

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
    }

    fun checkPin(context: Context, pin: String): Boolean {
        val storedHash = prefs(context).getString(KEY_PIN_HASH, null)
        return storedHash != null && storedHash == hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isMessageWaiting(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MESSAGE_WAITING, false)

    fun setMessageWaiting(context: Context, waiting: Boolean) {
        prefs(context).edit().putBoolean(KEY_MESSAGE_WAITING, waiting).apply()
    }
}
