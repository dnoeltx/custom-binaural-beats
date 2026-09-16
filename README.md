# Custom Binaural Beats

**Status: in development. Phase 1 of 7. No release yet, and nothing here plays sound so far.**

An Android app that generates binaural beat audio for sleep, tuned to the individual
listener rather than served from a fixed library.

## The problem it comes from

I sleep to binaural beat tracks from YouTube. They help me, and I make no claim beyond
that: the published evidence for binaural beats is mixed, and this app will never tell
you what it does to your brain. The tracks have practical problems, though, and all of
them are the same problem:

- they run about four hours, so they end in the middle of the night
- some sit at a pitch that is not relaxing to me, and nothing lets me change it
- a tone hums along and then just stops
- rain arrives five minutes in, or a bird appears, or a deep boom

Every one of those is a **surprise**. Something asks for attention, which is the exact
opposite of what a sleep sound is for.

So the app does two things. It finds the pitch range a particular listener finds
relaxing, by playing tones and asking for a yes or no on each one while they lie in the
dark. Then it generates an all-night session inside that range, with nothing in it that
was not chosen and no moment where anything changes abruptly.

## Why this repository looks the way it does

This is the third in a series of Android apps built with Claude Code, each one
deliberately escalating the *development process*:

1. [ChecklistV1](https://github.com/dnoeltx/ChecklistV1): built ad hoc. CI, tests, a real
   Room migration that preserved user data across an upgrade.
2. [historical-marker-alerts](https://github.com/dnoeltx/historical-marker-alerts):
   planned up front, offline-first, with a data pipeline and an architecture-arguing
   README.
3. **This one**: specification-driven, using [GitHub Spec Kit](https://github.com/github/spec-kit).

The process is part of the deliverable. Before any code existed, this repository had:

- a **[constitution](.specify/memory/constitution.md)** of eight ratified principles,
  including "no surprises", test-first everywhere, and honesty about effects
- a **[specification](specs/001-calibrated-sleep-sessions/spec.md)**: 33 functional
  requirements and 8 measurable success criteria, with four ambiguities resolved by
  explicit question and answer rather than by assumption
- a **[plan and research](specs/001-calibrated-sleep-sessions/plan.md)** where every
  technology choice cites a primary source, and nine numeric values are deliberately
  left unset because they are to be *measured*, not invented
- **[87 tasks](specs/001-calibrated-sleep-sessions/tasks.md)** in dependency order

### What that process has caught so far

Worth recording, because a process is only worth the defects it finds:

- **The task list violated the constitution.** The cross-artifact analysis found an
  implementation ordered before its own test, in a repository whose first principle
  ratified test-first as non-negotiable. Fixed before any code existed.
- **A requirement had no task at all.** "Only one session may run at a time" appeared in
  the spec and in nothing downstream.
- **A documentation page reported a stale release date** for a GitHub Action, off by two
  years from what the API says. Every version in this repo comes from Maven metadata or
  a releases API, never from a summary and never from memory.
- **CI caught a Windows-to-Linux defect on its first run.** `gradlew` was committed
  without its executable bit, which is invisible on Windows and fatal on an Ubuntu
  runner.

## Design decisions worth knowing

- **`:core` is a pure Kotlin module with no Android dependency**, and a Gradle check
  fails the build if that ever stops being true. The logic that matters (synthesis,
  calibration, scheduling) is therefore testable on the JVM in seconds, and an iOS port
  later reuses it rather than rewriting it.
- **A full night can be rendered offline, faster than real time, through the same code
  that plays it.** That is how "no abrupt changes" becomes a test a machine runs on
  every commit instead of a promise.
- **Everything stays on the device.** No accounts, no analytics, no server, and no
  network permission at all. The only way data leaves is the platform's own backup to
  your account.

## Requirements

- Android 8.0 (API 26) or newer
- **Stereo headphones or earbuds.** A binaural beat needs a separate tone in each ear,
  so it does not exist on a phone speaker. Verified on the sleep earbuds this is being
  built for before a line of code was written.

## Building

```
./gradlew :core:check          # JVM tests plus the no-Android-dependency check
./gradlew :app:assembleDebug
```

Android Studio generates `local.properties` with your SDK path on first open; it is not
committed.
