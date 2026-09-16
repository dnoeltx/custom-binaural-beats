---

description: "Task list for Calibrated Sleep Sessions"
---

# Tasks: Calibrated Sleep Sessions

**Input**: Design documents from `/specs/001-calibrated-sleep-sessions/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/core-api.md](./contracts/core-api.md), [quickstart.md](./quickstart.md)

**Tests**: REQUIRED and written first. Constitution Principle III mandates test-first everywhere; where a test genuinely cannot come first, the pull request must say why and how the behavior was verified.

**Organization**: grouped by user story so each is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on incomplete work)
- **[Story]**: US1 to US4, matching the user stories in spec.md
- Package root is `com.dnoel.binauralbeats`; adjust once the final application ID is chosen in T002.

## Measurement tasks

Several values are deliberately not invented (see the Measurement tasks table in plan.md). They appear below as `M` tasks. Each one MUST record the chosen value **and the reasoning** in `research.md` under a new "Measured values" section, per constitution Principle V.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: a compiling, testable two-module skeleton with the toolchain pinned.

- [x] T001 Verify current stable versions before writing any of them down: Kotlin, Android Gradle Plugin, Compose BOM, DataStore, kotlinx.serialization, JDK to pin, and the current Play target API requirement; record them with their source in `specs/001-calibrated-sleep-sessions/research.md` under "Toolchain versions (verified <date>)"
- [x] T002 Create the Gradle root with `settings.gradle.kts` including `:core` and `:app`, `build.gradle.kts`, `gradle.properties`, and the wrapper; set the application ID and confirm the package root
- [x] T003 Create the `:core` module as a plain Kotlin JVM module in `core/build.gradle.kts` with NO Android plugin and NO Android dependency, using the versions verified in T001
- [x] T004 Create the `:app` module in `app/build.gradle.kts` with Compose, Material 3, DataStore, kotlinx.serialization and lifecycle-service, depending on `:core`; set `minSdk` to 26 (the floor implied by `AudioFocusRequest` and notification channels, per plan.md) and `targetSdk`/`compileSdk` to the values verified in T001
- [x] T005 [P] Pin the JDK via `gradle/gradle-daemon-jvm.properties` so Gradle provisions it rather than using whatever is on PATH
- [x] T006 [P] Add `.gitignore` entries for Android and Gradle build output, `local.properties`, and IDE files, extending the existing root `.gitignore`
- [x] T007 [P] Add a failing placeholder test in `core/src/test/kotlin/com/dnoel/binauralbeats/core/SanityTest.kt` and make it pass, proving the JVM test path runs with no device
- [x] T008 Add a build check that fails if `:core` ever gains an Android dependency (a Gradle task asserting the `:core` configuration contains no `com.android.*` or `androidx.*` artifact), wired into `check` in `core/build.gradle.kts`
- [ ] T008c Add CI in `.github/workflows/ci.yml` running `:core:check`, `:app:testDebugUnitTest` and `:app:assembleDebug` on every pull request and push to `main`, with action versions verified against the GitHub releases API rather than written from memory. **Moved here from Phase 7 (was T069) on 2026-09-16**: leaving it until last would mean every PR from Phase 1 to Phase 6 merging into a protected `main` with no automated check, which defeats the protection. It cannot come earlier than this, because CI needs a project and a test to run
- [x] T008e Write a short README at `README.md`: what the app is, an explicit in-development status, the problem it comes from, the process argument, and the design decisions already made. **No feature claims**, because no feature exists yet. Added 2026-09-16 because the repository is public and currently has no front door; the full README remains T072
- [ ] T008d Enable the required status check on `main` once one CI run has passed, closing the gap deliberately left open at repository setup when no CI existed

**Checkpoint**: `./gradlew :core:test` and `./gradlew :app:assembleDebug` both succeed, and constitution VII is enforced by the build rather than by discipline.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the model, ports and test harness every story depends on.

**CRITICAL**: no user story work begins until this phase is complete.

- [x] T008b [P] Define the perceptible bounds as named constants in `core/src/main/kotlin/com/dnoel/binauralbeats/core/model/PerceptualBounds.kt` (maximum carrier frequency and maximum left/right difference), citing the source recorded in research.md, with a test in `core/src/test/kotlin/com/dnoel/binauralbeats/core/model/PerceptualBoundsTest.kt` asserting the scheduler can never produce a carrier or beat rate outside them (FR-002)
- [ ] T009 [P] Write tests for the model value objects and their validation rules in `core/src/test/kotlin/com/dnoel/binauralbeats/core/model/ModelValidationTest.kt`, quoting the rules from data-model.md: `ListenerProfile.lowHz > 0`, `lowHz < highHz`, both within the perceptible carrier bound, and the range wide enough to hold `carrierCount` carriers at the minimum spacing
- [ ] T010 Implement the model types in `core/src/main/kotlin/com/dnoel/binauralbeats/core/model/` per data-model.md: `AppState` (with `schemaVersion`), `ListenerProfile` (`lowHz`, `highHz`, `source` of CALIBRATED/PRESET/MANUAL, `createdAt`), `SessionConfiguration` (`beatArc` of DESCEND_THEN_HOLD/CONSTANT/DESCEND_THEN_VARY defaulting to DESCEND_THEN_HOLD, `endBehavior` defaulting to RunUntilStopped, `carrierCount` of 2 or 3 defaulting to 3, `volumeWarningAcknowledged` defaulting to false), `EndBehavior` (RunUntilStopped, AfterDuration, AtClockTime), `SessionRecord` (`endReason` of STOPPED_BY_LISTENER/COMPLETED_AS_CONFIGURED/OUTPUT_LOST/INTERRUPTED/UNKNOWN), `CalibrationSession`, `ToneJudgment`
- [ ] T011 [P] Define the outbound ports in `core/src/main/kotlin/com/dnoel/binauralbeats/core/ports/` exactly as in contracts/core-api.md: `AudioSink`, `StateStore`, `Clock`
- [ ] T012 [P] Implement test doubles in `core/src/test/kotlin/com/dnoel/binauralbeats/core/ports/`: a recording `FakeAudioSink`, an in-memory `FakeStateStore`, and a `FakeClock` with settable instant and local time
- [ ] T013 [P] Write tests for `ContinuityAnalyzer` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/analysis/ContinuityAnalyzerTest.kt`, including synthetic signals that MUST be flagged (a step, a cut to zero, a level jump) and signals that MUST NOT be flagged (a long smooth fade, slow drift)
- [ ] T014 Implement `ContinuityAnalyzer` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/analysis/ContinuityAnalyzer.kt` as a streaming analyzer with fixed-size windows that never holds a whole session in memory, reporting maximum sample-to-sample delta, per-block RMS rate of change, and a discontinuity count
- [ ] T015 Build a developer render tool that writes a rendered session to a WAV file in `core/src/test/kotlin/com/dnoel/binauralbeats/core/tools/RenderToWav.kt`, so every M task below can be judged by listening rather than by argument
- [ ] T016 [P] Write tests for serialization round-trip and schema fallback in `core/src/test/kotlin/com/dnoel/binauralbeats/core/model/AppStateSerializationTest.kt`: a round-trip preserves every field; an unknown or future `schemaVersion` returns defaults; a corrupt payload returns defaults and MUST NOT destroy the stored bytes
- [ ] T017 Implement `AppState` serialization in `core/src/main/kotlin/com/dnoel/binauralbeats/core/model/AppStateSerialization.kt` using kotlinx.serialization, with the fallback behavior from T016
- [ ] T018 [P] Write a Robolectric test for `DataStoreStateStore` in `app/src/test/kotlin/com/dnoel/binauralbeats/storage/DataStoreStateStoreTest.kt` covering write, read back, and recovery from a corrupt file, and see it fail before T019
- [ ] T019 Implement the DataStore-backed `StateStore` in `app/src/main/kotlin/com/dnoel/binauralbeats/storage/DataStoreStateStore.kt`, delegating all encoding to T017, satisfying T018
- [ ] T020 Configure Auto Backup in `app/src/main/AndroidManifest.xml` with explicit data extraction rules so the profile and settings are included (FR-028, SC-007)

**Checkpoint**: the model, the ports, the analyzer and persistence all exist and are tested with no device involved.

---

## Phase 3: User Story 1 - Fall asleep to a session tonight (Priority: P1) 🎯 MVP

**Goal**: choose a preset pitch, start a session, have it play uninterrupted all night with the phone locked, and stop it from the notification or from a single dim control.

**Independent test**: install on the S24, pick a preset, start, lock the phone, confirm audio continues overnight, and confirm both stop paths work (quickstart M1, M2, M3).

### Measurement first (these numbers feed the tests below)

- [ ] M001 [US1] Measure fade-in and fade-out durations by listening at bedtime volume using the T015 render tool; choose the shortest that is not noticeable; record value and reasoning in research.md
- [ ] M002 [US1] Derive the maximum rate of change for pitch and gain from M001 and encode them as the analyzer limits; record in research.md
- [ ] M003 [US1] Measure the minimum carrier spacing (FR-004) by rendering pairs at decreasing spacing and finding where roughness becomes audible; record value and reasoning in research.md
- [ ] M004 [US1] Choose beat rate values for each arc and the descent duration within the published sleep range, confirm by listening, and record in research.md
- [ ] M005 [US1] Measure the maximum imperceptible drift rate (FR-004a); record value and reasoning in research.md
- [ ] M006 [US1] Choose the three preset pitch values (low, medium, high) spanning the usable carrier range; confirm by listening; record in research.md

### Tests for User Story 1 (written and failing before implementation)

- [ ] T021 [P] [US1] Write `SessionSchedulerTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/SessionSchedulerTest.kt`: every carrier stays inside the profile range (FR-003), all active pairs share one beat rate at every instant (FR-003), spacing never drops below the M003 minimum (FR-004), and no parameter changes faster than the M002 limits (FR-006)
- [ ] T022 [P] [US1] Write `BeatArcTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/BeatArcTest.kt` covering DESCEND_THEN_HOLD as the default: the descent is monotonic, completes in the M004 duration, and holds steady thereafter
- [ ] T023 [P] [US1] Write `DriftTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/DriftTest.kt`: pitches move over a ten-hour session, never leave the profile range, never violate spacing, and never exceed the M005 rate (FR-004a)
- [ ] T024 [P] [US1] Write `FadeTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/audio/FadeTest.kt`: the first and last samples of any session are zero, and both fades follow the M001 durations (FR-005)
- [ ] T025 [P] [US1] Write `RendererDeterminismTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/audio/RendererDeterminismTest.kt`: identical inputs including the seed give byte-identical output; different seeds differ
- [ ] T026 [P] [US1] Write `AllocationTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/audio/AllocationTest.kt` proving `render` allocates nothing per block once warmed (research R1 garbage collection mitigation)
- [ ] T027 [US1] Write `FullNightContinuityTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/analysis/FullNightContinuityTest.kt` implementing both tiers from research R5: a parameter-timeline pass over ten hours, and a streamed sample-domain pass at reduced rate plus full rate windows around every transition; asserts zero violations (SC-002)
- [ ] T028 [P] [US1] Write `ProfileRangeComplianceTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/ProfileRangeComplianceTest.kt` asserting SC-005 across generated profiles

### Implementation for User Story 1

- [ ] T029 [P] [US1] Implement the oscillator and mixer in `core/src/main/kotlin/com/dnoel/binauralbeats/core/audio/Oscillator.kt` and `Mixer.kt`, phase-continuous so a frequency change never breaks the waveform
- [ ] T030 [US1] Implement `SoundLayer` and `BinauralToneLayer` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/audio/` per FR-014, with the layer list and mixing contract able to accept further layer types without signature changes
- [ ] T031 [US1] Implement `SessionScheduler` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/session/SessionScheduler.kt` satisfying T021 and T022, including carrier placement inside the profile range and the shared beat rate
- [ ] T032 [US1] Implement seeded drift in `core/src/main/kotlin/com/dnoel/binauralbeats/core/session/Drift.kt` satisfying T023
- [ ] T033 [US1] Implement `SessionRenderer` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/audio/SessionRenderer.kt` filling a caller-supplied buffer, allocation-free, satisfying T024 through T028
- [ ] T034 [US1] Implement the `AudioTrack` sink in `app/src/main/kotlin/com/dnoel/binauralbeats/playback/AudioTrackSink.kt` using MODE_STREAM, PERFORMANCE_MODE_POWER_SAVING, a deliberately large buffer, and exposing `getUnderrunCount()` (research R1). This is a thin adapter over a platform type: keep it free of logic, and **document the test-first exception in the pull request**, naming how it was verified (constitution III)
- [ ] T035 [US1] Implement the playback foreground service in `app/src/main/kotlin/com/dnoel/binauralbeats/playback/SessionService.kt` as a lifecycle service with a dedicated writer thread, and declare `foregroundServiceType="mediaPlayback"` plus `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` in `app/src/main/AndroidManifest.xml` (research R3). Keep session logic in `:core`; **document the test-first exception in the pull request** for whatever remains untestable (constitution III)
- [ ] T036 [US1] Request audio focus from inside the running service in `app/src/main/kotlin/com/dnoel/binauralbeats/playback/FocusController.kt`: `AUDIOFOCUS_GAIN` with `USAGE_MEDIA`, transient loss fades out and pauses, regain fades back in, permanent loss ends the session, automatic ducking is refused (research R4). Put the decision logic behind a pure function in `:core` so it is tested first, leaving only the platform wiring here; **document any remaining test-first exception in the pull request** (constitution III)
- [ ] T037 [US1] Implement the ongoing notification with a stop action in `app/src/main/kotlin/com/dnoel/binauralbeats/notification/SessionNotification.kt`, creating the notification channel at application start rather than at first session (FR-016); **document the test-first exception in the pull request** if the notification itself cannot be covered by a Robolectric test (constitution III)
- [ ] T038 [P] [US1] Build the home screen in `app/src/main/kotlin/com/dnoel/binauralbeats/ui/HomeScreen.kt`: a large Start control, the current profile or preset choice, and a path to settings, reachable in three taps or fewer (FR-008)
- [ ] T039 [P] [US1] Build the first-run welcome screen in `app/src/main/kotlin/com/dnoel/binauralbeats/ui/WelcomeScreen.kt` offering calibration or an immediate start from the M006 presets, with calibration never required (FR-023)
- [ ] T040 [P] [US1] Build the in-session screen in `app/src/main/kotlin/com/dnoel/binauralbeats/ui/SessionScreen.kt`: dark, one dim Stop control, nothing bright, no clock (FR-017)
- [ ] T041 [US1] Write and satisfy a test for headphone absence in `app/src/test/kotlin/com/dnoel/binauralbeats/playback/OutputRouteTest.kt`, then warn on the home screen when no stereo output is connected while still allowing the listener to continue (spec edge case)
- [ ] T042 [US1] Persist and update the `SessionRecord` across a session in `app/src/main/kotlin/com/dnoel/binauralbeats/playback/SessionRecorder.kt`, including `renderSeed` and `carrierSummary`, replacing any previous record (FR-029)
- [ ] T042b [US1] Write a test in `app/src/test/kotlin/com/dnoel/binauralbeats/playback/SingleSessionTest.kt` asserting that only one session can exist at a time (FR-013), covering a rapid double tap on Start and a start arriving from the notification while a session runs, then implement the guard in `app/src/main/kotlin/com/dnoel/binauralbeats/playback/SessionService.kt`
- [ ] T043 [US1] Ensure a session is never presented as running when it is not, including after process death, in `app/src/main/kotlin/com/dnoel/binauralbeats/playback/SessionState.kt` (FR-025), with a Robolectric test in `app/src/test/kotlin/com/dnoel/binauralbeats/playback/SessionStateTest.kt`
- [ ] T044 [US1] Verify on the physical S24 per quickstart M1, M2 and M3, including one overnight run with Battery Saver enabled, and record the resulting `getUnderrunCount()` in the pull request; also count the interactions needed to stop a session from the locked phone and confirm it is two or fewer (SC-003)

