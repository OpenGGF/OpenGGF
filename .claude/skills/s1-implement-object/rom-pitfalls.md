# Sonic 1 Object Implementation — ROM Behavioural Pitfalls

Anti-pattern catalogue accumulated from trace replay frontier advancement
fixes. **Read this before starting Phase 2 of `s1-implement-object/SKILL.md`**
and check each entry against the ROM behaviour of the object you're porting.

Each entry is a class of bug where the *naive* engine port diverges from ROM
in a way that survives unit tests but breaks trace replay parity. The
catalogue grows when a trace-replay-bug-fixing iteration commits an object
fix whose root cause could occur in any not-yet-implemented object — see
`.agents/skills/trace-replay-bug-fixing/SKILL.md` Phase 5 for the update
loop.

Format per entry: pattern name, symptom, root cause, what to check during
implementation, ROM citation, originating fix commit.

---

## P1 — CalcSine orbital position uses integer arithmetic shift, not floating-point round

**Symptom.** A satellite or orbiting child object is 1 px off its ROM
position in one or both axes, causing an off-by-one touch contact (premature
hurt, missed bounce) compared to ROM. The discrepancy is only visible at
certain orbit angles.

**Root cause.** ROM `Orb_CircleSpikeball` (and any object that calls
`CalcSine` to drive orbital/circular motion) does:

```asm
jsr CalcSine        ; d0 = sine (-0x100..0x100), d1 = cosine
asr.w #4,d1        ; cosine >> 4  (arithmetic right shift, truncates)
add.w obX(a1),d1   ; parent X + (cosine >> 4)
asr.w #4,d0        ; sine >> 4
add.w obY(a1),d0   ; parent Y + (sine >> 4)
```

Naive engine port uses `(int) Math.round(Math.cos(radians) * 16.0)`.
`CalcSine` sine=254 → `254 >> 4 = 15`; floating-point `Math.round(254/256.0 * 16) = Math.round(15.875) = 16`.
The 1-unit rounding difference places the child 1 px further from the parent
than ROM, which can make touch boxes meet when they should miss (or miss when
they should meet).

**What to check.** Any circular/orbital motion driven by `CalcSine`:
use `TrigLookupTable.sinHex(angle) >> 4` and
`TrigLookupTable.cosHex(angle) >> 4` for the positional components.
Do not use `Math.sin`/`Math.cos` with `Math.round` for ROM-accurate placement.

**ROM citation.** `docs/s1disasm/_incObj/60 Badnik - Orbinaut.asm:181-191`
(`Orb_CircleSpikeball`). `CalcSine` defined at
`docs/s1disasm/_incObj/sub CalcSine.asm`.

**Cross-game note.** S2 and S3K share the same `CalcSine` convention and the
same `asr.w #4` radius convention for circular child objects. Apply the same
`TrigLookupTable.sinHex/cosHex >> 4` pattern for any circular object in those
games.

**Originating commit.** See `bugfix/ai-s1-syz-slz-advance`
(SLZ2 f1016 -> f1493; Orbinaut spike placed 1px low by float round, touching
player touch-box top edge 4 frames before ROM's contact).

---

## P2 — Landing-frame standing bit: read it AFTER the solid-contact checkpoint, not before

**Symptom.** An object behaviour gated on "the player *just* started standing
on me this frame" fires one frame late: a fall/collapse/launch timer starts a
frame late, the object moves a frame late, and a rider is held ~1 px off ROM
for the divergence window.

**Root cause.** ROM resolves solid contact and the object's reaction to that
contact **within the same frame**. `Plat_Solid` (routine 2) calls
`PlatformObject`, which sets the standing bit (`bset #3,obStatus`) and bumps
the routine, then *falls through* to `Plat_Action` → the subtype handler,
which reads that **just-set** standing bit on the same frame (e.g. `.type03`
writes `move.w #30,objoff_3A` to start the fall timer). A naive engine port
runs its per-object "react to standing" logic (`moveFallOnStand()`) **before**
`checkpointAll()` commits the new standing state, so it reads the *previous*
frame's `playerStanding=false` and misses the same-frame trigger.

**What to check.** Any object routine that reacts to "player is now standing
on me" on the contact frame (collapse timers, fall delays, conveyor engage,
switch trip) must read the standing/push flag **after** the solid-contact
checkpoint inside `update()`, not from the pre-checkpoint snapshot. Add a
post-`checkpointAll()` guard keyed on the freshly-committed standing bit.

**ROM citation.** `docs/s1disasm/_incObj/18 Platforms.asm:201-216`
(`Plat_Action` `.type03` reads the standing bit set by `PlatformObject` in
`Plat_Solid` the same frame).

