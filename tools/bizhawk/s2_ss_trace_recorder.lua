-- s2_ss_trace_recorder.lua
-- BizHawk Lua script for recording Sonic 2 REV01 special-stage frame-by-frame
-- physics state during BK2 movie playback.
--
-- Derived from the s2_trace_recorder.lua (v9.10) skeleton: same explicit
-- frame-advance main loop, open_files/write_metadata structure, movie-input
-- derivation (extended here for P2), single output-dir creation at load
-- time, and flush cadence. The capture core is special-stage specific:
-- half-pipe track state, per-player ss_x/ss_y/ss_z half-pipe coordinates,
-- rings-to-go bookkeeping, and a real emu.islagged() lag column (the level
-- recorder's lag_counter column is a placeholder; special stages lag
-- heavily so this recorder must report real lag state).
--
-- Usage:
--   1. Open BizHawk with Sonic 2 REV01 ROM
--   2. Load the s2-lvl-select-special-stage.bk2 movie
--   3. Tools > Lua Console > load this script
--   4. Recording starts automatically once Game_Mode reaches 0x10
--      (special stage) and finalises when Game_Mode leaves 0x10, the movie
--      ends, or the frame cap is hit.
------------------------------------------------------------------------------

------------------
--- Shared lib ---
------------------

-- Locate tools/bizhawk/lib/ robustly across the .bat/%TEMP%-wrapper route, the
-- direct --lua= route, and headless launches (see lib/oggf_trace_common.lua and
-- SHARED_MODULE_HANDOFF.md). The launcher-provided env var wins; otherwise fall
-- back to this recorder's own directory, then CWD. Scoped in a do-block so the
-- helper's local slot is freed (these recorders sit near Lua's 200-locals cap).
local C
do
    local function oggf_lib_dir()
        local env = os.getenv("OGGF_BIZHAWK_LIB")        -- launcher-provided, most robust
        if env and #env > 0 then return env end
        local src = debug.getinfo(1, "S").source         -- "@<abs path to this recorder>"
        local dir = src:match("^@(.*[/\\])")             -- strip filename
        if dir then return dir .. "lib/" end
        return "lib/"                                     -- CWD fallback
    end
    -- assert() so a bad path surfaces as a load error (visible without
    -- --chromeless) instead of silently skipping the whole recorder.
    C = assert(loadfile(oggf_lib_dir() .. "oggf_trace_common.lua"))()
end

-----------------
--- Constants ---
-----------------

local LUA_SCRIPT_VERSION = "1.4-s2ss"
local TRACE_PROFILE = "s2_special_stage"

-- S2 REV01 ReadJoypads' shared RTS is $1156 (s2.asm:1361-1387). It executes
-- once after P1 and once after P2, so the callback filters A0=$F608 (both pads
-- stored) and the stack return PC=$88E (the Vint_S2SS call at $88A has
-- returned; s2.asm:837-840). The recurring loop's post-RunObjects call site
-- then attaches that exact sample to the completed pass. BizHawk permits only
-- two simultaneous execute hooks, so there is deliberately no RunObjects-entry
-- callback.
local PC_READ_JOYPADS_RETURN = 0x1156
local VINT_S2SS_READ_JOYPADS_RETURN_PC = 0x88E
local CTRL_2_READ_COMPLETE_A0 = 0xF608
-- $52B2 is the instruction after the recurring loop's jsr RunObjects
-- (s2.asm:6694-6729). Obj59's successful tail pops RunObjects' own return
-- frame and bypasses the generic RunObjects_End RTS when it raises
-- SS_Check_Rings_flag (s2.asm:72427-72445), but it still returns here.
local PC_S2SS_POST_RUN_OBJECTS = 0x52B2
local INPUT_SAMPLE_HOOK_NAME = "s2ss_input_sample"
local RUN_OBJECTS_HOOK_NAME = "s2ss_recurring_post_run_objects"

-- The workflow passes an absolute directory because EmuHawk's child-process
-- working directory is not stable across launcher/config variants.
local OUTPUT_DIR = os.getenv("OGGF_TRACE_OUTPUT_DIR") or "trace_output/"
if OUTPUT_DIR:sub(-1) ~= "/" and OUTPUT_DIR:sub(-1) ~= "\\" then
    OUTPUT_DIR = OUTPUT_DIR .. "/"
end

-- Headless mode: run at maximum speed, auto-exit when done.
local HEADLESS = true

local SOURCE_BK2 = os.getenv("OGGF_BK2_BASENAME") or ""
local BK2_FRAME_COUNT = tonumber(os.getenv("OGGF_BK2_FRAME_COUNT") or "")

