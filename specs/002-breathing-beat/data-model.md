# Phase 1 Data Model: Breathing Beat

**Date**: 2026-10-08 | **Feature**: [spec.md](./spec.md)

Everything here lives in the pure Kotlin core and contains no Android types. Only two of these are stored; the rest are derived, which is what keeps a night reproducible from its inputs.

## Stored

### SessionConfiguration (changed)

Gains the listener's two choices. Both default to on, because that is what listening preferred (FR-107).

| Field | Type | Default | Notes |
|---|---|---|---|
| `beatArc` | BeatArc | DESCEND_THEN_HOLD | Loses the DESCEND_THEN_VARY option, see below |
| `breathing` | Boolean | true | Whether the beat sweeps (FR-107) |
| `pitchWander` | Boolean | true | Whether the pair's pitch wanders (FR-110, FR-112) |

The band and the sweep period are deliberately absent: they are tuning, not settings (FR-107a). They are held where a later settings screen could supply them.

### BeatArc (changed, and the one real hazard)

`DESCEND_THEN_VARY` is removed, leaving `DESCEND_THEN_HOLD` and `CONSTANT` (FR-118a).

**A real device already has the removed name in its stored file.** Deleting the constant naively makes that payload fail to decode, and our own rule turns an unreadable payload into defaults, which would discard the listener's saved profile and settings. So `BeatArc` gets a serializer that maps any unrecognized name to `DESCEND_THEN_HOLD` and leaves the rest of the object intact. The schema version is not bumped. See research R4.

### SessionRecord (changed)

| Field | Type | Notes |
|---|---|---|
| `breathed` | Boolean | Whether this session's beat swept (FR-117) |
| `breathingBandHz` | Pair of Double, nullable | The band used, null when it did not breathe |
| `breathingPeriodMillis` | Long, nullable | The sweep period used, null when it did not breathe |

Recorded because a night the listener wants to ask about must be reconstructible, and because the values will change as measurement refines them.

## Tuning, not stored

### SessionTuning (changed)

Gains the measured values this feature introduces. As with every other number in this object, they arrive as parameters rather than constants so a later version can measure them per listener.

| Field | Type | Notes |
|---|---|---|
| `breathingBandHz` | ClosedRange of Double | Where the beat sweeps. Its upper end MUST stay below `maxBeatRateHz` (FR-103) |
| `breathingPeriod` | Duration | One full sweep, in tens of minutes (FR-104) |
| `maxBeatChangePerMinute` | Double | The rate of change limit for a breathing session (FR-105) |
| `wanderDepthHz` | Double | How far the pair's center pitch moves either side |
| `wanderPeriod` | Duration | One full wander, slower than the breathing period |
| `driftBudgetShare` | Double, 0 to 1 | How the range slack divides between drift and wander (research R3) |

Validation: the band must sit inside the perceptible beat bounds, its top must be below the rate measured as poor, its bottom must be above zero, and the wander period must be longer than the breathing period, since the wander is the slower of the two movements.

## Derived, never stored

### BreathingSchedule

Given the tuning, the arc's rate at a moment, and elapsed time, produces the beat rate to use.

- `rateAt(elapsed, arcRate)`: the arc's rate plus the sweep, where the sweep's amplitude ramps from zero to full across the opening descent (research R1).
- `amplitudeAt(elapsed)`: zero at the start of the night, full once the descent has finished.
- The result is clamped into the band, and the clamp is expected never to engage. A test asserts that: if it engages, the tuning is wrong.

### PitchWander

- `offsetAt(elapsed)`: one value, applied to every carrier equally, so spacing and the beat are untouched (research R3).
- Bounded by whatever slack remains after the minimum carrier spacing and drift have taken theirs. Zero when nothing remains (FR-113).

### ContinuityLimits (changed)

Gains a factory that returns the right limits for a session: the existing limits when breathing is off, and the breathing limits when it is on. The sample level checks that catch clicks, cuts and slope corners are identical in both (FR-114a).

## What does not change

Calibration, the listener profile, carrier spacing, the layer and mixer contracts, output loss handling, the notification, the screens, and the session diagnostics record. The session still produces one tone per ear (FR-106).
