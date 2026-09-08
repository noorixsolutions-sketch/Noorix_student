package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AuditLogEntity
import com.example.domain.repository.LearningRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the tamper-evident audit hash chain (v3 schema).
 *
 * The chain is pure math over (action, details, timestampMs, prevHash), so we
 * build self-contained entry chains and feed them to verifyAuditChain — no
 * database state or concurrency involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuditChainTest {

    private fun entry(action: String, details: String, t: Long, prev: String) = AuditLogEntity(
        auditId = "$action-$t",
        action = action,
        timestampMs = t,
        details = details,
        verificationHash = LearningRepository.auditChainHash(action, details, t, prev),
        prevHash = prev
    )

    private val repo: LearningRepository by lazy {
        LearningRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `audit chain verifies when each entry links to the previous hash`() {
        val e1 = entry("LOGIN", "user started session", 1_000L, LearningRepository.AUDIT_GENESIS_HASH)
        val e2 = entry("MASTERY_UPDATED", "factorisation 0.8", 2_000L, e1.verificationHash)
        val e3 = entry("PLAN_CREATED", "plan for factorisation", 3_000L, e2.verificationHash)

        assertTrue("A correctly linked chain must verify", repo.verifyAuditChain(listOf(e1, e2, e3)))
    }

    @Test
    fun `audit chain detects a tampered details field`() {
        val e1 = entry("LOGIN", "user started session", 1_000L, LearningRepository.AUDIT_GENESIS_HASH)
        val e2 = entry("MASTERY_UPDATED", "factorisation 0.8", 2_000L, e1.verificationHash)

        val tampered = e2.copy(details = "factorisation 0.99")
        assertFalse("Editing a stored entry's details must break the chain", repo.verifyAuditChain(listOf(e1, tampered)))
    }

    @Test
    fun `audit chain detects an edited previous-link`() {
        val e1 = entry("LOGIN", "user started session", 1_000L, LearningRepository.AUDIT_GENESIS_HASH)
        val e2 = entry("MASTERY_UPDATED", "factorisation 0.8", 2_000L, e1.verificationHash)

        // Pretend e2 was the first entry — the genesis link no longer matches e1's hash.
        val brokenLink = e2.copy(
            prevHash = LearningRepository.AUDIT_GENESIS_HASH,
            verificationHash = LearningRepository.auditChainHash(
                e2.action, e2.details, e2.timestampMs, LearningRepository.AUDIT_GENESIS_HASH
            )
        )
        assertFalse("Splicing in a genesis-linked entry mid-chain must fail", repo.verifyAuditChain(listOf(e1, brokenLink)))
    }

    @Test
    fun `audit chain hash is deterministic and order-sensitive over fields`() {
        val h1 = LearningRepository.auditChainHash("A", "d", 10L, "prev")
        val h2 = LearningRepository.auditChainHash("A", "d", 10L, "prev")
        val h3 = LearningRepository.auditChainHash("A", "d", 11L, "prev")

        assertEquals("Same inputs must give the same hash", h1, h2)
        assertTrue("Changing the timestamp must change the hash", h1 != h3)
    }
}
