# LBZ Big Arm ROM Port Design

**Date:** 2026-08-09
**Object:** S3KL `$CC`, `Obj_LBZFinalBoss2` / Big Arm
**Source of truth:** `docs/skdisasm/sonic3k.asm:154231-155585`, assembled
with `FixBugs = 0`

## Problem

`LbzFinalBoss2Instance` is an invisible, persistent placeholder. Knuckles'
`LbzFinalBoss1Instance` defeat handoff therefore reaches a live object slot but
cannot run the Big Arm fight, capsule/results sequence, escape cutscene, or
transition to MHZ.

Two earlier candidates are not safe foundations. Commit `98d968d7f` invented
boss phases, mapping ownership, and the defeat/capsule continuation instead of
porting the articulated native graph. A later uncommitted v2 candidate recovered
some useful labels and table values, but its tests entered internal states through
test-only force methods and it still changed native behavior: its init retained
routine `$00`, fall used an inclusive boundary, the grab-side test was reversed,
the non-invincible throw did not call the hurt path, throw movement omitted `$38`
gravity, its `FixBugs = 0` explanation was inverted, and its capsule/escape flow
was synthetic. No code or assertion from either candidate is accepted without an
independent source or ROM proof.

The base branch adds evidence that was unavailable to those reviews. Schema-v5
run `s3k-knuckles-complete-superemeralds`, segment `lbz_2`, is a canonical
BizHawk/Genplus-gx recording of the full Knuckles route. Its comparison-only aux
stream identifies the root as ROM code `$74262` and records the complete route:

| Trace frame | Native state |
|---:|---|
| 1454 | Big Arm root appears in slot 6 at `$43A0,$02D8` |
| 1544 | root plus the eight initial articulated children occupy slots 6-14 |
| 1582 | root is in routine `$06` (fall) |
| 1622 | routine `$08` (initial `$7F` wait) |
| 1781 | routine `$0A` (random motion) |
| 2122-2568 | real grab sequence `$1E,$20,$22,$24,$26,$28,$2A`, then `$0A` |
| 4003-4420 | drop/rise attack `$10,$12,$14,$16,$18,$1A,$1C` |
| 4422 | final hit replaces the root callback with the shared defeat owner |
| 4486-5009 | `loc_746F4` rise, then `loc_7473A` capsule/results wait |
| 5797-6165 | post-capsule ship/floor escape through `loc_74952` |

This trace constrains route order and timing. It is never an authority for
production state and will not be used to hydrate or tune the implementation.

## Design

### 1. Port the root as the native routine machine

Replace the placeholder with a persistent, renderable, attackable object whose
state names retain the even native routine bytes `$00-$2A`. Positions are ROM
centres. Store X and Y as 16.16 fixed-point values and add signed 8.8 velocities
shifted by eight, matching `MoveSprite`, `MoveSprite2`, and
`MoveSprite_LightGravity`; do not discard either subpixel byte. The low word is
not an eight-bit convenience fraction: arbitrary captured low-word values must
survive integer `move.w x_pos/y_pos` writes, held-player snaps, the carrier
handoff, rewind, and re-execution. Player word writes use
`NativePositionOps.writeXPosPreserveSubpixel` and its Y equivalent. In
particular, `loc_7490A` copies only the high words from Knuckles into the root
and does not zero either root fraction, while the later root-to-player copies
preserve the player's own low words.

Initialization uses `ObjDat_LBZFinalBoss2` and `SetUp_ObjAttributes` semantics:
routine advances from `$00` to `$02`, collision property is eight, `$38` bit 3
is set, the death plane is disabled through the existing S3K runtime owner,
position becomes camera `+$A0,-$50`, timer is `$59`, mapping frame is 5, the
root collision byte remains zero until `loc_74340` publishes `$0F`, the
palette and Kosinski-module job are submitted from the ROM, and
`Child1_MakeRoboHead4` is allocated. `Obj_Wait` pre-decrement/callback behavior
is retained rather than represented by fitted frame counts.

The normal fight dispatch is a direct translation of
`LBZFinalBoss2_Index`, including:

- strict unsigned branch boundaries and the ROM's same-entry fallthroughs;
- RNG consumption only at `loc_74F24` and `loc_74F82` and in the same order;
- routine `$0A`'s exact five-entry offset table and signed velocity/angle
  arithmetic: `$3A=0` is no-op, `2` subtracts four from Y velocity, `4` adds
  `$3C` to Y velocity and changes `$3C` from zero to two only when the result is
  zero, `6` is no-op, and the hit override `8` subtracts four from the Y
  position. Dispatch uses the complete byte value, never `& 6`;
- camera-relative bounce, drop, land, and rise transitions with the correct
  camera word at each source site. Routines `$10`, `$12`, and `$14`, plus the
  routine `$24` grab-floor approach, read `Camera_Y_pos_copy`; the other listed
  fight states retain their explicit `Camera_Y_pos` reads. A semantic
  camera-copy accessor maps the former to the base camera plus the currently
  applied shake offset, rather than silently aliasing base `camera.getY()`;
- grab acquisition from `loc_74C24`, native player control `$81`, and the
  `$1E->$2A` root path. `loc_74C8C` falls through to `loc_74CCC` on the
  acquisition entry, so the player is snapped to the refreshed grab owner in
  that same child dispatch. Release retains the native `$40` pre-decrement
  cadence and callback boundaries: `loc_74CF8` seeds `$2E=$40` and immediately
  falls through to the first decrement, ending the release entry at `$3F`.
  `loc_74D04` then freezes the grab owner's word position for the whole wait,
  even if its controller moves or flips. The `0 -> $FFFF` expiry entry only
  installs `loc_74C34` and returns; adjusted refresh and range acquisition do
  not resume until the following own entry, using the controller's then-current
  flip. This state remains distinct from `$81` grab control and `$83` external
  carrier control (bit 7 ownership, bit 1 external mapping/animation, and bit 0
  movement suppression);
- the `loc_745A8` side choice (`x > camera+$A0` keeps X `+$E0`; otherwise set
  X flip and use `+$60`);
- non-invincible throw through the engine's standard hurt response, and the
  invincible restore/rebound path; and
- routine `$2A` using ordinary `MoveSprite` gravity `$38`, not `MoveSprite2`.

No route, frame, movie, or zone-name predicate is introduced. The registry is
already the LBZ/S3KL owner; behavior consumes only object, player, camera, RNG,
and runtime flags that the ROM routine reads.

### 2. Recreate the native articulated object graph

Allocate children through `spawnChild` in `CreateChild1_Normal` order. Keep
each independently executing child as an `ObjectManager` object so slot cadence,
collision polling, render priority, and rewind identity remain observable.

Allocation failure is part of that helper contract. Native
`CreateChild1_Normal` and `CreateChild6_Simple` call
`AllocateObjectAfterCurrent` before writing any child fields. A nonzero return
ends the helper immediately: a multi-entry table keeps only entries allocated
before the failure, does not advance the table pointer or `d2` for the failed
entry, and does not attempt any later table entry. The caller then continues
from its own post-helper instruction; a one-shot callsite does not retry unless
its source callback is naturally dispatched again. The boss-local spawn helper
therefore treats an ObjectManager child with `destroyed=true` or no SST slot as
an allocation failure, never adds it to `children`, `graphChildren`, a floor or
emitter collection, or a dedicated root/controller field, and stops a native
multi-entry table at that point. Its successful-spawn ordinal is committed only
after a live slot is assigned, so a failed Java construction cannot create a
phantom ordinal/identity gap.

Retry behavior remains callsite-specific. The initial head, articulated tables,
landing child, defeat debris/follow visual, replacement head, flame, floor, and
each escape emitter's controller are one-shot attempts. The subtype-4 boss
explosion controller retries only on its next native three-entry emission
callback. The floor's `loc_74DEA` attempt consumes that qualifying `$39`
decrement whether allocation succeeds or fails and tries the next emission only
on the next qualifying four-V-int dispatch; a failed attempt consumes no
committed child ordinal, so the next successful emitter has the next successful
identity. `loc_74DA4` stops its seven-entry hitbox table at the first failure but
still performs the later stored camera-target writes and independent level-size allocation,
and `loc_74E30` still waits `$60` and self-terminates if its one controller
allocation fails. These are source retry/continuation rules, not a generic
"try again next frame" policy.

