# Quickstart and Validation: Calibrated Sleep Sessions

**Date**: 2026-09-16 | **Feature**: [spec.md](./spec.md)

How to build, run and prove this feature works. Scenarios map to the spec's success criteria.

## Prerequisites

- Android Studio with the project's pinned JDK (Gradle provisions it; do not rely on a system JDK).
- The physical Galaxy S24 with USB debugging. **No emulator**: an emulator cannot demonstrate all-night playback through Bluetooth sleep earbuds.
- Ozlo Sleepbuds (or any stereo headphones) paired to the phone. Stereo separation on the Ozlos was verified on 2026-09-15.
- `adb` is not on PATH by default: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`.

## Build and test

```
./gradlew :core:test           # pure JVM, fast, no device
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

## Automated validation

### V1. Continuity over a full night (SC-002, constitution II)

```
./gradlew :core:test --tests "*FullNightContinuity*"
```

Renders a ten-hour session through the same renderer used for playback, streams it through the analyzer, and fails on any discontinuity beyond the configured limits. Expected: zero violations, and a run time in seconds rather than minutes.

### V2. Every tone inside the profile range (SC-005)

Part of `:core` tests: renders sessions from generated profiles and asserts no carrier ever leaves the range and no pair violates minimum spacing.

### V3. Reproducibility (research R5)

Two renders with the same seed produce identical output; different seeds differ. Without this, a continuity failure could not be reproduced.

### V4. Non-vacuous tests (constitution III)

Mutation check, run by hand and recorded in the PR: break one behavior deliberately (remove the fade-in, disable spacing enforcement, freeze the drift) and confirm named tests fail. A test suite that survives these is not testing what it claims.

## Manual validation on the S24

### M1. First session within two minutes (SC-006)

Fresh install, no calibration. Open, choose a preset pitch, start. Expected: audio fades in, three taps or fewer, under two minutes from launch.

### Reading the diagnostics (FR-029a)

After any run, including one that ended while you slept:

```
adb shell run-as com.dnoel.binauralbeats cat files/app_state.json
```

`recentEndings` holds the last 20 endings, each with when the session started and ended,
how it ended, and the underrun count. This exists because a run stopped after about two
hours on 2026-10-05 and nothing survived to say why: the single most-recent-session record
had been overwritten by a later test, and the system log had rotated.

What the reasons mean when reading a morning-after record:

- `STOPPED_BY_LISTENER`: the stop control was used.
- `COMPLETED_AS_CONFIGURED`: the configured end behavior ran.
- `OUTPUT_LOST`: the earbuds stayed away past the grace period, so the app ended it.
- `INTERRUPTED`: audio focus was taken permanently, or the writer thread failed.
- `UNKNOWN`: the process died without running any cleanup. The likeliest cause is the
  system stopping the app, which on Samsung hardware means battery optimization is worth
  checking first.

### M2. All-night playback (SC-001, FR-009)

Start a session, lock the phone, leave it overnight with the earbuds in. Expected in the morning: still playing, no gaps or restarts. Verify afterwards from the session record and from `getUnderrunCount()` reported in logs. Run this at least once with Battery Saver enabled, since Battery Saver is known to increase underruns substantially (research R1).

### M3. Locked-phone control (FR-016, FR-017)

Mid-session, wake the screen without unlocking. Expected: a notification with a stop control that works. Unlock. Expected: a single dim Stop control and nothing bright.

### M4. Output loss and return (FR-024, SC-008)

Mid-session, take an earbud out or put both in the case. Expected: audio pauses, nothing plays from the phone speaker. Put them back within the grace period. Expected: audio fades back in and continues. Repeat, waiting past the grace period. Expected: the session ends cleanly and nothing claims to be running.

### M5. Interruption (spec edge case)

Mid-session, place a call or trigger an alarm. Expected: the session fades out and pauses, then fades back in after, never resuming at full level abruptly.

### M6. Calibration (SC-004, FR-019 to FR-023)

In a dark, quiet room, run calibration start to finish. Expected: two large tap targets, gradual transitions between tones, completion within about 12 minutes, a saved range shown in plain language. Then interrupt a second calibration halfway and confirm it resumes and that the earlier profile survived until the new one completed.

### M7. Backup and restore (SC-007)

With a profile and settings saved, uninstall and reinstall, then let the platform restore. Expected: profile and settings return with no manual export.

## What to capture for the README

Screenshots of the calibration screen and the dim in-session Stop, plus the morning-after evidence from M2. The previous project shipped a README whose weakest part was screenshots that showed only the start screen.
