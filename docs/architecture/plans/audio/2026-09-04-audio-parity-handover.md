# Audio parity programme: handover (2026-09-04)

Debrief and handover for whoever picks up the SMPS audio parity work after the
2026-09-03/04 lead session. Everything below is on `develop` unless a branch is
named. Numbers are stamped with the commit they were measured on; re-measure
before relying on any of them (see the measurement-hazard table in
`docs/agent-workflow/briefing-trace-rounds.md`).

## Where develop stands

- `develop` head at handover: `a306c0351` (S3K branch `bugfix/ai-s3k-oracle-freq-resend`
  fully merged at `ebc90caa3`; no lane is running). Before it `37c3f0c0b` (the two
  audited P1 fade regressions fixed; before it `5dd1b8122`: S3K 1-up fade-in body on the live path,
  driver-owned fade machine, four more gated oracle fields, intro oracle 760 →
  1490, then 1569 on the branch).
- Ordinary suite on `5dd1b8122`: 16,476 tests, 0 failures, 22 skips (three ROM
  paths). Guards: 609 (incl. the snapshot-copy guard), 0 failures. CI on develop was green at `725d4cfd5`.
- Trace sweep (`-Ptrace-replay`, three ROM paths) on `5dd1b8122`: 854 tests,
  7 failures, 6 skips; the seven are S1/S2/S3K complete-run chains,
  S2 EHZ halfpipe, `TestS3kAizTraceReplay`, `TestS3kReplayReferenceClosureIntegration`,
  `TestTraceRunReplayWalkerControlFlow`. CPZ2 segment 10 went green this session.

## The evidence base (what "green" means here)

Driver oracles compare, per driver service, a driver-RAM snapshot plus the
ordered YM/PSG/DAC write stream against a reference captured from the emulator
(TraceChaser observer core, ABI 5, `SNAPSHOT_AT_PC`). They are comparison-only;
recorded requests are the only stimulus. Every currently matching oracle is a
hard assertion on `Kind.MATCH` plus its literal tick count, so a regression
fails the build. Read the `MEASUREMENT_ONLY` lines by content; a green count is
not evidence (see `TestS2WidenedRequestOracle` history in the frontier log).

| Oracle | State at handover |
|---|---|
| S1 sound test music / SFX | MATCH 14,690 / 1,967 (JUnit since `373b4376c`) |
| S1 gameplay GHZ1, two recordings | MATCH 2,562 / 5,257 |
| S1 per-song windows (complete-run movie) | 20 windows, all 15 songs; 8 MATCH across 6 songs, 12 red with pinned frontiers |
| S2 driver v1 | MATCH 698 |
| S2 driver-state v2 EHZ w10150-12400 | MATCH 2,198 state and writes; DAC stream: one supersession join, excused |
| S2 request windows | MATCH per site (25+2, 52+0, 27+2 SFX+music), payload v4 |
| S2 CPZ w2700-3450 (second recording) | MATCH 720 state / 719 writes |
| S2 1-up window w20107-23600 | restore service pinned; whole window red (state tick 2880, writes tick 0) |
| S3K AIZ1 intro | service 1,569 of 5,263 (write difference: reference PSG FFh vs engine FM1 frequency, at an SFX taking PSG3); DAC stream red at run 338 byte 0 (88h vs 7Fh). Branch merged; resume from develop |

Per-effect and runtime-path tests (S3K): `TestS3kSfxLifecycleRom`,
`TestS3kSfxNoiseTailWriteStream`, `TestS3kSfxRuntimePathWithMusic`,
`TestS3kNoiseFormEffectWriteStream`, `TestSonic3kTitleScreenIntroSkipAudio`,
`TestS3kOneUpRestoreRom`, plus `TestSmpsSequencerConfigCopyCoverageGuard`
(every sequencer config field must survive the presentation copy; it found
five silently dropped settings on its first run).

## User-reported bugs and their status

| Report | Status | Where |
|---|---|---|
| S3K title theme dead after a late intro skip | fixed | `35d11ab69` |
| S3K spindash release wrong | fixed (noise-channel lock ownership, fabricated pitch multiplier removed) | `3947ba305` |
| S3K collapsing bridge cut early / no tail | fixed (same, plus per-pass PSG volume tail, config copier) | `3947ba305` |
| S3K quick shield silent | passes the in-play sequence test with the SFX-takeover write fix; not proven red-before | `3947ba305` |
| S3K abrupt music changes | drowning-restore substitution and S3K fade rate fixed | `fb091dfc3` |
| S2 music after 1-up ("funky remix") | fixed (tracks rest on hand-back) | `633ee400f` |
| S3K 1-up: exception in batch, no fade-in / wrong instruments, then PSG lost after the fade and fade state dropped by rewind | fixed in three steps | `725d4cfd5`, `5dd1b8122`, `37c3f0c0b` |
| S3K Knuckles held note | not reproduced by any test | open |

