-- Scratch-only S1 PLC execute-hook probe (S1 World REV01, SHA-1 69e102...).
-- It intentionally does not touch the canonical recorder or any fixture path.
local output_path = os.getenv("OGGF_PLC_PROBE_OUTPUT")
if not output_path or output_path == "" then error("OGGF_PLC_PROBE_OUTPUT is required") end
if io.open(output_path, "r") then error("refusing to overwrite " .. output_path) end
local out = assert(io.open(output_path, "w"))

local PLC_BUFFER, PLC_DEST, PLC_LEFT = 0xF680, 0xF684, 0xF6F8
local GAME_MODE, VINT, HBLANK_DEFERRED, FRAME_COUNT = 0xF600, 0xF62A, 0xF64F, 0xF7D6
local sequence, active_source = 0, 0
local function u8(a) return mainmemory.read_u8(a) end
local function u16(a) return mainmemory.read_u16_be(a) end
local function u32(a) return mainmemory.read_u32_be(a) end
local function slots()
  local n = 0
  for i = 0, 15 do if u32(PLC_BUFFER + i * 6) ~= 0 then n = n + 1 end end
  return n
end
local function emit(event, extra)
  sequence = sequence + 1
  local fields = string.format('"raw_frame":%d,"within_frame_order":%d,"event":"%s","game_mode":%d,"interrupt_handler":%d,"lag":%s,"hblank_deferred":%s,"queue_source":%d,"queue_destination":%d,"patterns_left_before":%d,"patterns_left_after":%d,"queue_slots":%d',
    emu.framecount(), sequence, event, u8(GAME_MODE), u8(VINT), u8(VINT) == 0 and "true" or "false", u8(HBLANK_DEFERRED) ~= 0 and "true" or "false", u32(PLC_BUFFER), u16(PLC_DEST), u16(PLC_LEFT), u16(PLC_LEFT), slots())
  out:write("{" .. fields .. (extra or "") .. "}\n"); out:flush()
end
-- ROM addresses pinned from sonic.asm: AddPLC 0x1566, NewPLC 0x159A,
-- ClearPLC 0x15DA, RunPLC 0x15EC, 9-tile 0x1642, 3-tile 0x165E, pop 0x16DC.
event.onmemoryexecute(function() emit("plc_submission", ',"operation":"append","plc_id":' .. ((emu.getregister("M68K D0") or 0) % 0x10000)) end, 0x1566)
event.onmemoryexecute(function() emit("plc_submission", ',"operation":"replace","plc_id":' .. ((emu.getregister("M68K D0") or 0) % 0x10000)) end, 0x159A)
event.onmemoryexecute(function() emit("plc_submission", ',"operation":"clear"') end, 0x15DA)
event.onmemoryexecute(function() active_source = u32(PLC_BUFFER); emit("plc_prepare_begin") end, 0x15EC)
-- The instruction after retail PatternsLeft publication is the begin/end race boundary.
event.onmemoryexecute(function() emit("plc_prepare_end") end, 0x161A)
event.onmemoryexecute(function() emit("plc_service") end, 0x1642)
event.onmemoryexecute(function() emit("plc_service") end, 0x165E)
event.onmemoryexecute(function() emit("plc_pop") end, 0x16DC)
event.onframeend(function() sequence = 0 end)
while true do emu.frameadvance() end