The first wait callback creates `ChildObjDat_75122`: controller `loc_749D0`,
visual attachment `loc_749AE`, joint visual `loc_74B9E`, and outer collision
piece `loc_74BC0`. The controller's first dispatch rewrites its native offsets
and creates `ChildObjDat_75144`: two `loc_74AFA` segments, `loc_74A9A`, and the
`loc_74C24` grab owner. Landing later adds `loc_74C00`. Child positions use
`Refresh_ChildPosition`, `Refresh_ChildPositionAdjusted`, or
`MoveSprite_CircularLookup` exactly where the source does; child offsets are
signed bytes and root/parent links retain native ownership.

The controller keeps its two native phases and its own render flip. Its first
own entry initializes routine 2, rewrites `child_dx/child_dy` from the table's
`+$14,+$24` to `+$14,-6`, and creates the nested graph, but performs no refresh;
the enclosing draw helper therefore still uses the table-spawned position on
that entry. Routine 2 performs only the circular lookup and does not call
`Change_FlipXUseParent`. The entry that first observes parent `art_tile` bit 7
publishes `$AD`, changes to routine 4, and performs one circular lookup with the
existing angle **and the controller's previously latched flip**; neither angle
adjustment nor a root-flip latch begins until the controller's next own
dispatch. In routine 4, root `$38` bit 1 returns before angle, flip, or circular
position refresh, leaving both controller coordinates and flip unchanged.
Root bit 2 skips only angle adjustment; `Change_FlipXUseParent` still copies the
root flip into the controller immediately before the circular lookup.

Adjusted child refresh always consumes the immediate native parent, never a
shortcut to the root. The two segments and grab owner therefore mirror the
controller's latched flip; their X offsets and render flip can deliberately
lag a root flip until the controller reaches routine 4's latch. The kinematic
joint rewires `parent3` to the root and separately retains the controller in
`$44`, so it copies the root flip while reading only the controller angle.

The segment held callback is not a Boolean mapping override. On the first
normal entry from zeroed animation state, `loc_74B3C` performs adjusted refresh
and `Animate_Raw`: the raw cursor becomes 1, and
`Animate_RawNoSST` reads `1(a1,d0.w)`, so cursor 1 selects script byte 2,
not script byte 1. Mapping therefore becomes 4 for subtype 0 or 8 for subtype
2, and the timer becomes script byte 0, value 9. A first held entry performs
that same step *before* installing `loc_74B76`, overriding the selected mapping
with 7/$B, and, for native subtype 2 only, adding eight to the stored signed
`child_dx`; cursor 1 and timer 9 remain untouched by the held override. Later held
`loc_74B76` entries refresh from that persistent adjusted offset and never run
`Animate_Raw`. On the first released entry, `loc_74B76` refreshes once with the
still-adjusted offset, then restores `loc_74B3C` and subtracts eight; ordinary
refresh/animation resumes only on the following entry. Mapping, raw cursor and
timer, signed offset, position, immediate-parent flip, and callback phase are
all rewind-visible.

The ordinary segment animation must also preserve the terminal raw-script
boundary. From stored cursor 5, the next expired-timer entry increments the
cursor to 6 and `1(a1,d0.w)` reads script byte 7, `$FC`. The restart helper
publishes script byte 1 (mapping 7 for subtype 0 or `$B` for subtype 2), reloads
timer 9, and then clears the cursor to 0 on that same own entry. Capture
immediately before this `$FC` entry, restore out of place, and require the same
mapping/timer/cursor restart after one re-executed own entry.

The two 64-byte circular lookup tables are gameplay ROM data, not Java literal
arrays. Read them from the locked-on ROM addresses selected by the S&K object
code (`$360B08` and `$3629A0`) through `services().romReader()`/`services().rom()`.
The addresses are independently located by full byte-pattern searches and the
labels in `Lockon S3/LockOn Data.asm`. Failure to load them is an explicit
unavailable-art/data state, never a disassembly-file fallback.

Child touch-response ownership follows the native setup routines rather than
the `d0` value passed to the flicker/draw helper:

- controller `loc_749D0` starts with collision zero and publishes `$AD` only
  after `loc_74A14` observes parent `art_tile` bit 7;
- outer piece `loc_74BC0` is the **only** articulated child that publishes
  `$9A`; `sub_74EBC` installs it only after the parent `art_tile` bit 7 is set;
- both `loc_74AFA` segments and the `loc_74A9A` joint initialize and remain at
  collision zero. Their `moveq #$C,d0` is the flicker cadence argument to
  `Child_DrawTouch_Sprite_FlickerMove`, not a collision-list byte; and
- landing child `loc_74C00` owns `$9C` from its initialization entry.

Enemy touch remains continuously polled for the three actual collision owners.
Root hits clear collision before the per-entry `sub_74FD2` equivalent; the
`$3C` flash timer restores the saved root collision and controller `$AD`. It
does not manufacture `$9A` on the zero-collision segments or joint.

Each child exposes its own native collision byte and zero-initialized
`collision_property`; a generic `boss.defeatStarted` mask must not clear every
child early. Final-hit effects happen on each later child slot. The controller,
both segments, and the kinematic joint take
`Child_DrawTouch_Sprite_FlickerMove` when their behavioral parent first has
status bit 7: set their own status bit 7, clear their local collision, install
`Obj_FlickerMove`, select the source-indexed velocity using `d0=$0C` plus native
subtype, and then move with ordinary gravity and alternate draw cadence until
the native off-screen deletion check. `Set_IndexedVelocity` tests bit 0 of the
**transitioning child's own** `render_flags`; it does not re-read the root's
current flip. The child therefore negates indexed X from its already-latched
`hFlip`. In particular, the controller's own flip can lag the root because
routine 2 and the routine-4 bit-1 early return do not execute
`Change_FlipXUseParent`; segments use the immediate controller flip latched by
their adjusted refresh, and the joint uses its own flip after its native
refresh. That child flip and the selected velocity remain rewind-visible.

Every articulated `Obj_FlickerMove` entry uses the shared native deletion
owner as well: `Camera_X_pos_coarse_back` is
`(Camera_X_pos-$80)&$FF80`, so the unsigned horizontal comparison is
`(x_pos&$FF80)-coarse_back > $280`, followed by the unsigned Y comparison
`y_pos-Camera_Y_pos+$80 > $200`. Masking `Camera_X_pos` directly shifts the
window by `$80` and is not an equivalent approximation. The implementation
uses `S3kBossFlickerMove.isOutsideNativeBounds` for articulated children as
well as debris, with no Big-Arm-local coarse-camera formula.

A cull does not clear the SST slot on that entry. Native `Obj_FlickerMove`
branches to `Go_Delete_Sprite_3`, which installs `Delete_Current_Sprite`, sets
status bit 7 and `$38` bit 4, and returns. The object remains registered—with
the same ObjectManager ID, SST slot, root inventory edge, position, velocity,
and flicker phase—through that frame boundary. Its next own entry executes only
the installed delete callback: no second move, gravity, flicker toggle, or draw,
then ObjectManager/root inventory removal becomes visible. Articulated children
and `loc_74D14` debris share one captured semantic `flickerDeletePending` phase;
they must not call `forgetChild`/`expireDynamic` on the cull entry. Snapshot and
out-of-place restore on both sides of this callback boundary preserve the
pending phase and exact identity, with no optional reference workaround.

The outer and landing children delete on
their own status-bit-7 checks. An unheld grab owner in `loc_74C34` reaches
`loc_74C7A`, clears Player 1 control, and deletes without mutating the
controller. A held grab owner in `loc_74CCC` instead branches directly to
`loc_74BFA` when it sees root status bit 7 and deletes **without** clearing
control; this is part of the shipped `FixBugs=0` final-hit path described below.
Attachment and visual children survive status bit 7 but delete when
`loc_746D8` sets root
`$38` bit 4 through `Child_Draw_Sprite2`; the freshly created defeat-follow
visual has the same bit-4 lifetime. This helper is also deferred:
`Go_Delete_Sprite_2` installs `Delete_Current_Sprite` and sets the child's
`$38` bit 4, but the SST slot clears only on the next own entry. On the signal
entry `loc_749BE` attachment and `loc_74BAE` visual perform their adjusted
refresh first; `loc_74E24` defeat-follow performs its unadjusted refresh first.
All three then submit no draw, remain in ObjectManager/root inventory with exact
ID/slot and a captured `parentBit4DeletePending` phase, and next execute delete
only—with no second refresh or draw. This deferred helper contract does not
change the direct `Delete_Current_Sprite` lifetimes of head, flame, outer,
landing, or grab paths. Rewind tests compare these source-fixed dispositions,
velocities, relative order, pending phases, and SST slots rather than deriving
an expected survivor list from production's current root inventory.