-- Hard-fail if Game_Mode never reaches 0x10 within this many emulated frames.
local START_TIMEOUT_FRAMES = 5000

-- S2 REV01 68K RAM addresses (mainmemory domain = $FF0000 base stripped).
-- Verified against docs/s2disasm/s2.constants.asm phase blocks (see task brief).
local ADDR_GAME_MODE               = 0xF600
local GAMEMODE_SPECIAL_STAGE        = 0x10

local ADDR_TRACK_ANIM               = 0xDB08  -- SSTrack_anim (u8)
local ADDR_CURRENT_SEGMENT          = 0xDB0A  -- SpecialStage_CurrentSegment (u8)
local ADDR_TRACK_ANIM_FRAME         = 0xDB0B  -- SSTrack_anim_frame (u8)
local ADDR_TRACK_DRAWING_INDEX      = 0xDB0D  -- SSTrack_drawing_index (u8)
local ADDR_TRACK_ORIENTATION        = 0xDB0E  -- SSTrack_Orientation (u8)
local ADDR_CUR_SPEED_FACTOR         = 0xDB16  -- SS_Cur_Speed_Factor (u16be)
local ADDR_TRACK_DURATION_TIMER     = 0xDB1F  -- SSTrack_duration_timer (u8)
local ADDR_PLAYER_ANIM_FRAME_TIMER  = 0xDB21  -- SS_player_anim_frame_timer (u8)
local ADDR_CHECK_RINGS_FLAG         = 0xDB86  -- SS_Check_Rings_flag (u8)
local ADDR_RING_REQUIREMENT         = 0xDB8C  -- SS_Ring_Requirement (u16be)
local ADDR_CURRENT_LEVEL_LAYOUT     = 0xDB8E  -- SS_CurrentLevelLayout (u32be)
local ADDR_PERFECT_RINGS_LEFT       = 0xDB9A  -- SS_Perfect_rings_left (u16be)
local ADDR_NO_RINGS_TOGO_LIFETIME   = 0xDBA2  -- SS_NoRingsTogoLifetime (u16be)
local ADDR_RINGS_TOGO_BCD           = 0xDBA4  -- SS_RingsToGoBCD (u16be, BCD)
local ADDR_HIDE_RINGS_TOGO          = 0xDBA6  -- SS_HideRingsToGo (u8)
local ADDR_TRIGGER_RINGS_TOGO       = 0xDBA7  -- SS_TriggerRingsToGo (u8)
local ADDR_TAILS_CONTROL_COUNTER    = 0xF702  -- Tails_control_counter (u16be)
local ADDR_SWAP_POSITIONS_FLAG      = 0xF742  -- SS_Swap_Positions_Flag (u8)
local ADDR_SPECIAL_STAGE_STARTED    = 0xDB23  -- SpecialStage_Started (u8)
local ADDR_CTRL_1_HELD              = 0xF604  -- Ctrl_1_Held (u8, raw physical)
local ADDR_CTRL_2_HELD              = 0xF606  -- Ctrl_2_Held (u8, raw physical)

-- Per-player object bases (S2 SST slots reused for special-stage players).
local SONIC_BASE = 0xB000
local TAILS_BASE = 0xB040

-- Per-player object offsets (s2.constants.asm:138-153).
local OFF_ID                 = 0x00  -- u8: Sonic=0x09, Tails=0x10, 0=absent
local OFF_ANIM_FRAME         = 0x1B  -- u8
local OFF_ANIM               = 0x1C  -- u8
local OFF_STATUS             = 0x22  -- u8
local OFF_ROUTINE            = 0x24  -- u8
local OFF_ROUTINE_SECONDARY  = 0x25  -- u8
local OFF_ANGLE              = 0x26  -- u8
local OFF_SS_X               = 0x2A  -- u16be
local OFF_SS_X_SUB           = 0x2C  -- u16be
local OFF_SS_Y               = 0x2E  -- u16be
local OFF_SS_Y_SUB           = 0x30  -- u16be
local OFF_FLIP_TIMER         = 0x33  -- u8
local OFF_SS_Z               = 0x34  -- u16be
local OFF_HURT_TIMER         = 0x36  -- u8
local OFF_SLIDE_TIMER        = 0x37  -- u8
local OFF_RINGS_HUNDREDS     = 0x3C  -- u8 (BCD digit)
local OFF_RINGS_TENS         = 0x3D  -- u8 (BCD digit)
local OFF_RINGS_UNITS        = 0x3E  -- u8 (BCD digit)

-- Object table (S2 SST: 128 slots of $40 bytes at $FFFFB000..$FFFFCFFF).
-- Used only to scan for the ObjID_SSResults sighting that marks stage end.
local OBJ_TABLE_START = 0xB000
local OBJ_SLOT_SIZE   = 0x40
local OBJ_TOTAL_SLOTS = 128
local OBJID_SS_RESULTS = 0x6F

