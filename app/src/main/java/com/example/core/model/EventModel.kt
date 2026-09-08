package com.example.core.model

enum class EventType {
    STUDENT_ONBOARDED,
    COURSE_PACKAGE_INSTALLED,
    DIAGNOSTIC_STARTED,
    DIAGNOSTIC_ITEM_SUBMITTED,
    DIAGNOSTIC_COMPLETED,
    QUESTION_PRESENTED,
    QUESTION_ATTEMPTED,
    SYMBOLIC_VERIFICATION_COMPLETED,
    CONCEPT_ASSESSED,
    MASTERY_UPDATED,
    MISCONCEPTION_DETECTED,
    PLAN_CREATED,
    PLAN_STARTED,
    PLAN_EXECUTED,
    PLAN_DEVIATION_DETECTED,
    PLAN_REVISED,
    MEDIA_GENERATED,
    MEDIA_CONSUMED,
    TRANSFER_TASK_ATTEMPTED,
    TRANSFER_TASK_EVALUATED,
    SYNC_BATCH_MERGED,
    CLOCK_ADVANCED_SIMULATED,
    CRYPTOGRAPHIC_AUDIT_PURGE
}

data class LearningEvent(
    val eventId: String,
    val eventType: EventType,
    val studentId: String,
    val courseVersionId: String,
    val hlcTimestamp: String,
    val physicalMs: Long,
    val payloadJson: String,
    val provenanceHash: String = ""
)

data class AuditLogItem(
    val auditId: String,
    val action: String,
    val timestampMs: Long,
    val details: String,
    val verificationHash: String
)
