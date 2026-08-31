# Audio frontier log

The audio counterpart of [trace-frontier-log.md](trace-frontier-log.md): the
running record of driver-oracle comparisons between the engine's SMPS driver
and reference captures recorded from the real driver running in the pinned
emulator (BizHawk 2.11 / Genesis Plus GX through TraceChaser).

Each entry records, newest first:

- **Date / commit / worktree** the comparison ran at.
- **Fixture** — the committed reference capture (and its BK2 movie) compared
  against, by file name under `src/test/resources/audio/parity/`.
- **Command** — the exact re-runnable invocation.
- **Result** — `MATCH` or the comparator's first divergence: tick ordinal,
  role/field (or event index), reference vs engine value. The comparator is
  validation-first and no-realignment, so one entry has exactly one first
  divergence; there is no error count beyond it. A capture failure is recorded
  as such, never as a parity result.
- **Notes** — what moved, or what the divergence is suspected to be. Fixing
  driver behaviour belongs to implementation lanes; this log only measures.

Comparisons at this tier are per driver invocation: driver-RAM-shaped track
state plus the ordered YM/PSG write stream of that invocation ("ticks"), as
defined by `com.openggf.tools.audio.parity`.

---

<!-- entries are prepended below, newest first -->

## 2026-08-31 — S3K driver projection advances from the 68k bootstrap to Z80 boot at tick 13

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the projection fix lands with this entry).
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged committed reference; its CPU-tagged full-bus rows and terminal
  digest remain intact).
- **Command:** the entry-of-record `S3kAudioParityTool compare` invocation
  below with the absolute SHA-1-verified locked-on ROM; `--ticks 13` was run
  separately as the green-prefix gate.
- **Result:** ticks **0-12 MATCH**. First divergence tick **13**,
  `EVENT_MISSING`, event 0: reference Z80 YM part II `82h = FFh`, engine
  missing (first-divergence-only comparator).
- **Notes:** the reader now validates each captured write's observer
  `source_cpu` and projects only CPU 1 (Z80) into this driver oracle. CPU 2
  (68k) writes, including tick 3's `PSGInitValues` `9F BF DF FF`, remain in
  the digest-authenticated fixture but are outside comparison. Tick 13 is the
  genuine `zInitAudioDriver -> zStopAllSound` burst (S3K spec §1 boot and §5,
  `D:523-551,2460-2521`); it spans movie frames 13-14 before ordinary `zVInt`
  service. The current frame-shaped engine host has no source-owned driver
  installation/boot-service boundary. Emitting it at a fixture frame or
  triggering it from comparison writes would violate the no-trace-hydration
  rule, so this frontier requires a service-shaped oracle/host boundary rather
  than a production sequencer patch.

## 2026-08-31 — S2 EHZ music prefix reaches the first SFX override at tick 210

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the fixes land with this entry).
- **Fixture:** `src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz`
  (unchanged committed reference).
- **Command:** `S2AudioOracleTool --fixture <committed fixture> --rom
  <absolute SHA-1-verified S2 REV01 ROM>` on the compiled worktree classes.
- **Result:** DIVERGENCE at tick **210** (movie row **10412**), `writes[0]`:
  reference PSG `0x9A`, engine YM part II `A4h = 33h`; **303 of 698 ticks
  divergent**. Ticks 0-209 match.
- **Notes:** the comparator now pairs each tick with the kind-9 service
  **completion** frame, not its begin frame; update 0 begins at row 10201 and
  completes at row 10202, so the old FM2 `1424h/1428h` frontier was a
  mid-track-walk snapshot. Tick writes are likewise kind-9-owned only rather
  than mixing the parent V-int's multi-frame load burst into its child update.
  The engine fixes exposed along the prefix are source-owned S2 semantics:
  resting PSG envelopes advance without writing, FM note preparation does not
  repeat pan, `zSetChanVol` rewrites all four TLs, E7 persists on DAC while
  FM/PSG clear it at expiry, FM no-attack still keys on, and note-start
  modulation follows key-on without forcing a write. At tick 210 the
  reference FM4 has override bit 2 set by an SFX and suppresses its modulation
  write (`sd:1088-1092`); this music-only engine capture deliberately injects
  no SFX. The next S2 frontier is therefore the declared SFX/admission tier.

