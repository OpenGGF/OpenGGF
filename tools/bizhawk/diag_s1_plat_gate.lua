-- Find why ROM's Plat_NoXCheck_AltY exits before landing at the GHZ d0=0 frame.
-- Hooks the gate instructions between the range check and the land write:
--   0x7B0E tst.b (f_playerctrl).w     (exit if negative)
--   0x7B16 cmpi.b #6,obRoutine(a1)    (exit if Sonic routine >= 6)
--   0x7B24 move.w d2,obY(a1)          (the LAND write -- only on a real landing)
-- Filters a1 = Sonic. COMPARISON-ONLY. Output: tools/bizhawk/trace_output/s1_plat_gate.txt

local START_FRAME = tonumber(os.getenv("OGGF_DIAG_START") or "3358")
local STOP_FRAME  = tonumber(os.getenv("OGGF_DIAG_STOP")  or "3365")
local SONIC = 0xFFD000
local F_PLAYERCTRL = 0xF7C8   -- mainmemory offset (sys-bus 0xFFF7C8)

local out = {}
local function log(s) table.insert(out, s) end
local function s16(v) v = v % 0x10000; if v >= 0x8000 then v = v - 0x10000 end; return v end
local function inwin() local f=emu.framecount(); return f>=START_FRAME and f<=STOP_FRAME end
local function isSonic() return ((emu.getregister("M68K A1") or 0) % 0x1000000) == SONIC end

local function at_gate0E()
    if not inwin() or not isSonic() then return end
    local d0 = s16(emu.getregister("M68K D0") or 0)
    local fpc = mainmemory.read_u8(F_PLAYERCTRL)
    local srtn = mainmemory.read_u8(0xD024)  -- Sonic obRoutine
    local sy = mainmemory.read_u16_be(0xD00C)
    log(string.format("f=%d GATE0E sonicY=%04X d0=%d f_playerctrl=0x%02X sonicRtn=0x%02X (exit_fpc=%s)",
        emu.framecount(), sy, d0, fpc, srtn, (fpc >= 0x80) and "YES" or "no"))
end

local function at_land24()
    if not inwin() or not isSonic() then return end
    log(string.format("f=%d LAND-WRITE (0x7B24 executed) sonicY=%04X", emu.framecount(),
        mainmemory.read_u16_be(0xD00C)))
end

local function dump()
    local path = os.getenv("OGGF_DIAG_OUT")
        or "C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output/s1_plat_gate.txt"
    local f = io.open(path, "w"); if not f then print("DIAG: cannot open "..path); return end
    f:write(table.concat(out, "\n") .. "\n"); f:close()
    print("DIAG: wrote "..path.." ("..#out.." lines)")
end

local ok1 = event and event.onmemoryexecute and pcall(function() event.onmemoryexecute(at_gate0E, 0x7B0E) end)
local ok2 = event and event.onmemoryexecute and pcall(function() event.onmemoryexecute(at_land24, 0x7B24) end)
print("S1 Plat gate hook loaded. ok="..tostring(ok1).."/"..tostring(ok2))

local written = false
while true do
    if emu.framecount() > STOP_FRAME and not written then written=true; dump(); client.exit(); break end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
