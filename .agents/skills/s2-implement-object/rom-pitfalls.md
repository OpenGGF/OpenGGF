# Sonic 2 Object Implementation — ROM Behavioural Pitfalls

Anti-pattern catalogue accumulated from trace replay frontier advancement
fixes. **Read this before starting Phase 2 of `s2-implement-object/SKILL.md`**
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

## P1 — Touch-response directional/state guards diverge from ROM

**Symptom.** Object rejects a rolling / spindash / invincible touch under a
condition ROM doesn't gate on. Trace shows the player passing through the
badnik or platform without the expected event (kill, bounce, launch).

**Root cause.** Engine added an extra gate (player below object, specific
direction, specific timer state) that ROM `Touch_Enemy` / `Touch_Killable` /
`Touch_KillEnemy` doesn't apply. ROM typically uses overlap-only for the
kill itself, and *only* uses position to choose the bounce direction.

**What to check.** When porting `onPlayerAttack` / touch-response code for a
new badnik or interactive object: read the ROM `subObjData` table at the
top of the object file and the `Touch_*` routine entry. List every gate the
ROM applies; reproduce *only* those. Do not add directional guards "for
safety".

**ROM citation.** `docs/s2disasm/s2.asm:84807-84890` (`Touch_Enemy` /
`Touch_KillEnemy`). Object touchbox via `subObjData ..., w, h, flags`
(e.g. `s2.asm:76603` for Grabber).

**Originating commit.** `c2d998751 fix(s2): CPZ Grabber badnik rolling-kill
independent of vertical position`.

---

## P2 — ROM multi-frame init collapsed into one engine frame

**Symptom.** A trace divergence appears N frames before the ROM-correct
state transition fires, then the transition itself is one frame early.
Timer values like `scriptTimer` differ by exactly N at the divergence.

**Root cause.** ROM dispatches object init across multiple frames: outer
`Obj_Init` writes routine and `rts`, the next frame enters the inner case
0 which performs the real init work. The engine constructor often does
both in zero frames, pre-setting `routine = subtype - <base>`. That
collapses ROM's two-frame init into one, shifting every downstream timer
by 1.

**What to check.** When porting object init, find ROM's outer init label
(`ObjXX_Init`), its `bra.w DisplaySprite` / `rts`, and the inner main
routine's case 0. If they live in separate frames in ROM (each ends in
`rts` from a dispatcher), preserve the frame count in the engine — don't
pre-resolve the inner init in the constructor.

**What to check (cont).** For trace replay paths that rely on the
pre-gameplay prelude (S2 v9.2-s2 native-prelude mode), expose a
`compensateForCollapsedInit()` hook that the trace replay bootstrap can
call to add the missing frame's worth of state changes. Gate it by zone /
condition so it doesn't fire in normal play.

**ROM citation.** `docs/s2disasm/s2.asm:78271-78284` (`ObjB2_Init` outer
dispatch) + `s2.asm:78368-78372` (`ObjB2_Main_WFZ_Start_init` inner case 0).

**Originating commit.** `44d7939e1 fix(s2): WFZ Tornado collapsed two-frame
init compensation`.

---

## P3 — Global object state vs ROM per-player object state bytes

**Symptom.** Sidekick (player 2) interaction with an object is suppressed
by a state flag set by Sonic's interaction, or vice versa. Tails fails to
trigger a Flipper / spring / monitor that Sonic just triggered.

**Root cause.** Engine uses a single `int` or `boolean` field for state
that ROM tracks per-player at SST offsets like `objoff_36` (P1) and
`objoff_37` (P2). When Sonic sets the global flag, Tails-side checks see
"already triggered" and bail.

**What to check.** When porting an interactive object, list every SST byte
the ROM reads/writes. If the offset is `objoff_36`, `+1` (i.e. `objoff_37`),
or any per-player pair, the engine state must be a per-sprite map
(`IdentityHashMap<AbstractPlayableSprite, Integer>` or similar), not a
global field. The originating Flipper case had a launch cooldown that
mattered to both players independently.

**ROM citation.** `docs/s2disasm/s2.asm:57870-57879` (Flipper per-player
state bytes); analogous pairs exist for springs, bumpers, monitors.

**Originating commit.** `3cb72b6af fix(s2): CNZ Flipper per-player launch
cooldown + ROM-accurate y_pos`.

---

## P4 — Character-dependent coordinate adjustments where ROM uses a fixed offset

**Symptom.** Tails Y diverges from Sonic Y by a character-specific amount
after a rolling launch, hurt, or other state transition — usually 1-5px.

**Root cause.** Engine reaches for a helper like
`getRollHeightAdjustment()` (which returns `runHeight - rollHeight` and
differs per character: Sonic 10, Tails 2) when ROM has a literal `addq.w
#N, y_pos(a1)` (constant for all characters).

**What to check.** When the ROM source code has `addq.w #<literal>,
y_pos(a1)` or `subq.w #<literal>, y_pos(a1)`, port that as a literal
`NativePositionOps.addYPosPreserveSubpixel(player, 5)`. Do not substitute a
character-aware helper "because Tails is shorter". Reserve raw
`setCentreYPreserveSubpixel(...)` calls for lower-level sprite internals or
non-playable/object-local state.

**ROM citation.** `docs/s2disasm/s2.asm:58042` (Flipper rolling entry:
`addq.w #5, y_pos(a1)`).

**Originating commit.** `3cb72b6af fix(s2): CNZ Flipper per-player launch
cooldown + ROM-accurate y_pos` (secondary bug).

---

## P5 — SolidObject returns non-solid prematurely on state transition

**Symptom.** Rider drops from a solid object on the exact frame of an
internal state-machine transition (e.g. WAIT → SLIDE → FALL). One-pixel y
divergence appears at the transition frame as the engine treats the rider
as airborne while ROM keeps them riding.

**Root cause.** Engine's `isSolidFor()` gates on `routineSecondary !=
STATE_FALL` or similar internal state. ROM's `Obj_Main` calls
`PlatformObject` unconditionally; the lift continues to position riders
across the entire state-machine lifecycle. ROM only stops being solid when
the object physically leaves the screen, often via a `move.w #$4000,
x_pos(a0)` off-screen warp inside the fall handler.

**What to check.** `isSolidFor()` for moving solids should track *physical
existence on screen*, not internal state-machine routine. Look at the
ROM's exit condition (usually an off-screen check inside the FALL handler
that performs the warp) and only return false after that warp has fired.

**ROM citation.** `docs/s2disasm/s2.asm:47381-47466` (`Obj16_Main` dispatch
order + `Obj16_Fall`).

**Originating commit.** `719c4034e fix(s2): HTZ Lift solid-while-falling +
ROM-order gravity/move`.

---

## P6 — Gravity-before-move vs ROM's move-before-gravity ordering

**Symptom.** A falling object's y_pos transitions integer values one frame
earlier than ROM. Often surfaces as a single-frame off-by-one on a rider's
camera_y / player y at the transition frame.

**Root cause.** Engine free-fall code does `yVel += gravity; yFixed +=
yVel` (gravity then move). ROM `Obj_*_Fall` consistently does
`ObjectMove` (which adds yVel to y_pos in subpixel) **first**, then `addi.w
#$<gravity>, y_vel(a0)` after. The order matters because the y_pos
integer rolls over one frame later in ROM.

**What to check.** When porting a free-fall routine, preserve the
`ObjectMove → addi.w gravity` order. Search the ROM routine for the
`ObjectMove` / `MoveSprite` call and the gravity `addi.w`; the call comes
*before* the addi.

**ROM citation.** `docs/s2disasm/s2.asm:47444-47466` (`Obj16_Fall`),
`s2.asm:29967-29981` (`ObjectMoveAndFall` reference impl).

**Originating commit.** `719c4034e fix(s2): HTZ Lift solid-while-falling +
ROM-order gravity/move` (secondary bug).

---

## P7 — Centre Y vs top-left Y for kill / boundary checks

**Symptom.** Sidekick (or player) fails to die when crossing the bottom
kill plane that ROM would trigger, or dies one frame late.

**Root cause.** Engine compares `sprite.getY()` (top-left convention,
y_radius pixels above centre) against the kill plane. ROM `Tails_LevelBound`
and `Sonic_LevelBound` compare `y_pos(a0)` which is the centre.