**Cross-game note.** S2 and S3K `SolidObject`/`PlatformObject` set the standing
bit during the same-frame solid pass that precedes the object's action handler;
the same "read standing after the checkpoint" rule applies.

**Originating commit.** See `bugfix/ai-s1-ghz1-advance` (GHZ1 f3246 -> GREEN;
type-03 platform fall timer started one frame late, holding Sonic 1 px high).

---

## P3 — Triggered countdown timer: ROM does not decrement on the same frame it sets the timer

**Symptom.** A timer-driven state machine transitions (or a child spawns) exactly
1 frame earlier than ROM. The timer value matches ROM when observed right after the
trigger event, but the transition fires too soon.

**Root cause.** ROM timer-setting code branches to `locret_XXXX` (rts) immediately
after writing the timer value — the decrement path is only entered on the *following*
frame when the timer is already non-zero. Naive engine code does:

```java
if (timer == 0) {
    if (contact) { timer = DELAY; }  // set
}
if (timer > 0) { timer--; /* ... */ }  // BUG: decrement fires on same frame
```

That collapses ROM's two-frame path into one, advancing every subsequent timer tick
by 1.

**What to check.** Whenever a ROM object reads a timer with `tst.w`/`beq.s` and
jumps to a common decrement label (`loc_10FC0`, etc.) only when non-zero: the `beq`
means "skip decrement if zero, fall through to rts". The set path (`move.w #N, timer`)
must `return` immediately in the engine — do NOT also run the decrement block in the
same `update()` call.

**ROM citation.** `docs/s1disasm/_incObj/5B SLZ Staircase.asm:104-119`
(`Stair_Type00`): timer check / set / rts, then the decrement at `loc_10FC0` is only
reached when entering non-zero. Same pattern in `Stair_Type02` at asm:122-137.
Equivalent bomb-fuse pattern: `docs/s1disasm/_incObj/3B Bomb.asm` (SLB bomb fuse
`skipsSameFrameUpdateAfterSpawn`; commit `bugfix/ai-slz1-advance`).

**Cross-game note.** The same tst/beq-over-decrement pattern appears in S2 and S3K
timer-driven objects. Verify every new object's timer-set path falls through to rts
*without* running the decrement in the same dispatch.

**Originating commit.** `bugfix/ai-s1-slz1-staircase`
(SLZ1 f933 → f2872; Staircase timer decremented 1 tick early on trigger frame).

---

## P4 — SolidObject Y contact window uses airHalfHeight (d2), not groundHalfHeight (d3)

**Symptom.** When a grounded player rides a multi-piece solid object and then walks
onto an adjacent piece, the fresh-landing snap seats the player 1px too low (engine
`newCentreY = ROM_y - 1`). The error appears on the piece-transition frame and every
fresh-landing frame where the player is grounded.

**Root cause.** ROM `SolidObject_cont` / `Solid_ChkCollision` always uses d2 (the
input top half-height, i.e. `airHalfHeight`) for the Y bounding-box calculation:

```asm
move.b  obHeight(a1), d3   ; d3 = Sonic's y_radius
ext.w   d3
add.w   d3, d2             ; d2 = airHalfHeight + y_radius  (maxTop)
```

The engine's `processMultiPieceCollision` was using `groundHalfHeight` for grounded
players, inflating `maxTop` by 1. That makes `distY = relY = 3` in ROM become
`distY = 4` in the engine, and the snap formula `newCentreY = playerCentreY - distY + 3`
produces `- 4 + 3 = -1` offset rather than `-3 + 3 = 0`.

The `groundHalfHeight` (d3) is only used by `MvSonicOnPtfm` / `MvSonicOnPtfm2` for
the *continued-ride re-seat*, not for the initial bounding-box Y test.

**What to check.** For any `MultiPieceSolidProvider` object, `processMultiPieceCollision`
uses `params.airHalfHeight()` for the Y window. This is handled generically in the
engine — no per-object override needed. But if you are implementing a new
multi-piece object and observe a 1px grounded seat error on piece transitions, confirm
that `SolidObjectParams.airHalfHeight()` carries the correct top half-height from
the ROM `SolidObject` call site's d2 value.

**ROM citation.** `docs/s1disasm/_incObj/sub SolidObject.asm:170-176`
(S1 `Solid_ChkCollision` Y window: `add.w d3,d2` where d2=airHalfHeight).
`docs/s2disasm/s2.asm:35361-35373` (S2 `SolidObject_cont` Y window: same convention).

**Cross-game note.** All three games (S1, S2, S3K) use d2 = airHalfHeight for the
`SolidObject` Y detection window. The engine fix is in the shared
`ObjectSolidContactController.processMultiPieceCollision` and applies universally.

