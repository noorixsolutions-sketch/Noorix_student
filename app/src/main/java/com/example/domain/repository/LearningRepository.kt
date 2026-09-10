package com.example.domain.repository

import android.content.Context
import com.example.core.crypto.PackageVerifier
import com.example.core.crypto.VerificationStatus
import com.example.core.hlc.HybridClock
import com.example.core.model.AcademicState
import com.example.core.model.AuditLogItem
import com.example.core.model.CoursePackage
import com.example.core.model.EdgeType
import com.example.core.model.EventType
import com.example.core.model.GraphNode
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
import com.example.core.model.RecalibrationRecord
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
import com.example.data.local.UserAccountEntity
import androidx.room.withTransaction
import com.example.core.model.CurriculumGraph
import com.example.core.model.CurriculumGraphParser
import com.example.core.model.UserProfile
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LearningRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(context)
    private val eventDao = db.eventDao()
    private val packageDao = db.coursePackageDao()
    private val syncQueueDao = db.syncQueueDao()
    private val auditDao = db.auditDao()
    private val accountDao = db.userAccountDao()
    val chatMessageDao = db.chatMessageDao()

    private val packageProvider = CoursePackageProvider(appContext)

    private val nodeId = appContext.getSharedPreferences("runtime_identity", Context.MODE_PRIVATE)
        .getString("node_id", null)
        ?: UUID.randomUUID().toString().also { generated ->
            appContext.getSharedPreferences("runtime_identity", Context.MODE_PRIVATE)
                .edit().putString("node_id", generated).apply()
        }
    private val hybridClock = HybridClock(nodeId)
    private val syncEngine = HlcSyncEngine(eventDao, syncQueueDao, hybridClock, db)
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

    // Curriculum knowledge graph (Cambridge_Curriculum_Library @ master)
    private val _curriculumGraph = MutableStateFlow<CurriculumGraph?>(null)
    val curriculumGraph: StateFlow<CurriculumGraph?> = _curriculumGraph.asStateFlow()

    // Google account (Credential Manager OAuth)
    private val _userAccount = MutableStateFlow<UserProfile?>(null)
    val userAccount: StateFlow<UserProfile?> = _userAccount.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auditMutex = Mutex()
    private var initJob: Job? = null

    init {
        initJob = scope.launch {
            initDefaultPackageIfNeeded()
            loadCurriculumGraph()
            loadUserAccount()
            refreshStudentModel()
        }
    }

    // ==========================================
    // Package readiness (fixes first-launch race)
    // ==========================================

    /** Awaits the initial package load (used by diagnostics to avoid an empty question bank). */
    suspend fun ensureDefaultPackage(timeoutMs: Long = 8000L) {
        if (_activePackage.value != null) return
        withTimeoutOrNull(timeoutMs) {
            while (_activePackage.value == null) delay(50L)
        }
    }

    private suspend fun initDefaultPackageIfNeeded() {
        val existing = packageDao.getActivePackage()
        if (existing == null) {
            val defaultMath = packageProvider.createMath4024Package()
            installAndActivatePackage(defaultMath)
        } else {
            val pkg = deserializePackage(existing)
            val status = PackageVerifier.verifyPackage(pkg)
            if (status is VerificationStatus.Valid) {
                _activePackage.value = pkg
            } else {
                val fallback = packageProvider.createMath4024Package()
                installAndActivatePackage(fallback)
            }
        }
    }

    private suspend fun loadCurriculumGraph() {
        try {
            val json = appContext.assets.open("curriculum/knowledge_graph.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            _curriculumGraph.value = CurriculumGraphParser.parse(json)
        } catch (_: Exception) {
            _curriculumGraph.value = null
        }
    }

    private suspend fun loadUserAccount() {
        _userAccount.value = accountDao.getActiveAccount()?.toProfile()
    }

    suspend fun saveUserAccount(profile: UserProfile) {
        accountDao.upsert(profile.toEntity())
        _userAccount.value = profile
        logAudit("GOOGLE_SIGN_IN", "Signed in as ${profile.displayName} (${profile.email})")
    }

    suspend fun clearUserAccount() {
        accountDao.clear()
        _userAccount.value = null
        logAudit("GOOGLE_SIGN_OUT", "Google account signed out locally")
    }

    // ==========================================
    // Student model projection (debounced)
    // ==========================================

    private var projectionJob: Job? = null
    private val projectionDebounceMs = 250L

    fun scheduleStudentModelRefresh() {
        projectionJob?.cancel()
        projectionJob = scope.launch {
            delay(projectionDebounceMs)
            projectStudentModelNow()
        }
    }

    suspend fun refreshStudentModel() {
        projectionJob?.cancel()
        projectStudentModelNow()
    }

    private suspend fun projectStudentModelNow() {
        val events = eventDao.getAllEvents().mapNotNull { entityToEvent(it) }
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
                academicState = projected.academicStates[targetConcept],
                profileCalibration = projected.profileCalibration
            )
        }
    }

    // ==========================================
    // Course Package Operations (Invariant P1)
    // ==========================================

    suspend fun installAndActivatePackage(pkg: CoursePackage): Pair<Boolean, String> {
        val verifyStatus = PackageVerifier.verifyPackage(pkg)
        if (verifyStatus !is VerificationStatus.Valid) {
            val reason = when (verifyStatus) {
                is VerificationStatus.HashMismatch -> "Hash Mismatch: expected ${verifyStatus.expected} but was ${verifyStatus.actual}"
                is VerificationStatus.SignatureInvalid -> "Cryptographic Signature Invalid: ${verifyStatus.reason}"
                is VerificationStatus.SchemaIncompatible -> "Schema Incompatible: requires ${verifyStatus.expected} but package was ${verifyStatus.actual}"
                is VerificationStatus.IntegrityCheckFailed -> "Graph Integrity Failed: dangling references ${verifyStatus.missingReferences}"
                else -> "Verification failed"
            }
            logAudit("PACKAGE_INSTALL_REJECTED", "Rejected ${pkg.manifest.packageId}: $reason")
            return Pair(false, "REJECTED BY PACKAGE VERIFIER: $reason")
        }

        val entity = CoursePackageEntity(
            packageId = pkg.manifest.packageId,
            courseId = pkg.manifest.courseId,
            version = pkg.manifest.version,
            subjectName = pkg.manifest.subjectName,
            status = "ACTIVE",
            contentHash = pkg.manifest.contentHash,
            manifestJson = packageProvider.serializeManifest(pkg),
            nodesJson = packageProvider.serializeNodes(pkg.nodes),
            edgesJson = packageProvider.serializeEdges(pkg.edges),
            questionsJson = packageProvider.serializeQuestions(pkg.questions),
            resourcesJson = packageProvider.serializeResources(pkg.resources)
        )

        db.withTransaction {
            packageDao.deprecateOtherVersions(pkg.manifest.packageId, pkg.manifest.courseId)
            packageDao.insertPackage(entity)
        }

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
        scheduleStudentModelRefresh()
        return event
    }

    val allEventsFlow: Flow<List<LearningEvent>> = eventDao.getAllEventsFlow().map { list ->
        list.mapNotNull { entityToEvent(it) }
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

        // Automatic Continuous Closed-Loop Calibration Trigger
        evaluateContinuousCalibration(
            conceptId = conceptId,
            activityType = "SYMBOLIC_VERIFIER",
            score = if (result.isValid) 1.0 else 0.40,
            durationMinutes = 5,
            errorType = result.errorType?.name
        )

        return result
    }

    // ==========================================
    // Vision & OCR Pipeline
    // ==========================================

    suspend fun parseNotebookBitmap(bitmap: android.graphics.Bitmap): NotebookParseResult {
        return VisionPipeline.parseNotebookBitmap(bitmap)
    }

    fun parseNotebookPage(lines: List<Pair<String, Double>>): NotebookParseResult {
        return VisionPipeline.parseNotebookPage(lines)
    }

    // ==========================================
    // Adaptive Diagnostic Session
    // ==========================================

    fun startDiagnostic() {
        val profile = _studentModel.value.profileCalibration
        _diagnosticSession.value = AdaptiveDiagnosticEngine.initializeSession(profile)
        scope.launch {
            recordEvent(
                EventType.DIAGNOSTIC_STARTED,
                JSONObject().apply {
                    put("grade", profile.studentGrade)
                    put("age", profile.studentAge)
                    put("targetGrade", profile.targetGrade)
                    put("priorTheta", profile.priorTheta)
                }.toString()
            )
        }
    }

    suspend fun submitDiagnosticAnswer(question: QuestionItem, selectedIndex: Int) {
        val currentSession = _diagnosticSession.value
        val updatedSession = AdaptiveDiagnosticEngine.submitAnswer(
            session = currentSession,
            question = question,
            selectedIndex = selectedIndex,
            durationMs = 15000L,
            studentId = _studentModel.value.studentId,
            courseVersionId = _studentModel.value.courseVersionId
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

        val bank = _activePackage.value?.questions ?: emptyList()
        val nextAvailable = if (bank.isNotEmpty()) {
            AdaptiveDiagnosticEngine.getNextQuestion(updatedSession, bank)
        } else {
            null
        }

        if (updatedSession.isCompleted || (bank.isNotEmpty() && nextAvailable == null)) {
            val sessionToComplete = if (updatedSession.isCompleted && updatedSession.synthesizedPlan != null) {
                updatedSession
            } else {
                val gaps = updatedSession.identifiedGaps
                val planConcept = if (gaps.isNotEmpty()) gaps.first() else question.targetConceptId
                val planConceptName = planConcept.substringAfterLast(":").replaceFirstChar { it.uppercase() }
                val plan = AdaptivePlannerEngine.createPlan(
                    studentId = _studentModel.value.studentId,
                    courseVersionId = _studentModel.value.courseVersionId,
                    targetConceptId = planConcept,
                    targetConceptName = planConceptName,
                    academicState = AcademicState(
                        conceptId = planConcept,
                        conceptName = planConceptName,
                        baseMastery = updatedSession.estimatedAbility,
                        confidence = updatedSession.confidence
                    ),
                    profileCalibration = _studentModel.value.profileCalibration,
                    identifiedGaps = gaps
                )
                val summary = "Diagnostic completed in ${updatedSession.itemsAnswered.size} questions. " +
                    "Estimated ability: ${String.format("%.2f", updatedSession.estimatedAbility)}. " +
                    "Gaps identified: ${if (gaps.isEmpty()) "None" else gaps.joinToString(", ")}. Adaptive plan active."
                updatedSession.copy(isCompleted = true, diagnosticSummary = summary, synthesizedPlan = plan)
            }
            _diagnosticSession.value = sessionToComplete

            if (sessionToComplete.synthesizedPlan != null) {
                _activePlan.value = sessionToComplete.synthesizedPlan
                recordEvent(
                    EventType.PLAN_CREATED,
                    JSONObject().apply {
                        put("planId", sessionToComplete.synthesizedPlan.planId)
                        put("targetConceptId", sessionToComplete.synthesizedPlan.targetConceptId)
                        put("interventionType", sessionToComplete.synthesizedPlan.interventionType)
                        put("expectedAccuracy", sessionToComplete.synthesizedPlan.predictions.expectedAccuracy)
                    }.toString()
                )
            }

            val compPayload = JSONObject().apply {
                put("questionsAnswered", sessionToComplete.itemsAnswered.size)
                put("finalEstimatedAbility", sessionToComplete.estimatedAbility)
                put("identifiedGaps", JSONArray(sessionToComplete.identifiedGaps))
                put("earlyStopReason", if (updatedSession.isCompleted) "STOPPING_CRITERIA" else "BANK_EXHAUSTED")
            }.toString()
            recordEvent(EventType.DIAGNOSTIC_COMPLETED, compPayload)
            logAudit(
                "DIAGNOSTIC_COMPLETED",
                "Completed in ${sessionToComplete.itemsAnswered.size} questions with ability ${sessionToComplete.estimatedAbility}"
            )
            refreshStudentModel()
        }
    }

    // ==========================================
    // Continuous Closed-Loop Calibration Engine
    // ==========================================

    suspend fun evaluateContinuousCalibration(
        conceptId: String,
        activityType: String,
        score: Double,
        durationMinutes: Int,
        errorType: String? = null
    ): RecalibrationRecord? {
        val currentModel = _studentModel.value
        val baselineState = currentModel.academicStates[conceptId]

        // Look up prerequisite state in KG
        val prereqs = getPrerequisites(conceptId)
        val prereqState = prereqs.firstOrNull()?.let { currentModel.academicStates[it.id] }

        val plan = _activePlan.value ?: AdaptivePlannerEngine.createPlan(
            studentId = currentModel.studentId,
            courseVersionId = currentModel.courseVersionId,
            targetConceptId = conceptId,
            targetConceptName = baselineState?.conceptName ?: conceptId.substringAfterLast(":"),
            academicState = baselineState,
            profileCalibration = currentModel.profileCalibration
        )

        val observedGain = (score * 0.20).coerceIn(0.0, 0.40)
        val obs = PlanObservation(
            observedMasteryGain = observedGain,
            observedAccuracy = score,
            observedDurationMinutes = durationMinutes,
            completion = true
        )

        val (evaluatedPlan, recalibrationRecord) = AdaptivePlannerEngine.evaluatePlanExecution(
            plan = plan,
            observation = obs,
            baselineAcademicState = baselineState,
            prerequisiteAcademicState = prereqState,
            errorType = errorType,
            triggerSource = activityType
        )

        _activePlan.value = evaluatedPlan

        val payload = JSONObject().apply {
            put("recalibrationId", recalibrationRecord.recalibrationId)
            put("triggerSource", activityType)
            put("conceptId", conceptId)
            put("attribution", recalibrationRecord.attribution.name)
            put("previousIntervention", recalibrationRecord.previousIntervention)
            put("newIntervention", recalibrationRecord.newIntervention)
            put("deltaAccuracy", evaluatedPlan.deviation?.deltaAccuracy)
            put("deltaGain", evaluatedPlan.deviation?.deltaMasteryGain)
            put("diagnosisNote", recalibrationRecord.diagnosisNote)
            put("actionTaken", recalibrationRecord.actionTaken)
        }.toString()

        recordEvent(EventType.PLAN_DEVIATION_DETECTED, payload)
        if (recalibrationRecord.previousIntervention != recalibrationRecord.newIntervention) {
            recordEvent(EventType.PLAN_REVISED, payload)
        }

        logAudit(
            "RECALIBRATION_TRIGGERED",
            "Attribution: ${recalibrationRecord.attribution.name} for $conceptId (${recalibrationRecord.diagnosisNote})"
        )

        refreshStudentModel()
        return recalibrationRecord
    }

    suspend fun evaluateCurrentPlan(
        observedGain: Double,
        observedAccuracy: Double,
        durationMinutes: Int
    ): LearningPlan? {
        val plan = _activePlan.value ?: return null
        val baseline = _studentModel.value.academicStates[plan.targetConceptId]
        val prereqs = getPrerequisites(plan.targetConceptId)
        val prereqState = prereqs.firstOrNull()?.let { _studentModel.value.academicStates[it.id] }

        val obs = PlanObservation(
            observedMasteryGain = observedGain,
            observedAccuracy = observedAccuracy,
            observedDurationMinutes = durationMinutes,
            completion = true
        )

        val (evaluatedPlan, recalRecord) = AdaptivePlannerEngine.evaluatePlanExecution(
            plan = plan,
            observation = obs,
            baselineAcademicState = baseline,
            prerequisiteAcademicState = prereqState
        )
        _activePlan.value = evaluatedPlan

        val payload = JSONObject().apply {
            put("planId", plan.planId)
            put("recalibrationId", recalRecord.recalibrationId)
            put("attribution", recalRecord.attribution.name)
            put("classification", evaluatedPlan.classification?.name)
            put("deltaAccuracy", evaluatedPlan.deviation?.deltaAccuracy)
            put("deltaGain", evaluatedPlan.deviation?.deltaMasteryGain)
            put("diagnosisNote", evaluatedPlan.diagnosisNote)
        }.toString()

        recordEvent(EventType.PLAN_DEVIATION_DETECTED, payload)
        logAudit("PLAN_EVALUATED", "Plan ${plan.planId} evaluated as ${evaluatedPlan.classification?.name}: ${evaluatedPlan.diagnosisNote}")
        refreshStudentModel()
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
            profileCalibration = _studentModel.value.profileCalibration,
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
        refreshStudentModel()
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

        // Continuous Closed-Loop Calibration Trigger
        evaluateContinuousCalibration(
            conceptId = task.targetConceptId,
            activityType = "TRANSFER_TASK",
            score = if (passed) 0.95 else 0.35,
            durationMinutes = 8,
            errorType = if (passed) null else "TRANSFER_APPLICATION_ERROR"
        )

        return Pair(passed, message)
    }

    // ==========================================
    // Offline Sync Simulation
    // ==========================================

    suspend fun simulateRemoteSyncBatch(): SyncBatchResult {
        val remoteTime = _simulatedWallTimeMs.value - 86_400_000L
        val remoteHlc = hybridClock.now(remoteTime).toString()

        val remoteEvents = listOf(
            LearningEvent(
                eventId = "remote-evt-" + UUID.randomUUID().toString(),
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

    companion object {
        const val AUDIT_GENESIS_HASH = "GENESIS-0000000000000000000000000000000000000000000000000000000000000000"

        fun auditChainHash(action: String, details: String, timestampMs: Long, prevHash: String): String {
            return PackageVerifier.computeSha256("$action|$details|$timestampMs|$prevHash")
        }
    }

    private suspend fun lastAuditHash(): String {
        val latest = auditDao.getAllAudits().firstOrNull()
        return latest?.verificationHash ?: AUDIT_GENESIS_HASH
    }

    suspend fun logAudit(action: String, details: String) {
        auditMutex.withLock {
            val prevHash = lastAuditHash()
            val timestampMs = System.currentTimeMillis()
            val hash = auditChainHash(action, details, timestampMs, prevHash)
            auditDao.insertAudit(
                AuditLogEntity(
                    auditId = "audit-" + UUID.randomUUID().toString(),
                    action = action,
                    timestampMs = timestampMs,
                    details = details,
                    verificationHash = hash,
                    prevHash = prevHash
                )
            )
        }
    }

    val auditLogsFlow: Flow<List<AuditLogEntity>> = auditDao.getAllAuditsFlow()

    fun verifyAuditChain(logs: List<AuditLogEntity>): Boolean {
        if (logs.isEmpty()) return true

        val byPrevHash = logs.associateBy { if (it.prevHash.isEmpty()) AUDIT_GENESIS_HASH else it.prevHash }

        if (byPrevHash.size == logs.size && byPrevHash.containsKey(AUDIT_GENESIS_HASH)) {
            var currentPrev = AUDIT_GENESIS_HASH
            var visitedCount = 0
            while (visitedCount < logs.size) {
                val nextEntry = byPrevHash[currentPrev] ?: return false
                val expectedPrev = if (nextEntry.prevHash.isEmpty()) AUDIT_GENESIS_HASH else nextEntry.prevHash
                if (expectedPrev != currentPrev) return false
                if (auditChainHash(nextEntry.action, nextEntry.details, nextEntry.timestampMs, expectedPrev) != nextEntry.verificationHash) {
                    return false
                }
                currentPrev = nextEntry.verificationHash
                visitedCount++
            }
            return true
        }

        var prev = AUDIT_GENESIS_HASH
        for (entry in logs.sortedBy { it.timestampMs }) {
            val expectedPrev = if (entry.prevHash.isEmpty()) AUDIT_GENESIS_HASH else entry.prevHash
            if (entry.prevHash.isNotEmpty() && entry.prevHash != prev) return false
            if (entry.prevHash.isEmpty() && prev != AUDIT_GENESIS_HASH) return false
            if (auditChainHash(entry.action, entry.details, entry.timestampMs, expectedPrev) != entry.verificationHash) {
                return false
            }
            prev = entry.verificationHash
        }
        return true
    }

    suspend fun purgeStudentData(studentId: String): String {
        val count = eventDao.purgeStudentEvents(studentId)
        val lastHash = lastAuditHash()
        val proof = PackageVerifier.computeSha256("PURGE|$count|$studentId|$lastHash").take(32)
        logAudit(
            "SECURE_PURGE",
            "Purged $count learning events for student $studentId. " +
                "Chain proof: $proof (deterministic hash of purge action + last chain hash)"
        )
        refreshStudentModel()
        return proof
    }

    // Helpers
    private fun entityToEvent(e: EventEntity): LearningEvent? {
        val type = runCatching { EventType.valueOf(e.eventType) }.getOrNull() ?: return null
        return LearningEvent(
            eventId = e.eventId,
            eventType = type,
            studentId = e.studentId,
            courseVersionId = e.courseVersionId,
            hlcTimestamp = e.hlcTimestamp,
            physicalMs = e.physicalMs,
            payloadJson = e.payloadJson,
            provenanceHash = e.provenanceHash
        )
    }

    private fun deserializePackage(entity: CoursePackageEntity): CoursePackage {
        val m = JSONObject(entity.manifestJson)
        val manifest = com.example.core.model.CoursePackageManifest(
            packageId = m.optString("packageId", entity.packageId),
            courseId = m.optString("courseId", entity.courseId),
            subjectName = m.optString("subjectName", entity.subjectName),
            version = m.optString("version", entity.version),
            schemaVersion = m.optString("schemaVersion", "1.0"),
            createdAt = m.optString("createdAt", "2026-09-08T00:00:00Z"),
            contentHash = m.optString("contentHash", entity.contentHash),
            parentVersion = m.optString("parentVersion", "").takeIf { it.isNotEmpty() },
            deltaBase = m.optString("deltaBase", "").takeIf { it.isNotEmpty() },
            signature = m.optString("signature", "")
        )

        val nodes = ArrayList<KGNode>()
        val nodesArr = JSONArray(entity.nodesJson)
        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.getJSONObject(i)
            val props = HashMap<String, String>()
            val propObj = n.optJSONObject("properties")
            if (propObj != null) {
                val keys = propObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    props[k] = propObj.optString(k, "")
                }
            }
            nodes.add(
                KGNode(
                    id = n.getString("id"),
                    type = NodeType.valueOf(n.getString("type")),
                    name = n.getString("name"),
                    properties = props
                )
            )
        }

        val edges = ArrayList<KGEdge>()
        val edgesArr = JSONArray(entity.edgesJson)
        for (i in 0 until edgesArr.length()) {
            val e = edgesArr.getJSONObject(i)
            val props = HashMap<String, String>()
            val propObj = e.optJSONObject("properties")
            if (propObj != null) {
                val keys = propObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    props[k] = propObj.optString(k, "")
                }
            }
            edges.add(
                KGEdge(
                    id = e.getString("id"),
                    type = EdgeType.valueOf(e.getString("type")),
                    source = e.getString("source"),
                    target = e.getString("target"),
                    properties = props
                )
            )
        }

        val questions = ArrayList<QuestionItem>()
        val qArr = JSONArray(entity.questionsJson)
        for (i in 0 until qArr.length()) {
            val q = qArr.getJSONObject(i)
            val options = ArrayList<String>()
            val optArr = q.optJSONArray("options")
            if (optArr != null) for (j in 0 until optArr.length()) options.add(optArr.getString(j))
            val misc = ArrayList<String>()
            val miscArr = q.optJSONArray("potentialMisconceptions")
            if (miscArr != null) for (j in 0 until miscArr.length()) misc.add(miscArr.getString(j))
            questions.add(
                QuestionItem(
                    id = q.getString("id"),
                    courseVersionId = q.getString("courseVersionId"),
                    targetConceptId = q.getString("targetConceptId"),
                    questionType = q.optString("questionType", "STRUCTURED"),
                    prompt = q.getString("prompt"),
                    options = options,
                    correctIndex = q.optInt("correctIndex", -1),
                    targetLaTeX = q.optString("targetLaTeX", ""),
                    difficulty = q.optDouble("difficulty", 0.5),
                    maxMarks = q.optInt("maxMarks", 4),
                    bloomLevel = q.optString("bloomLevel", "APPLICATION"),
                    rubrics = q.optString("rubrics", ""),
                    prerequisiteConceptId = q.optString("prerequisiteConceptId", "").takeIf { it.isNotEmpty() },
                    potentialMisconceptions = misc
                )
            )
        }

        val resources = ArrayList<ResourceItem>()
        val rArr = JSONArray(entity.resourcesJson)
        for (i in 0 until rArr.length()) {
            val r = rArr.getJSONObject(i)
            resources.add(
                ResourceItem(
                    id = r.getString("id"),
                    conceptId = r.getString("conceptId"),
                    resourceType = r.optString("resourceType", "TEXT"),
                    title = r.getString("title"),
                    content = r.getString("content"),
                    contentHash = r.optString("contentHash", "")
                )
            )
        }

        return CoursePackage(manifest, nodes, edges, questions, resources)
    }
}

private fun UserAccountEntity.toProfile() = UserProfile(
    userId = userId,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl
)

private fun UserProfile.toEntity() = UserAccountEntity(
    userId = userId,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl,
    active = true,
    updatedAtMs = System.currentTimeMillis()
)