**What to check.** Any kill / boundary / out-of-bounds check the object
triggers (or any check the object's collision drives) must compare against
`getCentreY()` not `getY()`. Same for X-axis side boundaries:
`getCentreX()` not `getX()`.

**ROM citation.** `docs/s2disasm/s2.asm:39929-39940` (`Tails_LevelBound`
bottom check), `s2.asm:36936-36960` (`Sonic_LevelBound`),
`s2.asm:84999-85019` (`KillCharacter` follow-up).

**Originating commit.** `4361de0e8 fix(s2): sidekick level-bound bottom
kill uses centre Y to match ROM`.

---

## P8 — Per-game post-event flow divergence (S2 deferred vs S3K immediate)

**Symptom.** Sidekick warps off-screen immediately on death in the engine,
but ROM keeps Tails at his death position for several frames until he
falls past a threshold.

**Root cause.** ROM `Obj02_Dead` (S2) defers the despawn warp until
`y_pos > Tails_Max_Y_pos + 0x100`, running gravity (`ObjectMoveAndFall`)
each frame until that threshold. ROM S3K `sub_13ECA` writes the despawn
marker immediately, one frame after kill. Engine generalised to the S3K
flow for both games, breaking S2.

**What to check.** When implementing sidekick state machines or
post-event flows that touch despawn / clean-up / return-to-pool, look at
each game's ROM equivalent separately. If they diverge, add a
`PhysicsFeatureSet` flag (already established pattern; see
`sidekickDeathUsesDeferredDespawn` for an example) and branch on the
flag — never on `gameId`.

**ROM citation.** `docs/s2disasm/s2.asm:40736-40759` (`Obj02_Dead` +
`Obj02_CheckGameOver` deferred-fall), `docs/s2disasm/s2.asm:29967-29981`
(`ObjectMoveAndFall`), `docs/s2disasm/s2.asm:39043-39052`
(`TailsCPU_Despawn` final warp). S3K immediate-warp baseline at
`docs/skdisasm/sonic3k.asm:26800-26809` (`sub_13ECA`).

**Originating commit.** `a4aca7d6f fix(s2): sidekick death uses
deferred-despawn flow to match S2 Obj02_Dead`.

---

## P9 — Integer math drops y_sub carry in 16:16 position updates

**Symptom.** Post-warp / post-teleport y_pos is exactly 1 pixel low (or
high) relative to ROM. The error appears only when the pre-event
`y_sub_pos + (y_vel & 0xFF00)` overflows the 16-bit subpixel boundary.
HTZ trace F538: ROM 0x0008 vs engine 0x0007 (-1px).

**Root cause.** ROM `ObjectMoveAndFall`
(`docs/s2disasm/s2.asm:29967-29981`) treats `y_pos:y_sub` as a single
32-bit long and executes `add.l d0,d3` where `d0 = y_vel<<8` (sign
extended). Subpixel overflow carries into `y_pos`. Java code that does
`y_pos += (y_vel >> 8)` after a `setCentreYPreserveSubpixel(...)` warp
treats the two halves as independent integers and DROPS the carry. The
overflowed low byte still lands in `y_sub` (because `setCentreY*` preserves
it), but `y_pos` is short by 1.

**What to check.** Any code path that:
1. Writes playable-sprite native `x_pos` / `y_pos` with `NativePositionOps`
   (or lower-level/raw preserve-subpixel setters in sprite internals),
2. THEN integrates by a velocity stored in subpixel units (`x_vel` / `y_vel`),
must use `AbstractSprite.move(xSpeed, ySpeed)` — which mirrors ROM's
`add.l d0, x_pos(a0)` / `add.l d0, y_pos(a0)` — rather than manual
`centreY += (ySpeed >> 8)` arithmetic. The same applies any time you
need to add `y_vel` to position and ROM stores the full position as a long.

**ROM citation.** `docs/s2disasm/s2.asm:29967-29981`
(`ObjectMoveAndFall`); same convention in S1 / S3K
(`docs/s1disasm/_inc/ObjectMove.asm`,
`docs/skdisasm/sonic3k.asm` `ObjectMoveAndFall` / `MoveSprite`).
Engine equivalent: `AbstractSprite.move` in
`src/main/java/com/openggf/sprites/AbstractSprite.java`.

**Originating commit.** `<pending>` (trace frontier advancement loop iter
1: HTZ F538 + MCZ F443 deferred-despawn sub-pixel & solid-contact gating).

---

## P10 — Solid object contacts must skip dead / despawning players

**Symptom.** A dying CPU sidekick (or main player) "lands" on a moving
solid object (lift / platform / drawbridge) under the impact point while
ROM would have him fall past it. Engine's `tails_y` freezes at the
platform top and `tails_y_speed` drops to 0, while ROM keeps Tails falling
through the platform.

**Root cause.** ROM `SolidObject_ChkBounds`
(`docs/s2disasm/s2.asm:35178-35182`) gates the full bounding-box check
with:

```
SolidObject_ChkBounds:
    tst.b    obj_control(a1)
    bmi.w    SolidObject_TestClearPush   ; bit 7 set => skip
    cmpi.b   #6,routine(a1)              ; routine >= 6?
    bhs.w    SolidObject_NoCollision     ; Dead/Gone/Respawning => skip
```

The two gates are independent. The `obj_control bit 7` path covers
respawning / object-controlled states (post-warp). The `routine >= 6`
path covers the Dead / Gone / Respawning routines themselves. An engine
that ports only the `obj_control` gate will still apply solid contacts to
a sidekick mid-deferred-death-fall (S2 routine = 6 with `obj_control = 0`),
landing dead Tails on platforms.

**What to check.** `blocksSolidContacts(player, candidate)` (or whatever
the engine's SolidObject pre-filter is named) needs BOTH gates:
1. `player.isObjectControlled()` — mirrors `obj_control` bit 7.
2. CPU sidekick state `DEAD_FALLING` (engine equivalent of ROM Tails
   routine = 6 / Obj02_Dead) — must short-circuit even though
   `obj_control` is still 0 during S2 deferred-despawn.

For Sonic / Tails / Knuckles main-player code paths, ROM routine = 6 is
the same death state and the engine should similarly skip solid contacts
based on its `dead` / death-state flag.

**ROM citation.** `docs/s2disasm/s2.asm:35178-35182`
(`SolidObject_ChkBounds`). Same convention in S1 and S3K with their
respective ROM offsets. Engine equivalent: `ObjectManager.SolidContacts.
blocksSolidContacts` in `src/main/java/com/openggf/level/objects/
ObjectManager.java`.

**Originating commit.** `<pending>` (trace frontier advancement loop iter
1: HTZ F538 + MCZ F443 deferred-despawn sub-pixel & solid-contact gating).

---

## P11 — Solid object break/trigger condition leaks main-player state into sidekick contact

**Symptom.** Sidekick (Tails) is suddenly knocked airborne + rolling + Y
shifted by 1 px while the main player is rolling through nearby terrain.
Trace shows ROM keeps the sidekick grounded with `status.standing` and
`status.pushing`, while the engine reports `status.in_air | rolling` and a
fresh downward `y_vel`. The divergence appears on the exact frame the
main player passes a breakable / launchable object even though the
sidekick isn't standing on that object.

**Root cause.** The engine cached `playerWasRolling = player.getRolling()`
inside the object's per-frame `update(...)` method, with `player` being
whichever sprite the object manager happened to pass (typically the main
player). The break/launch decision in `onSolidContact(player, contact)`
then OR'd the cache with the contacting player's own `getRolling()`:
`isRolling = playerWasRolling || player.getRolling()`. When the main
player was rolling, the cache made the OR true even for the sidekick's
side / bottom contact, so the object's break path fired with the
sidekick as the victim — knocking them airborne, snapping `y_radius`
from 11 down to 7 (`-1 px` apparent Y shift), and setting
`rolling | in_air | y_vel = -$300`.

**What to check.** Any solid object with a state-dependent break /
launch / monitor-pop / arrow-trigger:
1. Per-player conditions must read the *contacting* player's state, not
   a per-frame cached "saw rolling once" flag. Use the player parameter
   of `onSolidContact` directly: `player.getRolling()`,
   `player.getAir()`, etc.
2. ROM `Obj32_Main`, `Obj26_Main` (monitor), `Obj13_Main` (spring) check
   the *object's* `status(a0) & standing_mask` (the per-player standing
   bits the SolidObject routine sets on the OBJECT, indexed by which
   player is standing on it) plus that player's *animation* — never a
   global "was rolling" cache. Per-player anim is cached in
   `breakableblock_mainchar_anim` (objoff_32) and
   `breakableblock_sidekick_anim` (objoff_33), giving each player its
   own state byte.
3. Side / bottom contact almost never breaks ROM solids. Most breakable
   objects only fire on `contact.standing()` (the player is currently
   seated on top via SolidObject's standing path). A rolling player
   hitting the underside gets a CEILING collision via SolidObject and
   bonks; they do not break the block. Do not invent synthetic
   `touchBottom()` / `touchSide()` break paths "for completeness".
4. `update(...)` should not mutate player-derived caches that are read
   from another player's `onSolidContact`. If you need per-player
   state, key it on the player instance (IdentityHashMap) or read it
   inside the contact callback.

**ROM citation.** `docs/s2disasm/s2.asm:48889-48959` (Obj32 / BreakableBlock):
- 48891-48892 cache MainCharacter.anim / Sidekick.anim per-player
- 48899-48901 run SolidObject, then `andi.b #standing_mask, d0`
- 48911-48913 check each standing player's anim against `AniIDSonAni_Roll`
- 48940-48950 `Obj32_BouncePlayer` sets rolling + in_air + `y_vel = -$300`

**Originating commit.** `<pending>` (trace frontier advancement loop iter
3: HTZ F979 BreakableBlock leaked main-player rolling state into Tails'
side-contact callback, knocking Tails airborne with the wrong character
as victim).

---

## P12 -- Angle-based player detection ported as simplified bounding-box + facing guard

**Symptom.** A patrolling badnik that should attack the player when in
horizontal range either never attacks (player always on "wrong" facing
side), attacks at completely wrong frames, or its position drifts from
the ROM trace's position by tens of pixels over the trace lifetime
because it skips ROM attacks. Trace shows ROM badnik moving with periodic
stationary attack pauses; engine badnik continuously oscillates with no
pauses.

**Root cause.** ROM uses `Obj_GetOrientationToPlayer`
(`docs/s2disasm/s2.asm:72320-72346`) which picks the *closest* player
(MainCharacter vs Sidekick) by absolute horizontal x distance, then
returns `d2 = obj.x - closest_player.x` (signed word). The badnik's
attack-trigger condition is typically:

```
addi.w #$60, d2          ; d2 += 0x60 (offset)
cmpi.w #$C0, d2          ; compare against 0xC0
blo.s <attack>           ; branch if (d2 + 0x60) < 0xC0 unsigned
```

This is the canonical "is player within roughly +/-96px horizontally"
test. **There is no Y-axis check and no facing-direction guard.** The
test is symmetric around the badnik's x_pos.

A naive engine port replaces this with `Math.abs(player.x - obj.x) <=
DETECT_X_RANGE && Math.abs(player.y - obj.y) <= DETECT_Y_RANGE &&
playerIsLeft == facingLeft`. The added Y check is wrong (ROM has none).
The `playerIsLeft == facingLeft` guard is fundamentally wrong: it means
the badnik only attacks when the player is "in front" of it, but ROM
attacks regardless of facing. Plus the engine usually only reads
MainCharacter, ignoring the Sidekick selection ROM does.

**What to check.** When porting any badnik that uses
`Obj_GetOrientationToPlayer` followed by an `addi.w/cmpi.w/blo` pattern:

1. The check is horizontal-only -- do NOT add a Y bounds gate.
2. Compare against the *closest* of MainCharacter and Sidekick. Iterate
   `services().sidekicks()` and pick the sprite with minimum
   `Math.abs(currentX - sprite.getCentreX())`, preferring MainCharacter
   on ties (ROM `bls.s` keeps MainCharacter when distances equal).
3. The detection result is `(currentX - player.getCentreX() + 0x60) &
   0xFFFF < 0xC0`. Implement as a literal unsigned 16-bit window, not
   as separate "in front" + "in range" Java conditionals.
4. Preserve ROM ordering inside the patrol routine: detection runs
   BEFORE the direction-timer decrement and BEFORE `ObjectMove`. When
   attack triggers, the badnik enters attack state and does NOT move
   on the trigger frame.
5. ROM does NOT update `render_flags` / `x_flip` when transitioning to
   attack; the badnik continues to face whichever direction it was
   patrolling. Don't reset facing on attack entry.
6. Projectile fire direction in `loc_38C22`-style spawn routines uses
   ONLY MainCharacter (not the closest player) to decide left vs right.
   ROM `cmp.w x_pos(a2),d0 ; blo.s + ; neg.w d1` with `a2 = MainCharacter`.
7. Position at spawn for the projectile is `x_pos/y_pos` (no -8 or other
   offset) unless the ROM explicitly adds one.

**ROM citation.** `docs/s2disasm/s2.asm:75923-75976` (ObjA5 / Spiny
patrol-detection-attack flow), `docs/s2disasm/s2.asm:72320-72346`
(`Obj_GetOrientationToPlayer` closest-player selection +
`d2 = obj.x - player.x`), `docs/s2disasm/s2.asm:76050-76070`
(`loc_38C22` spike spawn uses MainCharacter for direction).

**Originating commit.** `<pending>` (trace frontier advancement loop iter
4: CPZ F844 Spiny detection ported with `dx <= 0x80 && dy <= 0x40 &&
playerIsLeft == facingLeft` bounding-box + facing guard; replaced with
ROM horizontal-only closest-player gate. Reduced CPZ trace errors
494 -> 434; frontier still at f844 due to a residual ~22 px Spiny
position drift -- the new detection fires at a different first frame
than ROM, indicating subtle timing/order details remain. Pattern itself
applies to every patrolling-shooter badnik that uses the angle-based
attack gate. ObjA4/Asteron, ObjA6/SpinyOnWall, and at least three S3K
analogues share the same idiom.)

---

## P13 -- SlopedSolidProvider.getSlopeBaseline() returning halfHeight when ROM slope table encodes absolute offsets

**Symptom.** Player rolling-air-falls toward a sloped platform/seesaw/bridge
that ROM cleanly lands them on, but the engine fires "no contact" and lets
them fall through. Frontier divergence appears as a y / y_speed / air mismatch
on the exact landing frame: ROM has `y_speed=0`, `air=0`, snapped y position;
engine still has `y_speed = previous + gravity`, `air=1`, kept falling. HTZ1
trace F988 surfaced this when Sonic should land on the tilted Seesaw (slope
sample 20, ROM surface = `obj_y - 20 = 980`, player bottom = 983, ROM lands;
engine `baseY = obj_y - (20 - 8) = 988`, computes `relY = -5 < minRelY = 0`,
returns null, no landing.)

**Root cause.** `SlopedSolidProvider.getSlopeBaseline()` controls
`resolveSlopedContact`'s shift between the raw slope sample and the
effective surface Y:

```
slopeOffset = slopeSample - slopeBase
baseY       = anchorY - slopeOffset
relY        = playerCenterY - baseY + 4 + playerYRadius
```

ROM `SlopedPlatform_cont` (s2.asm:35787-35793) reads the slope sample
directly: `move.b (a2,d0.w),d3 / ext.w d3 / move.w y_pos(a0),d0 /
sub.w d3,d0`.  There is no baseline subtraction; the slope table value
IS the offset from object_y to the surface.  S2's slope tables (e.g.
`byte_21C8E` for the seesaw) already encode that convention, so
`getSlopeBaseline()` must return `0` for any S2 slope object whose
data is sampled directly by SlopedPlatform / SlopedPlatform_cont.

The `COLLISION_HEIGHT` baseline pattern came from S1's GHZ bridge /
SLZ seesaw slope tables, which encode the surface offset relative to
the object's bottom edge (so the table needs to be shifted up by
COLLISION_HEIGHT to land on the object center).  S2 slope data
does NOT follow that convention -- positive values lift the surface
above object_y, negative values drop below.

