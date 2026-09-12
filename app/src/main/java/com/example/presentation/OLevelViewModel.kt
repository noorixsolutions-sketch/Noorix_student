package com.example.presentation

import android.app.Activity
import android.app.Application
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.AnalyticsSnapshot
import com.example.core.model.Bookmark
import com.example.core.model.BookmarkKind
import com.example.core.model.CoursePackage
import com.example.core.model.CurriculumGraph
import com.example.core.model.Flashcard
import com.example.core.model.FlashcardReview
import com.example.core.model.GamificationSnapshot
import com.example.core.model.LearningEvent
import com.example.core.model.LearningPlan
import com.example.core.model.MediaArtifact
import com.example.core.model.MediaLearningRecord
import com.example.core.model.QuestionItem
import com.example.core.model.ReviewGrade
import com.example.core.model.Step
import com.example.core.model.StudentModel
import com.example.core.model.StudyNote
import com.example.core.model.TransferTask
import com.example.core.model.UserProfile
import com.example.core.model.VerificationResult
import com.example.domain.analytics.LearningAnalyticsEngine
import com.example.domain.gamification.GamificationEngine
import com.example.domain.studytools.FlashcardReviewEngine
import com.example.domain.studytools.StudyToolsRepository
import com.example.BuildConfig
import com.example.data.curriculum.CoursePackageProvider
import com.example.data.curriculum.LearningContentProvider
import com.example.data.local.AuditLogEntity
import com.example.presentation.util.UiCopy
import com.example.domain.ai.NoorixAiAgentManager
import com.example.data.local.ChatMessageEntity
import com.example.domain.diagnostic.DiagnosticSessionState
import com.example.domain.planner.AdaptivePlannerEngine
import com.example.domain.repository.LearningRepository
import com.example.domain.sync.SyncBatchResult
import com.example.domain.vision.NotebookParseResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.domain.onboarding.OnboardingState
import com.example.domain.onboarding.OnboardingPhase
import com.example.domain.onboarding.OnboardingData
import com.example.domain.parent.ParentPrefs


import com.example.core.network.OpenRouterApi
import com.example.core.network.OpenRouterModel
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import android.net.Uri


enum class AppNavigationTab {
    ONBOARDING,
    DASHBOARD,
    AI_AGENTS,
    PACKAGES,
    LEARNING_HUB,
    DIAGNOSTIC,
    SYMBOLIC_TUTOR,
    MEDIA_TRANSFER,
    DECAY_SYNC,
    AUDIT,
    // ADDITIVE (§9 Goal Architecture): PIN-gated parent report.
    PARENT
}

class OLevelViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LearningRepository(application.applicationContext)

    val currentTab = MutableStateFlow(AppNavigationTab.ONBOARDING)
    val simulatedWallTimeMs: StateFlow<Long> = repository.simulatedWallTimeMs
    val activePackage: StateFlow<CoursePackage?> = repository.activePackage
    val studentModel: StateFlow<StudentModel> = repository.studentModel
    val activePlan: StateFlow<LearningPlan?> = repository.activePlan
    val learningContent = runCatching {
        LearningContentProvider(application.applicationContext).load()
    }.getOrDefault(com.example.core.model.KnowledgeLearningContent())

    // Noorix AI Agent state (initialized early so OpenRouter & Local config bind properly)
    private val agentManager = NoorixAiAgentManager(application.applicationContext, repository.chatMessageDao)
    private val _selectedAgentId = MutableStateFlow("danish")
    val selectedAgentId: StateFlow<String> = _selectedAgentId.asStateFlow()

    private val _agentStreamingText = MutableStateFlow<String?>(null)
    val agentStreamingText: StateFlow<String?> = _agentStreamingText.asStateFlow()

    private val _isAgentThinking = MutableStateFlow(false)
    val isAgentThinking: StateFlow<Boolean> = _isAgentThinking.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow("")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _openRouterModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val openRouterModels: StateFlow<List<OpenRouterModel>> = _openRouterModels.asStateFlow()

    private val _selectedOpenRouterModel = MutableStateFlow<String?>(null)
    val selectedOpenRouterModel: StateFlow<String?> = _selectedOpenRouterModel.asStateFlow()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val openRouterApi = retrofit.create(OpenRouterApi::class.java)

    fun updateOpenRouterApiKey(key: String) {
        _openRouterApiKey.value = key
        agentManager.setOpenRouterConfig(key, _selectedOpenRouterModel.value)
        if (key.isNotBlank()) {
            fetchOpenRouterModels(key)
        }
    }

    private fun fetchOpenRouterModels(key: String) {
        viewModelScope.launch {
            try {
                val response = openRouterApi.getModels("Bearer $key")
                _openRouterModels.value = response.data
                if (response.data.isNotEmpty() && _selectedOpenRouterModel.value == null) {
                    val defaultModel = response.data.first().id
                    _selectedOpenRouterModel.value = defaultModel
                    agentManager.setOpenRouterConfig(_openRouterApiKey.value, defaultModel)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectOpenRouterModel(modelId: String) {
        _selectedOpenRouterModel.value = modelId
        agentManager.setOpenRouterConfig(_openRouterApiKey.value, modelId)
    }

    val diagnosticSession: StateFlow<DiagnosticSessionState> = repository.diagnosticSession
    val mediaRecord: StateFlow<MediaLearningRecord?> = repository.mediaRecord

    // ==========================================
    // Google sign-in (Credential Manager OAuth)
    // ==========================================

    val userAccount: StateFlow<UserProfile?> = repository.userAccount

    private val _googleSignInBusy = MutableStateFlow(false)
    val googleSignInBusy: StateFlow<Boolean> = _googleSignInBusy.asStateFlow()

    fun signInWithGoogle(activity: Activity) {
        if (_googleSignInBusy.value) return
        viewModelScope.launch {
            _googleSignInBusy.value = true
            try {
                val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                if (serverClientId.isBlank() || serverClientId == "YOUR_GOOGLE_WEB_CLIENT_ID") {
                    _statusBanner.value = "Google sign-in not configured: set GOOGLE_WEB_CLIENT_ID in local.properties (see .env.example)."
                    return@launch
                }
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(true)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val response: GetCredentialResponse = try {
                    CredentialManager.create(activity).getCredential(activity, request)
                } catch (e: GetCredentialException) {
                    _statusBanner.value = "Google sign-in failed: ${e.localizedMessage ?: e.message}"
                    return@launch
                }
                val credential = response.credential
                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    _statusBanner.value = "Unexpected credential type: ${credential?.type}"
                    return@launch
                }
                val googleCredential: GoogleIdTokenCredential =
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data)
                    } catch (e: GoogleIdTokenParsingException) {
                        _statusBanner.value = "Failed to parse the Google ID token."
                        return@launch
                    }
                val rawEmail = if (googleCredential.id.contains("@")) googleCredential.id else ""
                val account = UserProfile(
                    userId = googleCredential.id,
                    displayName = googleCredential.displayName ?: (if (rawEmail.isNotEmpty()) rawEmail.substringBefore("@") else "Cambridge Candidate"),
                    email = rawEmail,
                    photoUrl = googleCredential.profilePictureUri?.toString()
                )
                repository.saveUserAccount(account)
                _statusBanner.value = "Signed in as ${account.email.ifEmpty { account.displayName }}."
            } catch (e: Exception) {
                _statusBanner.value = "Google sign-in failed: ${e.localizedMessage}"
            } finally {
                _googleSignInBusy.value = false
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            repository.clearUserAccount()
            _statusBanner.value = "Signed out of Google."
        }
    }

    // Onboarding State
    private val _onboardingState = MutableStateFlow(OnboardingState())
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

    fun updateOnboardingProfile(
        name: String,
        grade: String,
        age: Int,
        targetGrade: String,
        targetExamSession: String,
        dailyMinutes: Int
    ) {
        _onboardingState.value = _onboardingState.value.copy(
            candidateName = name,
            candidateGrade = grade,
            candidateAge = age,
            targetGrade = targetGrade,
            targetExamSession = targetExamSession,
            dailyTargetMinutes = dailyMinutes
        )
    }

    fun startOnboardingPhase(phase: OnboardingPhase) {
        _onboardingState.value = _onboardingState.value.copy(phase = phase)
        if (phase == OnboardingPhase.TEST_C_CURRICULUM) {
            startDiagnostic()
        }
        if (phase == OnboardingPhase.COMPLETE) {
            setTab(AppNavigationTab.DASHBOARD)
        }
    }

    fun submitTestAAnswer(value: Int) {
        val state = _onboardingState.value
        val newResponses = state.testAResponses + value
        if (state.testAIndex + 1 < OnboardingData.testA.size) {
            _onboardingState.value = state.copy(testAIndex = state.testAIndex + 1, testAResponses = newResponses)
        } else {
            val signals = mapOf(
                "persistence" to (newResponses[0] / 5f),
                "confidence" to (newResponses[1] / 5f),
                "frustration_response" to (newResponses[2] / 5f),
                "metacognition" to (newResponses[3] / 5f)
            )
            _onboardingState.value = state.copy(
                testAResponses = newResponses,
                psychometricSignals = signals,
                phase = OnboardingPhase.TEST_B_COGNITIVE
            )
        }
    }

    fun submitTestBAnswer(selectedIndex: Int) {
        val state = _onboardingState.value
        val q = OnboardingData.testB[state.testBIndex]
        val isCorrect = selectedIndex == q.correctIndex
        val newResponses = state.testBResponses + isCorrect
        
        if (state.testBIndex + 1 < OnboardingData.testB.size) {
            _onboardingState.value = state.copy(testBIndex = state.testBIndex + 1, testBResponses = newResponses)
        } else {
            val cogSignals = mapOf(
                "pattern_reasoning" to if (newResponses[0]) 1f else 0.5f,
                "logical_reasoning" to if (newResponses[1]) 1f else 0.5f,
                "working_memory" to if (newResponses[2]) 1f else 0.5f,
                "spatial_reasoning" to if (newResponses[3]) 1f else 0.5f
            )
            val psySignals = state.psychometricSignals ?: mapOf(
                "persistence" to 0.7f,
                "confidence" to 0.65f,
                "frustration_response" to 0.6f,
                "metacognition" to 0.75f
            )

            viewModelScope.launch {
                val payload = org.json.JSONObject().apply {
                    put("studentName", state.candidateName)
                    put("candidateGrade", state.candidateGrade)
                    put("candidateAge", state.candidateAge)
                    put("targetGrade", state.targetGrade)
                    put("targetExamSession", state.targetExamSession)
                    put("dailyTargetMinutes", state.dailyTargetMinutes)
                    put("psychometricSignals", org.json.JSONObject().apply {
                        psySignals.forEach { (k, v) -> put(k, v) }
                    })
                    put("cognitiveSignals", org.json.JSONObject().apply {
                        cogSignals.forEach { (k, v) -> put(k, v) }
                    })
                }.toString()

                repository.recordEvent(
                    com.example.core.model.EventType.STUDENT_ONBOARDED,
                    payload
                )
            }

            _onboardingState.value = state.copy(
                testBResponses = newResponses,
                cognitiveSignals = cogSignals,
                phase = OnboardingPhase.TEST_C_CURRICULUM
            )
            startDiagnostic()
        }
    }

    fun submitLearningHubQuiz(conceptId: String, quizId: String, isCorrect: Boolean) {
        viewModelScope.launch {
            val recal = repository.evaluateContinuousCalibration(
                conceptId = conceptId,
                activityType = "POST_STUDY_QUIZ",
                score = if (isCorrect) 1.0 else 0.25,
                durationMinutes = 3,
                errorType = if (isCorrect) null else "CONCEPTUAL_QUIZ_ERROR"
            )
            if (recal != null && recal.attribution != com.example.core.model.RecalibrationAttribution.ON_TRACK) {
                _statusBanner.value = "Continuous Calibration [${recal.attribution.name}]: ${recal.diagnosisNote}"
            }
        }
    }



    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val eventsList: StateFlow<List<LearningEvent>> = repository.allEventsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Knowledge graph built from the Cambridge_Curriculum_Library (master branch). */
    val curriculumGraph: StateFlow<CurriculumGraph?> = repository.curriculumGraph

    // Symbolic verifier state
    private val _verificationResult = MutableStateFlow<VerificationResult?>(null)
    val verificationResult: StateFlow<VerificationResult?> = _verificationResult.asStateFlow()

    private val _ocrResult = MutableStateFlow<NotebookParseResult?>(null)
    val ocrResult: StateFlow<NotebookParseResult?> = _ocrResult.asStateFlow()

    // Media Transfer state
    private val _currentMediaArtifact = MutableStateFlow<MediaArtifact?>(null)
    val currentMediaArtifact: StateFlow<MediaArtifact?> = _currentMediaArtifact.asStateFlow()

    private val _currentTransferTask = MutableStateFlow<TransferTask?>(null)
    val currentTransferTask: StateFlow<TransferTask?> = _currentTransferTask.asStateFlow()

    private val _transferFeedback = MutableStateFlow<String?>(null)
    val transferFeedback: StateFlow<String?> = _transferFeedback.asStateFlow()

    fun getChatMessages(agentId: String): kotlinx.coroutines.flow.Flow<List<ChatMessageEntity>> {
        return agentManager.getChatHistory(agentId)
    }

    fun selectAgent(agentId: String) {
        _selectedAgentId.value = agentId
    }

    fun onAgentOpened(agentId: String, promptStarter: String) {
        _selectedAgentId.value = agentId
        viewModelScope.launch {
            val existing = repository.chatMessageDao.getMessagesForAgent(agentId)
            if (existing.isEmpty() && promptStarter.isNotBlank()) {
                sendAgentMessage(agentId, promptStarter)
            }
        }
    }

    fun sendAgentMessage(agentId: String, text: String, isStepVerification: Boolean = false) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isAgentThinking.value = true
            _agentStreamingText.value = ""
            try {
                agentManager.sendMessageStream(agentId, text, isStepVerification).collect { partialText ->
                    _agentStreamingText.value = partialText
                }
            } catch (e: Exception) {
                _agentStreamingText.value = "Error streaming from tutor: ${e.localizedMessage}"
            } finally {
                _isAgentThinking.value = false
                _agentStreamingText.value = null
            }
        }
    }

    fun clearAgentChat(agentId: String) {
        viewModelScope.launch {
            agentManager.clearChat(agentId)
        }
    }

    // Sync state
    private val _syncResult = MutableStateFlow<SyncBatchResult?>(null)
    val syncResult: StateFlow<SyncBatchResult?> = _syncResult.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun setTab(tab: AppNavigationTab) {
        currentTab.value = tab
    }

    // ==========================================
    // Package Installation / Agnosticism
    // ==========================================

    fun installMathCourse() {
        viewModelScope.launch {
            val mathPkg = CoursePackageProvider(getApplication<Application>().applicationContext).createMath4024Package()
            val (success, _) = repository.installAndActivatePackage(mathPkg)
            _statusBanner.value = UiCopy.formatInstallResult(success, "Mathematics (Cambridge 4024)")
        }
    }

    fun installPhysicsCourse() {
        viewModelScope.launch {
            val physicsPkg = CoursePackageProvider(getApplication<Application>().applicationContext).createPhysics5054Package()
            val (success, _) = repository.installAndActivatePackage(physicsPkg)
            _statusBanner.value = UiCopy.formatInstallResult(success, "Physics (Cambridge 5054)")
        }
    }


    // ==========================================
    // Symbolic Verifier & Math OCR
    // ==========================================

    fun verifyCustomMathSteps(problem: String, steps: List<Step>, conceptId: String) {
        viewModelScope.launch {
            val res = repository.verifyMathSolution(problem, steps, conceptId)
            _verificationResult.value = res
            _statusBanner.value = UiCopy.formatMathVerification(res.isValid, res.firstInvalidStepIndex, res.errorType)
        }
    }

    private val _isOcrScanning = MutableStateFlow(false)
    val isOcrScanning: StateFlow<Boolean> = _isOcrScanning.asStateFlow()

    fun processCapturedBitmap(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            _isOcrScanning.value = true
            try {
                val ocr = repository.parseNotebookBitmap(bitmap)
                _ocrResult.value = ocr
                if (ocr.extractedSteps.isNotEmpty()) {
                    val firstStep = ocr.extractedSteps.firstOrNull()?.expression ?: "x(x+2)"
                    verifyCustomMathSteps(firstStep, ocr.extractedSteps, "concept:4024:factorisation")
                }
            } catch (e: Exception) {
                _statusBanner.value = "OCR Scan error: ${e.localizedMessage}"
            } finally {
                _isOcrScanning.value = false
            }
        }
    }

    fun runNotebookOcr(inputLines: List<Pair<String, Double>>) {
        val ocr = repository.parseNotebookPage(inputLines)
        _ocrResult.value = ocr
        if (ocr.extractedSteps.isNotEmpty()) {
            verifyCustomMathSteps("x(x+2)", ocr.extractedSteps, "concept:4024:factorisation")
        }
    }

    // ==========================================
    // Diagnostic
    // ==========================================

    fun startDiagnostic() {
        // Onboarding gate: Test C must not run the diagnostic before the
        // signed course package (question bank) has loaded — that race is
        // what used to leave onboarding stuck. The UI (TestCPhase and
        // DiagnosticScreen) gates rendering on `activePackage != null`.
        repository.startDiagnostic()
    }

    fun submitDiagnosticItem(question: QuestionItem, selectedIndex: Int) {
        viewModelScope.launch {
            repository.submitDiagnosticAnswer(question, selectedIndex)
        }
    }

    fun getNextDiagnosticQuestion(): QuestionItem? {
        val session = diagnosticSession.value
        val questions = repository.activePackage.value?.questions ?: emptyList()
        return com.example.domain.diagnostic.AdaptiveDiagnosticEngine.getNextQuestion(session, questions)
    }

    // ==========================================
    // Planner & Plan Evaluation
    // ==========================================

    fun evaluateCurrentPlan(gain: Double, accuracy: Double, duration: Int) {
        viewModelScope.launch {
            val res = repository.evaluateCurrentPlan(gain, accuracy, duration)
            if (res != null) {
                _statusBanner.value = UiCopy.formatPlanClassification(res.classification)
            }
        }
    }

    fun replan(interventionType: String) {
        viewModelScope.launch {
            val p = repository.replanCurrent(interventionType)
            _statusBanner.value = UiCopy.formatReplan(p.interventionType)
        }
    }

    fun getEmpiricalEfficacy() = AdaptivePlannerEngine.getInterventionEfficacyList()

    // ==========================================
    // Media & Transfer
    // ==========================================

    fun generateMedia(conceptId: String) {
        viewModelScope.launch {
            val (media, task) = repository.generatePodcastBrief(conceptId)
            _currentMediaArtifact.value = media
            _currentTransferTask.value = task
            _transferFeedback.value = null
            _statusBanner.value = "Generated Podcast Brief grounded in approved KG resource: '${media.title}'"
        }
    }

    fun submitTransferTask(task: TransferTask, optionIndex: Int, consumedPercent: Double) {
        viewModelScope.launch {
            val (passed, msg) = repository.submitTransferTaskAttempt(task, optionIndex, consumedPercent)
            _transferFeedback.value = msg
            _statusBanner.value = msg
        }
    }

    // ==========================================
    // Temporal Decay & Time Travel
    // ==========================================

    fun advanceSimulatedDays(days: Int) {
        viewModelScope.launch {
            repository.advanceSimulatedClock(days)
            _statusBanner.value = "Advanced simulated clock by $days days. Temporal decay re-calculated across all concepts."
        }
    }

    // ==========================================
    // Sync (real transport, honest failures)
    // ==========================================

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun triggerSyncNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.syncNow()
                if (result.isSuccess) {
                    val res = result.getOrThrow()
                    _syncResult.value = res
                    _statusBanner.value = UiCopy.formatSyncResult(res)
                } else {
                    _statusBanner.value = UiCopy.formatSyncError()
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // ==========================================
    // Audit & Purge
    // ==========================================

    /**
     * Synchronous re-verification of the audit hash chain (tamper-evidence).
     * UI calls this whenever [auditLogs] changes and displays the result.
     */
    fun verifyAuditChainNow(): Boolean = repository.verifyAuditChain(auditLogs.value)

    fun purgeStudentData() {
        viewModelScope.launch {
            repository.purgeStudentData(studentModel.value.studentId)
            _statusBanner.value = UiCopy.formatPurgeSuccess()
        }
    }

    fun formatTime(timestampMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }

    // ==========================================
    // Progress analytics + gamification (ADDITIVE, derived)
    // Snapshots are recomputed from the existing event log + student model;
    // no new tables, no changes to existing flows.
    // ==========================================

    private val studyTools = StudyToolsRepository(getApplication<Application>().applicationContext)

    val gamificationSnapshot: StateFlow<GamificationSnapshot> =
        combine(eventsList, studentModel) { events, model ->
            val mastered = model.academicStates.values.count { it.effectiveMastery >= 0.85 }
            GamificationEngine.computeSnapshot(
                events = events,
                dailyTargetMinutes = model.profileCalibration.dailyTargetMinutes,
                masteredConceptCount = mastered
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GamificationEngine.emptySnapshot()
        )

    val analyticsSnapshot: StateFlow<AnalyticsSnapshot> =
        combine(eventsList, studentModel) { events, model ->
            LearningAnalyticsEngine.computeSnapshot(events, model)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyticsSnapshot()
        )

    // ==========================================
    // Study tools: bookmarks, notes, review (ADDITIVE)
    // ==========================================

    val bookmarks: StateFlow<List<Bookmark>> = studyTools.bookmarksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val flashcardProgress: StateFlow<Map<String, FlashcardReview>> = studyTools.reviewProgressFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun notesForConcept(conceptId: String): kotlinx.coroutines.flow.Flow<List<StudyNote>> =
        studyTools.notesForConcept(conceptId)

    fun toggleBookmark(kind: BookmarkKind, refId: String, conceptId: String, title: String) {
        viewModelScope.launch {
            val added = studyTools.toggleBookmark(kind, refId, conceptId, title)
            _statusBanner.value = if (added) "Bookmarked: ${title.take(48)}" else "Bookmark removed."
        }
    }

    fun isBookmarked(kind: BookmarkKind, refId: String): Boolean =
        bookmarks.value.any { it.kind == kind && it.refId == refId }

    fun saveStudyNote(
        noteId: String? = null,
        conceptId: String,
        conceptName: String = "",
        title: String,
        body: String
    ) {
        viewModelScope.launch {
            studyTools.saveNote(noteId, conceptId, conceptName, title, body)
            _statusBanner.value = "Note saved."
        }
    }

    fun deleteStudyNote(noteId: String) {
        viewModelScope.launch {
            studyTools.deleteNote(noteId)
            _statusBanner.value = "Note deleted."
        }
    }

    fun gradeFlashcard(cardId: String, conceptId: String, grade: ReviewGrade) {
        viewModelScope.launch {
            studyTools.gradeFlashcard(cardId, conceptId, grade)
        }
    }

    /** Cards due for review right now (including never-reviewed). */
    fun dueFlashcards(cards: List<Flashcard>): List<Flashcard> =
        FlashcardReviewEngine.dueCards(cards, flashcardProgress.value)

    /** Ordered review queue: most-overdue first, then fresh cards. */
    fun reviewQueue(cards: List<Flashcard>): List<Flashcard> =
        FlashcardReviewEngine.sessionOrder(cards, flashcardProgress.value)

    // ==========================================
    // Tutor chat helpers (ADDITIVE)
    // ==========================================

    /** Parent encouragement: lights the 💛 badge on the bubble (flag stored by the caller). */
    fun sendParentEncouragement() {
        ParentPrefs.setMessageWaiting(getApplication(), true)
        _statusBanner.value = "Encouragement sent — it will glow on the student's bubble. 💛"
    }

    /** Quick-reply suggestion chips shown above the chat input per agent. */
    fun agentSuggestions(agentId: String): List<String> = when (agentId) {
        "danish" -> listOf("Explain factorisation simply", "Quiz me on quadratics", "Show common sign mistakes")
        "zoya" -> listOf("Explain Newton's laws visually", "Quiz me on forces", "Give a worked example")
        "ayesha" -> listOf("How do I write a 10-mark summary?", "Give me vocabulary drills", "Check my paragraph")
        "bilal" -> listOf("Give me a logic puzzle", "Explain this pattern", "Speed drill please")
        "exam_coach" -> listOf("Top Paper 1 pitfalls?", "How are method marks awarded?", "Make me a revision plan")
        "career_guide" -> listOf("Best subjects for Computer Science?", "Engineering vs Medicine?", "Map my A-Level cluster")
        else -> listOf("Explain simply", "Quiz me", "Give an example")
    }
}
