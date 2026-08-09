-- Dependency-free semantic contract for the S1 GHZ1 gameplay-audio timeline.
-- BizHawk-facing code supplies already-observed ROM state; this module does
-- not access emulator APIs and can therefore be exercised by /usr/bin/lua.

local Contract = {}
local ROLES = {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"}

local function integer(value, name)
    assert(type(value) == "number" and value == math.floor(value), name .. " must be an integer")
    return value
end

function Contract.u8(value) return integer(value, "byte") & 0xff end
function Contract.u32(value) return integer(value, "longword") & 0xffffffff end

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
        largest = math.max(largest, key)
    end
    for index = 1, largest do if value[index] == nil then return false end end
    return true, largest
end

function Contract.canonicalJson(value)
    local kind = type(value)
    if kind == "nil" then return "null" end
    if kind == "boolean" then return value and "true" or "false" end
    if kind == "number" then
        assert(value == value and value ~= math.huge and value ~= -math.huge, "JSON number must be finite")
        return value == math.floor(value) and tostring(value) or string.format("%.17g", value)
    end
    if kind == "string" then return '"' .. escapeJson(value) .. '"' end
    assert(kind == "table", "canonical JSON accepts only JSON values")
    local array, largest = isArray(value)
    local parts = {}
    if array then
        for index = 1, largest do parts[index] = Contract.canonicalJson(value[index]) end
    else
        local keys = {}
        for key, _ in pairs(value) do
            assert(type(key) == "string", "canonical JSON object keys must be strings")
            keys[#keys + 1] = key
        end
        table.sort(keys)
        for index, key in ipairs(keys) do
            parts[index] = Contract.canonicalJson(key) .. ":" .. Contract.canonicalJson(value[key])
        end
    end
    return (array and "[" or "{") .. table.concat(parts, ",") .. (array and "]" or "}")
end

local function soundClass(soundId)
    local id = Contract.u8(soundId)
    if id >= 0x81 and id <= 0x9f then return "MUSIC" end
    if id >= 0xa0 and id <= 0xcf then return "SFX" end
    if id >= 0xd0 and id <= 0xdf then return "SPECIAL_SFX" end
    if id >= 0xe0 then return "COMMAND" end
    error(string.format("unsupported S1 sound ID $%02X", id))
end

local function ownerClass(class)
    if class == "MUSIC" then return "MUSIC" end
    if class == "SFX" then return "NORMAL_SFX" end
    if class == "SPECIAL_SFX" then return "SPECIAL_SFX" end
    error("commands do not own hardware tracks")
end

local function owner(class, soundId, ordinal)
    return {owner_class = class, sound_id = soundId, request_ordinal = ordinal}
end

local function cloneOwner(value)
    return owner(value.owner_class, value.sound_id, value.request_ordinal)
end

local function sameOwner(left, right)
    return left.owner_class == right.owner_class and left.sound_id == right.sound_id
        and left.request_ordinal == right.request_ordinal
end

local function copyOwners(values)
    local result = {}
    for _, role in ipairs(ROLES) do result[role] = cloneOwner(values[role]) end
    return result
end

local function roleSet(headers, class)
    assert(type(headers) == "table", "track headers must be a table")
    local selected = class == "SFX" and headers.normal or headers.special
    assert(type(selected) == "table", "active track headers must contain normal and special tables")
    local result = {}
    for _, role in ipairs(ROLES) do
        if selected[role] == true then result[#result + 1] = role
        elseif selected[role] ~= nil then assert(selected[role] == false, "track activity must be boolean") end
    end
    return result
end

local function effectiveOwners(self, headers)
    local result = {}
    local normal = headers.normal or {}
    local special = headers.special or {}
    for _, role in ipairs(ROLES) do
        -- The ROM runs normal SFX after special SFX, so an active normal track
        -- wins the effective role even when the special track remains live.
        if normal[role] == true and self.normalOwners[role] then
            result[role] = cloneOwner(self.normalOwners[role])
        elseif special[role] == true and self.specialOwners[role] then
            result[role] = cloneOwner(self.specialOwners[role])
        else
            result[role] = cloneOwner(self.musicOwners[role])
        end
    end
    return result
end

function Contract.newInvocationLifecycle()
    local lifecycle = {active = false, stackPointer = nil, emulatorFrame = nil}

    function lifecycle:entry(stackPointer, emulatorFrame)
        local stack = Contract.u32(stackPointer)
        integer(emulatorFrame, "emulator frame")
        if self.active then
            assert(stack == self.stackPointer, "different-stack UpdateMusic entry before close")
            return "retry"
        end
        self.active, self.stackPointer, self.emulatorFrame = true, stack, emulatorFrame
        return "open"
    end

    function lifecycle:close()
        assert(self.active, "UpdateMusic close without active invocation")
        self.active, self.stackPointer, self.emulatorFrame = false, nil, nil
        return "close"
    end

    return lifecycle
end

-- Task 1 serializes the fixed owner-vector fields in lower camel case while
-- role enums remain uppercase. Keep this conversion at the producer boundary.
function Contract.jsonOwnerVector(values)
    return {fm3 = cloneOwner(values.FM3), fm4 = cloneOwner(values.FM4), fm5 = cloneOwner(values.FM5),
        psg1 = cloneOwner(values.PSG1), psg2 = cloneOwner(values.PSG2), psg3 = cloneOwner(values.PSG3)}
end

function Contract.newTimeline(activeMusicId)
    local musicId = Contract.u8(activeMusicId)
    assert(musicId >= 0x81 and musicId <= 0x9f, "timeline requires active music ID")
    local timeline = {
        active = false, frame = nil, tick = nil, lastTick = nil, queues = {}, requests = {},
        requestOrdinal = 0, requestCount = 0, diagnosticTickCount = 0, priority = 0,
        normalOwners = {}, specialOwners = {}, musicOwners = {}
    }
    for _, role in ipairs(ROLES) do timeline.musicOwners[role] = owner("MUSIC", musicId, 0) end
    timeline.finalOwners = copyOwners(timeline.musicOwners)

    function timeline:baseline()
        return {type = "baseline", bk2_frame = 860, active_music_id = musicId,
            diagnostic_tick = nil, owners = copyOwners(self.musicOwners)}
    end

    function timeline:beginTick(bk2Frame, diagnosticTick)
        assert(not self.active, "previous timeline tick was not closed")
        local frame = integer(bk2Frame, "BK2 frame")
        local tick = integer(diagnosticTick, "diagnostic tick")
        assert(frame >= 860 and frame < 4975, "semantic frame is outside [860,4975)")
        assert(tick >= 0 and (self.lastTick == nil or tick > self.lastTick),
            "diagnostic ticks must be monotonic")
        self.active, self.frame, self.tick, self.requests = true, frame, tick, {}
    end

    function timeline:queue(slot, soundId)
        assert(self.active, "queue write occurred outside a complete tick")
        local index = integer(slot, "queue slot")
        assert(index >= 0 and index <= 2, "S1 queue slot must be 0, 1, or 2")
        self.queues[index] = {sound_id = Contract.u8(soundId), queued_tick = self.tick}
    end

    function timeline:consume(slot, priority)
        assert(self.active, "queue consumption occurred outside a complete tick")
        local index = integer(slot, "queue slot")
        local request = self.queues[index]
        self.queues[index] = nil
        if not request then return nil end
        request.consumed_tick = self.tick
        request.priority = Contract.u8(priority)
        request.sound_class = soundClass(request.sound_id)
        return request
    end

    function timeline:dispatch(request, headers)
        assert(self.active and type(request) == "table", "dispatch requires a consumed request in an active tick")
        if request.sound_class == "COMMAND" or request.sound_class == "MUSIC" then return false end
        if request.priority < self.priority then return false end
        local roles = roleSet(headers, request.sound_class)
        if #roles == 0 then return false end
        self.requestOrdinal = self.requestOrdinal + 1
        self.requestCount = self.requestCount + 1
        local identity = owner(ownerClass(request.sound_class), request.sound_id, self.requestOrdinal)
        local identities = request.sound_class == "SFX" and self.normalOwners or self.specialOwners
        local before = copyOwners(self.finalOwners)
        for _, role in ipairs(roles) do identities[role] = cloneOwner(identity) end
        local after = effectiveOwners(self, headers)
        local arbitration = {}
        for _, role in ipairs(roles) do
            local acquired = sameOwner(after[role], identity)
            arbitration[#arbitration + 1] = {role = role, acquired = acquired,
                displaced_owner = cloneOwner(before[role]), final_owner = cloneOwner(after[role])}
        end
        self.finalOwners = after
        self.priority = request.priority
        self.requests[#self.requests + 1] = {request_ordinal = self.requestOrdinal,
            sound_class = request.sound_class, sound_id = request.sound_id,
            requested_roles = roles, arbitration = arbitration}
        return true
    end

    function timeline:closeTick(headers)
        assert(self.active, "timeline tick close without entry")
        self.finalOwners = effectiveOwners(self, headers)
        local record = {type = "frame", bk2_frame = self.frame, diagnostic_tick = self.tick,
            requests = self.requests, owners = copyOwners(self.finalOwners)}
        self.active, self.lastTick = false, self.tick
        self.diagnosticTickCount = self.diagnosticTickCount + 1
        return record
    end

    function timeline:terminal(diagnosticTickCount)
        assert(not self.active, "terminal cannot close an active tick")
        local count = diagnosticTickCount == nil and self.diagnosticTickCount or integer(diagnosticTickCount, "terminal diagnostic tick count")
        assert(count >= 0, "terminal diagnostic tick count must be non-negative")
        return {type = "terminal", frame_count = 4115, request_count = self.requestCount,
            diagnostic_tick_count = count}
    end

    return timeline
end

return Contract
