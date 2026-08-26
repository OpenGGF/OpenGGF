-- Who writes anim=5 to BOTH players at the AIZ act 2 entry?
--
-- Chain segment 4 (aiz_3) starts with ROM anim=0x05 (WAIT) on Sonic AND Tails
-- while both are airborne and falling. The per-VBlank trace row cannot show the
-- responsible instruction: Sonic_Init/Sonic_Init_Continued
-- (sonic3k.asm:21902-21941) never touch anim, and Sonic_Control dispatches on
-- `status & 6`, which at the recorded status=0x02 is the AIR mode -- so
-- Sonic_Move's unconditional `move.b #5,anim(a0)` (:22453) is not it either.
--
-- Observation-only. Hooks the anim byte of each player separately, because both
-- characters are wrong and they may not share a writer.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local LEVEL_FRAME_COUNTER = 0xFE04

local SONIC_BASE = 0xB000
local TAILS_BASE = 0xB04A
local ANIM = 0x20
local PREV_ANIM = 0x21
local ROUTINE = 0x05
local STATUS = 0x2A
local OBJECT_CONTROL = 0x2E

-- Span the load AND the recorded segment start. The SST is cleared to anim=00
-- at movie frame 19603; segment 4 row 0 is movie frame 19775, where the trace
-- already reads anim=05, so the write lands in between.
local WINDOW_FRAMES = 320

-- The route's act byte also reads 1 during AIZ act 1's transition tail (seen at
-- movie frame 12061, level_frame_counter 0x0CAC -- deep inside the act, not an
-- entry). The recorded segment 4 begins at movie frame 19775, after the
-- intervening special stage, so narrow the already-semantic act gate with a
-- movie-frame floor. The gate still identifies ROM state; the window only
-- stops the probe binding to an earlier occurrence of that state.
local MOVIE_FRAME_FLOOR = 19500

local function register(name)
    local value = emu.getregister(name)
    if value == nil then return 0 end
    return value
end

local function describe(base)
    return string.format("anim=%02X prev=%02X rtn=%02X status=%02X objctrl=%02X",
        mainmemory.read_u8(base + ANIM),
        mainmemory.read_u8(base + PREV_ANIM),
        mainmemory.read_u8(base + ROUTINE),
        mainmemory.read_u8(base + STATUS),
        mainmemory.read_u8(base + OBJECT_CONTROL))
end

local function animWriteHook(name, who, base)
    return {
        name = name,
        kind = "write",
        address = 0xFF0000 + base + ANIM,
        callback = function(context)
            context.log(string.format(
                "emu=%d lfc=%04X %s pc=%06X a0=%06X a1=%06X d0=%08X | %s",
                emu.framecount(),
                mainmemory.read_u16_be(LEVEL_FRAME_COUNTER),
                who,
                register("M68K PC") & 0xFFFFFF,
                register("M68K A0") & 0xFFFFFF,
                register("M68K A1") & 0xFFFFFF,
                register("M68K D0") & 0xFFFFFFFF,
                describe(base)))
        end
    }
end

local entryFrame = nil

ProbeRuntime.run({
    -- AIZ act 2, in the level game mode. The route enters it once.
    stage = function()
        return emu.framecount() >= MOVIE_FRAME_FLOOR
            and (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0
            and mainmemory.read_u8(CURRENT_ACT) == 1
    end,
    hooks = {
        animWriteHook("aiz2_sonic_anim_write", "SONIC", SONIC_BASE),
        animWriteHook("aiz2_tails_anim_write", "TAILS", TAILS_BASE),
    },
    onFrame = function(context)
        local inAct = emu.framecount() >= MOVIE_FRAME_FLOOR
            and (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0
            and mainmemory.read_u8(CURRENT_ACT) == 1
        if not inAct then return end
        if entryFrame == nil then
            entryFrame = emu.framecount()
            context.log(string.format(
                "ENTRY emu=%d lfc=%04X sonic[%s] tails[%s]",
                entryFrame,
                mainmemory.read_u16_be(LEVEL_FRAME_COUNTER),
                describe(SONIC_BASE),
                describe(TAILS_BASE)))
        end
        if emu.framecount() - entryFrame >= WINDOW_FRAMES then
            context.log(string.format(
                "WINDOW-END emu=%d sonic[%s] tails[%s]",
                emu.framecount(),
                describe(SONIC_BASE),
                describe(TAILS_BASE)))
            context.finish()
        end
    end
})
