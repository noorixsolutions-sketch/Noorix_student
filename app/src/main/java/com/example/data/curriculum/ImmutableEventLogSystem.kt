package com.example.data.curriculum

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.example.core.crypto.TinkEventSigner
import com.example.data.local.CurriculumDao
import com.example.data.local.CurriculumDatabase
import com.example.data.local.CurriculumInteractionEventEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Representation of a student learning action to be recorded in the immutable event log.
 */
data class StudentLearningAction(
    val studentId: String = EventLogger.DEFAULT_STUDENT_ID,
    val topicId: String,
    val interactionType: CurriculumInteractionType,
    val observedScore: Double? = null,
    val explicitMasteryScore: Double? = null,
    val durationSeconds: Int = 45,
    val topicTitle: String? = null,
    val subjectId: String? = null,
    val subjectName: String? = null,
    val gradeId: String? = null,
    val gradeName: String? = null,
    val bandId: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Verification result for an individual event record.
 */
data class RecordVerificationResult(
    val eventId: String,
    val isChainHashValid: Boolean,
    val isSignatureValid: Boolean,
    val isValid: Boolean,
    val failureReason: String? = null
)

/**
 * Comprehensive audit report of the entire immutable event log in Room.
 */
data class LogIntegrityReport(
    val totalEvents: Int,
    val isChainValid: Boolean,
    val areSignaturesValid: Boolean,
    val isTamperFree: Boolean,
    val verifiedCount: Int,
    val firstTamperedEventId: String? = null,
    val failureReason: String? = null
)

/**
 * High-integrity, immutable local event log system backed by Room and cryptographically
 * secured using Google Tink (Ed25519).
 *
 * Guarantees:
 * 1. Immutability: Room insertion enforces [androidx.room.OnConflictStrategy.ABORT], preventing
 *    overwriting or tampering with recorded actions.
 * 2. Cryptographic Authenticity: Every record is deterministically canonically encoded and signed
 *    using Google Tink's [com.google.crypto.tink.subtle.Ed25519Sign].
 * 3. Provable Hash Chain: Records are linked via a SHA-256 hash chain rooted at the genesis block.
 * 4. Local Verification: Signatures can be verified offline against the stored Tink public key
 *    using [com.google.crypto.tink.subtle.Ed25519Verify].
 */
class ImmutableEventLogSystem(
    private val context: Context,
    private val curriculumDatabase: CurriculumDatabase = CurriculumDatabase.getInstance(context),
    val tinkSigner: TinkEventSigner = TinkEventSigner.getOrCreate(context),
    private val eventLogger: EventLogger = EventLogger(
        context = context,
        curriculumDatabase = curriculumDatabase,
        tinkSigner = tinkSigner
    ),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val dao: CurriculumDao = curriculumDatabase.curriculumDao()

    /**
     * The Base64-encoded Ed25519 public key corresponding to the device/student signer.
     */
    val signerPublicKey: String
        get() = tinkSigner.publicKeyBase64

    /**
     * Records a student learning action into the immutable Room database.
     * The action is canonically encoded, signed with Tink, chained with SHA-256,
     * and stored with [androidx.room.OnConflictStrategy.ABORT].
     */
    suspend fun recordAction(action: StudentLearningAction): CurriculumInteractionEventEntity {
        return eventLogger.logInteraction(
            topicId = action.topicId,
            interactionType = action.interactionType,
            observedScore = action.observedScore,
            explicitMasteryScore = action.explicitMasteryScore,
            studentId = action.studentId,
            durationSeconds = action.durationSeconds,
            metadata = action.metadata,
            fallbackTopicTitle = action.topicTitle,
            fallbackSubjectId = action.subjectId,
            fallbackSubjectName = action.subjectName,
            fallbackGradeId = action.gradeId,
            fallbackGradeName = action.gradeName,
            fallbackBandId = action.bandId,
            timestampMs = action.timestampMs
        )
    }

    /**
     * Records a student quiz attempt with observed score and correctness.
     */
    suspend fun recordQuizAttempt(
        studentId: String,
        topicId: String,
        quizId: String,
        isCorrect: Boolean,
        score: Double = if (isCorrect) 1.0 else 0.25,
        durationSeconds: Int = 60,
        topicTitle: String? = null,
        subjectName: String? = null
    ): CurriculumInteractionEventEntity {
        return recordAction(
            StudentLearningAction(
                studentId = studentId,
                topicId = topicId,
                interactionType = CurriculumInteractionType.QUIZ_ATTEMPTED,
                observedScore = score,
                durationSeconds = durationSeconds,
                topicTitle = topicTitle,
                subjectName = subjectName,
                metadata = mapOf("quizId" to quizId, "isCorrect" to isCorrect, "score" to score)
            )
        )
    }

    /**
     * Records study interaction with notes, videos, or podcasts.
     */
    suspend fun recordStudySession(
        studentId: String,
        topicId: String,
        interactionType: CurriculumInteractionType,
        durationSeconds: Int = 90,
        metadata: Map<String, Any?> = emptyMap()
    ): CurriculumInteractionEventEntity {
        return recordAction(
            StudentLearningAction(
                studentId = studentId,
                topicId = topicId,
                interactionType = interactionType,
                durationSeconds = durationSeconds,
                metadata = metadata
            )
        )
    }

    /**
     * Verifies the cryptographic Tink signature and SHA-256 hash of an individual event record.
     */
    fun verifyRecord(event: CurriculumInteractionEventEntity): RecordVerificationResult {
        val isSignatureValid = TinkEventSigner.verifyEventRecord(event, tinkSigner.publicKeyBase64)
        if (!isSignatureValid) {
            return RecordVerificationResult(
                eventId = event.eventId,
                isChainHashValid = false,
                isSignatureValid = false,
                isValid = false,
                failureReason = "Tink Ed25519 signature verification failed or signature is invalid."
            )
        }

        val recomputedHash = EventLogger.computeEventChainHash(
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

        val isHashValid = recomputedHash == event.chainHash
        return RecordVerificationResult(
            eventId = event.eventId,
            isChainHashValid = isHashValid,
            isSignatureValid = isSignatureValid,
            isValid = isHashValid && isSignatureValid,
            failureReason = if (!isHashValid) "SHA-256 chain hash mismatch." else null
        )
    }

    /**
     * Full audit check: Verifies sequence continuity, unbroken SHA-256 hash chain,
     * and Google Tink Ed25519 signature for every single recorded event in the local Room database.
     */
    suspend fun verifyEntireLog(): LogIntegrityReport = withContext(ioDispatcher) {
        val events = dao.getAllInteractionEventsChronological()
        if (events.isEmpty()) {
            return@withContext LogIntegrityReport(
                totalEvents = 0,
                isChainValid = true,
                areSignaturesValid = true,
                isTamperFree = true,
                verifiedCount = 0
            )
        }

        var expectedPrev = EventLogger.GENESIS_CHAIN_HASH
        var expectedSeq = 1L
        var verifiedCount = 0

        for (event in events) {
            if (event.sequenceNumber != expectedSeq) {
                return@withContext LogIntegrityReport(
                    totalEvents = events.size,
                    isChainValid = false,
                    areSignaturesValid = false,
                    isTamperFree = false,
                    verifiedCount = verifiedCount,
                    firstTamperedEventId = event.eventId,
                    failureReason = "Sequence discontinuity at event ${event.eventId}: expected $expectedSeq, found ${event.sequenceNumber}"
                )
            }

            if (event.prevChainHash != expectedPrev) {
                return@withContext LogIntegrityReport(
                    totalEvents = events.size,
                    isChainValid = false,
                    areSignaturesValid = false,
                    isTamperFree = false,
                    verifiedCount = verifiedCount,
                    firstTamperedEventId = event.eventId,
                    failureReason = "Hash chain broken at event ${event.eventId}: expected prevHash $expectedPrev, found ${event.prevChainHash}"
                )
            }

            val recomputedHash = EventLogger.computeEventChainHash(
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
            if (recomputedHash != event.chainHash) {
                return@withContext LogIntegrityReport(
                    totalEvents = events.size,
                    isChainValid = false,
                    areSignaturesValid = false,
                    isTamperFree = false,
                    verifiedCount = verifiedCount,
                    firstTamperedEventId = event.eventId,
                    failureReason = "Hash payload mismatch at event ${event.eventId}: computed $recomputedHash vs stored ${event.chainHash}"
                )
            }

            val isSignatureValid = TinkEventSigner.verifyEventRecord(event, tinkSigner.publicKeyBase64)
            if (!isSignatureValid) {
                return@withContext LogIntegrityReport(
                    totalEvents = events.size,
                    isChainValid = true,
                    areSignaturesValid = false,
                    isTamperFree = false,
                    verifiedCount = verifiedCount,
                    firstTamperedEventId = event.eventId,
                    failureReason = "Cryptographic Tink Ed25519 signature invalid for event ${event.eventId}"
                )
            }

            expectedPrev = event.chainHash
            expectedSeq++
            verifiedCount++
        }

        LogIntegrityReport(
            totalEvents = events.size,
            isChainValid = true,
            areSignaturesValid = true,
            isTamperFree = true,
            verifiedCount = verifiedCount
        )
    }

    /**
     * Confirms that attempting to overwrite or mutate an existing event record in Room
     * is rejected by the underlying SQLite constraint ([androidx.room.OnConflictStrategy.ABORT]).
     */
    suspend fun attemptTamperInsert(tamperedEvent: CurriculumInteractionEventEntity): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            dao.insertImmutableInteractionEvent(tamperedEvent)
        }
    }

    /**
     * Observes the complete chronological learning action trail for a student.
     */
    fun getStudentHistory(studentId: String = EventLogger.DEFAULT_STUDENT_ID): Flow<List<CurriculumInteractionEventEntity>> {
        return dao.getInteractionTrailForStudentFlow(studentId)
    }

    /**
     * Retrieves a point-in-time snapshot of a student's learning action history.
     */
    suspend fun getStudentHistorySnapshot(studentId: String = EventLogger.DEFAULT_STUDENT_ID): List<CurriculumInteractionEventEntity> = withContext(ioDispatcher) {
        dao.getInteractionTrailForStudent(studentId)
    }

    /**
     * Observes all recorded events across all students.
     */
    fun getAllEventsFlow(): Flow<List<CurriculumInteractionEventEntity>> {
        return dao.getAllInteractionEventsFlow()
    }

    /**
     * Retrieves an event by its unique ID.
     */
    suspend fun getEventById(eventId: String): CurriculumInteractionEventEntity? = withContext(ioDispatcher) {
        dao.getInteractionEventById(eventId)
    }

    /**
     * Exports the cryptographic audit trail as a formatted JSON string for external verification.
     */
    suspend fun exportAuditTrailJson(): String = withContext(ioDispatcher) {
        val events = dao.getAllInteractionEventsChronological()
        val report = verifyEntireLog()

        val json = JSONObject().apply {
            put("system", "TinkImmutableEventLogSystem")
            put("algorithm", "Ed25519")
            put("signerPublicKey", signerPublicKey)
            put("totalEvents", events.size)
            put("isTamperFree", report.isTamperFree)
            put("events", JSONArray().apply {
                events.forEach { event ->
                    put(JSONObject().apply {
                        put("eventId", event.eventId)
                        put("sequenceNumber", event.sequenceNumber)
                        put("studentId", event.studentId)
                        put("interactionType", event.interactionType)
                        put("topicId", event.topicId)
                        put("observedScore", event.observedScore)
                        put("masteryBefore", event.masteryBefore)
                        put("masteryAfter", event.masteryAfter)
                        put("timestampMs", event.timestampMs)
                        put("prevChainHash", event.prevChainHash)
                        put("chainHash", event.chainHash)
                        put("signature", event.signature)
                        put("signerPublicKey", event.signerPublicKey)
                    })
                }
            })
        }
        json.toString(2)
    }

    companion object {
        @Volatile
        private var instance: ImmutableEventLogSystem? = null

        fun getInstance(context: Context): ImmutableEventLogSystem {
            return instance ?: synchronized(this) {
                instance ?: ImmutableEventLogSystem(context.applicationContext).also { instance = it }
            }
        }

        fun createForTesting(
            context: Context,
            database: CurriculumDatabase,
            signer: TinkEventSigner = TinkEventSigner.createEphemeral()
        ): ImmutableEventLogSystem {
            return ImmutableEventLogSystem(
                context = context,
                curriculumDatabase = database,
                tinkSigner = signer,
                eventLogger = EventLogger(
                    context = context,
                    curriculumDatabase = database,
                    tinkSigner = signer
                )
            )
        }
    }
}
