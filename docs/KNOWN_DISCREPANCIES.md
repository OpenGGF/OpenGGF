# Known Discrepancies from Original ROMs

This document tracks **intentional deviations** from the original Sonic 1 / 2 / 3&K ROMs that apply engine-wide (cross-game) or to Sonic 1 / Sonic 2 specifically. Entries here are architectural choices we've made (cleaner code, added features, deliberate corrections of known ROM bugs) that we accept and do not plan to revert. Runtime gameplay behavior is preserved unless a rationale explicitly justifies a visible change (e.g., the multi-sidekick entry adds gameplay that the ROM never supported).

**What does NOT belong here:**
- Bugs, incomplete implementations, and parity gaps that we *intend to fix* → [KNOWN_BUGS.md](KNOWN_BUGS.md)
- Sonic 3 & Knuckles-specific intentional discrepancies → [S3K_KNOWN_DISCREPANCIES.md](S3K_KNOWN_DISCREPANCIES.md)
- Sonic 3 & Knuckles-specific bugs → [S3K_KNOWN_BUGS.md](S3K_KNOWN_BUGS.md)

Each entry describes what the ROM does, what we do, and why — focusing on *why* the divergence is acceptable.

## Table of Contents

1. [Gloop Sound Toggle](#gloop-sound-toggle)
2. [Spindash Release Transpose Fix](#spindash-release-transpose-fix)
3. [Pattern ID Ranges](#pattern-id-ranges-for-guiresults-screen)
4. [HTZ Cloud Scroll Precision Fix](#htz-cloud-scroll-precision-fix)
5. [MCZ Rotating Platforms Child Cleanup](#mcz-rotating-platforms-child-cleanup)
6. [Multi-Sidekick Daisy Chain](#multi-sidekick-daisy-chain)
7. [Sonic 1 Monitor Sidekick Guard](#sonic-1-monitor-sidekick-guard)
8. [Bonus Stage Game Mode](#bonus-stage-game-mode)
9. [HCZ Conveyor Belt Rolling State Clear](#hcz-conveyor-belt-rolling-state-clear)
10. [Right-Wall Odd-Sensor Fallback Heuristics](#right-wall-odd-sensor-fallback-heuristics)
11. [S2 CPZ Visual Water Surface Oscillation](#s2-cpz-visual-water-surface-oscillation)
12. [S2 Music Offsets Resolved from Hardcoded REV01 Table](#s2-music-offsets-resolved-from-hardcoded-rev01-table)
13. [Right-Boundary Is Viewport-Independent (Level Edge)](#right-boundary-is-viewport-independent-level-edge)
14. [Object Despawn and Visibility Windows](#object-despawn-and-visibility-windows)
15. [Pre-Level Intro Prefix Trace Bootstrap Contract](#pre-level-intro-prefix-trace-bootstrap-contract)
16. [S2 Tornado Ride-Start Trace Bootstrap Contract](#s2-tornado-ride-start-trace-bootstrap-contract)
17. [S2 CNZ Slot-Machine Trace Bootstrap Contract](#s2-cnz-slot-machine-trace-bootstrap-contract)
18. [S3K Sidekick Seed-Frame Trace Bootstrap Debt](#s3k-sidekick-seed-frame-trace-bootstrap-debt)
19. [S3K Complete-Run Segment Start-Position Bootstrap Debt](#s3k-complete-run-segment-start-position-bootstrap-debt)
20. [Frame-0 Trace Bootstrap Snapshot Coverage Debt](#frame-0-trace-bootstrap-snapshot-coverage-debt)
21. [Sonic 1 Embedded Runtime Data Ratchet](#sonic-1-embedded-runtime-data-ratchet)

---

## Gloop Sound Toggle

**Location:** `BlueBallsObjectInstance.java`
**ROM Reference:** `s2.sounddriver.asm` lines 2142-2149

### Original Implementation

The ROM implements the Gloop sound toggle in the Z80 sound driver itself:

```asm
zPlaySound_CheckGloop:
    cp    SndID_Gloop           ; Is this the gloop sound?
    jr    nz,zPlaySound_CheckSpindash
    ld    a,(zGloopFlag)
    cpl                         ; Toggle the flag
    ld    (zGloopFlag),a
    or    a
    ret   z                     ; Return WITHOUT playing if flag is 0
    jp    zPlaySound            ; Only play every other call
```

This hardcodes a specific sound ID check into the driver, causing the Gloop sound to only play every other time it's requested.

### Our Implementation

We implement the toggle in `BlueBallsObjectInstance.playGloopSound()` instead:

```java
private static boolean gloopToggle = false;

private void playGloopSound() {
    if (!isOnScreen()) {
        return;
    }
    // Toggle flag - only play every other call (ROM: zGloopFlag)
    gloopToggle = !gloopToggle;
    if (!gloopToggle) {
        return;
    }
    AudioManager.getInstance().playSfx(SND_ID_GLOOP);
}
```

### Rationale

1. **Gloop is exclusively used by BlueBalls** - A search of the disassembly confirms `SndID_Gloop` (0xDA) is only referenced in `Obj1D` (BlueBalls). No other object uses this sound.

2. **Keeps SMPS driver generic** - Hardcoding sound-specific behavior in the driver would make it less reusable and harder to maintain. The driver should ideally just play what it's told.

3. **Encapsulates behavior** - The toggle is really a BlueBalls-specific feature to prevent sound spam when multiple balls are active. Keeping it in the object makes the relationship explicit.

4. **Identical runtime behavior** - The end result is the same: Gloop plays every other call, preventing audio spam from staggered sibling balls.

### Verification

Both implementations result in the Gloop sound playing at 50% frequency, which prevents overwhelming audio when multiple BlueBalls objects are bouncing with staggered timers.

---

## Spindash Release Transpose Fix

**Location:** `Sonic2SfxData.java`
**ROM Reference:** `docs/s2disasm/sound/sfx/BC - Spin Dash Release.asm`

### Original Implementation

The ROM SFX header for Spindash Release (0xBC) uses an invalid transpose value for FM5:

```asm
    smpsHeaderSFXChannel cFM5, Sound3C_SpindashRelease_FM5, $90, $00
```

This value is called out in the disasm as a bug. Some SMPS drivers interpret `$90` as a large negative transpose, which can underflow the note calculation and skip the initial FM burst.

### Our Implementation

We patch only this invalid FM transpose value when parsing SFX headers:

```java
int transpose = (byte) data[pos + 4];
if ((channelId & 0x80) == 0 && transpose == (byte) 0x90) {
    transpose = 0x10;
}
```

### Rationale

1. **Targets a known bad data value** - The disasm explicitly documents the `$90` transpose as invalid for this SFX.
2. **Preserves other SFX behavior** - We do not mask or normalize all transposes, only this exact FM case.
3. **Improves fidelity** - Restores the missing initial FM burst for 0xBC that is audible in hardware/driver-correct playback.

### Verification

Spindash Release now includes the initial FM5 hit before the delayed PSG noise, matching expected playback.

---

## Pattern ID Ranges

**Location:** `LevelManager.java`, `ObjectRenderManager.java`, `PatternAtlas.java`
**ROM Reference:** VDP VRAM tile management

### Original Implementation

The Mega Drive VDP has limited VRAM (~64KB), so the original game dynamically loads and overwrites pattern data. When displaying the results screen after completing an act, the game overwrites level tile patterns that are no longer needed with results screen graphics (score tallies, continue icons, etc.). Pattern indices directly correspond to VRAM tile addresses (0x0000-0x07FF typical range).

From `s2.asm`, results screen art is loaded into VRAM locations previously used by level tiles:
```asm
; Load results screen patterns, overwriting level data
lea     (ArtNem_TitleCard).l,a0
lea     (vdp_control_port).l,a4
move.w  #tiles_to_bytes(ArtTile_Title_Card),d0
```

### Our Implementation

We use **extended pattern ID ranges** with fixed bases that don't overlap:

| Base | Category | Notes |
|------|----------|-------|
| `0x00000` | Level tiles | Corresponds to VRAM tile indices (0-~2047) |
| `0x01000` | Special Stage | Track, objects, HUD for special stages |
| `0x10000` | Reserved legacy results range | Historical docs referenced results here; current release-screen allocations are registered under `PatternAtlasRange.RESULTS_SCREENS` at `0x60000` |
| `0x20000` | Objects | Monitors, springs, badniks, zone-specific objects |
| `0x28000` | HUD | Score, time, rings display (fixed base) |
| `0x30000` | Water surface | Underwater palette transition patterns |
| `0x34000` | S3K Dust | Spindash/skid dust art (`Sonic3kDustArt.DUST_PATTERN_BASE`) |
| `0x38000+` | Sidekick DPLC banks | Extra banks for duplicate-character sidekicks (global running offset) |
| `0x39000+` | Sidekick tail appendages | Extra banks for duplicate Tails tail sprites (Obj05) |
| `0x40000` | Title Card / S1 SS Results / S3K AIZ Intro | Shared base; mutually-exclusive game contexts (see below) |
| `0x50000` | Level Select / S1 Title Card / S3K Title Card / S3K Data Select | Shared base; mutually-exclusive game contexts (see below) |
| `0x60000` | Results screens | S1 Try Again Eggman, S2 title-screen background, S3K level results; registered as `PatternAtlasRange.RESULTS_SCREENS` |
| `0x61000` | Results-screen suballocation | S1 Try Again emerald art (`PatternAtlasRange.RESULTS_SCREENS.base() + 0x1000`) |
| `0x70000` | Special-stage results / S2 title sprites | Shared base in mutually-exclusive contexts; registered as `PatternAtlasRange.SPECIAL_STAGE_RESULTS` |
| `0x80000` | S2 title-screen credit text | Separate title-screen text allocation |
| `0xE0000` | S2 credits / S3K title animation | Shared base in mutually-exclusive contexts; registered as `PatternAtlasRange.S3K_TITLE_SCREEN_ANIMATION` |
| `0xE8000` | S3K title sprites | Registered as `PatternAtlasRange.S3K_TITLE_SCREEN_SPRITES` |

**Shared-base contexts** (`0x40000`):
- S2 Title Card (`TitleCardManager.PATTERN_BASE`) — gameplay scope, not active during cutscenes
- S2 Special Stage Results (`SpecialStageResultsScreenObjectInstance.PATTERN_BASE`)
- S1 Special Stage Results Screen (`Sonic1SpecialStageResultsScreen.PATTERN_BASE`)
- S3K AIZ Intro Art Loader (`AizIntroArtLoader.INTRO_PATTERN_BASE`) — only active during AIZ1 intro
- S2 Title Screen (`TitleScreenDataLoader`) — only active before any gameplay

**Shared-base contexts** (`0x50000`):
- S1 Title Card (`Sonic1TitleCardManager.PATTERN_BASE`)
- S3K Title Card (`Sonic3kTitleCardManager.PATTERN_BASE`)
- S1/S2/S3K Level Select (`Sonic1LevelSelectConstants`, `LevelSelectConstants`, `Sonic3kLevelSelectConstants`)
- S3K Data Select (`S3kDataSelectRenderer.DATA_SELECT_PATTERN_BASE`)

These bases are reused across game-context-mutually-exclusive subsystems (e.g., title card vs. level select vs. data select are never active in the same frame). `PatternAtlas.registerRange(...)` provides a diagnostic collision detector but is not yet enforced at every call site — adding bootstrap-time `registerRange` calls in each owning subsystem is a follow-up.

The source-level ownership for documented ranges is now guarded by
`TestArchUnitRules.virtual_pattern_base_fields_are_backed_by_pattern_atlas_range`
and the S3K frontend range signals in `TestArchitecturalSourceGuard`, so new
`*_PATTERN_BASE` constants inside a registered range must reference
`PatternAtlasRange` instead of hard-coding the numeric base.

```java
// LevelManager.java
private static final int OBJECT_PATTERN_BASE = 0x20000;
private static final int HUD_PATTERN_BASE = 0x28000;
```

The `PatternAtlas` stores all patterns in a HashMap keyed by pattern ID. Each category has a fixed base to prevent collisions when new sheets are dynamically registered (e.g., zone-specific objects like SmashableGround in HTZ).

### Rationale

1. **Level patterns remain cached** - No need to reload level tiles after results screen, enabling instant transitions.

2. **Simpler state management** - No need to track which tiles were overwritten or restore them later.

3. **Easier debugging** - Level and UI patterns coexist without interference; inspecting the atlas shows all patterns.

4. **No VRAM constraints** - Modern systems have abundant texture memory; emulating the 64KB limit adds complexity with no benefit.

### Verification

The rendered output is identical to the original - the same graphics appear at the same screen positions. Only the internal storage differs.

---

## HTZ Cloud Scroll Precision Fix

**Location:** `SwScrlHtz.java`
**ROM Reference:** `s2.asm` lines 15823-15831 (fixBugs path) vs line 15833 (original path)

### Original Implementation

The original ROM uses `asr.w #4,d1` to initialize the cloud layer scroll delta. This word-sized shift loses the fractional bits that were set up via `swap d1`, causing a visible 2-frame jerkiness in cloud scrolling as the fractional accumulator repeatedly underflows and corrects.

```asm
; Original (buggy) path:
    asr.w   #4,d1          ; word shift discards upper 16 bits (fractional part)
```

### Our Implementation

We use the `fixBugs` path from the disassembly, which preserves fractional precision:

```asm
; fixBugs path:
    swap    d1
    asr.l   #4,d1          ; long shift preserves fractional bits across the swap
```

This is implemented in `SwScrlHtz.java` using a 32-bit arithmetic shift after swapping the high/low words, matching the corrected assembly path.

### Rationale

1. **Known bug in original ROM** - The disassembly explicitly marks this as a bug with a `fixBugs` conditional path.
2. **Smoother cloud animation** - The fractional bits produce smooth per-frame cloud movement instead of the original's periodic stutter.
3. **Matches disassembly intent** - The `fixBugs` path represents what the original developers intended before the word/long shift mistake.

### Verification

Cloud layer scrolling in HTZ is smooth across all frames, without the 2-frame jitter visible in the original ROM.

---

## MCZ Rotating Platforms Child Cleanup

**Location:** `MCZRotPformsObjectInstance.java`
**ROM Reference:** `s2.asm` lines 53707-53726 (child spawn), lines 53801-53803 / 53826-53828 (`MarkObjGone2` calls)

### Original Implementation

In the ROM, all three objects (parent + 2 children) live in the same flat object RAM table. Children are allocated via `AllocateObjectAfterCurrent` into adjacent SST slots. Each object independently calls `MarkObjGone2` using `objoff_32` (base X = the parent's original spawn X):

```asm
; routine 2 exit (MTZ path / parent):
loc_27C5E:
    move.w  objoff_32(a0),d0
    jmpto   JmpTo3_MarkObjGone2

; routine 4 exit (MCZ path / children):
loc_27C9A:
    move.w  objoff_32(a0),d0
    jmpto   JmpTo3_MarkObjGone2
```

`Obj6A_InitSubObject` copies the parent's `x_pos` to each child's `objoff_32`:

```asm
    move.w  x_pos(a0),objoff_32(a1)
```

So all three share the same base X and self-destruct at the same camera threshold.

### Our Implementation

Our engine uses a two-tier object system: placement-windowed objects (`activeObjects`) and unwindowed dynamic objects (`dynamicObjects`). The parent is placement-managed, but children are dynamic and have no off-screen removal.

Instead of independent self-cleanup, the parent's `onUnload()` explicitly destroys its children:

```java
@Override
public void onUnload() {
    for (MCZRotPformsObjectInstance child : children) {
        child.setDestroyed(true);
    }
    children.clear();
}
```

### Rationale

1. **Architectural mismatch** - The ROM's flat object table lets every object run `MarkObjGone2` against a base X. Our dynamic objects have no equivalent windowing mechanism.

2. **Parent-driven cleanup is idiomatic** - This matches the pattern used by other parent-child objects in the engine (`AizGiantRideVineObjectInstance`, `Sonic1CaterkillerBadnikInstance`, `SolBadnikInstance`).

3. **Same trigger point** - The parent's Placement window check uses the same spawn X that the ROM's `MarkObjGone2` checks via `objoff_32`, so cleanup occurs at the same camera position.

### Verification

When the camera leaves the MCZ crate area, all three objects are removed. On return, the parent is re-spawned by Placement and creates fresh children — no accumulation.

---

## Multi-Sidekick Daisy Chain

**Location:** `Engine.java`, `SidekickCpuController.java`, `SpriteManager.java`, `LevelManager.java`
**ROM Reference:** Sonic 2 supports exactly one CPU-controlled Tails at `$FFFFB040` (Sidekick). S3K adds Knuckles as a playable character but still has at most one CPU follower.

### Original Implementation

The ROM allocates a single fixed RAM slot for the sidekick character. `Tails_CPU` routines follow Sonic with a 17-frame position/input history delay. There is no support for multiple sidekicks, sidekick chains, duplicate characters, or the main player character also appearing as a sidekick.

### Our Implementation

The engine supports an arbitrary number of CPU-controlled sidekicks configured via comma-separated `SIDEKICK_CHARACTER_CODE` (e.g. `"tails,knuckles,sonic,sonic"`). Key divergences:

**Daisy chain following.** Each sidekick follows the one in front of it rather than all following Sonic. The chain uses the same 17-frame history delay per link. When a middle sidekick despawns, downstream sidekicks self-heal to the nearest settled leader via `getEffectiveLeader()`.

**Duplicate characters.** The same character can appear multiple times (e.g. five Sonics). Each duplicate gets a separate DPLC pattern bank in the virtual `0x38000+` range to prevent atlas corruption. The `PlayerSpriteRenderer` uses `renderPatternWithId()` to bypass the VDP's 11-bit pattern index limit.

**Same character as player.** The main player's character can also be used as a sidekick (e.g. Sonic main + Sonic sidekick). VRAM bank slot allocation ensures each instance has independent DPLC storage.

**Per-character respawn strategies.** The ROM's Tails CPU respawn is a single fly-in-from-above behavior. The engine generalizes this via `SidekickRespawnStrategy`:
- **Tails**: flies in from above (ROM-accurate)
- **Knuckles**: glides in from the screen edge opposite to the leader's movement direction
- **Sonic**: walks or spindashes in from the nearest floor at the screen edge

**Parallel respawn.** When multiple sidekicks despawn simultaneously, they respawn in parallel using chain healing rather than cascading one-by-one.

**P2 input routing.** Only the first sidekick in the chain receives Player 2 controller input, matching the ROM's single-sidekick model.

### VRAM Bank Limits

Duplicate-character sidekick DPLC banks occupy pattern ID ranges above the VDP's native 11-bit (2048 tile) limit:

| Range | Purpose | Capacity |
|-------|---------|----------|
| `0x38000+` | Sidekick body DPLC banks | ~512 Sonic-type or ~64 before hitting tail range |
| `0x39000+` | Tails tail appendage (Obj05) banks | ~1,170 tail appendages |
| `0x40000` | Title Card (next range) | Collision boundary |

Practical limits before title card pattern corruption:
- **All-Sonic sidekicks** (no tail appendages): ~512
- **All-Tails sidekicks** (body + tail): ~65
- **Mixed configurations**: proportional; typical setups (≤10 sidekicks) are well within budget

### Rationale

1. **Fun/novelty** — Multiple characters running together in a chain looks great and extends the ROM's single-sidekick concept naturally.
2. **Architecture exercise** — The `SidekickRespawnStrategy` interface and chain healing demonstrate clean extension of ROM-accurate systems without if/else chains.
3. **No impact on accuracy** — Single-sidekick configurations behave identically to the ROM. Multi-sidekick behavior only activates when explicitly configured.

### Configuration

```json
{
  "SIDEKICK_CHARACTER_CODE": "tails,knuckles,sonic"
}
```

Empty string disables sidekicks (default). Single value preserves ROM-accurate single-sidekick behavior.

---

## Sonic 1 Monitor Sidekick Guard

**Location:** `Sonic1MonitorObjectInstance.java`
**ROM Reference:** `docs/s1disasm/_incObj/26 Monitor.asm`

### Original Implementation

Sonic 1 has no CPU sidekick actor. `Touch_Monitor` only ever runs with the single main player object in `a1`, so the ROM does not need a sidekick-specific monitor-break guard.

### Our Implementation

When cross-game sidekicks are donated into Sonic 1, `Sonic1MonitorObjectInstance.onTouchResponse(...)` now returns early for `player.isCpuControlled()`. This matches the engine's shared monitor rule already used by the Sonic 2 and Sonic 3K monitor implementations.

### Rationale

1. **Cross-game donation introduces a new actor class** - Sonic 1 content can now run with AI sidekicks that the ROM never had to consider.
2. **Protects intended ownership rules** - Sidekicks should not be able to break monitors or claim their rewards in donated-content scenarios.
3. **Keeps behavior aligned across games** - Sonic 2 and S3K already block CPU sidekicks from monitor breaks, so the donated S1 path should not be the odd exception.

### Verification

`TestSonic1MonitorObjectInstance.cpuSidekickCannotBreakSonic1Monitor` covers the donated-sidekick path. The local S1 disassembly also confirms that the static monitor icon mapping (`Map_Monitor` frame `2`) is real art, so no icon-suppression discrepancy entry is needed.

---

## Bonus Stage Game Mode

**Location:** `GameLoop.java`, `GameMode.java`
**ROM Reference:** `sonic3k.asm` Level: routine (line 7504)

### Original Implementation

S3K bonus stages (Gumball, Pachinko, Slots) are loaded through the normal `Level()` routine. The zone ID changes to a bonus zone ($1300/$1400/$1500), the level loads, and the game loop runs identically to any other level. No separate game mode exists.

### Engine Implementation

Bonus stages use a distinct `GameMode.BONUS_STAGE` that runs the same level rendering/physics/object pipeline as `LEVEL` mode, but with an explicit `AbstractBonusStageCoordinator` managing entry/exit lifecycle, state persistence, and ring gains.

### Reason

The engine's `GameLoop` dispatches behavior based on `GameMode`. Overloading `LEVEL` with conditional bonus stage logic would scatter bonus-specific checks across the codebase (timer suppression, death plane disable, exit detection, state save/restore). A dedicated mode keeps the lifecycle explicit and contained in the coordinator.

### Impact

None on gameplay. The level pipeline (rendering, physics, objects, collision) is identical between `LEVEL` and `BONUS_STAGE` modes. The only difference is the coordinator managing transitions.

---

## HCZ Conveyor Belt Rolling State Clear

**Location:** `HCZConveyorBeltObjectInstance.java` (`capturePlayer()`)
**ROM Reference:** `sonic3k.asm` lines 66490-66511 (standing capture), 66528-66547 (hanging capture)

### Original Implementation

The ROM's capture sequences for the HCZ conveyor belt (Obj 0x3E) do not contain an explicit `bclr #Status_Roll,status(a1)`. The capture code clears velocities, sets `object_control` to 3, snaps the player's Y position, and sets the mapping frame, but never directly modifies `Status_Roll`:

```asm
; Standing capture (sonic3k.asm:66490-66503):
    clr.w   x_vel(a1)
    clr.w   y_vel(a1)
    clr.w   ground_vel(a1)
    andi.b  #$FC,render_flags(a1)   ; clears render flip bits, NOT status
    move.w  d0,y_pos(a1)
    move.b  #0,anim(a1)
    move.b  #3,object_control(a1)
    move.b  #$63,mapping_frame(a1)
    ; ... state init, DPLC call
```

On the original hardware, `Status_Roll` is effectively neutralised during capture through side-effects of `object_control = 3` altering the player's main update path (skipping `Sonic_CheckRoll` and related routines). The release path unconditionally sets `Status_Roll` via `bset #Status_Roll,status(a1)` (sonic3k.asm:66454).

### Our Implementation

We explicitly clear the rolling state during capture:

```java
private void capturePlayer(AbstractPlayableSprite player, PlayerBeltState state,
                           int snapY, int initialFrame, int initialPhase) {
    player.setXSpeed((short) 0);
    player.setYSpeed((short) 0);
    player.setGSpeed((short) 0);
    player.setRenderFlips(false, false);

    // Explicit roll clear — not present in ROM capture sequence
    if (player.getRolling()) {
        player.setRolling(false);
    }

    player.setCentreY((short) snapY);
    // ...
}
```

### Rationale

1. **Touch responses run while object-controlled** — `object_control = 3` suppresses solid object collisions and animation, but does NOT suppress touch response (enemy/badnik) collision checks.

2. **Rolling = attacking** — `ObjectManager.isPlayerAttacking()` returns `true` when `getRolling()` is true. If `Status_Roll` persists from a jump into belt capture, the player incorrectly destroys enemies on contact (e.g. Chopper in HCZ) instead of taking damage.

3. **Observed gameplay confirms vulnerability** — On original hardware, Chopper can grab and hurt Sonic while he is on the conveyor belt, proving the player is NOT in an attacking state during capture.

4. **ROM clears implicitly, engine needs explicit** — The ROM achieves this through `object_control = 3` altering the player update path in ways our engine doesn't replicate as a side-effect. The explicit clear produces identical gameplay behavior.

5. **Must clear before Y snap** — `setRolling(false)` restores standing radii, which changes sprite height. Clearing after `setCentreY()` would shift the centre by half the height delta (5px for Sonic). Clearing before the snap ensures the snap uses standing-height coordinates.

### Verification

With the fix, the player is vulnerable to enemy touch responses while on the conveyor belt, matching original hardware behavior. The release path still unconditionally sets `Status_Roll`, so belt exit behavior is unaffected.

---

## Right-Wall Odd-Sensor Fallback Heuristics

**Location:** `CollisionSystem.pendingOddSensorFallbackAngles`, `AbstractPlayableSprite.rightWallPenetrationTimer`
**ROM Reference:** `AnglePos`/right-wall sensor selection paths in the Sonic 1, Sonic 2, and Sonic 3K disassemblies.

### Original Implementation

The ROM resolves the active wall sensor and floor angle from the current frame's sensor probes. Odd/flagged angle values are snapped from the same frame's result; there is no cross-frame map of prior alternate-sensor angles.

### Our Implementation

The engine carries two narrow right-wall stability heuristics:

1. `CollisionSystem.pendingOddSensorFallbackAngles` can remember the alternate sensor angle from a previous RIGHTWALL frame and apply it when the selected sensor reports distance 0 with an odd angle.
2. `AbstractPlayableSprite.rightWallPenetrationTimer` gives a short grace period while resolving right-wall penetration and is captured in playable-sprite rewind snapshots.

`CollisionSystem.resetState()` clears the pending-angle map so singleton reuse between tests or gameplay sessions cannot inherit stale fallback state.

### Rationale

The heuristics prevent visible ground-mode oscillation at right-wall transitions in the Java collision model while the broader collision pipeline still differs structurally from the ROM's exact object RAM and terrain probe sequencing.

### Verification

`CollisionSystemTest.resetStateClearsPendingOddSensorFallbackAngles` guards the reset behavior. `TestAbstractPlayableSpriteRewindCapture` covers the captured `rightWallPenetrationTimer` field.

---

## S2 CPZ Visual Water Surface Oscillation

**Location:** `Sonic2WaterDataProvider.getVisualWaterLevelOffset` (CPZ branch), `WaterSystem.getOscillatedWaterLevel`
**ROM Reference:** `docs/s2disasm/s2.asm:5273-5282` (`MoveWater`)

### Original Implementation

For non-ARZ S2 water zones (CPZ Act 2, plus the unused HPZ), `MoveWater` reads the first word of `Oscillating_Data`, shifts it right by 1, and adds the result to `Water_Level_2` to produce the visible water surface position:

```asm
MoveWater:
    clr.b   (Water_fullscreen_flag).w
    moveq   #0,d0
    cmpi.b  #aquatic_ruin_zone,(Current_Zone).w
    beq.s   +
    move.b  (Oscillating_Data).w,d0
    lsr.w   #1,d0
+
    add.w   (Water_Level_2).w,d0
    move.w  d0,(Water_Level_1).w
```

`Oscillating_Data` is the value-word of oscillator 0 (initial value `$0080`, limit `$10`), so the high byte read by `move.b` ranges 0..16 and the resulting `lsr.w #1` offset is 0..8 — a one-sided positive bob added on top of `Water_Level_2`.

### Engine Implementation

`Sonic2WaterDataProvider.getVisualWaterLevelOffset(ZONE_CPZ, 1)` returns `OscillationManager.getByte(0) - 8`, producing a signed offset in the range `-8..+7` centred around zero. `WaterSystem.getOscillatedWaterLevel` then adds this to the base water level. The shift-by-1 from the ROM is replaced by a subtract-by-8 recentring.

### Rationale

The engine uses this offset purely as a *visual* bob applied on top of the gameplay water level (`baseLevel`) returned by the water system. Centring around zero (`oscillation - 8` in place of the ROM's `oscillation >> 1`) keeps the absolute water gameplay surface anchored at the value owned by `WaterSystem` / the dynamic water handler, while the visible surface oscillates symmetrically `±8` pixels rather than only ever sitting 0..8 pixels *below* its nominal level. The original 0..+8 one-sided bob would otherwise need every base water level returned from `WaterDataProvider.getStartingWaterLevel` (and every dynamic target written by event managers) to be biased down by 4 pixels to hit the same visible mean — an invasive change for a purely cosmetic axis.

The `-8` recentring has been the engine's behaviour since `WaterSystem` was first authored; the `dfbc610c9` test commit / `7cad4c068` provider refactor only moved the existing logic out of `WaterSystem` and onto the per-game `WaterDataProvider`. No commit on this branch introduced the divergence.

### Verification

`TestSonic2WaterDataProvider.testCpz2VisualOffsetAtResetIsMinusEight` pins the post-`OscillationManager.reset()` value at literal `-8`, and `testCpz2VisualOffsetTracksOscillatorAfterStepping` asserts the formula `getByte(0) - 8` after stepping the oscillator (with an explicit `byte0 != 0` precondition so the test would fail if the oscillator never advanced). Trace replay fixtures are unaffected because the comparator covers camera/player position only, not water-level pixel offsets.

### Removal Condition

Remove this entry if the engine is ever re-aligned to the ROM's `lsr.w #1` formula. That would require either biasing every base water level returned from `WaterDataProvider.getStartingWaterLevel` (and every `DynamicWaterHandler` target write) down by 4 pixels, or changing the visual contract so callers expect a one-sided 0..+8 bob layered on top of a slightly higher mean.

---

## S2 Music Offsets Resolved from Hardcoded REV01 Table

**Location:** `Sonic2SmpsLoader.findMusicOffset` / `Sonic2SmpsLoader.musicMap`
**ROM Reference:** `docs/s2disasm/sound/_smps2asm_inc.asm` (`zMasterPlaylist` flag table + per-bank `MusicPoint` pointer tables, inside the Saxman-compressed Z80 driver blob)

### Original Implementation

The ROM resolves a music ID to its SMPS data through the Z80 sound driver's `zMasterPlaylist` flag table and the per-bank pointer tables (`MusicPoint` entries) it references. That structure only exists in readable form *after* the Saxman-compressed Z80 driver blob has been decompressed into Z80 RAM at runtime — the bytes sitting in 68K ROM are still compressed. The driver indexes `zMasterPlaylist` by the requested song ID to pick a bank and pointer.

### Our Implementation

`Sonic2SmpsLoader.findMusicOffset(musicId)` resolves song offsets from a hardcoded `Sonic2Music`-ID → REV01-ROM-offset map (`musicMap`) instead of reading `zMasterPlaylist` / `MusicPoint` from ROM:

```java
public int findMusicOffset(int musicId) {
    Integer mapped = musicMap.get(musicId);
    if (mapped != null) {
        return mapped;
    }
    // ...
}
```

The offsets in `musicMap` were discovered empirically and verified against REV01.

### Rationale

1. **The ROM table is compressed at rest.** `zMasterPlaylist` and the per-bank `MusicPoint` pointer tables live inside the Saxman-compressed Z80 driver blob in 68K ROM. The previous ROM-driven implementation parsed those compressed bytes directly and could not yield correct offsets; the table is only readable after a runtime Z80 decompression.

2. **Engine IDs are intentionally shifted.** The engine's `Sonic2Music` IDs are systematically shifted relative to the disassembly's `zMasterPlaylist` entry order (e.g. `EMERALD_HILL.id == 0x81` loads the EHZ track, but `zMasterPlaylist[0]` / disasm id `0x81` is `Mus_2PResult`). Even a properly Z80-decompressed lookup would disagree with the engine's intended track for most IDs.

3. **No audible difference.** Both paths reference the same underlying SMPS music data — only the lookup source differs. The hardcoded REV01 map is authoritative until both problems above are solved.

### Verification

The hardcoded `musicMap` covers every `Sonic2Music` entry, and the offsets are the empirically-verified REV01 ROM addresses used by the engine and the sound-test debug tool (`SoundTestApp`). Playback matches the original game's track-to-ID assignments.

### Removal Condition

Remove this entry once the S2 driver's `zMasterPlaylist` / `MusicPoint` tables are read through a runtime Z80 decompression path **and** the `Sonic2Music` ID shift is reconciled with the disassembly entry order, so `findMusicOffset` can resolve offsets from ROM data rather than the hardcoded REV01 map.

---

## Sonic 1 credits demo trace replay divergences (post-frame-0-hydration removal)

**Source:** Removed in commit following `6ea9554`. Prior commit `6ea9554` removed `TraceEvent.StateSnapshot` hydration from `AbstractCreditsDemoTraceReplayTest` so the replay tests now exercise the engine without per-frame trace correction. The follow-up commit additionally removed the trace-derived per-demo `STARTING_ANIMATION_ID` / `setDirection(RIGHT)` overrides from `Sonic1CreditsDemoBootstrap.applyStartingPose` (now deleted) and let the engine's natural `Sonic_Animate` pass and post-spawn defaults drive the frame-zero pose. Removing those overrides did not change which credits demos pass or fail.

### Status

All 8 S1 credits-demo trace replay tests pass in the 2026-05-19 targeted S1
regression sweep. The historical failures below were exposed after removing
trace-state hydration and trace-derived pose overrides; they were pre-existing
engine bugs that the prior hydration was masking, not regressions caused by
removing the trace-derived overrides. See `docs/TRACE_FRONTIER_LOG.md` for the
latest frontier snapshot.

| Test | First divergence frame | First divergence | Total errors |
|------|------------------------|------------------|--------------|
| `TestS1Credits01Mz2TraceReplay` | resolved 2026-05-19 | MZ push-block lava-geyser slot/launch phase | 0 in targeted replay |
| `TestS1Credits02Syz3TraceReplay` | resolved before 2026-05-19 sweep | full targeted replay passes | 0 in targeted sweep |
| `TestS1Credits03Lz3TraceReplay` | resolved 2026-05-18 | REV01 LZ wind-tunnel d0-clobber/vblank-phase emulation | 0 in targeted replay |
| `TestS1Credits04Slz3TraceReplay` | resolved before 2026-05-19 sweep | full targeted replay passes | 0 in targeted sweep |
| `TestS1Credits05Sbz1TraceReplay` | resolved before 2026-05-19 sweep | full targeted replay passes | 0 in targeted sweep |

### Rationale for not patching from traces

Per CLAUDE.md "Trace Replay Tests" the comparison-only invariant forbids hydrating engine state from `TraceEvent.StateSnapshot` events; trace data is read-only diagnostic input. Any per-credit override to mask these failures would be a spec violation. The bugs need to be diagnosed and fixed in the engine (likely physics, ring/object collision, or zone-specific systems such as LZ water/SBZ junction objects) and the failing tests turned green by ROM-accurate code paths, not bootstrap papering.

### Resolved MZ2 (`TestS1Credits01Mz2TraceReplay`) push-block/geyser root cause (2026-05-19)

The MZ2 credits trace diverged when the pushed block reached the
`PushB_LoadLava` positions and spawned a geyser maker from a later SST slot.
`GMake_Wait` (`docs/s1disasm/_incObj/4C & 4D Lava Geyser Maker.asm`) first
spends one live tick after `FindFreeObj`, then its bubble animation advances
to `GMake_MakeLava`. At that point the maker sets the parent block airborne
and writes `#-$580` to `obVelY`.

The engine now mirrors both phases: parent-spawned geyser makers start with
one live tick, and `Sonic1PushBlockObjectInstance.applyLavaGeyserLaunch`
preserves the first airborne frame's launch phase so the initial `#-$580`
displacement is visible before `loc_C056`'s `+$18` gravity affects the next
velocity. This removes the frame-493 maker timing drift and the frame-499
one-pixel vertical mismatch without reading trace state back into the engine.

### Resolved LZ3 (`TestS1Credits03Lz3TraceReplay`) y-bump root cause (2026-05-18)

The 2px Y drift starting at frame 221 is caused by a documented bug in the
ORIGINAL Sonic 1 REV01 ROM. `LZWindTunnels` (`docs/s1disasm/_inc/LZWaterFeatures.asm`)
overwrites the low byte of `d0` with `v_vbla_byte` (line 313) but later uses
`d0` as if it still held the saved player X for the curve check (line 329).
Disassembly comment at line 309: `d0 is overwritten but later used as if it
wasn't!`. The `if FixBugs` branch wraps `move.w d1,d0` to restore the saved
X.

The bug fires the curve adjustment (`+2`/`-2` to `obY`) on frames where it
would otherwise be skipped — most notably every 64 frames when the rushing
water sound branch reloads `d0` with `sfx_Waterfall = 0x00D0`. The recorded
trace, captured on REV01 hardware, contains those occasional `+2` bumps.

`Sonic1LZWaterEvents` now emulates the REV01 non-FixBugs path by preserving
the high byte of the player X check while replacing the low byte with
`v_vblank_byte & 0x3F`, and by using the waterfall SFX id on sound-gate
frames. `Sonic1CreditsDemoBootstrap` also seeds the LZ credits-demo vblank
phase when applying the lamppost state, so the first ROM y-bump occurs at the
same trace frame instead of drifting by the engine's default object-manager
counter phase.

The `Sonic1LZWaterEvents` X-push and Y-input nudges have been migrated from
`setCentreX`/`setCentreY` (which zero sub-pixels) to
`setCentreXPreserveSubpixel`/`setCentreYPreserveSubpixel` so that ROM-accurate
word-only writes (`addq.w #4,obX`, `subq.w #1,obY`, `addq.w #1,obY`,
`add.w d0,obY`) preserve `obSubpixelX`/`obSubpixelY`. This brings the trace's
`sub_x` line into agreement (was a persistent `0x6400` desync) while the
REV01 wind-tunnel bug emulation removes the remaining LZ3 y divergence.

### Removal Condition

Remove this entry once each listed test has been diagnosed (root-cause identified in the engine), fixed at the source, and is consistently green against the recorded ROM trace.

---

## Right-Boundary Is Viewport-Independent (Level Edge)

**Location:** `RightBoundary.java`, `PlayableSpriteMovement.doLevelBoundary()`
**ROM Reference:** `sonic3k.asm:23183-23186` (`Player_Boundary_Sides`, strict path: `Camera_max_X_pos + $128`); `s2.asm:36907-36909` (normal path: `Camera_max_X_pos + $128 + $40`)

### Behavior

The player's right level-boundary clamp is the level's design edge: `Camera_max_X_pos` plus a fixed offset relative to the **native** 320px screen width — NOT the render viewport.

- **Strict path** (S3K `Player_Boundary_Sides`, boss fight, end-of-level): `Camera_max_X_pos + $128` (= `maxX + 320 - 24`)
- **Normal path** (S1/S2/S3K non-strict ground/air): `Camera_max_X_pos + $128 + $40` (= `maxX + 320 - 24 + 64`)

`camera.getMaxX()` holds the native ROM `Camera_Max_X_pos` (the level's right scroll limit, e.g. EHZ `0x2940`), so `maxX + 320` is the level's right wall. `RightBoundary.compute` is called with the fixed `LEVEL_DESIGN_WIDTH = 320`, so the clamp lands at the wall at **every** `DISPLAY_ASPECT` — fully reproducing the ROM `+$128` / `+$128 + $40` values. This is NOT a divergence: native and widescreen produce identical boundaries.

### Why it must not widen

An earlier widescreen pass computed the boundary with `camera.getWidth()` instead of the native width, so at a wider viewport the clamp moved right with the screen (e.g. ULTRA_21_9 → `maxX + 528 - 24` = level edge + 184). Because the level geometry only exists up to `maxX + 320`, this let the player **walk past the level's right wall into the void beyond a camera lock and fall to their death** where no level exists. The boundary tracks the level's wall, not the screen, so it stays native regardless of viewport.

A known cosmetic consequence at widescreen: when the camera is locked at `maxX`, the wider screen renders past the level edge (`maxX + 320 .. maxX + viewportWidth`) as empty space, and the player stops before reaching the visible right edge. That is the safe trade-off.

**Do not "fix" this by clamping the camera's reachable X.** Capping the camera's right edge at the level edge (effective top-left max `maxX − (viewportWidth − 320)`) was tried and reverted: level events trigger on `camera.getX() >= threshold` where the threshold sits near `maxX` (e.g. `Sonic2EHZEvents` spawns the boss at `camera.getX() >= 0x28F0` with `maxX = 0x2940`). Reducing the camera's reachable X by the widescreen inset (208px at ULTRA) stops the camera short of those thresholds, so bosses never spawn and arena locks never engage. A real fix would have to make the event thresholds themselves viewport-aware, which is a larger, ROM-state-sensitive change deferred for now.

### Verification

`TestRightBoundary` pins the pure-function math; `TestPlayableSpriteMovement.rightLevelBoundaryIsViewportIndependentAtWidescreen` drives `doLevelBoundary` with a 528px camera and asserts the clamp still lands at the native level edge.

---

## Object Despawn and Visibility Windows

**Location:** `AbstractObjectInstance.isInRange()`, `AbstractObjectInstance.isChkObjectVisible()`
**ROM Reference:** `Macros.asm` (`out_of_range` macro: `cmpi.w #128+320+192,d0`); `docs/s1disasm/_incObj/sub ChkObjectVisible.asm`

### Original Implementation

**`out_of_range` despawn:** The ROM chunk-aligns the object X and the camera-left-minus-128 position, subtracts them (16-bit unsigned wrap), and compares against a single compile-time constant: `128 + 320 + 192 = 640` (`$280`). Any object whose distance exceeds 640 pixel-widths is deleted.

**`ChkObjectVisible` visibility:** The ROM subtracts the camera X from the object X (`move.w $10(a0),d0` / `sub.w (v_screenposx).w,d0`) and rejects the object if `dx` is outside `[0, 320)`, and similarly for Y / `[0, 224)`.

Both constants encode the native Mega Drive screen dimensions: 320 × 224 pixels.

### Our Implementation

Both limits are derived from the cached `cameraBounds` rectangle rather than hardcoded constants:

```java
// isInRange — despawn window scales with viewport width
int viewportWidth = cameraBounds.right() - cameraBounds.left();
return dist <= (128 + viewportWidth + 192);

// isChkObjectVisible — visibility rectangle scales with viewport
int viewportWidth  = cameraBounds.right()  - cameraBounds.left();
int viewportHeight = cameraBounds.bottom() - cameraBounds.top();
if (dx < 0 || dx >= viewportWidth)  return false;
return dy >= 0 && dy < viewportHeight;
```

`cameraBounds` is updated once per frame by `ObjectManager.updateCameraBounds()` from `camera.getX() / getY() / getWidth() / getHeight()`, so the window always matches the configured viewport.

**Load-ahead window is capped (intentionally narrower than the despawn window).** The despawn/visibility windows above scale by the *full* viewport width so on-screen objects at the wider right edge are never culled. The *spawn load-ahead* window (`AbstractPlacementManager.loadAheadFor`), however, grows only by the minimum pre-load lead — `max(320 + extraAhead, viewportWidth + 128)` — NOT by `viewportWidth + extraAhead`. The object slot pool is a fixed ROM-sized table (`ObjectSlotLayout`: S1=96, S2=112, S3K=89); a window that grew by the full extra width overran the pool in dense areas, so `allocateSlot()` returned −1 and spawns were silently dropped (objects intermittently failing to load when scrolling right at widescreen, in all games). Capping the load-ahead keeps the live-object count close to native (≈+2% at ULTRA_21_9 vs +27% before) so the pool no longer overruns, while the wider despawn/visibility windows still prevent right-edge culling. This narrower load window is deliberate — do not widen it to match the despawn window. Native (320) is byte-identical (load-ahead = `0x280`).

### Parity at Native Width

At `DISPLAY_ASPECT = NATIVE_4_3` (viewport width 320, height 224):

| Check | Engine limit | ROM literal | Match |
|-------|-------------|-------------|-------|
| `isInRange` | 128 + 320 + 192 = 640 | `$280` = 640 | exact |
| `isChkObjectVisible` X | `[0, 320)` | `[0, 320)` | exact |
| `isChkObjectVisible` Y | `[0, 224)` | `[0, 224)` | exact |

`TestObjectViewportWindowWidth` pins this parity with dedicated native-width test cases.

### Rationale

At widescreen viewport widths (e.g. `ULTRA_21_9` = 528 px) the ROM's hardcoded 640 despawn distance is smaller than the distance from the camera to the visible right edge of the widened viewport (~656 px), causing objects near the right edge to be incorrectly deleted mid-screen. Similarly, `ChkObjectVisible` with `dx >= 320` would report objects past the native right edge as invisible even though they are fully in view. Scaling both checks with `viewportWidth` is the correct design decision for the widescreen extension — an object genuinely in view on the wider screen must not be culled or despawned. Note this is the opposite of the [Right-Boundary Is Viewport-Independent (Level Edge)](#right-boundary-is-viewport-independent-level-edge) entry above: despawn/visibility track what is *on screen* (so they widen), whereas the right level boundary tracks the *level's wall* (so it stays native).

### Verification

`TestObjectViewportWindowWidth` covers all four cases: native `isInRange` at 640 and just past, widescreen `isInRange` at 768 (in range) and 896 (out of range); native `isChkObjectVisible` at dx/dy just inside and at exclusive bounds; widescreen `isChkObjectVisible` at dx=321/400 (visible past old 320 limit) and dx=528 (invisible at exclusive widescreen bound).

### Removal Condition

This entry should remain as long as widescreen `DISPLAY_ASPECT` presets are supported. It would only be removed if the engine reverted to a fixed 320 × 224 viewport assumption.
---

## Pre-Level Intro Prefix Trace Bootstrap Contract

**Location:** `TraceReplayBootstrap`, `TraceMetadata.hasPreLevelIntroPrefix`
**Scope:** Trace replay fixture compatibility only; not live gameplay.

### Original State

Some end-to-end replay fixtures intentionally begin recording before the ROM has
entered its first comparable LEVEL-mode row. Frame 0 may still contain stale
Player_1 RAM from pre-level or transition setup, while the trace must still
drive the visible intro prefix to preserve timers, object setup, and global
phase.

### Our Implementation

Such fixtures declare the generic `pre_level_intro_prefix` metadata capability.
`TraceReplayBootstrap` then starts replay from trace frame 0, validates the
current BK2 row while driving gameplay with the previously latched movie input,
treats rows before the first LEVEL-mode event as VBlank-only, advances
post-level input-latch rows without comparing their duplicated sampled state,
and uses the recorded `gameplay_start` checkpoint only to end the
prefix-specific phase classifier.
The bootstrap does not key this behavior from game id, zone id, act, route, or
fixture name, and it does not copy trace-row player state into the engine.

### Rationale

The ROM can spend hundreds of frames in setup/intro execution before the first
strict gameplay comparison row. Skipping directly to that row loses native
state that a single metadata start position cannot reconstruct, but treating
the early stale Player_1 rows as normal gameplay comparisons is equally wrong.
The explicit capability makes the fixture contract visible and reusable without
preserving a zone-specific carve-out.

### Removal Condition

This entry can be removed if all release replay fixtures start at a comparable
LEVEL-mode row or if the trace recorder exposes a richer generic phase stream
that makes this metadata capability redundant.

---

## S2 Tornado Ride-Start Trace Bootstrap Contract

**Location:** `TraceReplayBootstrap.usesS2TornadoRideStartForTraceReplay`,
`TraceReplaySessionBootstrap.applyS2TornadoRideStart`
**Scope:** Sonic 2 SCZ/WFZ trace replay comparison setup.

### Contract

The S2 Tornado route bootstrap is a deterministic native prelude contract for
SCZ/WFZ ride-start traces. It is not trace-row hydration: replay setup discovers
the live ObjB2 Tornado shape and applies the same title-card/object prelude
needed to reach the first comparable gameplay frame. Non-Tornado S2 traces fall
back to the generic title-card object ticks and must not receive the ride-start
state.

### Rationale

The ROM runs route-specific title-card and Tornado setup before normal gameplay
comparison begins. The replay fixture needs that deterministic prelude so frame
0 compares engine state to the same ROM phase. The contract is acceptable only
because it is route/object-state driven and covered by tests proving ordinary S2
routes do not get the Tornado prelude.

### Verification

`TestTraceReplayStartPositionPolicy`, `TestSonic2TornadoRidePrelude`, and
`TestPreludeFramesKnobsZero` cover the policy boundaries: SCZ/WFZ ride-start
fixtures use the Tornado prelude, ordinary S2 traces do not, and metadata-only
knobs remain zero unless the live ObjB2 shape selects the object prelude.

---

## S2 CNZ Slot-Machine Trace Bootstrap Contract

**Location:** `TraceReplayBootstrap.zoneFeatureTitleCardPreludeFramesForTraceReplay`
**Scope:** Sonic 2 trace replay fixture compatibility only; not live gameplay.

### Contract

S2 CNZ replay fixtures that advertise per-frame slot-machine state need a short
native slot-machine init prelude before comparison begins. The bootstrap now
consumes this through generic `TraceMetadata.hasPerFrameSlotMachineState()`
capability metadata; the old CNZ-named metadata helper remains only as a
deprecated alias for the current recorder schema string.

### Rationale

This is accepted release debt because it is a fixture capability boundary, not
a gameplay rule. The live game should model the ROM slot-machine state directly;
the trace replay layer only bridges old fixture data that did not record enough
state to compare from frame 0 without the prelude.

### Removal Condition

Regenerate the affected fixtures with a recorder schema name that is no longer
CNZ-specific, or replace the fixture-capability predicate with explicit runtime
feature-state phase metadata.

---

## S3K Sidekick Seed-Frame Trace Bootstrap Debt

**Location:** `TraceReplayBootstrap` sidekick seed-frame fixture capability
**Scope:** Sonic 3 and Knuckles trace replay comparison setup.

### Contract

Some S3K sidekick trace fixtures need one native sidekick setup tick before
normal comparison because trace frame 0 is a seed row for sidekick/history state.
Replay now requires explicit `TraceMetadata.hasSidekickSeedFramePrelude()`
capability metadata (`sidekick_seed_frame_prelude`) for that bootstrap phase.

### Rationale

The fixture capability is accepted trace replay setup, not live gameplay policy.
It replaces the previous first-frame movement-shape heuristic and keeps the
special bootstrap path out of route/frame carve-outs. `TestBuildToolingGuard`
guards `TraceReplayBootstrap` against regaining the retired shape inference.

### Removal Condition

Replace the ad hoc fixture capability string with recorder-emitted ROM phase
metadata for sidekick seed/history setup, then update replay bootstrap to
consume that richer phase contract.

---

## S3K Complete-Run Segment Start-Position Bootstrap Debt

**Location:** `TraceReplayBootstrap.isS3kCompleteRunSegment`,
`TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap`
**Scope:** Sonic 3 and Knuckles complete-run trace replay setup only.

### Current Boundary

S3K complete-run per-zone segments no longer seed frame-zero player, sidekick,
camera, object, or CPU state from trace rows. They drive and compare from trace
frame 0 with `ReplayStartState.DEFAULT`; `shouldSeedFrameZeroForTraceReplay`
and `shouldSeedReplayStartStateForTraceReplay` both remain false. The remaining
fixture dependency is narrower: replay setup applies the metadata start centre
coordinates once, then runs the same ground-snap/camera/event initialization
path used by ordinary trace fixtures.

### Rationale

These segments arm at a zone handoff or first controllable frame from a longer
complete-run movie, so the fixture metadata supplies the save-state entry
position for the segment. That is accepted frame-zero bootstrap debt, not
per-frame trace hydration, and it must not expand into copying recorded trace
rows or sidekick/camera state back into the engine.

### Verification

`TestTraceReplayStartPositionPolicy.s3kCompleteRunSegmentsDoNotSeedFrameZeroTraceState`
checks current complete-run fixtures use metadata start position only, keep an
unseeded replay start, and do not receive the S3K sidekick seed-row prelude.
`TestBuildToolingGuard.traceReplayLegacyExceptionsShouldBeDocumentedAndBounded`
keeps this release-debt entry present.

### Removal Condition

Replace per-segment metadata start positions with a native ROM-state handoff
model or regenerate complete-run fixtures so the replay can enter each segment
from the real preceding state without fixture-provided start coordinates.

---

## Frame-0 Trace Bootstrap Snapshot Coverage Debt

**Location:** `AbstractTraceReplayTest.captureEngineSnapshot`,
`TraceBinder.compareBootstrapFrame0`
**Scope:** Trace replay verification coverage only; not live gameplay.

### Current Boundary

The frame-0 bootstrap comparator is comparison-only: it never hydrates engine
state from `player_history_snapshot`, `cpu_state_snapshot`, or
`object_state_snapshot` events. However, the engine snapshot currently supplies
player history only. Sidekick CPU views and per-slot SST snapshots are left
empty because the needed live accessors are outside the release-review patch
scope. When a trace records those views and the engine cannot provide them, the
comparator emits bootstrap warnings rather than strict errors.

### Release Meaning

This is not a full sidekick/SST parity proof. A trace run with zero per-frame
errors but bootstrap warnings proves only the currently compared fields. Release
validation may treat warnings as blocking, but until engine-side sidekick CPU
and object-slot snapshot extraction is implemented, warning-free frame-0
sidekick/SST coverage is not guaranteed by this comparator.

### Removal Condition

Add native engine snapshot accessors for the sidekick CPU fields and cardinal
per-slot object SST fields, wire them into `AbstractTraceReplayTest`, then make
missing engine views for recorded native-prelude traces strict failures.

---

## Sonic 1 Embedded Runtime Data Ratchet

**Location:** `Sonic1ObjectArtProvider`, `Sonic1BossMappings`
**Scope:** Sonic 1 runtime data source debt.

### Current State

The runtime does not read gameplay asset bytes from `docs/` disassembly trees.
The former palette-cycle rows, conveyor waypoint and child spawner tables, GHZ
bridge bend tables, and the small `Map_Seesaw` / `Map_SSawBall` / `Map_Fan` /
`Map_Pylon` / `Map_Scen` / `Map_ExplodeItem` support-object mapping slice, the
LZ `Map_Jaws` / `Map_Burro` / `Map_Flap` / `Map_WFall` / `Map_Splash` tables,
the MZ/SLZ `Map_Fire` table, the MZ `Map_Bas` / `Map_Glass` / `Map_CStom` / `Map_Geyser` / `Map_LWall` tables, the LZ `Map_LConv` / `Map_Bub` tables, the GHZ `Map_Hel` / `Map_Swing_GHZ` tables, the SLZ `Map_Swing_SLZ` table, the SYZ
`Map_Bump` / `Map_Spring` / `Map_Roll` / `Map_Yad` tables, the GHZ `Map_Crab` / `Map_Moto` / `Map_Newt` / `Map_Buzz` / `Map_Missile` tables, the GHZ/SLZ `Map_Smash` table, the
LZ/SLZ/SBZ `Map_Orb` table, the LZ `Map_Harp` table, the MZ/SBZ `Map_Cat` table, the SBZ `Map_Hog` table, the SLZ/SBZ `Map_Bomb` table, the SBZ `Map_Flame` / `Map_Saw` / `Map_Elec` / `Map_ADoor` / `Map_Gird` /
`Map_Trap` / `Map_Spin` / `Map_Stomp` tables, the shared button `Map_But` table, the shared
animal `Map_Animal1` / `Map_Animal2` / `Map_Animal3` tables, the special-stage
result-card `Map_Got` / `Map_SSR` tables, the special-stage result emerald
`Map_SSRC` table, the Prison Capsule `Map_Pri` table, the Giant
Ring `Map_GRing` / Ring Flash `Map_Flash` tables, the GHZ giant ball
`Map_GBall` table, the SYZ/LZ spikeball chain `Map_SBall` / `Map_SBall2`
tables, the Big Spiked Ball `Map_BBall` table, the LZ Gargoyle `Map_Gar`
table, the LZ Block `Map_LBlock` table, the SYZ Boss Block `Map_BossBlock`
table, the SBZ rotating junction `Map_Jun` table, the SBZ Running Disc `Map_Disc` table, the LZ Breakable Pole
`Map_Pole` table, the MZ/LZ Push Block `Map_Push` table, the MZ Brick `Map_Brick` table,
the SYZ Spinning Light `Map_Light` table, the MZ Smashable Green Block
`Map_Smab` table, the MZ/SLZ/SBZ Collapsing Floor `Map_CFlo` table, the
MZ/SBZ Moving Block `Map_MBlock` table, the LZ Moving Block `Map_MBlockLZ`
table, the GHZ/SYZ/SLZ Basic Platform `Map_Plat_GHZ` / `Map_Plat_SYZ` /
`Map_Plat_SLZ` tables, the SLZ Elevator/Circling Platform/Staircase
`Map_Elev` / `Map_Circ` / `Map_Stair` tables, the unused small explosion
`Map_UnkExplode` table, the GHZ Collapsing Ledge `Map_Ledge` table, the MZ
large grassy platform `Map_LGrass` table, the SBZ
vanishing platform `Map_VanP` table, the SYZ/SLZ/LZ floating block and door
`Map_FBlock` table, the SBZ2
`Map_FFloor` table, the shared boss `Map_Eggman` / `Map_BossItems` tables, the SBZ2/FZ `Map_SEgg` table, the ending
`Map_ESon` / `Map_ECha` / `Map_ESth` tables, plus the Final Zone
`Map_EggCyl` / `Map_PLaunch` / `Map_Plasma` / `Map_FZLegs` / `Map_FZDamaged`
boss mapping slice, now load from the user-supplied ROM and their guard budgets
have been ratcheted down. The Sonic 1 object-provider and boss mapping budgets
are zero; remaining tile-word transformations use the shared
`SpriteMappingPieces` helper over ROM-loaded frames rather than provider-local
mapping literals.

### Release Boundary

New gameplay runtime asset data must still be ROM-backed.
`TestArchitecturalSourceGuard` locks the current zero-exception counts for
these files so this debt cannot reappear silently under the release branch.

### Removal Condition

Replace each embedded table with ROM-backed loaders or generated mappings
through the normal user-supplied ROM pipeline, then reduce or remove the
`sonic1EmbeddedRuntimeDataExceptionsStayDocumentedAndBounded` ratchet.

## Batch-2 Rewind: Transient Cosmetic Children Not Rewound (Re-emit In-Frame)

`Sonic1MotobugSmokeInstance` is intentionally **not** captured/recreated across a
held-rewind boundary (no rewind codec; its `#recreate` and `#finalScalar` keys stay in
`src/test/resources/rewind/coverage-baseline.txt`). It is the Motobug exhaust puff:
an animation-only object with no collision and no player/score/terrain state that plays a
short smoke script then self-deletes. The parent `Sonic1MotobugBadnikInstance` already has
a rewind `#recreate` path and continuously re-emits a fresh puff within ~1 frame on
forward re-simulation, so a dropped in-flight puff is visually undetectable. An
`exactSpawnCodec` is also the wrong tool here because the captured `ObjectSpawn` does not
carry the puff's facing bit. This mirrors the AIZ2 transient-children precedent (and the
S3K `MgzEndBossDefeatDebrisChild` case in `docs/S3K_KNOWN_DISCREPANCIES.md`): capture is
only worthwhile when a dropped object would otherwise visibly re-emit and play forward; a
sub-lifetime cosmetic that re-emits in-frame does not qualify.

All other batch-2 S1 transient/relink children (`Sonic1BombFuseInstance`,
`Sonic1BombShrapnelInstance`, `Sonic1BuzzBomberMissileInstance`,
`Sonic1BuzzBomberMissileDissolveInstance`, `Sonic1CannonballInstance`,
`Sonic1CaterkillerBodyInstance`, `Sonic1CrabmeatProjectileInstance`,
`Sonic1NewtronMissileInstance`, `GHZBossWreckingBall`, `Sonic1SLZBossSpikeball`,
`SYZBossSpike`) now have rewind codecs in `Sonic1ObjectRegistry` and are restored on a
backward seek.

## Batch-3 Rewind: Transient Cosmetic Children Not Rewound (Re-emit In-Frame)

`Sonic1SplashObjectInstance` (LZ water splash, object `0x08`) is intentionally **not**
captured/recreated across a held-rewind boundary (no rewind codec; its `#recreate` and
`#finalScalar` keys stay in `src/test/resources/rewind/coverage-baseline.txt`). It is a
short-lived, purely cosmetic water splash with no collision and no player/score/terrain
state: a 3-frame animation (~12 game ticks) that self-deletes, spawned on water
entry/exit. The water-entry/exit code path naturally re-emits it within ~1 frame on
forward re-simulation, so a dropped in-flight splash is visually undetectable. This
mirrors the AIZ2 transient-children precedent and the batch-2 `Sonic1MotobugSmokeInstance`
case above: capture is only worthwhile when a dropped object would otherwise visibly
re-emit and play forward; a sub-lifetime cosmetic that re-emits in-frame does not qualify.

All other batch-3 S1 objects that were previously dropped now have rewind codecs in
`Sonic1ObjectRegistry` and are restored on a backward seek: `FZCylinder`,
`FZPlasmaLauncher`, `FZPlasmaBall`, `Sonic1BossBlockInstance` (boss + fragment forms),
`Sonic1CollapsingFloorObjectInstance`, `Sonic1EggPrisonObjectInstance`,
`Sonic1ExplosionItemObjectInstance`, `Sonic1FloatingBlockObjectInstance`,
`Sonic1GrassFireObjectInstance`, `Sonic1LamppostTwirlInstance`,
`Sonic1MonitorPowerUpObjectInstance`, `Sonic1RingFlashObjectInstance`,
`Sonic1RingInstance` (collected/animating child rings),
`Sonic1SeesawBallObjectInstance`, `Sonic1SpikedBallChainObjectInstance`,
`Sonic1StomperDoorObjectInstance`, and `Sonic1TeleporterObjectInstance`.

## Batch-4 Rewind: Transient Cosmetic Children Not Rewound (Re-emit In-Frame)

`CPZBossSmokePuff` (CPZ boss retreat smoke) is intentionally **not** captured/recreated
across a held-rewind boundary (no rewind codec; its `#recreate` key stays in
`src/test/resources/rewind/coverage-baseline.txt`). It is a purely cosmetic smoke effect
with no collision and no player/score/terrain state: it re-derives its X/Y from the live
boss every frame (`x = mainBoss.getX() - 0x28`, `y = mainBoss.getY() + 4`) and
self-destructs when the boss is destroyed. It is also currently dead code — nothing in
`src/main` or the tests ever constructs it (the CPZ boss only spawns Robotnik/Flame/Pump/
Container/Pipe), so it can never enter a rewind snapshot at runtime. This mirrors the AIZ2
transient-children precedent and the batch-2/3 cosmetic cases above. All other batch-4 S2
CPZ-boss components and hazards that were previously dropped now have rewind codecs in
`Sonic2ObjectRegistry` and are restored on a backward seek: `CPZBossContainer`,
`CPZBossContainerFloor`, `CPZBossFallingPart`, `CPZBossFlame`, `CPZBossGunk`,
`CPZBossPipe`, `CPZBossPipePump`, `CPZBossPump`, `CPZBossRobotnik`,
`LavaBubbleObjectInstance`, `MCZFallingDebrisInstance`, `BubbleObjectInstance`, and
`OOZBurnerFlameObjectInstance`.

## Batch-5 Rewind: Transient Cosmetic Children Not Rewound (Re-emit In-Frame)

`Sonic1TryAgainEmeraldsObjectInstance` (S1 Object-8C "TRY AGAIN" chaos-emerald orbit
display) is intentionally **not** captured/recreated across a held-rewind boundary (no
rewind codec; its `#recreate` key stays in `src/test/resources/rewind/coverage-baseline.txt`).
It is a `GameMode.TRY_AGAIN_END` end-screen display object that is never instantiated in
production gameplay (no `new Sonic1TryAgainEmeraldsObjectInstance` outside its own file and
no registry/ObjectId binding; the live Try-Again screen is rendered by
`com.openggf.game.sonic1.credits.TryAgainEndManager`, which re-implements the emerald orbit
standalone). Rewind capture is gameplay-mode-scoped (`RewindRegistry`/`RewindController` live
on `GameplayModeContext`), so this object can never appear in a held-rewind snapshot and the
"dropped on restore -> vanishes" failure mode cannot occur. It also has no `ObjectSpawn`
(`super(null, "TryChaos")`) and derives all per-emerald state lazily from
`GameStateManager` emerald data, so `exactSpawnCodec` is structurally inapplicable. It holds
no player/score/terrain state. This mirrors the AIZ2 transient-children precedent and the
batch-2/3/4 cosmetic cases above. All other batch-5 S1 objects that were previously dropped
now have rewind codecs in `Sonic1ObjectRegistry` and are restored on a backward seek:
`Sonic1EndingEmeraldsObjectInstance`, `Sonic1EndingSonicObjectInstance`,
`Sonic1GlassReflectionInstance`, and `Sonic1ResultsScreenObjectInstance`.

## Batch-6 Rewind: Transient Cosmetic Children Not Rewound (Re-emit In-Frame)

Two batch-6 cosmetic transient children are intentionally **not** captured/recreated across
a held-rewind boundary (no rewind codec; their `#recreate` / `#finalScalar` keys stay in
`src/test/resources/rewind/coverage-baseline.txt`). Both self-regenerate, hold no
player/score/terrain state, and are structurally awkward to codec because their only
constructor takes the live player rather than an `ObjectSpawn`. This mirrors the AIZ2
transient-children precedent and the batch-2/3/4/5 cosmetic cases above.

- `com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance` (S2 Super Sonic sparkle/trail,
  ROM Obj7E): every scalar field (`animActive`, `freezeFlag`, `mappingFrame`, `frameTimer`,
  `visible`, `snapX`, `snapY`) is re-derived each frame from the live player's speed and centre
  position, and a full 6-frame cycle re-emits continuously while `|gSpeed| >= 0x800`. Its only
  ctor is `(AbstractPlayableSprite player)` (`super(null, ...)`, no `ObjectSpawn`), so
  `exactSpawnCodec` cannot supply the arg; it is owned and re-spawned by
  `Sonic2SuperStateController` (not the power-up spawner), so a deferred player-bound codec
  would orphan the pending entry. Dropping it causes at most a brief cosmetic absence that
  naturally re-emits.
- `com.openggf.level.objects.SplashObjectInstance` (water splash; spawned on the S2 power-up path
  and the S3K HCZ miniboss path): a ~30-frame animation (10 frames x 3 ticks) that self-expires
  via `ObjectLifetimeOps.expireDynamic(this)`, re-emitted on every water entry/exit. Its direct
  S1 sibling `Sonic1SplashObjectInstance` is already an established accept-drop with the same
  rationale. `facingLeft` is spawn-derivable and the renderer is transient.

All other batch-6 S2 objects that were previously dropped now have rewind codecs in
`Sonic2ObjectRegistry` and are restored on a backward seek: `RingPrizeObjectInstance` (CNZ
slot-machine ring prize), `SteamPuffObjectInstance` (MTZ steam puff), `SeesawBallObjectInstance`
(HTZ seesaw ball, parent-relink), and `CPZBossContainerExtend` (CPZ-boss container extend,
boss+container relink).

## Batch-7 Rewind: Transient Cosmetic Children Not Rewound (Re-emit In-Frame)

One batch-7 cosmetic object is intentionally **not** captured/recreated across a held-rewind
boundary (no rewind codec; its `#recreate` / `#finalScalar` keys stay in
`src/test/resources/rewind/coverage-baseline.txt`). This mirrors the AIZ2 transient-children
precedent and the batch-2/3/4/5/6 cosmetic cases above.

- `com.openggf.level.objects.BoxObjectInstance` (debug-box base class; renders only a coloured
  outline + crosshair, holds no player/score/terrain state): it is **never** registered as a
  factory in any `*ObjectRegistry` and is never spawned as its own concrete type in gameplay —
  all real instances are subclasses (checkpoints, springs, bridges, CNZ blocks, elevators, etc.),
  each with its own object ID and its own codec. Its baseline keys
  (`#recreate` + `#finalScalar#{b,g,halfHeight,halfWidth,highPriority,r}`) are a
  `RewindCoverageAnalyzer` static over-approximation of a base class that no live carry path can
  produce as itself, so it can never actually be dropped on a held rewind. Accept-drop-as-baseline
  rather than registering a production codec for an abstract-role base class with no spawn factory.

All other batch-7 objects now have rewind codecs and are restored on a backward seek:
`com.openggf.level.objects.boss.BossExplosionObjectInstance` (shared boss-defeat explosion,
registered per-game in `Sonic1ObjectRegistry`/`Sonic2ObjectRegistry`) and
`com.openggf.level.objects.SignpostSparkleObjectInstance` (shared S1+S2 signpost ring sparkle,
in `ObjectRewindDynamicCodecs.sharedCodecs()`; its non-final `worldX`/`worldY` are reapplied after
recreate). S3K's signpost sparkle (`S3kSignpostSparkleChild`) is already codec'd separately.
