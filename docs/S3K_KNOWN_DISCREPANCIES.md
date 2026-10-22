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

**CNZ2 primary-player operand (2026-10-21, local candidate):** the early
vertical-bound gate follows native unsigned `Player_1+x_pos`, independently
of an exported camera-target change. The [component matrix](architecture/validation/levels/s3k-cnz-act2.md#native-p1-vertical-bound-operand--2026-10-21)
records the contradictory registered-P2 controls and qualification limits.
This restores ROM semantics without adding an accepted deviation or changing
the 0.7 candidate API. No stock cold-route impact is established.

**AIZ handoff prerequisite (2026-10-12, candidate):** the runtime performance
qualification repairs shipped-ROM boss/results/button/bridge ownership without
adding an intentional deviation. The complete original AIZ → HCZ control passes;
the alternate still has an inherited allocation frontier. See the
[validation record](architecture/validation/performance/2026-10-12-runtime-performance-series.md)
and [known-bug record](status/s3k-known-bugs.md). Integration and performance
qualification remain pending.

**Stock parity audit (2026-10-07):** the continued swarm uses shipped-ROM
initialization, level-load and fan-child semantics; it adds no accepted deviation.
Executed qualification and remaining campaign/rewind frontiers belong in the
[dated stock audit](architecture/audits/2026-10-07-stock-parity-gap-verification.md#continued-swarm-from-the-delivered-base)
and [known-bug record](status/s3k-known-bugs.md), rather than new intentional entries.

**Authored AIZ lookout withdrawal (2026-10-16):** a local correction preserves
native status7/own-dispatch deletion before parent-slot reuse can strand linked arms.
The [known-bug record](status/s3k-known-bugs.md#authored-aiz-lookout-clears-a-physical-parent-before-its-arms-fixed)
and [delivery evidence](architecture/plans/2026-10-15-second-strike-campaign-delivery.md#a5-owned-graph-retirement--current-source-continuation)
separate focused lifecycle/replay and the completed fixed-input prefix from
whole-route obligations. No accepted ROM deviation or default unknown-slot fields
are introduced.

**Ending presentation scope, 2026-10-13:** the three effects omitted from
playable feature `c2103d5f7` are now implemented and locally qualified: IslandLift
sprites/art, rescue/AIZ Master Emerald palette cycling and AIZ return-hold AnPal.
The [resolved work record](status/s3k-known-bugs.md#native-ending-presentation-gaps-resolved)
and [ending matrix](architecture/validation/levels/s3k-ending.md#repaired-native-presentation-owners--2026-10-13)
record the50 affected focused checks and their limits. The2026-10-14 follow-up
implements retained-byte full-table continuation and managed rescue-plane child
graphs, with41 distinct core cases and77 distinct composed graph/boundary cases.
[Current qualification and remaining pressure/native-scene obligations](architecture/validation/levels/s3k-ending.md#remaining-parity-follow-up--2026-10-14)
remain explicit. Neither these repairs nor prior lifecycle results claim
whole-scene native pixel parity; no accepted deviation was added.

**Ending scroll resource ownership:** a follow-up corrects eager ending-table
reads that broke unrelated no-ROM provider registration. Both native banks are
now deferred until actual ending use and fail closed before RAM/output changes;
no fallback asset or intentional discrepancy is introduced. All 55 affected contract,
scroll/RAM, lifecycle and pixel checks pass; scope is recorded in the
[dated resource-boundary record](architecture/designs/2026-10-13-s3k-ending-lifecycle.md#deferred-ending-deformation-resources--2026-10-13).

**Native white-palette repair (2026-10-17, local candidate):** managed white fades now follow the
host ROM's distinct component routines and wait count, with the same display conversion on backdrop and
water colours. This removes an implementation defect, not an accepted deviation. The
[owner/evidence record](architecture/designs/2026-10-13-s3k-ending-lifecycle.md#native-white-palette-fade-owner-decision--2026-10-17-root)
and [known-bug limits](status/s3k-known-bugs.md#super-emerald-stage-layout-set-and-results-digit-column-open)
retain the remaining scene pixels, timing and native-product breadth; delivery validation is still pending.

**Results presentation repair (2026-10-17, local candidate):** the ROM-backed score/bonus geometry and
native opaque sprite priority are restored. All17 sampled results reveal checkpoints of the declared
Tails320 NTSC seven-Chaos product match original native RGB; stage pixels and broader products remain open.
The [results evidence](architecture/designs/2026-10-13-s3k-ending-lifecycle.md#native-results-geometry-and-painter-order--2026-10-17-root)
records the162 focused passes and the reproducible maintained capture tool. No accepted deviation was added.

**Slot retained-plane restore (2026-10-17, local candidate):** shared derived-cache
invalidation now preserves an exact just-restored authoritative physical plane,
and same-level encoded-layout restore rebinds geometry without a scene reload.
This repairs an implementation defect; no intentional ROM deviation is added.
The [bounded R7G record](architecture/validation/levels/s3k-bonus-stage-transitions.md#r7g-actual-captured-plane-inlet-and-bounded-repair-qualification--2026-10-17)
separates genuine stored-plane old-red/green and five maintained pilot passes
from earlier invalid-inlet evidence, untested finite products and RGB priority gaps.
Root's combined validation and integration remain pending.

**Slot foreground priority (2026-10-17, local candidate):** remove an unsupported
central-square tile priority promotion and preserve complete ROM descriptor words.
No intentional deviation or global priority stripping is introduced. The
[R8 evidence](architecture/validation/levels/s3k-bonus-stage-transitions.md#r8-fixed-five-originals-native-priority-components-close--2026-10-17)
records genuine actual-consumer old-red,54 focused passes and one fixed five-PNG
comparison closing the845 targeted wall/peppermint component pixels with no new
bad pixels. Remaining HUD/bootstrap and tiny RGB differences, native-high-glass
control and full-product/integration qualification remain open.

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
        // (sonic3k.asm:7881-7885, 8143-8158). A duplicate engine event init
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

**Bonus title producer correction (2026-10-17, local preservation):** the actual
Knuckles/Gumball apparent-act-zero owner now queues RedAct, Zone, Num1 and Bonus
through the existing ROM pipeline, including the number archive that its banner
does not draw. This removes an omitted producer, not an accepted deviation.
The independent archive old-red and120 affected cases pass, including pending
owner restore/forward twice. A separate native terrain-parent old-red now also
passes: active bonus entry retains the existing title-gated resource handoff
without enabling the ordinary four-child title graph. R36 covers145 cases in19
classes without failures/errors/skips, including all three actual bonus terrain
oracles and10 short entry/return pilots. Its water-only reflective entry/exit
fixture emits the same full-art-FIFO warning on actual Main803; it does not certify
art readiness. The reviewed geometry body relocation passes99 fresh source,
hardware-authority and exact API guards. R38's original route was denied returned
segment2 admission; an independent public-entry old-red then proved the saved-origin
bonus load was labeled ORDINARY. The narrow synchronous load cause mark is qualified
by128 affected cases and99 fresh guards, including an ordinary-load-after-return
control. R44's unchanged original Mega now attaches/compares returned segment2 and
stops with AIZ2 KosM transition-art FIFO-full. Partial errors are1/12/21196
(20427 physics+769 animation in returned AIZ), first returned non-camera34
module.busy native true/engine false;29 unmatched completion identities remain.
All reports are incomplete, recent5 is bounded, and actual compared/reference row
counts and LEVEL-release clocks are not serialized. SS/fdf remains unvisited.
No unique actual R38 receipt cause, complete ordinal difference, native births,
readiness or full route proof follows. [Exact component and original-route epochs](architecture/plans/2026-10-13-s3k-playable-completion.md#r44-actual-fresh-guards-and-original-returned-aiz-frontier--2026-10-18).


**Saved-origin bonus terrain handoff (2026-10-18, local preservation):** the
existing bonus-return signal now arms the same title/Nemesis-gated terrain
publisher after the active bonus provider has exited. It does not mark ordinary
host loads fresh or alter the bonus title graph, FIFO capacity or readiness.
Three independent Knuckles/AIZ1 public bonus-return native descriptor old-reds
now pass unchanged, with an ordinary non-fresh negative:144 affected tests/19
classes and99 fresh architectural/authority/API guards pass without skips.
Original Mega still ends in AIZ2 KosM FIFO-full (1 error); partial counts are
1/16/21196, returned first34 module.busy true/false,20 unmatched identities.
Gumball's retained1380 direct-queue fields changed; bounded recent arrays do not
prove an exhaustive delta or its cause. SS/FDF remains unvisited; current physical
occupants/submit/lease-consume/service chronology is not supplied by historical
R47/R50 witnesses. No native-birth, ordinal alignment, readiness or full-route
certificate. [Actual component and original-route epochs](architecture/plans/2026-10-13-s3k-playable-completion.md#r56-actual-fresh-guards-then-original-mega--conditional-two-phases-terminal).

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
- **Unsaved persistence requests** - disk persistence routes through `SaveSessionContext`; when no slot is active, persistence requests and disk writes no-op. Native full SaveGame runtime completion still clears the collected-ring mask.
- **Snapshot providers** - game-specific payload capture is handled by `SaveSnapshotProvider` implementations rather than direct SRAM-style writes.
- **Session-owned launch metadata** - active slot ownership, selected team, and launch zone/act are carried by `WorldSession` and `SaveSessionContext` rather than being inferred from config during gameplay.
- **Restricted clear restart modeling** - clear slots use Java-side restart tables reconstructed from the disassembly, including Knuckles-specific restrictions, rather than exposing unrestricted level selection.
- **Native S3K save-screen parity** - the native `S3K` `1 PLAYER` route now renders from the authored object layout and mapping frames; the old RECTI/text-placeholder selector path is gone on that production path.

Native full SaveGame gameplay semantics are retained independently of this JSON
storage deviation. `828bc94d8` routes exactly seven existing native caller gates
through `S3kFullSaveGame`: Results, both HPZ exits, SSZ defeat, DEZ escape, DDZ
ending and Knuckles LRZ StartNewLevel. The common ROM return `loc_C4CC` clears
all 32 bits of `Collected_special_ring_array`, including zero Save_pointer and
SK-alone branches; generic progression persistence, seamless loads, death/reload,
and special-stage/lives saves preserve their distinct semantics. The mask already
belongs to captured game state. Native clears after SRAM writing; Java clears
before its asynchronous persistence request because that payload omits the mask.
This does not claim identical SRAM ordering.

### Rationale

1. **Platform independence** - JSON files work on any OS without SRAM hardware emulation.
2. **Human-readable** - save files can be inspected and manually edited for debugging.
3. **Extensible** - the envelope format supports versioning and per-game payload schemas.
4. **Parity with the original menu flow** - the S3K save screen now follows the original authored layout and selector behavior, while the backend storage remains engine-owned.

### Verification

`TestSaveManager` verifies round-trip write/read, hash validation, corrupt quarantine, wrong-game detection, replacement of stale `.corrupt` artifacts, and no-op unsaved sessions. `TestS3kSaveSnapshotProvider` verifies payload capture includes team, zone, act, lives, emerald count, and clear-restart metadata. `TestS3kDataSelectPresentation` verifies the native save-screen renderer uses authored layout objects and mapping frames instead of the old RECTI overlay path. `TestGameLoop` verifies active-slot saves are written on bonus-stage and special-stage returns, that `S3K` `ONE_PLAYER` routes into native Data Select, and that `TWO_PLAYER`/overlay bypasses do not.

The [S3K lane audit](architecture/audits/2026-10-07-s3k-parity-gap-verification.md)
records 138 focused passes with zero skips, including actual Results completion,
all-bit mask restore and repeated completion, act-1 preservation, and missing/No
Save contexts. Standalone HCZ is clean and the matched chain giant-ring handoff
succeeds. The other six non-tally live callers are not directly newly route-qualified;
persistence-only preservation uses an absent session rather than a successful
disk write. SK-alone is a disassembly/direct-ROM branch contract, not live engine
execution evidence. See the [stock audit](architecture/audits/2026-10-07-stock-parity-gap-verification.md#continued-swarm-from-the-delivered-base)
for combined results and remaining frontiers; whole-route certification and
composition delivery remain separate.

### Manual Validation

- `2026-04-13`: native S3K parity pass captured via `com.openggf.game.sonic3k.dataselect.S3kDataSelectVisualCapture`, which renders the live native S3K Data Select frontend with real ROM assets into `target/s3k-dataselect-visual/native_s3k_dataselect_slot1.png` for inspection.

---







## Tails Flying-With-Cargo Physics

**Location:** Tails flight physics (`TailsFlightController`, `PlayableSpriteMovement.applyGravity`); CNZ1 carry and CPU flight routines (`SidekickCpuController`)
**ROM Reference:** `sonic3k.asm:27632` `Tails_Move_FlySwim` (+0x08 flight gravity), `sonic3k.asm:27593` `Tails_Stand_Freespace` (branch on `double_jump_flag`)

### Original Implementation

ROM `Tails_Stand_Freespace` at `sonic3k.asm:27593-27595` branches to `Tails_FlyingSwimming` whenever `double_jump_flag(a0)` is non-zero, swapping the normal `+0x38` air gravity for `+0x08` flight gravity from `Tails_Move_FlySwim` (sonic3k.asm:27673 `loc_1488C`). The flag is set when Tails picks up Sonic for the CNZ1 carry intro (`loc_13FC2` at sonic3k.asm:26944 writes `double_jump_flag=1`) and is NOT cleared by the ground-release path at `loc_14016` — Tails continues under flight physics until he actually touches the floor.

### Our Implementation

The engine reproduces this behavior with a feature-scoped gate rather than a flat bit check:

1. `SidekickCpuController.updateCarryInit()` sets `sidekick.setDoubleJumpFlag(1)` at the same point ROM `loc_13FC2` writes the flag.
2. The ground-release branch in `updateCarrying()` zeros Tails's `x_vel/y_vel/ground_vel` and keeps the air bit set (matching ROM `loc_14016` at sonic3k.asm:26963-26986). Crucially, it does NOT clear `double_jump_flag` — the ROM leaves it set so Tails continues in flight physics for at least one more tick while the carry-release impulse propagates to Sonic.
3. `PlayableSpriteMovement.applyGravity()` and `doObjectMoveAndFall()` gate flight gravity on `sprite.getSecondaryAbility() == FLY && sprite.getDoubleJumpFlag() != 0` (mirrors `Tails_Stand_Freespace` → `Tails_FlyingSwimming` branch).
4. Tails's CPU flight AI — `Tails_Catch_Up_Flying` (routine 0x02 at `sonic3k.asm:26514`) and `Tails_FlySwim_Unknown` (routine 0x04 at `sonic3k.asm:26574`) — is ported into `SidekickCpuController.CATCH_UP_FLIGHT` / `FLIGHT_AUTO_RECOVERY`, plus the NORMAL → `FLIGHT_AUTO_RECOVERY` transition on a dead leader.

### Rationale

1. **Feature-scoped gate over raw flag** — `double_jump_flag` is overloaded in the ROM: Sonic's insta-shield uses it (values 1-$20 during shield timing), Knuckles's glide uses it (1=gliding, 2=stopped, 3=sliding), and Tails's flight uses it (non-zero = flight-gravity). Gating the flight-gravity substitution on `SecondaryAbility.FLY` prevents Sonic's insta-shield and Knuckles's glide from accidentally acquiring the `+0x08` gravity. The ROM achieves the same scoping naturally because only Tails's code path hits `Tails_Stand_Freespace`.
2. **Plan reference** — See `docs/architecture/plans/2026-04-24-s3k-tails-cpu-flight-ai.md` for the full breakdown of the carry-release and flight-AI ports.

### Verification

`TestSidekickCpuControllerCarry`, `TestSidekickCpuControllerCatchUpFlight`, and `TestSidekickCpuControllerFlightAutoRecovery` cover the state-machine transitions. `TestS3kCnzCarryHeadless` verifies the CNZ1 intro carry-release frame window.

---







## MGZ2 Quake Chunk Source Address

**Location:** `Sonic3kMGZEvents.MGZ_QUAKE_CHUNK_ROM_ADDR = 0x3CBBB4`
**ROM Reference:** `MGZ2_QuakeChunks` (`sonic3k.lst:316889`), read by `sub_517EA`
(`sonic3k.asm:106983-106996`)

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
(`docs/skdisasm/sonic3k.asm:105246-105267`); `BATTLESHIP_WRAP_DIST_POST_BOMBING`
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

Locked-on MHZ subtype `$30` follows that prerequisite before any P1 or camera
claim (`CutsceneKnux_SKIntro`). The 2026-10-13 correction removes the former
character-only admission. Five supported-width manager/restore cases and the
existing cutscene cases pass; standalone component fixtures declare their mode
explicitly. Full cold Knuckles traversal remains a separate route obligation.

---




## SSZ Metropolis orb allocation and first dispatch (resolved local owner, 2026-10-21)

The head retains `CreateChild1_Normal` forward allocation. The first orb now uses
`AllocateObject` globally, stays in routine0 until its own dispatch, and initializes
itself plus at most six global siblings before returning at `loc_7AE12` without an
orbit, collision-list entry or draw. Failed allocation shells are excluded from the
ship's raw child list; successful prefixes persist without retries. The
[act-1 component evidence](architecture/validation/levels/s3k-ssz-act1.md#mtz-orb-allocation-and-first-dispatch--2026-10-21)
tracks managed slot order, publication and rewind boundaries. This closes the
source-supported initializer omission; current cold-route impact and new native
acceptance remain unmeasured. Existing freed-parent lifetime limits remain open.

## SSZ opening bridge top contact (resolved local owner, 2026-10-14)

`sub_45026` passes SolidObjectTop d1/d2/d3=`$60/$10/$09`. New contact at
`loc_1E44C` uses d3; `loc_1E45A` accepts unsigned word overlap`[-$10,-1]`
and writes relative Y before Player_TouchFloor. The former generic profile
selected d2 and a later absolute snap. Existing provider semantics now model
that native call. Ten literal controls, original-controller full-world replay
and existing bridge/profile consumers pass54/0/0/0. This fixes identified
native poll1342 acquisition, not whole SSZ RGB/audio or campaign parity.

The corrected passive capture now matches all1432 native P1/camera/counter/
ring observations. Maintained320/800 completions retain50/44 two-cycle
whole-world windows and actual DEZ incoming history retirement. The separate
P2 arrival discrepancy remains open: one X row and74 Y rows within119–209.
Native continuation allocation and dispatch order still need exact ownership;
no fitted delay or accepted intentional difference is claimed.


---

## Large-bubble character branch (resolved local owner, 2026-10-14)

`Obj_Bubbler` compares `Obj_Sonic` at sonic3k.asm:64759. Registration aliases
such as`sonic_p2` are not native character routines. Sonic's rolling branch
clears Roll and restores9/19 with Y−5; `loc_2FC5A` retains Roll and writes
literal9/15 with Y−1 for other native characters, including Knuckles whose
usual standing radius is19. The local object now uses persistent identity
and preserves the native center-word adjustment. Seven actual Sonic/Tails/
Knuckles alias vectors pass, as do six legacy pickup/priority/RNG cases and
two recreation checks. These focused checks do not certify HCZ routes, native
whole-scene/audio parity or every supported donor/team interaction.

---

## Air Countdown Digits: Rebuilt Mapping Frames Instead of VRAM DMA

**Location:** `Sonic3kObjectArtProvider.loadAirCountdownDigitArt()`, `S3kAirCountdownObjectInstance.java`
**ROM Reference:** `sonic3k.asm:33360-33367` (`AirCountdown_Init`), `sonic3k.asm:33529-33556` (`AirCountdown_Load_Art`)

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

- `Process_Kos_Queue_Main` (`docs/skdisasm/sonic3k.asm:2877-2878`) sets it with
  `ori.w #$8000,(Kos_decomp_queue_count).w` — commented in the disassembly as
  "set sign bit to signify decompression in progress" — immediately before entering
  `Process_Kos_Queue_Loop`.
- `Process_Kos_Queue_EndReached` (`docs/skdisasm/sonic3k.asm:2970-2973`) clears it with
  `andi.w #$7FFF,(Kos_decomp_queue_count).w` when the stream ends.
- `Set_Kos_Bookmark` (`docs/skdisasm/sonic3k.asm:2851`) only **reads** the bit, to decide
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
  (`docs/skdisasm/sonic3k.asm:5419-5420`); the S3 half is the same with an explicit `rts`
  (`docs/skdisasm/s3.asm:4801-4803`).
- `JumpToSegaScreen`, the handler for game modes `$10` and `$18`, only sets game mode 0
  (`sonic3k.asm:470-472`), which then advances the same way.
- A case-insensitive search for "sega" across both disassemblies returns **only** the
  cartridge header strings, the TMSS security write, those advancing routines, and one
  `SegaScr_VInt` reference in `s3.asm:852` that is vestigial — game mode 0 advances on its
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


Presentation correction (2026-09-27): the cold Knuckles final-fight movie
at800px shows black pixels beneath the floating island and missing cloud bands
(e.g. input4658). The unchanged develop58001b58e reproduces the gap with every
frame rendered; the concurrent glide-reset candidate is not its origin. The
native320 capture shows clouds in that region. Existing ending-plane extension
checks did not certify this earlier encounter mode. The shared finite-world
clip now tests camera/world coordinates rather than deformed texture samples,
preserving native cloud wrapping inside the visible level. The focused GPU
regression reproduces the old missing interior pixels and covers both physical
edges, drift directions and sprite-priority masks. See the SSZ2 matrix and
campaign audit for execution and delivery status.

Knuckles terminal overlap correction (2026-10-17): the eyecatch now preserves
native child order during SAT collection and reverse order during direct
drawing. Its declared native 320 NTSC Super-emerald checkpoint matches the
emulator's original active RGB exactly after a 70-pixel old-code GPU failure.
This is one SSZ2 component checkpoint; whole-terminal, cold, PAL/PCM, wide and
donor presentation limits remain in the
[ending matrix](architecture/validation/levels/s3k-ending.md#knuckles-eyecatch-native-sat-priority--2026-10-17).

## Sprite_OnScreen_Test lifetime audit

LRZ Fireworm defeat correction (2026-09-29): body retirement previously cleared
collision but froze the segments indefinitely. `loc_8F8F0` sets d0 to zero before
`Child_DrawTouch_Sprite_FlickerMove`; `Set_IndexedVelocity` therefore selects the
first four velocity pairs, reflected by render flip. Segments now execute
`Obj_FlickerMove` gravity, alternating visibility and unsigned offscreen deletion,
including after orphan recreation. Visible head/body/flame palette 1 already
matches `ObjSlot_Fireworm` / `ObjDat3_8F9FC`; palette 3 is the invisible spawner.
Both acts' loaded-art checks protect that distinction. See the LRZ act matrices
for verification scope; this is not whole-scene native visual certification.

LRZ1 sinking-rock art correction (2026-09-29): `Obj_LRZSinkingRock` uses
tile `$0D3`, palette 2, rather than `ArtTile_LRZMisc` (`$3A1`). The registry
now binds the rock to its terrain tiles; Act 2 retains its separate `$090`
base. The loaded-sheet regression checks both acts' ROM mappings and tile
bindings. See the LRZ1 coverage matrix for validation and visual evidence.

The LRZ cold-route audit based on `bc4e3285d` disproved the old guide claim
that `Sprite_OnScreen_Test` only draws. Its out-of-range branch clears the
respawn entry and deletes through `loc_1B5A0` (sonic3k.asm:37302–37318).
LRZ sinking rocks, landed spikes, smashing platforms and intact collapsing
bridges incorrectly suppressed this unload, retaining slots that change later
button/door execution order. The LRZ1 matrix records the correction and checks.

A sibling source audit found the same incorrect rationale in
`HCZWaterWallObjectInstance`. Its per-phase range contract still needs a
separate ROM audit: the actual cleanup countdown must remain persistent, but
phases reaching `Sprite_OnScreen_Test` cannot be dismissed as drawing only.
HCZ runtime behavior is unchanged by the LRZ correction; existing trace success
is not evidence that every offscreen phase is faithful. The DEZ energy-bridge
and LBZ cup providers checked during this audit already implement range tests.


## LRZ boss lava-plane presentation

The boss pool now uses its ROM high-priority background tiles over the
low-priority foreground lava wall, including the per-column slope and matching
sprite mask. The background source window follows the arena instead of retaining
the initial Death Egg image. Native-width fight footage confirms the visible
pool; synchronized native pixel comparison and widescreen route coverage remain
open in the [boss-act matrix](architecture/validation/levels/s3k-lrz-boss.md).
The ordinary cold Sonic+Tails route reaches HPZ with deterministic rewind at
62 boss-act/exit spots; this does not certify the remaining width, donor and
lifecycle combinations.

LRZ horizontal-button orientation correction (2026-09-29): both act skins now
preserve placement flips, matching `Obj_LRZButtonHorizontal`
(`ori.b #4,render_flags(a0)`). The draw call previously forced both flips off.
Press/release and recreation checks cover both acts and all four orientations;
inherited route and whole-scene presentation gaps remain in the act matrices.


LRZ Toxomister correction (2026-09-29): the stalk uses its native sub-sprite
centre, and only the seven animated puff children draw the cloud. ROM growth
and shrink/delete scripts replace the static tiny puffs; the native brief
upward dispersal remains. No intentional discrepancy is introduced. See the
[LRZ1 evidence and capture limitation](architecture/validation/levels/s3k-lrz-act1.md#toxomister-presentation-and-dispersal-2026-09-29).

Toxomister facing follow-up (2026-09-29): the mapping flip now matches
`Find_SonicTails` / `Change_FlipX` (set bit0 when the nearest player is right),
including the side of the next breath. Facing away was an implementation error,
not shipped-ROM behavior.

LRZ lava-fall art correction (2026-09-29): `loc_436EE` uses literal tile `$0D3`,
not `ArtTile_LRZMisc` (`$3A1`). The drop sheet now binds its four 4x4 pieces
to terrain tiles `$11B–$13A`; the mappings normalize `$48/$58` to sheet offsets
`0/$10`. This resolves the garbled drops without changing their timing.

LRZ swinging spikeball chain correction (2026-09-29): chain rendering no longer
subtracts the ball radius from `sub_43604`'s centre coordinates. Its own mapping
already supplies the piece offsets, so the links and ball share their anchor.

LRZ rock-crusher visibility correction (2026-09-29): initialization now submits
`ArtKosM_LRZRockCrusher` and the boss-explosion PLC at `loc_90188`, uploading
and refreshing the registered level sheet. Previously only palette/children
were initialized, leaving the crusher's tile bank unloaded. The pre-existing
`loc_90368` enemy-art requeue omission remains outside this visibility fix.

LRZ fireball-launcher flip correction (2026-09-29): launcher rendering retains
both placement flips, and `loc_42C1A`'s copied render flags now survive shot
creation, movement and rewind recreation. Previously leftward shots retained
rightward artwork, and both parent/child draw calls discarded vertical flips.

LRZ1 miniboss arm explosions (2026-09-29): `loc_78B86` now creates the
subtype6 explosion controller instead of one visual explosion. Each of the12
retiring ring parts emits three randomized bursts at three-frame intervals.
The hand-to-anchor retirement delays and surviving opposite arm are unchanged.
A follow-up to `1f620513a7` registers the missing LRZ1 boss-explosion renderer
against PLC tile `$500`: the earlier sequence-only fix still left every burst
invisible. This also supplies the main defeat and crusher explosion drawing path.

LRZ1 miniboss main defeat explosions (2026-09-29): `loc_78C60` now allocates
the subtype0 controller, preserving its31 randomized bursts across the
drill/body’s defeat. Previously that controller was reduced to one visual
explosion. The firing hands’ three-burst sequences are covered by the arm fix.

LRZ1 ending signpost pole (2026-09-29): `Obj_SignpostStubMain` calls
`Child_GetPriority`, copying both the sign face’s sprite bucket and art priority
bit. The pole now does the same, preventing high-priority foreground tiles from
hiding it below an otherwise visible sign. This shared child behavior also
applies to the other S3K signposts without a zone-specific rendering exception.

### SSZ arrival presentation correction (2026-09-30)

The arrival beam no longer draws a floating teleporter pad: the ROM multi-sprite
renderer skips the main image when its mapping frame is zero. Title-card entry
also preserves an already-locked arrival camera instead of forcibly snapping it
to the player and clamping it to the floor bound. The small rise to camera
`$BA8` followed by settling at `$BC0` remains intentional ROM behavior.
See the [Act1 matrix](architecture/validation/levels/s3k-ssz-act1.md#arrival-camera-and-beam-presentation-2026-09-30)
for focused contracts and inherited visual gaps.

### SSZ opening Death Egg mask (2026-09-30)

The opening also restores `loc_65794`'s separately allocated camera tracker
(`loc_66072` / `loc_6607E`). It publishes the signed, word-wrapped half-camera
delta before the Death Egg, its mask and its cloud update. Without that owner,
`_unkFA84` stayed zero and running right pulled the rising Egg too far left.
The helper's previous-camera word and initialization state are captured for
rewind and recreated with the cutscene graph.

Resolved the rising Death Egg drawing through the skyline: `loc_65B24` sets
`Spritemask_flag`, and SSZ now enables the SAT post-pass used by its frame `$C`
mask. The low art priority and bucket `$380` remain ROM values. See the
[Act1 matrix](architecture/validation/levels/s3k-ssz-act1.md#ssz-presentation-fixes-2026-09-30)
for focused evidence and remaining coverage.

SSZ opening bridge (2026-09-30): resolved the invisible outer sections.
`sub_45026` supplies three frame-zero subsprites spaced `$40` apart. The
renderer now draws the full `$C0` span and preserves the ROM art priority.

SSZ elevator facing (2026-09-30): `loc_45400` now clears both render flips
when grabbing a player. The native `$E5`/`$E9` mapping selection keeps logical
facing intact and avoids reversing the left hang pose.

SSZ EggRobo attachment (2026-09-30): the body now renders from its live native
position rather than inherited spawn coordinates. Gun and flame copy the body's
horizontal and vertical render flips in MAIN, including mirrored child offsets;
raw birth and INIT keep their cleared flips and unmirrored birth positions.
The muzzle flash copies only horizontal facing and always starts at GunY-4.
`sub_91930` retains delayed hover Y, falls back to live Y while `$32` is zero,
and latches gun position/flags during firing. The flame uses live Y from
`Refresh_ChildPositionAdjusted`. Scaled fly-by art remains an inherited gap.
The fly-by now retains the native waiter's saved-return low word `$151E` in its
Y fraction when mode0 initializes (`Obj_EggRobo $91518`, `loc_91874`, `sub_86180`).
Both historical Main and the preceding candidate cleared that residue. Focused
component controls exercise the resulting public-position carry and destructive
restore/forward replay before and after initialization; they do not measure its
ordinary route impact or explain the Main/current outcome difference. The separate
generic coarse-X unload path is unchanged. See the
[fraction qualification](architecture/validation/levels/s3k-ssz-act1.md#native-waiter-residue-in-fly-by-y--2026-10-21)
for exact source, red/green results and remaining route/native limits.
The [bounded enemy-art request leaf](architecture/validation/levels/s3k-ssz-act1.md#enemy-art-production-and-restoration-requests--2026-10-21)
adds native title-owned `17B17E -> A000` and post-pairing fly-by restoration
requests, each paired with real physical/timed work and one claim owner.
The [incidental-backpressure followup](architecture/validation/levels/s3k-ssz-act1.md#incidental-producers-and-armed-title-backpressure--2026-10-21)
retains armed title intent when either FIFO blocks and rejects an incidental
request that has no logical slot, without adding a retry owner or claiming
native overflow parity.
It retains the existing copied-module DMA contract. Authentic shared scratch
writes, live RAM reads at DMA drain and displayed residency generations remain
open; prepared standalone EggRobo sheets do not prove those properties.
The [bounded vertical-flip correction](architecture/validation/levels/s3k-ssz-act1.md#vertical-facing-and-child-y-offsets--2026-10-20)
is qualified with explicit flags0/1/2/3 component inlets, CPU/SAT replay output
and fresh complete-world restoration. Stock SSZ1 placements contain only flags0/1;
natural vertical-facing route relevance and native pixel parity remain unproved.
The inherited Shot shield descriptor now retains native byte `$2B=0` and
capability `NONE` through charge and motion. Real-shield contact still gives
ordinary shield-loss hurt; the former false descriptor only enabled a redundant
no-op deflection callback in the inspected stock consumer. The
[bounded shield contract check](architecture/validation/levels/s3k-ssz-act1.md#eggrobo-shot-shield-descriptor--2026-10-21)
does not establish a cause or correction for the separate cold-route failure.
The specialized animal launch is now source-backed and independently qualified:
fixed Rabbit/Chicken table, one full RNG draw, captured parent facing, direct
WALK/FLY entry/frame1 and stationary first draw, with persistent native render
flip through recreation. This resolves that component's launch gap, not the
cold Sonic campaign: the unchanged original case still dies at49613 after the
repair; actual coldAnimal629 parent/callback and first post-fix split remain
unobserved. [Bounded tests, source pin and limits](architecture/validation/levels/s3k-ssz-act1.md#r29-native-eggrobo-animal-launch-unit--2026-10-17).

- SSZ EggRobo rewind restores its live x/y spawn metadata after the base badnik
  restore; recreated moving bodies and attachments retain the captured position.

SSZ diagonal collapse (2026-09-30): the intact bridge stops drawing after
fragment allocation. Debris inherits the native flip and art priority, and
retains the full6..48 word delays instead of truncating them through the
two-bit `ObjectSpawn.renderFlags`. The shared flat-bridge fragment owner uses
the same corrected delay/flip contract and stops drawing its intact image.

- SSZ rotating post/carrier player poses use the ROM table's direct render flips
  and retain incoming facing, keeping the displayed pose aligned to the rotation.

SSZ EggRobo position restoration uses standard generic/badnik capture: inherited
position state stays synchronized to native x/y, without a custom restore hook.

- SSZ1 cloud-mode transitions keep the background source window aligned to the
  displayed vertical scroll, eliminating the blank entering frame. Rewind
  re-render restores the displayed mode and intermediate HScroll words.


## Blue Sphere player projection and shadows (local correction, 2026-10-17)

Native `sub_953E`/`sub_A206` project the main player and sidekick using
ROM ScalarTable2 and their different depths; the former linear screen offset
put their ground centres at 160/172 instead of 162/179 and distorted jumps.
The renderer now uses the ROM integer projection. `Draw_SSShadows`' fixed
4x4 shadow operators are also drawn behind ordinary sprites after the boot
fade, with the publication phase captured for rewind. The
[sanctuary matrix](architecture/validation/levels/s3k-hpz-sanctuary.md#blue-sphere-presentation-components--2026-10-17)
records old-source failures and 106 focused passes. A new actual framebuffer
comparison, remaining sphere/HUD phase differences, whole routes, donor,
PAL and PCM remain open; component passes do not certify those products.

Composed native RGB follow-up (2026-10-17): the actual seven-Chaos v8 capture
reduces stage reveal+22 from1112 to330 differing pixels, confined above y58;
all pixels at y128..223 match at +0/+10/+22. The17 sampled results frames
remain exact and both state/event CSVs are byte-identical to v6. See the
completion plan's composed presentation qualification. This certifies those
sampled native320 Tails/NTSC/Limited-RAW_RGB pictures only; upper HUD/distant
object phase and the wider route/configuration/PAL/PCM obligations stay open.


Distant-sphere follow-up (2026-10-17): the actual ROM mapping table aliases
several far-depth frames. Using those bytes passes138 focused checks without
skips and reduces stage reveal+22 from330 to46 raw RGB differences; the17
sampled results frames remain exact and gameplay/event CSVs are unchanged.
The remaining far-sphere overlap/priority gap is explicit in the
[completion plan](architecture/plans/2026-10-13-s3k-playable-completion.md#blue-sphere-distant-mapping-frames--2026-10-17).
These sampled native320 Tails/NTSC/Limited-RAW_RGB pictures do not certify
complete stage/cold routes, donor/PAL/PCM or other native configurations.


Rewind fade-publication follow-up (2026-10-17): the composed white-fade change
left a restored displayed generation held in scenes without a level upload latch.
The normal scene release now flushes that independent hold. Old-source gateway
and short latch assertions fail; corrected focused112 and complete sanctuary
entry/return matrices2006 pass without skips. The [completion plan](architecture/plans/2026-10-13-s3k-playable-completion.md#immediate-scene-fade-publication-after-rewind--2026-10-17)
records exact commands and scope. Native RGB residuals, strict traces and
configuration/cold/PAL/PCM limits outside these products remain open.


## Retained HCZ replay qualification — 2026-10-17

Worker source `b9c1c8b3d` closes the retained stored-carrier-target/X-lock and
water-owner extraction with focused/rewind/source evidence, but strict replay
remains red. Returned HCZ's first reported non-camera physics is7134 `sidekick_x`
(`0175/0176`), with133771 total errors over a longer window through14207 and no
owned giant-ring exit. Its summary does not expose the first animation error or
full span history. These are b9/866 observations; the returned HCZ route has
not been requalified on the subsequent load-owner fix. That bounded followup
moves Nemesis submission to the real load and closes first-title-loop preparation;
its canonical prefix fully closes segment8's four7126 fields and reaches the first
segment9 row. Independent old-red/green, focused134 and named guards131 pass
without skips. This is not a full canonical or all-auxiliary qualification.

Pre-fix matched dc338/866 HCZ-slice windows have4699/4353 spans and the same9482
`air` (1/0) frontier. Exact span/field-row comparison is mixed, with newly
mismatching CPU respawn counter at10834; historical3738 is not a matched baseline.
The full slice is not requalified on the new source. The distinct alternate is
requalified and retains exactly one26179 `rings` span (72/0).
[Followup evidence](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#bounded-followup-qualification-and-limits).
The engine pixel oracle fails at GLFW init; native-only 320 Sonic+Tails images
and passing named guards do not certify missing auxiliary channels, other
products or downstream campaign. [Exact evidence and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#final-single-invocation-strict-qualification),
[frontier log](status/trace-frontier-log.md#2026-10-17--retained-hcz-repair-on-root-stored-targets-and-qualified-limits).

Blue Sphere SAT/plane follow-up (2026-10-17): the private indexed compositor
resolves the first opaque sprite pixel before Plane A priority, preserves
native cell/mapping-piece order and the original unsigned `$54` gate, and
keeps players/HUD sprites before the grid and shadows after it. The fresh
maintained v11 capture has zero raw RGB differences at all 12 original-native
stage reveal checkpoints (+0..22 by 2), replacing the prior 19..46 residuals;
all 17 results samples remain exact. Its entire state.csv and ledger.csv are
byte-identical to v10, including LRZ2 return frame 2423. The [implementation,
focused GPU/resource checks and exact capture protocol](architecture/plans/2026-10-13-s3k-playable-completion.md#blue-sphere-sat-ownership-and-plane-a-composition--2026-10-17)
record the bounded result. This repairs the sampled overlap gap without an
accepted ROM deviation; complete timelines, other native configurations,
cold/donor/PAL/PCM and strict timing/trace products remain separate obligations.


R3 HCZ follow-up (2026-10-17), local worker merge21327d78a with Main460:
full canonical6/1F/0E/0S reaches returned HCZ11's complete14208-row reference
window but lacks the giant-ring exit;134710 field errors split115689physics and
19021animation, first reported non-camera6674 `rings`114/0. Independent full
HCZ slice2/1F/0E/0S reproduces all4353 spans(3424physics/929animation), first9482
air and9486 animation, with Poindexter passing. These are completed strict-red
qualifications, not new product certification. Matched dc3384699/8664353 remains
mixed,3738 unmatched; alternate760's one26179 ring span is reused, not freshly
passed. Active-level title-parent/movement-latch causality and the exact GLFW
oracle remain open; no fitted delay or separate fresh-load fix was substituted.
[Commands, windows, all groups and causal limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r3-current-composed-strict-qualification--2026-10-17).


R4 HCZ retained-title correction (2026-10-17): the real parent survives until
its higher-slot children stop writing the movement latch. Native reset follows
last movement→parent clear→stationary parent poll; short whole-world replay twice,
focused182 and guards131 pass. New canonical chain first reported non-camera
frontier is7134 sidekick_x0175/0176, closing6674 rings;133771 field errors remain
over the complete14208-row returned-HCZ window, with its giant-ring exit absent.
The distinct independent HCZ slice reproduces all4353 spans/first9482air and
passes Poindexter. No clean route/auxiliary/product/GLFW certification or second
fix; alternate760 reused, matched4699/4353 mixed,3738 unmatched.
[Exact native evidence, selectors, windows and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r4-retained-title-parent-and-child-movement-latch--2026-10-17).


### AIZ animal lifetime correction and remaining new alternate rings (2026-10-17 R5)

The ordinary subtype0 freed-animal owner now uses the preceding Render_Sprites
bit7, with MAIN polling before motion and walk/flight after routine work. Its
render width8/height0 and copied-camera word-mask bounds remain separate from
collision y_radiusC and S2's approximate Y bounds. Independent natural flight,
hole reuse, edge/wrap and twice-recreated whole-world replay checks pass, as do
73 affected cases, original AIZ, all20 exact standing assertions and the two-SS
prefix. S1/S2 behavior, animal physics/RNG/points and allocators are unchanged.

This useful owner correction does **not** resolve the alternate ring blocker:
39799 retains R3's11 ring entries, first13740rings1/2; a matched published803
control78232 has only trailing26179rings72/0. The ten early spans are new and
cannot be waived as inherited. The same animal correction alone is disproved
as their complete explanation. Post-fix ring/SST causality, nonzero-subtype and
ending/capsule animals, four native68E98 debris, fullaux/configuration/BK2/cold/
viewport/roster/fullchain coverage remain open. Local result, no Main delivery.
[Exact commands, profile qualification, evidence and limits](architecture/validation/levels/s3k-aiz2.md#r5-low-sst-journal-and-animal-lifetime-owner--2026-10-17).


### AIZ component lifetime correction closes the new alternate rings (2026-10-17 R7)

Normal body/arm/three barrels now keep their own SST dispatch through fatal
status7 and the literal fade wait, then retire one own entry after the physical
parent's bit4 tail. Captured known parent XY/render/$38/status survives same-slot
replacement; body animation and active barrel callbacks continue before the
separate touch/deletion gates. FallingShot reads that physical grandparent after
its own routine, including a final replacement-pass birth. Independent natural
production/forced-recreation controls and65 affected checks pass; original AIZ,
all20 standing assertions, twoSS and exact affected guards pass.

The once alternate72859 has only26179rings expected72/actual0, the exact remaining
published803-control identity/values; ten new13740..16669 spans are resolved.
The raw replay is still red for that inherited trailing field. Full aux/native
SST/phase/position comparison, four omitted68E98 debris and cold/otherBK2/config/
viewport/donor/roster/fullchain coverage remain open. This is a local owner
qualification; Root owns composed validation and Main delivery.
[Source, old-reds, review findings, commands and limits](architecture/validation/levels/s3k-aiz2.md#r7-physical-parent-and-five-child-defeat-retirement--2026-10-17).


R10 HCZ internal asset ownership (2026-10-17): production HCZ1→HCZ2 preserves
unwritten128×128 destination bytes; encoded native word lookup reads current
16×16/layout assets and positive MOVEA.W aliases from a bounded immutable copy
of the current ROM. Independent old-red/green and twice-recreated whole-world
mutation/scene/ROM-binding replay pass. Original prefix49095 closes7134's wall:
FG31/BG-1/combined-1, P20175.F800/vx0170/g0/pushing matches native. Through7135,
returned HCZ consumes7136/14208 rows with206 errors (11camera physics and195
animation), no non-camera physics mismatch, and no later frontier measured.
Full returned-HCZ, independent slice, alternate and exact GLFW remain unqualified
on the new source; this is not route certification. Focused mock error and two
MenuAudio source-guard failures are exactly attributed as inherited, not passes.
[Commands, prior refusal boundary, source hashes and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r10-encoded-collision-words-resident-destinations-and-rom-aliases--2026-10-17).


R11 HCZBlock Full2 owner (2026-10-17,152d07 composition): native loc1F3CA
bypasses both player and object offscreen gates. Existing object-local capability
now preserves underside separation offscreen. Independent corrected old-red
3/2F→visibility/world-replay4 green,157 total focused/118 affected guards pass
without skips. Original once prefix7687 closes7807 sidekick_y and matches7808;
HCZ7809/14208 rows remains206=11camera physics+195animation, no non-camera
physics mismatch through7808, warnings/bootstrap0. First remaining all-group
error3475 sidekick_animation_id17/1C; later non-camera frontier is unmeasured.
No full returned-HCZ/exit/slice/alternate/GLFW or route certification is claimed.
[Native source, exact controls/commands/hashes and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r11-hczblock-full2-visibility-owner-closes7807-through7808--2026-10-17).


R12 current composed HCZ qualification (2026-10-17,bef8 unchanged): one maintained
full canonical17194 compares all14208 returnedHCZ rows,125612 total field errors
(107498physics+18114animation),first non-camera8329y_speed0000/-0680. Earlier
six windows complete zero-error, but giant_ring exit remains absent; no onward
segment/playable exit. Four suppressed unmatched hardware completions are
unattributed. Separate slice witness proves10834counter0/1 is a correct consumer
of10833cached offscreen caused by enginecamera copy019A versus native0199 at
an inclusive left edge. Fresh-entry delay is disabled; no CPU/cache/runtime fix.
Selected P1/history/camera-latch producer remains unqualified. No extra selection,
native or broad run, and no general green.
[Exact fixtures, commands, clocks, windows, phase correction and handback](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r12-slice-cached-visibility-consequence-and-full-composed-canonical--2026-10-17).


R13 residency smoke regression repair (2026-10-17): DDZ component fixtures now
select their existing native fresh-start/no-floor inlet; manual undeclared FF82AF
terrain lookup still explicitly rejects unproduced definition20. LBZ's Death Egg
launch copies two encoded row-pointer words, preserving source bytes and later
mutation aliases through two forced whole-world restore/replay cycles. Exact
pre-edit15/1F/14E and independent ROM-pointer1/1F -> final86 focused/118 affected
guards all pass without skips. This does not requalify HCZ8329/exit, independent
slice/alternate/GLFW or full routes; no CPU/camera/unknown-RAM fallback changes.
[Exact owner evidence and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r13-residency-smoke-regressions-native-row-pointers-and-ddz-fixture-entry--2026-10-17).

R14 camera-input witness (2026-10-17, unchanged0cd178d8f production): original
82EA prefix through10835 remains104spans/10773 rows, first9482air1/0 and
10834counter0/1. Actual10833delay1 selects enginehistory023A, while current
P1word0231;186+(23A−186−A0)=019A is correct. Native selected table contents,
H_scroll, forced/latch state and camera-entry P1 are unobserved; final poses and
Pos_table_index are insufficient. No Camera/CPU repair or parity claim. Existing
named RecordPos10D80 pin is an operand in the actual ROM; verified entry10D66
is recorded for future plan review without changing capture authority/fixtures.
[Exact original inputs, phases, source pins and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r14-slice-camera-entry-history-inputs-no-camera-or-cpu-repair--2026-10-17).

R15 native camera input (2026-10-17): the missing native selection is now observed
in one original82EA power-on pass, all four endpoint controls/60 callbacks match.
10833 RecordPos writes0239.E0; delay0100 selects0239 ->0199. Engine R14 history
023A ->019A is also correct. This moves the causal input question upstream to
P1 arriving at RecordPos, not Camera/CPU. Exact earlier engine writer remains
unproven; no runtime fix or new engine/route/visual qualification is claimed.
[Actual byte-verified instruction phases and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r15-original-native-camerahistory-input-plan--2026-10-17).



ICZ1 Knuckles snow-free camera gate (2026-10-21): the native Player_mode3
branch skips ICZ1_BigSnowFall, so its zero snow offset must not block loc_1C9FA
at camera X=$3940. The existing snow-pile wait now applies only to the other
player modes; Sonic retains its settled-pile jump/control-release owner. The
new explicit camera-word component reaches both native thresholds through the
normal event handlers and replays the complete registered world twice. Its
predecessor failed1T/1F at expected background8/actual4; the affected five-class
selection passes29T/0F/0E/0S. This is later event progression, not the CNZ arrival
at player X=$3640 or a cold Knuckles route/ICZ2 handoff qualification.
[Source, commands and limits](architecture/validation/levels/s3k-icz-act1.md#knuckles-snow-free-camera-event-progression--2026-10-21).

ICZ2 crushing-column decoration reconstruction (2026-10-21): the ordinary
partial crossing exposed a strict second-restore mismatch in the child's
immutable birth spawn. `BottomDecoration.recreateForRewind` now retains the
captured record instead of recomputing it from a restore-phase parent position.
Live child Y still follows `parent.y + $B0` (`loc_8A612`); ordinary movement,
solid contact and drawing are unchanged. The original 35T/1F group and isolated
1T/1F captured-context diagnosis remain recorded. The affected group retains
106 passing cases; the separately corrected exact route passes1T/0F/0E/0S with
ten complete-registry rewind windows. Source/input applicability corrections and
partial-route limits are tracked in the [ICZ2 matrix](architecture/validation/levels/s3k-icz-act2.md#maintained-ordinary-partial-crossing--2026-10-21);
this is not a boss, full-act or native presentation certificate.

ICZ R11 synchronous retained16 owner (2026-10-17): Load_Level now preserves
known outgoing pattern groups until existing deferred publication, using current
target collision indices. Unknown placeholder capacity is not exported as RAM;
owned opaque restore also restores saved extent. Independent real-handoff old-red
1/1F -> construction/publication77green; review P2 opaque688/718 old-red1/1F ->
affected4green, with two forced world restores/replays and no skips. These are
separate source epochs. Cold/async/presentation16 carry remains unchanged empty;
R8 natural death/reload, full aux/native/strict trace/routes and combined smoke
remain open. [Exact evidence and limits](architecture/validation/levels/s3k-icz-act1.md#r11-synchronous-known16-handoff-repair--2026-10-17).

R11 ICZ final five exact source/API guards also pass without skips (89633);
Root's bounded P2 resolution inspection has no remaining material objection.
No extra construction/trace/broad re-execution or integration clearance follows.


R20/R21 spinning-column owner (2026-10-17, local f196-based candidate): native
cached offscreen release preserves OnObj/kinematics, while the existing native
column standing latch permits subsequent recapture. ClearHeldFlag alone does
not clear control. This repairs an implementation defect, not an accepted ROM
deviation; no shared solid/CPU/camera/cache/API changes. Genuine independent
old-red→green,73focused0skip, twice-world release/recapture and original native
8715..20endpoint equality are qualified. Separately authorized actual final
report95476 compares8721/14208, completefalse:206=11camera physics+195animation,
firstNonCameraNULL through8720, lag59/warnbootstrap0. Same-window10733 is213
(15/198), but bounded recent5 does not prove exhaustive residual-array identity.
Strict1F remains; first all-group3475animation17/1C. No later frontier, full
returned-HCZ/exit, independent slice/alternate/GLFW/native or product breadth
certificate. [Exact source, commands, rejected JUnit-only inference and report](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r21-actual-final-prefix-report-qualifies-the-local-column-repair--2026-10-17).


## DEZ final native terrain ownership — 2026-10-17

A validated matching synchronous native continuation now retains only the stock
predecessor's actual written 128x128 definitions and known 16x16 pattern groups.
The final scene's ROM prefix overwrites that state; current final collision
indices remain SolidIndexes[46decimal] `$09CFC0/+1`, not ordinary DEZ's `$09B1C0`.
Cold/manual/nonmatching and captured prepared builds do not borrow arbitrary
live geometry. The prepared negative observed REQUESTED, not an independently
executed LOADING prepared task; the latter refusal is source-reviewed only.

`DEZ3_ScreenInit` authenticates the independent test oracle at `$05A3DA..$05A400`.
Its actual header `0014/0008/0004/0004` is 20/8/4/4 decimal; low-word products
plus `$88` select `$F8`, and inclusive DBF clears `$FF80F8..$FF8FFF`. The encoded
owner performs that once-only scene write through the existing mutation pipeline,
with COW and flattened aliases; no generic layout decoder truncation is used.
Target resources, meaningful prefix and subsequent legitimate background edits
survive twice-forced registered-world recreation and forward replay.

On base `271457b316145fbc8549ad7fd09cd71007d333cb` plus the reviewed changes:
independent tail old-red76475 is 2/2F0E0S at final `$FF80FF` expected0/actual6;
affected74516 is **122/0F0E0S** (all `$F08` tail assertions reached), fresh five
source/API guards5039 are **5/0F0E0S**, and unchanged original
`TestDezIncomingFinalRouteCapture`54531 is **7/0F0E0S**. The five formerly failing
solo/incoming cases and the two team cases pass with their original movies,
assertions and replay windows. These are separate source-bound epochs, not a
full-suite or native route equivalence claim.

The native four row-alias WORD writes at encoded `$18/$1A/$84/$86` remain a
separate implementation limit. Definition0 is not asserted empty; actual selected
route layout/descriptor/clock equality with native remains unobserved. Native
pixels, additional products/lifecycle breadth, broader cold campaign and ending
Hyper/Kos6+46/PALPCM acceptance are unchanged.
[Full commands, rejected hypotheses, matched observations and qualification limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#dez-screeninit-unused-encoded-layout-owner-source-and-independent-red-design--2026-10-17).


R38-R41 column stale-air support follow-up (2026-10-17, local a170/db5a): the
native cached offscreen FullSolid skip can retain Air+OnObj and column standing;
that stale record must not restore ground mode before the next playable update.
Only the column opts into the existing airborne-stale-standing capability;
shared solid/movement/CPU/timer/cache/API code is unchanged. Independent real
bubble/column/visible-CPU/normal-frame35->34 old-red after all forced-world
controls becomes green with grounded K-1/natural0/old controls; exact affected
nine-class selection is76/0F0E0S, mandatory S3K4 included.

One unchanged original fullcanonical then completes returnedHCZ14208 with
6553 total=5489physics+1064animation, firstNonCamera11920status0002/0003, lag59,
comparator warn/bootstrap0. Earlier animation3475 remains; runtime output has
468SMPSshadow+2FAST+1markRemembered warnings separately. Six earlier windows are
zero; SS6 entry/HCZ return is observed, but independent seg13 ownership still
fails88306/act1 vs2 and its summary remains incomplete. First8775->11920 and
11118->6553 totals do not prove exhaustive residual-vector equivalence or a
fully green route; only recent5 is retained by the maintained summaries.
Actual campaign8774 Aircaller/native001D producer and the11920 writer remain
unqualified. [Exact old-red/source/focus/canonical/limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r41-original-canonical-advances-to11920-strict-route-remains-red--2026-10-17).


R44–R48 native follower flight-timeout status (2026-10-17, CPU8930/test779247/4fcd):
both S2 and S3K timeout owners write literal status02, not an OR of Air. The
timeout-only projection clears status without restoring radii/water physics or
clearing native interaction ownership. Independent naturalLEFT S2/S3K old-red
after local restore-forward controls becomes green in exact R46focused156/0F0E0S;
separate declared7F/radii/water controls also pass. R47 original canonical remains
strict **1/1F0E0S**: returnedHCZ complete14208 TOTAL6511=5447physics+1064animation,
firstNonCamera13322status0009/0001, earlier animation3475 retained. Six earlier
windows are completezero; seg13 remains incomplete with ownership88306/act1-vs2
and four suppressed hardware identities. Recent5/aggregate totals do not prove
full-vector/no-regression equivalence. Actual11920 engine caller, native001D
producer and campaign8774 Aircaller remain unobserved/separate. No fresh child
fullguard or fullroute certificate: known LevelManager3153/3144 is parent-owned.
[Exact old-red/source/focus/canonical/limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r47-original-canonical-firstnoncamera13322-strict-red--2026-10-17).


HCZ restore follow-up R56–R63 (2026-10-18): the independent moving-wall/shake
component's genuine joint12/2F old-red becomes R58's151/1F partial after restoring
scroll producer state and rebinding the event to its recreated managed wall.
The internal supplementary dust owner then restores omitted water/surface state
without changing the public four-field record. R62 exact13-class lifecycle
passes142/0F0E0S, including both immediate full-registry and twice identical-input
forward-B branches in the unchanged moving/stationary tests. All original keys
remain compared. [Exact scope and residual limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r53r63-joint-hcz-restore-owners-independent-red-to-bounded-green--2026-10-18).
Dedicated supplementary surface-resource recreation/missing-participant cases
remain unqualified at this R62 epoch; R64/R66 below separately qualify native
hand retirement retention. The actual R52 restore caller remains unobserved. This is no
new canonical/slice/alternate certificate or full-vector/no-regression waiver;
existing strict13322/6511 and separate88306ownership remain historical epochs.


HCZ hand retirement R64–R68 (2026-10-18): native044 deletion clears only its own
SST; it does not release Sonic/Tails' held control/status. The local owner now
drops its native grab references without writing players, while preserving the
engine extension-release policy and all normal launch/jump bodies. Unchanged
independent907443 completes two before-birth and two held forced-world/forward/
full-key cycles before the genuine R64 eight-leaf old-red; R66 exact ten-class
149/0F0E0S turns every final native leaf green. [Owners, evidence and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r64r68-native-hand-retirement-independent-old-red-to-bounded-green--2026-10-18).
No new route13322, freed-SST next CPU marker, R52 actual restore caller or
full-vector/ownership88306 certificate follows from this bounded component pass.


Fixed-Dust supplementary coverage R68/R69 (2026-10-18): exact real-ROM a9df passes
all four resource/registry cases in a sole six-class74/0F0E0S lifecycle. The
same-player public ROM-art rebuild/all-session twice-forward contract, genuine
older owner-only missing-key reset preserving nondefault ordinary dust, and
P1/P2 removal refusal before partial apply are qualified. [Evidence and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r68r69-fixed-dust-resource-and-registry-boundary-coverage--2026-10-18).
Missing-controller/uncaptured-live/active-water-missing-key, cross-session and
player recreation/cache-pixel branches remain unqualified. No new runtime or
public API/pin change, actual R52 caller or route/frontier certificate follows.


**Saved-return fixed title ownership (local composition,2026-10-18):** the existing
bonus-return signal permits the existing fixed SST8 graph during FULL assembly.
The unchanged public-return test now passes the graph/retirement/lease and nine
ROM-parent checks; fresh and ordinary-host controls pass (child168 focused and99
guards). The original route still ends at AIZ2 FIFO capacity with20 unmatched
identities; returned partial21197/first34 busy remains, SS/FDF unvisited. Combined
Root validation and current consume/physical-queue cause remain pending.

[Composition and limits](architecture/plans/2026-10-13-s3k-playable-completion.md#root-composition-saved-return-fixed-title-ownership-and-current-strict-limits-2026-10-18).


Fixed air-countdown raw animation ownership R77–R80 (2026-10-18): native
AirCountdown_ReduceAir and loc_1857C publish Drown17 from the fixed object pass,
including active predeath before ordinary protection/underwater guards. The
S3K fixed manager now publishes that raw byte after successful natural underflow
and reasserts it on active predeath, without resetting mapping/animator history
or changing shared physics/CPU/cadence/API. Genuine independent two-final-case
old-red becomes green in sole R79 twelve-class116/0F0E0S; ordinary decrement
control and new public P1 eight-oracle companion pass. All original3test bodies
remain unchanged. [Source evidence and limits](architecture/audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r77r80-fixed-countdown-owns-raw-drowning-animation--2026-10-18).
Canonical3475 writer and full route are not observed by this component, and
R80 original canonical measurement is separate. Existing digit-art DMA
representation and wider active-countdown bubble/timer limitations are not
closed by raw animation publication. No whole-world/guard/native certificate.


## Player-owned glide and native-P2 damage — qualified local components

The maintained glide correction in local `8bcf0ab6` carries the querying player's
live top solidity bit and collision array through both foot probes and the
extension, regress and edge scans. Reverse ceiling probes retain the player's
live LRB bit. Object defaults and foreground/background provider selection remain
separate. Its isolated R110 affected selection passed 113 tests in 12 classes,
including 18 independent terrain-query cases; that result belongs to that isolated
source epoch.

The 2026-10-21 vertical-climb correction based on `c8516838` uses the native
8/9 pre-offset plus signed X radius in upright, reverse and idle probes. Local
movement now retains powered speed, signed upper bounds, BD-pose undo and strict
negative floor landing with raw angle/current animation 5 and the existing climb
timer. S3K idle still clobbers its animation delta with the floor distance under
`FixBugs=0`; KiS2 still omits that probe. The
[source and component validation record](architecture/research/trace/2026-09-14-kis2-chain-frontier.md#wall-climb-vertical-contract--2026-10-21)
does not identify the separate CNZ wall-distance `-32` stall or certify a route.

The local `dae6c29e` damage correction selects the ringless hurt tail by actual
first native-P2 identity, the existing touch profile and module companion support,
independently of CPU input ownership. Overlap, attack, listener, protection and
cause handling retain their existing owners. Isolated R127 passed 195 tests in 14
classes, including both P2 input variants, bit7 rejection and ringless P1 death.
It does not qualify competition, extra-player or decorated-game breadth.

Root's composed source on `691de518` passed the bounded combined lifecycle
(session71445): **379T0F0E0S across46 report suites**, including the unchanged
ordinary physical Nemesis oracle and full-key two restore/forward controls.
Fresh guards (session93642) retained **107T2F0E0S**: both failures are byte-exact
to the prior HubMusic issues; no new guard failure was observed. These are
component and guard results. Both maintained original-route invocations remain
**1T1F0E0S**.
FDF R112's returned segment 2 is incomplete: 166 = 1 camera physics + 165 animation;
Slots segment 3 is incomplete: 7,974 = 7,620 physics + 354 animation, first non-camera
frame 220 `x_speed` ROM0/engine`-0400`. Production ownership is lost in TITLE_CARD,
generation 6/cursor 10161, with 28 unmatched completions; SS/FDF remain unvisited.
HCZ R128 retains the nine R122 logical payloads byte-for-byte: complete segment 11
62 = 11 physics + 51 animation, complete segment 13 37,930 = 33,300 physics + 4,630 animation,
first non-camera 442 `sidekick_x_speed` 01C7/`-0100`, then uncompared SS7 results at
movie/destination97208. Historical ownership at 88306 is not reobserved.
+21 remains UNWAIVED and -94 UNLOCALIZED. Neither component pass nor aggregate
route totals identify a native winning tile, contact/damage caller or full-route
cause. [Commands and retained qualification limits](status/trace-frontier-log.md#2026-10-18--root-composes-player-owned-glide-and-native-p2-hurt-tails).


## HCZ1 direct water art — bounded physical publication coverage

Root HEAD691de518 with source8 tupleaa0310dc now carries the actual selected
water producer into its load-owned Nemesis publication path. The selected unsigned
physical-X branch retains the native three-pattern service; incompatible selection,
replacement and enclosing load failure cannot borrow an earlier admission.
The queue and selected schedule restore together through the existing world registry.
The source contract is native044 Level load/water order7674/7931/7938..7939,
HCZ1 unsigned branch8521..8538 and direct V-int path792..809. H-int2's separate
flag-clear branch at1276 is not part of this component claim.

The sole normal/no-profile 29-class focus, session46923, passed232T/0F/0E/0S.
All seven water cases passed, including the unchanged physical publication oldred
and twice full-registry restore/forward control, alongside load/rewind/transition
consumers and required S3K checks. This is a local uncommitted tested source epoch.
It does not certify H-int rendering, an actual complete route or native caller,
and does not waive the earlier HCZ/FDF/Cold residuals or +21/-94 limits.
[Exact command, original report accounting and source limits](architecture/plans/2026-10-13-s3k-playable-completion.md).


HCZ2 snake-block lifetime (2026-10-22): the snake's native saved-base range
predicate now owns culling, without the manager's additional animated-X gate.
Its offscreen deletion also clears respawn ownership before removal. Actual
manager counterexamples cover the valid-base/invalid-animated fringe and the
permanent latch;92 affected cases and33 fresh owning guards pass without skips.
The final native-viewport fixture separately passes all17 owning manager cases.

The unchanged original chain now completes HCZ2 segment13 and moves its first
non-camera mismatch from row287 Y to row442 P2 horizontal speed01C7/−0100.
It remains red with later HCZ errors and an SS7 results-boundary stop at97208.
Native contact/cause, whole-world lifetime breadth and complete-route parity
remain open. The rejected first candidate's earlier ownership stop and its
correction are preserved in the dated plan.
[Scope and remaining obligations](architecture/validation/levels/s3k-hcz2.md#snake-block-respawn-lifecycle--2026-10-22).

MHZ Knuckles native glide source composition (2026-10-22): the four-quadrant
collision ordering, S3K floor-first/KiS2 wall-first semantic and inherited reverse
solidity/path correction are composed at `c4c66c1a0`. The16 affected movement,
terrain, rules, API and bootstrap classes pass433 tests without failures/errors/skips.
The unchanged maintained Act2-to-FBZ case remains red at actual death37752.
Its old complete assertion is not retained here; worsening is unattributed pending
bounded matched baseline/current checks. The separate ordinary37402 control-owner
proposal is not a route pass. Combined smoke, sliding/dual-plane breadth and full
Act2/FBZ qualification remain open; no integration waiver follows.
[Current source and consumer evidence](architecture/validation/levels/s3k-mhz-act2.md#root-native-glide-composition--2026-10-22).


Knuckles slide continuation correction (2026-10-22): collision-only correction
now precedes its independent second floor scan, with native signed-zero and
byte-radius semantics, reverse scanner continuation and KiS2 radii lifetime.
Get-up retains the turn byte before callbacks while ordinary landing still
clears it. The affected18-class selection passes462T/0F/0E/0S, including native/
ordinary callback checks and two complete registered restore/forward cycles for
each gravity mode. These are declared component controls, not route parity.
LevelBound/camera-bias prelude, S3K slide audio and live dual-plane breadth remain
open. The earlier maintained MHZ death37752 was not rerun in this scope; cause
and matched baseline/current attribution remain pending.
[Source, command and limits](architecture/plans/2026-10-13-s3k-playable-completion.md#root-knuckles-slide-collision-and-landing-contract--2026-10-22).
