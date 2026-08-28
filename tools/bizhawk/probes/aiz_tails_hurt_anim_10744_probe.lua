-- TEMPORARY diagnostic probe. Does the ROM execute HurtCharacter's
-- `move.b #$1A,anim(a0)` (0x10326) for Player_2 at the AIZ full-run
-- spiked-log hit (trace row 10744, Level_frame_counter 0x28C8)?
local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local LEVEL_FRAME_COUNTER = 0xFE04

local TAILS_BASE = 0xB04A
local TAILS_ROUTINE = TAILS_BASE + 0x05
local TAILS_XVEL = TAILS_BASE + 0x18
local TAILS_YVEL = TAILS_BASE + 0x1A
local TAILS_ANIM = TAILS_BASE + 0x20
local TAILS_STATUS = TAILS_BASE + 0x2A
local TAILS_INVULN = TAILS_BASE + 0x34

local WINDOW_LO = 0x28B8
local WINDOW_HI = 0x28E0

local function reg(name)
    local v = emu.getregister(name)
    if v == nil then return 0 end
    return v
end

local function inWindow()
    local gfc = mainmemory.read_u16_be(LEVEL_FRAME_COUNTER)
    return gfc >= WINDOW_LO and gfc <= WINDOW_HI, gfc
end

local function state()
    return string.format(
        "rout=%02X anim=%02X st=%02X xv=%04X yv=%04X inv=%02X",
        mainmemory.read_u8(TAILS_ROUTINE),
        mainmemory.read_u8(TAILS_ANIM),
        mainmemory.read_u8(TAILS_STATUS),
        mainmemory.read_u16_be(TAILS_XVEL),
        mainmemory.read_u16_be(TAILS_YVEL),
        mainmemory.read_u8(TAILS_INVULN))
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0
            and mainmemory.read_u8(CURRENT_ACT) == 1
            and mainmemory.read_u16_be(LEVEL_FRAME_COUNTER) >= (WINDOW_LO - 0x30)
    end,
    hooks = {
        {
            -- Every write to Tails' anim byte, with the writing PC.
            name = "tails_anim_write",
            kind = "write",
            address = 0xFF0000 + TAILS_ANIM,
            callback = function(context)
                local ok, gfc = inWindow()
                if not ok then return end
                context.log(string.format("ANIMW emu=%d gfc=%04X pc=%06X %s",
                    emu.framecount(), gfc, reg("M68K PC") & 0xFFFFFF, state()))
            end
        },
        {
            -- Every write to Tails' routine byte: identifies who sets routine 4
            -- and who puts it back to 2 two frames later.
            name = "tails_routine_write",
            kind = "write",
            address = 0xFF0000 + TAILS_ROUTINE,
            callback = function(context)
                local ok, gfc = inWindow()
                if not ok then return end
                context.log(string.format("ROUTW emu=%d gfc=%04X pc=%06X %s",
                    emu.framecount(), gfc, reg("M68K PC") & 0xFFFFFF, state()))
            end
        },
        {
            -- Execution of HurtCharacter's `move.b #$1A,anim(a0)` itself,
            -- with a0 so Player_1 and Player_2 are distinguishable.
            name = "hurtcharacter_anim_store",
            address = 0x010326,
            callback = function(context)
                local ok, gfc = inWindow()
                if not ok then return end
                context.log(string.format("HURTANIM emu=%d gfc=%04X a0=%06X %s",
                    emu.framecount(), gfc, reg("M68K A0") & 0xFFFFFF, state()))
            end
        },
        {
            -- HurtCharacter entry, to prove whether it is reached at all.
            name = "hurtcharacter_entry",
            address = 0x0102A0,
            callback = function(context)
                local ok, gfc = inWindow()
                if not ok then return end
                context.log(string.format("HURTENTRY emu=%d gfc=%04X a0=%06X %s",
                    emu.framecount(), gfc, reg("M68K A0") & 0xFFFFFF, state()))
            end
        },
        {
            name = "probe_stop",
            address = 0x01AADA,
            callback = function(context)
                if mainmemory.read_u16_be(LEVEL_FRAME_COUNTER) > WINDOW_HI then
                    context.finish()
                end
            end
        }
    }
})
