# Object-slot occupancy: current state

**Measured at** `694745c09`, 2026-08-21. Sources: `SlotOccupancyProbe` armed with
`OGGF_SLOT_PROBE=1 OGGF_SLOT_PROBE_FULL=1` over the full `-Ptrace-replay` sweep (94 reports,
29,832 comparable frames). The narrative is in the 2026-08-21 entries of
[trace-frontier-log.md](trace-frontier-log.md); this page is the state, so nobody has to read
eight of them to act.

**Nothing in the suite compares occupancy.** The probe decides no test outcome. Every number
here is invisible to CI.

## The three metric definitions, and why each replaced the last

| # | metric | replaced because |
|---|---|---|
| 1 | **per-slot presence** — a ROM slot the engine leaves empty | **Counts relocation as absence.** The same object at a different slot index produces a "missing" entry at the ROM's slot. Put `RING` at the top of the ranking; ring *counts* were equal on 83 of 87 divergent frames. |
| 2 | **per-type count deficit** — `romCount(T) - engCount(T)`, placement-invariant | **Ignored reserved slots.** The probe renders a parent-held child reservation as `RESERVED`, not a hex id, so slots that correctly model folded children read as absent. Put `BRIDGE` at the top at 1,969; crediting reservations leaves **19**. |
| 3 | **both-directions, reserved-credited** — short *and* over, with `RESERVED`/`UNATTRIB` credited against the shortfall | **Current.** A one-way metric reports an object that moved between slots as unchanged, and hides over-counts entirely — `Projectile` reads as "340 short" while the engine holds **373 more** than the ROM. |

Two definitions that follow from #3 and should be used as definitions, not rediscovered:

- **Reserved-slot credit.** `RESERVED` and `UNATTRIB` are engine occupancy. A shortfall covered
  by reserved slots on the same frame is **not** a deficit; it is a correctly folded composite.
- **The one-way / two-way axis.** *One-way* (short, ~no over) means the engine never makes them.
  *Two-way* (short and over comparable) means it makes them at the wrong time or in the wrong
  slot. These are different defects with different questions, and the axis cuts across every
  earlier family grouping.

## Fixed

| object | id | was | now |
|---|---|---|---|
| **CPZStaircase** (S2) | `0x78` | 966 short, per-frame shape exactly {3, 6} | **0 residual** (12 raw, all covered by `RESERVED` on the same frames). CPZ2 divergent frames 249 → 81; CPZ1 111 → 75. Landed `55a313ff4`. |

## Documented as CORRECT — do not re-target

Both reached the top of a ranking and both are correct code. Named here so the next ranking
does not rediscover them.

| object | id | why it looked wrong | why it is right |
|---|---|---|---|
| **BRIDGE** (S1) | `0x11` | 1,969 "deficit", metric #2 | Folds its logs into the parent **and reserves the child slots** (`allocateChildSlots`). 1,950 of 1,969 explained by `RESERVED` on the same frames; residual **19**. |
| **RING** (S1) | `0x25` | first place under metric #1 | Ring counts equal on 83 of 87 divergent frames — the rings exist, at other slot indices. Residual short 566/over 6 is real but third-order, not the "missing rings" the first ranking implied. |
| Vertical cannonball delete | — | read as `FixBugs`-only | **Both** ROM branches delete; the conditional is ordering alone. Engine margin `0xE0` = 224 matches `v_limitbtm2 + 224`. |

## Confirmed and unlanded

| object | id | shortfall | status |
|---|---|---|---|
| **CANNONBALL** (S1) | `0x20` | 581 short / 0 over | **Two defects diagnosed, fix parked.** `explode()` destroys and spawns instead of rewriting `obID` in place; plus an uncited `!isOnScreenX(256)` deletion `CBal` does not have. Must land together. **Prediction, untested:** `0x20` short → ~0 with over staying ~0, **and** `0x3F` short 282 *and* over 275 both falling. Blocked on the convert-in-place capability — see [the design note](../architecture/designs/2026-08-21-object-convert-in-place.md). |

## One-way — the engine never makes them

The remaining value. Same question for each: *why is this never created?*

| game | object | id | short | over | fixtures |
|---|---|---|---|---|---|
| S2 | RexonHead | `0x97` | 503 | 0 | 2 |
| S1 | CANNONBALL | `0x20` | 581 | 0 | 5 |
| S1 | RING | `0x25` | 566 | 6 | 22 |
| S1 | CHAINED_STOMPER | `0x31` | 314 | 0 | 4 |
| S1 | MZ_BOSS | `0x73` | 279 | 0 | 2 |
| S1 | FZ_BOSS | `0x85` | 210 | 0 | 2 |
| S1 | SBZ_SAW | `0x6A` | 181 | 0 | 4 |
| S2 | Bubbles | `0x24` | 240 | 45 | 2 |

**Check the reserved-slot question first for each** — several object families use
`allocateChildSlots`, and two commissioned targets have already turned out to be correct folds.

## Re-attributed — was the largest one-way target, is not one-way and is not its own defect

