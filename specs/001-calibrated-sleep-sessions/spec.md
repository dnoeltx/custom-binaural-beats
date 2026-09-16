# Feature Specification: Calibrated Sleep Sessions

**Feature Branch**: `feature/001-calibrated-sleep-sessions`

**Created**: 2026-09-16

**Status**: Draft

**Input**: User description: "An Android app that generates binaural beat audio for sleep, tuned to the individual listener. First run offers calibration or an immediate start from a few preset pitch choices (low, medium, high). Calibration is one relaxed sitting in a dark room: tones play, the listener taps one of two large half-screen targets (relaxing / not relaxing), and the taps narrow to a preferred carrier pitch range saved as a profile. Calibration tunes carrier pitch only; beat rate follows a schedule (beat rate tuning in settings is v2). Sessions use the saved profile, weaving 2-3 carriers inside the preferred range, each paired with a partner a few Hz apart. No feedback taps during a session. Beat rate arc over the night is configurable (gentle descent then hold [default], constant, or descend then vary) and end behavior is configurable (run until stopped [default], fade out after a set duration, fade out at a set clock time). The screen locks normally for security; the ongoing notification carries a stop control, and unlocking shows a single dim Stop control. Audio must run all night with the phone locked, through Bluetooth sleep earbuds. MVP is binaural tones only; the session must be modeled as layers so generated noise and recorded ambience can be added later without changing the scheduler, mixer contract, or calibration. Fine-tuning of the profile is available in settings."

## Clarifications

### Session 2026-09-16

- Q: When a session weaves two or three carrier pairs at once, should they all produce the same beat rate, or different ones? → A: All pairs share one beat rate (several pitches, one unified pulse).
- Q: Over a long night, should the carrier pitches stay fixed, or move slowly within the preferred range? → A: Very slow drift within the range, slow enough that no moment of change is perceptible.
- Q: During calibration, should each judged tone be a real binaural pair or a single steady tone? → A: Real binaural pairs, with the beat rate held at one fixed sleep-appropriate value throughout.
- Q: Should the app keep a record of past sessions, or remember nothing beyond profile and settings? → A: Remember the most recent session only; no accumulating history.
- Q: The measured 100 Hz minimum spacing means a narrow preferred range cannot hold the two or three carriers FR-003 requires. Accept single-carrier sessions, or constrain calibration to produce a usable range? → A: Constrain calibration (new FR-020a); a single carrier is a thinner sound than was approved, and the range is calibration's output to guarantee.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fall asleep to a session tonight (Priority: P1)

A listener who has just installed the app puts in their earbuds at bedtime, picks one of a few preset pitch choices (low, medium, high), and starts a session. The sound begins gently, continues without interruption while they fall asleep and while the phone locks itself, and keeps playing through the night. If they wake and want silence, they stop it from the notification without unlocking, or unlock and press a single dim Stop.

**Why this priority**: This is the whole product promise. Without it nothing else matters, and it delivers value on its own even if no other story is built.

**Independent Test**: Install, choose a preset, start a session, lock the phone, and confirm uninterrupted audio for a full night and that stopping works from both the notification and the unlocked screen.

**Acceptance Scenarios**:

1. **Given** a fresh install and connected earbuds, **When** the listener chooses a preset and starts a session, **Then** audio begins by fading in from silence and no sound event occurs that was not part of the chosen configuration.
2. **Given** a session is playing, **When** the phone locks itself and remains locked for eight hours, **Then** audio continues without gaps, restarts, or level jumps.
3. **Given** a session is playing and the phone is locked, **When** the listener wakes the screen, **Then** a notification offers a stop control that ends the session without unlocking.
4. **Given** a session is playing, **When** the listener unlocks the phone, **Then** the app shows a single dim Stop control and no other bright interface.
5. **Given** a session is stopped by the listener, **When** the sound ends, **Then** it fades to silence rather than cutting off.

---

### User Story 2 - Calibrate to my own ears (Priority: P2)

At a relaxed time, not necessarily bedtime, the listener runs calibration. In a dark, quiet room with earbuds in, tones play one after another. The listener taps the lower half of the screen when a tone feels relaxing and the upper half when it does not. The taps narrow toward a preferred pitch range, which is saved as their profile and used by every later session.

