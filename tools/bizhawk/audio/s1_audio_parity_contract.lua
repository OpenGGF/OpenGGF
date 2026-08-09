-- Deterministic, dependency-free S1 audio parity normalization contract.
-- This module deliberately has no BizHawk globals and never accepts asset bytes.

local Contract = {}
local MAX_INVOCATIONS = 36000

local function assertInteger(value, name)
    assert(type(value) == "number" and value == math.floor(value), name .. " must be an integer")
    return value
end

function Contract.u8(value)
    return assertInteger(value, "byte") & 0xff
end

function Contract.u16(value)
    return assertInteger(value, "word") & 0xffff
end

function Contract.u32(value)
    return assertInteger(value, "longword") & 0xffffffff
end

function Contract.s8(value)
    local normalized = Contract.u8(value)
    return normalized >= 0x80 and normalized - 0x100 or normalized
end

function Contract.s16(value)
    local normalized = Contract.u16(value)
    return normalized >= 0x8000 and normalized - 0x10000 or normalized
end

local function escapeJson(value)
    return (value:gsub('[%z\1-\31\\"]', function(character)
        local escapes = {['"'] = '\\"', ['\\'] = '\\\\', ['\b'] = '\\b', ['\f'] = '\\f', ['\n'] = '\\n', ['\r'] = '\\r', ['\t'] = '\\t'}
        return escapes[character] or string.format("\\u%04x", string.byte(character))
    end))
end

local function isArray(value)
    local largest = 0
    for key, _ in pairs(value) do
        if type(key) ~= "number" or key < 1 or key ~= math.floor(key) then return false end
        if key > largest then largest = key end
    end
    for index = 1, largest do
        if value[index] == nil then return false end
    end
    return true, largest
end

