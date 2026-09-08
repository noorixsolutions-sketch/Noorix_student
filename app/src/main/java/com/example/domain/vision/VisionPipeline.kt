package com.example.domain.vision

import com.example.core.model.Step
import java.util.UUID

data class ParsedRegion(
    val regionId: String,
    val lineText: String,
    val confidence: Double,
    val isUnclear: Boolean, // Confidence < 0.70 flagged UNCLEAR, never auto-corrected
    val detectedMathExpression: String? = null
)

data class NotebookParseResult(
    val captureId: String,
    val timestampMs: Long,
    val pageType: String = "NOTEBOOK_PAGE",
    val overallConfidence: Double,
    val regions: List<ParsedRegion>,
    val extractedSteps: List<Step>,
    val rawTrace: String
)

object VisionPipeline {

    const val CONFIDENCE_FLOOR = 0.70

    /**
     * Simulates scanning and OCR parsing of handwritten notebook work.
     * Enforces the invariant: confidence < 0.70 is flagged "UNCLEAR" and never hallucinated or auto-corrected.
     */
    fun parseNotebookPage(
        inputLines: List<Pair<String, Double>>,
        captureType: String = "NOTEBOOK_PAGE"
    ): NotebookParseResult {
        val captureId = "ocr-" + UUID.randomUUID().toString().take(6)
        val regions = mutableListOf<ParsedRegion>()
        val extractedSteps = mutableListOf<Step>()
        var stepIndex = 1

        for ((line, conf) in inputLines) {
            val isUnclear = conf < CONFIDENCE_FLOOR
            val displayText = if (isUnclear) "[UNCLEAR: Confidence ${String.format("%.2f", conf)} < $CONFIDENCE_FLOOR]" else line

            regions.add(
                ParsedRegion(
                    regionId = "reg-" + UUID.randomUUID().toString().take(4),
                    lineText = displayText,
                    confidence = conf,
                    isUnclear = isUnclear,
                    detectedMathExpression = if (!isUnclear) line else null
                )
            )

            if (!isUnclear && containsMathSymbols(line)) {
                extractedSteps.add(
                    Step(
                        stepIndex = stepIndex++,
                        expression = line.trim(),
                        note = "OCR Confidence: ${String.format("%.2f", conf)}"
                    )
                )
            }
        }

        val avgConf = if (inputLines.isNotEmpty()) inputLines.map { it.second }.average() else 0.0

        return NotebookParseResult(
            captureId = captureId,
            timestampMs = System.currentTimeMillis(),
            overallConfidence = String.format("%.2f", avgConf).toDouble(),
            regions = regions,
            extractedSteps = extractedSteps,
            rawTrace = "Document Detector -> Perspective Correction -> OCR Engine. Filtered ${regions.count { it.isUnclear }} unclear lines."
        )
    }

    private fun containsMathSymbols(text: String): Boolean {
        return text.contains("=") || text.contains("+") || text.contains("-") || text.contains("x") || text.contains("²")
    }
}
