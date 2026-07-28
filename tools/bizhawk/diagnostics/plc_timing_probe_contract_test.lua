-- Behavioral contract harness for the S1/S2 PLC timing probes.
-- Runs under stock Lua with BizHawk APIs replaced by deterministic fakes.
local probe_path = assert(arg[1], "probe path is required")
local callbacks = {}
local frame_callback
local state = {
  left = 0, slots = 0, source = 0x40, destination = 0x8000,
  game_mode = 0x0C, vint = 0, raw_frame = 1
}

local function address(name)
  return assert(tonumber(os.getenv(name)), name .. " is required")
end

local PLC_BUFFER = address("OGGF_PLC_BUFFER_RAM")
local PLC_DEST = address("OGGF_PLC_DEST_RAM")
local PLC_LEFT = address("OGGF_PLC_LEFT_RAM")
local GAME_MODE = address("OGGF_PLC_GAME_MODE_RAM")
local VINT = address("OGGF_PLC_INTERRUPT_HANDLER_RAM")

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
  read_u8 = function(location)
    if location == GAME_MODE then return state.game_mode end
    if location == VINT then return state.vint end
    return 0
  end,
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
  framecount = function() return state.raw_frame end,
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

    local function event_lines(event_name)
      local matches = {}
      for _, line in ipairs(output_lines()) do
        if string.find(line, '"event":"' .. event_name .. '"', 1, true) then
          table.insert(matches, line)
        end
      end
      return matches
    end

    local function assert_strict_order_within_raw_frame()
      local previous_order_by_raw_frame = {}
      for _, line in ipairs(output_lines()) do
        local raw_frame, within_frame_order = string.match(line,
          '"raw_frame":(%d+),"within_frame_order":(%d+)')
        assert(raw_frame and within_frame_order, "missing frame ordering fields")
        local previous = previous_order_by_raw_frame[raw_frame]
        assert(not previous or tonumber(within_frame_order) > previous,
          string.format("within-frame order did not increase for raw frame %s", raw_frame))
        previous_order_by_raw_frame[raw_frame] = tonumber(within_frame_order)
      end
    end

    -- Submission is an observed completed queue mutation, never a routine
    -- entry. The append begin hook captures the PLC id and pre-state; only the
    -- reviewed post-copy hook may publish the append edge.
    state.left, state.slots = 0, 0
    execute("OGGF_PLC_ADD_ENTRY")
    assert_event_count(0, "plc_submission")
    state.slots = 1
    execute("OGGF_PLC_ADD_POST")
    assert_event_count(1, "plc_submission")
    local append_line = output_lines()[1]
    assert(string.find(append_line, '"operation":"append"', 1, true))
    assert(string.find(append_line,
      '"queue_slots_before":0,"queue_slots_after":1', 1, true))

    -- The completed prepare PC is the shared return of active and rejected
    -- calls. It is an end edge only after the active-path begin hook arms it.
    execute("OGGF_PLC_PREPARE_END")
    assert_event_count(0, "plc_prepare_end")
    state.left, state.slots = 0, 1
    execute("OGGF_PLC_PREPARE_BEGIN")
    state.left = 12
    execute("OGGF_PLC_PREPARE_END")
    assert_event_count(1, "plc_prepare_begin")
    assert_event_count(1, "plc_prepare_end")
    execute("OGGF_PLC_PREPARE_END")
    assert_event_count(1, "plc_prepare_end")

    -- VInt selection must be captured before the ROM consumes it, and the
    -- deferred HBlank path must be marked before it clears its latch.
    state.vint = 0x08
    execute("OGGF_PLC_VINT_DISPATCH")
    execute("OGGF_PLC_HBLANK_DEFERRED_ENTRY")

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

    local partial_lines = event_lines("plc_service")
    assert(string.find(partial_lines[1],
      '"patterns_left_before":12,"patterns_left_after":3', 1, true))
    assert(string.find(partial_lines[2],
      '"patterns_left_before":4,"patterns_left_after":1', 1, true))
    assert(string.find(partial_lines[1],
      '"interrupt_handler":8,"lag":false,"hblank_deferred":true', 1, true))

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

    local completed_lines = event_lines("plc_service")
    assert(string.find(completed_lines[3],
      '"event":"plc_service"', 1, true))
    assert(string.find(completed_lines[3],
      '"patterns_left_before":2,"patterns_left_after":0', 1, true))
    local pop_lines = event_lines("plc_pop")
    local empty_lines = event_lines("plc_empty")
    assert(string.find(pop_lines[1], '"event":"plc_pop"', 1, true))
    assert(string.find(empty_lines[1], '"event":"plc_empty"', 1, true))

    -- A pre-hook claiming an active service with zero work is malformed and
    -- must continue to fail closed.
    state.left = 0
    execute("OGGF_PLC_FULL_SERVICE_PRE")
    local ok, failure = pcall(execute, "OGGF_PLC_PARTIAL_SERVICE_POST")
    assert(not ok, "zero-left pending service was accepted")
    assert(string.find(failure, "without active decoder", 1, true))

    assert(frame_callback, "frame-state callback was not registered")

    -- BizHawk may invoke the frame-end callback before later execute hooks
    -- still report the same emulation frame. Frame state must retain its
    -- semantics without resetting their ordering sequence.
    frame_callback()
    execute("OGGF_PLC_ADD_ENTRY")
    state.slots = 1
    execute("OGGF_PLC_ADD_POST")
    assert_strict_order_within_raw_frame()

    -- A new observed raw frame starts a fresh, strictly increasing sequence.
    state.raw_frame = 2
    execute("OGGF_PLC_CLEAR_BEGIN")
    state.slots = 0
    execute("OGGF_PLC_CLEAR_POST")
    assert_strict_order_within_raw_frame()

    print("PLC_PROBE_CONTRACT_OK")
    os.exit(0)
  end
}

dofile(probe_path)
