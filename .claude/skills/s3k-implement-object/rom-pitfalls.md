# Sonic 3 & Knuckles Object Implementation — ROM Behavioural Pitfalls

Anti-pattern catalogue accumulated from trace replay frontier advancement
fixes. **Read this before starting Phase 2 of `s3k-implement-object/SKILL.md`**
and check each entry against the ROM behaviour of the object you're porting.

Each entry is a class of bug where the *naive* engine port diverges from ROM
in a way that survives unit tests but breaks trace replay parity. The
catalogue grows when a trace-replay-bug-fixing iteration commits an object
fix whose root cause could occur in any not-yet-implemented object — see
`.agents/skills/trace-replay-bug-fixing/SKILL.md` Phase 5 for the update
loop.

The patterns below were first surfaced during S2 frontier advancement (see
`.agents/skills/s2-implement-object/rom-pitfalls.md` for the narrative
origin) but are cross-game: each applies to S3K objects unless the entry
explicitly says otherwise.

Format per entry: pattern name, symptom, root cause, what to check during
implementation, ROM citation, originating fix commit.

---

## P1 — Touch-response directional/state guards diverge from ROM

**Symptom.** Object rejects a rolling / spindash / invincible / shield
touch under a condition ROM doesn't gate on.

**Root cause.** Engine adds an extra gate (player below object, specific
direction, specific timer state) that ROM's touch-response routine doesn't
apply. ROM uses overlap-only for the kill; position only chooses bounce
direction.

**What to check.** When porting `onPlayerAttack` for an S3K badnik or
interactive object, list every gate ROM applies in `Touch_ChkValue` /
`Touch_KillEnemy` (or the S3K equivalent in `sonic3k.asm`). Reproduce only
those.

**ROM citation.** Sonic 3 & Knuckles touch-response routines live in
`docs/skdisasm/sonic3k.asm` near the `Touch_ChkValue` / `Touch_KillEnemy`
labels. S2 equivalent at `docs/s2disasm/s2.asm:84807-84890`.

**Originating commit (S2).** `c2d998751 fix(s2): CPZ Grabber badnik
rolling-kill independent of vertical position`.

---

## P2 — ROM multi-frame init collapsed into one engine frame

**Symptom.** Trace divergence appears N frames before the ROM state
transition fires, then the transition is one frame early. `scriptTimer` /
counter values differ by exactly N.

**Root cause.** ROM dispatches object init across multiple frames: outer
`ObjXX_Init` writes routine and returns, the next frame enters the inner
case 0 which performs the real init. The engine constructor often does
both in zero frames, collapsing ROM's two-frame init into one.

**What to check.** When porting init that has both an outer dispatch label
(`Obj_Init`) and an inner main-routine case 0 with non-trivial setup,
preserve the frame count. Don't pre-resolve the inner init in the
constructor unless ROM's dispatch never actually returns between them.

**ROM citation.** S3K object init pattern lives near each `ObjXX_Index`
table. Search for `bsr.w Obj_Init` or analogous. S2 origin example at
`docs/s2disasm/s2.asm:78271-78284, 78368-78372`.

**Originating commit (S2).** `44d7939e1 fix(s2): WFZ Tornado collapsed
two-frame init compensation`.

---

## P3 — Global object state vs ROM per-player object state bytes

**Symptom.** Knuckles or Tails (player 2) interaction with an object is
suppressed by a state flag Sonic set, or vice versa. Sidekick fails to
trigger a spring / hover-platform / bumper Sonic just used.

**Root cause.** Engine uses a single field for state that ROM tracks
per-player at SST offsets. When the first player flips the global, the
second player sees "already triggered".

**What to check.** List every SST byte ROM reads/writes. If the offset is
`objoff_36`, `objoff_37`, or any per-player pair, the engine must use a
per-sprite map (`IdentityHashMap<AbstractPlayableSprite, Integer>`), not a
global field.

**ROM citation.** S3K per-player object state at the `objoff_36/37`
convention; search `sonic3k.asm` for `objoff_36(a0)` and `objoff_37(a0)`.
S2 origin example at `docs/s2disasm/s2.asm:57870-57879`.