`loc_74D14` debris also retains the full helper boundary. Its initialization
entry performs `Refresh_ChildPositionAdjusted`, latches the root flip, selects
the subtype-indexed mapping and `Set_IndexedVelocity` pair, negates X velocity
from that latched child flip, installs `Obj_FlickerMove`, and draws once. Later
entries use 16.16 `MoveSprite`, add gravity `$38`, toggle the flicker bit and
draw only on alternating entries. They delete on either exact native unsigned
window check: `(x_pos & $FF80) - ((Camera_X_pos-$80)&$FF80) > $280` or
`y_pos-Camera_Y_pos+$80 > $200`. The implementation reuses
`S3kBossFlickerMove` for those shared primitives rather than keeping a
Big-Arm-only one-sided Y cutoff.

`loc_74E12` defeat-follow initialization is a setup-only entry and submits no
draw. Its first draw is the following `loc_74E24` entry, which uses unadjusted
`Refresh_ChildPosition`: its `dx=0,dy=$10` is never mirrored and it does not
inherit the root's flip. It continues drawing through `Child_Draw_Sprite2`
until a later entry observes root `$38` bit 4 and deletes.

Native priority words convert to engine buckets by unsigned division by `$80`,
not `RenderPriority.clamp(rawWord)`. The exact buckets are root/head 5,
controller 3, attachment 4, visual 6, outer 6, segment subtypes 0/1 at 1/3,
joint 3, landing 0, grab 0, defeat debris 2, defeat-follow 4, ship flame 5,
escape floor 6, and floor explosion 1. Landing and grab routines never call a
draw helper and therefore submit no render command. The outer child uses
`Refresh_ChildPositionAdjusted`, mirrors the parent flip, and draws only when
`(V_int_run_count & 1)==0`.

The art-tile priority bit is an independent rendering axis from that numeric
bucket. `loc_74340` sets root `art_tile` bit 7 and no later Big Arm routine
clears it: `loc_74710` and `loc_748D0` clear `render_flags` bit 7, not
`art_tile`; capsule handoff must not clear it. The root is therefore
high-art-priority whenever it draws from landing onward, including the
post-capsule ship phases. The head mirrors root
art priority each own entry through `Child_GetPriority`. Controller,
attachment, visual, both segments, joint and outer start with their copied or
ObjDat value and latch high only at their source `Child_GetPriorityOnce`,
`loc_74A14`, or `sub_74EBC` boundary; the controller/outer priority
transition remains coupled to their delayed collision publication. The
landing/grab slots retain copied art priority even though they do not draw.
Defeat debris, defeat-follow and escape floor take high priority directly from
their high-priority ObjDat art words; floor explosions retain their own
BossExplosion art contract. Ship flame is different: its abbreviated
`SetUp_ObjAttributes3` data has no art word, so `CreateChild1_Normal` copies the
root's then-current `art_tile` high bit into the flame exactly once. The flame
must retain that inherited value rather than return a constant `true` or mirror
later root changes. Each drawable object exposes this
captured/lifecycle-correct art priority through `isHighPriority()` without
conflating it with `getPriorityBucket()`.

`loc_74DEA` does **not** hide the escape floor for the emitter phase. It clears
the transient on-screen bit before the cadence/allocation branch, but
`loc_74D48` rejoins `Draw_And_Touch_Sprite` after every returning state
callback and `Render_Sprites` recomputes on-screen bit 7 from coordinates.
The floor therefore continues to submit its ordinary draw/touch tail on both
qualifying and non-qualifying emitter entries until actual deletion. These
priority, visibility, flip, touch, and cadence rules are tested by actual
render-command/touch collection, not scalar fields alone.

Root drawing is likewise an entry-level callback result, not simply
`!destroyed`. The ordinary `Obj_LBZFinalBoss2` wrapper draws/touches after every
fight routine and still draws on the final-hit entry that installs
`Wait_FadeToLevelMusic`. That final-hit entry and every non-expired fade
entry preserve the mapping selected by the interrupted fight routine;
mapping 5 is not published until fade expiry enters `loc_746D8`.
The fade-expiry entry writes root `$2E=119`, clears render bit 7, invokes
`loc_746D8`, selects mapping 5 and does not draw. `loc_746F4` draws
after each one-pixel rise while the new Y is greater than or equal to
`camera-$40`; equality still draws. The first entry strictly below that
threshold falls into `loc_74710` and does not draw. Capsule wait, two-signal
gate, autowalk, autowalk-target PLC/head allocation, post-ship-crossing,
floor-signal wait, and carried-player callbacks remain no-draw. Drawing resumes
on the first `loc_747D6` ship-rise entry and continues through the ship-rise
transition, cruise, floor allocation, floor wait, and pre-cross escape entries.
The entry that first reaches `camera+$1C0` executes `loc_748D0`, clears render
bit 7, and submits no root command. A captured per-entry render/callback scalar
preserves these exact draw/no-draw boundaries through rewind and re-execution.

### 3. Use only ROM-backed presentation data

Register the Big Arm body/hand sheet and Egg Robo head through the existing
S3K standalone ROM-art provider. Independently verify the following locked-on
ROM ranges before coding them:

- `ArtKosM_LBZFinalBoss2`, `Map_LBZFinalBoss2`, and
  `Pal_LBZFinalBoss2`;
- `ArtKosM_EggRoboHead` and `Map_EggRoboHead`; and
- the existing Robotnik ship, Egg Capsule, explosion, flame, player, and final
  boss 1 registrations reused by the continuation.

Mappings, art, palette bytes, and animation/data tables are never read from
`docs/skdisasm` at runtime. Art loading follows the production Kosinski queue
and provider path. The object may render only when the corresponding ROM sheet
is ready; it must not substitute generated or repository-owned graphics.

Submission timing follows the callers, not an eager root preflight. Root init
queues only `ArtKosM_LBZFinalBoss2`; the initial Knuckles
`Obj_RobotnikHead4` initialization owns its Egg Robo mapping/script and KosM
submission, while the preceding LBZ PLC owners supply the already-required ship
and explosion sheets. At the two-signal gate, `sub_7302E` submits the raw
Robotnik-ship/boss-explosion PLC. At the autowalk target, `Load_PLC $71` and the
Egg Robo KosM submission occur before bit 5 is cleared and the replacement head
is allocated. Do not make init load ship, explosion, or post-gate PLC data early
merely because a later phase will need it.

Raw animation bytes are gameplay presentation data too. Consume
`AniRaw_EggRoboHead` at `$0681D0` and `AniRaw_BossExplosion` at `$083FCC`
through the verified ROM reader rather than duplicating their frames/delays in
Java. `Obj_RobotnikHead4`'s initialization entry retains mapping frame zero and
does not run `Animate_Raw`; animation begins on its next own dispatch. A generic
boss explosion falls through from `Obj_BossExplosion1` initialization into
`Animate_RawNoSSTMultiDelay` on its first own entry. With zeroed raw cursor and
timer, the helper pre-decrements the timer, advances the cursor from 0 to 2,
reads ROM bytes 2/3, and ends that entry at mapping frame 0 with delay timer 1;
`sfx_Explode` is played on that same successful child entry. It neither remains
at raw offsets 0/1 nor advances to mapping frame 1. `BossExplosionHitbox_StartAnim`
only changes routine/callback on the wait-expiry entry; its following own entry
performs that same cursor `0->2`, mapping 0, timer 1 transition. Tests pin raw
cursor, mapping, timer, SFX, invisible wait-expiry boundary, and the full
ROM-driven end callback.

