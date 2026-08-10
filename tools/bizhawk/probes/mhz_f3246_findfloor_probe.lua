-- One-off diagnostic: MHZ complete-run f3246 ground-sensor divergence.
--
-- Records every input and output of `sub_F264` (sonic3k.asm:19264-19305) for the
-- two `Player_AnglePos` ground sensors, plus the `Player_Angle` selection and the
-- `loc_ED14` detach decision, across a window around trace frame 3246.
--
-- ROM addresses (all verified against the locked-on ROM bytes):
--   Player_AnglePos  $00EC2E  21F8 F7B4 F796   move.l (Primary_collision_addr).w,(Collision_addr).w
--   sub_F264         $00F264  4E95             jsr (a5)
--   loc_F274         $00F274  D44B             add.w a3,d2          (non-solid -> tile below)
--   rts (loc_F274)   $00F280  4E75                                  distance = sub_F30C + $10
--   loc_F282         $00F282  2478 F796        movea.l (Collision_addr).w,a2
--   loc_F2AA         $00F2AA  0804 000B        btst #$B,d4
--   loc_F2BA         $00F2BA  0241 000F        andi.w #$F,d1
--   rts (normal)     $00F2F0  4E75                                  distance = $F - ((y&$F)+height)
--   loc_F2FE         $00F2FE  944B             sub.w a3,d2          (full/negative -> tile above)
--   rts (loc_F2FE)   $00F30A  4E75                                  distance = sub_F30C - $10
--   Player_Angle     $00ED4C  3600 1438 F76A   move.w d0,d3 / move.b (Secondary_Angle).w,d2
--   locret_ED12      $00ED12  4E75             no reposition / small negative
--   loc_ED14         $00ED14  4A28 003C        tst.b stick_to_convex(a0)
--   loc_ED38         $00ED38  08E8 0001 002A   bset #Status_InAir,status(a0)   <- the detach
--
-- Read/log only: no emulated memory, input, register or savestate mutation.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local LEVEL_FRAME_COUNTER = 0xFE04
local PRIMARY_ANGLE = 0xF768
local SECONDARY_ANGLE = 0xF76A
local COLLISION_ADDR = 0xF796
local PRIMARY_COLLISION_ADDR = 0xF7B4
local SECONDARY_COLLISION_ADDR = 0xF7B8
local BACKGROUND_COLLISION_FLAG = 0xF664

local PLAYER_1 = 0xB000
local PLAYER_2 = 0xB04A

-- src/test/resources/traces/s3k/mhz_completerun/metadata.json bk2_frame_offset
local TRACE_OFFSET = 209756
local WINDOW_FIRST = 3200
local WINDOW_LAST = 3260

local ADDR_ANGLEPOS = 0x00EC2E
local ADDR_F264 = 0x00F264
local ADDR_F274_RTS = 0x00F280
local ADDR_F282 = 0x00F282
local ADDR_F2BA = 0x00F2BA
local ADDR_F2F0_RTS = 0x00F2F0
local ADDR_F30A_RTS = 0x00F30A
local ADDR_PLAYER_ANGLE = 0x00ED4C
local ADDR_ED12 = 0x00ED12
local ADDR_ED14 = 0x00ED14
local ADDR_ED38 = 0x00ED38

-- sub_F264 is re-entered by FindFloor for the background plane and by object
-- code; `sensor` counts the calls made inside the current Player_AnglePos.
local sensor = 0
local pending = nil

local function reg(name)
    return emu.getregister("M68K " .. name) or 0
end

local function word(value)
    return value & 0xFFFF
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

-- a0 is preserved across FindFloor/sub_F264, so it still names the player.
local function playerName()
    local a0 = word(reg("A0"))
    if a0 == PLAYER_1 then return "sonic" end
    if a0 == PLAYER_2 then return "tails" end
    return nil
end

local function prefix(who)
    return string.format("f=%d lfc=%04X who=%s", traceFrame(),
        mainmemory.read_u16_be(LEVEL_FRAME_COUNTER), who)
end

local function playerState(base)
    return string.format(
        "x=%04X y=%04X xsub=%04X ysub=%04X xvel=%04X yvel=%04X gvel=%04X"
            .. " ang=%02X status=%02X rtn=%02X yrad=%02X xrad=%02X"
            .. " topbit=%02X lrbbit=%02X stick=%02X",
        mainmemory.read_u16_be(base + 0x10),
        mainmemory.read_u16_be(base + 0x14),
        mainmemory.read_u16_be(base + 0x12),
        mainmemory.read_u16_be(base + 0x16),
        mainmemory.read_u16_be(base + 0x18),
        mainmemory.read_u16_be(base + 0x1A),
        mainmemory.read_u16_be(base + 0x1C),
        mainmemory.read_u8(base + 0x26),
        mainmemory.read_u8(base + 0x2A),
        mainmemory.read_u8(base + 0x05),
        mainmemory.read_u8(base + 0x1E),
        mainmemory.read_u8(base + 0x1F),
        mainmemory.read_u8(base + 0x46),
        mainmemory.read_u8(base + 0x47),
        mainmemory.read_u8(base + 0x3C))
end

