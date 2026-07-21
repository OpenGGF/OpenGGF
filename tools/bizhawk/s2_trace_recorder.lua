-- s2_trace_recorder.lua
-- BizHawk Lua script for recording Sonic 2 REV01 frame-by-frame physics
-- state during BK2 movie playback.
--
-- Usage:
--   1. Open BizHawk with Sonic 2 REV01 ROM
--   2. Load a BK2 movie file
--   3. Tools > Lua Console > load this script
--   4. Play the movie -- recording starts automatically when gameplay begins
--   5. Stop the movie or close the script to finalise output files
--
-- v2.0 changes: added subpixel, routine, camera, rings, status_byte columns
-- to physics.csv for faster divergence debugging. Object proximity tracking
-- logs nearby objects every frame instead of only new appearances every 4.
-- v2.1 changes: scan all 128 SST slots (was 63), emit slot_dump events on
-- object appearance for slot allocation comparison, add v_framecount to
-- physics.csv and aux events for ROM↔engine frame cross-referencing.
-- v2.2 changes: add standonobject (offset 0x3D) to physics.csv — which object
-- slot Sonic is riding on. Add routine_change events to aux with full Sonic
-- state + interacting object context (critical for hurt/bounce diagnosis).
-- v3.0-s2 changes: rename v_framecount to gameplay_frame_counter and add
-- vblank_counter plus lag_counter for counter-driven replay phase selection.
-- v4.0-s2 changes: emit per-slot object_state_snapshot events at frame -1
-- (pre-trace) so the engine can hydrate badnik/object state machines to
-- match what the ROM advanced during title-card/level-init iterations.
-- v5.0-s2 changes: append first-sidekick (Tails) state to each physics row so
-- replay can detect world-state drift caused by the sidekick before Sonic
-- diverges downstream.
-- v6.0-s2 changes: record explicit named character blocks for both Sonic and
-- Tails. Shared frame counters remain top-level, while per-character physics
-- fields become symmetric in the CSV.
-- v7.0-s2 changes: emit a pre-trace tails cpu_state_snapshot so replay can
-- hydrate the sidekick AI counters/state accumulated before frame 0.
-- v8.0-s2 changes: add character-scoped aux events and nearby-object scans
-- for both Sonic and Tails so replay debugging can see which character first
-- interacted with the world.
-- v8.1-s2 changes: include top_solid_bit/lrb_solid_bit in state_snapshot
-- diagnostics so collision-plane divergences can be checked against ROM.
-- v8.2-s2 changes: emit focused ObjB2 Tornado state diagnostics for the
-- SCZ/WFZ level-select route without feeding those values back into replay.
-- v9.3-s2 changes: derive the CSV `input` column from the BK2 movie input
-- via `movie.getinput()` instead of `mainmemory.read_u8(ADDR_CTRL1)`. ROM-
-- side `Ctrl_1_Held` ($FFF604) can lag the BK2's logical input by up to
-- several frames during long V-int subroutines or lag-frame sequences in
-- ARZ/OOZ/SCZ-style end-of-act windows (the SCZ Tornado section starting
-- around BK2 frame 5337 showed a 3-frame stale-B-held divergence). Keep
-- the raw_input/logical_input diagnostic fields in the `state_snapshot`
-- aux events so ROM-vs-BK2 input drift is still surfaced for debugging.
------------------------------------------------------------------------------

-----------------
--- Constants ---
-----------------

-- v9.6-s2 changes: include move_lock in state_snapshot diagnostics and emit
-- focused snapshots around the current S2 CNZ elevator/input frontier.
-- v9.7-s2 changes: support selecting later gameplay segments from
-- level-select BK2s. Those movies can cross from act 1 into act 2, but the
-- recorder used to finalise at the first non-level transition and therefore
-- only captured the first controllable segment.
-- v9.8-s2 changes: emit diagnostic per-frame Tails CPU state, including
-- Ctrl_2_Logical and the delayed Sonic history word/status consumed by
-- TailsCPU_Normal.
-- v9.9-s2 changes: add metadata.rng_seed for one-time replay bootstrap and
-- RNG-frontier diagnostics. CSV and aux schemas are unchanged.
-- v9.10-s2 changes: RECORDER HYGIENE ONLY (no schema/data change; existing
-- traces stay valid). Reliable movie-end self-exit so EmuHawk never runs away
-- past the movie: a hard FRAME_CAP backstop (movie.length()+64 else 2,000,000)
-- guarantees the while-true loop terminates even if every movie-end signal
-- fails, and a guarded post-loop block re-issues client.exit() (a no-op on some
-- BizHawk builds) then client.pause() so EmuHawk idles at 0% CPU instead of
-- free-running. (S2 already writes a SINGLE output dir, so it has at most one
-- brief load-time cmd window -- no per-segment mkdir spam to fix here, unlike
-- the multi-segment S1/S3K complete-run recorders.) Mirrors S1 recorder v3.6.
--
-- v9.3-s2: traces from this recorder version onward are bootstrap-comparable
-- against the post-universal-title-card engine (ADR-1, design spec 2026-05-15)
-- AND derive their CSV `input` column from BK2 directly via movie.getinput
-- (see v9.3-s2 change note above for context).
-- The bootstrap-comparator eligibility is derived from this version string by
-- TraceMetadata.nativePreludeMode() — no separate JSON flag is emitted.
-- v9.11-s2 changes: CSV v7 records each character's animation ID and displayed
-- mapping frame every frame for independent animation trace verification.
-- v9.12-s2 changes: multi-stage-trace-runs Task 1 -- env-gated run mode
-- (OGGF_TRACE_RUN_ID). Adds a stage-detour state machine for the S2 giant-
-- ring special-stage round trip (level -> ss -> level), a minimal special-
-- stage physics.csv/aux writer ported from s2_ss_trace_recorder.lua (no
-- event.onmemoryexecute hooks -- state is sampled directly once per $10
-- frame), numbered per-segment output subdirs under OUTPUT_DIR, and a
-- run_manifest.json emitter matching TraceRunManifest's schema (see
-- src/main/java/com/openggf/trace/TraceRunManifest.java). All of this is
-- inert without the env var: plain-mode output is byte-identical to v9.11-s2
-- except this version string (see the run/detour functions block below for
-- the placement rationale and the on_frame_end comments for the exact
-- plain-mode-unreachable gates).
local LUA_SCRIPT_VERSION = "9.12-s2"

-- Output directory (relative to BizHawk working dir)
local OUTPUT_DIR = "trace_output/"

-- Headless mode: run at maximum speed, auto-exit when done.
-- Enable when running via CLI: EmuHawk.exe --chromeless --lua ... --movie ... rom.gen
local HEADLESS = true

-- Movie frame limit: set to 0 for automatic detection from movie.length().
-- When the BK2 movie ends but game_mode is still 0x0C (e.g. waiting for
-- results screen), the emulator would loop forever. This safety limit
-- ensures the script finalises and exits.
local MOVIE_FRAME_SAFETY_MARGIN = 30   -- frames past movie end before auto-exit
local TRACE_PROFILE = os.getenv("OGGF_S2_TRACE_PROFILE") or "gameplay_unlock"
local TARGET_GAMEPLAY_SEGMENT = tonumber(os.getenv("OGGF_TRACE_GAMEPLAY_SEGMENT") or "0") or 0
local BK2_FRAME_COUNT = tonumber(os.getenv("OGGF_BK2_FRAME_COUNT") or "")
local SOURCE_BK2 = os.getenv("OGGF_BK2_BASENAME") or ""

-- Multi-stage run mode (env-gated). All new run-mode state/constants/
-- functions in this file are plain globals (no `local`): the chunk already
-- carries ~151 top-level locals against Lua's ~200-local-per-chunk budget.
-- `Run mode iff run_id ~= nil` gates every new behaviour below -- segment
-- subdirs, the detour branch, transitions, the manifest -- so plain-mode
-- runs (env unset) take none of the new code paths (see the on_frame_end
-- comments for the exact plain-mode-unreachable gates).
run_id = os.getenv("OGGF_TRACE_RUN_ID") or nil
segments_done = {}
transitions_done = {}
detour_active = nil                -- nil | "special_stage"
current_segment_dir_token = nil
current_ss_index = nil
-- LEVEL arms only; the ss segment does not consume a number here --
-- #segments_done would wrongly yield seg3_ for the return level segment,
-- because the ss entry sits between the two level entries in segments_done.
level_segment_count = 0
-- SS detours: first dir token is bare "ss", repeats "ss_2", "ss_3", ...
ss_segment_count = 0
-- Run-mode per-segment file-open target. Stays == OUTPUT_DIR for the whole
-- run in plain mode (never reassigned outside `if run_id ~= nil` branches);
-- open_files()/write_metadata()/reset_recording_state() below were switched
-- from OUTPUT_DIR to this so run mode can redirect per segment while
-- OUTPUT_DIR itself stays the manifest's root directory (contract item 2).
effective_output_dir = OUTPUT_DIR

-- Special-stage RAM addresses/offsets (S2 REV01), transcribed from
-- s2_ss_trace_recorder.lua for the minimal run-mode SS writer port (globals,
-- SS_-prefixed to avoid colliding with this file's existing player-object
-- OFF_*/ADDR_* locals -- several share the same byte offsets in the SST
-- layout, e.g. OFF_STATUS/OFF_ROUTINE/OFF_ANGLE above).
GAMEMODE_SPECIAL_STAGE          = 0x10
SS_SONIC_BASE                   = 0xB000
SS_TAILS_BASE                   = 0xB040
SS_OFF_ID                       = 0x00  -- u8: Sonic=0x09, Tails=0x10, 0=absent
SS_OFF_ANIM_FRAME               = 0x1B  -- u8
SS_OFF_ANIM                     = 0x1C  -- u8
SS_OFF_STATUS                   = 0x22  -- u8
SS_OFF_ROUTINE                  = 0x24  -- u8
SS_OFF_ROUTINE_SECONDARY        = 0x25  -- u8
SS_OFF_ANGLE                    = 0x26  -- u8
SS_OFF_SS_X                     = 0x2A  -- u16be
SS_OFF_SS_X_SUB                 = 0x2C  -- u16be
SS_OFF_SS_Y                     = 0x2E  -- u16be
SS_OFF_SS_Y_SUB                 = 0x30  -- u16be
SS_OFF_FLIP_TIMER               = 0x33  -- u8
SS_OFF_SS_Z                     = 0x34  -- u16be
SS_OFF_HURT_TIMER               = 0x36  -- u8
SS_OFF_SLIDE_TIMER              = 0x37  -- u8
SS_OFF_RINGS_HUNDREDS           = 0x3C  -- u8 (BCD digit)
SS_OFF_RINGS_TENS               = 0x3D  -- u8 (BCD digit)
SS_OFF_RINGS_UNITS              = 0x3E  -- u8 (BCD digit)

SS_ADDR_TRACK_ANIM              = 0xDB08  -- SSTrack_anim (u8)
SS_ADDR_CURRENT_SEGMENT         = 0xDB0A  -- SpecialStage_CurrentSegment (u8)
SS_ADDR_TRACK_ANIM_FRAME        = 0xDB0B  -- SSTrack_anim_frame (u8)
SS_ADDR_TRACK_DRAWING_INDEX     = 0xDB0D  -- SSTrack_drawing_index (u8)
SS_ADDR_TRACK_ORIENTATION       = 0xDB0E  -- SSTrack_Orientation (u8)
SS_ADDR_CUR_SPEED_FACTOR        = 0xDB16  -- SS_Cur_Speed_Factor (u16be)
SS_ADDR_TRACK_DURATION_TIMER    = 0xDB1F  -- SSTrack_duration_timer (u8)
SS_ADDR_PLAYER_ANIM_FRAME_TIMER = 0xDB21  -- SS_player_anim_frame_timer (u8)
SS_ADDR_CHECK_RINGS_FLAG        = 0xDB86  -- SS_Check_Rings_flag (u8)
SS_ADDR_RINGS_TOGO_BCD          = 0xDBA4  -- SS_RingsToGoBCD (u16be, BCD)
SS_ADDR_TAILS_CONTROL_COUNTER   = 0xF702  -- Tails_control_counter (u16be)
SS_ADDR_SWAP_POSITIONS_FLAG     = 0xF742  -- SS_Swap_Positions_Flag (u8)
-- v_lastspecial-equivalent special-stage index, sampled at SS arm time.
SS_ADDR_SPECIAL_STAGE_INDEX     = 0xFE16
INPUT_START                     = 0x80

-- Detour transition RAM fields (contract item 3/4; VERIFY-ON-FIRST-CAPTURE).
ADDR_BIGRING_FLAG               = 0xF7CD  -- f_bigring / special_bonus_entry_flag
ADDR_SAVED_X_POS                = 0xFE32
ADDR_SAVED_Y_POS                = 0xFE34
ADDR_LAST_STAR_POST_HIT         = 0xFE30
ADDR_EMERALDS                   = 0xFFB1