function Contract.canonicalJson(value)
    local kind = type(value)
    if kind == "nil" then return "null" end
    if kind == "boolean" then return value and "true" or "false" end
    if kind == "number" then
        assert(value == value and value ~= math.huge and value ~= -math.huge, "JSON number must be finite")
        if value == math.floor(value) then return tostring(value) end
        return string.format("%.17g", value)
    end
    if kind == "string" then return '"' .. escapeJson(value) .. '"' end
    assert(kind == "table", "canonical JSON accepts only JSON values")
    local array, largest = isArray(value)
    if array then
        local parts = {}
        for index = 1, largest do parts[index] = Contract.canonicalJson(value[index]) end
        return "[" .. table.concat(parts, ",") .. "]"
    end
    local keys = {}
    for key, _ in pairs(value) do
        assert(type(key) == "string", "canonical JSON object keys must be strings")
        keys[#keys + 1] = key
    end
    table.sort(keys)
    local parts = {}
    for index, key in ipairs(keys) do
        parts[index] = Contract.canonicalJson(key) .. ":" .. Contract.canonicalJson(value[key])
    end
    return "{" .. table.concat(parts, ",") .. "}"
end

local function fnv1a(bytes)
    local hash = 0x811c9dc5
    for index = 1, #bytes do
        hash = (hash ~ string.byte(bytes, index)) & 0xffffffff
        hash = (hash * 0x01000193) & 0xffffffff
    end
    return string.format("%08x", hash)
end

function Contract.hashState(state)
    return fnv1a(Contract.canonicalJson(state))
end

function Contract.hashEvents(events)
    return fnv1a(Contract.canonicalJson(events))
end

function Contract.newYmDecoder()
    local pending = {}
    local decoder = {}

    function decoder:feed(busEvent)
        assert(type(busEvent) == "table", "bus event must be a table")
        local kind = busEvent.kind
        if kind == "psg" then
            return {chip = "psg", value = Contract.u8(busEvent.value)}
        end
        assert(kind == "address" or kind == "data", "unsupported chip bus operation")
        local port = assertInteger(busEvent.port, "YM port")
        assert(port == 0 or port == 1, "unsupported YM port")
        local value = Contract.u8(busEvent.value)
        if kind == "address" then
            assert(pending[port] == nil, "orphan YM address")
            pending[port] = value
            return nil
        end
        assert(pending[port] ~= nil, "orphan YM data")
        local event = {chip = "ym2612", port = port, register = pending[port], value = value}
        pending[port] = nil
        return event
    end

    function decoder:finishTick()
        assert(pending[0] == nil and pending[1] == nil, "orphan YM address at tick boundary")
    end

    return decoder
end

local S1_MUSIC_SLOTS = {
    {role = "DAC", hardware = "DAC", voiceControl = 6},
    {role = "FM1", hardware = "FM1", voiceControl = 0},
    {role = "FM2", hardware = "FM2", voiceControl = 1},
    {role = "FM3", hardware = "FM3", voiceControl = 2},
    {role = "FM4", hardware = "FM4", voiceControl = 4},
    {role = "FM5", hardware = "FM5", voiceControl = 5},
    {role = "FM6", hardware = "FM6", voiceControl = 6},
    {role = "PSG1", hardware = "PSG1", voiceControl = 0x80},
    {role = "PSG2", hardware = "PSG2", voiceControl = 0xa0},
    {role = "PSG3", hardware = "PSG3", voiceControl = 0xc0}
}

local function filteredLoopCounters(counters, activeIndices)
    local result = {}
    for outputIndex, sourceIndex in ipairs(activeIndices) do
        assertInteger(sourceIndex, "loop-counter index")
        assert(sourceIndex >= 0, "loop-counter index must be non-negative")
        result[outputIndex] = Contract.u8(counters[sourceIndex + 1] or 0)
    end
    return result
end

local function liveOpenGgfReturnStack(stack, returnSp)
    local result = {}
    local count = Contract.u8(returnSp)
    assert(count <= #stack, "return stack cursor exceeds supplied stack")
    for index = 1, count do result[index] = Contract.u32(stack[index]) end
    return result
end

local function liveRomReturnStack(stack, stackPointer, assetBase, assetEnd)
    local pointer = Contract.u8(stackPointer)
    assert(pointer <= 0x30 and (0x30 - pointer) % 4 == 0,
        "ROM return-stack cursor must be aligned within 0x00..0x30")
    local base = Contract.u32(assetBase)
    local ending = Contract.u32(assetEnd)
    assert(base < ending, "ROM asset range must have a positive extent")
    local count = (0x30 - pointer) / 4
    assert(count <= #stack, "ROM return stack cursor exceeds supplied stack")
    local result = {}
    -- `$F8` decrements StackPointer before storing, so physical top-to-bottom
    -- words run from the current cursor upward; canonical call order is reverse.
    for index = count, 1, -1 do
        local address = Contract.u32(stack[index])
        assert(address >= base and address < ending, "ROM return address is outside the GHZ asset range")
        result[#result + 1] = address - base
    end
    return result
end

local function normalizedGlobal(global, raw)
    local active
    local fadeOut
    local speedUp
    if raw then
        active = Contract.u8(global.fadeActive) ~= 0
        fadeOut = Contract.u8(global.fadeOut) ~= 0
        speedUp = (Contract.u8(global.speedUp) & 0x80) ~= 0
    else
        active = global.fadeActive == true
        fadeOut = global.fadeDirection == "out"
        speedUp = global.speedUp == true
    end
    return {
        fadeActive = active == true,
        fadeDelay = Contract.u8(global.fadeDelay),
        fadeDirection = active and (fadeOut and "out" or "in") or "none",
        fadeSteps = Contract.u8(global.fadeSteps),
        speedUp = speedUp,
        tempoReload = Contract.u8(global.tempoReload),
        tempoTimeout = Contract.u8(global.tempoTimeout)
    }
end

local function normalizedActiveTrack(track, role, hardware, activeIndices, returnStack)
    return {
        active = true,
        baseFrequency = Contract.u16(track.baseFrequency),
        detune = Contract.s8(track.detune),
        hardware = hardware,
        loopCounters = filteredLoopCounters(track.loopCounters or {}, activeIndices),
        returnStack = returnStack,
        role = role,
        transpose = Contract.s8(track.transpose),
        volume = Contract.s8(track.volume)
    }
end

function Contract.normalizeRom(snapshot, activeLoopIndices)
    assert(type(snapshot) == "table" and type(snapshot.global) == "table" and type(snapshot.tracks) == "table",
        "ROM snapshot requires global and tracks tables")
    assert(#snapshot.tracks == #S1_MUSIC_SLOTS, "ROM snapshot requires all ten fixed S1 music slots")
    local tracks = {}
    for index, track in ipairs(snapshot.tracks) do
        local slot = S1_MUSIC_SLOTS[index]
        if (Contract.u8(track.status) & 0x80) == 0 then
            tracks[index] = {active = false, hardware = slot.hardware, role = slot.role}
        else
            assert(Contract.u8(track.voiceControl) == slot.voiceControl,
                "ROM voice-control does not match active " .. slot.role .. " slot")
            tracks[index] = normalizedActiveTrack(track, slot.role, slot.hardware, activeLoopIndices,
                liveRomReturnStack(track.returnStack or {}, track.stackPointer, snapshot.assetBase, snapshot.assetEnd))
        end
    end
    return {global = normalizedGlobal(snapshot.global, true), tracks = tracks}
end

function Contract.normalizeOpenGgf(snapshot, activeLoopIndices)
    assert(type(snapshot) == "table" and type(snapshot.global) == "table" and type(snapshot.tracks) == "table",
        "OpenGGF snapshot requires global and tracks tables")
    local tracks = {}
    for index, track in ipairs(snapshot.tracks) do
        assert(type(track.role) == "string" and type(track.hardware) == "string",
            "OpenGGF track requires semantic role and hardware")
        if track.active ~= true then
            tracks[index] = {active = false, hardware = track.hardware, role = track.role}
        else
            tracks[index] = normalizedActiveTrack(track, track.role, track.hardware, activeLoopIndices,
                liveOpenGgfReturnStack(track.returnStack or {}, track.returnSp))
        end
    end
    return {global = normalizedGlobal(snapshot.global, false), tracks = tracks}
end

function Contract.newCycleDetector()
    local detector = {history = {}, seen = {}, invocations = 0, candidate = nil, accepted = nil}

    local function resetAfterRejectedCandidate(self, ordinal, stateHash, eventHash)
        self.history = {{ordinal = ordinal, stateHash = stateHash, eventHash = eventHash}}
        self.seen = {[stateHash] = 1}
        self.candidate = nil
    end

    function detector:observe(stateHash, eventHash)
        self.invocations = self.invocations + 1
        assert(self.invocations <= MAX_INVOCATIONS, "S1 audio recurrence was not proven within 36,000 invocations")
        assert(type(stateHash) == "string" and type(eventHash) == "string", "recurrence hashes must be strings")
        local ordinal = self.invocations - 1
        if self.accepted then
            local expected = self.history[self.accepted.startIndex]
            if stateHash == expected.stateHash and eventHash == expected.eventHash then
                return {startOrdinal = self.accepted.startOrdinal, period = self.accepted.period,
                    terminalRecordCount = self.invocations}
            end
            error("accepted recurrence third boundary changed")
        end
        if self.candidate then
            local expected = self.history[self.candidate.startIndex + self.candidate.progress]
            if stateHash ~= expected.stateHash or eventHash ~= expected.eventHash then
                resetAfterRejectedCandidate(self, ordinal, stateHash, eventHash)
                return nil
            end
            self.candidate.progress = self.candidate.progress + 1
            self.history[#self.history + 1] = {ordinal = ordinal, stateHash = stateHash, eventHash = eventHash}
            if self.candidate.progress == self.candidate.period then
                self.accepted = {startIndex = self.candidate.startIndex, startOrdinal = self.candidate.startOrdinal,
                    period = self.candidate.period}
                self.candidate = nil
            end
            return nil
        end
        local previous = self.seen[stateHash]
        self.history[#self.history + 1] = {ordinal = ordinal, stateHash = stateHash, eventHash = eventHash}
        self.seen[stateHash] = #self.history
        if previous then
            local start = self.history[previous]
            self.candidate = {startIndex = previous, startOrdinal = start.ordinal,
                period = #self.history - previous, progress = 1}
            if eventHash ~= start.eventHash then
                resetAfterRejectedCandidate(self, ordinal, stateHash, eventHash)
            elseif self.candidate.progress == self.candidate.period then
                self.accepted = {startIndex = self.candidate.startIndex,
                    startOrdinal = self.candidate.startOrdinal, period = self.candidate.period}
                self.candidate = nil
            end
        end
        return nil
    end

    return detector
end

return Contract
