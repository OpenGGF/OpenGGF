-- Read-only Sonic 1 REV01 music-driver observer.
-- The callback-validation mode deliberately preserves BizHawk callback
-- arguments before the production capture chooses its bus-event source.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)
local contractPath = runtimePath:gsub("/probes/probe_runtime.lua$",
    "/audio/s1_audio_parity_contract.lua")
local AudioContract = dofile(contractPath)

local SOUND_RAM = 0xF000
local GAME_MODE = 0xF600
local LEVEL_SELECT_ITEM = 0xFF82
local LEVEL_SELECT_SOUND = 0xFF84
local UPDATE_MUSIC = 0x71B4C
local UPDATE_MUSIC_RETURN = 0x71C4C
local PLAY_SEGA_RETURN = 0x71FD0
local SOUND_PLAY_BGM = 0x71FD2
local VALIDATE_ONLY = os.getenv("OGGF_AUDIO_CALLBACK_VALIDATE_ONLY") == "1"
local CAPTURE_DEBUG = os.getenv("OGGF_AUDIO_CAPTURE_DEBUG") == "1"
local EXPECTED_ROM_SHA1 = "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b"
local EXPECTED_ROM_CRC32 = "afe05eee"
local EXPECTED_MOVIE_SHA256 = "622ff642d0b0835a4f77bee568f2413f288ead3306a8bc2a93e8d8f77f24ca9c"
local EXPECTED_MOVIE_OPAQUE_HASH = "09DADB5071EB35050067A32462E39C5F"
local GHZ_ASSET_BASE = 0x745DC
local GHZ_ASSET_END = 0x74D44
local ACTIVE_LOOP_COUNTERS = {0, 1}

local callbackAddresses = {
    [0xA04000] = "fm_port0_address",
    [0xA04001] = "fm_port0_data",
    [0xA04002] = "fm_port1_address",
    [0xA04003] = "fm_port1_data",
    [0xC00011] = "psg"
}

-- Complete shipped-ROM write-site fallback, derived from
-- docs/s1disasm/sonic.lst. Opcode bytes are verified against the loaded
-- `MD CART` domain before any capture is allowed.
local fallbackManifest = {
    {address = 0x7273A, operation = "fm0_address", source = "D0", expectedOpcode = "13c000a04000"},
    {address = 0x72752, operation = "fm0_data", source = "D1", expectedOpcode = "13c100a04001"},
    {address = 0x72770, operation = "fm1_address", source = "D0", expectedOpcode = "13c000a04002"},
    {address = 0x72788, operation = "fm1_data", source = "D1", expectedOpcode = "13c100a04003"},
    {address = 0x7225E, operation = "psg", source = "D0", expectedOpcode = "13c000c00011"},
    {address = 0x72268, operation = "psg", source = "D0", expectedOpcode = "13c000c00011"},
    {address = 0x723B6, operation = "psg", source = "D4", expectedOpcode = "13c400c00011"},
    {address = 0x723C0, operation = "psg", source = "D4", expectedOpcode = "13c400c00011"},
    {address = 0x7246A, operation = "psg", source = "$1F(A0)", expectedOpcode = "13e8001f00c00011"},
    {address = 0x724DC, operation = "psg", source = "$1F(A5)", expectedOpcode = "13ed001f00c00011"},
    {address = 0x72912, operation = "psg", source = "D0", expectedOpcode = "13c000c00011"},
    {address = 0x72918, operation = "psg", source = "D6", expectedOpcode = "13c600c00011"},
    {address = 0x72984, operation = "psg", source = "D6", expectedOpcode = "13c600c00011"},
    {address = 0x729AE, operation = "psg", source = "D0", expectedOpcode = "13c000c00011"},
    {address = 0x729BC, operation = "psg", source = "#$9F", expectedOpcode = "10bc009f"},
    {address = 0x729C0, operation = "psg", source = "#$BF", expectedOpcode = "10bc00bf"},
    {address = 0x729C4, operation = "psg", source = "#$DF", expectedOpcode = "10bc00df"},
    {address = 0x729C8, operation = "psg", source = "#$FF", expectedOpcode = "10bc00ff"},
    {address = 0x72DFA, operation = "psg", source = "$1F(A0)", expectedOpcode = "13e8001f00c00011"},
    {address = 0x72E16, operation = "psg", source = "-1(A4)", expectedOpcode = "13ecffff00c00011"}
}

