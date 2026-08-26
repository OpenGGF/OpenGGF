-- Dependency-free source contract for the Sonic 1 REV01 complete-run audio observer.
-- It models request correlation, driver lifecycle, and source-owned outcomes only; emulator
-- callbacks and asynchronous native Z80 events remain inputs to the production capture owner.

local Contract = {}

local function integer(value, name)
    assert(type(value) == "number" and value == math.floor(value), name .. " must be an integer")
    return value
end

local function u8(value) return integer(value, "byte") & 0xff end
local function u32(value) return integer(value, "longword") & 0xffffffff end

local function copyArray(values)
    local result = {}
    for index, value in ipairs(values) do result[index] = value end
    return result
end

local function countMap(values)
    local count = 0
    for _, _ in pairs(values) do count = count + 1 end
    return count
end

local function checkedClass(value)
    assert(value == "music" or value == "normal_sfx" or value == "special_sfx"
        or value == "command", "unknown S1 request class")
    return value
end

local function currentBlock(self, class)
    if class ~= "normal_sfx" and class ~= "special_sfx" then return nil end
    if self.oneUp then return "one_up" end
    if self.fadeOut then return "fade_out" end
    if self.fadeIn then return "fade_in" end
    return nil
end

function Contract.newPriorityModel(activeMusicId)
    local model = {
        activeMusic = u8(activeMusicId), globalPriority = 0, slots = {}, deferred = nil,
        nextOrdinal = 0, oneUp = false, fadeOut = false, fadeIn = false,
        fadeDelay = 0, fadeCounter = 0, saved = nil, normalSfx = {}, specialSfx = {}
    }

    function model:request(frame, soundId, class, slot, priority)
        local request = {
            request_frame = integer(frame, "request frame"), sound_id = u8(soundId),
            class = checkedClass(class), slot = integer(slot, "queue slot"), priority = u8(priority)
        }
        assert(request.slot >= 0 and request.slot <= 2, "queue slot must be 0, 1, or 2")
        self.nextOrdinal = self.nextOrdinal + 1
        request.ordinal = self.nextOrdinal
        request.blocked_by = currentBlock(self, request.class)
        self.slots[request.slot] = request
        if request.slot == 0 then self.deferred = nil end
        return request
    end

    function model:setPriority(value) self.globalPriority = u8(value) end
    function model:priority() return self.globalPriority end
    function model:setFadeOut(value)
        assert(type(value) == "boolean", "fade-out state must be boolean")
        self.fadeOut = value
    end

    function model:pending(slot) return self.slots[integer(slot, "queue slot")] end
    function model:deferredQueue0() return self.deferred end
    function model:pendingCount()
        local count = countMap(self.slots)
        if self.deferred and self.slots[0] ~= self.deferred then count = count + 1 end
        return count
    end

    function model:setNormalSfx(role, soundId)
        assert(type(role) == "string" and role ~= "", "normal SFX role is required")
        self.normalSfx[role] = u8(soundId)
    end

    function model:setSpecialSfx(role, soundId)
        assert(type(role) == "string" and role ~= "", "special SFX role is required")
        self.specialSfx[role] = u8(soundId)
    end

    function model:normalSfxCount() return countMap(self.normalSfx) end
    function model:specialSfxCount() return countMap(self.specialSfx) end
    function model:savedMusic() return self.saved end
    function model:effectiveMusicRoles()
        return {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"}
    end

    local function clearDriverState(self)
        self.slots, self.deferred = {}, nil
        self.globalPriority = 0
        self.normalSfx, self.specialSfx = {}, {}
    end

    function model:transition(frame, kind)
        integer(frame, "transition frame")
        assert(kind == "stop_all" or kind == "death" or kind == "restart"
            or kind == "act_transition", "unsupported lifecycle transition")
        clearDriverState(self)
        self.oneUp, self.fadeOut, self.fadeIn = false, false, false
        self.fadeDelay, self.fadeCounter = 0, 0
        return {frame = frame, kind = kind, completion = "reset"}
    end

    local function dispatch(self, request, frame, priorityBefore)
        local decision = {
            frame = frame, request_frame = request.request_frame, request_ordinal = request.ordinal,
            sound_id = request.sound_id, accepted = false, priority_before = priorityBefore
        }
        local blocked = currentBlock(self, request.class)
        if request.class == "music" and request.sound_id == 0x88 and self.oneUp then
            decision.reason, decision.blocked_by = "repeated_one_up", "one_up"
            decision.priority_after = self.globalPriority
            return decision
        end
        if blocked then
            decision.reason, decision.blocked_by = "blocked", blocked
            if request.class == "normal_sfx" then self.globalPriority = 0 end
            decision.priority_after = self.globalPriority
            return decision
        end
        if request.class == "command" and request.sound_id == 0xE4 then
            decision.accepted, decision.reason = true, "stop_all"
            clearDriverState(self)
            decision.priority_after = self.globalPriority
            return decision
        end
        decision.accepted = true
        if request.class == "music" and request.sound_id == 0x88 then
            self.saved = {sound_id = self.activeMusic, override_bits_cleared = true}
            self.normalSfx = {}
            self.oneUp = true
            self.activeMusic = 0x88
            self.globalPriority = 0
            decision.reason = "one_up_save"
        else
            decision.reason = "accepted"
            if request.class == "music" then
                self.activeMusic = request.sound_id
                -- InitMusicPlayback clears the live $220 block, including v_sndprio.
                self.globalPriority = 0
            end
        end
        decision.priority_after = self.globalPriority
        return decision
    end

    function model:service(frame)
        local serviceFrame = integer(frame, "service frame")
        if self.deferred and self.slots[0] == nil then
            self.slots[0], self.deferred = self.deferred, nil
        end
        -- FixBugs=0: the word test covers queue0/queue1 only. Queue2 alone remains pending.
        local trigger = self.slots[0] ~= nil or self.slots[1] ~= nil
        if not trigger then return {frame = serviceFrame, decisions = {}} end

        local candidates = {}
        for slot = 0, 2 do
            if self.slots[slot] then candidates[#candidates + 1] = self.slots[slot] end
            self.slots[slot] = nil
        end
        local decisions, selected = {}, nil
        local priorityBefore = self.globalPriority
        for _, request in ipairs(candidates) do
            if selected ~= nil then
                -- The presence of v_sound_id is tested before the priority-table lookup.
                -- Consequently a later byte is copied to physical queue0 for the next
                -- service regardless of its priority; the last such byte wins.
                self.deferred = request
            elseif request.priority < self.globalPriority then
                decisions[#decisions + 1] = {
                    frame = serviceFrame, request_frame = request.request_frame,
                    request_ordinal = request.ordinal, sound_id = request.sound_id,
                    accepted = false, reason = "lower_priority",
                    priority_before = self.globalPriority, priority_after = self.globalPriority
                }
            else
                selected = request
                -- CycleSoundQueue commits d3 only when its sign bit is clear. Music and
                -- special-SFX priorities use bit 7 precisely so they can dispatch without
                -- replacing v_sndprio; normal blocked SFX later clears it at $722C6.
                if request.priority < 0x80 then self.globalPriority = request.priority end
            end
        end
        if selected then
            table.insert(decisions, 1, dispatch(self, selected, serviceFrame, priorityBefore))
        end
        return {frame = serviceFrame, decisions = decisions}
    end

    function model:beginOneUpRestore(frame)
        integer(frame, "restore frame")
        assert(self.oneUp and self.saved, "one-up restore requires saved music")
        self.activeMusic = self.saved.sound_id
        self.oneUp, self.fadeIn = false, true
        self.fadeDelay, self.fadeCounter = 0, 40
        return {frame = frame, restored_music_id = self.activeMusic, fade_steps = self.fadeCounter,
            effective_roles = self:effectiveMusicRoles()}
    end

    function model:fadeActive() return self.fadeIn end
    function model:advanceFadeService()
        assert(self.fadeIn, "fade service requires an active fade")
        local result = {attenuated = false, completed = false}
        if self.fadeDelay ~= 0 then
            self.fadeDelay = self.fadeDelay - 1
        elseif self.fadeCounter ~= 0 then
            self.fadeCounter = self.fadeCounter - 1
            self.fadeDelay = 2
            result.attenuated = true
        else
            self.fadeIn = false
            result.completed = true
        end
        return result
    end

    return model
end

function Contract.extraLifeOracle()
    local six = {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"}
    return {
        [3698] = {queued_music_id = 0x88, prior_music_id = 0x87},
        [3699] = {active_music_id = 0x88, effective_roles = copyArray(six)},
        [3702] = {normal_sfx_blocked_by = "one_up"},
        [3910] = {restored_music_id = 0x87, effective_roles = copyArray(six)}
    }
end

function Contract.deriveMusicRoles(fmDacCount, psgCount)
    local fmCount = integer(fmDacCount, "FM/DAC header count")
    local toneCount = integer(psgCount, "PSG header count")
    assert(fmCount >= 0 and fmCount <= 7, "FM/DAC header count exceeds loader inventory")
    assert(toneCount >= 0 and toneCount <= 3, "PSG header count exceeds loader inventory")
    local fmRoles = {"DAC", "FM1", "FM2", "FM3", "FM4", "FM5", "FM6"}
    local psgRoles = {"PSG1", "PSG2", "PSG3"}
    local result = {}
    for index = 1, fmCount do result[#result + 1] = fmRoles[index] end
    for index = 1, toneCount do result[#result + 1] = psgRoles[index] end
    return result
end

function Contract.fixBugsZeroDacRestore(priorFmDacCount)
    local count = integer(priorFmDacCount, "prior FM/DAC count")
    assert(count >= 0 and count <= 7, "prior FM/DAC count is invalid")
    return {prior_had_fm6 = count == 7, writes_dac_disable = false, restores_dac_pan = false}
end

function Contract.newFrameServiceLedger(firstFrame, exclusiveEnd)
    local first = integer(firstFrame, "first frame")
    local ending = integer(exclusiveEnd, "exclusive end")
    assert(first < ending, "frame ledger interval must be positive")
    local ledger = {rows = {}, first = first, ending = ending, finished = false}
    for frame = first, ending - 1 do ledger.rows[frame] = {} end

    function ledger:record(frame, service)
        assert(not self.finished, "cannot append to a finished frame ledger")
        local row = self.rows[integer(frame, "service frame")]
        assert(row ~= nil, "service frame is outside the ledger interval")
        assert(type(service) == "table", "service record must be a table")
        row[#row + 1] = service
    end

    function ledger:services(frame)
        return assert(self.rows[integer(frame, "row frame")], "row frame is outside the ledger interval")
    end

    function ledger:finish() self.finished = true; return self end
    return ledger
end

local CONTINUATIONS = {
    [0x71BD4] = true, [0x71BE6] = true, [0x71BF8] = true, [0x71C10] = true,
    [0x71C22] = true, [0x71C38] = true, [0x71C44] = true
}

function Contract.playSegaOutcome(observedInsideEpoch)
    assert(type(observedInsideEpoch) == "boolean", "armed-epoch observation must be boolean")
    return observedInsideEpoch and "abnormal_close" or "outside_armed_epoch"
end

function Contract.newInvocationLifecycle()
    local lifecycle = {active = false, stackPointer = nil, frame = nil}

    function lifecycle:entry(stackPointer, frame)
        local stack = u32(stackPointer)
        integer(frame, "emulator frame")
        if self.active then
            assert(stack == self.stackPointer, "different-stack UpdateMusic entry before close")
            return "retry"
        end
        self.active, self.stackPointer, self.frame = true, stack, frame
        return "open"
    end

    function lifecycle:close(reason)
        assert(self.active, "service close without active UpdateMusic")
        self.active, self.stackPointer, self.frame = false, nil, nil
        return reason or "close"
    end

    function lifecycle:playBgmDoubleReturn() return self:close("close") end
    function lifecycle:fadeInToPreviousDoubleReturn() return self:close("close") end
    function lifecycle:stopSpecialDoubleReturn(returnPc)
        assert(self.active, "special stop without active UpdateMusic")
        if CONTINUATIONS[u32(returnPc)] then return "continue" end
        return self:close("close")
    end
    function lifecycle:stopTrackDoubleReturn(returnPc)
        assert(self.active, "track stop without active UpdateMusic")
        if CONTINUATIONS[u32(returnPc)] then return "continue" end
        return self:close("close")
    end
    function lifecycle:playSegaOutcome(observedInsideEpoch)
        local outcome = Contract.playSegaOutcome(observedInsideEpoch)
        if outcome == "abnormal_close" then self:close(outcome) end
        return outcome
    end

    return lifecycle
end

function Contract.nativeDacServiceContract()
    return {
        requires_m68k_parent = false,
        dpcm = {kind = "z80_dpcm_byte", source_cpu = "Z80", begin_pc = 0x77,
            select_pc_1 = 0x86, data_pc_1 = 0x89, select_pc_2 = 0x9C, data_pc_2 = 0x9F,
            end_pc = 0xAC, ym2a_writes = 2},
        sega = {kind = "z80_sega_pcm_byte", source_cpu = "Z80", begin_pc = 0xC1,
            select_pc = 0xC2, data_pc = 0xC5, end_pc = 0xD0, ym2a_writes = 1}
    }
end

return Contract
