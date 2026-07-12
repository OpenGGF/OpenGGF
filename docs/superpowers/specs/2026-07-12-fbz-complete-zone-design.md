# Flying Battery Zone Complete Implementation Design

**Date:** 2026-07-12
**Status:** Approved
**Target:** Sonic 3 & Knuckles Flying Battery Zone, Acts 1 and 2
**Branch:** `feature/ai-fbz-complete`

## Requirements

### Goal

Implement Flying Battery Zone (FBZ) completely and accurately from the locked-on Sonic 3 & Knuckles disassembly. Both acts must be playable and visually coherent from level start through the final capsule, with ROM-accurate events, deformation, animated tiles, palette behavior, objects, badniks, bosses, transitions, collision, sound cues, and lifecycle timing.

The implementation must preserve native S3K behavior while remaining compatible with OpenGGF's multi-sidekick, widescreen, and cross-game donation features.

### Authoritative Sources

1. `docs/skdisasm/sonic3k.asm` and files included from `docs/skdisasm/Levels/FBZ/` are the behavioral authority.
2. S&K-side addresses from `sonic3k.asm` are preferred. S3-side data may be used only when the locked-on runtime genuinely references it and no S&K equivalent exists.
3. The existing analysis in `docs/s3k-zones/fbz-analysis.md` is the starting catalogue. Every cited routine must be rechecked during implementation.
4. The S3K complete-run BK2 is a late validation oracle, not an implementation shortcut.

### Strict Parity Target

Strict parity covers the original locked-on configurations:

- Sonic
- Tails
- Sonic and Tails
- Knuckles

Trace and visual reference data are comparison-only. Recorded state must never hydrate or synchronize live engine state during replay.

### Completion Scope

- Both acts load and complete from normal progression, level select, supported checkpoints, and the seamless Act 1-to-Act 2 transition.
- All FBZ event and background functionality is ported, including routines outside the stubbed `FBZ1_Resize` and `FBZ2_Resize` entry points.
- Every FBZ-specific placed or dynamically spawned object has a concrete implementation. No live FBZ route may resolve to `PlaceholderObjectInstance`.
- All shared objects used by FBZ are validated against their FBZ subtypes and art/collision configuration.
- Indoor/outdoor layout swaps, background redraws, palette mutations, deformation-mode changes, and magnetic polarity are accurate.
- Act 1 miniboss, Act 2 subboss, pre-boss plane transition, Act 2 end boss, exit sequence, generic final `Obj_EggCapsule`, placed `Obj_FBZEggPrison` objects, and the final transition to Sandopolis are complete and tested separately.
- Sprite mappings, art tiles, PLC/KosinskiM loads, priorities, animations, collision sizes, sound effects, music changes, and destruction effects match the disassembly.
- New runtime state is rewind-safe and resets correctly across session, level, death, checkpoint, and act-transition paths.
- Existing S1, S2, and S3K behavior remains green.

Unused ROM data is out of scope only when no live FBZ placement or reachable FBZ routine can invoke it.

### Compatibility Requirements

After native parity is substantially complete, perform a distinct compatibility audit:

- **Multi-sidekicks:** FBZ must remain stable with more than two characters. Shared state, solids, hazards, carriers, forced movement, bosses, and transitions must not assume exactly one main player and one sidekick.
- **Widescreen:** Camera locks, event thresholds, spawn/culling, boss arenas, screen-edge mechanics, and offscreen hazards must behave safely at all supported viewport widths.
- **Cross-game donation:** Each donated movement ruleset must be able to finish the mandatory route. If a route requires an unavailable native ability, add the smallest capability-driven workaround while leaving native S3K behavior unchanged.

Donation adaptations must be explicit in code comments, state the blocked route and donated capability, and live in an existing provider/profile/rules boundary. Raw game-name branches and silent behavior changes are prohibited.

## Exploration Synthesis

### Existing Support

- `LevelData`, `Sonic3kZoneRegistry`, title cards, level select, music, start positions, primary layout, collision, object placements, and ring placements already load both acts.
- `Sonic3kPlcArtRegistry.addFbzEntries()` registers a partial FBZ art set: still-sprite hangers/rails, FBZ spikes, Blaster, Technosqueek, the button, cork floor, and collapsing bridge.
- Shared cork-floor, collapsing-bridge, spike, button, and still-sprite code has FBZ-specific configuration.
- `Sonic3kPaletteCycler` correctly recognizes that `AnPal_FBZ` does not cycle colors.

