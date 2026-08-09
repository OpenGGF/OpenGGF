# Sonic 1 GHZ Music-Driver Parity Result

**Date:** 2026-08-09

**Result:** Valid deterministic capture; parity mismatch at tick zero

## Scope and command

This run exercised the music-only Sonic 1 parity harness against the supplied
sound-test movie. It did not compare PCM, DAC sample timing, sound effects, or
music/SFX contention.

From the `feature/ai-s1-audio-parity` worktree at commit `e1c8c2f91`:

```bash
S1_ROM_PATH='<repository-root>/Sonic The Hedgehog (W) (REV01) [!].gen'
tools/audio/run_s1_audio_parity.sh --rom "$S1_ROM_PATH"
```

The command returned `3`, the documented result for a valid parity mismatch.
All four captures completed; this was not a capture or tool failure.

## Input identity

| Input | Verified identity |
|---|---|
| ROM | Sonic 1 World REV01; SHA-1 `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`; CRC32 `afe05eee` |
| BK2 | `s1-soundtest-ghz.bk2`; SHA-256 `622ff642d0b0835a4f77bee568f2413f288ead3306a8bc2a93e8d8f77f24ca9c`; 989 input rows |
| Emulator | BizHawk 2.11, Genesis Plus GX; `EmuHawk.exe` SHA-256 `b2d4be5e2a766a5161cc26f3af2a90753c39d64c91c54a9884171aed09e21df3` |

The reference observer selected the proved `memory_callback` path, so there was
no PC-manifest fallback. Its callback proof covered 26,143 YM2612 port-zero
pairs, 4,363 port-one pairs, and 23,530 PSG writes. The 514 pre-epoch
`UpdateMusic` invocations were launch diagnostics only and were not replayed by
OpenGGF.

## Determinism and cycle proof

Detailed evidence remains locally in the ignored run directory
`target/audio-parity/s1-ghz/run.GOTxXlBd/`.

| Producer | Files | SHA-256 for each file | Bytes each | Records |
|---|---|---|---:|---:|
| BizHawk reference | `reference-1.jsonl`, `reference-2.jsonl` | `e81769e1663430cbb8c82e50e539397edc3149fe32b7a67be60169d178f57e9f` | 117,969,965 | 14,690 ticks plus metadata |
| OpenGGF | `openggf-1.jsonl`, `openggf-2.jsonl` | `5540fd2967f8775f3c75e750b4de282d9e00b42afed3534508d67f2c44c1d9b1` | 46,985,322 | 14,690 ticks plus metadata |

`cmp` confirmed byte identity independently for both producer pairs. Every
stream spans ordinals 0 through 14,689. Reference recurrence starts at ordinal
5,473 with period 4,608; the terminal ordinal is
`5,473 + (2 * 4,608) = 14,689`, proving one complete cycle followed by an
identical repeated cycle.

Both reference runs reached the recurrence stop and published normally. The
probe would have failed before publication on a second `$81`, a queued sound,
pause/fade/reset/Sega-PCM/speed-up contamination, a changed sound-test state,
or non-neutral post-movie input; none fired. BizHawk emitted non-fatal X11
`BadMatch` diagnostics during window initialization, but both output streams
remained byte-identical.

## First divergence

The comparator stopped at the first validation-ordered mismatch:

| Property | Value |
|---|---|
| Classification | `track_state_mismatch` |
| Tick | 0 |
| Role | DAC |
| Field | `base_frequency` |
| Reference | 32,768 |
| OpenGGF | 0 |
| Ticks fully compared | 0 |

The human report is 121 bytes and the JSON summary is 151 bytes. They contain
only the bounded field context above; no register stream or ROM/song payload is
embedded. Tick zero has 198 decoded reference transactions and 322 decoded
OpenGGF transactions, but event comparison was correctly not reached after the
earlier state gate failed.

This result authorizes investigation of tick-zero DAC `base_frequency`
semantics and capture alignment. It does not establish a chip-port ordering
fault, authorize reordering any YM2612/PSG writes, or by itself justify a
production audio change. Any correction needs the shipped-ROM routine and a
focused adjacent-order regression test as required by the harness design.

No detailed capture or report file is tracked; `git ls-files` returned no
entries for the run directory.
