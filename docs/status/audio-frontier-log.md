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
- **Break-it-on-purpose (comparator proof it actually compares):**
  - one corrupted fixture byte (tick 5000 DAC `duration` 11→12 in a temp copy) →
    `MISMATCH kind: track_state_mismatch, tick 5000, role DAC, field duration,
    reference 12, openggf 11`, exit 3;
  - one corrupted engine write (tick 7000 event 0 `ym2612 p0 reg 0x28` value 1→0 in a
    temp copy) → `MISMATCH kind: event_value_different, tick 7000, event 0,
    reference value=1, openggf value=0`, exit 3.