### Major Gaps

- No `Sonic3kFBZEvents` handler or `FbzZoneRuntimeState` exists.
- `Sonic3kScrollHandlerProvider` falls back to uniform default scrolling because no FBZ handler is registered.
- `Sonic3kPatternAnimator` does not register either FBZ AniPLC table.
- FBZ event-driven palette mutations are absent.
- Nearly all FBZ-specific objects, both badniks, the minibosses, and the end boss lack factories and concrete implementations.
- The placed-object inventory contains 533 FBZ-specific placements currently resolving to placeholders. Dynamically spawned end-boss/event objects add further gaps.
- No FBZ behavioral, event, scroll, animation, boss, or trace-replay tests exist.

### Existing Analysis

`docs/s3k-zones/fbz-analysis.md` already identifies the principal ROM routines and dependencies:

- Six Act 1 and one Act 2 foreground layout-modification regions
- Indoor/outdoor background state and palette changes
- Seamless Act 1-to-Act 2 reload with a `-$2E00` X translation
- Indoor scatter deformation, outdoor cloud deformation, and Act 2 boss cloud deformation
- Five AniPLC channels per act
- Magnetic polarity toggled every 256 frames
- Act 1 miniboss, Act 2 subboss, end-boss event control, plane reversal, clouds, pillars, end boss, exit, and capsule

The analysis is a catalogue, not an unquestioned authority. `ArtTile_FBZSpikes` and AniPLC intentionally share `$200-$207`; verify every ownership claim against the disassembly before implementation.

### Mandatory Coverage Inventories

Before implementing objects, generate `docs/s3k-zones/fbz-object-inventory.md` from both locked-on placement binaries and the S3KL pointer table. The checked inventory must contain one row per object ID and subtype with Act 1/Act 2 placement counts, primary ROM label, factory status, route impact, art/mapping/animation dependencies, child/dynamic spawn labels, participation policy, test owner, and completion status. It must include shared placed objects and at least the FBZ-specific ID families `$6F-$7F`, `$8A`, `$A8-$AC`, `$CE-$D0`, `$E0-$E5`, and `$FF`.

The same artifact must include:

- A dynamic-spawn graph covering event controllers, boss children, clouds, pillars, projectiles, explosions, exit objects, the generic final capsule, and placed FBZ egg prisons
- A subtype matrix derived from all 862 locked-on placements, rather than a name-only object list
- An audio cue matrix mapping every music/SFX call to its owning object routine, transition, one-shot/continuous behavior, and test
- A VRAM/PLC handoff matrix that includes monitor/spike/spring restoration after the subboss and exit-art loading after the end boss

### Key Risk

FBZ categories cannot be implemented as unrelated effects. `Events_bg+$04` jointly controls layout presentation, palette ownership, and deformation mode. The Act 2 finale couples camera bounds, background offsets, collision-plane selection, art loading, plane assignment, V-scroll, clouds, and boss spawning. Incorrect ordering would create visually plausible but behaviorally wrong output.

## Architecture Decision

### Delivery Strategy

Use route-driven vertical slices rather than subsystem-only or big-bang delivery:

1. Runtime state, event ownership, art ownership, animated tiles, and deformation foundation
2. Act 1 traversal objects and hazards
3. Act 1 miniboss and seamless transition
4. Act 2 traversal objects and hazards
5. Act 2 subboss and art handoff
6. Act 2 plane-transition sequence, end boss, exit, and egg prison
7. Exhaustive native parity polish
8. Late complete-run trace and visual validation
9. Final compatibility audit and native-parity regression

Every slice must leave a playable, testable frontier and must not weaken the final full-zone requirement.

### Runtime State Ownership

