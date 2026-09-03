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

## 2026-09-03 — S1 gameplay oracle: tick 618 → 629, a special SFX no longer steals a busy channel

- **Worktree/branch:** `.worktrees/audio-s1-special-frontier`,
  `bugfix/ai-s1-special-sfx-frontier`, on top of `234f1f606`.
- **Fixture:** `src/test/resources/audio/parity/s1/s1-gameplay-ghz1-reference.v2.jsonl.gz`,
  unchanged. Gate `TestS1GameplayAudioDriverOracle`.
- **Command:**

  ```
  LUA_BIN=lua5.4 mvn -Dmse=off -Dsonic1.rom.path=<absolute S1 REV01 .gen> \
    -Dtest=com.openggf.tools.audio.parity.TestS1GameplayAudioDriverOracle test -B
  ```

- **Result before:** MISMATCH, `EVENT_VALUE_DIFFERENT`, tick 618, event 3,
  field `decoded_write` — reference `<missing>`, engine YM2612 port 1 register
  176 (`0xB0`, feedback/algorithm) value 56.
- **Result after:** MISMATCH, `EVENT_VALUE_DIFFERENT`, tick 629 — reference
  YM2612 port 1 register 176 value 56, engine value 44.
- **What the divergence was.** Tick 618 dispatches the GHZ waterfall
  (`$D0`) through `Sound_PlaySpecial` while the ring SFX admitted at tick 593
  is still playing on FM4. The engine took FM4 from the ring at admission and
  played the waterfall's first note immediately: the whole 28-write voice load
  and key-on at tick 618. The reference emits nothing on FM4 until tick 629,
  where the ring's `cfStopTrack` finally hands the channel over.
- **The ROM routine.** `Sound_PlaySpecial`
  (`docs/s1disasm/s1.sounddriver.asm:1117`) initialises only the `v_spcsfx_*`
  slots. It sets bit 2 on the *music* slot as `Sound_PlaySFX` does (`:1146` for
  FM4, `:1153` for PSG3), but it never writes `v_sfx_fm4_track` or
  `v_sfx_psg3_track`. When those normal-SFX tracks are already playing it
  instead sets bit 2 on its own special track (`:1180-1182` and `:1185-1187`),
  so the special SFX advances its timing silently. The channel changes hands
  only in `cfStopTrack`'s special-track branch (`:2514-2518`), which is exactly
  the mechanism the earlier tick-629 entry below described from the other side.
- **The engine change.** `SmpsDriver` gained one predicate,
  `yieldsToIncumbentSfx`, applied at both admission-time sites that were
  taking the channel: the displacement scan in `prepareNewSfxAdmission` and
  the ownership install in `installPreparedSfxChannelOwnership`. A special SFX
  no longer displaces or relocks a channel held by a normal SFX. The music
  override bit is still set, because the ROM sets it unconditionally. This is
  the admission-time counterpart of the precedence `shouldStealLock` already
  applied per write; no new game-name or zone branch was introduced, and only
  S1's profile ever sets the special-SFX flag.
- **The next divergence.** At tick 629 both sides now emit a voice load on the
  same tick, but load different voices: the reference's `cfStopTrack` FM4
  branch takes `v_special_voice_ptr` and applies it to the special track
  (`:2514-2521`), while the engine restores the music voice on release. That is
  the next lane's target.
- **Gates at this commit:** S1 sound-test music `MATCH (14690 ticks)` and SFX
  `MATCH (1967 ticks)`, both exit 0, run as `S1AudioParityTool capture` +
  `compare` against the committed v1 references. S2
  `TestS2RequestAwareOracleRawStream#realCandidateAndBk2DrivenDriverStateCompare`
  `MATCH (698 ticks)`. The S3K oracle was not re-run: its command needs an
  external request sidecar that is not present in this worktree, and no S3K
  path can reach the changed code, since the special-SFX flag is set only by
  S1's audio profile.

## 2026-09-03 — S1 gameplay oracle: tick 629 (reference gap) → tick 618 (real engine divergence), special-SFX dispatch now captured

- **Worktree/branch:** `.worktrees/audio-s1-probe-special`,
  `feature/ai-s1-gameplay-probe-special`, on top of `dea9404e4`. Measurement
  only for the frontier move itself; the probe/capture-host changes that make
  the new tick observable are a small, mechanical routing fix (see below), not
  a driver-behaviour fix.
- **What changed — the probe.** `Sound_PlaySFX` (`docs/s1disasm/s1.sounddriver.asm:977`,
  PC `$721C6`) and `Sound_PlaySpecial` (`:1117`, "Sound_D0toDF") are two
  disjoint entry points reached directly from `PlaySoundID`'s shipped
  (`FixBugs = 0`) dispatch (`:690-706`): normal SFX ($A0-$CF) branches to
  `Sound_PlaySFX`, special SFX ($D0-$DF checked, though only $D0/Waterfall has
  a real `Go_SpecSoundIndex` entry -- anything else would already have crashed
  the ROM) branches directly to `Sound_PlaySpecial`. The v1 probe
  (`tools/audio/probes/s1_gameplay_driver_parity_probe.lua`) hooked only
  `Sound_PlaySFX`, so it never observed a `Sound_PlaySpecial` call -- the GHZ
  waterfall dispatch was invisible to the fixture. `Sound_PlaySpecial`'s PC
  ($7230C) was not obtainable from `RomOffsetFinder --game s1 find
  Sound_PlaySpecial` (it returned a stale/wrong offset -- cross-checked by
  re-deriving the already-known-good `Sound_PlaySFX` address the same way and
  finding it also wrong by a non-constant delta); the address was instead
  found by scanning the ROM for the opcode both routines share as their first
  instruction (`tst.b SMPS_RAM.f_1up_playing(a6)` = `4a2e0027`, byte-verified
  against v_fadeout_counter/f_fadein_flag tests and the `Go_SpecSoundIndex`
  table lookup that follows). The probe now hooks both PCs, asserting
  `$A0-$CF` at the `Sound_PlaySFX` site and `$D0-$DF` at the `Sound_PlaySpecial`
  site (the shipped-bug range, not narrowed to `$D0` -- see CLAUDE.md's
  `FixBugs` guidance) and appending either into the same flat per-tick
  `dispatches` array. **No schema change**: a recorded id's own value (>=
  `Sonic1Sfx.NORMAL_ID_MAX + 1`, i.e. `Sonic1AudioProfile.isSpecialSfx`)
  already disambiguates a special-SFX dispatch from a normal one, so a new
  field would have been redundant.
- **What changed — the replay host.** `S1OpenGgfSfxAudioCapture` (the shared
  host this oracle and the committed SFX oracle both use) unconditionally
  called `sequencer.setSpecialSfx(false)`. It now calls
  `sequencer.setSpecialSfx(profile.isSpecialSfx(soundId))` -- the engine's
  `SmpsSequencer`/`Sonic1SmpsLoader` already fully supported special-SFX
  loading and playback (`loadSfx` already redirected internally via
  `loadSpecialSfx`); the host just never asked for it.
- **Recapture.** `tools/audio/run_s1_audio_parity.sh --mode gameplay`
  (unchanged launcher, same movie/window: power-on through frame 3,000 of
  `sonic1-complete-withemeralds.bk2`). Two BizHawk captures byte-identical and
  two OpenGGF replay captures byte-identical (both checked by the launcher
  before it proceeds, `cmp -s`); uncompressed reference SHA-256
  `c8fe427155e405c234162152f23f74c941dcef24d9d9952984db63cf3c028ac7`
  (20,157,508 bytes, 2,343 ticks, **81 dispatches** vs v1's 70 -- the 11 new
  ones are all `Sound_PlaySpecial` calls to id `$D0`, GHZ waterfall).
  Published as `s1-gameplay-ghz1-reference.v2.jsonl.gz`; v1 retained
  unmodified (retired, not deleted -- see
  `src/test/resources/audio/parity/s1/fixture-manifest.json`).
