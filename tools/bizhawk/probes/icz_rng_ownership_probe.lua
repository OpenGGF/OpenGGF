local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local CAMERA_X_COPY = 0xEE80
local GAMEPLAY_COUNTER = 0xFE04
local V_INT = 0xFE0C
local V_INT_LOW = 0xFE0E
local RNG_SEED = 0xF636

local OBJECT_RAM = 0xB000
local OBJECT_SIZE = 0x4A
local OBJECT_END = 0xCAE2
local TRACE_OFFSET = 138117

local RNG_ENTRY = 0x001D24
local RNG_RTS = 0x001D4A
local PROCESS_SPRITES = 0x01AADA
local FIRST_SNOW_RETURN = 0x08B6C2
local CAMERA_ARM = 0x1000
local GFC_STOP = 0x58C0

local pending = nil
local ordinal = 0

local function u32(value)
    return value & 0xFFFFFFFF
end

local function swapWords32(value)
    return (((value >> 16) & 0xFFFF) | ((value & 0xFFFF) << 16)) & 0xFFFFFFFF
end

local function predict(seed)
    local d1 = seed & 0xFFFFFFFF
    if (d1 & 0xFFFF) == 0 then
        d1 = 0x2A6D365B
    end
    local d0 = d1
    d1 = u32(d1 << 2)
    d1 = u32(d1 + d0)
    d1 = u32(d1 << 3)
    d1 = u32(d1 + d0)
    d0 = (d0 & 0xFFFF0000) | (d1 & 0xFFFF)
    d1 = swapWords32(d1)
    d0 = (d0 & 0xFFFF0000) | (((d0 & 0xFFFF) + (d1 & 0xFFFF)) & 0xFFFF)
    d1 = (d1 & 0xFFFF0000) | (d0 & 0xFFFF)
    d1 = swapWords32(d1)
    return d0 & 0xFFFFFFFF, d1 & 0xFFFFFFFF
end

local function register(name)
    return emu.getregister(name) or 0
end

local function callerPc()
    local sp = register("M68K A7") & 0xFFFF
    assert(sp <= 0xFFFC, string.format("invalid A7 for return PC: %04X", sp))
    return mainmemory.read_u32_be(sp) & 0xFFFFFF
end

local function objectContext(registerName)
    local ptr = register("M68K " .. registerName) & 0xFFFF
    local delta = ptr - OBJECT_RAM
    if ptr < OBJECT_RAM or ptr >= OBJECT_END or delta % OBJECT_SIZE ~= 0 then
        return string.format("%s_ptr=%04X %s_slot=-1", registerName, ptr, registerName)
    end
    local slot = math.floor(delta / OBJECT_SIZE)
    return string.format(
        "%s_ptr=%04X %s_slot=%d %s_code=%08X %s_rtn=%02X %s_sub=%02X"
            .. " %s_x=%04X %s_y=%04X %s_parent3=%04X",
        registerName, ptr,
        registerName, slot,
        registerName, mainmemory.read_u32_be(ptr),
        registerName, mainmemory.read_u8(ptr + 0x05),
        registerName, mainmemory.read_u8(ptr + 0x2C),
        registerName, mainmemory.read_u16_be(ptr + 0x10),
        registerName, mainmemory.read_u16_be(ptr + 0x14),
        registerName, mainmemory.read_u16_be(ptr + 0x46))
end

local function prefix()
    return string.format(
        "emu=%d trace=%d gfc=%04X camera=%04X vint=%08X vintlow=%04X",
        emu.framecount(),
        emu.framecount() - TRACE_OFFSET,
        mainmemory.read_u16_be(GAMEPLAY_COUNTER),
        mainmemory.read_u16_be(CAMERA_X_COPY),
        mainmemory.read_u32_be(V_INT),
        mainmemory.read_u16_be(V_INT_LOW))
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0x05
            and mainmemory.read_u8(CURRENT_ACT) == 0x01
            and mainmemory.read_u16_be(CAMERA_X_COPY) >= CAMERA_ARM
    end,
    hooks = {
        {
            name = "icz_rng_entry",
            address = RNG_ENTRY,
            callback = function()
                assert(pending == nil, "nested/unmatched Random_Number entry")
                ordinal = ordinal + 1
                local seedBefore = mainmemory.read_u32_be(RNG_SEED)
                local predictedResult, predictedPost = predict(seedBefore)
                pending = {
                    ordinal = ordinal,
                    seedBefore = seedBefore,
                    predictedResult = predictedResult,
                    predictedPost = predictedPost,
                    caller = callerPc(),
                    a0 = objectContext("A0"),
                    a1 = objectContext("A1"),
                    d7 = register("M68K D7") & 0xFFFFFFFF,
                    entryPrefix = prefix()
                }
            end
        },
        {
            name = "icz_rng_rts",
            address = RNG_RTS,
            callback = function(context)
                assert(pending ~= nil, "Random_Number RTS without paired entry")
                local actualResult = register("M68K D0") & 0xFFFFFFFF
                local actualPost = mainmemory.read_u32_be(RNG_SEED)
                assert(actualResult == pending.predictedResult,
                    string.format("result mismatch predicted=%08X actual=%08X",
                        pending.predictedResult, actualResult))
                assert(actualPost == pending.predictedPost,
                    string.format("post-seed mismatch predicted=%08X actual=%08X",
                        pending.predictedPost, actualPost))
                context.log(string.format(
                    "%s ordinal=%d caller=%06X%s pre=%08X result=%08X post=%08X"
                        .. " d7=%08X %s %s",
                    pending.entryPrefix,
                    pending.ordinal,
                    pending.caller,
                    pending.caller == FIRST_SNOW_RETURN and " first_snow=1" or "",
                    pending.seedBefore,
                    actualResult,
                    actualPost,
                    pending.d7,
                    pending.a0,
                    pending.a1))
                local firstSnow = pending.caller == FIRST_SNOW_RETURN
                pending = nil
                if firstSnow then context.finish() end
            end
        },
        {
            name = "icz_rng_watchdog",
            address = PROCESS_SPRITES,
            callback = function(context)
                if mainmemory.read_u16_be(GAMEPLAY_COUNTER) > GFC_STOP then
                    assert(pending == nil, "watchdog reached with unmatched RNG entry")
                    context.log(prefix() .. " done=1")
                    context.finish()
                end
            end
        }
    }
})
