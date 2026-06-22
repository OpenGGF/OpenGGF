-- Hook S1 Plat_NoXCheck_AltY range check (PC 0x7B02, right after `sub.w d1,d0`)
-- to read the exact d0/d1/d2 and a0/a1 the platform/slope landing evaluates each
-- time it runs. Resolves the GHZ collapsing-ledge one-frame-late landing paradox.
-- COMPARISON-ONLY. Output: tools/bizhawk/trace_output/s1_plat.txt

local HOOK_PC = 0x7B02
local START_FRAME = tonumber(os.getenv("OGGF_DIAG_START") or "3358")
local STOP_FRAME  = tonumber(os.getenv("OGGF_DIAG_STOP")  or "3365")
local SONIC = 0xFFD000   -- system-bus address of Sonic object

local out = {}
local function log(s) table.insert(out, s) end
local function s16(v) v = v % 0x10000; if v >= 0x8000 then v = v - 0x10000 end; return v end

local function on_plat()
    local f = emu.framecount()
    if f < START_FRAME or f > STOP_FRAME then return end
    local a1 = (emu.getregister("M68K A1") or 0) % 0x1000000
    -- only the player (a1 = Sonic)
    if a1 ~= SONIC then return end
    local a0 = (emu.getregister("M68K A0") or 0) % 0x1000000
    local d0 = s16(emu.getregister("M68K D0") or 0)
    local d1 = s16(emu.getregister("M68K D1") or 0)
    local d2 = s16(emu.getregister("M68K D2") or 0)
    -- a0 = the platform/ledge object; read its id + y
    local objBase = a0 % 0x10000
    local objId = mainmemory.read_u8(objBase + 0x00)
    local objY = mainmemory.read_u16_be(objBase + 0x0C)
    local objX = mainmemory.read_u16_be(objBase + 0x08)
    local sonicY = mainmemory.read_u16_be(0xD00C)
    log(string.format("f=%d PLAT a0=0x%04X id=0x%02X obj@%04X,%04X sonicY=%04X d0=%d d1=%04X d2=%04X -> %s",
        f, objBase, objId, objX, objY, sonicY, d0, d1 % 0x10000, d2 % 0x10000,
        (d0 > 0) and "EXIT(above)" or ((d0 < -16) and "EXIT(deep)" or "LAND")))
end

local function dump()
    local path = os.getenv("OGGF_DIAG_OUT")
        or "C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output/s1_plat.txt"
    local f = io.open(path, "w")
    if not f then print("DIAG: cannot open " .. path); return end
    f:write(table.concat(out, "\n") .. "\n"); f:close()
    print("DIAG: wrote " .. path .. " (" .. #out .. " lines)")
end

local hooked = false
if event and event.onmemoryexecute then
    hooked = pcall(function() event.onmemoryexecute(on_plat, HOOK_PC) end)
end
print("S1 Plat hook loaded. hooked=" .. tostring(hooked) .. " pc=0x" .. string.format("%X", HOOK_PC))

local written = false
while true do
    if emu.framecount() > STOP_FRAME and not written then
        written = true; dump(); client.exit(); break
    end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
