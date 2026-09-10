package com.example.domain.projection

import com.example.core.model.AcademicState
import com.example.core.model.BehaviourState
import com.example.core.model.CapabilityState
import com.example.core.model.EventType
import com.example.core.model.LearningEvent
import com.example.core.model.MasteryStatus
import com.example.core.model.RecalibrationAttribution
import com.example.core.model.RecalibrationRecord
import com.example.core.model.StudentModel
import com.example.core.model.StudentProfileCalibration
import com.example.domain.diagnostic.AdaptiveDiagnosticEngine
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Projection Engine: Reconstructs student state purely from immutable event sequence.
 * Enforces multi-factor baseline calibration, temporal decay f(evidence, time, decay),
 * and recalibration history tracking.
 */
object ProjectionEngine {

    const val MS_PER_DAY = 86_400_000.0

    /**
     * Reconstruct complete StudentModel from the full ordered event log.
     * Replaying from t=0 yields the exact same deterministic state.
     */
    fun projectStudentState(
        events: List<LearningEvent>,
        currentSimulatedWallTimeMs: Long = System.currentTimeMillis()
    ): StudentModel {
        var studentId = "student-101"
        var studentName = "Cambridge Candidate"
        var activeCourse = "course:mathematics-4024"
        var courseVersion = "course-version:mathematics-4024:2025-2027"
        var lastHlc = "0:0:origin"
        var profileCalibration = StudentProfileCalibration()

        val academicMap = mutableMapOf<String, AcademicState>()
        var capability = CapabilityState()
        var behaviour = BehaviourState()
        val misconceptions = mutableListOf<String>()
        val recalibrationHistory = mutableListOf<RecalibrationRecord>()

        // Sort events causally
        val sortedEvents = events.sortedWith(compareBy({ it.physicalMs }, { it.hlcTimestamp }))

        for (event in sortedEvents) {
            lastHlc = event.hlcTimestamp
            studentId = event.studentId
            courseVersion = event.courseVersionId

            when (event.eventType) {
                EventType.STUDENT_ONBOARDED -> {
                    try {
                        val json = JSONObject(event.payloadJson)
                        studentName = json.optString("studentName", studentName)
                        activeCourse = json.optString("courseId", activeCourse)
                        val grade = json.optString("candidateGrade", profileCalibration.studentGrade)
                        val age = json.optInt("candidateAge", profileCalibration.studentAge)
                        val targetGrade = json.optString("targetGrade", profileCalibration.targetGrade)
                        val targetSession = json.optString("targetExamSession", profileCalibration.targetExamSession)
                        val dailyMinutes = json.optInt("dailyTargetMinutes", profileCalibration.dailyTargetMinutes)

                        var psyMap: Map<String, Float>? = null
                        if (json.has("psychometricSignals")) {
                            val psyObj = json.getJSONObject("psychometricSignals")
                            val m = mutableMapOf<String, Float>()
                            val keys = psyObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                m[k] = psyObj.getDouble(k).toFloat()
                            }
                            psyMap = m
                        }

                        var cogMap: Map<String, Float>? = null
                        if (json.has("cognitiveSignals")) {
                            val cogObj = json.getJSONObject("cognitiveSignals")
                            val m = mutableMapOf<String, Float>()
                            val keys = cogObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                m[k] = cogObj.getDouble(k).toFloat()
                            }
                            cogMap = m
                        }

                        profileCalibration = AdaptiveDiagnosticEngine.computeMultiFactorPrior(
                            grade = grade,
                            age = age,
                            targetGrade = targetGrade,
                            cognitiveSignals = cogMap,
                            psychometricSignals = psyMap
                        ).copy(
                            targetExamSession = targetSession,
                            dailyTargetMinutes = dailyMinutes
                        )

                        if (psyMap != null) {
                            behaviour = behaviour.copy(
                                persistence = psyMap["persistence"]?.toDouble() ?: behaviour.persistence,
                                confidence = psyMap["confidence"]?.toDouble() ?: behaviour.confidence,
                                frustrationResponse = psyMap["frustration_response"]?.toDouble() ?: behaviour.frustrationResponse,
                                completionConsistency = psyMap["metacognition"]?.toDouble() ?: behaviour.completionConsistency
                            )
                        }

                        if (cogMap != null) {
                            capability = capability.copy(
                                workingMemory = cogMap["working_memory"]?.toDouble() ?: capability.workingMemory,
                                reasoning = cogMap["logical_reasoning"]?.toDouble() ?: capability.reasoning,
                                spatialReasoning = cogMap["spatial_reasoning"]?.toDouble() ?: capability.spatialReasoning,
                                processingEfficiency = cogMap["pattern_reasoning"]?.toDouble() ?: capability.processingEfficiency
                            )
                        }
                    } catch (_: Exception) {}
                }

                EventType.COURSE_PACKAGE_INSTALLED -> {
                    try {
                        val json = JSONObject(event.payloadJson)
                        activeCourse = json.optString("courseId", activeCourse)
                    } catch (_: Exception) {}
                }

                EventType.CONCEPT_ASSESSED,
                EventType.MASTERY_UPDATED,
                EventType.DIAGNOSTIC_ITEM_SUBMITTED -> {
                    try {
                        val json = JSONObject(event.payloadJson)
                        val conceptId = json.optString("conceptId")
                        val conceptName = json.optString("conceptName", conceptId)
                        val score = json.optDouble("score", 0.5) // 0.0 to 1.0
                        val lambda = json.optDouble("lambda", 0.05)
                        val evidenceStrength = json.optDouble("evidenceStrength", 0.8)

                        if (conceptId.isNotEmpty()) {
                            val existing = academicMap[conceptId] ?: AcademicState(
                                conceptId = conceptId,
                                conceptName = conceptName,
                                difficultyLambda = lambda
                            )

                            val newEvidenceCount = existing.evidenceCount + 1
                            // Bayesian update for base mastery
                            val weight = min(0.9, 0.3 + (newEvidenceCount * 0.1) * evidenceStrength)
                            val newBase = (existing.baseMastery * (1.0 - weight)) + (score * weight)
                            val newConfidence = min(0.98, existing.confidence + 0.08)

                            val updated = existing.copy(
                                conceptName = conceptName.ifEmpty { existing.conceptName },
                                baseMastery = String.format("%.3f", newBase).toDouble(),
                                confidence = String.format("%.2f", newConfidence).toDouble(),
                                evidenceCount = newEvidenceCount,
                                lastAssessedPhysicalMs = event.physicalMs,
                                difficultyLambda = lambda
                            )
                            academicMap[conceptId] = updated
                        }
                    } catch (_: Exception) {}
                }

                EventType.MISCONCEPTION_DETECTED -> {
                    try {
                        val json = JSONObject(event.payloadJson)
                        val miscId = json.optString("misconceptionId")
                        if (miscId.isNotEmpty() && !misconceptions.contains(miscId)) {
                            misconceptions.add(miscId)
                        }
                    } catch (_: Exception) {}
                }

                EventType.SYMBOLIC_VERIFICATION_COMPLETED -> {
                    try {
                        val json = JSONObject(event.payloadJson)
                        val isValid = json.optBoolean("isValid", false)
                        val conceptId = json.optString("conceptId")
                        if (conceptId.isNotEmpty() && !isValid) {
                            val errorType = json.optString("errorType")
                            if (errorType == "SIGN_ERROR" && !misconceptions.contains("misconception:factorisation:sign-error")) {
                                misconceptions.add("misconception:factorisation:sign-error")
                            }
                        }
                    } catch (_: Exception) {}
                }

                EventType.TRANSFER_TASK_EVALUATED -> {
                    try {
                        val json = JSONObject(event.payloadJson)
                        val passed = json.optBoolean("passed", false)
                        val conceptId = json.optString("conceptId")
                        if (conceptId.isNotEmpty() && passed) {
                            val existing = academicMap[conceptId]
                            if (existing != null) {
                                val boostedBase = min(1.0, existing.baseMastery + 0.15)
                                academicMap[conceptId] = existing.copy(
                                    baseMastery = String.format("%.3f", boostedBase).toDouble(),
                                    evidenceCount = existing.evidenceCount + 1,
                                    lastAssessedPhysicalMs = event.physicalMs
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

                EventType.PLAN_DEVIATION_DETECTED,
                EventType.PLAN_REVISED -> {
                    try {
                        val json = JSONObject(event.payloadJson)
                        val recalId = json.optString("recalibrationId", "recal-" + event.eventId.take(6))
                        val trigger = json.optString("triggerSource", "POST_STUDY_QUIZ")
                        val conceptId = json.optString("conceptId", "concept:4024:factorisation")
                        val prevIntervention = json.optString("previousIntervention", "RETRIEVAL_PRACTICE")
                        val newIntervention = json.optString("newIntervention", json.optString("interventionType", "WORKED_EXAMPLES"))
                        val attrStr = json.optString("attribution", json.optString("classification", "ON_TRACK"))
                        val attribution = runCatching { RecalibrationAttribution.valueOf(attrStr) }.getOrDefault(RecalibrationAttribution.ON_TRACK)
                        val obsGain = json.optDouble("observedMasteryGain", json.optDouble("deltaGain", 0.0))
                        val expGain = json.optDouble("expectedMasteryGain", 0.18)
                        val obsAcc = json.optDouble("observedAccuracy", 0.75)
                        val expAcc = json.optDouble("expectedAccuracy", 0.80)
                        val note = json.optString("diagnosisNote", "")
                        val action = json.optString("actionTaken", "Plan updated")

                        recalibrationHistory.add(
                            RecalibrationRecord(
                                recalibrationId = recalId,
                                timestampMs = event.physicalMs,
                                triggerSource = trigger,
                                conceptId = conceptId,
                                previousIntervention = prevIntervention,
                                newIntervention = newIntervention,
                                attribution = attribution,
                                observedMasteryGain = obsGain,
                                expectedMasteryGain = expGain,
                                observedAccuracy = obsAcc,
                                expectedAccuracy = expAcc,
                                diagnosisNote = note,
                                actionTaken = action
                            )
                        )
                    } catch (_: Exception) {}
                }

                else -> {}
            }
        }

        // Apply Temporal Decay to calculate Effective Mastery across all concepts
        val decayedAcademicStates = academicMap.mapValues { (_, state) ->
            calculateDecayedState(state, currentSimulatedWallTimeMs)
        }

        return StudentModel(
            studentId = studentId,
            studentName = studentName,
            activeCourseId = activeCourse,
            courseVersionId = courseVersion,
            academicStates = decayedAcademicStates,
            capability = capability,
            behaviour = behaviour,
            activeMisconceptions = misconceptions,
            lastUpdatedHlc = lastHlc,
            profileCalibration = profileCalibration,
            recalibrationHistory = recalibrationHistory
        )
    }

    /**
     * Frozen Invariant: effective_mastery(t) = base_mastery * exp(-λ * (t - t_last))
     */
    fun calculateDecayedState(
        state: AcademicState,
        currentSimulatedWallTimeMs: Long
    ): AcademicState {
        if (state.lastAssessedPhysicalMs == 0L || state.baseMastery <= 0.0) {
            return state.copy(
                effectiveMastery = state.baseMastery,
                status = classifyMastery(state.baseMastery, isRegressed = false)
            )
        }

        val elapsedMs = max(0L, currentSimulatedWallTimeMs - state.lastAssessedPhysicalMs)
        val elapsedDays = elapsedMs / MS_PER_DAY
        val decayFactor = exp(-state.difficultyLambda * elapsedDays)
        val effective = state.baseMastery * decayFactor

        // Frozen spec rule: Re-assessment triggered when effective < 0.6 ∧ base > 0.7
        val isRegressed = (state.baseMastery > 0.70 && effective < 0.60)
        val status = if (isRegressed) {
            MasteryStatus.REGRESSED
        } else {
            classifyMastery(effective, isRegressed = false)
        }

        return state.copy(
            effectiveMastery = String.format("%.3f", effective).toDouble(),
            status = status
        )
    }

    private fun classifyMastery(score: Double, isRegressed: Boolean): MasteryStatus {
        if (isRegressed) return MasteryStatus.REGRESSED
        return when {
            score >= 0.85 -> MasteryStatus.MASTERED
            score >= 0.65 -> MasteryStatus.DEVELOPING
            score >= 0.40 -> MasteryStatus.EMERGING
            score > 0.0 -> MasteryStatus.UNCERTAIN
            else -> MasteryStatus.UNKNOWN
        }
    }
}