## Open items, in priority order

1. (closed at `37c3f0c0b`) Audit P1: PSG tracks stayed overridden and were
   attenuated after the S3K 1-up fade-in; now released at completion with
   volumes untouched, per `zDoMusicFadeIn`.
2. (closed at `37c3f0c0b`) Audit P1: the session snapshot copy dropped the
   driver fade counters; fixed, with a record-component survival guard.
3. S3K intro oracle from 1,569: resume from develop; the frontier log's top entry has the resume command and the six gated fields.
4. S1 red windows: five share one cause (an SFX from the previous window still
   holds a track at the epoch). Fix designed: replay from the predecessor
   window's epoch with its recorded requests and compare from the target epoch.
   Two are the act-clear double driver pass (ruled not admissible under rule 4;
   stays open). One is the 1-up restore. Four single frontiers.
5. S1 second movie (`sonic1-complete-withemeralds.bk2`): surveyed (101 windows,
   plan in the fixture manifest), uncaptured. ~45 min per capture pass, two
   passes per movie.
6. S2 `TestS1OverrideResumeAudioOracle` is inverted (asserts its reference is
   missing). Fix requires the 1-up windows `$88`/`$84` from the S1 whole-run
   probe; notes on branch `feature/ai-validate-next-audio-fixes`.
7. Driver-level 1-up backup of tempo, tempo speed-up, voice pointer and bank has
   no engine equivalent (presentation override stack preserves the sequencer).
8. S2 public request observer double-fires the ring at row 10960 (coalesced
   downstream; no parity failure today).
9. S3K second recordings (Tails/Knuckles) and widening past the AIZ1 intro; the
   insta-shield never fires in any committed S3K movie, so its oracle coverage
   needs a new recording.
10. Listening validation of the release build against the 08-27 checklist.

## Unmerged branches with handovers

- `bugfix/ai-s2-runchain-art-gaps` (two ROM-cited commits; closes the S2
  36–40-row art-gap family; the uniform 1 needs a level-load scheduling change:
  player creation has two owners in the engine, one in the ROM; five failed
  arms recorded).
- `bugfix/ai-boss-constructor-spawn-order` (research only; four ROM-correct
  spawn-order changes reverted because they destabilise rewind identity for
  after-current allocations; resumption plan in the trace frontier log).
- `feature/ai-validate-next-audio-fixes` (audit: both `next` audio fixes are
  already on develop; see `docs/architecture/audits/2026-09-04-next-audio-fixes-validation.md`).

## Rules that were learned the hard way this session

- Merge to develop only at a milestone (an oracle going green); lanes stay on
  their branch otherwise.
- Never `2>/dev/null` a commit in a chain; gate pushes on a clean tree.
- Never build or merge inside a lane's worktree; use `.worktrees/lead-verify`.
- `pgrep -f`/`pkill -f` match their own shell; use exit-marker files and PIDs.
- Two Maven runs in one worktree produce phantom reds (shared `target/`).
- Report the walk-failure axis name before any error count; a truncated chain
  reports fewer errors.
- A comparison that has never failed and one that never runs look identical:
  break each new gate on purpose once.
- The 08-27 revert (`b4c8fbd8a`) is why the S3K SFX "regressed"; re-derive from
  the ROM with oracle evidence, never re-apply those diffs verbatim.
- BizHawk exits 0 on Lua errors; treat a silent capture as failed until its
  output exists.

## Commands

```bash
# audio parity + audio packages, three ROM paths, S2 request movie
LUA_BIN=lua5.4 mvn -Dmse=off -B "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PWD/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PWD/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Ds2.request.bk2.path=$PWD/docs/BizHawk-2.11-linux-x64/Movies/sonic-2-sonic-tails-complete-emeralds.bk2" \
  "-Dtest=com/openggf/tools/audio/parity/**/*,com/openggf/audio/**/*" test
mvn -Dmse=off -B -Pguards test
LUA_BIN=lua5.4 mvn -Dmse=off -B -Ptrace-replay "-Dsonic1.rom.path=..." "-Dsonic2.rom.path=..." "-Ds3k.rom.path=..." test
```

Observer core matching the TraceChaser artifact lock (patch `2e1d1e59…`):
`<agent-scratch>/s2-widen/core-build/out/` (the s2-widen lane's build; its
`identity.json` matches the lock), installed beside a stock BizHawk at
`<agent-scratch>/s2-widen/bizhawk-s2req`. TraceChaser main is at `4fb6d08` (music-mailbox
observer site, payload v4).
