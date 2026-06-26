------------------------------------------------------------------------------
-- s1_trace_recorder.lua
-- BizHawk Lua script for recording Sonic 1 REV01 frame-by-frame physics
-- state during BK2 movie playback.
--
-- Usage:
--   1. Open BizHawk with Sonic 1 REV01 ROM
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
-- v3.0 changes: rename v_framecount to gameplay_frame_counter and add
-- vblank_counter plus lag_counter for counter-driven replay phase selection.
-- v3.3 changes: add metadata.rng_seed for one-time replay bootstrap and
-- RNG-frontier diagnostics. CSV schema is unchanged.
-- v3.4 changes: add s1_obj64_state aux events for LZ air-bubble maker
-- frontier diagnostics. CSV schema is unchanged.
-- v3.5 changes: (1) emit metadata.source_bk2 so the shared _movies/ BK2 resolver
-- finds the movie (previously omitted -> AbstractTraceReplayTest silently SKIPPED
-- the regenerated trace). (2) add obj_frame (obFrame / OFF_ANIM_FRAME_DISP 0x1A)
-- to object_near events for object-tilt/anim-phase diagnosis (e.g. SLZ seesaw
-- Obj5E tilt mapping frame). Both are diagnostic-only; CSV schema is unchanged.
-- v3.6 changes: RECORDER HYGIENE ONLY (no schema/data change; v3.5 traces stay
-- valid). (1) No more per-segment cmd-window flashes: all per-act output subdirs
-- are pre-created in a SINGLE os.execute at load (the only shell-out), and the
-- per-segment os.execute("mkdir") is removed. (2) Reliable movie-end self-exit:
-- the run now stops at min(S1_STOP_AT_FRAME, movie_end) and exits even when
-- client.exit() is a no-op on this BizHawk build (the loop terminates the lua and
-- a guarded post-loop fallback re-tries exit), so EmuHawk no longer runs away
-- after the movie finishes. metadata.lua_script_version reports "3.6".
-- v3.7 changes: ADD two per-frame diagnostic AUX events (CSV schema UNCHANGED;
-- both are comparison-only context, never engine write-back). v3.6 traces stay
-- valid (the new aux_schema_extras keys gate the parser). (1) "v_objstate": the
-- full 192-byte object respawn-state bit array (v_objstate $FFFC00..$FFFCC0) as a
-- compact hex string -- unblocks the slot-interleave / slot-cadence cluster (LZ2
-- f1068, MZ3 f9917, SYZ1 f4430, SBZ1) by exposing whether ROM's respawn bit is
-- clear (respawn) vs the engine's set (skip) at a backward-OPL reload. (2)
-- "camera_boundary": v_limitbtm1/v_limitbtm2/v_lookshift/f_bgscrollvert -- unblocks
-- MZ1 f2101 (engine v_limitbtm2 ~6px high). NOTE: player x_sub/y_sub were ALREADY
-- in physics.csv columns 12-13 since v2.0, so no CSV change was needed for the
-- subpixel-trajectory frontiers (SBZ2 f2224, SYZ3 f6358). metadata
-- lua_script_version reports "3.7"; aux_schema_extras gains v_objstate_per_frame
-- and camera_boundary_per_frame.
-- v3.8 changes: ADD two per-object fields to the EXISTING object_near aux event
-- (CSV schema UNCHANGED; comparison-only context, never engine write-back). v3.7
-- traces stay valid (the new aux_schema_extras key gates the parser; the parser
-- treats both fields as legacy-absent-safe). (1) "routine2": ob2ndRout (object
-- offset +0x25) -- the object's secondary routine, pinning boss post-defeat phase
-- transitions the primary routine byte hides (GHZ boss Obj3D enters ESCAPE ~1f
-- early at GHZ3 f8569). (2) "objoff_3c": objoff_3C (offset +0x3C) as an unsigned
-- 32-bit big-endian word -- the generic timer / 32-bit sub-pixel accumulator
-- (BGHZ_BossGenericTimer; FZ cylinder Obj84 rise accumulator that seeds the player
-- x_sub at FZ f3901). Both ride the existing object_near proximity gate, so they
-- only cost bytes for objects already in the player window. metadata
-- lua_script_version reports "3.8"; aux_schema_extras gains
-- object_near_routine2_objoff3c.
-- v3.9 changes: ADD three per-object WORD fields to the EXISTING object_near aux
-- event (CSV schema UNCHANGED; comparison-only context, never engine write-back).
-- v3.8 traces stay valid (the new aux_schema_extras key gates the parser; the
-- parser treats all three as legacy-absent-safe). "objoff_34"/"objoff_36"/
-- "objoff_38" are the per-object counter/timer/sub-state words at offsets +0x34/
-- +0x36/+0x38 (read_u16_be). They pin object-COUNTER-phase frontiers where the
-- seat/spawn reaches a counter value one frame before ROM -- the GHZ3 1-frame-
-- counter-defer shape, now visible per-object: SLZ Staircase Obj5B ride counter
-- (SLZ1 f2872), geyser-maker timer (MZ2 f2819), platform oscillation phase, etc.
-- All three ride the existing object_near proximity gate, so they only cost bytes
-- for objects already in the player window. NB +0x38 here is the object-frame
-- objoff_38 WORD, distinct from the player-only OFF_STICK_CONVEX byte. metadata
-- lua_script_version reports "3.9"; aux_schema_extras gains
-- object_near_objoff_34_36_38.
------------------------------------------------------------------------------

