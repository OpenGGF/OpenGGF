# Sonic 2 (Genesis) REV01 (Sonic 2 “01”) Checkpoints (Starposts) (Object $79)

This page describes **Sonic 2 REV01** checkpoint (starpost) behavior for comparison
with OpenGGF. The ROM routines are the reference; their description here does not
certify that every saved field, allocation failure or 2P branch has engine parity.
See [current implementation entry points](#current-openggf-implementation-entry-points)
below for the classes to inspect.

Reference: the repository-pinned `docs/s2disasm` submodule. Match its commit when
citing lines; a locally modified checkout or a different build can move addresses.
Primary files:
- `s2.asm` (main disassembly)
- `s2.constants.asm` (RAM + constants)
- `mappings/sprite/obj79_a.asm` (starpost mappings)
- `mappings/sprite/obj79_b.asm` (special-stage star mappings)
- `art/nemesis/Star pole.nem` (Nemesis art)
- `sound/sfx/A1 - Checkpoint.asm` (SFX)

---

## Terminology

Sonic 2 calls these “star poles / starposts” (often called “lampposts” in older docs). Internally they are **Object ID $79** (`ObjID_Starpost`).

There are three distinct visual/gameplay pieces:

Checkpoint pole (object $79, routine 0/2/4)
- the pole sprite itself, idle or activated

Checkpoint activation “dongle” (object $79, routine 6)
- the short-lived dangling sparkle/marker that swings briefly after activation

Special-stage “stars” (object $79, routine 8)
- the four orbiting stars that appear only when you hit a checkpoint with >= 50 rings (and fewer than 7 emeralds, in 1P)

---

## ROM offsets and labels (REV01)

All offsets below are ROM addresses as annotated in the disassembly labels/comments.

### Core object code
- Starpost object entry (`Obj79`) (Object $79): **ROM $001F0B4** (`s2.asm`, “Object 79 - Star pole / starpost / checkpoint”)
- Activation + save: `Obj79_CheckActivation` / `Obj79_SaveData`: within the same block (starting at **$001F0B4**)
- Load-from-checkpoint: `Obj79_LoadData`: **ROM $001F35E**
- Create special stars: `Obj79_MakeSpecialStars`: **ROM $001F4C4**
- Special star update logic: `Obj79_Star`: **ROM $001F536**

### Sprite mappings
- Starpost + dongle mappings: `Obj79_MapUnc_1F424` -> `mappings/sprite/obj79_a.asm` (**ROM $001F424**)
- Special star mappings: `Obj79_MapUnc_1F4A0` -> `mappings/sprite/obj79_b.asm` (**ROM $001F4A0**)

### Level start / respawn hook
- Level boundary + start location setup: `LevelSizeLoad` (**ROM $0000BFBC**)
  - If a checkpoint was previously activated, `LevelSizeLoad` calls `Obj79_LoadData` to restore saved state before camera initialization.

### Art + VRAM tile base
- VRAM tile base for checkpoint graphics: `ArtTile_ArtNem_Checkpoint = $047C` (`s2.constants.asm`)
- Pattern load request list: `PlrList_Std2` includes `plreq ArtTile_ArtNem_Checkpoint, ArtNem_Checkpoint` (`s2.asm`)
- Checkpoint Nemesis art: `ArtNem_Checkpoint: BINCLUDE "art/nemesis/Star pole.nem"` (`s2.asm`)

---

## Object placement in level data

Starposts are placed in level object layouts as **Object $79** with a **subtype** that encodes:
- low 7 bits: a per-act “checkpoint index” (increasing along level progression)
- bit 7 ($80): a flag used by checkpoint restore logic (see “camera min X lock” below)

Important: starposts are not “one global checkpoint”. Multiple starposts exist per act; the engine tracks the **highest index activated**.

---

## Runtime state: variables saved and restored

### The “activated checkpoint” marker
- `Last_star_pole_hit` (1 byte): the last activated starpost subtype (for player 1)
- `Saved_Last_star_pole_hit` (1 byte): copy used for restoring (`Obj79_LoadData` copies it back)

These are in the global work RAM map (see `s2.constants.asm` around `Last_star_pole_hit`).

### Saved checkpoint snapshot (player 1)

When player 1 activates a checkpoint, `Obj79_SaveData` stores:

- `Saved_x_pos`, `Saved_y_pos`: the checkpoint object’s world position (not the player’s)
- `Saved_Ring_count`: the ring count at time of activation
- `Saved_Timer`: the timer at time of activation
- `Saved_art_tile`: the player’s current `art_tile` (important for 2P art pointer adjustments / character tiles)
- `Saved_Solid_bits`: the player’s solid-bit / collision layer bits
- `Saved_Camera_*`: camera and parallax positions (X/Y for main BG and BG2/BG3)
- `Saved_Water_*`: water level and water flags (only applied if the level actually has water)
- `Saved_Extra_life_flags`: “extra life already awarded” flags at time of activation
- `Saved_Dynamic_Resize_Routine`: per-level dynamic boundary/event routine state
- `Saved_Camera_Max_Y_pos`: camera max Y boundary at time of activation

This snapshot is the “checkpoint state” that the engine can revert to.

There are parallel `_2P` variables for player 2 in 2-player mode.

---

## Graphics loading

### Art source and loading model

Checkpoint graphics are part of the standard level art set:

- Pattern Load Request list: `PlrList_Std2` always requests `ArtNem_Checkpoint` to VRAM tile base `ArtTile_ArtNem_Checkpoint ($047C)`.

In practice, this means you do not load checkpoint graphics on-demand when starposts spawn. They are available as soon as the level’s standard PLCs are processed.

### Sprite mappings and frames

Checkpoint pole mappings: `mappings/sprite/obj79_a.asm` (`Obj79_MapUnc_1F424`) defines 5 frames (indices 0–4).

Animation script `Ani_obj79` (in `s2.asm`) chooses frames as follows:
- anim 0: static frame 0 (idle)
- anim 1: static frame 1 (activated but not yet “finished”)
- anim 2: alternates frame 0 and frame 4 with delay 3 (the persistent activated appearance)

The spawned “dongle” object uses `mapping_frame = 2` (a 2x2 piece) and is animated by movement rather than the animation script.

Special stars mappings: `mappings/sprite/obj79_b.asm` (`Obj79_MapUnc_1F4A0`) defines:
- frame 0: a 2x2 star
- frames 1–2: tiny 1x1 sparkle frames used while orbiting (the routine cycles them)

---

## Activation rules (running through a checkpoint)

Main routine flow (1P + 2P):
- `Obj79_Main` checks activation for player 1, and also player 2 if `Two_player_mode` is enabled.

Activation guard:
- The object compares `Last_star_pole_hit & $7F` against its own `subtype & $7F`.
- If the last hit checkpoint index is already >= this checkpoint index, it will not re-activate.

Overlap region (player must pass through):
- Horizontal: centre delta from -8 inclusive to +8 exclusive (`x_delta + 8 < $10`, unsigned word comparison).
- Vertical: centre delta from -$40 inclusive to +$28 exclusive (`y_delta + $40 < $68`, unsigned word comparison).

On success (player passes through and it’s “new”):
- Plays `SndID_Checkpoint` (“ding-dong”)
- Spawns a secondary object (also ID $79) in routine 6 (“dongle”) that swings for a short duration
- Sets the main starpost animation to `anim = 1` (activated)
- Calls `Obj79_SaveData` to capture the checkpoint snapshot
- Marks the starpost as “activated” in the respawn table (so it stays activated if it despawns and respawns due to camera)

---

## Special-stage stars (>= 50 rings)

Stars are created at the moment the checkpoint is activated if all are true:
- the activation dongle allocation succeeded
- not in 2-player mode
- `Emerald_count != 7`
- `Ring_count >= 50`

Creation (`Obj79_MakeSpecialStars`):
- Attempts to allocate four objects after the current slot (same ID $79), each with routine 8 (“star”); allocation exhaustion stops the loop.
- Each star stores:
  - a center point (checkpoint X, checkpoint Y - $30)
  - an angle offset (0, $40, $80, $C0)
  - a growth/lifetime counter (`objoff_36`) starting at 0
- The star motion is a sinusoidal orbit around that center point, with a grow-in period, a collidable middle period, then a shrink-out and deletion.

When do stars become “enterable”:
- When `objoff_36 == $80`, the star sets `collision_flags = $D8`.
- At this stage, collision triggers special stage entry:
  - sets `(f_bigring)` and sets `Game_Mode = SpecialStage` (via the star’s collision property processing)

Important for reimplementation: the stars are not a separate object ID. They are still object $79 with a different routine.

---

## Checkpoint “respawn persistence” (scrolling away and back)

The starpost uses an entry in the **Object Respawn Table** (per-object respawn index) to remember if it has been activated.

At init (`Obj79_Init`):
- If the respawn table indicates it is activated, it forces the starpost into activated visuals (`anim = 2`) and sets the respawn activated bit.
- Otherwise it compares against `Last_star_pole_hit` (so if you activated a later checkpoint, earlier ones will appear activated too).

This matters if your engine streams objects in/out based on camera: the starpost visuals must be deterministic from saved state even after despawn/respawn.

---

## Restoring state after death (continue from checkpoint)

### Where restore happens

On level start or restart, the engine decides whether to start at the act’s start location or from a checkpoint.

During `LevelSizeLoad` (ROM $0000BFBC):
- If `Last_star_pole_hit != 0`, it calls `Obj79_LoadData` to restore checkpoint state.
- If not, it loads the act’s default start position from the `StartLocations` table.

There is also related logic that controls whether the HUD and counters are cleared on level init:
- If starting from a checkpoint (`Last_star_pole_hit != 0`), the code path skips clearing rings/time/extra-life flags in `Level_ClrHUD` (later code may still override, see below).

### What `Obj79_LoadData` restores (and what it resets)

`Obj79_LoadData` restores:
- `MainCharacter x/y` to the saved checkpoint position
- `Timer` to the saved timer, then adjusts it (sets frame = 59 and decrements seconds by 1)
- camera X/Y and all saved BG parallax positions
- character `art_tile` and collision solid bits
- water state (only if `Water_flag` indicates the level supports water)
- `Dynamic_Resize_Routine` and `Camera_Max_Y_pos` state

Rings and extra-life flags:
- The routine copies `Saved_Ring_count` and `Saved_Extra_life_flags` into their live variables, but then immediately clears them (`Ring_count = 0`, `Extra_life_flags = 0`).

Practical outcome:
- Restarting from a checkpoint restores position and world/camera state, but the player restarts with **0 rings**.

This matches the classic Sonic behavior where rings are lost on death even if you respawn at a checkpoint.

### Camera minimum X lock (subtype bit 7)

After restoring, `Obj79_LoadData` checks:
- `tst.b (Last_star_pole_hit)` then `bpl.s return`
- if the value is negative (bit 7 set), it sets:
  - `Camera_Min_X_pos = Saved_x_pos - $A0`

Meaning:
- If the checkpoint’s subtype has bit 7 set ($80), the game prevents the camera from scrolling back behind a point near the checkpoint after respawn.

When implementing subtype parsing, preserve bit 7 and implement this behavior as part of restore.

---

## Animation details you need to match

Pole object (routine 0/2/4):
- routine 0 (`Obj79_Init`) sets mappings, tile base, render flags, width = 8px, priority = 5
- routine 2 (`Obj79_Main`) checks activation and then falls through to animation
- routine 4 (`Obj79_Animate`) uses `AnimateSprite` with `Ani_obj79`
- rendering is standard `MarkObjGone` behavior

Dongle object (routine 6):
- created when a checkpoint is activated
- initial state:
  - `mapping_frame = 2`
  - `objoff_30/32` store its center point (checkpoint x, checkpoint y - $14)
  - `objoff_36 = $20` is its lifetime counter
  - `parent` points back to the checkpoint object
- motion:
  - decrements `objoff_36` each frame
  - uses `angle` updated by -$10 each frame, feeding `CalcSine`, scaling by $C00 to produce a small circular swing
- when timer expires:
  - it sets the parent checkpoint to the “finished” look (`anim = 2`, `mapping_frame = 0`)
  - deletes itself

Stars object (routine 8):
- orbit uses `CalcSine` plus additional shaping math, and scales with `objoff_36` to produce grow-in, stable orbit, shrink-out
- becomes collidable when `objoff_36 == $80` by setting `collision_flags = $D8`
- deletes itself after shrink-out ends

---

## Current OpenGGF implementation entry points

The implementation now exists; use these owners instead of recreating the earlier
prototype design:

| Responsibility | Current owner |
|---|---|
| Pole activation and native overlap checks | `game/sonic2/objects/CheckpointObjectInstance` |
| Swinging marker and orbiting stars | `CheckpointDongleInstance`, `CheckpointStarInstance` in the same package |
| Shared saved checkpoint state and rewind capture | `game/CheckpointState` |
| Level-load restoration | `level/LevelCheckpointCoordinator`, called by `LevelManager` |
| ROM art and mapping addresses | `game/sonic2/constants/Sonic2Constants` |
| Standard art registration and decode | `Sonic2PlcArtRegistry`, `Sonic2ObjectArt` |

Paths are relative to `src/main/java/com/openggf/`. The art constants are
`ART_NEM_CHECKPOINT_ADDR`, `MAP_UNC_CHECKPOINT_ADDR`,
`MAP_UNC_CHECKPOINT_STAR_ADDR` and `ART_TILE_CHECKPOINT`. Decode their bytes from
the user-supplied ROM. Disassembly mapping files are research references, never
runtime assets or baked fallbacks.

Objects use injected `services()` for checkpoint, audio and child-object access.
Separate Java classes for pole, dongle and stars are intentional; preserve the
native object ID, allocation order and behavior rather than requiring one class
for every routine. Save/restore changes must also preserve rewind and load boundaries.

Focused checks include `TestCheckpointDongleInstance`,
`TestCheckpointObjectInstanceTimeAttackGate`, `TestCheckpointStateRewind`,
`TestCheckpointStarpostGraphRewind` and `TestOrdinaryCheckpointActivationRestore`.
These cover specific contracts; their existence does not establish complete
REV01 checkpoint parity. Use [the testing guide](../contributing/testing.md)
and the affected ROM-backed route checks when changing behavior.

---

## Known pitfalls when reimplementing

If you see visual or state corruption in your current implementation, checkpoints have a couple of common failure modes:
- Treating subtype as signed without masking for ordering (you must compare on `& $7F`)
- Dropping subtype bit 7 (camera min X lock after restore will not work)
- Not restoring camera BG/BG2/BG3 positions, causing parallax jumps after death
- Confusing a separate helper class with a separate native ID: stars still represent object $79 routine 8.
- Loading checkpoint art on-demand instead of as part of standard level PLC (can lead to missing tiles if your load timing differs)
- Reading the `fixBugs` branch as retail behavior: shipped REV01 uses `fixBugs = 0`, including its missing respawn-index guard for debug-created posts.

---

## Quick reference tables (summary)

### Constants
| Name | Value | Notes |
|---|---:|---|
| Object ID | $79 | Starpost, dongle, and stars all use $79 |
| Art tile base | $047C | `ArtTile_ArtNem_Checkpoint` |
| Rings threshold | 50 | Stars spawn only if rings >= 50 and emeralds != 7 and not 2P |

### Key routines (ROM)
| Routine | ROM | Purpose |
|---|---:|---|
| `Obj79` | $001F0B4 | Main object entry |
| `Obj79_SaveData` | $001F298 (within block) | Save checkpoint snapshot |
| `Obj79_LoadData` | $001F35E | Restore from checkpoint snapshot |
| `Obj79_MakeSpecialStars` | $001F4C4 | Spawn 4 orbiting stars |
| `Obj79_Star` | $001F536 | Orbit + collision + delete |
