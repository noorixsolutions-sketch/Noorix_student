package com.example.core.crypto

import android.content.Context
import android.content.SharedPreferences
import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import com.example.data.local.CurriculumInteractionEventEntity
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Cryptographic event signer and verifier using Google Tink (Ed25519).
 * Ensures every local student learning action recorded in Room is cryptographically signed
 * with a private key and can be independently verified for tamper-resistance.
 */
class TinkEventSigner(
    private val keyPair: Ed25519Sign.KeyPair
) {
    private val signer = Ed25519Sign(keyPair.privateKey)
    private val verifier = Ed25519Verify(keyPair.publicKey)

    val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.publicKey)
    val privateKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.privateKey)

    /**
     * Signs the given canonical payload bytes using Tink Ed25519 and returns the Base64 signature.
     */
    fun sign(canonicalPayload: String): String {
        val payloadBytes = canonicalPayload.toByteArray(Charsets.UTF_8)
        val signatureBytes = signer.sign(payloadBytes)
        return Base64.getEncoder().encodeToString(signatureBytes)
    }

    /**
     * Verifies the signature of the payload using this signer's public key.
     */
    fun verify(canonicalPayload: String, signatureBase64: String): Boolean {
        return verifyWithKey(canonicalPayload, signatureBase64, keyPair.publicKey)
    }

    companion object {
        private const val PREFS_NAME = "tink_student_event_keys"
        private const val KEY_PRIV = "tink_ed25519_private"
        private const val KEY_PUB = "tink_ed25519_public"

        @Volatile
        private var defaultInstance: TinkEventSigner? = null

        /**
         * Obtains or creates a persistent Tink Ed25519 signer for the student/device.
         */
        fun getOrCreate(context: Context): TinkEventSigner {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: run {
                    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val storedPriv = prefs.getString(KEY_PRIV, null)
                    val storedPub = prefs.getString(KEY_PUB, null)

                    val keyPair = if (storedPriv != null && storedPub != null) {
                        try {
                            val privBytes = Base64.getDecoder().decode(storedPriv)
                            Ed25519Sign.KeyPair.newKeyPairFromSeed(privBytes.copyOf(32))
                        } catch (e: Exception) {
                            val fresh = Ed25519Sign.KeyPair.newKeyPair()
                            saveKeyPair(prefs, fresh)
                            fresh
                        }
                    } else {
                        val fresh = Ed25519Sign.KeyPair.newKeyPair()
                        saveKeyPair(prefs, fresh)
                        fresh
                    }

                    TinkEventSigner(keyPair).also { defaultInstance = it }
                }
            }
        }

        fun createEphemeral(): TinkEventSigner {
            return TinkEventSigner(Ed25519Sign.KeyPair.newKeyPair())
        }

        private fun saveKeyPair(prefs: SharedPreferences, keyPair: Ed25519Sign.KeyPair) {
            prefs.edit()
                .putString(KEY_PRIV, Base64.getEncoder().encodeToString(keyPair.privateKey))
                .putString(KEY_PUB, Base64.getEncoder().encodeToString(keyPair.publicKey))
                .apply()
        }

        /**
         * Verifies any canonical payload and Base64 signature against a given Base64 public key
         * using Google Tink's [Ed25519Verify].
         */
        fun verifySignature(
            canonicalPayload: String,
            signatureBase64: String,
            publicKeyBase64: String
        ): Boolean {
            if (signatureBase64.isBlank() || publicKeyBase64.isBlank()) return false
            return try {
                val pubBytes = Base64.getDecoder().decode(publicKeyBase64)
                val sigBytes = Base64.getDecoder().decode(signatureBase64)
                verifyWithKey(canonicalPayload, sigBytes, pubBytes)
            } catch (e: Exception) {
                false
            }
        }

        private fun verifyWithKey(
            canonicalPayload: String,
            signatureBytes: ByteArray,
            publicKeyBytes: ByteArray
        ): Boolean {
            return try {
                val tinkVerifier = Ed25519Verify(publicKeyBytes)
                tinkVerifier.verify(signatureBytes, canonicalPayload.toByteArray(Charsets.UTF_8))
                true
            } catch (e: GeneralSecurityException) {
                false
            } catch (e: Exception) {
                false
            }
        }

        private fun verifyWithKey(
            canonicalPayload: String,
            signatureBase64: String,
            publicKeyBytes: ByteArray
        ): Boolean {
            return try {
                val sigBytes = Base64.getDecoder().decode(signatureBase64)
                verifyWithKey(canonicalPayload, sigBytes, publicKeyBytes)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Builds a deterministic canonical representation of a student learning action
         * for cryptographic signing.
         */
        fun buildCanonicalPayload(
            eventId: String,
            sequenceNumber: Long,
            studentId: String,
            interactionType: String,
            topicId: String,
            observedScore: Double?,
            masteryBefore: Double,
            masteryAfter: Double,
            durationSeconds: Int,
            timestampMs: Long,
            prevChainHash: String,
            chainHash: String
        ): String {
            return buildString {
                append("ACTION:").append(eventId).append('|')
                append("SEQ:").append(sequenceNumber).append('|')
                append("STUDENT:").append(studentId).append('|')
                append("TYPE:").append(interactionType).append('|')
                append("TOPIC:").append(topicId).append('|')
                append("SCORE:").append(observedScore?.let { "%.4f".format(it) } ?: "NONE").append('|')
                append("MBEFORE:").append("%.4f".format(masteryBefore)).append('|')
                append("MAFTER:").append("%.4f".format(masteryAfter)).append('|')
                append("DUR:").append(durationSeconds).append('|')
                append("TS:").append(timestampMs).append('|')
                append("PREV:").append(prevChainHash).append('|')
                append("HASH:").append(chainHash)
            }
        }

        fun buildCanonicalPayload(event: CurriculumInteractionEventEntity): String {
            return buildCanonicalPayload(
                eventId = event.eventId,
                sequenceNumber = event.sequenceNumber,
                studentId = event.studentId,
                interactionType = event.interactionType,
                topicId = event.topicId,
                observedScore = event.observedScore,
                masteryBefore = event.masteryBefore,
                masteryAfter = event.masteryAfter,
                durationSeconds = event.durationSeconds,
                timestampMs = event.timestampMs,
                prevChainHash = event.prevChainHash,
                chainHash = event.chainHash
            )
        }

        /**
         * Verifies a stored [CurriculumInteractionEventEntity]'s cryptographic signature
         * using the public key embedded in the record (or fallback signer public key).
         */
        fun verifyEventRecord(
            event: CurriculumInteractionEventEntity,
            fallbackPublicKeyBase64: String? = null
        ): Boolean {
            if (event.signature.isBlank()) return false
            val pubKey = if (event.signerPublicKey.isNotBlank()) {
                event.signerPublicKey
            } else {
                fallbackPublicKeyBase64 ?: return false
            }
            val canonicalPayload = buildCanonicalPayload(event)
            return verifySignature(canonicalPayload, event.signature, pubKey)
        }
    }
}
