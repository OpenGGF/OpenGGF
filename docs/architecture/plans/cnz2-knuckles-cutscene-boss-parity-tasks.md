# CNZ2 Knuckles Cutscenes + End Boss — Disassembly Parity Task List (Sonic path)

Review date: 2026-07-18. Scope: the two CNZ Act 2 rival-Knuckles cutscenes, the cutscene
button/wall/lights-flash support objects, `Obj_CNZEndBoss`, and the post-boss capsule →
cannon → ICZ1 handoff, Sonic (non-Knuckles) path only.

ROM anchors are S&K-half labels in `docs/skdisasm/sonic3k.asm` unless noted. Key line
references: `CutsceneKnux_CNZ2A` :129040, `CutsceneKnux_CNZ2B` :129247, shared cutscene
helpers :128818–128899, wall child `loc_62458` :129180, flash child `loc_62480` :129192,
cutscene button `loc_65C04` :133936, `Obj_CNZEndBoss` :145801–146763, camera helpers
`Check_CameraInRange`/`loc_85CA4`/`loc_85D70` :180433–180599, ship child
`Obj_RobotnikShip4` :136485.

Engine files (all under `src/main/java/com/openggf/game/sonic3k/`):
`objects/CutsceneKnucklesCnz2AInstance.java`, `objects/CutsceneKnucklesCnz2BInstance.java`,
`objects/CutsceneKnuxCnz2WallInstance.java`, `objects/Cnz2CutsceneButtonInstance.java`,
`objects/CnzLightsFlashChildInstance.java`, `objects/bosses/CnzEndBoss*.java`,
`objects/CnzCannonInstance.java`, `objects/CnzEggCapsuleInstance.java`,
`events/Sonic3kCNZEvents.java`.

Tasks are ordered roughly by gameplay impact within each section. Each task is
self-contained for delegation. General rules: no zone/frame carve-outs, model the ROM
state that drives each branch; per CLAUDE.md keep guards and existing CNZ tests green and
update `docs/S3K_KNOWN_DISCREPANCIES.md` + `CHANGELOG.md` where relevant.

---

## A. Shared cutscene infrastructure

- [ ] **A1. Implement `Check_CameraInRange` gating for both cutscenes.**
  ROM :180433. Each frame before the routine dispatch, the object checks camera Y/X
  against a 4-word window and deletes itself (respawnable) when outside; it also sets
  approach-direction flags (bit7 = camY > lock target, bit6 = camX > lock max) in `$27`
  that `loc_85CA4` consumes. Windows: CNZ2A `word_6228E` = camY $176–$300, camX
  $1C00–$1E00 (:129034); CNZ2B `word_62520` = camY $720–$A00, camX $45C0–$46E0 (:129269).
  Engine currently starts both cutscenes as soon as object placement spawns them
  (~320 px early): music fade, control lock and camera writes can begin off-screen.
  Add the in-range gate + delete/respawn semantics to `CutsceneKnucklesCnz2AInstance`
  and `CutsceneKnucklesCnz2BInstance` (a shared helper is fine — HCZ2/LBZ cutscenes use
  the same routine).

- [ ] **A2. Port `loc_85CA4` boss/cutscene camera-converge helper faithfully.**
  ROM :180484. Three independent sub-goals tracked via `$27` bits: (0) play
  `boss_saved_mus` after the `$2E` timer (2*60 set by `loc_85D70`) regardless of camera
  state; (1) min-Y follows camera each frame until reaching `_unkFAB0`, then min-Y and
  target-max-Y lock; (2) min-X follows camera until `_unkFAB4` (or max-X ratchets down
  when approaching from the right, bit6), then min-X/max-X lock. Only when all three done
  does the continuation (`$34`) run. Engine `CutsceneKnucklesCnz2AInstance.updateCameraLock`
  approximates this but serializes music after timer, ignores approach-from-right, and
  `CnzEndBossInstance.updateCameraGate` snaps min/max instantly instead of converging.
  Extract a shared engine helper and use it in CNZ2A and the end boss.