Use the event-backed runtime-state pattern. `Sonic3kFBZEvents` is the canonical mutable owner; `FbzZoneRuntimeState` is an adapter constructed with the current handler instance, as `AizZoneRuntimeState` is today. `Sonic3kLevelEventManager` constructs the handler, installs the adapter, verifies `isBackedBy(currentHandler)`, and recreates/reconciles both after load, act transition, death, checkpoint restart, and rewind restore. Event methods are the only writers. Objects write through a new `FbzObjectEventBridge` plus `S3kFbzEventWriteSupport`; scroll, render, collision, and animation consumers read through the installed adapter.

The handler-backed state includes:

- Foreground layout-region routine/index
- Foreground and background indoor/outdoor flags
- Background redraw stage and direction
- Outdoor vertical bob offset
- Magnetic polarity and its ROM timer phase
- Act 2 foreground event stage
- Boss-event background stage
- Boss-event X/Y background offsets
- Boss-load/position-adjustment flag
- Ten stable rewind identity IDs for cloud objects, never raw object references
- Plane-assignment and background-collision mode
- Screen-shake-related FBZ state not already owned centrally

Do not duplicate authoritative values in multiple managers. Derived render values are recomputed from runtime state. `captureBytes()`/`restoreBytes()` must round-trip every field deterministically. After `ObjectManager` restore, resolve cloud IDs through the rewind identity service; if a required cloud is missing before the ROM cleanup stage, recreate it deterministically from the ROM position/frame table in original allocation order, otherwise leave a terminally cleaned-up slot absent.

### Event Ownership

Create `Sonic3kFBZEvents` and integrate it into `Sonic3kLevelEventManager`. The handler ports:

- `FBZ1_ScreenInit` and `FBZ2_ScreenInit`
- `FBZ1_ScreenEvent` and `FBZ2_ScreenEvent`
- `FBZ1_BackgroundInit` and `FBZ2_BackgroundInit`
- `FBZ1_BackgroundEvent` and `FBZ2_BackgroundEvent`
- Indoor/outdoor transition selection and redraw progress
- Act 1-to-Act 2 seamless transition
- Act 2 boss-event setup and final position adjustment

Gameplay layout writes must use `ZoneLayoutMutationPipeline` through an appropriate mutation surface. Palette writes must use `PaletteOwnershipRegistry` and `S3kPaletteWriteSupport`. Boss/event object creation must use the existing object-event bridge and slot-aware lifetime APIs.

Integration must cover every load seam: `Sonic3kLevelEventManager` construction/update/state installation/backing checks, `Sonic3kScrollHandlerProvider.load()`/`getHandler()`, `Sonic3kPatternAnimator.resolveAniPlcAddr()`/`installGraphChannels()`, `S3kAnimatedTileChannels`, and `Sonic3kZoneFeatureProvider.registerSpecialRenderEffects()`/`registerAdvancedRenderModes()`. Level load and FBZ1-to-FBZ2 reload clear and recreate these registries, so all FBZ contributors must be provider-registered rather than installed once by an object.

### Scroll and Render Ownership

Create `SwScrlFbz` and register it in `Sonic3kScrollHandlerProvider`. It implements:

- Indoor `FBZ_Deform` with the exact 34-band height array and scatter index table
- Outdoor `FBZ_Deform` with its eight bands, automatic cloud drift, and bob offset
- Act 2 `FBZ2_CloudDeform`, including background offsets, shake, cloud positioning, and faster drift

Use `ScrollEffectComposer`, `DeformationPlan`, and `ScatterFillPlan` for scanline construction. Use `AdvancedRenderModeController` for the temporary plane/V-scroll reversal and `SpecialRenderEffectRegistry` only for staged rendering that cannot be represented by the ordinary scroll path.

Gameplay-affecting scroll state must update during the logic frame; it must not exist only as a render-side mutation.

Plane reversal is split deliberately: `FbzZoneRuntimeState.backgroundCollisionActive` is set through the event bridge by the boss-event controller and consumed by terrain collision on the ROM-equivalent following player pass, while an advanced-render contributor reads the same canonical mode for Plane A/B and V-scroll assignment. Entry, steady state, boss-load handoff, and restoration are separately tested.