- **Result:** **MISMATCH**, `EVENT_VALUE_DIFFERENT`, tick 618, event 3, field
  `decoded_write` -- reference `<missing>`, engine
  `AudioParityChipWrite[chip=ym2612, port=1, register=176(0xB0), value=56]`.
  This is a **real engine divergence**, not a reference gap: the engine's
  admitted special-SFX sequencer emits an FM frequency write the real ROM's
  `Sound_PlaySpecial` run at this tick did not. Not investigated in this lane
  (measurement only, per the brief); a future lane should chase
  `s1.sounddriver.asm`'s special-SFX track service (`:1117` onward,
  `cfStopTrack`'s special-track branch at `:2510-2515`) before changing engine
  behaviour. The old tick-629 divergence (music FM4 override surviving past a
  special-SFX release) no longer reproduces as the first divergence because
  the new frontier at 618 is strictly earlier in the same window.
- **Break-on-purpose evidence:** flipping one YM2612 register-40 write value
  in tick 0 of a temp copy of the v2 reference is reported at tick 0 by
  `TestS1GameplayAudioDriverOracle.corruptingTheReferenceIsDetectedAtTheCorruptedTick`
  (still green against v2, confirming the comparison is live, not vacuous).
- **Gates at this commit:** `run_s1_audio_parity.sh --mode music` MATCH
  (14,690 ticks); `--mode sfx` MATCH (1,967 ticks); `--mode gameplay` reports
  the mismatch above (exit 3, expected); S2
  `TestS2RequestAwareOracleRawStream#realCandidateAndBk2DrivenDriverStateCompare`
  MATCH (698 ticks); `com.openggf.tools.audio.parity.**` 136 tests, 0
  failures, 4 skipped (unrelated missing S3K/other optional inputs);
  `-Pguards` green.

## 2026-09-03 — S3K DAC frequency pinned; frontier reaches the FM track-parse phase

- **Context:** `.worktrees/audio-s3k-observer`, branch
  `feature/ai-s3k-request-observer` on `ef8d4bcdb`. No fixture, capture or
  comparator changed; one normalizer field was pinned.
- **Command:** the same invocation as the entry below.
- **Result before:** `TRACK_STATE_MISMATCH` at tick 138, role `MUS_DAC`, field
  `frequency`, reference 134, engine `null`.
- **Result after:** `TRACK_STATE_MISMATCH` at tick 138, role `MUS_FM1`, field
  `resting`, reference `false`, engine `true`.
- **Notes:** the engine was not missing a DAC track. `S3kAudioStateNormalizer`
  reported `null` for a DAC track's frequency with the comment that the mapping
  was not pinned. It is pinned: `SavedDAC` and `FreqLow` are the same `zTrack`
  byte at offset `0Dh` (`Sound/Z80 Sound Driver.asm:45-56`), and
  `zUpdateDACTrack_cont` stores the raw sample byte there including bit 7,
  before the rest check, reusing it verbatim when a duration follows without a
  note (`D:2880-2892`). The engine already keeps that byte as the track note,
  so reporting it directly makes the two agree. The reference's 134 is `$86`,
  a DAC note with bit 7 set. `FreqHigh` at `0Eh` is unused by a DAC track.

  The next divergence is a genuine phase difference, characterised but not
  fixed. At the frame the title music loads, movie frame 252, the reference has
  its DAC track parsed (`DurationTimeout` `06`, `SavedDAC` `86`) while `MUS_FM1`
  is initialised and unparsed (`PlaybackControl` `80`, all note state zero). The
  FM track first parses on the following frame, 253, where it becomes
  `PlaybackControl` `90` with `DurationTimeout` `6c`. The engine dispatches the
  request and runs a full update in the same tick, so it parses every track on
  the load frame and reports `MUS_FM1` as resting one frame early. Fixing it
  means modelling where `zPlayMusic` hands off to the first `zUpdateMusic`, and
  why the DAC track is ahead of the FM tracks by one frame.
- **Regression gates:** S1 GHZ music oracle **`MATCH (14690 ticks)`** and S1
  sound-test SFX oracle **`MATCH (1967 ticks)`**, both exit 0. The S2 driver
  oracle is unchanged at `DIVERGENCE at tick 210 [303 of 698 ticks divergent]`,
  the same result as before this change. `TestS3kAudioParityComparator`,
  `TestS3kAudioOracleFixtureContract`, `TestS3kRequestObservationSidecar` and
  `TestS1AudioStateNormalizer` report 37 passing with one skip, the skip being
  the ROM-gated measurement when no sidecar path is supplied.

## 2026-09-03 — S3K oracle advances off the producer-input limitation to tick 138

- **Context:** `.worktrees/audio-s3k-observer`, branch
  `feature/ai-s3k-request-observer` on `a92b12513`, with `tools/tracechaser` on
  `bugfix/ai-s3k-request-observer` at `78b8c1e`. No committed fixture,
  comparator or engine owner changed.
- **Fixture:** the committed bounded oracle
  `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`,
  unchanged, plus a new external request sidecar supplying driver inputs only.
- **Command:**

  ```
  S3kAudioParityTool compare \
    --reference src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz \
    --requests <external>/s3k-request-observations.json \
    --rom <absolute locked-on S3K ROM>
  ```

- **Result before:** `REFERENCE_LIMITATION`, tick 128, field `producer_input`.
- **Result after:** **`MISMATCH`, `TRACK_STATE_MISMATCH` at tick 138, role
  `MUS_DAC`, field `frequency`, reference 134, engine `null`.**
- **Notes:** the v1 stream samples the mailbox before each invocation, so a
  request written and consumed inside one frame is invisible to it. Two serial
  power-on captures of `[0,5400)` observed 14 requests at the `Play_Music`
  bus-release instruction while the Z80 was stopped. Both captures are
  byte-identical, SHA-256
  `2063b558c9b81ba8ccdf487ddb95d9be1bfd7979997be831d0f73bd4164639d3`, and the
  extractor reduces them to a 14-entry sidecar only when they agree.

  Thirteen of the 14 appear in the committed fixture one frame later, which is
  exactly where a pre-invocation sample would see them: `e1` at row 13 against
  fixture frame 14, `ff` at 62 against 63, `25` at 251 against 252, and so on.
  The fourteenth, `fe` at row 242, has no fixture counterpart at all. That is
  `cmd_StopSEGA`, written and consumed inside one frame, and it is the input
  the limitation was reporting as missing. The agreement on the other thirteen
  is independent corroboration that the observer reads the right byte.

  Supplying it is an input, not a compared value. The oracle already takes its
  mailbox from the reference, the way the S1 tool plays the GHZ song; the
  sidecar only supplies a byte the old capture was blind to. The default reader
  path is untouched and still reports the same limitation at tick 128, pinned
  by a test.

  The new frontier at tick 138 is a real engine gap: the reference has a music
  DAC track with a frequency, and the engine has no such track.
- **Regression gates:** S1 GHZ music oracle **`MATCH (14690 ticks)`** and S1
  sound-test SFX oracle **`MATCH (1967 ticks)`**, both exit 0. The S2 driver
  oracle against its committed raw-v1 fixture reports `DIVERGENCE at tick 210
  [303 of 698 ticks divergent]`, which is its documented pre-request-awareness
  state; the `MATCH (698 ticks)` recorded on 2026-09-03 is against the
  unpublished request-aware candidate, and that invocation needs
  `--ignore-digest` against an external path, which was blocked here.
  `TestS3kRequestObservationSidecar` passes 11 of 11.

## 2026-09-03 — S3K pre-consumption mailbox observed; request layer still not compared

- **Context:** `.worktrees/audio-s3k-observer`, branch
  `feature/ai-s3k-request-observer`, with `tools/tracechaser` on
  `bugfix/ai-s3k-request-observer`. No fixture, comparator, profile or engine
  owner changed, and **no frontier moved**.
- **Fixture:** none. This is a disposable live smoke over rows `[0,400)` of
  `src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2`
  (SHA-256 `82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`,
  466,334 rows) against the locked-on S3K ROM, written to an external scratch
  path. It is not a parity comparison.