**Why this priority**: This is what makes the app different from a generic generator, but a listener can sleep to a preset without it.

**Independent Test**: Run calibration end to end, confirm it converges to a saved pitch range, then confirm a later session plays inside that range.

**Acceptance Scenarios**:

1. **Given** calibration is running, **When** a tone is playing, **Then** the screen shows only two large tap targets and a low-brightness progress indication, with no text requiring close reading.
2. **Given** the listener taps a judgment, **When** the next tone begins, **Then** the transition between tones is gradual and no tone stops abruptly.
3. **Given** enough judgments have been gathered, **When** calibration completes, **Then** a preferred pitch range is saved and the listener is told plainly that it is saved.
4. **Given** calibration is interrupted (the listener leaves, or the app is closed), **When** the listener returns, **Then** they may resume where they left off or discard it, and any previously saved profile is unchanged until a new one completes.
5. **Given** a saved profile exists, **When** a session starts, **Then** every tone it plays lies within the saved preferred range.

---

### User Story 3 - Shape the night (Priority: P3)

The listener configures how the session behaves over a night: whether the beat rate descends gently and then holds, stays constant, or descends and then varies, and whether the session runs until stopped, fades out after a chosen duration, or fades out at a chosen clock time.

**Why this priority**: Defaults serve the common case. These controls matter for someone who wants the session to be over before an alarm, or who finds a changing beat rate distracting.

**Independent Test**: Change each setting, run a session, and confirm the audio follows the configured arc and ends as configured.

**Acceptance Scenarios**:

1. **Given** the default configuration, **When** a session starts, **Then** the beat rate descends gently over the opening period and then holds steady for the rest of the night.
2. **Given** end behavior is set to a duration, **When** that duration elapses, **Then** the sound fades to silence gradually rather than stopping.
3. **Given** end behavior is set to a clock time, **When** that time arrives, **Then** the fade completes at or before that time.
4. **Given** any configuration change, **When** a session is already running, **Then** the running session is unaffected and the change applies to the next session.

---

### User Story 4 - Adjust my profile later (Priority: P4)

In settings, the listener reviews their saved profile, adjusts the preferred range by hand, or runs calibration again.

**Why this priority**: Preferences change and a first calibration may land imperfectly, but the app is usable without this.

**Independent Test**: Alter the profile in settings, then confirm the next session honors the change.

**Acceptance Scenarios**:

1. **Given** a saved profile, **When** the listener opens settings, **Then** the current preferred range is shown in plain language along with an option to adjust it or recalibrate.
2. **Given** the listener adjusts the range, **When** they save, **Then** the next session uses the adjusted range and the previous value is replaced.

---

### Edge Cases

