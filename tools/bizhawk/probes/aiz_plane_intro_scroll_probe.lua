local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua with the canonical probe runtime")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE_AND_ACT = 0xFE10
local PLANE = 0xB172
local PLANE_POINTER = 0x00067472
local PLANE_ROUTINE = 0xB177
local PLANE_SCROLL_SPEED = 0xB1B2
local EVENTS_FG_1 = 0xEEB6
local PLAYER_1_X = 0xB010
local LEVEL_FRAME_COUNTER = 0xFE04
local VBLANK_COUNTER = 0xFE0E

local sawEventZero = false
local sawFirstPlayerAdd = false

local function word(address)
    return mainmemory.read_u16_be(address)
end

local function snapshot(label)
    return string.format(
        "emu=%d label=%s pc=%06X routine=%02X events=%04X speed=%04X player_x=%04X level=%04X vblank=%04X d0=%04X d1=%04X",
        emu.framecount(),
        label,
        emu.getregister("M68K PC") or 0,
        mainmemory.read_u8(PLANE_ROUTINE),
        word(EVENTS_FG_1),
        word(PLANE_SCROLL_SPEED),
        word(PLAYER_1_X),
        word(LEVEL_FRAME_COUNTER),
        word(VBLANK_COUNTER),
        (emu.getregister("M68K D0") or 0) & 0xFFFF,
        (emu.getregister("M68K D1") or 0) & 0xFFFF)
end

ProbeRuntime.run({
    stage = function()
        local mode = mainmemory.read_u8(GAME_MODE)
        if (mode & 0x0F) ~= 0x0C then return false end
        if word(CURRENT_ZONE_AND_ACT) ~= 0 then return false end
        if mainmemory.read_u32_be(PLANE) ~= PLANE_POINTER then return false end
        local eventsFg1 = word(EVENTS_FG_1)
        return eventsFg1 >= 0xFFC0 and eventsFg1 <= 0xFFFF
            and word(PLANE_SCROLL_SPEED) == 0x0010
    end,
    hooks = {
        {
            name = "aiz_plane_intro_scroll_tail_entry",
            address = 0x067A08,
            callback = function(context)
                context.log(snapshot("tail_entry"))
            end
        },
        {
            name = "aiz_plane_intro_scroll_sign_branch",
            address = 0x067A10,
            callback = function(context)
                context.log(snapshot("sign_branch"))
            end
        },
        {
            name = "aiz_plane_intro_event_add_post_store",
            address = 0x067A18,
            callback = function(context)
                if word(EVENTS_FG_1) == 0 then
                    sawEventZero = true
                end
                context.log(snapshot("event_add_post_store"))
            end
        },
        {
            name = "aiz_plane_intro_player_add_post_add",
            address = 0x067A1E,
            callback = function(context)
                context.log(snapshot("player_add_post_add"))
                if sawEventZero and sawFirstPlayerAdd then
                    context.finish()
                elseif sawEventZero then
                    sawFirstPlayerAdd = true
                end
            end
        }
    }
})
