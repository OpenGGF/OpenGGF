# Known Discrepancies from Original S3K ROM

This document tracks **intentional deviations** from the original Sonic 3 & Knuckles ROM. Entries here are architectural choices we've made (cleaner code, added features, deliberate corrections of known ROM bugs) that we accept and do not plan to revert. Runtime gameplay behavior is preserved unless a rationale explicitly justifies a visible change (e.g., the "Save System" entry adds JSON persistence that replaces SRAM).

**What does NOT belong here:**
- Bugs, incomplete implementations, and parity gaps that we *intend to fix* → [s3k-known-bugs.md](status/s3k-known-bugs.md)
- General (cross-game) engine-level issues → [known-bugs.md](status/known-bugs.md)
- General (cross-game) intentional discrepancies → [known-discrepancies.md](status/known-discrepancies.md)

Each entry describes what the ROM does, what we do, and why — focusing on *why* the divergence is acceptable.

**TraceChaser extraction note (2026-08-29):** the optional pinned trace-tool
submodule changes recorder ownership, not Sonic 3 & Knuckles runtime behaviour.
No S3K discrepancy was added or reclassified by the cutover.

## Table of Contents

1. [YM Service Timing: Source-Relative Timeline Without Absolute VInt Phase](#ym-service-timing-source-relative-timeline-without-absolute-vint-phase)
2. [AIZ Intro Object Spawn Source](#aiz-intro-object-spawn-source)
3. [Obj_Wait Timer Pattern](#obj_wait-timer-pattern)
4. [Host-side KosM Preparation with Native Readiness](#host-side-kosm-preparation-with-native-readiness)
5. [Knuckles DPLC Pre-Loading](#knuckles-dplc-pre-loading)
6. [Save System](#save-system)
7. [Tails Flying-With-Cargo Physics](#tails-flying-with-cargo-physics)
8. [MGZ2 Quake Chunk Source Address](#mgz2-quake-chunk-source-address)
9. [AIZ2 Battleship Ship-Loop Display Compensation](#aiz2-battleship-ship-loop-display-compensation)
10. [LBZ1 Miniboss Box Pieces: PLC VRAM Restore Skipped](#lbz1-miniboss-box-pieces-plc-vram-restore-skipped)
11. [LBZ2 Launch Pad Collapse: Mutation Pipeline Offset](#lbz2-launch-pad-collapse-mutation-pipeline-offset)
12. [Standalone Sonic & Knuckles Cartridge Behaviour Is Out of Scope](#standalone-sonic--knuckles-cartridge-behaviour-is-out-of-scope)
13. [Air Countdown Digits: Rebuilt Mapping Frames Instead of VRAM DMA](#air-countdown-digits-rebuilt-mapping-frames-instead-of-vram-dma)
14. [s3k_kos_direct.prepared: a sub-frame ROM bit compared against a boundary-granular model](#s3k_kos_directprepared-a-sub-frame-rom-bit-compared-against-a-boundary-granular-model)
15. [SEGA Screen: an engine addition the ROM does not have](#sega-screen-an-engine-addition-the-rom-does-not-have)
16. [SOZ Spring Vine: Failed Display Allocation](#soz-spring-vine-failed-display-allocation)
17. [SOZ Background Event Modes and Torch Animation](#soz-background-event-modes-and-torch-animation)
18. [SSZ Act 2 Widescreen Background Columns](#ssz-act-2-widescreen-background-columns)

---

## YM Service Timing: Source-Relative Timeline Without Absolute VInt Phase

**Location:** `YmServiceTimingProfile`, `YmWriteTimeline`, `SmpsDriver`, and
`VirtualSynthesizer`.

### Original implementation

The retail Z80 sound driver runs asynchronously and writes the YM2612 through
the hardware bus. The observed Blue Sphere FM5 upload has stable relative bus
spacing, while its absolute placement within a VInt depends on scheduling state
outside the retained bounded capture.

### Our implementation

OpenGGF starts the S3K sound service at its engine-owned service boundary and
schedules every FM write by the native source-relative master-cycle vector.
The YM2612 advances and drains the writes at internal-sample boundaries. The
engine does not claim or synthesize an absolute native VInt phase, and Sonic 1
and Sonic 2 retain untimed profiles under their separately audited drivers.

### Rationale

The relative vector is reproducible across twelve native upload groups and is
enough to preserve chip evolution during the write sequence. Inventing an
absolute phase from a bounded movie would fit the fixture rather than model the
ROM. The retained evidence also contains no DMA-contended upload, so no special
DMA timing is inferred. These limits are explicit in the validation report and
do not introduce trace-fed runtime state.

### Verification

The native lab, compact oracle, transactional service tests, rewind/observer
guards, bounded playback trace, and three-ROM audio suites are recorded in
`docs/architecture/validation/audio/2026-08-22-s3k-blue-sphere-audio-validation.md`.

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

We spawn the intro object from `Sonic3kAIZEvents`, the zone-specific level event handler. `init(act)` calls
`spawnIntroObject()` when `shouldSpawnIntro(act)` holds (act 1 and the bootstrap is not skipping the intro), and
`updateAct1()` re-runs it as a one-shot fallback if the object is missing before the camera reaches the start of
the tracked range. The spawn reuses the ROM's fixed dynamic-object slot rather than allocating a fresh one:

```java
private boolean spawnIntroObject() {
    AizPlaneIntroInstance existing = findLiveIntroObject();
    if (existing != null) {
        // ROM SpawnLevelMainSprites installs Obj_AIZPlaneIntro in a fixed
        // dynamic-object slot before the first Process_Sprites call
        // (sonic3k.asm:7849-7853, 8111-8126). A duplicate engine event init
        // must re-adopt that live object, not allocate a second parent.
        AizPlaneIntroInstance.adoptActiveIntroInstance(existing);
        return true;
    }
    // ...
    ObjectSpawn spawn = new ObjectSpawn(0x60, 0x30, 0, 0, 0, false, 0);
    AizPlaneIntroInstance intro = lm.getObjectManager().createDynamicObjectAtSlot(
            () -> new AizPlaneIntroInstance(spawn), AIZ_PLANE_INTRO_SST_SLOT);
    // ...
}
```

`init()` also clears the camera's level-started flag before the spawn, matching the `clr.b (Level_started_flag).w`
in `SpawnLevelMainSprites`.

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







## Host-side KosM Preparation with Native Readiness

**Location:** `S3kKosModuleQueue.java`, `AizPlaneIntroInstance.java`, `Sonic3kAIZEvents.java`
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

This queues decompression work across hardware service intervals. Downstream,
`AIZ1_Resize` keeps the intro deformation active until the queue drains.

### Our Implementation

The engine now submits the real ROM-backed archives to
`S3kKosModuleQueue`. Its resumable decoder advances at the native
pre-main-loop and post-object boundaries, and consumers retain typed handles
until the queue admits readiness. Replay may hold an already-prepared matching
job until its independently recorded completion edge, but it cannot supply
payload or decoder progress. AIZ publishes its terrain overlays and changes
phase only after claiming that prepared queue output.

### Rationale

The remaining intentional difference is below the ROM-visible readiness
boundary: the host does not emulate instruction-by-instruction decoder
pre-emption or VDP bus bandwidth. It advances deterministic whole work units
at the correct service points. Queue order, ROM archive identity, module
count, prepared payload, readiness polling, and the consumer's downstream
mutation remain native-owned and rewind-safe.

### Verification

`TestS3kKosModuleQueue`, `TestSonic3kTitleCardKosQueue`,
and `TestS3kKosStructuralSequence` cover decoder parity, FIFO ownership,
and consumer polling; `TestS3kKosTimingRewindIntegration` (ROM-gated) covers
rewind across the recorded completion boundary.

---







Wider visibility can release a giant ring while the startup enemy-art batch is
still pending. `SSEntryRing_Display` normally restores badnik-explosion art and
immediately deletes the ring; native `Queue_Kos_Module` has no full-queue guard
and would scan past its four slots. The engine retains one invisible,
noncollidable, rewindable ring owner until the existing enemy admission finishes
and a physical slot is available. It then queues the restoration once and
retires. It does not enlarge the FIFO or force the title-owned enemy batch early.
Native available-capacity retirement is unchanged. MHZ2 admission tests cover
both positioned and checkpoint reloads at all five supported presets.

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
- **Native S3K save-screen parity** - the native `S3K` `1 PLAYER` route now renders from the authored object layout and mapping frames; the old RECTI/text-placeholder selector path is gone on that production path.

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

**Location:** Tails flight physics (`TailsFlightController`, `PlayableSpriteMovement.applyGravity`); CNZ1 carry and CPU flight routines (`SidekickCpuController`)
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
2. **Plan reference** — See `docs/architecture/plans/2026-04-24-s3k-tails-cpu-flight-ai.md` for the full breakdown of the carry-release and flight-AI ports.

### Verification

`TestSidekickCpuControllerCarry`, `TestSidekickCpuControllerCatchUpFlight`, and `TestSidekickCpuControllerFlightAutoRecovery` cover the state-machine transitions. `TestS3kCnzCarryHeadless` verifies the CNZ1 intro carry-release frame window.

---







## MGZ2 Quake Chunk Source Address

**Location:** `Sonic3kMGZEvents.MGZ_QUAKE_CHUNK_ROM_ADDR = 0x3CBBB4`
**ROM Reference:** `MGZ2_QuakeChunks` (`sonic3k.lst:316889`), read by `sub_517EA`
(`sonic3k.asm:106937-106950`)

The engine's S3K sourcing rule prefers S&K-half data (addresses below `0x200000`).
`MGZ2_QuakeChunks` is the one MGZ2 table that lives only in the Sonic 3 half: the
`$1080`-byte table occurs exactly once in the locked-on image, at `0x3CBBB4`, and the
S&K-half MGZ2 event code (`sub_517EA`, reached from `MGZ2_QuakeEvent` and
`MGZ2_ChunkEventReset`) reads it through the lock-on mapping with
`lea (MGZ2_QuakeChunks).l,a4`. There is no S&K-side copy to prefer, so this address is
the permanent source. `TestArchitecturalSourceGuard` pins the constant and this entry.

---




## AIZ2 Battleship Ship-Loop Display Compensation

Gameplay state follows the S&K disassembly: `AIZ2_DoShipLoop` writes
`Level_repeat_offset=$200` and subtracts `$200` from camera/player state when
the post-bombing ship loop reaches `$46C0`
(`docs/skdisasm/sonic3k.asm:105200-105221`); `BATTLESHIP_WRAP_DIST_POST_BOMBING`
is that `$200`.

**Deliberate renderer difference.** The ROM handles the burning-forest background
through `Level_repeat_offset` and its plane ring; the engine's
`SwScrlAiz.battleshipSmoothScrollX` is a non-wrapping smooth scroll that renders
the loop without a seam, repeated columns or filler. The foreground ring itself is
generated from the gameplay step exactly as `DrawTilesAsYouMove` does
(`LevelForegroundPlane.drawAsYouMove`, `TestAiz2ForestRingCameraGeneration`).
Validation history: the 2026-05-29 seam plan and the 2026-06-16 capture.

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
launch foreground-scroll offset. The copied 64-by-28-cell VDP window replaces
Plane A from screen Y=40 downward, without scrolling, in both visible tile
passes and the sprite-priority mask. Its final two VBlanks clear the remaining
upper Plane A strip, restore the six hidden rows, and disable the window.
Only the exposed upper strip follows the detach scroll; applying that scroll
to the lower window lifts the platform into grounded Sonic.

### Rationale

This is an intentional engine-equivalent divergence. Routing the collapse through
the shared mutation pipeline keeps gameplay tile edits rewind-safe, redraw-aware,
and covered by the no-direct-map-mutation policy while preserving the visible
launch-pad collapse behavior.

### Verification

`TestSonic3kLbzLaunchSignals` covers the copy/clear VBlanks, fixed window,
semantic pad-collapse signal, and mutation routing.
`TestForegroundWindowRendering` checks visible pixels, transparency, and the
priority mask when a GL context is available; `TestSonic3kLbzRewindRoundTrip`
covers the copied nametable and pending clear. `TestLbzLaunchRuntimeState`
covers rewind capture/restore for the launch state, and `TestNoDirectMapMutationsInGameplay` guards against direct
gameplay map writes.

---







## Standalone Sonic & Knuckles Cartridge Behaviour Is Out of Scope

The engine runs the locked-on Sonic 3 & Knuckles image only. Every branch the ROM
takes on `SK_alone_flag` (standalone S&K entry, the pulley-lift cheat's direct
`Level_select_flag` write, S&K-only title and level-select paths) is modelled with the
flag clear. This is a product-scope decision, not a parity gap: the standalone image is
not a supported input.

---




## Air Countdown Digits: Rebuilt Mapping Frames Instead of VRAM DMA

**Location:** `Sonic3kObjectArtProvider.loadAirCountdownDigitArt()`, `S3kAirCountdownObjectInstance.java`
**ROM Reference:** `sonic3k.asm:33320-33327` (`AirCountdown_Init`), `sonic3k.asm:33489-33516` (`AirCountdown_Load_Art`)

### Original Implementation

`Obj_AirCountdown` points at a single mapping table for its whole life:

```asm
        move.l  #Map_Bubbler,mappings(a0)
        tst.b   parent+1(a0)
        beq.s   loc_1819E
        move.l  #Map_Bubbler2,mappings(a0)
loc_1819E:
        move.w  #make_art_tile(ArtTile_Bubbles,0,0),art_tile(a0)
```

Mapping frames `$00`-`$08` are bubbles drawn from `ArtNem_Bubbles`. Frames
`$09`-`$12` all resolve to the *same* one-piece frame (`word_2FD7A`) whose 2x3
piece sits at tile `$384` past `ArtTile_Bubbles` — i.e. `ArtTile_DashDust`.
The frame therefore selects nothing on its own; `AirCountdown_Load_Art` DMAs six
tiles from `ArtUnc_AirCountdown + (frame - 9) * 6` into that fixed VRAM window
every time the mapping frame changes. P2 uses `Map_Bubbler2` and
`ArtTile_DashDust_P2` so the two players don't overwrite each other's digit.

### Our Implementation

We have no VRAM window to overwrite, so the provider builds a separate
`AIR_COUNTDOWN_DIGITS` sheet: the ten ROM mapping frames are re-emitted with the
ROM's own geometry (offsets, size, flip, palette, priority) and each frame's tile
index moved onto its own six-tile slice of `ArtUnc_AirCountdown`. The object
picks that sheet for frames `>= $09` and the shared `BUBBLER` sheet below it.

### Why This Is Acceptable

The mapping geometry and every art byte still come from the ROM through the
normal ROM-loading pipeline; only the indirection changes — a per-frame tile
base replaces a per-frame DMA into a shared window. The rendered result is
identical, and because both players read from the same immutable source art
rather than a mutable VRAM window, the P1/P2 `Map_Bubbler`/`Map_Bubbler2` split
collapses to a single shared sheet with no visible difference. Emulating the DMA
would mean modelling `ArtTile_DashDust` as mutable VRAM shared with the dash
dust, which buys nothing the tile rebase doesn't already give us.







## `s3k_kos_direct.prepared`: a sub-frame ROM bit compared against a boundary-granular model

**Status:** bounded, permanent modelling limit. One asymmetric comparison-side excusal, added 2026-08-10 in
`LoadQueueComparisonProjection`. It fires exactly once across the current fixture set
(AIZ complete run, frame 6349, direct job ordinal 36) and nowhere in CNZ or MHZ.

### The compared field is not "prepared"

The recorder projects this field from bit 15 of `Kos_decomp_queue_count`
(`tools/tracechaser/bizhawk-headless/src/Recording/LoadQueueStateProjector.cs`:
`bool prepared = (rawCount & 0x8000) != 0;`). In the ROM that bit means
**decompression is in progress right now**, not "prepared":

- `Process_Kos_Queue_Main` (`docs/skdisasm/sonic3k.asm:2845-2846`) sets it with
  `ori.w #$8000,(Kos_decomp_queue_count).w` — commented in the disassembly as
  "set sign bit to signify decompression in progress" — immediately before entering
  `Process_Kos_Queue_Loop`.
- `Process_Kos_Queue_EndReached` (`docs/skdisasm/sonic3k.asm:2938-2941`) clears it with
  `andi.w #$7FFF,(Kos_decomp_queue_count).w` when the stream ends.
- `Set_Kos_Bookmark` (`docs/skdisasm/sonic3k.asm:2819`) only **reads** the bit, to decide
  whether to redirect a preempting V-int's `rte` to `Backup_Kos_Registers`. It is not a
  work quantum and it neither sets nor clears the bit.

A recorded row therefore reads the bit set only when that frame's V-int happened to land
**inside** `Process_Kos_Queue_Loop` — a sub-frame 68000 cycle position. Frame-granularity
state cannot reconstruct it, in this engine or in principle.

### The engine's model, and why it can only err in one direction

`S3kKosDecompressionQueue.afterTimingService` arms the serviced FIFO head at each
`PRE_MAIN_LOOP` boundary and disarms it only when the recorded completion edge fires. The
engine's flag therefore means "the head survived a service boundary before its recorded
completion", which is a **boundary-granular over-approximation** of the ROM bit: it can
read true one or more boundaries before the ROM's V-int first landed in the loop, but it
cannot read false while the ROM is mid-loop. (The flag is comparison-only: `preparedEntries`
is read solely by `QueueDiagnosticSnapshot` capture and by rewind `capture()`/`restore()`;
no gameplay, art, or scheduling path consumes it.)

The excusal mirrors that asymmetry exactly. Only `actual=true / expected=false` is excused,
and only while the head's own recorded completion edge (matched by kind, ordinal and
submission fingerprint) still lies strictly in the future. `actual=false / expected=true`
stays a hard error forever — the engine claiming decompression was not in progress while
the ROM was mid-loop means a completion landed early, which is a real timing defect.
`busy`, `active_source`, `active_destination`, the waiting fingerprints, and the module
queue's `prepared` are all still fully compared.

The asymmetry is structural, not merely gated. The excusal's only effect is to raise the
*expected* row's `prepared` to true, which by construction cannot conceal an actual value
of false. Widening the polarity gate alone is therefore a semantic no-op — verified by
mutation: forcing the branch to accept both polarities left all of
`TestLoadQueueTraceComparison` green. The mutation that *would* be dangerous is changing
the rewrite to mirror the actual value instead of forcing true, and
`keepsReverseInProgressPolarityAsError` fails on exactly that.
`keepsInProgressBitComparedWithoutFutureRecordedCompletion` likewise fails when the
`rawFrame > frame` guard is removed. Both were confirmed by running the mutations.

### Refuted alternatives — do not rerun these

Measured against the 1-error baseline at `b5975c195` (frame 6349,
`queue.s3k_kos_direct.prepared` expected=false actual=true):

1. **Reordering the module and direct service passes** — breaks direct job ordinal 38 with
   a hard `IllegalStateException`, not a comparison error.
2. **Never setting the engine bit** — 37 errors, first at frame 64. 36 rows genuinely
   require it TRUE.
3. **A provenance rule** (distinguishing the false row by where the entry came from) — 29
   errors, first at frame 64. The TRUE rows and the FALSE row share routine, size class
   (`$800` words) and provenance, so no provenance predicate separates them.

### Rejected: a recorded decompression-start edge

Extending the v5 `hardware_timing.jsonl` stream with a decompression-**start** edge would
reproduce the bit exactly, and was rejected as a hard-rule-4 violation. A start edge
releases no engine work: nothing waits on it, no prepared ROM-backed job becomes ready
because of it. Its only observable effect would be to set the value of a compared field,
which makes the comparison a checksum of the sidecar against itself. Rule 4's test is
whether a recorded input only changes *when* real engine work becomes ready; a start edge
decides *what* a compared row says, so it is outside the exception however well the ROM
behaviour is cited.







## SEGA Screen: an engine addition the ROM does not have

**What the ROM does.** Nothing. There is no SEGA screen sequence in either half of the
locked-on cartridge.

- `Sega_Screen` — game mode 0's handler — is
  `move.b #4,(Game_mode).w` and falls straight into `Title_Screen`
  (`docs/skdisasm/sonic3k.asm:5387-5388`); the S3 half is the same with an explicit `rts`
  (`docs/skdisasm/s3.asm:4768-4770`).
- `JumpToSegaScreen`, the handler for game modes `$10` and `$18`, only sets game mode 0
  (`sonic3k.asm:454-456`), which then advances the same way.
- A case-insensitive search for "sega" across both disassemblies returns **only** the
  cartridge header strings, the TMSS security write, those advancing routines, and one
  `SegaScr_VInt` reference in `s3.asm:830` that is vestigial — game mode 0 advances on its
  first main-loop pass, so that V-int handler can run at most once.

There is no hold, no timer, and no SEGA sound command anywhere in either file.

**What we do.** `Sonic3kTitleScreenManager` presents a SEGA screen: it plays the SEGA sound,
holds for `SEGA_HOLD_DURATION = 180` frames, stops the sound and moves to the palette
transition.

The chant itself is no longer a presentation addition: since 2026-09-03 the sound it plays is the
ROM's own `zPlaySEGAPCM` transport (`Sound/Z80 Sound Driver.asm:4372-4424`), run by the driver
through the emulated YM2612 DAC at the ROM's own loop cadence. Only the screen and its hold
remain engine additions.

**Why this is acceptable.** It is a presentation addition, not a parity gap — there is no ROM
behaviour being approximated, so there is nothing for the 180 to be wrong against. It affects
only the pre-title sequence and no gameplay state, and it is skippable by input like the rest of
the intro.

**Why it is written down.** Found during the 2026-08-21 timing-constant provenance sweep, where
`SEGA_HOLD_DURATION` was the only constant of six checked with no ROM counterpart — the other
five were ROM-exact. Without this entry the next sweep re-flags it as an uncited constant and
someone goes looking for the ROM value that does not exist. **Whether to keep the screen at all
is a product decision, not a parity one.**

Full context:
[docs/architecture/audits/2026-08-21-timing-constant-provenance-sweep.md](architecture/audits/2026-08-21-timing-constant-provenance-sweep.md).

---







## SOZ Spring Vine: Failed Display Allocation

**Location:** `SozSpringVineObjectInstance`, native `Obj_SOZSpringVine` (`$40786`).

The native initialization does not guard all subsequent child writes after a
failed display-object allocation. OpenGGF leaves the controller active without
a display when no slot is available, rather than emulating writes through an
invalid allocation result. Normal successful allocation retains one later-slot
display child with eight pieces. Allocation-failure RAM corruption is outside
the spring-vine parity claim.








## SOZ Background Event Modes and Torch Animation

**Widescreen arena admission:** `sub_55E96`'s `$4310` gate is evaluated at
its centered native-width viewport origin. Comparing the wider outer left edge
prevented admission at widths 640/800 because the player reached the solid
`$4438` wall first. Native width preserves the ROM condition. This is a viewport
extension; it does not certify wider cold routes or native pixel identity.

**Widescreen pyramid residency:** the Act1 event source window covers the
viewport plus alignment/shimmer margin, instead of repeating its first 512px.
The BG-high color/mask passes use the main pass's source period. Native-width
residency stays 512px. Wider views extend the authored right edge with repeated
ROM masonry. The wide view's right edge locks at the native arena view's edge
(`$4310+320 = $4450`), so the player's `$4438` stop reads as the screen lock and
the foreground wall at `$4500` stays offscreen. The native arena spans 720px; a
wider view lowers the arena minimum below `$4180` and shows level art left of it,
and admission uses the focused player's `$43B0` position. This is
an intentional presentation extension, not native scenery or modified collision.

**Native low-level limits:** failed spring-vine allocation can write foreign SST
bytes (above). Cork quiet-skid polling and the boss charge's failed-allocation
terminal poll can also read bytes belonging to an arbitrary foreign object.
Known SOZ terminal states are represented, but no arbitrary SST-byte service is
invented to reproduce unrelated memory contents.

Open SOZ items (service timing, the module FIFO abort, pixel certification) are
tracked in [S3K known bugs](status/s3k-known-bugs.md) and the frontier log; route,
rewind and compatibility evidence lives in the
[SOZ plan](architecture/plans/2026-09-15-soz-methodology-v2.md) and per-act matrices.

---

## Death Egg widescreen backgrounds

Both cartridge acts use a fixed 320-pixel view (`PlainDeformation` and
`DEZ1_BackgroundInit` / `DEZ2_BackgroundInit`). Wider views intentionally keep
one centred native image. Act 1 reflects 32-pixel strips of its outer wall
tiles. Act 2 has no complete planet sides in the ROM: the renderer reflects
64-pixel surface strips outside the native crop, vertically remapped onto a
circle inferred from the indexed-art horizon. No generated bitmap or terrain
mutation is involved. The original 320 centre pixels and native viewport are
unchanged; the additional scenery is an engine presentation choice, not native
pixel parity. Surface repetition can be visible at the widest aspect.

See the [bring-up audit](architecture/audits/2026-09-22-sk-zone-bring-up.md)
and `TestS3kDezWidescreenBackground` for width, fade and load-boundary checks.


## LRZ1 and DEZ2 widescreen arena framing

The cartridge renders 320 pixels: LRZ1 `word_784E8` fixes camera X at `$2C00`,
and DEZ2 `word_7F0C6` permits `$3400..$34E0`. On wider displays, the engine
centers that original camera window by subtracting half the extra width from
its visible limits (240 pixels at 800). This is an intentional presentation
difference; surrounding level art that the cartridge never displayed at those
locks becomes visible. Native min/max words still define player walls.
`NativeViewportFraming` provides the calculation; the internal zone policy
`NativeArenaCameraFraming` opts in through captured runtime state. It does not
apply indiscriminately to other zones or rewrite their camera behavior.

LRZ arm placement, projectile/debris lifetimes and release thresholds use the
native-framed camera, preserving their world positions. The choice survives
LRZ's seamless act rebase and DEZ's escape, then resets with fresh zone state.
The native 320px LRZ arrival matches all 600 pre-change CSV rows; the 800px
arrival matches the same gameplay rows with camera X exactly 240 pixels left.
This is engine regression evidence, not a new native parity certification.

## SSZ Act 2 Widescreen Background Columns

The encounter's `loc_58F46` / `loc_5904A` supplies twenty VSRAM column words for
320 pixels. `SszAct2Deformation.columns` preserves those words, including the
native forward-copy overlap at the right edge. Wider viewports continue the last
native column's offset through their additional columns. There are no native
words for that extra view; repeating the boundary keeps the extended clouds
coherent without treating adjacent work RAM as scroll data. The native window
and its horizontal wave are unchanged. This extension is engine presentation,
not a claim of a native widescreen reference.


The seeded island presentation also extends its last native visible Plane B
tile column into the extra width. The ROM draws a512px plane but shows320px;
reading the wider authored layout exposed unrelated cloud strips. This separate
projection preserves native columns, priority and vertical sampling. Its native
viewport remains byte-identical in the inspected checkpoint, while the extra
width is an engine-authored presentation extension.


SSZ2's crane and transformation pans project the original320px camera window
into the selected width. Native min/max words, pan completion and player limits
remain separate. The crane holds the visible left edge at zero until enough
world space is available; the transformation pans six native pixels per pass.
At800px its displayed endpoint is336, so the view ends at1136 inside the nine
foreground blocks, instead of extending to1376. The Master Emerald has a
ROM-backed static visual precursor in wide views until `loc_7C818` starts drawing;
this intentionally differs from the ROM's invisible-until-dash allocation, with
no early object slot, collision, palette or PLC activity. Mecha's high hardware
priority is original ROM behaviour, not a widescreen exception.