That end callback is a two-entry lifetime. On the terminal `$F4` entry,
`AnimateRaw_CustomCode` calls `Go_Delete_Sprite`, which installs
`Delete_Current_Sprite` and status bit 7; the animation helper then clears the
raw cursor and returns to its caller. Both `Obj_BossExplosionAnim` and
`BossExplosionHitbox_Animate` continue their caller tail on that same entry:
the independently managed `S3kBossExplosionChild` draws its prior mapping, and
the floor hitbox draws that prior mapping and rejoins the ordinary touch list.
Neither clears its SST slot yet. The next own entry performs delete only, with
no animation step, draw, touch, RNG, or repeated init SFX. Preserve the prior
mapping, cursor 0, timer 0, status/pending-delete scalar, native-init-SFX-played
state, ObjectManager ID and exact slot across an out-of-place snapshot at the
terminal boundary. `EscapeFloorExplosionChild` additionally retains its
floor/root edges until that next-entry removal; the shared visible explosion
remains ObjectManager-only.

The floor/touch bytes remain source-literal: `ObjDat3_7510A`'s `$16` is its
mapping frame and its collision byte is zero. `ObjDat_BossExplosionHitbox`
initializes collision `$97`, but `BossExplosionHitbox_CheckParent` clears it
because this floor parent has collision zero. `Draw_And_Touch_Sprite` still
runs its ordinary collision-list tail with that zero byte; neither production
nor tests invent a `$16` floor touch response or retain `$97` here.

`S3kBossExplosionChild` is shared, and its existing public `(x,y)` constructor
already has a caller-owned-audio compatibility contract: established owners
independently choose whether to drain
`S3kBossExplosionController.PendingExplosion.playSfx()`, explicitly play an
effect beside allocation, or remain silent. For example,
`MhzEndBossWeatherMachineChild` plays its separately sourced
`WEATHER_MACHINE` effect before spawning the explosion but deliberately emits
no `EXPLODE` effect there. Changing the constructor to unconditional init audio
would duplicate, delay, or invent audio across unrelated bosses. Keep it
silent and add one explicit named factory, `createWithNativeInitSfx(x,y)`, for
the Big Arm `CreateChild6_Simple` path. A rewind-captured scalar distinguishes
that mode; construction/allocation is still silent, and only the later child's
first own entry plays exactly once before performing the raw cursor `0->2`
transition. The ordinary constructor continues to run the same corrected
ROM-backed raw animation but never plays audio. This is a caller-semantic
contract, not a game/zone predicate.

The compatibility audit covers every current direct constructor owner, which
remains unchanged: `AbstractS3kFloatingEndEggCapsuleInstance`,
`AbstractS3kUprightEggCapsuleInstance`, `AizEndBossInstance`,
`AizMinibossCutsceneInstance`, `AizMinibossInstance`,
`CnzMinibossBlockExplosionControllerChild`, `CnzMinibossInstance`,
`CutsceneKnucklesLbz1CollapseChild`, `CutsceneKnucklesSkIntroInstance`,
`HczMinibossInstance`, `IczMinibossExplosionControllerChild`,
`LbzMinibossBoxKnuxInstance`, `LbzMinibossInstance`,
`MgzDrillingRobotnikInstance`, `MgzEndBossKnuxInstance`,
`MgzMinibossInstance`, `MhzMinibossInstance`,
`CnzEndBossExplosionControllerChild`, `HczEndBossEggCapsuleInstance`,
`HczEndBossInstance`, `IczEndBossInstance`, `LbzEndBossInstance`,
`LbzFinalBoss1Instance`, `MhzEndBossInstance`, and
`MhzEndBossWeatherMachineChild`. Only `LbzFinalBoss2Instance` selects the new
factory. Its controller first creates the child at the parent's unoffset
coordinates and observes whether ObjectManager assigned a live SST slot. On
failure it returns with zero RNG/SFX. On success it consumes exactly one
`Random_Number`: the low word supplies X and the swapped high word supplies Y,
then rewrites the created child's word coordinates. A second RNG draw, a
`>>8` Y extraction, constructor-time audio, or controller-time audio is
source-wrong.

### 4. Port defeat and post-capsule continuation without a synthetic shortcut

The zero-hit branch follows `sub_74FD2`: clear boss collision, enter the shared
fade/defeat timing, pause the timer, create a subtype-4
`Obj_CreateBossExplosion` controller, and install `loc_746D8`. The controller,
not the root, owns the visible boss-explosion allocations. Subtype 4 selects
`CreateBossExp04`: `$39=$80`, X/Y ranges `$20/$20`, and the
`Obj_WaitForParent`/`Obj_BossExpControl1` routine pair. Its creation-time
zeroed `$2E` dispatch reaches `Obj_BossExpControl1` immediately. The byte
`$80` is signed-negative, so its leading `bmi` skips the decrement and emits
with `$39` still `$80`; every later three-entry callback does the same. This
subtype never counts down or self-deletes. It follows the root until
`Obj_WaitForParent` observes the root's `$38` bit 5 at `loc_74710` (or a zero
root code pointer), which is its sole termination path here. The nearby
player-control conditional is assembled with `FixBugs = 0` and must be
documented accurately: the shipped branch restores player control only when
root `$30` is zero; when the player is still marked held it skips that restore.
A fixed build would restore only when held. The held final-hit path must be
proved through the real owners, not by setting a Boolean: `loc_74C8C` first
acquires Player 1 and writes control `$81`, the ordinary eighth touch hit runs
the root before the later grab-owner slot, and `loc_74CCC` then sees root status
bit 7 and deletes through `loc_74BFA` without visiting the control-clear
`loc_74C7A`. Root `$30` stays nonzero, so shipped `loc_7506E` skips its restore
too. Player control therefore remains exactly `$81` across that deletion and
the entire capsule wait, until `loc_7473A` explicitly restores control at the
two-signal gate before installing autowalk lock. Rewind/re-execution at the
later-slot deletion and gate boundaries must preserve this deliberately buggy
lifetime.

Final-hit audio is deliberately different from nonfinal flash audio.
`collision_property==0` branches from `sub_74FD2` directly to `loc_75046`,
before `loc_74FFA`, so the eighth hit plays no `sfx_BossHit`. Creating or
dispatching the subtype-4 controller is also silent. The only explosion sound
on this path comes later from a successfully allocated visible
`Obj_BossExplosion1` on its own initialization entry; allocation failure
therefore produces neither `BossHit` nor `Explode`.

Visible explosion allocation follows `sub_83E84` failure ordering. The
controller first attempts the later-slot `CreateChild6_Simple` allocation at
the parent's unoffset coordinates. Only a successful allocation consumes
`Random_Number` and applies the X/Y offsets; only that created explosion's
first own entry plays `sfx_Explode`. Slot exhaustion therefore consumes no RNG,
plays no explosion SFX, and creates no retained owner edge. The visible child
then owns its ROM-backed `AniRaw_BossExplosion` lifecycle independently.

`Wait_FadeToLevelMusic` pre-decrements the retained `$3F` timer. On expiry it
starts the engine's semantic level-music fade owner, writes root `$2E=119`
**before allocation**, and only then calls `loc_746D8`; the port must not
silently replace this with an idle delay or leave the root scalar at the
pre-decremented negative value. `Obj_Song_Fade_ToLevelMusic` independently
owns a remaining value of 120 after allocation. On its first **own** dispatch
it runs initialization and falls through to pre-decrement that value to 119.
`AllocateObject` scans SST RAM, so the new owner may occupy a slot lower or
higher than the root; the port must assert the actual slot relation and must
not assume a later-slot same-pass dispatch. It restores level music only when
the counter becomes -1, on own entry 121 including initialization. Use an
isolated, rewind-visible native level-fade countdown mode; existing callers of
the shared `SongFadeTransitionInstance` constructor keep their established
timing.
`loc_746D8` selects mapping 5, sets `$38` bit 4, and creates all five
`ChildObjDat_7515E` debris children with native indexed velocities. The root
rises one pixel per object entry until strictly above camera `-$40`, then calls
the engine equivalent of `Boss_LoadEggCapsuleAndAnimals`.

That helper creates a route-8 floating `Obj_EggCapsule`, not an upright or
boss-private fabricated capsule. It also owns the native
`st (_unkFAA8).w` **before** allocating the capsule. Map that byte to
`GameStateManager.endOfLevelActive`: the retained Big Arm root sets it while
executing the `Boss_LoadEggCapsuleAndAnimals` equivalent, the production results
object clears it on its normal exit, and `loc_7473A` polls it directly. The
root must not substitute `endOfLevelFlag`, a results callback, or a timer for
that first signal.

