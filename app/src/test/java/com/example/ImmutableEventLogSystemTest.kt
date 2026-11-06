package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.crypto.TinkEventSigner
import com.example.data.curriculum.CurriculumInteractionType
import com.example.data.curriculum.ImmutableEventLogSystem
import com.example.data.curriculum.StudentLearningAction
import com.example.data.local.CurriculumDatabase
import com.google.crypto.tink.subtle.Ed25519Verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImmutableEventLogSystemTest {

    private lateinit var context: Context
    private lateinit var database: CurriculumDatabase
    private lateinit var ephemeralSigner: TinkEventSigner
    private lateinit var eventLogSystem: ImmutableEventLogSystem

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CurriculumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        CurriculumDatabase.setInstanceForTesting(database)

        ephemeralSigner = TinkEventSigner.createEphemeral()
        eventLogSystem = ImmutableEventLogSystem.createForTesting(
            context = context,
            database = database,
            signer = ephemeralSigner
        )
    }

    @After
    fun tearDown() {
        database.close()
        CurriculumDatabase.setInstanceForTesting(null)
    }

    @Test
    fun recordAction_createsCryptographicallySignedRecordInRoom() = runBlocking {
        val event = eventLogSystem.recordAction(
            StudentLearningAction(
                studentId = "student-001",
                topicId = "topic:9:math-4024:quadratic-formula",
                interactionType = CurriculumInteractionType.QUIZ_ATTEMPTED,
                observedScore = 0.95,
                durationSeconds = 60,
                topicTitle = "Quadratic Formula Mastery",
                subjectName = "Mathematics (Cambridge 4024)"
            )
        )

        assertNotNull(event.eventId)
        assertEquals(1L, event.sequenceNumber)
        assertEquals("student-001", event.studentId)
        assertEquals("topic:9:math-4024:quadratic-formula", event.topicId)
        assertTrue("Signature must not be blank", event.signature.isNotBlank())
        assertTrue("Signer public key must not be blank", event.signerPublicKey.isNotBlank())
        assertEquals(ephemeralSigner.publicKeyBase64, event.signerPublicKey)

        // Verify that Tink can independently verify this event record
        val isVerified = TinkEventSigner.verifyEventRecord(event)
        assertTrue("Tink Ed25519 signature must verify successfully", isVerified)

        // Verify individual record verification method
        val result = eventLogSystem.verifyRecord(event)
        assertTrue("Record verification must pass", result.isValid)
        assertTrue("Chain hash must be valid", result.isChainHashValid)
        assertTrue("Signature must be valid", result.isSignatureValid)
    }

    @Test
    fun verifyEntireLog_withMultipleSequentialActions_reportsTamperFree() = runBlocking {
        eventLogSystem.recordQuizAttempt(
            studentId = "student-101",
            topicId = "topic:9:math:linear-equations",
            quizId = "quiz-q1",
            isCorrect = true,
            score = 1.0
        )
        eventLogSystem.recordStudySession(
            studentId = "student-101",
            topicId = "topic:9:phys:kinematics",
            interactionType = CurriculumInteractionType.VIDEO_WATCHED,
            durationSeconds = 120
        )
        eventLogSystem.recordStudySession(
            studentId = "student-101",
            topicId = "topic:9:chem:atomic-structure",
            interactionType = CurriculumInteractionType.PODCAST_EXPLORED,
            durationSeconds = 180
        )
        eventLogSystem.recordAction(
            StudentLearningAction(
                studentId = "student-101",
                topicId = "topic:9:bio:cell-biology",
                interactionType = CurriculumInteractionType.DIAGNOSTIC_ASSESSED,
                observedScore = 0.85
            )
        )

        val report = eventLogSystem.verifyEntireLog()
        assertEquals(4, report.totalEvents)
        assertTrue("Chain must be valid", report.isChainValid)
        assertTrue("Signatures must be valid", report.areSignaturesValid)
        assertTrue("Log must be tamper-free", report.isTamperFree)
        assertEquals(4, report.verifiedCount)
    }

    @Test
    fun tamperedScore_failsTinkSignatureVerification() = runBlocking {
        val originalEvent = eventLogSystem.recordAction(
            StudentLearningAction(
                studentId = "student-007",
                topicId = "topic:10:cs:pseudocode",
                interactionType = CurriculumInteractionType.QUIZ_ATTEMPTED,
                observedScore = 0.40
            )
        )

        // Verify original is pristine
        assertTrue(eventLogSystem.verifyRecord(originalEvent).isValid)

        // Simulate an attacker attempting to change their quiz score from 0.40 to 1.00
        val tamperedEvent = originalEvent.copy(observedScore = 1.0)
        val tamperedResult = eventLogSystem.verifyRecord(tamperedEvent)

        assertFalse("Tampered record must fail verification", tamperedResult.isValid)
        assertFalse("Signature must fail for modified score", tamperedResult.isSignatureValid)
    }

    @Test
    fun tamperedTimestampOrTopic_failsTinkSignatureVerification() = runBlocking {
        val originalEvent = eventLogSystem.recordAction(
            StudentLearningAction(
                studentId = "student-007",
                topicId = "topic:9:math:matrices",
                interactionType = CurriculumInteractionType.FORMULA_REVIEWED
            )
        )

        // Simulate tampering with timestamp or topic
        val tamperedTimestamp = originalEvent.copy(timestampMs = originalEvent.timestampMs + 50000)
        assertFalse(TinkEventSigner.verifyEventRecord(tamperedTimestamp))

        val tamperedTopic = originalEvent.copy(topicId = "topic:9:math:different-topic")
        assertFalse(TinkEventSigner.verifyEventRecord(tamperedTopic))
    }

    @Test
    fun tamperedSignature_failsVerification() = runBlocking {
        val originalEvent = eventLogSystem.recordAction(
            StudentLearningAction(
                studentId = "student-007",
                topicId = "topic:9:chem:periodic-table",
                interactionType = CurriculumInteractionType.SOURCE_NOTE_ADDED
            )
        )

        // Corrupt signature Base64
        val corruptedSig = originalEvent.signature.reversed()
        val tamperedEvent = originalEvent.copy(signature = corruptedSig)
        val result = eventLogSystem.verifyRecord(tamperedEvent)

        assertFalse("Corrupted signature must fail", result.isValid)
        assertFalse(result.isSignatureValid)
    }

    @Test
    fun roomImmutability_duplicatePrimaryKeyInsertion_isRejected() = runBlocking {
        val event = eventLogSystem.recordAction(
            StudentLearningAction(
                studentId = "student-001",
                topicId = "topic:9:phys:forces",
                interactionType = CurriculumInteractionType.TOPIC_VIEWED
            )
        )

        // Attempting to overwrite existing record with same eventId must fail due to OnConflictStrategy.ABORT
        val duplicateInsert = eventLogSystem.attemptTamperInsert(event)
        assertTrue("Attempting to insert existing primary key must fail (ABORT)", duplicateInsert.isFailure)
    }

    @Test
    fun independentTinkVerification_usingRawEd25519Verify() = runBlocking {
        val event = eventLogSystem.recordAction(
            StudentLearningAction(
                studentId = "student-external",
                topicId = "topic:9:math:trigonometry",
                interactionType = CurriculumInteractionType.TRANSFER_TASK_COMPLETED,
                observedScore = 0.92
            )
        )

        // Reconstruct canonical payload exactly as defined in the specification
        val canonicalPayload = TinkEventSigner.buildCanonicalPayload(event)
        val rawPublicKey = Base64.getDecoder().decode(event.signerPublicKey)
        val rawSignature = Base64.getDecoder().decode(event.signature)

        // Use Tink's Ed25519Verify directly
        val verifier = Ed25519Verify(rawPublicKey)
        var rawVerified = false
        try {
            verifier.verify(rawSignature, canonicalPayload.toByteArray(Charsets.UTF_8))
            rawVerified = true
        } catch (e: Exception) {
            rawVerified = false
        }

        assertTrue("Direct Tink Ed25519Verify must validate the signature", rawVerified)
    }

    @Test
    fun studentActionHistory_queriesChronologicalTrailForStudent() = runBlocking {
        eventLogSystem.recordAction(
            StudentLearningAction(studentId = "alice", topicId = "topic:1", interactionType = CurriculumInteractionType.TOPIC_VIEWED)
        )
        eventLogSystem.recordAction(
            StudentLearningAction(studentId = "bob", topicId = "topic:2", interactionType = CurriculumInteractionType.QUIZ_ATTEMPTED)
        )
        eventLogSystem.recordAction(
            StudentLearningAction(studentId = "alice", topicId = "topic:3", interactionType = CurriculumInteractionType.VIDEO_WATCHED)
        )

        val aliceHistory = eventLogSystem.getStudentHistorySnapshot("alice")
        assertEquals(2, aliceHistory.size)
        assertEquals("topic:1", aliceHistory[0].topicId)
        assertEquals("topic:3", aliceHistory[1].topicId)

        val bobHistory = eventLogSystem.getStudentHistorySnapshot("bob")
        assertEquals(1, bobHistory.size)
        assertEquals("topic:2", bobHistory[0].topicId)
    }

    @Test
    fun exportAuditTrailJson_generatesVerifiableJsonPayload() = runBlocking {
        eventLogSystem.recordQuizAttempt(
            studentId = "student-audit",
            topicId = "topic:audit-1",
            quizId = "q-audit",
            isCorrect = true,
            score = 1.0
        )

        val auditJson = eventLogSystem.exportAuditTrailJson()
        val parsed = org.json.JSONObject(auditJson)
        assertEquals("TinkImmutableEventLogSystem", parsed.getString("system"))
        assertEquals("Ed25519", parsed.getString("algorithm"))
        assertEquals(eventLogSystem.signerPublicKey, parsed.getString("signerPublicKey"))
        assertEquals(1, parsed.getInt("totalEvents"))
        assertTrue(parsed.getBoolean("isTamperFree"))

        val eventsArray = parsed.getJSONArray("events")
        assertEquals(1, eventsArray.length())
        val firstEvent = eventsArray.getJSONObject(0)
        assertEquals("topic:audit-1", firstEvent.getString("topicId"))
        assertEquals("student-audit", firstEvent.getString("studentId"))
        assertTrue(firstEvent.getString("signature").isNotBlank())
    }
}