- [ ] **A3. Use terrain probes for Knuckles landings, not spawn-Y flooring.**
  ROM bounce routines (`loc_6237C` :129110, `loc_620AA` :128874) call
  `ObjCheckFloorDist` and snap by the returned distance. Both engine cutscene classes
  treat `spawn.y()` as the floor, so bounce heights/timings (and the button-press moment)
  drift wherever the floor isn't exactly at spawn Y. Use
  `ObjectTerrainUtils.checkFloorDist` (as `CnzEndBossMagnetChild` already does).

- [ ] **A4. Fix animation-delay off-by-one convention.**
  ROM `Animate_Raw` shows each frame for (delay+1) frames. Engine `animateLoop(frames,
  delay)` shows frames for `delay` frames. `RUN_DELAY = 5` already accounts for ROM 4+1,
  but `JUMP_DELAY = 1` (ROM byte_666AF delay 1 → should be 2) and `LAUGH_DELAY = 7`
  (ROM 7 → should be 8) do not. Audit every frames/delay constant in both cutscene
  classes against `byte_6669F`/`byte_666A9`/`byte_666AF`/`byte_666B9`/`byte_666BF`
  (:135115–135126) and align to the +1 convention.

## B. CNZ2A first encounter (button / water / lights-out)

- [ ] **B1. Correct the post-bounce laugh animation frames.**
  After the final landing (`loc_623B8` → `loc_62056` :128843) Knuckles plays
  `byte_666B9` = frames $1C,$1C,$1D (delay 7+1), not the $1E/$1F loop. Engine
  `routineLaughWait` sets `mappingFrame = 0x1C` then immediately overwrites it with
  `LAUGH_LOOP` {$1E,$1F}. Use the $1C/$1D script for LAUGH_WAIT; keep $1E/$1F only for
  the initial pose (`byte_666BF`, used in CAMERA_LOCK/PRE_JUMP phases — that part is
  correct).

- [ ] **B2. Restore camera bounds gradually at cutscene end.**
  ROM `loc_623FE` (:129152): on Knuckles leaving the screen it sets target-max-Y back to
  the stored value instantly, then spawns `ChildObjDat_66568` = three gradual movers
  (`Obj_DecLevStartYGradual`, `Obj_DecLevStartXGradual`, `Obj_IncLevEndXGradual`).
  Engine `restoreStoredCameraBounds()` snaps everything instantly — visible camera jump.
  Reuse/generalize `CnzEndBossBoundaryController` (it already implements the gradual
  0x4000-subpixel ramp) to cover min-X-down, min-Y-down and max-X-up here.

- [ ] **B3. Palette line 2 snapshot/restore + line 1 decision.**
  ROM init (`loc_622E4` :129064) copies Normal_palette_line_2 → Target_palette_line_2
  ($20 bytes) before loading `Pal_CutsceneKnux` into line 1; the ending (`loc_62422`
  :129161) copies the target snapshot back into line 2. The S&K half does **not** reload
  `Pal_CNZ` into line 1 at CNZ2A's end (that is `s3.asm` `loc_44D6E` behavior — the
  engine comment in `restoreLevelPaletteLine1()` cites the wrong half). Decide: either
  match S&K exactly (snapshot/restore line 2, leave line 1 until CNZ2B end reloads it)
  after verifying `Pal_CutsceneKnux` vs `Pal_CNZ` line-1 bytes, or keep the engine's
  line-1 restore as a documented intentional divergence in
  `docs/S3K_KNOWN_DISCREPANCIES.md`. Implement the line-2 snapshot/restore either way.

- [ ] **B4. End-of-cutscene housekeeping: PLC_Monitors reload + music fade.**
  ROM `loc_62422` loads `PLC_Monitors` raw (monitor art shares the cutscene-Knuckles
  VRAM region) and spawns `Obj_Song_Fade_ToLevelMusic` (fade-out then level music).
  Engine skips the PLC reload and hard-cuts to the CNZ2 track. Verify whether the
  engine's VRAM/art pipeline needs the monitor-art reload (monitors after the cutscene
  may render corrupt); add a fade-to-level-music path instead of `playMusic`.

