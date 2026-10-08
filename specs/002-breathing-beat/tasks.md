---

description: "Task list for Breathing Beat"
---

# Tasks: Breathing Beat

**Input**: Design documents from `/specs/002-breathing-beat/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/core-api.md](./contracts/core-api.md), [quickstart.md](./quickstart.md)

**Tests**: REQUIRED and written first, per constitution Principle III. Where a test genuinely cannot come first, the pull request must say why and how the behavior was verified.

**Numbering**: tasks here are T2xx and measurements M2xx, so they never collide with feature 001's in conversation or in a commit message.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on incomplete work)
- **[Story]**: US1 to US3, matching the user stories in spec.md

---

## Phase 1: Setup

**Purpose**: capture evidence that stops existing before the code changes.

- [ ] T201 **Do this before installing any build that removes the arc.** Capture the real stored state from the phone with `adb shell run-as com.dnoel.binauralbeats cat files/app_state.json` and save it as a test fixture at `core/src/test/resources/stored-state-with-removed-arc.json`. Once a build without `DESCEND_THEN_VARY` has run on the device, this payload cannot be captured again, and it is the only honest input for the migration test in T203
- [ ] T202 Record in `research.md` what the captured payload actually contains: the stored `beatArc` value, whether a profile is present, and whether a session record is present. If the stored arc is not `DESCEND_THEN_VARY`, say so and construct the fixture by hand instead, noting that it is synthetic

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the migration and the parameters every story depends on.

**CRITICAL**: no user story work begins until this phase is complete.

- [ ] T203 Write `BeatArcMigrationTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/model/BeatArcMigrationTest.kt`: decoding the T201 fixture yields a state whose profile, settings and session record all survive intact, and whose arc reads as `DESCEND_THEN_HOLD`. Also assert the negative case the fallback rule would otherwise produce, namely that the state is NOT reduced to defaults
- [ ] T204 Remove `DESCEND_THEN_VARY` from `BeatArc` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/model/Model.kt`, leaving `DESCEND_THEN_HOLD` and `CONSTANT`, and add a serializer mapping any unrecognized name to `DESCEND_THEN_HOLD` (FR-118a, research R4). Do not bump `schemaVersion`: under our own rule an unknown version falls back to defaults and would destroy the saved profile
- [ ] T205 [P] Write `SessionTuningValidationTest` additions in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/SessionTuningValidationTest.kt` for the new fields, quoting the constraints from data-model.md: the band's upper end must stay below `maxBeatRateHz`, its lower end above zero, the band must sit inside the perceptible beat bounds, and `wanderPeriod` must be longer than `breathingPeriod` because the wander is the slower movement
- [ ] T206 Add `breathingBandHz`, `breathingPeriod`, `maxBeatChangePerMinute`, `wanderDepthHz`, `wanderPeriod` and `driftBudgetShare` to `SessionTuning` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/session/SessionTuning.kt`, with the validation from T205. Values stay in `MEASURED` as parameters, never as constants in audio code (FR-107a). **`driftBudgetShare` is not measured until M205 in Phase 5**, so give it an explicitly provisional value, named as such in a comment with a pointer to M205, rather than a number that reads as decided
- [ ] T205a [P] Write `SettingsDefaultsTest` additions asserting `breathing` and `pitchWander` both default to true, so a listener who never opens settings hears the preferred sound (SC-106, FR-107). **Moved out of the user story phases**: T208 reads `configuration.breathing`, so the field has to exist before the foundational phase compiles
- [ ] T205b Add `breathing` and `pitchWander` to `SessionConfiguration` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/model/Model.kt`, both defaulting to true (FR-107, FR-110)
- [ ] T205c **Capture the reference audio for SC-107 before anything changes the scheduler.** Render a session with the current pre-breathing implementation and store the digest as a fixture at `core/src/test/resources/pre-breathing-reference.json`. Like T201, this expires: once T215 lands, the original behavior can only be approximated. The assertion itself is T217
- [ ] T207 [P] Write `ContinuityLimitsFactoryTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/analysis/ContinuityLimitsFactoryTest.kt`: a breathing session gets looser rate of change limits, a non-breathing session gets feature 001's, and **a planted click is caught under both**, because only the rate of change limits differ (FR-114a)
- [ ] T208 Add `ContinuityLimits.forSession(configuration, tuning, profile, sampleRate)` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/analysis/ContinuityAnalyzer.kt`, leaving `ContinuityAnalyzer` itself unchanged so the caller decides which limits apply (research R2)

**Checkpoint**: stored state survives the arc removal, and the two sets of limits exist and are distinguishable.

---