The second signal is the already-typed `WaterSystem` dynamic-water lock, whose
API and snapshot explicitly model `_unkFAA2`. Preserve all three native owners:

1. `Obj_LBZFinalBossKnux` clears `_unkFAA2` before installing
   `Obj_LBZFinalBoss1`; therefore Knuckles initialization in
   `LbzFinalBoss1Instance` clears
   `setDynamicWaterLocked(ZONE_LBZ, 1, false)`, including when a stale locked
   value was restored or carried into the wrapper entry.
2. LBZ's route-8 capsule sets the lock, not the boss. Every post-open routine
   `$10` entry calls `sub_868F8` and then falls through to `loc_866F4`, including
   the entries where the post-open timer has not yet allowed results to start.
   The opening entry is still routine `$08`/`loc_8662A`: it changes the capsule
   to routine `$10`, runs `sub_865DE`, and falls only through the generic
   `Swing_UpAndDown`/`MoveSprite2` tail. It does **not** run `loc_866F4`, move
   left two pixels, or latch water on that entry. The next own dispatch is the
   first `$10` entry that reaches `loc_866F4`. On the entry that does start
   `Obj_LevelResults`, `sub_868F8` changes the capsule to routine `$12` and
   still falls through; subsequent routine `$12`
   entries reach `loc_866F4` through `loc_86716`. After `Swing_UpAndDown`,
   compare unsigned capsule X with the exact route threshold
   `Camera_X_pos - $60`. While
   `capsule.x > camera.x-$60`, subtract two from X and perform `MoveSprite2`.
   Once `capsule.x <= camera.x-$60`, set the LBZ2 dynamic-water lock and return
   without that entry's `MoveSprite2`. The lock is latched and is not a
   results-complete notification.
3. Big Arm `loc_7473A` advances only when
   `!gameState.isEndOfLevelActive()` **and**
   `waterSystem.isDynamicWaterLocked(ZONE_LBZ, 1)`. It never writes either
   signal while polling.

Add the smallest route-phase hook and protected coordinate access needed by an
LBZ subclass of `AbstractS3kFloatingEndEggCapsuleInstance`. The hook runs on
every post-open routine `$10`/`$12` dispatch, beginning one entry after the
opening `$08` dispatch: before results are eligible, on
the same dispatch that starts them, and after they clear if the threshold has
not yet been reached. It owns the leftward two-pixel step and can suppress the
base vertical move on the latch entry. Button, animals, explosion, results
screen, ROM art, and all non-LBZ capsule behavior remain owned by the shared
production capsule.

Object-slot timing is part of the contract. The retained root precedes the
capsule and results objects, so it polls before either later-slot writer in a
pass. A capsule crossing may make `_unkFAA2` true only after the root has
already waited; the results object's exit may likewise clear `_unkFAA8` only
after that pass's root poll. The root first advances on its next own-slot
dispatch after both stored values are ready. The canonical capture independently
shows the signals are not one edge: the root enters `loc_7473A` at frame 5009;
the capsule enters routine `$10` at frame 5155 with X `$4372`, continues its
pre-results left motion, enters routine `$12` at frame 5220 with X `$42F0`, and
reaches the source-derived `$42A0` threshold (`camera=$4300`) around frame 5260;
the root nevertheless remains at `loc_7473A` through frame 5796 and advances at
5797 only after the tally clears.

After those two native conditions are independently satisfied, translate
`loc_7473A-loc_7498E` in order: restore and lock Knuckles, force the walk to
camera `+$50`. Root `$38` bit 5 was set at `loc_74710` before capsule creation;
it remains set through the entire two-signal wait and is cleared only at the
autowalk target in `loc_74784`, immediately before creating the replacement
head. At that same target, submit production PLC `$71` (FinalBoss1 and boss
explosion) and queue the ROM Egg Robo head art. Then raise and launch the ship with
its flame, create the falling FinalBoss1 floor/debris child, wait for that
child's `$38` bit-3 signal, then make exactly 127 qualified later-slot
`loc_74E30` allocation attempts:
`loc_74DEA` initializes `$39=$7F`, pre-decrements it on each
`V_int_run_count&3==0` dispatch, attempts allocation for results `$7E..$00`,
and takes `bmi` to delete on the next qualifying decrement to `$FF`. These are
exactly 127 qualified allocation attempts, not a guarantee of 127 children:
successful emitter children are SST-slot-dependent and therefore number at
most 127. Each escape emitter
selects its absolute position from `word_74E7C`, creates its own subtype-4
`Obj_CreateBossExplosion` controller, waits `$60`, then sets its own `$38` bit
5 before jumping to `Go_Delete_Sprite`. That signal entry only installs the
emitter's pending delete; the emitter remains in ObjectManager and the
root/floor inventories with its exact ID/slot until its next own entry. Its
later-slot controller observes bit 5 in the same object pass through
`Obj_WaitForParent` and installs **its own** pending `Go_Delete_Sprite` callback,
also remaining registered through that boundary; each removes itself only on
its following own entry. Root-owned subtype-4 defeat controllers have the same
two-entry termination when root bit 5 becomes visible. Rewind captures emitter
bit 5, both pending callback phases, parent/controller edges, IDs and slots,
including a snapshot between the emitter signal and later controller slot.
No pending object emits, follows, moves, draws, or performs a bespoke
child/back-edge mutation on its delete entry. In particular, a controller's
next-entry deletion must not call back into its `emitterParent` to clear an
emitter-owned field: native `Obj_WaitForParent` reads the parent but
`loc_83EC2` only installs/delegates deletion. The emitter may already have
deleted in its earlier slot on that same pass. Ordinary ObjectManager and
root/floor inventory pruning when that next-entry deletion executes is
required. The controller alone creates the independent visible `S3kBossExplosionChild`
objects at the native random offsets and three-entry cadence. Force the walk to
X `$4510`, transfer Knuckles to
object control `$83`, alternate player mapping `$8C/$8D` every eleven entries,
and request typed MHZ zone 7, engine act 0 (`StartNewLevel $0700`) only after the carried Y reaches
the native `_unkFAB0+$200` threshold. Reuse the existing typed level-transition
service and current LBZ runtime floor/camera owner; do not invent a timer-based
ending.

The fight/escape owns two additional globals at exact source entries.
`loc_745F6` writes timed `Screen_shake_flag=$14` on the grab-floor impact. Add a
semantic LBZ timed-shake write on the transition/event bridge and an LBZ event
owner that preserves native foreground/background order. Objects write `$14`
before ScreenEvents. The LBZ foreground phase first consumes the offset prepared
on the preceding background phase into `Camera_Y_pos_copy`; only afterward does
the LBZ background `ShakeScreen_Setup` pre-decrement 20 to 19 and publish ROM
`ScreenShakeArray[19] == -5` for the **next** frame. Thus the impact frame keeps
the old camera-copy offset, and the following frame observes base Y minus five.
If Player 1's native routine is at least 6, `ShakeScreen_Setup` neither
decrements the positive timer nor samples the table and publishes zero for the
next copy; the countdown resumes only after the player is eligible. The owner
reads the table through the ROM pipeline, keeps prepared/applied offsets and
the countdown rewind-visible, and publishes the actual runtime camera-copy
offset for exactly that lifetime. A bare `GameStateManager.screenShakeActive`
boolean is insufficient because LBZ's existing offset path is gated by the
unrelated Death Egg rumble state.
`loc_748D0` sets root status bit 6 and `$38` bits 4 and 5, clears
`Boss_flag`, and clears the transient render on-screen bit when the ship first
crosses camera `+$1C0`, before the floor-signal wait. In the engine this
publishes the captured status-bit-6 scalar and clears `currentBossId` on that
entry, not at defeat, capsule opening, or MHZ transition.

The floor's boundary-target write and the gradual workers remain separate
native-visible steps. `loc_74DA4` writes **stored targets**, not current bounds:
`Camera_stored_max_Y_pos=$1000`, `Camera_target_max_Y_pos=$1000`,
`Camera_stored_max_X_pos=$6000`, and `Camera_stored_min_Y_pos=0`, then creates
the three `Child1_Act2LevelSize` callbacks. The Big Arm bridge therefore has a
dedicated literal-target entry into the existing LBZ worker owner; it must not
reuse the generic current-level-size values and must not call current
`setMaxY($1000)`, `setMaxX($6000)`, or `setMinY(0)` on the floor entry.

