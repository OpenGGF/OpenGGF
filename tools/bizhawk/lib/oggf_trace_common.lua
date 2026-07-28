------------------------------------------------------------------------------
-- oggf_trace_common.lua
-- Shared, game-agnostic leaf helpers for the BizHawk trace recorders in
-- tools/bizhawk/. Consumed by the per-game recorders via a robust loader
-- (see SHARED_MODULE_HANDOFF.md). This module holds ONLY pure leaf helpers
-- whose output is byte-for-byte identical to the inline copies they replace.
--
-- HARD CONSTRAINT: the trace comparator diffs emitted bytes. Every helper here
-- is a verbatim copy of the previously-inlined body (identical string.format
-- specifiers and arithmetic). Do NOT "improve" formatting or merge distinct
-- helpers (json_escape vs json_quote differ deliberately — see below).
--
-- SCOPE: leaf helpers only. Do NOT move schema writers (open_files,
-- write_metadata, write_run_manifest, build_slot_dump, ...), *_csv_version /
-- schema constants, or the fast-headless toggle block into this module. Those
-- embed per-game schema bytes and/or are statically grepped by
-- prepare_bizhawk_fast_lua.ps1 and MUST stay inline per recorder.
--
-- Two helpers take former file-scope upvalues as parameters so they stay pure:
--   bk2_input_mask(fallback_raw, trace_row, bk2_frame_offset, frame_adjustment)
--   write_aux(aux_file, json_str)                              -- was aux_file
-- Both also call BizHawk globals (mainmemory/movie/emu) which are present in
-- this module's environment when loaded from a recorder.
------------------------------------------------------------------------------

local M = {}

-- Genesis joypad bitmask (matching engine convention)
local INPUT_UP    = 0x01
local INPUT_DOWN  = 0x02
local INPUT_LEFT  = 0x04
local INPUT_RIGHT = 0x08
local INPUT_JUMP  = 0x10

M.INPUT_UP    = INPUT_UP
M.INPUT_DOWN  = INPUT_DOWN
M.INPUT_LEFT  = INPUT_LEFT
M.INPUT_RIGHT = INPUT_RIGHT
M.INPUT_JUMP  = INPUT_JUMP

-- Read a 16-bit signed value (big-endian)
local function read_speed(base, offset)
    return mainmemory.read_s16_be(base + offset)
end
M.read_speed = read_speed

-- Convert raw ROM joypad byte to engine input bitmask.
-- ROM bits: 0=Up 1=Down 2=Left 3=Right 4=B 5=C 6=A 7=Start
-- Bits 0-3 already match INPUT_UP/DOWN/LEFT/RIGHT; collapse A/B/C to JUMP.
local function rom_joypad_to_mask(raw)
    local mask = raw & 0x0F                        -- directions (bits 0-3)
    if (raw & 0x70) ~= 0 then mask = mask + INPUT_JUMP end  -- A|B|C -> JUMP
    return mask
end
M.rom_joypad_to_mask = rom_joypad_to_mask

-- Read the BK2 movie's logical input for the just-completed frame and convert
-- it to the engine's input bitmask. Bypasses ROM-side staleness in the held-
-- input byte which can lag the BK2 logical input by a game frame on lag frames
-- and long V-int paths, producing spurious "Input alignment error" failures.
-- The replay fixture reads the same BK2 directly, so movie.getinput here keeps
-- the trace's `input` column aligned with what the replay sees. Falls back to
-- the RAM-derived mask when no movie is loaded.
--
-- bk2_frame_offset is the recorder's own upvalue passed in to keep this pure:
-- replay metadata defines trace row N as BK2 frame (bk2_frame_offset + N).
local function bk2_input_mask(
        fallback_raw, trace_row, bk2_frame_offset, frame_adjustment)
    if not movie.isloaded() then
        return rom_joypad_to_mask(fallback_raw)
    end
    -- Replay metadata defines trace row N as BK2 frame
    -- (bk2_frame_offset + N). Use that same convention here; direct
    -- emu.framecount() is one frame ahead in this recorder loop.
    local frame_index = bk2_frame_offset ~= nil
        and trace_row ~= nil
        and (bk2_frame_offset + trace_row + (frame_adjustment or 0))
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
M.bk2_input_mask = bk2_input_mask

-- Format a number as hex with specified width
local function hex(val, width)
    width = width or 4
    if val < 0 then
        val = val + 0x10000
    end
    return string.format("%0" .. width .. "X", val)
end
M.hex = hex

-- Get ground mode from angle (offset quadrants matching ROM thresholds).
-- Floor wraps: 0xE0-0xFF and 0x00-0x1F are both mode 0.
local function angle_to_ground_mode(angle)
    if angle <= 0x1F or angle >= 0xE0 then return 0 end   -- floor
    if angle >= 0x20 and angle <= 0x5F then return 1 end   -- right wall
    if angle >= 0x60 and angle <= 0x9F then return 2 end   -- ceiling
    return 3                                                 -- left wall
end
M.angle_to_ground_mode = angle_to_ground_mode

-- Write a JSONL line to aux file. aux_file is the recorder's own handle passed
-- in to keep this pure (was a file-scope upvalue).
local function write_aux(aux_file, json_str)
    if aux_file then
        aux_file:write(json_str .. "\n")
        aux_file:flush()
    end
end
M.write_aux = write_aux

-- Escape a value for embedding inside a JSON string. Returns the escaped text
-- WITHOUT surrounding quotes (S1/S2 recorders quote separately). Distinct from
-- json_quote below — do NOT merge them.
local function json_escape(value)
    value = tostring(value or "")
    value = value:gsub("\\", "\\\\")
    value = value:gsub('"', '\\"')
    return value
end
M.json_escape = json_escape

-- Escape a value AND wrap it in double quotes, yielding a complete JSON string
-- literal (S3K recorders use this form). Distinct from json_escape above — do
-- NOT merge them; their outputs differ (quotes vs no quotes).
local function json_quote(value)
    return '"' .. tostring(value)
        :gsub("\\", "\\\\")
        :gsub('"', '\\"') .. '"'
end
M.json_quote = json_quote

return M
