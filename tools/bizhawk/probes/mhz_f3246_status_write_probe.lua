-- One-off diagnostic, follow-up to mhz_f3246_findfloor_probe.lua.
--
-- That probe established that at MHZ trace frame 3246 `Player_AnglePos`
-- (sonic3k.asm:18726-18843) returns via `locret_ED12` with `d1 = 0` — it does
-- NOT detach — yet the player is airborne for f3247-f3255. This probe catches
-- the actual writer of `Status_InAir` by watching Player_1's `status` byte and
-- logging the 68k PC at the write.
--
-- Player_1 is the first slot of Dynamic_object_RAM ($FFB000); `status` is $2A
-- (sonic3k.constants.asm:30), so the byte is $FFB02A. Write hooks are
-- registered on both the 68k-RAM offset and the full bus address so whichever
-- domain BizHawk's Genesis core exposes is covered.
--
-- Verified ROM bytes for the execute hooks:
--   Player_SlopeRepel  $011E2C  4A28 003C     tst.b stick_to_convex(a0)
--   loc_11E4E          $011E4E  0C40 0280     cmpi.w #$280,d0
--   SlopeRepel detach  $011E68  08E8 0001 002A  bset #Status_InAir,status(a0)
--   Player_AnglePos    $00EC2E  21F8 F7B4 F796
--
-- Read/log only: no emulated memory, input, register or savestate mutation.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local LEVEL_FRAME_COUNTER = 0xFE04

local PLAYER_1 = 0xB000
local STATUS = 0x2A
local PLAYER_1_STATUS = PLAYER_1 + STATUS
local PLAYER_1_STATUS_BUS = 0xFF0000 + PLAYER_1_STATUS

local TRACE_OFFSET = 209756
local WINDOW_FIRST = 3240
local WINDOW_LAST = 3252

local ADDR_ANGLEPOS = 0x00EC2E
local ADDR_SLOPEREPEL = 0x011E2C
local ADDR_SLOPEREPEL_SPEED = 0x011E4E
local ADDR_SLOPEREPEL_DETACH = 0x011E68

local lastStatus = nil

local function reg(name)
    return emu.getregister("M68K " .. name) or 0
end

local function signedWord(value)
    local v = value & 0xFFFF
    if v >= 0x8000 then return v - 0x10000 end
    return v
end

local function traceFrame()
    return emu.framecount() - TRACE_OFFSET
end

local function inWindow()
    local f = traceFrame()
    return f >= WINDOW_FIRST and f <= WINDOW_LAST
end

local function prefix()
    return string.format("f=%d lfc=%04X", traceFrame(),
        mainmemory.read_u16_be(LEVEL_FRAME_COUNTER))
end

local function playerState()
    return string.format(
        "x=%04X y=%04X xvel=%04X yvel=%04X gvel=%04X ang=%02X status=%02X"
            .. " rtn=%02X movelock=%04X stick=%02X",
        mainmemory.read_u16_be(PLAYER_1 + 0x10),
        mainmemory.read_u16_be(PLAYER_1 + 0x14),
        mainmemory.read_u16_be(PLAYER_1 + 0x18),
        mainmemory.read_u16_be(PLAYER_1 + 0x1A),
        mainmemory.read_u16_be(PLAYER_1 + 0x1C),
        mainmemory.read_u8(PLAYER_1 + 0x26),
        mainmemory.read_u8(PLAYER_1 + 0x2A),
        mainmemory.read_u8(PLAYER_1 + 0x05),
        mainmemory.read_u16_be(PLAYER_1 + 0x2E),
        mainmemory.read_u8(PLAYER_1 + 0x3C))
end

local function onStatusWrite(context, tag)
    if not inWindow() then return end
    local now = mainmemory.read_u8(PLAYER_1_STATUS)
    context.log(string.format("%s STATUS_WRITE(%s) pc=%06X value=%02X prev=%s %s",
        prefix(), tag, reg("PC") & 0xFFFFFF, now,
        lastStatus == nil and "??" or string.format("%02X", lastStatus),
        playerState()))
    lastStatus = now
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0x07
            and traceFrame() >= WINDOW_FIRST - 4
    end,
    hooks = {
        {
            name = "mhz_f3246_status_write_ram",
            kind = "write",
            address = PLAYER_1_STATUS,
            callback = function(context) onStatusWrite(context, "ram") end
        },
        {
            name = "mhz_f3246_status_write_bus",
            kind = "write",
            address = PLAYER_1_STATUS_BUS,
            callback = function(context) onStatusWrite(context, "bus") end
        },
        {
            name = "mhz_f3246_sloperepel",
            address = ADDR_SLOPEREPEL,
            callback = function(context)
                if (reg("A0") & 0xFFFF) ~= PLAYER_1 or not inWindow() then return end
                context.log(string.format("%s SLOPEREPEL_IN %s", prefix(), playerState()))
            end
        },
        {
            name = "mhz_f3246_sloperepel_speed",
            address = ADDR_SLOPEREPEL_SPEED,
            callback = function(context)
                if (reg("A0") & 0xFFFF) ~= PLAYER_1 or not inWindow() then return end
                context.log(string.format("%s SLOPEREPEL_SPEEDGATE d0=%d", prefix(),
                    signedWord(reg("D0"))))
            end
        },
        {
            name = "mhz_f3246_sloperepel_detach",
            address = ADDR_SLOPEREPEL_DETACH,
            callback = function(context)
                if not inWindow() then return end
                context.log(string.format("%s SLOPEREPEL_DETACH a0=%04X %s", prefix(),
                    reg("A0") & 0xFFFF, playerState()))
            end
        },
        {
            name = "mhz_f3246_frame_mark",
            address = ADDR_ANGLEPOS,
            callback = function(context)
                if (reg("A0") & 0xFFFF) ~= PLAYER_1 then return end
                if traceFrame() > WINDOW_LAST then
                    context.log(string.format("f=%d done=1", traceFrame()))
                    context.finish()
                    return
                end
                if not inWindow() then return end
                lastStatus = mainmemory.read_u8(PLAYER_1_STATUS)
                context.log(string.format("%s ANGLEPOS_IN %s", prefix(), playerState()))
            end
        }
    }
})
