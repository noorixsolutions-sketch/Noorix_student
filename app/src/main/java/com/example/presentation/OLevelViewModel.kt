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
import com.example.core.model.GraphStage
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
import com.example.domain.ai.OpenRouterPrefs
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.domain.onboarding.AgeBand
import com.example.domain.onboarding.LearnerMode
import com.example.domain.onboarding.LearnerPrefs
import com.example.domain.onboarding.OnboardingState
import com.example.domain.onboarding.OnboardingPhase
import com.example.domain.diagnostic.BuiltInCheckinBank
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
    PARENT,
    // Two-mode experience (docs/two-mode-experience.md §5): primary destinations.
    PROGRESS,
    PROGRAM
}

class OLevelViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LearningRepository(application.applicationContext)

    // Two-mode model (docs/two-mode-experience.md): a returning learner goes
    // straight to Home in the mode they chose; onboarding never replays.
    private val persistedLearner = runCatching { LearnerPrefs.load(application.applicationContext) }.getOrNull()

    val currentTab = MutableStateFlow(
        if (persistedLearner != null) AppNavigationTab.DASHBOARD else AppNavigationTab.ONBOARDING
    )
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

    private val _openRouterModelsLoading = MutableStateFlow(false)
    val openRouterModelsLoading: StateFlow<Boolean> = _openRouterModelsLoading.asStateFlow()

    private val _openRouterModelsError = MutableStateFlow<String?>(null)
    val openRouterModelsError: StateFlow<String?> = _openRouterModelsError.asStateFlow()

    init {
        // Restore persisted cloud config, then load the free-models catalogue
        // (the /models endpoint is public, so this works with no key).
        val (savedKey, savedModel) = OpenRouterPrefs.load(getApplication())
        _openRouterApiKey.value = savedKey
        _selectedOpenRouterModel.value = savedModel.ifBlank { null }
        agentManager.setOpenRouterConfig(savedKey, savedModel.ifBlank { null })
        refreshOpenRouterModels()
    }

    fun updateOpenRouterApiKey(key: String) {
        _openRouterApiKey.value = key
        OpenRouterPrefs.saveApiKey(getApplication(), key)
        agentManager.setOpenRouterConfig(key, _selectedOpenRouterModel.value)
    }

    /**
     * Reloads the free-models catalogue from OpenRouter, newest list every
     * time. Public endpoint — works with no key; a saved key is attached
     * when present so key-gated free models appear too.
     */
    fun refreshOpenRouterModels() {
        viewModelScope.launch {
            _openRouterModelsLoading.value = true
            _openRouterModelsError.value = null
            try {
                val key = _openRouterApiKey.value
                val auth = key.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
                val response = openRouterApi.getModels(auth)
                val free = response.data.filter { it.isFree }.sortedBy { it.displayName.lowercase() }
                _openRouterModels.value = free
                if (free.isEmpty()) {
                    _openRouterModelsError.value = "No free models found. Check your connection and retry."
                } else {
                    val persisted = _selectedOpenRouterModel.value
                    if (persisted.isNullOrBlank()) {
                        selectOpenRouterModel(free.first().id)
                    } else if (free.none { it.id == persisted }) {
                        _openRouterModelsError.value =
                            "Saved model \"" + persisted + "\" is no longer free — pick a new one below."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _openRouterModelsError.value = "Couldn't load models: ${e.message ?: "network error"}"
            } finally {
                _openRouterModelsLoading.value = false
            }
        }
    }

    fun selectOpenRouterModel(modelId: String) {
        _selectedOpenRouterModel.value = modelId
        OpenRouterPrefs.saveModelId(getApplication(), modelId)
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
                
                var rawEmail = ""
                try {
                    val parts = googleCredential.idToken.split(".")
                    if (parts.size == 3) {
                        val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                        val json = org.json.JSONObject(payload)
                        rawEmail = json.optString("email", "")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

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

    // ==========================================
    // Two-mode onboarding (docs/two-mode-experience.md)
    // The learner chooses whether to be guided. Once guided, the system
    // determines the direction. The learner stays free to explore at any time.
    // Assessment is continuous and embedded in learning, never an entrance exam.
    // ==========================================

    private val _onboardingState = MutableStateFlow(persistedLearner ?: OnboardingState())
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

    /** The learner's band (defaults to SECONDARY until an age is chosen). */
    fun learnerBand(): AgeBand = _onboardingState.value.band

    /** Explore path: zero friction — no name, no age, no questions. */
    fun chooseExploreMode() {
        val state = _onboardingState.value.copy(
            mode = LearnerMode.EXPLORE,
            isComplete = true,
            phase = OnboardingPhase.COMPLETE
        )
        _onboardingState.value = state
        saveLearnerPrefs()
        repository.setDiagnosticBankOverride(null)
        viewModelScope.launch {
            // Quiet evidence start: the event log records the learner's entry so
            // exploration can feed the same student model as the guided program.
            repository.recordEvent(
                com.example.core.model.EventType.STUDENT_ONBOARDED,
                org.json.JSONObject().apply {
                    put("studentName", state.learnerName.ifBlank { "Learner" })
                    put("learnerMode", "EXPLORE")
                }.toString()
            )
        }
        setTab(AppNavigationTab.DASHBOARD)
    }

    /**
     * Tiny, age-adaptive profile (guided path). Records the STUDENT_ONBOARDED
     * event with the payload contract ProjectionEngine reads, so the student
     * model — and the diagnostic prior — are calibrated before the first check.
     */
    fun updateGuidedProfile(
        name: String,
        age: Int,
        stage: String,
        targetGrade: String,
        targetExamSession: String,
        dailyMinutes: Int
    ) {
        _onboardingState.value = _onboardingState.value.copy(
            mode = LearnerMode.GUIDED,
            learnerName = name,
            age = age,
            stage = stage,
            targetGrade = targetGrade,
            targetExamSession = targetExamSession,
            dailyTargetMinutes = dailyMinutes
        )
        viewModelScope.launch {
            val payload = org.json.JSONObject().apply {
                put("studentName", name.ifBlank { "Learner" })
                put("candidateGrade", stage)
                put("candidateAge", age)
                put("targetGrade", targetGrade)
                put("targetExamSession", targetExamSession)
                put("dailyTargetMinutes", dailyMinutes)
                put("learnerMode", "GUIDED")
            }.toString()
            repository.recordEvent(
                com.example.core.model.EventType.STUDENT_ONBOARDED,
                payload
            )
        }
    }

    /**
     * Enter the guided path from the welcome screen or a later
     * "Build my learning plan" tap. A learner with a saved profile skips the
     * profile step and goes straight to the first check.
     */
    fun beginGuidedOnboarding() {
        val state = _onboardingState.value
        val phase = if (state.hasProfile) OnboardingPhase.GUIDED_FIRST_CHECK else OnboardingPhase.GUIDED_PROFILE
        _onboardingState.value = state.copy(mode = LearnerMode.GUIDED, phase = phase)
        setTab(AppNavigationTab.ONBOARDING)
        if (phase == OnboardingPhase.GUIDED_FIRST_CHECK) startGuidedFirstCheck()
    }

    /**
     * The embedded first check. Younger bands run their built-in number game /
     * quick check; secondary learners run the adaptive questions of the active
     * signed course package. Same engine, same student model.
     */
    fun startGuidedFirstCheck() {
        val bank = when (learnerBand()) {
            AgeBand.EARLY, AgeBand.PRIMARY -> BuiltInCheckinBank.forBand(learnerBand())
            AgeBand.SECONDARY -> null // use the active package bank
        }
        repository.setDiagnosticBankOverride(bank)
        startDiagnostic()
    }

    /** Finish: persist the choice, then enter the app in the chosen mode. */
    fun finishOnboarding() {
        _onboardingState.value = _onboardingState.value.copy(
            phase = OnboardingPhase.COMPLETE,
            isComplete = true
        )
        saveLearnerPrefs()
        setTab(AppNavigationTab.DASHBOARD)
    }

    private fun saveLearnerPrefs() {
        runCatching { LearnerPrefs.save(getApplication(), _onboardingState.value) }
    }

    fun startOnboardingPhase(phase: OnboardingPhase) {
        _onboardingState.value = _onboardingState.value.copy(phase = phase)
        if (phase == OnboardingPhase.GUIDED_FIRST_CHECK) {
            startGuidedFirstCheck()
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

    // ==========================================
    // Curriculum knowledge graph — the journey map (Pre-K → IB)
    // The master-branch material (9 stages, 165 subjects) drives the stage
    // selection in onboarding, the Explore browse and the Program screen.
    // ==========================================

    val kgStages: StateFlow<List<GraphStage>> = curriculumGraph
        .map { graph -> graph?.stages.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun kgStageFor(order: Int): GraphStage? =
        curriculumGraph.value?.stages?.firstOrNull { it.order == order }

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

    /**
     * Explore's conversational entry (docs/two-mode-experience.md §2.1):
     * "Explain photosynthesis." — always delivers the question to the tutor,
     * then opens the chat so the learner sees the answer stream in.
     */
    fun askTutor(agentId: String, question: String) {
        if (question.isBlank()) return
        _selectedAgentId.value = agentId
        sendAgentMessage(agentId, question)
        setTab(AppNavigationTab.AI_AGENTS)
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
        // Bank readiness gate: the first check renders "preparing your
        // questions" (with a skip) until a bank exists for the learner's band —
        // the built-in bank for younger bands is instant; secondary learners
        // wait on the signed course package. The UI gates rendering on
        // `diagnosticBankReady()` (DiagnosticScreen + onboarding first check).
        repository.startDiagnostic()
    }

    fun submitDiagnosticItem(question: QuestionItem, selectedIndex: Int) {
        viewModelScope.launch {
            repository.submitDiagnosticAnswer(question, selectedIndex)
        }
    }

    fun getNextDiagnosticQuestion(): QuestionItem? {
        val session = diagnosticSession.value
        val questions = repository.currentDiagnosticBank()
        return com.example.domain.diagnostic.AdaptiveDiagnosticEngine.getNextQuestion(session, questions)
    }

    /** True when the current band has a question bank for the check-in. */
    fun diagnosticBankReady(): Boolean = repository.currentDiagnosticBank().isNotEmpty()

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