**Checkpoint**: User Story 1 is independently shippable. The app can put someone to sleep and be stopped.

---

## Phase 4: User Story 2 - Calibrate to my own ears (Priority: P2)

**Goal**: one relaxed sitting of two-target judgments produces a saved preferred pitch range that later sessions use.

**Independent test**: run calibration end to end, confirm a saved range, then confirm a session plays inside it (quickstart M6).

- [ ] M007 [US2] Measure calibration tone duration, the maximum number of judgments, and the inactivity timeout from a real sitting, targeting the 12-minute budget in SC-004; record values and reasoning in research.md
- [ ] T045 [P] [US2] Write `CalibrationSearchTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/calibration/CalibrationSearchTest.kt`: converges to a plausible range for a simulated listener with a known preference, terminates within the M007 maximum (FR-020), and is deterministic
- [ ] T046 [P] [US2] Write `CalibrationEdgeCaseTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/calibration/CalibrationEdgeCaseTest.kt`: a listener who rejects everything and one who accepts everything both yield no fabricated range, and the caller is offered retry or preset (spec edge case)
- [ ] T047 [P] [US2] Write `CalibrationResumeTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/calibration/CalibrationResumeTest.kt`: an interrupted sitting resumes to exactly the same state, and an existing profile is untouched until a new calibration reaches COMPLETED (FR-022)
- [ ] T048 [US2] Implement `CalibrationSearch` and `CalibrationSearchState` in `core/src/main/kotlin/com/dnoel/binauralbeats/core/calibration/`, satisfying T045 through T047
- [ ] T049 [US2] Implement calibration tone presentation so every judged tone is a real binaural pair at a single fixed beat rate for the whole sitting (FR-021a), in `core/src/main/kotlin/com/dnoel/binauralbeats/core/calibration/CalibrationPresenter.kt`, with gradual transitions between tones (FR-006)
- [ ] T050 [US2] Build the calibration screen in `app/src/main/kotlin/com/dnoel/binauralbeats/ui/CalibrationScreen.kt`: two large tap targets (lower "relaxing", upper "not relaxing"), low-brightness progress, no text requiring close reading (FR-019)
- [ ] T051 [US2] Wire calibration to persistence in `app/src/main/kotlin/com/dnoel/binauralbeats/ui/CalibrationViewModel.kt`: save `calibrationInProgress` as it advances, write the profile only on completion, and offer resume or discard on return
- [ ] T052 [US2] Implement the inactivity pause from M007 so an abandoned sitting stops rather than running indefinitely (spec edge case), with a test in `core/src/test/kotlin/com/dnoel/binauralbeats/core/calibration/CalibrationTimeoutTest.kt`
- [ ] T053 [US2] Verify on the physical S24 per quickstart M6, including interrupting and resuming a calibration

