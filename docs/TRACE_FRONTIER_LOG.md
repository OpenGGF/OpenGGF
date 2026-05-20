# Trace Frontier Log

## 2026-05-20 - S2 Tails y_radius preservation on non-rolling landing (MCZ2 frame 1290 -> 1487)

- Branch: `develop` worktree `agent-a6820a41fdf6642c1`
- Command: `mvn test -Dtest=TestS2Mcz2LevelSelectTraceReplay -q`
- Result: MCZ2 frontier advanced from frame 1290 (816 errors) to frame 1487 (773 errors).
- Regression check: `TestS2MczLevelSelectTraceReplay` still at frame 1085 (452 errors,
  pre-existing); `TestS2Ehz1TraceReplay` still at frame 304 (813 errors, pre-existing);
  `TestS2ArzLevelSelectTraceReplay` passes (unchanged).

### Root cause

`ObjectManager.SolidContacts.clearRollingOnLanding()` had an else-if branch that called
`applyStandingRadii(false)` whenever a player landed with `rolling=false` and non-default
collision radii. This is S3K behavior (`Player_TouchFloor`, `sonic3k.asm:24341-24343` /
`29134-29136` — unconditionally resets y_radius/x_radius before testing `Status_Roll`).

S2 `Tails_ResetOnFloor` (`s2.asm:40624-40641`) uses `btst #Status_Roll; bne
Tails_ResetOnFloor_Part2`, gating the y_radius reset on `Status_Roll` being set. When not
rolling, the reset is skipped entirely, preserving any stale y_radius from the despawn path.

In MCZ2 at frame 1290, the `TailsCPU_Flying` respawn path (`s2.asm:38797`) clears rolling via
a direct status byte write (`Status_InAir`) without touching y_radius, leaving y_radius=14
(rollYRadius, not standYRadius=15). The spurious `applyStandingRadii(false)` reset y_radius
14→15 one pixel too early, raising Tails' ceiling probe by 1 px and producing a different
ceiling collision result from the ROM.

### Fix

Gated the non-rolling radius reset in `clearRollingOnLanding()` on
`featureSet.landingRollClearUsesCurrentYRadiusDelta()`, which is true only for S3K. S2 and S1
now skip the else-if branch entirely when not rolling, matching `Tails_ResetOnFloor` gate.

Files changed:
- `ObjectManager.java` (`SolidContacts.clearRollingOnLanding`) — gate added
- Debug prints removed from `PlayableSpriteMovement.java`, `CollisionSystem.java`,
  `AbstractPlayableSprite.java` (no logic change)
- `DebugMCZ2BlockDump.java` deleted (diagnostic test file)

### New MCZ2 frontier (frame 1487)

`air mismatch (expected=0, actual=1)` — Sonic is in the air in the engine but on the ground
in the ROM. At frame 1487, Sonic is riding SwingingPlatform slot 17 (x=0x06A0, y=0x05B4) in
the ROM. The engine SwingingPlatform position diverges due to OscillationManager byte drift,
a pre-existing SwingingPlatform oscillation issue separate from the fix in this commit. The
frame 1290 cascade (816 → 773 errors, 43-error improvement) was hidden behind the y_radius bug.

## 2026-05-19 - S2 SwingingPlatform (Obj15) out-of-range unload + CalcSine angle convention (MCZ2 frame 1009 -> 1290)

- Branch: `develop` worktree `agent-a262078995bf698e7`
- Command: `mvn test -Dtest=TestS2Mcz2LevelSelectTraceReplay -q`
- Result: MCZ2 frontier advanced from frame 1009 (909 errors) to frame 1290 (816 errors).
- Regression check: `TestS2MczLevelSelectTraceReplay` still at frame 1085 (452 errors,
  pre-existing); `TestS2Ehz1TraceReplay` still at frame 304 (813 errors, pre-existing);
  `TestS2ArzLevelSelectTraceReplay` still at frame 563 (1482 errors, pre-existing).

### Root causes

Two independent bugs in `SwingingPlatformObjectInstance`:

**Bug 1 — Constructor position-based immediate unload:**
The constructor called `updatePositions(0)` to initialise the platform position.
At oscValue=0 with chainCount=8, `cos(0)=256` produces `xOffset = 256*136/256 = 136`.
So `this.x = baseX + 136`. For the MCZ2 spawn at baseX=0x0620, camera≈0x03C0:
`isOutOfRangeS1(0x06A8, 0x03C0) = (0x06A8 - 0x03C0 + 128) = 0x380 = 896 > 640` → immediate
unload. Marked dormant before first `update()`. The platform never became active.

Fix: removed `updatePositions(0)` from constructor; `this.x = baseX`, `this.y = baseY`.
Added `getOutOfRangeReferenceX()` override returning `baseX`, matching ROM's use of
`obX(a0)` (parent `x_pos` = spawn X = baseX during the out-of-range check in `loc_FE50`).

**Bug 2 — Wrong CalcSine angle convention (1-pixel X error):**
`updatePositions` used `swingAngle = (oscValue - 0x40) & 0xFF` and mapped `(-sin, cos)` to
`(X, Y)`. This assumed SINCOSLIST perfect antisymmetry. The ROM table is NOT antisymmetric:
SINCOSLIST[147] = -117 while SINCOSLIST[19] = 115 (verified against `sinewave.bin`).
At osc=0x53: ROM X offset = cos(0x53) * chainLength / 256 = -117*40/256 ≈ -19 px;
engine: -SINCOSLIST[0x13] * 40 / 256 = -115*40/256 ≈ -18 px. Off by 1 pixel.

Fix: call `calcSine(oscValue)` / `calcCosine(oscValue)` directly (matching ROM `CalcSine`),
then use sin→Y, cos→X. Verified exhaustively: 0 divergences over all 3840
(osc ∈ 0..255) × (chainCount ∈ 1..15) combinations against the ROM fixed-point simulation.

**Additional fix — BOUNCE clamp logic:**
The `updateBounceSwing` method previously had a `Math.abs(dx) < 0x20` player-proximity gate
that does not exist in ROM `sub_FE70` (s2.asm:22556-22586). Also, BOUNCE_LEFT threshold
was `oscValue < 0x40` (should be `< 0x3F`; osc==0x3F requires sound + clamp).
Both fixed to match ROM exactly.

### New MCZ2 frontier (frame 1290)

`tails_y mismatch (expected=0x04FE, actual=0x04FF)`. Tails is not riding the
expected SwingingPlatform (`ROM: onObj=23, engine: onObj=FFFFFFFF`). The engine's
nearby platform for Tails (s23 @06D7,054E) differs from the ROM's (s25 @068F,054B)
in X position (06D7 vs 068F). This is a separate sidekick-on-platform riding
issue; not in scope for this iter.

## 2026-05-19 - S2 SteamSpring (Obj42) bypasses offscreen sidekick gate (MTZ3 frame 460 -> 765)

- Branch: `develop` (HEAD `1b5308308`)
- Worktree: isolated agent worktree, `git reset --hard develop`
- Command: `mvn -q -Dmse=relaxed "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MTZ3 frontier advanced from frame 460 to frame 765 (2626 -> 2601 errors).
- Regression check (run together): TestS2Mtz3 PASS-advance; TestS2Mtz (MTZ1)
  unchanged frame 375 tails_air (905 errors, pre-existing); TestS2Mtz2 unchanged
  frame 305 y mismatch (2325 errors, pre-existing); TestS2Mcz2 unchanged
  frame 1079 y mismatch (802 errors, pre-existing); TestS2Ehz1 PASS.
  Pre-fix baseline confirmed by `git stash` run of MTZ/MTZ2 -> same
  frontier+error counts (905 @ f375 and 2325 @ f305).

### Root cause

ROM `Obj42` (SteamSpring) routine 2 (`loc_26688`, `s2.asm:52030-52049`) calls
`SolidObject_Always_SingleCharacter` for BOTH Sonic and Tails with no
intervening on-screen check:

```
loc_26688:
    ; Sonic call
    moveq #p1_standing_bit,d6
    jsrto JmpTo2_SolidObject_Always_SingleCharacter
    ; Tails call
    lea (Sidekick).w,a1
    moveq #p2_standing_bit,d6
    jsrto JmpTo2_SolidObject_Always_SingleCharacter   ; no on_screen gate
```

This is the same pattern documented as pitfall **P27**
(`.agents/skills/s2-implement-object/rom-pitfalls.md:1214-1245`):
`SolidObject_Always_SingleCharacter` jumps directly to `SolidObject_cont`
(`s2.asm:35147`) without the `SolidObject_OnScreenTest` gate at
`s2.asm:35140-35145`, and the regular `SolidObject` P2 prologue's
`render_flags.on_screen` check at `s2.asm:34825-34828` is not on this
code path.

The engine still applied `shouldSkipOffscreenSidekickFullSolid`
(`ObjectManager.java:7861`) to the SteamSpring's P2 contact pass, returning
early before reaching the new-landing detection in
`processInlineObjectForPlayer`. The gate only short-circuits when the
sidekick is off-screen for render purposes; at MTZ3 f460 Tails is at
`(0x0328, 0x02D0)` and the camera is at `(0x0282, 0x018C)`. With a 32-pixel
Y margin and a 224-pixel viewport, Tails' `relY = 0x143 = 323` exceeds the
`height + margin = 256` limit, so the gate fires. The SteamSpring at
`(0x0330, 0x02F0)` with `half_height = 0x10` exposes a landing surface at
`y = 0x02E0`; ROM lands Tails exactly there (`y = 0x02D0` with
`half_height = 0x10` puts her bottom on the surface). Engine Tails kept
falling (`y_speed = 0x0450` vs ROM's snap-to-0).

### Fix

`SteamSpringObjectInstance` now overrides `bypassesOffscreenSolidGate()`
to return `true`, mirroring `SpringObjectInstance` and
`LauncherSpringObjectInstance`. The override is per-object as required by
P27 step 3 -- the regular `SolidObject` P2 path still gates on sidekick
render state for objects that don't use the `Always` variant.

### Noted-but-not-applied (P27 follow-up candidates)

S2 callers of `SolidObject_Always_SingleCharacter` from `s2.asm` that lack
the override in the engine today:

- Obj66 (MTZSpringWall) -- `s2.asm:52805/52824` (`Obj66_Main`).
- Obj7B (PipeExitSpring) -- `s2.asm:55919/55927` (`Obj7B_Main`).
- Obj86 (Flipper) -- `s2.asm:58002/58011` (`Obj86_HorizontalType`).
- ObjD6 (PointPokey) -- `s2.asm:58597` (`ObjD6_Main`).

These are candidates for follow-up iters when their zone's trace frontier
points at sidekick missed-landing or missed-side-collision behaviour. Not
applied here per `docs/prompts/trace-frontier-advancement.md` rule 7
(minimum-viable-change).

### MCZ2 frontier (f1079) -- still blocked, not advanced this iter

MCZ2's first error is at frame 1079 with `y mismatch (expected=0x05B4,
actual=0x05A9)`. Sonic stands on a SwingingPlatform (`Obj15`); ROM's
`onObj=10` (slot 16) vs engine `onSlot=23(0x15)`. Both engine and ROM
have multiple SwingingPlatforms at similar positions, but the slot
assignment differs (engine slot 17 @0620,05D0 matches ROM slot 16; engine
slot 23 @061C,05C5 matches ROM slot 17 @0683,05C5 by y but the engine x
is 0x67 px west of the ROM x). The 5-pixel y discrepancy and 0x67-pixel
x discrepancy suggest the engine and ROM are riding *different* swinging
platforms with different rotation phases or different chain configurations.
This is a deeper SwingingPlatform parity issue that needs dedicated
investigation; not in scope for this iter.

Persistent ledger for trace replay frontier work. Update this file whenever a
trace fix is committed, a frontier moves, a previously passing trace regresses,
or a full `*TraceReplay` sweep is run to choose the next target.

## 2026-05-19 - S2 CNZ2 Crawl closer-player investigation (frontier unchanged at f1490)

- Branch: `develop` (HEAD `1b5308308`)
- Worktree: isolated agent worktree, `git reset --hard develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: unchanged, 1057 errors, frame 1490 `tails_y_speed` mismatch
- Outcome: closer-player fix attempted and reverted (see below); frontier log updated only

### Investigation summary

CNZ2 frame 1490 root cause: Crawl (`ObjC8`, slot s17 at `@0735,0470`) is in ATTACKING state
(`rtn=06`) in the ROM but stays in ENEMY (`col=$17`) in the engine. ROM `Obj_GetOrientationToPlayer`
(`s2.asm:72320-72346`) picks the **closer** of Sonic and Tails by absolute X distance (`mvabs.w`
+ `bls.s`). At frame 1490, Tails (dx≈0x003D) is closer to the second Crawl than Sonic (standing
at `@0776`), so the ROM's `loc_3D416` proximity check fires on Tails → Crawl enters ATTACKING.
Engine only passes the main player (Sonic) to `updateWalking`/`updatePausing` proximity checks →
ATTACKING never fires for Tails-triggered proximity → Crawl stays ENEMY → no Tails y_speed change.

### Closer-player fix attempt

Added `closestPlayer(mainPlayer)` helper in `CrawlBadnikInstance` that walks `services().sidekicks()`
and returns the player with the smaller `|getCentreX() - currentX|`, matching the ROM's `mvabs.w +
bls.s` logic (sidekick wins only if **strictly** closer). Also fixed `updatePausing()` to accept
and use the closest player for the proximity check (ROM `loc_3D2A6` calls `loc_3D416` before the
timer-expiry branch), and corrected the pause-timer comparison from `<= 0` to `< 0` to match ROM's
`bmi` (fires when result is negative, not zero).

Result: frontier moved from **f1490 → f630** (1057 → 1096 errors). The fix correctly enters
ATTACKING at the right frame for the second Crawl (Tails trigger at f1490), but introduces a new
first-error at f630.

### Frame 630 regression analysis

At frame 630, a **different Crawl** (slot s20, the first CNZ2 Crawl at X≈0x032E/0x032F) bounces
Sonic. The bounce happened in the ROM too (ROM rtn=06 at f630, Sonic velocity changes). But the
engine Crawl s20 is 1 pixel further right (X=0x032F) than the ROM Crawl (X=0x032E), causing a
different `calcAngle(dx, dy)` → different bounce velocities (`y_speed: expected=-0666, actual=-0682`).

**Key findings:**
1. The 1px X position drift in Crawl s20 (`@032F` engine vs `@032E` ROM) is **pre-existing** —
   it exists in the original code too (confirmed by reverting the fix and observing the same
   Crawl position in the diagnostic text). The drift is invisible in the original code because
   Sonic never reaches s20 with the right trajectory to trigger a bounce.
2. The second Crawl (s17) also has a 3px X drift (`@0732` engine vs `@0735` ROM), also pre-existing.
3. Both Crawl position drifts are NOT caused by the closer-player fix. They are a separate bug in
   `CrawlBadnikInstance`'s subpixel movement accumulation or spawn-position initialization.
4. The closer-player fix is ROM-accurate per `Obj_GetOrientationToPlayer` but cannot land until
   the Crawl X-position drift is diagnosed and fixed.

### Crawl X-position drift: candidate root causes

The ROM's `ObjectMove` (s2.asm:29994-30008) uses **32-bit fixed-point**:
- Loads `(x_pos << 16 | x_sub)` as 32-bit value `d2`
- `ext.l; asl.l #8` on x_vel → for vel=0x0020, becomes 0x00002000
- `add.l d0, d2` → x_sub (lower 16 bits) += 0x2000 per frame; overflow carries to x_pos

Engine Crawl uses 8-bit xSubpixel: `xSubpixel += 0x20; if xSubpixel >= 0x100: x += 1, xSubpixel &= 0xFF`
which is equivalent to the upper byte of ROM x_sub — should produce identical pixel steps.

Candidate sources of the 1px/3px drift:
- **Spawn x_sub initialization**: if ROM initializes x_sub to a non-zero value (e.g., from
  adjacent memory or LoadSubObject), the first pixel arrives sooner in the ROM than in the engine
  (where xSubpixel starts at 0). Level layout parsing may not provide a sub-pixel component.
