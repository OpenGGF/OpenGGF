# S3&K TAS trick verdicts — 2026-10-26

All **35 original family IDs** now have executed representative outcomes: **22 supported, 9 unsupported, 4 mixed**, comprising **48 named child scenarios**. Mixed is an aggregation of separately tested children. It is not an unverified category. There are no unresolved core scenarios in this report.

The broader original names and publication lists remain in the machine-readable report. A family verdict applies to the named representative children below, not every technique mentioned anywhere under that historical heading. In particular, supported no-Left/Right propulsion does not certify the campaign: the publication’s reset-assisted completion has its own unsupported child. No complete movie, ring total, campaign synchronization or every historical revision was executed.

This supersedes the route-level partial labels in the [previous S3K evidence matrix](2026-10-26-tas-tricks-s3k.md) under the user’s representative-mechanism standard. Its original source notes and detailed Blue Sphere/respawn native derivations remain useful historical evidence.

## Identity, ownership and method

- Engine base and unchanged production: `c69447d77baddd8d3f7e4a177d73c8d327dd223d`. Source audit: `99b16240` (original census). Final evidence is the local commit containing this document; the external JSON records its full SHA after commit.
- Owned worktree: `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-86295626`, branch `feature/ai-tas-verdicts-s3k-20261026`. Fifteen new game-specific test classes and this documentation only. Parent owns shared records, final integration and push.
- Original ROM: `$HOME/code/projects/OpenGGF/s3k.gen`, 4,194,304 bytes; CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`. No copies or links were created. Every selected ROM-required case actually executed; all completed runs had zero skips.
- Native reference: immutable `skdisasm` commit `8b2cf3c4178f2382f844da67798de4befc50c5e0`, `sonic3k.asm`, retail `FixBugs=0`. Read with immutable Git objects into the external task directory. Dirty main disassembly/tool checkouts were neither used as proof nor changed.
- Author evidence: cached original submissions in `$HOME/scratch/agent-tmp/codex/tasvideos-trick-audit-2026-10-26/`, with child-specific publication/submission links below. No new web census. No native capture was necessary for these independently specified local branches, and no gameplay trace rows were imported into the engine.
- Final JSON: `$HOME/scratch/agent-tmp/codex/tas-trick-verdicts-2026-10-26/s3k-verdicts.json`. Each original family preserves its name, scope, original publication IDs and references; children carry their own publication IDs, native expectation, selector, observed result and evidence source.

Synthetic inlets declare valid geometry, save data, slot occupancy or an owner boundary. Production input, collision, object, transition and load owners then execute the technique. A passing limitation test means confirmed unsupported behavior, never native parity. No runtime fixes, disabled tests, oracle resnapshots or unconditional failing assertions are included.

## Reached transitions that determine the result

- **Wall zip:** start Sonic outside an authored full-cell wall, fall onto the real LBZ diagonal lift, and let its ordinary carry acquire grounded embedding. Holding Left produces consecutive +26/+32-pixel corrections with velocities `$1AF4`/`$1FF4`, then exits the far wall face. `loc_F576/sub_F584` yields distances −27/−32; `loc_113D6` computes `−12 − (distance << 8)` and clears ground velocity. The same lift/input without wall cells moves left. This is distinct from the supported downward floor-penetration child.
- **Wheel adhesion:** the actual wheel sets adhesion; real rolling-monitor contact bounces the player; ordinary movement performs the airborne arc and floor collision outside the wheel. The landing owner retains adhesion, then movement crosses a gap. Grounded wheel departure clears adhesion and falls at the gap. The fixture never teleports to represent landing or writes an airborne/landing flag after monitor contact.
- **HCZ slot reuse:** the coordinated `HCZWaterSkimHandler` activates skim, processes a fresh jump exit, and continues updating participants through camera departure alongside `ObjectManager`. A nonpersistent slot control unloads, but the skim record stays allocated and blocks reuse. This proves an unavailable prerequisite of the later boss-overwrite route.
- **Pause:** ordinary gameplay advances the bound V-int counter; actual Start pauses three iterations; Start resumes ordinary advancement. During pause the engine holds both V-int owners, level frame, IGT and player position. Native `Pause_Loop` waits VBlanks and `VInt_Done` increments the global phase. The +3 parity assertion was red before retaining the explicitly named limitation. The relevant engine path is `GameLoop.prepareAdmittedIteration` → `LevelFrameStep.serviceVBlankOnly`; it does not advance the bound ObjectManager clock.
- **Special-stage entry:** actual level request, ROM stage load, entry fade and `GameLoop` input reach the last pre-boot sample. A one-tick Up pulse reaches the stage input owner but is replaced by neutral before the first player update. Native `SpecialStage` masks interrupts after the fade; `loc_8454` runs `Process_Sprites` before `Wait_VSync`, so the last Up should already start at `$200`. Native parity was red. Neutral stays stopped; Up on the boot update starts and accelerates through the first main-loop update. These three retained cases pass. Separately, initial-Up delays 0/1/2 advance the real player through the first speedup and change later turn completion to 24/25/26 updates under identical held Right; that supports shifted turn admission, not the author’s tentative 14–16-frame explanation.
- **Act/save/bonus seams:** LBZ results actually create children and signal act-2 loading before a real pit death abandons the tally; the no-death control retains it. NG+ starts from a declared legitimate cleared save but executes title, save-grid selection and AIZ load. Bonus bounds start from the actual Knuckles AIZ2 event lock, execute bonus entry/return and observe min-X reset, with an ordinary neutral retention control.

## Original-family matrix

Child numbers link each family result to its exact executable scope below. Publication attribution belongs to children; it must not be broadcast from one unsupported child to every publication in a mixed or broad family.

| Original stable ID | Verdict | Executed children |
|---|---|---|
| `s3k-movement` | **supported** | S01 |
| `s3k-rolljump` | **supported** | S02 |
| `s3k-zip` | **supported** | S03, S04 |
| `s3k-stair` | **supported** | S05 |
| `s3k-hwrap` | **unsupported** | S06 |
| `s3k-vwrap` | **supported** | S07 |
| `s3k-offscreen` | **supported** | S08, S09 |
| `s3k-camera` | **supported** | S10 |
| `s3k-slope` | **supported** | S11 |
| `s3k-coop` | **supported** | S12 |
| `s3k-respawn` | **supported** | S13 |
| `s3k-slots` | **supported** | S14 |
| `s3k-bridge` | **unsupported** | S15 |
| `s3k-hczslot` | **unsupported** | S16 |
| `s3k-act` | **supported** | S17 |
| `s3k-glide` | **supported** | S18, S19 |
| `s3k-flight` | **supported** | S20, S21 |
| `s3k-nolr` | **mixed** | S22, S23, S24 |
| `s3k-super` | **supported** | S25, S26 |
| `s3k-shields` | **supported** | S27 |
| `s3k-sphere-ring` | **supported** | S28 |
| `s3k-sphere-motion` | **supported** | S29 |
| `s3k-sphere-timing` | **mixed** | S30, S31, S32 |
| `s3k-bounds` | **supported** | S33 |
| `s3k-ngplus` | **supported** | S34 |
| `s3k-rings` | **supported** | S35 |
| `s3k-rng` | **mixed** | S36, S37 |
| `s3k-boss` | **supported** | S38 |
| `s3k-ddz` | **mixed** | S39, S40 |
| `s3k-reset` | **unsupported** | S41 |
| `s3k-competition` | **unsupported** | S42 |
| `s3k-standalone` | **unsupported** | S43, S44, S45 |
| `s3k-legacy-mode` | **unsupported** | S46 |
| `s3k-ort` | **unsupported** | S47 |
| `s3k-mode` | **unsupported** | S48 |

## Child expectations, observations and controls

### `s3k-movement` — supported

Original family: Spindash charging, slope launch, rolling and ordinary damage boosts. Button-timed spindash charging, slope-sensitive acceleration/jumps, rolling friction and chosen damage impulses build or preserve speed.

**S01 — Charge/release a Knuckles spindash without horizontal input: supported.** [6106M / 9169S](https://tasvideos.org/9169S)

Native expectation: Down plus fresh jump charges; release launches at $800 unrevved and $C00 fully revved.

Executed result and control: Both charge levels executed and travelled on a real sensed floor.

Selector: `com.openggf.physics.TestTasS3kTerrainEntry#knucklesSpindashChargesReleasesAndTravelsWithoutLeftOrRight`.

