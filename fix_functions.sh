#!/bin/bash
cat << 'REPLACE' > tmp_funcs.txt
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
REPLACE

perl -0777 -pi -e 's/fun submitTestAAnswer\([\s\S]*?startDiagnostic\(\)\s*\}\s*\}/`cat tmp_funcs.txt`/e' app/src/main/java/com/example/presentation/OLevelViewModel.kt

