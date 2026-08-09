-- Read-only Sonic 1 REV01 GHZ1 gameplay-audio timeline producer.
-- Queue slots, global priority, and audio ticks are diagnostics only; semantic
-- output uses the Task 1 v1 request/arbitration/owner vocabulary.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
runtimePath = runtimePath:gsub("\\", "/")
local ProbeRuntime = dofile(runtimePath)
local contractPath = ProbeRuntime.siblingPath(runtimePath, "audio/s1_gameplay_audio_timeline_contract.lua")
local Timeline = dofile(contractPath)

local SOUND_RAM = 0xF000
local QUEUE_PCS = {[0x138E] = 0, [0x1394] = 1, [0x139A] = 2}
local UPDATE_MUSIC, UPDATE_MUSIC_RETURN = 0x71B4C, 0x71C4C
local CYCLE_SOUND_QUEUE, PLAY_SOUND_ID = 0x71F02, 0x71F4C
local PLAY_SEGA_RETURN, PLAY_BGM, BGM_LOAD_MUSIC = 0x71FD0, 0x71FD2, 0x7202C
local PLAY_SFX, NORMAL_ROLE_DECLARED, NORMAL_ROLE_INITIALIZED = 0x721C6, 0x7222E, 0x7227C
local PLAY_SPECIAL, SPECIAL_ROLE_DECLARED, SPECIAL_ROLE_INITIALIZED = 0x7230C, 0x7234C, 0x7236E
local RESTORE_PREVIOUS_MUSIC = 0x72B14
local SEGMENT_START, SEGMENT_END = 860, 4975
local EXPECTED_ROM_SHA1 = "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b"
local EXPECTED_ROM_CRC32 = "afe05eee"
local EXPECTED_MOVIE_SHA256 = "f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b"
local EXPECTED_MOVIE_ROWS = 225101
local EXPECTED_MOVIE_HEADER_SHA1 = "09DADB5071EB35050067A32462E39C5F"

-- All execute addresses are pinned to the shipped FixBugs = 0 S1 REV01 ROM.
local opcodeManifest = {
    {address = 0x138E, expectedOpcode = "11c0f00a"}, {address = 0x1394, expectedOpcode = "11c0f00b"},
    {address = 0x139A, expectedOpcode = "11c0f00c"}, {address = 0x71B4C, expectedOpcode = "33fc010000a11100"},
    {address = 0x71C4C, expectedOpcode = "4e75"}, {address = 0x71F02, expectedOpcode = "207900071990"},
    {address = 0x71F4C, expectedOpcode = "7e00"}, {address = 0x71FD0, expectedOpcode = "4e75"},
    {address = 0x71FD2, expectedOpcode = "0c070088"}, {address = 0x7202C, expectedOpcode = "4eba059c"},
    {address = 0x721C6, expectedOpcode = "4a2e0027"}, {address = 0x7222E, expectedOpcode = "1803"},
    {address = 0x7227C, expectedOpcode = "3a99"}, {address = 0x7230C, expectedOpcode = "4a2e0027"},
    {address = 0x7234C, expectedOpcode = "6b0c"}, {address = 0x7236E, expectedOpcode = "3a99"},
    {address = 0x72B14, expectedOpcode = "204e"}
}