**Originating commit (S2).** `3cb72b6af fix(s2): CNZ Flipper per-player
launch cooldown + ROM-accurate y_pos`.

---

## P4 — Character-dependent coordinate adjustments where ROM uses a fixed offset

**Symptom.** Sidekick Y diverges from leader Y by a character-specific
amount after a rolling launch or hurt — usually 1-5 px.

**Root cause.** Engine reaches for a helper like
`getRollHeightAdjustment()` when ROM has a literal `addq.w #N, y_pos(a1)`
(constant for all characters).

**What to check.** When ROM source shows `addq.w #<literal>, y_pos(a1)`,
port that as a literal `setCentreYPreserveSubpixel((short)(preCentreY +
<literal>))`. Don't substitute a character-aware helper.

**Originating commit (S2).** `3cb72b6af` (secondary).

---

## P5 — SolidObject returns non-solid prematurely on state transition

**Symptom.** Rider drops from a moving solid on the exact frame of an
internal state-machine transition. Trace shows a 1-px y divergence at the
transition frame.

**Root cause.** Engine's `isSolidFor()` gates on internal state
(`routineSecondary != STATE_FALL`). ROM's main dispatcher calls the solid
positioning unconditionally; the object stays solid until it physically
warps off-screen.

**What to check.** `isSolidFor()` for S3K moving solids (AIZ collapsing
platforms, MGZ falling pillars, HCZ retractable spikes, CNZ platforms,
etc.) should track physical existence, not state-machine routine.

**Originating commit (S2).** `719c4034e fix(s2): HTZ Lift
solid-while-falling + ROM-order gravity/move`.

---

## P6 — Gravity-before-move vs ROM's move-before-gravity ordering

**Symptom.** A falling object's y_pos rolls over one frame earlier than
ROM. Surfaces as a one-frame off-by-one on a rider's y at the transition
frame.

**Root cause.** Engine free-fall does `yVel += gravity; yFixed += yVel`.
ROM consistently does `ObjectMove` (move) first, then `addi.w #$<gravity>,
y_vel(a0)`.

**What to check.** When porting an S3K free-fall routine, preserve the
`ObjectMove` → gravity order. S3K's `ObjectMoveAndFall` equivalent will
have the same pattern.

**Originating commit (S2).** `719c4034e` (secondary).

---

## P7 — Centre Y vs top-left Y for kill / boundary checks

**Symptom.** Sidekick fails to die crossing the bottom kill plane, or dies
one frame late.

**Root cause.** Engine compares `sprite.getY()` (top-left) against the
kill plane. ROM compares centre Y (`y_pos(a0)`).

**What to check.** Kill / boundary / out-of-bounds checks must compare
against `getCentreY()` not `getY()`. Same for X-axis side boundaries:
`getCentreX()` not `getX()`.

**ROM citation.** S3K bottom-kill is gated by `Tails_LevelBound`-analog
inside the S3K Tails/Knuckles AI; search `sonic3k.asm` for level-boundary
labels.

**Originating commit (S2).** `4361de0e8 fix(s2): sidekick level-bound
bottom kill uses centre Y to match ROM` (fix applied universally for CPU
sidekicks; S3K already used centre Y on its physics path, so the change
is symmetric).

---

## P8 — Per-game post-event flow divergence (S3K immediate vs S2 deferred)

**Symptom.** Sidekick despawn / level-end / boss-defeat flow differs
between S3K and S2 in a way that the engine generalised over.

**Root cause.** Some post-event flows have intentionally different ROM
semantics across games. The most prominent in trace work:

- **Sidekick death**: S3K `sub_13ECA` warps the sidekick to the despawn
  marker (x=0x4000) on the frame after kill. S2 `Obj02_Dead` defers the
  warp until the sidekick falls past `Tails_Max_Y_pos + 0x100` (several
  frames of gravity-only execution after the kill).

**What to check.** When implementing or modifying post-event flows
(despawn, results-screen handoff, level-end), look at each game's ROM
equivalent separately. If they diverge, add a `PhysicsFeatureSet` flag
following the established pattern (e.g. `sidekickDeathUsesDeferredDespawn`).
Never gate on `gameId`.

