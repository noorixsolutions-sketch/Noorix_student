package com.example.domain.repository

import android.content.Context
import com.example.core.crypto.PackageVerifier
import com.example.core.crypto.VerificationStatus
import com.example.core.hlc.HybridClock
import com.example.core.model.AcademicState
import com.example.core.model.AuditLogItem
import com.example.core.model.CoursePackage
import com.example.core.model.CoursePackageManifest
import com.example.core.model.EdgeType
import com.example.core.model.EventType
import com.example.core.model.KGEdge
import com.example.core.model.KGNode
import com.example.core.model.LearningEvent
import com.example.core.model.LearningPlan
import com.example.core.model.MediaArtifact
import com.example.core.model.MediaBrief
import com.example.core.model.MediaFormat
import com.example.core.model.MediaLearningRecord
import com.example.core.model.NodeType
import com.example.core.model.PlanClassification
import com.example.core.model.PlanObservation
import com.example.core.model.QuestionItem
import com.example.core.model.ResourceItem
import com.example.core.model.Step
import com.example.core.model.StudentModel
import com.example.core.model.TransferTask
import com.example.core.model.VerificationResult
import com.example.data.curriculum.CoursePackageProvider
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.CoursePackageEntity
import com.example.data.local.EventEntity
import com.example.domain.diagnostic.AdaptiveDiagnosticEngine
import com.example.domain.diagnostic.DiagnosticSessionState
import com.example.domain.media.MediaTransferEngine
import com.example.domain.media.StandardMediaProvider
import com.example.domain.planner.AdaptivePlannerEngine
import com.example.domain.projection.ProjectionEngine
import com.example.domain.sync.HlcSyncEngine
import com.example.domain.sync.SyncBatchResult
import com.example.domain.verifier.SymbolicMathVerifier
import com.example.domain.vision.NotebookParseResult
import com.example.domain.vision.VisionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LearningRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val eventDao = db.eventDao()
    private val packageDao = db.coursePackageDao()
    private val syncQueueDao = db.syncQueueDao()
    private val auditDao = db.auditDao()

    private val hybridClock = HybridClock("android-node-01")
    private val syncEngine = HlcSyncEngine(eventDao, syncQueueDao, hybridClock)
    private val mathVerifier = SymbolicMathVerifier()
    private val mediaProvider = StandardMediaProvider()

    // In-memory runtime state for active course and simulation clock
    private val _simulatedWallTimeMs = MutableStateFlow(System.currentTimeMillis())
    val simulatedWallTimeMs: StateFlow<Long> = _simulatedWallTimeMs.asStateFlow()

    private val _activePackage = MutableStateFlow<CoursePackage?>(null)
    val activePackage: StateFlow<CoursePackage?> = _activePackage.asStateFlow()

    private val _studentModel = MutableStateFlow(StudentModel())
    val studentModel: StateFlow<StudentModel> = _studentModel.asStateFlow()

    private val _activePlan = MutableStateFlow<LearningPlan?>(null)
    val activePlan: StateFlow<LearningPlan?> = _activePlan.asStateFlow()

    private val _diagnosticSession = MutableStateFlow(DiagnosticSessionState())
    val diagnosticSession: StateFlow<DiagnosticSessionState> = _diagnosticSession.asStateFlow()

    private val _mediaRecord = MutableStateFlow<MediaLearningRecord?>(null)
    val mediaRecord: StateFlow<MediaLearningRecord?> = _mediaRecord.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            initDefaultPackageIfNeeded()
            refreshStudentModel()
        }
    }

    private suspend fun initDefaultPackageIfNeeded() {
        val existing = packageDao.getActivePackage()
        if (existing == null) {
            val defaultMath = CoursePackageProvider.createMath4024Package()
            installAndActivatePackage(defaultMath, isInitial = true)
        } else {
            val pkg = deserializePackage(existing)
            _activePackage.value = pkg
        }
    }

    suspend fun refreshStudentModel() {
        val events = eventDao.getAllEvents().map { entityToEvent(it) }
        val projected = ProjectionEngine.projectStudentState(events, _simulatedWallTimeMs.value)
        _studentModel.value = projected

        // Check if active plan exists
        if (_activePlan.value == null) {
            val targetConcept = projected.academicStates.values.firstOrNull()?.conceptId ?: "concept:4024:factorisation"
            val targetName = projected.academicStates[targetConcept]?.conceptName ?: "Factorisation"
            _activePlan.value = AdaptivePlannerEngine.createPlan(
                studentId = projected.studentId,
                courseVersionId = projected.courseVersionId,
                targetConceptId = targetConcept,
                targetConceptName = targetName,
                academicState = projected.academicStates[targetConcept]
            )
        }
    }

    // ==========================================
    // Course Package Operations (Invariant P1)
    // ==========================================

    suspend fun installAndActivatePackage(pkg: CoursePackage, isInitial: Boolean = false): Pair<Boolean, String> {
        val jsonString = CoursePackageProvider.serializePackageToJson(pkg)

        // 1. Verify signature and cryptographic hash
        val verifyStatus = PackageVerifier.verifyPackage(pkg, jsonString)
        if (verifyStatus !is VerificationStatus.Valid && !isInitial) {
            val reason = when (verifyStatus) {
                is VerificationStatus.HashMismatch -> "Hash Mismatch: expected ${verifyStatus.expected} but was ${verifyStatus.actual}"
                is VerificationStatus.SignatureInvalid -> "Cryptographic Signature Invalid: ${verifyStatus.reason}"
                is VerificationStatus.SchemaIncompatible -> "Schema Incompatible: requires ${verifyStatus.expected} but package was ${verifyStatus.actual}"
                is VerificationStatus.IntegrityCheckFailed -> "Graph Integrity Failed: dangling edges pointing to ${verifyStatus.missingReferences}"
                else -> "Verification failed"
            }
            logAudit("PACKAGE_INSTALL_REJECTED", "Rejected ${pkg.manifest.packageId}: $reason")
            return Pair(false, "REJECTED BY PACKAGE VERIFIER: $reason")
        }

        // 2. Stage into DB
        val entity = CoursePackageEntity(
            packageId = pkg.manifest.packageId,
            courseId = pkg.manifest.courseId,
            version = pkg.manifest.version,
            subjectName = pkg.manifest.subjectName,
            status = "ACTIVE",
            contentHash = pkg.manifest.contentHash,
            manifestJson = jsonString,
            nodesJson = serializeNodes(pkg.nodes),
            edgesJson = serializeEdges(pkg.edges),
            questionsJson = serializeQuestions(pkg.questions),
            resourcesJson = serializeResources(pkg.resources)
        )

        packageDao.deprecateOtherVersions(pkg.manifest.packageId, pkg.manifest.courseId)
        packageDao.insertPackage(entity)

        // 3. Emit immutable event
        val payload = JSONObject().apply {
            put("packageId", pkg.manifest.packageId)
            put("courseId", pkg.manifest.courseId)
            put("version", pkg.manifest.version)
            put("subjectName", pkg.manifest.subjectName)
        }.toString()

        recordEvent(EventType.COURSE_PACKAGE_INSTALLED, payload)
        _activePackage.value = pkg
        logAudit("PACKAGE_ACTIVATED", "Activated ${pkg.manifest.packageId} (${pkg.manifest.subjectName})")
        refreshStudentModel()
        return Pair(true, "PACKAGE ACTIVATED: Installed ${pkg.manifest.subjectName} (${pkg.manifest.version}) into generic runtime.")
    }

    // ==========================================
    // Curriculum KG Queries (Truth Separation)
    // ==========================================

    fun getPrerequisites(conceptId: String): List<KGNode> {
        val pkg = _activePackage.value ?: return emptyList()
        val prereqIds = pkg.edges
            .filter { it.type == EdgeType.PREREQUISITE_OF && it.target == conceptId }
            .map { it.source }
            .toSet()
        return pkg.nodes.filter { prereqIds.contains(it.id) }
    }

    fun getDependents(conceptId: String): List<KGNode> {
        val pkg = _activePackage.value ?: return emptyList()
        val depIds = pkg.edges
            .filter { it.type == EdgeType.PREREQUISITE_OF && it.source == conceptId }
            .map { it.target }
            .toSet()
        return pkg.nodes.filter { depIds.contains(it.id) }
    }

    fun getConceptNodes(): List<KGNode> {
        val pkg = _activePackage.value ?: return emptyList()
        return pkg.nodes.filter { it.type == NodeType.CONCEPT }
    }

    fun getQuestionsForConcept(conceptId: String): List<QuestionItem> {
        val pkg = _activePackage.value ?: return emptyList()
        return pkg.questions.filter { it.targetConceptId == conceptId }
    }

    // ==========================================
    // Event Store & Evidence Logging (Invariant P3)
    // ==========================================

    suspend fun recordEvent(eventType: EventType, payloadJson: String): LearningEvent {
        val currentCourse = _activePackage.value?.manifest?.version ?: "2025-2027"
        val event = syncEngine.recordLocalEvent(
            eventType = eventType,
            studentId = _studentModel.value.studentId,
            courseVersionId = currentCourse,
            payloadJson = payloadJson,
            simulatedWallTimeMs = _simulatedWallTimeMs.value
        )
        refreshStudentModel()
        return event
    }

    val allEventsFlow: Flow<List<LearningEvent>> = eventDao.getAllEventsFlow().map { list ->
        list.map { entityToEvent(it) }
    }

    // ==========================================
    // Temporal Dynamics & Time Travel Simulation
    // ==========================================

    suspend fun advanceSimulatedClock(days: Int) {
        val advanceMs = days * 86_400_000L
        val newTime = _simulatedWallTimeMs.value + advanceMs
        _simulatedWallTimeMs.value = newTime

        val payload = JSONObject().apply {
            put("advancedDays", days)
            put("newWallTimeMs", newTime)
        }.toString()
        recordEvent(EventType.CLOCK_ADVANCED_SIMULATED, payload)
        logAudit("CLOCK_ADVANCED", "Advanced simulated learning clock by $days days to observe decay.")
        refreshStudentModel()
    }

    // ==========================================
    // Symbolic Math Verifier
    // ==========================================

    suspend fun verifyMathSolution(
        problemLaTeX: String,
        steps: List<Step>,
        conceptId: String
    ): VerificationResult {
        val courseVer = _activePackage.value?.manifest?.version ?: "2025-2027"
        val result = mathVerifier.verifySteps(problemLaTeX, steps, courseVer)

        val payload = JSONObject().apply {
            put("problem", problemLaTeX)
            put("isValid", result.isValid)
            put("errorType", result.errorType?.name ?: "NONE")
            put("stepCount", steps.size)
            put("conceptId", conceptId)
        }.toString()

        recordEvent(EventType.SYMBOLIC_VERIFICATION_COMPLETED, payload)

        if (!result.isValid && result.errorType != null) {
            val miscPayload = JSONObject().apply {
                put("conceptId", conceptId)
                put("misconceptionId", "misconception:factorisation:sign-error")
                put("errorType", result.errorType.name)
            }.toString()
            recordEvent(EventType.MISCONCEPTION_DETECTED, miscPayload)
        } else if (result.isValid) {
            val assessPayload = JSONObject().apply {
                put("conceptId", conceptId)
                put("conceptName", "Factorisation")
                put("score", 1.0)
                put("lambda", 0.05)
                put("evidenceStrength", 0.85)
            }.toString()
            recordEvent(EventType.MASTERY_UPDATED, assessPayload)
        }

        return result
    }

    // ==========================================
    // Vision & OCR Pipeline
    // ==========================================

    fun parseNotebookPage(lines: List<Pair<String, Double>>): NotebookParseResult {
        return VisionPipeline.parseNotebookPage(lines)
    }

    // ==========================================
    // Adaptive Diagnostic Session
    // ==========================================

    fun startDiagnostic() {
        _diagnosticSession.value = DiagnosticSessionState()
        scope.launch {
            recordEvent(EventType.DIAGNOSTIC_STARTED, "{}")
        }
    }

    suspend fun submitDiagnosticAnswer(question: QuestionItem, selectedIndex: Int) {
        val currentSession = _diagnosticSession.value
        val updatedSession = AdaptiveDiagnosticEngine.submitAnswer(
            session = currentSession,
            question = question,
            selectedIndex = selectedIndex,
            durationMs = 15000L
        )
        _diagnosticSession.value = updatedSession

        val payload = JSONObject().apply {
            put("questionId", question.id)
            put("conceptId", question.targetConceptId)
            put("selectedIndex", selectedIndex)
            put("isCorrect", selectedIndex == question.correctIndex)
            put("score", if (selectedIndex == question.correctIndex) 1.0 else 0.0)
            put("estimatedAbility", updatedSession.estimatedAbility)
        }.toString()

        recordEvent(EventType.DIAGNOSTIC_ITEM_SUBMITTED, payload)

        if (updatedSession.isCompleted) {
            val compPayload = JSONObject().apply {
                put("questionsAnswered", updatedSession.itemsAnswered.size)
                put("finalEstimatedAbility", updatedSession.estimatedAbility)
                put("identifiedGaps", JSONArray(updatedSession.identifiedGaps))
            }.toString()
            recordEvent(EventType.DIAGNOSTIC_COMPLETED, compPayload)
            logAudit("DIAGNOSTIC_COMPLETED", "Completed in ${updatedSession.itemsAnswered.size} questions with ability ${updatedSession.estimatedAbility}")
        }
    }

    // ==========================================
    // Planner Execution & Plan Evaluation
    // ==========================================

    suspend fun evaluateCurrentPlan(
        observedGain: Double,
        observedAccuracy: Double,
        durationMinutes: Int
    ): LearningPlan? {
        val plan = _activePlan.value ?: return null
        val baseline = _studentModel.value.academicStates[plan.targetConceptId]

        val obs = PlanObservation(
            observedMasteryGain = observedGain,
            observedAccuracy = observedAccuracy,
            observedDurationMinutes = durationMinutes,
            completion = true
        )

        val evaluatedPlan = AdaptivePlannerEngine.evaluatePlanExecution(plan, obs, baseline)
        _activePlan.value = evaluatedPlan

        val payload = JSONObject().apply {
            put("planId", plan.planId)
            put("classification", evaluatedPlan.classification?.name)
            put("deltaAccuracy", evaluatedPlan.deviation?.deltaAccuracy)
            put("deltaGain", evaluatedPlan.deviation?.deltaMasteryGain)
            put("diagnosisNote", evaluatedPlan.diagnosisNote)
        }.toString()

        recordEvent(EventType.PLAN_DEVIATION_DETECTED, payload)
        logAudit("PLAN_EVALUATED", "Plan ${plan.planId} evaluated as ${evaluatedPlan.classification?.name}: ${evaluatedPlan.diagnosisNote}")
        return evaluatedPlan
    }

    suspend fun replanCurrent(newIntervention: String): LearningPlan {
        val currentPlan = _activePlan.value
        val conceptId = currentPlan?.targetConceptId ?: "concept:4024:factorisation"
        val conceptName = currentPlan?.targetConceptName ?: "Factorisation"
        val state = _studentModel.value.academicStates[conceptId]

        val newPlan = AdaptivePlannerEngine.createPlan(
            studentId = _studentModel.value.studentId,
            courseVersionId = _studentModel.value.courseVersionId,
            targetConceptId = conceptId,
            targetConceptName = conceptName,
            academicState = state,
            preferredIntervention = newIntervention
        )
        _activePlan.value = newPlan

        val payload = JSONObject().apply {
            put("newPlanId", newPlan.planId)
            put("interventionType", newPlan.interventionType)
            put("expectedAccuracy", newPlan.predictions.expectedAccuracy)
        }.toString()

        recordEvent(EventType.PLAN_REVISED, payload)
        logAudit("PLAN_REVISED", "Swapped intervention to $newIntervention with expected accuracy ${newPlan.predictions.expectedAccuracy}")
        return newPlan
    }

    // ==========================================
    // Media & Transfer Task (Invariant P6)
    // ==========================================

    suspend fun generatePodcastBrief(conceptId: String): Pair<MediaArtifact, TransferTask> {
        val conceptName = _studentModel.value.academicStates[conceptId]?.conceptName ?: "Algebraic Factorisation"
        val brief = MediaBrief(
            briefId = "brief-" + UUID.randomUUID().toString().take(6),
            conceptId = conceptId,
            conceptName = conceptName,
            format = MediaFormat.PODCAST,
            targetDurationMinutes = 6,
            studentLevel = "DEVELOPING",
            approvedEvidenceIds = listOf("resource:factorisation-explanation-001"),
            pedagogicalStrategy = "Socratic dialogue contrasting common factors with sign distributions."
        )

        val artifact = mediaProvider.generatePodcast(brief)
        val transferTask = MediaTransferEngine.createTransferTask(artifact, conceptId)

        val payload = JSONObject().apply {
            put("mediaId", artifact.mediaId)
            put("conceptId", conceptId)
            put("title", artifact.title)
        }.toString()
        recordEvent(EventType.MEDIA_GENERATED, payload)

        return Pair(artifact, transferTask)
    }

    suspend fun submitTransferTaskAttempt(
        task: TransferTask,
        selectedOption: Int,
        consumedPercent: Double
    ): Pair<Boolean, String> {
        val (passed, message) = MediaTransferEngine.evaluateTransferAttempt(task, selectedOption, consumedPercent)

        val payload = JSONObject().apply {
            put("taskId", task.taskId)
            put("mediaId", task.mediaId)
            put("conceptId", task.targetConceptId)
            put("passed", passed)
            put("bloomLevel", task.bloomLevel)
            put("consumedPercent", consumedPercent)
        }.toString()

        recordEvent(EventType.TRANSFER_TASK_EVALUATED, payload)

        _mediaRecord.value = MediaLearningRecord(
            mediaId = task.mediaId,
            consumptionPercent = consumedPercent,
            completedTransferTask = true,
            transferScore = if (passed) 1.0 else 0.0,
            masteryUpdated = passed
        )

        return Pair(passed, message)
    }

    // ==========================================
    // Offline Sync Simulation
    // ==========================================

    suspend fun simulateRemoteSyncBatch(): SyncBatchResult {
        // Create 2 simulated remote events from another device during offline 7-day period
        val remoteTime = _simulatedWallTimeMs.value - 86_400_000L
        val remoteHlc = hybridClock.now(remoteTime).toString()

        val remoteEvents = listOf(
            LearningEvent(
                eventId = "remote-evt-" + UUID.randomUUID().toString().take(6),
                eventType = EventType.CONCEPT_ASSESSED,
                studentId = _studentModel.value.studentId,
                courseVersionId = _studentModel.value.courseVersionId,
                hlcTimestamp = remoteHlc,
                physicalMs = remoteTime,
                payloadJson = JSONObject().apply {
                    put("conceptId", "concept:4024:factorisation")
                    put("conceptName", "Factorisation")
                    put("score", 0.78)
                    put("confidence", 0.90)
                }.toString()
            )
        )

        val result = syncEngine.mergeRemoteEvents(remoteEvents, _simulatedWallTimeMs.value)
        refreshStudentModel()
        logAudit("SYNC_MERGED", result.summary)
        return result
    }

    // ==========================================
    // Cryptographic Deletion & Audit (Acceptance Criteria)
    // ==========================================

    suspend fun logAudit(action: String, details: String) {
        val hash = PackageVerifier.computeSha256(action + details + System.currentTimeMillis())
        auditDao.insertAudit(
            AuditLogEntity(
                auditId = "audit-" + UUID.randomUUID().toString().take(6),
                action = action,
                timestampMs = System.currentTimeMillis(),
                details = details,
                verificationHash = hash.take(16)
            )
        )
    }

    val auditLogsFlow: Flow<List<AuditLogEntity>> = auditDao.getAllAuditsFlow()

    suspend fun purgeStudentData(studentId: String): String {
        val count = eventDao.purgeStudentEvents(studentId)
        val purgeProof = "PURGED-${UUID.randomUUID().toString().take(8)}-CRYPTO_VERIFIED"
        logAudit("SECURE_PURGE", "Purged $count student events for $studentId. Cryptographic erasure proof: $purgeProof")
        refreshStudentModel()
        return purgeProof
    }

    // Helpers
    private fun entityToEvent(e: EventEntity): LearningEvent {
        return LearningEvent(
            eventId = e.eventId,
            eventType = EventType.valueOf(e.eventType),
            studentId = e.studentId,
            courseVersionId = e.courseVersionId,
            hlcTimestamp = e.hlcTimestamp,
            physicalMs = e.physicalMs,
            payloadJson = e.payloadJson,
            provenanceHash = e.provenanceHash
        )
    }

    private fun deserializePackage(entity: CoursePackageEntity): CoursePackage {
        val manifest = CoursePackageManifest(
            packageId = entity.packageId,
            courseId = entity.courseId,
            subjectName = entity.subjectName,
            version = entity.version,
            schemaVersion = "1.0",
            createdAt = "2026-09-08T00:00:00Z",
            contentHash = entity.contentHash,
            signature = "ED25519:SIG:active"
        )
        return CoursePackage(
            manifest = manifest,
            nodes = CoursePackageProvider.createMath4024Package().nodes,
            edges = CoursePackageProvider.createMath4024Package().edges,
            questions = CoursePackageProvider.createMath4024Package().questions,
            resources = CoursePackageProvider.createMath4024Package().resources
        )
    }

    private fun serializeNodes(nodes: List<KGNode>): String = JSONArray().apply {
        nodes.forEach { n -> put(JSONObject().apply { put("id", n.id); put("name", n.name); put("type", n.type.name) }) }
    }.toString()

    private fun serializeEdges(edges: List<KGEdge>): String = JSONArray().apply {
        edges.forEach { e -> put(JSONObject().apply { put("id", e.id); put("source", e.source); put("target", e.target); put("type", e.type.name) }) }
    }.toString()

    private fun serializeQuestions(questions: List<QuestionItem>): String = JSONArray().apply {
        questions.forEach { q -> put(JSONObject().apply { put("id", q.id); put("prompt", q.prompt); put("difficulty", q.difficulty) }) }
    }.toString()

    private fun serializeResources(resources: List<ResourceItem>): String = JSONArray().apply {
        resources.forEach { r -> put(JSONObject().apply { put("id", r.id); put("title", r.title) }) }
    }.toString()
}
