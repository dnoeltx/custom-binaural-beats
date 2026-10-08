# Implementation Plan: Breathing Beat

**Branch**: `feature/002-breathing-beat` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-breathing-beat/spec.md`

## Summary

Make the beat breathe: instead of settling on one pulse rate for the night, the rate sweeps slowly between a low and a high value, with the pair's pitch wandering slowly beneath it. Chosen by listening, where a moving pulse beat a static one and one tone per ear beat both ears mixed.

Everything interesting lands in the pure Kotlin core, next to the scheduler that already turns a profile, a configuration and elapsed time into audio parameters. The Android side gains one setting and nothing else. The work is small in volume and carries one genuine hazard, which is removing a beat arc whose name is already written into stored data on a real phone (research R4).

## Technical Context

**Language/Version**: Kotlin, as pinned in feature 001

**Primary Dependencies**: none added. Breathing is arithmetic in a module that deliberately has no dependencies beyond kotlinx.serialization

**Storage**: the existing stored state, with no schema version bump, and a serializer change so a removed arc name does not destroy the file (research R4)

**Testing**: JVM tests in `:core`; the existing full night offline continuity render extended to breathing sessions; Robolectric only where a platform type is unavoidable; verification on the physical Galaxy S24 with Ozlo Sleepbuds, no emulator

**Target Platform**: unchanged from feature 001

**Project Type**: extension of an existing two module Android project

**Performance Goals**: unchanged. Rendering stays allocation free per block, and a ten hour night still renders offline in seconds

**Constraints**: the beat never reaches the rate measured as poor; movement is noticeable only if sought; no Android types in `:core`; all state on device

**Scale/Scope**: one listener, one device, one new setting, roughly four new core concepts

## Constitution Check

*GATE: evaluated before Phase 0 and re-evaluated after Phase 1 design.*

| Principle | How this design satisfies it | Status |
|---|---|---|
| I. No Surprises | The whole feature is movement, so this principle does the most work here. The sweep is smooth with no corners, its rate of change is bounded by a measured limit, and its amplitude ramps in rather than starting abruptly (research R1) | PASS |
| II. Continuity Is Proven by Test | The full night offline render is extended to cover breathing sessions. Click and level jump detection is unchanged, so the guard that catches real defects is untouched (FR-114a) | PASS |
| III. Test-First | Every piece here is pure arithmetic in `:core`, which is the easiest possible case for writing the test first. The only Android change is a toggle | PASS |
| IV. Spec Before Code | This plan derives from an approved spec with four clarifications answered. The sound bed and recorded ambience remain out of scope | PASS |
| V. Evidence Over Assumption | Four numbers are deliberately unset and listed as measurement tasks, with the candidates the listener already preferred recorded. The serializer hazard was found by reading the real stored file rather than assumed | PASS |
| VI. Honest About Effects | No claim about what a sweeping beat does to sleep. The feature is described as a sound the listener prefers, which is exactly what the evidence supports | PASS |
| VII. Platform-Neutral, Layered Core | All of it lands in `:core`. The layer contract is untouched, so a future sound bed still plugs in unchanged | PASS |
| VIII. The User Owns Their Data | No new data leaves the device. The serializer decision exists specifically to avoid destroying what is already stored | PASS |

**Post-Phase 1 re-evaluation**: unchanged, all PASS. The design adds no dependency, no network path, and no Android type to `:core`.

**Note on Principle I**: FR-114b forbids widening the breathing limits to make a failing check pass. That rule is the one most likely to be tested by reality, because the first red build will be tempting to fix with a number.

## Project Structure

### Documentation (this feature)

```text
specs/002-breathing-beat/
├── plan.md              # This file
├── spec.md              # Approved specification, four clarifications answered
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── core-api.md      # Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md             # Created later by /speckit-tasks
```

### Source Code

```text
core/src/main/kotlin/.../core/
├── session/
│   ├── Breathing.kt          # NEW: the sweep, its amplitude ramp, and composition with an arc
│   ├── PitchWander.kt        # NEW: the shared slow offset applied to every carrier
│   ├── SessionScheduler.kt   # CHANGED: composes arc, breathing, drift and wander
│   └── SessionTuning.kt      # CHANGED: gains the breathing and wander parameters
├── model/
│   └── Model.kt              # CHANGED: BeatArc loses an entry and gains a tolerant serializer
└── analysis/
    └── ContinuityLimits.kt   # CHANGED or NEW: a factory producing the right limits per session

app/src/main/kotlin/.../
└── ui/SettingsScreen.kt      # CHANGED: one toggle, or two if wander is separately exposed
```

**Structure Decision**: no new module and no new layer. Breathing is a property of the session's schedule, which already lives in `:core`, so the feature lands where the thing it modifies lives.

## Design decisions carried from research

1. **Breathing is a modifier over the arc**, with its amplitude ramping in across the opening descent, so the settling descent still reads as a descent (R1).
2. **The caller chooses the continuity limits**; the analyzer keeps its single limits parameter and a factory derives the right set (R2).
3. **Pitch wander is its own movement**, composed with drift rather than folded into it, and the two share one explicitly allocated budget of range slack (R3).
4. **Removing an arc is a data migration**, handled by a tolerant serializer with a test against a real stored payload (R4).

## Measurement tasks (values this plan does not invent)

| Value | How it will be set |
|---|---|
| The band the beat sweeps between | Listening, from the preferred 1 to 4 Hz and 2 to 3 Hz |
| The sweep period | Listening, from the preferred 10 and 30 minutes |
| Maximum rate of change of the beat | Derived from the chosen band and period, then confirmed by listening; becomes the breathing continuity limit |
| Pitch wander depth and period | Listening, from the preferred 15 Hz over 20 minutes |
| How the range slack divides between drift and wander | Measured on a narrow profile, where the two compete |

## Complexity Tracking

No constitution violations. Nothing in this design requires justification under this section.
