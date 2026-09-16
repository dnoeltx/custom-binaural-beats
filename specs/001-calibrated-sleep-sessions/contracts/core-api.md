# Contract: `:core` public interface

**Date**: 2026-09-16 | **Feature**: [spec.md](../spec.md)

This is the boundary between the platform-neutral core and the Android app (constitution VII). Everything here is expressed in plain Kotlin with no Android types and no platform assumptions. An iOS port reuses this module and supplies its own implementations of the outbound ports.

Signatures are indicative of shape and responsibility, not final. Types come from [data-model.md](../data-model.md).

## Inbound: what the app calls

### SessionScheduler

Turns configuration plus elapsed time into the parameters the audio should have right now. Pure and deterministic.

```
parametersAt(elapsed: Duration): SessionParameters
endsAt(startedAt: Instant): Instant?          // null for RunUntilStopped
```

`SessionParameters` carries the active carrier pairs (frequencies per ear), the shared beat rate, and a master gain. Contract rules:

- Every carrier lies inside the profile range (FR-003).
- Pairs are never closer than the minimum spacing (FR-004).
- Between any two adjacent instants, no parameter changes faster than the configured rate-of-change limits (FR-006, FR-004a).
- The same inputs always produce the same output, including drift, which is driven by an explicit seed.

### SessionRenderer

Fills a caller-supplied buffer with the next block of interleaved stereo samples. Allocates nothing per call (research R1).

```
render(into: FloatArray, frames: Int, startFrame: Long)
```

The renderer is the single source of audio for both playback and offline verification. A test renders a full night through the same code path that plays it; there is no separate "test synthesizer" that could drift from reality.

### CalibrationSearch

Chooses the next tone to present and decides when there is nothing more to learn.

```
next(state: CalibrationSearchState): CarrierHz?    // null when converged or capped
accept(state, judgment): CalibrationSearchState
result(state): ListenerProfile?
```

Contract rules:

- Terminates within a maximum number of judgments (FR-020).
- All state needed to resume lives in `CalibrationSearchState`, so an interrupted sitting resumes exactly (FR-022).
- An all-rejecting or all-accepting listener yields no result rather than a fabricated narrow range (spec edge case), and the caller offers a retry or a preset.

### ContinuityAnalyzer

Consumes rendered blocks and reports violations. Used by tests, and available for diagnostics.

```
accept(block: FloatArray, frames: Int)
violations(): List<ContinuityViolation>
```

## Outbound ports: what the app supplies

These are interfaces the core declares and the Android layer implements, so the core stays platform-free.

### AudioSink

```
open(sampleRate: Int, channels: Int)
write(buffer: FloatArray, frames: Int): Int      // blocking
close()
underrunCount(): Int
```

Android implements this over `AudioTrack`. Tests implement it as an in-memory or null sink, which is how a ten-hour session is exercised without a device.

### StateStore

```
read(): AppState
write(state: AppState)
```

Android implements it over DataStore. The core never learns where the bytes go. Rule from research R2: a read that fails or finds an unknown schema version returns defaults and MUST NOT destroy the stored file.

### Clock

```
now(): Instant
localTimeNow(): LocalTime
```

Injected so that clock-time end behavior, daylight saving, and long-session tests are all testable without waiting.

## Invariants the contract guarantees

1. No audio is produced outside the profile range, ever.
2. Every session begins and ends with a fade; the renderer has no path that emits a non-zero first or last sample (FR-005).
3. Identical inputs, including the seed, produce byte-identical output.
4. A layer added later plugs into the mixer without changing any signature above (FR-014).
