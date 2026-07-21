-- Probe 2: does the ROM run SolidObject_Monitor_SonicKnux for the object near
-- x=0x2B38 during the aiz_2 monitor-wall window, and which exit does it take?
-- Hooks (addresses from local labels around sonic3k.asm:40564):
--   0x1D696 = SolidObject_Monitor_SonicKnux entry (first instruction after
--             locret_1D694's rts at 0x1D694, word-aligned)
--   0x1D6BE = loc_1D6BE (all gates passed -> bra.w SolidObject_cont)
-- Frame loop also logs slot 8 (SST base 0xB250 = 0xB000 + 8*0x4A): code ptr,
-- status, x, y — to identify the object the recording calls 0x0001B588.
-- Window: bk2 7220-7245 (seg f219-f244). Output: diag_aiz2_monitor_solid_output.txt

local START_F = 7220
local STOP_F  = 7245
local SLOT8   = 0xB250

local out = assert(io.open("diag_aiz2_monitor_solid_output.txt", "w"))
out:write("== frame rows: F,<bk2>,slot8_code,slot8_status,slot8_x,slot8_y,p1_x,p1_y\n")
out:write("== hook rows:  H,<bk2>,<which>,a0,objx,objy\n")
out:flush()

local function hook(which)
    return function()
        local f = emu.framecount()
        if f < START_F - 5 or f > STOP_F + 5 then return end
        local a0 = emu.getregister("M68K A0") or emu.getregister("A0") or 0
        local base = a0 % 0x10000
        local ox = mainmemory.read_u16_be(base + 0x10)
        local oy = mainmemory.read_u16_be(base + 0x14)
        out:write(string.format("H,%d,%s,0x%X,0x%04X,0x%04X\n", f, which, a0, ox, oy))
        out:flush()
    end
end

event.onmemoryexecute(hook("ENTRY_1D696"), 0x1D696)
event.onmemoryexecute(hook("PASSGATES_1D6BE"), 0x1D6BE)

pcall(function() client.speedmode(400) end)

while true do
    emu.frameadvance()
    local f = emu.framecount()
    if f >= START_F and f <= STOP_F then
        local code = mainmemory.read_u32_be(SLOT8 + 0x00)
        local st   = mainmemory.read_u8(SLOT8 + 0x2A)
        local sx   = mainmemory.read_u16_be(SLOT8 + 0x10)
        local sy   = mainmemory.read_u16_be(SLOT8 + 0x14)
        local px   = mainmemory.read_u16_be(0xB000 + 0x10)
        local py   = mainmemory.read_u16_be(0xB000 + 0x14)
        out:write(string.format("F,%d,0x%08X,0x%02X,0x%04X,0x%04X,0x%04X,0x%04X\n",
                f, code, st, sx, sy, px, py))
        out:flush()
    end
    if f > STOP_F then
        out:close()
        pcall(function() client.exit() end)
        break
    end
end