- **Proximity check timing delta (pre-existing, not from this fix)**: if the original engine code
  triggers ATTACKING 1 frame earlier/later than the ROM (due to Sonic-only vs closest-player
  selection), the Crawl walks 1 extra frame → accumulates 0x20 subpixels → may produce 1 pixel if
  the accumulator was at 0xE0-0xFF before the stop.
- **Walk-timer beq/bmi boundary**: ROM walking uses `beq.s` (branch when timer==0), original engine
  used `<= 0` (same for normal countdown); these are equivalent for fresh timer values.

### Prerequisites for the closer-player fix to land

1. Diagnose and fix the Crawl X-position drift (need to add Crawl x_sub/xSubpixel to the trace
   recorder or add targeted logging to narrow down which frame the 1px diverges).
2. Or, if the drift is purely from the proximity timing: fix the proxy timing independently first,
   verify Crawl position matches, then add the closer-player change on top.

### Cross-game regression check

Reverted code is on develop; no new test failures introduced. CNZ1, EHZ1, MCZ2 frontiers unchanged.

---

## 2026-05-19 - S2 SwingingPlatform chainCount cap + half-link platform offset (MCZ2 frame 1006 -> 1079)

- Branch: `develop` (HEAD `6acc363ef`)
- Worktree: isolated agent worktree, `git reset --hard develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mcz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MCZ2 frontier advanced from frame 1006 to frame 1079 (781 -> 802 errors).
- Regression check: `TestS2Mcz1LevelSelectTraceReplay` PASS;
  `TestS2Ehz1TraceReplay` PASS;
  `TestS2CnzLevelSelectTraceReplay` still at frame 3906 (22 errors, pre-existing — verified by `git stash` baseline run).

### Root cause

`SwingingPlatformObjectInstance` (Object 0x15) caused Sonic to land 24 pixels
higher than the ROM platform in MCZ2 at frame 1006. Two related bugs:

1. **chainCount clamp**: The engine capped subtype low-nybble at 7
   (`Math.min(7, subtype & 0x0F)`), but ROM `Obj15_Init` (`s2.asm:22480`) just
   does `andi.w #$F,d1` with no upper cap. The MCZ2 spawn at `(0x0620, 0x0548)`
   has subtype `0x18`, so ROM uses 8 chain links while the engine used 7.
2. **Missing half-link platform offset**: ROM `sub_FE70` (`s2.asm:22645-22654`)
   accumulates `sin/cos*0x10` per chain link in the chain loop, then halves the
   last increment (`asr.l #1`) when writing the platform's final position. Net
   effect: the platform's offset from the pivot is `(chainCount + 0.5) * 0x10`,
   not `chainCount * 0x10`. The engine omitted the half-link, putting the
   platform 8 pixels too high.

With chainCount=7 and no half-link offset, the engine placed the platform at
`baseY + 0x70 = 0x05B8` while ROM placed it at `baseY + 0x80 + 0x08 = 0x05D0`.
The 24-pixel gap meant the engine's platform-top was at y=0x05B0 (still in
Sonic's fall path) while ROM's was at y=0x05C8 (below where ROM Sonic landed at
y=0x05B4 on a different object, slot 10). Engine therefore "caught" Sonic on
the SwingingPlatform that ROM never touched.

### Fix

`SwingingPlatformObjectInstance.java`:
- Removed the `Math.min(7, ...)` cap; chainCount is now `max(1, subtype & 0x0F)`
  matching the ROM mask.
- Changed `chainLength = chainCount * 0x10` to `chainCount * 0x10 + 8` for the
  platform's position offset; chain link positions still use `(i+1)*0x10`.

### New MCZ2 frontier (frame 1079)

`y mismatch (expected=0x05B4, actual=0x05A9)`. Engine now correctly avoids
landing on the first MCZ2 SwingingPlatform during the f1006-f1009 fall, and
Sonic position matches ROM exactly through frames 1000-1078. At frame 1079
the engine has Sonic riding the **second** MCZ2 SwingingPlatform
(`onSlot=23(0x15) @061C,05C5`), while ROM has Sonic standing on `onObj=10`
(unidentified non-`near` object at y=0x05B4). The second platform spawn
(`subtype 0x38 -> BOUNCE_RIGHT, 8 chains`) sits at ROM `@0683,05C5` (positive
X offset from pivot 0x0650) but the engine computes a *negative* X offset
(@061C). This is a separate swing-direction issue: engine pre-rotates oscValue
by `-0x40` and assigns `Y=cos, X=sin`, while ROM passes oscValue directly to
`CalcSine` and assigns `Y=sin, X=cos` (mirrored convention). Attempted that
fix in this iter — it makes Sonic match ROM exactly through frames 1000-1008
but reverts the frontier to 1009 because Sonic lands 1 frame later (1 pixel
gap on the BOUNCE_LEFT platform top). Needs paired investigation of the
landing-edge condition; deferred to a follow-up iter.

## 2026-05-19 - S2 Vertical/Diagonal Spring clears Hurt routine (MCZ2 frame 925 -> 1006)

- Branch: `develop` (HEAD `4b2b42097`)
- Worktree: isolated agent worktree, `git reset --hard origin/develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mcz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MCZ2 frontier advanced from frame 925 to frame 1006 (737 -> 781 errors).
- Regression check: `TestS2Ehz1TraceReplay` PASS;
  `TestS2MczLevelSelectTraceReplay` still at frame 1085 (452 errors, pre-existing);
  `TestS2CnzLevelSelectTraceReplay` still at frame 3906 (22 errors, pre-existing);
  `TestS2Cnz2LevelSelectTraceReplay` still at frame 1490 (1057 errors, pre-existing).

### Root cause

`SpringObjectInstance.applyUpSpring` (and Down/Diagonal variants) did not clear
the engine's `hurt` flag when launching the player. ROM `Obj41_Up loc_189CA`
(`s2.asm:33735`), `Obj41_Down loc_18CC6` (`s2.asm:34023`), and the diagonal
launchers (`loc_18DD8` line 34090, `loc_18EE6` line 34173) all unconditionally
write `move.b #2,routine(a1)`, which overwrites the routine byte from 4
(`Obj01_Hurt`) to 2 (`Obj01_Control`). The ROM thus exits the Hurt routine when
a vertical spring fires while Sonic is hurt mid-air; subsequent airborne frames
use `Obj01_MdAir`'s +$38 gravity plus `Sonic_UpVelCap` (-$FC0) instead of
`Obj01_Hurt`'s +$30 gravity (no cap).

The engine encoded `routine` differently — `hurt` is a boolean and the trace
test maps `isHurt() -> rtn=04`. With `hurt` left set after the spring launch,
`PlayableSpriteMovement.airbornePhysics()` skipped `doJumpHeight()` (which
contains `applyUpwardVelocityCap`) entirely, and `getGravity()` returned 0x30
rather than 0x38. MCZ2 trace frame 925: ROM y_speed `-0F88` (cap from -$1000 to
-$FC0 then +$38), engine y_speed `-0FD0` (uncapped -$1000 + $30). Diff: 0x48
subunits = exactly the missing 0x40 cap delta plus the 0x08 gravity-step
shortfall.

### Fix

`SpringObjectInstance.java`: added `player.setHurt(false)` to `applyUpSpring`,
`applyDownSpring`, and `applyDiagonalSpring`. `applyHorizontalSpring` was left
alone because horizontal springs (`Obj41_Horizontal loc_18AEE`) do NOT write
`routine = 2` in ROM (they keep the player grounded and unchanged in routine).

### New MCZ2 frontier (frame 1006)

`y_speed expected=0x0850, actual=0x0000`. Engine landed on a SwingingPlatform
(`onSlot=17(0x15)`, `st=08`, `ride=1`), but ROM trace has Sonic still falling
(`status=02`, `air=1`). The frame-925 spring launch and the f925-f1005
airborne arc now match exactly; the divergence is a new SwingingPlatform
landing/collision issue, not the same root cause.

## 2026-05-19 - S2 MTZ2 Conveyor (Obj6C) child base position fix

- Branch: worktree `agent-a6ca59e26305ee5a1` on top of `develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 2325 errors (was 2189 after Long Platform). Frontier frame
  unchanged at 305.
- Frontier moved: nominal frame stays at 305 (y mismatch), but the misfire
  changed from a missed-platform fall-through to a pure landing-y-snap delta.
  Engine now lands on the correct conveyor at frame 305 (`onSlot=127(0x6C)
  ride=1 vel=(0050,0000)`) at y=0x05F2 instead of ROM's y=0x05EB (7-px low).
  Per-frame data shows perfect parity for x, x_speed, y_speed, g_speed, angle,
  air, rolling, ground_mode, tails through frame 304; only y diverges by ~6-7
  px for the post-landing run on the s29 conveyor.

Root cause: `ConveyorObjectInstance.createOrSpawnChildren()` was constructing
each child with the child's offset spawn position used as its own `baseX/baseY`
(path origin). ROM `Obj6C_LoadSubObject` (s2.asm:54137-54151) captures the
PARENT's `x_pos`/`y_pos` into `d2`/`d3` before the spawn loop and writes those
unchanged into every child's `objoff_30`/`objoff_32`, even though the child's
`x_pos`/`y_pos` is set to `parent + layoutOffset`. Without this each child
orbited its own offset point instead of orbiting the shared parent center,
scattering the platforms (engine had conveyors at x=0x0340/0x037D where ROM
had them at x=0x0320 forming the vertical spine of the route across the lava
pit).

Fix: added a second `ConveyorObjectInstance` constructor accepting explicit
`baseX`/`baseY` and threaded the parent's `x_pos`/`y_pos` through from
`createOrSpawnChildren` to each child.

Files changed:
- `src/main/java/com/openggf/game/sonic2/objects/ConveyorObjectInstance.java`

Cross-game regression sweep:
- MTZ (act 1): 989 errors @ frame 281 (unchanged)
- MTZ2 (act 2): 2325 errors @ frame 305 (was 2189; frontier unchanged, residual
  7-px landing-y snap delta only)
- MTZ3 (act 3): 2414 errors @ frame 298 (unchanged)
- EHZ1: pass

## 2026-05-19 - S2 OOZ post-Octus frontier diagnosis (no committed change; new blockers identified)

- Branch: `develop` (HEAD `805852e8b`)
- Worktree state: clean develop
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ooz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: both still fail, frontiers stand at post-Octus-fix locations
  - OOZ1: 1482 errors, frontier frame 563 (`g_speed` expected `0x0341`, actual `0x033D`)
  - OOZ2: 1238 errors, frontier frame 389 (`y_speed` expected `-0358`, actual `0x0358`)
- Confirmed the prompt's stated frontiers (OOZ1 f509 Buzzer, OOZ2 f301 spring/fan) are stale;
  the 2026-05-19 Octus collision-size and rise-timing fix already moved them.

### OOZ1 f563 root cause (deferred: sub-pixel friction divergence)

Sonic on ground (angle 0, ground_mode 0, rolling 0, jump 0). g_speed sequence:
f560 0x0365, f561 0x0359, f562 0x034D (both -0x0C/frame friction), f563 ROM 0x0341
(-0x0C continues), engine 0x033D (-0x10). Engine applies an extra 4 sub-pixels of
deceleration on this frame. No nearby objects in the `near` list, no input change.
Could be water-exit friction, an off-by-one on the frame `inertia` is capped, or
a friction-step issue when `g_speed` crosses a particular threshold. Needs recorder
extension to log the per-frame friction delta to pin down.

### OOZ2 f389 root cause (deferred: missing/displaced flying Aquis kill bounce)

Sonic at (0x051D, 0x0525) in air falling, y_vel +0x0320 -> engine sees gravity only
(+0x0358), ROM sees `neg.w y_vel` on a Touch_KillEnemy top-branch bounce (-0x0358 after
the same +0x38 gravity). ROM `near` lists fresh `obj+ s16 0x27 / s20 0x29 / s21 0x28
@0524,0536` (Explosion / Points / Animal): a badnik was killed at that position on
this frame. No static OOZ_2.bin spawn matches `(0x524, 0x536)`, so the kill target
was a FLYING badnik. Static Aquis spawns are at `(0x0558, 0x8558)` and `(0x05C8,
0x8578)` (idx 13, 14). Engine `eng-near` at f389-f392 still has Aquis at the static
positions: engine Aquis is not chasing far enough to reach `(0x0524, 0x0536)` and so
never gets bounce-killed there. ROM Aquis flew there, was killed, neg.w bounced Sonic.

The recent Obj50 four-bug fix (62abeb4d7) addressed init x_vel, bmi timers,
closer-player target, and on_screen lag, but the chase-distance divergence is not
covered. Two leads worth following: (a) `Obj50_Chase` enters as soon as `on_screen`,
but ROM `render_flags.on_screen` is set via `RememberState` after `DisplaySprite` runs
in the same execute pass: the engine's last-frame snapshot may still trigger one
frame late vs. ROM, delaying chase entry; (b) `Obj50_FollowPlayer` adds `+/-$10` to
both axes per frame from `Obj50_Speeds`, capped at `$100`: the engine's cap or
sub-pixel accumulator may be discarding tiny velocity increments that ROM keeps.
Without a recorder field for `Obj50_timer`, `Obj50_shots_remaining`, and per-Aquis
position, narrowing the bug further is speculative.

ROM cite: `docs/s2disasm/s2.asm:60181-60295` (Obj50 chase/shoot/flee state machine).

Did not commit a fix: chase/sub-pixel divergence requires recorder-side diagnostics
(Aquis position over time) or a careful re-derivation of the speed/cap path that
risks regressing the four bugs the previous Aquis fix addressed. EHZ1 still green
locally.

## 2026-05-19 - S2 MTZ3 Obj6A zone-aware behavior and activation gating fix

- Branch: `worktree-agent-a10cbe7b6f47980c4` (reset to `develop` @ `7eaa19993`)
- Worktree state: clean develop + Obj6A rewrite
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MTZ3 frontier advances
  - MTZ3: 2414 → 2349 errors. Frontier moves frame 298 (`air` 0 vs 1) → frame 340 (`tails_g_speed` 0x0000 vs 0x0018)
- Regression check: MTZ1 unchanged at frame 375 (`tails_air`, 945 errors); MTZ2 unchanged at frame 222 (`x`, 2370 errors)
- Cross-game smoke: S1 GHZ1 + S2 EHZ1 still green. CNZ1 fails at frame 3906 (pre-existing, confirmed by re-running with stashed fix)

Root cause: `MCZRotPformsObjectInstance` (Obj6A) was hard-wired to MCZ behavior
across both zones. ROM `Obj6A_Init` (`s2.asm:53686-53751`) branches on
`Current_Zone`: MTZ uses `byte_27CDC` (4-phase, faster), `y_radius=0x0C`,
routine 2 (wait for player to walk off via `loc_27BDE`), and skips the
subtype-0x18 child-spawn block. MCZ uses `byte_27CF4`/`byte_27D12`,
`y_radius=0x20`, routine 4 (`loc_27C66`, move unconditionally), and spawns
two child platforms for subtype 0x18. Additionally, the engine masked
`spawn.subtype() & 0x0F` to derive `phaseIndex`, but ROM (`s2.asm:53750`)
stores the FULL subtype byte into `objoff_38` and uses it as a byte offset
into the velocity table (same P31 trap as the Obj65 fix).

For MTZ3 subtype-0 entries at `(0x02A0, 0x020C)` / `(0x02E0, 0x020C)`, the
combined defects caused platforms to use MCZ `y_radius=0x20` (32 px) instead
of MTZ `0x0C` (12 px) and start moving immediately, drifting `y +0x40` and
`x -0x4A` by frame 298 so the platform's right edge slid past the player and
triggered an air state ROM never sees.

ROM cite: `docs/s2disasm/s2.asm:53670-53871`.
## 2026-05-19 - S2 MTZ1 / MTZ2 frontier investigation (no committed change; frontiers unchanged)

- Branch: `develop`
- Worktree state: clean develop
- Commands:
  - `mvn -q -Dmse=off "-Dtest=TestS2MtzLevelSelectTraceReplay" test`
  - `mvn -q -Dmse=off "-Dtest=TestS2Mtz2LevelSelectTraceReplay" test`
- Result: unchanged frontiers
  - MTZ1: 945 errors, frontier frame 375 (`tails_air` expected `1`, actual `0`)
  - MTZ2: 2370 errors, frontier frame 222 (`x` expected `0x028C`, actual `0x028E`)

### MTZ1 root cause (deferred — requires SidekickCpuController investigation)

ROM physics CSV f0x176→f0x177 shows Tails transitioning from `(x=0x02BC,y=0x0250,status=0x09,
stand_on_obj=0x13)` to `(x=0x4000,y=0x0000,status=0x02,stand_on_obj=0x13)` with x_sub/y_sub
preserved (`0xE600,0xCF00`). These exact post-transition values are the literal stores in
`TailsCPU_Despawn` (`s2.asm:39043-39052`): `move.w #$4000,x_pos(a0); move.w #0,y_pos(a0);
move.b #$81,obj_control(a0); move.b #1<<status.player.in_air,status(a0)`. So ROM despawns
Tails one frame after the SteamSpring launch.

