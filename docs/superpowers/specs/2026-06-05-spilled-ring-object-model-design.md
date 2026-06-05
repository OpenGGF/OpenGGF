# Spilled-Ring Object Model (Obj37 in the unified touch loop) — Design

**Status:** Design approved (architecture). Pending spec review → implementation plan.
**Date:** 2026-06-05
**Driver:** Trace `TestS2Mtz2LevelSelectTraceReplay` f641 (Tails hurt ~5 frames early) and a class of
hurt/touch-ordering traces. The engine's spilled (lost) rings live outside the unified object touch
loop and update their physics at the wrong frame point, so ROM's slot-order "break on first
overlapping object" — where a lower-slot spilled ring suppresses a later hazard — is not reproduced.

---

## Problem

In ROM, scattered rings (Obj37, `collision_flags = $47`) are **real objects in `Dynamic_Object_RAM`**.
`TouchResponse` (docs/s2disasm/s2.asm:84956-85046) checks placed/level rings first (`Touch_Rings`),
then runs `Touch_Loop` over the object RAM **in slot order, breaking on the first overlapping object**
(`Touch_CheckCollision` → `Touch_ChkValue` → `rts`). `Touch_ChkValue` (s2.asm:85196-85219) routes
`collision_flags & $C0` in `$40–$7F` (size `!= $46`) to the ring/collectible branch: if the toucher is
not invulnerable (`invulnerable_time < 90`) it collects the ring (`move.b #4,routine(a1)`,
`move.w a0,parent(a1)`) and `rts` — and either way the overlap **ends the loop**, so any
higher-slot hazard (spikes, badnik claw) is **not** reached that frame.

The engine diverges in **two** ways:

1. **Outside the touch loop.** Spilled rings live in `RingManager.LostRingPool`
   (src/main/java/com/openggf/level/rings/RingManager.java:1105-1523), collided by a separate
   `checkCollection()` (RingManager.java:1289-1319). The unified loop
   `ObjectManager.TouchResponses.processCollisionLoop` (ObjectManager.java:5284-5424, break at 5422)
   never sees them, so the break-on-first-overlap cannot account for a lower-slot spilled ring.
2. **Wrong frame point.** Lost-ring physics runs in `LevelManager.update()` (step 7 of
   `LevelFrameStep`), **after** player physics + touch responses — whereas ROM Obj37 executes in the
   object loop (step 2/3). So lost-ring positions drift ~1 frame from ROM.