**What to check.** When porting any S2/S3K SlopedSolidProvider:

1. Find the ROM routine that calls `SlopedPlatform` / `SlopedPlatform_cont`
   (or the S3K equivalent SolidObjCheckSloped2 / loc_19EB6).
2. Look at the slope data values relative to ROM `y_pos(a0) - d3`:
   - If slope[mid] ~= 0 and the surface visually sits at object_y,
     the table encodes absolute offsets -> `getSlopeBaseline()` returns 0.
   - If slope[mid] ~= halfHeight and the surface visually sits at
     `object_y - halfHeight`, the table is relative to object bottom
     -> `getSlopeBaseline()` returns halfHeight (rare; S1 only).
3. Check `Sonic2/S3k BridgeObjectInstance.getSlopeBaseline()` and
   `AizFlippingBridgeObjectInstance.getSlopeBaseline()`: both return
   0 with the comment "Height table values are absolute offsets from
   obj_y".  That is the S2/S3K-standard pattern; objects that copy
   the S1 `COLLISION_HEIGHT` baseline without checking will fail
   the same way.
4. Confirm via trace replay: compute `surfaceTop = anchorY - rawSlopeSample`
   and `playerBottom = playerY + yRadius + 4`. ROM lands when
   `surfaceTop - playerBottom` is in `(-16, 0]`. The engine's `relY`
   must equal `-(surfaceTop - playerBottom) = playerBottom - surfaceTop`
   for `relY` to land in `[0, 16)`. If `slopeBase != 0` shifts the
   apparent surface by halfHeight, the landing window shifts the
   same amount and the player misses.

**ROM citation.** `docs/s2disasm/s2.asm:35787-35793` (SlopedPlatform_cont
slope sample -> surface Y, direct subtraction, no baseline);
`s2.asm:47103-47115` (Obj14_UpdateMappingAndCollision setup before
calling SlopedPlatform).  Engine equivalent:
`SlopedSolidProvider.getSlopeBaseline()`, `ObjectManager.
resolveSlopedContact` (`baseY = anchorY - slopeOffset`).

**Originating commit.** `<pending>` (trace frontier advancement loop iter
5: HTZ F988 Sonic missed Seesaw landing because SeesawObjectInstance
returned `COLLISION_HEIGHT` from `getSlopeBaseline()`, shifting the
effective surface 8 px below ROM's, so `relY` went negative and the
contact resolver returned null.  Companion fix: `Obj14_Main` calls
`Obj14_SetMapping` exactly once per frame, but the engine was calling
`updateAngle()` twice (once with the recomputed target, once
unconditionally with currentAngle), advancing `mapping_frame` at
twice ROM's rate during tilt transitions.  Both bugs surfaced
together at the f988 frontier.)

---

## P14 -- Engine edge-triggers ENEMY touch response but ROM polls every frame

**Symptom.** A badnik (or other ENEMY-category object) that should be
destroyed when the player transitions into an attacking state while
already overlapping the badnik stays alive instead. Trace shows ROM
killed the badnik AND applied the canonical Touch_KillEnemy bounce
(typically `y_vel -= $100` for side, `y_vel = -y_vel` for top, `y_vel +=
$100` for upward); engine has the badnik still in the active list and
the player's y_vel/x_vel unchanged. The MCZ trace surfaced this at
frame 825 when Sonic was standing in the Crawlton's bounding box with
`invulnerable_time != 0` (Touch_NoHurt path), then pressed B+Down to
start a Spindash. ROM `Touch_Enemy` re-read `anim(a0)` on frame 825,
saw `AniIDSonAni_Spindash`, ran Touch_KillEnemy and set
`y_vel = -$100`. Engine had Sonic in the badnik's overlap from a
previous frame so the touch callback was edge-suppressed.

**Root cause.** The engine's ReactToItem-equivalent loop in
`ObjectManager.TouchResponses.processCollisionLoop` historically
edge-triggered ENEMY (and SPECIAL) touch callbacks via:
```
boolean shouldTrigger = category == BOSS
        || category == HURT
        || provider.requiresContinuousTouchCallbacks()
        || !overlappingSet.contains(instance);
```
The intent was an optimisation: once a player overlaps an enemy and
the response runs, don't re-run while the overlap persists. ROM has
no such gate. ROM `Touch_Loop` (s2.asm:84502-84548) iterates every
frame and `Touch_Enemy` (s2.asm:84807-84890) re-reads
`status_secondary(a0)` and `anim(a0)` each call. The decision between
Touch_KillEnemy and Touch_ChkHurt is therefore made per-frame, not
once on entry, so any later state transition into Spindash / Roll /
Invincibility immediately switches the response to kill-and-bounce.

The same gate suppresses cases like rolling into an enemy from above
while frame-0 of the overlap is a hurt path (e.g. spike contact then
roll), and any pattern where touch happens before attacking state is
set.

**What to check.** When implementing a badnik (any
`AbstractBadnikInstance` subclass) or any object that uses the
`ENEMY` `TouchCategory`:
1. Do NOT rely on `wasAlreadyDestroyed` / "overlap memory" as a
   correctness mechanism. The framework now polls every frame, so the
   object's own `onPlayerAttack` must be idempotent and self-gating
   (`isDestroyed()` check at top of `onPlayerAttack`,
   pre-destruction state captured before mutating).
2. ROM Touch_Enemy reads the current `anim` byte each call. If your
   badnik wants to differentiate behaviour based on player state
   (e.g. boss multi-sprite hit_count vs Touch_KillEnemy bounce),
   read the player's current animation in `onPlayerAttack`, not
   cached state from `update()`.