local function verifyFallbackManifest()
    local seen = {}
    for _, site in ipairs(fallbackManifest) do
        assert(not seen[site.address], string.format("duplicate fallback PC $%06X", site.address))
        seen[site.address] = true
        local bytes = {}
        for offset = 0, (#site.expectedOpcode / 2) - 1 do
            bytes[#bytes + 1] = string.format("%02x", memory.read_u8(site.address + offset, "MD CART"))
        end
        assert(table.concat(bytes) == site.expectedOpcode,
            string.format("opcode mismatch at fallback PC $%06X", site.address))
    end
    assert(#fallbackManifest == 20, "fallback manifest must cover four FM and sixteen PSG write sites")
end

local function rotateLeft(value, count)
    return ((value << count) | (value >> (32 - count))) & 0xffffffff
end

local function loadedRomIdentity()
    local size = memory.getmemorydomainsize("MD CART")
    assert(size == 524288, "S1 REV01 ROM must be exactly 524,288 bytes")

    local crc = 0xffffffff
    local sha = {0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476, 0xc3d2e1f0}
    local words = {}
    local function processShaBlock(byteAt)
        for word = 0, 15 do
            local offset = word * 4
            words[word] = ((byteAt(offset) << 24) | (byteAt(offset + 1) << 16)
                | (byteAt(offset + 2) << 8) | byteAt(offset + 3)) & 0xffffffff
        end
        for word = 16, 79 do
            words[word] = rotateLeft(words[word - 3] ~ words[word - 8]
                ~ words[word - 14] ~ words[word - 16], 1)
        end
        local a, b, c, d, e = sha[1], sha[2], sha[3], sha[4], sha[5]
        for word = 0, 79 do
            local f, k
            if word < 20 then
                f, k = (b & c) | ((~b) & d), 0x5a827999
            elseif word < 40 then
                f, k = b ~ c ~ d, 0x6ed9eba1
            elseif word < 60 then
                f, k = (b & c) | (b & d) | (c & d), 0x8f1bbcdc
            else
                f, k = b ~ c ~ d, 0xca62c1d6
            end
            local temporary = (rotateLeft(a, 5) + f + e + k + words[word]) & 0xffffffff
            e, d, c, b, a = d, c, rotateLeft(b, 30), a, temporary
        end
        sha[1] = (sha[1] + a) & 0xffffffff
        sha[2] = (sha[2] + b) & 0xffffffff
        sha[3] = (sha[3] + c) & 0xffffffff
        sha[4] = (sha[4] + d) & 0xffffffff
        sha[5] = (sha[5] + e) & 0xffffffff
    end

    for block = 0, (size / 64) - 1 do
        local base = block * 64
        processShaBlock(function(offset)
            local value = memory.read_u8(base + offset, "MD CART")
            crc = crc ~ value
            for _ = 1, 8 do
                crc = ((crc >> 1) ~ (((crc & 1) ~= 0) and 0xedb88320 or 0)) & 0xffffffff
            end
            return value
        end)
    end
    processShaBlock(function(offset)
        if offset == 0 then return 0x80 end
        if offset == 61 then return 0x40 end
        return 0
    end)
    local shaParts = {}
    for index = 1, 5 do shaParts[index] = string.format("%08x", sha[index]) end
    return {crc32 = string.format("%08x", (~crc) & 0xffffffff), sha1 = table.concat(shaParts)}
end

local function verifyIdentity()
    local rom = loadedRomIdentity()
    assert(rom.sha1 == EXPECTED_ROM_SHA1, "loaded ROM is not Sonic 1 World REV01 (SHA-1)")
    assert(rom.crc32 == EXPECTED_ROM_CRC32, "loaded ROM is not Sonic 1 World REV01 (CRC32)")
    assert(gameinfo.getromname() == "Sonic The Hedgehog (W) (REV01) [!]",
        "BizHawk ROM name does not identify S1 World REV01")
    assert(movie.isloaded(), "pinned S1 sound-test BK2 must be loaded")
    assert(movie.length() == 989, "pinned S1 sound-test BK2 must have 989 input rows")
    local header = movie.getheader()
    assert(header.Core == "Genplus-gx", "S1 parity BK2 must select Genesis Plus GX")
    assert(header.emuVersion == "Version 2.11", "S1 parity BK2 must select BizHawk 2.11")
    assert(header.GameName == "Sonic The Hedgehog (W) (REV01) [!]", "S1 parity BK2 game mismatch")
    assert(header.SHA1 == EXPECTED_MOVIE_OPAQUE_HASH, "S1 parity BK2 opaque identity mismatch")
    local movieSha256 = AudioContract.requireSha256(
        assert(os.getenv("OGGF_BIZHAWK_MOVIE_SHA256"),
            "run_bizhawk_lua must supply the actual BK2 SHA-256"),
        EXPECTED_MOVIE_SHA256, "launcher BK2")
    return rom, movieSha256
end

local function readU8(offset) return mainmemory.read_u8(SOUND_RAM + offset) end
local function readU16(offset) return mainmemory.read_u16_be(SOUND_RAM + offset) end
local function readU32(offset) return mainmemory.read_u32_be(SOUND_RAM + offset) end

local roles = {"DAC", "FM1", "FM2", "FM3", "FM4", "FM5", "FM6", "PSG1", "PSG2", "PSG3"}
local expectedVoiceControls = {6, 0, 1, 2, 4, 5, 6, 0x80, 0xa0, 0xc0}

local function readRomSnapshot()
    local fadeOutCount = readU8(0x04)
    local fadeInFlag = readU8(0x24)
    local fadeOut = fadeOutCount ~= 0
    local tracks = {}
    local trackDiagnostics = {}
    for index = 1, 10 do
        local base = 0x40 + (index - 1) * 0x30
        local status = readU8(base)
        local voiceControl = readU8(base + 0x01)
        if (status & 0x80) ~= 0 then
            local voiceControlMatches = voiceControl == expectedVoiceControls[index]
                or (index == 10 and voiceControl == 0xe0)
            assert(voiceControlMatches,
                string.format("active %s voice-control mismatch", roles[index]))
        end
        local loopCounters = {}
        for offset = 0, 11 do loopCounters[offset + 1] = readU8(base + 0x24 + offset) end
        local stackPointer = readU8(base + 0x0D)
        local returnStack = {}
        if (status & 0x80) ~= 0 then
            assert(stackPointer <= 0x30 and ((0x30 - stackPointer) % 4) == 0,
                string.format("active %s return-stack cursor is invalid", roles[index]))
            for offset = stackPointer, 0x2F, 4 do
                returnStack[#returnStack + 1] = readU32(base + offset)
            end
        end
        local dataPointer = readU32(base + 0x04)
        tracks[index] = {
            status = status,
            voiceControl = voiceControl,
            baseFrequency = readU16(base + 0x10),
            dataPointer = dataPointer,
            detune = readU8(base + 0x1E),
            duration = readU8(base + 0x0E),
            durationReload = readU8(base + 0x0F),
            loopCounters = loopCounters,
            panAmsFms = readU8(base + 0x0A),
            returnStack = returnStack,
            stackPointer = stackPointer,
            transpose = readU8(base + 0x08),
            voiceOrEnvelope = readU8(base + 0x0B),
            volume = readU8(base + 0x09),
            volumeEnvelopeIndex = readU8(base + 0x0C)
        }
        trackDiagnostics[index] = {
            ams_fms_pan = readU8(base + 0x0A),
            data_position = dataPointer >= GHZ_ASSET_BASE and dataPointer < GHZ_ASSET_END
                and dataPointer - GHZ_ASSET_BASE or -1,
            duration_countdown = readU8(base + 0x0E),
            duration_reload = readU8(base + 0x0F),
            envelope_cursor = readU8(base + 0x0C),
            envelope_or_voice = readU8(base + 0x0B),
            modulation_delay = readU8(base + 0x18),
            modulation_delta = AudioContract.s8(readU8(base + 0x1A)),
            modulation_enabled = (status & 0x08) ~= 0,
            modulation_speed = readU8(base + 0x19),
            modulation_steps = readU8(base + 0x1B),
            modulation_value = AudioContract.s16(readU16(base + 0x1C)),
            note_fill_countdown = readU8(base + 0x12),
            note_fill_reload = readU8(base + 0x13),
            overridden = (status & 0x04) ~= 0,
            resting = (status & 0x02) ~= 0,
            role = roles[index],
            status = status,
            tie_next = (status & 0x10) ~= 0,
            voice_control = voiceControl
        }
    end
    return {
        assetBase = GHZ_ASSET_BASE,
        assetEnd = GHZ_ASSET_END,
        global = {
            fadeActive = (fadeOutCount ~= 0 or fadeInFlag ~= 0) and 1 or 0,
            fadeDelay = fadeOut and readU8(0x06) or readU8(0x25),
            fadeOut = fadeOut and 1 or 0,
            fadeSteps = fadeOut and fadeOutCount or readU8(0x26),
            speedUp = readU8(0x2A),
            tempoReload = readU8(0x02),
            tempoTimeout = readU8(0x01)
        },
        tracks = tracks
    }, {
        global = {
            communication = readU8(0x07), fade_in_flag = fadeInFlag,
            fade_out_counter = fadeOutCount, one_up = readU8(0x27), pause = readU8(0x03),
            priority = readU8(0x00), push = readU8(0x2C), queues = {readU8(0x0A), readU8(0x0B), readU8(0x0C)},
            ring_speaker = readU8(0x2B), sound_id = readU8(0x09), speed_up_reload = readU8(0x29),
            updating_dac = readU8(0x08), voice_selector = readU8(0x0E)
        },
        tracks = trackDiagnostics
    }
end

local function assertStableSoundTest()
    assert(mainmemory.read_u8(GAME_MODE) == 0x04, "capture left Level Select/Sound Test mode")
    assert(mainmemory.read_u16_be(LEVEL_SELECT_ITEM) == 0x14, "capture left the Sound Test row")
    assert(mainmemory.read_u16_be(LEVEL_SELECT_SOUND) == 0x01, "capture changed the selected GHZ sound")
    assert(readU8(0x03) == 0, "pause command contaminated capture")
    assert(readU8(0x04) == 0 and readU8(0x24) == 0, "fade command contaminated capture")
    -- `f_speedup` is exactly $00 off / $80 on in s1.sounddriver.asm. GHZ sound-test
    -- capture never requests the speed-up command, so any active or transition byte is contamination.
    assert(readU8(0x2A) == 0, "speed-up command contaminated capture")
    assert(readU8(0x09) == 0x80, "unexpected sound ID contaminated capture")
    assert(readU8(0x0A) == 0 and readU8(0x0B) == 0 and readU8(0x0C) == 0,
        "sound queue contaminated capture")
end

local function callbackArgument(value)
    local kind = type(value)
    if kind == "number" or kind == "string" or kind == "boolean" then return value end
    return {lua_type = kind, rendered = tostring(value)}
end

local function callbackArguments(...)
    local result = {}
    for index = 1, select("#", ...) do result[index] = callbackArgument(select(index, ...)) end
    return result
end

local validation = {
    epochReached = false,
    callbackCount = 0,
    pcDataCount = 0,
    lastDataPc = nil,
    identityLogged = false
}
local invocationLifecycle = AudioContract.newInvocationLifecycle()
local currentOrdinal = nil
local currentOpenFrame = nil
local currentRawEvents = nil
local currentDecodedEvents = nil
local currentDecoder = nil
local records = {}
local cycleDetector = AudioContract.newCycleDetector()
local callbackProof = AudioContract.newCallbackProof()
local romIdentity
local movieIdentity

local function beginCapturedInvocation(ordinal, openFrame)
    currentOrdinal = ordinal
    currentOpenFrame = openFrame
    currentRawEvents = {}
    currentDecodedEvents = {}
    currentDecoder = AudioContract.newYmDecoder()
end

local function emitCapture(context, proof)
    local header = movie.getheader()
    context.log(AudioContract.canonicalJson({
        capture = "s1_ghz_music_driver_reference",
        callback_contract = {
            arguments = {"address", "value", "flags"},
            proof = callbackProof:counts(),
            source = callbackProof:assertVerified()
        },
        cycle_start = proof.startOrdinal,
        diagnostic_fields = {
            global = {"priority", "pause", "fade flags", "queues", "sound id", "voice selector",
                "DAC update", "1-up", "speed-up reload", "communication", "ring speaker", "push"},
            track = {"resting", "note fill", "modulation phase", "raw status", "raw voice control"}
        },
        gating_fields = {
            global = {"tempo timeout", "tempo reload", "speed-up", "fade state"},
            track = {"active", "role", "hardware", "overridden", "do not attack", "modulation enabled",
                "sequence position", "transpose", "volume", "pan/AMS/FMS", "voice/envelope", "duration",
                "duration reload", "PSG envelope cursor", "base frequency", "detune", "live loop counters",
                "live return stack"}
        },
        launch_update_music_invocations = invocationLifecycle:launchInvocationCount(),
        movie = {
            archive_sha256 = movieIdentity,
            core = header.Core,
            emulator = header.emuVersion,
            game = header.GameName,
            input_rows = movie.length(),
            opaque_header_hash = header.SHA1
        },
        period = proof.period,
        rom = romIdentity,
        schema = "openggf.s1_audio_parity_reference.v1",
        terminal_record_count = proof.terminalRecordCount,
        type = "capture_metadata"
    }))
    for _, recordJson in ipairs(records) do context.log(recordJson) end
    context.finish()
end

local function closeCapturedInvocation(context)
    local function debugPhase(phase)
        if CAPTURE_DEBUG then
            context.log(AudioContract.canonicalJson({ordinal = currentOrdinal, phase = phase, type = "debug"}))
        end
    end
    assert(currentOrdinal == #records, "audio-driver ordinal is not continuous")
    debugPhase("decoder_finish")
    currentDecoder:finishTick()
    debugPhase("contamination")
    assertStableSoundTest()
    debugPhase("snapshot")
    local snapshotOk, rawSnapshot, diagnostics = pcall(readRomSnapshot)
    if not snapshotOk then
        if CAPTURE_DEBUG then
            context.log(AudioContract.canonicalJson({snapshot_error = tostring(rawSnapshot), type = "debug"}))
        end
        error(rawSnapshot, 0)
    end
    if currentOrdinal == 0 then
        callbackProof:assertVerified()
        local expectedActive = {true, true, true, true, true, true, false, true, true, true}
        for index, expected in ipairs(expectedActive) do
            local active = (rawSnapshot.tracks[index].status & 0x80) ~= 0
            assert(active == expected, string.format("GHZ initialized unexpected %s activity", roles[index]))
        end
    end
    debugPhase("normalize")
    local normalized = AudioContract.normalizeRom(rawSnapshot, ACTIVE_LOOP_COUNTERS)
    local stateHash = AudioContract.hashState(normalized)
    local eventHash = AudioContract.hashEvents(currentDecodedEvents)
    local record = {
        diagnostic = {
            emulator_frame = emu.framecount(),
            game_mode = mainmemory.read_u8(GAME_MODE),
            interrupt_mask = ((emu.getregister("M68K SR") or 0) >> 8) & 7,
            invocation_open_frame = currentOpenFrame,
            raw_state = diagnostics
        },
        events = currentDecodedEvents,
        ordinal = currentOrdinal,
        raw_bus = currentRawEvents,
        state = normalized,
        type = "tick"
    }
    records[#records + 1] = AudioContract.canonicalJson(record)
    debugPhase("cycle")
    local proof = cycleDetector:observe(stateHash, eventHash)
    currentOrdinal, currentOpenFrame, currentRawEvents, currentDecodedEvents, currentDecoder = nil, nil, nil, nil, nil
    if proof then emitCapture(context, proof) end
end

local hooks = {}
local function addHook(hook) hooks[#hooks + 1] = hook end

addHook({
    name = "s1_audio_update_music_entry",
    address = UPDATE_MUSIC,
    callback = function(context)
        local frame = emu.framecount()
        local sp = (emu.getregister("M68K A7") or 0) & 0xffffffff
        if VALIDATE_ONLY or CAPTURE_DEBUG then
            context.log(AudioContract.canonicalJson({
                active = invocationLifecycle:isActive(), event = "update_entry", frame = frame, sp = sp
            }))
        end
        local action = invocationLifecycle:entry(sp, frame)
        if action == "open_capture" then beginCapturedInvocation(#records, frame) end
    end
})

addHook({
    name = "s1_audio_update_music_return",
    address = UPDATE_MUSIC_RETURN,
    callback = function(context)
        if VALIDATE_ONLY or CAPTURE_DEBUG then
            context.log(AudioContract.canonicalJson({
                active = invocationLifecycle:isActive(), event = "update_return", frame = emu.framecount(),
                open_frame = invocationLifecycle:openEmulatorFrame()
            }))
        end
        local action = invocationLifecycle:close()
        if action == "close_capture" and not VALIDATE_ONLY then closeCapturedInvocation(context) end
    end
})

addHook({
    name = "s1_audio_play_sega_abnormal_return",
    address = PLAY_SEGA_RETURN,
    callback = function()
        invocationLifecycle:playSegaAbnormalExit()
    end
})

addHook({
    name = "s1_audio_ghz_epoch",
    address = SOUND_PLAY_BGM,
    callback = function(context)
        local soundId = (emu.getregister("M68K D7") or 0) & 0xFF
        local action = invocationLifecycle:acceptBgm(soundId)
        if action == "arm_tick_zero" then
            validation.epochReached = true
            beginCapturedInvocation(0, invocationLifecycle:openEmulatorFrame())
            if not VALIDATE_ONLY then return end
            context.log(AudioContract.canonicalJson({
                event = "epoch", frame = emu.framecount(), sound_ram_root = SOUND_RAM
            }))
        end
    end
})

for pc, port in pairs({[0x72752] = 0, [0x72788] = 1}) do
    addHook({
        name = string.format("s1_audio_callback_validation_pc_%06x", pc),
        address = pc,
        callback = function(context)
            if not invocationLifecycle:isArmed() then return end
            validation.pcDataCount = validation.pcDataCount + 1
            local d0 = (emu.getregister("M68K D0") or 0) & 0xFF
            local d1 = (emu.getregister("M68K D1") or 0) & 0xFF
            callbackProof:observeFmDataPc(port, d0, d1)
            validation.lastDataPc = {
                d0 = d0,
                d1 = d1,
                frame = emu.framecount(),
                pc = pc
            }
            if VALIDATE_ONLY then
                context.log(AudioContract.canonicalJson({event = "fm_data_pc", observed = validation.lastDataPc}))
            end
        end
    })
end

for address, operation in pairs(callbackAddresses) do
    addHook({
        name = string.format("s1_audio_callback_validation_write_%06x", address),
        address = address,
        kind = "write",
        callback = function(context, callbackAddress, value, flags, ...)
            if not invocationLifecycle:isArmed() then return end
            assert(select("#", ...) == 0, "BizHawk audio write callback argument count changed")
            assert(callbackAddress == address, "BizHawk audio write callback address mapping changed")
            assert(type(value) == "number" and value == math.floor(value) and value >= 0 and value <= 0xFF,
                "BizHawk audio write callback value mapping changed")
            validation.callbackCount = validation.callbackCount + 1
            local port = operation:match("port1") and 1 or 0
            if address == 0xC00011 then
                callbackProof:observePsg(value)
            elseif operation:match("_address$") then
                callbackProof:observeYmAddress(port, value)
            else
                callbackProof:observeYmData(port, value)
            end
            if VALIDATE_ONLY then
                context.log(AudioContract.canonicalJson({
                    arguments = callbackArguments(callbackAddress, value, flags),
                    d0 = (emu.getregister("M68K D0") or 0) & 0xFF,
                    d1 = (emu.getregister("M68K D1") or 0) & 0xFF,
                    event = "write_callback",
                    frame = emu.framecount(),
                    operation = operation,
                    pc = (emu.getregister("M68K PC") or 0) & 0xFFFFFF,
                    preceding_data_pc = validation.lastDataPc
                }))
            else
                assert(invocationLifecycle:isActive() and currentDecoder ~= nil,
                    "audio port write occurred outside the active captured UpdateMusic invocation")
                local busEvent
                if address == 0xC00011 then
                    busEvent = {kind = "psg", value = value}
                else
                    busEvent = {
                        kind = operation:match("_address$") and "address" or "data",
                        port = port,
                        value = value
                    }
                end
                if CAPTURE_DEBUG and #currentRawEvents < 8 then
                    context.log(AudioContract.canonicalJson({
                        address = callbackAddress, kind = busEvent.kind, port = busEvent.port,
                        type = "debug_bus", value = value
                    }))
                end
                currentRawEvents[#currentRawEvents + 1] = {
                    address = callbackAddress,
                    flags = flags,
                    kind = busEvent.kind,
                    port = busEvent.port,
                    source = "memory_callback",
                    value = value
                }
                local ok, decoded = pcall(function() return currentDecoder:feed(busEvent) end)
                if not ok then
                    if CAPTURE_DEBUG then
                        context.log(AudioContract.canonicalJson({
                            address = callbackAddress, decoder_error = tostring(decoded), kind = busEvent.kind,
                            port = busEvent.port, type = "debug_bus_failure", value = value
                        }))
                    end
                    error(decoded, 0)
                end
                if decoded then currentDecodedEvents[#currentDecodedEvents + 1] = decoded end
            end
        end
    })
end

verifyFallbackManifest()
romIdentity, movieIdentity = verifyIdentity()

ProbeRuntime.run({
    stage = function() return true end,
    continueAfterMovie = true,
    hooks = hooks,
    onFrame = function(context)
        if VALIDATE_ONLY and not validation.identityLogged then
            validation.identityLogged = true
            local header = movie.getheader()
            context.log(AudioContract.canonicalJson({
                event = "identity_api",
                movie_core = header.Core,
                movie_emu_version = header.emuVersion,
                movie_game_name = header.GameName,
                movie_length = movie.length(),
                movie_opaque_sha1 = header.SHA1,
                rom_hash = gameinfo.getromhash(),
                rom_name = gameinfo.getromname()
            }))
        end
        if context.movieFinished() then
            local function requireNeutral(player, controls)
                for control, pressed in pairs(controls) do
                    assert(not pressed, string.format("post-movie player %d input is not neutral: %s", player, control))
                end
            end
            requireNeutral(1, joypad.get(1))
            requireNeutral(2, joypad.get(2))
        end
        if VALIDATE_ONLY and validation.epochReached and callbackProof:isVerified() then
            context.log(AudioContract.canonicalJson({
                callback_count = validation.callbackCount,
                callback_proof = callbackProof:counts(),
                event = "callback_validation_complete",
                fm_data_pc_count = validation.pcDataCount
            }))
            context.finish()
        elseif VALIDATE_ONLY and emu.framecount() > 1100 then
            error("callback validation did not collect the required GHZ initialization window")
        elseif not VALIDATE_ONLY and invocationLifecycle:isArmed() and #records >= 36000 then
            error("S1 audio recurrence was not proven within 36,000 invocations")
        end
    end
})
