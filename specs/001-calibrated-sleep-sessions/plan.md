# Implementation Plan: Calibrated Sleep Sessions

**Branch**: `feature/001-calibrated-sleep-sessions` | **Date**: 2026-09-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-calibrated-sleep-sessions/spec.md`

## Summary

An Android app that generates binaural sleep audio tuned to one listener. A relaxed two-target calibration finds the listener's preferred carrier pitch range; sessions then weave two or three carrier pairs inside that range, sharing one beat rate that follows a configurable arc, drifting slowly, and running all night with the phone locked.

The technical approach is deliberately small: synthesize PCM in pure Kotlin and hand it to `AudioTrack` from a `mediaPlayback` foreground service, store about four records in DataStore, and keep every piece of interesting logic in a pure-Kotlin module that has no Android dependency at all. That module is also what makes the central promise testable: an entire night can be rendered offline, faster than real time, through the same code that plays it. See [research.md](./research.md) for the evidence behind each choice.

## Technical Context

**Language/Version**: Kotlin (version pinned at implementation, verified against current release, not from memory)

**Primary Dependencies**: Jetpack Compose with Material 3, AndroidX DataStore, kotlinx.serialization, AndroidX Lifecycle service. No audio library, no NDK, no dependency injection framework.

**Storage**: DataStore holding one serialized `AppState` object (see [data-model.md](./data-model.md))

**Testing**: JVM unit tests for `:core`; Robolectric or instrumented tests in `:app` only where a platform type is unavoidable; manual overnight verification on hardware per [quickstart.md](./quickstart.md)

**Target Platform**: Android, minSdk 26, targeting the current Play requirement (to be confirmed at implementation). Verification device: physical Galaxy S24, no emulator.

**Project Type**: Mobile application, two Gradle modules

**Performance Goals**: continuous stereo playback for at least ten hours with zero underruns; a full ten-hour session rendered offline in seconds; audio generation allocation-free per block

**Constraints**: entirely offline; no accounts; audio-only product with no latency requirement; all state on device; no Android types in `:core`

**Scale/Scope**: one listener, one device, four screens (welcome, calibration, home, in-session), roughly four persisted records

## Constitution Check

*GATE: evaluated before Phase 0 and re-evaluated after Phase 1 design.*

| Principle | How this design satisfies it | Status |
|---|---|---|
| I. No Surprises | Every parameter change flows through the scheduler's rate-of-change limits; fades in and out are structural (contract invariant 2); interruptions fade rather than jump; the volume warning is visual only and never appears mid-session | PASS |
| II. Continuity Is Proven by Test | One renderer serves both playback and verification; a full night is analyzed in two tiers without a device (research R5, quickstart V1) | PASS |
| III. Test-First | `:core` is pure and fast, so tests genuinely can come first. Android-facing pieces sit behind `AudioSink`, `StateStore` and `Clock`, so their logic is testable too. Non-vacuity is checked by deliberate mutation (quickstart V4). Exceptions must be argued in the PR | PASS |
| IV. Spec Before Code | This plan derives only from the approved spec; backlog items (noise layers, sleep detection, hosted backup, iOS) are named and excluded | PASS |
| V. Evidence Over Assumption | Every technical choice in research.md cites a primary source; unresolved numbers are listed below as measurement tasks rather than guessed in code; version numbers are deferred to implementation-time verification | PASS |
| VI. Honest About Effects | No claim about sleep or physiology anywhere in these artifacts; the volume warning deliberately avoids stating decibels the app cannot measure (research R6) | PASS |
| VII. Platform-Neutral, Layered Core | `:core` is a separate Gradle module with no Android dependency, so the build enforces the rule; the layer and mixer contracts admit new layer types without signature changes | PASS |
| VIII. The User Owns Their Data | DataStore on device, platform backup only, no network code in the app at all | PASS |

**Post-Phase 1 re-evaluation**: unchanged, all PASS. The design added no dependency, no network path, and no Android type in `:core`. The one item to watch during implementation is Principle III: the foreground service and the `AudioTrack` sink are the places where test-first is hardest, which is exactly why they are thin adapters behind interfaces.

**Constitution amendment candidate raised by this feature**: FR-026a introduces a volume-safety rule that currently exists only for this feature. If it should bind every future feature, amend the constitution to v1.1.0. Not blocking.

## Project Structure

### Documentation (this feature)

```text
specs/001-calibrated-sleep-sessions/
├── plan.md              # This file
├── spec.md              # Approved specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── core-api.md      # Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md             # Created later by /speckit-tasks
```

### Source Code (repository root)

```text
core/                                  # Pure Kotlin. No Android dependency.
├── src/main/kotlin/.../core/
│   ├── audio/                         # Oscillators, mixer, SessionRenderer, fades
│   ├── session/                       # SessionScheduler, beat arcs, end behavior, drift
│   ├── calibration/                   # CalibrationSearch and its state
│   ├── analysis/                      # ContinuityAnalyzer
│   ├── model/                         # AppState and the entities in data-model.md
│   └── ports/                         # AudioSink, StateStore, Clock
└── src/test/kotlin/                   # JVM tests, including the full-night continuity run

app/                                   # Android only. Thin.
├── src/main/kotlin/.../
│   ├── playback/                      # Foreground service, AudioTrack sink, focus, noisy receiver
│   ├── storage/                       # DataStore implementation of StateStore
│   ├── ui/                            # Compose screens: welcome, calibration, home, in-session
│   └── notification/                  # Ongoing notification with stop action
└── src/test/kotlin/
```

**Structure Decision**: two modules, `:core` and `:app`, as decided by the owner before planning. The separation is not stylistic: it is how constitution VII is enforced mechanically rather than by discipline, and it is what keeps a ten-hour render runnable on a laptop. Converting `:core` to a Kotlin Multiplatform module later must remain a build-configuration change, which is why nothing in it may reference a JVM-only API without noting it.

## Measurement tasks (values this plan deliberately does not invent)

Per constitution Principle V, these are set by measurement or listening during implementation and recorded with their reasoning. They are inputs to `/speckit-tasks`, not decisions made here.

| Value | How it will be set |
|---|---|
| Fade-in and fade-out durations | Listening test at bedtime volume; the shortest that is not noticeable |
| Maximum rate of change (pitch, gain) | Derived from the fade work, then encoded as the analyzer's limits |
| Beat rate values for the arcs, and descent duration | Chosen within the published sleep range, then confirmed by listening |
| Minimum carrier spacing (FR-004) | Measured: render pairs at decreasing spacing and find where roughness becomes audible |
| Maximum drift rate (FR-004a) | Listening test; the fastest that remains imperceptible |
| Calibration tone duration, maximum judgments, inactivity timeout | From a real calibration sitting, targeting the 12-minute budget (SC-004) |
| Disconnect grace period (FR-024) | Proposed 3 minutes; confirm by use |
| Volume warning threshold (FR-026a) | Proposed 60% of maximum media volume, from WHO guidance (research R6) |
| Preset pitch values (low, medium, high) | Chosen to span the usable carrier range; confirmed by listening |

## Complexity Tracking

No constitution violations. Nothing in this design requires justification under this section.
