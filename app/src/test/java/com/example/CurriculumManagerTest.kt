package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.curriculum.CurriculumFilter
import com.example.data.curriculum.CurriculumInteractionType
import com.example.data.curriculum.CurriculumManager
import com.example.data.curriculum.EventLogger
import com.example.data.local.CurriculumDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurriculumManagerTest {

    private lateinit var context: Context
    private lateinit var database: CurriculumDatabase
    private lateinit var eventLogger: EventLogger
    private lateinit var curriculumManager: CurriculumManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CurriculumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        CurriculumDatabase.setInstanceForTesting(database)
        eventLogger = EventLogger(
            context = context,
            curriculumDatabase = database,
            appDatabase = null,
            ioDispatcher = Dispatchers.Unconfined
        )
        curriculumManager = CurriculumManager(
            context = context,
            database = database,
            ioDispatcher = Dispatchers.Unconfined,
            eventLogger = eventLogger
        )
    }

    @After
    fun tearDown() {
        database.close()
        CurriculumDatabase.setInstanceForTesting(null)
    }

    @Test
    fun ensureIndexed_populatesAllK12TablesAndReusesChecksumCache() = runBlocking {
        // First index run parses assets and writes to Room
        val firstStats = curriculumManager.ensureIndexed(forceReindex = false)
        assertFalse("First index run should parse assets, not reuse cache", firstStats.wasReusedFromCache)
        assertEquals(4, firstStats.totalBands)
        assertEquals(13, firstStats.totalGrades) // Kindergarten + Grades 1..12
        assertTrue("Should index at least 35 subjects across K-12", firstStats.totalSubjects >= 35)
        assertTrue("Should index at least 40 topics across K-12", firstStats.totalTopics >= 40)
        assertTrue("SHA-256 content checksum must be 64 hex chars", firstStats.contentChecksum.length == 64)

        // Second call with forceReindex = false should reuse the Room index via SHA-256 checksum
        val cachedStats = curriculumManager.ensureIndexed(forceReindex = false)
        assertTrue("Second index call with unchanged checksum should reuse Room cache", cachedStats.wasReusedFromCache)
        assertEquals(firstStats.contentChecksum, cachedStats.contentChecksum)
        assertEquals(firstStats.totalTopics, cachedStats.totalTopics)

        // Forced reindex rebuilds cleanly
        val forcedStats = curriculumManager.ensureIndexed(forceReindex = true)
        assertFalse("Forced reindex should rebuild Room tables", forcedStats.wasReusedFromCache)
        assertEquals(firstStats.totalTopics, forcedStats.totalTopics)
    }

    @Test
    fun hierarchicalBrowsing_returnsOrderedBandsGradesSubjectsAndTopics() = runBlocking {
        val bands = curriculumManager.getBandsHierarchy()
        assertEquals(4, bands.size)
        assertEquals("band:k-5", bands[0].bandId)
        assertEquals("band:6-8", bands[1].bandId)
        assertEquals("band:9-10", bands[2].bandId)
        assertEquals("band:11-12", bands[3].bandId)

        // Verify Kindergarten through Grade 5 in Elementary Band
        val elementaryGrades = curriculumManager.getGradesForBand("band:k-5")
        assertEquals(6, elementaryGrades.size)
        assertEquals("grade:k", elementaryGrades.first().gradeId)
        assertEquals("grade:5", elementaryGrades.last().gradeId)

        // Verify Grade 9 subjects and topics
        val grade9Subjects = curriculumManager.getSubjectsForGrade("grade:9")
        assertTrue("Grade 9 should have multiple subjects", grade9Subjects.size >= 4)

        val math9Topics = curriculumManager.getTopicsForSubject("subj:9:math-4024")
        assertTrue(math9Topics.any { it.topicId == "concept:4024:factorisation" })

        val subjectNames = curriculumManager.getDistinctSubjectNames()
        assertTrue("Distinct subjects should include Mathematics", subjectNames.any { it.contains("Mathematics") })
    }

    @Test
    fun browseAndSearchTopics_filtersByBandGradeSubjectAndMultiWordQuery() = runBlocking {
        curriculumManager.ensureIndexed()

        // 1. Search by topic keyword & formula across all bands
        val calculusResults = curriculumManager.searchTopics("calculus")
        assertTrue("Searching 'calculus' should find A-Level calculus topics", calculusResults.isNotEmpty())
        assertTrue(calculusResults.any { it.topicId == "topic:11:math:differentiation-integration" })

        // 2. Multi-word token search across grade + misconception/formula
        val factorisationResults = curriculumManager.browseTopics(
            CurriculumFilter(
                bandId = "band:9-10",
                gradeId = "grade:9",
                query = "trinomial factorisation"
            )
        )
        assertEquals(1, factorisationResults.size)
        assertEquals("concept:4024:factorisation", factorisationResults.first().topicId)

        // 3. Reactive Flow emits filtered topics
        val elementaryPhonics = curriculumManager.browseTopicsFlow(
            CurriculumFilter(bandId = "band:k-5", query = "phonics")
        ).first()
        assertTrue(elementaryPhonics.any { it.topicId == "topic:k:eng:phonics-alphabet" })
    }

    @Test
    fun getNotebookLmBundleForTopic_synthesizesGroundedPodcastVideoAndQuizFromRoomIndex() = runBlocking {
        val bundle = curriculumManager.getNotebookLmBundleForTopic(
            topicId = "concept:4024:factorisation",
            customUserNotes = listOf("Always check common factors first before splitting middle terms.")
        )
        assertNotNull("Should synthesize NotebookLM bundle from Room-indexed topic", bundle)
        requireNotNull(bundle)
        assertEquals("concept:4024:factorisation", bundle.topic.topicId)
        assertEquals(5, bundle.sources.size) // 4 core syllabus sources + 1 custom note
        assertTrue(bundle.podcast.turns.size >= 6)
        assertEquals(5, bundle.videoExplainer.scenes.size)
        assertEquals(4, bundle.quizItems.size)
    }

    @Test
    fun eventLogger_recordsImmutableEntriesAndBuildsLongTermMasteryTrajectory() = runBlocking {
        curriculumManager.ensureIndexed()

        val topic = curriculumManager.getTopicById("concept:4024:factorisation")
        requireNotNull(topic)

        // 1. Log topic viewed
        val e1 = eventLogger.logTopicViewed(topic, studentId = "student-42", durationSeconds = 60)
        assertEquals(1L, e1.sequenceNumber)
        assertEquals(EventLogger.GENESIS_CHAIN_HASH, e1.prevChainHash)
        assertTrue(e1.masteryAfter > e1.masteryBefore)

        // 2. Log podcast explored
        val e2 = eventLogger.logPodcastExplored(
            topicId = topic.topicId,
            questionAsked = "Why do we flip the sign when factoring a negative group?",
            studentId = "student-42",
            durationSeconds = 120
        )
        assertEquals(2L, e2.sequenceNumber)
        assertEquals(e1.chainHash, e2.prevChainHash)
        assertTrue(e2.masteryAfter > e1.masteryAfter)

        // 3. Log quiz attempt (correct)
        val e3 = eventLogger.logQuizAttempt(
            topicId = topic.topicId,
            quizId = "quiz-1",
            isCorrect = true,
            score = 1.0,
            studentId = "student-42",
            durationSeconds = 45
        )
        assertEquals(3L, e3.sequenceNumber)
        assertEquals(e2.chainHash, e3.prevChainHash)
        assertTrue(e3.masteryAfter > e2.masteryAfter)

        // Verify SHA-256 chain integrity and trajectory
        assertTrue("Immutable trail hash chain should verify cleanly", eventLogger.verifyTrailIntegrity())

        val trajectory = eventLogger.getTopicMasteryTrajectory(topic.topicId)
        assertEquals(3, trajectory.size)
        assertEquals(e1.eventId, trajectory[0].eventId)
        assertEquals(e3.eventId, trajectory[2].eventId)

        val analysis = eventLogger.analyzeLongTermMastery()
        assertEquals(3, analysis.totalInteractions)
        assertEquals(1, analysis.totalAssessmentEvents)
        assertEquals(1.0, analysis.overallAverageQuizAccuracy, 0.001)
        assertEquals(225, analysis.totalStudyDurationSeconds)
        assertTrue(analysis.isChainIntegrityVerified)
    }

    @Test
    fun oLevelSubjectMasteryQueries_aggregateProgressAndSurviveReindexing() = runBlocking {
        curriculumManager.ensureIndexed()

        // Initially all O-Level subjects are indexed with 0.0 mastery
        val initialSubjects = curriculumManager.getOLevelSubjectMastery()
        assertEquals(5, initialSubjects.size) // Math 4024, Physics 5054, Chemistry 5070, Biology 5090, CS 2210

        // Calibrate mastery across O-Level Mathematics and Physics topics
        curriculumManager.updateTopicMastery("concept:4024:factorisation", 0.90)
        curriculumManager.updateTopicMastery("concept:4024:simultaneous-equations", 0.85)
        curriculumManager.updateTopicMastery("concept:4024:quadratic-equations", 0.75)
        curriculumManager.updateTopicMastery("concept:5054:kinematics", 0.88)

        val updatedSubjects = curriculumManager.oLevelSubjectMasteryFlow.first()
        val mathAgg = updatedSubjects.first { it.subjectName.contains("Mathematics") }
        val physicsAgg = updatedSubjects.first { it.subjectName.contains("Physics") }

        assertEquals(6, mathAgg.totalTopics)
        assertEquals(3, mathAgg.assessedTopics)
        assertEquals(2, mathAgg.masteredTopics) // 0.90 and 0.85 >= 0.80
        assertEquals(1, mathAgg.developingTopics) // 0.75 in [0.55, 0.80)
        assertTrue("Math average mastery should be > 0.40", mathAgg.averageMastery > 0.40)

        assertEquals(4, physicsAgg.totalTopics)
        assertEquals(1, physicsAgg.masteredTopics)
        assertTrue("Physics average mastery should be > 0.20", physicsAgg.averageMastery > 0.20)

        // Priority topics query should return unassessed (0.0) topics first
        val priorityTopics = curriculumManager.getDiagnosticPriorityTopics()
        assertTrue(priorityTopics.isNotEmpty())
        assertEquals(0.0, priorityTopics.first().masteryScore, 0.001)

        // Force a full re-index and verify mastery scores are preserved!
        curriculumManager.ensureIndexed(forceReindex = true)
        val afterReindexSubjects = curriculumManager.getOLevelSubjectMastery()
        val mathAfterReindex = afterReindexSubjects.first { it.subjectName.contains("Mathematics") }
        assertEquals(mathAgg.masteredTopics, mathAfterReindex.masteredTopics)
        assertEquals(mathAgg.averageMastery, mathAfterReindex.averageMastery, 0.001)
    }
}