-- Genesis joypad bitmask (matching engine convention). The five directional/
-- jump bits are single-sourced in lib/oggf_trace_common.lua; INPUT_START is
-- s2_ss-specific and stays inline.
local INPUT_UP    = C.INPUT_UP
local INPUT_DOWN  = C.INPUT_DOWN
local INPUT_LEFT  = C.INPUT_LEFT
local INPUT_RIGHT = C.INPUT_RIGHT
local INPUT_JUMP  = C.INPUT_JUMP
local INPUT_START = 0x80

-----------------
--- State     ---
-----------------

local started = false
local finished = false
local trace_frame = 0
local bk2_frame_offset = 0
local stage_finished_emitted = false
local results_started_emitted = false
local prev_check_rings_flag = 0
local prev_hide_rings_to_go = 0
local prev_trigger_rings_to_go = 0
local prev_no_rings_togo_lifetime = 0
local prev_special_stage_started = nil
-- RunObjects consumes the physical sample captured by the preceding
-- Vint_S2SS. It can finish on either side of the next VBlank observation, so
-- completed passes queue forward to the next non-lag row. Using only the last
-- non-lag row at return binds state backwards and drops passes that cross
-- lag-labelled samples (s2.asm:6679-6688, 29805-29849).
local last_nonlag_trace_frame = -1
local pending_run_objects_ends = {}
local next_run_objects_pass_sequence = 0
local next_input_sample_sequence = 0
local latest_input_sample = nil
local last_completed_input_sample_sequence = nil
local previous_s2ss_sample_p1_held = 0
local previous_s2ss_sample_p2_held = 0
local run_objects_hook_registered = false

local physics_file = nil
local aux_file = nil
local unregister_run_objects_hook

-----------------
--- Helpers   ---
-----------------

-- json_escape single-sourced in lib/oggf_trace_common.lua.
local json_escape = C.json_escape

-- Write a JSONL line to aux file. Every emitted event uses a "type" key
-- (never "event") so the generic TraceEvent parser's default branch
-- preserves it as a StateSnapshot with the field intact for exact-match
-- lookups (e.g. SpecialStageTraceData.isStageFinished checks fields["type"]).
-- Body single-sourced in lib/oggf_trace_common.lua; thin local wrapper
-- forwards the file-scope aux_file upvalue.
local function write_aux(json_str)
    C.write_aux(aux_file, json_str)
end

-- Read one player's special-stage SST state. Returns present=false with
-- zeroed fields when the slot's id byte is 0 (character absent, e.g. Tails
-- not unlocked/selected).
local function read_ss_character(base)
    local id = mainmemory.read_u8(base + OFF_ID)
    if id == 0 then
        return {
            present = false,
            ss_x = 0, ss_x_sub = 0, ss_y = 0, ss_y_sub = 0, ss_z = 0,
            angle = 0, routine = 0, routine_secondary = 0, status = 0,
            anim = 0, anim_frame = 0, rings_bcd = 0,
            hurt_timer = 0, slide_timer = 0, flip_timer = 0,
        }
    end

    local hundreds = mainmemory.read_u8(base + OFF_RINGS_HUNDREDS)
    local tens = mainmemory.read_u8(base + OFF_RINGS_TENS)
    local units = mainmemory.read_u8(base + OFF_RINGS_UNITS)

    return {
        present = true,
        ss_x = mainmemory.read_u16_be(base + OFF_SS_X),
        ss_x_sub = mainmemory.read_u16_be(base + OFF_SS_X_SUB),
        ss_y = mainmemory.read_u16_be(base + OFF_SS_Y),
        ss_y_sub = mainmemory.read_u16_be(base + OFF_SS_Y_SUB),
        ss_z = mainmemory.read_u16_be(base + OFF_SS_Z),
        angle = mainmemory.read_u8(base + OFF_ANGLE),
        routine = mainmemory.read_u8(base + OFF_ROUTINE),
        routine_secondary = mainmemory.read_u8(base + OFF_ROUTINE_SECONDARY),
        status = mainmemory.read_u8(base + OFF_STATUS),
        anim = mainmemory.read_u8(base + OFF_ANIM),
        anim_frame = mainmemory.read_u8(base + OFF_ANIM_FRAME),
        rings_bcd = (hundreds << 16) | (tens << 8) | units,
        hurt_timer = mainmemory.read_u8(base + OFF_HURT_TIMER),
        slide_timer = mainmemory.read_u8(base + OFF_SLIDE_TIMER),
        flip_timer = mainmemory.read_u8(base + OFF_FLIP_TIMER),
    }