The three logical workers start from the incoming non-target current bounds and
retain independent 16.16 accumulators. Because `loc_74DA4` allocates them
after the floor's own slot, all three later SST slots execute once in the
**same object pass** as floor settlement. The engine's centralized LBZ
event-owner phase has already run before dynamic objects, so the semantic Big
Arm bridge must perform those three creation-entry ticks as part of the
settlement publication rather than deferring them to the next event frame.
Immediately after the settlement pass the accumulators are exactly
`[$4000,$4000,$8000]` and the current bounds are still unchanged.
`Obj_IncLevEndXGradual` and
`Obj_DecLevStartYGradual` add `$4000` per own entry; their visible integer-step
sequence begins `0,0,0,1,1,1,1,2,...`. `Obj_IncLevEndYGradual` adds `$8000`,
beginning `0,1,1,2,2,3,...`. Thus all three first worker entries mutate only
their accumulator and leave current bounds unchanged; the next ordinary event
frame is their second entry, not their first. Each later entry adds
the accumulator's current high word to max X/max Y or subtracts it from min Y,
then clamps to the stored literal target. Max X and min Y delete when their
candidate reaches/crosses the target; max Y writes an equal `$1000` value and
deletes only on the following overshooting entry because the native branch is
`bgt`, not `bge`. The three accumulator values, active/completed phases, and
stored targets are rewind-visible. Ordinary camera max-Y target easing is a
separate later frame phase toward the same `$1000` target and is tested
separately; it is not evidence that the floor wrote a current bound. The prior
immediate `$0FFE` oracle is rejected.

Likewise, entering
the carried-player phase selects frame `$8C` immediately with timer `$A`; the
first eleven-entry expiry increments the external-frame counter and selects
`$8C` again, the second selects `$8D`, and subsequent expiries alternate.

### 5. Make every graph edge rewind-safe

The root and all spawnable children implement the established rewind recreate
contracts. Scalar routine/timer/fixed-point/collision/RNG-derived state is
captured, including root/child art-tile priority, root status bit 6, the root
fade-handoff counter and native fade-owner remaining counter. Parent,
controller, capsule, flame, and escape-floor references are
captured as rewind IDs and rewired during phase-two recreation. This includes
root/escape-emitter to subtype-4 explosion-controller edges. Visible
`S3kBossExplosionChild` instances are not part of the root or controller graph:
`sub_83E84` creates them with `CreateChild6_Simple`, then
`Obj_BossExplosion1` reads no controller/parent field and ends through its own
animation callback. `ObjectManager` is therefore their sole lifetime and rewind
owner and captures each still-active explosion independently by its own ID,
slot, coordinates, raw cursor/timer/mapping, `nativeInitSfx` mode, and whether
that first-entry SFX has already fired. No root/controller live-explosion
collection and no child-to-controller cleanup back-edge is introduced.

That single ownership also closes the object-slot deletion boundary. A visible
explosion may finish in a later slot after the controller and root have already
run in the same object pass; `ObjectManager` removes its identity before the
frame is eligible for rewind capture. Any captured root/controller collection
would retain the removed Java instance until a later owner entry and fail exact
reference closure at this legitimate boundary. Omitting those non-native
collections makes deletion atomic at the real owner. When
`Obj_WaitForParent` deletes the controller first, already-created visible
explosions remain independently scheduled and finish their native animation;
neither an invented cascade delete nor detach callback is required. Final
constructor-derived offsets and subtypes are captured as scalars or otherwise
represented explicitly so `TestRewindCoverageGuard` does not silently omit
them.

Big Arm's nested child family uses a boss-local two-phase restore-shell pattern,
not geometric reconstruction. The root is itself a dynamic object, followed in
the snapshot by its independently scheduled children. During phase one, every
concrete `BossChild` is recreated through a private `ObjectSpawn` constructor
whose base constructor accepts a null behavioral parent, initializes only the
exact concrete type and structural collections, and copies the captured spawn
position. The object manager can then restore the captured slot and register the
captured object ID without first knowing any parent or sibling reference. During
phase two, compact state restores the root's ordered `children`,
`graphChildren`, and `childOrder` collections and every dedicated parent,
controller, floor, emitter, and capsule reference by exact `ObjectRefId`; final
mutable collections are restored in place. The shell never
uses `nearestLiveObject`, never adopts into a provisional root, and never adds a
shared rewind special case.

The articulated controller has no invented outgoing child collection. At
`loc_749EC`, `CreateChild1_Normal` writes the controller into each created
child's `parent3`; `loc_74A9A`, `loc_74AFA`, and `loc_74C24` then retain their
own controller/root pointers, but the creator stores no returned segment,
joint, or grab-owner address. The engine therefore captures the root's active
structural child inventory plus each child's native-directed controller/root
edge, not `ArmControllerChild.segments`, `joint`, or `grabOwner` back-edges.
This direction matters at defeat: `loc_74C24` deletes after observing root
status bit 7, so ObjectManager can unregister it in its later slot without
leaving an unresolvable controller reference. The root inventory is pruned on
that same child deletion; controller allocation order remains provable from the
root inventory and exact SST slots. The child does not call back through its
native controller pointer or otherwise mutate the controller during deletion;
there is no controller edge to clean. Required-ID decoding remains strict, and
no optional/deferred reference codec is introduced to conceal a stale edge.

This split is required, not merely defensive. The first out-of-place graph test
proved that the prior `BossChild.recreateForRewind` path could not even create a
probe for `RobotnikHead4Child(LbzFinalBoss2Instance, boolean)` (and later
controller-dependent constructor shapes were equally outside the generic probe
matrix). Dynamic ID 2 was therefore absent when phase two restored the root's
captured graph collection, producing `Missing required object reference`.
Deferring or dropping the root collection would hide the missing identity and
lose exact ordering; extending shared constructor guessing would broaden the
runtime for a boss-local graph. Spawn-only shells make phase-one completeness
explicit while leaving all semantic links to the existing exact-ID phase.

A real `ObjectManager` round trip must cover at least the live articulated
grab graph and the post-defeat graph. The latter includes both post-capsule
signals: `GameStateSnapshot.endOfLevelActive`, the LBZ2
`WaterSystemSnapshot.DynamicWaterEntry.locked` value, and the route-8 capsule's
current/subpixel position must restore together. Test-only state-forcing methods
are not production acceptance evidence.

The same manager-backed graph test fills the production S3K SST pool and
captures **on the same object-pass boundary as a failed child allocation**, before
any later root dispatch could prune a bad edge. It covers a mid-table structural
failure and recurring floor/controller failures. The root's exact captured
inventory must equal the live manager-assigned IDs/slots in native successful
allocation order; no destroyed slot-minus-one construction may be reachable
from the root, floor, emitter, or a dedicated field, and strict required-ID
capture/restore must succeed without an optional/deferred codec. After freeing a
slot, re-execution proves that one-shot tables remain partial and do not retry,
the three-entry explosion callback retries at its next source interval, and the
floor consumes the failed qualifying counter value before its next four-V-int
attempt. Successful child ordinals and ObjectRefIds must be identical before and
after out-of-place restore; deliberately perturbing failed-edge filtering,
table-stop behavior, or success-only ordinal commit must make the test fail.

Fixed-point rewind rows seed non-zero arbitrary low words for root, debris,
floor, capsule, held player, and carried player. They then execute both
high-word-only writes and velocity steps, restore out of place, and require the
same low words and next results. Global rows additionally capture the timed LBZ
shake countdown/current offset, `currentBossId`, root bits 5/6, root/child
art priority, post-target PLC submission state, and both root/fade-owner music
timers at their exact object-slot
boundaries. Child rows additionally retain the controller/segment/grab callback
phase, immediate-parent flip, segment raw cursor/timer and stored offset, grab
release timer/frozen position, debris flicker phase/velocity, defeat-follow
first-draw state, and root per-entry draw eligibility.

## Tests and evidence

Use test-driven slices. Each behavior test is written and observed red before
the owning production slice:

1. init, `$59` wait, root routine advance, ROM-data availability, and exact
   initial child allocation order;