**Originating commit.** `bugfix/ai-s1-slz1-staircase`
(SLZ1 f933 → f2872; staircase piece fresh-landing 1px low when grounded).

---

---

## P5 — SynchroAnimate global counter: objects read v_ani0_frame from BEFORE the current frame's SynchroAnimate call

**Symptom.** An object whose animation or harmfulness is gated on `v_ani0_frame` (the global sync counter) fires one tick early at multiples of 12 gfc — e.g. a spike marks itself harmful when ROM would have it harmless, causing a spurious HURT one trace-frame early.

**Root cause.** Two layered bugs under one class:

1. *Per-object unseeded counter:* Any per-object `animCounter` initialized to 0 at construction diverges from the shared `v_ani0_frame` whenever the object streams in mid-level (the real `v_ani0_frame` has been ticking since level start). Always derive `v_ani0_frame` from `levelManager.getFrameCounter() + 1` (= current gfc), not from a per-object accumulator. See also SBZ Electrocuter pattern (`v_framecount` source).

2. *Off-by-one: ExecuteObjects runs before SynchroAnimate:* ROM `Level_MainLoop` (sonic.asm:2980) order is:
   ```
   addq.w #1,(v_framecount).w   ; gfc++ at top of loop (line 2984)
   jsr ExecuteObjects            ; objects READ v_ani0_frame (line 2988)
   jsr SynchroAnimate            ; UPDATES v_ani0_frame (line 3010)
   ```
   At loop gfc=N, objects read `v_ani0_frame` set by iteration N-1's `SynchroAnimate`. `SynchroAnimate` ticks on calls 1, 13, 25, … (underflow branch `bpl`: initial `v_ani0_time=0` underflows to 0xFF on call 1). After N-1 calls, tick count = `ceil((N-1)/12) = (N+10)/12` (integer division). The correct formula is:
   ```java
   animCounter = (-((gfc + ANIM_FRAME_DURATION - 2) / ANIM_FRAME_DURATION)) & 0x07;
   //           = (-((gfc + 10) / 12)) & 7   for ANIM_FRAME_DURATION=12
   ```
   Using `(gfc + 11) / 12` (the naive "after N calls") overshoots by 1 tick at every multiple of 12.

**What to check.** Any object that reads `(v_ani0_frame).w` in `Hel_RotateSpikes` or similar:
- Derive from `levelManager.getFrameCounter() + 1` (never from a per-object counter or a constructor-seeded value).
- Use `(gfc + 10) / 12` not `(gfc + 11) / 12` for the tick count.

**ROM citation.** `docs/s1disasm/_incObj/17 GHZ Spiked Pole Helix.asm:95-105` (`Hel_RotateSpikes`: `move.b (v_ani0_frame).w,d0`). `SynchroAnimate`: `docs/s1disasm/sonic.asm:3111-3119` (bpl branch on underflow; reload to 11). `Level_MainLoop` order: `docs/s1disasm/sonic.asm:2984-3010`.

**Originating commits.** `bugfix/ai-s1-ghz3-f4650` (f4650 -> f5043, unseeded counter); `bugfix/ai-s1-ghz3-f5043` (f5043 -> f6464, off-by-one formula).

## P6 — Object-set control lock (locktime/move_lock) decrements only on grounded frames

**Symptom.** An object that disables the player's D-pad for N frames (a horizontal spring, a launcher, anything writing `locktime`/`move_lock`) lets input back in too early when the lock window spans airborne frames — the player starts accelerating/decelerating before ROM does. Typical trace signature: a 1px `x` and ~0x80 `x_speed`/`x_sub` divergence on the frame just after the player lands from the launch, with input freshly pressed that the ROM still ignores.

**Root cause.** ROM's horizontal control lock is the single RAM word `locktime` (S1, objoff_3E) = `move_lock` (S2/S3K). It is decremented **only on grounded frames**, inside `Sonic_SlopeRepel` (S1) / the slope-repel routine (S2/S3K), which the grounded player modes (`MdNormal`/`MdRoll`) call but the airborne modes (`MdJump`/`MdJump2`) do **not**. So while the player is airborne the lock is FROZEN — its countdown pauses for the entire jump/launch arc and resumes only on landing.