**ROM citation.** S3K immediate-warp baseline at
`docs/skdisasm/sonic3k.asm:26800-26809` (`sub_13ECA`). S2 deferred flow at
`docs/s2disasm/s2.asm:40736-40759`.

**Originating commit.** `a4aca7d6f fix(s2): sidekick death uses
deferred-despawn flow to match S2 Obj02_Dead`.

---

## P9 — Integer math drops y_sub carry in 16:16 position updates

**Symptom.** Post-warp / post-teleport y_pos is exactly 1 pixel low (or
high) relative to ROM. The error appears only when the pre-event
`y_sub_pos + (y_vel & 0xFF00)` overflows the 16-bit subpixel boundary.

**Root cause.** ROM `ObjectMoveAndFall` / `MoveSprite` treats `y_pos:y_sub`
as a single 32-bit long and executes `add.l d0,d3` where `d0 = y_vel<<8`
(sign extended). Subpixel overflow carries into `y_pos`. Java code that
does `y_pos += (y_vel >> 8)` after a `setCentreYPreserveSubpixel(...)`
warp treats the halves as independent integers and DROPS the carry. The
overflowed low byte still lands in `y_sub` (because `setCentreY*`
preserves it), but `y_pos` is short by 1.

**What to check.** Any code path that:
1. Calls `setCentreXPreserveSubpixel(...)` / `setCentreYPreserveSubpixel(...)`
   (ROM word-write equivalents to `x_pos` / `y_pos`),
2. THEN integrates by a velocity stored in subpixel units (`x_vel` / `y_vel`),
must use `AbstractSprite.move(xSpeed, ySpeed)` — which mirrors ROM's
`add.l d0, x_pos(a0)` / `add.l d0, y_pos(a0)` — rather than manual
`centreY += (ySpeed >> 8)` arithmetic.

**ROM citation.** S3K `MoveSprite` (`docs/skdisasm/sonic3k.asm:36032-36042`)
and `ObjectMoveAndFall`. Same 16:16 fixed-point convention as S1 / S2.
Engine equivalent: `AbstractSprite.move` in
`src/main/java/com/openggf/sprites/AbstractSprite.java`.

**Originating commit.** `<pending>` (S2 trace frontier advancement loop
iter 1: HTZ F538 + MCZ F443 deferred-despawn sub-pixel & solid-contact
gating; cross-applies to S3K objects warping & integrating velocity).

---

## P10 — Solid object contacts must skip dead / despawning players

**Symptom.** A dying CPU sidekick (or main player) "lands" on a moving
solid object (lift / platform / drawbridge) under the impact point while
ROM would have him fall past it. Engine's sidekick `y` freezes at the
platform top and `y_speed` drops to 0, while ROM keeps Tails falling
through the platform.

**Root cause.** ROM `SolidObject_ChkBounds` (S3K equivalent of S2
`s2.asm:35178-35182`) gates the full bounding-box check with:

```
SolidObject_ChkBounds:
    tst.b    obj_control(a1)
    bmi.w    SolidObject_TestClearPush   ; bit 7 set => skip
    cmpi.b   #6,routine(a1)              ; routine >= 6?
    bhs.w    SolidObject_NoCollision     ; Dead/Gone/Respawning => skip
```

The two gates are independent. The `obj_control bit 7` path covers
respawning / object-controlled states. The `routine >= 6` path covers the
Dead / Gone / Respawning routines themselves.

For S3K, `obj_control` is set on frame N+1 immediately via `sub_13ECA`
(`docs/skdisasm/sonic3k.asm:26800-26809`), which means the `obj_control`
gate covers most of the dead-fall window. The S2 deferred-despawn flow
spends multiple frames in routine = 6 BEFORE `obj_control` flips, so the
`routine >= 6` gate is required there. An engine that ports only the
`obj_control` gate will still apply solid contacts to a sidekick mid-
deferred-death-fall (S2-specific), but the rule is universal.

