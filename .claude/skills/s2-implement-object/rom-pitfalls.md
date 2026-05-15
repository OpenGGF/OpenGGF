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
`setCentreYPreserveSubpixel((short)(preCentreY + 5))`. Do not substitute a
character-aware helper "because Tails is shorter".

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
1. Calls `setCentreXPreserveSubpixel(...)` / `setCentreYPreserveSubpixel(...)`
   (ROM word-write equivalents to `x_pos` / `y_pos`),
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
