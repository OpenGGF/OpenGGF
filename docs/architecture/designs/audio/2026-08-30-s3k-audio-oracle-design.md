# S3K sound-driver oracle: reference capture, fixture, comparator

**Date:** 2026-08-30
**Branch / worktree:** `feature/ai-sdre-oracle-s3k` (`.worktrees/sdre-oracle-s3k`),
from `feature/ai-sound-driver-re` `f087b8947` with `feature/ai-sdre-gaps` and
`feature/ai-sdre-spec-s3k` merged.
**Kind:** design + delivery record (phase-B oracle lane, S3K).
**Inputs:** `2026-08-30-sound-driver-re-gap-analysis.md` (oracle lane),
`2026-08-30-s3k-sound-driver-behaviour-spec.md`,
`2026-08-30-s3k-sound-driver-routine-map.md`,
`docs/architecture/audits/audio/2026-08-30-audio-oracle-tooling-map.md`.
**Source rule:** sources-closed — driver behaviour statements cite the
skdisasm-backed routine map/spec only; TraceChaser/engine code was read for
"what the tooling does today".

## 1. What was built

A committed, re-runnable per-invocation oracle for the S3K Z80 sound driver
in the shape that worked for S1 (driver-RAM track state + ordered YM/PSG
write stream per driver invocation), with the reference produced by the
native headless GPGX harness rather than the S1 lane's Lua/EmuHawk path
(which cannot see Z80-issued chip writes and is barred for this lane).

