#!/bin/bash
sed -i 's/"{ "cognitiveSignals":/"{ \\"cognitiveSignals\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"psychometricSignals":/\\"psychometricSignals\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"persistence":/\\"persistence\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"confidence":/\\"confidence\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"frustration_response":/\\"frustration_response\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"metacognition":/\\"metacognition\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"pattern_reasoning":/\\"pattern_reasoning\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"logical_reasoning":/\\"logical_reasoning\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"working_memory":/\\"working_memory\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/"spatial_reasoning":/\\"spatial_reasoning\\":/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
sed -i 's/} }"/} }"/g' app/src/main/java/com/example/presentation/OLevelViewModel.kt