## Phase 3: User Story 1 - A pulse that breathes (Priority: P1) 🎯 MVP

**Goal**: the beat sweeps slowly between a low and a high rate instead of holding one, and never becomes something that asks for attention.

**Independent test**: render a full night with breathing on, and confirm the rate moves, stays in its band, never reaches the rate judged poor, and never changes faster than its limit.

### Measurement first (these numbers gate the tests below)

- [ ] M201 [US1] Choose the band the beat sweeps between, from the candidates the listener preferred on 2026-10-08: 1 to 4 Hz, and 2 to 3 Hz. Generate candidates with the WAV writer, listen on the S24 with the Ozlo Sleepbuds, record the value and the reasoning in `research.md` under "Measured values"
- [ ] M202 [US1] Choose the sweep period, from the preferred 10 and 30 minutes. Clips must loop and run at least three minutes, because a 30 minute sweep cannot be judged in forty seconds (quickstart). Record value and reasoning
- [ ] M203 [US1] Derive the maximum rate of change of the beat from M201 and M202, confirm by listening that movement at that rate is noticeable only if sought, and record it. This becomes the breathing continuity limit used by T208

### Tests for User Story 1 (written and failing before implementation)

- [ ] T209 [P] [US1] Write `BreathingScheduleTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/BreathingScheduleTest.kt`: across ten hours the rate stays inside the band, never reaches `maxBeatRateHz`, never reaches zero, changes no faster than the M203 limit between adjacent instants, and is deterministic for identical inputs (FR-101 to FR-105)
- [ ] T210 [P] [US1] Write `BreathingRampTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/BreathingRampTest.kt`: the sweep amplitude is zero at the first instant and reaches full only once the opening descent has finished, so the descent still reads as a descent (research R1, FR-119)
- [ ] T211 [P] [US1] Write `BreathingNotStaticTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/BreathingNotStaticTest.kt`: the rate measurably differs between any two moments ten minutes apart (SC-103). This is the opposite failure to T209 and the reason the feature exists
- [ ] T212 [US1] Write `BreathingClampInertTest` in the same package: the clamp that keeps the composed rate inside the band never engages during a ten hour session with the measured values. If it engages, the tuning is wrong rather than the clamp being useful (plan, design decision 1)
- [ ] T213 [US1] Extend `FullNightContinuityTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/analysis/FullNightContinuityTest.kt` to cover a breathing session, using the limits from `forSession`, including the full rate windows around the start, the end of the descent and the fade out (SC-104)

### Implementation for User Story 1

- [ ] T214 [US1] Implement `Breathing` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/session/Breathing.kt`: a smooth cycle between the band edges with no corners, an amplitude that ramps across the opening descent, and composition as `rate(t) = arc(t) + amplitude(t) * sweep(t)` clamped into the band
- [ ] T215 [US1] Compose breathing into `SessionScheduler` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/session/SessionScheduler.kt` so `beatRateAt` consults it, leaving `parametersAt` and `writeParametersInto` signatures unchanged and the allocation free path still allocation free
- [ ] T216 [US1] Record what the session did in `SessionRecord`: `breathed`, `breathingBandHz` and `breathingPeriodMillis`, null when it did not breathe (FR-117, data-model.md), written where the existing record is completed in `app/src/main/kotlin/com/dnoel/binauralbeats/playback/SessionService.kt`

**Checkpoint**: a night breathes, provably, without a device.

---

## Phase 4: User Story 2 - Choosing whether it breathes (Priority: P2)

**Goal**: the listener can turn it off, and off means exactly what feature 001 did.

**Independent test**: toggle the setting, run a session, confirm the beat behaves as chosen.