- [ ] **B5. Drive the button press from proximity, not a bespoke handshake.**
  ROM button `loc_65C04` (:133936) presses when the tracked Knuckles object
  (`_unkFAA4`) enters `word_65C48` = dx,dy ∈ [-$18,$30) (`Check_InMyRange`). Engine
  reproduces the range check but adds `hasReachedButtonImpact()` (set on the second
  landing) as an extra gate — a workaround for imprecise bounce trajectories. After A3
  lands (terrain-probe landings), verify Knuckles' second bounce actually lands in
  range and remove the handshake; the range check alone must decide the press frame.

- [ ] **B6. Missing button subtypes and pressed-state persistence.**
  ROM `off_65C40` dispatches subtypes 0/2/4/6: 0 = `loc_65C56` (unlock controls, raise
  stored/target max-Y to $1000, gradual `Child6_IncLevY`), 2 = `loc_65C72`
  (`st Level_trigger_array+8`), 4 = water/flash (implemented), 6 = vacuum tubes
  (implemented). Check CNZ act-2 object placement for Sonic-path uses of subtypes 0/2
  and implement any that are placed. Also: ROM clears the button's `respawn_addr` entry
  so a pressed button never respawns un-pressed, and sets `_unkFAA9` on any press —
  audit engine respawn behavior for `Cnz2CutsceneButtonInstance` (pressed state must
  survive despawn/respawn and rewind) and find/model `_unkFAA9` consumers.

- [ ] **B7. Layout mutation on the vacuum-tube button (subtype 6).**
  ROM `loc_65CAC` (:133957) also rewrites four level-layout bytes (chunk column at
  `Level_layout_main+$40 → +$8E`: $14,$0F,$0F,$88 down successive rows) with screen
  shake, opening the tube. Engine `pressVacuumTubeButton()` only shakes and spawns the
  two `Obj_CNZVacuumTube` controllers. Add the layout edit via
  `ZoneLayoutMutationPipeline` (required by `TestNoDirectMapMutationsInGameplay`).

## C. CNZ2B second encounter (teleporter exit / drop to boss)

- [ ] **C1. Music transitions: fade, don't cut.**
  ROM init runs `sub_65DD6` (:134091) = spawn `Obj_Song_Fade_Transition` with
  `mus_Knuckles` (fade out current, then Knuckles theme); the exit (`loc_625E2` :129339)
  spawns `Obj_Song_Fade_ToLevelMusic`. Engine plays both tracks immediately. Wire both
  ends through the engine's fade path (same work item as B4's fade helper).

- [ ] **C2. Animate the post-jump laugh wait.**
  After the second landing the shared bounce routine (`loc_620D8` :128891) switches to
  `byte_666B9` ($1C/$1C/$1D laugh-stand) which plays during the $7F-frame wait
  (`loc_625BE` Animate_Raw + Obj_Wait). Engine `routinePostJumpWait` freezes on the last
  jump frame. Add the laugh animation (same script as B1).

- [ ] **C3. Reload `Pal_CNZ` into palette line 1 at walk-off end.**
  ROM `loc_625E2`: when Knuckles leaves the screen it clears `Player_1.object_control`,
  loads `Pal_CNZ` via `PalLoad_Line1`, and starts the music fade. Engine never restores
  line 1 here (it restored early in CNZ2A instead — see B3). After B3 lands, this is the
  canonical restore point.

- [ ] **C4. Verify forced-input and control-lock sequencing against `loc_62528`.**
  ROM init clears `Ctrl_1_logical`, sets `Ctrl_1_locked`, and writes
  `object_control = $80`; the forced-right walk uses held-right on the logical pad until
  x ≥ $4760; the final phase forces held-left until `Camera_Y + $160 < knuckles.y_pos`,
  then clears `Ctrl_1_locked`. Engine approximates with
  `setControlLocked`/`setForcedInputMask`; audit ordering (engine briefly unlocks in
  `routineExitRight` before re-locking in FORCE_PLAYER_LEFT — ROM keeps `Ctrl_1_locked`
  set through the whole sequence) and confirm the drop-shaft exit condition actually
  triggers in-engine (it depends on camera Y vs the object's spawn Y; instrument once,
  record expected values in the test).

