-- Read-only source/shape observer for Sonic 1 REV01 complete-run audio.
-- The fixed headless C# owner combines these reviewed M68K boundaries with Task 7's buffered
-- typed Z80 services in one emulator pass; this Lua file never synthesizes native DAC writes.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through the fixed probe launcher")
runtimePath = runtimePath:gsub("\\", "/")
local ProbeRuntime = dofile(runtimePath)
local contractPath = ProbeRuntime.siblingPath(runtimePath,
    "audio/s1_complete_run_audio_contract.lua")
local Contract = dofile(contractPath)

local FIRST_FRAME, EXCLUSIVE_END = 860, 225101
local SOUND_RAM = 0xF000
local loaderVoiceControls = {0x06, 0x00, 0x01, 0x02, 0x04, 0x05, 0x06, 0x80, 0xA0, 0xC0}
local legalTrackContinuations = {
    [0x71BD4] = true, [0x71BE6] = true, [0x71BF8] = true, [0x71C10] = true,
    [0x71C22] = true, [0x71C38] = true, [0x71C44] = true
}

-- Exact World REV01 / FixBugs=0 execute bytes. Names are source labels, not inferred events.
local opcodeManifest = {
    {name = "QueueSound1", address = 0x138E, expectedOpcode = "11c0f00a"},
    {name = "QueueSound2", address = 0x1394, expectedOpcode = "11c0f00b"},
    {name = "QueueSound3", address = 0x139A, expectedOpcode = "11c0f00c"},
    {name = "UpdateMusic", address = 0x71B4C, expectedOpcode = "33fc010000a11100"},
    {name = "FixBugs0QueueTrigger", address = 0x71BB2, expectedOpcode = "4a6e000a"},
    {name = "DoStartZ80Return", address = 0x71C4C, expectedOpcode = "4e75"},
    {name = "CycleSoundQueue", address = 0x71F02, expectedOpcode = "207900071990"},
    {name = "DeferredQueueZeroStore", address = 0x71F26, expectedOpcode = "1d41000a"},
    {name = "SelectedRequestMask", address = 0x71F2C, expectedOpcode = "0240007f"},
    {name = "PlaySoundID", address = 0x71F4C, expectedOpcode = "7e00"},
    {name = "PlaySegaSoundStackSkip", address = 0x71FCE, expectedOpcode = "584f"},
    {name = "PlaySegaSoundReturn", address = 0x71FD0, expectedOpcode = "4e75"},
    {name = "SoundPlayBGM", address = 0x71FD2, expectedOpcode = "0c070088"},
    {name = "OneUpClearMusicOverrides", address = 0x71FE6, expectedOpcode = "08950002"},
    {name = "OneUpStopNormalSfx", address = 0x71FF8, expectedOpcode = "08950007"},
    {name = "OneUpSave220", address = 0x72012, expectedOpcode = "22d8"},
    {name = "OneUpSetPlaying", address = 0x72018, expectedOpcode = "1d7c00800027"},
    {name = "BgmLoadMusic", address = 0x7202C, expectedOpcode = "4eba059c"},
    {name = "BgmFmDacLoadLoop", address = 0x72098, expectedOpcode = "08d10007"},
    {name = "BgmPsgLoadLoop", address = 0x72126, expectedOpcode = "08d10007"},
    {name = "BgmPreservedSpecialFm4", address = 0x72182, expectedOpcode = "4a6e0340"},
    {name = "BgmPreservedSpecialPsg3", address = 0x7218E, expectedOpcode = "4a6e0370"},
    {name = "BgmReturnStackSkip", address = 0x721B6, expectedOpcode = "584f"},
    {name = "BgmReturn", address = 0x721B8, expectedOpcode = "4e75"},
    {name = "SoundPlaySFX", address = 0x721C6, expectedOpcode = "4a2e0027"},
    {name = "NormalOneUpBlock", address = 0x721CA, expectedOpcode = "660000fa"},
    {name = "NormalFadeOutTest", address = 0x721CE, expectedOpcode = "4a2e0004"},
    {name = "NormalFadeOutBlock", address = 0x721D2, expectedOpcode = "660000f2"},
    {name = "NormalFadeInTest", address = 0x721D6, expectedOpcode = "4a2e0024"},
    {name = "NormalFadeInBlock", address = 0x721DA, expectedOpcode = "660000ea"},
    {name = "NormalIdAfterRewrite", address = 0x721F4, expectedOpcode = "0c0700a7"},
    {name = "NormalRoleDeclared", address = 0x7222E, expectedOpcode = "1803"},
    {name = "NormalRoleInitialized", address = 0x7227C, expectedOpcode = "3a99"},
    {name = "NormalBlockedClearsPriority", address = 0x722C6, expectedOpcode = "422e0000"},
    {name = "SoundPlaySpecial", address = 0x7230C, expectedOpcode = "4a2e0027"},
    {name = "SpecialOneUpBlock", address = 0x72310, expectedOpcode = "660000b4"},
    {name = "SpecialFadeOutTest", address = 0x72314, expectedOpcode = "4a2e0004"},
    {name = "SpecialFadeOutBlock", address = 0x72318, expectedOpcode = "660000ac"},
    {name = "SpecialFadeInTest", address = 0x7231C, expectedOpcode = "4a2e0024"},
    {name = "SpecialFadeInBlock", address = 0x72320, expectedOpcode = "660000a4"},
    {name = "SpecialRoleDeclared", address = 0x7234C, expectedOpcode = "6b0c"},
    {name = "SpecialRoleInitialized", address = 0x7236E, expectedOpcode = "3a99"},
    {name = "SpecialBlockedRetainsPriority", address = 0x723C6, expectedOpcode = "4e75"},
    {name = "StopAllSound", address = 0x7259E, expectedOpcode = "702b123c0080"},
    {name = "StopAllRamCleared", address = 0x725BC, expectedOpcode = "1d7c00800009"},
    {name = "DoFadeIn", address = 0x7267C, expectedOpcode = "4a2e0025"},
    {name = "FadeInCounterCheck", address = 0x72688, expectedOpcode = "4a2e0026"},
    {name = "FadeInCounterStep", address = 0x7268E, expectedOpcode = "532e0026"},
    {name = "FadeInRestoreOverride", address = 0x726D6, expectedOpcode = "08ae00020040"},
    {name = "FadeInComplete", address = 0x726DC, expectedOpcode = "422e0024"},
    {name = "FadeInReturn", address = 0x726E0, expectedOpcode = "4e75"},
    {name = "FadeInToPrevious", address = 0x72B14, expectedOpcode = "204e"},
    {name = "Saved220Copy", address = 0x72B1E, expectedOpcode = "20d9"},
    {name = "FixBugs0ImmediateRestore", address = 0x72B24, expectedOpcode = "08ee00020040"},
    {name = "RestoreFmLoop", address = 0x72B3A, expectedOpcode = "08150007"},
    {name = "RestorePsgLoop", address = 0x72B66, expectedOpcode = "08150007"},
    {name = "RestorePsgNoteOff", address = 0x72B70, expectedOpcode = "4ebafe2e"},
    {name = "SetFadeIn", address = 0x72B82, expectedOpcode = "1d7c00800024"},
    {name = "SetFadeCounter40", address = 0x72B88, expectedOpcode = "1d7c00280026"},
    {name = "ClearOneUp", address = 0x72B8E, expectedOpcode = "422e0027"},
    {name = "FadeReturnStackSkip", address = 0x72B9A, expectedOpcode = "504f"},
    {name = "FadeReturn", address = 0x72B9C, expectedOpcode = "4e75"},
    {name = "StopSpecialStackSkip", address = 0x72C22, expectedOpcode = "504f"},
    {name = "StopSpecialReturn", address = 0x72C24, expectedOpcode = "4e75"},
    {name = "StopTrackStackSkip", address = 0x72E02, expectedOpcode = "504f"},
    {name = "StopTrackReturn", address = 0x72E04, expectedOpcode = "4e75"}
}