Native evidence: SonicKnux_Spindash (23681 onward), Sonic_Move and Player_AnglePos; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Executed charge/release plus existing AIZ loop traversal; individual damage-boost routes are not covered by the charge child.

### `s3k-rolljump` — supported

Original family: Rolling-jump steering lock and jump re-press restoring air control. Rolling into a jump locks steering; S3K jump re-press clears the rolling-jump state before air-control processing. Authors also exploit the rolling-jump collision shape.

**S02 — Sonic jump re-press unlocks a rolling jump: supported.** [2741M / 4514S](https://tasvideos.org/4514S), [5453M / 8453S](https://tasvideos.org/8453S)

Native expectation: Fresh second press clears roll-jump before air steering; Tails does not enter Sonic shield moves.

Executed result and control: Fresh second press cleared rolling-jump and applied left air acceleration; Tails control retained the latch.

Selector: `com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kJumpRepressClearsRollingJumpBeforeAirControl+s3kTailsJumpRepressDoesNotEnterSonicShieldMoves`.

Native evidence: Sonic_JumpHeight / Sonic double-jump dispatch; Tails separate flight dispatch; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-zip` — supported

Original family: Terrain penetration, wall/floor zips and subpixel entries. Enter solid terrain through slope/step edges, subpixels, object pushes or movement impulses; collision correction moves the player through the geometry.

**S03 — Downward terrain entry through a top-only ledge: supported.** [2468M / 4087S](https://tasvideos.org/4087S), [2741M / 4514S](https://tasvideos.org/4514S), [2744M / 4515S](https://tasvideos.org/4515S), [3422M / 5427S](https://tasvideos.org/5427S), [3425M / 5486S](https://tasvideos.org/5486S), [3521M / 5639S](https://tasvideos.org/5639S), [4266M / 6655S](https://tasvideos.org/6655S), [5453M / 8453S](https://tasvideos.org/8453S), [5504M / 8498S](https://tasvideos.org/8498S), [6615M / 9430S](https://tasvideos.org/9430S)

Native expectation: A descending foot deeper than -(vy integer+8) passes through; the adjacent shallower foot lands.

Executed result and control: Depth -10 clipped and exited below the ledge; -9 landed.

Selector: `com.openggf.physics.TestTasS3kTerrainEntry#downwardStairClipPassesThroughLedgeOnlyBeyondTheNativeDepthGate`.

**S04 — LBZ lift acquires grounded wall embedding; Left drives repeated reverse correction and exit: supported.** [2468M / 4087S](https://tasvideos.org/4087S)

Native expectation: Platform_DiagonalLift carries the player into a full-cell wall. Fresh Left acceleration is -12; loc_113D6 subtracts signed wall distance shifted eight, yielding $1AF4 then $1FF4 and clearing ground velocity.

Executed result and control: Actual fall/landing, 100 neutral carry updates, two grounded-entry corrections of 26/32 pixels, then wall exit. Empty-wall control moved left and had no large corrections.

Selector: `com.openggf.physics.TestTasS3kTerrainEntry#lbzLiftWallEntryProducesRepeatedReverseCorrectionsAndExit`.

Native evidence: loc_1542C/loc_15444 (28945 onward); Platform_DiagonalLift (50340 onward); loc_F576/sub_F584 (19628–19708); loc_11370/loc_113D6 (22752–22808); all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: The floor-penetration child and platform-acquired horizontal wall-zip child are distinct. Longer corridors, vertical zips, other initial embeddings and publication routes were not tested.

### `s3k-stair` — supported

Original family: Downward stair clipping and landing-depth thresholds. A precisely placed downward movement exceeds the landing penetration threshold at a step, allowing entry into a wall/floor for subsequent zipping.

**S05 — Downward stair clip: supported.** [2468M / 4087S](https://tasvideos.org/4087S)

Native expectation: The native downward landing-depth boundary admits a clip one pixel beyond the landing control.

Executed result and control: Actual sensors and air-collision owner: -9 lands, -10 falls through and out.

Selector: `com.openggf.physics.TestTasS3kTerrainEntry#downwardStairClipPassesThroughLedgeOnlyBeyondTheNativeDepthGate`.

Native evidence: Tails_DoLevelCollision / loc_1542C / loc_15444 (28911–28955); all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-hwrap` — unsupported

Original family: Horizontal coordinate underflow / left-edge level wrap. Move past the left edge so the ROM unsigned word position is interpreted beyond the right boundary; selected routes first unlock the camera/left boundary.

**S06 — Left-edge underflow reaches the unsigned right boundary: unsupported.** [779M / 1378S](https://tasvideos.org/1378S), [1112M / 1935S](https://tasvideos.org/1935S), [1400M / 2460S](https://tasvideos.org/2460S), [1595M / 2784S](https://tasvideos.org/2784S), [1617M / 2802S](https://tasvideos.org/2802S), [1656M / 2877S](https://tasvideos.org/2877S), [2468M / 4087S](https://tasvideos.org/4087S), [2494M / 4118S](https://tasvideos.org/4118S), [2741M / 4514S](https://tasvideos.org/4514S), [2744M / 4515S](https://tasvideos.org/4515S), [3425M / 5486S](https://tasvideos.org/5486S), [4266M / 6655S](https://tasvideos.org/6655S), [5310M / 8223S](https://tasvideos.org/8223S), [5453M / 8453S](https://tasvideos.org/8453S), [5504M / 8498S](https://tasvideos.org/8498S), [5654M / 8501S](https://tasvideos.org/8501S), [6081M / 9155S](https://tasvideos.org/9155S), [6106M / 9169S](https://tasvideos.org/9169S), [6615M / 9430S](https://tasvideos.org/9430S), [6890M / 10042S](https://tasvideos.org/10042S)

Native expectation: Player_LevelBound predicts $FFF0, compares it unsigned and writes right bound $1128.

Executed result and control: Engine wrote left bound $10 and cleared speed; in-bounds control retained speed.

Selector: `com.openggf.physics.TestTasS3kTerrainEntry#currentLimitationLeftUnderflowClampsLeftInsteadOfUnsignedRightBoundary`.

Native evidence: Player_LevelBound / Player_Boundary_Sides (23207–23263), unsigned BHI/BLO; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-vwrap` — supported

Original family: Vertical wrapping primitive and subpixel preservation. Wrap player/camera Y through the zone mask, sometimes triggered by camera lag or a high/low route. Authors use the resulting screen position to change object visibility.

**S07 — Native Y-word wrap preserves subpixels: supported.** [331M / 632S](https://tasvideos.org/632S), [712M / 1330S](https://tasvideos.org/1330S), [826M / 1508S](https://tasvideos.org/1508S), [1112M / 1935S](https://tasvideos.org/1935S), [1387M / 2416S](https://tasvideos.org/2416S), [1400M / 2460S](https://tasvideos.org/2460S), [1595M / 2784S](https://tasvideos.org/2784S), [1617M / 2802S](https://tasvideos.org/2802S), [1656M / 2877S](https://tasvideos.org/2877S), [2468M / 4087S](https://tasvideos.org/4087S), [2494M / 4118S](https://tasvideos.org/4118S), [2593M / 4289S](https://tasvideos.org/4289S), [2741M / 4514S](https://tasvideos.org/4514S), [2744M / 4515S](https://tasvideos.org/4515S), [3422M / 5427S](https://tasvideos.org/5427S), [3425M / 5486S](https://tasvideos.org/5486S), [3521M / 5639S](https://tasvideos.org/5639S), [4266M / 6655S](https://tasvideos.org/6655S), [5310M / 8223S](https://tasvideos.org/8223S), [5453M / 8453S](https://tasvideos.org/8453S), [5504M / 8498S](https://tasvideos.org/8498S), [5654M / 8501S](https://tasvideos.org/8501S), [6081M / 9155S](https://tasvideos.org/9155S), [6106M / 9169S](https://tasvideos.org/9169S), [6615M / 9430S](https://tasvideos.org/9430S), [6890M / 10042S](https://tasvideos.org/10042S)

Native expectation: Player/camera wrap masks only the integer Y word; an arena min-Y gate suppresses wrapping.

Executed result and control: Both player/camera paths preserved Y subpixels; arena min-Y suppressed wrapping and restoring -$100 admitted it.

Selector: `com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kVerticalWrapPreservesYSubpixelLikeRomWordMask+s3kCameraUpdateWrapPreservesFocusedSpriteYSubpixelLikeRomWordMask+arenaBoundDisablesControlWrapWithoutLosingTheLevelWrapRange`.

Native evidence: Player_AnglePos / player and camera Y-word wrapping; arena min-Y gate; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-offscreen` — supported

Original family: Offscreen solid-object passage and object loading/unloading. Leave an object outside the rendered/camera range to suppress new contact or unload it, then pass through or exploit its absence while terrain remains solid.

**S08 — Pass an offscreen full solid that remains allocated: supported.** [2468M / 4087S](https://tasvideos.org/4087S)

Native expectation: A camera-invisible ordinary full solid does not obstruct the player; the same visible geometry stops motion.

Executed result and control: Same live solid: visible control stopped motion; invisible case moved 80 pixels through and beyond it at $400.

Selector: `com.openggf.level.objects.TestTasS3kOffscreenSolid`.

**S09 — AIZ bridge range unload and respawn: supported.** [6890M / 10042S](https://tasvideos.org/10042S)

Native expectation: The bridge tail unloads outside coarse-X range and clears its respawn latch for re-entry.

Executed result and control: Actual allocation, departure and return recreated the bridge.

Selector: `com.openggf.level.objects.TestObjectManagerCounterBasedDynamicUnload#s3kAizDrawBridgeUnloadsAndClearsItsRespawnLatchAtTheRomTail`.

Native evidence: SolidObjectFull render visibility gate; Obj_AIZDrawBridge range/unload tail; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Persistent full-solid passage is distinct from range unload/reload; neither asserts existing-rider behavior for every object.

### `s3k-camera` — supported

Original family: Camera lag, locks, look-down offsets and low-camera boss setups. Hold or lag the camera relative to the player to change loading, collision availability, arena triggers or boss spawn position; some routes maintain locked bounds through transitions.

**S10 — Camera lock preserves lag and biased camera scroll slows: supported.** [2741M / 4514S](https://tasvideos.org/4514S), [3425M / 5486S](https://tasvideos.org/5486S)

Native expectation: Scroll lock retains pending horizontal history delay; nondefault look bias follows slow-scroll rules.

Executed result and control: Fresh focused camera assertions passed; local camera owners, not a boss route.

Selector: `com.openggf.camera.TestCamera#scrollLockPreservesPendingHorizontalHistoryDelay+testCameraSlowScrollWhenBiasNotDefault`.

Native evidence: CameraScroll horizontal delay and Camera_X_pos_bias branches; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Executed local lag/lock/bias semantics; no entire low-camera boss setup or LRZ dash route is claimed.

### `s3k-slope` — supported

Original family: Slope glitch / retained adhesion, antigravity and animation state. Retain slope/adhesion or object-control state after unusual departure or object unloading, allowing anomalous climbing, floor entry, antigravity or a retained FBZ animation.

**S11 — Wheel adhesion survives airborne departure, landing and gap: supported.** [2468M / 4087S](https://tasvideos.org/4087S)

Native expectation: Wheel airborne removal forgets attachment without clearing adhesion; landing preserves it and the player can traverse empty floor.

Executed result and control: Monitor bounce, real movement arc, floor landing outside wheel and subsequent gap traversal passed. Grounded wheel departure cleared adhesion and fell at the gap.

Selector: `com.openggf.physics.TestTasS3kTerrainEntry#airborneWheelDepartureRetainsAdhesionAndCrossesAnUnsupportedGap`.

Native evidence: sub_328E8 (68385 onward), Monitor_Break bounce, Player_AnglePos stick_to_convex (18764 onward); all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Sonic exercises the shared wheel/monitor/landing mechanism on declared floor geometry. ICZ block, elevator, capsule and animation-state variants are not separate tested routes.

### `s3k-coop` — supported

Original family: Two-player Tails carries, pushes, attacks and offscreen assistance. Manual P2 Tails carries or pushes Sonic, attacks bosses, pins cutscene actors, collects offscreen rings or interacts while P1 controls the camera.

**S12 — Manual P2 carry attach and jump release: supported.** [4086M / 6240S](https://tasvideos.org/6240S)

Native expectation: Exact unsigned grab window attaches the carried player; neutral and directional jump release use different native velocities.

Executed result and control: Real carry, release and active flight paths passed contact boundaries.

Selector: `com.openggf.sprites.playable.TestTailsCarryController#manualGrabUsesExactUnsignedContactWindowsAndImmediateNativeAttachment+jumpReleaseUsesExactNeutralAndDirectionalNativeValues+manualP2FlightUsesLiveCarryStateForAirAndUnderwaterFlightUpdates`.

Native evidence: Tails carry unsigned contact, attachment and jump-release routines; selected test native assertions; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Carry attach/release and live flight are tested; full cooperative ring totals, every push and every boss assist are not.

### `s3k-respawn` — supported

Original family: Tails forced return, manual respawn jumps and AI positioning. Manipulate offscreen loss/return and P2 input so Tails reappears or jumps at a favorable position, accelerating bosses and carries.

**S13 — Manual P2 jump bypasses CPU return cadence: supported.** [4086M / 6240S](https://tasvideos.org/6240S)

Native expectation: Jump/Start, not direction alone, releases manual return off the CPU 64-frame cadence.

Executed result and control: Seven input/leader/cadence cases passed actual return owner.

Selector: `com.openggf.sprites.playable.TestTasS3kRespawnBypass`.

Native evidence: Tails_Catch_Up_Flying / loc_13B50 / loc_13B78 (26514–26560); all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-slots` — supported

Original family: Ordinary object-slot allocation order and capacity manipulation. Arrange monitors, dust, projectiles, rings or capsules so future objects occupy particular available slots or update in a useful order.

**S14 — Allocate below owner versus first free slot: supported.** [1656M / 2877S](https://tasvideos.org/2877S), [6890M / 10042S](https://tasvideos.org/10042S)

Native expectation: Results use AllocateObjectBelow; cutscene Knuckles uses first-free AllocateObject.

Executed result and control: Production capsule/results/controller allocation paths and HCZ handoff passed.

Selector: `com.openggf.game.sonic3k.objects.TestAiz2BossEndSequenceObjects#aizResultsUseAllocateObjectBelowTheCapsuleOwner+controllerUsesAllocateObjectForCutsceneKnuckles+hydrocityTransitionKeepsPrePhysicsCameraTargetForHandoffFrame`.

Native evidence: AllocateObjectAfterCurrent and AllocateObject; AIZ2 result/cutscene allocation owners; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Ordinary allocation is supported; raw slot alias, overwritten slots and ORT corruption have separate unsupported family records.

### `s3k-bridge` — unsupported

Original family: AIZ2 bridge skip through cutscene slot-6 self-button alias. Unusual object allocation leaves the bridge button referring to the slot that now contains itself rather than cutscene Knuckles, allowing the bridge/camera to release early.

**S15 — Self-occupied slot 6 presses its own cutscene button: unsupported.** [1656M / 2877S](https://tasvideos.org/2877S), [4266M / 6655S](https://tasvideos.org/6655S), [6081M / 9155S](https://tasvideos.org/9155S), [6890M / 10042S](https://tasvideos.org/10042S)

Native expectation: Native raw _unkFAA4 slot pointer aliases the button; dx=dy=0 presses it and unlocks P1.

Executed result and control: Live button in slot 6 stayed unpressed/locked; typed Knuckles at identical coordinates pressed/unlocked.

Selector: `com.openggf.game.sonic3k.objects.TestTasS3kMemoryLimitations#currentLimitationSelfOccupiedSlotSixCannotActAsCutsceneKnuckles`.

Native evidence: Obj_CutsceneButton / loc_65C04 / loc_65C56 (133978 onward), raw _unkFAA4 alias; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-hczslot` — unsupported

Original family: HCZ water-walking slot unload/reuse and next-act boss overwrite. Unload the walk-on-water record, let a boss occupy its slot, then overwrite that reused slot during act transition to force a quick death/locked-camera state.

**S16 — Water-skim slot becomes reusable after offscreen departure: unsupported.** [1617M / 2802S](https://tasvideos.org/2802S), [1656M / 2877S](https://tasvideos.org/2877S), [4266M / 6655S](https://tasvideos.org/6655S), [6615M / 9430S](https://tasvideos.org/9430S)

Native expectation: HCZWaterSplash_SkimMain runs participants then Sprite_OnScreen_Test can delete the record, allowing allocation reuse.

Executed result and control: Actual handler activated skim, jumped out, and ran through departure. Nonpersistent control unloaded; skim owner stayed in slot 5 and prevented reuse.

Selector: `com.openggf.game.sonic3k.features.TestTasS3kWaterSkimLifetime`.

Native evidence: HCZWaterSplash_SkimMain / HCZWaterSplash_DisplaySkim (75360–75404); Sprite_OnScreen_Test; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Confirmed unavailable range-reuse prerequisite; no claim that the later native boss overwrite/death route itself executed.

### `s3k-act` — supported

Original family: Boss/act skips, transition deaths, timer resets and checkpoint routing. Reach alternate bosses, die at a tally/transition, alter route flags or use camera/player timing to bypass an act, cutscene or timer progression.

**S17 — Death after pre-tally LBZ act reload abandons tally: supported.** [2741M / 4514S](https://tasvideos.org/4514S)

Native expectation: ResultsCreate loads LBZ2 before tally; a subsequent death restarts act 2 without waiting for the tally.

Executed result and control: Real ResultsCreate/art/children caused act-2 load with tally still live. Pit death then reloaded act 2, removed tally, lost one life and awarded no pending tally score. No-death control retained tally.

Selector: `com.openggf.tools.TestTasS3kTallyDeath`.

Native evidence: Obj_LevelResultsCreate (62631–62673), LBZ1BGE_DoTransition (111285 onward), ordinary death/restart owner; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Declared valid post-boss results owner, then actual resource load, tally and death restart. Preceding boss fight, pit approach, MGZ both-act skip and other checkpoint routes are not substituted by this LBZ child.

### `s3k-glide` — supported

Original family: Knuckles gliding, wall climbing, superglides and collision entries. Knuckles enters thin/deep floors or walls through glide/collision ordering, uses enemy/monitor contact for fast glides and combines jumps/climbing for faster ascent. Aerial movement is sensitive to slope and wall state.

**S18 — Glide inner-corner contact ordering and wall-climb bounds: supported.** [2593M / 4289S](https://tasvideos.org/4289S)

Native expectation: Floor response follows wall/ceiling checks at corrected coordinates; native wall-climb bounds and radius gates apply.

Executed result and control: Real inner-corner sensor and contact arbitration tests passed.

Selector: `com.openggf.sprites.managers.TestKnucklesGlideCollisionContract#s3kFloorWinsAfterWallAndClearCeilingAtCorrectedCoordinates+realGroundSensorInnerCornerCollectsWallAndFloorBeforeNativeResponse`.

**S19 — Wall-climb signed-word clamp and wrapping bypass: supported.** [2593M / 4289S](https://tasvideos.org/4289S)

Native expectation: Signed upper clamp is bypassed by level wrapping; retail idle distance/radius behavior is retained.

Executed result and control: Native signed/radius controls passed.

Selector: `com.openggf.sprites.managers.TestKnucklesWallClimbVerticalContract#upperClampUsesSignedWordsAndWrappingBypass+idleUsesRadiusAndRetailDistanceClobberOnlyOnS3k`.

Native evidence: Knuckles glide floor/wall contact and wall-climb signed-word/radius routines; selected test native assertions; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Named contact arbitration and wall-climb primitives are tested, not a Rhinobot-triggered superglide route.

### `s3k-flight` — supported

Original family: Solo Tails flight and flight-assisted collision routing. Tails flight reaches ceilings, maintains favorable height or combines with terrain entry and boss positioning in the solo route.

**S20 — Airborne second press starts Tails flight: supported.** [2468M / 4087S](https://tasvideos.org/4087S)

Native expectation: Fresh airborne second press enters flight; activation tick retains ordinary gravity before flight gravity.

Executed result and control: Activation and next-frame gravity passed production movement owner.

Selector: `com.openggf.sprites.managers.TestPlayableSpriteMovementTailsFlight#nativeS3kMainTailsActivatesFlightOnAirborneJumpRepress+activationFrameKeepsNormalAirGravityBeforeFlightGravityStarts`.

**S21 — Flapping and native flight timer phase: supported.** [2468M / 4087S](https://tasvideos.org/4087S)

Native expectation: Odd ROM clock decrements flight duration; ready/flap phases apply +8/-20 and top-camera velocity clamp.

Executed result and control: Four controller boundaries passed.

Selector: `com.openggf.sprites.managers.TestTailsFlightController#timerDecrementsOnlyOnOddRomVisibleFrames+readyJumpStartsFlapButCurrentFrameStillAddsEight+flapStateSubtractsTwentyWithoutReadyGravity+upwardVelocityClampsWithinTenHexPixelsOfCameraMinimum`.

Native evidence: Tails airborne flight dispatch and odd V-int flight timer/flap owner; selected test native assertions; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Actual activation and native flight phases are tested; MGZ moving-terrain boss route is not.

### `s3k-nolr` — mixed

Original family: No Left/Right directional-input completion. Use wall recoil, balancing, springs, glide momentum, enemies and object interactions to turn/move Knuckles without any Left/Right input.

**S22 — Knuckles spindash propulsion with Down and jump only: supported.** [6106M / 9169S](https://tasvideos.org/9169S)

Native expectation: Charge and release accelerate without Left/Right.

Executed result and control: Unrevved/full-charge movement passed, with no horizontal input.

Selector: `com.openggf.physics.TestTasS3kTerrainEntry#knucklesSpindashChargesReleasesAndTravelsWithoutLeftOrRight`.

**S23 — Knuckles wall-jump reverses facing without Left/Right: supported.** [6106M / 9169S](https://tasvideos.org/9169S)

Native expectation: Fresh jump from a real wall anchor flips facing and gives x=-$400,y=-$380; neutral retains climb.

Executed result and control: Both fresh-jump and neutral control passed, including following neutral displacement.

Selector: `com.openggf.physics.TestTasS3kTerrainEntry#knucklesWallJumpReversesAndPropelsWithoutLeftOrRight`.

**S24 — Published reset-assisted no-Left/Right completion: unsupported.** [6106M / 9169S](https://tasvideos.org/9169S)

Native expectation: 9169S explicitly uses reset to skip MHZ to FBZ transition; the input path must represent that action.

Executed result and control: Reset and Power rows were consumed as neutral; Start survived as positive control.

Selector: `com.openggf.game.sonic3k.TestTasS3kUnavailableCapabilities`.

Native evidence: SonicKnux_Spindash and Knuckles wall-jump branch; 9169S reset action also requires input capability; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Supported propulsion children do not certify a full no-Left/Right campaign. The explicitly reset-assisted 9169S route is a separate unsupported child.

### `s3k-super` — supported

Original family: Super/Hyper abilities, directional dash and Hyper Knuckles quake. Emerald progress selects Super/Hyper tiers; Hyper Sonic dashes with directional velocities, while Hyper Knuckles wall impacts can trigger a quake. TAS routes use the speed/impulse for earlier entry and attacks.

**S25 — Hyper directional dash and invalid direction masks: supported.** [1387M / 2416S](https://tasvideos.org/2416S), [1617M / 2802S](https://tasvideos.org/2802S), [2744M / 4515S](https://tasvideos.org/4515S), [5453M / 8453S](https://tasvideos.org/8453S), [5654M / 8501S](https://tasvideos.org/8501S)

Native expectation: Hyper second press consumes the ability and applies native directional speed table, including invalid masks.

Executed result and control: Fresh focused ability/eligibility and native threshold controls passed.

Selector: `com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kHyperSonicSecondJumpUsesDirectionalDashInsteadOfInstaShield+s3kHyperDashPreservesRomInvalidDirectionMaskSemantics`.

**S26 — Hyper Knuckles wall-impact speed threshold: supported.** [779M / 1378S](https://tasvideos.org/1378S), [3422M / 5427S](https://tasvideos.org/5427S), [3521M / 5639S](https://tasvideos.org/5639S), [5310M / 8223S](https://tasvideos.org/8223S)

Native expectation: Unsigned pre-zero ground speed $480 triggers the powered wall impact; $47F does not.

Executed result and control: Fresh focused ability/eligibility and native threshold controls passed.

Selector: `com.openggf.game.sonic3k.TestHyperKnucklesWallImpactGate`.

Native evidence: Sonic Hyper directional dash table; Knuckles wall-impact unsigned ground_vel >= $480; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-shields` — supported

Original family: Fire/bubble/lightning shield actions and collision boosts. Fire dash, bubble bounce and lightning jumps/attraction enable ground entry, altered speed and collection; losing or retaining a shield also changes boss/contact outcomes.

**S27 — Lightning second jump and steep bubble-bounce conversion: supported.** [2741M / 4514S](https://tasvideos.org/4514S), [5453M / 8453S](https://tasvideos.org/8453S)

Native expectation: Lightning clears the jump-height latch; a steep bubble landing copies post-bounce Y velocity to ground speed.

Executed result and control: Native ability and landing boundaries passed.

Selector: `com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kLightningShieldClearsJumpHeightLatchLikeRomJumpingByte+s3kBubbleShieldSteepLandingCopiesPostBounceYSpeedToGroundSpeed`.

Native evidence: Sonic lightning second-jump and steep bubble landing branches; selected test native assertions; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Lightning and bubble actions have executed boundaries; no claim of every fire-dash/collision boost route.

### `s3k-sphere-ring` — supported

Original family: Blue Sphere outside-fill / red-path conversion exploit. The conversion algorithm chooses an inside direction using an early turn on a closed red path; unusual shapes make it flood outside instead of the intended interior, converting many blue spheres.

**S28 — Blue Sphere notched-loop outside fill: supported.** [3521M / 5639S](https://tasvideos.org/5639S)

Native expectation: A closed notched red path chooses a geometrically exterior blue seed and converts its eight-connected component; disconnected/interior blue and a broken-loop control do not convert.

Executed result and control: Three actual conversion layouts passed, including disconnected component and open-loop controls.

Selector: `com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereRingConversion`.

Native evidence: Red_Loop_Processes_Loop (13204–13242); Blue_To_Ring_Next_Ring_B/R (12952–12995); all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-sphere-motion` — supported

Original family: Blue Sphere turns, red-sphere avoidance and bumper movement. Time turns/jumps at cell boundaries, reverse with bumpers, exploit red-sphere turn timing and select routes under jump/bumper constraints.

**S29 — Turn near red and reverse on bumper: supported.** [3521M / 5639S](https://tasvideos.org/5639S)

Native expectation: A 16-update turn avoids the red cell hit by a straight control; bumper contact reverses stage motion.

Executed result and control: Real stage movement/turn/bumper event tests passed. Generic declared board, not publication layout.

Selector: `com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereMotion`.

Native evidence: sub_9580 turn/collision tail (11950–12115); bumper reversal/unlock branch; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-sphere-timing` — mixed

Original family: Special-stage entry buffering and emerald-spawn positioning. Buffer input or delay entry by a frame to change turning cadence; tune the final sphere, reverse and turn so the emerald spawns at a shorter effective distance.

**S30 — Final-blue timing and subpixel-dependent emerald position: supported.** [3521M / 5639S](https://tasvideos.org/5639S), [5453M / 8453S](https://tasvideos.org/8453S)

Native expectation: After final blue, 85 updates spawn the emerald at the rounded forward cell; adjacent subpixels choose distinct cells.

Executed result and control: Actual final-blue collection through delayed emerald creation passed two neighboring position cases.

Selector: `com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereMotion`.

**S31 — Pre-load Up is retained into the first special-stage player update: unsupported.** [3521M / 5639S](https://tasvideos.org/5639S)

Native expectation: The last entry-fade input remains in Ctrl_1 across the interrupt-masked load. Boot Process_Sprites precedes Wait_VSync, so Up starts motion at velocity $200 before the next input sample.

Executed result and control: Native parity ran red: pre-load Up reached the production input owner but the boot update saw neutral and remained stopped. Named limitation, neutral control and first-boot-Up positive control all passed through the 22 fade holds and first mainloop update.

Selector: `com.openggf.tools.TestTasS3kSpecialStageEntryInput#currentLimitationPreLoadPulseIsLostBeforeFirstSpecialStagePlayerUpdate`.

**S32 — Delaying initial Up shifts turn admission after the first speedup: supported.** [3521M / 5639S](https://tasvideos.org/5639S)

Native expectation: The rate timer advances before movement even while unstarted. One/two neutral updates before Up change position at the first speedup and the later $E0-alignment turn gate.

Executed result and control: Delays 0/1/2 executed 1800 player updates: rate $1400, velocity $1200, Y=$9FB6/$9FC6/$9FD6. The same held Right then completed the turn after 24/25/26 updates and moved X to $1014. Zero-delay is the control.

Selector: `com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereStartDelay`.

Native evidence: loc_84C2 (10773–10789); sub_9B62 / loc_9BA6 (12564–12625); SpecialStage / loc_8454 before first Wait_VSync; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Entry buffering has its own child verdict. Delayed-start turn admission has a separate representative child; the author’s optimal route and tentative 14–16-frame turning explanation are not certified.

### `s3k-bounds` — supported

Original family: Bonus-stage return resetting character-route boundaries. Enter and exit a bonus stage to reset camera/route bounds and reach Sonic-side areas with Knuckles, especially for ring or emerald collection.

**S33 — Bonus return resets Knuckles horizontal route boundary: supported.** [2593M / 4289S](https://tasvideos.org/4289S)

Native expectation: AIZ2 Knuckles min-X lock persists ordinarily; bonus return reloads default horizontal bounds and permits access behind it.

Executed result and control: Actual AIZ2 Knuckles event set min-X $1040; neutral control kept it. Actual bonus entry and return reset min-X to zero.

Selector: `com.openggf.tools.TestTasS3kBonusBounds`.

Native evidence: AIZ2_KnuxResize1 (39202 onward); Load_Starpost_Settings / loc_2D2C2 (61800–61857); all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Request boundaries are declared; actual bonus load and return run. Portal approach and complete ring attack are outside the child.

### `s3k-ngplus` — supported

Original family: Newgame+ starting emerald/save state. Start from verified completed-game progress with emeralds already owned, enabling powered routes, giant-ring awards or DDZ access.

**S34 — Cleared save loads AIZ with its emerald ownership: supported.** [1617M / 2802S](https://tasvideos.org/2802S), [2744M / 4515S](https://tasvideos.org/4515S), [4086M / 6240S](https://tasvideos.org/6240S), [5654M / 8501S](https://tasvideos.org/8501S)

Native expectation: Choose AIZ on a cleared save; new stage retains complete emerald progress, unlike an uncleared save.

Executed result and control: Actual title/save-grid CLEAR_RESTART loaded AIZ with all seven super emerald states and five lives; uncleared LOAD_SLOT control retained empty progress.

Selector: `com.openggf.tools.TestTasS3kNewGamePlus`.

Native evidence: loc_D540 / loc_D57A (17001–17031), cleared-file selected stage and saved emerald copy through sub_DA1E; 8501S and 6240S verification-save claim; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Valid authored engine save, production save-grid selection and load; no imported emulator RAM or complete powered campaign.

### `s3k-rings` — supported

Original family: Ring attack, lightning attraction and cooperative ring collection. Collect maximum rings using character-route access, giant rings, magnetic attraction, deliberate camera placement, offscreen P2 collection and careful prevention of ring despawn/loss.

**S35 — Attracted ring collection and P2 shared ledger timing: supported.** [2593M / 4289S](https://tasvideos.org/4289S), [4086M / 6240S](https://tasvideos.org/6240S)

Native expectation: Accepted main/P2 touch awards exactly once in the ring-owner phase, including rewind-forward replay.

Executed result and control: Actual accepted touch, delayed owner award and two replay checks passed.

Selector: `com.openggf.tests.TestS3kAttractedRingAwardPhase#acceptedControlOneTouchAwardsOnlyWhenRingOwnerRuns+actualNativeP2AcceptanceAwardsSharedLedgerOnlyOnceInMainOwnerPass+acceptedPendingWorldRestoresAndAwardsExactlyOnceAcrossTwoForwardReplays`.

Native evidence: Attracted-ring accepted-touch/award owner, native main/P2 shared ledger; selected test native assertions; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Collection/ledger/rewind mechanics are tested; published 8,577/13,771 totals require full routes.

### `s3k-rng` — mixed

Original family: Pause/universal-clock/RNG and allocation-order manipulation. Pause or vary inputs/object spawns to align universal timers, attack patterns, signpost monitors, ghost behavior and RNG consumption order.

**S36 — Start-pause advances universal clock while gameplay waits: unsupported.** [712M / 1330S](https://tasvideos.org/1330S), [1112M / 1935S](https://tasvideos.org/1935S), [1400M / 2460S](https://tasvideos.org/2460S), [1595M / 2784S](https://tasvideos.org/2784S), [1656M / 2877S](https://tasvideos.org/2877S), [2741M / 4514S](https://tasvideos.org/4514S), [2744M / 4515S](https://tasvideos.org/4515S), [3425M / 5486S](https://tasvideos.org/5486S), [5504M / 8498S](https://tasvideos.org/8498S)

Native expectation: Pause_Loop services VBlank and VInt_Done advances global phase without spending IGT.

Executed result and control: Paused bound V-int stayed unchanged alongside level frame, IGT and player; steady-state normal ticks advanced it and resume advanced by one. Native +3 parity had first failed.

Selector: `com.openggf.tools.TestTasS3kPauseClock`.

**S37 — Signpost sparkle consumes RNG on selected V-int phases: supported.** [2468M / 4087S](https://tasvideos.org/4087S)

Native expectation: EndSignFall sparkle consumes one Random_Number only when V-int &3 is zero, changing the state seen by later consumers.

Executed result and control: Actual falling signpost phase 4 allocated one sparkle and advanced seed $1234->$EA56EA54; phase 5 allocated none and kept the seed.

Selector: `com.openggf.game.sonic3k.objects.TestTasS3kSignpostRng`.

Native evidence: Pause_Loop (1572 onward), VInt_Done (558 onward); Obj_EndSignFall (176240 onward), CreateChild6_Simple (177210 onward), Random_Number (3024 onward); all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Signpost RNG consumption is separately traced to 2468M/4087S, an additional source beyond the original family publication list. No selected later boss pattern is inferred.

### `s3k-boss` — supported

Original family: Boss quick hits, capsule timing and unusual hitbox/arena routes. Use simultaneous players, tall rolling jumps, delayed last hits, pre-arena positioning, slope/ground entry, invulnerability timing, low cameras or delayed capsules to shorten fights and transitions.

**S38 — Damage MHZ boss before arena while respecting flash lockout: supported.** [3422M / 5427S](https://tasvideos.org/5427S)

Native expectation: Pre-arena attacks reduce health only to two; repeated hit during flash cannot consume terminal hits.

Executed result and control: Actual damage owner, health floor and flash control passed.

Selector: `com.openggf.game.sonic3k.objects.bosses.TestMhzEndBossNativeDamage#preArenaDamageClampsAtTwoButStillFlashesAndStopsTheAttacker+anotherAttackDuringTheFlashCannotConsumeTheTerminalCount`.

Native evidence: sub_76782 / loc_767A0 pre-arena damage floor, loc_767B0 flash gate; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Pre-arena damage and flash rejection are tested; six hits in six frames and all boss/capsule strategies are not inferred.

### `s3k-ddz` — mixed

Original family: Doomsday missile alignment and pause-assisted in-game-time optimization. Position or delay to steer missiles and shorten hit recovery; the 4515S NG+ route pauses to improve missile behavior relative to in-game time, while 8501S explicitly avoids that pause method.

**S39 — Natural missile launch reaches phase-sensitive homing: supported.** [1387M / 2416S](https://tasvideos.org/2416S), [2744M / 4515S](https://tasvideos.org/4515S), [5654M / 8501S](https://tasvideos.org/8501S)

Native expectation: After ride/drift/straight/turn, HOME turns by two except when V-int &3 is zero; both phases move.

Executed result and control: Two natural-launch cases reached HOME at angle $C0; phase 4 stayed $C0, phase 5 turned to $C2.

Selector: `com.openggf.game.sonic3k.objects.TestTasS3kDdzMissileSteering`.

**S40 — Pause-assisted missile alignment: unsupported.** [2744M / 4515S](https://tasvideos.org/4515S)

Native expectation: Pausing changes global homing phase without advancing IGT.

Executed result and control: Pause/resume clock controls confirmed that pause freezes V-int, contradicting native phase advance.

Selector: `com.openggf.tools.TestTasS3kPauseClock`.

Native evidence: Obj_DDZMissile / launcher states (174131–174250), sub_82A82 (175237 onward); Pause_Loop / VInt_Done; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Only 2744M/4515S is assigned the unsupported pause optimization. Natural homing is separately supported for all three publications.

### `s3k-reset` — unsupported

Original family: Native soft reset/crash-dependent routing and cutscene skips. Issue a Genesis soft reset at a specified state; the older Sonic mode-corruption run also deliberately crashes before resetting. Other Knuckles/no-LR runs reset during time-bonus transitions.

**S41 — Native Reset/Power action through playback boundary: unsupported.** [6081M / 9155S](https://tasvideos.org/9155S), [6106M / 9169S](https://tasvideos.org/9169S), [6615M / 9430S](https://tasvideos.org/9430S)

Native expectation: Reset or Power must reach a native reset-capable session; they are not neutral controller frames.

Executed result and control: Actual Input Log parser discarded Reset and Power while preserving Start.

Selector: `com.openggf.game.sonic3k.TestTasS3kUnavailableCapabilities`.

Native evidence: Genesis Reset/Power is a machine action in the original input log, not any of the eight joypad buttons; reset-dependent author claims; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Confirmed playback action loss; no attempt to emulate arbitrary RAM/crash behavior or prove every crash chain.

### `s3k-competition` — unsupported

Original family: Standalone Sonic 3 Competition routes. Play the two-player Competition campaign, using zone-specific tree/ramp/lap/height shortcuts; Balloon Park height behavior is specific to standalone Sonic 3.

**S42 — Launch standalone Sonic 3 Competition: unsupported.** [1713M / 2953S](https://tasvideos.org/2953S)

Native expectation: Selecting TWO_PLAYER must open the native competition host.

Executed result and control: Actual GameLoop consumed selection and returned to title because stock provider capability is absent.

Selector: `com.openggf.TestTasS3kTitleCapabilities#currentLimitationCompetitionSelectionReturnsToTitleWithoutACompetitionHost`.

Native evidence: Standalone S3 TWO_PLAYER competition mode dispatch, contrasted with exercised missing NativeTitleModeProvider; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

### `s3k-standalone` — unsupported

Original family: Standalone Sonic 3/Sonic & Knuckles gameplay and mode selection. Run the official standalone ROM variants, or reach S&K mode after corrupting the locked-on mode flag, retaining variant-specific entry/title/boss/route behavior.

**S43 — Standalone S&K title starts Knuckles at MHZ: unsupported.** [2486M / 4110S](https://tasvideos.org/4110S), [2494M / 4118S](https://tasvideos.org/4118S), [5310M / 8223S](https://tasvideos.org/8223S)

Native expectation: SK title one-player selection enters LEVEL at $0700, without the S3 save grid.

Executed result and control: Detected standalone header still routes one-player to DATA_SELECT; distinct two-player action proves request routing ran.

Selector: `com.openggf.TestTasS3kTitleCapabilities#currentLimitationStandaloneKnucklesTitleStartStillRoutesToLockedOnSaveMenu`.

**S44 — Standalone Sonic 3 Competition host: unsupported.** [1713M / 2953S](https://tasvideos.org/2953S)

Native expectation: Standalone competition needs its native mode host.

Executed result and control: Selection was exercised and returned to title.

Selector: `com.openggf.TestTasS3kTitleCapabilities#currentLimitationCompetitionSelectionReturnsToTitleWithoutACompetitionHost`.

**S45 — Locked-on corruption switches to SK-alone mode: unsupported.** [6081M / 9155S](https://tasvideos.org/9155S), [6890M / 10042S](https://tasvideos.org/10042S)

Native expectation: An overflowing KosM queue must write adjacent native mode state.

Executed result and control: Four valid ROM archives filled engine capacity; fifth rejected without mutation; freed-slot retry accepted.

Selector: `com.openggf.game.sonic3k.objects.TestTasS3kMemoryLimitations#currentLimitationFifthKosMArchiveIsRejectedInsteadOfWritingAdjacentModeState`.

Native evidence: SK title LEVEL/$0700 dispatch (6610–6650); S3 competition dispatch; Queue_Kos_Module (2690–2709); all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: The title-routing, competition-host and KosM-overflow children have different publication IDs. Unsupported does not mean each variant ran every failure.

### `s3k-legacy-mode` — unsupported

Original family: Older AIZ crash/reset + HCZ queue-overflow mode corruption. 9155S arranges AIZ camera-underflow corruption, preserves it with one timed reset, uses HCZ drowned tally/O Zone and decompression overflow plus a special-stage detour to alter SK_alone, then game overs into S&K.

**S46 — KosM queue overflow into adjacent native mode state: unsupported.** [6081M / 9155S](https://tasvideos.org/9155S)

Native expectation: Native Queue_Kos_Module scans six-byte entries without a four-entry bound, enabling the published corruption sequence.

Executed result and control: Full-queue fifth admission was rejected; queue snapshot unchanged; identical archive accepted after service freed a slot.

Selector: `com.openggf.game.sonic3k.objects.TestTasS3kMemoryLimitations#currentLimitationFifthKosMArchiveIsRejectedInsteadOfWritingAdjacentModeState`.

Native evidence: Queue_Kos_Module (2690–2709), six-byte unbounded free-entry scan; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: The exercised full KosM queue is one unavailable necessary capability, not a simulated successful native crash/reset chain.

### `s3k-ort` — unsupported

Original family: Object Respawn Table desynchronization and four active AIZ2 bosses. Exhaust allocation during placement so the ROM placement cursor and remembered-object cursor desynchronize; later loading duplicates a still-live AIZ2 boss until four bosses/capsules exist.

**S47 — Leftward mid-pass SST exhaustion desynchronizes ORT: unsupported.** [6890M / 10042S](https://tasvideos.org/10042S)

Native expectation: After one free slot is consumed with more entries pending, native recovery misaligns back cursor/ORT; next left pass can duplicate live B.

Executed result and control: Native duplicate-count parity failed: expected 2, observed 1. Explicit limitation and fully-full upfront control then both passed; exhaustion and resumed placement were reached.

Selector: `com.openggf.level.objects.TestTasS3kOrtExhaustion#currentLimitationLeftwardMidPassExhaustionDoesNotDuplicateTheLiveObject`.

Native evidence: loc_1B892 / loc_1B8A4 (37649–37671), unequal a0/a3 backward cursor recovery on SST exhaustion; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: Reached SST exhaustion and resumed left placement prove this ORT limitation; four simultaneous bosses were not fabricated.

### `s3k-mode` — unsupported

Original family: Newest four-tally KosM overflow into SK_alone, then game over. 10042S synchronizes four AIZ2 capsules/tallies, enqueuing enough modules to spill across the ROM KosM queue into adjacent state, including SK_alone. After deliberate game over, the title is S&K and the rest of the S3 half is skipped.

**S48 — KosM queue overflow into adjacent native mode state: unsupported.** [6890M / 10042S](https://tasvideos.org/10042S)

Native expectation: Native Queue_Kos_Module scans six-byte entries without a four-entry bound, enabling the published corruption sequence.

Executed result and control: Full-queue fifth admission was rejected; queue snapshot unchanged; identical archive accepted after service freed a slot.

Selector: `com.openggf.game.sonic3k.objects.TestTasS3kMemoryLimitations#currentLimitationFifthKosMArchiveIsRejectedInsteadOfWritingAdjacentModeState`.

Native evidence: Queue_Kos_Module (2690–2709), six-byte unbounded free-entry scan; all references use the pinned file above. Reused component tests retain their independent native assertions and the prior matrix’s derivations.

Boundaries: The fifth archive is valid and accepted after capacity frees. Rejection establishes unavailable overflow; adjacent mode writes/game-over campaign were not simulated.

## Exact focused execution record

All commands ran from the owned worktree on the base plus the then-current owned tests. Later affected selectors replace earlier narrower or failed assertions; no completed unchanged selection was repeated as a ceremony. **105 distinct retained cases** have passing final evidence across these focused runs: **68 reused cases and 37 cases in the 15 new classes**. This is not a single 105-case batch or a broad-suite pass. Reused Knuckles comparison methods include KiS2 branch controls without loading a KiS2 ROM.

Each invocation used this exact environment and command form, substituting the literal selector from the command block below; R5–R7 and R10 additionally used `--lean`. Red probes are recorded rather than concealed. Subsequent ordinary tests retain named current limitations and controls, not the failing parity assertions.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py [--lean] -Dmse=off "-Dtest=$TAS_S3K_SELECTORS" \
  "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

`[--lean]` above is notation only: include it only on R5–R7/R10. The commands below use `$HOME` for the original ROM owner’s home; after shell expansion these are the executed absolute ROM arguments:

R1:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.sprites.playable.TestTasS3kRespawnBypass,com.openggf.sprites.playable.TestTailsCarryController#manualGrabUsesExactUnsignedContactWindowsAndImmediateNativeAttachment+jumpReleaseUsesExactNeutralAndDirectionalNativeValues+manualP2FlightUsesLiveCarryStateForAirAndUnderwaterFlightUpdates,com.openggf.sprites.playable.TestSidekickCpuControllerCatchUpFlight#catchUp64FrameGateTriggersOnlyEvery64Frames+catchUp64FrameGateSuppressedWhenSonicIsObjectControlled,com.openggf.sprites.managers.TestKnucklesGlideCollisionContract#s3kFloorWinsAfterWallAndClearCeilingAtCorrectedCoordinates+realGroundSensorInnerCornerCollectsWallAndFloorBeforeNativeResponse,com.openggf.sprites.managers.TestKnucklesWallClimbVerticalContract#upperClampUsesSignedWordsAndWrappingBypass+idleUsesRadiusAndRetailDistanceClobberOnlyOnS3k,com.openggf.sprites.managers.TestPlayableSpriteMovementTailsFlight#nativeS3kMainTailsActivatesFlightOnAirborneJumpRepress+activationFrameKeepsNormalAirGravityBeforeFlightGravityStarts,com.openggf.sprites.managers.TestTailsFlightController#timerDecrementsOnlyOnOddRomVisibleFrames+readyJumpStartsFlapButCurrentFrameStillAddsEight+flapStateSubtractsTwentyWithoutReadyGravity+upwardVelocityClampsWithinTenHexPixelsOfCameraMinimum,com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kLightningShieldClearsJumpHeightLatchLikeRomJumpingByte+s3kBubbleShieldSteepLandingCopiesPostBounceYSpeedToGroundSpeed,com.openggf.tests.TestS3kAttractedRingAwardPhase#acceptedControlOneTouchAwardsOnlyWhenRingOwnerRuns+actualNativeP2AcceptanceAwardsSharedLedgerOnlyOnceInMainOwnerPass+acceptedPendingWorldRestoresAndAwardsExactlyOnceAcrossTwoForwardReplays,com.openggf.game.sonic3k.objects.TestAiz2BossEndSequenceObjects#aizResultsUseAllocateObjectBelowTheCapsuleOwner+controllerUsesAllocateObjectForCutsceneKnuckles+hydrocityTransitionKeepsPrePhysicsCameraTargetForHandoffFrame,com.openggf.level.objects.TestSolidObjectManager#sonic3kOffscreenSolidPushReleasePublishesNativeWalkRunAnimationWord,com.openggf.level.objects.TestObjectManagerCounterBasedDynamicUnload#s3kAizDrawBridgeUnloadsAndClearsItsRespawnLatchAtTheRomTail,com.openggf.camera.TestCamera#scrollLockPreservesPendingHorizontalHistoryDelay+testCameraSlowScrollWhenBiasNotDefault,com.openggf.tests.TestS3kAiz1SpindashLoopTraversal,com.openggf.game.sonic3k.objects.bosses.TestMhzEndBossNativeDamage#preArenaDamageClampsAtTwoButStillFlashesAndStopsTheAttacker+anotherAttackDuringTheFlashCannotConsumeTheTerminalCount,TestTasS3kBlueSphereRingConversion,TestTasS3kBlueSphereMotion,TestTasS3kMemoryLimitations,TestTasS3kUnavailableCapabilities,TestTasS3kTerrainEntry' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R2:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestTasS3kTerrainEntry' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R3:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestTasS3kTerrainEntry,TestTasS3kWaterSkimLifetime,TestTasS3kTitleCapabilities,TestTasS3kNewGamePlus,TestTasS3kDdzMissileSteering,TestTasS3kPauseClock' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R4:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestTasS3kTerrainEntry#airborneWheelDepartureRetainsAdhesionAndCrossesAnUnsupportedGap,TestTasS3kNewGamePlus,TestTasS3kPauseClock,TestTasS3kOrtExhaustion,TestTasS3kOffscreenSolid,TestTasS3kBonusBounds,TestTasS3kSignpostRng,TestTasS3kTallyDeath,TestPlayableSpriteMovement#s3kJumpRepressClearsRollingJumpBeforeAirControl+s3kTailsJumpRepressDoesNotEnterSonicShieldMoves+s3kVerticalWrapPreservesYSubpixelLikeRomWordMask+s3kCameraUpdateWrapPreservesFocusedSpriteYSubpixelLikeRomWordMask+arenaBoundDisablesControlWrapWithoutLosingTheLevelWrapRange+s3kHyperSonicSecondJumpUsesDirectionalDashInsteadOfInstaShield+s3kHyperDashPreservesRomInvalidDirectionMaskSemantics,TestHyperKnucklesWallImpactGate,TestSonic3kSuperTransformationEligibility#eligibilitySelectsExplicitCharacterFormTier+sonicElementalShieldBlocksButBasicShieldAllowsTransformation' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R5:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestTasS3kOrtExhaustion,TestTasS3kTerrainEntry#lbzLiftWallEntryProducesRepeatedReverseCorrectionsAndExit' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R6:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestTasS3kTerrainEntry#lbzLiftWallEntryProducesRepeatedReverseCorrectionsAndExit' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R7:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestTasS3kTerrainEntry#lbzLiftWallEntryProducesRepeatedReverseCorrectionsAndExit' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R8:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestTasS3kSpecialStageEntryInput' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R9:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestTasS3kSpecialStageEntryInput' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

R10:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestTasS3kBlueSphereStartDelay' "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

| Run | Cases / failures / errors / skips | Maven time | Queue wait | Finished UTC | Interpretation |
|---|---|---|---|---|---|
| R1 | 66 / 0 / 0 / 0 | 35.079 s | 0s | 2026-10-26T12:12:59Z | 58 existing cases freshly executed; eight initial new cases also passed. Later wheel/HCZ refinements supersede their initial narrower checks. |
| R2 | 9 / 4 / 0 / 0 | 31.171 s | 376s | 2026-10-26T12:21:32Z | Four fixture failures: wall anchor was one pixel inside instead of touching; crouch animation prerequisites were missing. No unsupported verdict came from these failures. |
| R3 | 17 / 3 / 0 / 0 | 33.474 s | 923s | 2026-10-26T12:44:26Z | 14 passed. Wheel neutral travel budget and completed-save action expectation needed fixture corrections. Pause +3 native-clock parity was reached and red. |
| R4 | 24 / 1 / 0 / 0 | 34.303 s | 503s | 2026-10-26T12:55:41Z | 23 passed. ORT native duplicate-count assertion alone red: expected two B owners, observed one. Real wheel landing/gap, NG+, bonus return, pause/resume and tally death passed. |
| R5 | 4 / 2 / 0 / 0 | 41.588 s | 0s | 2026-10-26T13:00:27Z | Both explicit ORT limitation/control cases passed. Both lift inlets failed because the camera excluded the platform and prevented landing; no zip verdict was inferred. |
| R6 | 2 / 0 / 0 / 0 | 44.367 s | 12s | 2026-10-26T13:02:53Z | Camera inlet corrected to include lift. Both actual landing/carry/Left/exit cases passed. |
| R7 | 2 / 0 / 0 / 0 | 39.520 s | 0s | 2026-10-26T13:15:32Z | Added native-derived $1AF4/$1FF4 assertions to the two consecutive grounded-entry corrections; both wall and empty control passed. |
| R8 | 3 / 1 / 0 / 0 | 33.079 s | 0s | 2026-10-26T13:21:25Z | Two controls passed; pre-load pulse native parity alone red (expected started, observed stopped). |
| R9 | 3 / 0 / 0 / 0 | 36.099 s | 3s | 2026-10-26T13:24:45Z | Explicit entry-buffer limitation plus neutral and boot-input controls passed, including input-owner arrival and subsequent first main-loop update. |
| R10 | 3 / 0 / 0 / 0 | 41.671 s | 0s | 2026-10-26T13:27:48Z | All three initial-Up delays passed native displacement/rate/turn-admission formulas. |

An initial pre-R1 attempt stopped at Java compilation on a test-only `GroundSensor` radius cast; it ran no tests. The cast was corrected before R1. Wheel travel, wall anchor, crouch animation, completed-save action and lift visibility failures were fixture defects, not evidence of unsupported engine mechanisms. The pause, ORT and entry-buffer parity failures were reached discrepancies against independent native expectations; their retained limitation cases subsequently passed.

No broad/category/full-suite, native tool build, desktop input injection or movie replay was launched. The parent owns combined validation, including the repository’s AIZ1 skip, level loading, bootstrap resolver and decoding checks if needed for integration. The measurement-hazard guidance was read before reporting. Queue wait and Maven execution times above are separate and are not summed as delivery wall time.

The focused disassembly citation checker passed all six recognized citation groups in the historical document at the pinned revision (`python3 tools/disasm/disasm_citations.py check docs/architecture/validation/2026-10-26-tas-tricks-s3k.md --rev skdisasm=8b2cf3c4178f2382f844da67798de4befc50c5e0 --strict --limit 10`). New native owner ranges above were read directly from that immutable file. `git diff --check` passed.

Final report checks preserve all 35 original IDs exactly once, validate child verdict aggregation and publication references, and resolve every selector to an existing test method/class. Consumed task Maven logs are removed after this durable record is written. No task-owned live job or child is left at handoff. Main and shared production remain unchanged.