**What to check.** `blocksSolidContacts(player, candidate)` (or whatever
the engine's SolidObject pre-filter is named) needs BOTH gates:
1. `player.isObjectControlled()` — mirrors `obj_control` bit 7.
2. CPU sidekick state `DEAD_FALLING` (engine equivalent of ROM Tails
   routine = 6) — must short-circuit even though `obj_control` is still
   0 during S2 deferred-despawn.

**ROM citation.** S3K `SolidObject_ChkBounds` in
`docs/skdisasm/sonic3k.asm` (mirrors S2 `s2.asm:35178-35182`). Engine
equivalent: `ObjectManager.SolidContacts.blocksSolidContacts` in
`src/main/java/com/openggf/level/objects/ObjectManager.java`.

**Originating commit.** `<pending>` (S2 trace frontier advancement loop
iter 1: HTZ F538 + MCZ F443).

---

## P11 — Solid object break/trigger condition leaks main-player state into sidekick contact

**Symptom.** Sidekick (Tails / Knuckles) is suddenly knocked airborne +
rolling + Y shifted by 1 px while the main player is rolling through
nearby terrain. Trace shows ROM keeps the sidekick grounded with
`status.standing` and `status.pushing`, while the engine reports
`status.in_air | rolling` and a fresh downward `y_vel`. The divergence
appears on the exact frame the main player passes a breakable /
launchable object even though the sidekick isn't standing on that
object.

**Root cause.** The engine cached `playerWasRolling = player.getRolling()`
inside the object's per-frame `update(...)` method, with `player` being
whichever sprite the object manager happened to pass (typically the main
player). The break/launch decision in `onSolidContact(player, contact)`
then OR'd the cache with the contacting player's own `getRolling()`. When
the main player was rolling, the cache made the OR true even for the
sidekick's side / bottom contact, so the object's break path fired with
the sidekick as the victim.

**What to check.** Any S3K solid object with a state-dependent break /
launch / monitor-pop / trigger:
1. Per-player conditions must read the *contacting* player's state, not
   a per-frame cached "saw rolling once" flag. Use the player parameter
   of `onSolidContact` directly.
2. ROM equivalents in S3K cache main / sidekick anim per-player in the
   object's SST and check `status(a0) & standing_mask` — never a global
   "was rolling" cache.
3. Side / bottom contact rarely breaks ROM solids. Most breakable
   objects only fire on `contact.standing()`. Rolling player into the
   underside gets a CEILING collision via `SolidObject`, not a break.

**ROM citation.** S2 `docs/s2disasm/s2.asm:48889-48959` (Obj32 / breakable
block). S3K equivalents in `docs/skdisasm/sonic3k.asm` use the same
SolidObject + per-player anim cache pattern; check for `standing_mask`
and per-character anim bytes (objoff_32 / objoff_33 equivalents) in the
object's main routine.

**Originating commit.** `<pending>` (S2 trace frontier advancement loop
iter 3: HTZ F979 BreakableBlock; cross-game mirror for S3K-implementable
objects following the same pattern).

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

**ROM citation.** S3K shares this idiom for several patrolling-shooter
badniks. Search `docs/skdisasm/sonic3k.asm` for the
`GetOrientationToPlayer` analogue (same closest-player selection
pattern by `mvabs.w` of horizontal delta) and `addi.w #$60, d2 ; cmpi.w
#$C0, d2 ; blo` gating in the badnik's main routine. S2 original at
`docs/s2disasm/s2.asm:75923-75976` (ObjA5 Spiny) and
`docs/s2disasm/s2.asm:72320-72346` (`Obj_GetOrientationToPlayer`).

**Originating commit (S2).** `<pending>` (trace frontier advancement loop iter
4: S2 CPZ F844 Spiny detection ported with simplified bounding-box +
facing guard instead of ROM closest-player horizontal-only gate). Cross-
applied to S3K here because S3K reuses the same `GetOrientationToPlayer`
pattern in numerous patrolling-shooter badniks. Verify each S3K badnik
implementation against its specific routine before relying on the
pattern.

---

## P13 -- SlopedSolidProvider.getSlopeBaseline() returning halfHeight when ROM slope table encodes absolute offsets

**Symptom.** Player rolling-air-falls toward a sloped platform / bridge /
ICZ snowpile that ROM cleanly lands them on, but the engine fires "no
contact" and lets them fall through. Frontier divergence appears as a y /
y_speed / air mismatch on the exact landing frame: ROM has `y_speed=0`,
`air=0`, snapped y position; engine still has `y_speed = previous +
gravity`, `air=1`, kept falling.

**Root cause.** `SlopedSolidProvider.getSlopeBaseline()` controls
`resolveSlopedContact`'s shift between the raw slope sample and the
effective surface Y:

```
slopeOffset = slopeSample - slopeBase
baseY       = anchorY - slopeOffset
relY        = playerCenterY - baseY + 4 + playerYRadius
```

S3K slope-sampling helpers (`SolidObjCheckSloped`,
`SolidObjCheckSloped2`, `loc_19EB6`-style direct surface compares)
read the slope sample directly: `move.b (a2,d0.w),d3 / ext.w d3 /
move.w y_pos(a0),d0 / sub.w d3,d0`.  There is no baseline subtraction;
the slope table value IS the offset from object_y to the surface.
S3K's slope tables (e.g. `IczBigSnowPile_HeightTable`, AIZ flipping
bridge tables) already encode that convention, so `getSlopeBaseline()`
must return `0` for any S3K slope object whose data is sampled
directly by these helpers.