Add one canonical `BackgroundPlaneCollisionProvider` contract owned by `GameplayModeContext` and consumed by every floor, wall, ceiling, ring, `GroundSensor.scanWorld`, and `CalcRoomInFront` path. Its default adapter preserves current HCZ/MGZ/CNZ behavior by translating `GameStateManager.backgroundCollisionFlag` plus the active parallax handler's camera differences into the new semantic collision state. `ZoneRuntimeState` may publish an optional explicit state; FBZ's handler-backed adapter supplies the mode and ROM camera-difference values, while zones without an override use the existing adapter. Collision code reads only this provider, so legacy and FBZ state never become competing authorities.

When inactive the provider returns foreground-only behavior. When active, each probe first resolves the ordinary foreground layer, then probes map layer 1 using ROM `Camera_X_diff`/`Camera_Y_diff`, restores world coordinates, and selects the nearer valid result exactly like `FindFloor`/`FindWall` and their ring equivalents. Activation written by the FBZ controller becomes visible on the next player collision pass; death, restart, boss handoff, and zone unload reset it. Rendering remains a separate consumer of the same canonical event mode. Add HCZ/MGZ/CNZ regression tests alongside FBZ entry, steady-state, handoff, and restoration tests.

### Animated Tiles and VRAM Ownership

Register all five AniPLC channels for both acts through `AnimatedTileChannelGraph` and the S3K pattern animator:

- `$200-$207`
- `$208-$20F`
- `$210-$22F`
- `$230-$237`
- `$238-$247`

Act 1 and Act 2 use different timing/frame data for the large `$210` channel. No FBZ level PLC writes `$200-$207`: AniPLC script 3 is the first and live writer, DMAing `ArtUnc_AniFBZ__3` there, while spike objects only reference those tile IDs. `AnimatedTileChannelGraph` does not enforce VRAM ranges, so add a focused test proving the exact source, destination, first-update timing, and stable spike tile references rather than inventing a PLC handoff.

Complete boss, cloud, pillar, exit, capsule, and gimmick art registrations before enabling consumers. Prefer S&K-side ROM addresses and verify every decompression source.

Event-driven indoor/outdoor colors are one-shot mutations of ROM palette line 4, which maps to engine palette index 3, colors 2-9. Submit the patch through `S3kPaletteWriteSupport`, resolve it immediately during the staged redraw, and reapply it during load/restart/rewind reconciliation. Do not model it as a per-frame AnPal channel. Boss setup also owns line 4 color 1 at the documented stage.

### Object Architecture

Implement all FBZ-specific placements and dynamically spawned objects with `ObjectServices` injection. Use:

- `ObjectControlState` for forced movement, grabs, launches, and control locks
- `ObjectPlayerQuery` and `ObjectPlayerParticipationPolicy` for native-slot and multi-sidekick targeting
- `NativePositionOps` for playable `x_pos`/`y_pos` writes
- `ObjectLifetimeOps` for deletion, despawn, remembered placement, replacement, and slot transfer
- Canonical solid, touch-response, and lifecycle profiles where applicable
- `spawnChild` and slot-aware replacement APIs for ROM child allocation behavior

Port ROM init/update cadence, fixed-point movement, integer trig, collision timing, render eligibility, child counts, and offscreen deletion exactly. Do not add zone-, route-, or frame-specific trace carve-outs.

The object inventory is a blocking gate: no object-family task starts until its IDs, used subtypes, dynamic children, art, audio, slot allocation primitive (`FindFreeObj`, `FindNextFreeObj`, in-place replacement, or parent-slot reuse), and focused tests are recorded.

### Boss Architecture

Implement the Act 1 miniboss, Act 2 subboss, boss-event control platform, pillars, clouds, Act 2 end boss, Robotnik/EggRobo variants, ship/head/flame children, exit door/hall, generic final egg capsule, and the separate placed FBZ egg-prison objects through the S3K boss/object workflows.

Boss state machines must preserve:

- Arena thresholds and camera bounds
- Music and palette transitions
- Character-specific art and behavior
- Collision/hit timing and invulnerability phases
- Child spawn and slot order
- Defeat, explosion, transition, and cleanup cadence
- Checkpoint/re-entry initialization
- Every music/SFX edge in the audio cue matrix, including continuous versus one-shot playback and silence/stop behavior

