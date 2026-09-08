package com.example.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.CoursePackage
import com.example.core.model.LearningEvent
import com.example.core.model.LearningPlan
import com.example.core.model.MediaArtifact
import com.example.core.model.MediaLearningRecord
import com.example.core.model.QuestionItem
import com.example.core.model.Step
import com.example.core.model.StudentModel
import com.example.core.model.TransferTask
import com.example.core.model.VerificationResult
import com.example.data.curriculum.CoursePackageProvider
import com.example.data.local.AuditLogEntity
import com.example.domain.ai.NoorixAiAgentManager
import com.example.data.local.ChatMessageEntity
import com.example.domain.diagnostic.DiagnosticSessionState
import com.example.domain.planner.AdaptivePlannerEngine
import com.example.domain.repository.LearningRepository
import com.example.domain.sync.SyncBatchResult
import com.example.domain.vision.NotebookParseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.domain.onboarding.OnboardingState
import com.example.domain.onboarding.OnboardingPhase
import com.example.domain.onboarding.OnboardingData


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
    DIAGNOSTIC,
    SYMBOLIC_TUTOR,
    MEDIA_TRANSFER,
    DECAY_SYNC,
    AUDIT
}

class OLevelViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LearningRepository(application.applicationContext)

    val currentTab = MutableStateFlow(AppNavigationTab.ONBOARDING)
    val simulatedWallTimeMs: StateFlow<Long> = repository.simulatedWallTimeMs
    val activePackage: StateFlow<CoursePackage?> = repository.activePackage
    val studentModel: StateFlow<StudentModel> = repository.studentModel
    val activePlan: StateFlow<LearningPlan?> = repository.activePlan

    private val _openRouterApiKey = MutableStateFlow("")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _openRouterModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val openRouterModels: StateFlow<List<OpenRouterModel>> = _openRouterModels.asStateFlow()

    private val _selectedOpenRouterModel = MutableStateFlow<String?>(null)
    val selectedOpenRouterModel: StateFlow<String?> = _selectedOpenRouterModel.asStateFlow()

    private val _localModelUri = MutableStateFlow<Uri?>(null)
    val localModelUri: StateFlow<Uri?> = _localModelUri.asStateFlow()

    private val _localMmprojUri = MutableStateFlow<Uri?>(null)
    val localMmprojUri: StateFlow<Uri?> = _localMmprojUri.asStateFlow()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val openRouterApi = retrofit.create(OpenRouterApi::class.java)

    fun updateOpenRouterApiKey(key: String) {
        _openRouterApiKey.value = key
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
                    _selectedOpenRouterModel.value = response.data.first().id
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectOpenRouterModel(modelId: String) {
        _selectedOpenRouterModel.value = modelId
    }

    fun setLocalMmprojUri(uri: Uri) {
        _localMmprojUri.value = uri
    }

    fun setLocalModelUri(uri: Uri) {
        _localModelUri.value = uri
    }

    val diagnosticSession: StateFlow<DiagnosticSessionState> = repository.diagnosticSession
    val mediaRecord: StateFlow<MediaLearningRecord?> = repository.mediaRecord

    // Onboarding State
    private val _onboardingState = MutableStateFlow(OnboardingState())
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

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
            viewModelScope.launch {
                repository.recordEvent(
                    com.example.core.model.EventType.STUDENT_ONBOARDED,
                    "{ \"psychometricSignals\": { \"persistence\": ${signals["persistence"]}, \"confidence\": ${signals["confidence"]}, \"frustration_response\": ${signals["frustration_response"]}, \"metacognition\": ${signals["metacognition"]} } }"
                )
            }
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
            val signals = mapOf(
                "pattern_reasoning" to if (newResponses[0]) 1f else 0.5f,
                "logical_reasoning" to if (newResponses[1]) 1f else 0.5f,
                "working_memory" to if (newResponses[2]) 1f else 0.5f,
                "spatial_reasoning" to if (newResponses[3]) 1f else 0.5f
            )
            viewModelScope.launch {
                repository.recordEvent(
                    com.example.core.model.EventType.STUDENT_ONBOARDED,
                    "{ \"cognitiveSignals\": { \"pattern_reasoning\": ${signals["pattern_reasoning"]}, \"logical_reasoning\": ${signals["logical_reasoning"]}, \"working_memory\": ${signals["working_memory"]}, \"spatial_reasoning\": ${signals["spatial_reasoning"]} } }"
                )
            }
            _onboardingState.value = state.copy(
                testBResponses = newResponses,
                cognitiveSignals = signals,
                phase = OnboardingPhase.TEST_C_CURRICULUM
            )
            startDiagnostic()
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

    // Noorix AI Agent state
    private val agentManager = NoorixAiAgentManager(application.applicationContext, repository.chatMessageDao)
    private val _selectedAgentId = MutableStateFlow("danish")
    val selectedAgentId: StateFlow<String> = _selectedAgentId.asStateFlow()

    private val _agentStreamingText = MutableStateFlow<String?>(null)
    val agentStreamingText: StateFlow<String?> = _agentStreamingText.asStateFlow()

    private val _isAgentThinking = MutableStateFlow(false)
    val isAgentThinking: StateFlow<Boolean> = _isAgentThinking.asStateFlow()

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
            val mathPkg = CoursePackageProvider.createMath4024Package()
            val (success, msg) = repository.installAndActivatePackage(mathPkg)
            _statusBanner.value = msg
        }
    }

    fun installPhysicsCourse() {
        viewModelScope.launch {
            val physicsPkg = CoursePackageProvider.createPhysics5054Package()
            val (success, msg) = repository.installAndActivatePackage(physicsPkg)
            _statusBanner.value = msg
        }
    }

    fun installTamperedPackageTest() {
        viewModelScope.launch {
            val mathPkg = CoursePackageProvider.createMath4024Package()
            val tamperedManifest = mathPkg.manifest.copy(
                contentHash = "tampered_hash_00000000000000000000000000000000",
                signature = "TAMPERED_FORGED_SIGNATURE"
            )
            val tamperedPkg = mathPkg.copy(manifest = tamperedManifest)
            val (success, msg) = repository.installAndActivatePackage(tamperedPkg)
            _statusBanner.value = msg
        }
    }

    // ==========================================
    // Symbolic Verifier & Math OCR
    // ==========================================

    fun verifyCustomMathSteps(problem: String, steps: List<Step>, conceptId: String) {
        viewModelScope.launch {
            val res = repository.verifyMathSolution(problem, steps, conceptId)
            _verificationResult.value = res
            _statusBanner.value = if (res.isValid) {
                "Symbolic Math Verification: ALL STEPS VALID. Deterministic proof committed."
            } else {
                "Symbolic Math Verification: Invalid step ${res.firstInvalidStepIndex} (${res.errorType}). Error isolated."
            }
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
                _statusBanner.value = "Plan Evaluated: ${res.classification?.name} - ${res.diagnosisNote}"
            }
        }
    }

    fun replan(interventionType: String) {
        viewModelScope.launch {
            val p = repository.replanCurrent(interventionType)
            _statusBanner.value = "Plan Revised: Swapped to $interventionType. Expected Accuracy: ${(p.predictions.expectedAccuracy * 100).toInt()}%"
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
    // Offline Sync
    // ==========================================

    fun simulateOfflineSync() {
        viewModelScope.launch {
            val res = repository.simulateRemoteSyncBatch()
            _syncResult.value = res
            _statusBanner.value = res.summary
        }
    }

    // ==========================================
    // Audit & Purge
    // ==========================================

    fun purgeStudentData() {
        viewModelScope.launch {
            val proof = repository.purgeStudentData(studentModel.value.studentId)
            _statusBanner.value = "Student data purged. Proof: $proof"
        }
    }

    fun formatTime(timestampMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }
}