-----------------
--- Constants ---
-----------------

-- Output directory (relative to BizHawk working dir).
-- MULTI-SEGMENT complete-run recorder: OUTPUT_DIR is reassigned per act to
-- BASE_OUTPUT_DIR<zone><act>/ (e.g. trace_output/ghz1/) on each arm. See on_frame_end.
local BASE_OUTPUT_DIR = "trace_output/"
local OUTPUT_DIR = BASE_OUTPUT_DIR

-- Headless mode: run at maximum speed, auto-exit when done.
-- Enable when running via CLI: EmuHawk.exe --chromeless --lua ... --movie ... rom.gen
local HEADLESS = true

-- Movie frame limit: set to 0 for automatic detection from movie.length().
-- When the BK2 movie ends but game_mode is still 0x0C (e.g. waiting for
-- results screen), the emulator would loop forever. This safety limit
-- ensures the script finalises and exits.
local MOVIE_FRAME_SAFETY_MARGIN = 30   -- frames past movie end before auto-exit

-- S1 REV01 68K RAM addresses (mainmemory domain = $FF0000 base stripped)
local ADDR_GAME_MODE       = 0xF600
local ADDR_CTRL1           = 0xF604   -- byte: v_jpadhold1 (raw held input)
local ADDR_CTRL1_DUP       = 0xF602   -- byte: v_jpadhold2 (game-logic copy, zeroed when locked)
local ADDR_RING_COUNT      = 0xFE20   -- word: ring count (BCD)
local ADDR_CAMERA_X        = 0xF700   -- long: v_screenposx (camera X pixel:sub)
local ADDR_CAMERA_Y        = 0xF704   -- long: v_screenposy (camera Y pixel:sub)
local ADDR_ZONE            = 0xFE10   -- byte: current zone number (v_zone)
local ADDR_ACT             = 0xFE11   -- byte: current act number (v_act)
local ADDR_RANDOM          = 0xF636   -- long: v_random pseudo-random number buffer

-- Player object base ($FFD000)
local PLAYER_BASE          = 0xD000
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
local OFF_ROUTINE_2ND      = 0x25   -- byte: ob2ndRout — object secondary routine
                                    --       (e.g. GHZ boss Obj3D post-defeat ESCAPE phase)
local OFF_OBJOFF_3C        = 0x3C   -- LONG (32-bit): objoff_3C — generic timer / 32-bit
                                    --       sub-pixel accumulator (e.g. BGHZ_BossGenericTimer,
                                    --       FZ cylinder Obj84 rise accumulator)
local OFF_OBJOFF_34        = 0x34   -- WORD: objoff_34 — per-object counter/sub-state
                                    --       (e.g. SLZ Staircase Obj5B ride counter)
local OFF_OBJOFF_36        = 0x36   -- WORD: objoff_36 — per-object timer/phase
                                    --       (e.g. platform oscillation phase, maker timer)
local OFF_OBJOFF_38        = 0x38   -- WORD: objoff_38 — per-object sub-state/timer
                                    --       (NB object-frame +0x38 word; distinct from the
                                    --       player-only OFF_STICK_CONVEX byte below)
local OFF_SUBTYPE          = 0x28   -- byte
local OFF_RENDER_FLAGS     = 0x01   -- byte: obRender
local OFF_ANGLE            = 0x26   -- byte: terrain angle
local OFF_STICK_CONVEX     = 0x38   -- byte
local OFF_STAND_ON_OBJ     = 0x3D   -- byte: standonobject — SST index Sonic stands on (0=none)
local OFF_CTRL_LOCK        = 0x3E   -- word: control lock timer

-- S1 Player routine values (obRoutine byte → table index):
--   0 = Sonic_Main (init)
--   2 = Sonic_Control (normal movement — handles ground, air, roll internally)
--   4 = Sonic_Hurt (recoil after damage)
--   6 = Sonic_Death
--   8 = Sonic_ResetLevel
-- NOTE: S1 has NO separate air/roll routines — Sonic_Control handles all of
-- those internally. This differs from S2 which splits into separate routines.

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
local OBJSTATE_SIZE         = 0xC0     -- 192 bytes (ds.b $C0; docs/s1disasm/sonic.lst v_objstate..v_objstate_end FFFC00..FFFCC0)
-- v_objstate[0] = forward counter, v_objstate[1] = backward counter