**Checkpoint**: the app is now tuned to the listener rather than to a preset.

---

## Phase 5: User Story 3 - Shape the night (Priority: P3)

**Goal**: the listener chooses the beat-rate arc and how the session ends.

**Independent test**: change each setting, run a session, confirm the audio follows the configured arc and ends as configured.

- [ ] T054 [P] [US3] Write `EndBehaviorTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/EndBehaviorTest.kt`: RunUntilStopped never ends on its own; AfterDuration fades to silence ending exactly at the duration; AtClockTime completes its fade at or before the stated local time
- [ ] T055 [P] [US3] Write `ClockTimeEdgeCaseTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/ClockTimeEdgeCaseTest.kt` using `FakeClock` for a daylight saving transition and a time zone change, asserting the fade lands at the intended local wall-clock time (spec edge case)
- [ ] T056 [P] [US3] Write `ArcVariantTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/session/ArcVariantTest.kt` for CONSTANT and DESCEND_THEN_VARY, asserting both stay inside the M002 rate limits
- [ ] T057 [US3] Implement the remaining arcs and all three end behaviors in `core/src/main/kotlin/com/dnoel/binauralbeats/core/session/`, satisfying T054 through T056
- [ ] T058 [US3] Build the settings screen in `app/src/main/kotlin/com/dnoel/binauralbeats/ui/SettingsScreen.kt` for arc, end behavior and carrier count, with the defaults from data-model.md
- [ ] T059 [US3] Enforce that a configuration change never affects a running session (FR-012), with a test in `app/src/test/kotlin/com/dnoel/binauralbeats/ui/SettingsIsolationTest.kt`

