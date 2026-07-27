local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local START = assert(tonumber(os.getenv("OGGF_START")), "OGGF_START is required")
local STOP = assert(tonumber(os.getenv("OGGF_STOP")), "OGGF_STOP is required")
local TRACE_OFFSET = 27170

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local RNG_SEED = 0xF636
local OBJECT_RAM = 0xB000
local OBJECT_SIZE = 0x4A
local OBJECT_COUNT = 110
local DYNAMIC_FIRST = 3
local DYNAMIC_LAST = 92

local OBJ_AIR_COUNTDOWN = 0x00018164
local OBJ_BUBBLER = 0x0002F938

local function frame()
    return emu.framecount()
end

local function traceFrame()
    return frame() - TRACE_OFFSET
end

local function ptr16(registerName)
    return (emu.getregister(registerName) or 0) & 0xFFFF
end

local function slotFor(ptr)
    local delta = ptr - OBJECT_RAM
    if delta < 0 or delta >= OBJECT_SIZE * OBJECT_COUNT
            or delta % OBJECT_SIZE ~= 0 then
        return -1
    end
    return math.floor(delta / OBJECT_SIZE)
end

local function objectSummary(ptr)
    local slot = slotFor(ptr)
    if slot < 0 then
        return string.format("ptr=%04X slot=-1", ptr)
    end
    return string.format(
        "ptr=%04X slot=%d code=%08X routine=%02X subtype=%02X x=%04X y=%04X rf=%02X"
            .. " o32=%02X o33=%02X o34=%04X o36=%04X o38=%08X o3C=%08X",
        ptr,
        slot,
        mainmemory.read_u32_be(ptr),
        mainmemory.read_u8(ptr + 0x05),
        mainmemory.read_u8(ptr + 0x2C),
        mainmemory.read_u16_be(ptr + 0x10),
        mainmemory.read_u16_be(ptr + 0x14),
        mainmemory.read_u8(ptr + 0x04),
        mainmemory.read_u8(ptr + 0x32),
        mainmemory.read_u8(ptr + 0x33),
        mainmemory.read_u16_be(ptr + 0x34),
        mainmemory.read_u16_be(ptr + 0x36),
        mainmemory.read_u32_be(ptr + 0x38),
        mainmemory.read_u32_be(ptr + 0x3C))
end

local function rngSeed()
    return mainmemory.read_u32_be(RNG_SEED)
end

local function callerPc()
    local sp = ptr16("M68K A7")
    if sp > 0xFFFC then return 0 end
    return mainmemory.read_u32_be(sp) & 0xFFFFFF
end

local function dynamicOccupancy()
    local occupied = {}
    for slot = DYNAMIC_FIRST, DYNAMIC_LAST do
        local ptr = OBJECT_RAM + slot * OBJECT_SIZE
        local code = mainmemory.read_u32_be(ptr)
        if code ~= 0 then
            occupied[#occupied + 1] = string.format("%d:%08X/%02X",
                slot, code, mainmemory.read_u8(ptr + 0x05))
        end
    end
    return table.concat(occupied, ",")
end

local function inWindow()
    local f = frame()
    return f >= START and f <= STOP
end

local pass = 0

ProbeRuntime.run({
    -- Hooks are not installed during the AIZ prefix or the long HCZ wait.
    -- Each invocation arms only for one narrow HCZ1 evidence window.
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0x01
            and mainmemory.read_u8(CURRENT_ACT) == 0x00
            and frame() >= START
    end,
    hooks = {
        {
            name = "hcz_process_sprites_entry",
            address = 0x01AADA,
            callback = function(context)
                if frame() > STOP then
                    context.finish()
                    return
                end
                if not inWindow() then return end
                pass = pass + 1
                context.log(string.format(
                    "f=%d trace=%d pass=%d PROCESS occupancy=[%s]",
                    frame(), traceFrame(), pass, dynamicOccupancy()))
            end
        },
        {
            name = "hcz_process_sprites_dispatch",
            address = 0x01AAFC,
            callback = function(context)
                if not inWindow() then return end
                local ptr = ptr16("M68K A0")
                local code = mainmemory.read_u32_be(ptr)
                if code == OBJ_AIR_COUNTDOWN or code == OBJ_BUBBLER then
                    context.log(string.format(
                        "f=%d trace=%d pass=%d rng=%08X DISPATCH %s",
                        frame(), traceFrame(), pass, rngSeed(), objectSummary(ptr)))
                end
            end
        },
        {
            name = "hcz_allocate_object_entry",
            address = 0x01BAF2,
            callback = function(context)
                if not inWindow() then return end
                context.log(string.format(
                    "f=%d trace=%d pass=%d rng=%08X ALLOC_ENTRY caller=%06X owner={%s}",
                    frame(), traceFrame(), pass, rngSeed(), callerPc(),
                    objectSummary(ptr16("M68K A0"))))
            end
        },
        {
            name = "hcz_air_countdown_allocate_return",
            address = 0x0185BA,
            callback = function(context)
                if not inWindow() then return end
                context.log(string.format(
                    "f=%d trace=%d pass=%d rng=%08X AIR_ALLOC_RETURN owner={%s} result={%s} d0=%08X",
                    frame(), traceFrame(), pass, rngSeed(),
                    objectSummary(ptr16("M68K A0")),
                    objectSummary(ptr16("M68K A1")),
                    emu.getregister("M68K D0") or 0))
            end
        },
        {
            name = "hcz_bubbler_allocate_return",
            address = 0x02FACE,
            callback = function(context)
                if not inWindow() then return end
                context.log(string.format(
                    "f=%d trace=%d pass=%d rng=%08X BUBBLER_ALLOC_RETURN owner={%s} result={%s} d0=%08X",
                    frame(), traceFrame(), pass, rngSeed(),
                    objectSummary(ptr16("M68K A0")),
                    objectSummary(ptr16("M68K A1")),
                    emu.getregister("M68K D0") or 0))
            end
        }
    }
})