**BossExplosion (S2, `0x58`) and Eggrobo (S2, `0xC7`) are the same defect**, measured
2026-08-21 over the same 94-report sweep. Do not work them separately.

| what | measurement |
|---|---|
| reserved-slot credit | **none** — zero `RESERVED`/`UNATTRIB` lines on any S2 divergent frame. Not a fold. |
| metric #3, both directions | **1,336 short / 35 over** across the same 6 fixtures |
| relocation inside the raw signal | **961 of 2,297** raw `rom=0x58` lines cancel against an `eng=0x58` on the same frame |
| sampling phase | **ruled out** — 1,077 of the 1,084 DEZ shortfall is ONE contiguous 173-frame episode, f7649-7887 |
| "engine never makes them" | **false** — the engine holds `BossExplosion` on 149 of those 173 frames |

**What the engine actually holds where the ROM has `0x58`:**

| engine | lines | share |
|---|---|---|
| `-` (absent) | 1,621 | 70.6% |
| **`0xC7`** (Eggrobo/DEZ boss) | **511** | 22.2% |
| `0x5D` (CPZ boss) | 135 | 5.9% |
| `0xAF` | 28 | 1.2% |

And the mirror is exact: where the engine holds `0xC7` and the ROM does not, the ROM holds
`0x58` on **511** lines and anything else on 31. **511 = 511** — the same slots, counted twice
under two names, in two different tables of this page.

**Mechanism: convert-in-place, the capability CANNONBALL is already parked on.** The ROM
rewrites the id of the live object rather than deleting and spawning:
`ObjC5_PlatformExplode` does `move.b #ObjID_BossExplosion,id(a0)`
(`docs/s2disasm/s2.asm:81762`), and `Obj5D_Main_Explode2` and `loc_3DFBA` (inside `ObjC7`) are
the same shape. The engine leaves the object under its original id, which reads as a
BossExplosion shortfall *and* an Eggrobo over-count simultaneously.

So this is **blocked on the same capability** as CANNONBALL — see
[the design note](../architecture/designs/2026-08-21-object-convert-in-place.md) — and it
should land with that work, not before it. The ~29% conversion share is the part that fix
addresses; the 70.6% `eng=-` remainder is a separate question and is the only part still worth
its own investigation.

**One warning for the next ranking.** Grouping by raw recorded id mixes games: id `0x58` also
appears in S1 and S3K fixtures (3,410 raw short lines across 17 fixtures), but `0x58` is
BossExplosion only in S2 (`ObjPtr_BossExplosion: dc.l Obj58`, `s2.asm:30004`). Filtering to S2
is what reproduces this page's own 6-fixture count. Any per-object row must be filtered by game
before it means anything.

## Two-way — made at the wrong time or place

Different question: *why is this mistimed?* A deficit-ranked list under-reports these, and for
three of them the engine holds **more** than the ROM.

| game | object | id | short | over | fixtures |
|---|---|---|---|---|---|
| S2 | Eggrobo † | `0xC7` | 6 | **523** | 1 |
| S2 | Rexon2 | `0x96` | 0 | **457** | 2 |
| S2 | Projectile | `0x98` | 340 | **373** | 22 |
| S2 | SmallBubbles | `0x0A` | 231 | 247 | 5 |
| S1 | EXPLOSION | `0x3F` | 282 | 275 | 15 |
| S2 | EggPrison | `0x3E` | 301 | 96 | 4 |
| S2 | LeavesGenerator | `0x2C` | 262 | 115 | 5 |
| S1 | LZ_CONVEYOR | `0x63` | 0 | **181** | 4 |
| S1 | FLOATING_BLOCK | `0x56` | 53 | 126 | 13 |
| S2 | ArrowShooter | `0x22` | 246 | 82 | 2 |

† **Eggrobo is not a separate target.** 511 of its 523 over-counts are slots where the ROM has
converted the object to `BossExplosion` in place — see the re-attribution section above. It
lands with that work.

`Eggrobo`, `Rexon2` and `LZ_CONVEYOR` have **no shortfall at all** and were invisible to every
deficit-ranked list produced before metric #3. `EXPLOSION`'s two-way shape is expected to be a
symptom of the CANNONBALL conversion defect rather than its own bug — that is the prediction
above, and it is untested.

## Scope limits

- **S3K is excluded from all per-object rows.** Its recorded id is the truncated low byte of a
  32-bit code pointer, not an identity, so grouping S3K entries by object groups by an artefact.
  Its presence divergence is real (10 of 10 fixtures) and cannot be attributed. See
  [known-discrepancies](known-discrepancies.md).
- **The sampling-phase alternative is not ruled out in general.** `slot_dump` frames are ~14
  apart, so a one-sample divergence cannot be distinguished from an intra-frame timing
  difference. It *is* ruled out for rings specifically (episodes persist a mean of 4.06 samples).
  Establish it per object before treating a small divergence as an engine defect.
- **Occupancy is not landable as a comparison yet** — 88 of 94 fixtures diverge on presence
  alone. Fix, re-measure with the probe (free), and wire it in when the red set is reviewable.