A prior focused fix that injected a touch-loop break (without fixing #2) advanced mtz2 f641→f646 but
**regressed mcz1** f2757→f1665: the drifted lost-ring position reported an overlap where ROM had none,
so the (correct) break wrongly suppressed a real hurt. Fixing the order **requires** also fixing the
position timing — which is why this design adopts the full ROM-object model.

---

## Goal / non-goals

**Goal.** Model spilled rings as real ROM Obj37 objects that (a) execute their physics in the object
loop at the ROM frame point and (b) participate in the unified slot-ordered touch loop with ROM
`Touch_ChkValue` semantics (collect-if-eligible, then break). Result: a lower-slot spilled ring
suppresses a later hazard exactly as ROM does, with frame-accurate positions, across S1/S2/S3K.

**Success criteria.**
- mtz2 advances past f641 (Tails hurt timing matches ROM).
- **mcz1 does NOT regress** (the position-drift misfire is gone — the whole point).
- No green trace regresses (S1 ×10, S2 ehz1/scz/wfz); no S2/S3K frontier moves backward.
- Lost-ring rewind and rendering remain correct.

**Non-goals.**
- Placed/level rings stay on their separate `Touch_Rings` bitmap path (RingManager `collectStageRings`
  / S1 Obj25 touch path) — **unchanged**. This design touches **only spilled Obj37 rings**.
- No new gameplay behavior; comparison-only (no engine state hydrated from trace data).
- No zone/route/frame/gameId carve-outs — per-game differences gated on `PhysicsFeatureSet`.

---

## Architecture

Retire the parallel `LostRingPool` lifecycle (deferred physics + separate collection + bespoke rewind)
in favor of a real **`LostRingObjectInstance`** (Obj37):

- Lives in the object **slot table + `execOrder`** (it already allocates dynamic slots today via
  `allocateSlotAfter`, ObjectManager — now it also *executes* in those slots).
- **Executes its physics in the object loop** (`LevelFrameStep` step 2/3), at the ROM frame point —
  removing the deferred `LevelManager.update()` physics call.
- **Participates in the unified touch loop** in slot order via `collision_flags = $47`; the loop's
  ring-category handler reproduces `Touch_ChkValue`.

`LostRingPool` slims to a **spawner + render registry**: `spawnLostRings()` allocates slots (as today)
and spawns `LostRingObjectInstance`s through the normal object-spawn path, and exposes the live ring
set for the renderer. Bespoke physics/collection/rewind are removed.

### Components

| Component | Responsibility | Notes / refs |
|---|---|---|
| `LostRingObjectInstance` (new) | ROM Obj37: position/velocity/`lifetime`/`sparkleStartFrame`/`phaseOffset`; `updateMovement()` = bounce physics (gravity, per-game floor cadence, lifetime countdown, off-bottom delete); `collision_flags=$47`; `appendRenderCommands`. | Mirrors current `LostRing.java` state + `LostRingPool.updatePhysics` math, relocated into an object. ROM Obj37 / `Obj_LostRings`. |
| `ObjectManager.TouchResponses` ring handler | In `processCollisionLoop`, for `collision_flags & $C0` in `$40–$7F` and size `!= $46`: collect-if-not-invulnerable (`invulnerable_time ≥ 90` skip-collection gate), then **break**. | Models `Touch_ChkValue` ring branch, s2.asm:85196-85219. Shared across players/sidekick. |
| `RingManager` / `LostRingPool` (slimmed) | `spawnLostRings()` → allocate slots + spawn instances; hold live-ring registry for `RingRenderer`. Remove `updatePhysics`, `checkCollection`, bespoke rewind snapshot. | RingManager.java:1105-1523. |
| `RingRenderer` | Reads the live `LostRingObjectInstance` set (or each ring self-renders via `appendRenderCommands`), keeping cached pattern rendering. | RingManager `RingRenderer`. |

### Frame ordering (before → after)

```
BEFORE                                        AFTER
2/3. object exec loop (no lost rings)         2/3. object exec loop INCLUDES LostRingObjectInstance
     player physics + unified touch loop           player physics + unified touch loop now sees
        (lost rings absent)                            lost rings in slot order (collect+break)
7.   LevelManager.update():                   7.   LevelManager.update(): lost-ring physics REMOVED
        LostRingPool.updatePhysics()  <-- drift          (physics now ran in the object loop, on time)
        checkCollection() (separate)
```

### Touch handler semantics (ROM `Touch_ChkValue`, s2.asm:85196-85219)

On overlap with a spilled ring in slot order:
1. `collision_flags & $C0` selects the collectible branch ($40–$7F); size byte `$47 & $3F = $07`
   (size `$46` is a monitor, not a ring — excluded).
2. If `invulnerable_time ≥ 90` (MainCharacter's, or the toucher's own when MainCharacter is the
   toucher): **skip collection**.
3. Else: collect — mark the ring collected (ROM `routine(a1)=4` sparkle), credit a ring, play SFX,
   set `parent` to the toucher.
4. **Either way: break the touch loop** (the ring was the first overlapping object). Later-slot
   hazards are not reached this frame.

Self-collection of just-spilled rings is prevented as in ROM by the freshly-spilled state (the engine's
existing initial animation-counter / lifetime gate that already blocks same-frame re-pickup).

---

## Cross-game (S1 / S2 / S3K)

Obj37 exists in all three games. Differences become Obj37 object behavior gated on `PhysicsFeatureSet`,
never `gameId`:

- **Floor-check cadence:** S1 every 4 frames (mask `#3`), S2/S3K every 8 (mask `#7`) — already a
  per-game value in `LostRingPool`; relocate as object data / a feature value.
- **S3K-only:** reverse-gravity rings and lightning-shield attraction — preserved as Obj37 behavior
  gated on existing S3K feature flags.
- The touch handler + invuln gate are ROM-identical across games (`Touch_ChkValue`), so shared.
- **Capacity:** ROM caps spilled rings at `0x20` (32). The engine must mirror this cap and ensure 32
  ring-objects + other dynamic objects do not exhaust the per-game dynamic slot pool
  (`ObjectSlotLayout`: S1 first=32/count=96, S2 16/112, S3K 4/89). Spawn must cap at the ROM limit and
  degrade gracefully (no allocation failure).

---

## Rewind

Because spilled rings become real slot objects, the **generic object rewind**
(`GenericFieldCapturer` / compact schema codecs + stable identity ids in
`com.openggf.game.rewind.identity`) captures them automatically. The bespoke `LostRingPool` rewind
snapshot (RingManager.java rewind paths) is **retired**. Requirements:
- `LostRingObjectInstance` exposes a **stable identity id** so rewind seek/replay is deterministic
  across the spawn/despawn of 32 transient rings.
- All ring scalar fields have codecs (or are `@RewindTransient`/`@RewindDeferred` as appropriate) so
  default compact capture applies, consistent with other dynamic objects.
- A rewind torture/round-trip test covers spilling rings, seeking across the spill, and replaying.

---

## Edge cases & error handling

- **Slot exhaustion:** cap spilled-ring spawns at the ROM `0x20` limit; if the dynamic pool is full,
  spawn fewer (ROM behavior under slot pressure) — never throw. Log if a cap truncates.
- **Lifetime / off-bottom delete:** ring deletes at `lifetime ≤ 0` or below the camera bottom (ROM
  Obj37 delete), through the normal object delete path (`SlotAllocator.release`).
- **Collected state:** a collected ring plays the sparkle then deletes; it must not re-trigger the
  touch break after collection.
- **Invulnerability:** overlap during invuln breaks the loop but does not collect (modeled exactly).
- **Determinism:** ring spawn velocities/phase are derived deterministically (no `Math.random`);
  preserve the current angle-based scatter math.

---

## Testing

1. **Unit — physics frame-exactness:** `LostRingObjectInstance` bounce/gravity/floor-cadence/lifetime
   match ROM Obj37 step-for-step (per game), ported from the current `LostRingPool.updatePhysics`
   assertions.
2. **Unit — ordering invariant (the mtz2 mechanism):** with a spilled ring at a *lower* slot than a
   hazard, both overlapping the player, the touch loop collects the ring and **breaks**, so the hazard
   does not fire; with the ring at a *higher* slot, the hazard fires first. Direct, ROM-cited.
3. **Unit — invuln gate:** overlap during `invulnerable_time ≥ 90` breaks without collecting.
4. **Occupancy oracle:** spilled rings now occupy slots; `TestS2ObjectOccupancyOracle` should see
   Obj37 in slots consistent with ROM (comparison-only).
5. **Rewind:** torture/round-trip across a spill.
6. **Trace:** mtz2 advances; **mcz1 does not regress**; greens stay green; full single-fork S1/S2/S3K
   `*TraceReplay` sweep shows no frontier moves backward.

---

## Risks

- **Blast radius:** physics, collection, rewind, and rendering all move from the pool into the object
  model. Mitigated by TDD (each piece behind a test before cutover) and the occupancy-oracle + trace
  guards. Stage the migration so the old pool path is removed only after the object path is green.
- **Slot pressure:** 32 ring-objects compete for the dynamic pool with other objects; mitigated by the
  ROM cap and graceful truncation, verified by a capacity test.
- **Determinism for rewind identity** across 32 transient rings — addressed by stable identity ids.

---

## Out of scope (follow-ups)

- Placed/level ring collection paths (`Touch_Rings` / `collectStageRings` / S1 Obj25) — unchanged.
- Any sidekick-CPU steering work (separate effort; the sidekick touch/bounce infrastructure is already
  ROM-correct per the 2026-06-05 investigation).
