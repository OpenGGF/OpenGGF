# S3K Super Emeralds — Remediation Plan

Full review of `feature/ai-s3k-super-emeralds` (8 commits vs `origin/next`,
merge base `beb7b64c1`) against `sonic3k.asm`. This plan is the work order for
bringing the branch to disassembly parity. Execute with subagents per wave;
waves are ordered by dependency. Every finding below was verified against the
disassembly at the cited labels/lines unless marked **VERIFY**.

## Ground rules for the executing agent

- Source of truth: `docs/skdisasm/sonic3k.asm`. **The `docs/*disasm` symlinks
  currently point at a nonexistent local path.** If broken,
  clone `https://github.com/sonicretro/skdisasm` somewhere local and fix or
  bypass the symlink before starting. Cite the label (e.g. `loc_90B32`) in
  commit messages/comments where the repo's style does.
- Build on JDK 21: `export JAVA_HOME=<path-to-jdk-21>` before any `mvn`
  (system default may be a newer JDK and produces phantom failures).
- S3K ROM for tests: `-Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"`
  (file in repo root). S1/S2 ROMs also exist in the root for full sweeps
  (`-Dsonic1.rom.path=`, `-Dsonic2.rom.path=`).
- Never branch on zone name/trace; per-game differences use owners
  (hard rules in CLAUDE.md). Keep pedestal/controller logic driven by the
  four-state array semantics, not by route.
- One branch: continue on `feature/ai-s3k-super-emeralds`. Commit trailers per
  `.githooks/run-policy`; `Changelog: updated` for fix commits touching
  `src/main/`.
- Repo hygiene: `docs/plans/` (top-level, untracked) violates artifact
  placement — move anything useful into `docs/architecture/plans/` and delete
  the directory. Delete stray `config.yaml.corrupt` if unowned.

## Key ROM references (sonic3k.asm)

| Subsystem | Labels / lines |
|---|---|
| Sanctuary controller `Obj_HPZSSEntryControl` | `loc_90962`–`loc_90CA0` (~197830–198140) |
| Pedestal `Obj_HPZSuperEmerald` | 197642–197830 (`off_9079A`, `sub_907FA`, `sub_90832`, `sub_9084E`, `loc_90880`, `loc_9089E`, `loc_908DE`, `loc_90926`) |
| Falling crystal | `loc_90CA2`–`loc_90D72` |
| Orbit ceremony parent/children | `loc_90D78`–`loc_90F90` (`sub_90F0A`, `byte_90F64/6C`, `word_90F74/90`) |
| Master Emerald `Obj_HPZMasterEmerald` | 197575–197640 + `loc_90734` glow child + `off_914CE` |
| Teleporter `Obj_SSZHPZTeleporter` | 90960–91240, slope `byte_466E8` (92626) |
| Entry ring `Obj_SSEntryRing` / flash / `SSEntry_CheckLevel` | 128252–128520 |
| `Save_Level_Data2` | 61760–61780 |
| Special-stage award (`bset #0` state, counts) | 12660–12700 (`loc_9CCE`) |
| HPZ results-hub return (post-special-stage) | 63036–63290 (`SpecialStage_Results`, `loc_2E150`+, `loc_2E226`), pedestal `_unkFAC0` at 197779, transform choreography 63860–64300 (`loc_2E9D8`, `loc_2EAA6`, `loc_2ECD0`, `loc_2EDAE`), `HPZS_ScreenInit` 120842 |
| Transform gates | `Sonic_CheckTransform` 23485+, `Tails_Transform` 28702+, Knuckles ~32577+ |
| Hyper dash | `Sonic_HyperDash` 23543–23600 (velocity table `Sonic_HyperDash_Velocities`) |
| Screen nuke | `HyperAttackTouchResponse` 21337–21437 (`HyperTouch_Special` writes `ori.b #3`, Knuckles-alone-only reposition) |
| Hyper stars | `Obj_HyperSonic_Stars` 34485–34595 |
| Trail | `Obj_HyperSonicKnux_Trail` 35393–35430 |
| Super Flickies | `Obj_SuperTailsBirds` 35035–35392 |
| Hyper Knuckles quake | `Knuckles_Gliding_HitWall` 30802–30858, `ShakeScreen_BG` 104248–104266 |
| Hyper flash (VInt) | `VInt_8` 723–748 |
| Zone $17 music playlist | 7498–7523 (`mus_LRZ2` — engine already correct) |

---

## Wave 1 — Confirmed behavioural bugs (independent, parallelizable)