-- Camera vertical-boundary / look-shift state (v3.7 diagnostic, MZ1 f2101 cluster).
-- Absolute mainmemory addresses confirmed from docs/s1disasm/sonic.lst instruction
-- operands (e.g. `cmp.w (v_limitbtm2).w,d0` assembles to F72E):
local ADDR_LIMITBTM1       = 0xF726   -- word: v_limitbtm1 (primary bottom level boundary)
local ADDR_LIMITBTM2       = 0xF72E   -- word: v_limitbtm2 (secondary/eased bottom boundary the camera clamps to)
local ADDR_LOOKSHIFT       = 0xF73E   -- word: v_lookshift (up/down look screen shift; default $60)
local ADDR_BGSCROLLVERT    = 0xF75C   -- byte: f_bgscrollvert (bottom-boundary-moving / vertical bg-scroll flag)

-- Object table (S1 SST: 128 slots of $40 bytes at $FFD000)
local OBJ_TABLE_START      = 0xD000
local OBJ_SLOT_SIZE        = 0x40
local OBJ_TOTAL_SLOTS      = 128  -- total SST slots (0-127)
local OBJ_DYNAMIC_START    = 32   -- first dynamic slot (FindFreeObj starts here)
local OBJ_DYNAMIC_COUNT    = 96   -- dynamic slots 32-127

-- Frame counter (v_framecount at $FFFE04, word — increments each Level_MainLoop)
-- NOTE: 0xFE0C is v_vbla_count (longword, VBlank interrupt counter — different!)
local ADDR_FRAMECOUNT      = 0xFE04
local ADDR_VBLA_WORD       = 0xFE0E

-- Genesis joypad bitmask (matching engine convention)
local INPUT_UP    = 0x01
local INPUT_DOWN  = 0x02
local INPUT_LEFT  = 0x04
local INPUT_RIGHT = 0x08
local INPUT_JUMP  = 0x10

-- Game mode values
local GAMEMODE_LEVEL = 0x0C

-- Zone ID to short name mapping (matches s1disasm Constants.asm)
local ZONE_NAMES = {
    [0] = "ghz",   -- Green Hill Zone
    [1] = "lz",    -- Labyrinth Zone
    [2] = "mz",    -- Marble Zone
    [3] = "slz",   -- Star Light Zone
    [4] = "syz",   -- Spring Yard Zone
    [5] = "sbz",   -- Scrap Brain Zone
    [6] = "endz",  -- Ending Zone
    [7] = "ss",    -- Special Stage
}