-- S2 REV01 68K RAM addresses (mainmemory domain = $FF0000 base stripped)
local ADDR_GAME_MODE       = 0xF600
local ADDR_CTRL1           = 0xF604   -- byte: Ctrl_1_Held (raw held input)
local ADDR_CTRL1_DUP       = 0xF602   -- byte: Ctrl_1_Held_Logical
local ADDR_CTRL2           = 0xF606   -- byte: Ctrl_2_Held (raw held input)
local ADDR_CTRL2_LOGICAL   = 0xF66A   -- byte: Ctrl_2_Held_Logical
local ADDR_RING_COUNT      = 0xFE20   -- word: Ring_count
local ADDR_CAMERA_X        = 0xEE00   -- long: Camera_X_pos
local ADDR_CAMERA_Y        = 0xEE04   -- long: Camera_Y_pos
local ADDR_ZONE            = 0xFE10   -- byte: Current_Zone
local ADDR_ACT             = 0xFE11   -- byte: Current_Act
local ADDR_RANDOM          = 0xF636   -- long: RNG_seed
-- Player object base ($FFFFB000 = MainCharacter)
local PLAYER_BASE          = 0xB000
local OFF_X_POS            = 0x08   -- word: centre X
local OFF_X_SUB            = 0x0A   -- word: X subpixel (16-bit fraction)
local OFF_Y_POS            = 0x0C   -- word: centre Y
local OFF_Y_SUB            = 0x0E   -- word: Y subpixel (16-bit fraction)
local OFF_X_VEL            = 0x10   -- signed word: X velocity
local OFF_Y_VEL            = 0x12   -- signed word: Y velocity
local OFF_INERTIA          = 0x14   -- signed word: ground speed
local OFF_RADIUS_Y         = 0x16   -- signed byte: Y radius (hitbox half-height)
local OFF_RADIUS_X         = 0x17   -- signed byte: X radius (hitbox half-width)
local OFF_ANIM_FRAME_DISP  = 0x1A   -- byte
local OFF_ANIM_FRAME       = 0x1B   -- byte
local OFF_ANIM_ID          = 0x1C   -- byte
local OFF_ANIM_TIMER       = 0x1E   -- byte
local OFF_STATUS           = 0x22   -- byte: status flags
local OFF_ROUTINE          = 0x24   -- byte: player movement routine
local OFF_ANGLE            = 0x26   -- byte: terrain angle
local OFF_STICK_CONVEX     = 0x38   -- byte
local OFF_STAND_ON_OBJ     = 0x3D   -- byte: interact — SST index Sonic stands on (0=none)
local OFF_CTRL_LOCK        = 0x2E   -- word: move_lock timer
local OFF_TOP_SOLID_BIT    = 0x46   -- byte: active top collision plane ($0C/$0E)
local OFF_LRB_SOLID_BIT    = 0x47   -- byte: active side/bottom collision plane ($0D/$0F)

-- S2 player routine values (obRoutine byte → table index):
--   0 = Obj01_Init
--   2 = Obj01_Control
--   4 = Obj01_Hurt
--   6 = Obj01_Dead
local ROUTINE_HURT         = 0x04
local ROUTINE_DEATH        = 0x06

-- Status flag bits
local STATUS_FACING_LEFT   = 0x01
local STATUS_IN_AIR        = 0x02
local STATUS_ROLLING       = 0x04
local STATUS_ON_OBJECT     = 0x08
local STATUS_ROLL_JUMP     = 0x10
local STATUS_PUSHING       = 0x20
local STATUS_UNDERWATER    = 0x40

-- ObjPosLoad cursor state (for ROM↔engine cursor comparison)
local ADDR_OPL_ROUTINE     = 0xF76C   -- byte: v_opl_routine (0=OPL_Main, 2=OPL_Next)
local ADDR_OPL_SCREEN      = 0xF76E   -- word: v_opl_screen (last processed camera chunk)
local ADDR_OPL_DATA_FWD    = 0xF770   -- long: v_opl_data (forward cursor ROM pointer)
local ADDR_OPL_DATA_BWD    = 0xF774   -- long: v_opl_data+4 (backward cursor ROM pointer)
local ADDR_OBJSTATE         = 0xFC00   -- byte[192]: v_objstate array (verified from ROM lea instruction)
-- v_objstate[0] = forward counter, v_objstate[1] = backward counter
local ADDR_SONIC_STAT_RECORD_BUF = 0xE400
local ADDR_SONIC_POS_RECORD_BUF  = 0xE500
local ADDR_SONIC_POS_RECORD_INDEX = 0xEED2
local ADDR_TAILS_CONTROL_COUNTER = 0xF702
local ADDR_TAILS_RESPAWN_COUNTER = 0xF704
local ADDR_TAILS_CPU_ROUTINE     = 0xF708
local ADDR_TAILS_CPU_TARGET_X    = 0xF70A
local ADDR_TAILS_CPU_TARGET_Y    = 0xF70C
local ADDR_TAILS_INTERACT_ID     = 0xF70E
local ADDR_TAILS_CPU_JUMPING     = 0xF70F

-- Object table (S2 SST: 128 slots of $40 bytes at $FFFFB000)
local OBJ_TABLE_START      = 0xB000
local OBJ_SLOT_SIZE        = 0x40
local OBJ_TOTAL_SLOTS      = 128  -- total SST slots (0-127)
local OBJ_DYNAMIC_START    = 16   -- first dynamic slot (Dynamic_Object_RAM)
local OBJ_DYNAMIC_COUNT    = 112  -- dynamic slots 16-127
local SIDEKICK_BASE        = OBJ_TABLE_START + OBJ_SLOT_SIZE  -- slot 1 = Tails/sidekick

-- Frame counter (v_framecount at $FFFE04, word — increments each Level_MainLoop)
-- NOTE: 0xFE0C is Vint_runcount (longword, VBlank interrupt counter);
-- read +2 so the CSV stores the low word that changes during normal traces.
local ADDR_FRAMECOUNT      = 0xFE04
local ADDR_VBLA_WORD       = 0xFE0E
local ADDR_SLOT_MACHINE_IN_USE = 0xFF4C
local ADDR_SLOT_MACHINE_ROUTINE = 0xFF4E
local ADDR_SLOT_MACHINE_TIMER = 0xFF4F
local ADDR_SLOT_MACHINE_INDEX = 0xFF51
local ADDR_SLOT_MACHINE_REWARD = 0xFF52
local ADDR_SLOT_MACHINE_SLOT1_POS = 0xFF54
local ADDR_SLOT_MACHINE_SLOT1_SPEED = 0xFF56
local ADDR_SLOT_MACHINE_SLOT1_ROUTINE = 0xFF57
local ADDR_SLOT_MACHINE_SLOT2_POS = 0xFF58
local ADDR_SLOT_MACHINE_SLOT2_SPEED = 0xFF5A
local ADDR_SLOT_MACHINE_SLOT2_ROUTINE = 0xFF5B
local ADDR_SLOT_MACHINE_SLOT3_POS = 0xFF5C
local ADDR_SLOT_MACHINE_SLOT3_SPEED = 0xFF5E
local ADDR_SLOT_MACHINE_SLOT3_ROUTINE = 0xFF5F

-- Genesis joypad bitmask (matching engine convention)
local INPUT_UP    = 0x01
local INPUT_DOWN  = 0x02
local INPUT_LEFT  = 0x04
local INPUT_RIGHT = 0x08
local INPUT_JUMP  = 0x10

-- Game mode values
local GAMEMODE_LEVEL = 0x0C

-- Zone ID to short name mapping (matches s2.constants.asm)
local ZONE_NAMES = {
    [0x00] = "ehz",
    [0x01] = "unknown_01",
    [0x02] = "wz",
    [0x03] = "unknown_03",
    [0x04] = "mtz",
    [0x05] = "mtz",
    [0x06] = "wfz",
    [0x07] = "htz",
    [0x08] = "hpz",
    [0x09] = "unknown_09",
    [0x0A] = "ooz",
    [0x0B] = "mcz",
    [0x0C] = "cnz",
    [0x0D] = "cpz",
    [0x0E] = "dez",
    [0x0F] = "arz",
    [0x10] = "scz",
}

-- Engine progression zone ids used by Sonic2ZoneRegistry / TraceCatalog.
local ROM_ZONE_TO_ENGINE_ZONE = {
    [0x00] = 0,  -- EHZ
    [0x0D] = 1,  -- CPZ
    [0x0F] = 2,  -- ARZ
    [0x0C] = 3,  -- CNZ
    [0x07] = 4,  -- HTZ
    [0x0B] = 5,  -- MCZ
    [0x0A] = 6,  -- OOZ
    [0x04] = 7,  -- MTZ
    [0x05] = 7,  -- MTZ alternate act id
    [0x10] = 8,  -- SCZ
    [0x06] = 9,  -- WFZ
    [0x0E] = 10, -- DEZ
}

-- Snapshot interval (frames between full state snapshots in aux file)
local SNAPSHOT_INTERVAL = 60

-- Object proximity radius (pixels) for per-frame nearby object logging
local OBJECT_PROXIMITY = 160

-----------------
--- State     ---
-----------------

local started = false
local finished = false   -- once true, never re-arm
local skipping_segment = false
local skipped_segment_zone_name = nil
local gameplay_segment_index = 0
local trace_frame = 0
local bk2_frame_offset = 0
local start_x = 0
local start_y = 0
local start_rng_seed = 0
local start_zone_id = 0
local start_rom_zone_id = 0
local start_zone_name = "unknown"
local start_act = 0
local emitted_checkpoints = {}
local last_zone_act_state_key = nil
local recorded_sidekick_present = false

local prev_character_state = {
    sonic = { status = 0, routine = 0, ctrl_lock = 0 },
    tails = { status = 0, routine = 0, ctrl_lock = 0 },
}
local prev_opl_screen = -1  -- track OPL chunk transitions

-- Object tracking: slot -> last known type ID
local known_objects = {}

-- File handles
local physics_file = nil
local aux_file = nil
local close_files
local read_character_trace_state

-----------------
--- Helpers   ---
-----------------

-- Read a 16-bit signed value (big-endian)
local function read_speed(base, offset)
    return mainmemory.read_s16_be(base + offset)
end

-- Convert raw ROM joypad byte (Ctrl_1_Held) to engine input bitmask.
-- ROM bits: 0=Up 1=Down 2=Left 3=Right 4=B 5=C 6=A 7=Start
-- Bits 0-3 already match INPUT_UP/DOWN/LEFT/RIGHT; collapse A/B/C to JUMP.
local function rom_joypad_to_mask(raw)
    local mask = raw & 0x0F                        -- directions (bits 0-3)
    if (raw & 0x70) ~= 0 then mask = mask + INPUT_JUMP end  -- A|B|C -> JUMP
    return mask
end

-- Read the BK2 movie's logical input for the just-completed frame and convert
-- it to the engine's input bitmask. This bypasses ROM-side staleness in
-- $FFF604 (Ctrl_1_Held) which can lag the BK2 input by several frames on
-- specific lag-frame / long-V-int-subroutine windows (notably SCZ Tornado-
-- handoff and OOZ/ARZ end-of-act transitions). The replay test fixture
-- reads the same BK2 file directly, so using movie.getinput here keeps the
-- trace's `input` column perfectly aligned with what the replay sees.
--
-- Returns the engine bitmask: bit0=UP, bit1=DOWN, bit2=LEFT, bit3=RIGHT,
-- bit4=JUMP (if any of A/B/C are pressed). Falls back to the RAM-derived
-- mask when no movie is loaded.
local function bk2_input_mask(fallback_raw, trace_row)
    if not movie.isloaded() then
        return rom_joypad_to_mask(fallback_raw)
    end
    -- Replay metadata defines trace row N as BK2 frame
    -- (bk2_frame_offset + N). Use that same convention here; direct
    -- emu.framecount() is one frame ahead in this recorder loop.
    local frame_index = bk2_frame_offset ~= nil
        and trace_row ~= nil
        and (bk2_frame_offset + trace_row)
        or emu.framecount()
    local jp = movie.getinput(frame_index, 1)
    if jp == nil then
        return rom_joypad_to_mask(fallback_raw)
    end
    local mask = 0
    if jp["P1 Up"]    or jp["Up"]    then mask = mask | INPUT_UP    end
    if jp["P1 Down"]  or jp["Down"]  then mask = mask | INPUT_DOWN  end
    if jp["P1 Left"]  or jp["Left"]  then mask = mask | INPUT_LEFT  end
    if jp["P1 Right"] or jp["Right"] then mask = mask | INPUT_RIGHT end
    if jp["P1 A"] or jp["A"] or jp["P1 B"] or jp["B"]
            or jp["P1 C"] or jp["C"] then
        mask = mask | INPUT_JUMP
    end
    return mask
end

-- Format a number as hex with specified width
local function hex(val, width)
    width = width or 4
    if val < 0 then
        val = val + 0x10000
    end
    return string.format("%0" .. width .. "X", val)
end

local function json_escape(value)
    value = tostring(value or "")
    value = value:gsub("\\", "\\\\")
    value = value:gsub('"', '\\"')
    return value
end

local function is_level_gated_reset_aware_profile()
    return TRACE_PROFILE == "level_gated_reset_aware"
end

local function engine_zone_for_rom_zone(rom_zone_id)
    return ROM_ZONE_TO_ENGINE_ZONE[rom_zone_id] or rom_zone_id