## D. End boss — `Obj_CNZEndBoss` core loop

- [ ] **D1. Add the missing post-field wind-down phase.**
  ROM `loc_6E650` (:145964): after the $FF-frame magnetic-pull phase, bit2 clears and
  bit7 sets for another $FF frames (`loc_6E66C`) while the arms decelerate, and only
  then routine $C (descend) starts. Engine goes CHARGE → DESCEND immediately, so the
  whole cycle is ~4 s short and arm deceleration is mis-keyed to DESCEND/ASCEND.
  Add a WIND_DOWN routine between CHARGE and DESCEND; key `CnzEndBossArmChild`
  deceleration to it (ROM arm `loc_6EA40`/`loc_6EA70` decrement speed every $40 frames
  while parent bit7 is set).

- [ ] **D2. Magnet drop must move horizontally toward the player.**
  ROM magnet child `loc_6E87E` (:146142): at drop start it sets x_vel = ±$100 toward the
  closest player (`Find_SonicTails`) and `MoveSprite` keeps X motion through the fall
  and floor bounces (bounce: y_vel = -y_vel/2 while ≥ $80, `sfx_FloorThump` each
  impact). Engine `CnzEndBossMagnetChild.beginDrop` falls straight down. Add the
  X velocity + moving bounces (gravity $38 is already correct).

- [ ] **D3. Magnet reattach happens at the bottom of the boss's descent.**
  ROM `loc_6E69C` (:145991): when the body reaches magnet_y - $14 it sets bit3; the
  magnet (`loc_6E920`) sees bit3, clears it and returns to follow mode — so the boss
  visibly picks the magnet up and carries it back up. Engine leaves the magnet landed
  through ASCEND and teleports it at cycle end (`resetForNextCycle`). Reattach at
  DESCEND-bottom instead.

- [ ] **D4. Magnet is a hazard whenever alive, not only while dropping.**
  ROM sets collision `$8B` from `ObjDat3_6ED9C` at child init and only clears it on
  parent defeat (`sub_6ED22`). Engine returns 0x8B only while `dropping` — the docked
  magnet under the boss is currently safe to touch. Make it always hazardous until
  defeat.

- [ ] **D5. Magnet head animation script.**
  ROM `byte_6EE1D` (:146736) multi-delay script: (4,0)(5,0)(4,0)(5,0)(4,4)(5,0)(4,9)
  loop, driven while parent bit3 set (`loc_6E8FE`), reset to frame 4 otherwise.
  Engine's `ANIMATION`/`animTimer` approximation drops the trailing (4,9) pair and
  gates on routine ≥ CHARGE. Port the script and the bit3 gating.

- [ ] **D6. Boss body sprite flicker during hit invulnerability.**
  ROM `sub_6EC9E` (:146574) sets status bit6 every frame of the $20-frame hit timer
  (sprite blinks) and restores `collision_flags` from `$25` when it ends. Engine does
  the palette flash (correct colors 9/10/11/14 on line 1, dark/bright by timer parity)
  but never blinks the sprite. Add alternate-frame render skip during
  `hitInvulnerabilityTimer`.

- [ ] **D7. Arm frame selection extras.**
  Engine `frameForAngle` matches `sub_6EBF0`/`byte_6EC14`. Missing: the ROM skips the
  table lookup while `mapping_frame == 3`, and arms also run the `byte_6EE0E`
  multi-delay script (frames 1/3) in some routines (`loc_6E9E2`, `loc_6EA26`,
  `loc_6EA5C`). Low priority visual polish; port the script + frame-3 guard.

- [ ] **D8. Spawn children at routine-0 init.**
  ROM spawns ship + magnet + 4 arms in `loc_6E4F2` (first dispatched routine after the
  camera converge completes). Engine spawns magnet/arms in `updateCameraLock` completion
  and never spawns a ship child (see E1). Align spawn timing when reworking A2/E1.

