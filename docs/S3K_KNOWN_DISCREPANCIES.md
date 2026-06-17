# Known Discrepancies from Original S3K ROM

This document tracks **intentional deviations** from the original Sonic 3 & Knuckles ROM. Entries here are architectural choices we've made (cleaner code, added features, deliberate corrections of known ROM bugs) that we accept and do not plan to revert. Runtime gameplay behavior is preserved unless a rationale explicitly justifies a visible change (e.g., the "Save System" entry adds JSON persistence that replaces SRAM).

**What does NOT belong here:**
- Bugs, incomplete implementations, and parity gaps that we *intend to fix* → [S3K_KNOWN_BUGS.md](S3K_KNOWN_BUGS.md)
- General (cross-game) engine-level issues → [KNOWN_BUGS.md](KNOWN_BUGS.md)
- General (cross-game) intentional discrepancies → [KNOWN_DISCREPANCIES.md](KNOWN_DISCREPANCIES.md)

Each entry describes what the ROM does, what we do, and why — focusing on *why* the divergence is acceptable.

## Table of Contents

1. [AIZ Intro Object Spawn Source](#aiz-intro-object-spawn-source)
2. [Obj_Wait Timer Pattern](#obj_wait-timer-pattern)
3. [Immediate Art Loading](#immediate-art-loading)
4. [Knuckles DPLC Pre-Loading](#knuckles-dplc-pre-loading)
5. [Save System](#save-system)
6. [Tails Flying-With-Cargo Physics](#tails-flying-with-cargo-physics)
7. [HCZ Object Mappings: Removal of `docs/` Runtime Reads](#hcz-object-mappings-removal-of-docs-runtime-reads)
8. [AIZ2 Battleship Ship-Loop Display Compensation](#aiz2-battleship-ship-loop-display-compensation)
9. [LBZ1 Miniboss Box Pieces: PLC VRAM Restore Skipped](#lbz1-miniboss-box-pieces-plc-vram-restore-skipped)
10. [LBZ2 Launch Pad Collapse: Mutation Pipeline Offset](#lbz2-launch-pad-collapse-mutation-pipeline-offset)
11. [LBZ2 End Boss Smoke Puffs: Immortal-Object Quirk Not Replicated](#lbz2-end-boss-smoke-puffs-immortal-object-quirk-not-replicated)
12. [LBZ2 Finale Player Scripts: Engine Animation IDs Instead of Raw Mapping Frames](#lbz2-finale-player-scripts-engine-animation-ids-instead-of-raw-mapping-frames)

---

## AIZ Intro Object Spawn Source

**Location:** `Sonic3kAIZEvents.java`  
**ROM Reference:** `sonic3k.asm` line 8111+ (`SpawnLevelMainSprites`)

### Original Implementation

The ROM creates `Obj_AIZPlaneIntro` inside `SpawnLevelMainSprites`, which runs during the main level initialization sprite pass:

```asm
    cmpi.w  #0,(Current_zone_and_act).w     ; AIZ Act 1?
    bne.s   loc_6834
    cmpi.w  #2,(Player_mode).w              ; Not 2-player?
    bhs.s   locret_6832
    move.l  #Obj_AIZPlaneIntro,(Dynamic_object_RAM+(object_size*2)).w
    clr.b   (Level_started_flag).w
```

### Our Implementation

We spawn the intro object from `Sonic3kAIZEvents.init()`, the zone-specific level event handler:

```java
@Override
public void init(int act) {
    if (act == 0 && !bootstrap.isAiz1GameplayAfterIntro()) {
        spawnObject(new AizPlaneIntroInstance(...));
    }
}
```

### Rationale

1. **Consistent with engine architecture** - All dynamic object spawning for cutscenes goes through level event handlers (for example `Sonic2CNZEvents` spawning the CNZ boss). No separate `SpawnLevelMainSprites` equivalent exists.
2. **Object exists from frame 1 either way** - Both paths create the object before the first `update()` call.
3. **Cleaner init flow** - Zone-specific behavior belongs in zone event handlers, not in a monolithic sprite spawning routine.

### Verification

The intro object is active on the first frame of level execution, identical to the ROM's timing.

---

## Obj_Wait Timer Pattern

**Location:** `AizPlaneIntroInstance.java`, `CutsceneKnucklesAiz1Instance.java`  
**ROM Reference:** `sonic3k.asm` `Obj_Wait` subroutine, SST offsets `$2E`/`$34`

### Original Implementation

The ROM uses a convention where SST offset `$2E` is a countdown timer and `$34` is a 32-bit pointer to a callback routine. `Obj_Wait` decrements `$2E` each frame and calls the routine at `$34` when it reaches zero:

```asm
Obj_Wait:
    subq.w  #1,$2E(a0)
    bpl.s   locret
    movea.l $34(a0),a1
    jmp     (a1)
```

### Our Implementation

We use explicit named fields (`waitTimer`, `waitCallback`) or inline timer logic within each routine method, rather than raw SST offset conventions:

```java
if (--waitTimer < 0) {
    onWaitExpired();  // or direct routine advance
}
```

### Rationale

1. **Named fields are self-documenting** - `waitTimer` is clearer than `$2E(a0)` when reading Java code.
2. **No function pointer indirection needed** - Java's method dispatch and routine switch make callbacks unnecessary; the expired handler is just the next case in the state machine.
3. **Same timing behavior** - The countdown interval and frame-exact trigger points are identical.

### Verification

Timer-driven routine transitions fire on the exact same frame as the ROM's `Obj_Wait` pattern.

---

## Immediate Art Loading

**Location:** `AizPlaneIntroInstance.java`, `AizIntroPlaneChild.java`, `AizIntroTerrainSwap.java`  
**ROM Reference:** `sonic3k.asm` `Queue_Kos_Module` calls at `loc_6777A`, `Kos_decomp_queue_count` gate in `AIZ1_Resize`

### Original Implementation

The ROM queues KosinskiM-compressed art for deferred DMA transfer during V-blank:

```asm
    lea     (ArtKosM_AIZIntroPlane).l,a1
    move.w  #tiles_to_bytes(ArtTile_AIZIntroPlane),d2
    jsr     (Queue_Kos_Module).l
    lea     (ArtKosM_AIZIntroEmeralds).l,a1
    move.w  #tiles_to_bytes(ArtTile_AIZIntroEmeralds),d2
    jsr     (Queue_Kos_Module).l
```

This queues the decompression work to be spread across multiple V-blank intervals, avoiding frame drops from large decompressions. Downstream, `AIZ1_Resize` routine 2 gates the transition to routine 4 (Y boundary unlock, dynamic maxY) on `Kos_decomp_queue_count` reaching `0` - the BG event handler stays in intro deformation mode until the queue drains.

### Our Implementation

We decompress and load art immediately during the object's init phase:

```java
byte[] planeArt = ResourceLoader.decompress(romAddr, CompressionType.KOSINSKI_MODULED);
graphicsManager.writePatterns(ART_TILE_AIZ_INTRO_PLANE, planeArt);
```

Since there is no decompression queue to poll, the `AIZ1_Resize` routine `2 -> 4` gate uses an `introWasPlayed` flag (from `Sonic3kAIZEvents.shouldSpawnIntro()`) instead of a queue count. When the intro was played, a 30-frame countdown simulates the queue drain delay. When the intro was skipped, `mainLevelPhaseActive` is set immediately - matching the ROM where `Kos_decomp_queue_count` is already `0` at level start.

### Rationale

1. **No V-blank constraint** - The engine does not have a V-blank DMA budget. Decompression during init has no frame timing impact.
2. **Art available before first draw** - Immediate loading guarantees patterns are ready when the object first renders, eliminating any possibility of a blank-frame glitch.
3. **Simpler code path** - No deferred queue management is needed.
4. **Intro check is equivalent to queue count** - When the intro was not played, no Kos data was queued, so the count would be `0`. Checking `introWasPlayed` produces the same result.

### Verification

All art tiles are present from the first frame the object renders. `TestS3kAiz1SkipHeadless` and `TestS3kAiz1LoopRegression` verify skip-intro correctly unlocks Y boundaries. `TestS3kAiz1SpindashLoopTraversal` verifies Sonic is not killed by premature pit death on the approach to the first loop.

---

## Knuckles DPLC Pre-Loading

**Location:** `CutsceneKnucklesAiz1Instance.java`  
**ROM Reference:** `sonic3k.asm` `Perform_DPLC` calls in `CutsceneKnux_AIZ1`

### Original Implementation

The ROM uses Dynamic Pattern Loading Cues (DPLC) to transfer only the patterns needed for the current animation frame into VRAM each frame:

```asm
CutsceneKnux_AIZ1:
    ...
    lea     DPLCPtr_CutsceneKnux(pc),a2
    jsr     (Perform_DPLC).l
    jmp     (Draw_Sprite).l
```

This minimizes VRAM usage by loading only the active frame's tiles, reusing the same VRAM region as the frame changes.

### Our Implementation

We pre-load all Knuckles cutscene frames at init time, assigning each frame's patterns to distinct tile indices:

```java
// Load all DPLC frames at init
for (int frame = 0; frame < frameCount; frame++) {
    loadDplcFrame(frame, baseArtTile + frameOffset);
}
```

### Rationale

1. **No VRAM scarcity** - Modern systems have abundant texture memory; the VDP's limit does not apply directly.
2. **Eliminates per-frame pattern transfer** - No need to track which frame was last loaded or detect frame changes.
3. **Simpler rendering** - Each mapping frame references stable tile indices, making the draw path straightforward.

### Verification

Every Knuckles animation frame displays the correct patterns at the correct positions, matching the ROM's per-frame DPLC result.

---

## Save System

**Location:** `com.openggf.game.save`, `com.openggf.game.dataselect`, `com.openggf.game.sonic3k.dataselect`  
**ROM Reference:** `sonic3k.asm` SRAM routines (`ReadSaveGame`, `WriteSaveGame`), save-screen objects (`ObjDat_SaveScreen`, `Obj_SaveScreen_*`)

### Original Implementation

The ROM stores save data directly in battery-backed SRAM at fixed offsets. Each of the 8 slots occupies a contiguous region with zone/act, character, emerald, and clear flags packed into specific byte positions. The save screen itself is object-driven, with authored selector/card objects and mappings rather than a debug-style overlay.

### Our Implementation

OpenGGF now keeps the native S3K save-screen flow but stores saves as JSON envelopes instead of raw SRAM. Key differences:

- **Per-slot JSON files** stored at `saves/s3k/slotN.json` wrapped in a `SaveEnvelope` with version, game code, slot number, payload, and hash.
- **SHA-256 integrity** rather than the ROM checksum routine. Hash mismatches log warnings during Data Select scan but do not block otherwise valid saves.
- **Corrupt quarantine** - malformed, unreadable, wrong-game, or structurally invalid save files are renamed to `.corrupt` and treated as empty slots.
- **No-op unsaved sessions** - save requests route through `SaveSessionContext`; when no slot is active, they silently no-op.
- **Snapshot providers** - game-specific payload capture is handled by `SaveSnapshotProvider` implementations rather than direct SRAM-style writes.
- **Session-owned launch metadata** - active slot ownership, selected team, and launch zone/act are carried by `WorldSession` and `SaveSessionContext` rather than being inferred from config during gameplay.
- **Restricted clear restart modeling** - clear slots use Java-side restart tables reconstructed from the disassembly, including Knuckles-specific restrictions, rather than exposing unrestricted level selection.
- **Native S3K save-screen parity** - the native `S3K` `1 PLAYER` route now renders from the authored object layout and mapping frames; the old RECTI/text-placeholder selector path is gone on that production path. Cross-game donation remains separate work, and the temporary S1/S2 placeholder managers are not part of this parity claim.

### Rationale

1. **Platform independence** - JSON files work on any OS without SRAM hardware emulation.
2. **Human-readable** - save files can be inspected and manually edited for debugging.
3. **Extensible** - the envelope format supports versioning and per-game payload schemas.
4. **Parity with the original menu flow** - the S3K save screen now follows the original authored layout and selector behavior, while the backend storage remains engine-owned.

### Verification

`TestSaveManager` verifies round-trip write/read, hash validation, corrupt quarantine, wrong-game detection, replacement of stale `.corrupt` artifacts, and no-op unsaved sessions. `TestS3kSaveSnapshotProvider` verifies payload capture includes team, zone, act, lives, emerald count, and clear-restart metadata. `TestS3kDataSelectPresentation` verifies the native save-screen renderer uses authored layout objects and mapping frames instead of the old RECTI overlay path. `TestGameLoop` verifies active-slot saves are written on bonus-stage and special-stage returns, that `S3K` `ONE_PLAYER` routes into native Data Select, and that `TWO_PLAYER`/overlay bypasses do not.

### Manual Validation

- `2026-04-13`: native S3K parity pass captured via `com.openggf.game.sonic3k.dataselect.S3kDataSelectVisualCapture`, which renders the live native S3K Data Select frontend with real ROM assets into `target/s3k-dataselect-visual/native_s3k_dataselect_slot1.png` for inspection.

---

## Tails Flying-With-Cargo Physics

**Location:** Tails flight physics (`SidekickCpuController`, `PlayableSpriteMovement.applyGravity`)
**ROM Reference:** `sonic3k.asm:27592` `Tails_Move_FlySwim` (+0x08 flight gravity), `sonic3k.asm:27553` `Tails_Stand_Freespace` (branch on `double_jump_flag`)

### Original Implementation

ROM `Tails_Stand_Freespace` at `sonic3k.asm:27553-27555` branches to `Tails_FlyingSwimming` whenever `double_jump_flag(a0)` is non-zero, swapping the normal `+0x38` air gravity for `+0x08` flight gravity from `Tails_Move_FlySwim` (sonic3k.asm:27633 `loc_1488C`). The flag is set when Tails picks up Sonic for the CNZ1 carry intro (`loc_13FC2` at sonic3k.asm:26904 writes `double_jump_flag=1`) and is NOT cleared by the ground-release path at `loc_14016` — Tails continues under flight physics until he actually touches the floor.

### Our Implementation

The engine reproduces this behavior with a feature-scoped gate rather than a flat bit check:

1. `SidekickCpuController.updateCarryInit()` sets `sidekick.setDoubleJumpFlag(1)` at the same point ROM `loc_13FC2` writes the flag.
2. The ground-release branch in `updateCarrying()` zeros Tails's `x_vel/y_vel/ground_vel` and keeps the air bit set (matching ROM `loc_14016` at sonic3k.asm:26923-26946). Crucially, it does NOT clear `double_jump_flag` — the ROM leaves it set so Tails continues in flight physics for at least one more tick while the carry-release impulse propagates to Sonic.
3. `PlayableSpriteMovement.applyGravity()` and `doObjectMoveAndFall()` gate flight gravity on `sprite.getSecondaryAbility() == FLY && sprite.getDoubleJumpFlag() != 0` (mirrors `Tails_Stand_Freespace` → `Tails_FlyingSwimming` branch).
4. Tails's CPU flight AI — `Tails_Catch_Up_Flying` (routine 0x02 at `sonic3k.asm:26474`) and `Tails_FlySwim_Unknown` (routine 0x04 at `sonic3k.asm:26534`) — is ported into `SidekickCpuController.CATCH_UP_FLIGHT` / `FLIGHT_AUTO_RECOVERY`, plus the NORMAL → `FLIGHT_AUTO_RECOVERY` transition on a dead leader.

### Rationale

1. **Feature-scoped gate over raw flag** — `double_jump_flag` is overloaded in the ROM: Sonic's insta-shield uses it (values 1-$20 during shield timing), Knuckles's glide uses it (1=gliding, 2=stopped, 3=sliding), and Tails's flight uses it (non-zero = flight-gravity). Gating the flight-gravity substitution on `SecondaryAbility.FLY` prevents Sonic's insta-shield and Knuckles's glide from accidentally acquiring the `+0x08` gravity. The ROM achieves the same scoping naturally because only Tails's code path hits `Tails_Stand_Freespace`.
2. **Plan reference** — See `docs/superpowers/plans/2026-04-24-s3k-tails-cpu-flight-ai.md` for the full breakdown of the carry-release and flight-AI ports.

### Verification

`TestSidekickCpuControllerCarry`, `TestSidekickCpuControllerCatchUpFlight`, and `TestSidekickCpuControllerFlightAutoRecovery` cover the state-machine transitions. `TestS3kCnzCarryHeadless` verifies the CNZ1 intro carry-release frame window.

---

## HCZ Object Mappings: Removal of `docs/` Runtime Reads

**Location:** `Sonic3kObjectArtProvider.java`, `Sonic3kConstants.java`
**ROM Reference:** `Lockon S3/LockOn Data.asm:838` (`Map_HCZMiniboss`), `:856` (`Map_HCZEndBoss`), `:192` (`Map_HCZWaterWall`)

### Original Implementation (engine, pre-fix)

`Sonic3kObjectArtProvider` previously parsed three HCZ object mapping tables (`Map_HCZMiniboss`, `Map_HCZEndBoss`, `Map_HCZWaterWall`) by reading `.asm` source files from `docs/skdisasm/Levels/HCZ/Misc Object Data/` at runtime via `Files.readAllLines`, falling back to an empty mapping list (and therefore invisible sprites) whenever the disassembly tree was absent. This violated the project's "ROM only for runtime assets" hard rule documented in `CLAUDE.md`.

### Fixed Implementation

All three call sites now read mapping bytes from the user-supplied ROM via `S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr)` using the new constants:
- `MAP_HCZ_MINIBOSS_ADDR = 0x3629E0` (was incorrectly `0x362A28`, which pointed at the first frame body rather than the offset table base)
- `MAP_HCZ_END_BOSS_ADDR = 0x3634D4`
- `MAP_HCZ_WATERWALL_ADDR = 0x22EE10`

Each address was derived from the disassembly's absolute `Frame_<addr>` labels (since the lock-on data is anchored at `org $200000` in the `Sonic3_Complete` build) and verified by reading the ROM at the computed offset and confirming the first word equals the expected offset-table size and the first frame's piece count matches the source.

The duplicate-frame workaround for shared `Frame_362BB0` labels in HCZ miniboss is no longer needed, because ROM-based reading of duplicate offsets yields duplicate frame entries naturally.

### Rationale

This is not a behavioral discrepancy from the ROM — sprite output is identical to before, when the disassembly tree was present. It is recorded here only because the previous implementation deviated from the project's ROM-only sourcing rule and silently degraded under the (CI / fresh clone) configurations where `docs/skdisasm/` is absent.

### Verification

`TestSonic3kLevelLoading` and `TestSonic3kBootstrapResolver` continue to pass. The `loadMappingsFromAsmInclude` helper and the three `Path` constants pointing under `docs/` have been removed from `Sonic3kObjectArtProvider`.

---

## MGZ2 Quake Chunk Source Address

**Location:** `Sonic3kMGZEvents.MGZ_QUAKE_CHUNK_ROM_ADDR`
**ROM Reference:** `0x3CBBB4`, S3-half `MGZ2_QuakeChunks`

The MGZ2 earthquake chunk replacement table is currently read from the S3-half
address `0x3CBBB4`. The project normally prefers S&K-side addresses for locked-on
S3K runtime data, but this quake table is recorded as a reviewed exception until an
equivalent S&K-side source is verified.

`TestArchitecturalSourceGuard.mgz2QuakeChunkS3HalfAddressIsReviewedAndDocumented`
pins both the runtime address and this documentation so the exception cannot drift
silently.

---

## AIZ2 Battleship Ship-Loop Display Compensation

Gameplay state follows the S&K disassembly: `AIZ2_DoShipLoop` writes
`Level_repeat_offset=$200` and subtracts `$200` from camera/player state when
the post-bombing ship loop reaches `$46C0`
(`docs/skdisasm/skdisasm/sonic3k.asm:105200-105221`). Do not change
`BATTLESHIP_WRAP_DIST_POST_BOMBING` away from `$200`.

**Display validated seamless (2026-06-16).** Once the AIZ trace frontier advanced
past the battleship (`TestS3kAizTraceReplay` first error f19089, into the AIZ2
end-boss arena), the ship-loop wrap was visually validated via a trace-faithful
`TraceCaptureTool` capture: at the `$200` wrap (trace f16507 cam `$443C` → f16508
cam `$4240`) the burning-forest background renders continuously with **no seam,
no repeated columns, and no empty `$200`–`$400` filler scrolling into view** — the
current non-wrapping `SwScrlAiz.battleshipSmoothScrollX` BG deform handles the loop
seamlessly. The earlier-deferred forest-loop BG fix
(`docs/superpowers/plans/2026-05-29-aiz2-battleship-wrap-seam.md`) is **not
warranted**: its premise (empty filler scrolls into view) does not manifest with
the current smooth-scroll renderer. No remaining display gap.

---

## LBZ1 Miniboss Box Pieces: PLC VRAM Restore Skipped

**Location:** `LbzMinibossBoxRig.java` (`Phase.LINGER` removal), `Lbz1RobotnikEventController.java`, `LbzMinibossBoxInstance.java`
**ROM Reference:** `sonic3k.asm` `loc_8CF1E` (`PLC_LBZRobotnikAfter`, `PLC_MonitorsSpikesSprings`)

### Original Implementation

When the last LBZ1 miniboss box piece scrolls off screen, `loc_8CF1E` reloads
`PLC_MonitorsSpikesSprings` (subtype `$C` only) and `PLC_LBZRobotnikAfter`
(bubbles + LBZ misc art) before deleting itself, because the box/boss art had
overwritten those VRAM tile ranges on real hardware.

### Our Implementation

The engine loads the box, boss, and Robotnik ship art as standalone
`Pattern[]` sheets outside the level's shared pattern buffer, so no VRAM tiles
are overwritten and there is nothing to restore. The pieces' off-screen
removal range (`$280` coarse) and lingering drift behaviour are replicated;
only the PLC reloads are omitted.

### Rationale

Standalone PLC decompression is the project's preferred boss-art strategy
("Why standalone" in the s3k-implement-boss skill): it avoids the VRAM overlap
conflict entirely instead of emulating the overwrite-and-restore cycle.

### Verification

`TestS3kLbz1KnucklesSequenceHeadless#lbz1RobotnikFoldsAwayBurstPanelsAndKeepsDriftersUntilOffscreen`
covers the piece lifecycle including the off-screen cull;
`TestSonic3kPlcArtRegistry` guards the standalone sheets.

---

## LBZ2 Launch Pad Collapse: Mutation Pipeline Offset

**Location:** `Sonic3kLBZEvents.java`, `LbzZoneRuntimeState.java`

**ROM Reference:** LBZ2 launch finale dynamic events and foreground scroll state

### Original Implementation

The ROM drives the LBZ2 launch-pad collapse through its launch event RAM and
foreground scroll mechanics. The visible effect is a small foreground terrain
clear while the Death Egg launch scroll state is active.

### Our Implementation

The engine keeps `Events_fg_5` reserved for the LBZ1 -> LBZ2 transition path and
uses semantic launch state in `LbzZoneRuntimeState` instead. The pad-collapse
request is consumed by `Sonic3kLBZEvents`, which routes the terrain clear through
`ZoneLayoutMutationPipeline` / `LevelMutationSurface` and combines it with the
launch foreground-scroll offset.

### Rationale

This is an intentional engine-equivalent divergence. Routing the collapse through
the shared mutation pipeline keeps gameplay tile edits rewind-safe, redraw-aware,
and covered by the no-direct-map-mutation policy while preserving the visible
launch-pad collapse behavior.

### Verification

`TestSonic3kLbzLaunchSignals` covers the semantic pad-collapse signal and
mutation routing, `TestLbzLaunchRuntimeState` covers rewind capture/restore for
the launch state, and `TestNoDirectMapMutationsInGameplay` guards against direct
gameplay map writes.

---

## LBZ2 End Boss Smoke Puffs: Immortal-Object Quirk Not Replicated

**Location:** `LbzEndBossInstance.LbzEndBossSmokePuffChild`

**ROM Reference:** `sonic3k.asm` `loc_73BA0`/`loc_73BDC`, anim script `byte_741F8`

### Original Implementation

The LBZ2 end-boss smoke puff is meant to delay `-2*(subtype-$10)` frames, play
frames `7,7,8,9` at delay 5, then delete via the `$F4` anim command, whose
handler (`AnimateRaw_CustomCode`) aliases `Obj_Wait` with `$34 =
Go_Delete_Sprite`. However, the puff's main loop `loc_73BDC` executes
`addq.w #1,$2E(a0)` every frame, while the `$F4` command only runs
`subq.w #1,$2E(a0)` once per anim pass (~19 frames). `$2E` therefore grows
monotonically and never goes negative, so `Go_Delete_Sprite` never fires: the
ROM object loops its smoke frames in a leaked object slot until the level
unloads (invisible for the rising subtype-0 puffs once off-screen, and masked
by the short fight duration for the explosion-spray puffs).

### Our Implementation

The engine implements the clearly-intended behaviour: delay, play `7,7,8,9` at
the ROM cadence, then expire the child.

### Rationale

Replicating the slot leak would accumulate stale objects with no gameplay or
visual value the original authors intended. All other smoke parameters (delays,
frame cadence, subtype-0 rise velocity) match the ROM.

### Verification

`TestLbzEndBossInstance` covers the spike-ball spray spawning the four delayed
smoke puffs and the rolling-smoke speed gate.

---

## LBZ2 Finale Player Scripts: Engine Animation IDs Instead of Raw Mapping Frames

**Location:** `LbzFinalBoss1Instance.java`, `Lbz2RobotnikShipInstance.java`

**ROM Reference:** `sonic3k.asm` `loc_72C68`/`byte_7386A`/`byte_73874` (look-up
scripts), `Obj_LBZ2RobotnikShip` `loc_8D2B6` (grab)

### Original Implementation

During the Death Egg launch look-up the ROM freezes both players with
`object_control = $83` and drives raw player mapping frames through
`Animate_ExternalPlayerSprite` (`$C4, $55, $59, $5A` for P1, a longer `$5A`
hold for P2). The hang-ride grab is detected through the ship's touch response
(`collision_flags = $CA` writing `collision_property`, ignoring value 2).

### Our Implementation

The look-up uses the engine's forced `LOOK_UP` player animation (with held Up
input) for both players instead of raw external mapping-frame scripts. The ship
grab uses a centre-distance box matching the ObjDat touch dimensions and only
ever grabs the main player.

### Rationale

The engine's forced-animation path renders the same player pose for the same
duration without porting the external-animator opcode stream; the grab box is
behaviourally equivalent because only Player 1 can trigger the ROM touch path.
The hang pin itself (frame `$BA`/`$AD`, `(x-4, y-$12)` every frame) matches the
ROM exactly via the object mapping-frame control used elsewhere.

### Verification

`TestLbzFinalBoss1Instance` covers the milestone-A freeze/look-up and finale
phases; `TestLbz2RideCameoInstances` covers the grab, pin frames, release
velocities, and final-boss spawn coordinates.
