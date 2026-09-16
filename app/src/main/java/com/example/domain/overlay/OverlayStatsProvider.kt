package com.example.domain.overlay

import android.content.Context
import com.example.core.model.EventType
import com.example.core.model.GamificationSnapshot
import com.example.core.model.LearningEvent
import com.example.data.local.AppDatabase
import com.example.domain.analytics.LearningAnalyticsEngine
import com.example.domain.gamification.GamificationEngine
import com.example.domain.mastery.BktEngine
import com.example.domain.projection.ProjectionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared overlay stats — ADDITIVE.
 *
 * One suspend entry point used by both primary surfaces (Bubble service and
 * Tutor Keyboard IME) so the bubble halo, quick HUD and keyboard glyph
 * strip can never disagree with each other or with the main app. All
 * derivation reuses the same pure engines as the Dashboard.
 */
data class OverlayStats(
    val gamification: GamificationSnapshot,
    val accuracyPercent: Int,
    val confusionDetected: Boolean
)

object OverlayStatsProvider {

    suspend fun load(context: Context): OverlayStats = withContext(Dispatchers.IO) {
        runCatching {
            val entities = AppDatabase.getInstance(context).eventDao().getAllEvents()
            val events = entities.mapNotNull { entity ->
                val type = runCatching { EventType.valueOf(entity.eventType) }.getOrNull()
                    ?: return@mapNotNull null
                LearningEvent(
                    eventId = entity.eventId,
                    eventType = type,
                    studentId = entity.studentId,
                    courseVersionId = entity.courseVersionId,
                    hlcTimestamp = entity.hlcTimestamp,
                    physicalMs = entity.physicalMs,
                    payloadJson = entity.payloadJson,
                    provenanceHash = entity.provenanceHash
                )
            }
            val projected = ProjectionEngine.projectStudentState(events, System.currentTimeMillis())
            val mastered = projected.academicStates.values.count { it.effectiveMastery >= 0.85 }
            val gam = GamificationEngine.computeSnapshot(
                events = events,
                dailyTargetMinutes = projected.profileCalibration.dailyTargetMinutes,
                masteredConceptCount = mastered
            )
            val (answered, correct) = LearningAnalyticsEngine.computeAccuracy(events)
            OverlayStats(
                gamification = gam,
                accuracyPercent = if (answered == 0) 0 else (correct * 100 / answered),
                confusionDetected = BktEngine.recentWrongCount(events) >= 3
            )
        }.getOrDefault(
            OverlayStats(GamificationEngine.emptySnapshot(), 0, false)
        )
    }
}
