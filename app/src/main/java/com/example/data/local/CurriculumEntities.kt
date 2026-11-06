package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Metadata record tracking the Room index state of the K-12 curriculum JSON files.
 */
@Entity(tableName = "curriculum_index_meta")
data class CurriculumIndexMetaEntity(
    @PrimaryKey val metaKey: String = META_KEY_K12,
    val curriculumTitle: String,
    val version: String,
    val notebookLmEnabled: Boolean,
    val contentChecksum: String,
    val totalBands: Int,
    val totalGrades: Int,
    val totalSubjects: Int,
    val totalTopics: Int,
    val indexedAtMs: Long
) {
    companion object {
        const val META_KEY_K12 = "k12_curriculum_index"
    }
}

/**
 * Represents a K-12 grade band (e.g., Elementary K-5, Middle 6-8, High 9-10 O-Level, Senior 11-12 A-Level).
 */
@Entity(
    tableName = "curriculum_bands",
    indices = [Index(value = ["sortOrder"])]
)
data class CurriculumBandEntity(
    @PrimaryKey val bandId: String,
    val bandName: String,
    val assetFile: String,
    val gradeRangeJson: String,
    val sortOrder: Int,
    val version: String,
    val indexedAtMs: Long
)

/**
 * Represents an individual grade level within a K-12 band (Kindergarten through Grade 12).
 */
@Entity(
    tableName = "curriculum_grades",
    indices = [
        Index(value = ["bandId"]),
        Index(value = ["bandId", "sortOrder"])
    ]
)
data class CurriculumGradeEntity(
    @PrimaryKey val gradeId: String,
    val bandId: String,
    val gradeName: String,
    val ageRange: String,
    val sortOrder: Int
)

/**
 * Represents a subject within a specific grade level (e.g., Mathematics, Physics, Chemistry, English).
 */
@Entity(
    tableName = "curriculum_subjects",
    indices = [
        Index(value = ["gradeId"]),
        Index(value = ["bandId"]),
        Index(value = ["subjectName"]),
        Index(value = ["gradeId", "sortOrder"])
    ]
)
data class CurriculumSubjectEntity(
    @PrimaryKey val subjectId: String,
    val gradeId: String,
    val bandId: String,
    val subjectName: String,
    val iconName: String,
    val topicCount: Int,
    val sortOrder: Int
)

/**
 * Represents an indexed K-12 curriculum topic with full syllabus notes, formulas,
 * worked examples, common misconceptions, pre-normalized search tokens, and
 * student mastery tracking columns for fast SQL aggregation across subjects.
 */
@Entity(
    tableName = "curriculum_topics",
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["gradeId"]),
        Index(value = ["bandId"]),
        Index(value = ["subjectName"]),
        Index(value = ["bandId", "subjectName"]),
        Index(value = ["masteryScore"]),
        Index(value = ["title"]),
        Index(value = ["subjectId", "sortOrder"])
    ]
)
data class CurriculumTopicEntity(
    @PrimaryKey val topicId: String,
    val subjectId: String,
    val gradeId: String,
    val bandId: String,
    val bandName: String,
    val gradeName: String,
    val ageRange: String,
    val subjectName: String,
    val iconName: String,
    val title: String,
    val summary: String,
    val keyFormulasJson: String,
    val sourceNotesJson: String,
    val workedExample: String,
    val commonMisconception: String,
    val searchTokens: String,
    val sortOrder: Int,
    val masteryScore: Double = 0.0,
    val masteryStatus: String = "UNASSESSED",
    val evidenceCount: Int = 0,
    val lastStudiedAtMs: Long = 0L
)

/**
 * Persistent per-topic mastery state table that survives full curriculum JSON re-indexing
 * and stores cumulative evidence metrics for each topic.
 */
@Entity(
    tableName = "curriculum_topic_mastery",
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["subjectName"]),
        Index(value = ["bandId"]),
        Index(value = ["gradeId"]),
        Index(value = ["masteryScore"])
    ]
)
data class CurriculumTopicMasteryEntity(
    @PrimaryKey val topicId: String,
    val subjectId: String,
    val subjectName: String,
    val gradeId: String,
    val bandId: String,
    val masteryScore: Double,
    val masteryStatus: String,
    val evidenceCount: Int,
    val correctCount: Int = 0,
    val attemptCount: Int = 0,
    val totalStudySeconds: Int = 0,
    val lastInteractionType: String = "NONE",
    val lastStudiedAtMs: Long = 0L
)

/**
 * Append-only, immutable Room entity recorded by [com.example.data.curriculum.EventLogger]
 * whenever a student interacts with any K-12 curriculum topic, quiz, explainer, or diagnostic.
 * Forms a cryptographic SHA-256 hash chain for long-term mastery trajectory analysis.
 */