### 1.1 Sanctuary controller pose table indexed by wrong key
`HPZSSEntryControlObjectInstance.applyConversionPlayerMappings(conversionSpawnCursor)`
passes the conversion **ordinal**; ROM `loc_90B32` indexes `byte_90BBC` by the
**emerald subtype** (`$3C(a0)`, the value from `_unkFAB0`). Since conversion
order is `[5,3,1,0,2,4,6]`, poses are wrong whenever order ≠ identity.
Fix: index `conversionPoseForTest` by subtype (the table content already
matches ROM `{flip, variantOffset/4}` per subtype 0–6; final pan uses the
subtype-2 pose `{0,0}` which the current `applyFinalPlayerMappings` mimics).

### 1.2 Pedestal render priority keyed by subtype instead of state
`HPZSuperEmeraldObjectInstance.getPriorityBucket()` returns
`subtype == 1 || subtype == 2 ? 1 : 4`. ROM `sub_90832`/`word_9083E` selects
priority by **emerald state**: states 1/2 → `$80` (high), states 0/3 → `$200`.
Fix: priority from `progression.state(subtype)`.

### 1.3 Pedestal Knuckles palette line, subtype 3
`KNUCKLES_COMPLETED_PALETTE_LINES[3]` is `2`; ROM `word_90816` pair for
subtype 3 is `(normal 0, knuckles 1)`. Change to `1`.
(Ceremony tables `PALETTES_SONIC/KNUCKLES` in
`HPZSanctuarySmallEmeraldCeremonyObjectInstance` were verified correct.)

### 1.4 Ceremony orbits all seven emeralds regardless of state
ROM `sub_90F0A`: a ceremony child whose `Collected_emeralds_array[i]` is not
exactly 1 sets its parent arrival bit and deletes at spawn — only state-1
emeralds fly. The engine ceremony orbits and departs all seven. Fix: pass the
controller's `S3kEmeraldProgression` into the ceremony (constructor), give each
child a `participating` flag; non-participants count as arrived immediately and
never render. Departure (`loc_90E86`) is per-child at its ROM `word_90F74`
velocity until offscreen (`render_flags` check) — replace the fixed `0x100`
frame timer with per-child offscreen deletion, participants only.

### 1.5 Ceremony children must blink
ROM `loc_90E3E` draws children only on even `V_int_run_count` frames. The
falling crystal already blinks; apply the same `(frameCounter & 1) == 0` gate
to the ceremony children's render.

