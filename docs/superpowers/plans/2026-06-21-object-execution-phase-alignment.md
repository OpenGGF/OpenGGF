# Implementation Plan — Object-Execution-Phase Alignment (S1/S2/S3K trace parity)

**Status:** Proposed
**Author:** AI (trace-cluster-fixes worktree)
**Date:** 2026-06-21
**Branch base:** `develop`
**Scope:** Close the dominant root behind the 53 `*TraceReplay` frontier failures.

---

## 1. Problem statement

A full `*TraceReplay` sweep (90 tests, `-Dtrace.frontierOnly=true`) shows **53 failing
traces**. The true per-trace frontier table is in
[`docs/TRACE_FRONTIER_LOG.md`](../../TRACE_FRONTIER_LOG.md) (2026-06-21 "FULL SWEEP"
entry). Eleven roots were investigated this session; **every one converges on a single
systemic cause:**

> The engine's object-execution **order** and per-object **player-position-sampling
> timing** diverge from ROM's `ExecuteObjects` by one frame / one sub-step. Object↔player
> contact, landing, launch, push, and ride decisions are therefore evaluated at the
> wrong phase, producing missed launches, one-frame-early contacts/landings,
> hypersensitive bounces, wrong air/ground state, and downstream camera/slot effects.

This is **not** fixable trace-by-trace with isolated edits. Six measured fix→sweep→revert
cycles this session all reverted (each regressed, was neutral, or was coupled). The
fix must align the execution model itself, **per object family**, instrumented and
gated.

### 1.1 What does NOT work (proven this session — do not retread)

| Attempt | Result | Lesson |
|---|---|---|
| OOZ popping-platform `usesInclusiveRightEdge=true` | net-neg (OOZ-LS 1782→1251) | shared side-contact boundary; exposed a corner ride-vs-push coupling |
| MHZ mushroom `usesStickyContactBuffer=false` | neutral | riding not from sticky; it's the landing-band phase |
| GHZ collapsing-ledge heightmap `$30→$2F` | net-neg (GHZ3 1246→564) | heightmap is coupled to the index-range geometry (ROM reads adjacent memory) |
| Horizontal-spring defer-launch to established-push | **net-neg (+4: AIZ/HCZ/ICZ)** | **spring timing is NOT uniform — no blanket phase shift** |
| GHZ penetration landing (earlier campaign) | net-neg (MZ1) | landing threshold is object/context specific |

**Hard rules carried into this plan:** no blanket phase shifts; no zone/route/frame
carve-outs; model the ROM routine each object actually runs; gate every change on the
full sweep **and** `TestSidekickCpuFollowParity`; revert anything net-negative.

---

## 2. Root: engine vs ROM execution model