- **Earbuds disconnect mid-session** (out of range, battery dies, case closed): the session pauses, never falls back to the phone speaker, resumes with a fade if the earbuds return within the grace period, and otherwise ends cleanly (FR-024).
- **Session started without stereo headphones**: the app warns that binaural beats require headphones, since the effect does not exist on a single speaker, and lets the listener continue anyway.
- **An interruption arrives** (phone call, alarm, another app's audio): the session yields as the platform requires, and afterward either resumes by fading back in or ends cleanly. It never resumes abruptly at full level.
- **Battery runs out or the system stops the app**: on next open, the app does not silently believe a session is still running.
- **Calibration where the listener rejects everything, or accepts everything**: calibration ends with an honest result rather than inventing a narrow range, and offers to retry or accept a default.
- **Calibration where the listener stops tapping** (falls asleep, walks away): the session pauses itself after a period of no input rather than running indefinitely.
- **Clock-time end behavior across a daylight saving change or a time zone change**: the fade happens at the intended local wall-clock time.
- **A very long session** (beyond the longest configured duration, for example a nap plus a night): the audio remains continuous and within the same constraints.
- **Session start while another session is already running**: only one session exists at a time.

## Requirements *(mandatory)*

### Functional Requirements

**Sound generation**

- **FR-001**: The system MUST generate binaural tones by delivering a separate tone to each ear, where the difference between the two produces the intended beat rate.
- **FR-002**: The system MUST keep every carrier tone below the upper limit at which a binaural beat is perceptible, and every beat rate below the difference limit, as recorded in the project constraints.
- **FR-003**: A session MUST weave two or three carrier pairs at once, all lying inside the listener's preferred range. All active pairs MUST share the same beat rate at any moment, so the listener hears several pitches but a single unified pulse.
- **FR-004**: Simultaneous carriers MUST be spaced so that they do not produce audible roughness or an unintended beat between them. The minimum spacing MUST be determined by measurement and recorded.
- **FR-004a**: Carrier pitches MUST drift slowly within the listener's preferred range over the course of a session, at a rate slow enough that no individual moment of change is perceptible. The drift MUST respect the minimum spacing in FR-004 at all times and MUST never carry a carrier outside the preferred range. The maximum drift rate is a tunable value to be set from measurement.
- **FR-005**: All audio MUST begin by fading in from silence and end by fading out to silence, including when the listener stops a session manually.
- **FR-006**: No change in pitch, level, or content during a session may exceed the rate-of-change limits defined for the product. Nothing may be introduced that the listener did not configure.
- **FR-007**: The system MUST be able to produce the full audio of a session as data, without playing it, so that continuity can be verified automatically.

**Sessions**

- **FR-008**: A listener MUST be able to start a session in three taps or fewer from opening the app.
- **FR-009**: A session MUST continue playing while the phone is locked and the screen is off, for at least ten hours.
- **FR-010**: The system MUST offer beat-rate arcs of: gentle descent then hold (default), constant, and descent then slow variation.
- **FR-011**: The system MUST offer end behaviors of: run until stopped (default), fade out after a chosen duration, and fade out at a chosen clock time.
- **FR-012**: A configuration change MUST NOT alter a session already in progress.
- **FR-013**: Only one session may run at a time.
- **FR-014**: A session MUST be modeled as a set of independent sound layers mixed together, each with its own level envelope. The MVP ships one layer type (binaural tones). Adding a layer type later MUST NOT require changing the session scheduler, the mixing contract, or calibration.

**Controls while sleeping**

- **FR-015**: The phone MUST lock normally during a session. The app MUST NOT hold the screen on or bypass the lock screen.
- **FR-016**: While a session runs, an ongoing notification MUST be present and MUST offer a control that stops the session without unlocking the phone.
- **FR-017**: On unlocking during a session, the app MUST present a single dim Stop control and no other bright interface.
- **FR-018**: The app MUST NOT emit any sound that is not part of the session. No completion chimes, no alerts.

**Calibration**

- **FR-019**: Calibration MUST present tones one at a time with two large tap targets covering roughly half the screen each: the lower for "relaxing", the upper for "not relaxing".
- **FR-020**: Calibration MUST use the listener's judgments to narrow toward a preferred carrier pitch range, and MUST stop once further tones would not meaningfully narrow it or a maximum number of judgments is reached.
- **FR-020a**: Calibration MUST NOT produce a preferred range too narrow to hold at least two carriers at the minimum spacing, plus the margin a pair needs around its centre. Where the listener's judgments would give a narrower range, calibration MUST widen it around the same centre and record that it did so. Added 2026-09-16 after measurement set the minimum spacing at 100 Hz, which made a narrower range physically unable to satisfy FR-003.
- **FR-021**: Calibration MUST tune carrier pitch only. Beat rate is not calibrated in this feature.
- **FR-021a**: Every tone presented during calibration MUST be a binaural pair, so the listener judges the same kind of sound a session produces. The beat rate MUST be held at a single sleep-appropriate value for the whole calibration, so that pitch is the only variable the listener is judging.
- **FR-022**: Calibration MUST be interruptible and resumable, and MUST NOT overwrite an existing saved profile until a new calibration completes.
- **FR-023**: On first run, the system MUST offer a choice between calibrating and starting immediately from a small set of preset pitch choices (low, medium, high). Calibration MUST NOT be required before the first session.

**Robustness**

- **FR-024**: If audio output is lost mid-session (earbuds disconnect, battery dies), the session MUST NOT continue playing to another output such as the phone speaker. It MUST pause and wait for the same kind of output to return. If it returns within a grace period, the session MUST resume by fading in from silence and continue its arc from where it paused. If the grace period passes, the session MUST end cleanly. The grace period is a tunable value to be set during planning.
- **FR-025**: The system MUST NOT present a session as running when no audio is playing.
- **FR-026**: The listener's volume MUST be under their control, and the system MUST NOT raise the level on its own.
- **FR-026a**: Above a defined level, the system MUST warn that prolonged listening at that level is not recommended for all-night use, and MUST allow the listener to continue anyway. The threshold MUST be set from published hearing-exposure guidance and recorded.
- **FR-026b**: The warning MUST be visual only and MUST NOT interrupt or alter audio in progress. It MUST appear while the listener is setting the level, never once a session is under way, so that it can never become a surprise during the night.
- **FR-027**: All functionality MUST work with no network connection.

**Data**

- **FR-028**: The profile, configuration, and calibration results MUST be stored on the device, MUST survive reinstalling the app through the platform's own backup, and MUST NOT be sent to any service operated by this project.
- **FR-029**: The system MUST retain a record of the most recent session only: when it started, how it ended, and what it played. Starting a new session MUST replace it. No accumulating history is kept, and no session history screen is part of this feature.

### Key Entities

- **Listener Profile**: the saved result of calibration. Holds a preferred carrier pitch range and the date it was produced. One active profile at a time; replaced when a new calibration completes or the listener edits it.
- **Calibration Session**: one sitting. Holds the sequence of tones presented, the judgment given for each, its progress, and whether it completed, was abandoned, or is resumable.
- **Tone Judgment**: a single decision, relating one presented tone to one of the two verdicts.
- **Session Configuration**: the listener's choices for beat-rate arc and end behavior, plus the preset pitch choice when no profile exists.
- **Session**: one night of playback. Knows its configuration, its profile, when it started, and how it ended (stopped by the listener, faded out as configured, or interrupted). Only the most recent one is retained.
- **Sound Layer**: one contributing source within a session, with its own level envelope. Binaural tones are the only type in this feature.
- **Carrier Pair**: two tones, one per ear, whose difference produces a beat. A session has two or three active at once.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A session plays for at least ten hours with the phone locked and produces no gap, restart, or abrupt level change.
- **SC-002**: A rendered full-length session contains no discontinuity exceeding the product's rate-of-change limits, verified automatically rather than by listening.
- **SC-003**: A listener can start a session in three taps or fewer, and stop it from a locked phone in two interactions or fewer.
- **SC-004**: Calibration completes in a single sitting of 12 minutes or less for a typical listener.
- **SC-005**: Every tone in a session after calibration lies within the listener's saved preferred range, verifiable from the retained record of the most recent session.
- **SC-006**: A listener who has never opened the app before can start their first session within 2 minutes of opening it, without calibrating.
- **SC-007**: Profile and settings survive reinstalling the app on the same device with no manual export.
- **SC-008**: When output is lost and restored within the grace period, the session resumes automatically with a fade and no listener action; when it is not restored, the session is no longer presented as running.

## Assumptions

- The listener uses stereo headphones or earbuds. Binaural beats do not exist without separate left and right signals, verified on the target sleep earbuds on 2026-09-15.
- The listener is a single person on their own device. No accounts, no multiple profiles per device, no sharing between devices in this feature.
- "Relaxing" is treated as a preference judgment by the listener, not a physiological measurement. The app claims nothing about effects.
- A typical night is about eight hours; ten hours of continuous playback is used as the design target to leave margin.
- The preset pitch choices offered before calibration are a small fixed set (low, medium, high) chosen to span the usable range, with the middle one as the default.
- Beat rate values for sleep, the opening descent duration, and fade lengths are treated as tunable numbers to be set from measurement during planning, not fixed here.
- The calibration procedure narrows a single dimension (pitch). The specific search strategy and the number of tones required are design decisions for the planning phase.
- No user-facing content is bundled in this feature (no recordings), so no content licensing applies yet.