-- v3.6 directory hygiene: pre-create every per-act segment dir in ONE shell-out
-- at load so the per-segment os.execute("mkdir") (which flashed a cmd window for
-- each zone) is gone. Defined here (after ZONE_NAMES / BASE_OUTPUT_DIR) so the
-- frame loop's ensure_segment_dir() reference resolves to this local.
local function precreate_segment_dirs()
    -- Strip any trailing slash before quoting: a trailing "\" inside a cmd-quoted
    -- path escapes the closing quote (`"trace_output\"` is malformed).
    local function quote_dir(p)
        local win = (p:gsub("/", "\\"))      -- parens: keep only the string, drop gsub's count
        win = (win:gsub("\\+$", ""))         -- drop trailing backslashes
        return "\"" .. win .. "\""
    end
    local quoted = { quote_dir(BASE_OUTPUT_DIR) }
    for _, zname in pairs(ZONE_NAMES) do
        for act = 1, 3 do
            quoted[#quoted + 1] = quote_dir(BASE_OUTPUT_DIR .. zname .. tostring(act))
        end
    end
    -- Windows mkdir takes multiple paths; 2>NUL swallows "already exists".
    -- One brief cmd window for the whole run.
    os.execute("mkdir " .. table.concat(quoted, " ") .. " 2>NUL")
end

-- Shell-free fallback for an UNKNOWN zone id whose dir was not pre-created
-- (start_zone_name = "unknown_XX"). Probes via a temp file; only shells out if
-- the dir genuinely does not exist, so the normal known-zone path never spawns a
-- console.
local function ensure_segment_dir(dir)
    local probe_path = dir .. ".oggf_dir_probe"
    local probe = io.open(probe_path, "w")
    if probe then
        probe:close()
        os.remove(probe_path)
        return  -- dir exists (pre-created); no shell-out.
    end
    os.execute("mkdir \"" .. (dir:gsub("/", "\\")) .. "\" 2>NUL")
end

-- Snapshot interval (frames between full state snapshots in aux file)
local SNAPSHOT_INTERVAL = 60

-- Object proximity radius (pixels) for per-frame nearby object logging
local OBJECT_PROXIMITY = 160

-----------------
--- State     ---
-----------------

local started = false
local finished = false   -- once true, never re-arm
local trace_frame = 0
local bk2_frame_offset = 0
local start_x = 0
local start_y = 0
local start_rng_seed = 0
local start_zone_id = 0
local start_zone_name = "unknown"
local start_act = 0

local prev_status = 0
local prev_routine = 0
local prev_ctrl_lock = 0
local prev_opl_screen = -1  -- track OPL chunk transitions

-- Object tracking: slot -> last known type ID
local known_objects = {}

-- File handles
local physics_file = nil
local aux_file = nil

-----------------
--- Helpers   ---
-----------------

-- Read a 16-bit signed value (big-endian)
local function read_speed(base, offset)
    return mainmemory.read_s16_be(base + offset)
end

-- Convert raw ROM joypad byte (v_jpadhold1) to engine input bitmask.
-- ROM bits: 0=Up 1=Down 2=Left 3=Right 4=B 5=C 6=A 7=Start
-- Bits 0-3 already match INPUT_UP/DOWN/LEFT/RIGHT; collapse A/B/C to JUMP.
local function rom_joypad_to_mask(raw)
    local mask = raw & 0x0F                        -- directions (bits 0-3)
    if (raw & 0x70) ~= 0 then mask = mask + INPUT_JUMP end  -- A|B|C -> JUMP
    return mask
end

-- Mirrors the S2 recorder's BK2-derived input read. ROM-side v_jpadhold1
-- updates from inside ReadJoypads which runs only from specific V-int
-- subroutines; on lag frames and during long V-int paths the written byte
-- can lag the BK2 logical input by one game frame, producing spurious
-- "Input alignment error" failures in AbstractCreditsDemoTraceReplayTest.
-- Read the BK2 movie input directly so the CSV input column matches what
-- the test fixture's BK2 reader will see during validation.
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

-----------------
--- Recording ---
-----------------

local function open_files()
    physics_file = io.open(OUTPUT_DIR .. "physics.csv", "w")
    aux_file = io.open(OUTPUT_DIR .. "aux_state.jsonl", "w")

    -- v3 header: gameplay/VBlank execution counters plus stand_on_obj.
    physics_file:write("frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,"
        .. "x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,"
        .. "vblank_counter,lag_counter\n")
    physics_file:flush()
end

local function write_metadata()
    -- Use zone/act captured at recording start (not current RAM which may have advanced)
    local meta_file = io.open(OUTPUT_DIR .. "metadata.json", "w")
    meta_file:write("{\n")
    meta_file:write('  "game": "s1",\n')
    meta_file:write('  "zone": "' .. start_zone_name .. '",\n')
    meta_file:write('  "zone_id": ' .. start_zone_id .. ',\n')
    meta_file:write('  "act": ' .. (start_act + 1) .. ',\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "start_x": "0x' .. hex(start_x) .. '",\n')
    meta_file:write('  "start_y": "0x' .. hex(start_y) .. '",\n')
    meta_file:write('  "rng_seed": "0x' .. hex(start_rng_seed, 8) .. '",\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    meta_file:write('  "lua_script_version": "3.9",\n')
    meta_file:write('  "trace_schema": 3,\n')
    meta_file:write('  "csv_version": 4,\n')
    meta_file:write('  "aux_schema_extras": ["s1_obj64_state_per_frame", "object_near_obj_frame", '
        .. '"v_objstate_per_frame", "camera_boundary_per_frame", "object_near_routine2_objoff3c", '
        .. '"object_near_objoff_34_36_38"],\n')
    meta_file:write('  "rom_checksum": "",\n')
    meta_file:write('  "notes": "",\n')
    -- The complete-run recorder always plays the shared complete-run BK2. Emit
    -- source_bk2 so AbstractTraceReplayTest's _movies/ resolver finds it; without
    -- it the regenerated trace silently SKIPS (no BK2 -> Assumptions.assumeTrue).
    meta_file:write('  "source_bk2": "s1-complete-run.bk2"\n')
    meta_file:write("}\n")
    meta_file:close()
    print(string.format("Metadata written. Zone: %s Act %d, Trace frames: %d",
        start_zone_name, start_act + 1, trace_frame))
end

local function close_files()
    if physics_file then
        physics_file:close()
        physics_file = nil
    end
    if aux_file then
        aux_file:close()
        aux_file = nil
    end
end

-- Build a compact summary of ALL occupied dynamic slots (32-127).
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

local function write_s1_obj64_state(slot, addr, vfc)
    local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
    local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"s1_obj64_state","slot":%d,'
        .. '"x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X",'
        .. '"render_flags":"0x%02X","subtype":"0x%02X","anim":"0x%02X",'
        .. '"objoff_32":"0x%02X","objoff_33":"0x%02X",'
        .. '"objoff_34":"0x%04X","objoff_36":"0x%04X","objoff_38":"0x%04X",'
        .. '"objoff_3c":"0x%08X"}',
        trace_frame,
        vfc,
        slot,
        obj_x,
        obj_y,
        mainmemory.read_u8(addr + OFF_ROUTINE),
        mainmemory.read_u8(addr + OFF_STATUS),
        mainmemory.read_u8(addr + OFF_RENDER_FLAGS),
        mainmemory.read_u8(addr + OFF_SUBTYPE),
        mainmemory.read_u8(addr + OFF_ANIM_ID),
        mainmemory.read_u8(addr + 0x32),
        mainmemory.read_u8(addr + 0x33),
        mainmemory.read_u16_be(addr + 0x34),
        mainmemory.read_u16_be(addr + 0x36),
        mainmemory.read_u16_be(addr + 0x38),
        mainmemory.read_u32_be(addr + 0x3C)))
