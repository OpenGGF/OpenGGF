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
local LUA_SCRIPT_VERSION = "9.10-s2"

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

local function reset_recording_state()
    close_files()
    os.remove(OUTPUT_DIR .. "metadata.json")
    os.remove(OUTPUT_DIR .. "physics.csv")
    os.remove(OUTPUT_DIR .. "aux_state.jsonl")
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

-----------------
--- Recording ---
-----------------

local function open_files()
    physics_file = io.open(OUTPUT_DIR .. "physics.csv", "w")
    aux_file = io.open(OUTPUT_DIR .. "aux_state.jsonl", "w")

    -- v6 header: shared execution counters plus explicit Sonic/Tails state blocks.
    physics_file:write("frame,input,camera_x,camera_y,rings,gameplay_frame_counter,"
        .. "vblank_counter,lag_counter,sonic_present,sonic_x,sonic_y,sonic_x_speed,"
        .. "sonic_y_speed,sonic_g_speed,sonic_angle,sonic_air,sonic_rolling,"
        .. "sonic_ground_mode,sonic_x_sub,sonic_y_sub,sonic_routine,sonic_status_byte,"
        .. "sonic_stand_on_obj,tails_present,tails_x,tails_y,tails_x_speed,"
        .. "tails_y_speed,tails_g_speed,tails_angle,tails_air,tails_rolling,"
        .. "tails_ground_mode,tails_x_sub,tails_y_sub,tails_routine,"
        .. "tails_status_byte,tails_stand_on_obj\n")
    physics_file:flush()
end

local function write_metadata()
    -- Use zone/act captured at recording start (not current RAM which may have advanced)
    local sidekick_present = recorded_sidekick_present
            or read_character_trace_state(SIDEKICK_BASE).present ~= 0
    local characters_json = sidekick_present and '["sonic", "tails"]' or '["sonic"]'
    local sidekicks_json = sidekick_present and '["tails"]' or '[]'
    local meta_file = io.open(OUTPUT_DIR .. "metadata.json", "w")
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
    meta_file:write('  "trace_schema": 8,\n')
    meta_file:write('  "csv_version": 6,\n')
    meta_file:write('  "aux_schema_extras": ["cnz_slot_machine_state_per_frame", "cpu_state_per_frame"],\n')
    meta_file:write('  "trace_profile": "' .. json_escape(TRACE_PROFILE) .. '",\n')
    meta_file:write('  "bizhawk_version": "2.11",\n')
    meta_file:write('  "genesis_core": "Genplus-gx",\n')
    meta_file:write('  "route": "' .. start_zone_name .. '",\n')
    meta_file:write('  "source_bk2": "' .. json_escape(SOURCE_BK2) .. '",\n')
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
            if gameplay_segment_index < TARGET_GAMEPLAY_SEGMENT then
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
            finished = true
            return
        end
        if movie.mode() == "FINISHED" then
            print(string.format(
                "Movie playback finished at trace frame %d (emu frame %d). Finalising.",
                trace_frame, emu.framecount()))
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

    -- v6 CSV: shared execution counters plus explicit Sonic/Tails state blocks.
    physics_file:write(string.format(
        "%04X,%04X,%04X,%04X,%04X,%04X,%04X,%04X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,"
            .. "%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X\n",
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
        sidekick.stand_on_obj))
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
        finished = true
    end

    -- If recording is done, finalise files and exit from INSIDE the loop.
    -- Code after the loop may never execute because client.exit() kills
    -- the process immediately.
    if finished then
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