- **Core:** a freshly built observer at ABI 5, decompressed SHA-256
  `c47e8e1aef25b39d4a947d8d57f77b2680cfb013103315945a48dabc2f4a54b0`, build id
  `6feee0d1b2ca882b`, installed to a scratch BizHawk home outside the
  repository. Seven native selftests pass, including the new
  `snapshot-at-pc-harness`.
- **Result:** exit 0, 400 rows observed and published, **four mailbox
  observations**. Process inventories were empty before and after.

  | Row | Active kind at the boundary | Mailbox byte |
  |---:|---|---|
  | 13 | 6, DriverInit | `e1` |
  | 62 | 0, root | `ff` |
  | 242 | 11, UpdateEverything | `fe` |
  | 251 | 0, root | `25` |

- **Notes:** row 242 is the source frame the service-128 limitation names, and
  its byte is `$FE`, `cmd_StopSEGA`. That value is now read from Z80 RAM
  `$1C0A` while the bus is still held, at the `startZ80` instruction before it
  executes. It is not inferred from the later stop burst, from SEGA-PCM exit,
  or from the fixture.

  Two corrections to the 2026-09-02 audit stand. The bus-release instruction is
  at `$1370`, not `$1374`, which falls inside its long operand and is never an
  instruction PC. And the boundary is not a child of the SEGA-PCM iteration:
  the observer's service stack is shared across processors, so the active kind
  is whichever Z80 service happens to be on top, measured here as kind 6, kind
  11 and root. That is why the observation is now taken by a
  parent-independent native action rather than a service push and pop.

  The request layer is still `UNAVAILABLE` for comparison. Authenticating the
  reference side alone cannot yield `MATCH`: the OpenGGF side must
  independently observe an equivalent request through its own producer before
  the layer can be compared. `REFERENCE_LIMITATION / producer_input` remains in
  force and the first divergence is unchanged at service 128.

  The native build attestation was simplified in the same round, on an explicit
  human ruling: the host-image trust roots, chained recipe digests, secure
  runtime and reproduction ritual are replaced by one build script whose
  provenance is an output rather than a gate. Pinned source commits, pinned
  clang packages and the patch remain pinned.
- **Regression gates:** the TraceChaser `S3k` filter reports 143 passing. Four
  failures across the `S2` and `S3k` filters were present on the pinned
  baseline; one of them, the observer-installation test, now fails for a new
  reason because it still pins the retired identity family and has not been
  moved to the simplified contract.

## 2026-09-03 — S3K request observer reaches the boundary; the reviewed topology does not hold

- **Context:** `.worktrees/audio-s3k-observer`, branch
  `feature/ai-s3k-request-observer`, on top of `feb9ea267`, with
  `tools/tracechaser` on `bugfix/ai-s3k-request-observer` at `7dd4cf3`.
  No fixture, candidate, comparator, profile or engine owner was changed, and
  no frontier moved.
- **Fixture:** none. This is a disposable, non-authoritative live smoke, not a
  parity comparison. The movie is
  `src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2`
  (SHA-256 `82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`,
  466,334 rows) against the locked-on S3K ROM.
- **Command:** a disposable reflection driver invoking the internal
  `S3kPreconsumptionRequestCaptureRunner.CaptureRawSmokePrefix` seam over rows
  `[0,400)` under `timeout --signal=TERM --kill-after=30s 20m mono`, against
  the 2026-09-02 base observer install
  (`install-a`, core SHA-256 `fa43fbc7ab2b38e2139c8288d1fc1489ecad353613283d2892a2a26399798b3a`).
  Output went to an external scratch path; no fixture destination was written.
- **Result:** exit 0, 400 rows observed and published, **zero mailbox
  submissions**. Process inventories were empty before and after.
- **Notes:** two facts in
  `docs/architecture/audits/audio/2026-09-02-s3k-preconsumption-request-producer-audit.md`
  do not survive execution.

  First, the bus-release instruction is at `$1370`, not `$1374`. The shipped
  bytes are `33fc010000a11100` at `$1358`, `0839000000a11100` at `$1360`,
  `66f6` at `$1368`, `13c000a01c0a` at `$136A`, `33fc000000a11100` at `$1370`
  and `4e75` at `$1378`. `$1374` falls inside the release instruction's long
  operand, so it is never an instruction PC and a hook placed there is silently
  unreachable. The first smoke recorded four `$1358` visits and zero `$1374`
  visits, which is what exposed it. `$1370` lies strictly inside the approved
  `$1358..$1374` interval and carries the exact approved opcode.

  Second, the diagnostic `Play_Music` does not run under the SEGA-PCM
  iteration. With the end hook at `$1370` the four invocations in `[0,400)` are
  bracketed by matched pairs at rows 13, 62, 242 and 251, and their active
  kinds are 6 (`DriverInit`), 0 (root), **11 (`UpdateEverything`)** and 0
  (root). Row 242 is the source frame the service-128 limitation names, and its
  active kind is 11, not the reviewed kind 8. Because the service stack is
  shared across CPUs, the parent of an M68K `Play_Music` is whichever Z80
  service happens to be on top, so no fixed single-parent `PUSH_BEGIN` topology
  can express this boundary. The kind-13 child therefore never opens and no
  `$1C0A` snapshot is taken.

  This is the audit's own declared stop condition: the existing exact actions
  cannot express the topology without a false lifecycle. `REFERENCE_LIMITATION
  / producer_input` and `FRESH_AUTHENTICATED_NATIVE_GPGX_AUTHORITY_UNAVAILABLE`
  both remain in force, and the service-128 first divergence is unchanged.
- **Regression gates:** the TraceChaser `S3k` filter reports 143 passing with
  the two pre-existing failures also present on the pinned baseline `8700dd0`
  (`Bk2Reader reads the canonical S3K fixture movies` needs
  `TRACECHASER_TEST_FIXTURE_ROOT`; `GpgxZ80AudioCapabilityTests lock reviewed
  S2 and S3K service manifests` fails on both trees). The 11 new
  `S3kPreconsumptionRequestProfile` cases pass.

## 2026-09-03 — S1 gameplay oracle stops at tick 629: the fixture records no special-SFX dispatch

- **Worktree/branch:** `.worktrees/audio-s1-gameplay-frontier`,
  `bugfix/ai-s1-gameplay-frontier`, at `6c788b4fe`. Measurement only; no engine
  change accompanies this entry.
- **Result:** MISMATCH, `TRACK_STATE_MISMATCH`, tick 629, role FM4, field
  `overridden` — reference true, engine false.
- **Diagnosis — a reference limitation, not an engine defect.** Tick 583
  dispatches `sfx_Spring` (`0xCC`) onto FM4 and tick 593 dispatches a ring
  (`0xB5`, resolved to the left-speaker `0xCE`) onto the same channel. `SndCE`
  is three notes totalling `$24` ticks, so it ends exactly at tick 629, and
  both streams agree write-for-write up to that point.
  `cfStopTrack` (`docs/s1disasm/s1.sounddriver.asm:2489-2563`) then chooses
  where to hand FM4 back. Its FM4 case first tests
  `v_spcsfx_fm4_track.PlaybackControl` (`:2510-2515`): when a **special** SFX is
  playing it restores `v_special_voice_ptr` into the special track and never
  touches the music track, so the music FM4 override bit survives. Only the
  fall-through `.getpointer` path (`:2519-2528`) reaches the music track and
  clears bit 2.
  The reference keeps the music FM4 override set at ticks 629-631, so the ROM
  took the special-SFX branch. Ticks 630 and 631 confirm it: their FM4
  frequency and key-on writes land *after* that invocation's music PSG writes,
  which the music FM walk (`:214-221`) precedes, so they come from the
  special-SFX section (`:243-247`).
