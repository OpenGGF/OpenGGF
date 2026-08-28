-- How does Sonic's animation byte reach 0x05 on segment 4's row 0?
--
-- The anchor probe (aiz2_entry_anchor_frame_probe.lua) settled that row 0 of
-- `aiz_3` is BizHawk frame 19776, and showed that across the 19775 -> 19776
-- boundary anim ($20), prev_anim ($21), mapping_frame ($22) and status ($2A)
-- all change together (00/00/00/00 -> 05/05/BA/02) with NO write event on the
-- anim byte itself -- the last observed anim write, at 19775, stores zero.
--
-- Either the neighbouring SST bytes are written on that frame and the anim byte
-- alone arrives by some other route, or none of them are and the write hook is
-- blind to whatever performs the update. Hooking the neighbours discriminates:
-- observation-only, one hook per byte, logged over a three-frame window.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11
local SONIC_BASE = 0xB000
local MOVIE_FRAME_FLOOR = 19500

local LOG_FIRST_FRAME = 19774
local LOG_LAST_FRAME = 19778

local WATCHED = {
    { offset = 0x00, label = "id_high" },
    { offset = 0x05, label = "routine" },
    { offset = 0x20, label = "anim" },
    { offset = 0x21, label = "prev_anim" },
    { offset = 0x22, label = "mapping_frame" },
    { offset = 0x2A, label = "status" },
}

local function register(name)
    local value = emu.getregister(name)
    if value == nil then return 0 end
    return value
end

local hooks = {}
for _, watch in ipairs(WATCHED) do
    hooks[#hooks + 1] = {
        name = "sst_arrival_" .. watch.label,
        kind = "write",
        address = 0xFF0000 + SONIC_BASE + watch.offset,
        callback = function(context)
            local frame = emu.framecount()
            if frame < LOG_FIRST_FRAME or frame > LOG_LAST_FRAME then return end
            context.log(string.format(
                "WRITE emu=%d %-13s pc=%06X a0=%06X a1=%06X d0=%08X d1=%08X | now=%02X",
                frame, watch.label,
                register("M68K PC") & 0xFFFFFF,
                register("M68K A0") & 0xFFFFFF,
                register("M68K A1") & 0xFFFFFF,
                register("M68K D0") & 0xFFFFFFFF,
                register("M68K D1") & 0xFFFFFFFF,
                mainmemory.read_u8(SONIC_BASE + watch.offset)))
        end
    }
end

ProbeRuntime.run({
    stage = function()
        return emu.framecount() >= MOVIE_FRAME_FLOOR
            and (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0
            and mainmemory.read_u8(CURRENT_ACT) == 1
    end,
    hooks = hooks,
    onFrame = function(context)
        local frame = emu.framecount()
        if frame < LOG_FIRST_FRAME then return end
        if frame > LOG_LAST_FRAME then
            context.log("WINDOW-END emu=" .. frame)
            context.finish()
            return
        end
        local parts = {}
        for _, watch in ipairs(WATCHED) do
            parts[#parts + 1] = string.format("%s=%02X", watch.label,
                mainmemory.read_u8(SONIC_BASE + watch.offset))
        end
        context.log(string.format("FRAME emu=%d mode=%02X | %s",
            frame, mainmemory.read_u8(GAME_MODE), table.concat(parts, " ")))
    end
})
