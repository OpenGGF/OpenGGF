-- Purpose: read-only Genesis execution callbacks and bounded RAM observations.
-- Inputs: capture_native_references.py ROM/movie, Lua plan, optional SAME-MOVIE save.
-- Origin: S2 Competition lane F ring/camera/placement frontiers, 2026-11-08.
-- Comparison evidence only. Never writes RAM or controllers; no print (Linux Lua lag).
local output = assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local plan = dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
local state = os.getenv('OGGF_NATIVE_FIXTURE_STATE')
assert(type(plan.last) == 'number' and plan.last > 0, 'last frame required')
local function register(name)
    local value = emu.getregister('M68K ' .. name)
    assert(type(value) == 'number', 'missing M68K register ' .. name)
    return value & 0xffffffff
end
local function wanted(frame)
    if not plan.windows then return true end
    for _, window in ipairs(plan.windows) do
        if frame >= window[1] and frame <= window[2] then return true end
    end
    return false
end
local function name(value)
    assert(type(value) == 'string' and value:match('^[%a_][%w_]*$'), 'unsafe CSV name')
    return value
end
local function memory(fields)
    local result = {}
    for _, field in ipairs(fields) do
        local address = field.register and register(field.register) or assert(field.address)
        address = address + (field.offset or 0)
        assert(field.length >= 1 and field.length <= 65536, 'RAM extent out of range')
        local bytes = {}
        for index = 0, field.length - 1 do
            bytes[#bytes + 1] = string.format('%02X', mainmemory.read_u8((address + index) & 0xffff))
        end
        result[#result + 1] = table.concat(bytes)
    end
    return result
end
local function header(file, prefix, fields)
    local columns = {prefix}
    for _, field in ipairs(fields) do columns[#columns + 1] = name(field.name) end
    file:write(table.concat(columns, ','), '\n')
end
client.speedmode(6400)
client.invisibleemulation(false)
if state then
    assert(type(plan.state_frame) == 'number', 'same-movie state frame required')
    assert(savestate.load(state), 'state load failed')
    assert(emu.framecount() == plan.state_frame, 'state/movie frame mismatch')
end
local state_fields, event_fields = plan.state_fields or {}, plan.event_fields or {}
local states = assert(io.open(output .. '/state.csv', 'w'))
local events = assert(io.open(output .. '/events.csv', 'w'))
header(states, 'frame', state_fields)
header(events, 'event,trigger_pc,completed_frame,inflight_frame,observed_pc,d0,d1,d2,d3,d4,d5,d6,d7,a0,a1,a2,a3,a4,a5,a6,a7', event_fields)
local callbacks, counts = plan.callbacks or {}, {}
for _, callback in ipairs(callbacks) do
    local label, pc = name(callback.name), assert(callback.pc)
    assert(counts[label] == nil, 'duplicate callback name')
    counts[label] = 0
    -- NLua exposes callable methods as userdata: do not require type=='function'.
    assert(event.onmemoryexecute ~= nil, 'execution callback API absent')
    assert(event.onmemoryexecute(function()
        local frame = emu.framecount()
        if not wanted(frame + 1) then return end
        counts[label] = counts[label] + 1
        local columns = {label, pc, frame, frame + 1, register('PC')}
        for index = 0, 7 do columns[#columns + 1] = register('D' .. index) end
        for index = 0, 7 do columns[#columns + 1] = register('A' .. index) end
        for _, value in ipairs(memory(event_fields)) do columns[#columns + 1] = value end
        events:write(table.concat(columns, ','), '\n')
        events:flush()
    end, pc, 'oggf_observe_' .. label), 'callback registration failed')
end
local samples = 0
while emu.framecount() < plan.last do
    emu.frameadvance()
    local frame = emu.framecount()
    if wanted(frame) then
        local columns = {frame}
        for _, value in ipairs(memory(state_fields)) do columns[#columns + 1] = value end
        states:write(table.concat(columns, ','), '\n')
        states:flush()
        samples = samples + 1
    end
    if frame == plan.save_frame then savestate.save(output .. '/checkpoint.State') end
end
states:close()
events:close()
assert(samples > 0, 'no completed-frame samples')
local receipt = assert(io.open(output .. '/counts.csv', 'w'))
receipt:write('event,hits\n')
for _, callback in ipairs(callbacks) do
    local hits = counts[callback.name]
    receipt:write(callback.name, ',', hits, '\n')
    assert(hits >= (callback.min_hits or 1), 'missing callback: ' .. callback.name)
end
receipt:close()
local done = assert(io.open(output .. '/done.txt', 'w'))
done:write('frame=', emu.framecount(), '\nsamples=', samples, '\n')
done:close()
client.exit()
