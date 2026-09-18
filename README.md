# Noorix Learning

Noorix Learning is an offline-first Android study app spanning KG → O-Level. It includes signed curriculum packages, adaptive practice, symbolic math verification, local/remote tutor routing, and a curriculum knowledge graph (9 stages, 165 subjects — generated from the `master` branch of `Cambridge_Curriculum_Library`).

## Two-mode experience (see `docs/two-mode-experience.md`)

The product is a **guided learning system with an optional program commitment**, not an AI teacher that asks the learner what they want:

- **Explore** — the learner can ask about anything ("Explain photosynthesis.", "Help me with this question."). No enrollment, no diagnostic, no commitment; evidence is collected quietly from ordinary interaction.
- **Guided Program** — the learner chooses to commit, and the system progressively takes responsibility: baseline → assessment → diagnosis → plan → teaching → practice → assessment → adaptation → progression.

Age changes the interaction model, not the engine: **Little Learner** (4–7), **Explorer** (8–11) and **O-Level Learner** (12–16) get different language, assessment framing, pacing and fields (`AgeBandProfile` in `domain/onboarding/`). The baseline assessment is embedded in the first learning moment — a number game for a five-year-old, a mark-free check-in for a fifteen-year-old — never an entrance exam. Younger bands run their baseline from `BuiltInCheckinBank` (same adaptive engine, same student model); O-Level learners run the signed course-package question bank.

The app opens conversationally ("Hi, I'm here to help you learn.") with exactly two paths — **Explore something** / **Build my learning plan**. After onboarding (persisted in `LearnerPrefs`, never replayed), Home is the learner's journey (*here's where we are, here's what we're working on, Continue*), alongside **Explore**, **Progress**, **Program** (the Pre-K → O-Level journey map from the knowledge graph) and **Tutors**. No student-facing surface exposes engine machinery (no θ, no "prior", no "calibration step") — see the copy guardrails in the doc.

## Learning Hub content

Learning content is stored in `app/src/main/assets/curriculum/learning_content.json`. Each explainer, flashcard, and quiz is linked to a signed course-package concept ID such as `concept:4024:factorisation`. The app filters content by the selected knowledge-graph concept.

Explainer videos use YouTube embed URLs generated from a validated video ID. Only trusted, curated video IDs should be committed to the asset; replace the sample IDs with the organisation's approved educational videos before release. Flashcards support reveal/hide behavior, and quizzes provide immediate answer feedback and explanations.

## Progress, gamification & study tools (additive layer)

These features are derived from — and never modify — the existing event log, signed packages, and verifier:

- **Progress analytics (Dashboard):** XP + level card, daily-goal ring (from the onboarding `dailyTargetMinutes`), trailing-7-day activity chart, blended accuracy, and a per-concept mastery breakdown. Pure derivation in `domain/analytics/LearningAnalyticsEngine.kt`.
- **Gamification:** XP rules per event type, level curve, consecutive-day streaks (with yesterday grace), and 10 badges (First Steps, Daily Grind, Week Warrior, XP tiers, Sharpshooter, Math Prover, Concept Master, Comeback). Pure derivation in `domain/gamification/GamificationEngine.kt`.
- **Study tools (Learning Hub):** full-text search across videos/flashcards/quizzes, bookmarks with a Saved filter, per-concept notes, and a spaced-repetition Review Mode (SM-2 lite: Again/Hard/Good/Easy scheduling). Persisted in a standalone `study_tools.db` (`data/local/StudyToolsDatabase.kt`) so the core evidence database and its migrations stay untouched.
- **Bubble chat + math keyboard (Tutors & Math Lab):** tutor messages render with avatars, timestamps, markdown-lite (**bold**, `code`, bullets), copy actions, and an animated streaming cursor; quick-reply suggestion chips and a shared cursor-aware math symbol keyboard (`presentation/components/MathKeyboardAccessory.kt`) sit above the input. Math Lab's symbol palette uses the same shared symbols with verifier-compatible inserts.

Unit tests for the three pure engines live in `app/src/test/java/com/example/` (`GamificationEngineTest`, `LearningAnalyticsEngineTest`, `FlashcardReviewEngineTest`) and run with `./gradlew testDebugUnitTest`.

## Primary surfaces (Goal Architecture v1)

Additive implementation of the goal's two primary surfaces plus supporting layers. Everything is behind Settings (Audit → Primary Surfaces) and off by default:

- **Aisha Bubble** (`presentation/bubble/`): foreground `AishaBubbleService` hosting a 56dp overlay bubble in every app. Tap → tutor chat, double-tap → 8-petal fan-out (Math, Writing, Quiz, Canvas, Library, Notes, Voice, Media — deep-linked into existing tabs), drag → snap to edge, swipe up → quick HUD (auto-dismiss 4s). Halo/badges follow the §3 state contract (`domain/bubble/BubbleStateMapper.kt`): focus tint, amber streak-at-risk pulse, confusion tilt, 🎯 mock badge, 💛 parent-message badge.
- **Canvas overlay** (same service): draw with a finger on top of any app (top-anchored window + toolbar window; touches elsewhere pass through). Strokes are session-local in v1.
- **Tutor Keyboard** (`presentation/keyboard/`): system IME with glyph strip (streak · today XP · level), 6-persona Tutor row (D/Z/A/B/E/C deep-linking to tutor chats), and swappable modes — QWERTY (+symbols), Math (shared verifier-compatible symbols), Quiz (A/B/C/D + True/False). Enable via Settings → Enable in Settings → Switch keyboard.
- **HUD glyph strip** (`components/HudGlyphStrip.kt`): ambient today-bar + streak + subject dots under the top bar in main contexts.
- **Parent Dashboard** (Settings → Open Parent Dashboard): PIN-gated (default `1234`, changeable) weekly narrative composed on-device from measured stats (`domain/parent/`), attention items, exam horizon, and actions — encouragement lights the 💛 badge on the bubble.
- **BKT mastery** (`domain/mastery/BktEngine.kt`): Bayesian Knowledge Tracing P(L) per concept runs alongside the existing projection model and is exposed on concept rows (`bktMastery`); the bubble's confusion signal uses recent wrong-answer runs.

Both primary surfaces read through `domain/overlay/OverlayStatsProvider.kt`, so bubble, keyboard, and Dashboard can never disagree. Tests: `BubbleStateMapperTest`, `BktEngineTest`, `ParentNarrativeTest`.

## Local build

Install Android SDK 36 and configure either `ANDROID_HOME` or `local.properties` with `sdk.dir=/path/to/android-sdk`. The CI workflow creates its own debug keystore. For a local debug build, generate a debug keystore at the repository root or update the Gradle signing configuration to use the standard Android debug keystore.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Data protection

The Room database contains student learning events, account profile data, audit records, and tutor chat history. It is explicitly excluded from Android cloud backup and device transfer. The standalone study-tools database (`study_tools.db`: bookmarks, notes, review schedule) is excluded as well. If cloud sync is introduced, define an authenticated, acknowledged server protocol before enabling it.