## E. End boss — defeat sequence and Robotnik ship

- [ ] **E1. Implement the Robotnik ship child (`Obj_RobotnikShip4`, subtype 9).**
  ROM `Child1_MakeRoboShip4` (:136700, offset (0,-8)) → `Obj_RobotnikShip4` (:136485):
  init takes `mapping_frame = subtype = 9` (engine's inline draw uses frame 5 — wrong
  frame) and spawns the Robotnik head child (`Child1_MakeRoboHead`, animates
  `AniRaw_RobotnikHead`, shows the hurt frame while the boss's status bit6 flicker is
  set). On parent defeated (status bit7): spawns `Child6_CreateBossExplosion` subtype 4
  — this is the ROM source of the defeat explosion sequence. On parent bit4: damaged
  frame $A, then rises until `Camera_Y + $40 ≥ y`, then flies right at $300 with a
  `Child1_MakeRoboShipFlame` for $100 frames and clears `Boss_flag`. Replace the static
  inline ship draw in `CnzEndBossInstance.appendRenderCommands` with a real child
  object (reuse/extend `HczEndBossRobotnikShip` if its behavior matches this shared
  routine set), and let it own the defeat explosions instead of the engine's ad-hoc
  `S3kBossExplosionController`.

- [ ] **E2. Defeat timing/music: follow `Wait_FadeToLevelMusic`.**
  ROM `loc_6ECF6` → `Wait_FadeToLevelMusic` (:179656): boss keeps drawing for $3F
  frames (boss music still playing), then hides (render bit7 clear), sets `$2E = 2*60-1`,
  spawns `Obj_Song_Fade_ToLevelMusic`, and jumps to `loc_6E6C6`. `BossDefeated` adds
  100→1000 points and stops the HUD timer (engine ✓ score, verify HUD-timer stop).
  Engine calls `fadeOutMusic()` immediately at defeat. Fix ordering: no fade at hit-0;
  fade spawns after the $3F wait; body stops rendering from that point.

- [ ] **E3. Capsule spawns ~2*60 frames after the debris burst, not immediately.**
  ROM chain: `$3F` wait → `loc_6E6C6` (bit4 set, debris children spawned, Obj_Wait with
  the 2*60-1 timer) → `loc_6E6E4` (clear `Boss_flag`, widen camera, spawn
  `Obj_EggCapsule` at ($4990,$2E0)). Engine `applyDefeatHandoff` runs everything at
  $3F. Insert the second wait.

- [ ] **E4. Correct defeat debris (body halves).**
  ROM `ChildObjDat_6EE00` → `loc_6EBAC` (:146438): two pieces at (∓$14,0), frames
  $B/$C, `Obj_FlickerMove` with `Set_IndexedVelocity` index 0 → velocities
  (-$100,-$100) and (+$100,-$100), no gravity, drawn every other frame, deleted
  off-screen. Engine `CnzEndBossDefeatDebrisChild` uses ±$300/-$200 with a life
  counter. Fix velocities/lifetime semantics (add a shared FlickerMove helper — several
  S3K bosses need it).

- [ ] **E5. Arms and magnet convert to debris on defeat.**
  ROM bit4 handling: arms (`sub_6ED4C` :146645) become frame 1, non-colliding,
  `Obj_FlickerMove` with indexed velocity 0 (per-arm subtype → different vectors from
  `Obj_VelocityIndex`); the magnet (`sub_6ED22` :146623) deletes and spawns two sparks
  (`ChildObjDat_6EDF2` → `loc_6E936`, frame $A, indexed velocity 8 → (∓$200,-$200),
  h-flip on the second). Engine children just vanish via `expireDynamic`. Implement the
  scatter.