The subboss handoff must reload `PLC_Monitors`, then `PLC_MonitorsSpikesSprings`, at the disassembly-defined stages. The end-boss aftermath must queue `PLCKosM_FBZEndBoss_Exit`. After capsule/exit processing, wait for the camera-Y `$720` gate and request `StartNewLevel #$0800` (Sandopolis Act 1).

### Rewind and Lifecycle

`GameplayModeContext` already registers `ZoneRuntimeRegistry`; do not register it again. Implement complete deterministic state serialization, event-handler restore reconciliation, cloud/object ID relinking, and focused capture/restore/capture equality tests. All object final scalars and references must satisfy the rewind coverage guards. Parent-recreated render-only children may use the documented baseline convention only when parent recreation genuinely restores them.

### Failure Behavior

Unknown live FBZ subtypes, missing art, impossible event stages, invalid plane modes, and missing child allocation must fail visibly in focused tests or diagnostics. They must not silently fall back to approximate geometry, unrelated art, or placeholder behavior.

## Feature Design

### Frame Data Flow

Adopt the existing ROM-aligned `LevelFrameStep` order; FBZ must not reorder the shared pipeline:

1. Run only proven FBZ pre-physics hooks, if any disassembly routine genuinely executes there.
2. Run player physics and dynamic objects in the established S3K slot order, including fixed-slot hooks, child spawning, touch, solidity, forced movement, and deletion.
3. Move/clamp the camera using the previous frame's bounds.
4. Run FBZ fixed objects and `Sonic3kFBZEvents.update()` against the post-scroll camera.
5. Flush queued layout mutations.
6. Apply boundary easing for the next frame.
7. Synchronize post-camera placements and advance remaining level systems.
8. Compute FBZ deformation and advance AniPLC/palette/render registries in their existing post-event phases.
9. Render using the active plane assignment, V-scroll orientation, shake, priorities, and special passes.
10. Capture rewind state at the established gameplay boundary.

Any exception must cite the exact ROM call phase and use `updatePrePhysics()`, a fixed-slot hook, or camera-driven-scroll support rather than moving generic FBZ events earlier.

### Act 1

Act 1 must support every indoor/outdoor crossing, all six foreground layout regions, matching background redraw direction, corresponding line-4 palette changes, indoor/outdoor deformation selection, all placed traversal objects and hazards, the miniboss arena, defeat flow, and seamless transition.

The transition uses `RELOAD_TARGET_LEVEL`, targets FBZ Act 2, does not show a title card, preserves the current music (the ROM does not issue a music change here), does not preserve level gamestate/respawn-table state, preserves the offset camera position, and applies player/camera offsets `(-$2E00, 0)`. It must not deactivate before the current background-event tail finishes.

ROM `Offset_ObjectsDuringTransition` scans the exact object-RAM interval from `Dynamic_object_RAM+object_size` through the slot immediately before `Breathing_bubbles`, skipping dynamic slot zero, and offsets occupied entries only when render-flag bit 2 marks level space. Extend `SeamlessLevelTransitionRequest` with a request-scoped `ROM_WORLD_SLOT_RANGE` preservation policy; `PERSISTENT_ONLY` remains the default. The FBZ policy carries explicit start-inclusive/end-exclusive slot bounds and includes slot-backed `BossChildComponent` objects in range, while composite/non-slot children are carried only with their owner.

Generalize the existing level-repeat vocabulary into a shared `participatesInLevelSpaceOffset()` predicate and a guaranteed `applyLevelSpaceOffset(dx, dy)` operation. `AbstractObjectInstance` performs the native centre-coordinate/subpixel-preserving shift; `onCarriedAcrossSeamlessTransition()` remains a post-shift hook for extra anchors or cached origins, not the primary coordinate move. Snapshot, retention, and offset tests must cover the lower excluded slot, first included slot, last included fixed slot, excluded `Breathing_bubbles` boundary, ordinary nonpersistent objects, and slot-backed boss children. Reloaded placements/rings restart after the carried-object snapshot, matching the cleared object/ring load routines.

### Act 2

Act 2 must support its foreground layout region, indoor/outdoor behavior, all placed objects and hazards, the character-specific subboss, and its post-defeat art handoff.

The finale is an explicit event state machine:

