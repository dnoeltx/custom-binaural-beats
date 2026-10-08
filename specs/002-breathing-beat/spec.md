# Feature Specification: Breathing Beat

**Feature Branch**: `feature/002-breathing-beat`

**Created**: 2026-10-08

**Status**: Draft

**Input**: User description: "An Android feature that makes a sleep session's beat breathe: the pulse sweeps slowly up and down inside a bounded band rather than holding one rate, with the pair's pitch wandering slowly beneath it."

## Context

Feature 001 produces a session whose beat descends over the opening stretch and then holds one steady rate for the night. Listening on 2026-10-08 established that a beat which keeps moving, slowly, is preferred to one that settles and stays. This feature adds that movement. It does not replace calibration, the session engine, or any behavior in 001.

Three findings from that listening session govern the design:

1. **One tone per ear beat both ears mixed decisively.** The binaural arrangement stays.
2. **Movement must be measured in tens of minutes.** Cycles of 20 to 150 seconds were described as annoying within a minute; cycles of 10 to 30 minutes were not.
3. **The beat must be a bounded parameter, never an emergent one.** An earlier arrangement let each ear glide on its own cycle, so the gap between them, which is the beat, reached about 15 Hz. That was immediately audible as the sound turning bumpy, and it violated the measurement from 001 that 4 Hz is poor and 6 Hz unsettling.

A fourth finding shapes the priorities: when pitch movement and beat movement were isolated from each other, the listener chose the clips where **the beat** moved. Pitch wander is secondary.

## Clarifications

### Session 2026-10-08

- Q: Should breathing be a new arc, a modifier on top of whichever arc is chosen, or a change to what descend then hold does after its descent? (FR-118) -> A: A modifier, applicable to any arc.
- Q: Does the existing descend then vary arc still have a reason to exist now that breathing can apply to any arc? -> A: No. Remove it; breathing supersedes it, with measured values rather than invented ones.
- Q: Should the automated full night continuity check hold breathing sessions to feature 001's limits, or give them their own? -> A: Their own, measured separately, for the rate of change limits only. Click and level jump detection is unchanged.
- Q: Should the listener set the band and sweep period, or only turn breathing on and off with measured defaults? -> A: On and off only for now, with controls added later if real use shows they are wanted.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A pulse that breathes (Priority: P1)

The listener starts a session as usual. Instead of settling on one pulse rate for the night, the pulse slowly quickens and slows, over tens of minutes, never fast enough to be unsettling and never so still that it becomes a fixture. They do not watch it happen; they notice only that it is pleasant to lie with.

**Why this priority**: This is the feature. Everything else here supports it.

**Independent Test**: Run a session with breathing enabled and confirm from the rendered audio that the beat rate moves continuously within its band, and that no moment of abrupt change occurs.

**Acceptance Scenarios**:

1. **Given** a session with breathing active, **When** any two moments ten minutes apart are compared, **Then** the beat rate differs measurably.
2. **Given** a session with breathing active, **When** the whole night is examined, **Then** the beat rate never reaches the rate judged unsettling, and never falls to zero.
3. **Given** a session with breathing active, **When** the rate of change is measured at every instant, **Then** it never exceeds the limit set for audible but unremarkable movement.
4. **Given** a session with breathing active, **When** one ear is examined alone, **Then** it carries a single tone, because the beat exists only between the ears.

---

### User Story 2 - Choosing whether it breathes (Priority: P2)

The listener decides whether a session breathes or holds steady, in the same place they already choose how the night is shaped.

**Why this priority**: The steady behavior from 001 remains valid and some nights may call for it, but a listener who never changes the setting should still get the preferred sound.

**Independent Test**: Change the setting, run a session, and confirm the beat behaves as chosen.

**Acceptance Scenarios**:

1. **Given** a listener who has never opened settings, **When** they start a session, **Then** it breathes, because that is what listening preferred.
2. **Given** breathing turned off, **When** a session runs, **Then** the beat behaves exactly as feature 001 describes.
3. **Given** a session already playing, **When** the setting is changed, **Then** the running session is unaffected.

---

### User Story 3 - Pitch that wanders beneath it (Priority: P3)

Underneath the breathing pulse, the pair's pitch drifts slowly up and down, so the sound is never quite identical to how it was ten minutes ago.

**Why this priority**: Isolating the two movements showed the listener responds to the beat rather than the pitch. This adds depth but is not the point, and a session without it was still preferred to a static one.

**Independent Test**: Run a session with pitch wander active and confirm the pair's center pitch moves within the listener's range while the beat continues to breathe independently.

**Acceptance Scenarios**:

1. **Given** pitch wander active, **When** a night is examined, **Then** the pair's center pitch moves, stays inside the listener's profile range, and reverses rather than running to an edge.
2. **Given** pitch wander active, **When** the beat rate is examined, **Then** it follows its own schedule and is not disturbed by the pitch movement.
3. **Given** pitch wander turned off, **When** a session runs, **Then** the beat still breathes.

---

### Edge Cases

- **A profile too narrow to wander in.** A calibrated range may leave little room once carrier spacing is satisfied. Pitch wander reduces or stops rather than violating spacing or leaving the range.
- **Breathing during the opening descent.** The session already descends from a higher rate over the opening stretch. The two movements must not fight, double back, or produce a rate outside the band.
- **A session that ends mid sweep.** The fade to silence takes priority, the beat is never left somewhere unintended, and nothing changes abruptly at the end.
- **A session paused and resumed** after an interruption or a lost output. The breathing continues from where it paused rather than jumping to where it would have been.
- **A very long session**, beyond one full sweep of the slowest setting. The movement continues with no seam at the point where a sweep repeats.

## Requirements *(mandatory)*

### Functional Requirements

**The breathing itself**

