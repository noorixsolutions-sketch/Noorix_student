package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_events")
data class EventEntity(
    @PrimaryKey val eventId: String,
    val eventType: String,
    val studentId: String,
    val courseVersionId: String,
    val hlcTimestamp: String,
    val physicalMs: Long,
    val payloadJson: String,
    val provenanceHash: String
)

@Entity(tableName = "course_packages")
data class CoursePackageEntity(
    @PrimaryKey val packageId: String,
    val courseId: String,
    val version: String,
    val subjectName: String,
    val status: String, // STAGED, ACTIVE, DEPRECATED
    val contentHash: String,
    val manifestJson: String,
    val nodesJson: String,
    val edgesJson: String,
    val questionsJson: String,
    val resourcesJson: String
)

@Entity(tableName = "offline_sync_queue")
data class OfflineSyncQueueEntity(
    @PrimaryKey val eventId: String,
    val eventType: String,
    val hlcTimestamp: String,
    val physicalMs: Long,
    val payloadJson: String,
    val status: String = "PENDING", // PENDING, SYNCED, REJECTED
    val confidence: Double = 1.0,
    val retryCount: Int = 0
)

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey val auditId: String,
    val action: String,
    val timestampMs: Long,
    val details: String,
    val verificationHash: String,
    // Hash of the previous audit entry (GENESIS for the first) — makes the
    // audit log a tamper-evident hash chain (v3 schema).
    val prevHash: String = ""
)

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val active: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val agentId: String,
    val sender: String, // "student" or "agent"
    val content: String,
    val timestampMs: Long,
    val isVerifiedMath: Boolean = false,
    val mathVerificationTrace: String? = null
)

