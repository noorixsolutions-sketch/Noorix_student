package com.example.domain.sync

import android.util.Log
import com.example.core.model.EventModel
import com.example.core.model.EventType
import com.example.core.model.HLC
import com.example.core.model.LearningEvent
import com.example.data.local.AuditLogEntity
import com.example.data.local.EventDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SyncBatchResult(
    val accepted: List<String>,
    val duplicates: List<String>,
    val rejected: List<String>
)

class OfflineSyncEngine(private val eventDao: EventDao) {

    // Simulates Phase 1: Deterministic offline queue + HLC timestamping + idempotent sync
    suspend fun syncOfflineEvents(events: List<LearningEvent>): SyncBatchResult = withContext(Dispatchers.IO) {
        val accepted = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        val rejected = mutableListOf<String>()

        // Simulate network latency
        delay(1500)

        for (event in events) {
            val existing = eventDao.getEventById(event.eventId)
            if (existing != null) {
                // Idempotent rejection: already exists
                duplicates.add(event.eventId)
            } else {
                // Validate HLC
                val hlc = HLC.parse(event.hlcTimestamp)
                if (hlc == null) {
                    rejected.add(event.eventId)
                    continue
                }

                // Insert into "cloud" (local DB acting as cloud in this mock)
                val entity = AuditLogEntity(
                    eventId = event.eventId,
                    eventType = event.eventType.name,
                    studentId = event.studentId,
                    courseVersionId = event.courseVersionId,
                    hlcTimestamp = event.hlcTimestamp,
                    physicalMs = event.physicalMs,
                    payloadJson = event.payloadJson,
                    provenanceHash = event.provenanceHash
                )
                try {
                    eventDao.insertEvent(entity)
                    accepted.add(event.eventId)
                } catch (e: Exception) {
                    rejected.add(event.eventId)
                }
            }
        }
        
        Log.d("SyncEngine", "Synced batch: ${accepted.size} accepted, ${duplicates.size} dupes, ${rejected.size} rejected.")
        SyncBatchResult(accepted, duplicates, rejected)
    }

    // Hybrid Logical Clock resolution for offline merge conflicts
    fun resolveConflicts(localEvents: List<LearningEvent>, remoteEvents: List<LearningEvent>): List<LearningEvent> {
        val merged = (localEvents + remoteEvents).distinctBy { it.eventId }
        // Sort strictly by HLC implementation rules
        return merged.sortedWith(compareBy({ HLC.parse(it.hlcTimestamp)?.physicalMs ?: 0L }, { HLC.parse(it.hlcTimestamp)?.logicalCounter ?: 0L }))
    }
}
