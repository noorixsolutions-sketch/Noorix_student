package com.example.core.model

enum class MediaFormat {
    PODCAST,
    EXPLAINER_VIDEO,
    INTERACTIVE_DIAGRAM
}

data class MediaBrief(
    val briefId: String,
    val conceptId: String,
    val conceptName: String,
    val format: MediaFormat,
    val targetDurationMinutes: Int,
    val studentLevel: String, // EMERGING, DEVELOPING, ADVANCED
    val approvedEvidenceIds: List<String>,
    val pedagogicalStrategy: String
)

data class MediaArtifact(
    val mediaId: String,
    val briefId: String,
    val title: String,
    val format: MediaFormat,
    val audioDurationSeconds: Int,
    val summaryText: String,
    val scriptSegments: List<String>,
    val approvedEvidenceHashes: List<String>
)

data class TransferTask(
    val taskId: String,
    val mediaId: String,
    val targetConceptId: String,
    val transferConceptId: String, // Novel transfer concept not covered in media directly
    val bloomLevel: String = "APPLICATION", // Must be APPLICATION or ANALYSIS
    val prompt: String,
    val scenarioContext: String,
    val solutionOptions: List<String>,
    val correctIndex: Int,
    val thresholdScore: Double = 0.70
)

data class MediaLearningRecord(
    val mediaId: String,
    val consumptionPercent: Double,
    val completedTransferTask: Boolean,
    val transferScore: Double,
    val masteryUpdated: Boolean
)
