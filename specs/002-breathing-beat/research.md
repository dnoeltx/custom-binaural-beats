# Phase 0 Research: Breathing Beat

**Date**: 2026-10-08 | **Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Four questions were left open by the specification. Each is answered here with its reasoning, per constitution Principle V.

## R1. How breathing composes with the opening descent (FR-119)

**Decision**: the arc defines where the beat rate sits at each moment, and breathing sweeps around that line with an amplitude that **ramps in across the opening descent**, reaching full depth only once the descent has finished.

**Rationale**: the descent exists to settle the listener, and it reads as a settling signal precisely because the pulse is slowing. A sweep at full depth during that stretch would make the pulse quicken again partway down, which contradicts the one thing the opening is for. Ramping the amplitude from zero also keeps the composed rate of change inside its limit without arithmetic gymnastics: during the descent the arc is moving fastest and the sweep is moving least.

**Composition rule**: `rate(t) = arc(t) + amplitude(t) * sweep(t)`, where `amplitude(t)` rises from zero to full over the descent and `sweep(t)` is a smooth cycle between the band edges. The composed rate is then clamped into the band, and the clamp is asserted to be inert in tests: if it ever engages, the parameters are wrong rather than the clamp being useful.

**Alternatives considered**: breathing only after the descent completes, which produces a visible seam at the moment it starts; and breathing at full depth throughout, which fights the descent as described.

## R2. How the continuity analyzer learns which limits apply (FR-114a)

**Decision**: the caller supplies the limits. `ContinuityAnalyzer` keeps its single `ContinuityLimits` parameter, and a new factory produces the right limits for a given session from its configuration, the tuning, the sample rate and the profile.

**Rationale**: the analyzer has one job, and teaching it about sessions would make it know about the thing it is meant to judge. The call site already knows whether breathing is on. This also matches how M002's limits are already derived rather than fixed: the factory extends that derivation rather than introducing a new concept.

**What differs between the two sets**: only the rate of change limits. The sample level checks that catch clicks, cuts and slope corners stay exactly as they are, because a click is a click whether or not the beat is sweeping. This is FR-114a, and the tests assert it by feeding a planted click through the breathing limits and confirming it is still caught.

**Alternatives considered**: limits that vary with time inside one render, which is more faithful but makes a failure hard to interpret; and two analyzers, which duplicates the streaming logic that already exists.

## R3. Whether pitch wander belongs in the existing drift

**Decision**: a separate, named movement. Drift stays as it is.

**Rationale**: they differ in every way that matters.

| | Drift (feature 001) | Pitch wander (this feature) |
|---|---|---|
| Purpose | stop the sound becoming a fixture | an audible slow movement the listener enjoys |
| Perceptibility | deliberately below threshold | deliberately above it |
| Applies to | each carrier independently | the whole pair together |
| Effect on spacing | changes the gaps between carriers | preserves them |
| Effect on the beat | none | none |
| Disableable | no | yes, on its own (FR-112) |

Folding wander into drift would mean one mechanism with two contradictory purposes and a single rate limit that has to satisfy both.

**Composition**: `carrier(i, t) = resting(i) + drift(i, t) + wander(t)`. Drift moves carriers apart and together; wander moves all of them as one.

**The shared budget, which is the real constraint**: both movements live inside the slack left over once the minimum carrier spacing is satisfied. That slack is already small for a narrow profile. The plan allocates it explicitly rather than letting the two movements discover each other at the range edge: drift keeps the allowance it has today, wander gets what remains, and where nothing remains the wander is zero (FR-113).

## R4. Removing an arc from data that is already stored

**This is the finding that matters most in this document, and it is a data loss risk rather than a tidiness question.**

A real device has `"beatArc":"DESCEND_THEN_VARY"` in its stored state right now. Deleting that constant from the enum makes the stored payload fail to decode. Our own rule, written in feature 001 and tested, is that an unreadable payload falls back to defaults. **So the obvious implementation of "remove the arc" would silently discard the listener's saved profile and settings on the first launch after upgrading**, which is the exact outcome the rule exists to prevent in the opposite case.

**Decision**: `BeatArc` gets a serializer that maps any unrecognized name, including the removed one, to `DESCEND_THEN_HOLD`, leaving the rest of the stored object intact. The schema version is not bumped, consistent with the reasoning already recorded for the diagnostics field.

**Verification**: a test decodes a payload containing the removed arc, taken from the real device file, and asserts that the profile, settings and session record all survive and that the arc reads as descend then hold. This mirrors the test written when the diagnostics field was added.

**Alternatives considered**: keeping the constant as deprecated, which leaves a dead option that some future screen will eventually render; and bumping the schema version, which under our own fallback rule destroys the saved profile, the very thing a listener would most hate to lose since recovering it costs another calibration sitting.

### What the device actually had (T201 and T202, captured 2026-10-08)

The payload was captured before any build without the arc ran, and it says something the plan did not assume: **the stored arc is `DESCEND_THEN_HOLD`, not the removed `DESCEND_THEN_VARY`.**

That is because the settings screen offering a choice of arc was never built. It is tasks T054 to T059 of feature 001, still unstarted, so nothing has ever been able to select the arc that is now being removed.

**What this changes, and what it does not.** The migration hazard is theoretical for this particular device rather than imminent, so the risk of losing a profile on upgrade is lower than feared. The tolerant serializer is still worth building, for a reason that outlives this arc: our fallback rule turns **any** unreadable stored value into defaults, so every future enum change carries the same hazard, and the serializer is the general defense rather than a one time fix.

**The fixture is therefore synthetic**, and labeled as such: `core/src/test/resources/stored-state-with-removed-arc.json` is the real captured file with the arc value swapped to the removed one, so it keeps a real profile shape, a real session record and a real diagnostics log around the value under test. The untouched capture is kept beside it as `stored-state-before-arc-removal.json`.

**Bonus from the capture.** The diagnostics record added last week held five entries, including a session from 2026-10-07 21:30 to 2026-10-08 06:28: **8.96 hours, ended by the listener, zero underruns**. That is the number that rotated out of the system log twice before anyone could read it, and it is the first durable evidence for SC-001 of feature 001.

## Measurement, carried into tasks

Nothing numeric is decided here. The candidates the listener preferred on 2026-10-08 are recorded in the spec, and the tasks will pin:

- the band the beat sweeps between, from the preferred 1 to 4 Hz and 2 to 3 Hz
- the sweep period, from the preferred 10 and 30 minutes
- the maximum rate of change of the beat, which becomes the breathing continuity limit
- the depth and period of pitch wander, from the preferred 15 Hz over 20 minutes

Each is set by listening on the physical device with the sleep earbuds, and recorded with its reasoning.