## 2026-08-31 — S3K tick-3 attribution retracted: this is the 68k PSG bootstrap, not `zStopAllSound`

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` at `a390f1649` plus this documentation
  correction.
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged committed reference).
- **Command:** the entry-of-record `S3kAudioParityTool compare` invocation
  below, plus direct inspection of the fixture's first non-empty write rows
  against `skdisasm/sonic3k.asm:175-184,260` and
  `Sound/Z80 Sound Driver.asm` `zInitAudioDriver` / `zStopAllSound`.
- **Result:** the comparator remains red at tick 3, event 0, PSG `0x9F`
  missing. **No production fix is valid at that frontier.** The reference row
  contains exactly `0x9F,0xBF,0xDF,0xFF`, matching the 68k power-on
  `PSGInitValues` loop before the SMPS driver is installed. The actual Z80
  initialization burst first appears at tick 13 and continues at tick 14 with
  the source-specified FM silence/SSG-EG/PSG/DAC/FM3 sequence.
- **Notes:** the 2026-08-30 entry's claim that tick 3 was
  `zInitAudioDriver -> zStopAllSound` is retracted. `S3kOpenGgfAudioCapture`
  is a driver/request host and has no 68k power-on execution boundary; adding
  the four writes on oracle tick 3 would key engine behavior to a fixture
  frame, while emitting them from `SmpsDriver` startup would assign 68k-owned
  work to the wrong subsystem. This is a host-capture scope gap. The committed
  fixture and comparator remain unchanged, so a later oracle revision must
  establish a source-owned 68k bootstrap boundary before it can expose the
  first driver-owned divergence.

## 2026-08-31 — S1 SFX frontier advances from admission tick 351 to release tick 377

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the fix lands in the same commit; base
  `fcc190d5f`).
- **Fixture:** `src/test/resources/audio/parity/s1/s1-soundtest-sfx-reference.v1.jsonl.gz`
  (unchanged committed reference).
- **Command:** `S1AudioParityTool capture --capture sfx` followed by `compare`
  against the committed fixture in `<external run root>/sdre2-s1-sfx`, with
  the absolute SHA-1-verified S1 REV01 ROM path.
- **Result:** **MISMATCH**, first divergence tick **377**, event 3: the engine
  emits an extra PSG `0x9F` after the reference's final event. Tick 351's prior
  `event_extra` is green. The S1 GHZ music gate remains **MATCH (14,690
  ticks)** in a fresh committed-reference engine capture.
- **Notes:** `Sound_PlaySFX` (`SD:977-1087`) has no PSG1/2 takeover write; the
  typed S1 PSG takeover profile now leaves the first visible write to the SFX
  track while legacy profiles retain the existing synthetic silence. The new
  tick-377 frontier is release-shaped: after both streams write PSG `0xB3`,
  `0xF7`, `0x9F`, the engine writes a second `0x9F` and begins immediate music
  restoration, while the reference stops. That belongs to the profile-shaped
  stop/restore gap (§6.2), not admission.

## 2026-08-31 — Cadence 2–4 land without moving the three live oracle frontiers

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the fix lands in the same commit; base
  `f07e45c44`).
- **Fixtures:** the unchanged committed S1 GHZ, S2 EHZ reload-window, and S3K
  AIZ1 intro references named in their entries below.
- **Commands:** S1 engine capture and comparison against the committed fixture
  through `S1AudioParityTool capture` / `compare` in
  `<external run root>/sdre2-s1-committed`; the entry-of-record
  S2 and S3K Java invocations below with absolute, SHA-1-verified ROM paths.
- **Results:** S1 music **MATCH (14,690 ticks)**. S2 remains at tick 0,
  `track.FM2.dataPointer`, expected `0x1424`, actual `0x1428`, **669 of 698
  ticks divergent**. S3K remains at tick 3, `EVENT_MISSING`, event 0, reference
  PSG `0x9F`, engine missing.
- **Notes:** live presentation is now outer-frame locked; S2 PAL uses the
  driver-global 6-per-5 music cadence while SFX stays single-service; S3K PAL
  repeats the complete driver pass 7-per-6 and the shared speed tail produces
  the cited 5-per-4 vector. These branches are absent from the three current
  oracle windows, so unchanged frontiers are expected; the cadence vectors are
  pinned by `TestSmpsSequencerCadence`. A fresh BizHawk S1 reference recapture
  was attempted through `run_s1_audio_parity.sh` but the local host had no X
  display; that capture failure is not reported as a parity result. The
  committed-reference engine comparison above is the recorded gate.

## 2026-08-31 — S2 frontier: tick-0 `tempoTimeout` green after the delay-frame cadence fix

- **Worktree/branch:** `.worktrees/sdre2-cadence`, `feature/ai-sdre2-cadence`
  (the fix lands in the same commit as this entry; base
  `feature/ai-sound-driver-re` `fc3e70c95`).
- **Fixture:** `src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz`
  (unchanged committed reference).
- **Command:** the entry-of-record S2 invocation (`S2AudioOracleTool --fixture
  <committed fixture> --rom <s2.gen>` on the compiled worktree classes, as in
  the 2026-08-30 first-measurement entry).
- **Result:** DIVERGENCE — first divergence still tick 0 (movie row 10201),
  now `track.FM2.dataPointer` expected `0x1424` actual `0x1428`;
  **698 → 669 of 698 ticks divergent**. The previous frontier field,
  tick-0 `global.tempoTimeout` (`0x3c` vs `0x0`), is green: the sequencer now
  seeds its accumulator at song load (`sd:1820-1822`) and runs `TempoWait` on
  the first update (`sd:545-551`), and a no-carry frame pre-increments every
  music slot's `DurationTimeout` while the track walk still runs
  (`sd:596-619`, gap analysis §1.2 #2).
- **Notes:** the exposed `dataPointer` divergence is a load/track-walk stream
  position gap (engine 4 bytes ahead on FM2 at the first update), not a
  cadence field — it belongs to a music-load/note-parse lane. Cross-checks at
  the same commit: S1 GHZ music oracle **MATCH (14,690 ticks)** held; S1
  sound-test SFX frontier unchanged (tick 351 `event_extra`); S3K frontier
  unchanged (tick 3 boot-silence `EVENT_MISSING`, unreachable by cadence).

## 2026-08-30 — S1 sound-test SFX oracle first light: red at the first SFX admission

- **Worktree/branch:** `.worktrees/sdre-oracle-s1`, `feature/ai-sdre-oracle-s1` (commit recorded with the fixture landing).
- **Fixture:** `src/test/resources/audio/parity/s1/s1-soundtest-sfx-reference.v1.jsonl.gz`
  (movie `s1-soundtest-sfx.bk2`: the pinned GHZ sound-test prefix, then eight normal SFX
  `$A0 $A4 $A6 $AA $B5 $C6 $CC $CF`, dispatched at tick ordinals 351, 525, 689,
  863, 1072, 1311, 1495 and 1664; 1,967 ticks, epoch at GHZ music acceptance).
  Reference recorded twice on BizHawk 2.11/GPGX (debug and production probe modes)
  with byte-identical emitted captures.
- **Command:**
  `tools/audio/run_s1_audio_parity.sh --mode sfx --output-root <external dir>`
  (or the recorded `S1AudioParityTool capture --capture sfx` + `compare` pair against
  the committed fixture).
- **Result:** **MISMATCH** (exit 3). First divergence: tick **351** — precisely the
  invocation whose recorded `dispatches` is `[0xA0]`, the first SFX (jump) — event
  index 2, kind `event_extra`: the engine emits `psg 0x9F` (PSG1 silence) that the ROM
  does not. Reference events at that tick run `psg 0xB3, psg 0xF6, psg 0x80, psg 0x14, …`;
  the engine inserts a PSG1 attenuation-off silence between index 1 and the ROM's
  frequency latch. There is no error count beyond the first divergence by design.
- **Notes:** the extra write comes from the engine's SFX channel-steal path
  (`SmpsDriver.writePsg` lock acquisition calls `silencePsgChannel` when an SFX takes a
  channel from music), while S1 `Sound_PlaySFX` only marks the music track overridden and
  writes nothing at admission (S1 routine map §6). Matches gap analysis §1.2 #6
  (override/restore burst shape is profile work). State and all 350 earlier ticks
  (music-only, including tick 0's music-load burst) match. Fix belongs to an
  implementation lane, not this oracle lane.

## 2026-08-30 — S1 GHZ music oracle re-established from a committed fixture: MATCH

- **Worktree/branch:** `.worktrees/sdre-oracle-s1`, `feature/ai-sdre-oracle-s1`.
- **Fixture:** `src/test/resources/audio/parity/s1/s1-soundtest-ghz-reference.v1.jsonl.gz`
  (movie `s1-soundtest-ghz.bk2`; 14,690 ticks to proven recurrence, cycle start 5,473,
  period 4,608). Uncompressed SHA-256 `5941958c…` — byte-identical to the 2026-08-09 and
  2026-08-30 audit captures, and to a fresh capture recorded this session with the
  consumer-side domain-fixed probe (`tools/audio/probes/s1_audio_driver_parity_probe.lua`).
- **Command:** `S1AudioParityTool capture` + `compare` against the committed `.gz` fixture
  (external run root; also reachable via `tools/audio/run_s1_audio_parity.sh --mode music
  --output-root <external dir>`).
- **Result:** **`S1 audio parity: MATCH (14690 ticks)`**, exit 0.
- **Break-it-on-purpose (comparator proof it actually compares):** two independent
  corruption experiments were run (this lane's, and the concurrent writer's — see the
  validation record's provenance note); all four outcomes were first divergences with
  exit 3:
  - fixture byte, run A: tick 5000 `tempoTimeout` 3→4 → `global_state_mismatch,
    tick 5000, field tempo_timeout, reference 4, openggf 3`;
  - engine write, run A: tick 3001 event 0 `ym2612 p0 reg 0xA4` 34→35 →
    `event_value_different, tick 3001, event 0`;
  - fixture byte, run B (concurrent writer's, per commit 0c1d0580e; not re-run by
    this lane): tick 5000 DAC `duration` 11→12 → `track_state_mismatch, tick 5000,
    role DAC, field duration`;
  - engine write, run B (same provenance): tick 7000 event 0 `ym2612 p0 reg 0x28`
    1→0 → `event_value_different, tick 7000, event 0`.

## 2026-08-30 — S2 driver oracle: first measurement (expected red)

- **Worktree:** `.worktrees/sdre-oracle-s2`, branch `feature/ai-sdre-oracle-s2`
  (commit recorded in the entry's own commit).
- **Oracle:** `S2AudioOracleComparator` against
  `src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz`
  (movie rows 10150-10899 of the pinned S2 complete-emeralds movie; EHZ music
  reload anchor at row 10195; recorded by the TraceChaser headless harness
  with the patch-0001 GPGX audio observer — see the fixture's metadata JSON
  and `docs/architecture/research/audio/2026-08-30-s2-driver-oracle.md`).
- **Command:**

  ```bash
  mvn -q -Dmse=off compile dependency:build-classpath -Dmdep.outputFile=target/oracle-classpath.txt
  java -cp "target/classes:$(cat target/oracle-classpath.txt)" \
    com.openggf.tools.audio.parity.s2.S2AudioOracleTool \
    --fixture "$PWD/src/test/resources/audio/parity/s2/s2-ehz-reload-w10150-10900.raw.jsonl.gz" \
    --rom "$PWD/s2.gen"
  ```

- **Result:** DIVERGENCE — 698 of 698 recovered driver-update ticks divergent.
- **First divergence:** tick 0 (movie row 10201), `global.tempoTimeout`,
  expected `0x3c`, actual `0x0`.
- **Reading:** the ROM seeds `TempoTimeout = CurrentTempo = 9Eh` at song load
  (`s2.sounddriver.asm:1820-1822`) and runs `TempoWait` at the top of the
  first `zUpdateMusic` (`sd:545-551`): `9Eh + 9Eh = 13Ch` → carry → `3Ch`.
  The engine's first update leaves its tempo accumulator at 0 — neither the
  load-time seed nor the first-update accumulation is modelled
  (gap analysis §1.2 #1/#2; behaviour spec §3.1). Every subsequent tick also
  diverges (cadence differences cascade through durations, envelope cursors
  and the write stream), so 698/698 is the honest count, and the tick-0 field
  is the frontier to move first. Two measurement facts recovered from the
  reference along the way, both now encoded in the comparator's tick
  recovery: the Saxman EHZ load masks interrupts across movie rows
  10195-10200 (those frames hold a half-initialised driver image and no
  `zUpdateMusic` service), and the caught-up Z80 misses row 10202's V-int
  entirely — one oracle tick is therefore one completed `zUpdateMusic`
  service from the observer's service stream, not one video frame.
- **Break-it evidence** (`TestS2AudioOracleComparator`, outputs from the
  evidence run at this commit):
  - untampered self-comparison: `S2 driver oracle: MATCH (698 ticks)`;
  - reference byte corrupted (tick 40, `FM1.DurationTimeout ^ 0x55`):
    `S2 driver oracle: DIVERGENCE at tick 40 (movie row 10242), field
    track.FM1.durationTimeout: … expected=0x41 actual=0x14 [1 of 698 ticks
    divergent]`;
  - engine write corrupted (tick 20, `writes[0] value ^ 0x40`):
    `S2 driver oracle: DIVERGENCE at tick 20 (movie row 10222), field
    writes[0]: … expected=ym0[0x28]=0x0 actual=ym0[0x28]=0x40 [1 of 698
    ticks divergent]`.
## 2026-08-30 - S3K oracle first frontier: boot silence burst (tick 3) — attribution retracted

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
- Result: **red**, as expected for the first run. **Superseded attribution:**
  the 2026-08-31 correction above proves this row is the 68k power-on PSG
  initialization loop, not the Z80 driver's initialization burst.
  First divergence: **tick 3, `EVENT_MISSING`, event 0** — the reference
  emits PSG `9Fh` as the first of the 68k bootstrap's four
  `PSGInitValues`; the driver-only engine host emits nothing. Error count:
  first divergence only (comparator stops); ticks 0-2 of the same run are
  green (`MATCH (3 ticks)` with `--ticks 3`).
- Broken on purpose before trusting the comparison (project rule): a
  corrupted `zCurrentTempo` byte in a temp copy (terminal digest recomputed)
  reports `GLOBAL_STATE_MISMATCH` at its exact tick with expected/actual
  (`64` vs `0`, exit 3); the same corruption without the digest fix is
  refused as `terminal body digest mismatch` (exit 4); a corrupted engine
  write is reported at its tick/event index by
  `TestS3kAudioParityComparator.corruptedWriteIsReportedAtItsEventIndex`.
- Unmodelled requests this run (logged by the capture host, not silently
  skipped): `E1h` fade-out (7 ticks), `FFh` SEGA chant (1 tick).
