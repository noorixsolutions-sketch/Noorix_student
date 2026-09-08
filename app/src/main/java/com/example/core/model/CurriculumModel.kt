package com.example.core.model

data class Provenance(
    val sourceType: String = "OFFICIAL_SYLLABUS",
    val sourceReference: String = "Cambridge-O-Level-2026",
    val sourceVersion: String = "2026-v2",
    val retrievedAt: String = "2026-09-08T00:00:00Z",
    val verified: Boolean = true,
    val contentHash: String = ""
)

enum class NodeType {
    QUALIFICATION,
    COURSE,
    COURSE_VERSION,
    SUBJECT,
    TOPIC,
    CONCEPT,
    LEARNING_OBJECTIVE,
    SKILL,
    ASSESSMENT_OBJECTIVE,
    MISCONCEPTION,
    QUESTION,
    RESOURCE
}

enum class EdgeType {
    CONTAINS,
    PARENT_OF,
    PREREQUISITE_OF,
    DEPENDS_ON,
    TEACHES,
    ASSESSES,
    MAPS_TO,
    REQUIRES_SKILL,
    HAS_MISCONCEPTION,
    NEXT,
    SUPERSEDES,
    ALIGNED_WITH
}

data class KGNode(
    val id: String,
    val type: NodeType,
    val name: String,
    val properties: Map<String, String> = emptyMap(),
    val provenance: Provenance = Provenance()
)

data class KGEdge(
    val id: String,
    val type: EdgeType,
    val source: String,
    val target: String,
    val properties: Map<String, String> = emptyMap()
)

data class CoursePackageManifest(
    val packageId: String,
    val courseId: String,
    val subjectName: String,
    val version: String,
    val schemaVersion: String = "1.0",
    val createdAt: String,
    val contentHash: String,
    val parentVersion: String? = null,
    val deltaBase: String? = null,
    val signature: String
)

data class QuestionItem(
    val id: String,
    val courseVersionId: String,
    val targetConceptId: String,
    val questionType: String = "STRUCTURED", // STRUCTURED, MULTIPLE_CHOICE, STEP_MATH
    val prompt: String,
    val options: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val targetLaTeX: String = "",
    val difficulty: Double = 0.5,
    val maxMarks: Int = 4,
    val bloomLevel: String = "APPLICATION", // RECALL, APPLICATION, ANALYSIS
    val rubrics: String = "",
    val prerequisiteConceptId: String? = null,
    val potentialMisconceptions: List<String> = emptyList()
)

data class ResourceItem(
    val id: String,
    val conceptId: String,
    val resourceType: String = "TEXT", // TEXT, PODCAST_BRIEF, WORKED_EXAMPLE
    val title: String,
    val content: String,
    val contentHash: String
)

data class CoursePackage(
    val manifest: CoursePackageManifest,
    val nodes: List<KGNode>,
    val edges: List<KGEdge>,
    val questions: List<QuestionItem>,
    val resources: List<ResourceItem>
)
