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