end

local function apparent_act_for(rom_zone_id, actual_act)
    if rom_zone_id == 0x05 then
        return actual_act + 2
    end
    return actual_act
end

-- Get ground mode from angle (offset quadrants matching ROM thresholds).
-- Floor wraps: 0xE0-0xFF and 0x00-0x1F are both mode 0.
local function angle_to_ground_mode(angle)
    if angle <= 0x1F or angle >= 0xE0 then return 0 end   -- floor
    if angle >= 0x20 and angle <= 0x5F then return 1 end   -- right wall
    if angle >= 0x60 and angle <= 0x9F then return 2 end   -- ceiling
    return 3                                                 -- left wall
end

-- Write a JSONL line to aux file
local function write_aux(json_str)
    if aux_file then
        aux_file:write(json_str .. "\n")
        aux_file:flush()
    end
end

local function emit_zone_act_state(frame, raw_zone_id, engine_zone_id, actual_act, apparent_act, game_mode)
    local key = string.format("%d:%d:%d:%d:%d:%d",
        frame, raw_zone_id, engine_zone_id, actual_act, apparent_act, game_mode)
    if key == last_zone_act_state_key then
        return
    end
    last_zone_act_state_key = key
    write_aux(string.format(
        '{"frame":%d,"event":"zone_act_state","actual_zone_id":%d,"engine_zone_id":%d,"actual_act":%d,"apparent_act":%d,"game_mode":%d}',
        frame, raw_zone_id, engine_zone_id, actual_act, apparent_act, game_mode))
end

local function emit_checkpoint_once(frame, name, raw_zone_id, engine_zone_id, actual_act, apparent_act, game_mode, notes)
    if emitted_checkpoints[name] then
        return
    end
    emitted_checkpoints[name] = true
    local notes_json = ""
    if notes ~= nil and notes ~= "" then
        notes_json = string.format(',"notes":"%s"', json_escape(notes))
    end
    write_aux(string.format(
        '{"frame":%d,"event":"checkpoint","name":"%s","actual_zone_id":%d,"engine_zone_id":%d,"actual_act":%d,"apparent_act":%d,"game_mode":%d%s}',
        frame, json_escape(name), raw_zone_id, engine_zone_id, actual_act, apparent_act, game_mode, notes_json))
end

local function emit_current_zone_act_state(frame, game_mode)
    local raw_zone_id = mainmemory.read_u8(ADDR_ZONE)
    local engine_zone_id = engine_zone_for_rom_zone(raw_zone_id)
    local actual_act = mainmemory.read_u8(ADDR_ACT)
    local apparent_act = apparent_act_for(raw_zone_id, actual_act)
    emit_zone_act_state(frame, raw_zone_id, engine_zone_id, actual_act, apparent_act, game_mode)
    if actual_act ~= start_act then
        emit_checkpoint_once(frame,
            string.format("act_transition_to_%s%d", start_zone_name, apparent_act + 1),
            raw_zone_id, engine_zone_id, actual_act, apparent_act, game_mode, nil)
    end
end

-- State-only reset, split out of reset_recording_state (contract item 4) so
-- the run-mode post-SS re-arm can reuse it WITHOUT deleting the just-
-- finalized ss/ segment's output files. Called by both reset_recording_state
-- below (plain-mode level_gated_reset_aware branch, which also os.removes
-- the flat files) and the run-mode-only global reset_recording_state_keep_files
-- (defined after close_files, alongside the other run/detour functions).
local function reset_recording_state_fields()
    started = false
    trace_frame = 0
    bk2_frame_offset = 0
    start_x = 0
    start_y = 0
    start_rng_seed = 0
    start_zone_id = 0
    start_rom_zone_id = 0
    start_zone_name = "unknown"
    start_act = 0
    prev_character_state = {
        sonic = { status = 0, routine = 0, ctrl_lock = 0 },
        tails = { status = 0, routine = 0, ctrl_lock = 0 },
    }
    prev_opl_screen = -1
    known_objects = {}
    emitted_checkpoints = {}
    last_zone_act_state_key = nil
end

local function reset_recording_state()
    close_files()
    os.remove(effective_output_dir .. "metadata.json")
    os.remove(effective_output_dir .. "physics.csv")
    os.remove(effective_output_dir .. "aux_state.jsonl")
    reset_recording_state_fields()
end

-----------------
--- Recording ---
-----------------

local function open_files()
    physics_file = io.open(effective_output_dir .. "physics.csv", "w")
    aux_file = io.open(effective_output_dir .. "aux_state.jsonl", "w")

    -- v7 header: shared execution counters plus symmetric Player/Sidekick blocks.
    physics_file:write("frame,input,camera_x,camera_y,rings,gameplay_frame_counter,"
        .. "vblank_counter,lag_counter,player_present,player_x,player_y,player_x_speed,"
        .. "player_y_speed,player_g_speed,player_angle,player_air,player_rolling,"
        .. "player_ground_mode,player_x_sub,player_y_sub,player_routine,player_status_byte,"
        .. "player_stand_on_obj,player_animation_id,player_mapping_frame,"
        .. "sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,"
        .. "sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,"
        .. "sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,"
        .. "sidekick_status_byte,sidekick_stand_on_obj,sidekick_animation_id,"
        .. "sidekick_mapping_frame\n")
    physics_file:flush()
end

local function write_metadata()
    -- Use zone/act captured at recording start (not current RAM which may have advanced)
    local sidekick_present = recorded_sidekick_present
            or read_character_trace_state(SIDEKICK_BASE).present ~= 0
    local characters_json = sidekick_present and '["sonic", "tails"]' or '["sonic"]'
    local sidekicks_json = sidekick_present and '["tails"]' or '[]'
    local meta_file = io.open(effective_output_dir .. "metadata.json", "w")
    meta_file:write("{\n")
    meta_file:write('  "game": "s2",\n')
    meta_file:write('  "zone": "' .. start_zone_name .. '",\n')
    meta_file:write('  "zone_id": ' .. start_zone_id .. ',\n')
    meta_file:write('  "rom_zone_id": ' .. start_rom_zone_id .. ',\n')
    meta_file:write('  "act": ' .. (apparent_act_for(start_rom_zone_id, start_act) + 1) .. ',\n')
    meta_file:write('  "gameplay_segment": ' .. gameplay_segment_index .. ',\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "start_x": "0x' .. hex(start_x) .. '",\n')
    meta_file:write('  "start_y": "0x' .. hex(start_y) .. '",\n')
    meta_file:write('  "characters": ' .. characters_json .. ',\n')
    meta_file:write('  "main_character": "sonic",\n')
    meta_file:write('  "sidekicks": ' .. sidekicks_json .. ',\n')
    meta_file:write('  "rng_seed": "0x' .. hex(start_rng_seed, 8) .. '",\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    meta_file:write('  "lua_script_version": "' .. LUA_SCRIPT_VERSION .. '",\n')
    meta_file:write('  "trace_schema": 9,\n')
    meta_file:write('  "csv_version": 7,\n')
    meta_file:write('  "aux_schema_extras": ["cnz_slot_machine_state_per_frame", "cpu_state_per_frame"],\n')
    meta_file:write('  "trace_profile": "' .. json_escape(TRACE_PROFILE) .. '",\n')
    meta_file:write('  "bizhawk_version": "2.11",\n')
    meta_file:write('  "genesis_core": "Genplus-gx",\n')
    meta_file:write('  "route": "' .. start_zone_name .. '",\n')
    meta_file:write('  "source_bk2": "' .. json_escape(SOURCE_BK2) .. '",\n')
    -- Run mode only (contract item 6): mirrors the S1/S3K level writers'
    -- conditional run_id/segment_index emission. Gated as a single block on
    -- run_id so plain-mode metadata.json stays byte-identical (Step 6).
    if run_id ~= nil then
        meta_file:write('  "run_id": "' .. run_id .. '",\n')
        meta_file:write('  "segment_index": ' .. #segments_done .. ',\n')
    end
    meta_file:write('  "rom_checksum": "",\n')
    meta_file:write('  "notes": ""\n')
    meta_file:write("}\n")
    meta_file:close()
    print(string.format("Metadata written. Zone: %s Act %d, Trace frames: %d",
        start_zone_name, start_act + 1, trace_frame))
end

function read_character_trace_state(base)
    local present = mainmemory.read_u8(base) ~= 0
    if not present then
        return {
            present = 0,
            x = 0,
            y = 0,
            x_speed = 0,
            y_speed = 0,
            g_speed = 0,
            angle = 0,
            air = 0,
            rolling = 0,
            ground_mode = 0,
            x_sub = 0,
            y_sub = 0,
            routine = 0,
            status = 0,
            stand_on_obj = 0,
            animation_id = 0,
            mapping_frame = 0,
        }
    end

    local status = mainmemory.read_u8(base + OFF_STATUS)
    local angle = mainmemory.read_u8(base + OFF_ANGLE)
    local air = (status & STATUS_IN_AIR) ~= 0
    local rolling = (status & STATUS_ROLLING) ~= 0

    return {
        present = 1,
        x = mainmemory.read_u16_be(base + OFF_X_POS),
        y = mainmemory.read_u16_be(base + OFF_Y_POS),
        x_speed = read_speed(base, OFF_X_VEL),
        y_speed = read_speed(base, OFF_Y_VEL),
        g_speed = read_speed(base, OFF_INERTIA),
        angle = angle,
        air = air and 1 or 0,
        rolling = rolling and 1 or 0,
        ground_mode = air and 0 or angle_to_ground_mode(angle),
        x_sub = mainmemory.read_u16_be(base + OFF_X_SUB),
        y_sub = mainmemory.read_u16_be(base + OFF_Y_SUB),
        routine = mainmemory.read_u8(base + OFF_ROUTINE),
        status = status,
        stand_on_obj = mainmemory.read_u8(base + OFF_STAND_ON_OBJ),
        animation_id = mainmemory.read_u8(base + OFF_ANIM_ID),
        mapping_frame = mainmemory.read_u8(base + OFF_ANIM_FRAME_DISP),
    }
end

local function write_cnz_slot_machine_state()
    if not aux_file then return end
    if start_rom_zone_id ~= 0x0C then return end

    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local vbc = mainmemory.read_u16_be(ADDR_VBLA_WORD)
    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"vbc":"0x%04X","event":"cnz_slot_machine_state",'
        .. '"in_use":"0x%04X","routine":"0x%02X","timer":"0x%02X","index":"0x%02X",'
        .. '"reward":"0x%04X","slot1_pos":"0x%04X","slot1_speed":"0x%02X","slot1_routine":"0x%02X",'
        .. '"slot2_pos":"0x%04X","slot2_speed":"0x%02X","slot2_routine":"0x%02X",'
        .. '"slot3_pos":"0x%04X","slot3_speed":"0x%02X","slot3_routine":"0x%02X"}',
        trace_frame,
        vfc,
        vbc,
        mainmemory.read_u16_be(ADDR_SLOT_MACHINE_IN_USE),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_ROUTINE),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_TIMER),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_INDEX),
        mainmemory.read_u16_be(ADDR_SLOT_MACHINE_REWARD),
        mainmemory.read_u16_be(ADDR_SLOT_MACHINE_SLOT1_POS),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_SLOT1_SPEED),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_SLOT1_ROUTINE),
        mainmemory.read_u16_be(ADDR_SLOT_MACHINE_SLOT2_POS),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_SLOT2_SPEED),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_SLOT2_ROUTINE),
        mainmemory.read_u16_be(ADDR_SLOT_MACHINE_SLOT3_POS),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_SLOT3_SPEED),
        mainmemory.read_u8(ADDR_SLOT_MACHINE_SLOT3_ROUTINE)))
end

function close_files()
    if physics_file then
        physics_file:close()
        physics_file = nil
    end
    if aux_file then
        aux_file:close()
        aux_file = nil
    end
end

-----------------------------------------------------------------------------
-- Multi-stage run mode: run/detour functions (globals, port of
-- s1_complete_run_recorder.lua / s3k_complete_run_recorder.lua's run/detour
-- machinery, adapted to S2 constants and the minimal SS writer from
-- s2_ss_trace_recorder.lua). MUST be defined here, after close_files, so
-- they close over the file-scope locals they reference (physics_file,
-- aux_file, trace_frame, started, bk2_frame_offset, start_zone_id,
-- start_rom_zone_id, start_act, close_files, bk2_input_mask, write_metadata,
-- reset_recording_state_fields, json_escape, TRACE_PROFILE,
-- apparent_act_for) as upvalues. A global `function` defined earlier in the
-- chunk (e.g. near the top constants) would instead bind those bare names as
-- globals (nil), passing the parse gate but exploding at the first live
-- detour.
-----------------------------------------------------------------------------

-- Run-mode-only state reset that mirrors reset_recording_state's field reset
-- WITHOUT deleting any files (contract item 4). Used by the on_frame_end
-- detour machine's Block 2 fall-through, immediately after finalize_ss_
-- segment() closes the already-finalized ss/ segment: without this, stale
-- emitted_checkpoints/last_zone_act_state_key from the FIRST level segment
-- would silently suppress "gameplay_start"/act-transition checkpoints on the
-- RETURN level segment (the checkpoint dedup tables are keyed by name only,
-- not per-segment).
function reset_recording_state_keep_files()
    reset_recording_state_fields()