end

-- v3.7: full v_objstate respawn-bit array dump (compact hex), every frame.
-- Unblocks the slot-interleave / slot-cadence cluster (LZ2 f1068, MZ3 f9917,
-- SYZ1 f4430, SBZ1): the comparator can see, at a backward-OPL reload, whether
-- the ROM respawn bit is CLEAR (ROM respawns the object) vs the engine's
-- objState bit SET (engine skips the respawn) -- the exact LZ2 f217-style root.
-- v_objstate[0]/[1] are the OPL fwd/bwd counters; [2..] are per-spawn remember
-- bits indexed by RememberState. Diagnostic context ONLY (never write-back).
local function write_v_objstate()
    local parts = {}
    for i = 0, OBJSTATE_SIZE - 1 do
        parts[#parts + 1] = string.format("%02X", mainmemory.read_u8(ADDR_OBJSTATE + i))
    end
    write_aux(string.format(
        '{"frame":%d,"event":"v_objstate","bytes":"%s"}',
        trace_frame, table.concat(parts)))
end

-- v3.7: camera vertical-boundary / look-shift state, every frame.
-- Unblocks MZ1 f2101 (engine v_limitbtm2 ~6px too high): logging v_limitbtm2 /
-- v_limitbtm1 / v_lookshift / f_bgscrollvert resolves the +6-vs-+2 / clamp
-- ordering deterministically. Diagnostic context ONLY (never write-back).
local function write_camera_boundary()
    write_aux(string.format(
        '{"frame":%d,"event":"camera_boundary","limitbtm1":"0x%04X","limitbtm2":"0x%04X",'
        .. '"lookshift":"0x%04X","bgscrollvert":"0x%02X"}',
        trace_frame,
        mainmemory.read_u16_be(ADDR_LIMITBTM1),
        mainmemory.read_u16_be(ADDR_LIMITBTM2),
        mainmemory.read_u16_be(ADDR_LOOKSHIFT),
        mainmemory.read_u8(ADDR_BGSCROLLVERT)))
end

-- Scan all object slots (1-127). Log appearances, disappearances, proximity,
-- and emit a full slot_dump when any dynamic object appears.
local function scan_objects(player_x, player_y)
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

        -- Proximity check: log active objects near the player every frame.
        -- This captures the exact position of objects involved in collisions
        -- without needing to add temporary diagnostic code to the engine.
        if obj_id ~= 0 then
            if obj_id == 0x64 then
                write_s1_obj64_state(slot, addr, vfc)
            end
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local dx = math.abs(obj_x - player_x)
            local dy = math.abs(obj_y - player_y)
            if dx <= OBJECT_PROXIMITY and dy <= OBJECT_PROXIMITY then
                local obj_status = mainmemory.read_u8(addr + OFF_STATUS)
                local obj_routine = mainmemory.read_u8(addr + OFF_ROUTINE)
                -- obj_frame = obFrame / OFF_ANIM_FRAME_DISP (0x1A). For tilt/anim
                -- objects (e.g. SLZ seesaw Obj5E) this is the mapping/tilt frame,
                -- needed to compare object-anim-phase against the engine.
                local obj_frame = mainmemory.read_u8(addr + OFF_ANIM_FRAME_DISP)
                -- ob2ndRout (offset +0x25): object secondary routine. Pins boss
                -- post-defeat phase transitions (e.g. GHZ boss Obj3D ESCAPE) that
                -- the primary routine byte alone does not reveal.
                local obj_routine2 = mainmemory.read_u8(addr + OFF_ROUTINE_2ND)
                -- objoff_3C (offset +0x3C): 32-bit generic timer / sub-pixel
                -- accumulator (BGHZ_BossGenericTimer; FZ cylinder Obj84 rise). Read
                -- as an unsigned 32-bit big-endian word; consumers reinterpret the
                -- high word as a signed 16-bit pixel offset where appropriate.
                local obj_off3c = mainmemory.read_u32_be(addr + OFF_OBJOFF_3C)
                -- objoff_34/36/38 (offsets +0x34/+0x36/+0x38): per-object
                -- counter/timer/sub-state WORDs. Pin object-counter-phase frontiers
                -- where the seat/spawn reaches a counter value one frame before ROM
                -- (e.g. SLZ Staircase Obj5B ride counter at SLZ1 f2872, geyser-maker
                -- timer at MZ2 f2819) — the GHZ3 1-frame-counter-defer shape, now
                -- visible per-object.
                local obj_off34 = mainmemory.read_u16_be(addr + OFF_OBJOFF_34)
                local obj_off36 = mainmemory.read_u16_be(addr + OFF_OBJOFF_36)
                local obj_off38 = mainmemory.read_u16_be(addr + OFF_OBJOFF_38)
                write_aux(string.format(
                    '{"frame":%d,"vfc":%d,"event":"object_near","slot":%d,"type":"0x%02X",'
                    .. '"x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X","obj_frame":"0x%02X",'
                    .. '"routine2":"0x%02X","objoff_3c":"0x%08X",'
                    .. '"objoff_34":"0x%04X","objoff_36":"0x%04X","objoff_38":"0x%04X"}',
                    trace_frame, vfc, slot, obj_id, obj_x, obj_y, obj_routine, obj_status, obj_frame,
                    obj_routine2, obj_off3c, obj_off34, obj_off36, obj_off38))
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

local function write_state_snapshot()
    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local anim_id = mainmemory.read_u8(PLAYER_BASE + OFF_ANIM_ID)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local routine = mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE)
    local y_radius = mainmemory.read_s8(PLAYER_BASE + OFF_RADIUS_Y)
    local x_radius = mainmemory.read_s8(PLAYER_BASE + OFF_RADIUS_X)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"state_snapshot","control_locked":%s,"anim_id":%d,'
        .. '"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,'
        .. '"on_object":%s,"pushing":%s,"underwater":%s,'
        .. '"roll_jumping":%s}',
        trace_frame,
        vfc,
        ctrl_lock > 0 and "true" or "false",
        anim_id,
        status,
        routine,
        y_radius,
        x_radius,
        ((status & STATUS_ON_OBJECT) ~= 0) and "true" or "false",
        ((status & STATUS_PUSHING) ~= 0) and "true" or "false",
        ((status & STATUS_UNDERWATER) ~= 0) and "true" or "false",
        ((status & STATUS_ROLL_JUMP) ~= 0) and "true" or "false"
    ))
