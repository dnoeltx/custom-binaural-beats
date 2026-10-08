# Quickstart and Validation: Breathing Beat

**Date**: 2026-10-08 | **Feature**: [spec.md](./spec.md)

How to build, listen to, and prove this feature. Scenarios map to the spec's success criteria.

## Prerequisites

Same as feature 001: Android Studio with the pinned JDK, the physical Galaxy S24 with USB debugging, and the Ozlo Sleepbuds. No emulator, because nothing about a sweeping beat can be judged without hearing it on the earbuds it is for.

`adb` lives at `$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` and is not on the path.

## Build and test

```
./gradlew :core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

## Automated validation

### V1. The beat stays inside its band for a whole night (SC-101)

Part of `:core` tests. Walks a ten hour session and asserts the rate never reaches the rate measured as poor, never reaches zero, and never leaves the band. The clamp that enforces the band is asserted never to engage: if it does, the tuning is wrong rather than the clamp being useful.

### V2. The beat never moves faster than its limit (SC-102)

Walks the same session comparing adjacent instants, including across the opening descent where the arc and the sweep move at once. This is the test that will fail first if the measured values are too eager, and the response is slower audio, not a wider limit (FR-114b).

### V3. The sound is never static (SC-103)

Asserts the rate measurably differs between moments ten minutes apart, which is the opposite failure to V2 and the reason breathing exists.

### V4. A full night is still continuous (SC-104)

The existing offline continuity render, extended to breathing sessions, using the limits from the factory. A planted click is run through the breathing limits to prove the sample level checks still catch it (FR-114a).

### V5. Breathing off reproduces feature 001 exactly (SC-107)

Renders the same session twice, once through this feature with breathing off and once through the behavior 001 specifies, and asserts the audio is identical for identical inputs.

### V6. The removed arc does not destroy stored state (research R4)

Decodes a payload containing `DESCEND_THEN_VARY`, taken from the real device file, and asserts the profile, settings and session record all survive and the arc reads as descend then hold. **Take the payload from the phone before upgrading it**, with:

```
adb shell run-as com.dnoel.binauralbeats cat files/app_state.json
```

## Listening, which is where the numbers come from

Each measurement task renders candidates offline with the WAV writer in test sources, then plays them through the phone. The rig that worked before: generate the files, serve them with `python -m http.server`, tunnel with `adb reverse tcp:PORT tcp:PORT`, and open the page in Chrome on the phone. Use one audio element and swap its source rather than one element per clip, because Chrome limits how many media elements a page can keep alive and the later ones silently refuse to play.

Clips must loop and run at least three minutes. A thirty minute sweep cannot be judged in forty seconds, and the question is never "can you hear it move" but "could this run for eight hours".

## Manual validation on the S24

### M1. A night that breathes

Start a session with breathing on and sleep. In the morning, read the diagnostics record for duration, reason and underruns:

```
adb shell run-as com.dnoel.binauralbeats cat files/app_state.json
```

Expected: a full night, ended by the listener, zero underruns, and a record stating the band and period used.

### M2. The toggle does what it says

Turn breathing off, start a session, and confirm the pulse settles and holds as it did before this feature.

### M3. Upgrading over real data

Install over the existing build without clearing data, and confirm the saved profile and settings survive. This is the one that catches the hazard in research R4, and it cannot be checked on a fresh install.

### M4. Does it actually help

The only question that matters, and the only one no test answers: after a few nights, is this better to sleep to than the steady version? If not, the feature is wrong regardless of how well it passes.
