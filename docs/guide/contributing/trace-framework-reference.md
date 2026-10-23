# Trace Framework Reference

A deep, end-to-end reference for the OpenGGF trace-replay framework. The live
recording and publication path is the native BizHawk headless harness and the
single v5 contract documented in
[`trace-v5-publication.md`](trace-v5-publication.md). The Lua and stable-retro
sections below are retained as historical implementation background and
diagnostic reference; they are not current publication instructions.

For a shorter contributor-facing guide, see [`trace-replay.md`](trace-replay.md). This document
is the long-form companion that documents every layer.

---

## Table of Contents

1. [What the Trace Framework Is For](#1-what-the-trace-framework-is-for)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Part I — Recording: BizHawk Lua](#3-part-i--recording-bizhawk-lua)
   - 3.1 [Script inventory](#31-script-inventory)
   - 3.2 [`s1_trace_recorder.lua`](#32-s1_trace_recorderlua)
   - 3.3 [`s2_trace_recorder.lua`](#33-s2_trace_recorderlua)
   - 3.4 [`s3k_trace_recorder.lua`](#34-s3k_trace_recorderlua)
   - 3.5 [`s1_credits_trace_recorder.lua` (removed)](#35-s1_credits_trace_recorderlua-removed)
   - 3.6 [Diagnostic/debug Lua scripts](#36-diagnosticdebug-lua-scripts)
   - 3.7 [Batch launchers](#37-batch-launchers)
4. [Part II — Recording: stable-retro Python](#4-part-ii--recording-stable-retro-python)
   - 4.1 [`trace_core.py` API](#41-trace_corepy-api)
   - 4.2 [`GenesisRAM` byte-swap handling](#42-genesisram-byte-swap-handling)
   - 4.3 [`s1_trace_recorder.py`](#43-s1_trace_recorderpy)
   - 4.4 [`s1_credits_trace_recorder.py`](#44-s1_credits_trace_recorderpy)
   - 4.5 [Platform setup (WSL / macOS / Linux)](#45-platform-setup-wsl--macos--linux)
5. [Part III — Trace File Formats](#5-part-iii--trace-file-formats)
   - 5.1 [`metadata.json`](#51-metadatajson)
   - 5.2 [`physics.csv` — v5](#52-physicscsv--v5)
   - 5.3 [`aux_state.jsonl` — event catalogue](#53-aux_statejsonl--event-catalogue)
   - 5.4 [`.bk2` BizHawk movie](#54-bk2-bizhawk-movie)
6. [Part IV — Java Replay Harness](#6-part-iv--java-replay-harness)
   - 6.1 [`AbstractTraceReplayTest`](#61-abstracttracereplaytest)
   - 6.2 [`AbstractCreditsDemoTraceReplayTest`](#62-abstractcreditsdemotracereplaytest)
   - 6.3 [Concrete test classes](#63-concrete-test-classes)
   - 6.4 [Trace data models](#64-trace-data-models)
   - 6.5 [Comparison — `TraceBinder` / `ToleranceConfig`](#65-comparison--tracebinder--toleranceconfig)
   - 6.6 [`DivergenceReport` and the JSON/context outputs](#66-divergencereport-and-the-jsoncontext-outputs)
   - 6.7 [Execution phase model (lag-frame detection)](#67-execution-phase-model-lag-frame-detection)
   - 6.8 [S3K checkpoints and historical elastic windows](#68-s3k-checkpoints-and-historical-elastic-windows)
   - 6.9 [`EngineDiagnostics` and context windows](#69-enginediagnostics-and-context-windows)
7. [Part V — Runtime Collision Tracing](#7-part-v--runtime-collision-tracing)
8. [Part VI — Test Resource Layout](#8-part-vi--test-resource-layout)
9. [Part VII — CI Pipeline](#9-part-vii--ci-pipeline)
10. [Part VIII — Agent-Driven Iterative Improvement Loop](#10-part-viii--agent-driven-iterative-improvement-loop)
    - 10.1 [Why the loop exists](#101-why-the-loop-exists)
    - 10.2 [Inputs available to the agent](#102-inputs-available-to-the-agent)
    - 10.3 [The skills that drive iteration](#103-the-skills-that-drive-iteration)
    - 10.4 [The `/loop` and subagent-driven patterns](#104-the-loop-and-subagent-driven-patterns)
    - 10.5 [Plan/research/spec artefacts](#105-planresearchspec-artefacts)
    - 10.6 [Case study — MZ1: 229 → 147 → 57 errors](#106-case-study--mz1-229--147--57-errors)
    - 10.7 [Case study — S3K AIZ intro parity gate](#107-case-study--s3k-aiz-intro-parity-gate)
    - 10.8 [Branch policy feedback into the loop](#108-branch-policy-feedback-into-the-loop)
11. [Part IX — Appendices](#11-part-ix--appendices)
    - 11.1 [RAM address reference per game](#111-ram-address-reference-per-game)
    - 11.2 [Pre-v5 schema history](#112-pre-v5-schema-history)
    - 11.3 [S1/S2/S3K zone name tables](#113-s1s2s3k-zone-name-tables)
    - 11.4 [Related source files](#114-related-source-files)

---

## 1. What the Trace Framework Is For

OpenGGF reimplements the physics, collision, object timing, and scroll behaviour of Sonic 1, 2
and 3&K at pixel-level parity. Trace replay measures that parity on a recorded route by feeding the
engine the same inputs as the original ROM and comparing their state frame by frame.
Its evidence is bounded by the fixture, compared fields and completed run.

The trace framework is the plumbing that does that:

- **Record** — run the real ROM through the native BizHawk headless harness
  with a deterministic input stream, and snapshot player/camera/object state every frame.
- **Replay** — drive the engine with the same input stream and step it frame by frame,
  honouring the same ROM-internal pacing (gameplay frames vs VBlank-only lag frames).
- **Compare** — match the engine's per-frame state against the recorded ROM state, with
  configurable tolerances, and emit a structured divergence report.
- **Iterate** — feed the divergence report back to humans or to agents (through a bounded debugging task) to diagnose and fix the first non-cascading error,
  and re-run.

Trace replay tests are described by [`trace-replay.md`](trace-replay.md) as "the highest-signal
tests in the repo for physics, object timing, spawn timing, and collision parity work."

---

## 2. High-Level Architecture

```
                ┌──────────────────────────────────────────┐
                │       ROM (Sonic 1 / 2 / 3&K)            │
                │   native BizHawk headless harness        │
                └──────────────────────────────────────────┘
                                   │
                                   │  per-frame RAM reads
                                   ▼
                ┌──────────────────────────────────────────┐
                │  Native recorder                         │
                │   tools/tracechaser/bizhawk-headless/     │
                │   src/Recording/                         │
                └──────────────────────────────────────────┘
                                   │ writes v5 payloads
                                   ▼
                ┌──────────────────────────────────────────┐
                │  Trace fixture                           │
                │   metadata.json  physics.csv  aux_state  │
                │   .bk2           (BizHawk movie)         │
                └──────────────────────────────────────────┘
                                   │ consumed by
                                   ▼
                ┌──────────────────────────────────────────┐
                │  AbstractTraceReplayTest (JUnit 5)       │
                │   - HeadlessTestFixture                  │
                │   - TraceBinder                          │
                │   - TraceExecutionModel                  │
                │   - ROM-backed timing admission          │
                └──────────────────────────────────────────┘
                                   │ emits
                                   ▼
                ┌──────────────────────────────────────────┐
                │  target/trace-reports/<profile>/         │
                │   <logical-key>-<lane>-<owner-hash>.json │
                │   ...-<owner-hash>_context.txt           │
                └──────────────────────────────────────────┘
                                   │ read by
                                   ▼
                ┌──────────────────────────────────────────┐
                │  Agent iteration loop                    │
                │   - trace-replay-bug-fixing skill         │
                │   - subagent-driven-development skill    │
                │   - trace-replay / bizhawk-headless      │
                │   - plan/research/spec docs              │
                └──────────────────────────────────────────┘
```

The recorder lives in the optional pinned `tools/tracechaser` submodule; initialize
it explicitly before capture. Java replay lives in OpenGGF. The ROM is not tracked; it is gitignored and users must supply
their own legally-obtained copy.

---

## 3. Part I — Recording: BizHawk Lua

**Historical implementation background.** The schemas, metadata and algorithms in
this section describe predecessor recorders. They are not accepted production
formats or current bootstrap authority; use the v5/native path above.

BizHawk (`tools/tracechaser/.dependencies/BizHawk-2.11-win-x64/EmuHawk.exe`, not checked in) runs the ROM with a
deterministic BK2 movie. A companion Lua script is loaded with `--lua` which, via
`event.onframeend`, snapshots RAM into `physics.csv` and emits diagnostic events into
`aux_state.jsonl`. The historical script defined its predecessor fixture format.

### 3.1 Script inventory

The historical recorder/probe inventory included:

| Category | File | Purpose |
|---|---|---|
| Main recorder | `s1_trace_recorder.lua` | S1 REV01 per-frame physics |
| Main recorder | `s2_trace_recorder.lua` | S2 REV01 per-frame physics, Sonic + Tails |
| Main recorder | `s3k_trace_recorder.lua` | S3&K per-frame physics, zone_act_state + checkpoints |
| Credits recorder | `s1_credits_trace_recorder.lua` (removed; see `tools/tracechaser/retro/s1_credits_trace_recorder.py`) | Recorded the 8 ROM-owned credits demos |
| Launcher | `record_trace.bat` | Wraps BizHawk for S1 recording |
| Launcher | `record_s2_trace.bat` | Wraps BizHawk for S2 recording with PowerShell stdout bridge |
| Launcher | `record_s3k_trace.bat` | Wraps BizHawk for S3K recording, exposes `aiz_end_to_end` profile |
| Removed launcher | `record_s1_credits_traces.bat` | Former credits-demo wrapper |
| Debug | `debug_s2_endact_inputs.lua` | Dump S2 end-of-act input handling |
| Debug | `debug_s2_tails_despawn.lua` | Log Tails despawn/render flags near frame 5490–5520 |
| Debug | `s3k_domain_probe.lua` | Probe multiple BizHawk memory domains to verify the right one |
| Debug | `s3k_handoff_diag.lua` | Dump AIZ→HCZ handoff state and raw hex windows |
| Debug | `s3k_player_base_compare.lua` | Compare candidate player object bases (0xB000 vs 0xB400) |
| Debug | `s3k_player_search.lua` | Heuristic scan over all RAM to locate the player struct |
| Debug | `s3k_slot_scan.lua` | Dump all OST slots at specific target frames |
| Debug | `hook_solid_classify.lua` | M68K execute hook at 0x101D0 to capture distance regs |
| Debug | `hook_speedtopos.lua` | M68K execute hook at `ObjectMoveAndFall`/`SpeedToPos` |
| Debug | `trace_y_instructions.lua` | Frame-boundary Y snapshot and delta |
| Debug | `trace_y_poll.lua` | `event.onmemorywrite` on each byte of Sonic Y |
| Debug | `watch_y_write.lua` | Memory write hook with M68K PC capture |

### 3.2 `s1_trace_recorder.lua`

**File:** `tools/tracechaser/bizhawk/s1_trace_recorder.lua` (~680 lines)

#### Frame detection

Recording starts when the ROM transitions into `game_mode == 0x0C` (GAMEMODE_LEVEL) **and**
`ctrl_lock_timer == 0` (player input unlocked). On the frame recording is armed, the script
returns without writing; the next `on_frame_end()` writes trace frame `0x0000`. Recording
stops when `game_mode` transitions away from `0x0C`.

#### Historical CSV v4 schema (22 columns)

```
frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,
x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,
gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
```

- All fields hexadecimal except `air`, `rolling`, `ground_mode` (decimal 0/1/0–3).
- `x_speed`, `y_speed`, `g_speed` are signed 16-bit stored as unsigned hex (`uhex`).
- `status_byte` bit layout: `0x01`=facing left, `0x02`=in air, `0x04`=rolling,
  `0x08`=on object, `0x10`=roll-jumping, `0x20`=pushing, `0x40`=underwater.
- `ground_mode`: 0=floor, 1=right-wall, 2=ceiling, 3=left-wall (quadrant of `angle`).
- `lag_counter` is always `0` for S1 (ROM has no exposed lag counter).

#### aux_state.jsonl event types

| Event | Fields (key ones) | Emitted |
|---|---|---|
| `object_appeared` | `slot`, `object_type`, `x`, `y` | When an SST slot becomes non-empty |
| `object_removed` | `slot`, `object_type` | When an SST slot is cleared |
| `object_near` | `slot`, `type`, `x`, `y`, `routine`, `status` | Every frame, per-object within 160px of Sonic |
| `slot_dump` | `slots`: array of `[slot, type_hex]` | When **any** object appeared that frame; full occupancy of slots 32–127 |
| `state_snapshot` | `control_locked`, `anim_id`, `status_byte`, `routine`, `y_radius`, `x_radius`, `on_object`, `pushing`, `underwater`, `roll_jumping` | Every 60 frames, or on air / rolling / on_object / control_locked transitions |
| `mode_change` | `field` (air/rolling/on_object/control_locked), `from`, `to` | Whenever one of those bits flips |
| `routine_change` | `from`, `to`, `sonic_x`, `sonic_y`, `x_vel`, `y_vel`, `inertia`, `status`, `stand_on_obj`, and optional `stand_obj_*` | Whenever `obRoutine` changes. S1 routines: 0=init, 2=control, 4=hurt, 6=death, 8=reset |
| `cursor_state` | `opl_screen`, `fwd_ptr`, `bwd_ptr`, `fwd_ctr`, `bwd_ctr`, `dir` | When ObjPosLoad cursor advances |

All events carry `frame` (the trace frame number) and `vfc` (`gameplay_frame_counter`).

#### Key RAM addresses (all relative to `$FF0000`)

| Name | Addr | Size | Notes |
|---|---|---|---|
| `ADDR_GAME_MODE` | `0xF600` | byte | `0x0C` = GAMEMODE_LEVEL |
| `ADDR_ZONE` / `ADDR_ACT` | `0xFE10` / `0xFE11` | byte | |
| `ADDR_RING_COUNT` | `0xFE20` | word BE | |
| `ADDR_FRAMECOUNT` | **`0xFE04`** | word BE | `v_framecount`; advances only on Level_MainLoop completion. **Not** `0xFE02` (common trap — see MZ1 case study) |
| `ADDR_VBLA_WORD` | `0xFE0E` | word BE | Advances every VBlank |
| `ADDR_CAMERA_X` / `ADDR_CAMERA_Y` | `0xF700` / `0xF704` | long, read hi word | |
| `PLAYER_BASE` | `0xD000` | | Sonic SST slot 0 |
| `OFF_X_POS` / `OFF_Y_POS` / `OFF_X_SUB` / `OFF_Y_SUB` | `0x08` / `0x0C` / `0x0A` / `0x0E` | word BE | 16:8 fixed-point (pixel + subpixel) |
| `OFF_X_VEL` / `OFF_Y_VEL` / `OFF_INERTIA` | `0x10` / `0x12` / `0x14` | signed word BE | |
| `OFF_ROUTINE` / `OFF_STATUS` / `OFF_ANGLE` | `0x24` / `0x22` / `0x26` | byte | |
| `OFF_STAND_ON_OBJ` | `0x3D` | byte | SST slot index (not RAM ptr) |
| `OFF_CTRL_LOCK` | `0x3E` | word BE | Control lock timer |
| `ADDR_OPL_SCREEN` | `0xF76E` | word BE | ObjPosLoad last processed chunk |
| `ADDR_OPL_DATA_FWD/BWD` | `0xF770` / `0xF774` | long BE | OPL cursor pointers |

#### Historical metadata.json (rejected by current readers)

```json
{
  "game": "s1",
  "zone": "ghz", "zone_id": 0, "act": 1,
  "bk2_frame_offset": 840,
  "trace_frame_count": 3905,
  "start_x": "0x0050", "start_y": "0x03B0",
  "recording_date": "2026-04-19",
  "lua_script_version": "3.0",
  "trace_schema": 3,
  "csv_version": 4,
  "rom_checksum": "",
  "notes": ""
}
```

### 3.3 `s2_trace_recorder.lua`

**File:** `tools/tracechaser/bizhawk/s2_trace_recorder.lua` (~947 lines) — the most complex recorder.

#### What changes vs S1

- **Two characters:** Sonic at `$FFB000` and Tails at `$FFB040` (slot 1 of the SST). Every
  physics field appears twice, once under a `sonic_` prefix and once under `tails_`.
- **`OBJ_DYNAMIC_START = 16`** (S2) vs S1's 32.
- **Control lock offset:** S2's `OFF_CTRL_LOCK = 0x2E` (word), *different from S1's `0x3E`*.
- **Pre-trace snapshots** (frame `-1`): the recorder emits a block of `object_state_snapshot`
  events with the raw 64-byte dump of every non-empty SST slot (plus semantic word aliases)
  before trace frame 0. The Java harness uses these to hydrate the engine's object manager so
  that routine/timer state matches the ROM at trace start.
- **Tails CPU snapshot** (frame `-1`): `cpu_state_snapshot` carries `control_counter`,
  `respawn_counter`, `cpu_routine`, `target_x/y`, `interact_id`, `jumping`.
- **Player history snapshot** (frame `-1`): 64-entry ring buffer of Sonic position/input/
  status captured from RAM at `$FFE400/E500` for pre-trace position history hydration.

#### Historical CSV v6 schema (38 columns)

```
frame,input,camera_x,camera_y,rings,gameplay_frame_counter,vblank_counter,lag_counter,
sonic_present,sonic_x,sonic_y,sonic_x_speed,sonic_y_speed,sonic_g_speed,
sonic_angle,sonic_air,sonic_rolling,sonic_ground_mode,
sonic_x_sub,sonic_y_sub,sonic_routine,sonic_status_byte,sonic_stand_on_obj,
tails_present,tails_x,tails_y,tails_x_speed,tails_y_speed,tails_g_speed,
tails_angle,tails_air,tails_rolling,tails_ground_mode,
tails_x_sub,tails_y_sub,tails_routine,tails_status_byte,tails_stand_on_obj
```

#### aux_state.jsonl additions for S2

- `state_snapshot` / `routine_change` / `mode_change` / `object_near` carry an additional
  `character` field (`"sonic"` or `"tails"`).
- `state_snapshot` also carries `raw_input` / `raw_input_mask` / `logical_input` /
  `logical_input_mask` so that input-handling divergences (especially around Tails CPU) are
  debuggable.
- Pre-trace (frame = `-1`) events: `object_state_snapshot`, `player_history_snapshot`,
  `cpu_state_snapshot`.

### 3.4 `s3k_trace_recorder.lua`

**File:** `tools/tracechaser/bizhawk/s3k_trace_recorder.lua` (~745 lines).

#### Critical S3K differences

- **32-bit position format:** Unlike S1/S2's separate `$08/$0A` (x pixel / x subpixel), S3K
  packs position as `$10.L` where high word = pixel and low word = subpixel. Same applies to
  Y at `$14.L`. The recorder reads them as separate words.
- **Velocity offsets shift:** `OFF_X_VEL = 0x18`, `OFF_Y_VEL = 0x1A`, `OFF_INERTIA = 0x1C`.
- **`OFF_ROUTINE = 0x05`** (not `0x24`).
- **`OFF_STAND_ON_OBJ = 0x42` as a word** that holds a RAM pointer to the stood-on object,
  not an SST slot index. The recorder converts it to a slot via the helper
  `interact_addr_to_slot()` (subtracts OST base `0xB400`, divides by slot size `0x4A`).
- **OST geometry differs:** `OBJ_TABLE_START = 0xB000`, `OBJ_SLOT_SIZE = 0x4A` (74 bytes,
  not 64), `OBJ_TOTAL_SLOTS = 110`, `OBJ_DYNAMIC_START = 3`, `OBJ_DYNAMIC_COUNT = 90`.
- **Per-game counters:** `ADDR_FRAMECOUNT = 0xFE08`, `ADDR_VBLA_WORD = 0xFE12`,
  `ADDR_LAG_FRAME_COUNT = 0xF628`. S3K is the only game that records a real `lag_counter`.
- **`ADDR_APPARENT_ACT = 0xEE4F`:** differs from `ADDR_ACT = 0xFE11` during AIZ2 reload
  (act=1 but apparent=0 while the Act 1→2 seamless handoff is in progress).
- **`ADDR_PLAYER_MODE = 0xFF08`:** Sonic/Tails/Knuckles character selection.
- **`ADDR_EVENTS_FG_5 = 0xEEC6`:** used by the AIZ1 fire-transition checkpoint detector.
- **`ADDR_CTRL1_LOCKED = 0xF7CA`:** different "control locked" concept from S1/S2's
  `OFF_CTRL_LOCK`. Both are checked.
- **Object `type`** is written as `0x%08X` — it's a 32-bit code-pointer (the ROM address of
  the object's main routine), not an 8-bit ID.

#### Trace profile switch — `OGGF_S3K_TRACE_PROFILE`

```
TRACE_PROFILE = os.getenv("OGGF_S3K_TRACE_PROFILE") or "gameplay_unlock"
```

**`gameplay_unlock` (default):** Start when `game_mode == 0x0C` **and** `ctrl_lock == 0`
**and** `ctrl_locked == 0`. Returns once-and-skips like S1/S2.

**`aiz_end_to_end`:** Start when the BK2 movie is loaded and either `game_mode == 0x0C` or
player position is non-zero. Records through the entire intro and into gameplay. Used for
the authoritative AIZ1→AIZ2→HCZ fixture at
`src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/`.

In `aiz_end_to_end` mode the recorder emits:

- `zone_act_state` edge-triggered on any change of
  `(actual_zone_id, actual_act, apparent_act, game_mode)`.
- Named `checkpoint` events when the game crosses well-defined milestones:
  - `intro_begin` — trace frame 0
  - `gameplay_start` — `level_started != 0 && game_mode == 0x0C && move_lock == 0 && ctrl_locked == 0`
  - `aiz1_fire_transition_begin` — zone=0, act=0, `events_fg_5 != 0`
  - `aiz2_reload_resume` — zone=0, act=1, `apparent_act == 0`
  - `aiz2_main_gameplay` — zone=0, act=1, unlocked
  - `hcz_handoff_complete` — zone=1, act=0, unlocked

Current capture and checkpoint validation use the native workflow in
[Trace v5 capture and publication](trace-v5-publication.md).

### 3.5 `s1_credits_trace_recorder.lua` (removed)

This Lua recorder and its `record_s1_credits_traces.bat` launcher no longer exist in the
tree; the surviving credits recorder is the stable-retro
`tools/tracechaser/retro/s1_credits_trace_recorder.py` (section 4.4), which writes the same layout
under `trace_output/credits_demos/`. The description below records the predecessor workflow. Current credits fixtures
use v5 and the native harness; neither predecessor producer is a publication path.

Recorded the eight ROM-owned credits demos. The inputs are not in a BK2 — they are embedded
in the ROM at `DemoEndData` (see `Sonic1CreditsDemoData`) and replayed by the stock
`MoveSonicInDemo` routine. The recorder forces `GM_Credits` from the title screen, waits for
each demo to become active (`demo_flag == 0x8001` and `credits_num` matches), and writes a
separate trace directory per demo under `trace_output/credits_demos/` (gitignored).

Each predecessor demo trace carried the S1 v4 schema plus these metadata fields:

```json
"trace_type": "credits_demo",
"input_source": "rom_ending_demo",
"credits_demo_index": 0,
"credits_demo_slug": "ghz1_credits_demo_1",
"emu_frame_start": 317
```

A `manifest.json` in `credits_demos/` enumerates all eight sub-directories.

### 3.6 Diagnostic/debug Lua scripts

These are **not** part of the replay pipeline — they are point probes used by humans or
agents while diagnosing specific divergences. They drop text files into `trace_output/`
rather than structured CSVs.

| Script | Purpose |
|---|---|
| `debug_s2_endact_inputs.lua` | Frame-range dump of S2 end-of-act input handling (frames 5036–5108) |
| `debug_s2_tails_despawn.lua` | Tails render/despawn flags over frames 5490–5520 |
| `s3k_domain_probe.lua` | Probe mainmemory / M68K BUS / System Bus domains to find the correct S3K RAM window |
| `s3k_handoff_diag.lua` | Last 360 frames of AIZ→HCZ with raw hex windows at `0xFE00-0xFE30` and `0xEE50-0xEEA0` |
| `s3k_player_base_compare.lua` | Compare candidate bases `0xB000` vs `0xB400` at target frames |
| `s3k_player_search.lua` | Heuristic scan of every 16-bit address looking for a player-shaped struct |
| `s3k_slot_scan.lua` | Dump all occupied OST slots at given target frames |
| `hook_solid_classify.lua` | `event.onmemoryexecute` at `0x101D0` (SolidObject classification) — captures `d1`/`d5` (`|distY|`, `|distX|`) to explain side-vs-top contact decisions |
| `hook_speedtopos.lua` | Execute hooks at `0x0DC66` / `0x0DC6A` / `0x0DC92` — traces Y position updates inside `ObjectMoveAndFall` / `SpeedToPos` |
| `trace_y_instructions.lua` | Detect Y change across one frame |
| `trace_y_poll.lua` | `event.onmemorywrite` on each byte of Sonic Y |
| `watch_y_write.lua` | Memory write hook on Y with M68K PC capture |

Agents use these when a divergence is traced to a specific ROM routine and the question is
"what were the registers when the ROM made that decision?" — something the `physics.csv`
stream alone cannot answer.

### 3.7 Batch launchers

These predecessor launchers drove BizHawk with `--chromeless --lua <script>
--movie <bk2> <rom>`. Do not copy historical workstation paths or in-repository
output defaults. Retained diagnostic launchers require explicit external output
and consumer-root settings; read the pinned script before using one.

**`record_trace.bat`:** Direct shell invocation — simplest form.

**`record_s2_trace.bat`:** Same, but wrapped in a PowerShell bridge
(`System.Diagnostics.ProcessStartInfo` with `RedirectStandardOutput`/`StandardError`) so that
Lua `print` output streams back to the caller, since BizHawk chromeless mode does not print
to stdout by default.

**`record_s3k_trace.bat`:** Extends the PowerShell bridge with a preamble that opens the
BK2 as a ZIP, counts the lines in `Input Log.txt`, and sets
`OGGF_BK2_FRAME_COUNT` and `OGGF_S3K_TRACE_PROFILE` before launching. This lets the Lua
script know how long the movie is and which profile to activate (`gameplay_unlock` vs
`aiz_end_to_end`).


---

## 4. Part II — Recording: stable-retro Python

**Historical diagnostic background.** This is the predecessor Python workflow;
current fixture publication uses the native BizHawk harness.

stable-retro is a Python library that embeds the same Genesis Plus GX core as BizHawk but is
fully headless and runs on macOS / Linux / WSL. The S1 Python recorder was used
for earlier diagnostics. Do not assume byte-identical output across these hosts
and the canonical native recorder; publication requires measured candidate comparison.

`tools/tracechaser/retro/` contents:

| File | Lines | Role |
|---|---|---|
| `trace_core.py` | ~732 | Shared infrastructure |
| `s1_trace_recorder.py` | ~229 | Main S1 recorder (BK2 replay or savestate boot) |
| `s1_credits_trace_recorder.py` | ~584 | ROM-driven credits demo recorder |
| `debug_credits.py` | ~43 | One-off inspector |
| `requirements.txt` | 3 | `stable-retro>=0.9.9`, `numpy>=1.21` |

### 4.1 `trace_core.py` API

**Classes**

- `GenesisRAM(ram_array=None)` — wrapper over the 64 KB work RAM numpy array. Provides
  `u8(addr)`, `s8(addr)`, `u16(addr)`, `s16(addr)`, `u32(addr)` that transparently handle
  byte-swapping (see 4.2).
- `BizhawkBK2(path)` — parses a BizHawk `.bk2` (ZIP containing `Input Log.txt`) and exposes
  per-frame input as a numpy action array compatible with stable-retro's 12-button Genesis
  button order. Handles BizHawk's LogKey format (`#P1 Up|P1 Down|...` with `#` marking
  group boundaries) and maps buttons:
  ```python
  _BK2_TO_RETRO = {
      'up': 4, 'down': 5, 'left': 6, 'right': 7,
      'a': 1, 'b': 0, 'c': 8, 'start': 3,
  }
  ```
- `TraceRecorder(output_dir)` — owns the CSV/JSONL writers and per-frame state machine.
  Mirrors the BizHawk script's recording logic 1:1:
  - `record_frame(mem)` — read RAM, write one physics row, emit any aux events
  - `_check_mode_changes()` — emit `mode_change` events
  - `_emit_routine_change()` — emit `routine_change` events
  - `_write_state_snapshot()` — periodic or transition-driven `state_snapshot`
  - `_scan_objects()` — produce `object_appeared`, `object_removed`, `object_near`,
    `slot_dump`
  - `_check_opl()` — ObjPosLoad cursor tracking

**Functions**

- `rom_joypad_to_mask(raw)` — convert ROM joypad byte to engine input bitmask
- `angle_to_ground_mode(angle)` — same quadrant mapping as the Lua script
- `write_metadata(path, **fields)` — writes `metadata.json` preserving field order
- `write_credits_manifest(path, entries)` — writes the credits `manifest.json`

**Constants** (exported for both recorders)

All the `ADDR_*`, `OFF_*`, `STATUS_*`, `OBJ_*`, `INPUT_*`, `GAMEMODE_*` constants used by the
S1 Lua recorder, plus the LZ lamppost state field names and the `ZONE_NAMES` dict.

### 4.2 `GenesisRAM` byte-swap handling

stable-retro's `genesis_plus_gx` core exposes 68K work RAM **with bytes swapped within each
16-bit word** (x86 little-endian layout of a big-endian system). So a word `$0050` at 68K
address `$FFD008` appears in the numpy array as:

```
ram[0xD008] = 0x50   # low byte
ram[0xD009] = 0x00   # high byte
```

The fix is to XOR the address with 1 for byte reads:

```python
def u8(self, addr):
    return int(self._ram[addr ^ 1])
```

For word reads the bytes are read raw because the swap is symmetric once you read both:

```python
def u16(self, addr):
    return int(self._ram[addr]) | (int(self._ram[addr + 1]) << 8)

def u32(self, addr):
    hi = int(self._ram[addr])     | (int(self._ram[addr + 1]) << 8)
    lo = int(self._ram[addr + 2]) | (int(self._ram[addr + 3]) << 8)
    return (hi << 16) | lo
```

Anyone reading stable-retro RAM directly (bypassing `GenesisRAM`) must remember the XOR 1
rule for byte accesses.

### 4.3 `s1_trace_recorder.py`

Three input-source modes, controlled by CLI flags:

```bash
# Native stable-retro BK2
python tools/tracechaser/retro/s1_trace_recorder.py --movie path.bk2 --output-dir tools/tracechaser/retro/trace_output/

# Auto-parse a BizHawk BK2
python tools/tracechaser/retro/s1_trace_recorder.py --bizhawk-bk2 path.bk2 --output-dir ...

# Boot from a savestate (GreenHillZone.Act1 etc.) — used for interactive or unit-test recordings
python tools/tracechaser/retro/s1_trace_recorder.py --state GreenHillZone.Act1 --output-dir ...
```

The main loop looks like:

```python
if movie:
    if not movie.step(): break
    action = np.array([movie.get_key(i, 0) for i in range(NUM_BUTTONS)], dtype=np.int8)
elif bk2_input:
    action = bk2_input[emu_frame] if not started else bk2_input[bizhawk_gameplay_start + recorder.trace_frame]
else:
    action = np.zeros(NUM_BUTTONS, dtype=np.int8)

env.step(action)
mem.update(env.get_ram())
recorder.record_frame(mem)
```

`render_mode=None` is set unconditionally on every `stable_retro.make()` call so the
recorder never opens a window.

### 4.4 `s1_credits_trace_recorder.py`

The credits recorder has three `--force-mode` strategies for kicking the ROM into
`GAMEMODE_CREDITS` without a movie:

1. **`redirect_level` (default):** Wait for title, inject START, wait for `GAMEMODE_LEVEL`,
   then RAM-patch `demo_flag=0`, `credits_num=0`, `game_mode=0x1C`. Safest because the ROM
   has finished its level-load pipeline by the time credits takes over.
2. **`direct`:** From the title screen, directly set `demo_flag=0x8001`, `credits_num=idx+1`,
   `zone_word=DEMO_ZONE_ACT_WORDS[idx]`, `lives=3`, `rings=0`, etc. plus LZ3-specific lamp
   state when `idx == 3`. Skips the level-load detour.
3. **`none`:** Sit on the title screen and wait for the game to naturally enter credits.

The RAM patches are routed through stable-retro's `env.data.set_value(name, value)`, and the
necessary variables are installed by `install_trace_data()` writing a `trace_data.json`
alongside the Sonic 1 game integration directory:

```python
_TRACE_VARS = {
    "game_mode":     (0xF600, "|u1"),
    "demo_flag":     (0xFFF2, ">u2"),
    "demo_num":      (0xFFF4, ">u2"),
    "credits_num":   (0xFFF6, ">u2"),
    "zone_word":     (0xFE10, ">u2"),
    "lives":         (0xFE12, "|u1"),
    "rings":         (0xFE20, ">u2"),
    "time_long":     (0xFE22, ">u4"),
    "score_long":    (0xFE26, ">u4"),
    "water_routine": (0xF64D, "|u1"),
    "water_state":   (0xF64E, "|u1"),
    # ... plus 15 LZ-lamp vars ...
}
```

The LZ3 (`idx == 3`) patch restores a lamp room state that the credits sequence normally
inherits from a prior level:

```python
last_lamp=1, lamp_x=0x0A00, lamp_y=0x062C, lamp_rings=13,
lamp_limit_btm=0x0800, lamp_scr_x=0x0957, lamp_scr_y=0x05CC,
lamp_bg1_x=0x04AB, lamp_bg1_y=0x03A6, lamp_water_pos=0x0308,
lamp_water_rout=1, lamp_water_stat=1, ...
```

### 4.5 Platform setup (WSL / macOS / Linux)

stable-retro requires a compiled C extension. Wheels exist for macOS and Linux but **not
Windows** — Windows users use WSL. The `s1-retro-trace` skill documents the setup
thoroughly:

```bash
# Windows: create a persistent venv under Ubuntu-24.04 WSL
wsl -d Ubuntu-24.04 -- bash -c 'python3 -m venv ~/retro-env'
wsl -d Ubuntu-24.04 -- bash -c 'source ~/retro-env/bin/activate && pip install stable-retro numpy'

# Copy ROM to WSL-local path (avoid slow /mnt/ I/O)
wsl -d Ubuntu-24.04 -- bash -c 'mkdir -p /tmp/roms && cp "/mnt/c/.../s1.gen" /tmp/roms/'

# Import ROM
wsl -d Ubuntu-24.04 -- bash -c 'cd /home && source ~/retro-env/bin/activate && PYTHONPATH="" python3 -m stable_retro.import /tmp/roms'
```

A historical workstation had a local `stable-retro-0.9.9/stable_retro/` checkout
that shadowed the installed package. That directory is not a repository prerequisite.
When reproducing these diagnostics, use an isolated environment and verify which
package Python imports; the old `cd /home` workaround is host-specific.

ROM SHA-1 must be `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b` (Sonic 1 REV01 World).

---

## 5. Part III — Trace File Formats

Every production recorder produces the same v5 envelope and payload set (plus
the BK2 movie for BK2-owned recordings).

### 5.1 `metadata.json`

Common metadata fields (profile-specific validation lives in `TraceMetadata` and
the native candidate validator):

| Field | Type | Meaning |
|---|---|---|
| `game` | string | `"s1"`, `"s2"`, or `"s3k"` |
| `zone` | string | short zone name (`ghz`, `mz`, `ehz`, `aiz`, …) |
| `zone_id` | int | raw ROM zone byte |
| `act` | int | 1-indexed act number |
| `bk2_frame_offset` | int | emu frame at which recording began (= BK2 frame where the movie crosses into gameplay) |
| `trace_frame_count` | int | number of rows in physics.csv |
| `start_x`, `start_y` | `"0x….."` | Sonic's starting coordinates |
| `recording_date` | string | `YYYY-MM-DD` |
| `recorder` | string | opaque producer identity; canonical fixtures use `native-bizhawk-headless` |
| `recorder_version` | string | opaque implementation version; never a parser selector |
| `trace_schema` | int | the sole data contract selector; exactly `5` |
| `rom_checksum` | string | optional |
| `notes` | string | optional |

S2-specific additions: `characters`, `main_character: "sonic"`, and `sidekicks`. Normal Sonic
and Tails routes record `characters: ["sonic","tails"]` and `sidekicks: ["tails"]`; routes where
the ROM suppresses Tails, such as SCZ and WFZ, record `characters: ["sonic"]` and `sidekicks: []`.

S3K-specific additions (only in `aiz_end_to_end` profile):
`bizhawk_version: "2.11"`, `genesis_core: "Genplus-gx"`, and a descriptive `notes`.

Credits-demo additions: `trace_type: "credits_demo"`, `input_source: "rom_ending_demo"`,
`credits_demo_index`, `credits_demo_slug`, `emu_frame_start`.

Optional `pre_trace_oscillation_frames` field, consumed by
`AbstractTraceReplayTest` to pre-advance the engine's oscillation manager (see 6.1).

### 5.2 `physics.csv` — v5

Ordinary level traces have one fixed 42-column symmetric
primary-character/sidekick row, or 43 columns when the recording carries the
trailing `life_count` column (see below). There is no other width autodetection
and no `csv_version`. Dedicated special-stage readers are selected by `game` plus
`trace_profile`: S1 uses 14 columns, S2 uses 48, and S3K uses 20. All other
widths are rejected.

**Life counter (`life_count`, trailing 43rd column).** ROM `Life_count`
(`$FFFFFE12` in Sonic 1, 2, and 3&K alike). Recorded per-frame so a death or a
1UP is attributable to the exact frame it happened on. Recordings made before
the column existed omit it and `TraceFrame.lives()` reports
`TraceFrame.LIVES_ABSENT`; that is the only path on which no life comparison
runs, and it is keyed on the recording, never on the engine. When the column is
present `TraceBinder` emits three fields: `lives_present` (ERROR if the engine
snapshot carries no life count, so the comparison cannot silently vanish),
`lives` (the level — a missed death stays divergent from the transition on), and
`lives_delta` (the per-frame change — the field that names the exact frame a
life was gained or lost). The recorded value is comparison-only and is never
hydrated into engine state.

**Compared when both sides record them:** `x_sub`, `y_sub`, `routine`, `status_byte`,
`rings`, `camera_x`, and `camera_y`. These fields are strict frontier fields when the
trace row has extended data and `EngineDiagnostics` carries the matching engine snapshot.

Execution counters and aux-only context remain diagnostic unless a named
comparator contract says otherwise; queue/dynamic-art evidence is strict and
comparison-only.

**Always compared:** `x`, `y`, `x_speed`, `y_speed`, `g_speed`, `angle`, `air`, `rolling`,
`ground_mode` (derived from angle), plus the sidekick's copy of those fields in S2/S3K
sidekick-capable schemas.

### 5.3 `aux_state.jsonl` — event catalogue

One JSON object per line, compact (no whitespace). Every event carries `frame` and `event`
keys; most also carry `vfc` (`gameplay_frame_counter`).

Representative diagnostic event types (not an exhaustive capability inventory):

| `event` | Games | Frame | Purpose |
|---|---|---|---|
| `object_appeared` | all | ≥0 | SST slot went non-empty |
| `object_removed` | all | ≥0 | SST slot cleared |
| `object_near` | all | ≥0 | Active object within 160 px of player |
| `slot_dump` | all | ≥0 | Full dynamic-slot occupancy, emitted any frame an object appeared |
| `state_snapshot` | all | ≥0 | Full player snapshot every 60 frames or on transitions |
| `mode_change` | all | ≥0 | `air`/`rolling`/`on_object`/`control_locked` transition |
| `routine_change` | all | ≥0 | Player `obRoutine` change with full position/velocity context |
| `cursor_state` | S1, S2 | ≥0 | ObjPosLoad cursor movement (S3K has no OPL) |
| `object_state_snapshot` | S2 | -1 | Raw 64-byte dump of every non-empty SST slot at trace start |
| `player_history_snapshot` | S2 | -1 | 64-entry ring buffer of Sonic history |
| `cpu_state_snapshot` | S2 | -1 | Tails AI counters |
| `zone_act_state` | S3K | ≥0 | Edge-triggered on `(zone,act,apparent_act,game_mode)` change |
| `checkpoint` | S3K (aiz_end_to_end) | ≥0 | `intro_begin`, `gameplay_start`, `aiz1_fire_transition_begin`, `aiz2_reload_resume`, `aiz2_main_gameplay`, `hcz_handoff_complete` |
| `player_mode_set` | S3K | ≥0 | Sonic/Tails/Knuckles switch |

S1 routines (for `routine_change`): `0`=init, `2`=control, `4`=hurt, `6`=death, `8`=reset.
**Not** the same as S2/S3K.

### 5.4 `.bk2` BizHawk movie

A zipped bundle of text files containing:
- `Input Log.txt` — one line per frame, `|` delimited, with characters per button
- `Header.txt` — emulator, core, ROM hash, log key
- `Comments.txt`, `Subtitles.txt`, etc. (unused here)

`Bk2MovieLoader` parses `Input Log.txt` and its LogKey bindings for P1 and optional
explicitly labelled P2 controls, including Start. Unlabelled legacy logs do not
invent a P2 lane.

---

## 6. Part IV — Java Replay Harness

JUnit replay harnesses live under `src/test/java/com/openggf/tests/trace/`. Shared
models, parsers, comparators and bootstrap logic live in `src/main/java/com/openggf/trace/`;
runtime collision hooks live in `src/main/java/com/openggf/physics/`.

### 6.1 `AbstractTraceReplayTest`

**File:** `src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java`

The base JUnit-5 test class. Subclasses override `game()`, `zone()`, `act()`,
`traceDirectory()`, and optionally `tolerances()` / `reportOutputDir()` /
`overridePreTraceOscFrames()`.

`replayMatchesTrace()` performs these operations:

1. Resolve the canonical or candidate fixture root and gate on required fixture
   payloads/ROMs. Missing inputs may skip the test; inspect skips before claiming parity.
2. Parse strict v5 data, validate replay eligibility and resolve the movie from
   metadata's shared `_movies/<source_bk2>` placement or a retained local `.bk2`.
3. Prepare the recorded team and ROM-backed level through `TraceReplaySessionBootstrap`,
   `TraceReplayBootstrap` and `HeadlessTestFixture`. Some entry profiles require a
   fresh level load rather than a reused `SharedLevel`.
4. Step the production frame driver with movie input and the supported phase model.
   Dedicated hardware timing can release only matching prepared production work,
   under the [timing input contract](../../architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md).
5. Capture engine snapshots and compare the selected verification groups with
   `TraceBinder`. Physics/aux rows are comparison evidence, never gameplay write-back.
6. Publish an owner-keyed report and validate group accounting, warnings/errors,
   rewind closure and timing closure as applicable. The finalization path regenerates
   the report even when replay fails early so stale evidence cannot mask that failure.

Reports live under
`target/trace-reports/<profile>/<logical-key>-<lane>-<owner-hash>.json`, with
ownership sidecars and divergence context. Consult the report's completed frame
count and selected groups, not just Maven's exit status.

### 6.2 `AbstractCreditsDemoTraceReplayTest`

Provides a separate JUnit harness for S1 credits demos:

- **No `.bk2`.** Input is read from the ROM's `DemoEndData` table via
  `Sonic1CreditsDemoData.DEMO_DATA_ADDR[idx]` / `DEMO_DATA_SIZE[idx]`, wrapped in a
  `DemoInputPlayer` that implements the ROM's demo-input protocol.
- **Demo-specific initial state.** When `idx == 3` (LZ3), `Sonic1CreditsDemoBootstrap.applyLzLampostState(...)`
  restores the lamp state (ring count, camera, water level/routine).
- **Frame limit = `min(trace.frameCount, DEMO_TIMER[idx])`.**
- **REV01 demo bug emulation.** `MoveSonicInDemo` has a bug that zeroes `d2` before reading
  the held-state, causing the jump button to press every frame while the stream says "held"
  — the test forces `setForcedJumpPress(jump)` each frame to replicate.

### 6.3 Concrete test classes

**Sonic 1 full-run:**
- `TestS1Ghz1TraceReplay` — GHZ Act 1
- `TestS1Mz1TraceReplay` — MZ Act 1

**Sonic 1 credits demos (8):**
- `TestS1Credits00Ghz1TraceReplay` through `TestS1Credits07Ghz1bTraceReplay`

**Sonic 2 full-run & regressions:**
- `TestS2Ehz1TraceReplay`
- `TestS2Ehz1BuzzerSpawnRegression`
- `TestS2Ehz1BridgeTailsStandingRegression`
- `TestS2Ehz1MonitorBreakRegression`
- `DebugS2Ehz1SpiralProbe`, `DebugS2Ehz1TailsReplayWindowProbe` (diagnostic probes)

**Sonic 3K full-run & supporting:**
- `TestS3kAizTraceReplay` — AIZ1 → AIZ2 → HCZ1 end-to-end
- `TestS3kAizReplayBootstrap` — bootstrap hydration unit test
- `TestS3kReplayCheckpointDetector` / `TestS3kRequiredCheckpointGuard` — unit tests for the S3K-specific pieces
- `DebugS3kAizReplayWindowProbe` (diagnostic)

**Synthetic / unit:**
- `TestS2SyntheticV3Fixture`, `TestS3kSyntheticV3Fixture` — exercise v5 parsing
  without a real ROM; the class names retain historical `V3` wording

### 6.4 Trace data models

- **`TraceData`** — owns `List<TraceFrame>` and a `Map<Integer,List<TraceEvent>>` of aux
  events, plus `preTraceObjectSnapshots()`, `initialVblankCounter()`.
- **`TraceFrame`** — record with hex-parsed fields; `parseCsvRow(line)` accepts only
  the ordinary v5 widths (42 columns, or 43 with `life_count`). Holds `hasExtendedData()`, `stateEquals()` (used
  for physics-only equality in lag detection), `formatDiagnostics()`.
- **`TraceMetadata`** — parsed `metadata.json`.
- **`TraceEvent`** — sealed interface; subtypes include `Checkpoint`, `ZoneActState`,
  `ObjectStateSnapshot`, `PlayerHistorySnapshot`, `CpuStateSnapshot`, `ObjectAppeared`,
  `ObjectRemoved`, `ObjectNear`, `ModeChange`, `RoutineChange`, `StateSnapshot` (fallback
  for unknown event types).
- **`TraceCharacterState`** — per-character state for the v5 sidekick block.
- **`RomObjectSnapshot`** — the raw-bytes-plus-semantic-aliases structure used by
  `object_state_snapshot`.

### 6.5 Comparison — `TraceBinder` / `ToleranceConfig`

`TraceBinder.compareFrame()` compares nine physics fields per character, plus
shared trace-frame fields when both ROM and engine recorded them:

| Field | Rule | Default tolerance |
|---|---|---|
| `x`, `y` | absolute diff | warn ≥1, error ≥1 |
| `x_speed`, `y_speed`, `g_speed` | absolute diff; **sign change is ERROR** | warn ≥1, error ≥1 |
| `angle` | circular-distance diff | warn ≥1, error ≥1 |
| `air`, `rolling` | exact match; any mismatch = ERROR | — |
| `ground_mode` | exact match of `deriveGroundMode(angle) & 0xFF` | — |
| `x_sub`, `y_sub` | exact match when both sides recorded subpixel diagnostics | warn ≥1, error ≥1 |
| `routine`, `status_byte` | exact match when both sides recorded character diagnostics | — |
| `rings` | exact match when both sides recorded; mode-gated | `RingCountMode.FORCE_ERROR` |
| `camera_x`, `camera_y` | absolute diff (both sides masked `& 0xFFFF`); only fires when ROM trace and `EngineDiagnostics` both recorded camera coords | warn ≥1, error ≥1 |

`ToleranceConfig.DEFAULT` is fully strict — any one-pixel/one-unit divergence is an ERROR:

```java
ToleranceConfig(positionWarn=1, positionError=1,
                speedWarn=1, speedError=1, speedSignChangeIsError=true,
                angleWarn=1, angleError=1,
                cameraWarn=1, cameraError=1,
                RingCountMode.FORCE_ERROR)
```

Tests can override `tolerances()` for known parity gaps — e.g. some S3K trace tests opt
into `withRingCountMode(WARN_ONLY)` while ring loss/donation parity is being chased, and
`withCameraTolerances(warn, error)` exists for the same reason on the camera fields.
The default is to fail loudly on any divergence.

`Severity`: `MATCH` / `WARNING` / `ERROR`. `FieldComparison` stores the pair of formatted
strings plus the severity and delta. `FrameComparison` aggregates all nine (or
nine + sidekick nine) comparisons for one frame and knows whether it has any error or any
divergence.

**Input validation.** Every frame, `binder.validateInput(expected, bk2Input)` asserts that
the BK2 movie's input matches the trace CSV's `input` column. A mismatch means the movie
has been re-recorded but the CSV was not regenerated — the test fails loudly rather than
quietly producing meaningless divergences.

### 6.6 `DivergenceReport` and the JSON/context outputs

`DivergenceReport` groups consecutive diverging `FrameComparison`s by field into
`DivergenceGroup`s (`field`, `severity`, `startFrame`, `endFrame`, `expectedAtStart`,
`actualAtStart`, `cascading`). The first group on each field is non-cascading; subsequent
groups on the same field within the same error run are flagged `cascading=true` so humans
can ignore them.

**JSON output**
(`target/trace-reports/<profile>/<logical-key>-<lane>-<owner-hash>.json`):

```json
{
  "error_count": 3,
  "warning_count": 5,
  "total_frames": 3905,
  "summary": "3 errors, 5 warnings. First error: frame 512 -- x_speed mismatch ...",
  "latest_checkpoint": {"frame": 500, "name": "gameplay_start", ...},
  "errors": [
    {"field": "x_speed", "severity": "ERROR", "start_frame": 512, "end_frame": 520,
     "expected_at_start": "0x0200", "actual_at_start": "0x01F8", "cascading": false},
    ...
  ],
  "warnings": [...]
}
```

**Context window**
(`target/trace-reports/<profile>/<logical-key>-<lane>-<owner-hash>_context.txt`), written
only if there are errors. Table of the `radius=10` frames on either side of the first
error, showing every divergent field plus the one-line `romDiag` and `engineDiag` strings
so the developer can see ROM state vs engine state at the moment the divergence opened.

Example text shape:

```
Frame | Exp x     | Act x     | Exp y_speed | Act y_speed
510   | 0x0200    | 0x0200    | 0x0000      | 0x0000
511   | 0x0202    | 0x0200    | 0x0010      | 0x000E   * ERROR y_speed
512   | 0x0204    | 0x0202    | 0x0020      | 0x001C   * ERROR y_speed
rom: sub=(0x0000,0x0000) rtn=02 cam=(0x0300,0x0200) rings=10 status=00
eng: rtn=02 onSlot=-1 rings=10 st=00 cam=0300
```

### 6.7 Execution phase model (lag-frame detection)

`TraceExecutionModel` and `TraceReplayBootstrap.phaseForReplay(...)` classify
recorded execution boundaries. `TraceExecutionPhase` contains
`FULL_LEVEL_FRAME`, `FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD`,
`PLAYABLE_ANIMATION_ONLY`, `ADVANCE_ONLY`, and `VBLANK_ONLY`. The frame-closure
driver maps each phase to the corresponding existing engine loop/input operation.

The distinction matters when a VBlank completes without a level iteration, or
when a native loop services animation/input without the full gameplay pass. It
does not authorize copying recorded gameplay values into the engine. Current
readers require v5; there is no pre-v3 schema fallback.

### 6.8 S3K checkpoints and historical elastic windows

`S3kReplayCheckpointDetector` and `S3kRequiredCheckpointGuard` provide semantic
milestone checks for supported S3K replays. Checkpoint identity and order supplement
frame comparisons; they do not establish that an unobserved route is correct.

The April 2026
[intro parity design](../../architecture/designs/2026-04-21-s3k-aiz-intro-parity-gate-design.md)
described an elastic-window controller that disabled strict comparisons inside
selected cutscenes. That controller is no longer present. Read the current
`AbstractTraceReplayTest.replayS3kTrace(...)` and production bootstrap/phase owners
when changing replay alignment; do not reintroduce that historical bypass.

### 6.9 `EngineDiagnostics` and context windows

`EngineDiagnostics` is a record snapshot. Selected fields are compared for
pass/fail when both sides provide them; other fields enrich diagnostic context. It holds:

- `routine`, `standOnSlot`, `standOnType`
- `rings`, `statusByte`
- `cameraX`, `cursorIdx`, `leftCursorIdx`, `fwdCtr`, `bwdCtr` (ObjPosLoad placement)
- `solidEvent` — one-line summary of touch/solid resolution at this frame
- `xSub`, `ySub`

Formatted by `EngineDiagnostics.format()` / `formattedOnly()` into the `eng:` line that
appears in the context window. Supplementary formatters
(`EngineNearbyObjectFormatter`, `TouchResponseDebugHitFormatter`,
`TraceEventFormatter`) produce additional context lines such as nearby-object listings
and recent aux events around the error.

---

## 7. Part V — Runtime Collision Tracing

Independent of file-based trace replay, the engine has a *runtime* collision-tracing hook
that records per-frame collision events in memory. This is used by collision-heavy tests
that want to assert "Sonic's ground sensor returned angle X, and the left-wall sensor
contacted solid Y at (a, b)" without parsing CSV.

Files in `src/main/java/com/openggf/physics/`:

- `CollisionTrace` — interface with `onTerrainProbesStart/Result/Complete`,
  `onSolidContactsStart/Candidate/Resolved/Complete`,
  `onSolidCheckpointStart/Result`, `onPostAdjustment`, `getEvents()`, `clear()`.
- `CollisionEvent` — one event record (terrain probe or solid contact) with all relevant
  fields.
- `SensorResult` — the result of a `GroundSensor` probe: angle, distance, success flag.
- `SolidContact` — contact payload for solid-object resolution.
- `RecordingCollisionTrace` — stores events in an `ArrayList<CollisionEvent>` for later
  inspection.
- `NoOpCollisionTrace` — the default zero-cost implementation used when tracing is disabled.

`CollisionSystem` holds a `CollisionTrace trace` field and forwards every probe/contact to
it. Tests that want trace output set `trace = new RecordingCollisionTrace()` before driving
a frame, then read `trace.getEvents()` after.

This is orthogonal to the CSV trace pipeline — it's a finer-grained lens for collision-only
regressions, not a recording of full gameplay.

---

## 8. Part VI — Test Resource Layout

Canonical fixtures live under `src/test/resources/traces/<game>/`. They include
single-level routes, credits demos, complete-run segments and manifests, plus
shared deduplicated movies under `<game>/_movies/`. A representative level fixture
contains `metadata.json`, `physics.csv.gz`, `aux_state.jsonl.gz`, and optional
dedicated timing data. Movie location is resolved from metadata/run ownership;
not every segment has its own `.bk2`.

Keep payloads compressed in Git. Native candidates are recorded outside the
repository and installed only through the validated publication workflow. Synthetic
parser/phase fixtures may be built directly in tests and need no ROM. Directory
names, file sizes and test presence are not parity evidence; current dated results
belong in the [trace frontier log](../../status/trace-frontier-log.md).

---

## 9. Part VII — CI Pipeline

[Testing](testing.md#ci-in-github) describes the current event matrix owned by
`.github/workflows/ci.yml`: develop/next push smoke after a quiet period, non-draft
PR/manual smoke with separate policy/guard checks as applicable, and optional
manual ROM-fixture trace replay. Master publication uses the separate Release
workflow's explicit full suite and guards.

Hosted jobs do not have private ROMs. They exercise v5 parser, bootstrap, timing,
checkpoint and guard contracts where synthetic inputs suffice; ROM-gated skips do
not demonstrate gameplay parity. Inspect test identities and skips rather than
assuming every replay body ran. Native capture is performed separately with the
pinned TraceChaser harness, verified ROMs and BizHawk prerequisites.

---

## 10. Part VIII — Agent-Driven Iterative Improvement Loop

This is the crucial feedback loop that turns the trace framework into an *improvement
engine*. Agents run it over long sessions — the user describes this as "work long hours to
systematically diagnose and improve accuracy."

### 10.1 Why the loop exists

The engine has thousands of small physics edge-cases that only manifest at specific
frame/context combinations. A human cannot enumerate them. But a trace fixture *does* —
every frame where the engine diverges from the ROM is one such edge case. If you can:

1. Find the first non-cascading error,
2. Hypothesise a root cause,
3. Implement a fix,
4. Re-run the fixture,
5. Compare the new error count / first-error frame to the baseline,

and repeat, comparing exact frontiers and regressions rather than assuming the
error count will decrease monotonically. Agents
excel at this because steps 1, 4 and 5 are mechanical (parse JSON, count errors, diff
frames) and steps 2, 3 are LLM-friendly (read disassembly, read engine, edit Java).

### 10.2 Inputs available to the agent

For any given trace divergence, the agent has:

- **`target/trace-reports/<profile>/<logical-key>-<lane>-<owner-hash>.json`** —
  machine-readable error list.
- **`target/trace-reports/<profile>/<logical-key>-<lane>-<owner-hash>_context.txt`** —
  human-readable first-error window with side-by-side ROM vs engine diagnostics.
- **`src/test/resources/traces/<game>/<name>/physics.csv.gz`** — every ROM-state row.
- **`src/test/resources/traces/<game>/<name>/aux_state.jsonl.gz`** — every ROM event.
- **`docs/s1disasm/` / `docs/s2disasm/` / `docs/skdisasm/`** — optional pinned disassembly submodules
  for ROM research, never runtime asset sources.
- **`RomOffsetFinder` tool** (`com.openggf.tools.disasm.RomOffsetFinder`) — symbol → offset
  resolution with ROM verification.
- **Engine code under `src/main/java/com/openggf/`** — the thing being fixed.
- **The engine's own collision trace** (`RecordingCollisionTrace`) for fine-grained
  collision divergences.
- **Diagnostic Lua scripts** (`hook_solid_classify.lua`, `hook_speedtopos.lua`, etc.) when
  the divergence narrows to a single ROM instruction.
- **Prior case studies** — the frontier history in `docs/status/trace-frontier-log.md`
  and the write-ups under `docs/architecture/research/` and `docs/architecture/audits/`.

### 10.3 The skills that drive iteration

Project skills are mirrored in `.agents/skills/` and `.claude/skills/`:

- `trace-replay-bug-fixing` owns divergence diagnosis and ROM-backed fixes.
- `s1-trace-replay` covers running and adding Sonic 1 replay fixtures.
- `bizhawk-headless-trace` owns canonical native capture and publication.
- `trace-green-fleet` coordinates independent failing trace frontiers.
- The matching disassembly, object/boss, zone and rendering skills provide domain
  guidance when a fix crosses those owners.

Use repository delivery and validation policy; skill workflow defaults do not
require extra approval ceremonies or repeated unchanged reviews.

### 10.4 The `/loop` and subagent-driven patterns

Earlier sessions used client-specific `/loop` scheduling. It is not a repository
prerequisite or a portable API. Current work can run inline or use bounded delegated
tasks with explicit file/worktree ownership and stopping conditions.

One lead owns delivery-wide validation. Workers run focused checks for their edits;
reuse existing queued/running jobs for unchanged inputs instead of launching duplicate
suites. Keep one command session and observer per job, and treat queue waiting as
pending work. See [agent guidance](../../../AGENTS.md) for checkpoints, resource
admission and delivery rules.

### 10.5 Plan/research/spec artefacts

Longer, structured work lives under `docs/architecture/`:

- **`architecture/plans/`** — step-by-step execution plans (`executing-plans` skill format). Current
  trace-related historical plans:
  - `2026-04-20-s2-s3k-trace-recorder-v3.md` — historical recorder plan
  - `2026-04-21-s3k-aiz-intro-parity-gate.md` — aligning elastic-window replay with the
    approved parity gate
  - `2026-04-21-s3k-end-to-end-trace-fixture.md` — roadmap for the real AIZ→HCZ fixture
- **`architecture/research/`** — investigation dumps.
  - `2026-04-20-s2-trace-addresses.md` — S2 RAM addresses
  - `2026-04-20-s3k-trace-addresses.md` / `2026-04-21-s3k-trace-addresses.md` — S3K RAM
    addresses, S&K-side only
- **`architecture/designs/`** — concrete design contracts.
  - `2026-04-21-s3k-aiz-intro-parity-gate-design.md` — the six-checkpoint gate contract

Keep task decisions and rejected approaches in the existing dated architecture
artifact when they would otherwise need to be re-derived. Update a compact checkpoint
at handoff; do not add validation receipts or raw-log archives. Documentation trailers
describe the staged file obligations under the repository policy.

### 10.6 Case study — MZ1: 229 → 147 → 57 errors

Reconstructed from the MZ1 trace-progress notes of the time:

- **Baseline:** 229 errors on first run of `TestS1Mz1TraceReplay`.
- **Session 1 fixes (229 → 147):** subpixel position fidelity, sensor alignment, solid
  object landing width.
- **Session 2 fixes (147 → 57):** seven concrete fixes to solid-object interactions —
  boundaries, path exclusivity, sticky buffer, `MvSonicOnPtfm2` offset for MovingBlock /
  Platform / CollapsingFloor, on-screen X-only guard for touch responses, first-frame skip
  for touch responses, pre-update of collision flags for correct touch response timing.
- **Root cause of remaining 57 errors:** a single 1-pixel X difference at trace frame 6100
  where Sonic leaves a MovingBlock. It cascades to a lava-ball touch at frame 6638, which
  cascades to the remaining 55 errors. Classic "first non-cascading error" lesson.
- **BizHawk-side confirmation:** used an `onmemoryexecute` hook at ROM `0x0081DC`
  (`sub.w D2,8(A1)` inside `MvSonic2`) to confirm that the ROM's `MvSonic2` carries **both**
  X and Y deltas on every frame, *including the jump-off frame*, using D2 (saved platform X
  before `MBlock_Move`). The engine did apply the X carry in `clearRidingObject()` but at
  the specific frame, the computed delta was 0 because the platform's oscillation hadn't
  moved it that tick.
- **Gotcha discovered:** the S1 recorder's `ADDR_FRAMECOUNT` had been set to `0xFE02` —
  wrong. Correct address is `0xFE04`, verified from the ROM `addq.w #1` instruction at
  `0x003AEC`. Once fixed, the trace's `gameplay_frame_counter` column aligned correctly
  with the ROM's `v_framecount` and the cursor-tracking events started matching.

This case study is the canonical example of:
1. Divergence cascade analysis (57 errors → 1 root cause).
2. Using BizHawk execute hooks as a ROM microscope once the first-error frame is known.
3. How a bug in the recorder itself (wrong `ADDR_FRAMECOUNT`) produces hard-to-diagnose
   divergences downstream.

### 10.7 Case study — S3K AIZ intro parity gate

Even more structural than MZ1 — here the issue is not "fix a physics bug" but "the fixture
itself cannot match pixel-perfectly through the intro." Solution:

1. Write a spec
   (`docs/architecture/designs/2026-04-21-s3k-aiz-intro-parity-gate-design.md`) defining the
   six-checkpoint contract.
2. Implement detectors (`S3kReplayCheckpointDetector`) + controller
   (`S3kElasticWindowController`) + guard (`S3kRequiredCheckpointGuard`) in the test
   harness.
3. Update the recorder (`s3k_trace_recorder.lua`'s `aiz_end_to_end` profile) to emit the
   same six checkpoints into `aux_state.jsonl`.
4. Add a fixture sanity gate (PowerShell block in [`trace-replay.md`](trace-replay.md)) that
   verifies no checkpoints are missing, duplicated, or out of order *before* the fixture is
   committed.
5. Enable strict pixel comparison only outside the elastic windows; allow up to
   ±180 engine ticks inside each window.

Once all of that is in place, the S3K fixture can catch genuine divergences in AIZ2 main
gameplay and the HCZ handoff without failing on the fundamentally-unavoidable intro drift.

### 10.8 Branch policy feedback into the loop

The repo's trailer policy (see `CLAUDE.md` §"Branch Documentation Policy") closes the loop:
every trace-related commit must stage:

- `CHANGELOG.md` — what changed, visible in the trace section of the changelog
- `docs/status/known-discrepancies.md` / `docs/S3K_KNOWN_DISCREPANCIES.md` — intentional deviations
- `docs/guide/*` — keep the guide truthful
- `AGENTS.md` + `CLAUDE.md` — keep agent guidance truthful
- `CONFIGURATION.md` — if a new env var was added (`OGGF_S3K_TRACE_PROFILE`,
  `BIZHAWK_EXE`, etc.)

This means every iteration that touches the trace framework necessarily updates the
documentation the next iteration reads. The loop is literally self-documenting.

---

## 11. Part IX — Appendices

### 11.1 RAM address reference per game

All offsets are from `$FF0000`. See the recorders for authoritative values.

**Sonic 1 (REV01):**

| Name | Addr | Size | Notes |
|---|---|---|---|
| `game_mode` | `0xF600` | byte | `0x0C` = level |
| `v_framecount` | `0xFE04` | word BE | Not `0xFE02` |
| `v_vblank` | `0xFE0E` | word BE | |
| `zone / act` | `0xFE10 / 0xFE11` | byte | |
| `rings` (BCD word) | `0xFE20` | word BE | |
| `camera_x / camera_y` | `0xF700 / 0xF704` | long, hi word | |
| `player_base` | `0xD000` | SST slot 0 | |
| player offsets | `0x08..0x3E` | | see s1 recorder |
| `stand_on_obj` | `+0x3D` | byte | SST slot |
| `opl_screen / opl_fwd / opl_bwd` | `0xF76E / 0xF770 / 0xF774` | | |

**Sonic 2 (REV01):**

| Name | Addr | Size | Notes |
|---|---|---|---|
| `game_mode` | `0xF600` | byte | |
| `v_framecount` | `0xFE04` | word BE | Level_frame_counter |
| `v_vblank` | `0xFE0E` | word BE | Vint_runcount+2 |
| `player_base (sonic)` | `0xB000` | | |
| `sidekick_base (tails)` | `0xB040` | | SST slot 1 |
| `tails_control_counter` | `0xF702` | word BE | |
| `tails_respawn_counter` | `0xF704` | word BE | |
| `tails_cpu_routine` | `0xF708` | word BE | |
| `tails_cpu_target_x / y` | `0xF70A / 0xF70C` | word BE | |
| `tails_interact_id` | `0xF70E` | byte | |
| `tails_cpu_jumping` | `0xF70F` | byte | |
| `sonic_stat_record_buf` | `0xE400` | byte buf | Pre-trace history |
| `sonic_pos_record_buf` | `0xE500` | byte buf | |
| `sonic_pos_record_index` | `0xEED2` | word BE | |
| `OFF_CTRL_LOCK` | `+0x2E` | word BE | Differs from S1 (`0x3E`) |

**Sonic 3&K (locked-on):**

| Name | Addr | Size | Notes |
|---|---|---|---|
| `game_mode` | `0xF600` | byte | |
| `v_framecount` | `0xFE08` | word BE | Not `0xFE04` |
| `v_vblank` | `0xFE12` | word BE | V_int_run_count+2 |
| `lag_frame_count` | `0xF628` | word BE | Only S3K has this |
| `zone / act` | `0xFE10 / 0xFE11` | byte | |
| `apparent_act` | `0xEE4F` | byte | Display act during AIZ2 reload |
| `player_mode` | `0xFF08` | word BE | Sonic/Tails/Knuckles |
| `events_fg_5` | `0xEEC6` | word BE | AIZ1 fire transition flag |
| `level_started_flag` | `0xF711` | byte | |
| `ctrl1_locked` | `0xF7CA` | byte | |
| `camera_x / camera_y` | `0xEE78 / 0xEE7C` | word BE | |
| `player_base` | `0xB000` | | Slot 0 |
| `ost_base` | `0xB400` | | Dynamic slot start |
| `x_pos` (32-bit) | `+0x10 / +0x12` | word.word | hi=pixel, lo=subpixel |
| `y_pos` (32-bit) | `+0x14 / +0x16` | | |
| `x_vel / y_vel / inertia` | `+0x18 / +0x1A / +0x1C` | signed word BE | |
| `routine` | `+0x05` | byte | |
| `status` | `+0x2A` | byte | |
| `angle` | `+0x26` | byte | |
| `stand_on_obj` | `+0x42` | word BE | **RAM ptr**, convert via `(addr-0xB400)/0x4A` |
| `ctrl_lock` | `+0x32` | word BE | |

### 11.2 Pre-v5 schema history

The table below is predecessor history only. Current code accepts exactly
`trace_schema: 5` and no separate CSV or recorder-derived schema selector.

| Schema | CSV cols | Key additions |
|---|---|---|
| v1 | 11 | Baseline: frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode |
| v2 | 18 | + x_sub, y_sub, routine, camera_x, camera_y, rings, status_byte |
| v2.1 | 19 | + gameplay_frame_counter (originally `v_framecount`) |
| v2.2 | 20 | + stand_on_obj |
| v3 (S1/S3K) | 22 | + vblank_counter, lag_counter |
| v4 (S2) | — | Per-slot `object_state_snapshot` aux events at frame -1 |
| v5 (S2) | 37 | First-sidekick state appended to each row (deprecated) |
| v6 (S2) | 38 | Explicit named `sonic_*` / `tails_*` blocks with shared header |
| v7 (S2) | 38 | + pre-trace `cpu_state_snapshot` for Tails AI |
| v8 (S2) | 38 | Character-scoped aux events and per-character nearby-object scans |

None of these predecessor versions is a current compatibility target.

### 11.3 S1/S2/S3K zone name tables

**S1 REV01** (`ZONE_NAMES` in `s1_trace_recorder.lua`):

```
0 ghz   1 lz    2 mz    3 slz   4 syz   5 sbz   6 endz  7 ss
```

**S2 REV01**:

```
0x00 ehz  0x02 wz  0x04-05 mtz  0x06 wfz  0x07 htz  0x08 hpz
0x0A ooz  0x0B mcz 0x0C cnz     0x0D cpz  0x0E dez  0x0F arz  0x10 scz
```

**S3K**:

```
0x00 aiz  0x01 hcz  0x02 mgz  0x03 cnz  0x04 fbz  0x05 icz  0x06 lbz
0x07 mhz  0x08 soz  0x09 lrz  0x0A ssz  0x0B dez  0x0C ddz  0x0D hpz
```

### 11.4 Related source files

**Current recorder:**

- `tools/tracechaser/bizhawk-headless/run.sh`
- `tools/tracechaser/bizhawk-headless/src/Recording/`

**Historical/diagnostic recorder references:**

- `tools/tracechaser/bizhawk/s1_trace_recorder.lua`
- `tools/tracechaser/bizhawk/s2_trace_recorder.lua`
- `tools/tracechaser/bizhawk/s3k_trace_recorder.lua`
- `tools/tracechaser/bizhawk/record_trace.bat`, `record_s2_trace.bat`, `record_s3k_trace.bat`
- `tools/tracechaser/retro/trace_core.py`
- `tools/tracechaser/retro/s1_trace_recorder.py`
- `tools/tracechaser/retro/s1_credits_trace_recorder.py`
- `tools/tracechaser/retro/requirements.txt`

**JUnit harness** (`src/test/java/com/openggf/tests/trace/`):

- `AbstractTraceReplayTest.java`
- `AbstractCreditsDemoTraceReplayTest.java`
- `s3k/S3kReplayCheckpointDetector.java`, `s3k/S3kCheckpointProbe.java`,
  `s3k/S3kRequiredCheckpointGuard.java`

**Production trace model and replay helpers** (`src/main/java/com/openggf/trace/`):

- `TraceData.java`, `TraceFrame.java`, `TraceMetadata.java`, `TraceEvent.java`,
  `TraceCharacterState.java`
- `TraceBinder.java`, `ToleranceConfig.java`, `Severity.java`, `FieldComparison.java`,
  `FrameComparison.java`
- `DivergenceReport.java`, `DivergenceGroup.java`
- `EngineDiagnostics.java`, `EngineNearbyObjectFormatter.java`,
  `TouchResponseDebugHitFormatter.java`, `TraceEventFormatter.java`
- `TraceExecutionModel.java`, `TraceExecutionPhase.java`
- `TraceReplayBootstrap.java`
- `RomObjectSnapshot.java` (under `src/main/java/com/openggf/level/objects/`)
- `Sonic1CreditsDemoData.java` (under `src/main/java/com/openggf/game/sonic1/credits/`)

**Runtime collision trace** (`src/main/java/com/openggf/physics/`):

- `CollisionTrace.java`, `CollisionEvent.java`, `SensorResult.java`, `SolidContact.java`,
  `RecordingCollisionTrace.java`, `NoOpCollisionTrace.java`
- `CollisionSystem.java` (holds the trace handle)

**Docs:**

- `docs/guide/contributing/trace-replay.md` — user-facing guide
- `docs/guide/contributing/trace-framework-reference.md` — this document
- `docs/architecture/plans/2026-04-20-s2-s3k-trace-recorder-v3.md`
- `docs/architecture/plans/2026-04-21-s3k-aiz-intro-parity-gate.md`
- `docs/architecture/plans/2026-04-21-s3k-end-to-end-trace-fixture.md`
- `docs/architecture/research/2026-04-20-s2-trace-addresses.md`
- `docs/architecture/research/2026-04-20-s3k-trace-addresses.md`
- `docs/architecture/research/2026-04-21-s3k-trace-addresses.md`
- `docs/architecture/designs/2026-04-21-s3k-aiz-intro-parity-gate-design.md`

**Skills:**

- `.claude/skills/s1-trace-replay/SKILL.md`
- `.claude/skills/trace-replay-bug-fixing/SKILL.md`
- `.claude/skills/bizhawk-headless-trace/SKILL.md`