### ROM (`ExecuteObjects`)
Single slot-ordered pass per frame. The player object (slot 0/1) runs first and fully
moves + collides; later object slots then run, each calling its `SolidObject` /
`Touch_Response` / launch routine against the player's **current (post-move) state**,
sampled once. Status bits set by an object's `SolidObject` this frame are read by
*later* code (and by the next frame's reads), giving ROM its characteristic
"set status this frame, act on it next frame" cadence for some objects (e.g. springs
read an *established* `Status_Push`), while others act immediately.

### Engine (`LevelFrameStep.execute`, inline path)
Two-phase: (1) `prepareTouchResponseSnapshots` → player physics (`spriteUpdate`,
which moves the player and runs `applyTouchResponses`) → (2) object pass
(`updateObjectPositionsPostPhysicsWithoutTouches`, with inline solid checkpoints via
`ObjectManager.executeObjectWithSolidContext` → `ObjectSolidContactController`). Solid
contacts resolve in pass (2) against post-physics player position
(`getCentreX/Y`), with per-object compensations already bolted on
(`getTopSolidPlayerPositionHistoryFrames`, `prePhysicsCentre`, sticky buffer,
`usesInclusiveRightEdge`, `touchResponseUsesPreviousCollisionResponseList`).

### The gap
The two-phase split + per-object compensations approximate ROM but are **one frame /
one sub-step off in family-specific ways**. The compensations are ad-hoc; there is no
single per-family model of "at what phase does this object sample the player, and on
which frame does its response fire." This plan introduces that model.

---

## 3. Design

### 3.1 Central abstraction: a per-family contact-evaluation phase
Extend the existing `SolidRoutineProfile` / object-profile system (do **not** invent a
new registry) with an explicit, ROM-cited **contact-evaluation phase** per object
family, capturing:

- **Position sample phase** — which player position the object's contact/landing/launch
  reads: `CURRENT` (post-move), `FRAME_START` (pre-physics; cf. CPZ2 tube fix
  `getPrePhysicsCentreX/Y`), or `HISTORY(n)` (`getCentreX/Y(n)`; already used by S3K
  `SolidObjectTop` new-landing via `getTopSolidPlayerPositionHistoryFrames`).
- **Response-trigger condition** — fires on `FIRST_CONTACT` vs `ESTABLISHED_CONTACT`
  (contact persisted ≥1 frame / `Status_Push` set last frame). Springs differ here
  per object — this MUST be per-instance from the ROM routine, never blanket.
- **Object-position sample phase** — for moving objects (bumper orbit, platforms),
  whether the contact reads the object's pre- or post-update position this frame.

Each value is set from the object's ROM routine with a disasm citation, exactly like
existing `PhysicsFeatureSet` / `SolidRoutineProfile` flags. Default = current engine
behaviour (so unconverted objects are unchanged).

### 3.2 Validation harness (build first — Phase 0)
Per-family work needs the ROM phase measured, not guessed. Build a reusable diff harness:

- **Engine side:** a guarded (`OGGF_PHASE_DEBUG`) dump in
  `ObjectManager.executeObjectWithSolidContext` / `ObjectSolidContactController` that
  logs, for a target frame window: object id/slot, object pre/post-update position,
  the player position sampled, the contact decision, and the resulting player velocity.
- **ROM side:** BizHawk Lua hooks on the relevant routines (`SolidObject`,
  `sub_32F56` bumper bounce, `Obj_Spring`, `SlopeObject`, the spring/launch sites),
  capturing the object's `x_pos/y_pos`, the player's `x_pos/y_pos`, and the resulting
  `x_vel/y_vel` at the same trace frame. Reuse the existing `tools/bizhawk/diag_*.lua`
  pattern (`diag_s2_ooz_pform.lua` is the template; S3K needs object-offset + S&K-half
  address resolution via `RomOffsetFinder`).
- **Output:** a side-by-side "engine sampled X at phase P → decision D" vs "ROM sampled
  X → decision D" so the phase delta is unambiguous before any code change.

This is comparison-only diagnostics (honours the trace comparison-only invariant).

### 3.3 Per-change protocol (every step)
1. Pick the earliest frontier trace in the family.
2. Harness-measure the engine-vs-ROM phase delta at that single frame (bisect).
3. Set the per-family phase value (ROM-cited) on that object family only.
4. Run the **full** `*TraceReplay` sweep (`-Dtrace.frontierOnly=true`) + capture the
   per-trace frontier table; diff vs baseline.
5. Run `TestSidekickCpuFollowParity` and the must-keep-green S3K tests.
6. **Keep iff net-positive** (≥1 frontier cleared/advanced, **0 regressions**).
   Otherwise revert and record why in `docs/TRACE_FRONTIER_LOG.md`.
7. Commit scoped, with trailers; update the frontier log (cleared/advanced/regressed).

Baseline frontier table to diff against: the 2026-06-21 full-sweep entry (53 failures).

---

## 4. Phased implementation (family by family, by leverage)

Order chosen so the most-shared, best-understood families come first and each phase's
gating de-risks the next.

### Phase 0 — Harness + baseline (no engine behaviour change)
- Build the engine dump + ROM Lua hooks (§3.2).
- Re-confirm the 53-failure baseline; snapshot the frontier table.
- **Exit:** harness produces a clean engine-vs-ROM phase diff for one trace
  (use OOZ2 f1070 or CNZ short f291 as the smoke test).

### Phase 1 — Solid-object **landing** phase (largest, best-characterised)
- **Targets:** OOZ2-LS f1070 (`air 0/1` on popping platform), MHZ-CR f72 (rolls off
  mushroom vs engine lands/rides), GHZ1/GHZ2-CR f2573/f2369 (`y_speed` large→0),
  S3K LBZ-CR f1694 (`air`).
- **ROM refs:** `SolidObjectTop` / `SolidObject_Landed` (s2.asm:35368-35387),
  S3K `SolidObjectFull2_1P` new-landing reads pre-`RideObject_SetRide`
  (sonic3k.asm:41982-42015), S1 `Plat_NoXCheck_AltY` / `SlopeObject` (sonic.lst gates
  0x7AF2-0x7B24). Note: GHZ ledge needs the heightmap **and** index-range modelled
  together (ROM reads adjacent memory for outer indices — see 2026-06-21 cycle-4 entry).
- **Approach:** introduce the landing position-sample phase value; set per family from
  ROM. Do **not** touch the heightmap alone (proven net-neg).
- **Exit:** ≥1 of the landing frontiers cleared/advanced, 0 regressions.

### Phase 2 — Side-contact / push phase
- **Targets:** OOZ-LS f1782 (tails push, ROM-captured: push@f1781, engine 1 frame
  late), the slot/contact onesies that depend on side-contact (ARZ2 f523 slot via
  contact order).
- **ROM refs:** `SolidObject_cont` (`bhi` inclusive right edge), `SolidObject_LeftRight`
  side-vs-top decision (sonic3k.asm GetArcTan d5/d1 ordering). The inclusive-edge change
  must be **paired** with corner ride-vs-push parity (proven: edge alone regresses
  OOZ-LS to f1251).
- **Approach:** model side-contact registration + corner resolution together as one
  per-family change; gate.

### Phase 3 — Launch phase (springs, bumpers, fans)
- **Targets:** CNZ short f291 (bumper orbit-vs-bounce timing, hypersensitive),
  CNZ-CR f1846 (horizontal spring launch timing + Tails interaction), the missed-launch
  sign-flips MZ2 f2578 / HTZ2 f1078 (ROM launches up, engine falls).
- **ROM refs:** `Obj_Spring` launch gating, `sub_32F56` bumper bounce + `loc_32E7E`
  orbit, the per-object launch trigger.
- **Critical:** spring timing is **per-object non-uniform** (AIZ/HCZ/ICZ launch
  immediately; deferring all regressed +4). Each spring/launcher's trigger condition
  must come from its own ROM routine. The bumper case is orbit-position-vs-bounce
  timing (object-position sample phase, §3.1).
- **Approach:** per-object trigger + object-position sample phase; gate hard (this phase
  has the highest regression risk — see the +4).

### Phase 4 — Sidekick CPU follow/contact phase
- **Targets:** tails_x onesies (CPZ f3365, CPZ2 f2889, MCZ2 f4485, CNZ2 f4418,
  MTZ3 f1973), CNZ-CR f1846 (CPU steering + spring).
- **ROM refs:** `TailsCPU`/`loc_13DD0`-`loc_13E64` (sonic3k.asm:26690-26743),
  `Status_Push` btst gating. This lives in the fragile `SidekickCpuController`.
- **Mandatory gate:** `TestSidekickCpuFollowParity` (79 tests; NOT in must-keep-green,
  has merged regressions before — see memory `sidekick-cpu-validation-gap`) on every
  change, plus `TestArchUnitRules`.
- **Approach:** most fragile; do last, smallest steps, heaviest gating.

### Phase 5 — Residual onesies + trace hygiene
- **Targets:** sub-pixel position onesies (Ghz3 f1246, Mz3 f1702, Syz3 f3468, Hcz f1489,
  Aiz x_sub f1095), camera_y residue (LZ1 f5745) once their upstream air/landing cause
  is fixed by Phases 1-4.
- **Trace hygiene:** audit complete-run traces for recorder/input-alignment desyncs
  lurking past the physics frontier (e.g. MGZ-CR f33271 BK2-vs-physics.csv). Regenerate
  bad recordings via `tools/bizhawk/record_*` (a trace-data fix, not an engine fix).

---

## 5. Guardrails & invariants

- **Comparison-only:** trace data is read-only diagnostic input; never hydrate engine
  state from the trace in committed code (trace-replay-bug-fixing skill).
- **Cross-game parity:** any change to shared physics/collision/sidekick code must check
  all three disassemblies; per-game divergence → `PhysicsFeatureSet`/profile flag, never
  `if (gameId == ...)`. No zone/route/frame carve-outs.
- **Net-positive rule:** keep a change only if the full sweep shows it net-positive
  (cleared/advanced with zero regressions). An "artificial" frontier bump that breaks on
  a later root must be reverted, not shipped.
- **Guards before any push:** `TestArchUnitRules`, `TestRewindCoverageGuard`,
  `TestNoDirectMapMutationsInGameplay`, `TestNoServicesInObjectConstructors`,
  `TestObjectServicesMigrationGuard`, and the must-keep-green S3K set
  (`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`,
  `TestSonic3kDecodingUtils`).
- **Frontier log:** update `docs/TRACE_FRONTIER_LOG.md` on every frontier move / full
  sweep (command, commit/worktree, pass/fail, error count, first-error frame/field).
- **Workspace:** scoped commits; this is a shared tree (never `git add -A`); verify own
  changes in isolation.

## 6. Risks

| Risk | Mitigation |
|---|---|
| Shared-code change regresses green traces | Per-family default-off phase flag; full-sweep gate every step; revert net-neg |
| Spring/launch non-uniformity (the +4) | Per-object ROM-cited trigger, never blanket; Phase 3 gated hardest |
| Sidekick controller fragility | Phase 4 last; `TestSidekickCpuFollowParity` + ArchUnit on every change |
| Hypersensitive contacts (bumper near-center) | Some frontiers may be sub-pixel-irreducible; accept "advanced" not "cleared", document, move on |
| Coupled multi-part roots (GHZ heightmap+index) | Model both halves in one change; never land one half |

## 7. Milestones / definition of done
- **M0:** harness operational, baseline re-confirmed (53).
- **M1:** landing family — ≥3 frontiers cleared/advanced, 0 regressions.
- **M2:** side-contact family — OOZ-LS + ARZ2 advanced.
- **M3:** launch family — ≥2 of {CNZ bumper, CNZ-CR spring, MZ2/HTZ2} advanced.
- **M4:** sidekick family — ≥3 tails onesies cleared, parity suite green.
- **M5:** residual onesies + trace-hygiene pass; final full sweep; fail count materially
  below 53 with zero regressions and all guards green.

Each milestone is independently shippable (scoped commits, frontier log updated) and
must leave the suite no worse than it found it.
