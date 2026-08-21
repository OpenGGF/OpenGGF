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

## Roadmap traceability

| Phase | Primary automated evidence | State |
|---|---|---|
| 0 — small accuracy harness | `TestChipWriteObserver`, `TestYm2612ChipGpgxParity`, `TestPsgChipGpgxParity`, `TestRomAudioIntegration`, and the pinned S1 BizHawk parity tool | Objective checks pass; [human checklist](2026-08-21-smps-playback-listening-checklist.md) pending |
| 1 — scheduler and tempo | `TestSmpsSequencerDriverCadence`, `TestS3kPalDriverCadence`, `TestSmpsSequencerTempoMath`, and `TestAudioDiagnosticObservers` | Automated acceptance covered |
| 2 — admission and takeover | `TestSmpsGlobalSfxPriority`, `TestPreparedSfxAdmission`, `TestS1SfxTakeoverOrder`, `TestSmpsDriverServiceOrder`, and `TestSmpsCompositeVoice` | Automated acceptance covered |
| 3 — pause/fade/1-up | `TestSmpsPauseProtocol`, `TestSmpsSequencerFadeTiming`, `TestMusicOverrideRestore`, `TestAudioVoiceRegistry`, and `TestUnifiedAudioPresentationIntegration` | Automated acceptance covered; subjective transitions pending |
| 4 — bytecode/envelopes | `TestSonic3kModEnvelopeRetailBugs`, `TestSonic3kCoordFlagParity`, `TestSonic2SpindashRevRequest`, `TestSmpsSequencer`, and the ROM catalog tests | Automated acceptance covered |
| 5 — chip/DAC/PCM/region | `TestRegionalChipClocks`, `TestAudioHardwareDefaults`, `TestYm2612ChipGpgxParity`, `TestSegaPcmCommandRouting`, `TestAudioPresentationSourceParity`, and `TestRomAudioIntegration` | Automated acceptance covered |
| 6 — ROM content | the S1/S2/S3K catalog-resolution and loader-framing suites | Automated acceptance covered on all three pinned ROMs |

Phase 7 is deliberately not an acceptance item. It is cleanup after audible
parity and remains deferred.

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

### Fresh completion audit

On commit `683b1a3984958b4b6ae53baf383a81bee6727078`, JDK 21.0.11 and the three
pinned ROMs produced these additional results:

- the exact 43 runnable classes in the changed 44-path test manifest ran 414
  tests with 0 failures, 0 errors, and 0 skips;
- the pinned `s1-soundtest-ghz.bk2` comparison matched all 14,690 ordered
  driver ticks against BizHawk 2.11 / Genesis Plus GX;
- the two BizHawk captures were byte-identical at SHA-256
  `5941958c4eb38da4f71e1e5860b49b2d13d6fa0aaedcf244fa7b8d4ecb5d6efc`;
  the two independent OpenGGF captures were byte-identical at SHA-256
  `06f2fda57779b6e1ec53078bc3040ff49135ff89ddb37bb325ef5d4f5e65187a`.

The external comparison covers S1 GHZ's normal music path. It is intentionally
not presented as evidence for SFX contention, pause, fade, speed, or 1-up; those
remain explicit human checklist rows.

### Clean full-suite comparison

Both sides started from empty `target/` directories, used Maven's serial
`-Pci` profile, JDK 21.0.11, and the same three pinned ROMs. The baseline is
the exact pre-playback commit `7039887187948f86089e39a9f0fff0f17b26bdab`;
the candidate is `683b1a3984958b4b6ae53baf383a81bee6727078`.

| Tree | Surefire summary | Unique failing/error methods |
|---|---:|---:|
| pre-playback baseline | 15,171 tests; 54 failures; 69 errors; 18 skips | 123 |
| playback candidate | 15,251 tests; 52 failures; 64 errors; 18 skips | 116 |

The exact method-set comparison has no failure or error absent from the clean
baseline and removes seven baseline-red special-stage presentation/rewind
methods. The full suite remains red from pre-existing failures, but the
candidate introduces no new failing/error method, no baseline failure worsens,
and no changed or audio-focused test is red.

## Limits and follow-up

The validation is based on disassembly-owned state transitions, ordered chip
writes, deterministic PCM/register fixtures, real-ROM catalog traversal, and
integration playback. It does not claim a subjective listening test was
performed by the automation. Human A/B approval is tracked explicitly in the
[listening checklist](2026-08-21-smps-playback-listening-checklist.md); it must
not reopen complete-game semantic tracing or broad backend cleanup without a
named audible mismatch.

Phase 7 cleanup remains deferred. The rejected semantic-observer worktree and
its protected stashes are not part of this delivery.
