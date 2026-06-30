-- s1_level_map.lua — discovery pass: log every game_mode/zone/act transition
-- across a BK2 movie so we can map per-level bk2_frame_offset values for the
-- complete-run trace generation. Read-only; writes trace_output/level_map.txt
-- and exits EmuHawk when the movie ends.
--
-- S1 RAM: game_mode @ 0xF600 (0x0C = level), zone @ 0xFE10, act @ 0xFE11.

local ADDR_GAME_MODE = 0xF600
local ADDR_ZONE      = 0xFE10
local ADDR_ACT       = 0xFE11

local OUT_PATH = "C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output/level_map.txt"
local out = io.open(OUT_PATH, "w")
out:write("# frame  game_mode  zone  act   (transitions only)\n")
out:flush()

local total = movie.length()
out:write(string.format("# movie length = %d frames\n", total))
out:flush()

local last = nil
while emu.framecount() < total do
    local gm = mainmemory.read_u8(ADDR_GAME_MODE)
    local z  = mainmemory.read_u8(ADDR_ZONE)
    local a  = mainmemory.read_u8(ADDR_ACT)
    local key = gm .. "/" .. z .. "/" .. a
    if key ~= last then
        out:write(string.format("frame=%d game_mode=0x%02X zone=%d act=%d\n",
                emu.framecount(), gm, z, a))
        out:flush()
        last = key
    end
    emu.frameadvance()
end

out:write(string.format("# END at frame %d\n", emu.framecount()))
out:close()
client.exit()
