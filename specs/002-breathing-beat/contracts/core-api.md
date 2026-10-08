# Contract: what this feature adds to the `:core` boundary

**Date**: 2026-10-08 | **Feature**: [spec.md](../spec.md)

Feature 001's contract is unchanged. This adds three things to the same module and changes one signature. Signatures are indicative of shape and responsibility, not final.

## New: BreathingSchedule

Turns the arc's rate into a breathing one.

```
rateAt(elapsed: Duration, arcRateHz: Double): Double
amplitudeAt(elapsed: Duration): Double
```

Contract rules:

- The returned rate always lies inside the configured band.
- It never reaches the rate measured as poor, and never reaches zero (FR-103).
- Between any two instants, it changes no faster than the configured limit (FR-105).
- The amplitude is zero at the session's first instant and reaches full only once the opening descent is over, so the descent still reads as a descent (research R1).
- Pure and deterministic. The same inputs always give the same rate.

## New: PitchWander

One slow offset applied to every carrier equally.

```
offsetAt(elapsed: Duration): Double
```

Contract rules:

- The offset moves all carriers together, so carrier spacing and the beat are untouched (research R3).
- It stays inside whatever slack remains after spacing and drift, and is zero when nothing remains (FR-113).
- It reverses rather than running to an edge.
- Pure and deterministic.

## New: a factory for continuity limits

```
ContinuityLimits.forSession(
    configuration: SessionConfiguration,
    tuning: SessionTuning,
    profile: ListenerProfile,
    sampleRate: Int,
): ContinuityLimits
```

Contract rules:

- Returns feature 001's limits when breathing is off, and the breathing limits when it is on (FR-114a).
- The sample level limits, which catch clicks, cuts and slope corners, are **identical in both**. Only the rate of change limits differ. A planted click is caught either way, and a test proves it.
- `ContinuityAnalyzer` itself is unchanged and still takes one limits value. The caller decides.

## Changed: SessionScheduler

`parametersAt` and `writeParametersInto` keep their signatures. What changes is how the numbers inside are produced:

```
carrier(i, t) = restingCentre(i) + drift(i, t) + wander(t)
beatRate(t)   = breathing.rateAt(t, arcRate(t))
```

The existing invariants still hold and are still tested: every tone inside the profile range, carriers never closer than the minimum spacing, all pairs sharing one beat rate, nothing changing faster than its limit, and the whole night reproducible from the seed.

## Changed: BeatArc

Loses `DESCEND_THEN_VARY`, keeps `DESCEND_THEN_HOLD` and `CONSTANT`, and gains a serializer that maps any unrecognized name to `DESCEND_THEN_HOLD`.

**This is a data migration disguised as an enum edit.** A real device has the removed name stored. Without the tolerant serializer, that payload fails to decode, our fallback rule turns it into defaults, and the listener loses a calibrated profile that costs a twelve minute sitting to replace.

## Invariants this feature adds

1. The beat rate is always an explicit bounded value, never an emergent result of two tones moving independently (FR-101).
2. Turning breathing off reproduces feature 001's audio exactly, for the same inputs (FR-108, SC-107).
3. Pitch wander never changes the beat, and breathing never changes the pitch. The two movements are independent and separately disableable (FR-112).