2. fall/bob/random/drop and strict boundary/callback cadence;
3. circular child anchors; controller init-position/flip lag and routine-4
   latch; immediate-parent adjusted refresh; segment normal-to-held-to-normal
   callback/offset/animation boundaries, including a no-grab zero-state entry
   proving cursor 0->1 then script-byte-2 mapping 4/8 and timer 9, plus the held
   7/$B override retaining that cursor/timer; delayed `$AD` publication;
   independent art-tile high-priority propagation and persistence for
   root/head/controller/attachment/visual/segments/joint/outer and the
   high-ObjDat defeat/escape children, plus ship-flame copy-on-create
   inheritance from the root rather than a constant or dynamic mirror;
   outer-only, delayed `$9A`; zero-collision segments/joint; immediate landing
   `$9C`; and exact child/root render-command cadence, first-draw, flip, and
   no-draw callback boundaries;
4. a naturally acquired grab through throw, including both player-response
   branches and the release entry `$3F`, frozen cooldown positions, switch-only
   expiry, and next-entry adjusted refresh/range reacquisition under both
   controller flips;
5. eight real touch-response hits, flash restoration, the subtype-4
   controller's creation-time emission, constant signed-negative `$80`,
   three-entry cadence, no final-hit `BossHit` and no controller-time audio,
   later successful-child `Explode`, defeat debris, parent-bit-5 teardown, and the correctly
   documented `FixBugs = 0` branch in both directions (`$30==0` restores and a
   naturally acquired held `$30!=0` path retains player control `$81` through
   later-slot grab deletion and until the capsule gate),
   successful-versus-exhausted visible-explosion
   allocation ordering with zero RNG/SFX on failure and exactly one successful
   RNG advance split into low-word X/swapped-high-word Y, exact articulated
   flicker/deletion disposition, child-own-flip indexed-X velocity, the shared
   `(Camera_X_pos-$80)&$FF80` coarse-back deletion window, debris
   adjusted-flip/indexed-X velocity and
   alternating full-XY `Obj_FlickerMove` lifetime, including the cull-entry
   pending-delete survivor and next-own-entry removal, shared/floor explosion
   `$F4` terminal render/touch plus next-entry deletion, and root-controller
   bit-5 pending termination,
   and the final-hit mapping preservation, root `$2E=119` handoff, and
   slot-order-independent native level-music fade state 120 after allocation,
   119 after its first own dispatch, and restoration on own entry 121;
6. the production floating-capsule/results lifecycle, entered through the real
   Knuckles `LbzFinalBoss1Instance` handoff and eight attacks delivered by the
   ordinary `ObjectTouchResponseController`, proving Boss_flag setup and the
   two-signal
   gate in three observable stages: before the left threshold
   (`endOfLevelActive=true`, water lock false, root waits), after the capsule
   crosses `camera-$60` (`true`, true, root still waits), and after the results
   object clears active (`false`, true, root advances on its next slot entry),
   followed by the complete post-capsule transition, exact bit-5 lifetime,
   post-autowalk PLC/head submission, timed impact-shake offsets, literal
   `$6000/0/$1000` stored camera targets with three independent gradual-worker
   accumulators (same-settlement-pass first entries at
   `$4000/$4000/$8000`, unchanged bounds, then later fixed-point sequence),
   continued floor draw/touch throughout the emitter phase, and status bit 6
   plus Boss_flag clear at the ship threshold;
7. articulated and defeat graph rewind round trips, with snapshots on both
   sides of the capsule threshold and deterministic restore/re-execution of
   capsule position, the two global signals, writer order, and root state. At
   the defeat and escape-controller boundaries, enumerate active
   `S3kBossExplosionChild` instances only from `ObjectManager`, capture their
   exact IDs, slots, coordinates, and animation pair/timer state, and prove an
   out-of-place restore retains those values. Snapshot immediately before
   controller teardown, re-execute through deletion, and prove the pre-existing
   visible explosions remain managed, advance independently, and eventually
   self-finish; neither test nor production may obtain them from or add them to
   a root/controller list or back-edge. The late pre-capsule snapshot also
   proves the deleted `loc_74C24` grab owner is absent from the root inventory
   and that the controller declares no non-native segment/joint/grab-owner
   outgoing fields, while each still-active articulated child retains its exact
   native-directed controller/root ID. Before deletion, capture the original
   articulated allocation order and SST slots; after the real child deletion,
   assert the root inventory equals the surviving articulated objects enumerated
   independently from ObjectManager, in that original relative order and with
   the same exact slots. Out-of-place restore and one re-executed boundary entry
   must preserve that equality. The test must remain red for a child-to-controller
   cleanup callback/mutation, an optional reference codec, or any missing
   required ID. With the production SST pool exhausted, capture immediately
   after mid-table and recurring allocation failures and prove no failed object
   is retained, successful ordinals/IDs have no phantom gap, later table entries
   stop, one-shot owners do not retry, and recurring owners retry only at their
   source three-entry/four-V-int boundary after a slot is freed. At an
   articulated/debris flicker cull boundary, the culled object remains in both
   ObjectManager and root inventory with the same exact ID/slot and a captured
   pending-delete phase; restore/re-entry removes it only on its next own
   callback, with no further motion/draw. The same graph coverage captures
   literal camera targets, all three worker accumulators/phases, the
   same-settlement-pass `$4000/$4000/$8000` zero-motion entries and later
   max-X/min-Y/max-Y increments, then requires
   identical out-of-place restore/re-execution. It also snapshots the emitter
   `$60` bit-5 signal before and after the later controller slot so both
   independent `Go_Delete_Sprite` callbacks, retained edges/slots and
   next-entry removals replay exactly; and
8. a Knuckles `lbz_2` scenario replay that drives the committed BK2 through
   the ordinary frame-closure path and asserts native routine/position/graph
   checkpoints from the comparison-only aux stream without writing trace state
   into the engine.

Run the focused boss/registry suite, ROM-art crawler, rewind coverage and graph
guards, required S3K keep-green tests, and the Knuckles LBZ trace lane under
JDK 21 with the canonical locked-on ROM. If an earlier engine divergence keeps
the whole trace red, record its exact first frame/field and retain a scenario
checkpoint only if the production route independently reaches Big Arm. Never
relax tolerances or fit constants to the movie.

The fight suite contains explicit mutation-sensitive methods for the routine
`$0A` offset-0/2/4/6/8 table and bounce fallthroughs, drop/land/rise strict
boundaries, grab side selection, vulnerable/invincible throw, controller
routine-2 activation delay, first-entry stale position, flip latch and bit-1
frozen position; segment held/release callback ordering; grab cooldown freeze
and reacquisition delay; native priority/render cadence including root,
defeat-follow and debris callback boundaries; debris flip/indexed velocity,
alternating draw and horizontal/vertical deletion; arbitrary-low-word
preservation; and the naturally acquired held `FixBugs=0` branch. Each render
test collects real commands on consecutive own entries and is red if an object
merely exposes the expected scalar while drawing on a native no-draw entry. A
nonzero prepared shake offset must also distinguish every
`Camera_Y_pos_copy` state from a base-camera mutation. The timed-shake test pins
the impact frame's old applied offset, background preparation of timer 19 and
offset -5, next-frame application, then Player routine 6 pausing the countdown
while publishing zero. A
Surefire selector listing a nonexistent method is not evidence; the named
methods must exist and report their own executed counts.

Two focused articulated-flicker methods pin the review-found boundaries.
`articulatedFlickerVelocityUsesTransitioningChildOwnFlip` naturally creates a
controller/root flip lag, triggers status-bit-7 transition, and requires the
indexed X sign from the controller's latched own flip; it must fail if the root
flip is consulted at transition. It also checks a segment whose immediate
controller-derived flip opposes the root. `articulatedFlickerCullUsesCameraCoarseBackWindow`
uses a camera value with bit `$80` set and exercises both an object at native
coarse-back (survives) and at coarse-back+`$300` (deletes after movement), so a
plain `cameraX&$FF80` implementation reverses at least one lifetime result.
`sourceFixedArticulatedDefeatDispositionRestoresAndReexecutes` repeats those
own-flip and coarse-back boundaries around an out-of-place snapshot: exact IDs,
slots, selected velocities, survival/deletion, and next flicker state must
match after restore and re-entry. Expected values come from the source formula
and pre-transition child flip, never from production's survivor inventory or
the root's current flip.