| Piece | Path |
|---|---|
| Reference capture entry point (C#, compiled against the pinned TraceChaser harness sources) | `tools/audio/s3k/S3kAudioOracleReferenceCapture.cs` |
| Capture runner (verifies stock + observer-core hashes, compiles, runs headless) | `tools/audio/run_s3k_audio_oracle_reference.sh` |
| Committed reference fixture (gzip JSONL, 5,400 invocations) | `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz` |
| Fixture identity sidecar | `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-metadata-v1.json` |
| Reference reader/decoder (RAM → zTrack vocabulary; digest-validating) | `com.openggf.tools.audio.parity.s3k.S3kAudioReferenceReader` |
| Field inventory (GATE vs DIAGNOSTIC, ROM + engine source per field) | `S3kAudioFieldRegistry` |
| Engine capture host (drives `SmpsDriver` with the recorded request timeline) | `S3kOpenGgfAudioCapture` |
| Engine state normalizer (snapshots → driver-RAM vocabulary) | `S3kAudioStateNormalizer` |
| Comparator (validation-first, no realignment, first divergence) | `S3kAudioParityComparator` |
| CLI (`validate` / `compare`, exit 0/2/3/4) | `S3kAudioParityTool` |
| Fixture integrity + break-it tests | `TestS3kAudioOracleFixtureContract`, `TestS3kAudioParityComparator` |
| Frontier record | `docs/status/audio-frontier-log.md` |

## 2. Reference capture semantics

One reference row = one emulated frame = one `zVInt` driver invocation
(spec §1: the whole driver runs inside the vertical-blank interrupt; the DAC
loop between interrupts touches no track RAM). Per row:

- **`mailbox` (pre-invocation)** — `zMusicNumber`/`zSFXNumber0`/`zSFXNumber1`
  (`1C0A-1C0C`) read *before* the frame advances: the 68k-written requests
  the coming invocation consumes (`zFillSoundQueue`, map §4.2). These are
  driver *inputs*, recorded so the engine can be driven with the same request
  timeline; they are never copied into engine state.
- **`writes`** — the frame's ordered YM/PSG bus writes from the patch-0001
  observer's kind-3/kind-4 chip events, decoded with the production YM
  address-latch rule (subjects 0/2 latch a register address per port and
  survive frames; subjects 1/3 emit `port/register/value`); PSG events emit
  the raw byte. Each write also records the issuing CPU. No
  service-ownership projection is used — attribution is out of band via the
  RAM snapshot, per the lane brief.
- **`ram` (post-invocation)** — Z80 `1C00h-1FA0h`
  (`zDataStart..zTracksSaveEnd`), the driver's variable + track + 1-up-save
  RAM, read from the core's `Z80 RAM` memory domain after the frame. Post-pass
  sampling matches the S1 recorder convention (trace row N is after frame N).
- A `metadata` first row pins schema, ROM SHA-1/CRC32, movie name + SHA-256 +
  frame count, RAM window, observer core hash; a `terminal` row pins tick
  count, decoded write count, and a SHA-256 over the tick-row bytes.

Determinism: two consecutive 5,400-frame captures were byte-identical.

### Observer provenance

The capture uses the **production** patch-0001 (`buffer-z80-audio-events`)
observer core, verified against the committed
`native/gpgx-audio-observer/artifact-lock.json` identity
(compressed SHA-256 `e65315743a6a1228…`, build id `cba4d8c88cf968a9`) and a
stock BizHawk 2.11 home verified against `install-core.sh`'s `LOCKED_STOCK`
list. The runner script re-verifies both hashes on every run and assembles
the BizHawk home in the caller's scratch directory. The S3K-only diagnostic
patches (0002/0003, cycle-stamped) were **not** used: the per-invocation
oracle needs frame + execution order, which the production core provides,
and the diagnostic locks are `production_lock_eligible: false`.

The C# entry point compiles against the pinned TraceChaser harness sources
(gitlink `9e51ff79e`) with the same hash-pinned Roslyn `csc.exe` the harness
build uses; it lives in OpenGGF (not the submodule) because the submodule
cannot be pushed from this lane. The harness's own `Bk2Reader`,
`GpgxHost`, manifest loader and `ApplyFrame` are reused unchanged.

## 3. Fixture

`s3k-aiz1-intro-reference-v1`: movie frames 0-5399 (90 s) of the committed
`s3k-complete-sonic-tails.bk2` from power-on — boot, SEGA chant, title music
(`25h`), file select, Knuckles intro theme (`1Fh`), AIZ1 music (`01h`,
≈54 s) and 10+ distinct gameplay SFX (`33h 3Ch 3Dh 48h 4Ah 59h 62h AFh B1h
B7h BAh …`), satisfying the ≥10 s music + ≥6 SFX brief. 1.4 MB gzipped
(23 MB raw), within the few-MB budget;
`TestTraceFixtureCompressionGuard`'s scope is `src/test/resources/traces/`
and the fixture is compressed regardless. `TestS3kAudioOracleFixtureContract`
re-verifies gzip SHA-256, uncompressed SHA-256, sidecar agreement, source-BK2
identity and the stream's terminal digest on every suite run.

## 4. Comparator semantics

`S3kAudioParityTool compare` decodes the reference (validation first: schema,
ROM identity, RAM window, ordinal continuity, terminal digest), replays the
recorded request timeline into the engine's real `SmpsDriver` +
`Sonic3kSmpsLoader`/`Sonic3kSmpsSequencerConfig` (music `01h-32h`, credits
`DCh`, SFX `33h-DBh`, `E0h/E2h/E6h-FEh` stop-all, `E4h` stop-SFX, mirroring
`zPlaySoundByIndex` D:1641-1665; `E1h/E5h/E3h/FFh` are reported as
unmodelled), advances one NTSC frame (735 samples) per tick, captures chip
writes via `ChipWriteObserver`, and normalizes
`SmpsDriverSnapshot`/`SmpsSequencerSnapshot` into the driver-RAM vocabulary.
Comparison per tick, in order: GATE globals (`zCurrentTempo`,
`zTempoAccumulator`, `zTempoSpeedup`, `zSpeedupTimeout`), GATE track fields
for the sixteen fixed slots (nine music, seven SFX, ROM slot order), then the
ordered write stream. First difference wins; nothing realigns.
`S3kAudioFieldRegistry` is the executable inventory of which zTrack/global
bytes are compared and which stay DIAGNOSTIC (unmapped engine coordinate:
`DataPointer`, modulation phase bytes, `zDACIndex`, fades, queue bytes).

## 5. Broken on purpose (evidence)

- Baseline on real data: `--ticks 3` → `MATCH (3 ticks)`.
- One corrupted RAM byte (`zCurrentTempo`, tick 1) in a temp copy with the
  terminal digest recomputed → `GLOBAL_STATE_MISMATCH / tick 1 /
  currentTempo / reference 64 / openggf 0`, exit 3.
- Same corruption without recomputing the digest → refused:
  `terminal body digest mismatch`, exit 4.
- One corrupted engine write → `EVENT_VALUE_DIFFERENT` at its exact
  tick/event (`TestS3kAudioParityComparator`, committed).

## 6. First frontier

See `docs/status/audio-frontier-log.md` 2026-08-30: red at **tick 3,
`EVENT_MISSING` event 0** — the reference's Z80 boot `zStopAllSound` burst
(first write PSG `9Fh`) has no engine counterpart, because the engine driver
performs no boot-time initialisation writes. This lane does not fix driver
behaviour; the entry pins the start line for the driver-repair lanes.

## 7. Known limits and open questions

1. The engine capture host models neither fades (`E1h/E5h`), PSG-mute
   (`E3h`), nor the SEGA chant (`FFh`); requests are logged as unmodelled.
   Divergences beyond such ticks are only meaningful once those transforms
   are modelled or the passage avoids them.
2. `voiceIndex`/`modulationCtrl` engine mappings are best-effort composites
   (registry documents both); if the first state divergence lands on one of
   them, verify the mapping against the routine map before treating it as a
   driver bug.
3. The reference records lag frames (`lag` flag); the S3K driver still runs
   its `zVInt` on 68k-lag frames, so no row is skipped on either side.
4. PAL, pause, 1-up and speed-shoes passages are not in this fixture; they
   need their own movies (the capture runner takes any BK2).
5. The oracle compares from power-on; per-song oracles (like S1 GHZ) can be
   cut from any tick range later, but the committed fixture keeps the
   power-on origin so the boot burst stays visible.