The `COLLISION_HEIGHT` baseline pattern came from S1's GHZ bridge /
SLZ seesaw slope tables, which encode the surface offset relative to
the object's bottom edge.  S3K slope data does NOT follow that
convention -- positive values lift the surface above object_y,
negative values drop below.

**What to check.** When porting any S3K SlopedSolidProvider:

1. Find the ROM routine that samples the slope and computes the
   surface.  S3K uses `SolidObjCheckSloped` (sonic3k.asm:41982-42015),
   `SolidObjCheckSloped2` (sonic3k.asm:41887-41914), or the
   per-object loc_19EB6-style direct subtraction.
2. Look at the slope data values relative to ROM `y_pos(a0) - d3`:
   - If slope[mid] ~= 0 and the surface visually sits at object_y,
     the table encodes absolute offsets -> `getSlopeBaseline()` returns 0.
   - If slope[mid] ~= halfHeight and the surface visually sits at
     `object_y - halfHeight`, the table is relative to object bottom
     -> `getSlopeBaseline()` returns halfHeight (rare; S1 only).
3. Check existing S3K SlopedSolidProvider impls: `IczBigSnowPileInstance`
   and `AizFlippingBridgeObjectInstance` both return 0 with the
   comment "Height table values are absolute offsets from obj_y" --
   that is the S3K-standard pattern.
4. Confirm via trace replay: compute `surfaceTop = anchorY - rawSlopeSample`
   and `playerBottom = playerY + yRadius + 4`. ROM lands when
   `surfaceTop - playerBottom` is in `(-16, 0]`. The engine's `relY`
   must equal `playerBottom - surfaceTop` for `relY` to land in
   `[0, 16)`. If `slopeBase != 0` shifts the apparent surface by
   halfHeight, the landing window shifts the same amount and the
   player misses.

**ROM citation.** `docs/skdisasm/sonic3k.asm` (SolidObjCheckSloped /
SolidObjCheckSloped2 / per-object loc_19EB6 equivalents -- slope
sample -> surface Y, direct subtraction, no baseline).  Engine
equivalent: `SlopedSolidProvider.getSlopeBaseline()`, `ObjectManager.
resolveSlopedContact` (`baseY = anchorY - slopeOffset`).