- **FR-101**: The beat rate MUST be an explicit parameter of the session at every instant, bounded above and below, and MUST NOT be a byproduct of two tones moving independently.
- **FR-102**: The beat rate MUST sweep continuously between a low and a high value, in a smooth shape with no corners, reversing at each end.
- **FR-103**: The beat rate MUST NOT reach the rate previously measured as poor, and MUST remain above zero at all times, so the pulse never disappears entirely.
- **FR-104**: One complete sweep MUST take tens of minutes. The exact period is a measured value, see Measurement below.
- **FR-105**: The rate at which the beat changes MUST NOT exceed a limit set by listening, so the movement is noticeable only if sought.
- **FR-106**: Every tone MUST remain a binaural pair, one tone per ear, as in feature 001.

**Choosing it**

- **FR-107**: The listener MUST be able to choose whether a session breathes, and breathing MUST be the default.
- **FR-107a**: The band and the sweep period MUST NOT be exposed as controls in this feature. They come from measurement, as fades and carrier spacing do. Controls may be added by a later specification once real use shows which, if any, are wanted, so the values MUST be held as parameters that a settings screen could later supply rather than as constants in the audio code.
- **FR-108**: Turning breathing off MUST produce exactly the behavior specified in feature 001, with no residue of this feature.
- **FR-109**: A change to the setting MUST NOT affect a session already running.

**Pitch wander**

- **FR-110**: The pair's center pitch MAY wander slowly, as a movement separate from the beat, with its own slower period.
- **FR-111**: Pitch wander MUST stay inside the listener's profile range, MUST preserve the minimum carrier spacing at all times, and MUST reverse rather than run to an edge.
- **FR-112**: Pitch wander MUST be disableable on its own, without affecting the breathing.
- **FR-113**: Where a profile is too narrow to wander in, the wander MUST reduce or stop rather than violate FR-111.

**Everything carried over from 001**

- **FR-114**: Nothing MUST change abruptly. The continuity guarantees of feature 001 apply to this feature's movements.
- **FR-114a**: A breathing session MUST be checked against its own rate of change limits, measured separately, because feature 001's limits were derived from movement intended to be imperceptible while this movement is intended to be perceptible. The sample level checks that catch clicks, cuts and level jumps MUST remain exactly as they are: only the rate of change limits differ.
- **FR-114b**: The breathing limits MUST be derived by listening and recorded with their reasoning, never widened to make a failing check pass. A check that fails because the audio changed too fast is reporting a defect, and the response is to slow the audio.
- **FR-115**: A full night MUST remain renderable offline, faster than real time, through the same code that plays it, so that FR-102 through FR-105 are checked automatically rather than by listening.
- **FR-116**: The audio MUST remain reproducible from the session's own inputs, including any seed, so a complaint about a particular night can be reproduced.
- **FR-117**: The retained record of the most recent session MUST state whether it breathed, and the band and period it used.

**How it relates to the existing arcs**

- **FR-118**: Breathing MUST be a modifier that applies on top of whichever beat arc the listener has chosen, rather than a fourth arc or a change to an existing one. The arc decides where the beat rate sits over the night; breathing makes it sweep around that line.
- **FR-118a**: The descend then vary arc from feature 001 MUST be removed, leaving descend then hold and constant. Its slow wander was an unmeasured smaller version of breathing, was never chosen in any listening test, and keeping it would offer two overlapping ways to make the beat move. A stored setting naming it MUST fall back to descend then hold rather than failing to load.
- **FR-119**: With breathing active during an opening descent, the two movements MUST compose without the beat rate leaving its band, reversing direction unexpectedly, or exceeding the rate of change limit at any instant.

### Measurement

Per the project's constitution these numbers come from listening and are recorded with their reasoning. They are not fixed by this specification.

- The band the beat sweeps between. Two candidates were preferred: 1 to 4 Hz, and 2 to 3 Hz.
- The time one full sweep takes. Candidates preferred: 10 minutes and 30 minutes.
- The maximum rate of change of the beat.
- The depth and period of pitch wander. One candidate was preferred: 15 Hz over 20 minutes.

### Key Entities

- **Breathing settings**: whether a session breathes, the band it sweeps between, how long a sweep takes, and whether pitch wander is active with its own depth and period. Stored with the listener's other session settings.
- **Beat schedule**: the beat rate at any moment of a session, derived from the settings, the elapsed time, and the session's seed. Not stored, so a night can be reproduced from its inputs.
- **Pitch wander**: the offset applied to the pair's center pitch at any moment, bounded by the profile range and the carrier spacing rule.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-101**: Across a ten hour session, the beat rate stays within its band at every instant, never reaching the rate judged poor and never reaching zero.
- **SC-102**: Across the same session, the change in beat rate per minute never exceeds the measured limit.
- **SC-103**: The beat rate measurably differs between any two moments ten minutes apart, so the sound is never static.
- **SC-104**: A rendered full night contains no discontinuity beyond the product's limits, verified automatically rather than by listening.
- **SC-105**: With pitch wander active, every tone across a full night stays inside the listener's profile range, verifiable from the session's own record.
- **SC-106**: A listener who has never opened settings hears a breathing session on their first night.
- **SC-107**: Turning breathing off reproduces feature 001's audio exactly, for the same inputs.

## Assumptions

- The listener uses the same stereo earbuds assumed by feature 001, and the binaural arrangement is unchanged.
- The preferred band and period fall within the candidates already listened to, so the measurement task chooses between them rather than searching afresh.
- Breathing applies to the whole night rather than to a portion of it. A session whose beat breathes for an hour and then holds is not part of this feature.
- The listener does not need to see the beat rate. Nothing here adds a display, a graph, or a readout.
- Feature 001's calibration, output handling, notification, screens and persistence are unchanged by this feature.