- **Why the engine cannot follow.** GHZ's special SFX reaches the driver
  through `Sound_PlaySpecial` (`:1105`), a separate entry point with its own
  track slots. The fixture's per-tick `dispatches` array records only
  `Sound_PlaySFX` calls, and `raw_state.tracks` carries the ten music slots
  only — neither the special-SFX admission nor its track state is captured. The
  replay host has no data from which to admit that sound, so the engine
  correctly releases FM4 to music where the ROM releases it to a special SFX
  the fixture never mentions. `voice_selector` is `0x40` on every tick and is
  not evidence either way: `UpdateMusic` stores it unconditionally before the
  special-SFX section (`:243`).
- **What would move it:** a re-capture whose probe also records
  `Sound_PlaySpecial` dispatches and the two special-SFX track slots, published
  through the fixture contract. That is capture work, not engine work.
- **Gates at this commit:** S1 sound-test music MATCH (14,690 ticks) and SFX
  MATCH (1,967 ticks); the audio packages, `TestSmpsFadeAudioThroughput` and
  the S2 driver oracle run 2,470 tests with 0 failures.

## 2026-09-03 — S1 gameplay oracle: tick 316 → 629 (SFX walks fixed RAM slots)

- **Worktree/branch:** `.worktrees/audio-s1-gameplay-frontier`,
  `bugfix/ai-s1-gameplay-frontier`, on top of `2ba02dbad`.
- **Fixture and command:** unchanged from the entry below.
- **Result before:** MISMATCH, `EVENT_VALUE_DIFFERENT`, tick 316, event 3 —
  reference YM2612 port 1 register `0xB0` value 4, engine PSG `0x87`.
- **Result after:** MISMATCH, `TRACK_STATE_MISMATCH`, tick 629, role FM4,
  field `overridden` — reference true, engine false.
- **What moved, part one — the walk.** S1 `UpdateMusic` has no per-sound SFX
  service. It walks one fixed array of SFX track slots, SFX FM3..FM5
  (`docs/s1disasm/s1.sounddriver.asm:222-231`) then SFX PSG1..PSG3
  (`:233-241`), with no notion of which sound owns a slot. So two live sounds
  interleave by channel, not by admission order: at tick 316 a ring on FM4 is
  serviced before the jump still holding PSG1, though the jump started
  fourteen invocations earlier. The engine serviced each SFX sequencer whole,
  in admission order. `SmpsDriver` now walks the SFX slots itself when every
  live SFX program declares `SfxTrackWalkMode.CHANNEL_RAM_ORDER` (S1 only),
  driving each sequencer's tracks through a new begin/tick/finish pass.
- **What moved, part two — the release.** With the walk in place the frontier
  landed at tick 562, where a finishing FM5 SFX restored the music voice after
  the SFX PSG1 slot instead of before it. `cfStopTrack` (`:2489-2563`) hands
  the channel back from inside the finishing track's own slot service, whether
  or not the sound has other tracks still playing; the engine deferred the
  release of a wholly finished sound to end-of-frame completion cleanup. The
  slot walk now reconciles the finishing slot inline, through a new
  `SmpsSequencerHost.reconcileFinishedSfxSlot`. Non-coordinated games keep the
  previous deferral. No constant was introduced.
- **New frontier:** tick 629, the reference still has the music FM4 track
  overridden where the engine has released it — an override-lifetime question,
  not an ordering one.
- **Gates held:** S1 sound-test music MATCH (14,690 ticks) and SFX MATCH
  (1,967 ticks); the audio packages plus `TestSmpsFadeAudioThroughput` and the
  S2 driver oracle run 2,470 tests with 0 failures.

## 2026-09-03 — S1 gameplay oracle: tick 302 → 316 (SFX admission owns its channels)

- **Worktree/branch:** `.worktrees/audio-s1-gameplay-frontier`,
  `bugfix/ai-s1-gameplay-frontier` from `develop` at `2229b5b7c`.
- **Fixture:** `src/test/resources/audio/parity/s1/s1-gameplay-ghz1-reference.v1.jsonl.gz`
  (2,343 ticks, 70 dispatches).
- **Command:**

  ```
  java -cp target/classes:<deps> com.openggf.tools.audio.parity.S1AudioParityTool \
      capture --repo <worktree> --run-root <external run root> \
      --reference <run root>/reference.jsonl --rom <abs S1 REV01 ROM> \
      --output <run root>/openggf.jsonl --capture gameplay
  java -cp target/classes:<deps> com.openggf.tools.audio.parity.S1AudioParityTool \
      compare --repo <worktree> --run-root <external run root> \
      --reference <run root>/reference.jsonl --openggf <run root>/openggf.jsonl \
      --human-report <run root>/report.txt --json-report <run root>/report.json
  ```

- **Result before:** MISMATCH, `EVENT_EXTRA`, tick 302, event 0 — engine PSG
  write `0x92` (PSG1 volume 2) with no reference counterpart.
- **Result after:** MISMATCH, `EVENT_VALUE_DIFFERENT`, tick 316, event 3 —
  reference YM2612 port 1 register `0xB0` value 4, engine PSG `0x87`.
- **What moved:** tick 302 dispatches `sfx_Jump` (`0xA0`), which loads a PSG1
  track. `Sound_PlaySFX`'s header loader sets `PlaybackControl` bit 2 on the
  displaced *music* track while loading each SFX track — `.sfx_loadloop` for FM
  (`docs/s1disasm/s1.sounddriver.asm:1029`) and `.sfxinitpsg` for PSG
  (`:1037`) — and it is reached from `PlaySoundID` at the top of `UpdateMusic`
  (`:202`), before that same invocation's DAC/FM/PSG music walk (`:208-227`).
  So the music PSG1 track is already overridden on the admitting invocation and
  emits nothing, even though the SFX track has written no register yet. The
  engine had S1 on `SfxChannelOwnershipMode.FIRST_WRITE`, so the music track
  still emitted its volume byte. S1 now uses `ADMISSION`, the mode S2 already
  derived from the identical `zPlaySound` shape. No constant was introduced.
- **New frontier:** tick 316 dispatches `sfx_Ring` (`0xB5`) onto FM4 while the
  jump SFX still holds PSG1. `UpdateMusic` walks the *fixed SFX RAM slots* —
  SFX FM3..FM5 (`:222-231`) then SFX PSG1..PSG3 (`:233-241`) — so the ring's
  FM4 writes precede the jump's PSG1 writes. The engine services SFX
  sequencer-by-sequencer in admission order, so the jump's PSG1 writes come
  first. Same writes, wrong order.
- **Gates held:** `run_s1_audio_parity.sh --mode music` MATCH (14,690 ticks);
  `--mode sfx` MATCH (1,967 ticks); S2
  `TestS2RequestAwareOracleRawStream#realCandidateAndBk2DrivenDriverStateCompare`
  passes.

## 2026-09-03 — S1 gains a second audio oracle, sourced from real gameplay

- **Worktree/branch:** `.worktrees/audio-s1-complete-oracle`,
  `feature/ai-s1-complete-run-oracle` from `develop` at `feb9ea267`.