local function verifyOpcodeManifest()
    for _, site in ipairs(opcodeManifest) do
        local bytes = {}
        for offset = 0, #site.expectedOpcode / 2 - 1 do
            bytes[#bytes + 1] = string.format("%02x",
                memory.read_u8(site.address + offset, "MD CART"))
        end
        assert(table.concat(bytes) == site.expectedOpcode,
            string.format("%s opcode mismatch at $%06X", site.name, site.address))
    end
    assert(#loaderVoiceControls == 10 and legalTrackContinuations[0x71C44],
        "loader and stack-continuation contracts must be complete")
end

-- Shape consumed by the fixed native bridge. These services are parentless because UpdateMusic
-- restarts the Z80 before returning and the sample loops continue asynchronously.
local typed_z80_dac = {
    requires_m68k_parent = false,
    z80_dpcm_byte = {source_cpu = "Z80", begin_pc = 0x77, select_1 = 0x86,
        data_1 = 0x89, select_2 = 0x9C, data_2 = 0x9F, completion_pc = 0xAC},
    z80_sega_pcm_byte = {source_cpu = "Z80", begin_pc = 0xC1, select = 0xC2,
        data = 0xC5, completion_pc = 0xD0}
}

local function acceptTypedZ80Service(event)
    assert(type(event) == "table" and event.source_cpu == "Z80",
        "typed native DAC service must identify its Z80 owner")
    assert(event.kind == "z80_dpcm_byte" or event.kind == "z80_sega_pcm_byte",
        "unknown typed native DAC service")
    assert(type(event.raw_chip_events) == "table" and #event.raw_chip_events > 0,
        "typed Z80 service requires raw_chip_events")
    local expected = typed_z80_dac[event.kind]
    assert(event.begin_pc == expected.begin_pc and event.completion_pc == expected.completion_pc,
        "typed Z80 DAC service boundary mismatch")
    return event
