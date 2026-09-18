# Noorix Student — Two-Mode Learning Experience

Status: **Approved direction — implemented in the onboarding, home, Explore, Progress,
Program and check-in surfaces.** This document is the source of truth for the product's
interaction model. The learning engine (diagnostic → student model → academic state →
plan synthesis → intervention → continuous recalibration) is unchanged; what changes is
that the UI now **embodies the engine's behavior** instead of exposing its machinery.

## 1. Product principles

> **The learner chooses whether to be guided.**
> **Once guided, the system determines the direction.**
> **The learner remains free to explore at any time.**
> **Assessment is continuous and embedded in learning, not an entrance exam.**

Consequences that bind every screen:

1. The app is a **guided learning system with an optional program commitment** — not an
   AI teacher that interrogates the learner about what they want.
2. No screen may force a commitment, a diagnostic, or a profile before the learner has
   received value.
3. The system is the **guide**: it does not constantly demand
   ("YOU NEED TO STUDY X") and it does not constantly defer
   ("WHAT WOULD YOU LIKE TO STUDY?"). It observes, suggests, and moves forward when the
   evidence says the learner is ready:
   - "You've been doing well with equations. There's one small thing still getting in
     your way. Let's work on it."
   - "That looks better. Let's try one slightly harder question."
   - "You seem comfortable with this now. I think we're ready to move forward."
   - "We can stop here. Tomorrow I'll bring back two questions to make sure this sticks."
4. The learning engine is **common across all ages**. What may differ by age is
   **presentation, language, assessment method, pacing and motivation — and nothing else**.

## 2. The two modes

### 2.1 Explore

- Entry: the welcome screen, or the Explore tab, or the "Explore" card on Home.
- The learner freely says: *"Explain photosynthesis."*, *"I don't understand
  simultaneous equations."*, *"Help me with this question."*
- **No enrollment. No diagnostic interrogation. No commitment. No disruption.**
- The system teaches the requested topic (tutor chat, hub explainers, flashcards,
  quizzes, Math Lab) and **quietly collects learning evidence** where appropriate —
  answers, practice, review — into the same event log the guided program uses.
- Explore is not a lesser mode: it is the default state of a fresh install, and it is
  always available.

### 2.2 Guided Program

- Entry: "Build my learning plan" (welcome screen, Home card, or Program screen).
- Commitment is explicit and voluntary: the learner chooses a learning journey, and the
  system then **progressively** takes responsibility for:

  baseline → assessment → diagnosis → plan → teaching → practice → assessment →
  adaptation → progression

- **Progressively** is the operative word. Nothing is demanded up front:
  - The only profile information collected is a **tiny, age-adaptive set**
    (name, age, stage — and target/pace only for O-Level learners).
  - The baseline assessment happens **inside the first learning moment**, framed for
    the learner's age (a number game for a five-year-old; a mark-free check-in for a
    fifteen-year-old), not as an administrative prerequisite.
  - After that, assessment is **continuous**:

    interaction → evidence → hypothesis → teaching → evidence → correction

    not: ONBOARDING → gigantic test → permanent student model.

### 2.3 Switching

- Explore → Guided: one tap ("Build my learning plan") from Home or Program. If a
  profile already exists, the system goes straight to the check-in; it never re-asks.
- Guided → Explore: the Explore tab is always one tap away. Leaving the program
  suspends nothing and deletes nothing; the plan waits on Home.
- "Change my details / restart onboarding" stays available in Settings so a growing
  learner can update age, stage or target without re-doing anything.

## 3. Age-adaptive model

Age must change the interaction model. Three bands map onto the curriculum
knowledge graph's stages (the KG shipped in the app is generated from the
`master` branch of `Cambridge_Curriculum_Library`: 9 stages, 165 subjects,
825 resources, Pre-K → IB).

| | **Little Learner** (4–7) | **Explorer** (8–11) | **O-Level Learner** (12–16) |
|---|---|---|---|
| KG stage | 1 · Early Years Pre-K | 2 · Primary Stages 1–6 | 3 · Lower Secondary (Pre-O-Level) or 4 · O-Level Upper Secondary |
| Presentation | Pictures, voice-first design, very large tap targets, one idea per screen | Guided activities, lightweight explanations, small challenges | Increasingly explicit reasoning, structured practice, deeper progress information |
| Language | Almost no terminology; short sentences; encouragement | Friendly coaching words; "we'll figure it out together" | Plain academic language; optional rigorous diagnostics; exam vocabulary |
| Assessment | Implicit — a "little number game", 6 short items, no marks, no "wrong" | A few quick questions; "no marks here — I'm just finding where to start" | A mark-free check-in across expected topics; rigorous diagnostic optional later |
| Pacing | Very short activities; frequent praise | Small challenges; visible progress | Sprints toward exam horizon; spaced retrieval |
| Motivation | Streaks as "learning days", badges as stickers | XP, streaks, visible mastery growth | Exam horizon, target grade, readiness |
| Onboarding fields | Name, age, stage | Name, age, stage | Name, age, stage, **target grade, exam session, daily minutes** |
| Progress screen | What we learned; stickers | XP, goal ring, weekly activity, mastery, badges | All of the above + check-in quiz + exam horizon |