**Checkpoint**: the night is shaped by the listener, with defaults that work untouched.

---

## Phase 6: User Story 4 - Adjust my profile later (Priority: P4)

**Goal**: review, hand-adjust, or re-run calibration from settings.

**Independent test**: change the range in settings and confirm the next session honors it.

- [ ] T060 [P] [US4] Write `ProfileEditValidationTest` in `core/src/test/kotlin/com/dnoel/binauralbeats/core/model/ProfileEditValidationTest.kt`: a hand-edited range is rejected unless `lowHz < highHz`, both are within the perceptible carrier bound, and the range is wide enough for `carrierCount` carriers at the M003 spacing
- [ ] T061 [US4] Add profile display and editing to `app/src/main/kotlin/com/dnoel/binauralbeats/ui/SettingsScreen.kt`, showing the current range in plain language with a recalibrate action (US4 acceptance scenarios 1 and 2)
- [ ] T062 [US4] Set `source` to MANUAL on a hand edit and CALIBRATED on a completed calibration, replacing the previous profile, in `app/src/main/kotlin/com/dnoel/binauralbeats/ui/SettingsViewModel.kt`

---

## Phase 7: Robustness and Cross-Cutting Concerns

- [ ] M008 Confirm or revise the FR-024 disconnect grace period (proposed 3 minutes) from real use; record the value and reasoning in research.md
- [ ] M009 Confirm or revise the FR-026a volume warning threshold (proposed 60% of maximum media volume, from WHO guidance); record the reasoning, and state explicitly that no decibel claim is made
- [ ] T063 [P] Write `OutputLossTest` in `app/src/test/kotlin/com/dnoel/binauralbeats/playback/OutputLossTest.kt`: output loss pauses without ever routing to the phone speaker, return within the grace period resumes with a fade and continues the arc, and expiry ends the session cleanly (FR-024, SC-008)
- [ ] T064 Implement output-loss handling in `app/src/main/kotlin/com/dnoel/binauralbeats/playback/OutputWatcher.kt` using `ACTION_AUDIO_BECOMING_NOISY` and audio device callbacks, with the M008 grace period (research R4)
- [ ] T065 [P] Implement the volume warning in `app/src/main/kotlin/com/dnoel/binauralbeats/ui/VolumeWarning.kt`: visual only, shown while setting the level and never during a session, using the M009 threshold and stating no decibel figure (FR-026a, FR-026b); persist `SessionConfiguration.volumeWarningAcknowledged` so the listener is not warned repeatedly about a level they have already accepted
- [ ] T066 [P] Assert the app emits no sound of its own (no chimes, no alerts) in `app/src/test/kotlin/com/dnoel/binauralbeats/playback/NoExtraSoundTest.kt` (FR-018), and in the same file assert the app never changes the system volume itself: no `setStreamVolume` or equivalent call exists anywhere in `:app` (FR-026)
- [ ] T067 [P] Assert the app never holds the screen on and never bypasses the lock screen in `app/src/test/kotlin/com/dnoel/binauralbeats/ui/ScreenPolicyTest.kt` (FR-015)
- [ ] T068 Confirm there is no network permission and no network code anywhere in `app/src/main/AndroidManifest.xml` and the source tree (FR-027, FR-028, constitution VIII)
- [ ] T069 MOVED to Phase 1 as T008c and T008d on 2026-09-16. CI belongs at the start of implementation, not the end; see the note on T008c
- [ ] T070 [P] Perform the mutation check from quickstart V4 (remove the fade-in, disable spacing enforcement, freeze the drift) and record in the pull request which named tests failed for each (constitution III)
- [ ] T071 Run the full quickstart manual suite on the physical S24 (M1 through M7) and record results, including the overnight run and the backup and restore check
- [ ] T072 [P] Expand the README written in T008e: what the app does, the calibration idea, the architecture argument for the pure-Kotlin core, the offline continuity test, the measured values and why they are what they are, and the honest note on binaural beat evidence (constitution VI)
- [ ] T073 [P] Capture screenshots for the README on the S24: the calibration screen, the dim in-session Stop, and the home screen in dark theme
- [ ] T074 Decide whether FR-026a's volume rule should bind all future features; if so, amend the constitution to v1.1.0 in its own pull request (raised by plan.md)

