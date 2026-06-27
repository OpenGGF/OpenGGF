-- Diagnostic probe (COMPARISON-ONLY, read-only): resolve the SYZ1 f4430
-- Sonic_FloorUp branch contradiction using BizHawk M68K PC-execute hooks to read
-- MID-FRAME register/RAM state that the per-frame recorder cannot see.
--
-- THE CONTRADICTION (SYZ1 complete-run, trace f4430, g_speed exp=0x0000):
--   Sonic_FloorUp (docs/s1disasm/_incObj/01 Sonic.asm:1681-1731) has TWO ceiling
--   branches after `Sonic_FindCeiling` aligns Sonic to the ceiling:
--     * FLAT ceiling (asm:1715, PC 0x13DF2): `move.w #0,obVelY(a0)` then rts.
--       Zeroes obVelY; does NOT touch obInertia.
--     * ANGLED ceiling (asm:1720-1726, inertia copy at asm:1723, PC 0x13E02):
--       `move.b d3,obAngle / Sonic_ResetOnFloor / move.w obVelY(a0),obInertia(a0)`
--       (+ neg for ascending). Copies obVelY -> obInertia; does NOT zero obVelY.
--   The branch decision is asm:1711-1714:
--       move.b d3,d0 / addi.b #$20,d0 / andi.b #$40,d0 (PC 0x13DEC)
--       bne.s .angledceiling                            (PC 0x13DF0)
--   A prior agent argued the engine reads ceiling angle 0xA8 -> takes
--   .angledceiling -> g_speed = -obVelY = +0x370, with obVelY NOT zeroed -- yet
--   ROM ends g_speed=0. EITHER ROM takes the FLAT branch (engine branch-selection
--   bug, fixable) OR ROM takes angledceiling with obVelY already 0.
--
-- This probe hooks the two distinguishing instruction PCs and, when the player
-- object (a0 == v_player = 0xFFD000) is being processed inside the SYZ1 f4430
-- window, dumps which branch fired plus obVelY/obInertia/angle. Read-only.
--
-- PCs from docs/s1disasm/sonic.lst:
--   65024 1681/13DB6  Sonic_FloorUp:                       (entry)
--   65047 1713/13DEC  andi.b #$40,d0  (branch-decision setup)
--   65048 1714/13DF0  bne.s .angledceiling (the decision)
--   65049 1715/13DF2  move.w #0,obVelY(a0)  (FLAT ceiling branch)
--   65057 1723/13E02  move.w obVelY(a0),obInertia(a0) (ANGLED ceiling inertia copy)
--
-- Frame mapping: SYZ1 complete-run metadata bk2_frame_offset = 61548, so trace
-- frame 4430 = BizHawk frame 61548 + 4430 = 65978. Gate a band around it.

local PLAYER_ADDR  = 0xFFD000   -- v_player (full 24-bit address; a0 in Sonic_FloorUp)
local PB           = 0xD000     -- mainmemory window offset for v_player
local OFF_X_POS    = 0x08       -- word: centre X
local OFF_Y_POS    = 0x0C       -- word: centre Y
local OFF_X_VEL    = 0x10       -- signed word: obVelX
local OFF_Y_VEL    = 0x12       -- signed word: obVelY
local OFF_INERTIA  = 0x14       -- signed word: obInertia (ground speed)
local OFF_ANGLE    = 0x26       -- byte: obAngle
local OFF_STATUS   = 0x22       -- byte: obStatus
local OFF_ROUTINE  = 0x24       -- byte: obRoutine

-- Sonic_FloorUp instruction PCs (ROM addresses, big-endian 68k execution space).
local PC_FLOORUP_ENTRY = 0x13DB6
local PC_DECISION      = 0x13DF0   -- bne.s .angledceiling
local PC_FLAT_CEIL     = 0x13DF2   -- move.w #0,obVelY(a0)   -> FLAT branch taken
local PC_ANGLED_COPY   = 0x13E02   -- move.w obVelY,obInertia -> ANGLED branch taken

-- BizHawk frame window around SYZ1 trace f4430 (bk2_offset 61548 + 4430 = 65978).
local WIN_LO = 65900
local WIN_HI = 66060

-- Optional hard stop a little past the window (S1_STOP_AT_FRAME) so a headless run
-- finishes fast instead of replaying the whole movie.
local STOP_AT = tonumber(os.getenv("S1_STOP_AT_FRAME") or "66200")

local out = {}
local function log(s) table.insert(out, s) end

-- emu.getregister returns the full register; mask to 24-bit address space.
local function reg(name) return (emu.getregister(name) or 0) % 0x1000000 end

-- Is the object being processed (a0) the player, in the f4430 window?
local function player_in_window(a0, ef)
    return a0 == PLAYER_ADDR and ef >= WIN_LO and ef <= WIN_HI
end