**The engine is the same in all three rows.** Only the wrapper changes:
`AgeBandProfile` (in `domain/onboarding/`) carries the copy, the field visibility and
the assessment framing; `BuiltInCheckinBank` (in `domain/diagnostic/`) supplies the
age-appropriate baseline items for younger bands because the signed question banks are
O-Level subjects (Cambridge 4024 Mathematics, 5054 Physics). Secondary learners use the
package question bank through the existing adaptive diagnostic, exactly as before.

### 3.1 The age → stage → bank routing

```
age (and stage choice) ──► AgeBand ──► KG stage (journey map)
                              │
                              ▼
              BuiltInCheckinBank (EARLY / PRIMARY)
              active course package bank (SECONDARY)
                              │
                              ▼
        same IRT diagnostic session ──► student model ──► plan synthesis
```

## 4. Onboarding (rewritten)

The old flow — "CALIBRATION STEP 1: CANDIDATE PROFILE" → class/age/target-grade/exam/
minutes → "Proceed to Diagnostic Assessment" — is gone. The new flow:

### Step 0 — Conversational welcome (all ages)

> **Hi, I'm here to help you learn.**
> Ask me about anything you're studying.
> Or, if you'd like, we can make a learning plan together and I'll keep track of
> what you're learning and help you progress.

Two paths, and only two:

- **Explore something** — "Learn anything, anytime. No sign-up, no pressure."
- **Build my learning plan** — "I'll guide you step by step and help you progress."

Footer: "You can change this anytime — there's no wrong choice."

### Step 1a — Explore path

Zero friction. No name, no age, no questions. The learner is in Explore mode and lands
on Home. Evidence collection starts silently with the first interaction. A later
"Build my learning plan" tap upgrades to Guided without re-asking what Explore never
collected.

### Step 1b — Guided path: tiny profile

- "Your name (or a nickname you like)" — optional text.
- "How old are you?" — age chips for the band the learner lands in (4–7 / 8–11 / 12–16).
- "What's your stage?" — stage chips **revealed only after age is chosen** (the form
  literally grows or shrinks with the age selection):
  - 4–7 → Nursery (KG), KG 1, KG 2, Grade 1, Grade 2, Grade 3
  - 8–11 → Grade 1 … Grade 6
  - 12–16 → Pre-O-Level (Grade 9), O-Level Year 1 (Grade 10), O-Level Year 2
    (Grade 11), Private Candidate — **plus**, and only plus: target grade (A*/A/B/C),
    exam session, and "minutes a day" (defaults, all changeable later).
- Copy is age-adaptive ("First, a tiny bit about you — an adult can help." vs.
  "This helps me pick the right pace. You can change any of it later.").

### Step 1c — Guided path: the first check (embedded assessment)

Framing per band:

- **4–7:** "Let's play a little number game so I can see what you already know."
  "No marks, no getting it wrong — just playing." → 6 built-in numeracy/pattern
  items, big buttons, encouragement.
- **8–11:** "I'll ask you a few quick questions to find the best place to start."
  "There are no marks here — I'm just figuring out where you are." → 6 built-in
  primary items.
- **12–16:** "I'll give you a few questions across the topics you're expected to know."
  "There are no marks here. I'm figuring out where to start." → adaptive questions
  from the signed course package (4–6 items, IRT-selected as before).

Skip is always offered ("Skip for now") — the system simply starts with default
hypotheses and corrects them through ordinary interaction, which is exactly what the
recalibration loop was built to do.

### Step 1d — Guided path: ready

A friendly "you're all set" screen: what we'll start with (first plan focus), the next
few steps in plain words, and "Let's get started." **No ability scores, no θ, no
"prior", no "velocity", no IRT percentages** — those numbers stay in the event log and
the audit trail where they belong.

### 4.1 Onboarding persistence

Completion, mode, name, age, stage, target and pace persist on-device
(`LearnerPrefs`). A returning learner goes straight to Home in the mode they chose;
onboarding never replays.

## 5. The two-mode home

Primary experience = **Home: "Your learning journey"** — one place that says:

> Here's where we are.
> Here's what we're working on.
> **Continue**

plus, alongside it, the three companion destinations.

| Tab | Purpose | Content |
|---|---|---|
| **Home** | The journey | Greeting (age-adaptive); the journey card — *where we are* (current focus / stage), *what we're working on* (plan step or explore invitation), **Continue**; the three destination cards; ask-a-tutor strip; (12–16) a quiet check-in entry |
| **Explore** | Learn anything without joining a program | Ask-a-tutor box (type a question → any tutor); browse the curriculum knowledge graph by **stage → subject** (the Pre-K → O-Level master material: syllabus/notes/past-papers/mark-scheme resource chips); the concept learning hub (videos, flashcards, quizzes, notes, review) for the active course |
| **Progress** | Understand how you're developing | XP + level, daily-goal ring, weekly activity, per-concept mastery, badges — the derived analytics; (12–16) the check-in quiz entry. Nothing here is a score report; it's the learner's own story of growth |
| **Program** | See the larger journey | The active plan (focus, how we'll start, stages with progress), the learner's **KG stage and its subjects** (the whole journey map), (12–16) the exam horizon (target grade, session); in Explore mode: an invitation to build the plan |
| **Tutors** | The learning crew | The existing agent roster (kept in the bar per stakeholder decision); each tutor deep-links into chat, quiz or Math Lab |

Everything else stays reachable where genuinely necessary — Math Lab (journey card +
Math Lab chip), check-in quiz (Progress / Home for 12–16), packages, audit, decay/sync
and the PIN-gated parent dashboard (Settings) — but none of it competes with the four
primary destinations.

### 5.1 Home by mode

- **Guided, plan active:** "Here's where we are." → current focus concept + plan
  step; "Continue" resumes the learning hub for that concept.
- **Guided, no plan yet:** "Your journey starts here." → "Take a quick check-in and
  I'll shape your first plan." (goes straight to the check-in if a profile exists).
- **Explore:** "Learn anything you like." → "Explore" (hub) and a secondary card:
  "Want me to guide you? Build my learning plan."

### 5.2 The counsellor, not the interrogator

Home never asks "what do you want to study?" and never commands "you must study X."
It states where things stand and offers the next step: the Continue button is always
one, and it comes from the plan, not from a menu.

## 6. Copy guardrails (what a KG child never reads)

Banned in student-facing surfaces (they may live in code, logs and the audit trail):

| Never show | Say instead |
|---|---|
| "CALIBRATION STEP 1: CANDIDATE PROFILE" | "First, a tiny bit about you" |
| "Bayesian prior ability", "expected learning velocity" | (nothing — it's just how we start) |
| "θ = 0.47 (IRT)", "Prior θ", "Calibration Confidence 62%" | "Question 3 of about 6" |
| "Psychometric & cognitive testing" | "a little number game" / "a few quick questions" |
| "Closed-loop adaptive calibration" | "I'll keep adjusting as you learn" |
| "Candidate", "Candidate Dashboard" | the learner's name, or "Learner" |
| "SYNTHESIZED ADAPTIVE PLAN", raw intervention enums ("WORKED_EXAMPLES") | "Here's how we'll start" + "Your plan now starts with worked examples." (`UiCopy.formatReplan`) |

Rule of thumb: if the sentence contains a statistics word, a Greek letter, or the word
*calibration*, it belongs in `AuditScreen`, not on a screen a six-year-old can see.

## 7. What did not change

- **Engines:** adaptive diagnostic (IRT item selection), planner + intervention
  efficacy, BKT mastery, projection from the event log, analytics, gamification,
  symbolic math verification, vision/OCR — all untouched.
- **Content pipeline:** signed course packages (`tools/sign_course_package.py`),
  the knowledge-graph generator, Learning Hub content linked to package concept IDs.
- **Trust:** audit hash chain, data purge, sync stub behavior, parent PIN gate.
- **Backend routing:** OpenRouter cloud with deterministic offline fallback for every
  tutor — the offline engine answers from the same bundled assets, so Explore works
  fully offline.

## 8. Known content boundary (honest limits)

- Signed, question-bearing course packages exist for **Cambridge 4024 Mathematics and
  5054 Physics** only. Younger bands therefore use `BuiltInCheckinBank` for the
  baseline (clearly framed as a game/quick-check, not syllabus assessment); as
  Pre-K/Primary packages are signed and added, the same routing automatically prefers
  the package bank when the learner's stage has one.
- The KG stages 5–9 (IGCSE, A-Level, AP, IB) are shown in the journey map for context
  but are not teachable yet; they render as "coming soon" rather than hidden.