end

-- Run-mode per-segment mkdir. Reuses this file's existing mechanism (the
-- unconditional os.execute("mkdir ...") 2>NUL done once at load time for the
-- flat OUTPUT_DIR, near the bottom of the file) just parameterised per
-- segment dir instead of the single flat directory.
function ensure_output_dir(dir)
    os.execute("mkdir \"" .. dir .. "\" 2>NUL")
end

-- Appends a finished LEVEL segment's entry to segments_done (run-mode-only
-- callers: the detour entry, the movie-done funnel, and the non-level/BK2-
-- end/FRAME_CAP finalize sites once routed through finalize_run_end). `act`
-- is 1-based and `profile` is TRACE_PROFILE -- pinned to "gameplay_unlock"
-- for the round-trip because the Task 3 capture procedure does not set
-- OGGF_S2_TRACE_PROFILE and the Task 2 fixture's level segments/manifest
-- entries carry exactly that string (TRACE_PROFILE's default, ~L98).
function append_level_segment_done(rows)
    segments_done[#segments_done + 1] = {
        dir = current_segment_dir_token,
        kind = "level",
        profile = TRACE_PROFILE,
        zone_id = start_zone_id,
        act = apparent_act_for(start_rom_zone_id, start_act) + 1,
        bk2_frame_offset = bk2_frame_offset,
        rows = rows,
    }
end

-- Read the BK2 movie's logical input for the given absolute BK2 frame index
-- and controller (1 or 2), converted to the engine's input bitmask. Minimal
-- port of s2_ss_trace_recorder.lua's joypad_mask_from_frame (~L339-359).
-- Returns 0 when no movie is loaded or the controller has no recorded input.
function joypad_mask_from_frame(frame_index, player)
    if not movie.isloaded() then
        return 0
    end
    local jp = movie.getinput(frame_index, player)
    if jp == nil then
        return 0
    end
    local prefix = "P" .. player .. " "
    local mask = 0
    if jp[prefix .. "Up"] or jp["Up"] then mask = mask | INPUT_UP end
    if jp[prefix .. "Down"] or jp["Down"] then mask = mask | INPUT_DOWN end
    if jp[prefix .. "Left"] or jp["Left"] then mask = mask | INPUT_LEFT end
    if jp[prefix .. "Right"] or jp["Right"] then mask = mask | INPUT_RIGHT end
    if jp[prefix .. "A"] or jp["A"] or jp[prefix .. "B"] or jp["B"]
            or jp[prefix .. "C"] or jp["C"] then
        mask = mask | INPUT_JUMP
    end
    if jp[prefix .. "Start"] or jp["Start"] then mask = mask | INPUT_START end
    return mask
end

-- Read one player's special-stage SST state. Minimal port of
-- s2_ss_trace_recorder.lua's read_ss_character (~L189-223). Returns
-- present=false with zeroed fields when the slot's id byte is 0 (character
-- absent, e.g. Tails not unlocked/selected).
function read_ss_character(base)
    local id = mainmemory.read_u8(base + SS_OFF_ID)
    if id == 0 then
        return {
            present = false,
            ss_x = 0, ss_x_sub = 0, ss_y = 0, ss_y_sub = 0, ss_z = 0,
            angle = 0, routine = 0, routine_secondary = 0, status = 0,
            anim = 0, anim_frame = 0, rings_bcd = 0,
            hurt_timer = 0, slide_timer = 0, flip_timer = 0,
        }
    end

    local hundreds = mainmemory.read_u8(base + SS_OFF_RINGS_HUNDREDS)
    local tens = mainmemory.read_u8(base + SS_OFF_RINGS_TENS)
    local units = mainmemory.read_u8(base + SS_OFF_RINGS_UNITS)

    return {
        present = true,
        ss_x = mainmemory.read_u16_be(base + SS_OFF_SS_X),
        ss_x_sub = mainmemory.read_u16_be(base + SS_OFF_SS_X_SUB),
        ss_y = mainmemory.read_u16_be(base + SS_OFF_SS_Y),
        ss_y_sub = mainmemory.read_u16_be(base + SS_OFF_SS_Y_SUB),
        ss_z = mainmemory.read_u16_be(base + SS_OFF_SS_Z),
        angle = mainmemory.read_u8(base + SS_OFF_ANGLE),
        routine = mainmemory.read_u8(base + SS_OFF_ROUTINE),
        routine_secondary = mainmemory.read_u8(base + SS_OFF_ROUTINE_SECONDARY),
        status = mainmemory.read_u8(base + SS_OFF_STATUS),
        anim = mainmemory.read_u8(base + SS_OFF_ANIM),
        anim_frame = mainmemory.read_u8(base + SS_OFF_ANIM_FRAME),
        rings_bcd = (hundreds << 16) | (tens << 8) | units,
        hurt_timer = mainmemory.read_u8(base + SS_OFF_HURT_TIMER),
        slide_timer = mainmemory.read_u8(base + SS_OFF_SLIDE_TIMER),
        flip_timer = mainmemory.read_u8(base + SS_OFF_FLIP_TIMER),
    }
end

-- One reusable state reader backs the SS physics.csv row. Minimal port of
-- s2_ss_trace_recorder.lua's read_ss_state (~L228-245).
function read_ss_state()
    return {
        speed_factor = mainmemory.read_u16_be(SS_ADDR_CUR_SPEED_FACTOR),
        track_anim = mainmemory.read_u8(SS_ADDR_TRACK_ANIM),
        track_anim_frame = mainmemory.read_u8(SS_ADDR_TRACK_ANIM_FRAME),
        track_drawing_index = mainmemory.read_u8(SS_ADDR_TRACK_DRAWING_INDEX),
        track_orientation = mainmemory.read_u8(SS_ADDR_TRACK_ORIENTATION),
        track_duration_timer = mainmemory.read_u8(SS_ADDR_TRACK_DURATION_TIMER),
        current_segment = mainmemory.read_u8(SS_ADDR_CURRENT_SEGMENT),
        player_anim_frame_timer = mainmemory.read_u8(SS_ADDR_PLAYER_ANIM_FRAME_TIMER),
        rings_togo_bcd = mainmemory.read_u16_be(SS_ADDR_RINGS_TOGO_BCD),
        check_rings_flag = mainmemory.read_u8(SS_ADDR_CHECK_RINGS_FLAG),
        tails_control_counter = mainmemory.read_u16_be(SS_ADDR_TAILS_CONTROL_COUNTER),
        swap_positions_flag = mainmemory.read_u8(SS_ADDR_SWAP_POSITIONS_FLAG),
        sonic = read_ss_character(SS_SONIC_BASE),
        tails = read_ss_character(SS_TAILS_BASE),
    }
end

