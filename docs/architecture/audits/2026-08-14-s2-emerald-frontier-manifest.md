# S2 complete-emerald chain — frontier manifest

**Date:** 2026-08-14
**HEAD:** `aef2ae90c`
**Status:** `TestS2EhzHalfpipeRoundTripChain` GREEN. `TestS2CompleteEmeraldRunChain` RED on
5 axes.
**Suite at HEAD, all fixes stacked:** trace profile 842 run / 9 failures / 56 errors /
4 skipped, 65 red classes — identical by name to the session baseline, no interaction
regression.

---

## What syncs

The run is 28 segments, EHZ1 → Death Egg, 7 special stages, ~245,000 movie frames.

- **27 of 28 segments compare clean.**
- **Player physics matches the recording row-for-row on every frame of the run.**
- **24 of 27 gap transitions compare entirely clean.**
- On the failing gaps, transfer ids, edge ordinals, owners, submission origins and (except
  where noted) request lists and ledger fingerprints all match.

## What does not

### Axis 1 — `[walk-failure]` seg7_ehz2

```
comparator cursor 3977 of 3997, mode path=[TITLE_CARD], level ownership changed=true
```

20 recorded rows unconsumed: production ownership left `LEVEL` at tail step 0. **Not an
independent defect** — a consequence of axis 2, since the run leaves the level early when the
end-of-act sequence fires early.

### Axis 2 — `[segment-physics]` segment 11 (`seg7_ehz2`), 236 errors

```
first non-camera mismatch: frame 3525, queue.s2_nemesis_plc.busy  rom=false engine=true
```

**No physical field diverges.** At that frame every position, speed and sub-pixel value
matches. The divergence is the Nemesis PLC queue being busy while the ROM's is idle.

The burst is byte-identical to the ROM's — same `remaining_work` sequence
(94, 91 … 1 → 68, 65 … 2 → 12, 9, 6, 3), same 3-patterns-per-frame service, same 64-row
length. Only the **start row** differs, by 28 rows at session start and less now.

**Root cause.** `loc_3F3A8` (`s2.asm:84935-84942`) gates each random animal spawn on
`move.b (Vint_runcount+3).w,d0 / andi.b #7,d0 / bne`. The engine's counter arrives at segment
entry 6 rows (−2 mod 8) out of phase, firing **one extra spawn**. That consumes an extra
`nextEggPrisonAnimalXOffset` + `nextAnimalArtVariant` draw pair, so every later animal gets a
different position, type and travel direction (`btst #4,(Vint_runcount+3)`,
`s2.asm:24661-24665`). A different animal survives last, so `Obj3E`'s scan at
`loc_3F406` (`:85001-85012`) reaches `Load_EndOfAct` early.

*Not* the animal deletion predicate — that already models the ROM BuildSprites box
(`f9da6563e`), and per-animal lifetimes match the recording (engine 96–144 rows mean ~119;
ROM 95–144 mean ~119).

### Axis 3 — `[dynamic-art-gap]` seg4_ehz1 → seg5_ehz2, 4 fields

```
edge[8]   expected 32921   actual 32920
edge[9]   expected 32922   actual 32921
edge[10]  expected 32929   actual 32928
edge[11]  expected 32930   actual 32929
```

All `movie_logical_frame`, all exactly **one row early**. These are the title-card leave-loop
transfers — two *submitted*, two *completed*, moving as a pair (completion trails submission
by one row in both ROM and engine).

Everything else on this gap is exact, including `edge_count`, and `edge[2]`–`[7]`.
**This gap reported ten mismatching fields at −93 rows at session start.**

**Root cause is known and closable**, but its fix regresses the first interior return: see
[the design note](../designs/2026-08-13-level-entry-seam-frame-costing.md).

### Axis 4 — `[dynamic-art-gap]` ss_4 → seg6_ehz2, 2 fields

```
edge[0]  expected 46347   actual 46348
edge[1]  expected 46347   actual 46348
```

One row **late** — the only defect in the run pointing that direction. These are the
*submitted* pair (`pc 0x1B89A` = `LoadSonicDynPLC` exit, `0x1D1FE` = `LoadTailsDynPLC` exit,
tids 28084/28085). **Their completions at 46349 match exactly.**

**Proven unclosable at frame granularity** — recorded as
[known-discrepancy 28](../../status/known-discrepancies.md). The ROM submits at the leading
`jsr (RunObjects)` (`s2.asm:5003`), and between there and the leave loop's first
`bsr.w WaitForVint` (`:5060-5061`) runs only straight-line 68000 code with no `WaitForVint`
and no polled readiness gate. The masked-frame count in that tail is a pure cycle count
dominated by `BuildSprites`/`AniArt_Load`, whose cost depends on the object set loaded at the
entry position (`:5000`).

