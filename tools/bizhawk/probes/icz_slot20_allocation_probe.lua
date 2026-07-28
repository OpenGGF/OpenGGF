local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local GAMEPLAY_COUNTER = 0xFE04
local CAMERA_X_COPY = 0xEE80
local RNG_SEED = 0xF636

local OBJECT_RAM = 0xB000
local OBJECT_SIZE = 0x4A
local DYNAMIC_FIRST = 3
local DYNAMIC_LAST = 92
local TRACE_OFFSET = 138117

local PROCESS_SPRITES = 0x01AADA
local PROCESS_DISPATCH = 0x01AAFC
local ALLOCATE_OBJECT = 0x01BAF2
local ALLOCATE_AFTER_CURRENT = 0x01BAFA
local ALLOCATE_RETURN = 0x01BB14
local RNG_ENTRY = 0x001D24

local GFC_ARM = 0x3888
local GFC_STOP = 0x38A0
local CAMERA_ARM = 0x0600

local pendingAllocation = nil
local pass = 0

local function register(name)
    return emu.getregister(name) or 0
end

local function pointer(registerName)
    return register("M68K " .. registerName) & 0xFFFF
end

local function slotFor(ptr)
    local delta = ptr - OBJECT_RAM
    if delta < 0 or delta % OBJECT_SIZE ~= 0 then return -1 end
    local slot = math.floor(delta / OBJECT_SIZE)
    if slot < 0 or slot > 109 then return -1 end
    return slot
end

local function objectSummary(ptr)
    local slot = slotFor(ptr)
    if slot < 0 then return string.format("ptr=%04X slot=-1", ptr) end
    return string.format(
        "ptr=%04X slot=%d code=%08X routine=%02X subtype=%02X x=%04X y=%04X",
        ptr,
        slot,
        mainmemory.read_u32_be(ptr),
        mainmemory.read_u8(ptr + 0x05),
        mainmemory.read_u8(ptr + 0x2C),
        mainmemory.read_u16_be(ptr + 0x10),
        mainmemory.read_u16_be(ptr + 0x14))
end

local function callerPc()
    local sp = pointer("A7")
    assert(sp <= 0xFFFC, string.format("invalid A7 for return PC: %04X", sp))
    return mainmemory.read_u32_be(sp) & 0xFFFFFF
end

local function prefix()
    return string.format(
        "emu=%d trace=%d gfc=%04X pass=%d rng=%08X",
        emu.framecount(),
        emu.framecount() - TRACE_OFFSET,
        mainmemory.read_u16_be(GAMEPLAY_COUNTER),
        pass,
        mainmemory.read_u32_be(RNG_SEED))
end

local function occupancy()
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
    local gfc = mainmemory.read_u16_be(GAMEPLAY_COUNTER)
    return gfc >= GFC_ARM and gfc <= GFC_STOP
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0x05
            and mainmemory.read_u8(CURRENT_ACT) == 0x01
            and mainmemory.read_u16_be(CAMERA_X_COPY) >= CAMERA_ARM
            and mainmemory.read_u16_be(GAMEPLAY_COUNTER) >= GFC_ARM
    end,
    hooks = {
        {
            name = "icz_slot20_process",
            address = PROCESS_SPRITES,
            callback = function(context)
                local gfc = mainmemory.read_u16_be(GAMEPLAY_COUNTER)
                if gfc > GFC_STOP then
                    assert(pendingAllocation == nil,
                        "window ended with unmatched allocation")
                    context.finish()
                    return
                end
                if not inWindow() then return end
                pass = pass + 1
                context.log(prefix() .. " PROCESS occupancy=[" .. occupancy() .. "]")
            end
        },
        {
            name = "icz_slot20_dispatch",
            address = PROCESS_DISPATCH,
            callback = function(context)
                if not inWindow() then return end
                local ptr = pointer("A0")
                local slot = slotFor(ptr)
                if slot == 5 or (slot >= 19 and slot <= 21) then
                    context.log(prefix() .. " DISPATCH {" .. objectSummary(ptr) .. "}")
                end
            end
        },
        {
            name = "icz_slot20_allocate_plain",
            address = ALLOCATE_OBJECT,
            callback = function()
                if not inWindow() then return end
                assert(pendingAllocation == nil, "nested allocation entry")
                pendingAllocation = {
                    kind = "plain",
                    caller = callerPc(),
                    owner = objectSummary(pointer("A0"))
                }
            end
        },
        {
            name = "icz_slot20_allocate_after",
            address = ALLOCATE_AFTER_CURRENT,
            callback = function()
                if not inWindow() then return end
                assert(pendingAllocation == nil, "nested allocation entry")
                pendingAllocation = {
                    kind = "after",
                    caller = callerPc(),
                    owner = objectSummary(pointer("A0"))
                }
            end
        },
        {
            name = "icz_slot20_allocate_return",
            address = ALLOCATE_RETURN,
            callback = function(context)
                if not inWindow() then return end
                assert(pendingAllocation ~= nil, "allocation return without entry")
                context.log(string.format(
                    "%s ALLOC kind=%s caller=%06X owner={%s} result={%s} d0=%08X",
                    prefix(),
                    pendingAllocation.kind,
                    pendingAllocation.caller,
                    pendingAllocation.owner,
                    objectSummary(pointer("A1")),
                    register("M68K D0") & 0xFFFFFFFF))
                pendingAllocation = nil
            end
        },
        {
            name = "icz_slot20_rng",
            address = RNG_ENTRY,
            callback = function(context)
                if not inWindow() then return end
                context.log(string.format(
                    "%s RNG caller=%06X owner={%s}",
                    prefix(), callerPc(), objectSummary(pointer("A0"))))
            end
        }
    }
})
