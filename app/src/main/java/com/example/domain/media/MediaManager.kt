package com.example.domain.media

import com.example.core.model.MediaArtifact
import com.example.core.model.MediaBrief
import com.example.core.model.MediaFormat
import com.example.core.model.TransferTask
import java.util.UUID

interface MediaProvider {
    suspend fun generatePodcast(brief: MediaBrief): MediaArtifact
    suspend fun generateExplainerVideo(brief: MediaBrief): MediaArtifact
}

class StandardMediaProvider : MediaProvider {

    override suspend fun generatePodcast(brief: MediaBrief): MediaArtifact {
        return MediaArtifact(
            mediaId = "media-" + UUID.randomUUID().toString().take(6),
            briefId = brief.briefId,
            title = "O-Level Mastery: Deep Dive into ${brief.conceptName}",
            format = MediaFormat.PODCAST,
            audioDurationSeconds = brief.targetDurationMinutes * 60,
            summaryText = "Curriculum-grounded audio walkthrough of algebraic structures, common factor extraction, and avoiding sign distribution traps.",
            scriptSegments = listOf(
                "Host 1: Welcome to O-Level Maths Core. Today we're exploring ${brief.conceptName}.",
                "Host 2: That's right! A common pitfall in Cambridge Paper 1 is distributing negative signs over parentheses.",
                "Host 1: Remember, factorisation is undoing the distributive law. Always inspect common factors first.",
                "Host 2: In the next transfer challenge, test your understanding on a composite geometric algebraic problem!"
            ),
            approvedEvidenceHashes = brief.approvedEvidenceIds.map { "hash-$it" }
        )
    }

    override suspend fun generateExplainerVideo(brief: MediaBrief): MediaArtifact {
        return MediaArtifact(
            mediaId = "video-" + UUID.randomUUID().toString().take(6),
            briefId = brief.briefId,
            title = "Visualizing ${brief.conceptName}",
            format = MediaFormat.EXPLAINER_VIDEO,
            audioDurationSeconds = brief.targetDurationMinutes * 60,
            summaryText = "Visual geometric area model representing factorisation and binomial expansion.",
            scriptSegments = listOf(
                "Visual frame 1: Area model of rectangle with sides x and (x+3).",
                "Visual frame 2: Dividing into sub-areas x^2 and 3x.",
                "Visual frame 3: Factoring out x yields x(x+3)."
            ),
            approvedEvidenceHashes = brief.approvedEvidenceIds.map { "hash-$it" }
        )
    }
}

object MediaTransferEngine {

    /**
     * Invariant: Transfer task MUST reference >= 1 concept not directly mentioned in media.
     * Bloom level MUST be APPLICATION or ANALYSIS. Recall questions are rejected.
     */
    fun createTransferTask(media: MediaArtifact, targetConceptId: String): TransferTask {
        return TransferTask(
            taskId = "transfer-" + UUID.randomUUID().toString().take(6),
            mediaId = media.mediaId,
            targetConceptId = targetConceptId,
            transferConceptId = "concept:4024:geometric-algebra", // Novel application concept
            bloomLevel = "APPLICATION", // Frozen Spec: Must be APPLICATION or ANALYSIS
            prompt = "A rectangular garden has an area modeled by 2x^2 + 8x. If a landscaper partitions a square flower bed of area x^2 from it, express the remaining area in factored form, identifying the common width.",
            scenarioContext = "Transfer Scenario: Applying factorisation to geometric area modeling and perimeter constraints.",
            solutionOptions = listOf(
                "x(x + 8)",
                "2x(x + 4)",
                "x(x + 4) + x^2",
                "(x + 2)(x + 4)"
            ),
            correctIndex = 0, // 2x^2 + 8x - x^2 = x^2 + 8x = x(x + 8)
            thresholdScore = 0.70
        )
    }

    /**
     * Invariant: Media consumption alone awards zero mastery.
     * Only passing the Transfer Task with score >= threshold awards mastery update!
     */
    fun evaluateTransferAttempt(
        task: TransferTask,
        selectedOptionIndex: Int,
        mediaConsumedPercent: Double
    ): Pair<Boolean, String> {
        val isTransferCorrect = selectedOptionIndex == task.correctIndex

        if (mediaConsumedPercent < 0.80) {
            return Pair(false, "Media was only partially consumed (${(mediaConsumedPercent * 100).toInt()}%). Transfer validation requires complete engagement.")
        }

        if (task.bloomLevel == "RECALL") {
            return Pair(false, "REJECTED BY POLICY GATE: Recall questions cannot award mastery under Frozen Architecture v2.0.")
        }

        return if (isTransferCorrect) {
            Pair(true, "TRANSFER VALIDATED: Student successfully applied factorisation to novel geometric transfer scenario. Mastery credit approved.")
        } else {
            Pair(false, "TRANSFER FAILED: Selected incorrect model. Media consumption alone does NOT update mastery. Additional practice required.")
        }
    }
}