The engine models `locktime`/`move_lock` as `moveLockTimer`, decremented only in the grounded `doSlopeRepel()` path — correct. The trap: do NOT invent a per-object lock field decremented every frame (e.g. S1 spring's old `springingFrames`, ticked unconditionally in `tickStatus()`). A per-frame counter expires several frames early whenever the lock spans airborne frames.

**What to check.** Any object porting a `move.w #N,locktime(a1)` / `move.w #N,move_lock(a1)`:
- Set the lock through `player.setMoveLockTimer(N)`, NOT a bespoke counter. `moveLockTimer` already has the grounded-only decrement and feeds the same `!air && (moveLocked || ...)` ground control-lock gate.
- Confirm in the ROM which spring/launcher variant actually sets the lock. In S1 only `Spring_LR` (horizontal) sets `locktime`; vertical/down springs do not.

**ROM citation.** `docs/s1disasm/_incObj/41 Springs.asm:145` (`Spring_BounceLR`: `move.w #15,locktime(a1)`). Grounded-only decrement: `docs/s1disasm/_incObj/01 Sonic.asm:1383,1410` (`Sonic_SlopeRepel` `tst.w locktime` / `subq.w #1,locktime`), called from `Sonic_MdNormal`/`Sonic_MdRoll` (`asm:323,352`) but not the airborne modes (`asm:328-341,357-370`). S2 equivalent: `docs/s2disasm/s2.asm:34031` (horizontal spring `move.w #$F,move_lock(a1)`).

**Originating commit.** `bugfix/ai-s1-slz2-f1493` (SLZ2 f1714 -> f2552, 215 -> 137; S1 LR spring routed control lock through moveLockTimer).

---

## P7 — Dormant object has obColType=0 until it activates: ReactToItem skips it entirely

**Symptom.** A badnik/hazard that waits in an inert state before activating (curled, hidden, pre-trigger) wrongly hurts or interacts with the player while still dormant. Trace signature: the player loses rings / enters the hurt routine (`rtn` 2 -> 4) on a frame where ROM has him pass the object untouched; the object is on-screen and near the player but ROM never reacts to it.

**Root cause.** S1 `ReactToItem` gates every object on its `obColType` byte: `move.b obColType(a1),d0 / bne React_CheckHitboxOverlap` — if `obColType == 0` (col_none), the object is skipped before any hitbox test. Many objects do NOT set `obColType` in their init routine (routine 0 / `*_Main`); they write it only when they transition into an active/damaging/destroyable state. So a freshly spawned, not-yet-triggered object is non-collidable even though it has a position, a size, and is rendered. Engine object classes that hardcode `getCollisionFlags()` to a non-zero size index from construction make the dormant object collidable from frame 0.

**What to check.** For any object that has a pre-activation waiting state:
- Trace where the ROM first writes `obColType` (`move.b #col_*,obColType(a0)`). Everything before that write is col_none.
- Gate `getCollisionFlags()` to return `0` until the engine reaches the equivalent activated state. If the activation state is monotonic (the object never returns to the dormant routine once it has activated), derive the gate from the existing routine/secondary-routine field — no new rewind-captured field needed.
- Distinguish this from the on-screen render gate (`isOnScreenForTouch()` / `requiresRenderFlagForTouch()`): that handles "off-screen or DisplaySprite-not-yet-run". This pitfall is the orthogonal "on-screen but obColType still 0" case.

**ROM citation.** `docs/s1disasm/_incObj/Sonic ReactToItem.asm:52-53` (`tst`/`bne` obColType gate). Concrete object: `docs/s1disasm/_incObj/43 Badnik - Roller.asm:19-38` (`Roll_Main` sets height/width but never obColType) + `:86-100` (`Roll_Action_FromLeft` ob2ndRout=0 leaves it untouched until activation at `:96` sets `$8E`; stop-and-unfold `:177` sets `$0E`).

**Cross-game note.** S2 `Touch_Loop` has no render-flag gate but still checks `collision_flags(a1)` (zero = skipped); S3K only adds opted-in objects to `Collision_response_list`. So the "dormant -> not in collision until activated" invariant holds in all three — verify each new waiting-state object reports zero collision until it writes its colType / adds itself to the list.

**Originating commit.** `bugfix/ai-s1-syz1-f2338` (SYZ1 f2338 -> f4430; curled SYZ Roller (Obj 0x43) reported col=0x0E from spawn and hurt the falling player where ROM's dormant Roller has obColType=0).

---

## P8 — Multi-piece objects must spawn real OST-slot children (FindFreeObj/FindNextFreeObj), NOT internal arrays or bare slot-reservations

**Symptom.** A whole zone's object slots drift out of ROM alignment, surfacing as a downstream object mis-slotted dozens-to-hundreds of frames later (e.g. trace `obj_sNN_slot exp 0xNN act 0xMM`, or a far-off object missing/extra). The multi-piece object itself looks correct on screen.

**Root cause.** Modeling a chain / collapsing-floor row / multi-segment stomper / multi-link platform as ONE engine object that carries internal `linkX[]`/`linkY[]` arrays (render-only) — or that "reserves" slots without actually occupying them as real `ObjectInstance`s — under-allocates the OST. ROM spawns each piece as a genuine object via `FindFreeObj` (lowest free slot) or `FindNextFreeObj` (next free after parent), consuming real SST slots. Every subsequent `FindFreeObj`/ObjPosLoad call in the zone then sees a different free-slot pattern → cascading slot drift. **Count matters:** ROM `dbf d1` with `d1 = N` runs **N+1** times (the loop body + the fall-through), so a "spawn d1 children" loop creates `d1+1` pieces — off-by-one here under-allocates by one slot.

**What to check.** For any chain/floor/multi-segment object: trace the ROM spawn loop. Each `FindFreeObj`/`FindNextFreeObj` + `_move.b #id,obID(a1)` is a REAL child object the engine must spawn as a real `ObjectInstance` on its own slot (`spawnChild` / `allocateChild*`), not an internal array entry. Count the `dbf` iterations as `d1+1`. Render-only children that have no collision still occupy a slot in ROM — spawn them.

**ROM citation.** `docs/s1disasm/_incObj/15 Swinging Platforms.asm:67-105` (`.makechain`: `FindNextFreeObj`/`FindFreeObj` per link, `dbf d1,.makechain` = chain-length+1 children). `docs/s1disasm/_incObj/sub FindFreeObj.asm` (FindFreeObj lowest-free; FindNextFreeObj next-after-parent). MZ3 Chained Stomper: `docs/s1disasm/_incObj/31 MZ Chained Stompers.asm:59-97` (size-2 spike-fold = 2 children).

**Rewind note.** Parent-recreated render-only children resolve via `TestRewindCoverageGuard` baseline entries (the recreate path is parent-driven; baseline-entry the child's `#recreate` + `#finalScalar` keys). Precedents: `SpikedBallChain$ChainChild`, `CollapsingLedge$Fragment`.

**Cross-game note.** S2/S3K spawn multi-piece children through `AllocateObject` (lowest-free) / `AllocateObjectAfterCurrent` (after parent) — `docs/s2disasm/s2.asm` and `docs/skdisasm/sonic3k.asm:37911,37917`. Same "real OST slot per piece, count = dbf+1" rule.

**Originating commit.** `9f47f557f` (S1 SBZ2 Obj 0x15 swinging-platform chain links — render-only children spawned as real OST slots). Banked: MZ3 ChainStomp `StomperPieceChild`.

---

## P9 — Object delete/cancel checks must run only in the ROM routines that call them, not every frame

**Symptom.** A child/projectile object is deleted hundreds of frames too early (or too late), freeing its slot at the wrong time → slot drift / a downstream object mis-slotted. The object's own motion looks right until it vanishes.

**Root cause.** Porting a "cancel if parent destroyed" (or any delete-gate) check into the engine object's per-frame `update()` unconditionally, when ROM only calls that check from SPECIFIC routines. ROM dispatches by `obRoutine`: the cancel subroutine is `bsr`'d only from the routines that should run it, not from the active/in-flight routine. Running it every frame deletes the object during a phase ROM keeps it alive.

**What to check.** When porting any `*_ChkCancel` / `*_ChkDel` / delete-gate subroutine: find every `bsr`/`jsr` to it and note WHICH `obRoutine` values reach it. Gate the engine's call to the equivalent engine routine/state — do not call it from the shared per-frame entry.

**ROM citation.** `docs/s1disasm/_incObj/22, 23 Badnik - Buzz Bomber and Missile.asm:162-194,220-249` — `Msl_ChkCancel` (cancel missile if parent Buzz Bomber destroyed) is `bsr`'d only from `Msl_Main` (routine 0) and `Msl_Animate` (routine 2, the flare phases), NOT from the active `Msl_FromBuzz` (routine 4). The engine ran it every frame → missile deleted ~840 frames early.

**Cross-game note.** S2/S3K objects use the same `obRoutine`/`Obj_routine` jump-table dispatch; a delete/cancel check belongs in the routines that ROM `bsr`s it from. Verify the calling routines per object before porting.

**Originating commit.** `53da8c24a` (S1 Buzz Bomber Missile cancels-on-parent-destroyed only in flare phase, not active).

---

## P10 — Off-screen delete uses ROM's obRender render-box `[camX-obActWid, camX+320+obActWid)`, not a raw `isOnScreenX(160)` margin

**Symptom.** A short-lived object (debris, shrapnel, projectile) lingers a few frames longer (or unloads earlier) than ROM, so the count of that object's instances differs at a given frame → slot drift.

**Root cause.** Using a fixed engine `isOnScreenX(160)` (or any raw pixel margin) for the off-screen delete instead of the ROM render-box bound. ROM deletes when the `obRender` on-screen bit is clear, which `BuildSprites` computes from the camera-X-coarse window widened by the object's `obActWid` (and chunk-rounded). A 160px margin keeps the object alive over a wider/narrower window than ROM (e.g. Walking Bomb shrapnel lingered 24 frames vs ROM's 16).

**What to check.** For any object whose ROM delete is `tst.b obRender(a0) / bpl DeleteObject` (or the `out_of_range` macro): model the delete with the engine's ROM-render-box helper keyed on the object's `obActWid`, not `isOnScreenX(<arbitrary>)`. Scope the fix to the specific object — the broad badnik `isPersistent` keep-alive is a separate, inert concern.

**ROM citation.** `docs/s1disasm/_incObj/5F Badnik - Walking Bomb.asm:218-219` (shrapnel `tst.b obRender(a0) / bpl.w DeleteObject`). Render-box computation: `docs/s1disasm/_inc/BuildSprites.asm:47-58` (camera-X-coarse + width window sets the `obRender` on-screen bit).

**Cross-game note.** S2 `MarkObjGone` uses the chunk-rounded camera-X-coarse bound `$80 + screen_width + $80` (`docs/s2disasm/s2.asm`, `MarkObjGone`); S3K's render/display path is equivalent. Use the ROM render bound, not a raw margin, in all three.

**Originating commit.** `0a15683b9` (S1 Walking Bomb shrapnel deletes via ROM obRender render bound, not raw isOnScreenX).

---

## P11 — Broken/consumed objects must report `col_none` so ReactToItem skips them (orthogonal to P7's dormant case)

**Symptom.** A broken monitor (or any consumed/spent object) still reports a non-zero collision type and PREEMPTS an adjacent live object's interaction — e.g. an already-broken monitor's stale col_item shadows the break of a neighbouring monitor the player actually touches.

**Root cause.** S1 `ReactToItem` gates each object on `obColType` (`tst / bne` — `col_none` = skipped, `Sonic ReactToItem.asm:52-53`). A broken/consumed object that fails to write `col_none` keeps its old collision flags, so `ReactToItem` still considers it (and, because ReactToItem returns after the first overlap, it can consume the contact that should have hit the adjacent object). This is the *post-activation/consumed* mirror of **P7** (which covers the *pre-activation/dormant* case).

**What to check.** For any object with a "broken"/"consumed"/"spent" terminal state: confirm the ROM writes `move.b #col_none,obColType(a0)` on entering that state, and gate the engine's `getCollisionFlags()` to return `0` there. Cross-reference P7 for the dormant-before-activation case.

**ROM citation.** `docs/s1disasm/_incObj/26, 2E Monitors and Power-Ups.asm:183` (`move.b #col_none,obColType(a0)` — broken monitor stops further collision). Gate: `docs/s1disasm/_incObj/Sonic ReactToItem.asm:52-53`.

**Cross-game note.** S2 `Touch_Loop` reads `collision_flags(a1)` and `bne` to the check (zero = skipped, `docs/s2disasm/s2.asm:85048-85049`); S3K only touch-checks objects added to `Collision_response_list`. A consumed object must clear its flags / not re-add itself in all three.

**Originating commit.** `466f408a8` (S1 broken monitor clears col type so it stops preempting ReactToItem).

---

## P12 — Collapse/release drops must clear the player's on-object status bit, not just the engine's riding link

**Symptom.** A collapsing ledge/floor (or any "drop the rider" object) releases the player, but the player stays grounded / pinned at the object's last surface Y for a frame instead of falling — a 1px seat error and a 1-frame-late airborne transition.

**Root cause.** Calling only the engine's `clearRidingObject` (which drops the internal riding bookkeeping) leaves the player's `onObject`/grounded flags set, so the player is still treated as standing. ROM's release path does `bclr #3,obStatus(a1)` (clear the player's on-platform bit) — the player becomes airborne. The engine must mirror that: `setOnObject(false)` + `setAir(true)` on release, not just unlink the ride.

**What to check.** For any object that drops/releases a standing player (collapse, retract, vanish, switch-off): after `clearRidingObject`, also clear the player's on-object status and set air — matching the ROM `bclr #3,obStatus(a1)`.

**ROM citation.** `docs/s1disasm/_incObj/1A, 53 Collapsing Ledges and Floors.asm:104-105` (CollapseLedge `.delayCollapse` `bclr #3,obStatus(a1)`) and `:233-240` (CollapseFloor `.delayCollapse` `bclr #3,obStatus(a1)`).

**Cross-game note.** S2/S3K release routines likewise `bclr` the player's standing/on-object status bit; clearing only the engine ride link under-models them.

**Originating commits.** `7a9a8a4b2` (S1 CollapseFloor airborne-drop on collapse), `446d87185` (S1 GHZ CollapsingLedge clears rider on-object/push status on collapse-release).

---

## P13 — Routine transitions in the engine's pre-update SOLID pass run 1 frame AHEAD of ROM's in-execution PlatformObject routine advance — defer the first frame

**Symptom.** A timer-driven object (collapse delay, fall delay) started by a solid-contact routine transition fires 1 frame early vs ROM.

**Root cause.** ROM advances the object's `obRoutine` *inside* `PlatformObject`, which runs during the object's own `ExecuteObjects` slot — so the new routine's body first executes on the *next* frame. The engine resolves solid contact in a PRE-update pass before the object's `update()`, so the routine transition it sets is observed by the same-frame `update()` body → the timer/state advances one frame early. (This is the routine-advance sibling of **P2**, which is about reading the standing BIT after the checkpoint; P13 is about the ROUTINE transition the solid pass triggers.)

**What to check.** When a solid-contact/PlatformObject routine advance starts a per-object timer or state machine, guard the first frame: a `*EnteredThisFrame` flag that skips the new routine's body on the transition frame, matching ROM's "advance now, run next frame" cadence.

**ROM citation.** `docs/s1disasm/_incObj/1A, 53 Collapsing Ledges and Floors.asm` (CollapseFloor routine-4 entry via the solid pass; the collapse timer must not tick on the entry frame). See also P2 (`18 Platforms.asm:201-216`) for the standing-bit-read sibling.

**Cross-game note.** S2/S3K `SolidObject`/`PlatformObject` also advance the object routine during the object's own slot execution; an engine pre-update solid pass that sets the routine will see it one frame early — apply the same first-frame defer.

**Originating commit.** `7a9a8a4b2` (S1 Collapsing Floor defers routine-4 entry one frame via a `collapseEnteredThisFrame` guard).

---

## P14 — SolidObject edge tests are inclusive (`bhi`): `relX == width*2` IS a contact

**Symptom.** An object's side/top contact misses on the exact-edge frame — e.g. a rolling player grazing the precise right edge of a boss body produces no bounce, firing the bounce one frame late (when the player has penetrated 1px).

**Root cause.** ROM `SolidObject`'s X-range gate is `cmp.w d3,d0 / bhi.w Solid_NoCollision` where `d3 = width*2` and `d0 = playerX - objX + halfWidth`. `bhi` is exclusive-greater, so `d0 == d3` (player centre exactly at `objX + halfWidth`, i.e. `relX == width*2`) is NOT rejected — it IS a valid contact. An engine overlap gate using `relX >= width*2 → no contact` (exclusive) drops that exact-edge frame.

**What to check.** For any object using the plain `SolidObject` family, the right-edge X gate must treat `relX == width*2` as in-contact. In the engine, opt into `usesInclusiveRightEdge()` (the flag S3K horizontal springs and S1 spikes already use for their `SolidObject`-family `bhi` gates). Verify against the object's actual ROM solid routine.

**ROM citation.** `docs/s1disasm/_incObj/sub SolidObject.asm:167-168` (`cmp.w d3,d0 / bhi.w Solid_NoCollision`).

**Cross-game note.** S2 `SolidObject_cont` (`docs/s2disasm/s2.asm:35353-35354`) and S3K `SolidObject` (`docs/skdisasm/sonic3k.asm:41364-41365`) use the same `cmp.w d3,d0 / bhi` inclusive right edge. Apply `usesInclusiveRightEdge()` for any `SolidObject`-family object in those games too.

**Originating commit.** `caf70abb7` (S1 FZ boss uses ROM-inclusive SolidObject right edge for roll-bounce; f837 -> f1724).

---

## P15 — Object pushes the player: `add.w speed,obX(a1)` / `move.w pos,obX(a1)` preserve the rider's sub-pixel — never `setCentreX`/`setCentreY` (they ZERO it)

**Symptom.** A frontier reads as a "sub-pixel RAM-gated" 1px/0.x-px X (or Y) residual — the engine drifts a constant fraction behind ROM, crossing an integer boundary 1 frame off. The player is being pushed/carried/captured by an object (conveyor, fan/wind, moving solid, teleporter capture, MvSonicOnPtfm carry, ...) and the engine is byte-EXACT until the first frame that object touches him, then a CONSTANT delta thereafter.

**Root cause.** Almost every ROM object→player position write operates on the PIXEL word only: `add.w <speed>,obX(a1)` / `sub.w d0,obX(a1)` / `move.w <pos>,obX(a1)` write obX/obY at offset `$8`/`$C` and leave the sub-pixel words (`x_sub`/`y_sub` at `$A`/`$E`) UNTOUCHED — the rider keeps his accumulated fraction. The engine's `setCentreX(short)` / `setCentreY(short)` ZERO the sub-pixel (`this.xSubpixel = 0`). Calling them in an object-push path discards up to ~1px of fraction every frame the object acts on the player, putting him progressively behind ROM.

**What to check / fix.** For any object that pushes or carries the player each frame, or captures/repositions him to a pixel position the ROM writes via `add.w`/`sub.w`/`move.w` to the pixel word:
- **Incremental push** (`add.w speed,obX(a1)`): use `player.shiftX(delta)` / `player.shiftY(delta)` — they add an integer pixel delta and preserve the sub-pixel.
- **Set-to-position** (`move.w pos,obX(a1)`): use `player.setCentreXPreserveSubpixel(x)` / `setCentreYPreserveSubpixel(y)` (or `setX`/`setY`, which write the pixel word only).
- **Only keep `setCentreX`/`setCentreY` (sub-pixel zeroing) where ROM EXPLICITLY clears the fraction**, e.g. `move.w #0,obSubpixelX(a0)`. The S1 level-side-boundary clamp does exactly this (`Boundary_Sides`: `move.w d0,obX(a0)` THEN `move.w #0,obSubpixelX(a0)`, `01 Sonic.asm:1097-1100`) — there `setCentreX` is CORRECT. Always read the object's actual ROM routine before changing the setter; FAITHFUL-OR-BOUNCE per call site.

Audit method: gate a probe in `AbstractSprite.setCentreX/Y` on a system property that logs the first non-`AbstractSprite` caller whenever a NON-zero sub-pixel is zeroed (dedup per call site), then run the trace sweep; each hit is a candidate to verify against the disasm.

**ROM citation.** Sub-pixel-PRESERVING object pushes (engine should use `shiftX`/`setCentreXPreserveSubpixel`): S1 Conveyor `add.w conv_speed(a0),obX(a1)`; SLZ Fan `add.w d0,obX(a1)` (`5D SLZ Fan.asm:82`); SBZ Teleporter capture `move.w obX(a0),obX(a1)` (`72 SBZ Teleporter.asm:73-74`); `SolidObject` side-push `sub.w d0,obX(a1)` (`sub SolidObject.asm:239`); `MvSonicOnPtfm` Y-seat `move.w d0,obY(a1)` + X-carry `sub.w d2,obX(a1)` (`sub MvSonicOnPtfm.asm:36,39`); Spring side-push `addq.w #8,obX(a1)` (`41 Springs.asm:137`); MZ PushBlock align `add.w d0,obX(a1)` (`33 MZ, LZ Pushable Blocks.asm:404`). Sub-pixel-ZEROING (engine `setCentreX` is correct): level-side boundary `move.w #0,obSubpixelX(a0)` (`01 Sonic.asm:1100`).

**Also covers object SELF-motion, not just player-push.** The same rule applies when an object moves ITSELF: ROM `add.w speed,obX(a0)` adds to the object's own pixel word and preserves its own `x_sub`, so a rideable/collidable object that self-moves via `setCentreX`/`setCentreY` (zeroing) would drift ~1px and surface that 1px wherever the player rides/hits it. Use `SubpixelMotion.moveSprite` / `shiftX`/`shiftY` for self-motion too. (An object that legitimately moves in INTEGER pixel steps — e.g. SLZ staircase/seesaw use integer `int x`/`int y` self-position with no sub-pixel accumulator, matching ROM's integer/oscillation-table motion — has no fraction to discard and needs no change; a 16.16-accumulator object like the GHZ boss `xFixed`/`yFixed` already preserves it.) A `-Dobjsubpxaudit` probe in `setCentreX/Y` (log non-`AbstractSprite` object-code callers that zero a non-zero subpixel on any sprite) finds these. **S1 audit result (commit below): a full 19-zone sweep found ZERO object-self-motion subpixel-zeroing instances** — the gated 1px-object reds (SLZ1 f2872 staircase, GHZ3 f8021 boss, SLZ2/SLZ3) are genuinely counter-phase / RAM-gated, NOT self-motion-subpixel.

**Cross-game note.** S2/S3K conveyors, fans, moving solids, and `MvSonicOnPtfm`/`SolidObject` side-pushes use the same `add.w`/`sub.w`/`move.w obX(a1)` pixel-word convention and likewise PRESERVE the rider's sub-pixel — any S2/S3K object-push OR self-move path using a sub-pixel-zeroing centre setter is the same bug. (S3K `x_pos` is 16.16 with the same `$8`/`$A` obX/x_sub layout.)

**Originating commit.** `b5bc778d4` (S1 Conveyor Belt preserves rider sub-pixel X on push via `shiftX`; SBZ2 f2224 -> f2323). Companion: SBZ Teleporter capture uses `setCentreXPreserveSubpixel`/`setCentreYPreserveSubpixel`. Object-self-motion audit (no instances found in S1): this commit (catalogue update; no engine fix needed).
