package com.example.domain.vision

import android.graphics.Bitmap
import com.example.core.model.Step
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
     * Real OCR pipeline for student notebook scanning.
     * Pass 1: On-device ML Kit Text Recognition v2 (Fast, free, zero API cost).
     * Pass 2 (Conditional): Gemini Vision via Firebase AI for ambiguous or low-confidence math handwriting.
     *
     * Invariant: Confidence < 0.70 is strictly flagged as "UNCLEAR" and never hallucinated or auto-corrected.
     */
    suspend fun parseNotebookBitmap(bitmap: Bitmap): NotebookParseResult = withContext(Dispatchers.IO) {
        val captureId = "ocr-" + UUID.randomUUID().toString().take(6)
        val extractedLines = mutableListOf<Pair<String, Double>>()
        var usedSecondaryGeminiPass = false

        // =========================================================================
        // PASS 1: On-Device ML Kit Text Recognition v2
        // =========================================================================
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText: Text = Tasks.await(recognizer.process(image))

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val lineText = line.text.trim()
                    if (lineText.isEmpty()) continue

                    // ML Kit line confidence or fallback heuristic
                    val mlKitConf = line.confidence?.toDouble()
                    val confidence = when {
                        mlKitConf != null -> mlKitConf
                        // Fallback: estimate confidence based on recognition character bounds & alphanumeric ratio
                        line.elements.isNotEmpty() -> {
                            val elementConfs = line.elements.mapNotNull { it.confidence }
                            if (elementConfs.isNotEmpty()) elementConfs.average()
                            else estimateConfidenceFromTextQuality(lineText)
                        }
                        else -> estimateConfidenceFromTextQuality(lineText)
                    }

                    extractedLines.add(lineText to confidence)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("VisionPipeline", "ML Kit Pass 1 error: ${e.message}")
        }

        // =========================================================================
        // PASS 2: Gemini Vision Secondary Pass (if ML Kit confidence is low or handwriting unclear)
        // =========================================================================
        val avgConfidence = if (extractedLines.isNotEmpty()) extractedLines.map { it.second }.average() else 0.0
        val needsGeminiPass = extractedLines.isEmpty() || avgConfidence < CONFIDENCE_FLOOR || extractedLines.any { it.second < CONFIDENCE_FLOOR && containsMathSymbols(it.first) }

        if (needsGeminiPass) {
            try {
                val geminiModel = Firebase.ai.generativeModel(
                    modelName = "gemini-2.5-flash",
                    systemInstruction = content {
                        text(
                            "You are a specialized mathematical vision model for Cambridge O-Level notebooks. " +
                            "Transcribe handwritten algebra equations line by line. " +
                            "Format each line strictly as: LINE: <mathematical expression> | CONF: <0.00 to 1.00>. " +
                            "If handwriting is genuinely illegible, output: LINE: [UNCLEAR] | CONF: 0.30."
                        )
                    }
                )

                val promptContent = content {
                    image(bitmap)
                    text("Transcribe the student's handwritten math notebook steps with confidence scores.")
                }

                val response = geminiModel.generateContent(promptContent)
                val geminiText = response.text ?: ""

                val geminiLines = parseGeminiVisionLines(geminiText)
                if (geminiLines.isNotEmpty()) {
                    usedSecondaryGeminiPass = true
                    extractedLines.clear()
                    extractedLines.addAll(geminiLines)
                }
            } catch (e: Exception) {
                android.util.Log.w("VisionPipeline", "Gemini Vision Pass 2 fallback: ${e.message}")
            }
        }

        // Build structured NotebookParseResult enforcing CONFIDENCE_FLOOR invariant
        val regions = mutableListOf<ParsedRegion>()
        val extractedSteps = mutableListOf<Step>()
        var stepIndex = 1

        for ((line, conf) in extractedLines) {
            val isUnclear = conf < CONFIDENCE_FLOOR
            val displayText = if (isUnclear) {
                "[UNCLEAR: Confidence ${String.format("%.2f", conf)} < $CONFIDENCE_FLOOR]"
            } else {
                line
            }

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
                        note = "OCR Conf: ${String.format("%.2f", conf)} (${if (usedSecondaryGeminiPass) "Gemini Vision" else "ML Kit v2"})"
                    )
                )
            }
        }

        val finalAvg = if (extractedLines.isNotEmpty()) extractedLines.map { it.second }.average() else 0.0

        val trace = buildString {
            append("ML Kit Text Recognition v2 (On-device)")
            if (usedSecondaryGeminiPass) {
                append(" -> Gemini Vision Pass 2 (Math notation enhancement)")
            }
            append(". Filtered ${regions.count { it.isUnclear }} unclear lines below floor ($CONFIDENCE_FLOOR).")
        }

        NotebookParseResult(
            captureId = captureId,
            timestampMs = System.currentTimeMillis(),
            overallConfidence = if (extractedLines.isNotEmpty()) String.format("%.2f", finalAvg).toDouble() else 0.0,
            regions = regions,
            extractedSteps = extractedSteps,
            rawTrace = trace
        )
    }

    /**
     * Backward-compatible parse method for pre-transcribed or test lines.
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
            rawTrace = "Pre-transcribed OCR Parser. Filtered ${regions.count { it.isUnclear }} unclear lines below floor ($CONFIDENCE_FLOOR)."
        )
    }

    private fun parseGeminiVisionLines(rawResponse: String): List<Pair<String, Double>> {
        val result = mutableListOf<Pair<String, Double>>()
        val lines = rawResponse.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val linePattern = Regex("LINE:\\s*(.*?)\\s*\\|\\s*CONF:\\s*([0-9.]+)", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val match = linePattern.find(line)
            if (match != null) {
                val text = match.groupValues[1].trim()
                val conf = match.groupValues[2].toDoubleOrNull() ?: 0.85
                result.add(text to conf)
            } else if (line.contains("=")) {
                result.add(line to 0.88)
            }
        }
        return result
    }

    private fun estimateConfidenceFromTextQuality(line: String): Double {
        var score = 0.85
        if (line.contains("?") || line.contains("")) score -= 0.35
        if (line.length < 2) score -= 0.20
        if (line.matches(Regex("^[a-zA-Z0-9+\\-*=/^() ]+$"))) score += 0.08
        return score.coerceIn(0.20, 0.98)
    }

    private fun containsMathSymbols(text: String): Boolean {
        return text.contains("=") || text.contains("+") || text.contains("-") || text.contains("x") || text.contains("²")
    }
}
