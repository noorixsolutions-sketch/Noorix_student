package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CurriculumDao {

    // --- Index Metadata ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIndexMeta(meta: CurriculumIndexMetaEntity)

    @Query("SELECT * FROM curriculum_index_meta WHERE metaKey = :metaKey LIMIT 1")
    suspend fun getIndexMeta(metaKey: String = CurriculumIndexMetaEntity.META_KEY_K12): CurriculumIndexMetaEntity?

    @Query("SELECT * FROM curriculum_index_meta WHERE metaKey = :metaKey LIMIT 1")
    fun getIndexMetaFlow(metaKey: String = CurriculumIndexMetaEntity.META_KEY_K12): Flow<CurriculumIndexMetaEntity?>

    // --- Bulk Inserts & Atomic Re-Index ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBands(bands: List<CurriculumBandEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrades(grades: List<CurriculumGradeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<CurriculumSubjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<CurriculumTopicEntity>)

    @Query("DELETE FROM curriculum_topics")
    suspend fun clearTopics()

    @Query("DELETE FROM curriculum_subjects")
    suspend fun clearSubjects()

    @Query("DELETE FROM curriculum_grades")
    suspend fun clearGrades()

    @Query("DELETE FROM curriculum_bands")
    suspend fun clearBands()

    @Transaction
    suspend fun replaceFullCurriculumIndex(
        meta: CurriculumIndexMetaEntity,
        bands: List<CurriculumBandEntity>,
        grades: List<CurriculumGradeEntity>,
        subjects: List<CurriculumSubjectEntity>,
        topics: List<CurriculumTopicEntity>
    ) {
        clearTopics()
        clearSubjects()
        clearGrades()
        clearBands()
        insertBands(bands)
        insertGrades(grades)
        insertSubjects(subjects)
        insertTopics(topics)
        upsertIndexMeta(meta)
    }

    // --- Band & Hierarchy Queries ---

    @Query("SELECT * FROM curriculum_bands ORDER BY sortOrder ASC")
    suspend fun getAllBands(): List<CurriculumBandEntity>

    @Query("SELECT * FROM curriculum_bands ORDER BY sortOrder ASC")
    fun getAllBandsFlow(): Flow<List<CurriculumBandEntity>>

    @Transaction
    @Query("SELECT * FROM curriculum_bands ORDER BY sortOrder ASC")
    suspend fun getAllBandsHierarchy(): List<BandWithGradesHierarchyRelation>

    @Transaction
    @Query("SELECT * FROM curriculum_bands ORDER BY sortOrder ASC")
    fun getAllBandsHierarchyFlow(): Flow<List<BandWithGradesHierarchyRelation>>

    // --- Grade Queries ---

    @Query("SELECT * FROM curriculum_grades ORDER BY sortOrder ASC")
    suspend fun getAllGrades(): List<CurriculumGradeEntity>

    @Query("SELECT * FROM curriculum_grades WHERE bandId = :bandId ORDER BY sortOrder ASC")
    suspend fun getGradesForBand(bandId: String): List<CurriculumGradeEntity>

    @Query("SELECT * FROM curriculum_grades WHERE bandId = :bandId ORDER BY sortOrder ASC")
    fun getGradesForBandFlow(bandId: String): Flow<List<CurriculumGradeEntity>>

    @Transaction
    @Query("SELECT * FROM curriculum_grades WHERE gradeId = :gradeId LIMIT 1")
    suspend fun getGradeWithSubjects(gradeId: String): GradeWithSubjectsRelation?

    // --- Subject Queries ---

    @Query("SELECT * FROM curriculum_subjects ORDER BY sortOrder ASC")
    suspend fun getAllSubjects(): List<CurriculumSubjectEntity>

    @Query("SELECT * FROM curriculum_subjects WHERE gradeId = :gradeId ORDER BY sortOrder ASC")
    suspend fun getSubjectsForGrade(gradeId: String): List<CurriculumSubjectEntity>

    @Query("SELECT * FROM curriculum_subjects WHERE gradeId = :gradeId ORDER BY sortOrder ASC")
    fun getSubjectsForGradeFlow(gradeId: String): Flow<List<CurriculumSubjectEntity>>

    @Query("SELECT * FROM curriculum_subjects WHERE bandId = :bandId ORDER BY sortOrder ASC")
    suspend fun getSubjectsForBand(bandId: String): List<CurriculumSubjectEntity>

    @Query("SELECT DISTINCT subjectName FROM curriculum_subjects ORDER BY subjectName ASC")
    suspend fun getDistinctSubjectNames(): List<String>

    @Query("SELECT DISTINCT subjectName FROM curriculum_subjects ORDER BY subjectName ASC")
    fun getDistinctSubjectNamesFlow(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM curriculum_subjects WHERE subjectId = :subjectId LIMIT 1")
    suspend fun getSubjectWithTopics(subjectId: String): SubjectWithTopicsRelation?

    // --- Topic Browsing & Search Queries ---

    @Query("SELECT * FROM curriculum_topics ORDER BY sortOrder ASC")
    suspend fun getAllTopics(): List<CurriculumTopicEntity>

    @Query("SELECT * FROM curriculum_topics ORDER BY sortOrder ASC")
    fun getAllTopicsFlow(): Flow<List<CurriculumTopicEntity>>

    @Query("SELECT * FROM curriculum_topics WHERE topicId = :topicId LIMIT 1")
    suspend fun getTopicById(topicId: String): CurriculumTopicEntity?

    @Query("SELECT * FROM curriculum_topics WHERE topicId = :topicId LIMIT 1")
    fun getTopicByIdFlow(topicId: String): Flow<CurriculumTopicEntity?>

    @Query("SELECT * FROM curriculum_topics WHERE subjectId = :subjectId ORDER BY sortOrder ASC")
    suspend fun getTopicsForSubject(subjectId: String): List<CurriculumTopicEntity>

    @Query("SELECT * FROM curriculum_topics WHERE subjectId = :subjectId ORDER BY sortOrder ASC")
    fun getTopicsForSubjectFlow(subjectId: String): Flow<List<CurriculumTopicEntity>>

    @Query("SELECT * FROM curriculum_topics WHERE gradeId = :gradeId ORDER BY sortOrder ASC")
    suspend fun getTopicsForGrade(gradeId: String): List<CurriculumTopicEntity>

    @Query("SELECT * FROM curriculum_topics WHERE gradeId = :gradeId ORDER BY sortOrder ASC")
    fun getTopicsForGradeFlow(gradeId: String): Flow<List<CurriculumTopicEntity>>

    @Query("SELECT * FROM curriculum_topics WHERE bandId = :bandId ORDER BY sortOrder ASC")
    suspend fun getTopicsForBand(bandId: String): List<CurriculumTopicEntity>

    @Query("SELECT * FROM curriculum_topics WHERE bandId = :bandId ORDER BY sortOrder ASC")
    fun getTopicsForBandFlow(bandId: String): Flow<List<CurriculumTopicEntity>>

    /**
     * Multi-criteria browsing and full-text search across the indexed K-12 curriculum.
     * Any filter parameter passed as null or empty string is treated as a wildcard.
     */
    @Query(
        """
        SELECT * FROM curriculum_topics
        WHERE (:bandId IS NULL OR :bandId = '' OR bandId = :bandId)
          AND (:gradeId IS NULL OR :gradeId = '' OR gradeId = :gradeId)
          AND (:subjectId IS NULL OR :subjectId = '' OR subjectId = :subjectId)
          AND (:query = '' OR searchTokens LIKE '%' || LOWER(:query) || '%')
        ORDER BY sortOrder ASC
        """
    )
    suspend fun browseTopics(
        bandId: String? = null,
        gradeId: String? = null,
        subjectId: String? = null,
        query: String = ""
    ): List<CurriculumTopicEntity>

    @Query(
        """
        SELECT * FROM curriculum_topics
        WHERE (:bandId IS NULL OR :bandId = '' OR bandId = :bandId)
          AND (:gradeId IS NULL OR :gradeId = '' OR gradeId = :gradeId)
          AND (:subjectId IS NULL OR :subjectId = '' OR subjectId = :subjectId)
          AND (:query = '' OR searchTokens LIKE '%' || LOWER(:query) || '%')
        ORDER BY sortOrder ASC
        """
    )
    fun browseTopicsFlow(
        bandId: String? = null,
        gradeId: String? = null,
        subjectId: String? = null,
        query: String = ""
    ): Flow<List<CurriculumTopicEntity>>

    // --- Aggregate Count Queries ---

    @Query("SELECT COUNT(*) FROM curriculum_bands")
    suspend fun getBandCount(): Int

    @Query("SELECT COUNT(*) FROM curriculum_grades")
    suspend fun getGradeCount(): Int

    @Query("SELECT COUNT(*) FROM curriculum_subjects")
    suspend fun getSubjectCount(): Int

    @Query("SELECT COUNT(*) FROM curriculum_topics")
    suspend fun getTopicCount(): Int

    @Query("SELECT COUNT(*) FROM curriculum_topics")
    fun getTopicCountFlow(): Flow<Int>

    // --- Topic Mastery Persistence & O-Level Diagnostic Queries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicMastery(mastery: CurriculumTopicMasteryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicMasteryList(items: List<CurriculumTopicMasteryEntity>)

    @Query("SELECT * FROM curriculum_topic_mastery")
    suspend fun getAllTopicMastery(): List<CurriculumTopicMasteryEntity>

    @Query("SELECT * FROM curriculum_topic_mastery")
    fun getAllTopicMasteryFlow(): Flow<List<CurriculumTopicMasteryEntity>>

    @Query("SELECT * FROM curriculum_topic_mastery WHERE topicId = :topicId LIMIT 1")
    suspend fun getTopicMastery(topicId: String): CurriculumTopicMasteryEntity?

    @Query(
        """
        UPDATE curriculum_topics
        SET masteryScore = :masteryScore,
            masteryStatus = :masteryStatus,
            evidenceCount = :evidenceCount,
            lastStudiedAtMs = :lastStudiedAtMs
        WHERE topicId = :topicId
        """
    )
    suspend fun updateTopicMasteryColumns(
        topicId: String,
        masteryScore: Double,
        masteryStatus: String,
        evidenceCount: Int,
        lastStudiedAtMs: Long
    )

    @Transaction
    suspend fun recordTopicMasteryUpdate(
        masteryEntity: CurriculumTopicMasteryEntity,
        immutableEvent: CurriculumInteractionEventEntity? = null
    ) {
        if (immutableEvent != null) {
            insertImmutableInteractionEvent(immutableEvent)
        }
        upsertTopicMastery(masteryEntity)
        updateTopicMasteryColumns(
            topicId = masteryEntity.topicId,
            masteryScore = masteryEntity.masteryScore,
            masteryStatus = masteryEntity.masteryStatus,
            evidenceCount = masteryEntity.evidenceCount,
            lastStudiedAtMs = masteryEntity.lastStudiedAtMs
        )
    }

    /**
     * Room SQL query that aggregates a student's progress across O-Level subjects
     * (default bandId = 'band:9-10') based on topic mastery scores in the CurriculumManager.
     */
    @Query(
        """
        SELECT
            t.subjectName AS subjectName,
            MIN(t.subjectId) AS representativeSubjectId,
            t.bandId AS bandId,
            MAX(t.iconName) AS iconName,
            COUNT(t.topicId) AS totalTopics,
            SUM(CASE WHEN t.masteryScore > 0.0 OR t.evidenceCount > 0 THEN 1 ELSE 0 END) AS assessedTopics,
            SUM(CASE WHEN t.masteryScore >= 0.80 THEN 1 ELSE 0 END) AS masteredTopics,
            SUM(CASE WHEN t.masteryScore >= 0.55 AND t.masteryScore < 0.80 THEN 1 ELSE 0 END) AS developingTopics,
            SUM(CASE WHEN t.masteryScore > 0.0 AND t.masteryScore < 0.55 THEN 1 ELSE 0 END) AS emergingTopics,
            SUM(CASE WHEN t.masteryScore <= 0.0 THEN 1 ELSE 0 END) AS unassessedTopics,
            COALESCE(AVG(t.masteryScore), 0.0) AS averageMastery,
            COALESCE(AVG(CASE WHEN t.masteryScore > 0.0 THEN t.masteryScore ELSE NULL END), 0.0) AS assessedAverageMastery,
            COALESCE(MAX(t.masteryScore), 0.0) AS peakMastery,
            COALESCE(MIN(t.masteryScore), 0.0) AS lowestMastery,
            COALESCE(SUM(t.evidenceCount), 0) AS totalEvidenceCount,
            COALESCE(MAX(t.lastStudiedAtMs), 0) AS lastStudiedAtMs
        FROM curriculum_topics t
        WHERE (:bandId = '' OR t.bandId = :bandId)
        GROUP BY t.subjectName
        ORDER BY averageMastery DESC, MIN(t.sortOrder) ASC
        """
    )
    fun getOLevelSubjectMasteryFlow(bandId: String = "band:9-10"): Flow<List<OLevelSubjectMasteryAggregate>>

    @Query(
        """
        SELECT
            t.subjectName AS subjectName,
            MIN(t.subjectId) AS representativeSubjectId,
            t.bandId AS bandId,
            MAX(t.iconName) AS iconName,
            COUNT(t.topicId) AS totalTopics,
            SUM(CASE WHEN t.masteryScore > 0.0 OR t.evidenceCount > 0 THEN 1 ELSE 0 END) AS assessedTopics,
            SUM(CASE WHEN t.masteryScore >= 0.80 THEN 1 ELSE 0 END) AS masteredTopics,
            SUM(CASE WHEN t.masteryScore >= 0.55 AND t.masteryScore < 0.80 THEN 1 ELSE 0 END) AS developingTopics,
            SUM(CASE WHEN t.masteryScore > 0.0 AND t.masteryScore < 0.55 THEN 1 ELSE 0 END) AS emergingTopics,
            SUM(CASE WHEN t.masteryScore <= 0.0 THEN 1 ELSE 0 END) AS unassessedTopics,
            COALESCE(AVG(t.masteryScore), 0.0) AS averageMastery,
            COALESCE(AVG(CASE WHEN t.masteryScore > 0.0 THEN t.masteryScore ELSE NULL END), 0.0) AS assessedAverageMastery,
            COALESCE(MAX(t.masteryScore), 0.0) AS peakMastery,
            COALESCE(MIN(t.masteryScore), 0.0) AS lowestMastery,
            COALESCE(SUM(t.evidenceCount), 0) AS totalEvidenceCount,
            COALESCE(MAX(t.lastStudiedAtMs), 0) AS lastStudiedAtMs
        FROM curriculum_topics t
        WHERE (:bandId = '' OR t.bandId = :bandId)
        GROUP BY t.subjectName
        ORDER BY averageMastery DESC, MIN(t.sortOrder) ASC
        """
    )
    suspend fun getOLevelSubjectMastery(bandId: String = "band:9-10"): List<OLevelSubjectMasteryAggregate>

    @Query(
        """
        SELECT
            t.subjectId AS subjectId,
            t.subjectName AS subjectName,
            t.gradeId AS gradeId,
            t.gradeName AS gradeName,
            t.bandId AS bandId,
            MAX(t.iconName) AS iconName,
            COUNT(t.topicId) AS totalTopics,
            SUM(CASE WHEN t.masteryScore > 0.0 OR t.evidenceCount > 0 THEN 1 ELSE 0 END) AS assessedTopics,
            SUM(CASE WHEN t.masteryScore >= 0.80 THEN 1 ELSE 0 END) AS masteredTopics,
            COALESCE(AVG(t.masteryScore), 0.0) AS averageMastery,
            COALESCE(SUM(t.evidenceCount), 0) AS totalEvidenceCount,
            COALESCE(MAX(t.lastStudiedAtMs), 0) AS lastStudiedAtMs
        FROM curriculum_topics t
        WHERE (:bandId = '' OR t.bandId = :bandId)
        GROUP BY t.subjectId
        ORDER BY MIN(t.sortOrder) ASC
        """
    )
    fun getOLevelGradeSubjectMasteryFlow(bandId: String = "band:9-10"): Flow<List<OLevelGradeSubjectMasteryRow>>

    @Query(
        """
        SELECT
            t.subjectId AS subjectId,
            t.subjectName AS subjectName,
            t.gradeId AS gradeId,
            t.gradeName AS gradeName,
            t.bandId AS bandId,
            MAX(t.iconName) AS iconName,
            COUNT(t.topicId) AS totalTopics,
            SUM(CASE WHEN t.masteryScore > 0.0 OR t.evidenceCount > 0 THEN 1 ELSE 0 END) AS assessedTopics,
            SUM(CASE WHEN t.masteryScore >= 0.80 THEN 1 ELSE 0 END) AS masteredTopics,
            COALESCE(AVG(t.masteryScore), 0.0) AS averageMastery,
            COALESCE(SUM(t.evidenceCount), 0) AS totalEvidenceCount,
            COALESCE(MAX(t.lastStudiedAtMs), 0) AS lastStudiedAtMs
        FROM curriculum_topics t
        WHERE (:bandId = '' OR t.bandId = :bandId)
        GROUP BY t.subjectId
        ORDER BY MIN(t.sortOrder) ASC
        """
    )
    suspend fun getOLevelGradeSubjectMastery(bandId: String = "band:9-10"): List<OLevelGradeSubjectMasteryRow>

    @Query(
        """
        SELECT * FROM curriculum_topics
        WHERE (:bandId = '' OR bandId = :bandId)
        ORDER BY masteryScore ASC, evidenceCount ASC, sortOrder ASC
        """
    )
    fun getDiagnosticPriorityTopicsFlow(bandId: String = "band:9-10"): Flow<List<CurriculumTopicEntity>>

    @Query(
        """
        SELECT * FROM curriculum_topics
        WHERE (:bandId = '' OR bandId = :bandId)
        ORDER BY masteryScore ASC, evidenceCount ASC, sortOrder ASC
        """
    )
    suspend fun getDiagnosticPriorityTopics(bandId: String = "band:9-10"): List<CurriculumTopicEntity>

    // --- Immutable Curriculum Interaction Trail (EventLogger) ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImmutableInteractionEvent(event: CurriculumInteractionEventEntity): Long

    @Query("SELECT * FROM curriculum_interaction_events ORDER BY sequenceNumber DESC, timestampMs DESC")
    fun getAllInteractionEventsFlow(): Flow<List<CurriculumInteractionEventEntity>>

    @Query("SELECT * FROM curriculum_interaction_events ORDER BY sequenceNumber ASC, timestampMs ASC")
    suspend fun getAllInteractionEventsChronological(): List<CurriculumInteractionEventEntity>

    @Query("SELECT * FROM curriculum_interaction_events ORDER BY sequenceNumber DESC, timestampMs DESC LIMIT :limit")
    fun getRecentInteractionEventsFlow(limit: Int = 30): Flow<List<CurriculumInteractionEventEntity>>

    @Query("SELECT * FROM curriculum_interaction_events ORDER BY sequenceNumber DESC, timestampMs DESC LIMIT :limit")
    suspend fun getRecentInteractionEvents(limit: Int = 30): List<CurriculumInteractionEventEntity>

    @Query("SELECT * FROM curriculum_interaction_events WHERE topicId = :topicId ORDER BY sequenceNumber ASC, timestampMs ASC")
    suspend fun getInteractionTrailForTopic(topicId: String): List<CurriculumInteractionEventEntity>

    @Query("SELECT * FROM curriculum_interaction_events WHERE topicId = :topicId ORDER BY sequenceNumber DESC, timestampMs DESC")
    fun getInteractionTrailForTopicFlow(topicId: String): Flow<List<CurriculumInteractionEventEntity>>

    @Query("SELECT * FROM curriculum_interaction_events WHERE subjectName = :subjectName ORDER BY sequenceNumber ASC, timestampMs ASC")
    suspend fun getInteractionTrailForSubject(subjectName: String): List<CurriculumInteractionEventEntity>

    @Query("SELECT * FROM curriculum_interaction_events WHERE bandId = :bandId ORDER BY sequenceNumber DESC, timestampMs DESC")
    fun getInteractionEventsForBandFlow(bandId: String = "band:9-10"): Flow<List<CurriculumInteractionEventEntity>>

    @Query("SELECT * FROM curriculum_interaction_events WHERE studentId = :studentId ORDER BY sequenceNumber ASC, timestampMs ASC")
    suspend fun getInteractionTrailForStudent(studentId: String): List<CurriculumInteractionEventEntity>

    @Query("SELECT * FROM curriculum_interaction_events WHERE studentId = :studentId ORDER BY sequenceNumber DESC, timestampMs DESC")
    fun getInteractionTrailForStudentFlow(studentId: String): Flow<List<CurriculumInteractionEventEntity>>

    @Query("SELECT * FROM curriculum_interaction_events WHERE eventId = :eventId LIMIT 1")
    suspend fun getInteractionEventById(eventId: String): CurriculumInteractionEventEntity?

    @Query("SELECT * FROM curriculum_interaction_events ORDER BY sequenceNumber DESC LIMIT 1")
    suspend fun getLatestInteractionEvent(): CurriculumInteractionEventEntity?

    @Query("SELECT COUNT(*) FROM curriculum_interaction_events")
    suspend fun getInteractionEventCount(): Int

    @Query("SELECT COUNT(*) FROM curriculum_interaction_events")
    fun getInteractionEventCountFlow(): Flow<Int>

    @Query(
        """
        SELECT
            subjectName AS subjectName,
            COUNT(*) AS totalInteractions,
            SUM(CASE WHEN observedScore IS NOT NULL THEN 1 ELSE 0 END) AS quizAttempts,
            COALESCE(AVG(observedScore), 0.0) AS avgObservedScore,
            COALESCE(SUM(masteryDelta), 0.0) AS cumulativeMasteryDelta,
            COALESCE(MAX(masteryAfter), 0.0) AS peakMasteryReached,
            COALESCE(SUM(durationSeconds), 0) AS totalStudySeconds,
            COALESCE(MIN(timestampMs), 0) AS firstInteractionMs,
            COALESCE(MAX(timestampMs), 0) AS lastInteractionMs
        FROM curriculum_interaction_events
        GROUP BY subjectName
        ORDER BY totalInteractions DESC, subjectName ASC
        """
    )
    fun getSubjectActivityTrailSummariesFlow(): Flow<List<SubjectActivityTrailSummary>>

    @Query(
        """
        SELECT
            subjectName AS subjectName,
            COUNT(*) AS totalInteractions,
            SUM(CASE WHEN observedScore IS NOT NULL THEN 1 ELSE 0 END) AS quizAttempts,
            COALESCE(AVG(observedScore), 0.0) AS avgObservedScore,
            COALESCE(SUM(masteryDelta), 0.0) AS cumulativeMasteryDelta,
            COALESCE(MAX(masteryAfter), 0.0) AS peakMasteryReached,
            COALESCE(SUM(durationSeconds), 0) AS totalStudySeconds,
            COALESCE(MIN(timestampMs), 0) AS firstInteractionMs,
            COALESCE(MAX(timestampMs), 0) AS lastInteractionMs
        FROM curriculum_interaction_events
        GROUP BY subjectName
        ORDER BY totalInteractions DESC, subjectName ASC
        """
    )
    suspend fun getSubjectActivityTrailSummaries(): List<SubjectActivityTrailSummary>
}
