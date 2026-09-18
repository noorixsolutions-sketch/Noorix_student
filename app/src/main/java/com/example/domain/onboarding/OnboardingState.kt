package com.example.domain.onboarding

/**
 * Two-mode product model (docs/two-mode-experience.md).
 *
 * The product is a guided learning system with an optional program commitment —
 * not an AI teacher that interrogates the learner about what they want.
 *
 *  - [LearnerMode.EXPLORE]: free learning, no enrollment, no commitment. The learner
 *    can ask about anything; evidence is collected quietly from ordinary interaction.
 *  - [LearnerMode.GUIDED]: the learner chose a program. The system progressively
 *    takes responsibility: baseline → assessment → diagnosis → plan → teaching →
 *    practice → assessment → adaptation → progression.
 */
enum class LearnerMode { EXPLORE, GUIDED }

/**
 * Age bands change the interaction model — presentation, language, assessment
 * method, pacing and motivation. The learning engine itself stays common.
 */
enum class AgeBand(val minAge: Int, val maxAge: Int) {
    EARLY(4, 7),     // KG / early primary
    PRIMARY(8, 11),  // primary / middle school
    SECONDARY(12, 16); // secondary / O-Level

    companion object {
        fun fromAge(age: Int): AgeBand = when {
            age in EARLY.minAge..EARLY.maxAge -> EARLY
            age in PRIMARY.minAge..PRIMARY.maxAge -> PRIMARY
            else -> SECONDARY
        }
    }
}

/**
 * Per-band interaction profile: the copy, field visibility and assessment framing a
 * screen renders. One profile per band — the UI reads this instead of hard-coding
 * "candidate", "calibration" or IRT language.
 */
data class AgeBandProfile(
    val band: AgeBand,
    val label: String,
    val emoji: String,
    /** Home greeting line. */
    val greeting: String,
    /** Guided profile step. */
    val profileTitle: String,
    val profileSub: String,
    val ageOptions: List<Int>,
    val stageOptions: List<String>,
    val profileCta: String,
    /** O-Level-only fields (target grade, exam session, daily minutes). */
    val showTargetGrade: Boolean,
    val showExamSession: Boolean,
    val showDailyMinutes: Boolean,
    /** First embedded check (the baseline, delivered as learning, not an exam). */
    val checkEmoji: String,
    val checkIntro: String,
    val checkSub: String,
    val checkButton: String,
    val readyTitle: String,
    val readySub: String,
    /** Progress screen headline. */
    val progressHeadline: String,
    /** Curriculum knowledge-graph stage order (1..4 teachable) this band lives in. */
    val kgStageOrder: Int
) {
    companion object {
        val ofEARLY = AgeBandProfile(
            band = AgeBand.EARLY,
            label = "Little Learner",
            emoji = "🌱",
            greeting = "Let's learn something fun today!",
            profileTitle = "First, a tiny bit about you",
            profileSub = "Just so I know how to talk to you. An adult can help.",
            ageOptions = listOf(4, 5, 6, 7),
            stageOptions = listOf("Nursery (KG)", "KG 1", "KG 2", "Grade 1", "Grade 2", "Grade 3"),
            profileCta = "Next — there's a little game after!",
            showTargetGrade = false,
            showExamSession = false,
            showDailyMinutes = false,
            checkEmoji = "🎮",
            checkIntro = "Let's play a little number game so I can see what you already know.",
            checkSub = "No marks, no getting it wrong — just playing.",
            checkButton = "Let's play!",
            readyTitle = "You're all set!",
            readySub = "I'll keep track of what you learn and help you take the next little step.",
            progressHeadline = "What we've learned",
            kgStageOrder = 1
        )

        val ofPRIMARY = AgeBandProfile(
            band = AgeBand.PRIMARY,
            label = "Explorer",
            emoji = "🚀",
            greeting = "Ready for today's learning?",
            profileTitle = "First, a quick hello",
            profileSub = "A couple of taps and we can start.",
            ageOptions = listOf(8, 9, 10, 11),
            stageOptions = listOf("Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6"),
            profileCta = "Next",
            showTargetGrade = false,
            showExamSession = false,
            showDailyMinutes = false,
            checkEmoji = "🧩",
            checkIntro = "I'll ask you a few quick questions to find the best place to start.",
            checkSub = "There are no marks here — I'm just figuring out where you are.",
            checkButton = "Start the quick check",
            readyTitle = "You're all set!",
            readySub = "Here's a small first plan. We'll keep adjusting it as you learn.",
            progressHeadline = "Your progress",
            kgStageOrder = 2
        )

        val ofSECONDARY = AgeBandProfile(
            band = AgeBand.SECONDARY,
            label = "O-Level Learner",
            emoji = "🎯",
            greeting = "Where shall we pick up from?",
            profileTitle = "Let's set up your learning journey",
            profileSub = "This helps me pick the right pace. You can change any of it later.",
            ageOptions = listOf(12, 13, 14, 15, 16),
            stageOptions = listOf(
                "Pre-O-Level (Grade 9)",
                "O-Level Year 1 (Grade 10)",
                "O-Level Year 2 (Grade 11)",
                "Private Candidate"
            ),
            profileCta = "Continue",
            showTargetGrade = true,
            showExamSession = true,
            showDailyMinutes = true,
            checkEmoji = "📋",
            checkIntro = "I'll give you a few questions across the topics you're expected to know.",
            checkSub = "There are no marks here. I'm figuring out where to start.",
            checkButton = "Start the check-in",
            readyTitle = "You're all set.",
            readySub = "Here's where we'll start. I'll keep adjusting quietly as you go.",
            progressHeadline = "Your progress",
            kgStageOrder = 4
        )

        fun of(band: AgeBand): AgeBandProfile = when (band) {
            AgeBand.EARLY -> ofEARLY
            AgeBand.PRIMARY -> ofPRIMARY
            AgeBand.SECONDARY -> ofSECONDARY
        }
    }
}