local function logExit(context, tag)
    if pending == nil then return end
    context.log(string.format(
        "%s F264_EXIT sensor=%d path=%s d1=%d angle_out=%02X",
        pending.head, pending.sensor, tag,
        signedWord(reg("D1")), mainmemory.read_u8(pending.angleVar)))
    pending = nil
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0x07
            and traceFrame() >= WINDOW_FIRST - 4
    end,
    hooks = {
        {
            name = "mhz_f3246_anglepos",
            address = ADDR_ANGLEPOS,
            callback = function(context)
                if traceFrame() > WINDOW_LAST then
                    context.log(string.format("f=%d done=1", traceFrame()))
                    context.finish()
                    return
                end
                local who = playerName()
                if who == nil or not inWindow() then return end
                sensor = 0
                pending = nil
                local base = word(reg("A0"))
                context.log(string.format(
                    "%s ANGLEPOS zone=%02X act=%02X %s"
                        .. " prim_coll=%08X sec_coll=%08X coll=%08X bgflag=%02X",
                    prefix(who), mainmemory.read_u8(CURRENT_ZONE),
                    mainmemory.read_u8(CURRENT_ACT), playerState(base),
                    mainmemory.read_u32_be(PRIMARY_COLLISION_ADDR),
                    mainmemory.read_u32_be(SECONDARY_COLLISION_ADDR),
                    mainmemory.read_u32_be(COLLISION_ADDR),
                    mainmemory.read_u8(BACKGROUND_COLLISION_FLAG)))
            end
        },
        {
            name = "mhz_f3246_f264_entry",
            address = ADDR_F264,
            callback = function(context)
                local who = playerName()
                if who == nil or not inWindow() then return end
                sensor = sensor + 1
                local angleVar = word(reg("A4"))
                pending = {
                    sensor = sensor,
                    angleVar = angleVar,
                    head = prefix(who)
                }
                context.log(string.format(
                    "%s F264_IN sensor=%d sx=%04X sy=%04X d5=%02X d6=%04X"
                        .. " a3=%04X a4=%04X(%s) a5=%06X coll=%08X",
                    pending.head, sensor,
                    word(reg("D3")), word(reg("D2")),
                    reg("D5") & 0xFF, word(reg("D6")),
                    word(reg("A3")), angleVar,
                    angleVar == PRIMARY_ANGLE and "primary/right"
                        or (angleVar == SECONDARY_ANGLE and "secondary/left" or "?"),
                    reg("A5") & 0xFFFFFF,
                    mainmemory.read_u32_be(COLLISION_ADDR)))
            end
        },
        {
            name = "mhz_f3246_f282",
            address = ADDR_F282,
            callback = function(context)
                if pending == nil then return end
                context.log(string.format(
                    "%s F264_SOLID sensor=%d chunk_word=%04X chunk_idx=%03X",
                    pending.head, pending.sensor,
                    word(reg("D4")), word(reg("D0")) & 0x3FF))
            end
        },
        {
            name = "mhz_f3246_f2ba",
            address = ADDR_F2BA,
            callback = function(context)
                if pending == nil then return end
                local d0 = word(reg("D0"))
                context.log(string.format(
                    "%s F264_BLOCK sensor=%d block_id=%02X angle_arr=%02X"
                        .. " col_pre=%04X d4=%04X",
                    pending.head, pending.sensor,
                    (d0 >> 4) & 0xFF,
                    mainmemory.read_u8(pending.angleVar),
                    word(reg("D1")), word(reg("D4"))))
            end
        },
        {
            name = "mhz_f3246_exit_below",
            address = ADDR_F274_RTS,
            callback = function(context) logExit(context, "tile_below_+10") end
        },
        {
            name = "mhz_f3246_exit_normal",
            address = ADDR_F2F0_RTS,
            callback = function(context) logExit(context, "normal") end
        },
        {
            name = "mhz_f3246_exit_above",
            address = ADDR_F30A_RTS,
            callback = function(context) logExit(context, "tile_above_-10") end
        },
        {
            name = "mhz_f3246_player_angle",
            address = ADDR_PLAYER_ANGLE,
            callback = function(context)
                local who = playerName()
                if who == nil or not inWindow() then return end
                local primaryDist = signedWord(reg("D0"))
                local secondaryDist = signedWord(reg("D1"))
                context.log(string.format(
                    "%s PLAYER_ANGLE primary_d0=%d secondary_d1=%d"
                        .. " prim_angle=%02X sec_angle=%02X chosen=%s player_angle=%02X",
                    prefix(who), primaryDist, secondaryDist,
                    mainmemory.read_u8(PRIMARY_ANGLE),
                    mainmemory.read_u8(SECONDARY_ANGLE),
                    secondaryDist <= primaryDist and "secondary/left" or "primary/right",
                    mainmemory.read_u8(word(reg("A0")) + 0x26)))
            end
        },
        {
            name = "mhz_f3246_ed12",
            address = ADDR_ED12,
            callback = function(context)
                local who = playerName()
                if who == nil or not inWindow() then return end
                context.log(string.format("%s ED12_RETURN d1=%d player_angle=%02X",
                    prefix(who), signedWord(reg("D1")),
                    mainmemory.read_u8(word(reg("A0")) + 0x26)))
            end
        },
        {
            name = "mhz_f3246_ed14",
            address = ADDR_ED14,
            callback = function(context)
                local who = playerName()
                if who == nil or not inWindow() then return end
                context.log(string.format(
                    "%s ED14_POSITIVE min_d1=%d xvel=%04X stick=%02X",
                    prefix(who), signedWord(reg("D1")),
                    mainmemory.read_u16_be(word(reg("A0")) + 0x18),
                    mainmemory.read_u8(word(reg("A0")) + 0x3C)))
            end
        },
        {
            name = "mhz_f3246_ed38",
            address = ADDR_ED38,
            callback = function(context)
                local who = playerName()
                if who == nil then return end
                context.log(string.format("%s ED38_DETACH min_d1=%d %s",
                    prefix(who), signedWord(reg("D1")),
                    playerState(word(reg("A0")))))
            end
        }
    }
})
