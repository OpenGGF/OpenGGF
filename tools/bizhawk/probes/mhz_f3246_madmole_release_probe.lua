-- One-off diagnostic, third in the MHZ f3246 chain.
--
-- mhz_f3246_findfloor_probe.lua showed `Player_AnglePos` returns grounded
-- (`d1 = 0`, `locret_ED12`) at MHZ trace frame 3246.
-- mhz_f3246_status_write_probe.lua traced the airborne transition to a write at
-- ROM $08D72C — `bset #Status_InAir,status(a1)` in `loc_8D724`
-- (sonic3k.asm:193222-193228), the off-camera despawn tail of `loc_8D6E6`, the
-- Madmole child object (`ChildObjDat_8D9C8`/`ChildObjDat_8D9D0`,
-- sonic3k.asm:193508-193514).
--
-- This probe records that despawn: the child object's own slot/state, the value
-- of its `$44` player back-reference, and the released `a1` object's state
-- before and after the bset — confirming the released object is Player_1.
--
-- Verified ROM bytes:
--   loc_8D724   $08D724  3028 0044     move.w $44(a0),d0
--               $08D728  6708          beq.s loc_8D736
--               $08D72A  3240          movea.w d0,a1
--               $08D72C  08E9 0001 002A  bset #Status_InAir,status(a1)
--               $08D732  4229 002E     clr.b object_control(a1)
--
-- Read/log only: no emulated memory, input, register or savestate mutation.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local LEVEL_FRAME_COUNTER = 0xFE04
local CAMERA_X = 0xEE00
local CAMERA_Y = 0xEE04
local CAMERA_X_COARSE_BACK = 0xEE1E

local OBJECT_RAM = 0xB000
local OBJECT_SIZE = 0x4A
local OBJECT_END = 0xCAE2
local PLAYER_1 = 0xB000

local TRACE_OFFSET = 209756
local WINDOW_FIRST = 2900
local WINDOW_LAST = 3260

local ADDR_RELEASE_BSET = 0x08D72C
local ADDR_CHILD_MAIN = 0x08D6E6

local function reg(name)
    return emu.getregister("M68K " .. name) or 0
end

local function traceFrame()
    return emu.framecount() - TRACE_OFFSET
end

local function slotOf(ptr)
    local delta = ptr - OBJECT_RAM
    if ptr < OBJECT_RAM or ptr >= OBJECT_END or delta % OBJECT_SIZE ~= 0 then
        return -1
    end
    return math.floor(delta / OBJECT_SIZE)
end

local function describe(tag, ptr)
    return string.format(
        "%s_ptr=%04X %s_slot=%d %s_code=%08X %s_rtn=%02X %s_x=%04X %s_y=%04X"
            .. " %s_status=%02X %s_objctl=%02X %s_p44=%04X %s_parent3=%04X",
        tag, ptr, tag, slotOf(ptr),
        tag, mainmemory.read_u32_be(ptr),
        tag, mainmemory.read_u8(ptr + 0x05),
        tag, mainmemory.read_u16_be(ptr + 0x10),
        tag, mainmemory.read_u16_be(ptr + 0x14),
        tag, mainmemory.read_u8(ptr + 0x2A),
        tag, mainmemory.read_u8(ptr + 0x2E),
        tag, mainmemory.read_u16_be(ptr + 0x44),
        tag, mainmemory.read_u16_be(ptr + 0x46))
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0x07
            and traceFrame() >= WINDOW_FIRST - 4
    end,
    hooks = {
        {
            name = "mhz_f3246_madmole_child",
            address = ADDR_CHILD_MAIN,
            callback = function(context)
                local f = traceFrame()
                if f < WINDOW_LAST - 20 or f > WINDOW_LAST then return end
                local a0 = reg("A0") & 0xFFFF
                context.log(string.format("f=%d lfc=%04X CHILD_TICK %s cam_x=%04X"
                        .. " cam_y=%04X cam_back=%04X",
                    f, mainmemory.read_u16_be(LEVEL_FRAME_COUNTER),
                    describe("child", a0),
                    mainmemory.read_u16_be(CAMERA_X),
                    mainmemory.read_u16_be(CAMERA_Y),
                    mainmemory.read_u16_be(CAMERA_X_COARSE_BACK)))
            end
        },
        {
            name = "mhz_f3246_madmole_release",
            address = ADDR_RELEASE_BSET,
            callback = function(context)
                local f = traceFrame()
                local a0 = reg("A0") & 0xFFFF
                local a1 = reg("A1") & 0xFFFF
                context.log(string.format(
                    "f=%d lfc=%04X RELEASE released_is_player1=%s %s %s",
                    f, mainmemory.read_u16_be(LEVEL_FRAME_COUNTER),
                    a1 == PLAYER_1 and "yes" or "no",
                    describe("child", a0), describe("released", a1)))
                if f > WINDOW_LAST then context.finish() end
            end
        },
        {
            name = "mhz_f3246_madmole_watchdog",
            address = 0x00EC2E,
            callback = function(context)
                if traceFrame() > WINDOW_LAST then
                    context.log(string.format("f=%d done=1", traceFrame()))
                    context.finish()
                end
            end
        }
    }
})