-- ss/ metadata.json. Minimal port of s2_ss_trace_recorder.lua's
-- write_metadata (~L386-405): distinct shape from the level write_metadata
-- above -- trace_profile is unconditionally "s2_special_stage" and carries
-- special_stage_index + ss_csv_version, both required by
-- TraceRunManifest.Segment.validate for kind=="special_stage".
function write_ss_metadata()
    local meta_file = io.open(effective_output_dir .. "metadata.json", "w")
    meta_file:write("{\n")
    meta_file:write('  "game": "s2",\n')
    meta_file:write('  "trace_profile": "s2_special_stage",\n')
    meta_file:write('  "special_stage_index": ' .. current_ss_index .. ',\n')
    meta_file:write('  "ss_csv_version": 1,\n')
    meta_file:write('  "characters": ["sonic", "tails"],\n')
    meta_file:write('  "main_character": "sonic",\n')
    meta_file:write('  "sidekicks": ["tails"],\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "source_bk2": "' .. json_escape(SOURCE_BK2) .. '",\n')
    meta_file:write('  "lua_script_version": "' .. LUA_SCRIPT_VERSION .. '",\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    if run_id ~= nil then
        meta_file:write('  "run_id": "' .. run_id .. '",\n')
    end
    meta_file:write('  "fresh_load": false,\n')
    meta_file:write('  "segment_index": ' .. #segments_done .. '\n')
    meta_file:write("}\n")
    meta_file:close()
end

-- Arm the special-stage segment. Called exactly once per SS detour, on the
-- first frame game_mode reads $10 after detour_active was not already
-- "special_stage" (see the on_frame_end entry-vs-continuation gate).
--
-- Multi-detour dir tokens (upgraded from the single-ss MVP): the first
-- detour uses the bare "ss" token, repeats use "ss_2", "ss_3", ... --
-- the S3K segment_dir_counts convention. Needed because real round-trip
-- movies re-enter the halfpipe from later star posts.
--
-- SS frame-0 alignment (run-port convention -- NOT the interior
-- s2_ss_trace_recorder.lua's convention): the on_frame_end detour machine's
-- entry branch returns without writing a row, so ss row 0 is recorded on the
-- NEXT $10 frame with bk2_frame_offset sampled here at entry -- the same
-- alignment the shipped S1/S3K run ports use. The interior recorder instead
-- records frame 0 immediately in its own arming invocation (a one-frame
-- difference); keep this in mind for any future comparator work against
-- interior-recorder ss traces.
function start_ss_segment()
    ss_segment_count = (ss_segment_count or 0) + 1
    current_segment_dir_token = (ss_segment_count == 1) and "ss"
        or ("ss_" .. ss_segment_count)
    effective_output_dir = OUTPUT_DIR .. current_segment_dir_token .. "/"
    ensure_output_dir(effective_output_dir)

    started = true
    bk2_frame_offset = emu.framecount()
    trace_frame = 0
    current_ss_index = mainmemory.read_u8(SS_ADDR_SPECIAL_STAGE_INDEX)

    physics_file = io.open(effective_output_dir .. "physics.csv", "w")
    aux_file = io.open(effective_output_dir .. "aux_state.jsonl", "w")
    physics_file:write(
        "frame,input,input_p2,lag,speed_factor,track_anim,track_anim_frame,track_drawing_index,track_orientation,track_duration_timer,current_segment,player_anim_frame_timer,rings_togo_bcd,check_rings_flag,tails_control_counter,swap_positions_flag,sonic_present,sonic_ss_x,sonic_ss_x_sub,sonic_ss_y,sonic_ss_y_sub,sonic_ss_z,sonic_angle,sonic_routine,sonic_routine_secondary,sonic_status,sonic_anim,sonic_anim_frame,sonic_rings_bcd,sonic_hurt_timer,sonic_slide_timer,sonic_flip_timer,tails_present,tails_ss_x,tails_ss_x_sub,tails_ss_y,tails_ss_y_sub,tails_ss_z,tails_angle,tails_routine,tails_routine_secondary,tails_status,tails_anim,tails_anim_frame,tails_rings_bcd,tails_hurt_timer,tails_slide_timer,tails_flip_timer\n")
    physics_file:flush()
    write_ss_metadata()
    print(string.format(
        "SS segment armed at BizHawk frame %d (dir=%s, special_stage_index=%d).",
        bk2_frame_offset, current_segment_dir_token, current_ss_index))
end

-- Records one special-stage physics.csv row and advances trace_frame.
-- Minimal port of s2_ss_trace_recorder.lua's record_frame (~L588-635): SAME
-- 48-column header/row format (frame decimal, lag 0/1, everything else
-- lowercase hex -- the SS convention; do NOT reuse the level writer's %04X
-- helpers) but WITHOUT the interior recorder's event.onmemoryexecute
-- RunObjects-pass machinery (hard rule for this run port: no
-- onmemoryexecute hooks) -- state is sampled directly, once per $10 frame,
-- from read_ss_state() instead. Keeps the level segments' existing dead-
-- frame-skip semantics unchanged (this function does not touch them).
function write_ss_row()
    local state = read_ss_state()
    local sonic = state.sonic
    local tails = state.tails

    local frame_index = bk2_frame_offset + trace_frame
    local input_mask = joypad_mask_from_frame(frame_index, 1)
    local input_p2_mask = joypad_mask_from_frame(frame_index, 2)
    local lag = emu.islagged() and 1 or 0

    physics_file:write(string.format(
        "%d,%x,%x,%d,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x\n",
        trace_frame, input_mask, input_p2_mask, lag,
        state.speed_factor, state.track_anim, state.track_anim_frame,
        state.track_drawing_index, state.track_orientation,
        state.track_duration_timer, state.current_segment,
        state.player_anim_frame_timer, state.rings_togo_bcd,
        state.check_rings_flag, state.tails_control_counter,
        state.swap_positions_flag,
        sonic.present and 1 or 0, sonic.ss_x, sonic.ss_x_sub, sonic.ss_y,
        sonic.ss_y_sub, sonic.ss_z, sonic.angle, sonic.routine,
        sonic.routine_secondary, sonic.status, sonic.anim, sonic.anim_frame,
        sonic.rings_bcd, sonic.hurt_timer, sonic.slide_timer, sonic.flip_timer,
        tails.present and 1 or 0, tails.ss_x, tails.ss_x_sub, tails.ss_y,
        tails.ss_y_sub, tails.ss_z, tails.angle, tails.routine,
        tails.routine_secondary, tails.status, tails.anim, tails.anim_frame,
        tails.rings_bcd, tails.hurt_timer, tails.slide_timer, tails.flip_timer))

    if trace_frame % 60 == 0 then physics_file:flush() end
    if trace_frame % 300 == 0 then write_ss_metadata() end
    trace_frame = trace_frame + 1
end

-- Finalize the currently-armed SS segment: flush, rewrite metadata (final
-- trace_frame_count), close files, append its segments_done entry (kind
-- "special_stage", carrying special_stage_index so TraceRunManifest.Segment.
-- validate is satisfied), then reset the shared started/trace_frame state.
-- Mirrors s1_complete_run_recorder.lua:699-733 / s3k ~L5200.
function finalize_ss_segment()
    if not started then
        return
    end
    if physics_file then physics_file:flush() end
    write_ss_metadata()
    local rows = trace_frame
    print(string.format(
        "Finalised SS segment %s (special_stage_index=%d): %d rows, bk2_frame_offset=%d.",
        current_segment_dir_token, current_ss_index, rows, bk2_frame_offset))
    close_files()
    segments_done[#segments_done + 1] = {
        dir = current_segment_dir_token,
        kind = "special_stage",
        profile = "s2_special_stage",
        special_stage_index = current_ss_index,
        zone_id = 0,
        act = 0,
        bk2_frame_offset = bk2_frame_offset,
        rows = rows,
    }
    started = false
    trace_frame = 0
    current_ss_index = nil
end

-- Single end-of-run finalize funnel (port of s1_complete_run_recorder.lua
-- :745-757 / s3k ~L5822-5846). Every live termination path in run mode --
-- the top-of-function movie-done guard, the pre-arm movie-FINISHED site, the
-- non-level "left gameplay" stop, the BK2-end/FINISHED checks, and the
-- FRAME_CAP backstop -- calls this exactly once before setting
-- finished = true. `started` is true during BOTH an armed level segment AND
-- an armed SS segment (start_ss_segment sets it too), so this must NOT
-- unconditionally run the level finalize: doing so mid-detour would
-- overwrite ss/metadata.json (via the shared effective_output_dir), append a
-- bogus kind="level" entry, and leave finalize_ss_segment() as a silent
-- no-op on its `not started` guard. The explicit if/else below routes
-- correctly.
function finalize_run_end()
    if detour_active == "special_stage" then
        finalize_ss_segment()
        detour_active = nil
    elseif started then
        if physics_file then physics_file:flush() end
        write_metadata()
        append_level_segment_done(trace_frame)
        close_files()
        started = false
    end
    write_run_manifest()
end

-- Emits OUTPUT_DIR/run_manifest.json describing every segment and transition
-- recorded across the run. Run mode only -- run_id == nil skips entirely, so
-- plain-mode captures remain output-identical (contract item 7). Field
-- names match TraceRunManifest (Segment/Transition) exactly; the Lua-side
-- per-segment frame-count field is `rows`, emitted under the JSON key
-- "trace_frame_count". Inline literals (not shared S1/S3K globals) per
-- contract item 7: "9.12-s2" script version, "7B905383" (S2 World REV01
-- CRC32, per CLAUDE.md).
function write_run_manifest()
    if run_id == nil then
        return  -- plain-mode run: no manifest, output layout unchanged
    end
    local f = io.open(OUTPUT_DIR .. "run_manifest.json", "w")
    if not f then
        print("WARNING: could not open run_manifest.json for writing")
        return
    end
    -- Invariant check before writing: transition counts are bounded by
    -- boundaries, not equal to them. The checkable invariant is per-record:
    -- every record's to_segment == from_segment + 1 and
    -- to_segment <= #segments_done.
    for i, t in ipairs(transitions_done) do
        if t.to_segment ~= t.from_segment + 1 or t.to_segment > #segments_done then
            print(string.format(
                "WARNING: transition record %d has inconsistent segment indices "
                    .. "(from_segment=%d, to_segment=%d, #segments_done=%d)",
                i, t.from_segment, t.to_segment, #segments_done))
        end
    end
    f:write('{\n')
    f:write('  "run_schema": 1,\n')
    f:write('  "game": "s2",\n')
    f:write(string.format('  "run_id": %q,\n', run_id))
    f:write('  "source_bk2": "' .. json_escape(SOURCE_BK2) .. '",\n')
    f:write('  "rom_checksum": "7B905383",\n')
    f:write('  "lua_script_version": "' .. LUA_SCRIPT_VERSION .. '",\n')
    f:write('  "segments": [\n')
    for i, s in ipairs(segments_done) do
        local extra = ""
        if s.kind == "special_stage" then
            extra = string.format(', "special_stage_index": %d', s.special_stage_index)
        end
        f:write(string.format(
            '    {"dir": %q, "kind": %q, "trace_profile": %q, "bk2_frame_offset": %d, "trace_frame_count": %d, "zone_id": %d, "act": %d%s}%s\n',
            s.dir, s.kind, s.profile, s.bk2_frame_offset, s.rows, s.zone_id, s.act,
            extra, (i < #segments_done) and "," or ""))
    end
    f:write('  ],\n')
    f:write('  "transitions": [\n')
    for i, t in ipairs(transitions_done) do
        local parts = {
            string.format('"from_segment": %d', t.from_segment),
            string.format('"to_segment": %d', t.to_segment),
            string.format('"entry_kind": %q', t.entry_kind),
            string.format('"mode_change_bk2_frame": %d', t.mode_change_bk2_frame),
        }
        if t.special_bonus_entry_flag then parts[#parts+1] = string.format('"special_bonus_entry_flag": %d', t.special_bonus_entry_flag) end
        if t.saved_x_pos then parts[#parts+1] = string.format('"saved_x_pos": %d', t.saved_x_pos) end
        if t.saved_y_pos then parts[#parts+1] = string.format('"saved_y_pos": %d', t.saved_y_pos) end
        if t.last_star_post_hit then parts[#parts+1] = string.format('"last_star_post_hit": %d', t.last_star_post_hit) end
        if t.rings_before then parts[#parts+1] = string.format('"rings_before": %d', t.rings_before) end
        if t.rings_after then parts[#parts+1] = string.format('"rings_after": %d', t.rings_after) end
        if t.emeralds_before then parts[#parts+1] = string.format('"emeralds_before": %d', t.emeralds_before) end
        if t.emeralds_after then parts[#parts+1] = string.format('"emeralds_after": %d', t.emeralds_after) end
        f:write(string.format('    {%s}%s\n', table.concat(parts, ", "),
            (i < #transitions_done) and "," or ""))
    end
    f:write('  ]\n}\n')
    f:close()
    print(string.format("Wrote run_manifest.json (%d segments, %d transitions).",
        #segments_done, #transitions_done))
end

-- Build a compact summary of ALL occupied dynamic slots (16-127).
-- Returns a JSON array string: [[slot,typeId], ...] for each non-empty slot.
local function build_slot_dump()
    local entries = {}
    for slot = OBJ_DYNAMIC_START, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_id = mainmemory.read_u8(addr)
        if obj_id ~= 0 then
            entries[#entries + 1] = string.format("[%d,\"0x%02X\"]", slot, obj_id)
        end
    end
    return "[" .. table.concat(entries, ",") .. "]"
end

-- Dump the 64-byte SST slot at `addr` as a JSON object of byte fields,
-- keyed by raw offset ("off_00".."off_3F"), plus a handful of semantic
-- word aliases for readability. The engine side composes any word it
-- needs from the consecutive byte entries, so every per-object variable
-- at $2A-$3F is recoverable without per-object Lua knowledge.
local function build_object_fields(addr)
    local parts = {}
    -- Raw bytes 0x00..0x3F (64 bytes). The Java parser composes big-endian
    -- words on demand from consecutive byte offsets.
    for off = 0, OBJ_SLOT_SIZE - 1 do
        local val = mainmemory.read_u8(addr + off)
        parts[#parts + 1] = string.format('"off_%02X":"0x%02X"', off, val)
    end
    -- Semantic word aliases for the universal SST header (helps humans
    -- reading the aux file; also lets the engine skip byte composition
    -- for hot fields).
    parts[#parts + 1] = string.format('"x_pos":"0x%04X"',
        mainmemory.read_u16_be(addr + OFF_X_POS))
    parts[#parts + 1] = string.format('"x_sub":"0x%04X"',
        mainmemory.read_u16_be(addr + OFF_X_SUB))
    parts[#parts + 1] = string.format('"y_pos":"0x%04X"',
        mainmemory.read_u16_be(addr + OFF_Y_POS))
    parts[#parts + 1] = string.format('"y_sub":"0x%04X"',
        mainmemory.read_u16_be(addr + OFF_Y_SUB))
    local x_vel_raw = mainmemory.read_s16_be(addr + OFF_X_VEL)
    if x_vel_raw < 0 then x_vel_raw = x_vel_raw + 0x10000 end
    parts[#parts + 1] = string.format('"x_vel":"0x%04X"', x_vel_raw)
    local y_vel_raw = mainmemory.read_s16_be(addr + OFF_Y_VEL)
    if y_vel_raw < 0 then y_vel_raw = y_vel_raw + 0x10000 end
    parts[#parts + 1] = string.format('"y_vel":"0x%04X"', y_vel_raw)
    -- Semantic byte aliases (duplicate with off_XX but readable).
    parts[#parts + 1] = string.format('"id":"0x%02X"',
        mainmemory.read_u8(addr))
    parts[#parts + 1] = string.format('"render_flags":"0x%02X"',
        mainmemory.read_u8(addr + 0x01))
    parts[#parts + 1] = string.format('"status":"0x%02X"',
        mainmemory.read_u8(addr + OFF_STATUS))
    parts[#parts + 1] = string.format('"routine":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ROUTINE))
    parts[#parts + 1] = string.format('"routine_secondary":"0x%02X"',
        mainmemory.read_u8(addr + 0x25))
    parts[#parts + 1] = string.format('"mapping_frame":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ANIM_FRAME_DISP))
    parts[#parts + 1] = string.format('"anim":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ANIM_ID))
    parts[#parts + 1] = string.format('"anim_frame":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ANIM_FRAME))
    parts[#parts + 1] = string.format('"anim_frame_timer":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ANIM_TIMER))
    parts[#parts + 1] = string.format('"subtype":"0x%02X"',
        mainmemory.read_u8(addr + 0x28))
    return "{" .. table.concat(parts, ",") .. "}"
end

-- Emit one object_state_snapshot event per occupied SST slot at
-- detection time (before trace frame 0). The engine uses these during
-- trace replay to hydrate spawned object state machines so they match
-- the ROM's pre-trace progress (e.g. Coconuts mid-climb).
local function write_object_snapshots()
    if not aux_file then return end
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local count = 0
    -- Scan slots 1-127. Skip 0 (Sonic) since the engine hydrates the main
    -- player from metadata.start_x/start_y directly. Slot 1 (Tails/sidekick)
    -- is included so replay can restore the sidekick's pre-trace SST state.
    for slot = 1, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_id = mainmemory.read_u8(addr)
        if obj_id ~= 0 then
            write_aux(string.format(
                '{"frame":-1,"vfc":%d,"event":"object_state_snapshot",'
                .. '"slot":%d,"object_type":"0x%02X","fields":%s}',
                vfc, slot, obj_id, build_object_fields(addr)))
            count = count + 1
        end
    end
    print(string.format("Wrote %d pre-trace object_state_snapshot events.", count))
end

local function write_player_history_snapshot()
    if not aux_file then return end
    local x_entries = {}
    local y_entries = {}
    local input_entries = {}
    local status_entries = {}
    for i = 0, 63 do
        local offset = i * 4
        x_entries[#x_entries + 1] = tostring(mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_BUF + offset))
        y_entries[#y_entries + 1] = tostring(mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_BUF + offset + 2))
        input_entries[#input_entries + 1] = tostring(mainmemory.read_u16_be(ADDR_SONIC_STAT_RECORD_BUF + offset))
        status_entries[#status_entries + 1] = tostring(mainmemory.read_u8(ADDR_SONIC_STAT_RECORD_BUF + offset + 2))
    end

    write_aux(string.format(
        '{"frame":-1,"vfc":%d,"event":"player_history_snapshot","history_pos":%d,'
            .. '"x_history":[%s],"y_history":[%s],"input_history":[%s],"status_history":[%s]}',
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_INDEX) & 0xFF,
        table.concat(x_entries, ","),
        table.concat(y_entries, ","),
        table.concat(input_entries, ","),
        table.concat(status_entries, ",")))
end

local function write_tails_cpu_snapshot()
    if not aux_file then return end

    write_aux(string.format(
        '{"frame":-1,"vfc":%d,"event":"cpu_state_snapshot","character":"tails",'
            .. '"control_counter":%d,"respawn_counter":%d,"cpu_routine":%d,'
            .. '"target_x":"0x%04X","target_y":"0x%04X","interact_id":"0x%02X","jumping":%d}',
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        mainmemory.read_u16_be(ADDR_TAILS_CONTROL_COUNTER),
        mainmemory.read_u16_be(ADDR_TAILS_RESPAWN_COUNTER),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_ROUTINE),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_X),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_Y),
        mainmemory.read_u8(ADDR_TAILS_INTERACT_ID),
        mainmemory.read_u8(ADDR_TAILS_CPU_JUMPING)))
end

local function write_tails_cpu_per_frame()
    if not aux_file then return end

    local delay = (0x10 << 2) + 4
    local record_index = mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_INDEX) & 0xFF
    local delayed_index = (record_index - delay) & 0xFF

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"cpu_state","character":"tails",'
            .. '"interact":"0x%04X","idle_timer":%d,"flight_timer":%d,'
            .. '"cpu_routine":%d,"target_x":"0x%04X","target_y":"0x%04X",'
            .. '"auto_fly_timer":0,"auto_jump_flag":%d,'
            .. '"ctrl2_held":"0x%02X","ctrl2_pressed":"0x%02X",'
            .. '"ctrl2_raw_held":"0x%02X","ctrl1_logical":"0x%04X",'
            .. '"pos_table_index":"0x%02X","delayed_index":"0x%02X",'
            .. '"delayed_x":"0x%04X","delayed_y":"0x%04X",'
            .. '"delayed_input":"0x%04X","delayed_status":"0x%02X",'
            .. '"tails_status":"0x%02X","tails_interact":"0x%02X","tails_inertia":"0x%04X"}',
        trace_frame,
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        mainmemory.read_u8(ADDR_TAILS_INTERACT_ID),
        mainmemory.read_u16_be(ADDR_TAILS_CONTROL_COUNTER),
        mainmemory.read_u16_be(ADDR_TAILS_RESPAWN_COUNTER),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_ROUTINE),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_X),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_Y),
        mainmemory.read_u8(ADDR_TAILS_CPU_JUMPING),
        mainmemory.read_u8(ADDR_CTRL2_LOGICAL),
        mainmemory.read_u8(ADDR_CTRL2_LOGICAL + 1),
        mainmemory.read_u8(ADDR_CTRL2),
        mainmemory.read_u16_be(ADDR_CTRL1_DUP),
        record_index,
        delayed_index,
        mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_BUF + delayed_index),
        mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_BUF + delayed_index + 2),
        mainmemory.read_u16_be(ADDR_SONIC_STAT_RECORD_BUF + delayed_index),
        mainmemory.read_u8(ADDR_SONIC_STAT_RECORD_BUF + delayed_index + 2),
        mainmemory.read_u8(SIDEKICK_BASE + OFF_STATUS),
        mainmemory.read_u8(SIDEKICK_BASE + OFF_STAND_ON_OBJ),
        mainmemory.read_u16_be(SIDEKICK_BASE + OFF_INERTIA)))
end

-- Scan all object slots (1-127). Log appearances, disappearances, proximity,
-- and emit a full slot_dump when any dynamic object appears.
local function scan_objects(subjects)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local any_appeared = false

    for slot = 1, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_id = mainmemory.read_u8(addr)

        local prev_id = known_objects[slot] or 0

        -- Object appeared in this slot
        if obj_id ~= 0 and obj_id ~= prev_id then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"object_appeared","slot":%d,"object_type":"0x%02X","x":"0x%04X","y":"0x%04X"}',
                trace_frame, vfc, slot, obj_id, obj_x, obj_y))
            any_appeared = true
        end

        -- Object disappeared from this slot
        if obj_id == 0 and prev_id ~= 0 then
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"object_removed","slot":%d,"object_type":"0x%02X"}',
                trace_frame, vfc, slot, prev_id))
        end

        -- Proximity check: log active objects near Sonic and Tails every frame.
        -- Skip the subject's own SST slot so Tails doesn't spam near-self events.
        if obj_id ~= 0 then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local obj_y_sub = mainmemory.read_u16_be(addr + OFF_Y_SUB)
            local obj_y_vel = mainmemory.read_u16_be(addr + OFF_Y_VEL)
            local obj_status = mainmemory.read_u8(addr + OFF_STATUS)
            local obj_routine = mainmemory.read_u8(addr + OFF_ROUTINE)
            if obj_id == 0xB2 then
                write_aux(string.format(
                    '{"frame":%d,"vfc":%d,"event":"s2_tornado_state","slot":%d,'
                    .. '"x":"0x%04X","y":"0x%04X","y_sub":"0x%04X","y_vel":"0x%04X",'
                    .. '"routine":"0x%02X","routine_secondary":"0x%02X","status_byte":"0x%02X",'
                    .. '"objoff_2e":"0x%02X","objoff_2f":"0x%02X","objoff_30":"0x%02X","objoff_31":"0x%02X"}',
                    trace_frame, vfc, slot,
                    obj_x, obj_y, obj_y_sub, obj_y_vel,
                    obj_routine, mainmemory.read_u8(addr + 0x25), obj_status,
                    mainmemory.read_u8(addr + 0x2E),
                    mainmemory.read_u8(addr + 0x2F),
                    mainmemory.read_u8(addr + 0x30),
                    mainmemory.read_u8(addr + 0x31)))
            end
            for _, subject in ipairs(subjects) do
                if subject.present ~= 0 and slot ~= subject.slot then
                    local dx = math.abs(obj_x - subject.x)
                    local dy = math.abs(obj_y - subject.y)
                    if dx <= OBJECT_PROXIMITY and dy <= OBJECT_PROXIMITY then
                        write_aux(string.format(
                            '{"frame":%d,"vfc":%d,"event":"object_near","character":"%s","slot":%d,"type":"0x%02X",'
                            .. '"x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"}',
                            trace_frame, vfc, subject.character, slot, obj_id, obj_x, obj_y,
                            obj_routine, obj_status))
                    end
                end
            end
        end

        known_objects[slot] = obj_id
    end

    -- Emit a full dynamic-slot snapshot whenever any object appeared this frame.
    -- This lets us compare the engine's slot allocation against ROM's FindFreeObj.
    if any_appeared then
        local dump = build_slot_dump()
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"slot_dump","slots":%s}',
            trace_frame, vfc, dump))
    end
end

local function write_state_snapshot(character, base)
    if mainmemory.read_u8(base) == 0 then
        return
    end

    local ctrl_lock = mainmemory.read_u16_be(base + OFF_CTRL_LOCK)
    local anim_id = mainmemory.read_u8(base + OFF_ANIM_ID)
    local status = mainmemory.read_u8(base + OFF_STATUS)
    local routine = mainmemory.read_u8(base + OFF_ROUTINE)
    local y_radius = mainmemory.read_s8(base + OFF_RADIUS_Y)
    local x_radius = mainmemory.read_s8(base + OFF_RADIUS_X)
    local top_solid = mainmemory.read_u8(base + OFF_TOP_SOLID_BIT)
    local lrb_solid = mainmemory.read_u8(base + OFF_LRB_SOLID_BIT)
    local raw_input = mainmemory.read_u8(ADDR_CTRL1)
    local logical_input = mainmemory.read_u8(ADDR_CTRL1_DUP)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"state_snapshot","character":"%s","control_locked":%s,"move_lock":"0x%04X","anim_id":%d,'
        .. '"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,'
        .. '"top_solid_bit":"0x%02X","lrb_solid_bit":"0x%02X",'
        .. '"raw_input":"0x%02X","raw_input_mask":"0x%02X","logical_input":"0x%02X","logical_input_mask":"0x%02X",'
        .. '"on_object":%s,"pushing":%s,"underwater":%s,'
        .. '"roll_jumping":%s}',
        trace_frame,
        vfc,
        character,
        ctrl_lock > 0 and "true" or "false",
        ctrl_lock,
        anim_id,
        status,
        routine,
        y_radius,
        x_radius,
        top_solid,
        lrb_solid,
        raw_input,
        rom_joypad_to_mask(raw_input),
        logical_input,
        rom_joypad_to_mask(logical_input),
        ((status & STATUS_ON_OBJECT) ~= 0) and "true" or "false",
        ((status & STATUS_PUSHING) ~= 0) and "true" or "false",
        ((status & STATUS_UNDERWATER) ~= 0) and "true" or "false",
        ((status & STATUS_ROLL_JUMP) ~= 0) and "true" or "false"
    ))
end

local function check_mode_changes(character, base, state, status, routine)
    if mainmemory.read_u8(base) == 0 then
        state.status = 0
        state.routine = 0
        state.ctrl_lock = 0
        return
    end

    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    local was_air = (state.status & STATUS_IN_AIR) ~= 0
    local is_air = (status & STATUS_IN_AIR) ~= 0
    if was_air ~= is_air then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"air","from":%d,"to":%d}',
            trace_frame, vfc, character, was_air and 1 or 0, is_air and 1 or 0))
        write_state_snapshot(character, base)
    end

    local was_rolling = (state.status & STATUS_ROLLING) ~= 0
    local is_rolling = (status & STATUS_ROLLING) ~= 0
    if was_rolling ~= is_rolling then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"rolling","from":%d,"to":%d}',
            trace_frame, vfc, character, was_rolling and 1 or 0, is_rolling and 1 or 0))
    end

    local was_on_obj = (state.status & STATUS_ON_OBJECT) ~= 0
    local is_on_obj = (status & STATUS_ON_OBJECT) ~= 0
    if was_on_obj ~= is_on_obj then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"on_object","from":%d,"to":%d}',
            trace_frame, vfc, character, was_on_obj and 1 or 0, is_on_obj and 1 or 0))
    end

    local ctrl_lock = mainmemory.read_u16_be(base + OFF_CTRL_LOCK)
    local was_locked = state.ctrl_lock > 0
    local is_locked = ctrl_lock > 0
    if was_locked ~= is_locked then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"control_locked","from":%d,"to":%d}',
            trace_frame, vfc, character, was_locked and 1 or 0, is_locked and 1 or 0))
    end
    state.ctrl_lock = ctrl_lock

    -- Routine transition detection (S2 obRoutine raw values: 0=init, 2=control,
    -- 4=hurt, 6=death).
    -- Emit a rich event with full Sonic state and the object Sonic is standing on
    -- (if any). Especially valuable for hurt transitions (2→4).
    if routine ~= state.routine then
        local stand_on_obj = mainmemory.read_u8(base + OFF_STAND_ON_OBJ)
        local sonic_x = mainmemory.read_u16_be(base + OFF_X_POS)
        local sonic_y = mainmemory.read_u16_be(base + OFF_Y_POS)
        local sonic_xvel = mainmemory.read_s16_be(base + OFF_X_VEL)
        local sonic_yvel = mainmemory.read_s16_be(base + OFF_Y_VEL)
        local sonic_inertia = mainmemory.read_s16_be(base + OFF_INERTIA)

        -- If Sonic is standing on an object, read that object's type and position
        local obj_context = ""
        if stand_on_obj > 0 and stand_on_obj < OBJ_TOTAL_SLOTS then
            local obj_addr = OBJ_TABLE_START + (stand_on_obj * OBJ_SLOT_SIZE)
            local obj_id = mainmemory.read_u8(obj_addr)
            local obj_x = mainmemory.read_u16_be(obj_addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(obj_addr + OFF_Y_POS)
            local obj_routine = mainmemory.read_u8(obj_addr + OFF_ROUTINE)
            obj_context = string.format(
                ',"stand_obj_slot":%d,"stand_obj_type":"0x%02X","stand_obj_x":"0x%04X",'
                .. '"stand_obj_y":"0x%04X","stand_obj_routine":"0x%02X"',
                stand_on_obj, obj_id, obj_x, obj_y, obj_routine)
        end

        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"routine_change","character":"%s","from":"0x%02X","to":"0x%02X",'
            .. '"x":"0x%04X","y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,'
            .. '"status":"0x%02X","stand_on_obj":%d%s}',
            trace_frame, vfc, character, state.routine, routine,
            sonic_x, sonic_y, sonic_xvel, sonic_yvel, sonic_inertia,
            status, stand_on_obj, obj_context))

        -- On hurt/death transitions, also emit a full state snapshot for maximum context.
        if routine == ROUTINE_HURT or routine == ROUTINE_DEATH then
            write_state_snapshot(character, base)
        end
    end
    state.routine = routine
    state.status = status
