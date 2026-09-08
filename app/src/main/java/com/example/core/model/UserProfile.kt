package com.example.core.model

/**
 * Locally persisted Google account profile (via Credential Manager OAuth).
 * No Firebase dependency — the profile is stored in Room (user_accounts).
 */
data class UserProfile(
    val userId: String, // Google subject ID
    val displayName: String,
    val email: String,
    val photoUrl: String? = null
) {
    val initials: String
        get() = displayName
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifEmpty { "?" }
}