3. The fix targets `ENEMY` only. SPECIAL is still edge-triggered to
   keep monitors / object-controlled SolidObjects from firing
   responses every frame. If a SPECIAL object genuinely needs every-
   frame polling (e.g. a state machine that wants to observe the
   player's animation transition), implement
   `TouchResponseProvider.requiresContinuousTouchCallbacks()` and
   return true.
4. Trace replay test for any new badnik: run the badnik trace replay
   with the player overlapping it from frame N-1 and starting a
   spindash on frame N. ROM will fire Touch_KillEnemy on frame N;
   engine must too. If it doesn't, check whether your badnik has
   custom logic that depends on a one-shot trigger flag.

**ROM citation.** `docs/s2disasm/s2.asm:84502-84548` (`TouchResponse`
/ `Touch_Loop` — iterates every frame, no overlap memory),
`s2.asm:84807-84890` (`Touch_Enemy` / `Touch_KillEnemy` — reads
`status_secondary(a0)` / `anim(a0)` per call; the same routine handles
both hurt and kill outcomes based on current state).  Same per-frame
loop in S1 (`docs/s1disasm/_incObj/sub ReactToItem.asm`) and S3K
(`docs/skdisasm/sonic3k.asm` `Collision_response_list` dispatcher).
Engine equivalent: `ObjectManager.TouchResponses.processCollisionLoop`
(`shouldTrigger` decision around the `!overlappingSet.contains`
edge gate).

**Originating commit.** `<pending>` (trace frontier advancement loop
iter 6: MCZ F825 Sonic standing in Crawlton overlap was edge-trigger-
suppressed from the per-frame Touch_Enemy re-check; once continuous
polling was restored for ENEMY, Spindash entry on f825 fired
Touch_KillEnemy correctly and produced y_vel = -0x100, advancing the
MCZ frontier from 825 to 862 (619 errors -> 618 errors; same touch
loop also unmasks any future "transition-into-attack-during-overlap"
pattern in S1/S2/S3K badnik implementations).

---

## P15 -- Object update() resolves solid contacts BEFORE refreshing slope / collision state

**Symptom.** A sloped solid (seesaw, bridge, tilting platform) ports the
ROM logic but the rider's Y trails ROM by one frame during a state
transition.  Frontier divergence appears the frame the slope changes shape:
ROM has snapped to the new surface; the engine still uses the previous
frame's surface and lags 1-8 px depending on the slope-table delta.  HTZ1
trace f1017: ROM transitioned `mapping_frame` 2 -> 1 (tilted -> flat) and
sampled `SLOPE_FLAT[20]=5`, putting Sonic at `y=0x03D0`; engine still had
`mapping_frame=2` (xFlip) at sample time, sampled `SLOPE_TILTED[27]=2`
and put Sonic at `y=0x03D3`.  The state itself transitioned at the right
time -- it just happened AFTER the engine had already finished its
slope sample.

**Root cause.** ROM `Obj14_Main` (seesaw) order:

```
Obj14_Main:
    move.b objoff_3A(a0),d1     ; previous-frame target
    btst   #p1_standing_bit,status(a0)
    beq.s  loc_21A12             ; if no standing, alt path
    ; ... recompute d1 from player x ...
Obj14_UpdateMappingAndCollision:
    bsr.w  Obj14_SetMapping      ; <-- mapping_frame UPDATED here
    lea    (byte_21C8E).l,a2     ; slope table selected from NEW mapping_frame
    btst   #0,mapping_frame(a0)
    beq.s  +
    lea    (byte_21CBF).l,a2
+
    move.w x_pos(a0),-(sp)
    moveq  #0,d1
    move.b width_pixels(a0),d1
    moveq  #8,d3
    move.w (sp)+,d4
    bra.w  SlopedPlatform        ; <-- collision uses NEW state
```

ROM updates the slope-relevant state, **then** runs the collision call.
A naive engine port often inverts this:

```java
public void update(int frame, PlayableEntity player) {
    SolidCheckpointBatch batch = services().solidExecution().resolveSolidNowAll();
    // ... read standing players from batch ...
    int target = calculateTargetAngle();
    updateAngle(target);   // <-- TOO LATE: mapping_frame changes
                            //     after collision has already run
}
```

Because `getSlopeData()` and `isSlopeFlipped()` both key off
`mappingFrame`, sampling them inside `resolveSolidNowAll` returns the
previous frame's surface; the rider's Y lands one transition behind ROM.

**What to check.** When implementing any solid object whose collision
geometry depends on a tickable state byte (mapping_frame, animation
frame, internal angle, depression amount, slope offset table choice),
look at the ROM `Obj_Main` to see where the state update happens
relative to the SolidObject / SlopedPlatform / PlatformObject call:

1. If ROM updates the state *before* the collision call, the engine
   must update its equivalent *before* `resolveSolidNowAll()` /
   `checkpointAll()`.  Compute the target from the PREVIOUS frame's
   standing-player references (kept as instance fields) plus the
   CURRENT player x positions -- ROM does exactly that via
   `btst p1_standing_bit, status(a0)` on the entry-frame status and
   `move.w x_pos(a1), d0` on the current player position.
2. The previous-frame standing references are valid because ROM
   itself reads them before the collision call clears / re-sets them.
   In the engine, the latched `standingPlayer1` / `standingPlayer2`
   fields from the end of the prior `update()` give the same view.
3. If ROM updates the state *after* the collision call (e.g. some
   gravity / move-by-velocity is interleaved with collision in ROM
   order), preserve that placement -- don't blindly hoist state
   updates to the top of `update()`.
4. Watch for sibling helpers that already follow the ROM order:
   `BridgeObjectInstance.update()` calls `updateDepressionState()`,
   `rebuildBridgeShape()`, `updateSlopeData()` FIRST and only then
   runs `checkpointAll()`.  That is the correct template.
5. The fix is purely a reorder; no new state, no new flags.  Don't
   try to "buffer" the previous-frame slope -- match ROM order and
   the divergence disappears.

**ROM citation.** `docs/s2disasm/s2.asm:47037-47115` (Obj14_Main +
Obj14_UpdateMappingAndCollision: target compute -> Obj14_SetMapping
-> SlopedPlatform).  Same idiom in S1 Obj48 / Obj49 (S1 seesaw) and
in S3K AIZ flipping bridge and Tension bridge -- they all update
the slope-relevant state byte before calling the slope-collision
routine.  Engine equivalent: any solid object's
`update(int, PlayableEntity)` that calls
`services().solidExecution().resolveSolidNowAll()` should perform
slope-shape state updates BEFORE that call, mirroring ROM order.

**Originating commit.** `<pending>` (trace frontier advancement loop
iter 7: HTZ f1017 Sonic running across HTZ Seesaw during
mapping_frame=2 -> mapping_frame=1 transition.  Engine resolved
SlopedPlatform contact with stale mapping_frame=2, sampling
SLOPE_TILTED[27]=2 instead of ROM's SLOPE_FLAT[20]=5, leaving
Sonic 3 px low.  Reorder fixed: target compute + updateAngle()
now run BEFORE resolveSolidNowAll(), so the slope sampled uses the
freshly-transitioned mapping_frame.  Frontier: HTZ f1017 (943 errs)
-> f1084 (1180 errs, +67 frames).  Pattern applies to every
state-driven sloped solid that uses MANUAL_CHECKPOINT solid
execution.)



## P16 -- Monitor (and ROM SolidObject_AtEdge) push bit set on any grounded side contact, not just movingInto

**Symptom.** Player breaks a monitor (Touch_Monitor rolling break path) from
the side, but ROM sets `in_air=1` while the engine keeps the player grounded
(or vice-versa: engine fires in_air when ROM keeps grounded).  The break itself
is correct; the divergence is on the airborne transition that `Obj26_Break`
applies based on the monitor's accumulated standing/pushing bits.  MCZ f862:
ROM `air=1`, engine `air=0` after Sonic rolls into a monitor he pushed two
frames earlier.

**Root cause.** ROM `SolidObject_cont` -> `SolidObject_LeftRight` ->
`SolidObject_AtEdge` (`docs/s2disasm/s2.asm:35241-35248`) sets the OBJECT's
push bit and the player's pushing bit for ANY side contact when the player
is not airborne:

```
SolidObject_AtEdge:
    sub.w d0,x_pos(a1)
    btst #status.player.in_air,status(a1)
    bne.s SolidObject_SideAir       ; air -> no push, exit
    move.l d6,d4
    addq.b #pushing_bit_delta,d4
    bset d4,status(a0)              ; <- set OBJECT's push bit unconditionally
    bset #status.player.pushing,status(a1)
```

The engine's monitor-specific `resolveMonitorContact` was more restrictive,
gating `pushing` on `movingInto && !exactEdgeOverlap`:
```
boolean pushing = !player.getAir() && movingInto && !exactEdgeOverlap;
```
Position correction and speed zeroing should be gated on movingInto (ROM
`SolidObject_StopCharacter` runs only when moving toward the object), but the
push BIT is set even at rest on an edge.

`Obj26_Break` (s2.asm:25502-25515) keys its airborne transition off the
monitor's accumulated push/standing bits, so missing the push set when the
player was at rest on the monitor's edge cascades into "broken monitor
should fling Sonic upward but engine keeps him grounded" two frames later
when he rolls back into it.

**What to check.** When implementing any SolidObjectProvider whose ROM
counterpart routes through SolidObject_cont (i.e. uses the standard
SolidObject family, not a custom side-handler):

1. The push bit on the OBJECT and player must be set for any grounded side
   contact, regardless of movingInto.  `SolidObject_AtEdge` (ROM) /
   `resolveMonitorContact` / `resolveContactInternal` (engine) should
   compute `pushing = !player.getAir()` for the side branch.
2. Position correction (`sub.w d0, x_pos`) and speed zeroing
   (`SolidObject_StopCharacter`'s `move.w #0, inertia` / `x_vel`) remain
   gated on movingInto.  ROM only zeroes speed when the player is moving
   into the object; at rest or moving away the position correction and
   speed zeroing skip but the push bit still gets set.
3. The push bit must persist across rolling frames.  ROM
   `SolidObject_Monitor_Sonic` returns early when the player is rolling,
   leaving the previously-set push bit intact.  The engine's
   IdentityHashMap (or per-player flag) used by listeners like
   `MonitorObjectInstance.onSolidContact` must not be cleared while the
   player is in the overlap area.
4. Listener objects (BreakableBlock, Spring, Monitor, etc.) that key
   behaviour on "player was previously standing/pushing" should rely on
   the accumulated state, not on a single-frame contact check.
5. Cross-game: S1 `SolidObject_AtEdge` (s1disasm/_incObj/sub SolidObject.asm,
   `Solid_Centre` -> push bit set) and S3K's
   `SolidObjectFull2_1P` (sonic3k.asm `loc_1E06E` notes: "ROM loc_1E06E
   sets Status_Push for any grounded side contact") use the same rule.

**ROM citation.** `docs/s2disasm/s2.asm:35241-35253` (`SolidObject_AtEdge`),
`s2.asm:25502-25515` (`Obj26_Break` consumes the bits), `s2.asm:25448-25467`
(`SolidObject_Monitor_Sonic` returns early for rolling, preserving bits).
Engine equivalent: `ObjectManager.resolveMonitorContact` push gate
(`src/main/java/com/openggf/level/objects/ObjectManager.java`) and any
listener consuming the accumulated push/standing state in
`onSolidContact` (`MonitorObjectInstance`, etc.).

**Originating commit.** `<pending>` (trace frontier advancement loop iter
8: MCZ f862 Sonic spindash-rolled into a Monitor at the exact same Y, ROM
fired Obj26_Break with the monitor's p1_pushing bit still set from a prior
edge-rest frame and put Sonic airborne (y_vel=0, air=1) while the engine's
resolveMonitorContact had gated `pushing` on movingInto && !exactEdgeOverlap,
so the bit was never set, mainCharacterPushing stayed false, and the break
left Sonic grounded.  Fix: drop the movingInto/exactEdgeOverlap gates from
the push bit (keep them only on position correction / speed zeroing).
Frontier: MCZ f862 (618 errs) -> f903 (612 errs, +41 frames).  Pattern
applies to every solid object whose ROM uses standard SolidObject_cont
side resolution.)

---

## P17 -- Child object out_of_range uses own X instead of parent anchor, causing chunk-boundary unload

**Symptom.** A parent object's child (Seesaw ball, segmented body part, attached
hazard, swung weapon, etc.) silently vanishes shortly after the parent appears
on screen. The parent stays alive and behaves normally otherwise — its update
runs, players can stand on it, scroll handlers see it — but a feature that
depends on the child (ball launching the rider, body segment contact, attached
hazard hitbox) never fires. Trace replay shows the parent's state advancing
correctly while a player launch / contact event that requires the child silently
fails to trigger. The engine's `parent.ball` (or equivalent child reference) is
non-null and `child.destroyed=false`, but the child is no longer in
`ObjectManager.dynamicObjects` and is not in `execOrder[child.slot]`.

**Root cause.** The ROM dispatcher routes Obj14 (and similar parent+child
objects) through a single `Obj_Index` table that calls `MarkObjGone2` with
`d0 = objoff_30(a0)`. Crucially, `Obj14_Ball_Init` (`docs/s2disasm/s2.asm:47151`)
stores the parent seesaw's `x_pos` into `objoff_30(a0)` BEFORE applying the
ball's `addi.w #$28, x_pos(a0)` offset. So the ball's out-of-range check uses
the PARENT'S x position, not its own offset position. Both parent and ball
share the same camera-relative chunk reference and unload together.

A naive engine port adds the child via `addDynamicObjectAfterCurrent(child)` and
lets it fall back to the default `getOutOfRangeReferenceX()` which returns
`getX()` (the child's current position). The default
`ObjectManager.isOutOfRangeS1` formula rounds X to the nearest 128-byte chunk
(`objX & 0xFF80`) and compares against `(cameraX - 128) & 0xFF80`. For a
flipped seesaw whose parent is at `0x1520` and child at `0x1520 - 0x28 = 0x14F8`:
- Parent chunk: `0x1520 & 0xFF80 = 0x1500`
- Child chunk: `0x14F8 & 0xFF80 = 0x1480`
When the camera advances to `cameraX >= 0x1580`, the screen-rounded value is
`0x1500`. The parent's distance is `0x1500 - 0x1500 = 0` (in range), but the
child's distance is `0x1480 - 0x1500 = 0xFF80` unsigned = 65408 (way past the
640 threshold). The child alone is unloaded, leaving the parent's `child` field
pointing at an instance that's no longer in dynamicObjects.

**What to check.** For any parent+child pair where ROM's `Obj_Init` does the
sequence:

```
move.w x_pos(a0),objoff_30(a0)  ; save parent x BEFORE offset
addi.w #$xx,x_pos(a0)            ; apply child offset
[...]
move.b status(a0),status(a1)     ; copy parent status (with flip) to child
move.l a0,objoff_3C(a1)          ; store parent pointer for the child
```

then the child needs `getOutOfRangeReferenceX()` to return the parent's x_pos
(or, equivalently, the value saved in `objoff_30`). This applies to:
- Seesaw ball (`Obj14_Ball_Init` at s2.asm:47142)
- Any moving-solid child that's offset from its parent
- Body segments on Caterkiller / Crawl / multi-piece badniks
- Attached projectiles fired at a fixed offset from a parent gun mount

The fix is one method:

```java
@Override
public int getOutOfRangeReferenceX() {
    return parentCenterX;  // ROM objoff_30(a0) = parent x_pos
}
```

For S3K, the same rule applies whenever the disasm shows
`move.w x_pos(a0),objoff_30(a0)` immediately followed by `addi.w` /
`subi.w` on `x_pos(a0)` inside the child's init routine.

**ROM citation.** `docs/s2disasm/s2.asm:47151` (`Obj14_Ball_Init` saves seesaw_x
in objoff_30 before applying the ball offset), `s2.asm:46996`
(`Obj14` dispatcher's `move.w objoff_30(a0),d0 / jmpto JmpTo_MarkObjGone2`),
`s2.asm:30040-30057` (`MarkObjGone2` uses d0 = objoff_30 for the chunk-rounded
out_of_range comparison). Engine equivalent: any `AbstractObjectInstance`
subclass spawned as a positional offset from a parent should override
`getOutOfRangeReferenceX()` to return the parent's centre X.

**Originating commit.** `<pending>` (trace frontier advancement loop iter 9:
HTZ f4305 Sonic and Tails landed on the second (flipped) HTZ seesaw, but the
seesaw's ball had been unloaded the instant the camera entered chunk 0x1500
because the ball at 0x14F8 lived in chunk 0x1480. The seesaw's `ball` field
held a now-orphaned reference; `Obj14_Ball_Main`'s `objoff_3A` delta check
never ran, so the seesaw's `storedY=0x0760` launch velocity sat unused and
the players never went airborne. Adding `getOutOfRangeReferenceX()` =
parent seesaw x kept the ball alive while the parent is in range, restoring
the launch path. Frontier: HTZ f4305 (396 errs) -> f5044 (446 errs, +739 frames).
Pattern applies to every parent+offset-child pair in S2/S3K.)

---

## P18 -- Object bounce routines preserve unwritten velocity / inertia fields

**Symptom.** A bumper / launcher trace diverges immediately after touch:
ROM keeps the player's previous `x_vel`, `y_vel`, or `inertia`, while the
engine zeros one of them and changes the next solid-object contact or sidekick
follow state. CNZ f339/f340 surfaced this when ObjD7 Hex Bumper bounced Sonic
into Obj86 Flipper; ROM preserved the unwritten velocity component, but the
engine initialized both axes and cleared inertia.

**Root cause.** Many ROM object handlers write only the fields they need for
the chosen branch. ObjD7 left/right bounce writes `x_vel` only; up/down adjusts
the existing `x_vel` and writes `y_vel`; `ObjD7_BounceEnd` sets air / clears
status bits and plays sound, but does not clear `inertia`. A naive engine port
initializes `xVel = 0`, `yVel = 0`, then calls `setGSpeed(0)`, erasing ROM
state that later routines still observe.

**What to check.** For every object bounce / launch branch, list the exact ROM
writes. Preserve any velocity or inertia field the branch does not write:
initialize from the live player value, mutate only the written component, and
avoid clearing `gSpeed` unless the disassembly writes `inertia(a1)`.

**ROM citation.** `docs/s2disasm/s2.asm:59403-59454`
(`ObjD7_BouncePlayerOff`: left/right write only `x_vel`; up/down adjust
existing `x_vel` and write `y_vel`; bounce end does not clear inertia).

**Originating commit.** `<pending>` (trace frontier advancement loop iter 10:
CNZ ObjD7 Hex Bumper velocity preservation and TouchResponse timing advanced
the CNZ frontier from f202 to f507).

---

## P19 -- Shared monitor icon rewards use pre-move velocity tests

**Symptom.** A monitor reward applies one frame too early. In CNZ this made a
speed-shoes monitor double the player's air-control acceleration one physics
frame before the ROM did.

**Root cause.** The ROM monitor-content routine tests the icon's `y_vel`
before moving it. If the current rise step adds `$18` and lands exactly on
zero, the routine returns; the reward branch runs on the next object update.
A shared engine helper that applies the reward immediately after changing
`iconVelY` from negative to zero is one frame early.

**What to check.** For shared monitor code, verify S1, S2, and S3K before
changing the base routine. If all games match, keep it shared and cite all
three. If one differs, gate the behaviour at the owning abstraction instead
of changing every game implicitly.

**ROM citation.** S2 `docs/s2disasm/s2.asm:25618-25631`; S1
`docs/s1disasm/_incObj/2E Monitor Content Power-Up.asm:35-43`; S3K
`docs/skdisasm/sonic3k.asm:40723-40753` and S3-side
`docs/skdisasm/s3.asm:33392-33421`.

**Originating commit.** `<pending>` (trace frontier advancement loop iter 11:
CNZ speed-shoes monitor reward timing advanced the CNZ frontier from f976 to
f1146).

---

## P20 -- Level-event globals may need pre-object update order

**Symptom.** An object waits one frame too long for a zone-global routine to
finish. In CNZ, ObjD6 Point Pokey kept Sonic riding the cage for one extra
frame because the shared slot-machine manager was updated in the engine's late
zone-feature phase, after Point Pokey had already checked completion.

**Root cause.** S2 `LevEvents_CNZ` calls `SlotMachine` from the level-event
path, before the relevant object observes the global state. Treating the slot
machine as an ordinary late zone feature changed the producer/consumer order:
the global routine became inactive one frame too late from the object's point
of view.

**What to check.** When an object reads a zone-global manager or RAM flag,
locate the ROM writer and the ROM object execution order before choosing the
engine hook. Keep the ordering fix at the smallest owning scope. For CNZ this
means the slot-machine tick belongs in the S2 CNZ pre-physics/level-event
phase, while CNZ bumpers remain in the normal zone-feature update phase.

**ROM citation.** `docs/s2disasm/s2.asm:21494-21500`
(`LevEvents_CNZ` calls `SlotMachine`) and `docs/s2disasm/s2.asm:58827-58840`
(`SlotMachine` routine dispatch).

**Originating commit.** `<pending>` (trace frontier advancement loop iter 12:
CNZ Point Pokey / slot-machine ordering advanced the CNZ frontier from f1691
to f3830).

---

## P21 -- Sonic 2 object streaming is X-window only

**Symptom.** A placement object spawns hundreds of frames late when the route
passes above or below it. In CNZ, ObjD4 Big Block at `x=$0F00,y=$03A0` did not
exist until the camera-Y band reached it, leaving the oscillating block 537
updates behind the ROM at the first contact.

**Root cause.** S2 `ObjectsManager_GoingForward` / `ObjectsManager_GoingBackward`
load objects directly from the X-window scan via `ChkLoadObj`; there is no
`Camera_Y_pos` eligibility test in that path. Reusing a shared vertical spawn
filter for S2 exec-then-load placement silently delayed off-route objects whose
movement later affects the player.

**What to check.** For S2 object placement bugs, compare the object's update
count or phase against the ROM, not just its current coordinates. Keep the
vertical-filter bypass scoped to S2 placement; S3K and other games may still
need their own spawn-window rules.

**ROM citation.** `docs/s2disasm/s2.asm:32870-32950`
(`ObjectsManager_GoingBackward` / `ObjectsManager_GoingForward` call
`ChkLoadObj` from the X-window scan).

**Originating commit.** `<pending>` (trace frontier advancement loop iter 13:
S2 exec-then-load placement bypassed the vertical spawn filter and advanced the
CNZ frontier from f3830 to f3906).

---

## P22 -- Object-local capture may need previous-frame status

**Symptom.** A recapture on the same object is off by one pixel even though the
current object position, subtype, and player speeds match ROM. In CNZ, Tails
landed/re-landed on Obj85 LauncherSpring at the right X and subpixel, but the
engine treated the contact like a fresh non-rolling Tails capture and applied
the first-capture lift a second time.

**Root cause.** Some object routines observe player status as it existed before
the engine's current-frame normalization path. S2 Obj85's vertical capture
calls `SolidObject_Always_SingleCharacter`, then writes rolling/radii after the
standing bit is set. If engine-side physics has temporarily cleared the current
rolling flag before the object sees the contact, a port that checks only
`player.getRolling()` cannot distinguish a fresh Tails capture from a rolling
recapture. Use the player's recorded previous status when the ROM path depends
on that pre-normalized state.

**What to check.** For object-controlled capture/release paths, compare the
previous and current trace status bits before adding character-specific
position corrections. If a correction exists only to bridge engine top-left
hitbox semantics, gate it with the ROM-visible status history, not only the
current engine flag. Keep the hook object-local; do not change shared
SolidObject behavior for one object's capture quirk.

**ROM citation.** `docs/s2disasm/s2.asm:57520-57540`
(`Obj85_Up`/`loc_2AD26` captures after `SolidObject_Always_SingleCharacter`
sets the standing bit, then writes rolling/y_radius/x_radius).

**Originating commit.** `<pending>` (trace frontier advancement loop iter 14:
S2 Obj85 Tails recapture used previous-frame rolling status and advanced the
CNZ frontier from f3906 to f3957).

---

## P23 -- Full-solid bottom overlap may use live rolling y_radius

**Symptom.** A rolling airborne player is pushed sideways by a moving full
solid after ROM would already reject the vertical overlap. In CNZ, Sonic kept
ROM-correct air-control speed through ObjD4 Big Block, but the engine's solid
resolver classified a side contact at `relY=93`, snapped him 2 px right, and
zeroed `x_speed`.

**Root cause.** S2 `SolidObject_cont` adds the live `y_radius(a1)` to `d2`,
then doubles that same value for the lower reject bound. Rolling players
therefore use the smaller rolling radius on both the top and bottom halves.
The engine's default full-solid lower-half rule intentionally uses the taller
standing radius for some S2/S3K solids, but ObjD4 is a direct `SolidObject`
caller and needs the live-radius path.

**What to check.** When porting a full solid:
1. Read the exact helper it calls (`SolidObject`, `SolidObjectFull2`,
   `PlatformObject`, monitor variant, slope variant).
2. If the helper builds the lower bound from the same `d2 += y_radius(a1)`
   value used for the top bound, override
   `fullSolidBottomOverlapUsesCurrentYRadiusOnly(...)` on that object.
3. Keep the override object-local. Do not broaden shared lower-half behaviour
   unless all affected games and object families have been checked.
4. Trace symptom to look for: live position/speed matches ROM before solid
   contact, then the engine applies a sideways push/zero while ROM reports no
   contact and preserves air-control acceleration.

**ROM citation.** `docs/s2disasm/s2.asm:58348-58356` (ObjD4 passes
`d1=$2B,d2=$20,d3=$21` to `SolidObject`), `s2.asm:35135-35166`
(`SolidObject_cont` adds live `y_radius(a1)` to `d2`, doubles it, and rejects
when `d3 >= d4`).

**Originating commit.** `<pending>` (trace frontier advancement loop iter 15:
CNZ ObjD4 Big Block lower-half overlap used standing-radius height in the
engine and falsely side-pushed Sonic at f4074. Overriding
`fullSolidBottomOverlapUsesCurrentYRadiusOnly` advanced the CNZ frontier from
f4074 / 197 errors to f4121 / 227 errors).

---

## P24 -- Landing radius restore is not always shared across games

**Symptom.** A sidekick or object-controlled player stays one pixel too high
or too low on the first grounded frame after a launch/capture release, even
though position, subpixels, and speeds matched the previous frame. In CNZ,
Tails landed from Obj85 with ROM and engine both at `y=$0331`, then the engine
snapped to `y=$0330` on the next grounded frame and missed the following
Obj72-area airborne/rolling handoff.

**Root cause.** S2 `Tails_ResetOnFloor` only restores Tails's standing radii
inside the rolling branch. If Tails lands while already non-rolling but still
has object-written rolling radii (`y_radius=$0E,x_radius=7`), ROM leaves those
radii in place. The shared engine cleanup previously restored any non-rolling
custom radii to standing defaults, which is correct for S3K
`Player_TouchFloor` but not for S1/S2 reset-on-floor routines.

**What to check.** Before moving radius or landing cleanup into shared
playable code, read the reset routine for each game and character:

1. S1/S2 Sonic apply fixed roll-clear lifts only when rolling is set.
2. S2 Tails applies the one-pixel lift and `$0F/$09` radius restore only when
   rolling is set.
3. S3K restores default radii before checking roll state and uses the
   current-radius delta model.
4. Gate shared cleanup through the owning feature flag (or a narrower object
   hook) instead of assuming all games consume the same landing radii.

**ROM citation.** `docs/s2disasm/s2.asm:40629-40636`
(`Tails_ResetOnFloor_Part2` branches past radius restore when rolling is
clear), `docs/s2disasm/s2.asm:37781-37786` (S2 Sonic fixed rolling lift), and
`docs/skdisasm/sonic3k.asm:24341-24363` (S3K Player_TouchFloor restores
defaults and applies radius delta).

**Originating commit.** `<pending>` (S2 CNZ frame 5328 Tails Y mismatch was
caused by the shared non-rolling radius restore; gating it behind the S3K
radius-delta feature advanced the CNZ frontier from f5328 / 221 errors to
f5336 / 219 errors).

---

## P25 -- Obj85 preserved roll must suppress stale held jump, not fresh delayed press

**Symptom.** Tails either jumps too early out of the vertical Obj85 stopper
handoff, or never performs the later chamber-exit jump. In CNZ, letting all
delayed jump state through made Tails launch around frame 4028 while ROM stayed
grounded in the stopper. Suppressing all delayed jump state while the preserved
roll flag was set fixed that early launch but missed ROM's later fresh delayed
jump press at frame 5336.

**Root cause.** S2 Tails CPU copies Sonic's delayed logical input word before
the follow/filter path. Obj85's object-local preserved-roll handoff can leave a
held jump bit in that delayed sample while Tails is still grounded, but that is
not equivalent to a fresh press. The stale held bit must be suppressed during
the grounded preserved-roll handoff; the later fresh delayed jump press must
remain available so `Tails_Jump` can set `y_vel=-$680` and rolling air state.

**What to check.**
1. Keep Obj85 preserved-roll jump filtering object-scoped through the existing
   preserved-roll flag; do not change generic sidekick CPU jump semantics.
2. Distinguish delayed held jump from delayed jump press. Grounded preserved
   Obj85 frames with no fresh press should clear both held and press before
   `PlayableSpriteMovement` derives a new edge from held input.
3. Once Tails is airborne, do not clear held jump; the hold is used by jump
   height handling.
4. If another object needs similar handling, add a named object-owned marker
   rather than broadening the Obj85 gate.

**ROM citation.** `docs/s2disasm/s2.asm:38939-38946` (Tails CPU copies the
delayed `Ctrl_1_Logical` sample), `docs/s2disasm/s2.asm:57611-57625` (Obj85
vertical release path), and `docs/s2disasm/s2.asm:36996-37070`
(`Sonic_Jump`/`Tails_Jump` setup, including the `-$680` jump velocity).

**Originating commit.** `<pending>` (S2 CNZ frame 5336 Tails failed to enter
air+rolling because preserved-roll filtering cleared a fresh delayed jump
press. Suppressing only grounded stale held jump advanced the CNZ frontier from
f5336 / 219 errors to f5399 / 215 errors).

---

## P26 -- Riding solids can own stale logical horizontal input windows

**Symptom.** A player accelerates one or more frames before ROM while standing
on a moving/scripted solid, even though the trace CSV/BK2 input column already
shows the direction and the ROM `state_snapshot` sees no `move_lock` or
control lock. In CNZ, Sonic's right input appeared at frame 5997 while riding
ObjD5, but ROM inertia stayed zero until frame 6000.

**Root cause.** Some solid-helper/object phase combinations expose BK2-aligned
input before the player movement routine consumes the corresponding logical
horizontal value for the sampled physics row. Treating that as a game-wide
input offset breaks nearby jump/input edges. The timing belongs to the current
riding object/helper, not to all S2 movement.

**What to check.**
1. When a trace shows early acceleration while the player is riding a concrete
   solid, inspect the object's exact helper (`PlatformObject`, `PlatformObjectD5`,
   direct `SolidObject`, or bespoke checkpoint) before changing shared input
   handling.
2. Prefer the `SolidObjectProvider.staleHorizontalLogicalInputFramesWhileRiding`
   hook with a default of zero. Override it only on the object whose helper
   proves the stale window.
3. Keep existing object-specific windows on the owning object. SCZ Tornado and
   CNZ ObjD5 use the hook; shared movement should not branch on game id or
   object id directly.

**ROM citation.** `docs/s2disasm/s2.asm:58435-58443` (ObjD5 calls
`PlatformObjectD5` after its state routine), `docs/s2disasm/s2.asm:35617-35657`
(`PlatformObjectD5` continued-riding/skip-existing-platform helper), and
`docs/s2disasm/s2.asm:35402-35420` (`MvSonicOnPtfm` writes rider position).

**Originating commit.** `<pending>` (S2 CNZ frame 5997 Sonic accelerated three
frames before ROM while riding ObjD5. Moving stale horizontal suppression to a
per-solid hook and opting in ObjD5 advanced the CNZ frontier from f5997 / 197
errors to f6018 / 289 errors while S1 GHZ and S2 EHZ stayed green).

---

## P27 -- SolidObject_Always objects must bypass offscreen full-solid gates

**Symptom.** A sidekick or offscreen-adjacent player passes through the side of
an invisible/full solid even though ROM zeros `x_vel` and `inertia` at the
solid edge. In CNZ, Tails reached Obj74 at `x=$1535` while airborne/rolling;
ROM stopped him against the left edge, but the engine reported Obj74 as
`no-touch` and kept accelerating.

**Root cause.** Obj74 does not call the regular `SolidObject` helper. It calls
`SolidObject_Always`, whose disassembly comment explicitly says Obj74/Obj30
check solidity even if the object is offscreen. Applying the shared
sidekick-on-screen/full-solid offscreen gate to Obj74 skips exactly the side
contact ROM still resolves.

**What to check.**
1. For every solid object, identify the exact helper it calls before assuming
   the normal render/on-screen gate applies.
2. If the helper is `SolidObject_Always` or
   `SolidObject_Always_SingleCharacter`, override
   `bypassesOffscreenSolidGate()` on that object/class.
3. Keep the bypass per object/helper. Do not disable the shared offscreen gate
   for all S2 solids, because the regular `SolidObject` P2 path still gates on
   sidekick render state.

**ROM citation.** `docs/s2disasm/s2.asm:34863-34873`
(`SolidObject_Always` / `SolidObject_Always_SingleCharacter`) and
`docs/s2disasm/s2.asm:46152-46161` (`Obj74_Main` calls
`SolidObject_Always` after deriving subtype dimensions).

**Originating commit.** `<pending>` (S2 CNZ frame 6018 Tails missed Obj74's
left-edge side stop because the engine applied the offscreen sidekick full-solid
gate to a `SolidObject_Always` caller).

---

## P28 -- SPECIAL touch objects use Touch_Sizes radii and object-specific bounce tails

**Symptom.** A SPECIAL object bounces or triggers several frames too early, or
the immediate post-bounce physics fields differ even though the written
velocity matches ROM. In CNZ, ObjD8 applied `y_vel=-$700` at frame 6276 while
ROM was still falling, then later zeroed `inertia` even though ROM preserved
`$040E`.

**Root cause.** S2 `collision_flags` low six bits index the `Touch_Sizes` table,
whose bytes are X/Y radii, not full width/height. ObjD8 sets
`collision_flags=$D7`, selecting `Touch_Sizes[$17] = 8,8`; replacing this with
an approximate center-distance box changes the trigger frame. Also read the
object's common bounce tail literally: ObjD8's `loc_2C806` sets in-air and
clears roll-jump/pushing/jumping, but does not clear `inertia`.

**What to check.**
1. Decode `collision_flags & $3F` and use the `Touch_Sizes` radii before
   writing any manual SPECIAL-object overlap.
2. Prefer the shared touch-response rectangle math. If an object must poll its
   own `collision_property` or cooldown bytes, copy the ROM rectangle shape
   locally rather than inventing a center-distance approximation.
3. Do not assume all object rebounds clear `inertia`. Check for an explicit
   `clr.w inertia(a1)` in the object routine before calling `setGSpeed(0)`.

**ROM citation.** `docs/s2disasm/s2.asm:59570` (ObjD8
`collision_flags=$D7`), `docs/s2disasm/s2.asm:84623` (`Touch_Sizes[$17] =
8,8`), and `docs/s2disasm/s2.asm:59687-59692` (ObjD8 bounce tail does not
clear `inertia`).

**Originating commit.** `<pending>` (S2 CNZ frame 6276 early ObjD8 bounce and
frame 6281 inertia mismatch; fixing ObjD8 touch radii and preserving inertia
advanced the CNZ frontier to frame 6815).

---

## P29 -- Moving objects may own bespoke out_of_range delete bounds

**Symptom.** A moving object disappears before ROM would delete it, so later
collisions or bounces are missing even though the spawn record and movement
routine are correct. In CNZ, the moving ObjD7 Hex Bumper from spawn
`x=$1FF8,y=$028C,subtype=1` was gone by frame 8082; ROM still had it alive at
slot 38 and used it to launch Sonic left.

**Root cause.** Not every object tail-calls the standard `MarkObjGone` /
`out_of_range` macro with the current object X. Moving ObjD7 runs its animation
and movement, then checks both `objoff_30` and `objoff_32` movement bounds. It
only deletes when both bounds are outside the camera window, so a single-X
generic delete check can remove it too early.

**What to check.**
1. Read the end of the object routine before assuming the shared
   counter-based out-of-range path is correct.
2. If the ROM routine tests range endpoints, parent anchors, or other custom
   words, prefer the narrowest per-object hook over a game-wide behavior flag.
3. Keep stationary subtypes on the shared path unless the stationary ROM
   routine also bypasses the standard macro.

**ROM citation.** `docs/s2disasm/s2.asm:59489-59510` (moving ObjD7 tests
`objoff_30` and `objoff_32`, displaying if either bound remains in range and
deleting only after both fail the range check).

**Originating commit.** `<pending>` (S2 CNZ frame 8082 missing moving ObjD7;
keeping ObjD7 alive by its ROM movement bounds advanced the CNZ frontier to
frame 8419).

---

## P30 -- `bmi` countdown timers fire at -1, not 0

**Symptom.** A badnik or object waits one frame longer than ROM before a state
transition. Timer-indexed trace fields (e.g. `timer`, `scriptTimer`) match ROM
for one extra frame, then diverge by 1 as the state-machine branch fires one
frame late. OOZ1/OOZ2 trace: Octus hovered one frame lower than ROM (4 px
extra downward displacement) because the rise delay fired late.

**Root cause.** ROM countdown loops use:
```
subq.w  #1, timer(a0)
bmi.s   <transition>
```
`bmi` ("branch if minus") fires when the result is negative — that is, when the
timer decrements from 0 to -1 (the **next** frame after it reaches 0). Java
`if (timer <= 0)` fires when the timer reaches 0, one frame early. The correct
port is `timer--; if (timer < 0)`.

**What to check.** For every countdown timer in a ROM object routine, find the
branch instruction: `bmi` fires at -1, `beq` fires at 0, `bne` fires as long as
the value is non-zero. Do not use `<= 0` as a default "timer expired" check —
read the actual branch opcode in the disassembly.

**ROM citation.** `docs/s2disasm/s2.asm:59958-59967` (Octus `ObjA2_DelayBeforeRise`:
`subq.w #1, d1 / bmi.s ObjA2_Rise` — fires when d1 wraps to -1). The same pattern
appears in virtually every S2/S3K object's wait/delay phase.

**Originating commit.** `31567cb35 Fix S2 Octus collision size and rise-state
transition timing`.

---

## P31 -- Property table byte-offset mistakenly divided as entry index

**Symptom.** Object selects the wrong art frame, wrong collision dimensions,
wrong speed constant, or wrong movement waypoint from an object properties table.
The wrong frame or dimension is consistent (not random) and is typically one or
two entries away from the correct one. MTZ LongPlatform (Obj65) trace: platforms
always selected the wrong mapping_frame (and wrong props) because the table index
was off by a factor of 2 in the byte dimension.

**Root cause.** ROM property tables are addressed by byte offset via
`lea Table(pc,d0.w), a1` — `d0` is a byte offset, not a 0-based entry index.
A 2-byte-per-field table addressed with `d0 = subtype << 2` means the byte
offset is `subtype * 4`; the engine's `entryIndex = d0 / 4` collapses the
separate byte strides used for the first field (byte offset ÷ 2 → entry) and the
art frame index (byte offset ÷ 4 → frame). These two derived values must be
computed from the raw byte offset independently:
```
int entryIndex = d0 >> 1;   // byte offset / sizeof(word) = entry number
int frameIndex = d0 >> 2;   // byte offset / sizeof(longword) = art frame
```
Using `d0 >> 2` for both silently picks the wrong entry in the props table while
occasionally getting the frame right, giving inconsistent but deterministic
errors.

**What to check.** When a ROM routine has:
```
moveq   #<N>, d0
move.b  subtype(a0), d1
mulu.w  d1, d0
lea     SomeTable(pc,d0.w), a1
move.w  (a1)+, firstField(a0)   ; first word read
move.w  (a1),  secondField(a0)  ; second word read
```
trace the byte offset `d0` at the `lea` and at every subsequent `move.w`. Each
derived value (frame index, dimension, speed) is the byte offset divided by the
field size. Don't collapse them to a single Java `index = d0 / totalStride`.

**ROM citation.** `docs/s2disasm/s2.asm:52366-52376` (`Obj65_Properties` table,
2 words per entry), `s2.asm:52386-52394` (`Obj65_Init`: `mulu.w #4,d0 /
lea Obj65_Properties(pc,d0.w),a1 / move.w (a1)+,d1 / move.w (a1),d2`).

**Originating commit.** `a574826b6 Fix S2 MTZ SteamSpring timing and
MTZLongPlatform props-lookup`.

---

## P32 -- Solid checkpoint must run before state-machine position update, not after

**Symptom.** A player riding a vertically-moving solid is one frame behind ROM's
position during the transition. Specifically, the player lands on the rising
platform's pre-move surface in the engine but ROM places them at the pre-move
surface too — yet ROM then launches them (spring-fires / snaps position) one
frame before the engine does. MTZ1 trace: SteamSpring didn't fire until one frame
after ROM because the engine ran the state machine (which moved the spring up)
before the solid checkpoint.

**Root cause.** ROM `Obj42` (`loc_26688`) calls `SolidObject_Always_SingleCharacter`
**first** at the top of every frame, then the state machine branches run and update
`y_pos`. This means the solid contact sees the **pre-move** surface. The engine
naively put the state machine first (updating `yOffset` → new platform Y) and
then ran `checkpointAll()`, so players saw the post-move surface and the spring
fire was delayed by one frame.

```
; ROM order (s2.asm:52030-52049):
loc_26688:
    bsr SolidObject_Always_SingleCharacter   ; contact on PRE-move y
    bsr Obj42_StateMachine                   ; NOW update y_pos

; Naive engine order (WRONG):
void update() {
    updateStateMachine();   // moves yOffset first
    checkpointAll();        // contact on POST-move y  ← one frame late
}
```

The same rule applies when an object must fire a spring/launch from the contact
result before the position changes: capture the contact batch before the
state machine, then use the batch to decide whether to launch.

**What to check.** For any vertically (or horizontally) moving solid: find the
ROM dispatch order in `Obj_Main`. If the SolidObject/PlatformObject call appears
before the movement code, put `checkpointAll()` / `resolveSolidNowAll()` at the
top of `update()` before any position mutation. See also P15 (slope state update
order) for the complementary rule on sloped solids.

**ROM citation.** `docs/s2disasm/s2.asm:52030-52049` (`loc_26688`:
`SolidObject_Always_SingleCharacter` called BEFORE the `Obj42` state-machine
dispatch). `s2.asm:52121-52124` (`loc_2678E`: spring fire inside the standing
player loop, also pre-move).

**Originating commit.** `a574826b6 Fix S2 MTZ SteamSpring timing and
MTZLongPlatform props-lookup`.

---

## P33 -- PhysicsFeatureSet flags must be set to the correct ROM value when guard code is added

**Symptom.** A PhysicsFeatureSet flag is added to gate new behaviour, the guard
is wired into the physics code path, and all existing tests still pass — but the
trace diverges at the exact frame the guarded behaviour should fire, because the
flag is set to the wrong default for one or more games. CNZ2 trace regressed from
f1490 to f936 when `pinballLandingPreservesPinballMode` was added with `false` in
`SONIC_2` even though S2 ROM preserves pinball mode on landing.

**Root cause.** PhysicsFeatureSet constants (`SONIC_1`, `SONIC_2`, `SONIC_3K`)
are long field lists. A new field typically has a conservative default in the
shared constructor, and the per-game factory constant must explicitly set the
correct value. It is easy to add the flag, wire the guard, verify that S1/S3K
behave correctly, and forget to flip the flag for S2 (or vice versa). Unit tests
rarely cover the exact multi-frame state required to exercise a newly-gated
branch, so the error is silent until the trace replay runs.

**What to check.** When adding a PhysicsFeatureSet field:
1. Open the disassembly for ALL three games and find the equivalent routine.
2. Set the correct value in `SONIC_1`, `SONIC_2`, and `SONIC_3K` factory
   constants immediately — never leave any game at the fallback default unless
   you have verified the disassembly confirms it.
3. If a game's behaviour is unknown, mark it `TODO` in a comment beside the
   constant and log it in `docs/KNOWN_DISCREPANCIES.md`, but do not leave the
   wrong value silently in place.
4. Run the relevant trace replay for all three games after the change.

**ROM citation.** `docs/s2disasm/s2.asm:37770-37771` (`Sonic_ResetOnFloor` S2:
`bclr #status.player.in_pinball_mode,status(a1)` is absent — pinball mode is
NOT cleared on landing), `s2.asm:40625-40626` (S2 `Tails_ResetOnFloor_Part2`:
same omission). Compare S1 which does NOT have pinball mode at all, and S3K
which has its own `Player_TouchFloor`.

**Originating commit.** `7eaa19993 Preserve S2 pinball_mode mirror across
landing to restore CNZ2 frontier`.

---

## P34 — `Ctrl_1` byte-read is just-pressed edge, not held state

**Symptom.** An object that grabs or releases the player on button press
triggers one frame too early (on the frame the button is held from a prior
action) rather than only on a fresh press.

**Root cause.** ROM reads `move.w (Ctrl_1).w, d0` — this loads a word where
the **high byte is `Ctrl_1_Held`** (currently held buttons) and the **low
byte is `Ctrl_1_Press`** (buttons pressed this frame only). Any subsequent
`andi.b #buttons, d0` or `btst #button, d0` operates on the **low byte**
(`Ctrl_1_Press`), so the check tests just-pressed state. Engine methods like
`isJumpPressed()` return held state; `isJumpJustPressed()` (or its
equivalent) returns the just-pressed edge. If the object grabs on a held
check, it immediately releases the player the same frame the player arrived
via a held jump.

**What to check.** Whenever the ROM does
`move.w (Ctrl_1).w, d0 / andi.b #..., d0` or
`move.w (Ctrl_2).w, d0 / andi.b #..., d0`, the engine must use
`isJumpJustPressed()` / `isActionJustPressed()`, not the `*Pressed()` held
variants. This applies equally to grab-initiation, grab-release, and any
other button-gated state transition in object code.

**ROM citation.** `Obj7F_Action` (`s2.asm:56083-56106`):
`move.w (Ctrl_1).w,d0 / andi.b #button_B_mask|button_C_mask|button_A_mask,d0`.

**Originating commit.** `d14450c48 Fix S2 MCZ VineSwitch (0x7F) release input
and Tails grab`.

---

## P35 — Sidekick pass left as "Player 2 deferred" stub

**Symptom.** Object behaves correctly for Sonic but Tails can never interact
with it, or the interaction counter diverges whenever Tails reaches the object
first (wrong player targeted, wrong timing).

**Root cause.** ROM typically processes both `MainCharacter` and `Sidekick`
in sequence: `lea (MainCharacter).w,a1 / bsr Obj_Action` then
`lea (Sidekick).w,a1 / bsr Obj_Action` (or an analogous loop). Engine
implementations often stub the second pass with a `// Player 2 deferred`
comment and never fill it in. As a result, Tails cannot grab vines, trigger
switches, or interact with any object that explicitly processes both sprites.

**What to check.** After implementing the main-player interaction, search the
disassembly for a second `a1` load targeting `Sidekick` or `Ctrl_2` within
the same sub-routine. Implement the sidekick pass using
`services().sidekicks()` before committing. Do not leave "Player 2 deferred"
stubs in production code; they silently break two-player trace parity.

**ROM citation.** `Obj7F_Action` (`s2.asm:56071-56080`):
`lea (MainCharacter).w,a1 / move.w (Ctrl_1).w,d0 / bsr.s Obj7F_Action` then
`lea (Sidekick).w,a1 / move.w (Ctrl_2).w,d0 / bsr.s Obj7F_Action`.

**Originating commit.** `d14450c48 Fix S2 MCZ VineSwitch (0x7F) release input
and Tails grab`.

---

## P36 — `move.b #2,routine(a1)` clears the Hurt routine; engine must call `setHurt(false)`

**Symptom.** After a state-changing object interaction (spring launch, vine
release, conveyor exit, teleporter, etc.) the engine player's airborne gravity
stays at +$30 instead of +$38, and `Sonic_UpVelCap` (-$FC0) is skipped.
Trace-replay y_speed diverges by exactly the cap delta (0x40) plus an extra
0x08-per-frame gravity-step shortfall when the player launched from a hurt
state.

**Root cause.** ROM dispatches the player's outer state through
`Obj01_Index` / `Obj02_Index`: `0=Init, 2=Control, 4=Hurt, 6=Dead, 8=Gone`.
The Hurt routine (`Obj01_Hurt loc_1B12C`) runs its own physics tick with
`addi.w #$30,y_vel(a0)` and no upward velocity cap. ROM objects that "wake"
the player into normal play write `move.b #2,routine(a1)` directly,
unconditionally clearing the Hurt routine. The next player tick then dispatches
to `Obj01_Control` -> `Obj01_MdAir`, which uses +$38 gravity and the
`Sonic_UpVelCap` cap (`s2.asm:37113`).

The engine encodes Hurt as a boolean `hurt` field on the sprite, and the
trace test maps `isHurt() -> rtn=04` for comparison.
`PlayableSpriteMovement.airbornePhysics()` short-circuits `doJumpHeight()`
(and therefore the velocity cap) when `hurt=true`;
`AbstractPlayableSprite.getGravity()` returns 0x30 instead of 0x38 when hurt.
Without an explicit `setHurt(false)` in the engine object code, the
spring/vine/launcher launches Sonic but leaves him in the hurt physics
regime.

**What to check.** For every object that writes `move.b #2,routine(a1)` in
ROM:
- The Vertical/Diagonal Spring branches (`Obj41_Up`, `Obj41_Down`,
  `Obj41_DiagonallyUp`, `Obj41_DiagonallyDown` — `s2.asm:33735, 34023,
  34090, 34173`) all clear the routine. Note that `Obj41_Horizontal`
  does NOT, because horizontal springs keep the player grounded.
- `Touch_ChkValue` post-hurt recovery branches.
- Object-control exits (e.g., `loc_298E6` in `Obj7F` for vine grab).
- Teleporters, launchers, and tubes that take over the player and then
  release it back into Control.

Mirror the ROM by calling `player.setHurt(false)` alongside the velocity /
position assignment that ROM does under the `move.b #2,routine(a1)` line.
Do NOT also reset `invulnerable_time` — ROM keeps the existing value, and
`AbstractPlayableSprite` already exposes invulnerability via a separate
counter that the spring does not touch.

**ROM citation.** Spring up `Obj41_Up loc_189CA` (`s2.asm:33728-33735`):
```
loc_189CA:
    move.w  #(1<<8)|(0<<0),anim(a0)
    addq.w  #8,y_pos(a1)
    move.w  objoff_30(a0),y_vel(a1)
    bset    #status.player.in_air,status(a1)
    bclr    #status.player.on_object,status(a1)
    move.b  #AniIDSonAni_Spring,anim(a1)
    move.b  #2,routine(a1)                ; <-- clears Hurt
```

**Originating commit.** Fix S2 vertical/diagonal Spring (Obj41) clears Hurt
routine on launch — advances MCZ2 frontier from 925 to 1006.

---

## P37 — Parent-spawner factory returning `null` re-spawns children every frame

**Symptom.** A "parent-spawner" object (e.g. MTZ Obj6C with subtype bit 7 set)
spawns N children to form a cluster. The cluster appears to work at first, but
something downstream is subtly off — landing positions for the player are 5-10
pixels misaligned, child phase relationships drift, or a single child slot at
an unusually high slot index (engine slot 127 for S2 with 112 dynamic slots)
turns out to be the one the player actually stands on. The "lost" object
debug formatter / `eng-near` window may even omit the player's standing
target because the formatter truncates to the first ~12 slots by index.

The MTZ2 case: Sonic landed 7 pixels below the ROM landing height because the
engine had silently spawned dozens of redundant conveyor cohorts, and the
cohort he physically landed on was one freshly re-spawned several frames after
camera entry, not the original cohort the ROM tracks.

**Root cause.** The factory pattern used `return null` to mean "I spawned my
children via `ObjectManager.addDynamicObject`; do not register a parent
instance." The `inlineCreateObject` / `applyPendingSpawns` paths interpret a
`null` factory result as "spawn failed" and release the pre-allocated parent
slot WITHOUT calling `registerActiveObject(spawn, instance)`. Because the
parent `ObjectSpawn` is never added to `activeObjects`, the placement's
"already loaded" gate (`!activeObjects.containsKey(spawn)`) lets the parent
re-enter `sortedNewSpawns` every frame the camera keeps the chunk in window.
Each re-entry spawns a full N-child cohort, filling slots and producing
multiple parallel cohorts of the same cluster with different waypoint phases.

ROM does not have this problem because the parent's `Obj6C_Init` reuses the
parent's own SST entry as the first child (`movea.l a0,a1`), overwriting its
subtype with the first child's subtype (clearing bit 7). The parent slot
becomes a regular child object that runs `Obj6C_Main` from the next frame on
and never re-enters the `loc_28112` parent-spawn branch.

**Fix.** Mirror the ROM "parent becomes first child" pattern. The factory
constructs the first child from `layout[0]` (using the parent's own
`ObjectSpawn` slot), spawns the remaining N-1 children via
`addDynamicObject`, and returns the first child instance. Because the factory
now returns non-null, `registerActiveObject` runs, `activeObjects` contains
the spawn, and the placement does not re-enter the spawn into
`sortedNewSpawns` on subsequent frames.

**What to check.**
1. Any factory in `*ObjectRegistry.java` that returns `null` on a "parent
   subtype set" path. Greppable pattern: `(subtype & 0x80) != 0` /
   `return null;` inside a static factory.
2. Cross-reference with ROM `Init`: if the ROM uses `movea.l a0,a1` (or
   equivalent) to write the first child into the parent slot, replicate by
   returning the first child from the factory rather than `null`.
3. Verify after the fix that `eng-near` only shows the expected N child
   instances at the cluster (no duplicates at incrementing slot numbers,
   no slot index >> the rest of the cluster).
4. Watch for child base-position inheritance: parent-spawned children use
   the parent's `x_pos/y_pos` as `objoff_30/objoff_32` (waypoint base), not
   their own per-child layout offset. The engine constructor must accept an
   explicit `baseX/baseY` distinct from the child's spawn position.

**ROM citation.** S2 Obj6C `loc_28112`/`Obj6C_LoadSubObject`
(`docs/s2disasm/s2.asm:54269-54301`): `movea.l a0,a1` sets the first child
target to the parent's own slot; the `dbf` loop then `JmpTo8_AllocateObject`s
remaining children. After the loop `addq.l #4,sp; rts` unwinds the
intermediate stack frame and returns to `Obj6C`'s post-jsr instruction.

**Originating commit.** Fix S2 MTZ2 Conveyor (Obj6C) parent factory re-spawn
loop — engine now returns the first child from the factory instead of `null`,
so `activeObjects` records the spawn and placement stops re-spawning the
cluster every frame. Advances MTZ2 frontier y mismatch from 7 px to 1 px at
frame 305.

---

## P38 — `SolidObject` contact mutates velocity before hurt helpers read it

**Symptom.** Upside-down spikes or similar full-solid hazards hurt the player
at the right frame, but the post-hurt Y/subpixel or knockback state is off by
1-2 pixels. Trace context often shows the engine using a "pre-contact" velocity
while the ROM has already zeroed or changed that velocity inside the solid
routine.

**Root cause.** S2 Obj36 calls `SolidObject` first, then checks the returned
touch mask and calls `Touch_ChkHurt2`. `SolidObject_cont` / inside-contact
branches may mutate `y_vel(a1)` before returning. `Touch_ChkHurt2` reads the
current `y_vel(a1)`, not a saved pre-solid value, then subtracts
`y_vel<<8` from `y_pos` before `HurtCharacter`.

**What to check.** When an object calls `SolidObject` or
`SolidObject_Always*` before a hurt/helper routine, preserve the ROM call
order. Do not feed hurt helpers an ObjectManager pre-contact snapshot unless
the ROM saved one explicitly. Also check S2 `SolidObject_cont` lower-Y bounds:
it doubles the live `y_radius(a1)`, so rolling underside contact can differ
from ports that reuse a standing/default radius.

**ROM citation.** S2 Obj36 upside-down spike path
(`docs/s2disasm/s2.asm:29260-29283`) calls `SolidObject` before
`Touch_ChkHurt2`; `Touch_ChkHurt2` subtracts current `y_vel<<8`
(`docs/s2disasm/s2.asm:29297-29312`). S2 `SolidObject_cont` doubles live
`y_radius(a1)` for its lower-Y reject bound
(`docs/s2disasm/s2.asm:35156-35169`).

**Originating commit.** Fix S2 MTZ3 Obj36 spike contact/hurt ordering -- MTZ3
frontier advances from frame 3603 to frame 3617.

---

## P39 -- Same object ID can dispatch to different objects by subtype/routine

**Symptom.** A placement with an already-implemented object ID does nothing, or
uses the wrong movement/contact math, even though the engine has a class for
that ID. Trace context shows the ROM object ID matches the engine object ID, but
the ROM routine byte and nearby state do not match the implemented path.

**Root cause.** S2 objects often multiplex distinct behaviours under one ID.
Obj06 is both the EHZ spiral and the MTZ cylinder: `Obj06_Init` branches to
`Obj06_Cylinder` when the subtype is negative, setting `routine=4`, while
non-negative subtypes follow the spiral-path controller. Treating every Obj06
placement as a spiral leaves MTZ cylinder placements invisible to the player.

**What to check.** During init-porting, do not stop once the object ID maps to a
class. Read the init routine's subtype branches and routine writes, then make
sure each branch has an engine mode. For sine/cosine helpers, also preserve the
ROM return register: `CalcSine` returns sine in `d0` and cosine in `d1`; a path
that multiplies `d1` must use cosine, not sine.

**ROM citation.** S2 Obj06 init/cylinder path (`docs/s2disasm/s2.asm:46720-46811`,
`s2.asm:46853-46931`): negative subtype branches to `Obj06_Cylinder`; the
active rider path calls `CalcSine` and multiplies `d1` by `$2800` for the
vertical offset.

**Originating commit.** Fix S2 MTZ3 Obj06 cylinder mode -- MTZ3 frontier
advances from frame 4280 to frame 4656.

---

## P40 -- Native `x_pos` / `y_pos` writes must preserve the sibling subpixel byte

**Symptom.** Player integer position matches ROM after an object snaps or carries
the player, but `x_sub` or `y_sub` diverges. The next movement frame then drifts
by one or more pixels because ROM kept the existing subpixel residue while the
engine cleared it.

**Root cause.** ROM object code often writes only the native position word:
`move.w x_pos(a0),x_pos(a1)` or `move.w y_pos(a0),y_pos(a1)`. That changes the
integer word and leaves the adjacent subpixel byte/word untouched. Engine code
that uses `setCentreX(...)` / `setCentreY(...)` for a playable sprite rewrites a
higher-level coordinate and can clear or recompute the subpixel state.

**What to check.** When porting object code that writes `x_pos(a1)` or
`y_pos(a1)` directly to a playable sprite, use `NativePositionOps`:
`writeXPosPreserveSubpixel`, `writeYPosPreserveSubpixel`, or the corresponding
add helpers. Reserve raw centre setters for code paths where ROM also resets the
subpixel half or where the target is an object-local/non-playable coordinate.

**ROM citation.** S2 Obj69 Nut align and screw modes
(`docs/s2disasm/s2.asm:53566-53568`, `s2.asm:53579-53582`,
`s2.asm:53626-53629`) write `move.w x_pos(a0),x_pos(a1)` while leaving
`x_sub(a1)` intact.

**Originating commit.** Fix S2 MTZ3 Obj69 nut x_pos snap preserves player
x_sub -- MTZ3 frontier advances from frame 4793 to frame 5143.

---

## How to add a new entry

When a trace-replay-bug-fixing iteration commits an object fix whose root
cause is a class of bug (not a one-off):

1. Identify which pitfall pattern category fits, or pick the next P-number
   if none fit.
2. Append a new entry following the template above.
3. Reference the originating commit hash so future readers can see the full
   diff and test cases.
4. Mirror to `.claude/skills/s2-implement-object/rom-pitfalls.md` in the
   same commit. Use the commit trailer `Skills: updated`.
5. If the pattern is cross-game (S2 + S3K share it), copy the entry into
   `.agents/skills/s3k-implement-object/rom-pitfalls.md` with the analogous
   S3K disasm citation.