end

local function check_mode_changes(status, routine)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    local was_air = (prev_status & STATUS_IN_AIR) ~= 0
    local is_air = (status & STATUS_IN_AIR) ~= 0
    if was_air ~= is_air then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"air","from":%d,"to":%d}',
            trace_frame, vfc, was_air and 1 or 0, is_air and 1 or 0))
        write_state_snapshot()
    end

    local was_rolling = (prev_status & STATUS_ROLLING) ~= 0
    local is_rolling = (status & STATUS_ROLLING) ~= 0
    if was_rolling ~= is_rolling then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"rolling","from":%d,"to":%d}',
            trace_frame, vfc, was_rolling and 1 or 0, is_rolling and 1 or 0))
    end

    local was_on_obj = (prev_status & STATUS_ON_OBJECT) ~= 0
    local is_on_obj = (status & STATUS_ON_OBJECT) ~= 0
    if was_on_obj ~= is_on_obj then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"on_object","from":%d,"to":%d}',
            trace_frame, vfc, was_on_obj and 1 or 0, is_on_obj and 1 or 0))
    end

    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local was_locked = prev_ctrl_lock > 0
    local is_locked = ctrl_lock > 0
    if was_locked ~= is_locked then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"control_locked","from":%d,"to":%d}',
            trace_frame, vfc, was_locked and 1 or 0, is_locked and 1 or 0))
    end
    prev_ctrl_lock = ctrl_lock

    -- Routine transition detection (S1 obRoutine raw values: 0=init, 2=control,
    -- 4=hurt, 6=death, 8=reset).
    -- Emit a rich event with full Sonic state and the object Sonic is standing on
    -- (if any). Especially valuable for hurt transitions (2→4).
    if routine ~= prev_routine then
        local stand_on_obj = mainmemory.read_u8(PLAYER_BASE + OFF_STAND_ON_OBJ)
        local sonic_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
        local sonic_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
        local sonic_xvel = mainmemory.read_s16_be(PLAYER_BASE + OFF_X_VEL)
        local sonic_yvel = mainmemory.read_s16_be(PLAYER_BASE + OFF_Y_VEL)
        local sonic_inertia = mainmemory.read_s16_be(PLAYER_BASE + OFF_INERTIA)

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
            '{"frame":%d,"vfc":%d,"event":"routine_change","from":"0x%02X","to":"0x%02X",'
            .. '"sonic_x":"0x%04X","sonic_y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,'
            .. '"status":"0x%02X","stand_on_obj":%d%s}',
            trace_frame, vfc, prev_routine, routine,
            sonic_x, sonic_y, sonic_xvel, sonic_yvel, sonic_inertia,
            status, stand_on_obj, obj_context))

        -- On hurt/death transitions, also emit a full state snapshot for maximum context
        -- S1: hurt=0x04, death=0x06. S2: hurt=0x08, death=0x0A.
        if routine == 0x04 or routine == 0x06 then
            write_state_snapshot()
        end
    end
    prev_routine = routine
