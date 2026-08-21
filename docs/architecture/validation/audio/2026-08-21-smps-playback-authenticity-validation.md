# SMPS Playback Authenticity Validation

## Decision surface

This report evaluates the bounded playback work described by the
[SMPS playback authenticity roadmap](../../plans/audio/2026-08-21-smps-playback-authenticity-roadmap.md).
It does not treat complete-run semantic evidence, observer ABI expansion, or
architecture cleanup as delivery requirements. The question is whether the
engine sends more authentic timing, ownership, and sample data to its YM2612,
PSG, and DAC paths without introducing an attributable regression.

## Source-backed improvements

The implementation follows the shipped `FixBugs = 0`, `FixDriverBugs = 0`,
and `fix_sndbugs = 0` paths and adds focused tests for:

- S1, S2, and locked-on S3K tempo, duration, PAL, and speed-up service cadence;
- S1/S2 global SFX priority and S3K's priority-free per-channel admission;
- same-service takeover/release, ordinary BGM replacement, pause, fade, and
  1-up save/restore behavior;
- S2 spindash request/transposition bugs and S3K modulation-envelope bugs;
- region-correct chip clocks, reference PSG/DAC defaults, S2 DPCM cadence,
  and S3K's exclusive StopAll SEGA PCM path through the YM2612 DAC;
- exact retail song/SFX/envelope/DAC catalog framing for all three verified
  ROMs, with malformed or unreadable tables failing closed.

Game differences are represented by typed profile/configuration policy. Shared
runtime code contains no game-name or zone carve-out. Runtime content remains
ROM-owned.

## Verification

All commands used Maven on JDK 21.0.11 with the exact S1 REV01, S2 REV01, and
locked-on S3K ROM properties.

### Focused playback gates

- Final merged changed-test selection: 44 classes, 567 tests, 0 failures,
  0 errors, 0 skips.
- Broad audio regression selection: 80 classes, 687 tests, 0 failures,
  0 errors, 1 existing skip.
- Newly added audio tests followed by the affected GameLoop classes: 101
  tests, 0 failures, 0 errors.
- The exact upstream trace-session failure sequence followed by the hardened
  rewind boundary tests leaves the upstream class red, while both downstream
  classes pass 12/12. This proves their cleanup is test isolation rather than
  a runtime workaround.

### Clean full-suite comparison

Both sides started from empty `target/` directories and the same upstream
`develop` baseline.

| Tree | Surefire summary | Unique failing/error methods |
|---|---:|---:|
| `develop` | 15,194 tests; 52 failures; 67 errors; 18 skips | 119 |
| final merged playback tree | 15,274 tests; 52 failures; 64 errors; 18 skips | 116 |

The playback branch removes three real-provider special-stage errors by making
that test's Sonic 2 ROM dependency explicit. The final method-set comparison
has no failure or error absent from the clean baseline. The full suite remains
red from its pre-existing failures, but no baseline failure worsened and no
changed or audio-focused test is red.

## Limits and follow-up

The validation is based on disassembly-owned state transitions, ordered chip
writes, deterministic PCM/register fixtures, real-ROM catalog traversal, and
integration playback. It does not claim a subjective listening test was
performed by the automation. A human A/B remains useful release polish, but it
must not reopen complete-game semantic tracing or broad backend cleanup without
a named audible mismatch.

Phase 7 cleanup remains deferred. The rejected semantic-observer worktree and
its protected stashes are not part of this delivery.