`segmentFirstRawStepUsesCursorPlusOneWithoutGrabMask` drives each segment
subtype with the controller's grab flag clear. From zero cursor/timer it
requires cursor 1, mapping 4/8 from script byte 2, and timer 9 on that entry.
A second branch enables grab only after recording the raw result and proves
the same-entry 7/$B override masks mapping alone while leaving cursor/timer
unchanged. It must fail for `script[cursor]`, and the no-grab branch prevents
the held override from hiding that mutation.

`segmentRawRestartReadsFcAndRoundTripsBeforeOwnEntry` advances each no-grab
subtype to cursor 5 with timer 0, snapshots immediately before the
expired-timer entry, and restores out of place. One original/restored own entry
must advance cursor 6, read byte 7 `$FC`, publish mapping 7/$B and timer 9, and
clear the cursor to 0.
It must fail for reading `script[cursor]`, retaining cursor 6 after restart,
retaining the prior mapping, or failing to capture cursor/timer state.

`flickerCullInstallsDeleteCallbackBeforeNextEntryRemoval` runs one articulated
child and one defeat-debris child through an exact horizontal cull. On the cull
entry each remains live, keeps its manager ID/root edge and slot, sets the
pending callback, and submits no draw; the next own entry performs no movement
and removes it. This must fail for same-entry `forgetChild`/`expireDynamic` or
for a second move before deletion. The graph method above snapshots the live
pending object and repeats the next-entry removal out of place.

`bit4ChildrenRefreshThenDeferRemovalAcrossRestore` covers all three
`Go_Delete_Sprite_2` callers, not a representative subset. With root bit 4
newly visible, `loc_749BE` attachment and `loc_74BAE` visual must perform their
adjusted refresh, while `loc_74E24` defeat-follow performs its unadjusted
refresh; each then submits no draw and remains in ObjectManager/root inventory
with the same ID/SST slot and captured pending callback. The test snapshots
each signal boundary out of place. Its next own entry must perform no second
refresh or draw and must remove the manager ID/root edge. The oracle must fail
same-entry deletion, a skipped signal-entry refresh, a second delete-entry
refresh, any signal/delete-entry draw, or an uncaptured pending phase.

`bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence` seeds
current max X/min Y/max Y away from `$6000/0/$1000`, invokes the production
bridge, and requires those current values to remain unsnapped while the stored
targets and max-Y target become the source literals. The bridge return itself
must already expose same-pass first-entry accumulators
`$4000/$4000/$8000` with unchanged bounds; a zero-accumulator result is red.
Consecutive later logical worker entries assert the remaining visible-step
sequences `$4000: 0,0,1` for max X/min Y and `$8000: 1,1,2` for max Y,
then run to exact clamping/deletion.
The real production-route method independently asserts floor settlement does
not directly write the three current bounds, does run those same-pass first
ticks, and continues collecting a floor draw/touch tail on qualifying and
non-qualifying emitter entries; any later ordinary camera max-Y easing is
identified as its separate frame phase.
`fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries` captures the
worker state immediately after the settlement-pass zero-motion entries and
before a nonzero later entry, then
restores/re-executes exact accumulators, current bounds, target bounds and
active/completed phases.

`finalHitPreservesMappingUntilFadeExpiryAndRunsNativeFadeCounters` seeds a
non-5 fight mapping, applies the real final touch, and requires that mapping on
the hit entry and every non-expired root fade entry. At expiry it requires
mapping 5, root timer 119 before `AllocateObject`, and a new native fade owner
with remaining value 120 immediately after allocation. The test records the
real root/owner SST slots, then advances ObjectManager until that owner's first
own dispatch changes 120->119, whether its slot is lower or higher than the
root. An out-of-place snapshot then proves no music restore through owner entry
120 and exact restore/deletion on own entry 121. The legacy shared
fade-constructor tests must remain unchanged.

`shipCrossingSetsStatusBit6AndRetainsArtPriority` crosses the exact
`camera+$1C0` threshold and requires status bit 6, root flags 4/5, Boss_flag
clear, no root draw, and root art priority still high. A companion
`artTilePriorityPropagatesIndependentlyFromPriorityBucket` collects real
commands before/at/after `loc_74340`, delayed controller/outer activation,
capsule handoff and escape. It checks root/head/controller/attachment/visual/
segments/joint/outer plus high-ObjDat debris/follow/floor without accepting the
correct bucket as a substitute for `isHighPriority()`. Its flame branch creates
otherwise identical flames from low- and high-art-priority roots and requires
the copied values to remain low/high even after each root is toggled; a constant
`true` or dynamic parent mirror must fail.

`terminalRawCustomCodeDrawsOldFrameThenDeletesNextEntry` drives both shared
explosion audio modes to `$F4`: the terminal own entry keeps the old mapping,
draws once, retains its ID/slot with cursor/timer zero and captured pending
delete, and the following own entry deletes without another draw or SFX.
`floorExplosionTerminalEntryDrawsTouchesAndDefersRemoval` exercises a real
`EscapeFloorExplosionChild` through production ObjectManager and the ordinary
touch-response pass. The `$F4` entry retains its floor/root edges and slot,
draws the old mapping and executes the normal zero-collision touch-list tail;
the next own entry clears the edge/slot without draw/touch. An out-of-place
snapshot at each pending boundary must reproduce the same render/touch count,
audio state and next removal.

`emitterAndControllersDeferGoDeleteAcrossLaterSlots` pins allocation order.
The `$60` emitter expiry sets its stop bit and remains live/pending; the later
controller slot sees the signal, becomes independently pending and also
remains live. The next own emitter/controller entries remove each ID without
emission or bespoke child/back-edge mutation, while ordinary manager/root/floor
inventory pruning occurs. The root defeat controller repeats the same
root-bit-5 signal/pending/next-delete sequence. The graph suite snapshots both
before and after the later controller slot and asserts exact root/floor/emitter
edges, IDs, SST slots, emission count and callback phase after out-of-place
restore/re-execution. It retains a direct reference to the already-deleted
emitter shell and proves controller deletion does not clear or otherwise
mutate that shell; a `forgetController` callback must make the test fail.

The shared explosion boundary has its own mutation proof. A direct
`S3kBossExplosionChild` test constructs the ordinary compatibility mode and
the named native-init-SFX mode with identical coordinates/services. Both are
silent at construction; the ordinary mode remains silent through update, while
the native mode plays exactly once on its first own entry and never again.
The Big Arm controller test fills the SST pool to prove failure leaves RNG and
audio untouched, then frees one later slot and proves allocation itself is
silent, one RNG value supplies low-word X/swapped-high-word Y, and only the
later child entry produces one SFX. Rewind before and after that child entry
must not replay or lose the one-shot. Existing capsule/AIZ/HCZ/ICZ/LBZ/MGZ/MHZ
consumer tests run as a compatibility fleet so the isolated factory cannot
silently change established caller-owned audio choices. In particular the MHZ
weather-machine path must remain free of `EXPLODE` while retaining its existing
separately sourced `WEATHER_MACHINE` effect.

A separate allocation-closure test uses the production `ObjectManager`, not a
mock allocator. It exhausts later SST slots at each selected owner, executes the
real callback once, and captures immediately. It enumerates every root/floor/
emitter edge and compares it with manager IDs and slots before attempting an
out-of-place restore. It then frees exactly one slot and re-executes enough
object entries to distinguish no retry, three-entry retry, and four-V-int retry;
the expected successful child ordinal remains the next committed ordinal. The
test is mutation-sensitive to retaining the destroyed return value from
`spawnChild`, continuing a stopped `CreateChild1_Normal` table, consuming an
ordinal on failure, or retrying an escape emitter's one-shot controller.

## Acceptance boundary

Land code only if the native root graph, grab, defeat, floating capsule,
post-capsule continuation, ROM assets, and rewind graph all have source-backed
tests. The canonical route must at minimum reach Big Arm through production
objects and validate real checkpoints. The production route must reach `$CC`
through the live FinalBoss1 wrapper/handoff and real touch-response attacks
rather than constructing it or calling `onPlayerAttack` directly; a full green trace is preferred but an
unrelated earlier trace frontier is reported rather than hidden. If any
post-capsule owner cannot be represented without guessed state, stop before
shipping a partial visible fight and publish the independently verified ROM and
trace timeline as a validation artifact instead.