- [ ] **E6. Post-defeat camera bounds use the boss-lock min-X base ($4760).**
  ROM: stored-max-X = `_unkFAB4` + $190 = $48F0 (capsule area, `loc_6E6E4`), later
  `_unkFAB4` + $310 = $4A70 (cannon area, `loc_6E724`); stored-min-Y = $200. Engine
  bases both on `savedCameraMaxX` (the pre-boss camera max captured at the gate), which
  yields different absolute bounds. Hard-code the base to the boss lock min-X (0x4760)
  or thread the equivalent of `_unkFAB4` through the shared camera-lock helper from A2.

## F. Post-boss capsule → cannon → ICZ1

- [ ] **F1. Deterministic cannon launch: wait for angle $12.**
  ROM `loc_6E7E4` (:146087): after the $BF lock timer the boss waits until the cannon's
  `angle == $12` before forcing A/B/C on the logical pad — this makes the launch vector
  (from `sub2_mapframe` at that angle) deterministic (straight up). Engine
  `updatePostDefeatSequence` fires `triggerEndSequenceLaunch` as soon as the timer
  expires, launching in whatever direction the chamber happens to point. Add the angle
  gate (expose the spin angle from `CnzCannonInstance`) and prefer routing the launch
  through the forced-input path so the cannon's own button logic fires.

- [ ] **F2. Arm the end sequence at capture, not launch-ready.**
  ROM `loc_6E7B6` (:146074) proceeds when the cannon's per-player state byte `$30`
  becomes 1 (player captured, pull-down starting); the camera max-Y drop to $200,
  control lock and $BF timer all run during the pull-in/spin. Engine waits for
  `isEndSequenceLaunchReady()` (= fully pulled in). Expose the capture state and align.

- [ ] **F3. Queue the explosion art when the cannon spawns.**
  ROM `loc_6E778` queues `ArtKosM_BadnikExplosion` to `ArtTile_Explosion` alongside the
  cannon spawn at ($4B20,$2A8) on player x ≥ $4A30 (engine positions/threshold ✓).
  Verify the engine's EXPLOSION art key is actually resident in CNZ2 at this point (the
  launch puffs render from it); queue/load if not.

- [ ] **F4. Verify the ICZ handoff condition and player-state neutralization.**
  ROM `loc_6E80C`: transition when `Camera_Y + $20 ≥ Player_1.y_pos` →
  `StartNewLevel($500)` (ICZ1). Engine condition matches; `preparePlayersForIczFade`
  hidden/locked state is an engine-ism — verify against the actual ROM handoff (ROM
  does nothing special to the player besides the cannon launch state) and trim anything
  not needed by the engine's transition pipeline.

## G. Validation / regression

- [ ] **G1. Trace coverage.** Record/refresh a stable-retro reference trace covering:
  approach → CNZ2A cutscene → water rise/lights-out → teleporter → CNZ2B → shaft drop →
  boss fight (≥2 full attack cycles + all 8 hits) → capsule → cannon → ICZ transition.
  Wire into the `*TraceReplay` suite; update `docs/TRACE_FRONTIER_LOG.md` per policy.
  Use `trace-replay-bug-fixing` skill rules (trace data is comparison-only).
- [ ] **G2. Visual validation.** Run `s3k-zone-validate`-style screenshot comparisons at:
  cutscene A laugh/button press, lights-out palette, boss entry, magnet drop, field-pull,
  defeat debris + ship escape, capsule, cannon launch.
- [ ] **G3. Unit/headless tests** for each behavioral fix: camera-converge helper (A2),
  laugh-frame selection (B1), flash restore variants (existing), magnet drop X motion +
  reattach (D2/D3), wind-down phase timing (D1), defeat timeline frame counts (E2/E3),
  debris velocities (E4/E5), cannon angle gate (F1). Keep
  `TestRewindCoverageGuard`/`TestStaticStateRewindCoverageGuard` green — new children
  (ship, flame, sparks, flicker debris) need rewind recreate paths like the existing
  `CnzEndBossRewindLinks` pattern.
- [ ] **G4. Docs.** Record any deliberately retained divergences (e.g. B3 line-1 palette
  choice) in `docs/S3K_KNOWN_DISCREPANCIES.md`; commit trailers per branch policy.
