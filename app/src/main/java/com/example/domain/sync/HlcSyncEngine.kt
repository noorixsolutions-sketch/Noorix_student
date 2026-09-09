package com.example.domain.sync

import com.example.core.hlc.HlcTimestamp
import com.example.core.hlc.HybridClock
import com.example.core.model.EventType
import com.example.core.model.LearningEvent
import com.example.data.local.EventDao
import com.example.data.local.EventEntity
import com.example.data.local.AppDatabase
import com.example.data.local.OfflineSyncQueueEntity
import com.example.data.local.SyncQueueDao
import androidx.room.withTransaction
import org.json.JSONObject
import java.util.UUID

data class SyncBatchResult(
    val acceptedEvents: List<String>,
    val duplicateEvents: List<String>,
    val mergedConflictedEvents: List<String>,
    val updatedHlc: String,
    val summary: String
)

class HlcSyncEngine(
    private val eventDao: EventDao,
    private val syncQueueDao: SyncQueueDao,
    private val hybridClock: HybridClock,
    private val database: AppDatabase
) {

    /** Mark items complete only after a remote transport has acknowledged them. */
    suspend fun acknowledgeUploadedEvents(eventIds: List<String>) {
        if (eventIds.isNotEmpty()) syncQueueDao.markSynced(eventIds)
    }

    /**
     * Enqueue a newly generated local learning event with an HLC timestamp.
     */
    suspend fun recordLocalEvent(
        eventType: EventType,
        studentId: String,
        courseVersionId: String,
        payloadJson: String,
        simulatedWallTimeMs: Long = System.currentTimeMillis()
    ): LearningEvent {
        val hlc = hybridClock.now(simulatedWallTimeMs)
        val eventId = "evt-" + UUID.randomUUID().toString()

        val event = LearningEvent(
            eventId = eventId,
            eventType = eventType,
            studentId = studentId,
            courseVersionId = courseVersionId,
            hlcTimestamp = hlc.toString(),
            physicalMs = hlc.physicalMs,
            payloadJson = payloadJson,
            provenanceHash = "prov-" + hlc.nodeId
        )

        database.withTransaction {
            eventDao.insertEvent(
                EventEntity(
                    eventId = event.eventId,
                    eventType = event.eventType.name,
                    studentId = event.studentId,
                    courseVersionId = event.courseVersionId,
                    hlcTimestamp = event.hlcTimestamp,
                    physicalMs = event.physicalMs,
                    payloadJson = event.payloadJson,
                    provenanceHash = event.provenanceHash
                )
            )
            syncQueueDao.enqueue(
                OfflineSyncQueueEntity(
                    eventId = event.eventId,
                    eventType = event.eventType.name,
                    hlcTimestamp = event.hlcTimestamp,
                    physicalMs = event.physicalMs,
                    payloadJson = event.payloadJson,
                    status = "PENDING"
                )
            )
        }

        return event
    }

    /**
     * Merge remote incoming events from server/peers.
     * Causal order ensured by HLC.
     * Conflicts resolved using Confidence-Weighted Merge (not blind Last-Write-Wins).
     */
    suspend fun mergeRemoteEvents(
        remoteEvents: List<LearningEvent>,
        currentSimulatedTimeMs: Long = System.currentTimeMillis()
    ): SyncBatchResult {
        val accepted = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        val mergedConflicts = mutableListOf<String>()

        val existingEvents = eventDao.getAllEvents().associateBy { it.eventId }

        for (remote in remoteEvents) {
            // Null-safe parse: a malformed remote HLC string degrades to a
            // physicalMs-derived timestamp instead of crashing the batch.
            val remoteHlc = HlcTimestamp.parseOrNull(remote.hlcTimestamp)
                ?: HlcTimestamp(
                    physicalMs = remote.physicalMs,
                    logicalCounter = 0,
                    nodeId = "remote-fallback"
                )
            hybridClock.update(remoteHlc, currentSimulatedTimeMs)

            if (existingEvents.containsKey(remote.eventId)) {
                duplicates.add(remote.eventId)
                continue
            }

            // Check if there is a semantic conflict (e.g. concurrent MASTERY_UPDATED for same concept)
            val conflictCandidate = findSemanticConflict(remote, existingEvents.values)
            if (conflictCandidate != null) {
                // Confidence-weighted merge:
                // Rather than blind LWW, evaluate confidence of both evidence items
                val resolvedPayload = resolveConfidenceWeighted(remote, conflictCandidate)
                val resolvedEvent = remote.copy(
                    payloadJson = resolvedPayload,
                    provenanceHash = "MERGED[${remote.eventId}+${conflictCandidate.eventId}]"
                )
                eventDao.insertEvent(
                    EventEntity(
                        eventId = resolvedEvent.eventId,
                        eventType = resolvedEvent.eventType.name,
                        studentId = resolvedEvent.studentId,
                        courseVersionId = resolvedEvent.courseVersionId,
                        hlcTimestamp = resolvedEvent.hlcTimestamp,
                        physicalMs = resolvedEvent.physicalMs,
                        payloadJson = resolvedEvent.payloadJson,
                        provenanceHash = resolvedEvent.provenanceHash
                    )
                )
                mergedConflicts.add(remote.eventId)
            } else {
                eventDao.insertEvent(
                    EventEntity(
                        eventId = remote.eventId,
                        eventType = remote.eventType.name,
                        studentId = remote.studentId,
                        courseVersionId = remote.courseVersionId,
                        hlcTimestamp = remote.hlcTimestamp,
                        physicalMs = remote.physicalMs,
                        payloadJson = remote.payloadJson,
                        provenanceHash = remote.provenanceHash
                    )
                )
                accepted.add(remote.eventId)
            }
        }

        val updatedHlc = hybridClock.now(currentSimulatedTimeMs).toString()
        val summary = "HLC Sync Complete. Accepted: ${accepted.size}, Duplicates: ${duplicates.size}, Confidence-Weighted Merges: ${mergedConflicts.size}. Current HLC: $updatedHlc"

        return SyncBatchResult(
            acceptedEvents = accepted,
            duplicateEvents = duplicates,
            mergedConflictedEvents = mergedConflicts,
            updatedHlc = updatedHlc,
            summary = summary
        )
    }

    private fun findSemanticConflict(
        remote: LearningEvent,
        existing: Collection<EventEntity>
    ): EventEntity? {
        if (remote.eventType != EventType.MASTERY_UPDATED && remote.eventType != EventType.CONCEPT_ASSESSED) return null
        try {
            val remoteConcept = JSONObject(remote.payloadJson).optString("conceptId")
            return existing.find {
                (it.eventType == EventType.MASTERY_UPDATED.name || it.eventType == EventType.CONCEPT_ASSESSED.name) &&
                        JSONObject(it.payloadJson).optString("conceptId") == remoteConcept &&
                        kotlin.math.abs(it.physicalMs - remote.physicalMs) < 604_800_000L // within 7 days
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun resolveConfidenceWeighted(remote: LearningEvent, local: EventEntity): String {
        try {
            val rJson = JSONObject(remote.payloadJson)
            val lJson = JSONObject(local.payloadJson)

            val rScore = rJson.optDouble("score", 0.5)
            val lScore = lJson.optDouble("score", 0.5)

            val rConfidence = rJson.optDouble("confidence", 0.7)
            val lConfidence = lJson.optDouble("confidence", 0.7)

            // Weight by confidence
            val totalWeight = rConfidence + lConfidence
            val weightedScore = if (totalWeight > 0.0) ((rScore * rConfidence) + (lScore * lConfidence)) / totalWeight else (rScore + lScore) / 2.0
            val mergedConfidence = maxOf(rConfidence, lConfidence).coerceAtMost(0.98)

            rJson.put("score", String.format("%.3f", weightedScore).toDouble())
            rJson.put("confidence", String.format("%.2f", mergedConfidence).toDouble())
            rJson.put("mergeMethod", "CONFIDENCE_WEIGHTED_MERGE")
            return rJson.toString()
        } catch (_: Exception) {
            return remote.payloadJson
        }
    }
}