**Originating commit.** `<pending>` (cross-game mirror of S2 P13 entry;
the same pattern applies to any S3K SlopedSolidProvider whose slope
table encodes absolute offsets from object_y.  The S2 case fixed was
HTZ1 trace F988 Sonic missing the Seesaw landing because
SeesawObjectInstance returned `COLLISION_HEIGHT` from
`getSlopeBaseline()`, shifting the effective surface 8 px below
ROM's.)

---

## P14 -- Engine edge-triggers ENEMY touch response but ROM polls every frame

**Symptom.** A badnik (or other ENEMY-category object) that should be
destroyed when the player transitions into an attacking state while
already overlapping the badnik stays alive instead. Trace shows ROM
killed the badnik AND applied the canonical Touch_KillEnemy bounce
(typically `y_vel -= $100` for side, `y_vel = -y_vel` for top, `y_vel +=
$100` for upward); engine has the badnik still in the active list and
the player's y_vel/x_vel unchanged. The S2 MCZ trace surfaced this
at frame 825 when Sonic was standing in the Crawlton's bounding box
with `invulnerable_time != 0` (Touch_NoHurt path), then pressed
B+Down to start a Spindash; ROM re-checked anim on f825, saw
Spindash, ran Touch_KillEnemy and set `y_vel = -$100`. The same
pattern applies to S3K badniks whose touch response decisions are
keyed on the current player animation (e.g. Lance / spear-type
badniks that bounce rolling players, multi-sprite bosses whose
`boss_hitcount2` check fires from Touch_Enemy).

**Root cause.** The engine's `ObjectManager.TouchResponses.
processCollisionLoop` historically edge-triggered ENEMY (and SPECIAL)
touch callbacks. ROM has no such gate: the touch loop iterates every
frame and `Touch_Enemy` re-reads `status_secondary(a0)` and
`anim(a0)` each call, so the decision between Touch_KillEnemy and
Touch_ChkHurt is made per-frame. The same applies to S3K where
`Collision_response_list` is pre-built during ExecuteObjects and then
re-walked every frame -- there is no overlap-memory gating.

**What to check.** When implementing an S3K badnik or any object that
uses the `ENEMY` `TouchCategory`:
1. Make `onPlayerAttack` idempotent and self-gating (`isDestroyed()`
   check up front, capture pre-destruction state before mutating).
2. Read the player's current animation/state inside
   `onPlayerAttack`, not from a cached `update()` value. ROM
   Touch_Enemy reads `anim(a0)` each call.
3. S3K bosses with `boss_hitcount2` must accept multiple touches per
   overlap. The continuous polling lets the rolling/spindash player
   land subsequent hits without the engine suppressing the callback.
4. The fix is ENEMY-only. SPECIAL (collision_flags 0x40-0x7F) is
   still edge-triggered to keep monitors / object-controlled
   SolidObjects from firing responses every frame. If an S3K SPECIAL
   object needs every-frame polling, opt-in via
   `TouchResponseProvider.requiresContinuousTouchCallbacks()`.

**ROM citation.** `docs/skdisasm/sonic3k.asm` `Collision_response_list`
walking + `Touch_Enemy` equivalent (S3K mirrors S2 line 84807-84890;
S3K's `loc_1E600` Touch_Enemy variant reads anim per-call). Cross-game
S2 cite: `docs/s2disasm/s2.asm:84502-84548` (`Touch_Loop`),
`s2.asm:84807-84890` (`Touch_Enemy` / `Touch_KillEnemy`). Engine
equivalent: `ObjectManager.TouchResponses.processCollisionLoop`
(`shouldTrigger` decision).

**Originating commit.** `<pending>` (cross-game mirror of S2 P14
entry; ENEMY-continuous polling restored a per-frame Touch_Enemy
re-check that any future S3K badnik with state-dependent kill / hit
behaviour will rely on. Originating S2 trace: MCZ F825 Crawlton with
Sonic transitioning to Spindash during overlap.)

---

## P15 -- Object update() resolves solid contacts BEFORE refreshing slope / collision state

**Symptom.** A sloped solid (seesaw, bridge, tilting platform, flipping
platform) ports the ROM logic but the rider's Y trails ROM by one frame
during a state transition.  Frontier divergence appears the frame the
slope changes shape: ROM has snapped to the new surface; the engine
still uses the previous frame's surface and lags 1-8 px depending on
the slope-table delta.  The state itself transitioned at the right
time -- it just happened AFTER the engine had already finished its
slope sample.

**Root cause.** ROM `Obj_Main` for sloped solids updates the
mapping_frame / slope-table choice BEFORE the SolidObjCheckSloped2 /
loc_19EB6 call.  A naive engine port often inverts this:

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
previous frame's surface; the rider's Y lands one transition behind
ROM.  See S2 P15 / cross-game mirror for the full S2 case study
(HTZ Seesaw, mapping_frame 2 -> 1 transition, Sonic 3 px low for one
frame).

**What to check.** When implementing any S3K solid object whose
collision geometry depends on a tickable state byte (mapping_frame,
animation frame, internal angle, depression amount, slope offset
table choice), look at the ROM `Obj_Main` to see where the state
update happens relative to the SolidObjCheckSloped2 / loc_19EB6 /
PlatformObject call:

1. If ROM updates the state *before* the collision call, the engine
   must update its equivalent *before* `resolveSolidNowAll()` /
   `checkpointAll()`.  Compute the target from the PREVIOUS frame's
   standing-player references (kept as instance fields) plus the
   CURRENT player x positions -- ROM does that via per-player
   standing bits on `status(a0)` and reads of `x_pos(a1)` for the
   selected character.
2. The previous-frame standing references are valid because ROM
   itself reads them before the collision call clears / re-sets them.
   In the engine, the latched `standingPlayer1` / `standingPlayer2`
   fields from the end of the prior `update()` give the same view.
3. Watch for sibling helpers that already follow the ROM order:
   `BridgeObjectInstance` (S2 EHZ bridge) calls
   `updateDepressionState()`, `rebuildBridgeShape()`,
   `updateSlopeData()` FIRST and only then runs `checkpointAll()`.
   `Sonic3kCollapsingPlatformObjectInstance` and
   `AizFlippingBridgeObjectInstance` follow analogous patterns and
   are good templates for new S3K sloped solids.
4. The fix is purely a reorder; no new state, no new flags.  Don't
   try to "buffer" the previous-frame slope -- match ROM order and
   the divergence disappears.

**ROM citation.** S2 originating fix: `docs/s2disasm/s2.asm:47037-47115`
(Obj14_Main + Obj14_UpdateMappingAndCollision).  S3K analogues live
in `docs/skdisasm/sonic3k.asm` per-object: AIZ flipping bridge
state-update routine + `loc_19EB6` SolidObjCheckSloped2 call, MGZ
tilting blocks, ICZ segment columns.  Engine equivalent: any S3K
solid object's `update(int, PlayableEntity)` that calls
`services().solidExecution().resolveSolidNowAll()` should perform
slope-shape state updates BEFORE that call, mirroring ROM order.

**Originating commit.** `<pending>` (cross-game mirror of S2 P15
entry; HTZ Seesaw mapping_frame transition surfaced the ordering
issue.  S3K state-driven sloped solids share the same idiom -- ROM
updates state, then calls collision -- and a future S3K trace will
surface the same bug if the engine port reverses the order.)

---

## P17 -- Child object out_of_range uses own X instead of parent anchor, causing chunk-boundary unload

**Symptom.** A parent object's child (e.g. a multi-piece boss arm, attached
segment, swung weapon, fixed-offset projectile spawner) silently vanishes
shortly after the parent appears on screen. The parent stays alive and behaves
normally, but a feature that depends on the child (contact, launch, swing
attack) never fires. Trace replay shows the parent's state advancing
correctly while a hit/launch event that requires the child silently fails.
The engine's `parent.child` reference is non-null and `child.destroyed=false`,
but the child is no longer in `ObjectManager.dynamicObjects`.

**Root cause.** ROM dispatchers for parent+child objects often share one
`Obj_Index` table whose tail uses `move.w objoff_30(a0),d0 / jmpto
JmpTo_MarkObjGone2`. The child's init routine stores the PARENT's `x_pos`
into `objoff_30(a0)` BEFORE applying the `addi.w` offset that moves the
child relative to the parent. So both parent and child use the same camera-
relative chunk reference and unload together.

A naive engine port adds the child via `addDynamicObjectAfterCurrent` and
lets it default to `getOutOfRangeReferenceX() = getX()` (the child's
current position). The default `ObjectManager.isOutOfRangeS1` rounds X to
128-byte chunks. If the child's offset puts it into a different chunk than
the parent, the child can be unloaded as soon as the camera enters the
parent's chunk -- leaving the parent with an orphaned `child` reference and
permanent disablement of the child-dependent feature.

**What to check.** For any parent+child pair where ROM's `Obj_*_Init` does:

```
move.w x_pos(a0),objoff_30(a0)  ; save parent x BEFORE offset
addi.w #$xx,x_pos(a0)            ; apply child offset
```

the child must override `getOutOfRangeReferenceX()` to return the parent's
x position. S3K parent+child idioms to audit:
- Multi-piece boss children spawned by the main boss init
- AIZ battleship attachments (turrets, arms)
- Body segments on Caterkiller-style badniks
- Any projectile that fires from a fixed parent-relative offset and reads
  the parent via `objoff_3C`

The fix is one method:

```java
@Override
public int getOutOfRangeReferenceX() {
    return parentCenterX;  // ROM objoff_30(a0) = parent x_pos
}
```

**ROM citation.** S2 originating bug: `docs/s2disasm/s2.asm:47151`
(`Obj14_Ball_Init`), `s2.asm:46996` (Obj14 dispatcher tail with
`move.w objoff_30(a0),d0 / jmpto JmpTo_MarkObjGone2`), `s2.asm:30040-30057`
(`MarkObjGone2` chunk-rounded comparison). S3K analogue is the standard
multi-piece boss / hazard pattern; cite the specific
`Obj_Init` block when porting an S3K instance. Engine equivalent: any
`AbstractObjectInstance` subclass spawned at a positional offset from a
parent should override `getOutOfRangeReferenceX()` to return the parent's
centre X.

**Originating commit.** `<pending>` (cross-game mirror of S2 P17 entry;
HTZ Seesaw ball alone got unloaded when its chunk-boundary crossed the
camera threshold, while the parent stayed loaded.  S3K parent+child
objects share the ROM idiom and will surface the same trace divergence if
the engine port relies on the default `getX()` reference X.)

---

## P18 -- Shared monitor icon rewards use pre-move velocity tests

**Symptom.** A monitor reward applies one frame too early. The same shared
engine helper is used by S2 and S3K monitors, so a timing fix in the base
routine must be proven against all monitor-content ROM routines.

**Root cause.** The ROM monitor-content routine tests the icon's `y_vel`
before moving it. If the current rise step adds `$18` and lands exactly on
zero, the routine returns; the reward branch runs on the next object update.
A shared engine helper that applies the reward immediately after changing
`iconVelY` from negative to zero is one frame early.

**What to check.** For shared monitor code, verify S1, S2, and S3K before
changing the base routine. S3K has both S&K-side and S3-side monitor-content
code; cite the side that applies to the object being ported. If one game
differs, gate the behaviour at the owning abstraction instead of changing
every game implicitly.

**ROM citation.** S3K `docs/skdisasm/sonic3k.asm:40723-40753`; S3-side
`docs/skdisasm/s3.asm:33392-33421`; S2 analogue
`docs/s2disasm/s2.asm:25618-25631`; S1 analogue
`docs/s1disasm/_incObj/2E Monitor Content Power-Up.asm:35-43`.

**Originating commit.** `<pending>` (cross-game mirror of S2 P19 entry;
CNZ speed-shoes monitor reward timing advanced the S2 CNZ frontier from f976
to f1146 after confirming S1, S2, and S3K all use the same pre-move velocity
test).

---

## How to add a new entry

When a trace-replay-bug-fixing iteration commits an object fix whose root
cause is a class of bug (not a one-off):

1. Identify which pitfall pattern category fits, or pick the next P-number
   if none fit.
2. Append a new entry following the template above.
3. Reference the originating commit hash so future readers can see the full
   diff and test cases.
4. Mirror to `.claude/skills/s3k-implement-object/rom-pitfalls.md` in the
   same commit. Use the commit trailer `Skills: updated`.
5. If the pattern is cross-game, copy the entry into
   `.agents/skills/s2-implement-object/rom-pitfalls.md` with the analogous
   S2 disasm citation.

S3K-specific considerations: many patterns surface differently in S3K
because of (a) zone-set-aware object IDs, (b) the dual S&K-side / S3-side
ROM addresses, and (c) S3K's larger animated-state and PLC system. When
adding an entry that's specifically S3K-flavoured (e.g. zone-set
mis-resolution, S&K-vs-S3 address confusion), tag it with a leading
"**S3K-specific:**" marker so it doesn't get duplicated to the S2 file.
