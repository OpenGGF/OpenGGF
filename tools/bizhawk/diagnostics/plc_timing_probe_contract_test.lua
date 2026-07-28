-- Behavioral contract harness for the S1/S2 PLC timing probes.
-- Runs under stock Lua with BizHawk APIs replaced by deterministic fakes.
local probe_path = assert(arg[1], "probe path is required")
local callbacks = {}
local frame_callback
local state = { left = 0, slots = 0, source = 0x40, destination = 0x8000 }

local function address(name)
  return assert(tonumber(os.getenv(name)), name .. " is required")
end

local PLC_BUFFER = address("OGGF_PLC_BUFFER_RAM")
local PLC_DEST = address("OGGF_PLC_DEST_RAM")
local PLC_LEFT = address("OGGF_PLC_LEFT_RAM")

event = {
  onmemoryexecute = function(callback, hook)
    assert(not callbacks[hook], "duplicate hook registration at " .. hook)
    callbacks[hook] = callback
  end,
  onframeend = function(callback)
    frame_callback = callback
  end
}

mainmemory = {
  read_u8 = function() return 0 end,
  read_u16_be = function(location)
    if location == PLC_LEFT then return state.left end
    if location == PLC_DEST then return state.destination end
    return 0
  end,
  read_u32_be = function(location)
    if location >= PLC_BUFFER and
        location < PLC_BUFFER + state.slots * 6 and
        (location - PLC_BUFFER) % 6 == 0 then
      return state.source
    end
    return 0
  end
}

emu = {
  framecount = function() return 1 end,
  getregister = function() return 0 end,
  frameadvance = function()
    local function execute(name)
      local hook = address(name)
      assert(callbacks[hook], "missing callback for " .. name)
      callbacks[hook]()
    end

    local function output_lines()
      local lines = {}
      local input = assert(io.open(os.getenv("OGGF_PLC_PROBE_OUTPUT"), "r"))
      for line in input:lines() do table.insert(lines, line) end
      input:close()
      return lines
    end

    local function assert_event_count(expected, event_name)
      local count = 0
      for _, line in ipairs(output_lines()) do
        if string.find(line, '"event":"' .. event_name .. '"', 1, true) then
          count = count + 1
        end
      end
      assert(count == expected,
        string.format("expected %d %s events, got %d", expected, event_name, count))
    end

    -- The shared return is reached by both routines after their zero guards.
    -- Neither empty call reaches a service-pre hook or emits a service edge.
    execute("OGGF_PLC_PARTIAL_SERVICE_POST")
    execute("OGGF_PLC_PARTIAL_SERVICE_POST")
    assert_event_count(0, "plc_service")

    -- Both nonempty entry paths use the same shared partial-return hook.
    state.left, state.slots = 12, 1
    execute("OGGF_PLC_FULL_SERVICE_PRE")
    state.left = 3
    execute("OGGF_PLC_PARTIAL_SERVICE_POST")
    assert_event_count(1, "plc_service")

    state.left = 4
    execute("OGGF_PLC_SMALL_SERVICE_PRE")
    state.left = 1
    execute("OGGF_PLC_PARTIAL_SERVICE_POST")
    assert_event_count(2, "plc_service")

    local partial_lines = output_lines()
    assert(string.find(partial_lines[1],
      '"patterns_left_before":12,"patterns_left_after":3', 1, true))
    assert(string.find(partial_lines[2],
      '"patterns_left_before":4,"patterns_left_after":1', 1, true))

    -- A completing call bypasses the partial return. Its service edge appears
    -- exactly once, and only when the completed pop is observed.
    state.left = 2
    execute("OGGF_PLC_FULL_SERVICE_PRE")
    state.left = 0
    execute("OGGF_PLC_POP_PRE")
    assert_event_count(2, "plc_service")
    state.slots = 0
    execute("OGGF_PLC_POP_POST")
    assert_event_count(3, "plc_service")
    assert_event_count(1, "plc_pop")
    assert_event_count(1, "plc_empty")

    local completed_lines = output_lines()
    assert(string.find(completed_lines[3],
      '"event":"plc_service"', 1, true))
    assert(string.find(completed_lines[3],
      '"patterns_left_before":2,"patterns_left_after":0', 1, true))
    assert(string.find(completed_lines[4], '"event":"plc_pop"', 1, true))
    assert(string.find(completed_lines[5], '"event":"plc_empty"', 1, true))

    -- A pre-hook claiming an active service with zero work is malformed and
    -- must continue to fail closed.
    state.left = 0
    execute("OGGF_PLC_FULL_SERVICE_PRE")
    local ok, failure = pcall(execute, "OGGF_PLC_PARTIAL_SERVICE_POST")
    assert(not ok, "zero-left pending service was accepted")
    assert(string.find(failure, "without active decoder", 1, true))

    assert(frame_callback, "frame-state callback was not registered")
    print("PLC_PROBE_CONTRACT_OK")
    os.exit(0)
  end
}

dofile(probe_path)