local function snapshot(a0)
    -- Read the player work-RAM fields (mainmemory window).
    local vy = mainmemory.read_s16_be(PB + OFF_Y_VEL)
    local vx = mainmemory.read_s16_be(PB + OFF_X_VEL)
    local inertia = mainmemory.read_s16_be(PB + OFF_INERTIA)
    local angle = mainmemory.read_u8(PB + OFF_ANGLE)
    local status = mainmemory.read_u8(PB + OFF_STATUS)
    local routine = mainmemory.read_u8(PB + OFF_ROUTINE)
    local x = mainmemory.read_u16_be(PB + OFF_X_POS)
    local y = mainmemory.read_u16_be(PB + OFF_Y_POS)
    return string.format(
        "obVelY=0x%04X obVelX=0x%04X obInertia=0x%04X obAngle=0x%02X obStatus=0x%02X obRoutine=0x%02X x=0x%04X y=0x%04X",
        vy % 0x10000, vx % 0x10000, inertia % 0x10000, angle, status, routine, x, y)
end

-- Entry: confirms Sonic_FloorUp ran for the player this frame (context).
event.onmemoryexecute(function()
    local ef = emu.framecount()
    local a0 = reg("M68K A0")
    if not player_in_window(a0, ef) then return end
    -- d3 holds the landing angle the branch decision keys off; d0 is being built.
    local d3 = (emu.getregister("M68K D3") or 0) % 0x100
    local d0 = (emu.getregister("M68K D0") or 0) % 0x100
    log(string.format("ENTRY  emuf=%d (traceF=%d) PC=0x%05X d3=0x%02X d0=0x%02X | %s",
        ef, ef - 61548, PC_FLOORUP_ENTRY, d3, d0, snapshot(a0)))
end, PC_FLOORUP_ENTRY)

-- Decision point: log d0 right before the bne (d0 = (d3+$20)&$40); nonzero -> angled.
event.onmemoryexecute(function()
    local ef = emu.framecount()
    local a0 = reg("M68K A0")
    if not player_in_window(a0, ef) then return end
    local d0 = (emu.getregister("M68K D0") or 0) % 0x100
    local d3 = (emu.getregister("M68K D3") or 0) % 0x100
    log(string.format("DECIDE emuf=%d (traceF=%d) PC=0x%05X d0=0x%02X (nonzero->ANGLED) d3=0x%02X | %s",
        ef, ef - 61548, PC_DECISION, d0, d3, snapshot(a0)))
end, PC_DECISION)

-- FLAT ceiling branch taken: move.w #0,obVelY. obVelY is about to be zeroed;
-- obInertia is left untouched.
event.onmemoryexecute(function()
    local ef = emu.framecount()
    local a0 = reg("M68K A0")
    if not player_in_window(a0, ef) then return end
    log(string.format("*** FLAT-CEILING branch (PC=0x%05X, obVelY->0, obInertia untouched) "
        .. "emuf=%d (traceF=%d) | %s", PC_FLAT_CEIL, ef, ef - 61548, snapshot(a0)))
end, PC_FLAT_CEIL)

-- ANGLED ceiling branch taken: move.w obVelY,obInertia. Capture obVelY at the
-- moment of the copy (this is the value that becomes inertia).
event.onmemoryexecute(function()
    local ef = emu.framecount()
    local a0 = reg("M68K A0")
    if not player_in_window(a0, ef) then return end
    log(string.format("*** ANGLED-CEILING branch (PC=0x%05X, obInertia<-obVelY) "
        .. "emuf=%d (traceF=%d) | %s", PC_ANGLED_COPY, ef, ef - 61548, snapshot(a0)))
end, PC_ANGLED_COPY)

print(string.format(
    "SYZ1 Sonic_FloorUp probe armed. Window emuf [%d,%d] (trace f%d-%d). Stop at %d.",
    WIN_LO, WIN_HI, WIN_LO - 61548, WIN_HI - 61548, STOP_AT))

-- Headless: run at max speed.
if client ~= nil then
    if emu.limitframerate then emu.limitframerate(false) end
    if client.speedmode then client.speedmode(6400) end
    if client.invisibleemulation then client.invisibleemulation(true) end
end

local total = movie.isloaded() and movie.length() or 0
while true do
    local f = emu.framecount()
    if STOP_AT > 0 and f >= STOP_AT then break end
    if total > 0 and f >= total then break end
    if client.ispaused and client.ispaused() then client.unpause() end
    emu.frameadvance()
end

local path = os.getenv("OGGF_DIAG_OUT") or "syz1_floorup_probe.txt"
local file = io.open(path, "w")
file:write(string.format("== SYZ1 Sonic_FloorUp branch probe (bk2_offset 61548, trace f4430 = emuf 65978) ==\n"))
if #out == 0 then
    file:write("(no hook fired in the window -- player did not enter Sonic_FloorUp at f4430, "
        .. "OR the window/PCs need adjusting)\n")
end
for _, line in ipairs(out) do file:write(line .. "\n") end
file:close()
print("probe: wrote " .. #out .. " rows to " .. path)
if client ~= nil and client.exit then client.exit() end