The trigger path is `TailsCPU_CheckDespawn` (`s2.asm:39055-39081`): when Tails is off-screen
AND `on_object` is clear (just launched off the spring), it ticks `Tails_respawn_counter`;
when on-screen WITH `on_object` set but `id(interact) != Tails_interact_ID`, it immediately
jumps to `TailsCPU_Despawn`. In the trace Tails was on slot 0x13 (SteamSpring) one frame
earlier with `interact=0x13` set, then the spring launched him (`loc_26798`: y_vel=-0xA00,
in_air=1, on_object=0). The exact frame-after-launch despawn implies an interact-ID
mismatch trigger or a separate path I didn't trace down.

Engine behavior: at the same frame the engine keeps Tails on the spring
(`eng-tails-state pos=(02B3,0251) ride=s19 type=42 st=09 ... onObj=true`) — the
`SidekickCpuController` despawn predicates aren't matching ROM's immediate-despawn case.
This shows up downstream as `tails_air=1` (ROM) vs `0` (engine) and follows as a long
tail of position/state mismatches once ROM's Tails is parked at (0x4000, 0x0000) while
engine's Tails continues riding.

Fix scope is the sidekick-CPU despawn predicates (likely a `Tails_interact_ID` check that
fires when the interact-slot's object ID changes mid-spring-cycle, or an off-screen
post-launch immediate despawn). Did NOT commit because the predicate change touches
shared sidekick logic that must not regress S2 CNZ/SCZ/MCZ traces.

### MTZ2 root cause (deferred — platform-carry timing mismatch)

ROM physics CSV around frame 222 shows Sonic riding s18 (MTZLongPlatform subtype 5
conveyor) with platform moving +2px/frame and player position incrementing by exactly
`x_vel/256` per frame (NO platform carry observed in trace). Engine increments position
by `x_vel/256 + 2 (carry)` every frame, so the divergence grows +2px/frame starting
at the frame Sonic mounts the platform:

```
f222: ROM x=0x028C ENG x=0x028E (+2)
f223: ROM x=0x028E ENG x=0x0292 (+4)
f224: ROM x=0x0290 ENG x=0x0296 (+6)
f225: ROM x=0x0292 ENG x=0x029A (+8)
...
```

Both engine and ROM have the same `x_speed=0x01F4` and same `inertia`. The platform itself
moves +2/frame in both (verified via consecutive `near s18` x positions: ENG
`02A2->02A4->02A6` lockstep with ROM `02A4->02A6->02A8`, just lagged by 2px globally).

Per `s2.asm:52450-52468` (Obj65 `loc_26C1C`): platform's update saves x_pos to
`objoff_2E`, runs subtype 5 (`loc_26E4A:addq.w #2,x_pos`), loads `d4 = objoff_2E`
(pre-move x), then calls `JmpTo10_SolidObject` → `MvSonicOnPtfm` (`s2.asm:35402-35426`):
`sub.w x_pos(a0),d2; sub.w d2,x_pos(a1)`, which carries player by `+delta = +2`. So per
my code-read of the ROM, the carry SHOULD apply.

What I ruled out:
1. **Not `obj_control` gating** — engine output shows `objCtrl=false`, ROM player isn't
   in MTZ tube state (`obj_control=$81`) here.
2. **Not `routine >= 6` gating** — Sonic routine is 0x02 (Obj01_Control) the whole sequence.
3. **Not `Debug_placement_mode`** — gameplay is active.
4. **Not the landing-frame skip** — `SolidObject_Landed` skips carry only on the landing
   frame f221; the divergence persists on every subsequent frame.
5. **Not the platform stepping forward** — both ENG and ROM platforms step +2/frame,
   verified across 10 consecutive frames.
6. **Not the inertia/x_vel calculation** — both ENG and ROM have identical x_vel/inertia
   trajectories per frame.

What I could NOT reconcile: the trace data unambiguously shows the player's 32-bit
position (`x_pos:x_sub`) advancing by exactly `x_vel*0x100` per frame with zero extra
delta from the platform, while my reading of `MvSonicOnPtfm` says +2 should be added to
the high word. Either MvSonicOnPtfm is not running for the MTZ conveyor case (some gate I
missed), or the trace recorder samples before the carry, or the inertia accumulation
secretly absorbs the carry. Engine fix would need to suppress carry for MTZ Long
Platform conveyor — but the rationale isn't clear enough to land without verifying the
exact ROM gate. Risk of regressing CPZ/OOZ/MCZ moving platform traces if applied broadly.

Did NOT commit because:
- The "engine carry = ROM carry, just verified" reading conflicts with the trace data
  by a constant +2 delta per frame. Need to instrument a recording at the exact pre/post
  MvSonicOnPtfm point to know which side of the boundary the trace samples.
- Suppressing carry for MTZLongPlatform alone would mask the bug; need to understand the
  generic rule first to avoid cross-zone regression.

Cross-game regression: not run (no commit to verify).

## 2026-05-19 - S2 OOZ Aquis (Obj50) four-bug ROM-accuracy fix

- Branch: `worktree-agent-a11d73c9194c58ed5`
- Commit context: dirty worktree, base `d140c9d07` ("Fix S1 credits trace parity")
- Targets: S2 OOZ1 + OOZ2 trace replays, `AquisBadnikInstance` (Obj50)
- Commands:
  - `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay#replayMatchesTrace" test`
  - `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Ooz2LevelSelectTraceReplay#replayMatchesTrace" test`
  - cross-game: `TestS2Ehz1TraceReplay`, `TestS2MtzLevelSelectTraceReplay`, `TestS2CnzLevelSelectTraceReplay`

Four ROM-accuracy fixes applied to `AquisBadnikInstance` in a single patch:

1. **Initial x_vel.** `Obj50_Init` (s2.asm:60100) executes
   `move.w #-$100, x_vel(a0)` immediately after the standard init block. The
   engine constructor was leaving `xVelocity = 0`. Set `xVelocity = -0x100` in
   the constructor.
2. **`bmi` timer semantics.** `Obj50_FollowPlayer` (s2.asm:60244-60245) and
   `Obj50_WaitForNextShot` (s2.asm:60275-60276) use
   `subq.b #1, timer / bmi`, which fires when the byte wraps from `0x00` to
   `0xFF`. The engine used `if (--timer <= 0)` which fires one frame early.
   Switched both call sites to `timer = (byte)(timer - 1); if (timer < 0)`.
3. **Closer-player orientation.** `Obj_GetOrientationToPlayer`
   (s2.asm:72320-72346) picks the closer of MainCharacter and Sidekick by
   `|x_pos - obX|`. The engine only used the main player. Added a
   `closestPlayer(PlayableEntity main)` helper that walks `services().sidekicks()`
   and returns the sprite with minimum absolute X distance.
4. **`render_flags.on_screen` one-frame lag.** `Obj50_CheckIfOnScreen`
   (s2.asm:60181-60188) tests `render_flags.on_screen`, which the ROM clears at
   the start of each `BuildSprites` pass and only re-sets if the sprite is
   actually drawn this frame. The engine used `isOnScreen(32)` which has no
   lag. Added paired `onScreenLastFrame` / `onScreenThisFrame` flags: snapshot
   in `updateMovement()`, set `onScreenThisFrame = true` in
   `appendRenderCommands()` when `isOnScreen(0)` (exact viewport, matching the
   ROM bounds test), and gate the WAIT_FOR_SCREEN transition on
   `onScreenLastFrame`.

Result (before -> after):

| Test                                     | Before        | After         | Delta |
|------------------------------------------|---------------|---------------|-------|
| TestS2OozLevelSelectTraceReplay          | 1117 @ f509  | 1117 @ f509  | 0     |
| TestS2Ooz2LevelSelectTraceReplay         | 1329 @ f301  | 1280 @ f301  | -49   |
| TestS2Ehz1TraceReplay                    | PASS         | PASS         | -     |
| TestS2MtzLevelSelectTraceReplay          | 1015 @ f281  | 1015 @ f281  | 0     |
| TestS2CnzLevelSelectTraceReplay          | PASS         | PASS         | -     |

OOZ2 total error count drops by 49 (Aquis-attributable mismatches eliminated),
while the OOZ1 frontier is unrelated (gated by a Buzzer `0x4A` enemy bounce at
f509) and the OOZ2 first-error frontier is also unrelated (gated by a
Tails+Spring/Fan `tails_y_speed` mismatch at f301). The first-error frame
therefore does not move for either OOZ replay even though the Aquis behaviour
is now ROM-accurate. The OOZ2 error-count improvement is the verifiable signal
that the fix is correct. No cross-game regressions in EHZ1, MTZ, or CNZ.

## 2026-05-19 - Fix S2 MCZ VineSwitch release input and Tails support

- Branch: `develop` (worktree `agent-ab982cb17d078747f`)
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mcz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Status: PASS-not-yet (frontier advanced)
- Result: MCZ2 frontier advanced from frame 198 to frame 925 (936 -> 737 errors)
- Regression: `TestS2Ehz1TraceReplay`, `TestS2Cnz1LevelSelectTraceReplay`, `TestS2Mcz1LevelSelectTraceReplay` all PASS

Two related bugs in `VineSwitchObjectInstance` (S2 Obj7F):

1. Release used held-button state (`isJumpPressed()`) instead of just-pressed
   edge (`isJumpJustPressed()`). ROM `Obj7F_Action` does
   `move.w (Ctrl_1).w,d0 / andi.b #button_B_mask|button_C_mask|button_A_mask,d0`
   - the `.b` operates on the low byte of `Ctrl_1`, which is `Ctrl_1_Press`
   (just-pressed this frame), not `Ctrl_1_Held`. With the engine reading held
   state, Sonic was released a single frame after grabbing whenever the player
   held B (which is the same input that triggered the jump that put him near
   the vine).

2. Sidekick (Tails) was never processed. ROM iterates twice: once for
   `MainCharacter` with `Ctrl_1`, once for `Sidekick` with `Ctrl_2`. The
   engine looped only over the main character with a "Player 2 deferred"
   comment. Added a sidekick pass using `services().sidekicks()` so Tails can
   grab and hang from the vine.

Files: `src/main/java/com/openggf/game/sonic2/objects/VineSwitchObjectInstance.java`

## 2026-05-19 - S2 OOZ Aquis investigation (no committed change; frontiers unchanged)

- Branch: `develop`
- Worktree state: clean develop (no commit; experimental Aquis edits reverted)
- Commands:
  - `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
  - `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Ooz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: unchanged frontiers
  - OOZ1: 1412 errors, frontier frame 563 (`g_speed` 0x0341 vs 0x033D)
  - OOZ2: 1305 errors, frontier frame 389 (`y_speed` -0x0358 vs +0x0358)

OOZ2 frame 389 corresponds to CSV row 0185 (trace frame numbering differs from CSV row
numbering after pre-trace ROM warmup). ROM state at that row: Sonic at (0x051D, 0x0525)
with y_speed flipping from +0x0320 (f184) to -0x0358 (f185) — a classic enemy-bounce
negation. ROM events `obj+ s16 0x27 @0524,0536`, `obj+ s20 0x29`, `obj+ s21 0x28` show
an Aquis at slot 16 destroyed at (0x0524, 0x0536), spawning animal/points group. Engine
slot 16 still holds a live Aquis at (0x04FE, 0x054C) — 38 px left and 22 px below the
ROM position, so the engine's Sonic does not intersect the Aquis touch box (0x10, 0x08)
and never bounces.

Aquis movement diverges over many chase/shoot cycles before f389. Three ROM-vs-engine
Obj50 (`AquisBadnikInstance`) deltas were identified and individually exercised against
both OOZ traces (each reverted before the next):

1. **Initial x_vel (`s2.asm:60100`):** `move.w #-$100, x_vel(a0)` in `Obj50_Init` was not
   applied. Adding `xVelocity = -0x100` at construction time moved OOZ2 by -134 errors
   (1305 → 1171) but introduced **+27 OOZ1 errors** (1412 → 1439). Frontier frames did
   not move.
2. **P30 timer (`s2.asm:60244-60245, 60275-60276`):** `Obj50_FollowPlayer` and
   `Obj50_WaitForNextShot` both use `subq.b #1, timer; bmi.s ...`, which fires at
   timer=-1 (after the decrement); the engine used `timer--; if (timer <= 0)`, firing
   one frame earlier. Switching to the byte-signed `(byte) timer < 0` test plus
   ordering decrement-before-movement (so the transition frame skips ObjectMove, matching
   ROM `Obj50_DoneFollowing` falling through `MoveStop`) moved OOZ2 by -46 errors
   (1305 → 1259) but introduced **+54 OOZ1 errors** (1412 → 1466). Frontier frames did
   not move.
