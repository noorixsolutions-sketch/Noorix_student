package com.example.data.curriculum

import android.content.Context
import com.example.core.crypto.TinkEventSigner
import com.example.data.local.AppDatabase
import com.example.data.local.CurriculumDao
import com.example.data.local.CurriculumDatabase
import com.example.data.local.CurriculumInteractionEventEntity
import com.example.data.local.CurriculumTopicEntity
import com.example.data.local.CurriculumTopicMasteryEntity
import com.example.data.local.EventEntity
import com.example.data.local.OfflineSyncQueueEntity
import com.example.data.local.SubjectActivityTrailSummary
import com.example.domain.notebooklm.K12Topic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Standardized types of user interactions with the K-12 curriculum recorded
 * as immutable entries in Room by [EventLogger].
 */
enum class CurriculumInteractionType {
    TOPIC_VIEWED,
    QUIZ_ATTEMPTED,
    PODCAST_EXPLORED,
    VIDEO_WATCHED,
    FORMULA_REVIEWED,
    SOURCE_NOTE_ADDED,
    DIAGNOSTIC_ASSESSED,
    TRANSFER_TASK_COMPLETED,
    MASTERY_CALIBRATED
}

/**
 * Single point in a topic's chronological mastery trajectory derived from the
 * immutable Room interaction log.
 */
data class TopicMasteryTrajectoryPoint(
    val sequenceNumber: Long,
    val eventId: String,
    val topicId: String,
    val topicTitle: String,
    val subjectName: String,
    val interactionType: String,
    val observedScore: Double?,
    val masteryBefore: Double,
    val masteryAfter: Double,
    val masteryDelta: Double,
    val timestampMs: Long,
    val chainHash: String
)

/**
 * Aggregated long-term mastery analysis synthesized from the immutable
 * [CurriculumInteractionEventEntity] trail in Room.
 */
data class LongTermMasteryAnalysis(
    val totalInteractions: Int,
    val totalAssessmentEvents: Int,
    val overallAverageQuizAccuracy: Double,
    val averageMasteryGainPerInteraction: Double,
    val totalStudyDurationSeconds: Int,
    val strongestSubject: String?,
    val focusNeededSubject: String?,
    val isChainIntegrityVerified: Boolean,
    val subjectTrails: List<SubjectActivityTrailSummary>,
    val recentEvents: List<CurriculumInteractionEventEntity>
)

/**
 * Dedicated service that records user interactions with the K-12 curriculum as
 * append-only, immutable entries in Room ([CurriculumInteractionEventEntity]),
 * maintaining a cryptographic SHA-256 hash chain and updating topic mastery scores
 * for long-term mastery analysis and O-Level diagnostic visualization.
 */
