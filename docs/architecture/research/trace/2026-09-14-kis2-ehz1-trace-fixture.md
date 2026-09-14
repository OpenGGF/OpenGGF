# Knuckles in Sonic 2 EHZ1 trace fixture (2026-09-14)

First measurable fixture for the KiS2 patch: `src/test/resources/traces/kis2/ehz1/`
with `TestKis2Ehz1TraceReplay`. Frontier and commands are in
[the trace frontier log](../../../status/trace-frontier-log.md) (entry dated 2026-09-14).

## Recording path

- ROM: the user-supplied lock-on dump (3,407,872 bytes, S&K at $000000, Sonic 2
  World REV01 at $200000, the 256 KiB chip at $300000; BizHawk gamedb
  "Sonic and Knuckles & Sonic 2 (W) [!]"). Genesis Plus GX boots it as the
  lock-on cart without asking for separate firmware; the chip's patched Sonic 2
  program keeps the Sonic 2 RAM map, so TraceChaser's S2 recorder applies unchanged.
- TraceChaser identifies trace ROMs by SHA-1 and had no entry for this image.
  Submodule commit `9fd957b` on local branch `feature/ai-kis2-lock-on-identity`
  (patch in the task directory, not pushed, superproject pointer left at
  `4fb6d0802`) adds the identity, routes it to plain S2 trace mode, writes
  `main_character` knuckles, the file SHA-1 as `rom_checksum`, and a notes line
  naming logical ROM KIS2. No native load audit is armed: the S2 REV01
  dynamic-art profile validates opcode windows at Sonic 2 cart offsets, and the
  running KiS2 code lives in the chip window. A KiS2 dynamic-art profile needs
  the chip listing and is future work.
- Title timing (measured with Start-tap probes): a Start press skips the KiS2
  intro only from about frame 480; a second press starts the game; controls
  unlock 174 frames later, so a press at 480 and one at 540 arm the recorder at
  BK2 row 714.
- Movie authoring: `InputLogAuthorTool` script `kis2-ehz1.script` (committed
  beside the fixture) → `tools/traces/assemble_bk2_from_input_log.py` (re-keys
  to BizHawk's pad order and adds `SyncSettings.json` from `s2-ehz1.bk2`).
  Re-running both reproduces the committed movie and a byte-identical capture.
- Terrain for route design came from `LevelSolidityMapProbe` (opt-in test-scope
  diagnostic) rather than guesswork; the probe captures listed in the frontier
  entry settled what the map could not (one-way platforms, object springs).

## Replay bootstrap finding

`AbstractTraceReplayTest` resolved the recorded team to the `kis2` module, then
`SharedLevel.load` re-detected the root Sonic 2 module from the ROM and reopened
the session on it, so the first replay compared Sonic against a Knuckles trace.
The base test now resolves the recorded team again after the shared-level load
(a no-op for stock recordings), and the KiS2 test asserts the patch and sprite in
`afterFixtureBuild`.

## Validation

Focused: `TestKis2Ehz1TraceReplay` (red, frontier recorded),
`TestS2Ehz1TraceReplay` (red before and after with the identical first error),
and the trace fixture guards `TestTraceFixtureCompressionGuard`,
`TestTraceFixtureMovieAlignmentGuard`, `TestTraceFixtureLagPolledInputGuard`,
`TraceFilesTest`, `TestTraceDataParsing`, `TestBuildToolingGuard` (all green with
the new `kis2` fixture directory). TraceChaser: `test.sh --no-gates` passes the
new identity and metadata tests; the failures in that run are environment
(missing native audio-observer artefacts, `TRACECHASER_TEST_FIXTURE_ROOT`
unset, the pre-existing special-stage runner test, reproduced on a pristine
checkout). No `src/main` change; no broad run.