- **Fixture (new):** `src/test/resources/audio/parity/s1/s1-gameplay-ghz1-reference.v1.jsonl.gz`
  — the committed complete-run movie `sonic1-complete-withemeralds.bk2`
  (`src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds/`, SHA-256
  `f2e817936d…`, 225,101 input rows), captured from power-on through frame
  3,000 (2,343 driver invocations, epoch opens at frame 656 on the real GHZ1
  BGM dispatch — 341 dormant invocations precede it, title/SEGA/menu, unlike
  the two sound-test movies' shared 514) by a new probe,
  `tools/audio/probes/s1_gameplay_driver_parity_probe.lua` (a
  movie/window-specific variant of the committed `s1_audio_sfx_parity_probe.lua`,
  same shape: driver-RAM-derived track state plus ordered YM/PSG bus writes,
  one record per `UpdateMusic` invocation, plus a `dispatches` array of any
  `Sound_PlaySFX` calls the invocation made). 70 real SFX dispatches
  (jump/ring/spring/etc., not a scripted list) are captured in this window.
  Two BizHawk captures are byte-identical
  (SHA-256 `c7d58e8721f240ef…`, both runs).
- **Command:** `tools/audio/run_s1_audio_parity.sh --mode gameplay --rom
  <absolute SHA-1-verified S1 REV01 ROM> --bizhawk-home <BizHawk 2.11 Linux
  x64> --output-root <external run root>`.
- **Result:** **MISMATCH**, first divergence **tick 302, `EVENT_EXTRA`, event
  0** — the engine emits an extra PSG write (`0x92`) that the reference does
  not have. Pinned by `TestS1GameplayAudioDriverOracle.currentFrontierIsTheFirstDivergence`
  (ROM-gated, `-Dsonic1.rom.path=`). Not investigated in this lane (measurement
  only, per the brief); a future lane should chase the ROM routine that owns
  this write before changing engine behaviour.
- **Broken on purpose before trusting the comparison** (project rule): flipping
  one YM2612 register-40 write value in tick 0 of a temp copy of the reference
  is reported at tick 0 by
  `TestS1GameplayAudioDriverOracle.corruptingTheReferenceIsDetectedAtTheCorruptedTick`.
- **Existing S1 sound-test gates unchanged:** `run_s1_audio_parity.sh --mode
  music` still reports **MATCH (14,690 ticks)**; `--mode sfx` still reports
  **MATCH (1,967 ticks)** — both re-run against the unchanged committed
  fixtures in this worktree to confirm this lane's shared-code changes
  (`AudioParitySchema`/`AudioParityMetadata`/`AudioParityJsonl`/
  `AudioParityComparator`/`S1AudioParityTool` gained a third `gameplay`
  capture kind alongside `music`/`sfx`) did not regress them.
- **Notes:** the capture window is bounded to frame 3,000, not the originally
  planned ~5,000 (see `docs/architecture/plans/audio/2026-08-09-s1-ghz1-
  gameplay-audio-timeline-plan.md`'s [860,4975) window, which this frontier
  deliberately overlaps): BizHawk's headless client consistently
  self-terminates (clean process exit 0, no Lua error surfaced to its own
  console output even with `OGGF_TRACE_QUIET=0`, no native crash signal) at
  frame 3,219 of this specific movie when driven through
  `tools/tracechaser/bizhawk/run_bizhawk_lua.sh`'s established launch shape —
  a boundary the two short sound-test movies (≤2,791 rows) never previously
  reached through this launcher. The cause was not isolated: diagnostic
  `pcall` wrapping around every `invocationLifecycle` entry/close transition
  in the probe never fired, and the movie's own input transcript has nothing
  unusual at that row (`|..|...R....|........|`, plain held-right input, no
  reset/power marker). 3,000 stays safely inside the frames that reliably
  complete; a future lane investigating the launcher itself (not this
  worktree's scope) could recover the fuller window.
  Also fixed in this lane, filed as a separate `fix(tools):` commit per
  instruction: `run_s1_ghz1_gameplay_audio_timeline.sh` (the unrelated,
  never-executed S1 GHZ1 gameplay-audio *timeline* framework —
  `S1GameplayAudioTimeline*`, a different, semantic-decision capture shape
  from this driver-register oracle) was missing `OGGF_INPUT_REPOSITORY_ROOT`
  in its `capture_reference` call, so `run_bizhawk_lua.sh` aborted before
  BizHawk ever launched. That one-line fix is necessary but insufficient: the
  script's hardcoded in-repo `OUTPUT_ROOT` is separately rejected by
  `output_policy.py`'s external-output-root requirement, so that tool remains
  unexercised end-to-end; see `tools/audio/README.md`.

## 2026-09-03 — the S2 request-window candidate is published as a committed fixture

- **Context:** `.worktrees/audio-s2-fixture-publish`, branch
  `bugfix/ai-s2-request-fixture-publish`, on top of `feb9ea267`. No comparator,
  alignment, or engine behaviour changed; the payload is the captured bytes,
  gzipped and unmodified.
- **Fixture:** new —
  `src/test/resources/audio/parity/s2/s2-request-window-w10150-10900.raw-v2.jsonl.gz`
  (gz SHA-256 `be8ab87f45499fcf5db0aee5613d699f56d79d5d6a8ffacbbfbe21592ab95c15`,
  expanded SHA-256 `a7d56fe71674d9f4a9307e6fb6078f7832409bb310916e808faf28b1e9426c2c`,
  750 rows over `[10150,10900)`, 25 request transfers), with the provenance
  sidecar `s2-request-window-w10150-10900.metadata.json`. Driven by
  `sonic-2-sonic-tails-complete-emeralds.bk2` against the S2 World REV01 ROM
  (SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`). Two independent captures
  (`coincident-extract-g-final` and `-h-final`) hash-match, which is the Task 8A
  duplicate-capture gate; human approval to publish was granted 2026-09-03.
- **Command:**

  ```
  LUA_BIN=lua5.4 mvn -Dmse=off \
    '-Dsonic2.rom.path=<absolute S2 REV01 ROM>' \
    '-Ds2.request.bk2.path=<absolute complete-emeralds BK2>' \
    '-Dtest=com.openggf.tools.audio.parity.s2.TestS2RequestAwareOracleRawStream' \
    test -B
  ```

  `-Ds2.request.candidate.path=<absolute candidate>` still overrides the
  committed payload; the run was made both ways.
- **Result:** unchanged in both directions —
  `S2 unbound request candidate: MATCH: 25 production transfers agree` and
  `S2 driver oracle: MATCH (698 ticks)`. 24 tests, 0 failures, 0 skips, exit 0
  with the property and without it.
- **Notes:** publication installs a comparison reference only. `production_bound`
  stays false, the reader stays package-private and CLI-unreachable, the
  comparator stays a disposable test-only owner, and request equality remains a
  reference limitation. `TestS2RequestWindowFixture` pins both digests and the
  parsed window shape, so a drifted byte fails before any comparison can quietly
  change meaning. Widening and second-recording work is planned in
  `docs/architecture/plans/audio/2026-09-03-multi-recording-oracle-roadmap.md`.


## 2026-09-03 — S2 driver oracle reaches full MATCH over the 698-tick window

- **Context:** `.worktrees/s2-tick0-land`, branch `bugfix/ai-s2-level-playbgm-land`,
  on top of `810dbc039`. No fixture, candidate, comparator or alignment was
  changed.
- **Fixture:** the authenticated S2 driver oracle payload behind
  `TestS2AudioOracleFixture.fixturePath()`, driven by
  `sonic-2-sonic-tails-complete-emeralds.bk2` against the S2 World REV01 ROM
  (SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`).
- **Command:** the same invocation as the entry below.
- **Result before:** `S2 driver oracle: DIVERGENCE at tick 557 (movie row 10759),
  field writes.count: expected=4 actual=5 [20 of 698 ticks divergent]`.
- **Result after:** **`S2 driver oracle: MATCH (698 ticks)`**.
  `MATCH: 25 production transfers agree` is unchanged.
- **Notes:** the remaining extras were PSG attenuation bytes `0xf2`, `0xf4`,
  `0xf6`, `0xf8`, `0xfa`, `0xff` — a music PSG3 volume envelope ramping to
  silence on the noise channel. SFX `0xC1` Explosion is requested at movie row
  10759 and declares `cFM5` plus `cPSG3`, and the reference's PSG3
  `playbackControl` byte moves `0x80` to `0x84` on that exact row, so the ROM
  holds the track SFX-overridden for the whole span. The engine installs that
  override on the correct row; logging the transitions shows PSG channel 2
  flipping to overridden at row 10759. The defect was that the PSG branch of
  `SmpsSequencer.refreshVolume` consulted only the rest bit and never the
  override bit, so the envelope kept writing behind the override.

  All three drivers agree here, so this is a universal correction rather than a
  per-game one. S2 `zPSGUpdateVol` does `and 6` over the rest and override bits
  and returns (`s2.sounddriver.asm:1305-1308`); S1 `SetPSGVolume` tests the two
  bits separately (`s1.sounddriver.asm:1965-1969`); S3K reaches the same outcome
  one level up, where `zUpdatePSGTrack` returns on bit 2 before both the
  frequency pair and the volume tail (skdisasm `Sound/Z80 Sound
  Driver.asm:4079-4081`). In each case the flutter or envelope index still
  advances behind the suppressed write, which is why the gate belongs at the
  write and not at the envelope step.
- **Regression gates:** S1 GHZ music oracle `MATCH (14690 ticks)` and S1
  sound-test SFX oracle `MATCH (1967 ticks)`, both exit 0. The S1, S2, S3K and
  shared audio packages report 1,943 tests with the same five pre-existing
  failures as the entry below and no new ones.
  `TestSmpsFadeAudioThroughput` passes.

## 2026-09-03 — S2 ROM SFX-release semantics advance the driver frontier to tick 557

- **Context:** `.worktrees/s2-tick0-land`, branch `bugfix/ai-s2-level-playbgm-land`,
  base `3c54967f7`. No fixture, candidate, comparator or alignment was changed.
- **Fixture:** the authenticated S2 driver oracle payload behind
  `TestS2AudioOracleFixture.fixturePath()`, driven by
  `sonic-2-sonic-tails-complete-emeralds.bk2` against the S2 World REV01 ROM
  (SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`).
- **Command:**

  ```
  LUA_BIN=lua5.4 mvn -Dmse=off \
    '-Dsonic2.rom.path=<absolute S2 REV01 ROM>' \
    '-Ds2.request.bk2.path=<absolute complete-emeralds BK2>' \
    '-Ds2.request.candidate.path=<absolute s2-request-window.oracle-raw-v2.jsonl>' \
    '-Dtest=com.openggf.tools.audio.parity.s2.TestS2RequestAwareOracleRawStream#realCandidateAndBk2DrivenDriverStateCompare' \
    test -B
  ```

- **Result before:** `S2 driver oracle: DIVERGENCE at tick 266 (movie row 10468),
  field writes.count: expected=2 actual=4 [107 of 698 ticks divergent]`.
- **Result after:** `S2 driver oracle: DIVERGENCE at tick 557 (movie row 10759),
  field writes.count: expected=4 actual=5 [20 of 698 ticks divergent]`.
  `MATCH: 25 production transfers agree` is unchanged.
- **Notes:** every one of the 107 divergent ticks was the engine emitting extra
  writes and none was a missing write. At tick 266 the extras were an
  `A4`/`A0` frequency pair on FM4 continuing a modulation ramp the reference
  never emits. Decoding the reference's own `playbackControl` byte for the FM4
  music slot showed `0x9c` through movie row 10466 and `0x9a` from row 10467:
  the SFX-override bit clears and the **rest** bit sets in the same transition.
  That is `cfStopTrack`'s FM SFX tail, `s2.sounddriver.asm:3548-3553`, which does
  `res 2` / `set 1` and restores only the voice through `zSetVoiceMusic`; it
  sends no key-off, no pan rewrite and no frequency resend.
  `zStopPSGSFXTrack` (`:3581-3589`) is the same shape. Leaving the released
  music track at rest is what keeps `zDoModulation` returning early
  (`:989-991`), so the channel stays silent until its next note.

  Two changes were needed. `Sonic2SmpsSequencerConfig` now declares
  `ROM_VOICE_RESTORE` / `ROM_REST_RESTORE`, the modes S1 already declared for
  the identical routine. That alone changed nothing, because
  `SmpsAssetCatalog.copyBuilder` rebuilds every sequencer config for the
  presentation path and silently dropped five configured settings:
  `fmSfxReleaseMode`, `psgSfxReleaseMode`, `sfxTrackWalkMode`,
  `fmVolumeVoiceBankMode` and `palUpdateMode`. Every sequencer built through
  the catalog therefore reverted to the legacy full-restore behaviour,
  including S1's. Completing the copy is what delivered the fix.

  One test asserted the behaviour the ROM contradicts.
  `TestS2SfxAdmissionChannelMask.acceptedRingLeftOwnsFm4BeforeMusicFirstService`
  ended by requiring that restored music resume FM4 frequency output after the
  SFX releases the channel. Its final assertion now states the ROM's outcome
  instead, that the released track is at rest and no `A4`/`A0` pair is resent,
  with the routine cited. That class was green before this change and red with
  it, and is the only test the change moved.
- **Regression gates:** S1 GHZ music oracle `MATCH (14690 ticks)` and S1
  sound-test SFX oracle `MATCH (1967 ticks)`, both exit 0, captured and
  compared with `S1AudioParityTool` against the committed references. The audio
  test packages report 1,889 tests with five failures, all reproduced red at
  the base commit with this change reverted, or structurally unreachable from
  it: `TestSonic2RequestProductionWiring` (3) and `TestAudioPresentationBoundary`
  (1) were confirmed by a control run, and
  `TestAudioPresentationArchitectureGuard` (1) reads a fixed list of seven
  production files that includes neither file changed here.

## 2026-09-03 — S2 source-owned level-entry timing advances driver frontier to tick 266

- **Context:** landed on `bugfix/ai-s2-level-playbgm-land` from
  `.worktrees/s2-tick0-land`, base `7f5067b23`; no fixture or comparator
  semantics changed. The tranche was reviewed before landing and four defects
  were fixed on this branch: the level-music schedule was resolving the timing
  model against the zone registry's progression index instead of the ROM
  zone/act pair, which was correct only for EHZ; the title-card lifecycle
  serviced a second hardware `VINT_SERVICE` boundary per frame; the rewind
  registry adapter-count assertions were not updated for the new scheduler;
  and `LevelManager` grew against its size ratchet.
- **Command:** `LUA_BIN=lua5.4 mvn -Dmse=off
  '-Dsonic2.rom.path=$REPO/s2.gen'
  '-Ds2.request.bk2.path=$REPO/docs/BizHawk-2.11-linux-x64/Movies/sonic-2-sonic-tails-complete-emeralds.bk2'
  '-Ds2.request.candidate.path=$CAPTURES/s2-native-authority-live-evidence-20260902/coincident-extract-g-final/s2-request-window.oracle-raw-v2.jsonl'
  '-Dtest=com.openggf.tools.audio.parity.s2.TestS2RequestAwareOracleRawStream#realCandidateAndBk2DrivenDriverStateCompare'
  test -B`.
- **Result:** request transfer **MATCH (25/25)**. The driver frontier advances
  from tick 0 to **tick 266 (movie row 10468)**, `writes.count`, expected 2,
  actual 4; 107 of 698 ticks diverge.
- **Evidence:** `Level_PlayBgm` publishes EHZ once at row 10195. The ROM-data
  Saxman cost produces exactly six `LOAD_PENDING` rows (10195-10200), then a
  distinct `SERVICE_IN_FLIGHT` boundary at 10201 with no committed snapshot;
  update 0 commits at 10202 (`tempoAccumulator=0x3c`) and ordinary update 1 at
  10203 (`0xda`). Thus the previous tick-0 state and write fields all match,
  while the six-row readiness movement remains exactly the source-derived
  `0x58` to `0xa4` shift rather than absorbing the completion boundary.
- **Cross-checks at landing:** S1 GHZ music oracle **MATCH (14690 ticks)** and
  S1 sound-test SFX oracle **MATCH (1967 ticks, 8 dispatches)**, both exit 0.
  No `*TraceReplay` class changed result against base `7f5067b23`, and
  `TestS2CompleteEmeraldRunChain` is unchanged. The S2 driver comparison is
  `MEASUREMENT_ONLY`; the asserting companion is
  `TestS2RequestAwareOracleRawStream#levelPlayBgmPublishesEmeraldHillOnceAtTheNativeLoadBoundary`.

## 2026-09-03 — S2 Level_PlayBgm tranche frozen after two Critical reviews

- **Critical 1 — omitted and trace-coupled service:** `f7373c1cb` advanced the pending request from `ObjectManager`'s object-visible VBlank clock.
  Normal pre-player title-card VBlanks did not advance that clock, while trace-bootstrap-selected object passes could advance it.
  The tranche froze rather than treating the row-10195 comparator result as proof of a production-owned dispatch.
- **Critical 2 — duplicate service:** fix wave `248b03ed6` added a title-card service edge alongside `LevelFrameStep`'s existing VINT edge.
  Playable title-card leave rows consequently serviced the scheduler twice; the test covered only the pre-player predicate-false arm.
  The re-review failed, so `bugfix/ai-s2-c0a-replan3` is preserved as non-merge evidence and the clean replan restarts at `b8b23a8fd`.

## 2026-09-03 — S2 request-transfer window MATCH; driver tick frontier unchanged

- **Worktree/branch:** `.worktrees/s2-c0a-replan3`,
  `bugfix/ai-s2-c0a-replan3`; measured at `7b1442846` plus the reviewed
  spike-cause diff now committed as `89eab0649`.
- **Fixture:** two independently extracted, comparison-only raw-v2 candidates
  for source rows `[10150,10900)`, each SHA-256
  `a7d56fe71674d9f4a9307e6fb6078f7832409bb310916e808faf28b1e9426c2c`;
  both remain explicitly unbound (`production_bound:false`).
- **Command:** `mvn -Dmse=off -Dsonic2.rom.path=<absolute REV01 ROM>
  -Ds2.request.candidate.path=<candidate-g-or-h raw-v2 JSONL>
  -Ds2.request.bk2.path=<pinned complete-emeralds BK2>
  -Dtest=com.openggf.tools.audio.parity.s2.TestS2RequestAwareOracleRawStream#realCandidateComparesAgainstIndependentProductionBk2Run
  test -B`.
- **Result:** candidate g and candidate h independently reported
  **`MATCH: 25 production transfers agree`**, exit 0 (one test, no
  failures/errors/skips). Before the two source-owned fixes, the first
  divergences were transfer 3 (ring B5 in SFX0 rather than ROM SFX1) and then
  transfer 20 (CPU Tails spike damage A3 rather than ROM A6). The reviewed
  observer-only same-BK2 comparison at `89bdb6eb9` then reported
  **DIVERGENCE at tick 0 (movie row 10202)**, `global.tempoTimeout`, expected
  `0x3c`, actual `0x58`; 698 of 698 ticks diverged.
- **Notes:** the raw-v2 candidates remain comparison-only and supply no driver
  input; requests arise from the BK2-driven engine. This does not authenticate
  the candidates or bind replay authority. It supersedes the old tick-210
  music-only-host result: that host supplied no SFX requests, whereas the
  same-BK2 observer measures the production request path.

  At `3045e716d`, the S2 compressed-load readiness model cost the actual EHZ
  bank-2 Saxman path at 363,255 Z80 T-states (363,283 on the enabled PAL
  path). It predicted exactly six fully masked presentation rows and moved the
  same-BK2 tick-0 value from `0x58` to **`0xa4`**, no further. The remaining
  `0xa4` versus `0x3c` mismatch is the separately measured early Music0
  request: OpenGGF submits EHZ at row 10184, while native initialization is
  bounded to the row 10194→10195 boundary.

## 2026-09-02 — S3K E4 seven-slot stop/restore source correction under review; no oracle move

- **Worktree/branch:** `.worktrees/sound-driver-roadmap-completion`,
  `feature/ai-sound-driver-roadmap-completion`, candidate over accepted retained
  S3K E4-state commit `8e0babd09`.
- **Fixture:** none changed. The authenticated S3K AIZ1 reference has no E4
  request, so it cannot establish an E4 comparison result.
- **Command:** `mvn -Dmse=off
  -Dtest=TestSmpsDriverSession,TestS3kE4StopSfxPlan,TestSmpsStatefulCommandPolicy,TestSmpsPhysicalPolicy
  test -B`.
- **Result:** the earlier candidate omitted `cfStopTrack`'s E4-local
  `zGetSFXChannelPointers` PSG sequence. The corrected plan now covers, in native
  order, the raw YM `$28` hazard, `1Fh + current SFX VoiceControl`, the stopped-SFX
  bit-0 conditional `$FF`, the FixBugs=0 unconditional `$FF`, and then an eligible
  signed music-noise re-latch; it also retains AMS/FMS for the music `$B4` restore.
  Exact direct and composite rollback tests cover physical PSG failures and the
  post-logical-mutation/pre-publication boundary.
- **Notes:** this entry records a source correction, not a product-frontier closure.
  No comparator was run and no `MATCH` is claimed. Service 128 remains the same
  authenticated `REFERENCE_LIMITATION` (`producer_input`); standalone E3 and
  PSG-SFX-admission stale-`ix`/`$FF` behaviour are separate frontiers.

## 2026-09-02 — S3K E3 PSG-silence product gap closes without moving service 128

- **Worktree/branch:** `.worktrees/sound-driver-roadmap-completion`,
  `feature/ai-sound-driver-roadmap-completion` from `8952620bf` (Task 9A
  implementation; commit containing this entry).
- **Fixture:** no capture or fixture changed. The existing authenticated
  `s3k-aiz1-intro-reference-v1.jsonl.gz` remains unchanged and does not supply
  an E3 request.
- **Command:** focused profile/resolver/queue/session/oracle-host tests:
  `LUA_BIN=lua5.4 mvn -Dmse=off
  -Ds3k.rom.path=<absolute-S3K-ROM>
  '-Dtest=com.openggf.game.sonic3k.audio.TestSonic3kSpeedShoesCommandSemantics,com.openggf.audio.presentation.TestAudioPresentationCommandResolver,com.openggf.audio.presentation.TestAudioPresentationCommandQueue,com.openggf.audio.session.TestSmpsDriverSession#psgSilenceWritesExactRomProgramWithoutMutatingSessionState+psgSilenceObserverFailureCannotPartiallyApplyOrReplay,com.openggf.tools.audio.parity.s3k.TestS3kAudioOracleFixtureContract'
  test -B`.
- **Result:** **56 tests pass** with 0 failures, 0 errors, and 0 skips. The
  production typed route and engine oracle host both execute the immutable
  `9F BF DF FF` program sourced from `zPlaySoundByIndex` /
  `zPSGSilenceAll`. The session test retains the physical identity and all
  logical music/SFX/override/tempo/pending-service state, observes no YM
  writes, and proves there is no next-service duplicate. Observer failure is
  quarantined after commit and cannot partially apply or replay E3.
- **Notes:** this closes the source-backed E3 **product gap only**. It does not
  move or reinterpret the authenticated comparison. Service **128** remains
  `REFERENCE_LIMITATION`, `field=producer_input`, because the request consumed
  while the reference producer suspended interrupt services is unavailable.

## 2026-09-01 — Override-resume producer stops at `REFERENCE_LIMITATION`

- **Worktree/branch:** `.worktrees/sound-driver-roadmap-completion`,
  `feature/ai-sound-driver-roadmap-completion`; TraceChaser producer commits
  `912fef0a`, `e3fdf73`, and mechanics hardening through `a61450ee`.
- **Fixture:** none. The dedicated
  `src/test/resources/audio/parity/override-resume-first-divergence-v1/`
  commit bundle remains absent; its proposed nested S1/S2 members have no
  independent authority, and no capture or fixture gained authority.
- **Command:** focused TraceChaser S1, S2, extractor/publisher, CLI, and Lua
  contract tests plus `verify-deterministic-build.sh`; the locked observer
  recipe verifier and the reviewed-capability guard were then run as hard
  authority checks. Exact commands and hashes are recorded in
  `docs/architecture/validation/audio/2026-09-01-override-resume-reference-limitation.md`.
- **Result:** `REFERENCE_LIMITATION`, code
  `FRESH_AUTHENTICATED_NATIVE_GPGX_AUTHORITY_UNAVAILABLE`. The current host's
  `/usr/bin/ar` differs from the locked recipe, the current collector source
  differs from the pinned capability field that Task 8 is forbidden to
  refresh, and no current-session two-build observer inputs are configured.
  A static older install is not fresh capture authority.
- **Notes:** `a61450ee` hardens fd-relative staging and identity-uncertain
  quarantine, but it cannot prove nested-path containment when a retained
  target dirfd is renamed after revalidation, and four sequential `linkat`
  calls cannot provide one visibility point. The canonical plan now requires
  private construction of the complete dedicated bundle and one
  `renameat2(RENAME_NOREPLACE)` commit under an explicit cooperative-lock and
  namespace-stability precondition. That publisher redesign is required before
  publication. The mandatory provenance inventory also remains incomplete, so
  Task 8 made no Java production change, froze no literal expectation,
  performed no live capture, and left both S1 hard gates untouched.
  The subsequent atomic-bundle implementation replaces those four links with
  private exact-inventory construction and one directory
  `renameat2(RENAME_NOREPLACE)` under the documented cooperative lock and
  namespace-stability precondition. Bundle-aware Java consumers now reject an
  absent or invalid commit object without consulting legacy leaves. This moves
  no audio frontier: the fresh authenticated native-GPGX and complete
  provenance gates remain unavailable, no fixture was published, and the same
  `REFERENCE_LIMITATION` code remains authoritative.

## 2026-09-01 — S3K service 128 is an authenticated producer-input limitation

- **Worktree/branch:** `.worktrees/sound-driver-roadmap-completion`,
  `feature/ai-sound-driver-roadmap-completion` at `e4f083172`
  (documentation fix round 3; production comparator/reference evidence is
  unchanged).
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged authenticated 5,400-frame reference; projected to 5,286
  services).
- **Command:**
  `LUA_BIN=lua5.4 mvn -Dmse=off
  -Ds3k.rom.path=${OGGF_REPO_ROOT}/s3k.gen
  '-Dtest=com.openggf.tools.audio.parity.s3k.TestS3kAudioOracleFixtureContract,com.openggf.tools.audio.parity.s3k.TestS3kAudioParityComparator'
  test -B`, plus
  `java -cp target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar
  com.openggf.tools.audio.parity.s3k.S3kAudioParityTool compare
  --reference src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz
  --rom ${OGGF_REPO_ROOT}/s3k.gen --ticks 260 --format json`.
- **Result:** the 260-service run compares services 0-127, then stops at
  service/tick **128**, underlying reference event **0** (`YM port 1,
  register 82h, value FFh`). The typed report is
  `REFERENCE_LIMITATION`, `field=producer_input`, with
  `ProducerInputEvidence.Availability.UNAVAILABLE_DURING_PRODUCER_SUSPENSION`
  and reason `mailbox input was unavailable for the first observable service
  after reference producer interrupt services suspended`; the CLI exits **5**.
  This is not an engine divergence and there is no realignment. Ordinary
  missing-write evidence remains `EVENT_MISSING` and exits **3**; malformed
  reference/tool failures remain exit **4**.
- **Notes:** the limitation is selected from the source-owned
  `zPlaySEGAPCM` interrupt-suspension boundary and the first resumed service,
  not from a tick number, zone, request guess, or write shape. The exact
  84-write stop proof remains at service/tick **49** for `FFh`; service/tick
  **138** (next music activation) still begins with the exact reference
  84-write stop prefix. S1 hard gates remain `MATCH (14,690 ticks)` for GHZ
  music and `MATCH (1,967 ticks, 8 dispatches)` for sound-test SFX. Named
  remaining frontiers are the unsupported `E3h` PSG-mute product gap (not a
  structured producer-input `REFERENCE_LIMITATION`), the `E4h` seven-slot
  conditional physical write/restoration walk, and full `FFh` control-flow
  parity beyond the implemented 84-write stop/PCM transport (including the
  producer-side pre-consumption mailbox at service 128).

## 2026-08-31 — S1 sound-test SFX oracle reaches full MATCH

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` at `4c1efea6c` plus the pending S1
  implementation tranche.
- **Fixture:** `src/test/resources/audio/parity/s1/s1-soundtest-sfx-reference.v1.jsonl.gz`
  (unchanged committed reference; S1 World REV01 ROM SHA-1
  `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`).
- **Command:** `S1AudioParityTool capture --capture sfx --reference
  <run-root>/reference.jsonl.gz --rom <absolute verified ROM> --output
  <run-root>/openggf.jsonl`, followed by `S1AudioParityTool compare
  --reference <run-root>/reference.jsonl.gz --openggf
  <run-root>/openggf.jsonl --human-report <run-root>/report.txt
  --json-report <run-root>/report.json`.
- **Result:** **`S1 audio parity: MATCH (1967 ticks)`**, exit 0. A fresh
  capture/comparison against the committed GHZ music fixture remains
  **`MATCH (14690 ticks)`**, exit 0.
- **Notes:** first-divergence work from tick 377 through the end of the
  fixture modelled source-owned S1 behavior: one terminal note-off; the
  explicit PSG3 `$DF/$FF` admission pair and shared tone-3/noise ownership;
  per-track release with FM voice/pan and PSG rest/noise restoration; fixed
  SFX-RAM walk order; tied PSG volume service; raw `$B5` ring-speaker
  alternation; and shipped `FixBugs=0` `SendVoiceTL` reads through the global
  special-SFX pointer, including ROM vector bytes when it is zero. No fixture
  or comparator was changed. Human listening remains pending in the SMPS
  playback checklist.

## 2026-08-31 — S3K service projection crosses SEGA PCM to the hidden stop request

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the PCM-tier correction lands with this
  entry).
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged authenticated 5,400-frame reference).
- **Command:** `S3kAudioParityTool compare --reference <committed fixture>
  --rom <absolute SHA-1-verified locked-on ROM>` after compiling the worktree.
- **Result:** services 0-127 match. First divergence service **128** (source
  frame **242**), `EVENT_MISSING`, event 0: reference Z80 YM part II
  `82h = FFh`, engine missing.
- **Notes:** `FFh` enters `zPlaySEGAPCM`, which clears its request flag and
  disables interrupts for the whole chant (`D:4372-4424`). The projection now
  keeps the command-owned 84-write stop-all service, excludes register `2Ah`
  sample transport / `2Bh=80` DAC entry, and emits no fictitious driver
  services for the 100 transport-only frame rows. This yields **5,286**
  complete services. At source frame 242 the reference emits another exact
  stop-all burst, consistent with `FEh` (`cmd_StopSEGA`), but its pre-frame
  mailbox is empty: the 68k request is written and consumed within
  `host.Advance`, before the post-frame RAM snapshot. The v1 producer therefore
  cannot authorize the engine request at service 128. Moving this frontier
  requires a true pre-consumption 68k-to-Z80 mailbox probe, not inference from
  the burst.

## 2026-08-31 — S3K service projection reaches the SEGA command at service 49

- **Worktree/branch:** `.worktrees/sdre2-cadence-resume`,
  `feature/ai-sdre2-cadence-resume` (the service projection lands with this
  entry).
- **Fixture:** `src/test/resources/audio/parity/s3k/s3k-aiz1-intro-reference-v1.jsonl.gz`
  (unchanged authenticated 5,400-frame reference).
- **Command:** `S3kAudioParityTool compare --reference <committed fixture>
  --rom <absolute SHA-1-verified locked-on ROM>` after compiling the worktree.
- **Result:** the complete boot service and the first ordinary `E1h`
  fade-init service match. First divergence service **49**, `EVENT_MISSING`,
  event 0: reference Z80 YM part II `82h = FFh`, engine missing.
- **Notes:** the reader now projects 5,400 frame rows into 5,386 complete Z80
  services. It groups the cross-frame boot burst by the source-owned
  `zPalDblUpdCounter` transition `0 -> 5` at `zInitAudioDriver` completion
  (`D:523-551`), producing one 85-write boot service without a frame-number
  trigger. The engine host emits the exact shipped `zStopAllSound` sequence;
  the next service consumes the `E1h` request that remained pending during
  boot and matches its unconditional `zPSGSilenceAll`. Service 49 consumes
  `FFh` (`cmd_SEGA`), whose first action is another `zStopAllSound`; SEGA PCM
  dispatch is still explicitly unsupported by the host and is the new
  frontier.

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