end

-----------------
--- Main Loop ---
-----------------

local function on_frame_end()
    local game_mode = mainmemory.read_u8(ADDR_GAME_MODE)

    -- MULTI-SEGMENT: stop the whole pass at min(S1_STOP_AT_FRAME, movie_end).
    -- v3.6: the movie-end / FINISHED checks are NO LONGER gated on `started`, so
    -- the pass also terminates if the movie ends before gameplay is ever detected
    -- (previously such a run -- or one where S1_STOP_AT_FRAME was unset and
    -- movie.length() under-reported -- looped forever past the movie, leaving
    -- EmuHawk running away). Whichever of the stop bounds is reached first wins.
    local stop_at = tonumber(os.getenv("S1_STOP_AT_FRAME") or "0")
    local frame_now = emu.framecount()
    local movie_len = movie.isloaded() and movie.length() or 0
    local movie_done = (movie_len > 0 and frame_now >= movie_len)
        or (movie.isloaded() and movie.mode() == "FINISHED")
    local stop_reached = stop_at > 0 and frame_now >= stop_at
    if stop_reached or movie_done then
        if started then
            if physics_file then physics_file:flush() end
            write_metadata()
            close_files()
            started = false
        end
        finished = true
        return
    end

    if not started then
        if finished then return end
        -- Start when: level gameplay active AND player control lock timer is 0.
        -- The control lock timer (obCtrlLock, word at $D03E) is set during the title
        -- card and counts down to 0 when Sonic can first move. Using the player object's
        -- lock timer is correct; the old v_jpadhold1 check waited for "no buttons held"
        -- which delayed recording if the player was already pressing a direction.
        local ctrl_lock_timer = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
        if game_mode == GAMEMODE_LEVEL and ctrl_lock_timer == 0 then
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
            start_zone_id = mainmemory.read_u8(ADDR_ZONE)
            start_act = mainmemory.read_u8(ADDR_ACT)
            start_zone_name = ZONE_NAMES[start_zone_id] or string.format("unknown_%02x", start_zone_id)
            trace_frame = 0

            -- MULTI-SEGMENT: per-act output dir, e.g. trace_output/ghz1/ (zone name + 1-based act).
            -- v3.6: the directory was pre-created at load (precreate_segment_dirs), so
            -- NO per-segment os.execute("mkdir") here -- that popped a cmd-window for
            -- every zone segment. ensure_segment_dir is a no-op shell-free fallback that
            -- only fires (one shell-out) for an unexpected/unknown zone id not covered
            -- by the pre-created set.
            OUTPUT_DIR = BASE_OUTPUT_DIR .. start_zone_name .. tostring(start_act + 1) .. "/"
            ensure_segment_dir(OUTPUT_DIR)

            open_files()
            -- Write metadata immediately so it exists even if the process is killed
            write_metadata()
            print(string.format("Trace recording started at BizHawk frame %d, zone %s act %d, pos (%04X, %04X)",
                bk2_frame_offset, start_zone_name, start_act + 1, start_x, start_y))
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
        -- MULTI-SEGMENT: finalise this act and RE-ARM for the next (do NOT exit).
        -- Act transitions go 0x0C -> 0x8C -> 0x0C, so leaving LEVEL ends the act;
        -- the next 0x0C (with ctrl_lock cleared) starts the following act's segment.
        print(string.format("Segment end: %s act %d, %d frames. Finalising + re-arming.",
            start_zone_name, start_act + 1, trace_frame))
        if physics_file then physics_file:flush() end
        write_metadata()
        close_files()
        started = false
        trace_frame = 0
        return
    end

    -- Stop exactly when the trace would need an input frame past the end of
    -- the loaded BK2. BizHawk's movie mode can lag behind in chromeless runs,
    -- which lets the recorder append no-input tail frames that replay cannot
    -- consume later.
    if HEADLESS and movie.isloaded() then
        local movie_length = movie.length()
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

    -- Derive CSV `input` column from BK2 movie directly so the recorded
    -- value matches AbstractCreditsDemoTraceReplayTest's BK2 reader; ROM-
    -- side v_jpadhold1 is updated by ReadJoypads which only runs inside
    -- specific V-int subroutines and can lag the BK2 by a frame on lag-
    -- frame paths. raw_input still feeds the state_snapshot aux event.
    local raw_input = mainmemory.read_u8(0xF604)  -- v_jpadhold1
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

    -- vblank_counter ticks every VBlank. Sonic 1 does not expose a dedicated
    -- lag counter, so write 0 as a diagnostic placeholder in schema v3.
    local vblank_counter = mainmemory.read_u16_be(ADDR_VBLA_WORD)
    local lag_counter = 0

    -- v3 CSV: execution counters plus stand_on_obj.
    physics_file:write(string.format(
        "%04X,%04X,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%04X,%04X,%04X,%02X,%04X,%02X,%04X,%04X\n",
        trace_frame, input_mask, x, y,
        uhex(x_speed), uhex(y_speed), uhex(g_speed),
        angle,
        air and 1 or 0,
        rolling and 1 or 0,
        ground_mode,
        x_sub, y_sub,
        routine,
        camera_x, camera_y,
        rings,
        status,
        gameplay_frame_counter,
        stand_on_obj,
        vblank_counter,
        lag_counter))
    -- Flush periodically instead of every frame to reduce I/O overhead.
    -- Also update metadata every 300 frames (~5 sec) so a killed process
    -- still has a valid (if slightly stale) metadata.json.
    if trace_frame % 60 == 0 then
        physics_file:flush()
    end
    if trace_frame % 300 == 0 then
        write_metadata()
    end

    check_mode_changes(status, routine)
    prev_status = status

    if trace_frame % SNAPSHOT_INTERVAL == 0 then
        write_state_snapshot()
    end

    -- Object scanning: every frame for proximity, every 4 frames for full scan
    -- Proximity logging runs every frame so we never miss collision-relevant objects.
    scan_objects(x, y)

    -- v3.7 per-frame diagnostic context (comparison-only): the object respawn-bit
    -- array (slot-cadence cluster) and the camera vertical-boundary state (MZ1).
    write_v_objstate()
    write_camera_boundary()

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

