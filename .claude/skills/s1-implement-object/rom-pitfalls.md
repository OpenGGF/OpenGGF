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
