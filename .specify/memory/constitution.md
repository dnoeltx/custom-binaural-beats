# Custom Binaural Beats Constitution

## Core Principles

### I. No Surprises

The product exists to help a listener relax and fall asleep, so nothing it plays may draw attention.

- Every change in the audio MUST be gradual: no tone, layer, or the session itself may start,
  stop, or change pitch or volume abruptly.
- Nothing may enter a session that the listener did not choose. User-selected layers (for
  example noise, rain, or birds) are permitted; they MUST either be present from the start of
  the session or fade in on a schedule the listener configured.
- A layer MUST NOT contain transients that stand out from the rest of the mix (a deep boom, a
  sudden call). Recorded material is screened for this before it ships.
- The numeric limits that define "gradual" and "stand out" (ramp durations, maximum rate of
  change, transient thresholds) MUST come from listening tests and be recorded with their
  reasoning, per Principle V.

Rationale: every complaint that motivated this app was a surprise: a tone that just stops, rain
arriving five minutes in, an occasional boom. Not a jolt, but attention, which is the opposite of
the goal.

### II. Continuity Is Proven by Test

- The audio engine MUST be able to render any complete session offline, without a device and
  faster than real time.
- An automated test MUST render full-length sessions (including the longest supported duration)
  and fail on any discontinuity or step change that exceeds the Principle I limits.
- No change affecting generated audio merges without that test passing in CI.

Rationale: an eight-hour night cannot be a manual test loop. Rendering it offline turns the core
promise of Principle I into something a machine checks on every change.

### III. Test-First

- Tests are written before the code they cover, everywhere in the codebase, and are seen to fail
  before the implementation makes them pass.
- Code that is hard to test directly (audio output, the background service, Bluetooth behavior)
  MUST sit behind interfaces so the logic around it can still be developed test-first.
- Where a test genuinely cannot precede the code, the pull request MUST state why and describe
  how the behavior was verified instead. Silent exceptions are not permitted.
- Tests MUST be shown to be non-vacuous: deliberately breaking the behavior a test guards MUST
  make it fail.

### IV. Spec Before Code

- No feature is implemented without a specification that has passed the Spec Kit workflow gates
  (specify, clarify, plan, tasks, analyze).
- Ideas that emerge during development go to the backlog and become their own numbered
  specification. They MUST NOT be folded into the feature in progress.
- Scope changes to an in-progress feature require updating its spec first, then the plan and
  tasks, before the code changes.

Rationale: the aim is a predictable outcome instead of features one-offed as they come up, while
still giving new ideas a proper path in.

### V. Evidence Over Assumption

- Claims about platforms, library versions, APIs, and hardware behavior MUST be verified against
  a primary source or measured on a real device before a design depends on them.
- Thresholds and tuning values (calibration logic, fade lengths, carrier spacing, volume limits)
  MUST come from data, with the evidence and reasoning written down.
- A partial or degraded result MUST fail loudly rather than be presented as complete.

### VI. Honest About Effects

- The app, its store listings, and its documentation describe what the app generates, never what
  it does to the body or mind.
- No claims to treat, cure, diagnose, or improve sleep or any condition.

Rationale: the evidence on binaural beat effects is mixed, and app store health-claim policies
are strict. Describing the product accurately satisfies both.

### VII. Platform-Neutral, Layered Core

- Calibration, session scheduling, mixing, and synthesis parameters MUST contain no platform
  types, so an iOS port reuses this logic rather than rewriting it.
- Platform-specific code is limited to audio output, user interface, background execution, and
  platform services, each behind an interface.
- A session is a composition of independent sound layers, each with its own gain envelope, mixed
  by a shared engine. Binaural tone layers are the first layer type.
- Adding a new layer type (generated noise, recorded ambience, or others) MUST NOT require changes
  to the scheduler, the mixer contract, or calibration. Layer types beyond binaural tones are not
  in the MVP, but the architecture MUST accommodate them from the start.

### VIII. The User Owns Their Data

- Calibration results, presets, and listening history are stored on the device.
- The only path off the device is the platform's own user-controlled backup (Android Auto Backup,
  iCloud backup).
- No user accounts, no analytics, and no app-operated servers. Introducing hosted backup, sync, or
  any telemetry requires a constitution amendment first.

## Constraints

- **Headphones are assumed.** Binaural beats require separate left and right signals. Stereo
  separation was verified on Ozlo Sleepbuds on 2026-09-15; other outputs are not assumed to
  preserve it.
- **Perceptual bounds.** Binaural carriers stay below 1000 Hz and the left/right difference below
  30 Hz, the ranges within which published research reports a perceptible binaural beat. Changes
  to these bounds require cited evidence.
- **Offline.** Every feature in the MVP works with no network connection.
- **Bundled content is licensed.** Any recorded audio shipped with the app has a documented
  license permitting redistribution.
- **Android first.** Android is the initial platform; iOS follows only if the Android app proves
  useful. Principle VII keeps that path open.

## Development Workflow and Quality Gates

- All work happens on a branch (`feature/`, `fix/`, `chore/`) and reaches `main` only by pull
  request. `main` is protected; force pushes and deletion are disabled.
- Once CI exists, its build and test check is required on `main`, enabled in the same pull
  request that introduces CI.
- Commit messages follow Conventional Commits.
- The project owner runs all git and GitHub commands personally; AI assistance advises and
  prepares, it does not execute repository operations on the owner's behalf.
- AI-generated code, claims, and version numbers are held to Principle V like any other input.
- Significant decisions and their reasoning are recorded in the README or the relevant spec, not
  only in conversation.
- Every plan phase includes a Constitution Check against these principles, and every pull request
  confirms compliance or documents a justified exception.

## Governance

- This constitution supersedes other practices in this repository. Where a spec, plan, or task
  conflicts with it, the constitution wins until it is amended.
- Amendments are made by pull request, with the rationale in the description, and require a
  version bump:
  - MAJOR: a principle removed or redefined in a backward-incompatible way.
  - MINOR: a principle or section added, or guidance materially expanded.
  - PATCH: clarifications and wording that do not change meaning.
- Existing specs and plans are re-checked against an amended constitution before further work on
  them proceeds.

**Version**: 1.0.0 | **Ratified**: 2026-09-15 | **Last Amended**: 2026-09-15
