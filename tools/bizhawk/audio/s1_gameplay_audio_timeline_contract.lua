-- Dependency-free semantic contract for the S1 GHZ1 gameplay-audio timeline.
-- BizHawk-facing code supplies already-observed ROM state; this module does
-- not access emulator APIs and can therefore be exercised by /usr/bin/lua.

local Contract = {}
local ROLES = {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"}
local JSON_NULL = {}
Contract.JSON_NULL = JSON_NULL

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
    if value == JSON_NULL then return "null" end
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

local function checkedRoles(values, name)
    assert(type(values) == "table", name .. " must be a table")
    local result = {}
    local seen = {}
    for _, role in ipairs(values) do
        assert(type(role) == "string" and not seen[role], name .. " must contain unique hardware roles")
        local valid = false
        for _, expected in ipairs(ROLES) do if role == expected then valid = true break end end
        assert(valid, name .. " contains an unknown hardware role")
        seen[role] = true
        result[#result + 1] = role
    end
    return result, seen
end

local function effectiveOwners(self, headers)
    assert(type(headers) == "table" and type(headers.normal) == "table" and type(headers.special) == "table",
        "track headers must contain normal and special tables")
    local result = {}
    local normal = headers.normal
    local special = headers.special
    for _, role in ipairs(ROLES) do
        assert(normal[role] == nil or type(normal[role]) == "boolean", "normal track activity must be boolean")
        assert(special[role] == nil or type(special[role]) == "boolean", "special track activity must be boolean")
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
    local updateMusicTrackReturns = {
        [0x71BE6] = true, [0x71BF8] = true, [0x71C10] = true,
        [0x71C22] = true, [0x71C38] = true, [0x71C44] = true
    }

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

    function lifecycle:playSegaAbnormalExit()
        assert(self.active, "PlaySega abnormal exit without active UpdateMusic")
        self.active, self.stackPointer, self.emulatorFrame = false, nil, nil
        return "reset"
    end

    function lifecycle:playBgmDoubleReturn()
        assert(self.active, "Sound_PlayBGM double return without active UpdateMusic")
        self.active, self.stackPointer, self.emulatorFrame = false, nil, nil
        return "close"
    end

    function lifecycle:fadeInToPreviousDoubleReturn()
        assert(self.active, "cfFadeInToPrevious double return without active UpdateMusic")
        self.active, self.stackPointer, self.emulatorFrame = false, nil, nil
        return "close"
    end

    function lifecycle:stopTrackDoubleReturn(returnPc)
        assert(self.active, "cfStopTrack double return without active UpdateMusic")
        if updateMusicTrackReturns[Contract.u32(returnPc)] then return "continue" end
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

-- D7 is the selected sound only at PlaySoundID. Normal and special SMPS
-- initialization reuse it as their DBF track counter, so retain this byte from
-- the queue/dispatch boundary and assert it whenever an initializer observes it.
function Contract.assertSelectedIdentity(request, selectedSoundId)
    assert(type(request) == "table" and type(request.sound_id) == "number",
        "selected identity requires a queued sound request")
    assert(Contract.u8(selectedSoundId) == request.sound_id,
        "dispatch/init observation changed the original selected queued sound ID")
    return request.sound_id
end

-- CycleSoundQueue clears every RAM slot whether or not the enclosing
-- UpdateMusic invocation is in the retained semantic window. Keep that
-- observation separate from the pre-window $81 baseline provenance.
function Contract.newQueueBuffer()
    local buffer = {slots = {}, baselineMusic = nil, deferredQueue0 = nil, pendingCandidates = nil,
        nextQueueOrdinal = 0}

    function buffer:write(slot, soundId, bk2Frame)
        local index = integer(slot, "queue slot")
        local frame = integer(bk2Frame, "BK2 frame")
        assert(index >= 0 and index <= 2, "S1 queue slot must be 0, 1, or 2")
        local id = Contract.u8(soundId)
        self.nextQueueOrdinal = self.nextQueueOrdinal + 1
        self.slots[index] = {slot = index, sound_id = id, queue_ordinal = self.nextQueueOrdinal}
        -- A normal queue0 write supersedes CycleSoundQueue's internal requeue.
        if index == 0 then self.deferredQueue0 = nil end
        if frame < 860 and index == 0 and id == 0x81 then self.baselineMusic = id end
    end

    function buffer:cycle(observedSlots, retained, soundIdBeforeCycle)
        assert(type(observedSlots) == "table", "CycleSoundQueue requires observed queue slots")
        assert(type(retained) == "boolean", "CycleSoundQueue retained flag must be boolean")
        local soundId = Contract.u8(soundIdBeforeCycle)
        -- A prior $71F22 internal requeue has no QueueSound callback. Associate
        -- it only when the next cycle sees the same physical queue0 byte.
        if self.deferredQueue0 then
            if self.slots[0] == nil then
                assert(Contract.u8(assert(observedSlots[1], "missing observed queue0")) == self.deferredQueue0.sound_id,
                    "CycleSoundQueue deferred queue0 disagrees with observed RAM")
                self.slots[0] = self.deferredQueue0
            end
            self.deferredQueue0 = nil
        end
        -- An unresolved candidate list means $71F2C rejected every input by
        -- priority, so no PlaySoundID callback occurred and no requeue exists.
        self.pendingCandidates = nil
        local candidates = {}
        for slot = 0, 2 do
            local observed = Contract.u8(assert(observedSlots[slot + 1], "missing observed queue slot"))
            local request = self.slots[slot]
            assert(request == nil or request.sound_id == observed,
                "queue write observation disagrees with CycleSoundQueue RAM")
            if request and request.sound_id >= 0x81 then
                request.slot = slot
                candidates[#candidates + 1] = request
            end
            self.slots[slot] = nil
        end
        if soundId ~= 0x80 then
            -- $71F22 copies every later valid input to queue0, so its final
            -- value is the last valid candidate observed in source order.
            self.deferredQueue0 = candidates[#candidates]
            return {}
        end
        self.pendingCandidates = candidates
        return retained and candidates or {}
    end

    function buffer:consume(soundId)
        local selectedSoundId = Contract.u8(soundId)
        local candidates = self.pendingCandidates
        self.pendingCandidates = nil
        if not candidates then return nil end
        -- The first accepted source-order request fills v_sound_id. Every
        -- later valid request then follows $71F22 and is deferred, including
        -- requests with the same sound ID, so resolve the first matching
        -- identity rather than collapsing duplicates by their byte value.
        local selectedIndex = nil
        for index = 1, #candidates do
            if candidates[index].sound_id == selectedSoundId then selectedIndex = index; break end
        end
        if not selectedIndex then return nil end
        -- After a source selection, each later input hits $71F22; queue0 keeps
        -- only the last one for the following UpdateMusic tick.
        self.deferredQueue0 = candidates[#candidates] ~= candidates[selectedIndex]
            and candidates[#candidates] or nil
        return candidates[selectedIndex]
    end

    function buffer:driverRamCleared()
        -- StopAllSound's shipped FixBugs = 0 clear loop still covers every
        -- queue field. Retain only the observer's monotonic identity counter.
        self.slots = {}
        self.baselineMusic = nil
        self.deferredQueue0 = nil
        self.pendingCandidates = nil
    end

    function buffer:baselineMusicId() return self.baselineMusic end

    return buffer
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
    timeline.initialOwners = copyOwners(timeline.musicOwners)
    timeline.finalOwners = copyOwners(timeline.musicOwners)

    function timeline:baseline()
        return {type = "baseline", bk2_frame = 860, active_music_id = musicId,
            diagnostic_tick = JSON_NULL, owners = copyOwners(self.initialOwners)}
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
        local request
        if type(soundId) == "table" then
            assert(type(soundId.sound_id) == "number", "correlated queued request requires a sound ID")
            request = soundId
            request.sound_id = Contract.u8(request.sound_id)
            request.queued_tick = request.queued_tick or self.tick
        else
            request = {sound_id = Contract.u8(soundId), queued_tick = self.tick}
        end
        self.queues[index] = request
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

    function timeline:cycle(priority)
        assert(self.active, "CycleSoundQueue occurred outside a complete tick")
        local result = {}
        for slot = 0, 2 do
            local request = self.queues[slot]
            self.queues[slot] = nil -- ROM clears every slot, even when no candidate dispatches.
            if request and request.sound_id >= 0x81 then
                request.consumed_tick = self.tick
                request.priority = Contract.u8(priority)
                request.sound_class = soundClass(request.sound_id)
                result[#result + 1] = request
            end
        end
        return result
    end

    function timeline:dispatch(request, observation)
        assert(self.active and type(request) == "table", "dispatch requires a consumed request in an active tick")
        assert(type(observation) == "table", "dispatch requires source-derived initialization observation")
        assert(type(observation.accepted) == "boolean", "dispatch observation must prove accepted initialization")
        local roles, declaredSet = checkedRoles(observation.declared_roles, "declared roles")
        local initialized = checkedRoles(observation.initialized_roles, "initialized roles")
        assert(type(observation.headers) == "table", "dispatch requires final track headers")
        if not observation.accepted or request.sound_class == "COMMAND" or #roles == 0 or #initialized == 0 then return false end
        for _, role in ipairs(initialized) do assert(declaredSet[role], "initialized role must be declared") end
        self.requestOrdinal = self.requestOrdinal + 1
        self.requestCount = self.requestCount + 1
        local identity = owner(ownerClass(request.sound_class), request.sound_id, self.requestOrdinal)
        local before = copyOwners(self.finalOwners)
        if request.sound_class == "MUSIC" then
            if request.sound_id == 0x88 then self.musicStack = self.musicStack or {}; self.musicStack[#self.musicStack + 1] = copyOwners(self.musicOwners) end
            for _, role in ipairs(initialized) do self.musicOwners[role] = cloneOwner(identity) end
        else
            local identities = request.sound_class == "SFX" and self.normalOwners or self.specialOwners
            for _, role in ipairs(initialized) do identities[role] = cloneOwner(identity) end
        end
        local after = effectiveOwners(self, observation.headers)
        local arbitration = {}
        for _, role in ipairs(roles) do
            local acquired = sameOwner(after[role], identity)
            arbitration[#arbitration + 1] = {role = role, acquired = acquired,
                displaced_owner = cloneOwner(before[role]), final_owner = cloneOwner(after[role])}
        end
        self.finalOwners = after
        if request.sound_class ~= "MUSIC" then self.priority = request.priority end
        self.requests[#self.requests + 1] = {request_ordinal = self.requestOrdinal,
            sound_class = request.sound_class, sound_id = request.sound_id,
            requested_roles = roles, arbitration = arbitration}
        return true
    end

    function timeline:restoreMusic()
        assert(self.active, "music restoration occurred outside a complete tick")
        local stack = self.musicStack
        if not stack or #stack == 0 then return false end
        self.musicOwners = table.remove(stack)
        self.finalOwners = effectiveOwners(self, {normal = {}, special = {}})
        return true
    end

    function timeline:abandonTick()
        assert(self.active, "abandon requires an active timeline tick")
        self.active, self.frame, self.tick, self.requests = false, nil, nil, {}
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
