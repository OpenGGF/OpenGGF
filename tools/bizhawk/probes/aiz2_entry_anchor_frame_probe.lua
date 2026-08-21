-- Which BizHawk frame does chain segment 4's row 0 describe?
--
-- Segment `aiz_3` of the S3K complete run carries bk2_frame_offset = 19775 and
-- an anim of 0x05 (WAIT) on both players in row 0, while a previous per-write
-- probe saw anim reach 0x05 only tens of frames later. Either the segment's
-- rows are indexed at some fixed offset from bk2_frame_offset, or the trace
-- columns are not all sampled at the same point in a frame.
--
-- Observation-only, and deliberately PER-FRAME rather than per-write: the
-- recorder samples every physics column in one on_frame_end body, so the frame
-- that matches row 0 must match it on every column at once. Sampling happens in
-- ProbeRuntime's onFrame, which sits at exactly the recorder's on_frame_end
-- position in the loop (both run before emu.frameadvance()), so the two agree
-- on both what they read and which frame index they stamp it with.
--
-- Logs BOTH counters. The recorder's `gameplay_frame_counter` column reads
-- ADDR_FRAMECOUNT = 0xFE04 = Level_frame_counter
-- (s3k_complete_run_recorder.lua:576) and its `vblank_counter` column reads
-- ADDR_VBLA_WORD = 0xFE0E = the V_int_run_count low word (:584); this probe
-- reads the same two addresses so the comparison is a measurement rather than
-- a name match.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local LEVEL_FRAME_COUNTER = 0xFE04
local V_INT_RUN_COUNT_WORD = 0xFE0E
local LAG_FRAME_COUNT = 0xF628
local CAMERA_X = 0xEE78
local CAMERA_Y = 0xEE7C

local SONIC_BASE = 0xB000
local TAILS_BASE = 0xB04A
local X_POS = 0x10
local Y_POS = 0x14
local ANIM = 0x20
local PREV_ANIM = 0x21
local MAPPING_FRAME = 0x22
local ROUTINE = 0x05
local STATUS = 0x2A
local CTRL_LOCK = 0x32

-- Wide by design. A window that closes before the recorded segment starts
-- reports "nothing happened" indistinguishably from one that proves it, so
-- span well past both the nominal offset and the frame where the previous
-- probe first saw anim=0x05.
local LOG_FIRST_FRAME = 19700
local LOG_LAST_FRAME = 19900

-- The route's act byte also reads 1 during AIZ act 1's transition tail (a
-- previous run bound at movie frame 12061, deep inside act 1). The gate stays
-- semantic; the floor only stops it binding to that earlier occurrence.
local MOVIE_FRAME_FLOOR = 19500

local function register(name)
    local value = emu.getregister(name)
    if value == nil then return 0 end
    return value
end

local function inAiz2()
    return emu.framecount() >= MOVIE_FRAME_FLOOR
        and (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
        and mainmemory.read_u8(CURRENT_ZONE) == 0
        and mainmemory.read_u8(CURRENT_ACT) == 1
end

local function describe(base)
    return string.format("x=%04X y=%04X anim=%02X prev=%02X mf=%02X rtn=%02X st=%02X lock=%04X",
        mainmemory.read_u16_be(base + X_POS),
        mainmemory.read_u16_be(base + Y_POS),
        mainmemory.read_u8(base + ANIM),
        mainmemory.read_u8(base + PREV_ANIM),
        mainmemory.read_u8(base + MAPPING_FRAME),
        mainmemory.read_u8(base + ROUTINE),
        mainmemory.read_u8(base + STATUS),
        mainmemory.read_u16_be(base + CTRL_LOCK))
end

local function animWriteHook(name, who, base)
    return {
        name = name,
        kind = "write",
        address = 0xFF0000 + base + ANIM,
        callback = function(context)
            if emu.framecount() < LOG_FIRST_FRAME or emu.framecount() > LOG_LAST_FRAME then
                return
            end
            context.log(string.format("WRITE emu=%d %s pc=%06X a0=%06X a1=%06X | %s",
                emu.framecount(), who,
                register("M68K PC") & 0xFFFFFF,
                register("M68K A0") & 0xFFFFFF,
                register("M68K A1") & 0xFFFFFF,
                describe(base)))
        end
    }
end

ProbeRuntime.run({
    stage = inAiz2,
    hooks = {
        animWriteHook("anchor_sonic_anim_write", "SONIC", SONIC_BASE),
        animWriteHook("anchor_tails_anim_write", "TAILS", TAILS_BASE),
    },
    onFrame = function(context)
        local frame = emu.framecount()
        if frame < LOG_FIRST_FRAME then return end
        if frame > LOG_LAST_FRAME then
            context.log("WINDOW-END emu=" .. frame)
            context.finish()
            return
        end
        context.log(string.format(
            "FRAME emu=%d mode=%02X zone=%02X act=%02X lfc=%04X vbl=%04X lag=%04X cam=(%04X,%04X) | SONIC %s | TAILS %s",
            frame,
            mainmemory.read_u8(GAME_MODE),
            mainmemory.read_u8(CURRENT_ZONE),
            mainmemory.read_u8(CURRENT_ACT),
            mainmemory.read_u16_be(LEVEL_FRAME_COUNTER),
            mainmemory.read_u16_be(V_INT_RUN_COUNT_WORD),
            mainmemory.read_u16_be(LAG_FRAME_COUNT),
            mainmemory.read_u16_be(CAMERA_X),
            mainmemory.read_u16_be(CAMERA_Y),
            describe(SONIC_BASE),
            describe(TAILS_BASE)))
    end
})