local function verifyOpcodeManifest()
    for _, site in ipairs(opcodeManifest) do
        local bytes = {}
        for offset = 0, #site.expectedOpcode / 2 - 1 do
            bytes[#bytes + 1] = string.format("%02x", memory.read_u8(site.address + offset, "MD CART"))
        end
        assert(table.concat(bytes) == site.expectedOpcode,
            string.format("opcode mismatch at gameplay-audio PC $%06X", site.address))
    end
end

local function rotateLeft(value, count) return ((value << count) | (value >> (32 - count))) & 0xffffffff end

local function loadedRomIdentity()
    local size = memory.getmemorydomainsize("MD CART")
    assert(size == 524288, "S1 REV01 ROM must be exactly 524,288 bytes")
    local crc, sha, words = 0xffffffff, {0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476, 0xc3d2e1f0}, {}
    local function processBlock(byteAt)
        for word = 0, 15 do
            local offset = word * 4
            words[word] = ((byteAt(offset) << 24) | (byteAt(offset + 1) << 16)
                | (byteAt(offset + 2) << 8) | byteAt(offset + 3)) & 0xffffffff
        end
        for word = 16, 79 do words[word] = rotateLeft(words[word - 3] ~ words[word - 8] ~ words[word - 14] ~ words[word - 16], 1) end
        local a, b, c, d, e = sha[1], sha[2], sha[3], sha[4], sha[5]
        for word = 0, 79 do
            local f, k
            if word < 20 then f, k = (b & c) | ((~b) & d), 0x5a827999
            elseif word < 40 then f, k = b ~ c ~ d, 0x6ed9eba1
            elseif word < 60 then f, k = (b & c) | (b & d) | (c & d), 0x8f1bbcdc
            else f, k = b ~ c ~ d, 0xca62c1d6 end
            local temporary = (rotateLeft(a, 5) + f + e + k + words[word]) & 0xffffffff
            e, d, c, b, a = d, c, rotateLeft(b, 30), a, temporary
        end
        sha[1], sha[2], sha[3], sha[4], sha[5] = (sha[1] + a) & 0xffffffff, (sha[2] + b) & 0xffffffff,
            (sha[3] + c) & 0xffffffff, (sha[4] + d) & 0xffffffff, (sha[5] + e) & 0xffffffff
    end
    for block = 0, size / 64 - 1 do
        local base = block * 64
        processBlock(function(offset)
            local value = memory.read_u8(base + offset, "MD CART")
            crc = crc ~ value
            for _ = 1, 8 do crc = ((crc >> 1) ~ (((crc & 1) ~= 0) and 0xedb88320 or 0)) & 0xffffffff end
            return value
        end)
    end
    processBlock(function(offset) if offset == 0 then return 0x80 end if offset == 61 then return 0x40 end return 0 end)
    local parts = {}
    for index = 1, 5 do parts[index] = string.format("%08x", sha[index]) end
    return {crc32 = string.format("%08x", (~crc) & 0xffffffff), sha1 = table.concat(parts)}
end

local function verifyIdentity()
    local rom = loadedRomIdentity()
    assert(rom.sha1 == EXPECTED_ROM_SHA1 and rom.crc32 == EXPECTED_ROM_CRC32, "loaded ROM is not Sonic 1 World REV01")
    assert(gameinfo.getromname() == "Sonic The Hedgehog (W) (REV01) [!]", "BizHawk game identity mismatch")
    assert(movie.isloaded() and movie.length() == EXPECTED_MOVIE_ROWS, "pinned complete-game BK2 input length mismatch")
    local header = movie.getheader()
    assert(header.Core == "Genplus-gx", "BK2 must select Genesis Plus GX")
    assert(header.emuVersion == "Version 2.11", "BK2 must select BizHawk 2.11")
    assert(header.GameName == "Sonic The Hedgehog (W) (REV01) [!]" and header.SHA1 == EXPECTED_MOVIE_HEADER_SHA1,
        "BK2 header identity mismatch")
    local digest = assert(os.getenv("OGGF_BIZHAWK_MOVIE_SHA256"), "launcher must supply actual BK2 SHA-256")
    assert(digest:lower() == EXPECTED_MOVIE_SHA256, "launcher BK2 SHA-256 mismatch")
    verifyOpcodeManifest()
    return rom
end

local function readU8(offset) return mainmemory.read_u8(SOUND_RAM + offset) end
local TRACK_LENGTH, MUSIC_BASE, NORMAL_BASE, SPECIAL_BASE = 0x30, 0x40, 0x220, 0x340
local NORMAL_ROLES = {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"}
local SPECIAL_ROLES = {"FM4", "PSG3"}

local function activeTrack(base)
    local status = readU8(base)
    return {active = (status & 0x80) ~= 0 and (status & 0x04) == 0, status = status,
        voice_control = readU8(base + 1)}
end

local function readTrackHeaders()
    -- Read every one of the 18 ROM headers at the complete tick boundary:
    -- 10 music, 6 normal SFX, and 2 special SFX. Only effective owners emit.
    local music, normal, special = {}, {}, {}
    for index = 0, 9 do music[index + 1] = activeTrack(MUSIC_BASE + index * TRACK_LENGTH) end
    for index, role in ipairs(NORMAL_ROLES) do normal[role] = activeTrack(NORMAL_BASE + (index - 1) * TRACK_LENGTH).active end
    for index, role in ipairs(SPECIAL_ROLES) do special[role] = activeTrack(SPECIAL_BASE + (index - 1) * TRACK_LENGTH).active end
    return {music = music, normal = normal, special = special}
end

local timeline, lifecycle = Timeline.newTimeline(0x81), Timeline.newInvocationLifecycle()
local queueBuffer, activeTick, frames = Timeline.newQueueBuffer(), nil, {}
local cycleDiagnostics = nil
local diagnosticTick = 0
local romIdentity = nil

local function frameRecord(frame)
    local record = frames[frame]
    if not record then record = {requests = {}, diagnostic_tick = nil, owners = nil}; frames[frame] = record end
    return record
end

local function queueObserved(slot)
    local soundId = (emu.getregister("M68K D0") or 0) & 0xff
    queueBuffer:write(slot, soundId, emu.framecount())
end

local function addRole(roles, role)
    for _, existing in ipairs(roles) do if existing == role then return end end
    roles[#roles + 1] = role
end

local function normalRole(voiceControl)
    return ({[2] = "FM3", [4] = "FM4", [5] = "FM5", [0x80] = "PSG1", [0xA0] = "PSG2",
        [0xC0] = "PSG3", [0xE0] = "PSG3"})[voiceControl]
end

local function specialRole(voiceControl)
    return (voiceControl & 0x80) ~= 0 and "PSG3" or "FM4"
end

local function candidateObserved(soundClass)
    if not activeTick or not activeTick.selectedRequest then return end
    local request = activeTick.selectedRequest
    if request.sound_class ~= soundClass then return end
    local candidate = {accepted = false, request = request, selected_sound_id = request.sound_id,
        declared_roles = {}, initialized_roles = {}, sound_class = soundClass}
    Timeline.assertSelectedIdentity(candidate.request, candidate.selected_sound_id)
    activeTick.candidates[#activeTick.candidates + 1] = candidate
    activeTick.currentCandidate = candidate
end

local function consumeObserved()
    -- CycleSoundQueue already cleared every slot; PlaySoundID chooses at most one candidate.
    local soundId = readU8(0x09)
    local request = queueBuffer:consume(soundId)
    if not activeTick then return end
    assert(request == nil or request == activeTick.cycledBySoundId[soundId],
        "PlaySoundID disagrees with CycleSoundQueue correlated request")
    activeTick.selectedRequest = request
end

local function cycleObserved()
    -- ROM-only diagnostics are validated here and intentionally never enter
    -- the v1 semantic JSON fields used for cross-producer equality.
    local queues = {readU8(0x0A), readU8(0x0B), readU8(0x0C)}
    local retained = activeTick ~= nil
    local queued = queueBuffer:cycle(queues, retained, readU8(0x09))
    if not retained then return end
    cycleDiagnostics = {priority = readU8(0x00), queues = queues, tick = diagnosticTick}
    for _, request in ipairs(queued) do timeline:queue(request.slot, request) end
    activeTick.cycledBySoundId = {}
    for _, request in ipairs(timeline:cycle(cycleDiagnostics.priority)) do
        -- Queue slots are cleared regardless of driver priority outcome. The dispatch/init hooks below
        -- are the sole acceptance authority, rather than this diagnostic candidate map.
        activeTick.cycledBySoundId[request.sound_id] = request
    end
end

local function bgmLoadObserved()
    local candidate = activeTick and activeTick.currentCandidate
    if not candidate or candidate.sound_class ~= "MUSIC" then return end
    Timeline.assertSelectedIdentity(candidate.request, candidate.selected_sound_id)
    candidate.accepted = true
    for _, role in ipairs({"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"}) do
        addRole(candidate.declared_roles, role); addRole(candidate.initialized_roles, role)
    end
end

local function normalRoleDeclared()
    local candidate = activeTick and activeTick.currentCandidate
    if candidate and candidate.sound_class == "SFX" then addRole(candidate.declared_roles, assert(normalRole((emu.getregister("M68K D3") or 0) & 0xff), "unknown normal SFX voice control")) end
end

local function normalRoleInitialized()
    local candidate = activeTick and activeTick.currentCandidate
    if candidate and candidate.sound_class == "SFX" then
        Timeline.assertSelectedIdentity(candidate.request, candidate.selected_sound_id)
        candidate.accepted = true
        addRole(candidate.initialized_roles, assert(normalRole((emu.getregister("M68K D4") or 0) & 0xff), "unknown normal SFX initialized role"))
    end
end

local function specialRoleDeclared()
    local candidate = activeTick and activeTick.currentCandidate
    if candidate and candidate.sound_class == "SPECIAL_SFX" then addRole(candidate.declared_roles, specialRole((emu.getregister("M68K D4") or 0) & 0xff)) end
end

local function specialRoleInitialized()
    local candidate = activeTick and activeTick.currentCandidate
    if candidate and candidate.sound_class == "SPECIAL_SFX" then
        Timeline.assertSelectedIdentity(candidate.request, candidate.selected_sound_id)
        candidate.accepted = true
        addRole(candidate.initialized_roles, specialRole((emu.getregister("M68K D4") or 0) & 0xff))
    end
end

local function closeTick(context)
    if not activeTick then return end
    local headers = readTrackHeaders()
    if activeTick.restoreMusic then timeline:restoreMusic() end
    for _, candidate in ipairs(activeTick.candidates) do
        Timeline.assertSelectedIdentity(candidate.request, candidate.selected_sound_id)
        timeline:dispatch(candidate.request, {accepted = candidate.accepted, declared_roles = candidate.declared_roles,
            initialized_roles = candidate.initialized_roles, headers = headers})
    end
    local tick = timeline:closeTick(headers)
    local frame = frameRecord(tick.bk2_frame)
    for _, request in ipairs(tick.requests) do frame.requests[#frame.requests + 1] = request end
    frame.diagnostic_tick, frame.owners = tick.diagnostic_tick, tick.owners
    activeTick, cycleDiagnostics = nil, nil
    diagnosticTick = diagnosticTick + 1
end

local function emit(context)
    assert(queueBuffer:baselineMusicId() == 0x81, "frame-860 music baseline lacks preceding queue-1 $81 provenance")
    context.log(Timeline.canonicalJson({type = "metadata", schema = "s1_gameplay_audio_timeline.v1",
        capture = "s1_ghz_gameplay_audio_reference", rom = romIdentity,
        bk2 = {sha256 = EXPECTED_MOVIE_SHA256}, producer = "BizHawk 2.11 / Genesis Plus GX",
        segment_start_bk2_frame = 860, segment_end_bk2_frame = 4975, terminal_frame_count = 4115,
        field_inventory = {record_types = {"baseline", "frame", "terminal"}, ownership_roles = {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"},
            sound_classes = {"MUSIC", "SFX", "SPECIAL_SFX", "COMMAND"}, owner_classes = {"NONE", "MUSIC", "NORMAL_SFX", "SPECIAL_SFX"}}}))
    local baseline = timeline:baseline()
    baseline.owners = Timeline.jsonOwnerVector(baseline.owners)
    context.log(Timeline.canonicalJson(baseline))
    local diagnosticFrameCount = 0
    for frame = SEGMENT_START, SEGMENT_END - 1 do
        local record = frameRecord(frame)
        assert(record.owners ~= nil, "missing complete UpdateMusic tick for semantic BK2 frame " .. frame)
        if record.diagnostic_tick ~= nil then diagnosticFrameCount = diagnosticFrameCount + 1 end
        context.log(Timeline.canonicalJson({type = "frame", bk2_frame = frame, diagnostic_tick = record.diagnostic_tick,
            requests = record.requests, owners = Timeline.jsonOwnerVector(record.owners)}))
    end
    context.log(Timeline.canonicalJson(timeline:terminal(diagnosticFrameCount)))
    context.finish()
end

ProbeRuntime.run({
    stage = function()
        if not romIdentity then romIdentity = verifyIdentity() end
        return true
    end,
    hooks = {
        {name = "s1_gameplay_audio_queue_0", address = 0x138E, callback = function() queueObserved(QUEUE_PCS[0x138E]) end},
        {name = "s1_gameplay_audio_queue_1", address = 0x1394, callback = function() queueObserved(QUEUE_PCS[0x1394]) end},
        {name = "s1_gameplay_audio_queue_2", address = 0x139A, callback = function() queueObserved(QUEUE_PCS[0x139A]) end},
        {name = "s1_gameplay_audio_update_entry", address = UPDATE_MUSIC, callback = function()
            local action = lifecycle:entry((emu.getregister("M68K A7") or 0) & 0xffffffff, emu.framecount())
            if action == "open" and emu.framecount() >= SEGMENT_START and emu.framecount() < SEGMENT_END then
                timeline:beginTick(emu.framecount(), diagnosticTick)
                activeTick = {candidates = {}, cycledBySoundId = {}, currentCandidate = nil, selectedRequest = nil}
            end
        end},
        {name = "s1_gameplay_audio_cycle", address = CYCLE_SOUND_QUEUE, callback = function() cycleObserved() end},
        {name = "s1_gameplay_audio_consume", address = PLAY_SOUND_ID, callback = function() consumeObserved() end},
        {name = "s1_gameplay_audio_play_sega_return", address = PLAY_SEGA_RETURN, callback = function()
            if lifecycle:playSegaAbnormalExit() == "reset" and activeTick then timeline:abandonTick(); activeTick = nil end
        end},
        {name = "s1_gameplay_audio_bgm", address = PLAY_BGM, callback = function() candidateObserved("MUSIC") end},
        {name = "s1_gameplay_audio_bgm_load", address = BGM_LOAD_MUSIC, callback = function() bgmLoadObserved() end},
        {name = "s1_gameplay_audio_sfx", address = PLAY_SFX, callback = function() candidateObserved("SFX") end},
        {name = "s1_gameplay_audio_sfx_declared", address = NORMAL_ROLE_DECLARED, callback = function() normalRoleDeclared() end},
        {name = "s1_gameplay_audio_sfx_initialized", address = NORMAL_ROLE_INITIALIZED, callback = function() normalRoleInitialized() end},
        {name = "s1_gameplay_audio_special", address = PLAY_SPECIAL, callback = function() candidateObserved("SPECIAL_SFX") end},
        {name = "s1_gameplay_audio_special_declared", address = SPECIAL_ROLE_DECLARED, callback = function() specialRoleDeclared() end},
        {name = "s1_gameplay_audio_special_initialized", address = SPECIAL_ROLE_INITIALIZED, callback = function() specialRoleInitialized() end},
        {name = "s1_gameplay_audio_restore_previous_music", address = RESTORE_PREVIOUS_MUSIC, callback = function()
            if activeTick then activeTick.restoreMusic = true end
        end},
        {name = "s1_gameplay_audio_update_return", address = UPDATE_MUSIC_RETURN, callback = function(context)
            local action = lifecycle:close(); if action == "close" then closeTick(context) end
        end}
    },
    onFrame = function(context)
        if emu.framecount() >= SEGMENT_END and not activeTick then emit(context) end
    end,
    continueAfterMovie = true
})