1. Detect the boss-event camera threshold.
2. Spawn the control platform, pillars, and ten clouds in ROM order.
3. Load the outdoor palette and required art.
4. Carry participants while advancing background X/Y offsets.
5. Enable background collision and swap plane responsibilities.
6. Apply cloud deformation, V-scroll reversal, and screen shake.
7. Refresh the destination plane and adjust players/camera/layout.
8. Spawn the end boss only when the refresh stage is complete.
9. Clear transitional shake/collision state at the ROM-defined time.
10. Run boss defeat, exit door/hall, the generic final egg capsule, and separate placed FBZ egg-prison behavior.
11. At the camera-Y `$720` terminal gate, transition to Sandopolis Act 1 (`#$0800`).

### Compatibility Layering

Compatibility behavior is evaluated only after native parity:

- **Multi-sidekicks:** Explicitly choose which participants each routine affects. Ordinary solids and hazards consider all eligible players; scripted locks, carries, and boss gates use a documented participation policy and cannot deadlock on an extra sidekick.
- **Widescreen:** Keep event thresholds and arena bounds in world coordinates. Derive view-edge behavior from the active viewport and camera APIs. Test both horizontal extremes so extra visibility neither skips a lock nor activates a hazard early.
- **Cross-game donation:** Run every mandatory route with each donated capability profile. Add a workaround only when a route is otherwise impossible. Express the condition as a semantic capability/profile predicate, document why the original mechanic is unavailable, and preserve the native path bit-for-bit when donation is disabled.

The executable compatibility matrix is:

- Native roster: Sonic; Tails; Sonic and Tails; Knuckles at 320 px, with level-start, every supported starpost, seamless transition, both subboss/boss approaches, and final exit coverage
- Widescreen: Sonic and Tails plus Knuckles at exactly 320, 352, 400, 528, and 800 px through every camera lock, carrier, crusher, boss arena, and screen-edge transition
- Multi-sidekicks: main player plus 0, 1, 2, and 3 configured sidekicks, including duplicate characters, through representative solids/hazards and full completion of both acts
- Donation: S3K host with donation `off`, `s1`, and `s2`; each configuration must complete the mandatory route and all ability-gated mechanics

### `s3k-zone-bring-up` Skill Upgrade

Update `.agents/skills/s3k-zone-bring-up/SKILL.md` and its maintained mirror, if present, so future zone work requires:

- Complete disassembly event/function coverage, including ScreenInit, ScreenEvent, BackgroundInit, BackgroundEvent, and nonstandard handlers
- Inventory of placed and dynamically spawned objects, badniks, bosses, art, audio cues, and transitions
- Route-driven delivery with runtime-owned framework routing
- Test-first subsystem and object implementation
- Late, token-conscious trace validation after broad implementation
- Final multi-sidekick, widescreen, and cross-game-donation audits
- A native-parity regression after compatibility work

Before editing the skill, create `docs/superpowers/research/2026-07-12-s3k-zone-bring-up-skill-rubric.md` with binary assertions for full nonstandard event discovery, placed/dynamic object inventory, route-driven slices, test-first work, trace-last ordering, all three compatibility audits, and final native regression. Save the exact fresh-agent prompt, raw baseline output, and rubric result to `docs/superpowers/research/2026-07-12-s3k-zone-bring-up-skill-baseline.md`. After updating both `.agents/skills/s3k-zone-bring-up/SKILL.md` and `.claude/skills/s3k-zone-bring-up/SKILL.md`, rerun the identical prompt with a fresh agent and save raw output plus rubric result to `docs/superpowers/research/2026-07-12-s3k-zone-bring-up-skill-forward-test.md`. All rubric assertions must pass. Verify mirrors with `Compare-Object (Get-Content -Raw ...) (Get-Content -Raw ...)` and scan both for placeholders.

## Testing and Validation

### Test-First Development

Every behavior change starts with a focused failing test. Tests must fail for the missing ROM behavior before implementation and pass after the minimal implementation. Do not implement a family and add tests afterward.

### Focused Test Layers

