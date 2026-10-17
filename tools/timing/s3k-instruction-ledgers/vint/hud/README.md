# UpdateHUD instruction ledger (research prototype; nothing integrated)

> Citations: sonic3k.asm line numbers here were converted on promotion (2026-10-17) from the local checkout 1a454a0
> used by research passes 1-13 to the superproject pin skdisasm 044fa46. ROM addresses and bytes are identical in both
> revisions; see [../../README.md](../../README.md#citations).

## What runs
`hud_cost.py` runs the shipped UpdateHUD (`$DD06`) and its draw callees on `../../mini68k/mini68k.py`, a fail-closed interpreter for the opcode forms on this path only. The callees are:
- DrawSix/Three/Two/SingleDigitNumber;
- HUD_DrawZeroRings;
- HUD_Lives.

**Inputs** are the engine-owned HUD values: Update_HUD_* flags, Score, Ring_count, Timer, Game_paused, Life_count. RAM addresses are read from instruction operands at pinned PCs.

**Cost rules:**
- Digits come from repeated subtraction; there is no DIVU on this path.
- VDP writes cost only their instruction time. FIFO, DMA and renderer tile work are separate terms.
- **Open terms** (fail-closed): debug HUD, competition HUD, and time over (`UpdateHUD_TimeOver` → Kill_Character).

## Tests
`test_hud_cost.py`: 10 tests, OK.

**Native oracle:** all 42 retained ending buckets take one branch family: timer running, frame < 59, no flags set. The walk reproduces their exact per-PC counts, 272 cycles.

**Synthetic vectors** for every other branch, which the native data does not cover. Expected loop counts are derived from the source in the test:
- score 1230: 12 subtractions, 4 drawn digits, 6 positions;
- rings 57 and zero-ring art;
- second rollover 0:37→0:38: 14 subtractions, 3 draw blocks;
- lives 3 with a blank tens digit.

**Controls:**
- State perturbations change the counts.
- Open terms raise.
- GPGX and Motorola agree.
- A pin mutation is rejected.
- A deliberate SUB-carry mutant of `mini68k` fails 4 tests.

## Path cycles (GPGX; the research dump `hud-terms.json` was not promoted)

| Path | Cycles |
|---|---|
| Timer running, no rollover | 272 |
| Timer stopped | 178 |
| Paused | 198 |
| Second rollover (0:38) | 2,202 |
| Minute rollover | 1,942 |
| Score 1230 | 2,516 |
| Score 987654 | 4,252 |
| Rings 57 | 1,694 |
| Zero-ring art | 2,302 |
| Lives 3 | 1,062 |
| Lives 12 | 984 |

Cost is a pure function of these values. UpdateHUD also advances Timer, which is the owner's state and not a cost input.

## Engine owners for the inputs

Candidates at `0197c5a5a`; adoption is the Java owner's decision:
- `game/LevelTimer.java` and `game/LevelState.java`: Update_HUD_timer semantics, timer and pause;
- `game/LevelRingDisplay.java`: Ring_count and the Update_HUD_ring_count redraw;
- the score and life owners.

What is missing is a per-VInt snapshot of these dirty flags and values, taken at VInt.
