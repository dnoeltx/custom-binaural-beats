# Phase 1 Data Model: Calibrated Sleep Sessions

**Date**: 2026-09-16 | **Feature**: [spec.md](./spec.md)

All types below live in the pure-Kotlin `:core` module and contain no Android types. Persistence (see [research.md](./research.md) R2) stores one root object holding all of them.

## Root stored object

**AppState** (version 1)

| Field | Type | Notes |
|---|---|---|
| `schemaVersion` | Int | Present from the first release. An unknown or future version falls back to defaults and MUST NOT delete an existing file. |
| `profile` | ListenerProfile? | Null until a calibration completes or the listener picks a preset. |
| `settings` | SessionConfiguration | Always present; defaults defined below. |
| `calibrationInProgress` | CalibrationSession? | Present only while a calibration is resumable (FR-022). |
| `lastSession` | SessionRecord? | The most recent session only (FR-029). |

## Entities

### ListenerProfile

The saved result of calibration, or the preset chosen on first run.

| Field | Type | Validation |
|---|---|---|
| `lowHz` | Double | > 0, < `highHz`, within the perceptible carrier bound (FR-002) |
| `highHz` | Double | > `lowHz`, within the perceptible carrier bound |
| `source` | enum: CALIBRATED, PRESET, MANUAL | How the range was produced |
| `createdAt` | Instant | When it was saved |

Rules: exactly one profile is active. Replacing it is a whole-object write (a completed calibration, a preset choice, or a manual edit in settings). The range MUST be wide enough to hold the required number of simultaneous carriers at the minimum spacing (FR-004), which is a validation rule the editor and the calibration result both enforce.

### CalibrationSession

One sitting (FR-019 through FR-023).

| Field | Type | Notes |
|---|---|---|
| `id` | String | Stable across resume |
| `startedAt` | Instant | |
| `beatRateHz` | Double | Fixed for the whole calibration (FR-021a) |
| `judgments` | List of ToneJudgment | Ordered as presented |
| `state` | enum: IN_PROGRESS, COMPLETED, ABANDONED | |
| `searchState` | CalibrationSearchState | Whatever the search strategy needs to resume deterministically |
| `result` | ListenerProfile? | Set only when `state` is COMPLETED |

State transitions: IN_PROGRESS to COMPLETED (a range was found, or the maximum number of judgments was reached), or IN_PROGRESS to ABANDONED (listener discarded it, or it timed out from inactivity). No transition out of COMPLETED or ABANDONED. Only an IN_PROGRESS session is resumable, and none of them may overwrite `profile` before reaching COMPLETED.

### ToneJudgment

| Field | Type | Notes |
|---|---|---|
| `carrierHz` | Double | The pitch presented (the pair's centre) |
| `verdict` | enum: RELAXING, NOT_RELAXING | The two tap targets |
| `presentedAt` | Instant | |
| `responseMillis` | Long | Time from tone start to tap; lets a later analysis spot inattentive or reflex answers |

### SessionConfiguration

The listener's choices (FR-010, FR-011).

| Field | Type | Default |
|---|---|---|
| `beatArc` | enum: DESCEND_THEN_HOLD, CONSTANT, DESCEND_THEN_VARY | DESCEND_THEN_HOLD |
| `endBehavior` | EndBehavior | RunUntilStopped |
| `carrierCount` | Int, 2 or 3 | 3 |
| `volumeWarningAcknowledged` | Boolean | false (FR-026a) |

**EndBehavior** is one of: `RunUntilStopped`; `AfterDuration(duration)`; `AtClockTime(localTime)`. For `AtClockTime`, the fade MUST complete at or before the stated local wall-clock time (see the daylight saving edge case in the spec), so the scheduler works from local time, not from a fixed offset computed at start.

### SessionRecord

The most recent session only (FR-029).

| Field | Type | Notes |
|---|---|---|
| `startedAt` | Instant | |
| `endedAt` | Instant? | Null while running |
| `endReason` | enum: STOPPED_BY_LISTENER, COMPLETED_AS_CONFIGURED, OUTPUT_LOST, INTERRUPTED, UNKNOWN | UNKNOWN covers a process death, so the app never claims a session is running when it is not (FR-025) |
| `configuration` | SessionConfiguration | As used, not as currently set |
| `profile` | ListenerProfile | As used |
| `renderSeed` | Long | Makes the night reproducible offline (research R5) |
| `carrierSummary` | List of Double | The pitch range actually visited, sufficient to check SC-005 |

### Sound layer model (FR-014)

**SoundLayer** is the contract, not a stored entity: a source that fills a buffer for a time window and carries its own gain envelope. **BinauralToneLayer** is the only implementation in this feature. A session holds a list of layers and a mixer sums them. Adding a layer type later means adding an implementation, with no change to the scheduler, the mixer, or calibration.

**CarrierPair**: derived state within the tone layer, not stored. Holds the left and right frequencies, whose difference is the current beat rate; all active pairs share one beat rate (FR-003).

## Derived values, not stored

Beat rate at a given moment, the current drift position (FR-004a), and fade gains are all computed from the configuration, the profile, the seed and elapsed time. Keeping them derived is what makes a session reproducible offline and keeps persistence to the four records above.
