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
