#!/bin/bash
sed -i '/val signals = mapOf(/i\
            viewModelScope.launch {\
                repository.recordEvent(\
                    com.example.core.model.EventType.STUDENT_ONBOARDED,\
                    "{ \"psychometricSignals\": { \"persistence\": ${newResponses[0] / 5f}, \"confidence\": ${newResponses[1] / 5f}, \"frustration_response\": ${newResponses[2] / 5f}, \"metacognition\": ${newResponses[3] / 5f} } }"\
                )\
            }\
' app/src/main/java/com/example/presentation/OLevelViewModel.kt

sed -i '/val signals = mapOf(/i\
            viewModelScope.launch {\
                repository.recordEvent(\
                    com.example.core.model.EventType.STUDENT_ONBOARDED,\
                    "{ \"cognitiveSignals\": { \"pattern_reasoning\": ${if (newResponses[0]) 1f else 0.5f}, \"logical_reasoning\": ${if (newResponses[1]) 1f else 0.5f}, \"working_memory\": ${if (newResponses[2]) 1f else 0.5f}, \"spatial_reasoning\": ${if (newResponses[3]) 1f else 0.5f} } }"\
                )\
            }\
' app/src/main/java/com/example/presentation/OLevelViewModel.kt