-- v3.6: All segment dirs are pre-created at load by precreate_segment_dirs()
-- (defined just after ZONE_NAMES). This single load-time shell-out replaces the
-- old per-segment os.execute("mkdir") that flashed one cmd window per zone.
precreate_segment_dirs()

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
print("S1 Trace Recorder v3.7 loaded. Waiting for level gameplay (Game_Mode=0x0C, controls unlocked)...")

-- v3.6 hard safety net: even if every movie-end signal fails (movie.length()==0,
-- mode never reports FINISHED, S1_STOP_AT_FRAME unset), the loop must not run
-- forever. Cap at the movie length (+ a small margin) when known, else a large
-- absolute bound. This is the backstop that prevents the runaway EmuHawk.
local function absolute_frame_cap()
    local len = movie.isloaded() and movie.length() or 0
    if len > 0 then
        return len + 64  -- a few frames past the movie to let finalisation land
    end
    return 2000000       -- ~9h of frames; far beyond any S1 complete-run BK2
end
local FRAME_CAP = absolute_frame_cap()

while true do
    on_frame_end()

    -- Backstop: force-finish if we somehow blew past the movie/cap without any
    -- normal stop signal firing.
    if not finished and emu.framecount() >= FRAME_CAP then
        print(string.format(
            "Frame cap %d reached without a movie-end signal; finalising and exiting.", FRAME_CAP))
        if started then
            if physics_file then physics_file:flush() end
            write_metadata()
            close_files()
            started = false
        end
        finished = true
    end

    -- If recording is done, finalise files and exit from INSIDE the loop.
    -- Code after the loop may never execute because client.exit() kills
    -- the process immediately.
    if finished then
        -- MULTI-SEGMENT: on_frame_end already finalised the last open segment
        -- (write_metadata + close_files) at movie-end, so do NOT re-finalise here
        -- (start_*/trace_frame are reset and would corrupt the last segment's metadata).
        print("All segments recorded. Exiting.")
        break
    end

    -- If paused (e.g. BizHawk pauses on movie end), unpause so we get
    -- another iteration to detect the FINISHED state and exit cleanly.
    if client.ispaused() then
        client.unpause()
    end

    emu.frameadvance()
end

-- v3.6 reliable termination: client.exit() is a no-op on some BizHawk builds
-- (the recorded "kept running" symptom). All files are already flushed/closed by
-- the finalisation above, so it is safe to (a) call client.exit(), then (b) if it
-- returns at all, keep advancing while re-issuing exit and pausing -- so EmuHawk
-- stops chewing CPU/RAM even where the first exit did nothing. emu.frameadvance()
-- yields control back to the host so a working exit can take effect.
if HEADLESS then
    for _ = 1, 8 do
        client.exit()
        if client.ispaused() then client.unpause() end
        emu.frameadvance()
    end
    -- Last resort if the build truly ignores client.exit(): pause so EmuHawk idles
    -- (0% CPU) instead of free-running the movie. The host launcher's process kill
    -- / tasklist check then reaps it without a multi-GB runaway.
    client.pause()
end