1. **Analysis/constant tests:** ROM addresses, tables, subtypes, mappings, palette destinations, and art decompression.
2. **Runtime-state tests:** initialization, reset, checkpoint/re-entry, rewind capture, and impossible-state diagnostics.
3. **Event tests:** every threshold, direction, layout mutation, palette mutation, camera bound, transition stage, and boss-event stage.
4. **Scroll tests:** exact H-scroll/V-scroll output for representative cameras and every deformation mode.
5. **AniPLC tests:** channel destinations, timing, frame order, act differences, and ownership collisions.
6. **Object tests:** init cadence, movement, collision, touch, forced control, participants, children, despawn, and rewind.
7. **Boss tests:** spawn thresholds, attack phases, damage, defeat, child cadence, art/music/palette transitions, and cleanup.
8. **Headless route tests:** traversal through both acts, seamless transition, checkpoint starts, and final completion.
9. **Regression tests:** S3K must-keep-green tests, rewind guards, service guards, shared-object tests, and all previously green traces affected by shared changes.

For every slice, `docs/superpowers/research/2026-07-12-fbz-red-green-log.md` records task ID, requirement ID, exact RED command, expected missing-behavior failure excerpt, GREEN command/result, test commit, implementation commit, and reviewer verdict. This evidence—not final coverage alone—proves test-first development.

The must-green command set includes fully qualified tests to avoid duplicate simple names:

```powershell
mvn "-Dtest=com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.game.rewind.coverage.TestStaticStateRewindCoverageGuard,com.openggf.level.objects.TestObjectServicesMigrationGuard,com.openggf.level.objects.TestNoServicesInObjectConstructors,com.openggf.tests.TestNoServicesInObjectConstructors" test
```

Before implementation, record the current first-error frame, field, error count, and warning count for every known-red trace in the frozen baseline artifact. Final regression requires all must-green tests to pass and every known-red trace to preserve or advance that baseline under the ordering below.

Freeze the exact known-red test inventory and results in `docs/superpowers/research/2026-07-12-fbz-trace-baseline.json` before implementation. Compare results lexicographically: later first-error frame is always better; at the same frame, fewer errors is better; with frame and errors equal, fewer warnings is better. Any lexicographically worse result is a regression. Tests absent from the manifest are not silently classified as known-red.

The implementation plan must name every future FBZ test class. The final gate runs the named must-green command above, all `TestFbz*` focused/route/compatibility tests, `com.openggf.tests.trace.s3k.TestS3kFbzCompleteRunTraceFixture`, and `com.openggf.tests.trace.s3k.TestS3kFbzCompleteRunTraceReplay`; it cannot pass on the pre-existing suite alone.

### Late Trace and Visual Validation

Do not spend tokens iterating on the complete-run trace while major FBZ systems remain placeholders. Once both acts and all reachable objects are broadly complete:

1. Download the pinned official archive `https://github.com/TASEmulators/BizHawk/releases/download/2.11/BizHawk-2.11-win-x64.zip` (91,301,556 bytes; SHA-256 `722B5AAC5E1D89F890B2875B0150F4A86F5762D211F7CD47029CAC70434955C0`). Reject a size/hash mismatch, extract it under ignored `docs/BizHawk-2.11-win-x64`, and verify `EmuHawk.exe` exists. The recorder metadata must subsequently report BizHawk `2.11`.
2. Run `tools/bizhawk/s3k_complete_run_recorder.lua` against `src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2` and the verified `s3k.gen` ROM.
3. Install the FBZ segment by metadata/BK2 frame offset, not by directory-name assumptions. Add `TestS3kFbzCompleteRunTraceFixture` to assert: `game=s3k`, `zone=fbz`, `zone_id=4`, `act=1`, `bk2_frame_offset=237913`, `trace_frame_count=44282` (frames 237913-282194; next-zone entry 282195), source BK2 `s3k-complete-sonic-tails.bk2`, characters `[sonic,tails]`, main Sonic, sidekick Tails, `trace_schema=5`, `csv_version=5`, `trace_profile=complete_run`, `lua_script_version=6.28-s3k-completerun`, BizHawk `2.11`, core `Genplus-gx`, and ROM checksum `C5B1C655C19F462ADE0AC4E17A844D10`. Require `physics.csv.gz` and `aux_state.jsonl.gz` with no uncompressed duplicates and successful `TraceCatalog` discovery.
4. Add an FBZ complete-run replay test using controller input only.
5. Move the first divergence frontier through ROM-backed fixes; update `docs/TRACE_FRONTIER_LOG.md` whenever it moves.
6. Before render implementation, create `docs/s3k-zones/fbz-visual-checkpoints.json`. Each immutable checkpoint has an ID, act, exact BK2 frame or exact player/camera coordinates and deterministic input setup, expected 320x224 reference crop, additional widescreen dimensions when relevant, feature assertion, and output paths. It must cover every indoor/outdoor boundary, each AniPLC channel, seamless transition, subboss, plane reversal/cloud sequence, end boss, and exit. Persist results at `docs/s3k-zones/fbz-validation.md`; every mandatory checkpoint requires PASS, and LIKELY/FAIL must be resolved.