end

-- One reusable state reader backs both VBlank CSV rows and the execution-hook
-- snapshot. Keeping these reads together prevents the two diagnostic views
-- from silently drifting as the schema evolves.
local function read_ss_state()
    return {
        speed_factor = mainmemory.read_u16_be(ADDR_CUR_SPEED_FACTOR),
        track_anim = mainmemory.read_u8(ADDR_TRACK_ANIM),
        track_anim_frame = mainmemory.read_u8(ADDR_TRACK_ANIM_FRAME),
        track_drawing_index = mainmemory.read_u8(ADDR_TRACK_DRAWING_INDEX),
        track_orientation = mainmemory.read_u8(ADDR_TRACK_ORIENTATION),
        track_duration_timer = mainmemory.read_u8(ADDR_TRACK_DURATION_TIMER),
        current_segment = mainmemory.read_u8(ADDR_CURRENT_SEGMENT),
        player_anim_frame_timer = mainmemory.read_u8(ADDR_PLAYER_ANIM_FRAME_TIMER),
        rings_togo_bcd = mainmemory.read_u16_be(ADDR_RINGS_TOGO_BCD),
        check_rings_flag = mainmemory.read_u8(ADDR_CHECK_RINGS_FLAG),
        tails_control_counter = mainmemory.read_u16_be(ADDR_TAILS_CONTROL_COUNTER),
        swap_positions_flag = mainmemory.read_u8(ADDR_SWAP_POSITIONS_FLAG),
        sonic = read_ss_character(SONIC_BASE),
        tails = read_ss_character(TAILS_BASE),
    }
end

local function character_json(prefix, character)
    return string.format(
        ',"%s_present":%d,"%s_ss_x":%d,"%s_ss_x_sub":%d,'
        .. '"%s_ss_y":%d,"%s_ss_y_sub":%d,"%s_ss_z":%d,'
        .. '"%s_angle":%d,"%s_routine":%d,"%s_routine_secondary":%d,'
        .. '"%s_status":%d,"%s_anim":%d,"%s_anim_frame":%d,'
        .. '"%s_rings_bcd":%d,"%s_hurt_timer":%d,"%s_slide_timer":%d,'
        .. '"%s_flip_timer":%d',
        prefix, character.present and 1 or 0,
        prefix, character.ss_x, prefix, character.ss_x_sub,
        prefix, character.ss_y, prefix, character.ss_y_sub,
        prefix, character.ss_z, prefix, character.angle,
        prefix, character.routine, prefix, character.routine_secondary,
        prefix, character.status, prefix, character.anim,
        prefix, character.anim_frame, prefix, character.rings_bcd,
        prefix, character.hurt_timer, prefix, character.slide_timer,
        prefix, character.flip_timer)
end

local function write_run_objects_end(frame, pass, state)
    write_aux(string.format(
        '{"frame":%d,"type":"run_objects_end","pass_sequence":%d,'
        .. '"first_eligible_frame":%d,"completion_cursor_frame":%d,'
        .. '"input_sample_frame":%d,"input_sample_bk2_frame":%d,'
        .. '"previous_input_sample_frame":%d,"previous_input_sample_bk2_frame":%d,'
        .. '"input_sample_sequence":%d,"input_source":"vint_s2ss_read_joypads",'
        .. '"started_at_input_sample":%d,"p1_held":%d,"p2_held":%d,'
        .. '"previous_p1_held":%d,"previous_p2_held":%d,"speed_factor":%d,'
        .. '"track_anim":%d,"track_anim_frame":%d,"track_drawing_index":%d,'
        .. '"track_orientation":%d,"track_duration_timer":%d,'
        .. '"current_segment":%d,"player_anim_frame_timer":%d,'
        .. '"rings_togo_bcd":%d,"check_rings_flag":%d,'
        .. '"tails_control_counter":%d,"swap_positions_flag":%d',
        frame, pass.pass_sequence, pass.first_eligible_frame,
        pass.completion_cursor_frame, pass.input_sample_frame,
        pass.input_sample_bk2_frame, pass.previous_input_sample_frame,
        pass.previous_input_sample_bk2_frame, pass.input_sample_sequence,
        pass.started_at_input_sample, pass.p1_held, pass.p2_held,
        pass.previous_p1_held, pass.previous_p2_held, state.speed_factor,
        state.track_anim, state.track_anim_frame,
        state.track_drawing_index, state.track_orientation,
        state.track_duration_timer, state.current_segment,
        state.player_anim_frame_timer, state.rings_togo_bcd,
        state.check_rings_flag, state.tails_control_counter,
        state.swap_positions_flag)
        .. character_json("sonic", state.sonic)
        .. character_json("tails", state.tails)
        .. '}')