end

-----------------
--- Main Loop ---
-----------------

local function on_frame_end()
    local game_mode = mainmemory.read_u8(ADDR_GAME_MODE)

    -- 4b. Top-of-function movie-done guard, RUN MODE ONLY (port shape from
    -- s1_complete_run_recorder.lua ~L1163-1182). PLAIN MODE MUST NOT REACH
    -- ANY CODE IN THIS BLOCK -- it is gated on `run_id ~= nil` as the very
    -- first condition. Rationale: the existing BK2-end/FINISHED checks
    -- further down (~L1595-1612) sit BELOW the detour branch's `return`s
    -- added below, so without this guard a movie ending mid-$10 (inside the
    -- SS detour) would keep writing ss rows until FRAME_CAP instead of
    -- stopping promptly. Uses the SAME BK2_FRAME_COUNT-override "effective"
    -- movie length as the existing armed check -- movie.length() under-
    -- reports in chromeless runs, so a raw-length guard here would silently
    -- truncate the ss tail or seg2 while still writing a valid-looking
    -- manifest, invisible to a plain-mode inspection.
    if run_id ~= nil and HEADLESS and movie.isloaded() then
        local frame_now = emu.framecount()
        local movie_length = movie.length()
        if BK2_FRAME_COUNT ~= nil and BK2_FRAME_COUNT > movie_length then
            movie_length = BK2_FRAME_COUNT
        end
        local movie_done = (movie_length > 0 and frame_now >= movie_length)
            or movie.mode() == "FINISHED"
        if movie_done then
            print(string.format(
                "Run-mode movie-done guard fired at emu frame %d (effective movie length %d). Finalising.",
                frame_now, movie_length))
            finalize_run_end()
            finished = true
            return
        end
    end

    -- Stage-detour state machine, RUN MODE ONLY (gated on `run_id ~= nil` as
    -- the first condition of Block 1 below; Block 2's own gate,
    -- `detour_active == "special_stage"`, can only be true after Block 1 set
    -- it, so Block 2 is transitively plain-mode-unreachable too). Port of
    -- s1_complete_run_recorder.lua ~L1201-1244, S2 constants.
    --
    -- PLACEMENT IS LOAD-BEARING: this file's on_frame_end is structurally
    -- INVERTED vs S1/S3K -- its `if not started` arm-gate block comes FIRST
    -- (below), with an unconditional `return` at the end of that block --
    -- so the detour machine must sit ABOVE that gate (here, right after the
    -- 4b guard) for two reasons: (a) continuation $10 frames must reach
    -- Block 1 while `started` is still true, and (b) the Block 2 exit
    -- fall-through must land in the `if not started` gate below so it can
    -- safely re-arm the return level segment.
    --
    -- Block 1: SS entry/continuation. Gated on `started` (true both while a
    -- level segment is armed at the $0C->$10 edge AND on every SS
    -- continuation frame, since start_ss_segment() also sets `started =
    -- true`) so a movie beginning mid-$10 with nothing armed cannot produce
    -- a bogus from_segment=-1 transition.
    if run_id ~= nil and started and game_mode == GAMEMODE_SPECIAL_STAGE then
        if detour_active ~= "special_stage" then
            -- ENTRY: finalize the armed level segment first (flush + write_
            -- metadata + append_level_segment_done + close_files), THEN push
            -- the starpost_special transition with exact indices computed
            -- AFTER the finalize (so #segments_done already counts the
            -- just-finished level segment), THEN arm the SS segment once.
            if physics_file then physics_file:flush() end
            write_metadata()
            append_level_segment_done(trace_frame)
            close_files()
            started = false
            trace_frame = 0
            transitions_done[#transitions_done + 1] = {
                from_segment = #segments_done - 1,
                to_segment = #segments_done,
                entry_kind = "starpost_special",
                mode_change_bk2_frame = emu.framecount(),
                special_bonus_entry_flag = mainmemory.read_u8(ADDR_BIGRING_FLAG),
                saved_x_pos = mainmemory.read_u16_be(ADDR_SAVED_X_POS),
                saved_y_pos = mainmemory.read_u16_be(ADDR_SAVED_Y_POS),
                last_star_post_hit = mainmemory.read_u8(ADDR_LAST_STAR_POST_HIT),
                rings_before = mainmemory.read_u16_be(ADDR_RING_COUNT),
                emeralds_before = mainmemory.read_u8(ADDR_EMERALDS),
            }
            start_ss_segment()
            detour_active = "special_stage"
            local tx = transitions_done[#transitions_done]
            -- VERIFY-ON-FIRST-CAPTURE: print every transition field value.
            print(string.format(
                "S2 special-stage detour at bk2 frame %d (special_bonus_entry_flag=0x%02X, "
                    .. "saved=(0x%04X,0x%04X), last_star_post_hit=%d, rings_before=%d, emeralds_before=%d).",
                tx.mode_change_bk2_frame, tx.special_bonus_entry_flag,
                tx.saved_x_pos, tx.saved_y_pos, tx.last_star_post_hit,
                tx.rings_before, tx.emeralds_before))
            return
        end
        -- CONTINUATION: still inside the SS detour. The normal level-row
        -- path below (and the non-level re-arm branch) is unreachable for
        -- $10 frames -- this returns first, so the non-level branch never
        -- double-finalizes the same segment.
        write_ss_row()
        return
    end
    if detour_active == "special_stage" then
        -- First non-$10 frame after the detour (results tally trailing off
        -- game_mode $10, or the return load-handoff): finalize the SS
        -- segment here, BEFORE the level-family checks below, so it always
        -- closes exactly once regardless of what mode follows. Also resets
        -- the level-tracking state (checkpoints/known-objects/prev-state/
        -- etc.) WITHOUT deleting the just-finalized ss/ segment's files
        -- (contract item 4; reset_recording_state_keep_files is the
        -- never-os.remove half of the split reset_recording_state).
        finalize_ss_segment()
        detour_active = nil
        reset_recording_state_keep_files()
        -- fall through: non-$10 frames (results/fade under $0C or otherwise)
        -- are manifest-only until the level arm gate below re-arms.
    end

    if not started then
        if finished then return end
        if skipping_segment then
            if game_mode ~= GAMEMODE_LEVEL then
                if is_level_gated_reset_aware_profile() and skipped_segment_zone_name == "ehz" then
                    print("Skipped EHZ debug/menu bootstrap segment without counting it as a route segment.")
                else
                    print(string.format("Skipped gameplay segment %d.", gameplay_segment_index))
                    gameplay_segment_index = gameplay_segment_index + 1
                end
                skipped_segment_zone_name = nil
                skipping_segment = false
            end
            return
        end
        if HEADLESS and movie.isloaded() and movie.mode() == "FINISHED" then
            print(string.format(
                "Movie finished before gameplay segment %d became recordable. Finalising without trace rows.",
                TARGET_GAMEPLAY_SEGMENT))
            -- Run mode: this can fire mid-run between segments (e.g. the
            -- movie ends during the post-SS reload before the return level
            -- re-arms) -- funnel through finalize_run_end() or the manifest
            -- (and any already-recorded segments/transitions) is lost.
            -- Plain mode: unchanged (finished=true only).
            if run_id ~= nil then
                finalize_run_end()
            end
            finished = true
            return
        end
        -- Start when: level gameplay active AND player control lock timer is 0.
        -- The control lock timer (move_lock, word at MainCharacter+$2E) is set during the title
        -- card and counts down to 0 when Sonic can first move. Using the player object's
        -- lock timer is correct; the old raw-input check waited for "no buttons held"
        -- which delayed recording if the player was already pressing a direction.
        local ctrl_lock_timer = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
        if game_mode == GAMEMODE_LEVEL and ctrl_lock_timer == 0 then
            -- Segment-skip note (defensive only, contract item 4):
            -- gameplay_segment_index increments ONLY in the skipping_segment
            -- branch above, never when a recorded segment finalizes, so the
            -- return level segment cannot actually be swallowed by the
            -- TARGET_GAMEPLAY_SEGMENT check below as written today. Bypass
            -- it anyway once run mode has already armed one segment, purely
            -- as a guard against a future change to the skip-path
            -- bookkeeping -- not a live hazard as written.
            local bypass_target_check = run_id ~= nil and level_segment_count > 0
            if not bypass_target_check and gameplay_segment_index < TARGET_GAMEPLAY_SEGMENT then
                print(string.format(
                    "Skipping gameplay segment %d while seeking target segment %d.",
                    gameplay_segment_index, TARGET_GAMEPLAY_SEGMENT))
                local skip_zone_id = mainmemory.read_u8(ADDR_ZONE)
                skipped_segment_zone_name = ZONE_NAMES[skip_zone_id] or string.format("unknown_%02x", skip_zone_id)
                skipping_segment = true
                return
            end
            started = true
            -- emu.framecount() returns the frame that just completed. Since we
            -- skip the detection frame (return below without recording), frame 0
            -- is recorded one emu.frameadvance() later. BK2 input for that frame
            -- is at index emu.framecount() (not -1), because the advance runs
            -- one more frame before on_frame_end() captures frame 0.
            bk2_frame_offset = emu.framecount()
            start_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
            start_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
            start_rng_seed = mainmemory.read_u32_be(ADDR_RANDOM)

            -- Capture zone/act NOW at start, not at end when RAM may have advanced
            start_rom_zone_id = mainmemory.read_u8(ADDR_ZONE)
            start_zone_id = engine_zone_for_rom_zone(start_rom_zone_id)
            start_act = mainmemory.read_u8(ADDR_ACT)
            start_zone_name = ZONE_NAMES[start_rom_zone_id] or string.format("unknown_%02x", start_rom_zone_id)

            -- Run mode (contract item 2): redirect this segment's file opens
            -- into a numbered per-segment subdir under OUTPUT_DIR.
            -- level_segment_count counts LEVEL arms only -- the ss segment
            -- does not consume a number (#segments_done would wrongly yield
            -- seg3_ for this return level, since the ss entry sits between
            -- the two level entries in segments_done). Plain mode never
            -- reassigns effective_output_dir, so it stays == OUTPUT_DIR for
            -- the whole run and every file open below is unaffected.
            if run_id ~= nil then
                level_segment_count = level_segment_count + 1
                current_segment_dir_token = string.format("seg%d_%s%d", level_segment_count,
                    start_zone_name, apparent_act_for(start_rom_zone_id, start_act) + 1)
                effective_output_dir = OUTPUT_DIR .. current_segment_dir_token .. "/"
                ensure_output_dir(effective_output_dir)

                -- Post-SS re-arm (contract item 4): if the just-finished
                -- segment was the special stage, THIS arm is that stage's
                -- exit boundary. Indices are exact here: the SS-entry
                -- finalize above already appended the from-segment (the
                -- just-finished level) to segments_done, and
                -- finalize_ss_segment() appended the ss segment itself; this
                -- arm has not yet pushed the return level segment, so at
                -- this point segments_done == [level, ss] (#segments_done ==
                -- 2) and the push below yields from_segment=1,
                -- to_segment=2, matching the Task 2 fixture.
                if #segments_done > 0 and segments_done[#segments_done].kind == "special_stage" then
                    local exit_frame = emu.framecount()
                    local rings_after = mainmemory.read_u16_be(ADDR_RING_COUNT)
                    local emeralds_after = mainmemory.read_u8(ADDR_EMERALDS)
                    transitions_done[#transitions_done + 1] = {
                        from_segment = #segments_done - 1,
                        to_segment = #segments_done,
                        entry_kind = "stage_exit",
                        mode_change_bk2_frame = exit_frame,
                        -- ROM zeroes ring/emerald tracking on the level
                        -- reload that follows a special stage; rings_after
                        -- will read 0 here -- record the truth, per contract
                        -- item 4.
                        rings_after = rings_after,
                        emeralds_after = emeralds_after,
                    }
                    -- VERIFY-ON-FIRST-CAPTURE: print the transition field values.
                    print(string.format(
                        "S2 stage_exit transition at bk2 frame %d (rings_after=%d, emeralds_after=%d).",
                        exit_frame, rings_after, emeralds_after))
                end
            end

            open_files()
            -- Write metadata immediately so it exists even if the process is killed
            write_metadata()
            -- Schema v4: capture full SST state at the instant gameplay begins
            -- but before trace frame 0 is recorded. The engine hydrates object
            -- state machines from these snapshots so they mirror the ROM's
            -- pre-trace progress (title-card + level-init iterations).
            write_player_history_snapshot()
            write_tails_cpu_snapshot()
            write_object_snapshots()
            local start_apparent_act = apparent_act_for(start_rom_zone_id, start_act)
            emit_zone_act_state(0, start_rom_zone_id, start_zone_id, start_act, start_apparent_act, game_mode)
            emit_checkpoint_once(0, "gameplay_start", start_rom_zone_id, start_zone_id, start_act, start_apparent_act, game_mode, nil)
            print(string.format("Trace recording started at BizHawk frame %d, segment %d, zone %s act %d, pos (%04X, %04X)",
                bk2_frame_offset, gameplay_segment_index, start_zone_name, start_apparent_act + 1, start_x, start_y))
            if movie.isloaded() then
                print(string.format("Movie length: %d frames", movie.length()))
            end
        end
        -- Return without recording frame 0. The next emu.frameadvance() runs
        -- one frame of movement, and the NEXT on_frame_end() call writes
        -- frame 0 with post-movement state. This avoids a "dead frame"
        -- where input is present but speeds are 0 (ROM hasn't processed
        -- Sonic's movement yet on the frame where controls first unlock).
        return
    end

    if game_mode ~= GAMEMODE_LEVEL then
        if is_level_gated_reset_aware_profile() and start_zone_name == "ehz" then
            print(string.format(
                "level_gated_reset_aware: detected EHZ debug/menu exit at trace frame %d. Discarding and re-arming.",
                trace_frame))
            reset_recording_state()
            return
        end
        print("Left level gameplay at trace frame " .. trace_frame .. ". Finalising.")
        -- Run mode: game_mode reaching $10 (the SS detour) is already
        -- intercepted by Block 1 above while `started`, so this branch only
        -- fires here for a genuinely different non-level mode (results,
        -- game over, pause, etc.) -- a real stop, now funneled through
        -- finalize_run_end() so the manifest captures whatever segments/
        -- transitions were already recorded. Plain mode: unchanged
        -- (finished=true only).
        if run_id ~= nil then
            finalize_run_end()
        end
        finished = true
        return
    end

    -- Stop exactly when the trace would need an input frame past the end of
    -- the loaded BK2. BizHawk's movie mode can lag behind in chromeless runs,
    -- which lets the recorder append no-input tail frames that replay cannot
    -- consume later.
    if HEADLESS and movie.isloaded() then
        local movie_length = movie.length()
        if BK2_FRAME_COUNT ~= nil and BK2_FRAME_COUNT > movie_length then
            movie_length = BK2_FRAME_COUNT
        end
        if movie_length > 0 and (bk2_frame_offset + trace_frame) >= movie_length then
            print(string.format(
                "Reached BK2 end at trace frame %d (bk2 offset %d, movie length %d). Finalising.",
                trace_frame, bk2_frame_offset, movie_length))
            -- Shadowed in run mode by the 4b top-of-function guard (which
            -- fires strictly earlier on the same effective-length
            -- predicate) -- funneled through finalize_run_end() anyway,
            -- belt-and-braces (per the S1 run-port review lesson). Plain
            -- mode: unchanged (finished=true only).
            if run_id ~= nil then
                finalize_run_end()
            end
            finished = true
            return
        end
        if movie.mode() == "FINISHED" then
            print(string.format(
                "Movie playback finished at trace frame %d (emu frame %d). Finalising.",
                trace_frame, emu.framecount()))
            -- Shadowed dead code in run mode (see comment above). Plain
            -- mode: unchanged (finished=true only).
            if run_id ~= nil then
                finalize_run_end()
            end
            finished = true
            return
        end
    end

    emit_current_zone_act_state(trace_frame, game_mode)

    -- Primary physics state
    local x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
    local y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
    local x_sub = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_SUB)
    local y_sub = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_SUB)
    local x_speed = read_speed(PLAYER_BASE, OFF_X_VEL)
    local y_speed = read_speed(PLAYER_BASE, OFF_Y_VEL)
    local g_speed = read_speed(PLAYER_BASE, OFF_INERTIA)
    local angle = mainmemory.read_u8(PLAYER_BASE + OFF_ANGLE)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local routine = mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE)
    local animation_id = mainmemory.read_u8(PLAYER_BASE + OFF_ANIM_ID)
    local mapping_frame = mainmemory.read_u8(PLAYER_BASE + OFF_ANIM_FRAME_DISP)

    -- Camera position (pixel words from 32-bit values)
    local camera_x = mainmemory.read_u16_be(ADDR_CAMERA_X)
    local camera_y = mainmemory.read_u16_be(ADDR_CAMERA_Y)

    -- Ring count
    local rings = mainmemory.read_u16_be(ADDR_RING_COUNT)

    local air = (status & STATUS_IN_AIR) ~= 0
    local rolling = (status & STATUS_ROLLING) ~= 0
    local ground_mode = air and 0 or angle_to_ground_mode(angle)

    -- v9.3-s2: derive CSV `input` column from the BK2 movie directly so the
    -- recorded value perfectly matches what AbstractTraceReplayTest's BK2
    -- reader will see during validation. ROM-side $FFF604 (Ctrl_1_Held) is
    -- updated by Read_Joypads which only runs inside specific V-int
    -- subroutines; on lag frames and during long V-int paths in SCZ/OOZ/ARZ
    -- end-of-act windows it can lag the BK2 by several frames, producing
    -- spurious "Input alignment error" failures.
    --
    -- raw_input still captures ROM-side $FFF604 for the state_snapshot aux
    -- diagnostic; only the CSV `input` column switched to BK2-derived.
    local raw_input = mainmemory.read_u8(ADDR_CTRL1)
    local input_mask = bk2_input_mask(raw_input, trace_frame)

    -- Format helper for unsigned 16-bit hex
    local function uhex(val)
        if val < 0 then return val + 0x10000 end
        return val
    end

    -- gameplay_frame_counter ticks only when Level_MainLoop completes.
    local gameplay_frame_counter = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    -- standonobject: SST slot index of object Sonic is standing on (0 = none)
    local stand_on_obj = mainmemory.read_u8(PLAYER_BASE + OFF_STAND_ON_OBJ)

    -- vblank_counter ticks every VBlank. Sonic 2 does not expose a dedicated
    -- lag counter, so write 0 as a diagnostic placeholder in schema v3.
    local vblank_counter = mainmemory.read_u16_be(ADDR_VBLA_WORD)
    local lag_counter = 0
    local sidekick = read_character_trace_state(SIDEKICK_BASE)
    if sidekick.present ~= 0 then
        recorded_sidekick_present = true
    end

    -- v7 CSV: shared execution counters plus symmetric Player/Sidekick state blocks.
    physics_file:write(string.format(
        "%04X,%04X,%04X,%04X,%04X,%04X,%04X,%04X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X,"
            .. "%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X\n",
        trace_frame, input_mask,
        camera_x, camera_y,
        rings,
        gameplay_frame_counter,
        vblank_counter,
        lag_counter,
        1,
        x,
        y,
        uhex(x_speed),
        uhex(y_speed),
        uhex(g_speed),
        angle,
        air and 1 or 0,
        rolling and 1 or 0,
        ground_mode,
        x_sub,
        y_sub,
        routine,
        status,
        stand_on_obj,
        animation_id,
        mapping_frame,
        sidekick.present,
        sidekick.x,
        sidekick.y,
        uhex(sidekick.x_speed),
        uhex(sidekick.y_speed),
        uhex(sidekick.g_speed),
        sidekick.angle,
        sidekick.air,
        sidekick.rolling,
        sidekick.ground_mode,
        sidekick.x_sub,
        sidekick.y_sub,
        sidekick.routine,
        sidekick.status,
        sidekick.stand_on_obj,
        sidekick.animation_id,
        sidekick.mapping_frame))
    -- Flush periodically instead of every frame to reduce I/O overhead.
    -- Also update metadata every 300 frames (~5 sec) so a killed process
    -- still has a valid (if slightly stale) metadata.json.
    if trace_frame % 60 == 0 then
        physics_file:flush()
    end
    if trace_frame % 300 == 0 then
        write_metadata()
    end

    check_mode_changes("sonic", PLAYER_BASE, prev_character_state.sonic, status, routine)
    check_mode_changes("tails", SIDEKICK_BASE, prev_character_state.tails,
        sidekick.status, sidekick.routine)
    write_tails_cpu_per_frame()
    write_cnz_slot_machine_state()

    if trace_frame % SNAPSHOT_INTERVAL == 0
            or (trace_frame >= 5104 and trace_frame <= 5106)
            or (trace_frame >= 5995 and trace_frame <= 6005) then
        write_state_snapshot("sonic", PLAYER_BASE)
        write_state_snapshot("tails", SIDEKICK_BASE)
    end

    -- Object scanning: every frame for proximity, every 4 frames for full scan
    -- Proximity logging runs every frame so we never miss collision-relevant objects.
    scan_objects({
        { character = "sonic", slot = 0, present = 1, x = x, y = y },
        { character = "tails", slot = 1, present = sidekick.present, x = sidekick.x, y = sidekick.y },
    })
    -- OPL cursor state: emit event on chunk transitions for ROM↔engine comparison.
    -- v_opl_screen changes only when OPL_Next processes a new chunk.
    local opl_screen = mainmemory.read_u16_be(ADDR_OPL_SCREEN)
    if opl_screen ~= prev_opl_screen then
        local fwd_ptr = mainmemory.read_u32_be(ADDR_OPL_DATA_FWD)
        local bwd_ptr = mainmemory.read_u32_be(ADDR_OPL_DATA_BWD)
        local fwd_counter = mainmemory.read_u8(ADDR_OBJSTATE)
        local bwd_counter = mainmemory.read_u8(ADDR_OBJSTATE + 1)
        local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
        local dir = "R"
        if prev_opl_screen >= 0 and opl_screen < prev_opl_screen then
            dir = "L"
        end
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"cursor_state","opl_screen":"0x%04X",'
            .. '"fwd_ptr":"0x%08X","bwd_ptr":"0x%08X","fwd_ctr":%d,"bwd_ctr":%d,"dir":"%s"}',
            trace_frame, vfc, opl_screen, fwd_ptr, bwd_ptr, fwd_counter, bwd_counter, dir))
        prev_opl_screen = opl_screen
    end

    trace_frame = trace_frame + 1
