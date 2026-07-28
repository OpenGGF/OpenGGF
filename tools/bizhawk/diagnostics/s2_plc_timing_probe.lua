-- Scratch-only S2 PLC execute-hook probe (S2 World REV01, SHA-1 8bca5d...).
-- It intentionally does not touch the canonical recorder or any fixture path.
--
-- Each *_PRE/*_POST pair must be the reviewed instructions immediately before
-- and after the retail mutation. Do not substitute routine entry addresses.
local output_path = os.getenv("OGGF_PLC_PROBE_OUTPUT")
if not output_path or output_path == "" then error("OGGF_PLC_PROBE_OUTPUT is required") end
if io.open(output_path, "r") then error("refusing to overwrite " .. output_path) end
local out = assert(io.open(output_path, "w"))

local function required_address(name)
  local value = tonumber(os.getenv(name))
  if not value then error(name .. " must be a reviewed ROM/RAM address") end
  return value
end
local consumer_hooks = os.getenv("OGGF_PLC_CONSUMER_HOOKS")
if not consumer_hooks or consumer_hooks == "" then error("OGGF_PLC_CONSUMER_HOOKS is required") end

local PLC_BUFFER = required_address("OGGF_PLC_BUFFER_RAM")
local PLC_DEST = required_address("OGGF_PLC_DEST_RAM")
local PLC_LEFT = required_address("OGGF_PLC_LEFT_RAM")
local GAME_MODE = required_address("OGGF_PLC_GAME_MODE_RAM")
local VINT = required_address("OGGF_PLC_INTERRUPT_HANDLER_RAM")
local LAG_HANDLER = required_address("OGGF_PLC_LAG_HANDLER")
local ADD = required_address("OGGF_PLC_ADD_ENTRY")
local ADD_POST = required_address("OGGF_PLC_ADD_POST")
local REPLACE_BEGIN = required_address("OGGF_PLC_REPLACE_BEGIN")
local REPLACE_POST = required_address("OGGF_PLC_REPLACE_POST")
local CLEAR_BEGIN = required_address("OGGF_PLC_CLEAR_BEGIN")
local CLEAR_POST = required_address("OGGF_PLC_CLEAR_POST")
local PREPARE_BEGIN = required_address("OGGF_PLC_PREPARE_BEGIN")
local PREPARE_END = required_address("OGGF_PLC_PREPARE_END")
local FULL_PRE = required_address("OGGF_PLC_FULL_SERVICE_PRE")
local PARTIAL_SERVICE_POST = required_address("OGGF_PLC_PARTIAL_SERVICE_POST")
local SMALL_PRE = required_address("OGGF_PLC_SMALL_SERVICE_PRE")
local POP_PRE = required_address("OGGF_PLC_POP_PRE")
local POP_POST = required_address("OGGF_PLC_POP_POST")
local VINT_DISPATCH = required_address("OGGF_PLC_VINT_DISPATCH")
local HBLANK_DEFERRED_ENTRY = required_address("OGGF_PLC_HBLANK_DEFERRED_ENTRY")

local sequence, active_source, append, replacement, clear_before, preparing, pending_service = 0, 0, nil, nil, nil, nil
local frame_handler, frame_lag, frame_hblank, frame_mode = 0, true, false, 0
local function u8(a) return mainmemory.read_u8(a) end
local function u16(a) return mainmemory.read_u16_be(a) end
local function u32(a) return mainmemory.read_u32_be(a) end
local function slots()
  local n = 0
  for i = 0, 15 do if u32(PLC_BUFFER + i * 6) ~= 0 then n = n + 1 end end
  return n
end
local function snapshot() return { left = u16(PLC_LEFT), slot_count = slots() } end
local function emit(event, extra, source, before)
  local after = snapshot()
  before = before or after
  sequence = sequence + 1
  local fields = string.format('"raw_frame":%d,"within_frame_order":%d,"event":"%s","game_mode":%d,"interrupt_handler":%d,"lag":%s,"hblank_deferred":%s,"queue_source":%d,"queue_destination":%d,"patterns_left_before":%d,"patterns_left_after":%d,"queue_slots_before":%d,"queue_slots_after":%d',
    emu.framecount(), sequence, event, frame_mode, frame_handler,
    frame_lag and "true" or "false", frame_hblank and "true" or "false",
    source or u32(PLC_BUFFER), u16(PLC_DEST), before.left, after.left,
    before.slot_count, after.slot_count)
  out:write("{" .. fields .. (extra or "") .. "}\n"); out:flush()
end

event.onmemoryexecute(function()
  if append then error("append began before previous append completed") end
  append = { id = (emu.getregister("M68K D0") or 0) % 0x10000, before = snapshot() }
end, ADD)
event.onmemoryexecute(function()
  if not append then error("append post reached without append begin") end
  emit("plc_submission", ',"operation":"append","plc_id":' .. append.id, nil, append.before)
  append = nil
end, ADD_POST)
event.onmemoryexecute(function()
  replacement = { id = (emu.getregister("M68K D0") or 0) % 0x10000, before = snapshot() }
end, REPLACE_BEGIN)
event.onmemoryexecute(function()
  if not replacement then clear_before = snapshot() end
end, CLEAR_BEGIN)
event.onmemoryexecute(function()
  if not replacement then emit("plc_submission", ',"operation":"clear"', nil, clear_before) end
end, CLEAR_POST)
event.onmemoryexecute(function()
  if not replacement then error("replacement post reached without replacement begin") end
  emit("plc_submission", ',"operation":"replace","plc_id":' .. replacement.id, nil, replacement.before)
  replacement = nil
end, REPLACE_POST)
event.onmemoryexecute(function()
  active_source = u32(PLC_BUFFER)
  preparing = { source = active_source, before = snapshot() }
  emit("plc_prepare_begin", nil, preparing.source, preparing.before)
end, PREPARE_BEGIN)
event.onmemoryexecute(function()
  if not preparing then return end
  emit("plc_prepare_end", nil, preparing.source, preparing.before)
  preparing = nil
end, PREPARE_END)
local function service_pre()
  if pending_service then error("service began before previous service completed") end
  pending_service = snapshot()
end
local function partial_service_post()
  if not pending_service then return end
  if pending_service.left == 0 then error("partial service post reached without active decoder") end
  emit("plc_service", nil, active_source, pending_service); pending_service = nil
end
event.onmemoryexecute(service_pre, FULL_PRE); event.onmemoryexecute(service_pre, SMALL_PRE)
event.onmemoryexecute(partial_service_post, PARTIAL_SERVICE_POST)
event.onmemoryexecute(function() _G.plc_pop_before = snapshot() end, POP_PRE)
event.onmemoryexecute(function()
  if pending_service then emit("plc_service", nil, active_source, pending_service); pending_service = nil end
  emit("plc_pop", nil, active_source, _G.plc_pop_before)
  if slots() == 0 then active_source = 0; emit("plc_empty", nil, 0) end
end, POP_POST)
event.onmemoryexecute(function()
  frame_handler = u8(VINT); frame_lag = frame_handler == LAG_HANDLER; frame_hblank = false; frame_mode = u8(GAME_MODE)
end, VINT_DISPATCH)
event.onmemoryexecute(function() frame_hblank = true end, HBLANK_DEFERRED_ENTRY)
for spec in string.gmatch(consumer_hooks, "[^,]+") do
  local id, address = string.match(spec, "([^@]+)@(.+)")
  if not id or not address or not tonumber(address) then error("OGGF_PLC_CONSUMER_HOOKS entries are consumer@0xROM") end
  event.onmemoryexecute(function()
    local empty = u32(PLC_BUFFER) == 0 and u16(PLC_LEFT) == 0
    local source = empty and 0 or (u16(PLC_LEFT) ~= 0 and active_source or u32(PLC_BUFFER))
    emit("plc_consumer_observation", ',"consumer_id":"'..id..'","queue_empty":'..(empty and "true" or "false"), source)
  end, tonumber(address))
end
event.onframeend(function() emit("plc_frame_state"); sequence = 0 end)
while true do emu.frameadvance() end