Measured across all 27 censused transitions, the harness's `lastNonAdmittedRow` anchor
coincides with the ROM on 20, is one row late on 4, and is 8–47 rows out on 6. Two seams
sharing a destination act have opposite tails, so no structural predicate exists.

### Axis 5 — `[dynamic-art-gap]` ss_5 → seg7_ehz2, ~20 fields

```
edge_count              expected 16     actual 18      ← two SURPLUS transfers
edge[9].mapping_frame   expected 1      actual 105
edge[12].gap_edge_index expected 0      actual 2
edge[16].present        expected false  actual true
edge[17].present        expected false  actual true
```

**Mapping frame 105 = `0x69` = `TailsAni_Balance` frame 0** (`s2.asm:41570`). The engine emits
a Tails Balance animation frame the ROM never produces and submits art for it. That inserts
two extra ledger edges, shifting `gap_edge_index` by 2 and pushing every later edge's mapping
frame, row, requests and fingerprints out of alignment. **The entire cascade is downstream of
one surplus transfer.**

**Root cause.** `Obj01_Init_Continued` (`s2.asm:36206-36216`) seeds all 64 entries of the
position-record ring with `Sonic.x − $20`; `TailsCPU_Normal` reads 16 entries back
(`:39284-39291`). So Tails stands still for ~17 passes after any level init. The ROM spends
that window **before** the recording resumes; the engine spends it **inside** the gap, so it
is still at x=10064 when comparison starts where the ROM is at 10097, and `Tails_Balance`'s
`ChkFloorEdge` / `cmpi.w #$C,d1` test (`:39726-39730`) resolves differently at those two
positions.

---

## Grouping

| axis | fields | shape |
|---|---|---|
| 1 | — | consequence of axis 2 |
| 2 | 236 | mod-8 spawn phase → early end-of-act |
| 3 | 4 | one row early; closable, fix regresses elsewhere |
| 4 | 2 | one row late; **proven unclosable** |
| 5 | ~20 | one surplus Balance transfer, cascading |

Axes 1 and 2 are one defect. Axes 3 and 5 are both *"which physical row inside the seam does a
playable pass land on"*. Axis 4 is the only one with no rule-compliant route.

---

## Working rules followed this session

These are the constraints every round was briefed under, and the reasons they exist. They are
recorded because several were earned by violating them.

### Hard rules (project)

1. **Model ROM state, never the trace.** No branching on zone id, route, frame number or
   "known failing trace". *"ROM-default except at the return"* is still a carve-out.
2. **The bar is any BK2, not this BK2.** A green fixture proves the fixture.
3. **A constant measured off a fixture is a fitted model even when every test passes.**
   Measuring is a legitimate *starting point*; the landed value must be traceable to the ROM
   routine that owns it and cited there. A value close-but-not-equal to the ROM's is usually
   absorbing an error elsewhere — chase that, don't keep the constant.
4. **Trace data is comparison-only.** The hardware-timing port may only release readiness of
   a matching, already-submitted, ROM-backed art job. It may not decide *what* happens, carry
   gameplay values, or key on a frame index.
5. **No game-name or zone carve-outs in shared runtime code** — smallest accurate owner: a
   typed `GameRules` record, or an existing provider/profile/registry.

### Verification discipline

- **Never weaken, relax, widen, add tolerance to, exclude a field from, make advisory, delete
  or `@Disabled` any assertion.** If a test pins the old behaviour, **invert it with equal
  strength and a ROM citation** — the pattern is `sonic2ResultsCompletionRevealsWithoutAFadeToken`
  and the `TestSidekickCpuControllerLevelStart` inversion.
- **Fewer axes is not better.** If an axis disappears, name the field that now matches and
  prove it was *fixed*, not *skipped*. Two false greens this session came from comparator
  starvation.
- **Align gap edges by transfer id, not list index.** Index alignment makes one surplus
  transfer look like many shifted ones.
- **Run both profiles.** One round reported "both profiles" having run the trace numbers twice
  and missed a red unit test.
- **Diff red sets BY NAME**, not by count.
- **A test count below the baseline is truncation, not improvement.**
- **Serialise Maven runs** — concurrent builds share the LWJGL librarypath and manufacture
  ~172 spurious `UnsatisfiedLinkError`s.
- **Label every statement MEASURED / DERIVED / INFERRED.**
- **Adversarial verification defaults to REFUTED**, and reproduces control *and* after.
- **Refuting the brief's premise counts as success.** Across ~39 rounds every brief's central
  premise was refuted, and every refutation was correct.

