local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local LEVEL_FRAME_COUNTER = 0xFE04
local TAILS_BASE = 0xB04A
local TAILS_ANIM = TAILS_BASE + 0x20
local TAILS_STATUS = TAILS_BASE + 0x2A
local TAILS_OBJECT_CONTROL = TAILS_BASE + 0x2E
local TAILS_GROUND_VEL = TAILS_BASE + 0x1C
local CTRL_2_LOGICAL = 0xF606

local function register(name)
    local value = emu.getregister(name)
    if value == nil then return 0 end
    return value
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0
            and mainmemory.read_u8(CURRENT_ACT) == 0
            and mainmemory.read_u16_be(LEVEL_FRAME_COUNTER) >= 0x0950
    end,
    hooks = {
        {
            name = "aiz_tails_normal_dispatch_2707",
            address = 0x01365C,
            callback = function(context)
                local gfc = mainmemory.read_u16_be(LEVEL_FRAME_COUNTER)
                if gfc < 0x096F or gfc > 0x0975 then return end
                context.log(string.format(
                    "emu=%d gfc=%04X dispatch=Tails_Normal anim=%02X "
                        .. "status=%02X objctrl=%02X gvel=%04X ctrl2=%04X",
                    emu.framecount(),
                    gfc,
                    mainmemory.read_u8(TAILS_ANIM),
                    mainmemory.read_u8(TAILS_STATUS),
                    mainmemory.read_u8(TAILS_OBJECT_CONTROL),
                    mainmemory.read_u16_be(TAILS_GROUND_VEL),
                    mainmemory.read_u16_be(CTRL_2_LOGICAL)))
            end
        },
        {
            name = "aiz_tails_anim_write_2707",
            kind = "write",
            address = 0xFF0000 + TAILS_ANIM,
            callback = function(context)
                local gfc = mainmemory.read_u16_be(LEVEL_FRAME_COUNTER)
                if gfc < 0x0960 or gfc > 0x0988 then return end
                context.log(string.format(
                    "emu=%d gfc=%04X pc=%06X anim=%02X d0=%08X d1=%08X "
                        .. "status=%02X objctrl=%02X gvel=%04X ctrl2=%04X",
                    emu.framecount(),
                    gfc,
                    register("M68K PC") & 0xFFFFFF,
                    mainmemory.read_u8(TAILS_ANIM),
                    register("M68K D0") & 0xFFFFFFFF,
                    register("M68K D1") & 0xFFFFFFFF,
                    mainmemory.read_u8(TAILS_STATUS),
                    mainmemory.read_u8(TAILS_OBJECT_CONTROL),
                    mainmemory.read_u16_be(TAILS_GROUND_VEL),
                    mainmemory.read_u16_be(CTRL_2_LOGICAL)))
            end
        },
        {
            name = "aiz_tails_anim_2707_stop",
            address = 0x01AADA,
            callback = function(context)
                if mainmemory.read_u16_be(LEVEL_FRAME_COUNTER) > 0x0988 then
                    context.finish()
                end
            end
        }
    }
})
