# Audio Frontier Log

The audio analogue of [trace-frontier-log.md](trace-frontier-log.md): a
chronological record of sound-driver oracle runs — engine driver output
compared per driver invocation against a reference captured from BizHawk/GPGX
running the real ROM driver. Newest entry first.

Each entry records:

- **Worktree / branch / commit** the comparison ran in.
- **Fixture**: the committed reference capture (game, passage, checksum
  identity file).
- **Command**: the exact comparator invocation.
- **Result**: pass/fail with the **first divergence** (tick, role, field or
  event index, expected/actual). The oracle comparators are
  validation-first and no-realignment: they stop at the first difference, so
  the error count of a red run is reported as "first divergence only" unless
  a sweep mode says otherwise.
- What moved since the previous entry for that fixture, if anything.

An entry is expected to be red while a driver gap is open; the log exists to
pin *where* the divergence starts so a fix can be measured against it, and to
detect regressions of passages that have gone green.

## 2026-08-30 - S3K oracle first frontier: boot silence burst (tick 3)

- Worktree `.worktrees/sdre-oracle-s3k`, branch `feature/ai-sdre-oracle-s3k`
  (fixture, capture tooling and comparator land in the same commit as this
  entry; engine base `f087b8947` + sdre-gaps/spec-s3k docs merges).
- Fixture: `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (identity in `s3k-aiz1-intro-metadata-v1.json`): 5,400 driver invocations
  (movie frames 0-5399 of the committed `s3k-complete-sonic-tails.bk2` from
  power-on), 725,898 decoded YM/PSG writes; covers boot, SEGA chant, title
  music (`25h`), Knuckles intro theme (`1Fh`), AIZ1 music (`01h`, ~54 s) and
  ten-plus distinct gameplay SFX. Captured deterministically (two runs,
  byte-identical) by `tools/audio/run_s3k_audio_oracle_reference.sh` with the
  lock-verified patch-0001 observer core
  (`e65315743a6a1228…`, `artifact-lock.json` identity).
- Command:
  `java -cp "target/classes:$(cat target/s3k-oracle.classpath)"
  com.openggf.tools.audio.parity.s3k.S3kAudioParityTool compare
  --reference src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz
  --rom <locked-on s3k.gen>`
- Result: **red**, as expected for the first run.
  First divergence: **tick 3, `EVENT_MISSING`, event 0** — the reference
  emits the Z80 boot's `zStopAllSound` silence burst (first decoded write:
  PSG `9Fh`) three frames after power-on
  (`zInitAudioDriver` → `zStopAllSound`, skdisasm
  `Sound/Z80 Sound Driver.asm`), while the engine's driver emits nothing
  until the first game request: the engine has no boot-time driver
  initialisation burst. Error count: first divergence only (comparator
  stops); ticks 0-2 of the same run are green (`MATCH (3 ticks)` with
  `--ticks 3`).
- Broken on purpose before trusting the comparison (project rule): a
  corrupted `zCurrentTempo` byte in a temp copy (terminal digest recomputed)
  reports `GLOBAL_STATE_MISMATCH` at its exact tick with expected/actual
  (`64` vs `0`, exit 3); the same corruption without the digest fix is
  refused as `terminal body digest mismatch` (exit 4); a corrupted engine
  write is reported at its tick/event index by
  `TestS3kAudioParityComparator.corruptedWriteIsReportedAtItsEventIndex`.
- Unmodelled requests this run (logged by the capture host, not silently
  skipped): `E1h` fade-out (7 ticks), `FFh` SEGA chant (1 tick).