end

-- Create output directory at load time (avoids cmd.exe pause during gameplay)
os.execute("mkdir \"" .. OUTPUT_DIR .. "\" 2>NUL")

-- Run at maximum speed in headless mode.
-- emu.limitframerate(false) removes the 60fps cap.
-- client.speedmode(6400) sets emulator speed to 6400% as backup.
-- invisibleemulation(true) skips rendering for additional speedup.
-- Set HEADLESS_VISIBLE = true to keep the window visible for progress feedback.
local HEADLESS_VISIBLE = false
if HEADLESS then
    emu.limitframerate(false)
    client.speedmode(6400)
    if client.SetSoundOn then
        pcall(client.SetSoundOn, false)
    end
    if not HEADLESS_VISIBLE then
        client.invisibleemulation(true)
    end
end

-- Main loop using explicit frame-advance.
-- This pattern keeps the script in control of the event loop so we can:
--   1. Detect movie-end pauses (BizHawk pauses when a movie finishes)
--   2. Cleanly flush and close all files BEFORE calling client.exit()
-- The onframeend callback pattern doesn't work because callbacks stop
-- firing when BizHawk pauses, and client.exit() can kill the process
-- before file I/O completes.
print(string.format("S2 Trace Recorder v" .. LUA_SCRIPT_VERSION .. " loaded. Profile=%s. TargetSegment=%d. Waiting for level gameplay (Game_Mode=0x0C, controls unlocked)...",
    TRACE_PROFILE, TARGET_GAMEPLAY_SEGMENT))

