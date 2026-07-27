local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local GAMEPLAY_COUNTER = 0xFE04
local PLAYER = 0xB000
local X_POS = PLAYER + 0x10
local Y_POS = PLAYER + 0x14
local X_VEL = PLAYER + 0x18
local Y_VEL = PLAYER + 0x1A
local GROUND_VEL = PLAYER + 0x1C
local ANGLE = PLAYER + 0x26
local STATUS = PLAYER + 0x2A
local INTERACT = PLAYER + 0x42
local M68K_RAM = 0xFF0000

local startText = assert(os.getenv("OGGF_START"), "OGGF_START is required")
local stopText = assert(os.getenv("OGGF_STOP"), "OGGF_STOP is required")
local START = assert(tonumber(startText), "OGGF_START must be numeric")
local STOP = assert(tonumber(stopText), "OGGF_STOP must be numeric")

local function reg(name)
    return emu.getregister(name) or 0
end

local function snapshot(context, kind)
    context.log(string.format(
        "frame=%d gfc=%04X kind=%s pc=%06X a0=%04X a1=%04X d0=%08X d1=%08X"
            .. " pos=%04X,%04X vel=%04X,%04X g=%04X angle=%02X status=%02X interact=%04X",
        emu.framecount(),
        mainmemory.read_u16_be(GAMEPLAY_COUNTER),
        kind,
        reg("M68K PC") & 0xFFFFFF,
        reg("M68K A0") & 0xFFFF,
        reg("M68K A1") & 0xFFFF,
        reg("M68K D0") & 0xFFFFFFFF,
        reg("M68K D1") & 0xFFFFFFFF,
        mainmemory.read_u16_be(X_POS),
        mainmemory.read_u16_be(Y_POS),
        mainmemory.read_u16_be(X_VEL),
        mainmemory.read_u16_be(Y_VEL),
        mainmemory.read_u16_be(GROUND_VEL),
        mainmemory.read_u8(ANGLE),
        mainmemory.read_u8(STATUS),
        mainmemory.read_u16_be(INTERACT)))
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0x05
            and mainmemory.read_u8(CURRENT_ACT) == 0x01
            and emu.framecount() >= START
    end,
    hooks = {
        {
            name = "icz_f16361_yvel_hi",
            kind = "write",
            address = M68K_RAM + Y_VEL,
            callback = function(context) snapshot(context, "yvel-hi") end
        },
        {
            name = "icz_f16361_yvel_lo",
            kind = "write",
            address = M68K_RAM + Y_VEL + 1,
            callback = function(context) snapshot(context, "yvel-lo") end
        },
        {
            name = "icz_f16361_angle",
            kind = "write",
            address = M68K_RAM + ANGLE,
            callback = function(context) snapshot(context, "angle") end
        },
        {
            name = "icz_f16361_process_sprites",
            address = 0x01AADA,
            callback = function(context)
                snapshot(context, "process-sprites")
                if emu.framecount() > STOP then
                    context.finish()
                end
            end
        }
    }
})