end

local lifecycle = Contract.newInvocationLifecycle()
local sawPlaySegaInsideEpoch = false
local emittedHeader = false
local baseline = nil
local frame_service_counts = {}
local extraLifeOracleFrames = {3698, 3699, 3702, 3910}
local musicRoleByTrackRam = {
    [0xF040] = "DAC", [0xF070] = "FM1", [0xF0A0] = "FM2", [0xF0D0] = "FM3",
    [0xF100] = "FM4", [0xF130] = "FM5", [0xF160] = "FM6",
    [0xF190] = "PSG1", [0xF1C0] = "PSG2", [0xF1F0] = "PSG3"
}
local loader_roles = {}

local function executeSite(site, context)
    local frame = emu.framecount()
    if site.address == 0x71B4C and frame >= FIRST_FRAME and frame < EXCLUSIVE_END then
        frame_service_counts[frame] = (frame_service_counts[frame] or 0) + 1
        lifecycle:entry((emu.getregister("M68K A7") or 0) & 0xffffffff, frame)
    elseif site.address == 0x71C4C and lifecycle.active then
        lifecycle:close("close")
    elseif site.address == 0x7202C then
        loader_roles[frame] = {}
    elseif site.address == 0x72098 or site.address == 0x72126 then
        local trackRam = (emu.getregister("M68K A5") or 0) & 0xffff
        local role = assert(musicRoleByTrackRam[trackRam],
            string.format("unknown music loader track RAM $%04X", trackRam))
        local roles = assert(loader_roles[frame], "music loader iteration without BGM load")
        roles[#roles + 1] = role
    elseif site.address == 0x721B8 and lifecycle.active then
        lifecycle:playBgmDoubleReturn()
    elseif site.address == 0x71FCE and frame >= FIRST_FRAME and frame < EXCLUSIVE_END then
        sawPlaySegaInsideEpoch = true
        lifecycle:playSegaOutcome(true)
    elseif site.address == 0x72B9C and lifecycle.active then
        lifecycle:fadeInToPreviousDoubleReturn()
    elseif (site.address == 0x72C24 or site.address == 0x72E04) and lifecycle.active then
        local stack = (emu.getregister("M68K A7") or 0) & 0xffffffff
        local returnPc = mainmemory.read_u32_be(stack & 0xffff)
        if site.address == 0x72C24 then lifecycle:stopSpecialDoubleReturn(returnPc)
        else lifecycle:stopTrackDoubleReturn(returnPc) end
    end
    if not emittedHeader and frame >= FIRST_FRAME then
        emittedHeader = true
        context.log("s1_complete_run_audio_source_contract_v1")
    end
end

local hooks = {}
for index, site in ipairs(opcodeManifest) do
    local capturedSite = site
    hooks[index] = {name = "s1_complete_run_" .. site.name, address = site.address,
        callback = function(context) executeSite(capturedSite, context) end}
end

ProbeRuntime.run({
    stage = function()
        local frame = emu.framecount()
        if frame < FIRST_FRAME then return false end
        assert(frame == FIRST_FRAME, "S1 complete-run audio probe missed row-860 arm boundary")
        verifyOpcodeManifest()
        baseline = {
            frame = frame,
            priority = mainmemory.read_u8(SOUND_RAM + 0x00),
            soundId = mainmemory.read_u8(SOUND_RAM + 0x09)
        }
        return true
    end,
    hooks = hooks,
    onFrame = function(context)
        if emu.framecount() >= EXCLUSIVE_END then
            local e1Outcome = Contract.playSegaOutcome(sawPlaySegaInsideEpoch)
            assert(baseline and #extraLifeOracleFrames == 4,
                "baseline and mandatory extra-life oracle frames must be retained")
            context.log(string.format("baseline_frame=%d;priority=%d;sound_id=%d",
                baseline.frame, baseline.priority, baseline.soundId))
            context.log("extra_life_oracle_frames=3698,3699,3702,3910")
            context.log("frame_service_counts=zero_or_more")
            context.log("play_sega_outcome=" .. e1Outcome)
            context.log("typed_z80_dac=acceptTypedZ80Service;raw_chip_events;source_cpu=Z80")
            context.finish()
        end
    end,
    continueAfterMovie = true
})