@Entity(
    tableName = "curriculum_interaction_events",
    indices = [
        Index(value = ["topicId"]),
        Index(value = ["subjectId"]),
        Index(value = ["subjectName"]),
        Index(value = ["bandId"]),
        Index(value = ["studentId"]),
        Index(value = ["interactionType"]),
        Index(value = ["timestampMs"]),
        Index(value = ["sequenceNumber"], unique = true)
    ]
)
data class CurriculumInteractionEventEntity(
    @PrimaryKey val eventId: String,
    val sequenceNumber: Long,
    val studentId: String,
    val interactionType: String,
    val topicId: String,
    val topicTitle: String,
    val subjectId: String,
    val subjectName: String,
    val gradeId: String,
    val gradeName: String,
    val bandId: String,
    val observedScore: Double?,
    val masteryBefore: Double,
    val masteryAfter: Double,
    val masteryDelta: Double,
    val durationSeconds: Int,
    val metadataJson: String,
    val timestampMs: Long,
    val prevChainHash: String,
    val chainHash: String,
    val signature: String = "",
    val signerPublicKey: String = ""
)

/**
 * Room SQL query projection aggregating student mastery progress per O-Level subject
 * (or any specified curriculum band) directly from [CurriculumTopicEntity].
 */
data class OLevelSubjectMasteryAggregate(
    val subjectName: String,
    val representativeSubjectId: String,
    val bandId: String,
    val iconName: String,
    val totalTopics: Int,
    val assessedTopics: Int,
    val masteredTopics: Int,
    val developingTopics: Int,
    val emergingTopics: Int,
    val unassessedTopics: Int,
    val averageMastery: Double,
    val assessedAverageMastery: Double,
    val peakMastery: Double,
    val lowestMastery: Double,
    val totalEvidenceCount: Int,
    val lastStudiedAtMs: Long
) {
    val masteryPercent: Int
        get() = (averageMastery * 100.0).toInt().coerceIn(0, 100)

    val coveragePercent: Int
        get() = if (totalTopics > 0) ((assessedTopics.toDouble() / totalTopics) * 100.0).toInt().coerceIn(0, 100) else 0

    val readinessLabel: String
        get() = when {
            averageMastery >= 0.80 -> "Exam Ready (Distinction)"
            averageMastery >= 0.65 -> "Strong Foundation (Merit)"
            averageMastery >= 0.40 -> "Developing Mastery"
            assessedTopics > 0 -> "Emerging Baseline"
            else -> "Awaiting Diagnostic"
        }
}

/**
 * Room SQL query projection for grade-specific subject mastery rows (e.g., Grade 9 Math vs Grade 10 Math).
 */
data class OLevelGradeSubjectMasteryRow(
    val subjectId: String,
    val subjectName: String,
    val gradeId: String,
    val gradeName: String,
    val bandId: String,
    val iconName: String,
    val totalTopics: Int,
    val assessedTopics: Int,
    val masteredTopics: Int,
    val averageMastery: Double,
    val totalEvidenceCount: Int,
    val lastStudiedAtMs: Long
)

/**
 * Room SQL query projection summarizing the immutable learning activity trail per subject
 * from [CurriculumInteractionEventEntity] for long-term mastery analysis.
 */
data class SubjectActivityTrailSummary(
    val subjectName: String,
    val totalInteractions: Int,
    val quizAttempts: Int,
    val avgObservedScore: Double,
    val cumulativeMasteryDelta: Double,
    val peakMasteryReached: Double,
    val totalStudySeconds: Int,
    val firstInteractionMs: Long,
    val lastInteractionMs: Long
)

/**
 * Relational projection: a Subject together with all of its indexed Topics.
 */
data class SubjectWithTopicsRelation(
    @Embedded val subject: CurriculumSubjectEntity,
    @Relation(
        parentColumn = "subjectId",
        entityColumn = "subjectId"
    )
    val topics: List<CurriculumTopicEntity>
)

/**
 * Relational projection: a Grade together with all of its Subjects and their Topics.
 */
data class GradeWithSubjectsRelation(
    @Embedded val grade: CurriculumGradeEntity,
    @Relation(
        entity = CurriculumSubjectEntity::class,
        parentColumn = "gradeId",
        entityColumn = "gradeId"
    )
    val subjects: List<SubjectWithTopicsRelation>
)

/**
 * Relational projection: a complete K-12 Band hierarchy (Band -> Grades -> Subjects -> Topics).
 */
data class BandWithGradesHierarchyRelation(
    @Embedded val band: CurriculumBandEntity,
    @Relation(
        entity = CurriculumGradeEntity::class,
        parentColumn = "bandId",
        entityColumn = "bandId"
    )
    val grades: List<GradeWithSubjectsRelation>
)
