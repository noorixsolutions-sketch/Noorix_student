# Noorix Learning

Noorix Learning is an offline-first Android study app for Cambridge O-Level learning. It includes signed curriculum packages, adaptive practice, symbolic math verification, local/remote tutor routing, and a knowledge-graph-linked Learning Hub.

## Learning Hub content

Learning content is stored in `app/src/main/assets/curriculum/learning_content.json`. Each explainer, flashcard, and quiz is linked to a signed course-package concept ID such as `concept:4024:factorisation`. The app filters content by the selected knowledge-graph concept.

Explainer videos use YouTube embed URLs generated from a validated video ID. Only trusted, curated video IDs should be committed to the asset; replace the sample IDs with the organisation's approved educational videos before release. Flashcards support reveal/hide behavior, and quizzes provide immediate answer feedback and explanations.

## Local build

Install Android SDK 36 and configure either `ANDROID_HOME` or `local.properties` with `sdk.dir=/path/to/android-sdk`. The CI workflow creates its own debug keystore. For a local debug build, generate a debug keystore at the repository root or update the Gradle signing configuration to use the standard Android debug keystore.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Data protection

The Room database contains student learning events, account profile data, audit records, and tutor chat history. It is explicitly excluded from Android cloud backup and device transfer. If cloud sync is introduced, define an authenticated, acknowledged server protocol before enabling it.