- [ ] T217 [P] [US2] Write `BreathingOffReproducesFeature001Test` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/BreathingOffReproducesFeature001Test.kt`: with breathing off, the rendered audio matches the reference captured in T205c, for identical inputs (SC-107, FR-108)
- [ ] T217a [P] [US2] Write `BreathingAppliesToEveryArcTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/BreathingAppliesToEveryArcTest.kt`: breathing composes correctly with **both** remaining arcs, descend then hold and constant, staying inside the band and under the rate limit in each. FR-118 makes breathing a modifier over any arc, and every other task exercises the descend arc only
- [ ] T220 [US2] Add the toggle or toggles to `app/src/main/kotlin/com/dnoel/binauralbeats/ui/SettingsScreen.kt`, in plain language rather than naming a frequency band, and remove any reference to the deleted arc from the interface (FR-118a)
- [ ] T221 [US2] Assert a configuration change never affects a running session, extending the existing isolation test in `app/src/test/kotlin/com/dnoel/binauralbeats/ui/SettingsIsolationTest.kt` (FR-109)

---

## Phase 5: User Story 3 - Pitch that wanders beneath it (Priority: P3)

**Goal**: the pair's pitch drifts slowly up and down while the beat keeps breathing independently.

**Independent test**: render a night with wander on and confirm the center pitch moves, stays in range, and leaves the beat untouched.

- [ ] M204 [US3] Choose the pitch wander depth and period, from the preferred 15 Hz over 20 minutes. Record value and reasoning in `research.md`
- [ ] M205 [US3] Measure how the range slack divides between drift and wander on a narrow profile, where the two compete, and record `driftBudgetShare` with its reasoning (research R3)
- [ ] T222 [P] [US3] Write `PitchWanderTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/PitchWanderTest.kt`: the offset applies equally to every carrier so spacing and the beat are untouched, it stays inside the remaining slack, it reverses rather than running to an edge, and it is deterministic (FR-110, FR-111)
- [ ] T223 [P] [US3] Write `PitchWanderNarrowProfileTest`: where a profile leaves no slack after spacing and drift, the wander is zero rather than violating either (FR-113)
- [ ] T224 [P] [US3] Write `MovementsAreIndependentTest`: turning wander off leaves the breathing unchanged, and turning breathing off leaves the wander unchanged (FR-112)
- [ ] T225 [US3] Implement `PitchWander` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/session/PitchWander.kt` and compose it in `SessionScheduler` as `carrier(i, t) = restingCentre(i) + drift(i, t) + wander(t)`, with the budget split from M205
- [ ] T226 [US3] Extend the existing range compliance test so every tone still lies inside the profile with both movements active (SC-105)

---

## Phase 6: Polish and Cross-Cutting

- [ ] T227 Amend feature 001's artifacts where this feature changed them: `specs/001-calibrated-sleep-sessions/spec.md` and `data-model.md` reference the removed arc, and should note that it was removed by feature 002 rather than silently disagreeing with the code
- [ ] T228 [P] Perform a mutation check and record it in the pull request: neutralize the breathing amplitude ramp, and separately the band clamp, and confirm named tests fail for each. A test suite that survives both is not testing what it claims (constitution III)
- [ ] T228a State in the pull request that no continuity limit was widened during this work, or name every limit that was and why the audio could not be slowed instead (FR-114b). A failing continuity check reports audio that moves too fast, and the fix is slower audio
- [ ] T229 Verify on the physical S24 per quickstart M1 to M4, including **M3, installing over the existing data rather than a fresh install**, which is the only way to catch the migration hazard in research R4
- [ ] T230 [P] Record the measured values and the reasoning in `research.md` under "Measured values", in the same shape feature 001 used, so the numbers and their justification sit together
- [ ] T231 After a few nights of real use, answer quickstart M4: is a breathing session actually better to sleep to than the steady one? If not, say so in the pull request or an issue rather than shipping it because it was built

---

## Dependencies

- **T201 blocks T203**, and T201 expires: once a build without the arc runs on the phone, the real payload is gone.
- **Phase 2 blocks every user story.** The migration is first because it protects data that already exists.
- **M201 to M203 block T209 to T213.** Do the measurements before the tests that assert against them, rather than coding a placeholder constant and intending to fix it later.
- **T205c must run before T215 changes the scheduler**, otherwise the SC-107 comparison is the new implementation measured against itself. Like T201, it expires.
- **T205a and T205b are in Phase 2 on purpose**: T208's limits factory reads `configuration.breathing`, so the field must exist before the foundational phase compiles.
- **US2 (Phase 4)** depends on US1 for something to toggle.
- **US3 (Phase 5)** depends on Phase 2 and composes with US1, but its tests are independent.
- **T229** depends on everything, and T231 depends on T229 plus several nights.

## Parallel execution examples

- **Phase 2**: T205 and T207 can be written together; T203 comes first because it protects stored data.
- **US1 tests**: T209, T210 and T211 are separate files and independent.
- **US3 tests**: T222, T223 and T224 are independent of each other.

## Implementation strategy

**MVP is Phase 1 plus Phase 2 plus Phase 3.** That delivers the feature: a beat that breathes. The toggle and the pitch wander are refinements, and pitch wander in particular was the movement the listener cared least about when the two were isolated.

Deliver each phase as its own pull request into the protected `main`. Within a phase, tests come first, and any exception must be argued in the pull request rather than skipped quietly.

The task most likely to be skipped under time pressure is T201, because it takes thirty seconds and feels like housekeeping. It is the only one that cannot be done later.