Trace data remains diagnostic and comparison-only. Missing trace fields may be added to the recorder, but never to an engine-state hydration path.

### Final Compatibility Audit

After trace and visual polish:

- Run the defined 0/1/2/3-sidekick matrix, including duplicate-character banks, through representative interactions and full route completion.
- Exercise 320/352/400/528/800 widths through every camera lock, transition, boss arena, carrier, crushing hazard, and screen-edge event.
- Run mandatory routes with S3K-host donation `off`, `s1`, and `s2`, and document any capability workaround.
- Rerun strict native S3K parity tests with all compatibility features disabled.

Implement the matrix as parameterized `TestFbzCompatibilityMatrix` cases and persist the run table at `docs/s3k-zones/fbz-compatibility.md`. Exact multi-sidekick teams are Sonic+none, Sonic+Tails, Sonic+Tails+Knuckles, Sonic+Tails+Knuckles+Sonic, and Sonic+Sonic+Sonic+Sonic (duplicate-bank stress). Donation cases use S3K-host Sonic with source `off`, `s1`, and `s2`. Each row asserts no exception/deadlock, safe camera bounds, required interaction completion, and final route completion; widescreen rows additionally assert no premature event activation or out-of-arena fall/death.

## Acceptance Criteria

The feature is complete when:

- Both acts can be completed through all required native character routes.
- No reachable FBZ-specific placement or dynamic spawn uses a placeholder.
- All FBZ event, deformation, animated-tile, palette, PLC, object, boss, transition, and exit behavior identified in the disassembly is implemented.
- Focused tests cover every state transition and object family and were developed test-first.
- FBZ runtime and object state passes rewind and static-state coverage guards.
- The late complete-run FBZ trace is fully green with zero errors and zero warnings; no state synchronization, tolerance masking, or discrepancy waiver is used.
- Every mandatory visual checkpoint is PASS for geometry, palettes, priorities, scrolling, animation, shake, and plane assignment.
- Multi-sidekick and widescreen audits reveal no crashes, deadlocks, unsafe camera behavior, or unintended interaction changes.
- Every donated movement profile can complete the mandatory route, with narrowly scoped documented adaptations where necessary.
- Native S3K parity remains unchanged when compatibility features are disabled.
- All named must-green tests pass, and every previously known-red trace preserves or advances its recorded pre-change baseline.
- `s3k-zone-bring-up` contains and enforces the expanded workflow described above.

## Risks and Mitigations

- **Scope size:** Split by route slice and disjoint ownership; integrate only after focused verification.
- **Event/scroll/render coupling:** Keep one typed runtime state and test phase ordering explicitly.
- **Object slot cadence:** Use slot-aware lifetime APIs and late aux-backed trace timelines rather than compensating offsets.
- **VRAM overlap:** Define channel/PLC ownership before enabling art consumers.
- **Plane reversal:** Isolate the temporary render mode and test entry, steady state, and restoration.
- **Shared-code regressions:** Require cross-game disassembly checks and full trace regression for shared changes.
- **Compatibility contamination:** Add adaptations after native parity, gate them on semantic capabilities, and rerun native parity with adaptations disabled.
- **Trace cost:** Delay complete-run trace work until broad implementation is complete and use focused tests during development.
- **BizHawk absence:** Restore the pinned official 2.11 distribution only when entering late validation.
