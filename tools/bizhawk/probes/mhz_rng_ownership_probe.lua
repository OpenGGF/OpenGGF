-- MHZ complete-run RNG ownership probe.
--
-- Question: the MHZ complete-run fixture records no Random_Number advance on
-- trace row 73 -- the row its player lands on the mushroom cap -- but does
-- record one on row 74. The engine consumes on 73. Four engine-side placements
-- of Obj_MHZ_Pollen_Spawner were measured and none reproduced ROM's phase, so
-- this captures which call sites actually fire on which frames.
--
-- Emits one line per Random_Number call across the MHZ act 1 opening, with the
-- caller PC and the A0/A1 object context, so the owning routine of every call
-- in rows ~60-90 can be named rather than guessed.
--
-- Read/log only: no emulated memory, input, register or savestate mutation.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local CAMERA_X_COPY = 0xEE80
local GAMEPLAY_COUNTER = 0xFE04
local RNG_SEED = 0xF636

local PLAYER1 = 0xB000
local OBJECT_RAM = 0xB000
local OBJECT_SIZE = 0x4A
local OBJECT_END = 0xCAE2

-- MHZ complete-run segment: metadata bk2_frame_offset.
local TRACE_OFFSET = 209756

local RNG_ENTRY = 0x001D24
local RNG_RTS = 0x001D4A
local PROCESS_SPRITES = 0x01AADA

-- Stop once well past the window of interest (trace row ~120).
local TRACE_ROW_STOP = 120

local pending = nil
local ordinal = 0

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
        "%s_ptr=%04X %s_slot=%d %s_code=%08X %s_rtn=%02X %s_x=%04X %s_y=%04X",
        registerName, ptr,
        registerName, slot,
        registerName, mainmemory.read_u32_be(ptr),
        registerName, mainmemory.read_u8(ptr + 0x05),
        registerName, mainmemory.read_u16_be(ptr + 0x10),
        registerName, mainmemory.read_u16_be(ptr + 0x14))
end

-- Player_1 state as the pollen spawner's gate reads it: top_solid_bit and the
-- in-air status bit (sub_3DA24, sonic3k.asm:81633-81643).
local function playerGate()
    return string.format(
        "p1_y=%04X p1_status=%02X p1_air=%d p1_topsolid=%02X p1_stand=%02X",
        mainmemory.read_u16_be(PLAYER1 + 0x14),
        mainmemory.read_u8(PLAYER1 + 0x2A),
        (mainmemory.read_u8(PLAYER1 + 0x2A) & 0x02) ~= 0 and 1 or 0,
        mainmemory.read_u8(PLAYER1 + 0x3E),
        mainmemory.read_u8(PLAYER1 + 0x3D))
end

local function traceRow()
    return emu.framecount() - TRACE_OFFSET
end

local function prefix()
    return string.format(
        "emu=%d row=%d gfc=%04X seed=%08X %s",
        emu.framecount(),
        traceRow(),
        mainmemory.read_u16_be(GAMEPLAY_COUNTER),
        mainmemory.read_u32_be(RNG_SEED),
        playerGate())
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0x07
            and mainmemory.read_u8(CURRENT_ACT) == 0x00
            and emu.framecount() >= TRACE_OFFSET
    end,
    hooks = {
        {
            name = "mhz_rng_entry",
            address = RNG_ENTRY,
            callback = function()
                assert(pending == nil, "nested/unmatched Random_Number entry")
                ordinal = ordinal + 1
                pending = {
                    ordinal = ordinal,
                    seedBefore = mainmemory.read_u32_be(RNG_SEED),
                    caller = callerPc(),
                    a0 = objectContext("A0"),
                    a1 = objectContext("A1"),
                    entryPrefix = prefix()
                }
            end
        },
        {
            name = "mhz_rng_rts",
            address = RNG_RTS,
            callback = function(context)
                assert(pending ~= nil, "Random_Number RTS without paired entry")
                context.log(string.format(
                    "%s ordinal=%d caller=%06X pre=%08X result=%08X post=%08X %s %s",
                    pending.entryPrefix,
                    pending.ordinal,
                    pending.caller,
                    pending.seedBefore,
                    register("M68K D0") & 0xFFFFFFFF,
                    mainmemory.read_u32_be(RNG_SEED),
                    pending.a0,
                    pending.a1))
                pending = nil
            end
        },
        {
            name = "mhz_rng_frame_mark",
            address = PROCESS_SPRITES,
            callback = function(context)
                local row = traceRow()
                if row >= 0 and row <= TRACE_ROW_STOP then
                    context.log(string.format("%s process_sprites=1", prefix()))
                end
                if row > TRACE_ROW_STOP then
                    assert(pending == nil, "stop reached with unmatched RNG entry")
                    context.log(prefix() .. " done=1")
                    context.finish()
                end
            end
        }
    }
})