### 1.6 Conversion pedestal reveal is early; per-conversion save missing
ROM `loc_90ED0` (crystal midpoint, `$2E==$20`): converts `1→2`, spawns the
pedestal **hidden behind bit7 wait** (`loc_90880` — until the crystal's raw
animation ends it draws the colored mini frame, then reveals gray `$1E` when
the crystal's end routine `loc_90D5E` sets bit5), and calls
`SaveGame_SpecialStage`. Engine `onFallingCrystalMidpoint` spawns a fully gray
pedestal ~13+ frames early and never saves. Fix: add a `revealPending` state to
`HPZSuperEmeraldObjectInstance` (spawned by conversion: draw colored subtype
frame until revealed; reveal on `onFallingCrystalAnimationComplete(subtype)`),
and trigger the engine's save-game path (same one used after special stages)
at the midpoint conversion.

### 1.7 Crystal midpoint player mapping: Tails y += 4
ROM `sub_90EAE` adds 4 to `y_pos` for character_id 1 (Tails) before setting
mapping frame/anim 5. `applyMidpointPlayerMappings` omits it. Use
`NativePositionOps` for the write.

### 1.8 Pedestal selection: player lock + missing state-3 presentation
- ROM `loc_908DE`: when selection starts, `object_control = $81`, `anim = 5`
  on Player 1 for the 15-frame delay. Engine publishes the request with no
  player lock — add it (ObjectControlState bit7 + movement suppression) and
  clear appropriately on transition.
- ROM `loc_9089E`/`loc_908BE`: a state-3 pedestal flickers frame 7
  (`ArtTile_HPZEmeraldMisc` line 0) on odd V-int frames, and displays gray
  frame `$1E` while `_unkFAC0` (bit7 | stage) matches its subtype
  (the just-completed emerald during the return choreography). Engine renders a
  static colored frame. Implement the flicker now; `_unkFAC0` display comes
  with task 3.1.

### 1.9 Master Emerald completed-state glow child missing; gates wrong
- ROM spawns a glow child (`loc_90734`, `word_90FB8`: line 2, priority `$180`,
  frames from `RawAni_90768` indexed by palette-script step `$3B/2`) when all
  seven states are 3 **at object init**.
- Rotation script runs only while `_unkFAC1` (return-choreography-active flag)
  is clear; incomplete fixed colors `$6A0/$660` written only while visible.
- Engine `HPZMasterEmeraldObjectInstance` re-evaluates completion every frame,
  has no glow child, no `_unkFAC1` gate. Fix: latch completion at spawn, add
  the glow child (align its frame to the rotation step), model the
  choreography-active gate as a sanctuary-runtime flag.

### 1.10 Dead readiness timers contradict the design
`HPZSSEntryControlObjectInstance.teleporterReadyTimer` (decremented, never
read) and `SSZHPZTeleporterObjectInstance.buildTimer/ready` (unused by the
exit gate) are dead state that is also rewind-captured. The design explicitly
says there is no construction/readiness timer. Delete both.

### 1.11 Hyper Sonic stars: sin/cos axes swapped; no rewind capture
- ROM `loc_1941C`: `GetSineCosine` returns d0 = sine, d1 = cosine;
  `x_vel = sine << 3`, `y_vel = cosine << 3`. Engine `updateChild` uses
  `cosHex` for X and `sinHex` for Y. The Flicky flock correctly uses `sinHex`
  for X (`asr #3`) — make the stars consistent with the flock/ROM after
  confirming `TrigLookupTable.sinHex/cosHex` map to ROM d0/d1.
- The stars' orbit state (angles, timers, frames, accumulators, spark state)
  has no `RewindExtra` — rewinding resets the effect. Add capture/restore.

### 1.12 Super Flickies: spawn/flee X offset, reverse-gravity sign, scan cursor
- ROM init and `Obj_SuperTailsBirds_FlyAway` use `player - $C0` on **both**
  axes. Engine constructor and `flyingAway` destination only subtract on Y.
- Reverse gravity orbit: ROM computes `y - $20 (+$40 if reversed) + cos>>4` —
  the cosine term is added **after** the flip. Engine negates the term
  (`0x20 - yOffset`); should be `0x20 + yOffset`.
- Target scan: ROM `_unkF66C` advances one list entry per **scan call**
  (before the eligibility walk), scans only from the cursor to the list end,
  and resets to 0 when it passes the entry count. Engine advances only on
  success/full-failure and wraps with `floorMod`. Align to ROM.
- ROM hit path sets p2 `anim = 2` + `Status_InAir` only — drop the extra
  `setRolling(true)` here and in
  `ObjectTouchResponseController.applyPoweredScreenAttack` (both added it).

### 1.13 Hyper dash input decoding
ROM `Sonic_HyperDash` uses the 10-entry velocity table indexed by the raw
D-pad mask, with masks summing ≥ `$B` treated as no-input (dash forward) and
the in-table invalid combos (up+down = 3, left+up+down = 7) yielding **zero**
velocities. Engine's `hyperDash()` decodes axes independently, so e.g.
up+down+left dashes left instead of freezing. Replicate the table + `$B`
threshold exactly. (Speeds `±$800`, `ground_vel = x` component ✓ already.)

### 1.14 Hyper flash palette shape
ROM `VInt_8`: colors 0–31 white (`$EEE`), color 32 black, colors 33–63 white —
i.e. only line-2 color 0 stays black. Engine `buildHyperFlashUpload` blackens
color 0 of **every** line. Match the ROM layout (and confirm lines 0/1 color 0
are white).

### 1.15 Entry ring: HPZ entry starpost/checkpoint semantics
ROM `loc_618AC`: entering the sanctuary sets `Special_bonus_entry_flag = 2`,
**clears `Last_star_post_hit`**, sets `Restart_level_flag`; the origin
checkpoint survives only in `Saved2`. Engine
`S3kBigRingTransitionIntent.complete()` calls
`checkpoint.restoreFromSaved(...)` with the checkpoint's own values before
requesting HPZ — the HPZ session must instead start with **no active
checkpoint**, and the origin checkpoint state must live only in
`BigRingReturnState` for the return restore. Rework: capture checkpoint fields
into `BigRingReturnState` (already done), then *clear* the live checkpoint
state before `requestZoneAndAct(ZONE_HPZ, 1)`.

### 1.16 BigRingReturnState missing Saved2 fields
`Save_Level_Data2` also saves: `Timer` (level time), `Extra_life_flags`,
`status_secondary`, `Apparent_zone_and_act`, `Water_full_screen_flag`. The
record has none of these; on sanctuary exit the origin level's timer restarts
and ring-based extra-life thresholds re-arm. Add the fields, capture them in
`S3kBigRingTransitionIntent.complete()`, restore them in
`BigRingReturnState.restoreToPlayer`/`GameLoop.restoreBigRingReturn`.

### 1.17 Super-ring palette script hardcoded (hard-rule violation)
`Sonic3kSSEntryRingObjectInstance.SUPER_RING_PALETTE_STEPS` /
`NORMAL_RING_PALETTE_WORDS` are hardcoded int arrays. Hard rule 1: runtime
asset bytes come from the ROM. Load `PalSPtr_SSEntry` / `PalSPtr_SSEntry2`
script data via verified ROM addresses (add to `Sonic3kConstants`, verify with
`RomOffsetFinder --game s3k`), and drive the cadence from the script bytes
(verify the current 3/2 delays and the target CRAM indices {5,6,15} of line 1
against `Run_PalRotationScript`).

## Wave 2 — Powered-form parity and revert fades

### 2.1 Non-Sonic revert palette fade missing
`Sonic3kConstants.PAL_CYCLE_SUPER_KNUCKLES_REVERT_ADDR` is defined but unused;
`onRevertStarted` short-circuits to instant palette restore for
Knuckles/Tails/Hyper. ROM `SuperHyper_PalCycle` (~4640–4800) runs a status-2
fade for every character (Knuckles has a dedicated revert table). Implement
per-character revert fades from ROM data; keep the saved-normal-palette
restore as the terminal step.

### 2.2 Verify every powered palette-cycle constant against the ROM
Frame counts and reload timers currently in
`Sonic3kSuperStateController.configurePaletteForActiveTier` (Hyper Sonic:
12 frames/timer 4; Super Tails: 6 frames/timer $B; Super Knuckles:
10 frames/timer 2/wrap $E) and the CRAM index sets ({2,3,4} vs Tails {8,9,11})
were **not verified** in this review. Check each against
`PalCycle_HyperSonic` / `PalCycle_SuperTails` / `PalCycle_SuperKnuckles` and
the palette-write destinations in `SuperHyper_PalCycle`; also verify the four
`PAL_CYCLE_*_ADDR` constants with `RomOffsetFinder --game s3k`.

### 2.3 Verify new ROM address constants
All new `Sonic3kConstants` entries (HPZ layout/art/blocks/chunks/palettes,
`ART_NEM_HPZ_*`, `ART_KOSM_*`, `MAP_HPZ_*`, `MAP_HYPER_SONIC_STARS_ADDR`,
`MAP_SUPER_TAILS_BIRDS_ADDR`, art-tile bases) via
`RomOffsetFinder --game s3k` (labels: `Layout_HPZ`, `ArtKosM_HPZ_Primary/…`,
`HPZ_128x128_*`, `HPZ_16x16_*`, `Pal_HPZIntro`, `Pal_HPZ`,
`ArtNem_HPZEmeraldMisc`, `ArtNem_HPZGrayEmerald`, `ArtKosM_Teleporter`,
`ArtKosM_HPZSmallEmeralds`, `Map_HPZEmeraldMisc`, `Map_HPZChaosEmeralds`,
`ArtKosM_HyperSonicStars`, `Map_HyperSonicStars`, `ArtKosM_SuperTailsBirds`,
`Map_SuperTails_Birds`). Also verify HPZ level bounds/start/camera against
`LevelSizes`/start-location tables for slot `$1701` (profile hardcodes
camera `$15A0/$240`, bounds `[1500,1640,320,320]`).

### 2.4 Character identification by class-name sniffing
Sanctuary code decides Sonic/Tails/Knuckles via
`getClass().getSimpleName().contains("Tails")` etc. (controller, ceremony,
pedestal). Replace with the engine's character-code/type query used elsewhere
(see how `Sonic3kPlayerArt`/`resolveActiveMainCharacterCode` do it).

### 2.5 `TouchCategory.BOSS` result plumbing in flock/screen attack — VERIFY
The Flicky/boss hit path (`.enemy` boss branch: store flags in `$25`, damage
source = Player_2, `collision_flags = 0`, `collision_property--`, `bset #7`
when zero) is approximated via `onPlayerAttack(..., TouchCategory.BOSS)`.
Write a focused test against a real S3K boss instance to confirm one hit is
deducted and the boss's invulnerability window behaves as on hardware.

## Wave 3 — The post-special-stage return hub (largest missing piece)

### 3.1 Model the ROM results-hub choreography
After an HPZ special stage, ROM loads HPZ directly inside
`GameMode_SpecialStageResults` (63190–63290): camera X from `word_2E398[stage]`
(engine must place camera/player per returned stage), `HPZ_special_stage_completed`
set (success **and** failure), `_unkFAC0 = stage | $80` and `_unkFAC1` set only
on success. Then the results object (63860–64300) runs the emerald
transformation: the pedestal shows gray while `_unkFAC0` matches
(`loc_9089E`), a `Map_Invincibility` twin-star effect orbits and shrinks onto
the pedestal (`loc_2ECD0`–`loc_2EDCA`), `Emerald_flicker_flag` cycles 0–2, the
transform finishes with `clr _unkFAC0`, `sfx_Perfect`, `clr _unkFAC1`
(`loc_2E9D8`). Engine currently awards the emerald and reloads HPZ with a
`reentry` flag and no choreography. Implement:
- per-stage return camera X (extract `word_2E398` values from the ROM),
- the success transform choreography (stars object + pedestal gray-until-done
  + `sfx_Perfect`), gated behind the sanctuary runtime state so failure skips
  straight to selectable pedestals,
- `_unkFAC1`-equivalent gate consumed by the Master Emerald (task 1.9).

### 3.2 Exit + re-selection in the re-entry session
Confirm engine parity for: re-entered sanctuary allows standing on another
gray pedestal to launch its stage (ROM pedestal logic is self-contained ✓
engine), and teleporter exit only when no state-1/2 remains (engine
`exitEligible()` is semantically equivalent to ROM bit5 — keep, but add a
focused test for: mixed states block exit; all-super allows exit; zero-emerald
fresh entry allows exit immediately after intro).

### 3.3 `Sonic3kSpecialStageManager` reward ownership — keep-green check
The new `publishEmeraldReward` guard requires state == GRAY_SUPER before
awarding. Verify the sanctuary → special stage → return integration end to end
with `TestGameLoopSpecialStageEntryRequest`, `TestS3kSaveSnapshotProvider`,
and a new integration test: pedestal N → forced stage N → success awards only
N (2→3, `Super_emerald_count` semantics via `getCollectedSuperEmeraldIndices`),
failure leaves N at 2; both return to HPZ.

## Wave 4 — Failing tests and guards (branch-caused; fix, don't baseline away)

Run: `mvn test` with all three ROM props. Branch introduced these failures
(all pass on `origin/next`; re-verify each after Waves 1–3):

1. `TestS3kBadnikChildGraphRewind` — Ribot/SnaleBlaster spawn zero children.
   Likely broken by the `ObjectManager`/`DefaultObjectServices`/
   `PerObjectRewindSnapshot` changes on this branch. Investigate and fix the
   regression — badnik child creation must not depend on the new powered-
   attack read view.
2. `TestS3kLbz1KnucklesSequenceHeadless` — exit no longer clears native P2
   `object_control`. Almost certainly the sanctuary `ObjectControlState`
   changes (`nativeBit7FullControl`, `setObjectControlAllowsCpu`) leaking into
   the shared release path. Fix the shared semantics, not the test.
3. `TestScalarOnlyCodecDeletion` — `OrbinautOrbInstance` became unprobed
   (constructor-probe list). Caused by harness/graph changes on the branch;
   restore probeability or register the proper recreate path.
4. `TestRewindCoverageGuard` — new gap key
   `Flybot767BadnikInstance#finalScalar#layoutWaitUsesRetainedRenderFlag`
   (fallout of `AbstractS3kBadnikInstance` edits). Fix coverage, no baseline
   entry.
5. `TestRemainingRewindTailInventory` / `TestParentDependentGraphCoverageGuard`
   / `TestRewindArchitectureGuard` — inventory/annotation baselines must be
   updated **with** real coverage: new classes
   (`HyperSonicStarsObjectInstance`, `SuperTailsFlickyFlockObjectInstance`,
   `HPZ*` objects) need probe constructors or graph-test rows plus focused
   graph tests; `@RewindTransient` on the stars' `owner` needs triage or
   replacement with an `ObjectRefId` relink like the crystal's `parentRef`.
6. `TestObjectPhysicsStandardizationGuard` — two new violations
   (`Lbz2RobotnikShipInstance` touch-profile hook count,
   `LbzCupElevatorInstance` `setCentreYPreserveSubpixel`). These are guard
   count baselines disturbed by branch changes; route the new code through the
   approved helpers instead of growing the lists.
7. `TestPlayableRuntimeAccessGuard` — `PlayableSpriteMovement` now references
   `com.openggf.game.sonic3k.Sonic3kSuperStateController` directly. Move the
   dash-effects trigger behind the abstract
   `SuperStateController` API (it already has `triggerPoweredWallImpact`; add
   e.g. `onPoweredSecondaryAbility(objectManager)` and override in S3K).
8. `TestPerGameRuleArchitectureGuard` — `PlayerMovementRules` grew 22→23;
   either relocate the new rule to a better owner or update the frozen limit
   with an explicit architecture-review note.
9. `TestCollisionSystemAirLanding` (bubble-shield re-arm) and
   `TestPlayableSpriteMovement#s3kHurtUsesLiveRadiusDelta...` (832 vs 827) —
   regressions from the `PlayableSpriteMovement`/`AbstractPlayableSprite`
   edits (history array + glide/land changes). Bisect the branch's movement
   diff; the 5-pixel delta suggests the new `artTileAttributeHistory`
   recording or the glide-wall changes disturbed an existing code path.

## Wave 5 — Final verification

```bash
export JAVA_HOME=<path-to-jdk-21>
mvn "-Dtest=TestHpzSanctuaryObjects,TestS3kEmeraldProgression,TestS3kSanctuaryRuntimeState,TestSonic3kSSEntryRingFormation,TestSonic3kSuperEmeraldConversion,TestSuperTailsFlickyFlockRuntime,TestPoweredScreenAttack,TestSonic3kSuperStateRewind,TestHyperSonicStarsObjectInstance,TestS3kHpzSanctuaryHeadless" "-Ds3k.rom.path=..." test
mvn "-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSonic3kPlcArtRegistry" "-Ds3k.rom.path=..." test
mvn "-Dtest=TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard,TestGameStateManager,TestGameStateRewindSnapshot" test
mvn test   # full sweep with all three ROM props — zero branch-caused failures
mvn package
```

Update every focused test that encoded the buggy behaviour (pose-by-ordinal,
all-seven ceremony, priority-by-subtype, etc.) to assert the ROM behaviour —
do not weaken assertions to make the old behaviour pass.

## Explicitly reviewed and found correct (don't churn)

- `S3kEmeraldProgression` four-state transitions and conversion-flag latch
  timing (`loc_90AF2` before first 1→2) ✓
- Conversion order `[5,3,1,0,2,4,6]`, camera targets `word_90B0C`, ±$10/frame
  pan with `< $10` stop, `$21F`/`$1F` signed-countdown timings, concurrent
  crystal countdown ✓
- Exit band hysteresis (arm ≥ `$18`, fire < 8) and teleporter-standing gate ✓
- Ring routing matrix (forced negative subtype; SK-side = ROM zones ≥ MHZ or
  FBZ; 50-ring path incl. double `sfx_BigRing`; collected-bit timing at GoSS) ✓
  (verify engine `Sonic3kZoneIds` MHZ/FBZ mapping once, then leave)
- Save payload (`emeraldStates` exact two-bit list + normalization on decode) ✓
- Flicky vertical-acceleration branch graph, `±$20/×4` steering, wrap
  handling, 120-frame search delay, hit box `+$C/<$18`, chain scoring
  100/200/500/1000/10000 shared with the normal badnik chain ✓
- Hyper Knuckles quake trigger (`ground_vel ≥ $480` unsigned, `sfx_Thump`,
  20-sample decreasing shake into `Camera_X_pos_copy`, screen nuke) ✓
- Trail history selection (3/5 frames by level-frame parity, delayed art-tile
  byte, live mapping/render flags) ✓
- `HyperTouch_Special` `ori #3` + Knuckles-alone reposition ✓ (minus the
  `setRolling` noted in 1.12)
- HPZ music `mus_LRZ2`, resource slot `$1701` (zone `$17` act 1), teleporter
  slope table and `SolidObjectTopSloped2` params ✓
- Pedestal positions `word_90860`, crystal targets `word_90CD4`, crystal
  timing (`$3F` landing, midpoint at `$20`, 13-frame raw script, shake 8,
  `sfx_BossLaser`) ✓

---

## Execution record

### Requirements

The goals, non-goals, constraints, ROM anchors, acceptance criteria, and ordered work
items are the plan above together with the parent design's
`Requirements` section. Every numbered remediation item remains open until its ROM-accurate
implementation and focused assertion are present; a currently green test does not count
when it encodes the behavior identified as incorrect above.

### Exploration Synthesis

- Sanctuary presentation is currently split across
  `S3kSanctuaryRuntimeState`, `LevelTransitionCoordinator`, `GameLoop`, and the
  `HPZ*ObjectInstance` graph. The result transition carries only a stage number, so it
  cannot distinguish successful choreography from failure. A typed, rewind-captured
  return context must cross that boundary before the pedestal, Master Emerald, and return
  stars can consume it.
- ROM `word_2E398` contains return camera X values
  `{15A0,1540,1600,1500,1640,14B0,1690,15A0}`. Successful returns alone set the
  `_unkFAC0`/`_unkFAC1` equivalents and create the eight-child twin-star contraction;
  failures return directly to selectable pedestals.
- The ceremony, pedestal, controller, Master Emerald, entry-ring transition, powered
  effects, and rewind gaps in Waves 1, 3, and 4 are present in the named production
  owners. The focused baseline on JDK 21 ran 997 Wave-4 tests with 8 failures and 1 error;
  several failures listed in the original review snapshot have since become green, but
  their remediation items still require behavior-focused verification.
- `PalSPtr_SSEntry` and `PalSPtr_SSEntry2` resolve to S&K-side ROM addresses
  `0x061C28` and `0x061CA4`. The runtime must parse their header, frame-minus-one
  delays, CRAM destinations, and repeat command instead of retaining Java palette arrays.
- Re-reading `SuperHyper_PalCycle_Revert` corrects task 2.1: Sonic, including Hyper
  Sonic, uses the timed reverse fade. Tails and Knuckles take
  `SuperHyper_PalCycle_RevertNotSonic` and apply one normal/revert ROM-table step.
  Implementation follows the disassembly rather than the inaccurate sentence in task 2.1.
- The locally supplied S3K ROM is a noncanonical variant (SHA-1
  `B711A909CCE238CA4AF3E517A2EDCA306228EFA5`, CRC32 `0C06AA82`). Its relevant
  powered-palette bytes match the cloned disassembly, but final reporting must not claim
  canonical-ROM acceptance.

Self-review: every implementation decision below is backed by the plan, current source,
focused test output, or the cloned `sonicretro/skdisasm` labels; the ROM-variant limitation
is explicit.

### Architecture Decision

- Durable emerald state remains in `S3kEmeraldProgression`; special-stage reward
  publication remains owned by `Sonic3kSpecialStageManager`.
- `LevelTransitionCoordinator` owns the additive, rewindable sanctuary return context
  (stage and success). Existing public accessors remain for Mod API compatibility.
- `S3kSanctuaryRuntimeState` owns presentation-only return choreography state, matching
  pedestal-gray/flicker state, and the Master Emerald rotation gate. It never awards an
  emerald.
- The HPZ controller owns and passes one shared runtime/progression instance to its
  children. Cross-object rewind references use `ObjectRefId`; gameplay state is not copied
  into unrelated object snapshots.
- Return stars reuse the ROM-backed invincibility art already registered under
  `ObjectArtKeys.INVINCIBILITY_STARS`. Ring palette scripts and all new runtime tables are
  read through the ROM pipeline.
- Playable native writes use `NativePositionOps`, control locks use
  `ObjectControlState`, and object destruction uses `ObjectLifetimeOps`.
- Powered abilities are reached through the abstract `SuperStateController` boundary;
  no S3K class dependency is added to shared playable movement.

Rollback is local to the feature branch: the additive transition context and object
presentation state can be reverted without save migration because durable two-bit emerald
storage is unchanged.

Self-review: ownership follows existing runtime boundaries, preserves Mod API signatures,
avoids zone/trace carve-outs, and keeps all runtime bytes ROM-owned.

### Feature Design

Behavior and edge cases are the numbered tasks above, with the task-2.1 correction in the
Exploration Synthesis. Acceptance tests must cover successful and failed return sessions,
all four pedestal states, exact-state ceremony participation, conversion reveal/save
timing, selection control lock/release, stage camera values, return-star completion,
Master Emerald latch/gate/glow, powered input/palette/effect tables, Saved2 restoration,
checkpoint clearing, rewind round trips, and the Wave-4 guards.

Self-review: each user-visible branch has a deterministic semantic input and a focused
assertion; no behavior depends on route, trace, or frame-number exceptions.

### Implementation Plan

1. Sanctuary owner: Waves 1.1–1.10, 2.4, and 3.1–3.3; focused HPZ runtime/object,
   transition, result, and integration tests.
2. Powered owner: Waves 1.11–1.14 and 2.1–2.5; focused stars, Flickies, dash, flash,
   palette-cycle, boss-touch, and rewind tests.
3. Transition/guard owner: Waves 1.15–1.17 and Wave 4; Saved2/checkpoint/palette-script
   tests first, then rewind and architecture guard repair.
4. Integration owner: reconcile shared files, verify all ROM constants, run Wave 5 with
   JDK 21 and all discovered ROM paths, update this execution record, and perform an
   independent end-to-end review.

Each owner must preserve concurrent edits, implement behavior test-first, and report
changed files plus exact verification commands.

Self-review: primary ownership is disjoint. Shared seams
(`LevelTransitionCoordinator`, `ObjectServices`, and movement/controller APIs) are
sequenced through the sanctuary/transition owners and reconciled before the full sweep.

### Integration Report

All remediation waves are implemented on `feature/ai-s3k-super-emeralds`.

- The sanctuary now models the ROM's exact pedestal states and render priorities,
  selection lock, conversion reveal/save point, seven-participant ceremony, player
  blink/departure, Master Emerald latch/rotation/glow, and timer-free sloped teleporter.
- A typed `SanctuaryReturnContext` carries the selected stage and result through the
  production transition boundary. Successful returns use the stage camera table and
  twin-star contraction before releasing the Master Emerald gate; failed returns become
  selectable immediately. Both paths return directly to HPZ without a title-screen
  detour.
- Powered-form parity includes corrected Hyper star axes and rewind state, Super Flicky
  spawn/flee/gravity/cursor behavior, the raw-mask Hyper dash table, the VInt flash
  palette shape, ROM-accurate palette-cycle/revert behavior, and the shared powered
  attack surface.
- Big-ring entry clears the live checkpoint while retaining the origin checkpoint only
  in Saved2-compatible return state. Level time, extra-life flags, secondary status,
  apparent zone/act, and full-screen water state now round-trip. Entry-ring palette
  scripts are parsed from ROM at `0x061C28` and `0x061CA4`.
- New HPZ, return-effect, Hyper-star, and Flicky graphs have explicit rewind identities,
  policies, capture data, and focused round-trip coverage. Architecture, constructor,
  runtime-state, bootstrap, and Mod API surface guards were updated without adding
  gameplay carve-outs or runtime disassembly reads.
- The unrelated CNZ2 Knuckles cutscene/boss work that had been embedded in the source
  plan was preserved as
  `docs/architecture/plans/cnz2-knuckles-cutscene-boss-parity-tasks.md`; it is not part
  of this remediation.

Verification used JDK 21 and the discovered local S3K ROM:

- 174 focused architecture, rewind, powered-attack, transition, and Mod API assertions:
  pass.
- 1,159-test integrated remediation/Wave-4 matrix: 1,155 pass; the four failures are
  `TestS3kBadnikChildGraphRewind` isolation failures after the large mixed run. The same
  class passes all 24 assertions in a fresh Maven fork, including the Ribot and
  SnaleBlaster regressions named in Wave 4.
- S3K AIZ/level-loading/bootstrap/decoding/PLC compatibility matrix: pass.
- `mvn -DskipTests package`: pass; the executable dependency JAR is produced.
- `git diff --check`: pass.

The all-repository sweep was also attempted with all three discovered ROM properties.
It is not a usable clean gate in this worktree: it exhausted the Surefire heap after
reporting 71 failures and 28 errors across unrelated pre-existing areas, including
invalid-disassembly-symlink consumers, tests hardcoding `s3k.gen`, unavailable
runtime-art test services, global registry/test-order contamination, and unrelated
FBZ/ICZ/MHZ route tests. Fresh isolated reports are therefore the acceptance evidence
for this remediation; no failing fresh report points to the Super Emerald behavior.

The supplied S3K ROM is the noncanonical variant recorded above. Relevant bytes were
cross-checked against `sonicretro/skdisasm` commit `9fad8e21`, but this report does not
claim canonical-ROM acceptance.

### End-to-End Review

The final review traced the complete route in source and tests: big ring captures Saved2
state and clears the active checkpoint; HPZ initialization consumes the transition;
ceremony/conversion releases control; a gray pedestal requests its exact stage; reward
publication changes only that stage from 2 to 3; success and failure create distinct
return contexts; successful choreography alone gates the Master Emerald; re-selection
and exit eligibility read the durable progression; exit restores the origin level,
player, timer, flags, checkpoint, apparent zone/act, and water state.

The review also checked ownership and failure boundaries. Durable progression is changed
only by `Sonic3kSpecialStageManager`, presentation state remains sanctuary-local, runtime
tables are ROM-loaded, shared movement reaches S3K abilities through
`SuperStateController`, powered attacks use a narrow service surface, and object
constructors do not reach global services. Rewind restores parent/child graphs by
identity rather than copied object references. The candidate Mod API snapshot was
regenerated after the final surface extraction and its signature/pin/release-policy
tests pass.

No numbered remediation item remains open. The only verification limitation is the
repository-wide baseline described in the Integration Report, not an unimplemented or
known-failing Super Emerald path.