-- v9.10 hard safety net: even if every movie-end signal fails (movie.length()==0,
-- mode never reports FINISHED, game never leaves 0x0C), the loop must not run
-- forever. Cap at the movie length (+ margin) when known, else a large absolute
-- bound. This is the backstop that prevents the runaway EmuHawk.
local function absolute_frame_cap()
    local len = movie.isloaded() and movie.length() or 0
    if BK2_FRAME_COUNT ~= nil and BK2_FRAME_COUNT > len then
        len = BK2_FRAME_COUNT
    end
    if len > 0 then
        return len + 64  -- a few frames past the movie to let finalisation land
    end
    return 2000000       -- far beyond any S2 level-select / complete route BK2
end
local FRAME_CAP = absolute_frame_cap()

while true do
    on_frame_end()

    -- Backstop: force-finish if we somehow blew past the movie/cap without any
    -- normal stop signal firing.
    if not finished and emu.framecount() >= FRAME_CAP then
        print(string.format(
            "Frame cap %d reached without a movie-end signal; finalising and exiting.", FRAME_CAP))
        -- Run mode: funnel through finalize_run_end() so the manifest (and
        -- any already-recorded segments/transitions) survive this backstop.
        -- Plain mode: unchanged (finished=true only; the main loop's
        -- `if finished then` block below still does its own
        -- flush/write_metadata/close_files as it always has).
        if run_id ~= nil then
            finalize_run_end()
        end
        finished = true
    end

    -- If recording is done, finalise files and exit from INSIDE the loop.
    -- Code after the loop may never execute because client.exit() kills
    -- the process immediately.
    if finished then
        -- Run mode: finalize_run_end() already closed the segment files and
        -- wrote the manifest before `finished` was set; re-finalizing here
        -- would mislabel a successful run as "no rows recorded" (S1 model's
        -- post-restructure exit block).
        if run_id ~= nil then
            print(string.format(
                "Run complete: %d segment(s), %d transition(s) recorded. Exiting.",
                #segments_done, #transitions_done))
            break
        end
        print("Recording complete. Writing final output...")
        local recorded_trace = physics_file ~= nil
        if recorded_trace then
            physics_file:flush()
            write_metadata()
        else
            print("No gameplay trace rows were recorded.")
        end
        close_files()
        if recorded_trace then
            print(string.format("Trace finalised: %s act %d, %d frames.",
                start_zone_name, apparent_act_for(start_rom_zone_id, start_act) + 1, trace_frame))
        end
        break
    end

    -- If paused (e.g. BizHawk pauses on movie end), unpause so we get
    -- another iteration to detect the FINISHED state and exit cleanly.
    if client.ispaused() then
        client.unpause()
    end

    emu.frameadvance()
end

-- v9.10 reliable termination: client.exit() is a no-op on some BizHawk builds
-- (the "kept running past the movie" symptom). All files are already
-- flushed/closed above, so it is safe to call client.exit() repeatedly (with an
-- emu.frameadvance() yield so a working exit takes effect), then client.pause()
-- as a last resort so EmuHawk idles at 0% CPU instead of free-running into a
-- multi-GB runaway -- the host launcher's process-kill/tasklist check then reaps
-- it cleanly.
if HEADLESS then
    for _ = 1, 8 do
        client.exit()
        if client.ispaused() then client.unpause() end
        emu.frameadvance()
    end
    client.pause()
end