class EventLogger(
    private val context: Context,
    private val curriculumDatabase: CurriculumDatabase = CurriculumDatabase.getInstance(context),
    private val appDatabase: AppDatabase? = runCatching { AppDatabase.getInstance(context) }.getOrNull(),
    private val tinkSigner: TinkEventSigner = TinkEventSigner.getOrCreate(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val dao: CurriculumDao = curriculumDatabase.curriculumDao()
    private val writeMutex = Mutex()

    val allEventsFlow: Flow<List<CurriculumInteractionEventEntity>> =
        dao.getAllInteractionEventsFlow()

    val recentEventsFlow: Flow<List<CurriculumInteractionEventEntity>> =
        dao.getRecentInteractionEventsFlow(limit = 35)

    val eventCountFlow: Flow<Int> =
        dao.getInteractionEventCountFlow()

    val subjectActivityTrailsFlow: Flow<List<SubjectActivityTrailSummary>> =
        dao.getSubjectActivityTrailSummariesFlow()

    fun observeTopicTrail(topicId: String): Flow<List<CurriculumInteractionEventEntity>> =
        dao.getInteractionTrailForTopicFlow(topicId)

    /**
     * Records an immutable curriculum interaction in Room and atomically updates
     * the corresponding topic's mastery state in [CurriculumDatabase].
     */
    suspend fun logInteraction(
        topicId: String,
        interactionType: CurriculumInteractionType,
        observedScore: Double? = null,
        explicitMasteryScore: Double? = null,
        studentId: String = DEFAULT_STUDENT_ID,
        durationSeconds: Int = 45,
        metadata: Map<String, Any?> = emptyMap(),
        fallbackTopicTitle: String? = null,
        fallbackSubjectId: String? = null,
        fallbackSubjectName: String? = null,
        fallbackGradeId: String? = null,
        fallbackGradeName: String? = null,
        fallbackBandId: String? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): CurriculumInteractionEventEntity = withContext(ioDispatcher) {
        writeMutex.withLock {
            val topicEntity: CurriculumTopicEntity? = dao.getTopicById(topicId)
            val existingMastery: CurriculumTopicMasteryEntity? = dao.getTopicMastery(topicId)

            val resolvedSubjectId = topicEntity?.subjectId
                ?: existingMastery?.subjectId
                ?: fallbackSubjectId
                ?: inferSubjectIdFromTopicId(topicId)

            val resolvedSubjectName = topicEntity?.subjectName
                ?: existingMastery?.subjectName
                ?: fallbackSubjectName
                ?: inferSubjectNameFromTopicId(topicId)

            val resolvedGradeId = topicEntity?.gradeId
                ?: existingMastery?.gradeId
                ?: fallbackGradeId
                ?: inferGradeIdFromTopicId(topicId)

            val resolvedGradeName = topicEntity?.gradeName
                ?: fallbackGradeName
                ?: inferGradeNameFromGradeId(resolvedGradeId)

            val resolvedBandId = topicEntity?.bandId
                ?: existingMastery?.bandId
                ?: fallbackBandId
                ?: inferBandIdFromGradeId(resolvedGradeId)

            val resolvedTopicTitle = topicEntity?.title
                ?: fallbackTopicTitle
                ?: topicId.substringAfterLast(":").replace("-", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            val masteryBefore = existingMastery?.masteryScore
                ?: topicEntity?.masteryScore
                ?: 0.0

            val previousEvidenceCount = existingMastery?.evidenceCount
                ?: topicEntity?.evidenceCount
                ?: 0

            val clampedObserved = observedScore?.coerceIn(0.0, 1.0)
            val masteryAfter = computeUpdatedMasteryScore(
                masteryBefore = masteryBefore,
                evidenceCount = previousEvidenceCount,
                interactionType = interactionType,
                observedScore = clampedObserved,
                explicitMasteryScore = explicitMasteryScore
            )
            val masteryDelta = roundTo3Decimals(masteryAfter - masteryBefore)
            val newEvidenceCount = previousEvidenceCount + 1
            val newStatus = classifyMasteryStatus(masteryAfter, newEvidenceCount)

            val latestEvent = dao.getLatestInteractionEvent()
            val nextSeq = (latestEvent?.sequenceNumber ?: 0L) + 1L
            val prevHash = latestEvent?.chainHash ?: GENESIS_CHAIN_HASH
            val eventId = "cur-evt-${UUID.randomUUID()}"

            val metadataJson = JSONObject().apply {
                put("interactionType", interactionType.name)
                put("topicId", topicId)
                put("subjectName", resolvedSubjectName)
                put("gradeName", resolvedGradeName)
                metadata.forEach { (k, v) ->
                    if (v != null) put(k, v)
                }
            }.toString()

            val chainHash = computeEventChainHash(
                prevChainHash = prevHash,
                eventId = eventId,
                sequenceNumber = nextSeq,
                studentId = studentId,
                interactionType = interactionType.name,
                topicId = topicId,
                observedScore = clampedObserved,
                masteryAfter = masteryAfter,
                timestampMs = timestampMs
            )

            val canonicalPayload = TinkEventSigner.buildCanonicalPayload(
                eventId = eventId,
                sequenceNumber = nextSeq,
                studentId = studentId,
                interactionType = interactionType.name,
                topicId = topicId,
                observedScore = clampedObserved,
                masteryBefore = masteryBefore,
                masteryAfter = masteryAfter,
                durationSeconds = max(1, durationSeconds),
                timestampMs = timestampMs,
                prevChainHash = prevHash,
                chainHash = chainHash
            )
            val signature = tinkSigner.sign(canonicalPayload)
            val signerPublicKey = tinkSigner.publicKeyBase64

            val immutableEvent = CurriculumInteractionEventEntity(
                eventId = eventId,
                sequenceNumber = nextSeq,
                studentId = studentId,
                interactionType = interactionType.name,
                topicId = topicId,
                topicTitle = resolvedTopicTitle,
                subjectId = resolvedSubjectId,
                subjectName = resolvedSubjectName,
                gradeId = resolvedGradeId,
                gradeName = resolvedGradeName,
                bandId = resolvedBandId,
                observedScore = clampedObserved,
                masteryBefore = masteryBefore,
                masteryAfter = masteryAfter,
                masteryDelta = masteryDelta,
                durationSeconds = max(1, durationSeconds),
                metadataJson = metadataJson,
                timestampMs = timestampMs,
                prevChainHash = prevHash,
                chainHash = chainHash,
                signature = signature,
                signerPublicKey = signerPublicKey
            )

            val isQuizAttempt = clampedObserved != null
            val isCorrectAttempt = (clampedObserved ?: 0.0) >= 0.65

            val updatedMasteryEntity = CurriculumTopicMasteryEntity(
                topicId = topicId,
                subjectId = resolvedSubjectId,
                subjectName = resolvedSubjectName,
                gradeId = resolvedGradeId,
                bandId = resolvedBandId,
                masteryScore = masteryAfter,
                masteryStatus = newStatus,
                evidenceCount = newEvidenceCount,
                correctCount = (existingMastery?.correctCount ?: 0) + if (isQuizAttempt && isCorrectAttempt) 1 else 0,
                attemptCount = (existingMastery?.attemptCount ?: 0) + if (isQuizAttempt) 1 else 0,
                totalStudySeconds = (existingMastery?.totalStudySeconds ?: 0) + max(1, durationSeconds),
                lastInteractionType = interactionType.name,
                lastStudiedAtMs = timestampMs
            )

            dao.recordTopicMasteryUpdate(
                masteryEntity = updatedMasteryEntity,
                immutableEvent = immutableEvent
            )

            // Mirror into AppDatabase learning_events + offline_sync_queue if available
            mirrorToAppDatabase(immutableEvent)

            immutableEvent
        }
    }

    /**
     * Convenience helper when a student selects or views a K-12 curriculum topic.
     */
    suspend fun logTopicViewed(
        topic: K12Topic,
        studentId: String = DEFAULT_STUDENT_ID,
        durationSeconds: Int = 45
    ): CurriculumInteractionEventEntity = logInteraction(
        topicId = topic.topicId,
        interactionType = CurriculumInteractionType.TOPIC_VIEWED,
        studentId = studentId,
        durationSeconds = durationSeconds,
        fallbackTopicTitle = topic.title,
        fallbackSubjectName = topic.subjectName,
        fallbackGradeName = topic.gradeName,
        metadata = mapOf("summary" to topic.summary.take(120))
    )

    /**
     * Convenience helper when a student answers a quiz question on a curriculum topic.
     */
    suspend fun logQuizAttempt(
        topicId: String,
        quizId: String,
        isCorrect: Boolean,
        score: Double = if (isCorrect) 1.0 else 0.25,
        studentId: String = DEFAULT_STUDENT_ID,
        durationSeconds: Int = 60,
        topicTitle: String? = null,
        subjectName: String? = null
    ): CurriculumInteractionEventEntity = logInteraction(
        topicId = topicId,
        interactionType = CurriculumInteractionType.QUIZ_ATTEMPTED,
        observedScore = score,
        studentId = studentId,
        durationSeconds = durationSeconds,
        fallbackTopicTitle = topicTitle,
        fallbackSubjectName = subjectName,
        metadata = mapOf(
            "quizId" to quizId,
            "isCorrect" to isCorrect,
            "score" to score
        )
    )

    /**
     * Convenience helper when a student explores the 2-Host Audio Podcast or asks the hosts a question.
     */
    suspend fun logPodcastExplored(
        topicId: String,
        questionAsked: String? = null,
        studentId: String = DEFAULT_STUDENT_ID,
        durationSeconds: Int = 120
    ): CurriculumInteractionEventEntity = logInteraction(
        topicId = topicId,
        interactionType = CurriculumInteractionType.PODCAST_EXPLORED,
        studentId = studentId,
        durationSeconds = durationSeconds,
        metadata = mapOf("questionAsked" to (questionAsked ?: "Full episode playback"))
    )

    /**
     * Convenience helper when a student watches the 5-scene Video Explainer for a topic.
     */
    suspend fun logVideoExplainerWatched(
        topicId: String,
        sceneCount: Int = 5,
        studentId: String = DEFAULT_STUDENT_ID,
        durationSeconds: Int = 150
    ): CurriculumInteractionEventEntity = logInteraction(
        topicId = topicId,
        interactionType = CurriculumInteractionType.VIDEO_WATCHED,
        studentId = studentId,
        durationSeconds = durationSeconds,
        metadata = mapOf("sceneCount" to sceneCount)
    )

    /**
     * Convenience helper when a student adds a grounded custom source note to NotebookLM.
     */
    suspend fun logSourceNoteAdded(
        topicId: String,
        notePreview: String,
        studentId: String = DEFAULT_STUDENT_ID
    ): CurriculumInteractionEventEntity = logInteraction(
        topicId = topicId,
        interactionType = CurriculumInteractionType.SOURCE_NOTE_ADDED,
        studentId = studentId,
        durationSeconds = 40,
        metadata = mapOf("notePreview" to notePreview.take(100))
    )

    /**
     * Convenience helper when a diagnostic check-in or mastery calibration records a topic score.
     */
    suspend fun logDiagnosticAssessment(
        topicId: String,
        observedScore: Double,
        explicitMasteryScore: Double? = null,
        studentId: String = DEFAULT_STUDENT_ID,
        durationSeconds: Int = 45,
        note: String = "Diagnostic calibration"
    ): CurriculumInteractionEventEntity = logInteraction(
        topicId = topicId,
        interactionType = CurriculumInteractionType.DIAGNOSTIC_ASSESSED,
        observedScore = observedScore,
        explicitMasteryScore = explicitMasteryScore,
        studentId = studentId,
        durationSeconds = durationSeconds,
        metadata = mapOf("note" to note)
    )

    /**
     * Returns the chronological mastery trajectory for a specific topic from the
     * immutable Room interaction log.
     */
    suspend fun getTopicMasteryTrajectory(topicId: String): List<TopicMasteryTrajectoryPoint> =
        withContext(ioDispatcher) {
            dao.getInteractionTrailForTopic(topicId).map { e ->
                TopicMasteryTrajectoryPoint(
                    sequenceNumber = e.sequenceNumber,
                    eventId = e.eventId,
                    topicId = e.topicId,
                    topicTitle = e.topicTitle,
                    subjectName = e.subjectName,
                    interactionType = e.interactionType,
                    observedScore = e.observedScore,
                    masteryBefore = e.masteryBefore,
                    masteryAfter = e.masteryAfter,
                    masteryDelta = e.masteryDelta,
                    timestampMs = e.timestampMs,
                    chainHash = e.chainHash
                )
            }
        }

    /**
     * Verifies that the immutable curriculum interaction trail in Room has an unbroken
     * SHA-256 cryptographic hash chain from [GENESIS_CHAIN_HASH] to the latest event.
     */
    suspend fun verifyTrailIntegrity(): Boolean = withContext(ioDispatcher) {
        val chronological = dao.getAllInteractionEventsChronological()
        if (chronological.isEmpty()) return@withContext true

        var expectedPrev = GENESIS_CHAIN_HASH
        var expectedSeq = 1L

        for (event in chronological) {
            if (event.sequenceNumber != expectedSeq) return@withContext false
            if (event.prevChainHash != expectedPrev) return@withContext false

            val recomputed = computeEventChainHash(
                prevChainHash = event.prevChainHash,
                eventId = event.eventId,
                sequenceNumber = event.sequenceNumber,
                studentId = event.studentId,
                interactionType = event.interactionType,
                topicId = event.topicId,
                observedScore = event.observedScore,
                masteryAfter = event.masteryAfter,
                timestampMs = event.timestampMs
            )
            if (recomputed != event.chainHash) return@withContext false

            // Cryptographically verify the event record's Tink Ed25519 signature
            if (event.signature.isNotBlank()) {
                val isSignatureValid = TinkEventSigner.verifyEventRecord(event, tinkSigner.publicKeyBase64)
                if (!isSignatureValid) return@withContext false
            }

            expectedPrev = event.chainHash
            expectedSeq++
        }
        true
    }

    /**
     * Verifies the cryptographic Tink Ed25519 signature of an individual student action event.
     */
    fun verifyEventSignature(event: CurriculumInteractionEventEntity): Boolean {
        return TinkEventSigner.verifyEventRecord(event, tinkSigner.publicKeyBase64)
    }

    /**
     * Returns the Base64-encoded Ed25519 public key used by this [EventLogger] to sign student actions.
     */
    fun getSignerPublicKey(): String = tinkSigner.publicKeyBase64

    /**
     * Performs long-term mastery analysis across all immutable curriculum interaction
     * entries stored in Room.
     */
    suspend fun analyzeLongTermMastery(): LongTermMasteryAnalysis = withContext(ioDispatcher) {
        val allEvents = dao.getAllInteractionEventsChronological()
        val subjectTrails = dao.getSubjectActivityTrailSummaries()
        val chainVerified = verifyTrailIntegrity()

        val assessmentEvents = allEvents.filter { it.observedScore != null }
        val avgQuizAccuracy = if (assessmentEvents.isNotEmpty()) {
            roundTo3Decimals(assessmentEvents.mapNotNull { it.observedScore }.average())
        } else {
            0.0
        }

        val avgDelta = if (allEvents.isNotEmpty()) {
            roundTo3Decimals(allEvents.map { it.masteryDelta }.average())
        } else {
            0.0
        }

        val totalStudySecs = allEvents.sumOf { it.durationSeconds }
        val strongestSubject = subjectTrails.maxByOrNull { it.peakMasteryReached }?.subjectName
        val focusSubject = subjectTrails.minByOrNull { it.peakMasteryReached }?.subjectName

        LongTermMasteryAnalysis(
            totalInteractions = allEvents.size,
            totalAssessmentEvents = assessmentEvents.size,
            overallAverageQuizAccuracy = avgQuizAccuracy,
            averageMasteryGainPerInteraction = avgDelta,
            totalStudyDurationSeconds = totalStudySecs,
            strongestSubject = strongestSubject,
            focusNeededSubject = focusSubject,
            isChainIntegrityVerified = chainVerified,
            subjectTrails = subjectTrails,
            recentEvents = allEvents.asReversed().take(25)
        )
    }

    private suspend fun mirrorToAppDatabase(event: CurriculumInteractionEventEntity) {
        val db = appDatabase ?: return
        runCatching {
            val mappedEventType = when (event.interactionType) {
                CurriculumInteractionType.DIAGNOSTIC_ASSESSED.name -> "DIAGNOSTIC_ITEM_SUBMITTED"
                CurriculumInteractionType.QUIZ_ATTEMPTED.name,
                CurriculumInteractionType.MASTERY_CALIBRATED.name -> "MASTERY_UPDATED"
                CurriculumInteractionType.PODCAST_EXPLORED.name,
                CurriculumInteractionType.VIDEO_WATCHED.name -> "MEDIA_CONSUMED"
                CurriculumInteractionType.TRANSFER_TASK_COMPLETED.name -> "TRANSFER_TASK_EVALUATED"
                else -> "CONCEPT_ASSESSED"
            }

            val payload = JSONObject().apply {
                put("conceptId", event.topicId)
                put("conceptName", event.topicTitle)
                put("subjectName", event.subjectName)
                put("gradeName", event.gradeName)
                put("score", event.observedScore ?: event.masteryAfter)
                put("masteryBefore", event.masteryBefore)
                put("masteryAfter", event.masteryAfter)
                put("interactionType", event.interactionType)
                put("chainHash", event.chainHash)
            }.toString()

            val hlc = "${event.timestampMs}:${event.sequenceNumber}:curriculum-logger"

            db.eventDao().insertEvent(
                EventEntity(
                    eventId = event.eventId,
                    eventType = mappedEventType,
                    studentId = event.studentId,
                    courseVersionId = "k12-curriculum:2026.1",
                    hlcTimestamp = hlc,
                    physicalMs = event.timestampMs,
                    payloadJson = payload,
                    provenanceHash = event.chainHash.take(32)
                )
            )
            db.syncQueueDao().enqueue(
                OfflineSyncQueueEntity(
                    eventId = event.eventId,
                    eventType = mappedEventType,
                    hlcTimestamp = hlc,
                    physicalMs = event.timestampMs,
                    payloadJson = payload,
                    status = "PENDING",
                    confidence = 0.92
                )
            )
        }
    }

    companion object {
        const val DEFAULT_STUDENT_ID = "student-101"
        const val GENESIS_CHAIN_HASH =
            "GENESIS-CURRICULUM-00000000000000000000000000000000000000000000"

        internal fun computeUpdatedMasteryScore(
            masteryBefore: Double,
            evidenceCount: Int,
            interactionType: CurriculumInteractionType,
            observedScore: Double?,
            explicitMasteryScore: Double?
        ): Double {
            if (explicitMasteryScore != null) {
                return roundTo3Decimals(explicitMasteryScore.coerceIn(0.0, 1.0))
            }

            if (observedScore != null) {
                val clamped = observedScore.coerceIn(0.0, 1.0)
                if (masteryBefore <= 0.0 || evidenceCount == 0) {
                    // First assessment establishes baseline blended with prior
                    val initial = (0.25 * 0.30) + (clamped * 0.70)
                    return roundTo3Decimals(initial.coerceIn(0.05, 0.99))
                }
                val weight = min(0.75, 0.35 + (min(evidenceCount, 6) * 0.05))
                val updated = (masteryBefore * (1.0 - weight)) + (clamped * weight)
                return roundTo3Decimals(updated.coerceIn(0.05, 0.99))
            }

            // Constructive study / engagement boost when exploring topic content
            val studyBoost = when (interactionType) {
                CurriculumInteractionType.TOPIC_VIEWED -> 0.06
                CurriculumInteractionType.FORMULA_REVIEWED -> 0.07
                CurriculumInteractionType.SOURCE_NOTE_ADDED -> 0.08
                CurriculumInteractionType.PODCAST_EXPLORED -> 0.09
                CurriculumInteractionType.VIDEO_WATCHED -> 0.10
                else -> 0.08
            }

            val base = if (masteryBefore <= 0.0) 0.22 else masteryBefore
            val boosted = min(0.88, base + studyBoost * (1.0 - base * 0.6))
            return roundTo3Decimals(boosted.coerceIn(0.05, 0.99))
        }

        internal fun classifyMasteryStatus(score: Double, evidenceCount: Int): String {
            if (score <= 0.0 && evidenceCount == 0) return "UNASSESSED"
            return when {
                score >= 0.80 -> "MASTERED"
                score >= 0.55 -> "DEVELOPING"
                score > 0.0 -> "EMERGING"
                else -> "UNASSESSED"
            }
        }

        internal fun computeEventChainHash(
            prevChainHash: String,
            eventId: String,
            sequenceNumber: Long,
            studentId: String,
            interactionType: String,
            topicId: String,
            observedScore: Double?,
            masteryAfter: Double,
            timestampMs: Long
        ): String {
            val scorePart = observedScore?.let { String.format(Locale.US, "%.3f", it) } ?: "NONE"
            val masteryPart = String.format(Locale.US, "%.3f", masteryAfter)
            val canonical = "$prevChainHash|$eventId|$sequenceNumber|$studentId|$interactionType|$topicId|$scorePart|$masteryPart|$timestampMs"
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun roundTo3Decimals(value: Double): Double =
            (value * 1000.0).roundToInt() / 1000.0

        private fun inferSubjectIdFromTopicId(topicId: String): String = when {
            topicId.contains("4024") || topicId.contains(":math:") -> "subj:9:math-4024"
            topicId.contains("5054") || topicId.contains(":phys:") -> "subj:9:physics-5054"
            topicId.contains("5070") || topicId.contains(":chem:") -> "subj:9:chemistry-5070"
            topicId.contains("5090") || topicId.contains(":bio:") -> "subj:9:biology-5090"
            topicId.contains("2210") || topicId.contains(":cs:") -> "subj:10:cs-2210"
            else -> "subj:general"
        }

        private fun inferSubjectNameFromTopicId(topicId: String): String = when {
            topicId.contains("4024") || topicId.contains(":math:") -> "Mathematics (Cambridge 4024)"
            topicId.contains("5054") || topicId.contains(":phys:") -> "Physics (Cambridge 5054)"
            topicId.contains("5070") || topicId.contains(":chem:") -> "Chemistry (Cambridge 5070)"
            topicId.contains("5090") || topicId.contains(":bio:") -> "Biology (Cambridge 5090)"
            topicId.contains("2210") || topicId.contains(":cs:") -> "Computer Science (Cambridge 2210)"
            else -> "General Curriculum"
        }

        private fun inferGradeIdFromTopicId(topicId: String): String {
            val parts = topicId.split(":")
            if (parts.size >= 2 && (parts[1].all { it.isDigit() } || parts[1] == "k")) {
                return "grade:${parts[1]}"
            }
            return "grade:9"
        }

        private fun inferGradeNameFromGradeId(gradeId: String): String = when (gradeId) {
            "grade:k" -> "Kindergarten"
            "grade:9" -> "Grade 9 (O-Level Year 1)"
            "grade:10" -> "Grade 10 (O-Level Year 2 / Final Exam)"
            else -> "Grade ${gradeId.substringAfter("grade:")}"
        }

        private fun inferBandIdFromGradeId(gradeId: String): String {
            val num = gradeId.substringAfter("grade:").toIntOrNull()
            return when {
                gradeId == "grade:k" || (num != null && num in 1..5) -> "band:k-5"
                num != null && num in 6..8 -> "band:6-8"
                num != null && num in 9..10 -> "band:9-10"
                num != null && num in 11..12 -> "band:11-12"
                else -> "band:9-10"
            }
        }
    }
}