end

local function publish_run_objects_end(pass, frame)
    write_run_objects_end(frame, pass, pass.state)
end

local function publish_pending_run_objects_ends(frame)
    if #pending_run_objects_ends == 0 then return end
    for _, pass in ipairs(pending_run_objects_ends) do
        publish_run_objects_end(pass, frame)
    end
    pending_run_objects_ends = {}
end

local function flush_pending_run_objects_ends()
    publish_pending_run_objects_ends(last_nonlag_trace_frame)
end

-- The final RunObjects return can occur inside the lag-labelled VBlank that
-- first exposes SS_Check_Rings_flag. No later recurring observation exists to
-- flush it forward, so publish that one pending pass at the raw finish
-- observation with its exact ReadJoypads identity intact.
local function publish_pending_finish_pass()
    if #pending_run_objects_ends ~= 1 then
        error(string.format(
            "stage finish expected exactly one pending RunObjects pass, got %d",
            #pending_run_objects_ends))
    end
    local pass = pending_run_objects_ends[1]
    if pass.completion_cursor_frame ~= trace_frame then
        error(string.format(
            "terminal pass completion cursor %d differs from finish observation %d",
            pass.completion_cursor_frame, trace_frame))
    end
    if pass.state.check_rings_flag == 0 then
        error("terminal pending pass did not raise SS_Check_Rings_flag")
    end
    publish_pending_run_objects_ends(trace_frame)
end

-- Read the BK2 movie's logical input for the given absolute BK2 frame index
-- and controller (1 or 2), converted to the engine's input bitmask. Returns
-- 0 when no movie is loaded or the controller has no recorded input (the
-- expected case for P2 on a single-player special-stage movie).
local function joypad_mask_from_frame(frame_index, player)
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

-----------------
--- Recording ---
-----------------

local function open_files()
    physics_file = io.open(OUTPUT_DIR .. "physics.csv", "w")
    aux_file = io.open(OUTPUT_DIR .. "aux_state.jsonl", "w")

    physics_file:write(
        "frame,input,input_p2,lag,speed_factor,track_anim,track_anim_frame,track_drawing_index,track_orientation,track_duration_timer,current_segment,player_anim_frame_timer,rings_togo_bcd,check_rings_flag,tails_control_counter,swap_positions_flag,sonic_present,sonic_ss_x,sonic_ss_x_sub,sonic_ss_y,sonic_ss_y_sub,sonic_ss_z,sonic_angle,sonic_routine,sonic_routine_secondary,sonic_status,sonic_anim,sonic_anim_frame,sonic_rings_bcd,sonic_hurt_timer,sonic_slide_timer,sonic_flip_timer,tails_present,tails_ss_x,tails_ss_x_sub,tails_ss_y,tails_ss_y_sub,tails_ss_z,tails_angle,tails_routine,tails_routine_secondary,tails_status,tails_anim,tails_anim_frame,tails_rings_bcd,tails_hurt_timer,tails_slide_timer,tails_flip_timer\n")
    physics_file:flush()
end

local function close_files()
    unregister_run_objects_hook()
    if physics_file then
        physics_file:close()
        physics_file = nil
    end
    if aux_file then
        aux_file:close()
        aux_file = nil
    end
end

local function write_metadata()
    local meta_file = io.open(OUTPUT_DIR .. "metadata.json", "w")
    meta_file:write("{\n")
    meta_file:write('  "game": "s2",\n')
    meta_file:write('  "trace_profile": "' .. TRACE_PROFILE .. '",\n')
    meta_file:write('  "special_stage_index": 0,\n')
    meta_file:write('  "ss_csv_version": 1,\n')
    meta_file:write('  "characters": ["sonic", "tails"],\n')
    meta_file:write('  "main_character": "sonic",\n')
    meta_file:write('  "sidekicks": ["tails"],\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "source_bk2": "' .. json_escape(SOURCE_BK2) .. '",\n')
    meta_file:write('  "lua_script_version": "' .. LUA_SCRIPT_VERSION .. '",\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    meta_file:write('  "bizhawk_version": "2.11",\n')
    meta_file:write('  "genesis_core": "Genplus-gx"\n')
    meta_file:write("}\n")
    meta_file:close()
    print(string.format("Metadata written. Trace frames: %d", trace_frame))
end

-- Frame -1 pre-trace snapshot: fixed special-stage parameters captured once,
-- before trace frame 0 is written.
local function write_pretrace_snapshot()
    if not aux_file then return end
    write_aux(string.format(
        '{"frame":-1,"type":"state_snapshot","ring_requirement":"0x%04x",'
        .. '"current_level_layout":"0x%08x","initial_speed_factor":"0x%04x",'
        .. '"perfect_rings_left":"0x%04x"}',
        mainmemory.read_u16_be(ADDR_RING_REQUIREMENT),
        mainmemory.read_u32_be(ADDR_CURRENT_LEVEL_LAYOUT),
        mainmemory.read_u16_be(ADDR_CUR_SPEED_FACTOR),
        mainmemory.read_u16_be(ADDR_PERFECT_RINGS_LEFT)))
end

-- Scan all 128 SST slots ($FFFFB000..$FFFFCFFF) for the first appearance of
-- ObjID_SSResults ($6F). This is later than the canonical stage-finished
-- boundary and marks only the start of the recorded, uncompared results tail.
local function check_results_started()
    if results_started_emitted then return end
    for slot = 0, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        if mainmemory.read_u8(addr) == OBJID_SS_RESULTS then
            write_aux(string.format('{"frame":%d,"type":"results_started","slot":%d}',
                trace_frame, slot))
            results_started_emitted = true
            return
        end
    end
end

local function check_checkpoint(check_rings_flag)
    if prev_check_rings_flag == 0 and check_rings_flag ~= 0 then
        write_aux(string.format(
            '{"frame":%d,"type":"checkpoint","check_rings_flag":"0x%02x"}',
            trace_frame, check_rings_flag))
        if not stage_finished_emitted then
            if last_nonlag_trace_frame < 0 then
                error("final checkpoint resolved before any logical observation")
            end
            publish_pending_finish_pass()
            write_aux(string.format(
                '{"frame":%d,"observed_frame":%d,"type":"stage_finished",'
                .. '"check_rings_flag":"0x%02x"}',
                last_nonlag_trace_frame, trace_frame, check_rings_flag))
            stage_finished_emitted = true
        end
    end
    prev_check_rings_flag = check_rings_flag
end

local function check_message_state()
    local hide_rings_to_go = mainmemory.read_u8(ADDR_HIDE_RINGS_TOGO)
    local trigger_rings_to_go = mainmemory.read_u8(ADDR_TRIGGER_RINGS_TOGO)
    local no_rings_togo_lifetime = mainmemory.read_u16_be(ADDR_NO_RINGS_TOGO_LIFETIME)
    if hide_rings_to_go ~= prev_hide_rings_to_go
            or trigger_rings_to_go ~= prev_trigger_rings_to_go
            or no_rings_togo_lifetime ~= prev_no_rings_togo_lifetime then
        write_aux(string.format(
            '{"frame":%d,"type":"message_state","hide_rings_to_go":"0x%02x",'
            .. '"trigger_rings_to_go":"0x%02x","no_rings_togo_lifetime":"0x%04x"}',
            trace_frame, hide_rings_to_go, trigger_rings_to_go, no_rings_togo_lifetime))
        prev_hide_rings_to_go = hide_rings_to_go
        prev_trigger_rings_to_go = trigger_rings_to_go
        prev_no_rings_togo_lifetime = no_rings_togo_lifetime
    end
end

local function check_control_state()
    local special_stage_started = mainmemory.read_u8(ADDR_SPECIAL_STAGE_STARTED)
    if prev_special_stage_started == nil
            or special_stage_started ~= prev_special_stage_started then
        write_aux(string.format(
            '{"frame":%d,"type":"control_state","started":%d}',
            trace_frame, special_stage_started ~= 0 and 1 or 0))
        prev_special_stage_started = special_stage_started
    end
end

local function on_s2ss_input_sample()
    if not started or finished or physics_file == nil then return end
    if mainmemory.read_u8(ADDR_GAME_MODE) ~= GAMEMODE_SPECIAL_STAGE then return end
    local a0 = emu.getregister("M68K A0") & 0xFFFF
    if a0 ~= CTRL_2_READ_COMPLETE_A0 then return end
    local stack_pointer = emu.getregister("M68K A7") & 0xFFFF
    local return_pc = mainmemory.read_u32_be(stack_pointer) & 0xFFFFFF
    if return_pc ~= VINT_S2SS_READ_JOYPADS_RETURN_PC then return end
    local p1_held = mainmemory.read_u8(ADDR_CTRL_1_HELD)
    local p2_held = mainmemory.read_u8(ADDR_CTRL_2_HELD)
    local input_sample_bk2_frame = emu.framecount()
    local previous_input_sample_frame = input_sample_bk2_frame - bk2_frame_offset - 1
    local previous_input_sample_bk2_frame = input_sample_bk2_frame - 1
    if latest_input_sample ~= nil then
        previous_input_sample_frame = latest_input_sample.input_sample_frame
        previous_input_sample_bk2_frame = latest_input_sample.input_sample_bk2_frame
    end
    latest_input_sample = {
        input_sample_sequence = next_input_sample_sequence,
        input_sample_frame = input_sample_bk2_frame - bk2_frame_offset,
        input_sample_bk2_frame = input_sample_bk2_frame,
        previous_input_sample_frame = previous_input_sample_frame,
        previous_input_sample_bk2_frame = previous_input_sample_bk2_frame,
        started_at_input_sample = mainmemory.read_u8(ADDR_SPECIAL_STAGE_STARTED),
        p1_held = p1_held,
        p2_held = p2_held,
        previous_p1_held = previous_s2ss_sample_p1_held,
        previous_p2_held = previous_s2ss_sample_p2_held,
    }
    previous_s2ss_sample_p1_held = p1_held
    previous_s2ss_sample_p2_held = p2_held
    next_input_sample_sequence = next_input_sample_sequence + 1
end

local function on_recurring_post_run_objects()
    if not started or finished or physics_file == nil then return end
    if mainmemory.read_u8(ADDR_GAME_MODE) ~= GAMEMODE_SPECIAL_STAGE then return end
    if stage_finished_emitted then return end
    -- The startup/fade loops have different observation ownership. Atomic
    -- pass-end comparison begins only when the ROM's recurring gameplay loop
    -- is enabled by SpecialStage_Started (s2.asm:6689,9745).
    if mainmemory.read_u8(ADDR_SPECIAL_STAGE_STARTED) == 0 then
        return
    end
    if latest_input_sample == nil then
        error("recurring RunObjects return observed without a preceding Vint_S2SS input sample")
    end
    if latest_input_sample.started_at_input_sample == 0 then return end
    if last_completed_input_sample_sequence ~= nil
            and latest_input_sample.input_sample_sequence
                <= last_completed_input_sample_sequence then
        error("more than one active RunObjects pass consumed the same Vint_S2SS sample")
    end

    local pass = {
        input_sample_sequence = latest_input_sample.input_sample_sequence,
        first_eligible_frame = latest_input_sample.input_sample_frame,
        input_sample_frame = latest_input_sample.input_sample_frame,
        input_sample_bk2_frame = latest_input_sample.input_sample_bk2_frame,
        previous_input_sample_frame = latest_input_sample.previous_input_sample_frame,
        previous_input_sample_bk2_frame = latest_input_sample.previous_input_sample_bk2_frame,
        started_at_input_sample = latest_input_sample.started_at_input_sample,
        p1_held = latest_input_sample.p1_held,
        p2_held = latest_input_sample.p2_held,
        previous_p1_held = latest_input_sample.previous_p1_held,
        previous_p2_held = latest_input_sample.previous_p2_held,
    }
    pass.pass_sequence = next_run_objects_pass_sequence
    pass.completion_cursor_frame = trace_frame
    pass.state = read_ss_state()
    next_run_objects_pass_sequence = next_run_objects_pass_sequence + 1
    last_completed_input_sample_sequence = pass.input_sample_sequence

    -- The return hook runs during emu.frameadvance(), after the prior row has
    -- already been sampled. Queue forward to the next eligible observation;
    -- normally that is the next non-lag row, while the finish-causing pass is
    -- published explicitly at its raw observation. Publishing immediately to
    -- last_nonlag_trace_frame would bind the pass backwards.
    table.insert(pending_run_objects_ends, pass)
end

local function register_run_objects_hook()
    if run_objects_hook_registered then return end
    if not event or not event.onmemoryexecute then
        error("BizHawk event.onmemoryexecute is required for S2 SS RunObjects-end capture")
    end
    event.onmemoryexecute(on_s2ss_input_sample, PC_READ_JOYPADS_RETURN,
        INPUT_SAMPLE_HOOK_NAME)
    event.onmemoryexecute(on_recurring_post_run_objects, PC_S2SS_POST_RUN_OBJECTS,
        RUN_OBJECTS_HOOK_NAME)
    run_objects_hook_registered = true
end

unregister_run_objects_hook = function()
    if not run_objects_hook_registered then return end
    if event and event.unregisterbyname then
        pcall(event.unregisterbyname, INPUT_SAMPLE_HOOK_NAME)
        pcall(event.unregisterbyname, RUN_OBJECTS_HOOK_NAME)
    end
    run_objects_hook_registered = false
end

local function record_frame()
    local state = read_ss_state()
    local sonic = state.sonic
    local tails = state.tails

    local frame_index = bk2_frame_offset + trace_frame
    local input_mask = joypad_mask_from_frame(frame_index, 1)
    local input_p2_mask = joypad_mask_from_frame(frame_index, 2)
    local lag = emu.islagged() and 1 or 0
    if lag == 0 then
        last_nonlag_trace_frame = trace_frame
        flush_pending_run_objects_ends()
    end

    -- frame is decimal and lag is 0/1; every other column (including the
    -- *_present flags) is lowercase hex per the ss_csv_version 1 schema.
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

    if trace_frame % 60 == 0 then
        physics_file:flush()
    end
    if trace_frame % 300 == 0 then
        write_metadata()
    end

    check_control_state()
    check_checkpoint(state.check_rings_flag)
    check_message_state()
    check_results_started()

    trace_frame = trace_frame + 1
end

-----------------
--- Main Loop ---
-----------------

local function on_frame_end()
    local game_mode = mainmemory.read_u8(ADDR_GAME_MODE)

    if not started then
        if finished then return end
        if game_mode == GAMEMODE_SPECIAL_STAGE then
            started = true
            -- emu.framecount() returns the frame that just completed. This
            -- recorder does NOT skip a "dead" frame like the level
            -- recorder does: the frame that first shows Game_Mode==0x10 is
            -- recorded immediately as trace frame 0.
            bk2_frame_offset = emu.framecount()
            open_files()
            register_run_objects_hook()
            prev_check_rings_flag = mainmemory.read_u8(ADDR_CHECK_RINGS_FLAG)
            prev_hide_rings_to_go = mainmemory.read_u8(ADDR_HIDE_RINGS_TOGO)
            prev_trigger_rings_to_go = mainmemory.read_u8(ADDR_TRIGGER_RINGS_TOGO)
            prev_no_rings_togo_lifetime = mainmemory.read_u16_be(ADDR_NO_RINGS_TOGO_LIFETIME)
            write_metadata()
            write_pretrace_snapshot()
            print(string.format(
                "SS trace recording started at BizHawk frame %d.", bk2_frame_offset))
            if movie.isloaded() then
                print(string.format("Movie length: %d frames", movie.length()))
            end
        else
            if emu.framecount() >= START_TIMEOUT_FRAMES then
                error(string.format(
                    "Game_Mode never reached 0x10 (special stage) within %d frames.",
                    START_TIMEOUT_FRAMES))
            end
            return
        end
    end

    if game_mode ~= GAMEMODE_SPECIAL_STAGE then
        print("Left special stage gameplay at trace frame " .. trace_frame .. ". Finalising.")
        finished = true
        return
    end

    -- Stop exactly when the trace would need an input frame past the end of
    -- the loaded BK2 (mirrors the level recorder's movie-end handling).
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

    record_frame()
end

-- Create output directory at load time (avoids cmd.exe pause during gameplay).
os.execute("mkdir \"" .. OUTPUT_DIR .. "\" 2>NUL")

-- Run at maximum speed in headless mode.
local HEADLESS_VISIBLE = false
if HEADLESS then
    emu.limitframerate(false)
    client.speedmode(6400)
    if client.SetSoundOn then
        pcall(client.SetSoundOn, false)
    end
    if not HEADLESS_VISIBLE and client.invisibleemulation then
        client.invisibleemulation(true)
    end
end

print(string.format(
    "S2 Special Stage Trace Recorder v" .. LUA_SCRIPT_VERSION
    .. " loaded. Waiting for Game_Mode=0x10 (special stage)..."))

-- Hard safety net: even if every movie-end signal fails, the loop must not
-- run forever. Cap at the movie length (+ margin) when known, else a large
-- absolute bound (mirrors the v9.10 s2_trace_recorder.lua hygiene fix).
local function absolute_frame_cap()
    local len = movie.isloaded() and movie.length() or 0
    if BK2_FRAME_COUNT ~= nil and BK2_FRAME_COUNT > len then
        len = BK2_FRAME_COUNT
    end
    if len > 0 then
        return len + 64
    end
    return 2000000
end
local FRAME_CAP = absolute_frame_cap()

while true do
    on_frame_end()

    if not finished and emu.framecount() >= FRAME_CAP then
        print(string.format(
            "Frame cap %d reached without a movie-end signal; finalising and exiting.", FRAME_CAP))
        finished = true
    end

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
            print(string.format("Trace finalised: %d frames.", trace_frame))
        end
        break
    end

    if client.ispaused() then
        client.unpause()
    end

    emu.frameadvance()
end

-- Reliable termination: client.exit() is a no-op on some BizHawk builds, so
-- retry with frame-advance yields, then pause as a last resort so EmuHawk
-- idles at 0% CPU instead of free-running (mirrors s2_trace_recorder.lua v9.10).
if HEADLESS then
    for _ = 1, 8 do
        client.exit()
        if client.ispaused() then client.unpause() end
        emu.frameadvance()
    end
    client.pause()
end
