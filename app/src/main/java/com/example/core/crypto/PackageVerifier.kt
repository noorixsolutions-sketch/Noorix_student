package com.example.core.crypto

import com.example.core.model.CoursePackage
import com.example.core.model.KGEdge
import com.example.core.model.KGNode
import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import java.util.Base64

sealed class VerificationStatus {
    object Valid : VerificationStatus()
    data class HashMismatch(val expected: String, val actual: String) : VerificationStatus()
    data class SignatureInvalid(val reason: String) : VerificationStatus()
    data class SchemaIncompatible(val expected: String, val actual: String) : VerificationStatus()
    data class IntegrityCheckFailed(val missingReferences: List<String>) : VerificationStatus()
}

/**
 * Cryptographic Course Package Verifier (Ed25519 via Google Tink).
 *
 * SECURITY MODEL: the publisher's Ed25519 PRIVATE key lives only in the offline
 * signing tooling (tools/generate_package_signatures.py, run in CI / publisher
 * environment). This APK embeds the PUBLIC key alone, so a tampered package can
 * never be re-signed on device.
 *
 * Verification chain:
 *   1. schema version gate
 *   2. recompute the deterministic canonical payload (PackageCanonicalizer)
 *   3. SHA-256(canonical) must equal manifest.contentHash
 *   4. Ed25519 signature over the canonical bytes must verify against the
 *      embedded publisher public key
 *   5. graph integrity: every edge endpoint, question target/prerequisite and
 *      resource concept must reference an existing node
 */
object PackageVerifier {

    const val SUPPORTED_SCHEMA_VERSION = "1.0"

    // Noorix curriculum publisher public key (base64).
    // Generated offline: python3 tools/generate_package_signatures.py
    // Seed: "NoorixCurriculumPublisherKey2025" (NOT shipped in the APK).
    const val PUBLISHER_PUBLIC_KEY_BASE64 = "pzvLXgi6p9ugtgcEQhJ0od14BkfjXFJEeCau0rpGmJA="

    val PUBLISHER_PUBLIC_KEY: ByteArray by lazy {
        Base64.getDecoder().decode(PUBLISHER_PUBLIC_KEY_BASE64)
    }

    fun computeSha256(content: String): String = PackageCanonicalizer.sha256Hex(content)

    fun computeCanonicalPayload(pkg: CoursePackage): String = PackageCanonicalizer.canonicalize(pkg)

    fun verifyPackage(pkg: CoursePackage): VerificationStatus {
        // 1. Schema version check
        if (pkg.manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return VerificationStatus.SchemaIncompatible(
                expected = SUPPORTED_SCHEMA_VERSION,
                actual = pkg.manifest.schemaVersion
            )
        }

        // 2. + 3. Hash verification over the deterministic canonical payload
        val canonical = PackageCanonicalizer.canonicalize(pkg)
        val calculatedHash = PackageCanonicalizer.sha256Hex(canonical)
        if (pkg.manifest.contentHash != calculatedHash) {
            return VerificationStatus.HashMismatch(expected = calculatedHash, actual = pkg.manifest.contentHash)
        }

        // 4. Real Ed25519 cryptographic signature verification using Google Tink
        //    Signature is over the CANONICAL BYTES, not the hash string.
        try {
            val verifier = Ed25519Verify(PUBLISHER_PUBLIC_KEY)
            val signatureBytes = Base64.getDecoder().decode(pkg.manifest.signature)
            verifier.verify(signatureBytes, canonical.toByteArray(Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            return VerificationStatus.SignatureInvalid("Corrupted Base64 signature format: ${e.message}")
        } catch (e: GeneralSecurityException) {
            return VerificationStatus.SignatureInvalid("Ed25519 signature verification failed: signature does not match publisher key")
        } catch (e: Exception) {
            return VerificationStatus.SignatureInvalid("Cryptographic verification error: ${e.message}")
        }

        // 5. Graph integrity check (nodes, edges, questions, resources all resolve)
        val nodeIds = pkg.nodes.map { it.id }.toSet()
        val missing = mutableSetOf<String>()
        for (edge: KGEdge in pkg.edges) {
            if (!nodeIds.contains(edge.source)) missing.add(edge.source)
            if (!nodeIds.contains(edge.target)) missing.add(edge.target)
        }
        for (q in pkg.questions) {
            if (!nodeIds.contains(q.targetConceptId)) missing.add(q.targetConceptId)
            q.prerequisiteConceptId?.let { if (it.isNotEmpty() && it != "null" && !nodeIds.contains(it)) missing.add(it) }
            for (misc in q.potentialMisconceptions) {
                if (misc.isNotEmpty() && misc != "null" && !nodeIds.contains(misc)) missing.add(misc)
            }
        }
        for (r in pkg.resources) {
            if (!nodeIds.contains(r.conceptId)) missing.add(r.conceptId)
        }
        if (missing.isNotEmpty()) {
            return VerificationStatus.IntegrityCheckFailed(missing.sorted())
        }

        return VerificationStatus.Valid
    }
}
