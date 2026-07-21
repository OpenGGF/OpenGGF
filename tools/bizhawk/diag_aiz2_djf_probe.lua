-- Probe: Knuckles double_jump_flag/anim/x across the aiz_2 monitor-wall window.
-- Movie: s3-knux-multibonus-ss.bk2. aiz_2 segment bk2_frame_offset = 7001;
-- segment frames f150-f250 => bk2 frames 7151-7251 (probe wide: 7140-7260).
-- Output: diag_aiz2_djf_probe_output.txt in this script's directory.
-- Player_1 = 0xB000 (mainmemory); double_jump_flag = +0x2F; anim = +0x20;
-- x pixel = u16 at +0x10 (high word of 32-bit position); y pixel = +0x14.

local START_F = 7140
local STOP_F  = 7260
local PLAYER  = 0xB000

local out = assert(io.open("diag_aiz2_djf_probe_output.txt", "w"))
out:write("bk2_frame,seg_frame,djf,anim,x,y,status\n")
out:flush()

pcall(function() client.speedmode(400) end)

while true do
    emu.frameadvance()
    local f = emu.framecount()
    if f >= START_F and f <= STOP_F then
        local djf    = mainmemory.read_u8(PLAYER + 0x2F)
        local anim   = mainmemory.read_u8(PLAYER + 0x20)
        local x      = mainmemory.read_u16_be(PLAYER + 0x10)
        local y      = mainmemory.read_u16_be(PLAYER + 0x14)
        local status = mainmemory.read_u8(PLAYER + 0x2A)
        out:write(string.format("%d,%d,0x%02X,0x%02X,0x%04X,0x%04X,0x%02X\n",
                f, f - 7001, djf, anim, x, y, status))
        out:flush()
    end
    if f > STOP_F then
        out:close()
        pcall(function() client.exit() end)
        break
    end
end
