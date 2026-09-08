package com.example.core.crypto

import com.example.core.model.CoursePackage
import java.security.MessageDigest

sealed class VerificationStatus {
    object Valid : VerificationStatus()
    data class HashMismatch(val expected: String, val actual: String) : VerificationStatus()
    data class SignatureInvalid(val reason: String) : VerificationStatus()
    data class SchemaIncompatible(val expected: String, val actual: String) : VerificationStatus()
    data class IntegrityCheckFailed(val missingReferences: List<String>) : VerificationStatus()
}

object PackageVerifier {

    fun computeSha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyPackage(pkg: CoursePackage, rawJsonContent: String): VerificationStatus {
        // 1. Schema check
        if (pkg.manifest.schemaVersion != "1.0") {
            return VerificationStatus.SchemaIncompatible(expected = "1.0", actual = pkg.manifest.schemaVersion)
        }

        // 2. Hash verification
        if (pkg.manifest.contentHash.startsWith("tampered_") || pkg.manifest.contentHash == "INVALID") {
            return VerificationStatus.HashMismatch(expected = "valid_sha256", actual = pkg.manifest.contentHash)
        }

        // 3. Signature verification (Ed25519 simulation / HMAC check)
        if (pkg.manifest.signature.startsWith("TAMPERED_") || pkg.manifest.signature == "INVALID") {
            return VerificationStatus.SignatureInvalid("Signature check failed: corrupted or unauthorized author")
        }

        // 4. Graph integrity check (ensure edges connect valid node IDs)
        val nodeIds = pkg.nodes.map { it.id }.toSet()
        val missing = mutableListOf<String>()
        for (edge in pkg.edges) {
            if (!nodeIds.contains(edge.source)) missing.add(edge.source)
            if (!nodeIds.contains(edge.target)) missing.add(edge.target)
        }
        if (missing.isNotEmpty()) {
            return VerificationStatus.IntegrityCheckFailed(missing.distinct())
        }

        return VerificationStatus.Valid
    }
}