3. **Closer-player orientation (`s2.asm:72320-72346`):** `Obj_GetOrientationToPlayer`
   targets the closer of Sonic/Tails (`mvabs.w` + `bls.s`); the engine uses Sonic only.
   Adding closer-player selection on top of the P30 fix only changed OOZ2 by -1 more
   error (1259 → 1258) and did not advance the frontier. (Behaviour at f389 has Sonic
   and Tails at almost the same x_pos, so closer-player doesn't matter there.)

Combined fixes (initial x_vel + P30 + closer-player) produced a 1172-error OOZ2 result
(net -133) but kept the OOZ1 regression around +30 errors. None of the combinations
advanced the OOZ2 f389 frontier; all variants left engine Aquis position ~38 px X and
~22 px Y away from ROM at the moment Sonic should have hit it.

OOZ1 f563 (`g_speed` 0x0341 vs 0x033D, no nearby objects in diagnostic, flat ground at
camera (0462, 064C)) is a 4-subpixel velocity drift unrelated to Aquis touch geometry.
Aquis-only changes that should be ROM-accurate (especially the initial x_vel from
`Obj50_Init`) introduced new error frames after f563 in OOZ1, suggesting either a
downstream engine bug whose effect was masked by the previously-incorrect Aquis behaviour
or an additional Aquis discrepancy still unaccounted for. Without a fix that advances at
least one frontier and does not regress the other, no commit was made.

Not yet explored / ruled out:
- `Obj50_CheckIfOnScreen` triggers on `render_flags.on_screen` (set after object renders
  inside the precise camera viewport). Engine uses `isOnScreen(32)` (camera bounds + 32 px
  margin). These can fire on different frames, changing how long the first chase phase
  has to move the Aquis before the player gets close enough.
- Wing child object: ROM allocates a separate OST slot in `Obj50_Init`. Engine renders
  the wing inline. Wing should not affect physics, but the slot allocation could shift
  slot numbering for other objects in the same window.
- `Obj_CapSpeed` exact rounding — verified equivalent to engine's `clampSpeed`.
- Trace replay test harness uses CSV-after-warmup frame numbering; the diagnostic `frame
  389` corresponds to CSV row 0185 in `src/test/resources/traces/s2/ooz2/physics.csv.gz`.

## 2026-05-19 - S2 CNZ2 pinball_mode preservation flag fix (silent regression)

- Branch: `develop`
- Worktree state: clean develop + flag fix
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1057 errors — frontier restored to frame 1490 `tails_y_speed`
- Cross-game: `*TraceReplay` sweep (41 tests, 21 failures), all matching pre-existing frontiers

On a clean develop reset, CNZ2 was failing at frame 936 (`tails_air`, 1161 errors), not frame
1490 (1057 errors) as the prior commit narrative described. `PhysicsFeatureSet.SONIC_2.
pinballLandingPreservesPinballMode` was still `false`, so `PlayableSpriteMovement.resetOnFloor()`
unconditionally cleared the engine `pinball_mode` mirror on every landing. That broke
`SidekickCpuController.updateNormal()`'s rolling+pinball+!air suppress guard.

Fix: flipped `pinballLandingPreservesPinballMode` to `true` in `PhysicsFeatureSet.SONIC_2`.
ROM `Sonic_ResetOnFloor` / `Tails_ResetOnFloor` (`s2.asm:37770-37771, 40625-40626`) both
`tst.b pinball_mode` / `bne.s Part3`; Part3 only clears in_air/pushing/rolljumping/jumping —
`pinball_mode` is never cleared.

CNZ2 frame 1490 `tails_y_speed` root cause investigated: Crawl (`ObjC8`) proximity and range
checks use `Obj_GetOrientationToPlayer` (`s2.asm:72320-72346`) which tests the **closer** of
Sonic/Tails (`mvabs.w` + `bls.s`). Engine `CrawlBadnikInstance` only passes the leader (Sonic).
Implementing closer-player selection fixed the 3-px x-position drift at frame 1490, but
introduced a 1-pixel regression at frame 630 (`y_speed -0666` vs `-0682`, 1096 errors) that
could not be diagnosed. Closer-player fix reverted. Frame 630 Crawl x-position needs further
recorder diagnostics before the frame-1490 fix can land.

## 2026-05-19 - S2 MTZ SteamSpring timing and MTZLongPlatform props-lookup fix

- Branch: `develop`
- Worktree state: clean develop + MTZ fixes
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2MtzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MTZ1 and MTZ2 frontiers advance; MTZ3 unchanged
  - MTZ1: 1015 → 945 errors. Frontier moves frame 281 (`y` 0x024D vs 0x0255) → frame 375 (`tails_air` 1 vs 0)
  - MTZ2: 2335 → 2370 errors. Frontier moves frame 221 (`y`) → frame 222 (`x` 0x028C vs 0x028E)
  - MTZ3: unchanged at frame 298 (`air` 0 vs 1); root cause is `MCZRotatingPlatformsObjectInstance` placement
- Cross-game regression: S1 GHZ1, S1 MZ1, S2 EHZ1, S2 CNZ1, S2 SCZ still green

Root causes fixed:

1. **SteamSpring (Obj42) solid-checkpoint ordering:** Engine ran the state machine THEN
   resolved solid contacts (post-update), so the player followed the spring's new y_pos one
   frame too early. ROM `loc_26688` (`s2.asm:52030-52049`) calls
   `SolidObject_Always_SingleCharacter` BEFORE the state-machine branches update `objoff_36`
   / `y_pos`. Fix: switched to `MANUAL_CHECKPOINT` mode; `checkpointAll()` runs first in
   `update()`, spring fire applied from batch result (manual mode suppresses the compatibility
   `onSolidContact` callback). MTZ1 frontier moved 281 → 375.

2. **MTZLongPlatform (Obj65) properties off-by-2 indexing:** Engine used `d0 >> 2` for
   both the props-table entry AND `mapping_frame`, collapsing 8 ROM entries to 4. ROM
   `s2.asm:52386-52394` does `lea Obj65_Properties(pc,d0.w),a3` (entry index = d0/2) then
   separately `lsr.w #2,d0` for `mapping_frame` (= d0/4). For subtype 0xB1 this picked
   entry 3 `{0x40, 0x03}` (stationary) instead of entry 6 `{0x40, 0x0C}` (moveSubtype=7,
   maxDist=0x80), so the platform never moved. Fix: `entryIndex = d0 >> 1`,
   `frameIndex = d0 >> 2`. MTZ2 frontier moved 221 → 222.

MTZ3 frame 298 `air` mismatch: `MCZRotatingPlatformsObjectInstance` (Obj6A) has placement/
subtype divergence. Noted for separate investigation.

## 2026-05-19 - S2 OOZ Octus collision and rise timing parity

- Branch: `develop`
- Worktree state: clean develop + Octus fixes
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ooz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: both still fail, but frontiers advance
  - OOZ1: 1117 → 1412 errors. Frontier moves frame 509 (`y_speed` 0x0300 vs -0x0300) → frame 563 (`g_speed` 0x0341 vs 0x033D)
  - OOZ2: 1337 → 1305 errors. Frontier moves frame 301 (`tails_y_speed` -0x0278 vs -0x0178) → frame 389 (`y_speed` -0x0358 vs +0x0358)
- Cross-game regression: S1 GHZ1, S1 MZ1, S2 EHZ1 still green

Root causes fixed:

1. **Octus (Obj4A) touch box size:** `OctusBadnikInstance` hard-coded `COLLISION_SIZE_INDEX = 0x0C`
   ((20,16) half-extents). ROM `s2.asm:59905` writes `collision_flags = $A`, mapping to
   `Touch_Sizes[$A] = (16,8)`. The oversized box made Sonic bounce off Octuses 1–2 frames
   early, producing the OOZ1 frame-509 y_speed sign reversal and the OOZ2 frame-301 cascade.

2. **Octus rise/hover transition timing:** ROM `Obj4A_DelayBeforeMoveUp` (`s2.asm:59958-59967`)
   uses `bmi` (branch when timer is negative after decrement) and falls through to
   `JmpTo19_ObjectMove` on the transition frame, applying `y_vel=-$200` immediately. The engine
   used `timer <= 0` and skipped movement on the transition frame, leaving Octus ~4px lower than
   ROM throughout `MoveUp`. Same `bmi`-vs-zero correction applied to `Obj4A_Hover`
   (`s2.asm:59981-59988`).

## 2026-05-19 - Full test run and fresh trace frontier sweep

- Branch: `develop`
- Worktree state: dirty with S1 credits trace fixes and existing local changes
- Commands:
  - `mvn -q -Dmse=off test`
  - cleared generated `target/trace-reports`
  - `mvn -q -Dmse=off "-Dtest=*TraceReplay,*ReplayBootstrap" test -DfailIfNoTests=false`
- Result: full suite failed with 4870 tests, 21 failures, 2 errors, 6 skipped; fresh trace sweep failed with 74 tests, 38 failures, 0 errors, 0 skipped

The fresh trace sweep confirms no S1 trace replay regression from the credits
fixes: `TestS1Ghz1TraceReplay`, `TestS1Mz1TraceReplay`, and all eight
`TestS1Credits*TraceReplay` classes pass. The full suite still has a separate
debug-probe failure, `DebugS1Credits01Mz2PushBlockProbe`, because it asks for
frame 539 outside the current probe window.

Other non-trace full-suite failures remain outside this frontier table,
including `DefaultObjectServices`/object-service migration guards, a
`TestGroundSensor` top-solid expectation, rewind torture divergences, CNZ slot
machine RNG, several S3K event/object assertions, HTZ boss touch response, S2
MCZ rotating platform lifecycle, and singleton lifecycle guard coverage.

Fresh JSON reports under `target/trace-reports` produced these first
non-cascading frontiers:

| Trace | Errors | Warnings | Frames | Frontier / first error |
| --- | ---: | ---: | ---: | --- |
| `s2_arz1` | 813 | 0 | 5064 | frame 304, `tails_x_speed` expected `0x00D0`, actual `0x00C2` |
| `s2_arz2` | 2129 | 0 | 7716 | frame 225, `y` expected `0x04DC`, actual `0x04D9` |
| `s2_cnz1` | 22 | 0 | 9469 | frame 3906, `tails_y` expected `0x06C0`, actual `0x06C1` |
| `s2_cnz2` | 1057 | 0 | 12081 | frame 1490, `tails_y_speed` expected `-059B`, actual `-052B` |
| `s2_cpz1` | 434 | 0 | 5741 | frame 844, `x_speed` expected `-0200`, actual `0x00E4` |
| `s2_cpz2` | 1646 | 0 | 12142 | frame 962, `y` expected `0x03AD`, actual `0x03AC` |
| `s2_dez1` | 174 | 0 | 7888 | frame 536, `rolling` expected `1`, actual `0` |
| `s2_htz1` | 490 | 0 | 8856 | frame 5511, `x` expected `0x151B`, actual `0x1523` |
| `s2_htz2` | 815 | 0 | 10160 | frame 795, `tails_air` expected `0`, actual `1` |
| `s2_mcz1` | 452 | 0 | 6083 | frame 1085, `y` expected `0x056C`, actual `0x056B` |
| `s2_mcz2` | 936 | 0 | 10953 | frame 198, `y_speed` expected `0x0000`, actual `-0300` |
| `s2_mtz1` | 1015 | 0 | 10133 | frame 281, `y` expected `0x024D`, actual `0x0255` |
| `s2_mtz2` | 2335 | 0 | 12697 | frame 221, `y` expected `0x062D`, actual `0x062A` |
| `s2_mtz3` | 2414 | 0 | 15622 | frame 298, `air` expected `0`, actual `1` |
| `s2_ooz1` | 1117 | 0 | 11015 | frame 509, `y_speed` expected `0x0300`, actual `-0300` |
| `s2_ooz2` | 1329 | 0 | 13317 | frame 301, `tails_y_speed` expected `-0278`, actual `-0178` |
| `s2_wfz1` | 589 | 0 | 16426 | frame 4719, `x_speed` expected `0x08D6`, actual `0x08E2` |
| `s3k_aiz1` | 1738 | 22 | 20463 | frame 1057, `tails_x` expected `0x13CC`, actual `0x7F00` |
| `s3k_cnz1` | 3776 | 17 | 42242 | frame 4790, `tails_x` expected `0x6125`, actual `0x7F00` |
| `s3k_mgz1` | 2625 | 60 | 35861 | frame 1538, `y` expected `0x0DC9`, actual `0x0DC8` |

Fresh trace sweep pass rows:
`TestS1Ghz1TraceReplay`,
`TestS1Mz1TraceReplay`,
`TestS1Credits00Ghz1TraceReplay` through
`TestS1Credits07Ghz1bTraceReplay`,
`TestS2Ehz1TraceReplay`,
`TestS2SczLevelSelectTraceReplay`,
and the non-`replayMatchesTrace` S3K CNZ/AIZ focused assertions except the AIZ
bootstrap/player-window failures listed below.

`TestS3kAizReplayBootstrap` has 17 current assertion frontiers that are not
represented by the JSON comparator table: frame 0 AIZ intro object routine state,
legacy pre-level prefix classification, missing/incorrect gameplay-start anchor
at frame 1387, gameplay-start detection off by two frames, title-card/Knuckles
state around gameplay start, missing `aiz1_fire_transition_begin`, frame 1800
post-fire-transition player X, frame 1719 Monkey Dude height, frame 1983 roll
rock debris, frame 2006 rolling hitbox and replay X, frame 2110/2155 floating
platform state, and frame 4886 AIZ2 reload resume mismatches.

`TestS3kAizTraceReplay.playerMatchesTraceThroughFirstGiantRideVineWindow` also
fails at trace frame 2876: player X expected `8023`, actual `7692`.

## 2026-05-19 - S1 credits LZ and MZ trace fixes

- Branch: `develop`
- Worktree state: dirty with S1 credits trace fixes
- Commands:
  - `mvn -q -Dmse=off "-Dtest=TestS1Credits03Lz3TraceReplay" test -DfailIfNoTests=false`
  - `mvn -q -Dmse=off "-Dtest=TestS1Credits01Mz2TraceReplay,TestS1Credits03Lz3TraceReplay" test -DfailIfNoTests=false`
  - `mvn -q -Dmse=off "-Dtest=TestS1Credits01Mz2TraceReplay" test -DfailIfNoTests=false`
  - `mvn -q -Dmse=off "-Dtest=TestS1Ghz1TraceReplay,TestS1Mz1TraceReplay,TestS1Credits*TraceReplay" test -DfailIfNoTests=false`
- Result: targeted LZ3 and MZ2 pass; S1 GHZ1, MZ1, and all S1 credits traces pass in the targeted regression sweep

`TestS1Credits03Lz3TraceReplay` now reaches the full trace window after
emulating the Sonic 1 REV01 non-FixBugs `LZWindTunnels` d0 clobber and seeding
the LZ credits-demo vblank phase at the lamppost bootstrap.

`TestS1Credits01Mz2TraceReplay` now reaches the full trace window. Parent-spawned
lava geyser makers delay their first proximity-triggered advance by one live
tick, matching the ROM slot timing through the original frame-493 divergence.
The remaining one-pixel push-block vertical-motion delta at frame 499 was closed
by preserving the first airborne frame's REV01 launch phase: the geyser maker
sets the parent push block airborne from a later SST slot, and the block's
subpixel/velocity seed now keeps the first `#-$580` displacement before
`loc_C056`'s `+$18` gravity affects the next visible velocity.

## 2026-05-18 - Full trace replay frontier sweep after S2 act expansion

- Branch: `develop`
- Worktree state: dirty with S2 act fixture/test expansion and trace recorder updates
- Command: `mvn -q -Dmse=off '-Dtest=*TraceReplay' test -DfailIfNoTests=false`
- Result: fail, 51 trace replay/invariant/policy methods inspected, 25 failures

This snapshot is from the current workspace after adding Sonic 2 act-specific
level-select replay tests and regenerated fixtures. Passing rows reached their
full configured trace/assertion window. Failing rows record the first mismatched
frame or first assertion failure.

| Test | Status | Errors | Warnings | Frontier / first error |
| --- | --- | ---: | ---: | --- |
| `TestTraceReplayInvariantGuard.concreteTraceReplayTestsUseSharedReplayBaseClass` | pass | 0 | 0 | full trace |
| `TestTraceReplayInvariantGuard.defaultTraceReplayToleranceIsStrict` | pass | 0 | 0 | full trace |
| `TestTraceReplayInvariantGuard.traceParserDataAndCatalogStayIndependentOfEngineRuntime` | pass | 0 | 0 | full trace |
| `TestTraceReplayInvariantGuard.traceReplayCodeDoesNotWriteRecordedStateBackIntoEngine` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.ordinaryS2TraceDoesNotUseTornadoRideStart` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s2SczAndWfzLevelSelectUseNativeTornadoRideStart` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTracePreLevelPrefixAdvancesMovieWithoutTickingLevel` | fail | 1 | 0 | frame 289 classified as `VBLANK_ONLY` instead of `FULL_LEVEL_FRAME` |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTraceStartsAtFrameZeroWithoutSkippingIntro` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTraceUsesLiveIntroSpawnInsteadOfRecordedFrameZeroPosition` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kGameplayTraceSeedsFrameZeroAfterSidekickTitleCardPrelude` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kGameplayTraceStillDoesNotSeedFrameZeroWhenObjectSnapshotsExist` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kMgzGameplayTraceDrivesFrameZeroWhenSonicAlreadyMoved` | fail | 1 | 0 | S3K MGZ sidekick prelude assertion |
| `TestTraceReplayStartPositionPolicy.vblankOnlyRowsAdvanceMovieButDoNotCompareGameplayState` | fail | 1 | 0 | `FULL_LEVEL_FRAME` strict comparison assertion |
| `TestS1Credits00Ghz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits01Mz2TraceReplay.replayMatchesTrace` | fail | 6 | 0 | frame 493, `y` |
| `TestS1Credits02Syz3TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits03Lz3TraceReplay.replayMatchesTrace` | fail | 6 | 0 | frame 221, `y` |
| `TestS1Credits04Slz3TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits05Sbz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits06Sbz2TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits07Ghz1bTraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Ghz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Mz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS2ArzLevelSelectTraceReplay.replayMatchesTrace` | fail | 813 | 0 | frame 304, `tails_x_speed` |
| `TestS2Arz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 2129 | 0 | frame 225, `y` |
| `TestS2CnzLevelSelectTraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS2Cnz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 1262 | 0 | frame 0, `air` |
| `TestS2CpzLevelSelectTraceReplay.replayMatchesTrace` | fail | 434 | 0 | frame 844, `x_speed` |
| `TestS2Cpz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 1646 | 0 | frame 962, `y` |
| `TestS2DezEndingLevelSelectTraceReplay.replayMatchesTrace` | fail | 174 | 0 | frame 536, `rolling` |
| `TestS2Ehz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS2HtzLevelSelectTraceReplay.replayMatchesTrace` | fail | 398 | 0 | frame 5511, `x` |
| `TestS2Htz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 821 | 0 | frame 762, `tails_y` |
| `TestS2MczLevelSelectTraceReplay.replayMatchesTrace` | fail | 452 | 0 | frame 1085, `y` |
| `TestS2Mcz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 936 | 0 | frame 198, `y_speed` |
| `TestS2MtzLevelSelectTraceReplay.replayMatchesTrace` | fail | 964 | 0 | frame 281, `y` |
| `TestS2Mtz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 2335 | 0 | frame 221, `y` |
| `TestS2Mtz3LevelSelectTraceReplay.replayMatchesTrace` | fail | 2414 | 0 | frame 298, `air` |
| `TestS2OozLevelSelectTraceReplay.replayMatchesTrace` | fail | 1117 | 0 | frame 509, `y_speed` |
| `TestS2Ooz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 1329 | 0 | frame 301, `tails_y_speed` |
| `TestS2SczLevelSelectTraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS2WfzLevelSelectTraceReplay.replayMatchesTrace` | fail | 589 | 0 | frame 4719, `x_speed` |
| `TestS3kAizTraceReplay.cameraMatchesTraceThroughFirstDelayedScrollBurst` | pass | 0 | 0 | full trace |
| `TestS3kAizTraceReplay.playerMatchesTraceThroughFirstGiantRideVineWindow` | fail | 1 | 0 | frame 2876, assertion mismatch |
| `TestS3kAizTraceReplay.replayMatchesTrace` | fail | 1738 | 22 | frame 1057, `tails_x` |
| `TestS3kAizTraceReplay.rhinobotDoesNotDespawnOneFrameBeforeRomContact` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.replayMatchesTrace` | fail | 3876 | 17 | frame 4790, `tails_x` |
| `TestS3kCnzTraceReplay.traceReplayAppliesDelayedRightInputToTailsOnFrame123` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayAppliesFirstCarryRightPulseOnFrame31` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayAppliesFirstMainJumpOnFrame142` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayDoesNotPulseCarryRightOnFrame1` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayHorizontalSpringLandingHandoffMatchesFrame3649` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayS3kRightWallPathRunsCeilingSeparationOnFrame5236` | pass | 0 | 0 | full trace |
| `TestS3kMgzTraceReplay.replayMatchesTrace` | fail | 2625 | 60 | frame 1538, `y` |

## 2026-05-18 - S2 level-select act coverage expansion

- Branch: `develop`
- Worktree state: dirty with generated S2 act fixtures and replay test additions
- Command: `mvn -Dmse=off '-Dtest=TestS2TraceRouteAssertions' test`
- Result: pass, 22 tests

S2 level-select replay coverage now has fixtures and concrete replay test
classes for every main Sonic 2 zone/act except EHZ Act 2. Multi-act BK2s are
split into independent fixtures using `OGGF_TRACE_GAMEPLAY_SEGMENT`, so each
fixture keeps its own `bk2_frame_offset` and contiguous gameplay input window.
DEZ uses the existing `dez_ending` fixture, which contains Death Egg gameplay
and the ending sequence. MTZ Act 3 uses ROM zone id `0x05` with raw act byte
`0`, and the recorder now emits metadata act 3 while retaining the raw ROM
zone/act in aux diagnostics.

| Fixture | Status | Errors | First error |
| --- | --- | ---: | --- |
| `TestS2Ehz1TraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Ehz2TraceReplay` | missing fixture/test | n/a | no BK2/fixture yet |
| `TestS2CpzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Cpz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2ArzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Arz2LevelSelectTraceReplay` | added test for existing fixture | n/a | not rerun |
| `TestS2CnzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Cnz2LevelSelectTraceReplay` | added test for existing fixture | n/a | not rerun |
| `TestS2HtzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Htz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2MczLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Mcz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2OozLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Ooz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2MtzLevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2Mtz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2Mtz3LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2SczLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2WfzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2DezEndingLevelSelectTraceReplay` | added replay test for existing fixture | n/a | not rerun |

## 2026-05-17 - BizHawk input-indexing fix impact snapshot

- Branch: `feature/ai-trace-frontier-continuation`
- Commit under test: `535895780 Fix BizHawk trace input indexing`
- Worktree state: dirty with local CNZ slot-machine investigation edits
- Command: `mvn -q -Dmse=off '-Dtest=*TraceReplay' test -DfailIfNoTests=false`
- Result: 31 test methods run, 14 failures

Recorder impact note: the committed recorder fix regenerated only the S2 CNZ
trace. CNZ physics row count stayed at 9469, input bytes were unchanged, and
all 9469 `vblank_counter` values changed from `0000` to real ROM VBlank
counter values. The focused clean CNZ replay stayed at the same first failure
before and after the recorder commit: frame 1691, 398 errors. The recorder fix
was diagnostic-impact only for CNZ, but it made VBlank-driven slot timing
debuggable.

| Test | Status | Errors | First error |
| --- | --- | ---: | --- |
| `TestTraceReplayInvariantGuard` | pass | 0 | |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTracePreLevelPrefixAdvancesMovieWithoutTickingLevel` | fail | n/a | first AIZ frame classified as `VBLANK_ONLY` instead of `FULL_LEVEL_FRAME` |
| `TestTraceReplayStartPositionPolicy.s3kMgzGameplayTraceDrivesFrameZeroWhenSonicAlreadyMoved` | fail | n/a | S3K MGZ setup prelude assertion failed |
| `TestTraceReplayStartPositionPolicy.vblankOnlyRowsAdvanceMovieButDoNotCompareGameplayState` | fail | n/a | `FULL_LEVEL_FRAME` strict-comparison assertion failed |
| `TestS1Credits00Ghz1TraceReplay` | pass | 0 | |
| `TestS1Credits01Mz2TraceReplay` | fail | 6 | frame 493, `y` |
| `TestS1Credits02Syz3TraceReplay` | pass | 0 | |
| `TestS1Credits03Lz3TraceReplay` | fail | 6 | frame 221, `y` |
| `TestS1Credits04Slz3TraceReplay` | pass | 0 | |
| `TestS1Credits05Sbz1TraceReplay` | pass | 0 | |
| `TestS1Credits06Sbz2TraceReplay` | pass | 0 | |
| `TestS1Credits07Ghz1bTraceReplay` | pass | 0 | |
| `TestS1Ghz1TraceReplay` | pass | 0 | |
| `TestS1Mz1TraceReplay` | pass | 0 | |
| `TestS2ArzLevelSelectTraceReplay` | fail | 831 | frame 304, `tails_x_speed` |
| `TestS2CnzLevelSelectTraceReplay` | fail | 413 | frame 1680, `air` |
| `TestS2CpzLevelSelectTraceReplay` | fail | 434 | frame 844, `x_speed` |
| `TestS2Ehz1TraceReplay` | pass | 0 | |
| `TestS2HtzLevelSelectTraceReplay` | fail | 398 | frame 5511, `x` |
| `TestS2MczLevelSelectTraceReplay` | fail | 394 | frame 1085, `y` |
| `TestS2OozLevelSelectTraceReplay` | fail | 1118 | frame 397, `tails_y` |
| `TestS2SczLevelSelectTraceReplay` | fail | 48 | frame 6222, `y_speed` |
| `TestS2WfzLevelSelectTraceReplay` | fail | 595 | frame 4720, `x_speed` |
| `TestS3kAizTraceReplay.playerMatchesTraceThroughFirstGiantRideVineWindow` | fail | n/a | trace frame 2876, player X |
| `TestS3kAizTraceReplay.replayMatchesTrace` | fail | 1715 | frame 1057, `tails_x` |
| `TestS3kCnzTraceReplay` | fail | 3707 | frame 4790, `tails_x` |
| `TestS3kMgzTraceReplay` | fail | 2625 | frame 1538, `y` |

Next active target: S2 CNZ slot-machine release timing. With the local
investigation edits, CNZ moved from the clean-branch frame 1691 failure to frame
1680, where the engine releases Sonic from the Point Pokey cage at VBlank
`0x105A` while ROM still reports Sonic riding the cage.

## 2026-05-17 - S2 CNZ slot-machine frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with CNZ slot-machine/bootstrap fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 353 errors
- First error: frame 3830, `x_speed` (`expected=-0833`, `actual=0x0000`)

Frontier moved from the Point Pokey cage release at frame 1691 to the later
CNZ Big Block / bumper-contact segment at frame 3830. The slot-machine fix
used the regenerated `cnz_slot_machine_state` aux rows to verify the ROM target
word (`0x0203`), VBlank seed window, and same-call Routine5->Routine6
completion path. The aux rows remain diagnostic only; replay still drives the
engine from BK2 input plus one-time native timing bootstrap.

## 2026-05-17 - S2 CNZ object-streaming vertical-filter fix

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 placement fix and Big Block trace diagnostics
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 191 errors
- First error: frame 3906, `tails_y` (`expected=0x06C0`, `actual=0x06BF`)

Frontier moved from the frame 3830 CNZ Big Block side-contact stop to frame
3906, where Tails lands/re-leaves a launcher spring one pixel lower than ROM.
The frame 3830 Big Block report showed only 39 engine updates for ObjD4 while
ROM-side ObjD4 had been active since frame 3330. The fix keeps the vertical
spawn-filter bypass scoped to Sonic 2 object placement, matching S2's
X-window-only `ObjectsManager_GoingForward` / `ObjectsManager_GoingBackward`
path (`docs/s2disasm/s2.asm:32870-32950`).

## 2026-05-17 - S2 CNZ Obj85 Tails recapture frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 launcher-spring fix and line-ending-only test-file noise
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 191 errors
- First error: frame 3957, `tails_y` (`expected=0x06F1`, `actual=0x06F0`)

Frontier moved from frame 3906 to frame 3957. The frame 3906 mismatch was a
Tails-only Obj85 vertical launcher recapture: ROM had Tails already rolling on
the previous frame, but the engine's current-frame state had been normalized
before Obj85 capture code ran, so the object applied its Tails first-capture
one-pixel lift again. The fix makes Obj85 consult the previous recorded status
bit for Tails rolling recaptures and removes the extra top-landing lift for
Tails's shorter `$0F` standing radius. The next blocker is after the launcher
sequence: Tails lands from the launch with ROM still reporting rolling
(`status=0x05`) while the engine clears rolling (`status=0x01`).

## 2026-05-17 - S2 CNZ Obj85 roll handoff and ObjD4 lower-bound frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 roll-preservation and ObjD4 geometry fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 227 errors
- First error: frame 4121, `x_speed` (`expected=-058C`, `actual=-0559`)

Frontier moved from frame 3957 through the Obj85 Tails landing/roll-stop
sequence and then from frame 4074 to frame 4121 after the ObjD4 lower-bound
fix. Obj85 now preserves the vertical launcher rolling handoff only for that
object's post-release path, using its inclusive right-edge capture and
object-local ground-wall follow-up rather than changing shared roll or solid
behaviour. The later frame 4074 Sonic mismatch was ObjD4 Big Block: the engine
used the taller standing-radius lower-half overlap for a rolling airborne
player, falsely classified a side contact, shifted Sonic right, and zeroed
`x_speed`. S2 `SolidObject_cont` uses the live `y_radius(a1)` for ObjD4's
bottom reject bound, so ObjD4 now opts into
`fullSolidBottomOverlapUsesCurrentYRadiusOnly(...)`.

## 2026-05-17 - S2 CNZ bumper integer trig and speed-shoes timer phase

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 CNZ bumper trig, speed-shoes feature flag, trace diagnostics, and skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.game.TestS3kCharacterSpeeds#speedShoesTimerStaysActiveForRomMovementFrames,com.openggf.game.TestPhysicsProfile#testSpeedShoesTimerPhaseCompensation_PerGame,com.openggf.game.TestHybridPhysicsFeatureSet#testExpectedHybridFeatureSetValues' test -DfailIfNoTests=false`
- Result: fail, 406 errors
- First error: frame 4351, `tails_x` (`expected=0x4000`, `actual=0x0E0D`)

Frontier moved from frame 4121 to frame 4351. The frame 4121 player
`x_speed` mismatch was the S2 CNZ map bumper angle-reflection path:
ROM uses `CalcAngle`/`CalcSine` integer tables before multiplying by
`-$A00` (`docs/s2disasm/s2.asm:32334-32677`), while the engine used a
floating-point `atan2`/`cos` approximation that rounded the incoming angle one
step differently. The later frame 4216 mismatch was speed-shoes expiry phase:
S2 `Obj01_ChkShoes` decrements the `$4B0` word timer from display after
movement and clears speed shoes on the decrement-to-zero frame
(`docs/s2disasm/s2.asm:36008-36025`), so the engine's pre-physics timer must
not add the extra phase tick for S2. S3K keeps the existing extra tick behind
`PhysicsFeatureSet.speedShoesTimerPrePhysicsExtraTicks()` because its ROM uses
a byte `(20*60)/8` timer decremented only every eighth frame
(`docs/skdisasm/sonic3k.asm:22067-22078,40818`). The next blocker is a Tails
despawn mismatch: ROM has Tails at the S2 off-screen marker `$4000,0`, while
the engine leaves her local at approximately `$0E0D,$0320`.

## 2026-05-17 - S2 CNZ Tails spawning object-control gate

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 strict sidekick spawning gate and skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 459 errors
- First error: frame 4599, `tails_x` (`expected=0x0EA5`, `actual=0x0EA4`)

Frontier moved from frame 4351 to frame 4599. The frame 4351 mismatch was an
S2 `TailsCPU_Spawning` gate: ROM tests Sonic's raw `obj_control` byte before
respawning Tails (`docs/s2disasm/s2.asm:38743-38762`), while the engine only
checked the high-level full-object-control flag and missed a movement
suppression-only object-control state from Sonic's flipper/object release. The
fix is scoped to the strict S2-style spawning gate via the existing
`sidekickSpawningRequiresGroundedLeader()` feature; S3K's catch-up path keeps
its narrower gates. The next blocker is a Tails follow/respawn X drift: after
the marker delay and respawn, engine Tails is one pixel left of ROM at frame
4599.

## 2026-05-17 - S2 CNZ post-playable bumper timing and Tails landing radii

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 CNZ post-playable zone-feature hook, Obj85 Tails-only landing preserve, and shared landing-radius gate staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 219 errors
- First error: frame 5336, `tails_x_speed` (`expected=0x00C0`, `actual=0x00CC`)

Frontier moved from frame 4599 through a temporary f5294 Sonic landing
regression and the f5328 Tails one-pixel ground snap to frame 5336. The f4599
X drift came from S2 CNZ map bumpers updating too early relative to the
playable loop; `SpecialCNZBumpers` follows player/object execution in the ROM
main loop, so CNZ now updates its bumper manager through a zone-feature
post-playable-physics hook rather than the generic pre-player feature phase.
The f5294 Sonic regression was Obj85's vertical-launch roll-preserve hook
applying to Sonic even though `Sonic_ResetOnFloor` clears rolling normally.
The f5328 Tails Y mismatch came from shared landing cleanup restoring
non-rolling custom radii for S2 Tails; S2 `Tails_ResetOnFloor` only restores
Tails's `$0F/$09` standing radii inside the rolling branch
(`docs/s2disasm/s2.asm:40629-40636`), while the unconditional non-rolling
radius restore belongs to the S3K `Player_TouchFloor` radius-delta model. The
next blocker is a Tails Obj72/conveyor-area handoff: ROM puts Tails into
air+rolling with `y_vel=-0x680` at frame 5336, while the engine remains
grounded and continues normal CPU acceleration.

## 2026-05-17 - S2 CNZ Obj85 preserved-roll delayed jump filtering

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 preserved-roll sidekick CPU jump filtering and mirrored skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 215 errors
- First error: frame 5399, `tails_y` (`expected=0x0329`, `actual=0x032A`)

Frontier moved from frame 5336 to frame 5399. The frame 5336 mismatch was the
same Obj85 vertical-stopper preserved-roll handoff, but on the sidekick CPU
input path: the engine suppressed all delayed jump state while the preserved
roll flag was set. That kept the earlier grounded stopper frames stable but
also cleared the fresh delayed Sonic jump press ROM later copies through
`TailsCPU_Normal` (`docs/s2disasm/s2.asm:38939-38946`), preventing
`Tails_Jump` from entering air+rolling with `y_vel=-$680`
(`docs/s2disasm/s2.asm:36996-37070`). The filter now remains scoped to the
grounded Obj85 preserved-roll handoff: stale held jump with no fresh delayed
press is cleared, while the later fresh delayed press is allowed through. The
next blocker is a one-pixel Tails Y mismatch after the later airborne/dead
routine handoff around frame 5399; ROM has `y=$0329`, engine has `y=$032A`.

## 2026-05-17 - S2 CNZ dead-fall ObjectMoveAndFall ordering

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with shared death-movement ordering and S2 deferred generic-dead despawn fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 200 errors
- First error: frame 5951, `tails_y` (`expected=0x02B1`, `actual=0x02B0`)

Frontier moved from frame 5399 through the S2 Tails dead-fall marker handoff
to frame 5951. The frame 5399 mismatch came from the shared generic death
path moving with the post-gravity `y_vel`; ROM dead/fall routines load the old
velocity for position, then store the gravity-incremented velocity for the
next frame (`docs/s2disasm/s2.asm:37901-37911,40736-40738,29967-29981`;
S1 `docs/s1disasm/_incObj/01 Sonic.asm:1792-1795`; S3K
`docs/skdisasm/sonic3k.asm:29280-29285,36068-36083`). The frame 5544 mismatch
was the same S2 `Obj02_Dead` deferred-despawn rule reached through the generic
`dead` flag instead of the sidekick CPU `DEAD_FALLING` state: ROM checks
`Tails_Max_Y_pos + $100`, branches to `TailsCPU_Despawn`, then still runs that
frame's `ObjectMoveAndFall` (`docs/s2disasm/s2.asm:40736-40759,39043-39052`).
The new blocker is a Tails air+rolling transition near an Obj74 invisible
block/elevator area: ROM sets Tails airborne+rolling with `y_vel=-$680`, while
the engine leaves Tails grounded at `y=$02B0`.

## 2026-05-17 - S2 CNZ Obj85 preserved-roll lifetime scope

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with Obj85 preserved-roll lifetime scope fix
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 197 errors
- First error: frame 5997, `x_speed` (`expected=0x0000`, `actual=0x000C`)

Frontier moved from frame 5951 to frame 5997. The frame 5951 mismatch came from
the Obj85 preserved-roll marker leaking beyond the rolling stopper handoff: by
frame 5951 Tails was grounded and non-rolling with the marker still set, so the
engine suppressed the normal-follow auto-jump that ROM takes once the preserved
rolling state is gone. The filter now requires Tails to still be rolling before
it suppresses the Obj85 stale push/jump handoff (`docs/s2disasm/s2.asm:38939-38946,
39015-39022,57611-57625`). The new blocker is not ObjD5 gameplay: the trace CSV
input edge turns RIGHT before S2 ROM `Ctrl_1_Held_Logical` reaches `Sonic_Move`
(`docs/s2disasm/s2.asm:701,1361-1387,36253-36260`), so replay currently drives
Sonic one input edge earlier than the sampled ROM physics row.

## 2026-05-17 - S2 CNZ ObjD5 riding horizontal-input phase

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with ObjD5 riding-input phase hook, SCZ Tornado hook migration, regenerated CNZ trace v9.6 diagnostics, and mirrored skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 289 errors
- First error: frame 6018, `tails_g_speed` (`expected=0x0000`, `actual=0x0018`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 5997 to frame 6018. The frame 5997 mismatch was a
V-int/logical-input phase issue while Sonic was riding S2 CNZ ObjD5. The
regenerated v9.6 recorder snapshots showed no `move_lock` or control lock;
instead, the BK2/CSV right edge was visible before the ROM physics row showed
the corresponding inertia change. The fix is object-scoped: shared movement
now asks the current riding `SolidObjectProvider` whether to suppress newly
pressed horizontal logical input for a small number of riding frames, with a
default of zero. ObjD5 opts into three frames based on its
`PlatformObjectD5`/`MvSonicOnPtfm` helper path (`docs/s2disasm/s2.asm:58435-58443,
35617-35657,35402-35420`). The existing SCZ Tornado stale logical-input
compensation was migrated onto the same object hook so shared movement no
longer hard-codes a Sonic 2 object id. The new blocker is Tails follow
acceleration after the Obj74/invisible-block jump window: Sonic matches at
frame 6018, but engine Tails has already applied follow steering
(`tails_g_speed=0x0018`) while ROM keeps Tails at zero ground speed.

## 2026-05-17 - S2 CNZ Obj74 SolidObject_Always offscreen bypass

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with Obj74 offscreen-gate bypass, focused unit test, and mirrored S2 object skill notes staged next
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestInvisibleBlockObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 290 errors
- First error: frame 6276, `y_speed` (`expected=0x0070`, `actual=-0700`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 6018 to frame 6276. The frame 6018 mismatch was S2
Obj74 invisible block side contact: subtype `$33` gives `width_pixels=$20`,
so `Obj74_Main` passes `d1=$2B` and the left edge is `$1560-$2B=$1535`,
matching ROM Tails's stopped X position. The engine skipped the contact
because Obj74 had not opted out of the shared offscreen sidekick full-solid
gate. Obj74 calls `SolidObject_Always`, whose ROM helper explicitly checks
solidity even when the object is offscreen
(`docs/s2disasm/s2.asm:34863-34873,46152-46161`), so the bypass is scoped to
`InvisibleBlockObjectInstance` rather than changing the game-wide solid gate.
The new blocker is a Sonic vertical-speed mismatch after the later monitor /
bonus-block area: ROM is falling with `y_vel=$0070` at frame 6276 while the
engine has entered an upward launch/rebound path (`y_vel=-$0700`).

## 2026-05-17 - S2 CNZ ObjD8 touch radius and inertia preservation

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with ObjD8 touch/bounce fix, focused unit test, and mirrored S2 object skill notes staged next
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestBonusBlockObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 284 errors
- First error: frame 6815, `y_speed` (`expected=-017A`, `actual=-06C8`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 6276 through the first ObjD8 hit to frame 6815. The
frame 6276 mismatch was an early CNZ Bonus Block hit: ObjD8 sets
`collision_flags=$D7`, so the SPECIAL touch size is `Touch_Sizes[$17] = 8,8`
radii, not the engine's old center-distance 12x24 box
(`docs/s2disasm/s2.asm:59570,84623`). The object now uses the ROM
TouchResponse rectangle math for its local collision check. A follow-up frame
6281 mismatch showed that ObjD8's `loc_2C806` bounce tail sets in-air, clears
roll-jump/pushing/jumping, and plays the bonus-bumper sound, but does not
clear `inertia`; the engine now preserves `g_speed`
(`docs/s2disasm/s2.asm:59687-59692`). The new blocker is a later ObjD8 /
bumper cluster around frame 6815, where ROM has a shallow reflected velocity
but the engine has already applied a strong vertical rebound.

## 2026-05-17 - S2 CNZ ObjD8 TouchResponse latch timing

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with ObjD8 latch-timing fix and focused unit test staged next
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestBonusBlockObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 132 errors
- First error: frame 7420, `x` (`expected=0x1C6C`, `actual=0x1C67`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 6815 to frame 7420. The frame 6815 mismatch was
ObjD8 firing inside its own update by re-polling current player overlap. ROM
does not do that: `TouchResponse` latches `collision_property(a0)`, then
`ObjD8_Main` consumes P1/P2 bits and per-player cooldown bytes at `objoff_30`
before calling `loc_2C74E` (`docs/s2disasm/s2.asm:59565-59604,59684-59702`).
ObjD8 now follows the same object-local latch model as S2 Obj44 instead of
changing the shared touch routine. The new blocker is a later forced-spin /
camera-X mismatch after the CNZ bumper/bonus-block cluster: player speed and
subpixel state match, but engine camera X is five pixels left when Sonic enters
Obj84.

## 2026-05-17 - S2 CNZ Obj84 wall-mode autoroll X preservation

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with Obj84 wall-mode autoroll fix and focused unit test staged next
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestForcedSpinObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 130 errors
- First error: frame 7874, `camera_y` (`expected=0x020E`, `actual=0x020C`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 7420 to frame 7874. The frame 7420 mismatch was
S2 Obj84 forced-spin autoroll on a wall-mode surface: `Obj84` sets rolling,
writes the rolling radii/status/animation, and applies `addq.w #5,y_pos(a1)`,
but it never adjusts `x_pos` (`docs/s2disasm/s2.asm:46377-46495`). The engine's
generic wall-mode rolling transition can shift centre X when the width changes,
so Obj84 now preserves the ROM centre X only on this object path. The next
blocker is a later platform/ride-state camera-Y mismatch: player position and
velocity match at frame 7874, but ROM has the on-object status bit and camera
Y two pixels lower than the engine.

## 2026-05-17 - S2 CNZ Obj26 SolidObject_cont geometry

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj26 geometry fix staged next; unrelated line-ending-only dirty files remain unstaged
- Rejected experiment: overriding S2 Obj26 `getMonitorSolidObjectVerticalOffset()` to `4` regressed CNZ to frame 7870, `x_speed` (`expected=0x0450`, `actual=0x0000`), because it combined the generic `SolidObject_cont` vertical bias with the SPG monitor-special side classifier.
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestMonitorObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 56 errors
- First error: frame 8082, `x_speed` (`expected=-0800`, `actual=0x0517`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 7874 to frame 8082. The frame 7874 mismatch was S2
Obj26 monitor solid geometry: `SolidObject_Monitor_Sonic` only gates the
rolling animation hit, then branches into generic `SolidObject_cont`
(`docs/s2disasm/s2.asm:25448-25452`). Engine Obj26 was still opting into the
shared SPG monitor-special classifier, whose top/side geometry differs from
S2 `SolidObject_cont` and missed the ROM standing-bit transition on the
monitor at slot 19 (`0x1E10,0x0291`). The fix is per-object and per-game:
S2 Obj26 now uses normal solid classification after its existing roll gate.
S1 monitor behavior and S3K monitor behavior are unchanged. The new blocker is
an Obj84/ObjD4/bumper region handoff: at frame 8082 ROM launches Sonic left at
`x_speed=-$0800`, while the engine continues right at `x_speed=$0517`.

## 2026-05-17 - S2 CNZ ObjD7 moving range out_of_range

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 ObjD7 range-delete fix staged next; unrelated line-ending-only dirty files remain unstaged
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestHexBumperObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 11 errors
- First error: frame 8419, `tails_x` (`expected=0x0000`, `actual=0x4000`)
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail only on CNZ; S1 GHZ and S2 EHZ reports show `Failures: 0, Errors: 0`

Frontier moved from frame 8082 to frame 8419. The frame 8082 mismatch was a
missing moving ObjD7 Hex Bumper from CNZ spawn `0x1FF8,0x028C` subtype `1`:
ROM kept it alive at slot 38 and launched Sonic left, while the engine had
deleted it with the generic single-X `out_of_range` predicate. Moving ObjD7
does not tail-call `MarkObjGone`; it tests both `objoff_30` and `objoff_32`
movement bounds and deletes only when both are outside the camera window
(`docs/s2disasm/s2.asm:59489-59510`). The shared object manager now has a
narrow custom out-of-range hook, and only moving ObjD7 opts in; all other
objects keep the previous shared path unless they explicitly override it. The
new blocker is a later sidekick despawn/comparator mismatch at frame 8419.

## 2026-05-17 - S2 CNZ Tails flying timeout marker split

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Tails respawn/flying timeout fix staged next; unrelated line-ending-only dirty files remain unstaged
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.sprites.playable.TestSidekickCpuDespawnParity#s2FlyingRespawnTimeoutReturnsToSpawningAtZeroMarker" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.sprites.playable.TestSidekickCpuDespawnParity,com.openggf.sprites.playable.TestSidekickCpuControllerFlightAutoRecovery" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 10 errors
- First error: frame 9147, `tails_x` (`expected=0x4000`, `actual=0x2908`)

Frontier moved from frame 8419 to frame 9147. The frame 8419 mismatch was not
the normal S2 `TailsCPU_CheckDespawn` marker path; ROM was in
`TailsCPU_Flying`'s offscreen timeout, which writes `x_pos=0,y_pos=0`,
`Tails_CPU_routine=2`, `obj_control=$81`, and `Status_InAir`
(`docs/s2disasm/s2.asm:38795-38806`). The engine's APPROACHING path had run
the generic normal despawn pre-check first, producing the `$4000,0` marker
instead. Tails' respawn strategy now owns the S2 flying timeout and keeps S3K
on its existing catch-up-flight path. The new blocker is an end-of-act Tails
state mismatch: ROM marks Tails airborne and at the `$4000,0` normal marker
around frame 9147 while the engine has already settled Tails beside Sonic.

## 2026-05-17 - S2 CNZ Tails shared respawn counter green

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Tails respawn-counter carry fix, focused parity test, frontier log, and mirrored trace skill notes staged next; unrelated line-ending-only dirty files remain unstaged
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.sprites.playable.TestSidekickCpuDespawnParity" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: pass, 0 errors
- First error: none

CNZ is green. The frame 9147 blocker was the second half of the S2 Tails
offscreen-counter split: ROM uses one `Tails_respawn_counter` word across
`TailsCPU_Flying` and the later NORMAL `TailsCPU_CheckDespawn`. The engine had
correctly separated the flying timeout marker (`0,0`) from the normal marker
(`$4000,0`), but then discarded the accumulated offscreen fly-in frames when
the approach completed. CNZ clears Tails' render flag at frame 8847, completes
fly-in at frame 8862 with 16 counted offscreen frames, and therefore reaches
the shared 300-frame normal despawn deadline at frame 9147. The fix is scoped
to the Tails respawn strategy carry hook; other sidekick strategies and S3K's
catch-up-flight path keep independent NORMAL despawn timers.

## 2026-05-18 - S2 CNZ2 frame-0 jump-edge bootstrap fix

- Branch: `develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1193 errors (down from 1262)
- First error: frame 16, `tails_air` (was frame 0, `air`)

The CNZ2 trace recorded the player holding the jump button (input `0x0010`)
continuously from the last title-card frame through the first dozen gameplay
frames. The headless replay fixture skips the title card entirely, so the
leader's `jumpInputPressedPreviousFrame` / `PlayableSpriteMovement.jumpPrevious`
edge trackers entered gameplay frame 0 with virgin `false`. When the BK2
delivered a held jump on frame 0, the engine's edge detector computed
`(jump && !false) = true` and fired `Sonic_Jump` (`docs/s2disasm/s2.asm:36253-36260`)
one frame ahead of ROM, perturbing frame-0 `y`, `y_speed`, `air`, `rolling`, and
cascading to every subsequent row.

Fix: `TraceReplaySessionBootstrap.applyBootstrap` now reads the BK2 input
mask at the cursor offset `-1` (the last title-card frame the production
GameLoop would have ticked) and seeds both the sprite-level jump edge
(`AbstractPlayableSprite.setJumpInputPressed`) and the movement-controller
edge (`PlayableSpriteMovement.primeJumpPreviousForBootstrap`) accordingly.
This mirrors ROM's continuously-updated `Ctrl_1_Held` (`s2.asm:701,1361-1387`
`ReadJoypads` — updated from V-int regardless of `Sonic_ControlsLock`) so the
engine's edge detector correctly computes a held-not-pressed state.

Cross-game regression: S1 GHZ1, S2 EHZ1, S2 CNZ1 all stayed green. Other
act-2 traces (MTZ2, ARZ2, MCZ2, MTZ3, OOZ2, HTZ2, CPZ2) start frame 0 with
no jump held so their frontiers are unchanged.

Next active target: frame 16 `tails_air` on CNZ2. Diagnostic shows Tails CPU
emitting held jump state but Tails ends up `air+rolling` post-physics. The
bootstrap history-pos mismatch (`0x0068` ROM vs `0x0019` engine) may be
causing the 16-frame delayed jump-press lookup to read the wrong slot.

## 2026-05-18 - S2 CNZ2 Tails held-jump bootstrap fix

- Branch: `develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1217 errors
- Frontier moved: frame 16 `tails_air` → frame 205 `camera_x`
- Cross-game `*TraceReplay` sweep: 41 tests / 22 failures, same baseline — no regressions

Root cause: `PlayableSpriteMovement.storeInputState` computed
`inputJumpPress = (jump && !jumpPrevious) || forcedJumpPress` uniformly for
both human-driven and CPU-controlled sprites. For CPU sprites whose `aiJump`
mirrors the leader's delayed held bit but whose `aiJumpPress` mirrors the
delayed press byte, the edge calculation against Tails' virgin `jumpPrevious=false`
manufactured a spurious press the first time the leader's held bit transitioned
from clear to set. CNZ2's BK2 starts with jump held in the title-card prelude,
so Tails inherited a recorded held jump bit without a matching recorded press
byte, and fired `doJump()` at frame 16 putting her into `air+rolling y_speed=-0x680`.

Fix: for `sprite.isCpuControlled()` consume only the explicit `forcedJumpPress`
signal from `SidekickCpuController.getInputJumpPress()`. ROM cite:
`TailsCPU_Normal` (`docs/s2disasm/s2.asm:38939-38946,39025-39027`) writes
the whole delayed `Ctrl_1_Logical` word into `Ctrl_2_Logical`, so Tails'
`Ctrl_2_Press` low byte always matches the leader's delayed press byte rather
than re-deriving an edge from her own held bit.

Note: the `player_history.pos expected=0x0068 actual=0x0019` BootstrapDivergence
is a comparator unit-mismatch (ROM byte offset vs engine slot index) — both
represent the same ring fill state. Not the root cause of the frame-16 failure.

## 2026-05-18 - S2 CNZ2 horizontal spring inclusive right-edge fix

- Branch: `develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1130 errors (down from 1217)
- Frontier moved: frame 205 `camera_x` → frame 435 `x_speed`

Root cause: `SpringObjectInstance.getSolidParams()` (HORIZONTAL subtype) did not
override `usesInclusiveRightEdge()`, so it defaulted to `false` (exclusive `>=`).
At frame 205 Sonic's centre X = 539 = spring.centreX (520) + halfWidth (19), exactly
the right boundary. ROM `SolidObject_cont` (`docs/s2disasm/s2.asm:35147-35150`)
rejects with `bhi` (strictly greater), so `relX == width*2` IS a valid contact.
The engine's default `>=` treated that same case as out-of-range and skipped the
spring entirely. Spring at (0x0208, 0x05F0) then fired correctly: Sonic snapped to
0x0213 (x−8) with x_vel=+$1000, matching the CSV transition at frame 205.

Fix: `SpringObjectInstance` now overrides `usesInclusiveRightEdge()` to return `true`
when `getType() == TYPE_HORIZONTAL`, matching the same pattern already used in
`Sonic3kSpringObjectInstance` for `TYPE_HORIZONTAL`. ROM reference:
`Obj41_Horizontal` routes through `SolidObject_Always_SingleCharacter` →
`SolidObject_cont` (`docs/s2disasm/s2.asm:33780-33784,35147`), whose X gate uses
`bhi`, making the right-edge pixel a valid side contact.

New blocker: frame 435 `x_speed` (expected=0x01CD, actual=-0x05FE). Engine has
Sonic moving fast leftward while ROM continues rightward; engine diagnostic shows
a Crawl (0xC8) touch at slot 20 and matching subpixel position at frame start.

## 2026-05-18 - S2 CNZ2 Crawl (0xC8) bounce physics and ENEMY dispatch fix

- Branch: `develop`
- Command: `mvn -q "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1078 errors (down from 1130)
- Frontier moved: frame 435 `x_speed` → frame 554 `tails_y`

Root cause: Two bugs in `CrawlBadnikInstance` and one in `ObjectManager` ENEMY dispatch.

1. **Wrong collision size index** (`CrawlBadnikInstance.COLLISION_SIZE_INDEX`): was 0x09
   (Touch_Sizes[9] = 12×16 px) instead of ROM's 0x17 (Touch_Sizes[23] = 8×8 px).
   This caused a wider touch window, producing the first touch 3 frames earlier
   (frame 435) than the actual first contact (frame 438).

2. **Incorrect radial bounce**: `applyBounce` applied a flat −$700 y_vel instead of
   the ROM's radial `CalcAngle + CalcSine × −$700 / 256` formula (`loc_3D3A4`,
   s2.asm:81932-81958). Rewritten using `TrigLookupTable.calcAngle` / `cosHex` /
   `sinHex`, matching the wobble `(Level_frame_counter >> 8) & 3` exactly.
   For the trace contact at frame 438 (dx=16, dy=12, angle=27), expected
   y_vel = −$44B (−1099) was confirmed correct.

3. **Double-bounce from ENEMY dispatch** (`ObjectManager.handleTouchResponse`):
   after `onPlayerAttack` applied the radial bounce (y_vel=−1099), the ENEMY path
   called `applyEnemyBounce` because `hpBeforeHit=0 && !wasAlreadyDestroyed`.
   `applyEnemyBounce` overwrote y_vel to −843 (+256 offset vs expected). Fix:
   `applyEnemyBounce` is now conditional on `instance.isDestroyed()` after
   `onPlayerAttack`. Normal badniks (destroy-in-one-hit) are unaffected; Crawl's
   shield bounce (survives) correctly skips the second bounce.

Files changed:
- `src/main/java/com/openggf/game/sonic2/objects/badniks/CrawlBadnikInstance.java`
- `src/main/java/com/openggf/level/objects/ObjectManager.java`

New blocker: frame 554 `tails_y` (expected=0x05F1, actual=0x05F0). Tails Y off by
1 px when rolling-mode transition occurs (engine `st=00`, ROM `status=04`). Crawl
in slot 20 is in WALKING state (rtn=02), not involved. Likely a Tails rolling-mode
transition timing issue: `tails mode rolling 0→1` annotation present at frame 554.

## 2026-05-18 - S2 CNZ2 pinball-mode Tails jump and landing-clear fixes

- Branch: `develop`
- Command: `mvn -q "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1057 errors (down from 1078)
- Frontier moved: frame 554 `tails_y` → frame 1490 `tails_y_speed`

Root cause: Two bugs in sidekick CPU and landing logic related to `pinball_mode`
(set by S2 Obj84/FORCED_SPIN when Tails crosses the x=0x0400 horizontal trigger
in CNZ2).

1. **`PlayableSpriteMovement.resetOnFloor()` cleared `pinball_mode` unconditionally**:
   ROM `Tails_ResetOnFloor` (`docs/s2disasm/s2.asm:40624-40660`) branches to
   `Tails_ResetOnFloor_Part3` when `tst.b pinball_mode(a0)` is non-zero (`bne.s`),
   skipping the roll-clear block entirely. Part3 only clears in_air/pushing/
   rolljumping/jumping flags — it never touches `pinball_mode`. The engine's
   unconditional `sprite.setPinballMode(false)` was clearing pinball mode on every
   landing, causing the 16-frame delayed B-press to fire a jump at frame 936 when
   pinball mode should have been preserved. Fix: made the `setPinballMode(false)` call
   conditional on `!(rolling && pinballMode && preservePinballRoll)`, matching both
   ROM `Sonic_ResetOnFloor` (`s2.asm:37770-37771`) and the already-correct
   `CollisionSystem.java` implementation.

2. **`SidekickCpuController.updateNormal()` did not suppress jump when rolling in
   pinball mode**: ROM `Obj02_MdRoll` (`docs/s2disasm/s2.asm:39279-39282`) skips
   `bsr.w Tails_Jump` entirely when `pinball_mode` is set. The engine's 16-frame
   delayed press at frame 937 (Tails lands at frame 930-931, fresh B-press delayed
   from frame 921) would reach `doJump()` unguarded. Fix: added pinball-mode check
   in `updateNormal()` after the existing Obj85 rolling suppression block.

3. **`doCheckStartRoll()` narrowed CPU move_lock guard to S3K only**: S1/S2
   `Sonic_RollStart` has no `move_lock` gate (`docs/s2disasm/s2.asm:36954-36963,
   39939-39942`). The prior over-broad guard for all games blocked S2 Tails from
   rolling during move_lock even though the ROM never suppresses it. Narrowed to
   S3K only (where `Tails_InputAcceleration_Path` at `sonic3k.asm:27797-27815` gates
   duck/direction input on `move_lock`). This allowed the frame 554 Tails rolling
   start to propagate correctly. Updated `TestPlayableSpriteMovement.
   cpuSidekickMoveLockSuppressesGeneratedDownRoll` to use S3K physics explicitly.

Files changed:
- `src/main/java/com/openggf/game/sonic2/objects/badniks/CrawlBadnikInstance.java`
  (deferred ATTACKING touch via `TouchResponseListener`, `getCollisionFlags()` 0xD7)
- `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
  (`resetOnFloor` pinball guard, `doCheckStartRoll` S3K-only move_lock guard)
- `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
  (pinball-mode jump suppression in `updateNormal`)
- `src/test/java/com/openggf/sprites/managers/TestPlayableSpriteMovement.java`
  (updated `testS2LandingPreservesRollingInPinballMode` and
  `cpuSidekickMoveLockSuppressesGeneratedDownRoll` to match ROM-accurate behavior)

New blocker: frame 1490 `tails_y_speed` (expected=-0x059B, actual=-0x052B).
Tails bounced by ATTACKING Crawl (rtn=06, slot 19) at x=0x0735, but engine Crawl
is at x=0x732 (3 px left). The 3-pixel x-position difference changes the `CalcAngle`
result, reducing the upward component of the radial bounce by 0x70. Root cause is
a Crawl walking-position drift accumulated before the ATTACKING transition —
likely the proximity check fires at a different frame in engine vs ROM, causing the
Crawl to stop walking at a different x.

## 2026-05-19 - S2 MTZ Long Platform PROPERTIES table index + non-conveyor carry fixes

- Branch: `develop`
- Command: `mvn -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 2189 errors (down from 2370)
- Frontier moved: frame 221 `y` -> frame 305 `y` (now an MTZ2 conveyor landing
  issue at slot s29 `0x6C` Conveyor near `(0x0320,0x0607)`)
- Cross-game `*TraceReplay` regression: S1 GHZ1, S2 EHZ1, S2 CNZ, S2 CNZ2, S2 ARZ,
  S2 MTZ3, S2 OOZ unchanged. S2 MTZ act 1 dropped from 1015 -> 989 errors at the
  same frame 281 `y` Steam Spring frontier.

Root cause: Two bugs in `MTZLongPlatformObjectInstance` (s2.asm Obj65).

1. **`PROPERTIES` table indexed by `d0 >> 2` instead of `d0 >> 1`**: ROM
   `Obj65_Init` (`docs/s2disasm/s2.asm:52379-52414`) computes
   `d0 = (subtype >> 2) & $1C` as the **byte offset** into the 2-byte-per-entry
   `Obj65_Properties` table (`s2.asm:52366-52376`), then performs a second
   `lsr.w #2,d0` to derive the mapping_frame (= entry_index / 2). The engine was
   conflating these two values and using `d0 >> 2` (= mapping_frame) as the
   `PROPERTIES[]` row index, so a button-spawned platform with subtype `0xB1`
   (entry 6, mapping_frame 3) ended up reading entry 3 `{0x40, 0x03}` instead
   of entry 6 `{0x40, 0x0C}` for width/y_radius, and entry 4 `{0x10, 0x10}`
   instead of entry 7 `{0x80, 0x07}` for maxDist and the child subtype. With
   `childSubtype` decoded as `0x10` rather than `7`, the engine set
   `moveSubtype = 0x10 & 0x0F = 0` (stationary) and skipped the
   `currentDist = maxDist` seeding, so the button-triggered platform at
   `(0x02C0, 0x064D)` in MTZ2 never moved off its spawn x. Fix: split the
   shared index into `entryIndex = d0 >> 1` (table row) and
   `frameIndex = d0 >> 2` (mapping_frame).

2. **Continued-riding carry applied for non-conveyor movement subtypes**: ROM
   `Obj65` saves `x_pos` to `objoff_2E` before each frame's subtype routine
   (`s2.asm:52454`) and uses `objoff_2E` as the `d4` carry reference for
   `MvSonicOnPtfm` (`s2.asm:35402-35423`). The movement routines for subtypes
   1/2/6/7 (`loc_26D50`) and 3 (`loc_26E1A`) refresh `objoff_2E` to the new
   `x_pos` after the move, so `MvSonicOnPtfm` computes a zero carry delta and
   the rider stays still while the platform glides underneath. Only the
   conveyor subtype 5 (`loc_26E4A`) leaves `objoff_2E` untouched, producing
   the conveyor's `+2 px/frame` rider carry. The engine's `ObjectManager`
   inline continued-riding path was unconditionally shifting the rider by
   `currentX - ridingX`, so once the platform-index bug was fixed and the
   subtype-7 retract started moving, Sonic was being carried +2 px/frame
   matching the platform speed. Fix: added
   `SolidObjectProvider.carriesRiderOnHorizontalMove(player)` (default
   `true`) and overrode it on `MTZLongPlatformObjectInstance` to return
   `moveSubtype == 5`, so only the conveyor subtype carries the rider.

Files changed:
- `src/main/java/com/openggf/game/sonic2/objects/MTZLongPlatformObjectInstance.java`
- `src/main/java/com/openggf/level/objects/SolidObjectProvider.java`
- `src/main/java/com/openggf/level/objects/ObjectManager.java`

DEZ ending investigation (no fix this session): frontier still at frame 536
`rolling` (expected=1, actual=0) for `TestS2DezEndingLevelSelectTraceReplay`,
174 errors. The trace shows Sonic in an air+rolling jump from frame 417 with
a hard radial velocity reversal at frame 536 (`xv 0x0178 -> 0xFE70`,
`yv 0x03E0 -> 0xFBE8`) inside a fixed-camera arena (`camera_x=0x0224`). The
nearby DEZ_1 layout has only Obj2D barriers and the Obj C7 Death Egg Robot,
none at the bounce x; the reversal pattern looks like a scripted
boss-arena bounce. The default trace reporter does not surface enough
post-bounce engine state to localise which rolling-clear path fires before
frame 536. Tractable next step: extend the trace recorder to capture the
ENEMY/ATTACK touch slot per frame so the engine's first rolling-clear can be
isolated, or replay just the DEZ ending with a per-frame rolling-state
printout to localise the divergent frame.

## 2026-05-19 - S2 MTZ3 Obj6A per-phase activation gate

- Branch: `worktree-agent-ac2f125d8f7e5c121` (merged from `develop`)
- Command: `mvn -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 2626 errors (was 2349)
- Frontier moved: frame 340 `tails_g_speed` -> frame 460 `tails_air` (Tails
  missed-landing event; ROM lands and tails_x jumps to 0x4000 indicating
  CPU respawn / despawn marker; engine keeps falling)
- Cross-game `*TraceReplay` regression check:
  - EHZ1: green (all frames match)
  - MTZ1: still frame 375 `tails_air`, errors 945 -> 905 (improved)
  - MTZ2: unchanged at frame 305 `y` (conveyor-cluster landing parity, separate bug)
  - ARZ/ARZ2/CPZ/CPZ2/CNZ/CNZ2/HTZ/HTZ2/MCZ/MCZ2/OOZ/OOZ2/SCZ/WFZ/DEZ all
    unchanged
  - S3K AIZ/CNZ/MGZ unchanged

Root cause: `MCZRotPformsObjectInstance.loadPhaseParameters()` did not mirror
ROM `loc_27CA2`'s `move.b #0,objoff_36(a0)` (`docs/s2disasm/s2.asm:53844`).
ROM Obj6A in MTZ routes through routine 2 (`loc_27BDE` at s2.asm:53754) which
gates `ObjectMove` on `objoff_36` (the activation flag). Each call to
`loc_27CA2` clears that flag at the end, so the next frame falls back to
standing-bit edge detection (`loc_27BDE` -> `loc_27C28`) and the platform
waits for another walk-off before running its next phase. The engine's
prior implementation latched `activated=true` permanently after the first
walk-off, so once Sonic stepped off any MTZ Obj6A it would cycle through
all four MTZ phases unattended -- across ~340 frames the cumulative phase
offset put slot 17's platform 0x2C px right and 0x16 px up of ROM, exactly
the position where Tails landed on the engine's platform top while ROM's
Tails kept falling.

Fix: in `loadPhaseParameters`, clear `activated` when `isMtz` (MCZ uses
routine 4 `loc_27C66` which ignores `objoff_36`, so its `activated=true`
seed must survive). Also documented the ROM citation alongside the reset.

File changed:
- `src/main/java/com/openggf/game/sonic2/objects/MCZRotPformsObjectInstance.java`

New blocker: frame 460 `tails_air` expected=0, actual=1, with cascading
tails_y_speed/tails_g_speed/tails_y/tails_x errors. ROM Tails y_speed jumps
from 0x428 (still falling) at f459 to 0 at f460 (landing), tails_x lifts to
0x4000 at f461 (CPU despawn marker). Engine continues falling with
y_speed=0x450. Diagnosis target: identify which solid surface ROM Tails
lands on at f460 (`pos=(0x0328, 0x02D0)`) and why engine misses it.

MTZ2 conveyor frontier (still frame 305 `y` 0x05EB vs 0x05F2): investigated
but not fixed. Engine player lands on a child conveyor whose Y is ~7 px
lower than ROM's equivalent conveyor (slot s29 `0x6C @0320,0607`). The
engine's `eng-near` debug list shows only one of the three left-column
conveyors (column 0x0320), so the offending slot 127 is presumably the
middle child at ~y=0x060E rather than the ROM's y=0x0607. Layout offset
tables and parent base-position routing match ROM exactly; the displacement
suggests either differential waypoint-velocity timing among siblings or a
spawn position issue not visible in the current trace surface. Tractable
next step: extend the trace recorder to dump ALL conveyor slot positions
(not just the truncated near list) so the engine's full column-0x0320
conveyor set can be diff'd against ROM s28/s29/s30.

## 2026-05-19 — S2 MTZ2 Conveyor (Obj6C) parent factory re-spawn loop fix

- Target: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace" test`
- Baseline (HEAD develop / `1b5308308`): MTZ2 frontier frame 305, y 0x05F2 vs ROM 0x05EB (7 px), 2325 errors
- Result: frame 305, y 0x05EA vs ROM 0x05EB (1 px, sub-pixel residual), 2770 errors
- Branch context: worktree `agent-a6827fe9d2a969098` on `develop`

Root cause. `ConveyorObjectInstance.createOrSpawnChildren` returned `null` for
parent-spawner subtypes (bit 7 set), expecting `ObjectManager` to skip
registering the parent in `activeObjects`. The placement loop
(`syncActiveSpawnsLoad` `sortedNewSpawns` gate at
`ObjectManager.java:2362`) only filters spawns already in `activeObjects`,
so the parent re-entered the spawn list every frame the chunk stayed in the
camera window. Diagnostic logging in `createOrSpawnChildren` showed the
8-child cohort spawning 207,312 times across the test run (≈ 25,914 parent
re-spawns).

Fix. Mirror ROM `Obj6C_Init` `loc_28112` (`s2.asm:54269-54301`), which uses
`movea.l a0,a1` to reuse the parent slot as the first child:
`createOrSpawnChildren` now constructs the first child instance from
`layout[0]` using the parent's own spawn and returns it as the factory
result. The remaining 7 children spawn via `addDynamicObject` as before.
Because the factory now returns non-null, `registerActiveObject(spawn,
firstInstance)` runs and the placement's `activeObjects.containsKey(spawn)`
gate suppresses re-spawn.

Verification:
- `TestS2Mtz2LevelSelectTraceReplay`: frame 305 first error, y 7 px → 1 px
- `TestS2MtzLevelSelectTraceReplay`: unchanged at frame 375 `tails_air`
- `TestS2Mtz3LevelSelectTraceReplay`: unchanged at frame 460 `tails_air`
- `TestS2Ehz1TraceReplay`: green
- `TestS2CnzLevelSelectTraceReplay`: unchanged 22 errors at frame 3906
  `tails_y` 1 px (pre-existing sub-pixel residual)
- `TestS2Mcz2LevelSelectTraceReplay`: unchanged at frame 1079, 802 errors
- All S3K HCZ conveyor unit tests pass

The 1 px residual at MTZ2 f305 is sub-pixel rounding at landing; the
conveyor cluster positions now match ROM exactly (engine s28 @0320,0607
matches ROM s29 @0320,0607; engine s29 @0320,064D matches ROM s30
@0320,064D).

MTZ1 frontier (frame 375 `tails_air` expected 1 actual 0) investigated.
ROM warps Tails to (0x4000, 0x0000) with `in_air` set — the classic
`TailsCPU_Despawn` marker (`s2.asm:39043-39052`). At frame 374 Tails is
standing (yvel=0) at (0x02BC, 0x0250); the camera at (0x020C, 0x014D)
puts Tails roughly 0x103 (259) px below the bottom of the 224 px screen.
Sonic just rocketed up off the SteamSpring (`y_vel=-0x300` at f375), so
the camera is locked high enough that Tails is off-screen.
`TailsCPU_CheckDespawn` (`s2.asm:39055-39081`) immediate-despawns when
off-screen + `status.player.on_object` + `Tails_interact_ID` mismatch, or
ticks `Tails_respawn_counter` to 0x12C otherwise. Determining which path
fires in the ROM requires either the `Tails_interact_ID` value or the
`Tails_respawn_counter` to be exposed in the trace; both are currently
absent from the schema. Not committed; flagged as a recorder-extension
candidate.

## 2026-05-19 - S2 MTZ3 Tails air=0 at frame 765 investigation (no committed change; dead end)

- Branch: `develop` (HEAD `6acc363ef`)
- Worktree: isolated agent worktree
- Command: `mvn -q -Dmse=relaxed "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 2601 errors, frontier frame 765, field `air` (expected=1, actual=0)
- Outcome: investigation only; no fix identified; frontier unchanged

This investigation follows the SteamSpring `bypassesOffscreenSolidGate` fix that
advanced MTZ3 from f460 to f765. The new frontier has ROM Tails airborne (`air=1`)
while the engine has Tails grounded (`air=0`).

### ROM state at frame 765

From `aux_state.jsonl.gz` and `physics.csv.gz`:
- Tails at `(0x05D5, 0x01CB)`, ysp=0x0000, `air=1`, `stand_on_obj=22` (slot 22)
- Sonic undergoes routine change from 2 to 4 (hurt), `stand_obj_slot=23`
  (a LargeRotPform, Obj6E, at x=0x0534, y=0x021A — Sonic lands on it)
- Lost rings (type 0x37) spawn at slots 20, 28, 31, 32, 33
- Frame 760-765: Tails position oscillates slowly around `(0x05C5-0x05D5, 0x01D0-0x01CB)`,
  ysp=0x0000, air=1, no change in stand_on_obj (=22 throughout this window)

Nearby ROM objects at f765 (from aux slot dump):
- Slot 49: type 0x36 (Spikes), status=0x02 (upside-down), at `(0x05C0, 0x0130)`
- Slot 23: type 0x6E (LargeRotPform), `(0x0534, 0x021A)` — the platform Sonic lands on
- Slot 30: type 0x6E (LargeRotPform), `(0x056D, 0x021A)`
- Slot 16, 18, 35, 39-42: type 0x6E (additional LargeRotPforms in the MTZ3 room)
- Slot 43: type 0x9F (Shellcracker)
- Slot 29: type 0x42 (SteamSpring)

### Solid-object candidates eliminated

1. **ShellcrackerClaw (slots 44-46, 52-56, type 0xA0):** Not a `SolidObjectProvider`;
   cannot ground Tails.

2. **Spikes slot 49 (upside-down, Obj36 routine 6 at `(0x05C0, 0x0130)`):** Upside-down
   spikes call `SolidObject` (`s2.asm:29260`) to produce a ground-contact box whose top
   surface is at `y = 0x0130 - 0x10 = 0x0120 = 288`. Tails is at y=0x01CB = 459, which
   is 171 px below the spike box top. Cannot ground Tails.

3. **LargeRotPform slot 30 (`(0x056D, 0x021A)`):** Entry index for this platform (from
   byte_283C0, `s2.asm:54473`) is likely subtype 2 (width=0x60, yRadius=0x18). The
   platform top surface is at `y_centre - yRadius = 0x021A - 0x18 = 0x0202 = 514`.
   Tails is at y=0x01CB = 459. Tails is 55 px ABOVE the top of this platform — it
   cannot be the source of a ground contact from below.

4. **LargeRotPform slot 23:** Dropped out of Tails' `object_near` list by frame 765
   (too far away to register contact). Tails is still logged with `stand_on_obj=22`
   from prior frames, but slot 22 is not a nearby solid at f765.

5. **Offscreen gate false positive:** Tails is at `(0x05D5, 0x01CB)`, camera at
   approximately `(0x04E0, 0x014D)` for MTZ3 at this region. Relative Y from camera
   top is approximately 0x01CB - 0x014D = 0x7E = 126 px, well within the 224 px
   viewport plus any margin. `shouldSkipOffscreenSidekickFullSolid` returns false
   (Tails is on-screen).

### Root cause hypothesis (unconfirmed)

After the SteamSpring launches Tails at frame 460, ROM moves Tails to `(0x4000, 0x0000)`
and keeps her in `TailsCPU_Flying` state (`obj_control=0x81`) for approximately 178
frames (`s2.asm:38788-38888`). The engine's equivalent is `TailsRespawnStrategy` in
`APPROACHING` state (`objectControlled=true`, `requiresPhysics()=false`). In both ROM
and engine, terrain collision is suppressed during this flight phase.

The transition out of flight:
- **ROM:** `TailsCPU_Flying_Part2` exits to normal when Tails reaches Sonic's 16-frame-
  delayed position AND `andi.b #$D2,d2; bne.s return` clears (d2 is Sonic's status —
  must not have in_air, roll_jump, underwater, or bit7 set). On exit, Tails is placed
  at Sonic's position with `obj_control` cleared; normal physics resumes.
- **Engine:** `TailsRespawnStrategy.updateApproaching()` returns true when
  `remainingDx==0 && dy==0 && (recordedStatus & 0xD2)==0`.
  `SidekickCpuController.updateApproaching()` then clears `objectControlled`, zeroes
  XSpeed/YSpeed/GSpeed, sets `air=true`, and transitions to `NORMAL` state.

The engine Tails at frame 765 has `air=0` (grounded) while ROM Tails has `air=1`. This
implies the engine Tails exits APPROACHING at a position where terrain collision
immediately grounds her, while ROM Tails exits `TailsCPU_Flying` airborne.

Two candidate causes:
- **Y position offset on APPROACHING-to-NORMAL transition:** The engine's APPROACHING
  trajectory places Tails at a Y that happens to coincide with the top of an oscillating
  LargeRotPform at that exact frame. The oscillator phase difference between engine and
  ROM would determine which pform top is where.
- **Oscillator phase mismatch:** If `OscillationManager.getByte(0x20)` / `getByte(0x24)`
  are at a slightly different phase in the engine vs ROM at f765, one of the many
  LargeRotPforms in the MTZ3 room could present its collision top exactly at Tails' Y
  in the engine but not in the ROM.

### What would be needed to confirm

1. **Recorder extension:** Add `tails_cpu_state` (maps to `obj_control` byte),
   `tails_cpu_target_x`, and `tails_cpu_target_y` fields to `physics.csv` so the
   APPROACHING-to-NORMAL transition frame can be pinpointed precisely (currently
   impossible from existing trace fields).
2. **Oscillator field:** Add `oscillator_0x20` and `oscillator_0x24` to aux diagnostics
   so the LargeRotPform platform positions can be reconstructed from the ROM side and
   compared against the engine positions at the transition frame.
3. **Alternative:** Run the engine with diagnostic logging of the APPROACHING-to-NORMAL
   transition frame, Tails' exact position at that frame, and which solid object (if any)
   grounds her.

Did not commit a fix. Root cause requires recorder extension or runtime diagnostic
logging to identify the exact frame and platform involved. Flagged as a
recorder-extension candidate.

---

## 2026-05-20 - S2 ARZ Rising Pillar (Obj2B) player-release on_object clear (ARZ1 f304 -> f311)

- Branch: `develop` worktree `agent-ae0e08d9dae24b8cc`
- Command: `mvn test -Dtest=TestS2ArzLevelSelectTraceReplay`
- Result: ARZ1 frontier advanced from frame 304 (813 errors) to frame 311 (846 errors).
- Regression check: `TestS2Ehz1TraceReplay` PASS (0 errors); `TestS2Arz2LevelSelectTraceReplay`
  still at frame 225 (2129 errors, pre-existing); `TestS2MczLevelSelectTraceReplay` and
  `TestS2Mcz2LevelSelectTraceReplay` unchanged.

### Correction to prior log entries

Two entries from the 2026-05-20 MCZ2 session incorrectly stated:
- "TestS2Ehz1TraceReplay still at frame 304 (813 errors, pre-existing)"
- "TestS2ArzLevelSelectTraceReplay passes (unchanged)"

These were transposed. `TestS2Ehz1TraceReplay` was and remains PASS (0 errors, full trace).
`TestS2ArzLevelSelectTraceReplay` was the test at frame 304/813 errors.

### Root cause

`RisingPillarObjectInstance.releasePlayerAndBreak()` (s2.asm:51393-51405, `loc_25AF6`) set
`rolling=true` and `in_air=true` on the launched player but omitted the ROM's
`bclr #status.player.on_object,status(a1)` (s2.asm:51401), leaving on_object set.

Two cascading effects:

1. **status bit mismatch:** Engine Tails entered frame 304 with both in_air=true and
   on_object=true (status 0x0F), while ROM Tails had only in_air=true (status 0x07).

2. **Stale riding state re-grounding:** The `ridingStates` map in `ObjectManager.SolidContacts`
   still held a Tails→pillar entry from frame 303. In `PlayableSpriteMovement` at the start
   of frame 304, the pre-movement recovery check (`!isUnifiedCollision && sprite.getAir() &&
   hasGroundingObjectSupport() && sprite.getYSpeed() >= 0`) fired: `hasGroundingObjectSupport()`
   returned true from the stale riding state entry, forcing `setAir(false)` and `setOnObject(true)`
   again. This caused the solid contacts path to apply object-riding deceleration (−0x26) instead
   of air deceleration (−0x18), producing tails_x_speed=0x00C2 vs ROM's 0x00D0.

### Fix

In `releasePlayerAndBreak()`:
- Added `player.setOnObject(false)` matching ROM s2.asm:51401.
- Added `objectManager.clearRidingObject(player)` to flush the stale riding-state entry
  so the pre-movement recovery cannot re-ground the player on the next frame.

### New frontier at frame 311

ROM Tails enters hurt routine (routine 02→04) at frame 311 when hit by an Arrow (Obj22,
slot 18) at position (0x031C, 0x0378). Engine Arrow is at (0x0300, 0x0378) — 0x1C (28px)
to the left. Tails' centre Y (0x0369) with roll radius 7 px gives bottom at 0x0370; Arrow
top at 0x0376 is 6 px below Tails' bottom, so no touch contact. The Arrow position error
was already present at frame 304 (engine 0x02E4 vs ROM 0x0300) before this fix — it was
masked by the earlier pillar-launch failure. Root cause: Obj22 Arrow movement or spawn
position divergence, pre-existing and separate from this fix.