/**
 * Maps a learner's age + stage choice onto a curriculum knowledge-graph stage.
 * The KG (generated from the master branch of Cambridge_Curriculum_Library) carries
 * 9 stages; stages 1–4 are the teachable range for this app (Pre-K → O-Level).
 */
object CurriculumStageMapper {
    fun stageOrderFor(band: AgeBand, stageLabel: String): Int = when (band) {
        AgeBand.EARLY -> AgeBandProfile.ofEARLY.kgStageOrder
        AgeBand.PRIMARY -> AgeBandProfile.ofPRIMARY.kgStageOrder
        AgeBand.SECONDARY -> when {
            // Pre-O-Level sits in Lower Secondary (stages 7–9) on the KG.
            stageLabel.contains("Pre-O") || stageLabel.contains("Grade 9") -> 3
            else -> AgeBandProfile.ofSECONDARY.kgStageOrder
        }
    }

    /** Human stage name for the KG stage order, e.g. "Early Years Pre-K". */
    fun stageNameFor(order: Int): String = when (order) {
        1 -> "Early Years (Pre-K)"
        2 -> "Primary (Stages 1–6)"
        3 -> "Lower Secondary (Stages 7–9)"
        4 -> "O-Level (Upper Secondary)"
        else -> "Stage $order"
    }
}

enum class OnboardingPhase {
    /** Conversational choice: explore something, or build a learning plan. */
    WELCOME,
    /** Tiny, age-adaptive profile (guided path only). */
    GUIDED_PROFILE,
    /** Embedded first check — assessment as part of learning, never an entrance exam. */
    GUIDED_FIRST_CHECK,
    /** Friendly "you're all set" preview before entering the app. */
    GUIDED_READY,
    COMPLETE
}

data class OnboardingState(
    val phase: OnboardingPhase = OnboardingPhase.WELCOME,
    /** Null until the learner picks a path on the welcome screen. */
    val mode: LearnerMode? = null,
    val learnerName: String = "",
    val age: Int? = null,
    /** Stage label chosen in the profile step (KG-stage friendly wording). */
    val stage: String = "",
    /** O-Level only; blank for younger bands (UI must treat blank as "not set"). */
    val targetGrade: String = "",
    val targetExamSession: String = "",
    val dailyTargetMinutes: Int = 45,
    val isComplete: Boolean = false
) {
    val band: AgeBand
        get() = age?.let { AgeBand.fromAge(it) } ?: AgeBand.SECONDARY

    val profile: AgeBandProfile
        get() = AgeBandProfile.of(band)

    val kgStageOrder: Int
        get() = CurriculumStageMapper.stageOrderFor(band, stage)

    /** True when a guided learner has enough profile data to skip the profile step. */
    val hasProfile: Boolean
        get() = age != null && stage.isNotBlank()
}

object OnboardingData {
    /** Kept for the STUDENT_ONBOARDED payload contract read by ProjectionEngine. */
    val targetGradeOptions = listOf("A*", "A", "B", "C")

    val examSessionOptions = listOf(
        "May/June 2027",
        "Oct/Nov 2026",
        "May/June 2026",
        "Oct/Nov 2027"
    )

    val dailyMinutesOptions = listOf(30, 45, 60, 90)
}
