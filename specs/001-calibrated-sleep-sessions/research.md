# Phase 0 Research: Calibrated Sleep Sessions

**Date**: 2026-09-16 | **Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

All findings verified against primary sources on the date above, per constitution Principle V. Version-sensitive items are marked for re-verification at implementation time.

## R1. Audio output mechanism

**Decision**: `AudioTrack` in `MODE_STREAM`, fed pre-generated PCM from Kotlin, running on a dedicated writer thread inside the playback service, with `PERFORMANCE_MODE_POWER_SAVING` and a deliberately large buffer.

**Rationale**:

- The power saving path "typically has deeper internal buffers and better underrun resistance, with a tradeoff of higher latency" ([Android media docs](https://developer.android.com/media/media3/exoplayer/battery-consumption)). This product has no latency requirement at all: nothing responds to input in real time, and a second of startup delay is invisible. Trading latency for underrun resistance is exactly the right trade here.
- No NDK, no C++ toolchain, no third-party audio dependency. This matches the approach already used in the owner's other app, which used platform text-to-speech rather than adding a library.
- Buffer sizing guidance is to start large, then tune down while watching `getUnderrunCount()`. We take the opposite discipline: start large and stay large, and use `getUnderrunCount()` as a test assertion rather than an optimization target.

**Alternatives considered**:

- **Oboe / AAudio (NDK)**: the low-latency, pro-audio path. Rejected: it solves latency, which we do not have, at the cost of a C++ toolchain and a second language in the build.
- **Audio offload to a DSP**: the most power-efficient option for long playback, but it is designed for *encoded* streams handed to a hardware decoder. We synthesize raw PCM continuously, so there is nothing to offload.

**Risks and mitigations**:

- **Battery Saver throttles the CPU** and has been measured to increase audio underruns by about 300% ([Android media docs](https://developer.android.com/media/media3/exoplayer/battery-consumption)). Mitigation: large buffers, power saving performance mode, generation work that is cheap and allocation-free, and an explicit overnight verification on the S24 with Battery Saver enabled.
- **Garbage collection pauses** over ten hours. Mitigation: pre-allocate all sample buffers once and reuse them; the core generator writes into a caller-supplied array and allocates nothing per block. This is a testable property, not a hope.

**To re-verify at implementation**: exact `AudioTrack` builder API shape and whether `ENCODING_PCM_FLOAT` or 16-bit integer output is the better fit for the mixer.

## R2. Persistence

**Decision**: DataStore holding a single serialized application-state object, with kotlinx.serialization providing the serializer. No Room, no SQLite.

**Rationale**: Android's own guidance is explicit: "If you need to support large or complex datasets, partial updates, or referential integrity, consider using Room instead of DataStore. DataStore is ideal for small datasets" ([DataStore docs](https://developer.android.com/topic/libraries/architecture/datastore)). Our data is one profile, one settings object, one in-progress calibration, and one most-recent-session record (FR-029). There are no relational queries, no partial updates, and nothing grows without bound. A JSON serializer rather than protocol buffers keeps the data classes in the pure-Kotlin core module, so persistence never dictates the core's shape.

**Alternatives considered**:

- **Room**: familiar from the owner's two previous apps and would give more migration practice, but it brings SQLite, a DAO layer and a schema for roughly four records. Rejected as machinery exceeding the problem. Note that this is a deliberate departure from the previous projects rather than an oversight.
- **Proto DataStore**: type-safe and nested-object capable, but requires a `.proto` schema and a codegen plugin, and would pull the data definitions out of the core module.
- **Plain SharedPreferences**: no flow-based reads, no atomic writes, no typed objects.

**Consequence to record**: schema evolution is now our own problem. The stored object carries a version field from day one, and an unreadable or future-versioned file MUST fall back to defaults rather than crash, and MUST NOT silently destroy a saved profile.

## R3. All-night playback with the screen locked

**Decision**: a foreground service with `android:foregroundServiceType="mediaPlayback"`, declaring `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

**Rationale**: verified in the [foreground service types documentation](https://developer.android.com/develop/background-work/services/fgs/service-types): `mediaPlayback` requires no runtime permission and, unlike `dataSync` and `mediaProcessing`, has **no time limit**. The service is started from the visible app when the listener presses Start, so background-start restrictions do not apply.

Note for Android 15 and later: an app may request audio focus only when it is the top app or is running a foreground service ([audio focus docs](https://developer.android.com/media/optimize/audio-focus)). Our service satisfies this, but focus MUST be requested from within the running service rather than before it starts.

The app does not keep the screen on and does not bypass the lock screen (FR-015).

## R4. Interruptions and output changes

**Decision**:

- Request audio focus with `AUDIOFOCUS_GAIN` and `USAGE_MEDIA` via `AudioFocusRequest` (API 26+).
- On **transient loss** (with or without ducking permitted): fade out, pause, and on regain fade back in. We do not allow automatic ducking, because a level that jumps down and back up is precisely the kind of attention-grabbing change Principle I forbids.
- On **permanent loss**: fade out and end the session. The listener restarts it deliberately.
- Register a receiver for `ACTION_AUDIO_BECOMING_NOISY`, which the system broadcasts when a headset is unplugged or a Bluetooth device disconnects, and which media apps are expected to treat as "pause" ([Android audio output guidance](https://developer.android.com/guide/topics/media-apps/volume-and-earphones)).

**FR-024 grace period**: on output loss, pause and hold. Watch for the output returning (audio device callbacks). If it returns within the grace period, fade back in and continue the arc from where it paused. If not, end the session cleanly and stop the service so nothing claims to be running (FR-025).

**Proposed grace period: 3 minutes**, on the reasoning that a bud falling out or a brief walk out of range resolves in well under that, while a dead battery never will. This is a proposal to confirm by observation, not a measured value.

## R5. Verifying continuity offline (constitution II)

**Decision**: the core module exposes a deterministic renderer that, given a configuration, a profile and a start time, produces the session's samples in sequential blocks with no audio device involved. Continuity is asserted in two tiers:

- **Tier 1, parameter timeline**: the scheduler's output (per-block target frequencies and gains) is checked across a full ten-hour session for rate-of-change violations and for range and spacing compliance. Cheap, runs on every commit.
- **Tier 2, sample domain**: the renderer is streamed through an analyzer that accumulates statistics (maximum sample-to-sample delta, per-block RMS and its rate of change, discontinuity count) without ever holding the whole session in memory. Ten hours of stereo audio at full rate is roughly 12 GB, so materializing it is not an option; a streaming analyzer with a fixed-size window is.

For speed, the full-length tier 2 run uses a reduced analysis sample rate, with a full-rate run over shorter windows around every scheduled transition (session start, arc changes, fades, pause and resume). The reduced rate is legitimate here because the properties under test are envelope and parameter continuity, not audio bandwidth.

**Determinism requirement**: the renderer MUST produce identical output for identical inputs, including any randomness used by the drift in FR-004a, which therefore takes an explicit seed. Without that, a failing continuity test could not be reproduced.

## R6. Volume warning level (FR-026a)

**Finding**: the WHO and ITU safe listening standard sets an adult reference of **80 dB(A) for 40 hours per week**, with the safe duration shrinking as level rises (90 dB is about 4 hours per week), and recommends keeping device volume at or below about 60% of maximum ([WHO](https://www.who.int/news-room/questions-and-answers/item/deafness-and-hearing-loss-safe-listening), [WHO-ITU standard](https://www.who.int/publications/i/item/9789241515276)).

**The honest problem**: eight hours a night is about 56 hours a week, which already exceeds the 40-hour reference *at* 80 dB. More importantly, **Android exposes no way to know the actual sound pressure level in the listener's ear.** It depends on the earbuds, the fit, and the file. Any claim of decibels would be invented.

**Decision**: the warning is expressed in terms the app can honestly stand behind. It triggers on the system media volume index relative to its maximum, using a threshold expressed as a fraction, and its text says that prolonged all-night listening is not recommended at this level and that lower is safer. It does not state a decibel figure, does not track cumulative exposure, and does not claim a safety guarantee.

**Proposed threshold: 60% of maximum media volume**, taken from the WHO recommendation, applied to the only quantity the app can actually read. To be recorded in the plan as a value derived from guidance rather than measured.

**Also relevant**: Android already shows its own high-volume warning on many devices for wired and Bluetooth output. Ours adds the all-night context rather than duplicating a general one.

## R7. Platform and toolchain

**Decision**: Kotlin with Jetpack Compose and Material 3; Gradle Kotlin DSL; JDK pinned via `gradle/gradle-daemon-jvm.properties` as in the owner's other repository. Two modules: a pure-Kotlin `:core` with no Android dependency, and `:app`.

**minSdk**: 26 is the floor implied by the APIs above (`AudioFocusRequest` and notification channels both arrived at 26, and Auto Backup at 23).

**To verify at implementation, not from memory**: current Play target API requirement, current stable versions of Kotlin, Compose, DataStore and the Android Gradle Plugin, and the JDK version to pin. The owner's previous project was bitten by a stale version written from memory, which is exactly the failure this note exists to prevent.

## R8. Toolchain versions (verified 2026-09-16, task T001)

Queried from primary sources on the date above: Maven metadata for library artifacts, the GitHub releases API for actions, and vendor documentation for the rest. Re-verify before any future bump; do not copy these forward from memory.

| Component | Version | Source |
|---|---|---|
| Kotlin | 2.4.20 (2026-09-07) | [kotlinlang.org releases](https://kotlinlang.org/docs/releases.html) |
| Android Gradle Plugin | 9.4.0, requires Gradle 9.6.0+ and JDK 17+, max API 37 | [AGP release notes](https://developer.android.com/build/releases/gradle-plugin) |
| Gradle | 9.7.0 | wrapper carried over from the owner's previous project, above the AGP minimum |
| JDK | Temurin 21, pinned in `gradle/gradle-daemon-jvm.properties` | LTS, above the AGP minimum of 17 |
| Compose BOM | 2026.09.00 | Google Maven metadata |
| DataStore | 1.2.1 | Google Maven metadata (1.3.0 is alpha; stable chosen) |
| lifecycle-service | 2.11.0 | Google Maven metadata (2.12.0 is alpha) |
| activity-compose | 1.13.0 | Google Maven metadata (1.14.0 is alpha) |
| kotlinx-serialization-json | 1.11.0 | Maven Central (1.12.0 is RC; stable chosen) |
| kotlinx-coroutines-test | 1.11.0 | Maven Central |
| Robolectric | 4.17 | Maven Central |
| JUnit Jupiter | 6.1.3 | Maven Central |
| actions/checkout | v7.0.1 (2026-07-20) | GitHub releases API |
| actions/setup-java | v6.0.1 (2026-09-09) | GitHub releases API |
| gradle/actions/setup-gradle | v6.3.0 (2026-08-02) | GitHub releases API |

**Play target API requirement**: new apps and updates must target API 36 or higher as of 2026-08-31 ([Play target API docs](https://developer.android.com/google/play/requirements/target-sdk)).

**SDK levels chosen**: `compileSdk` 37 (the maximum AGP 9.4.0 supports), `targetSdk` 36, `minSdk` 26. Targeting 36 satisfies Play while avoiding API 37 behavior changes that nothing in this feature needs. Revisit when a feature requires an API 37 capability.

**Test frameworks**: `:core` uses JUnit Jupiter; `:app` uses JUnit 4 because Robolectric's runner requires it. A mixed setup is deliberate rather than accidental.

**Method note worth recording**: a documentation page summarized the `gradle/actions` release as dated 2024. The GitHub releases API showed 2026-08-02 for the same tag. Version facts were taken from the API and from Maven metadata, never from a prose summary.
