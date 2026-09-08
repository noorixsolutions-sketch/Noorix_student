#!/bin/bash
sed -i '33a\
import com.example.domain.onboarding.OnboardingState\
import com.example.domain.onboarding.OnboardingPhase\
import com.example.domain.onboarding.OnboardingData\
' app/src/main/java/com/example/presentation/OLevelViewModel.kt

sed -i '/enum class AppNavigationTab {/a\
    ONBOARDING,' app/src/main/java/com/example/presentation/OLevelViewModel.kt

sed -i '/val currentTab = MutableStateFlow(AppNavigationTab.DASHBOARD)/c\
    val currentTab = MutableStateFlow(AppNavigationTab.ONBOARDING)' app/src/main/java/com/example/presentation/OLevelViewModel.kt

sed -i '/val mediaRecord: StateFlow<MediaLearningRecord?> = repository.mediaRecord/a\
\
    // Onboarding State\
    private val _onboardingState = MutableStateFlow(OnboardingState())\
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()\
\
    fun startOnboardingPhase(phase: OnboardingPhase) {\
        _onboardingState.value = _onboardingState.value.copy(phase = phase)\
        if (phase == OnboardingPhase.TEST_C_CURRICULUM) {\
            startDiagnostic()\
        }\
        if (phase == OnboardingPhase.COMPLETE) {\
            setTab(AppNavigationTab.DASHBOARD)\
        }\
    }\
\
    fun submitTestAAnswer(value: Int) {\
        val state = _onboardingState.value\
        val newResponses = state.testAResponses + value\
        if (state.testAIndex + 1 < OnboardingData.testA.size) {\
            _onboardingState.value = state.copy(testAIndex = state.testAIndex + 1, testAResponses = newResponses)\
        } else {\
            // Calculate pseudo signals\
            val signals = mapOf(\
                "persistence" to (newResponses[0] / 5f),\
                "confidence" to (newResponses[1] / 5f),\
                "frustration_response" to (newResponses[2] / 5f),\
                "metacognition" to (newResponses[3] / 5f)\
            )\
            _onboardingState.value = state.copy(\
                testAResponses = newResponses,\
                psychometricSignals = signals,\
                phase = OnboardingPhase.TEST_B_COGNITIVE\
            )\
        }\
    }\
\
    fun submitTestBAnswer(selectedIndex: Int) {\
        val state = _onboardingState.value\
        val q = OnboardingData.testB[state.testBIndex]\
        val isCorrect = selectedIndex == q.correctIndex\
        val newResponses = state.testBResponses + isCorrect\
        \
        if (state.testBIndex + 1 < OnboardingData.testB.size) {\
            _onboardingState.value = state.copy(testBIndex = state.testBIndex + 1, testBResponses = newResponses)\
        } else {\
            val signals = mapOf(\
                "pattern_reasoning" to if (newResponses[0]) 1f else 0.5f,\
                "logical_reasoning" to if (newResponses[1]) 1f else 0.5f,\
                "working_memory" to if (newResponses[2]) 1f else 0.5f,\
                "spatial_reasoning" to if (newResponses[3]) 1f else 0.5f\
            )\
            _onboardingState.value = state.copy(\
                testBResponses = newResponses,\
                cognitiveSignals = signals,\
                phase = OnboardingPhase.TEST_C_CURRICULUM\
            )\
            startDiagnostic()\
        }\
    }\
' app/src/main/java/com/example/presentation/OLevelViewModel.kt
