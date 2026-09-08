package com.example.core.crypto

import com.google.crypto.tink.subtle.Ed25519Sign
import java.util.Base64

/**
 * Publisher cryptographic signer for Cambridge O-Level course packages.
 * Uses Ed25519 asymmetric cryptography via Google Tink.
 */
object CoursePackageSigner {

    // Deterministic 32-byte seed for official Noorix Learning Solutions Curriculum Authority
    private val PUBLISHER_SEED = "NoorixCurriculumPublisherKey2025".toByteArray(Charsets.UTF_8)

    private val keyPair = Ed25519Sign.KeyPair.newKeyPairFromSeed(PUBLISHER_SEED)

    val PUBLISHER_PUBLIC_KEY: ByteArray = keyPair.publicKey

    val PUBLISHER_PUBLIC_KEY_BASE64: String by lazy {
        Base64.getEncoder().encodeToString(keyPair.publicKey)
    }

    /**
     * Signs arbitrary binary data with the publisher's Ed25519 private key.
     */
    fun signData(data: ByteArray): String {
        val signer = Ed25519Sign(keyPair.privateKey)
        val signature = signer.sign(data)
        return Base64.getEncoder().encodeToString(signature)
    }

    /**
     * Signs a SHA-256 hex string with Ed25519.
     */
    fun signHash(hashString: String): String {
        return signData(hashString.toByteArray(Charsets.UTF_8))
    }
}
