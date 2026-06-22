-- Capture OOZ popping-platform (id $33) Y vs Tails state across the OOZ-LS f1782
-- cluster-4 divergence (engine sets Tails Status_Push one frame late).
-- COMPARISON-ONLY. Output: tools/bizhawk/trace_output/s2_ooz_pform.txt
-- BK2 frame = bk2_frame_offset(2772) + trace_frame. trace 1781 -> BK2 4553.

local START = tonumber(os.getenv("OGGF_DIAG_START") or "4546")
local STOP  = tonumber(os.getenv("OGGF_DIAG_STOP")  or "4560")
local OBJ_BASE = 0xB000   -- mainmemory offset of MainCharacter (sys-bus 0xFFB000)
local SLOT = 0x40
local TAILS = 0xB040

local out = {}
local function log(s) table.insert(out, s) end
local function u16(off) return mainmemory.read_u16_be(off) end
local function u8(off) return mainmemory.read_u8(off) end

local function dumpFrame()
    local f = emu.framecount()
    if f < START or f > STOP then return end
    -- Tails state
    local tx = u16(TAILS + 0x08)
    local ty = u16(TAILS + 0x0C)
    local tst = u8(TAILS + 0x22)
    local tgv = u16(TAILS + 0x1C)  -- inertia/ground_vel (S2 inertia at 0x1C)
    local txv = u16(TAILS + 0x10)
    log(string.format("f=%d tails x=%04X y=%04X st=%02X(push=%s) gv=%04X xv=%04X",
        f, tx, ty, tst, ((tst & 0x20) ~= 0) and "Y" or "n", tgv, txv))
    -- Scan object slots for id $33 (popping platform) near Tails
    for slot = 0, 0x5F do
        local base = OBJ_BASE + slot * SLOT
        local id = u8(base + 0x00)
        if id == 0x33 then
            local ox = u16(base + 0x08)
            local oy = u16(base + 0x0C)
            if ox >= 0x0C90 and ox <= 0x0D10 then
                log(string.format("       pform slot=%d id=33 x=%04X y=%04X (dy_tails=%d dx=%d)",
                    slot, ox, oy, ty - oy, tx - ox))
            end
        end
    end
end

local function dump()
    local path = os.getenv("OGGF_DIAG_OUT")
        or "C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output/s2_ooz_pform.txt"
    local fh = io.open(path, "w"); if not fh then print("DIAG: cannot open "..path); return end
    fh:write(table.concat(out, "\n") .. "\n"); fh:close()
    print("DIAG: wrote "..path.." ("..#out.." lines)")
end

local written = false
while true do
    dumpFrame()
    if emu.framecount() > STOP and not written then written=true; dump(); client.exit(); break end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