---

## Dependencies

- **Phase 1** blocks everything.
- **Phase 2** blocks all user stories.
- **M001 through M006** block the US1 tests that assert against those numbers (T021 to T028). Do the measurements first; do not code a placeholder constant and plan to "fix it later", which is how an invented number becomes permanent.
- **US1 (Phase 3)** is the MVP and depends only on Phases 1 and 2.
- **US2 (Phase 4)** depends on Phase 2 and reuses the US1 audio path; it does not depend on US1's UI.
- **US3 (Phase 5)** depends on the scheduler from US1.
- **US4 (Phase 6)** depends on US2 for a profile to edit, and on Phase 2 validation.
- **Phase 7** depends on the playback path from US1. CI is no longer here: it moved to T008c and T008d in Phase 1.

## Parallel execution examples

- **Phase 2**: T009, T011, T012, T013, T016 can all proceed together; they touch different files.
- **US1 tests**: T021 through T026 and T028 are all independent files and can be written in parallel; T027 depends on T014 and the renderer contract.
- **US1 screens**: T038, T039 and T040 are separate Compose files.
- **Phase 7**: T063, T065, T066, T067, T070, T072 and T073 are largely independent.

## Implementation strategy

**MVP is Phase 1 + Phase 2 + Phase 3 (User Story 1).** That is a complete product for one person: pick a preset, sleep, stop it. Everything after that makes it tuned, configurable and polished.

Deliver each phase as its own pull request into the protected `main`. Within a phase, tests come first per constitution III, and any exception must be argued in the pull request rather than skipped silently.

The measurement tasks are the thing most likely to be skipped under time pressure, and they are the whole reason this app can claim to be tuned rather than guessed. Do them with the render tool from T015 and write down the reasoning.