### Failure modes discovered (the expensive ones)

- **Tautological checks.** A ring-write-vs-CPU-pass count returned 1:1 — but the warm-up path
  emits exactly one ring write per sidekick pass *by construction*, so it could not have
  disagreed. It looked like evidence, carried none, and a hypothesis was built on it.
  **Ask what result would have falsified a check before trusting it.**
- **Published arithmetic errors.** "The engine's follow delay is 16 where the ROM's is 17" was
  written into a design note as an engine defect. `Sonic_RecordPos` increments the ring index
  *after* the write, so the read is last-written − 16 and the engine was already correct.
- **Mislabelled probe output.** A probe printed `liveLeaderX` under a `delayedTargetX` heading
  and a pre-pass inertia as post-pass, producing an apparent `Stand`-with-non-zero-dx that does
  not exist. **A probe's column labels are not measurements.**
- **Approximations that agree often enough to look like models.** The `lastNonAdmittedRow`
  anchor is right on 20 of 27 seams by coincidence.
- **Unreached ≠ passing.** `ss_6` and `ss_7` report no failures only because the chain aborts
  before comparing them; their latent anchor error is 8–47 rows.

### Regression fingerprints

Each identifiable from its error profile alone, so a wrong lever is recognised immediately:

| what was pulled | signature |
|---|---|
| V-int counter bumped | 122,139 errors; segment 7 frame 524; `sidekick_y` |
| pass deleted not moved | 3 axes; segment 2 = 47,639; `sidekick_y` frame 1132 |
| engine-side release pass | 4 axes; segment 2 = 50,679/50,811; frame 0; `dynamic_art.edges rom=[] engine=[0]` |
| leave-loop fall-through alone | 12 axes; segment 11 = 7,104 |
| ownership released early | "lost production ownership before source closure"; cursor 38806; 2 axes |
| census walk alone | 3 axes; segment 2 = 58,355; frame 1; `sidekick_x` 0x0DDE vs 0x0DDD |
| walk + fall-through | segment 2 = 47,802; frame 0; `sidekick_x` 0x0DDD vs 0x0DDB |
| walk budget wrong | `IllegalArgument rowsConsumed must be 0 or 1` |
| comparison shifted one row | segment 2 = 99,105; player breaks at frame 15 |

### Explanations measured and refuted (do not re-run)

For the interior-return residual: an extra entry pass (both paths run 26); a follow-direction
flip (no `FollowLeft` exists in the seam); ring refill parity (strictly 1:1 at all seven
entries); a 16-vs-17 lookback (arithmetic error, engine correct); boundary adoption (a one-row
shift nearly doubles the residual); the sidekick running fewer passes (4235:4235 exactly); and
`Stand` with non-zero dx (probe artifact).

For the clock: the act-advance code-path hypothesis; event-count factoring of the
special-stage deficit; the "invented 78-frame constant" reading; the bonus-tally explanation
of seam variance.

---

## Fixes landed this session

All ROM-cited, each independently verified, none introducing a fitted constant.

| commit | fix |
|---|---|
| `edc396f5e` | `Pal_FadeToBlack`'s 22 counted V-blanks spent at the boundary |
| `8695c029e` | `LoadZoneTiles` spends one V-blank per `$1000`-byte DMA chunk |
| `a04774b31` | admission census + lag-row replay |
| `0bc7cc4be` | removed a 21-frame reveal fade the ROM never performs |
| `cd0d893a1` | player art submitted at `InitPlayers` |
| `4d51aa04f` | bonus tally drains two countdowns, finishing with the longer |
| `609361da8` | pre-level fade ordered before the title card (87 → 109 rows) |
| `2a1f9a270` | `TailsCPU_Init` returns instead of falling through to steering |
| `bad614ae6` | corrected `SS_Ctrl_Record_Buf` word expectation |
| `7c7aa5901` | corrected a wrong ROM claim in the leave-loop javadoc |

**Net:** the dominant gap went from ten mismatching fields at −93 rows to four at −1.
Segment 11: 287 → 236 errors.

---

## Open decision

The chain cannot go green while axis 4 is honestly reported. Two options, both requiring
explicit authorisation:

1. **Excuse `movie_logical_frame`** at seams where the submission row falls inside a masked
   span. Chain goes green; adds a second instance of the papering-over this session found in
   `TestS1GhzMazeRoundTripChain` (:29-70), which is probably hiding the same class of defect.
2. **Cycle-level modelling** of `BuildSprites`/`AniArt_Load`. Closes it properly; is a partial
   68000 cycle emulator, and collides with the S3K vertical-slice priority in `CLAUDE.md`.
