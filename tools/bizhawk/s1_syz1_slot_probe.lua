-- Diagnostic probe (COMPARISON-ONLY, read-only): SYZ1 f4431 standonobject /
-- slot-54 occupancy + Sonic_SlopeRepel / Sonic_AnglePos detach timeline, via
-- BizHawk M68K PC-execute hooks.
--
-- FINDING (resolves SYZ1 f4431; falsifies the banked "slot-0x54 ridable solid"
-- root): the aux `onObj=54` is the HEX standonobject byte 0x54 = decimal slot 84,
-- NOT decimal slot 54. The byte is STALE (no SolidObject/PlatformObject write of
-- standonobject fires in the f4392-4452 window; Status_OnObj/bit3 is never set).
-- The player is on TERRAIN, not an object. ROM f4427-4430 is a SpikedBall hurt
-- knockback (Sonic_Hurt routine 4) that lands at 14A2,0487 angle 0xA8 and
-- Sonic_HurtStop zeroes vy/vx/inertia (matches engine f4430). ROM f4431
-- (Sonic_MdNormal): wall-walks angle 0xC0 -> vY=0xFFF6(-10), then
-- Sonic_SlopeRepel detaches (SLOPE-DETACH 0x13C48, |inertia| 0x0C < 0x280).
-- Engine f4431 is already airborne -> air gravity vY=+12, one control-frame out
-- of phase. Shared steep-wall hurt-recovery-into-control physics; not a slot bug.
--
-- (original note retained:)
-- At SYZ1 trace f4431 the player y_speed diverges (ROM -000A / engine +000C);
-- the per-frame aux shows ROM standonobject (onObj)=54 (decimal slot) but BOTH
-- ROM and engine carry obID 0x37 (LostRing) in slot 54 at frame-start, so the
-- standonobject byte was set on an EARLIER frame when slot 54 held a SOLID.
-- This probe captures, mid-frame:
--   (A) every SolidObject / PlatformObject write of standonobject(v_player)
--       (PC 0x102E6 SolidObject, PC 0x7B66 PlatformObject) -> which slot the
--       player just landed on + that slot's obID/X/Y.
--   (B) FindFreeObj (0xE11A) / FindNextFreeObj (0xE130) returns -> which slot
--       allocated (a1 after the bset Status_OnObj path); and DeleteObject
--       (0xDCCE) frees -> which slot (a0).
--   (C) per-frame end-of-frame snapshot: player obStatus / standonobject /
--       obVelY / obX / obY, and the obID at slots 46 (FloatingBlock) and 54.
-- Read-only. Gated to a frame window around SYZ1 f4431 (bk2_offset 61548).
--
-- PCs from docs/s1disasm/sonic.lst:
--   0xDCCE  DeleteObject:            (a0 = slot being cleared)
--   0xE11A  FindFreeObj:             (returns slot ptr in a1 + Z flag)
--   0xE130  FindNextFreeObj:
--   0x102E6 SolidObject move.b d0,standonobject(a1)  a1=v_player, d0=new slot
--   0x7B66  PlatformObject move.b d0,standonobject(a1) a1=v_player, d0=new slot
--
-- Frame mapping: SYZ1 bk2_frame_offset = 61548 -> trace f4431 = emuf 65979.

local V_OBJSPACE   = 0xFFD000
local PLAYER_ADDR  = 0xFFD000
local PB           = 0xD000     -- mainmemory window offset for v_player
local SLOT_SIZE    = 0x40
local OFF_ID       = 0x00
local OFF_X_POS    = 0x08
local OFF_Y_POS    = 0x0C
local OFF_X_VEL    = 0x10
local OFF_Y_VEL    = 0x12
local OFF_INERTIA  = 0x14
local OFF_STATUS   = 0x22
local OFF_ROUTINE  = 0x24
local OFF_ANGLE    = 0x26
local OFF_STAND    = 0x3D
local OFF_LOCKTIME = 0x3E

local PC_DELETEOBJ   = 0xDCCE
local PC_FINDFREE    = 0xE11A
local PC_FINDNEXT    = 0xE130
local PC_SOLID_SET   = 0x102E6
local PC_PLAT_SET    = 0x7B66

local BK2_OFFSET = 61548
local WIN_LO = 65940   -- ~ trace f4392
local WIN_HI = 66000   -- ~ trace f4452
local STOP_AT = tonumber(os.getenv("S1_STOP_AT_FRAME") or "66050")

local out = {}
local function log(s) table.insert(out, s) end
local function tf(ef) return ef - BK2_OFFSET end
local function reg(name) return (emu.getregister(name) or 0) % 0x1000000 end
local function in_win(ef) return ef >= WIN_LO and ef <= WIN_HI end

-- read obID at an absolute slot address (mainmemory window = addr & 0xFFFF)
local function slot_off(slotaddr) return slotaddr % 0x10000 end
local function id_at_addr(slotaddr)
    return mainmemory.read_u8(slot_off(slotaddr) + OFF_ID)
end
local function x_at_addr(slotaddr)
    return mainmemory.read_u16_be(slot_off(slotaddr) + OFF_X_POS)
end
local function y_at_addr(slotaddr)
    return mainmemory.read_u16_be(slot_off(slotaddr) + OFF_Y_POS)
end

local function slot_index_of(addr)
    return math.floor(((addr % 0x1000000) - V_OBJSPACE) / SLOT_SIZE)
end

local function player_snap()
    return string.format(
        "P[stand=%d status=0x%02X rtn=0x%02X ang=0x%02X lock=%d vY=0x%04X vX=0x%04X inert=0x%04X x=0x%04X y=0x%04X]",
        mainmemory.read_u8(PB + OFF_STAND),
        mainmemory.read_u8(PB + OFF_STATUS),
        mainmemory.read_u8(PB + OFF_ROUTINE),
        mainmemory.read_u8(PB + OFF_ANGLE),
        mainmemory.read_u16_be(PB + OFF_LOCKTIME),
        mainmemory.read_s16_be(PB + OFF_Y_VEL) % 0x10000,
        mainmemory.read_s16_be(PB + OFF_X_VEL) % 0x10000,
        mainmemory.read_s16_be(PB + OFF_INERTIA) % 0x10000,
        mainmemory.read_u16_be(PB + OFF_X_POS),
        mainmemory.read_u16_be(PB + OFF_Y_POS))
end

-- SlopeRepel entry: capture locktime/angle/inertia/status at the gate.
local PC_SLOPEREPEL = 0x13C1A
local PC_SLOPE_DETACH = 0x13C48
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a0 = reg("M68K A0")
    if a0 ~= PLAYER_ADDR then return end
    log(string.format("SLOPEREPEL emuf=%d (tf=%d) | %s", ef, tf(ef), player_snap()))
end, PC_SLOPEREPEL)
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a0 = reg("M68K A0")
    if a0 ~= PLAYER_ADDR then return end
    log(string.format("*** SLOPE-DETACH emuf=%d (tf=%d) | %s", ef, tf(ef), player_snap()))
end, PC_SLOPE_DETACH)
-- AnglePos in-air detach (floor >14px away): bset #1,obStatus at 0x14E3A
local PC_ANGLEPOS_DETACH = 0x14E3A
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a0 = reg("M68K A0")
    if a0 ~= PLAYER_ADDR then return end
    local d1 = emu.getregister("M68K D1") or 0
    log(string.format("*** ANGLEPOS-DETACH emuf=%d (tf=%d) d1(floordist)=%d | %s", ef, tf(ef), d1, player_snap()))
end, PC_ANGLEPOS_DETACH)

-- (A) SolidObject sets standonobject(v_player) = d0
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    if a1 ~= PLAYER_ADDR then return end
    local d0 = (emu.getregister("M68K D0") or 0) % 0x100
    local a0 = reg("M68K A0")  -- the solid object being processed
    local slotaddr = V_OBJSPACE + d0 * SLOT_SIZE
    log(string.format(
        "SOLID-SET emuf=%d (tf=%d) -> stand=slot%d obID=0x%02X @%04X,%04X | a0=slot%d(id0x%02X) | %s",
        ef, tf(ef), d0, id_at_addr(slotaddr), x_at_addr(slotaddr), y_at_addr(slotaddr),
        slot_index_of(a0), id_at_addr(a0), player_snap()))
end, PC_SOLID_SET)

-- (A) PlatformObject sets standonobject(v_player) = d0
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    if a1 ~= PLAYER_ADDR then return end
    local d0 = (emu.getregister("M68K D0") or 0) % 0x100
    local a0 = reg("M68K A0")
    local slotaddr = V_OBJSPACE + d0 * SLOT_SIZE
    log(string.format(
        "PLAT-SET  emuf=%d (tf=%d) -> stand=slot%d obID=0x%02X @%04X,%04X | a0=slot%d(id0x%02X) | %s",
        ef, tf(ef), d0, id_at_addr(slotaddr), x_at_addr(slotaddr), y_at_addr(slotaddr),
        slot_index_of(a0), id_at_addr(a0), player_snap()))
end, PC_PLAT_SET)

-- (B) DeleteObject: a0 = the slot being cleared
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a0 = reg("M68K A0")
    local s = slot_index_of(a0)
    if s < 0 or s > 0x7F then return end
    -- only log deletions of mid/high slots (the cluster of interest)
    log(string.format("DELETE    emuf=%d (tf=%d) slot%d obID=0x%02X @%04X,%04X",
        ef, tf(ef), s, id_at_addr(a0), x_at_addr(a0), y_at_addr(a0)))
end, PC_DELETEOBJ)

-- Per-frame end-of-frame snapshot of slots 46 + 54 + player.
local last_logged = -1
local function frame_snapshot(ef)
    if ef == last_logged then return end
    last_logged = ef
    local s46 = V_OBJSPACE + 46 * SLOT_SIZE
    local s54 = V_OBJSPACE + 54 * SLOT_SIZE
    log(string.format(
        "FRAME     emuf=%d (tf=%d) | s46 id0x%02X @%04X,%04X | s54 id0x%02X @%04X,%04X | %s",
        ef, tf(ef),
        id_at_addr(s46), x_at_addr(s46), y_at_addr(s46),
        id_at_addr(s54), x_at_addr(s54), y_at_addr(s54),
        player_snap()))
end

print(string.format("SYZ1 slot probe armed. Window emuf [%d,%d] (tf %d-%d). Stop %d.",
    WIN_LO, WIN_HI, tf(WIN_LO), tf(WIN_HI), STOP_AT))

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
    if in_win(f) then frame_snapshot(f) end
    if client.ispaused and client.ispaused() then client.unpause() end
    emu.frameadvance()
end

local path = os.getenv("OGGF_DIAG_OUT") or "syz1_slot_probe.txt"
local file = io.open(path, "w")
file:write("== SYZ1 standonobject/slot-54 probe (bk2_offset 61548, trace f4431 = emuf 65979) ==\n")
if #out == 0 then
    file:write("(no rows -- window/PCs need adjusting)\n")
end
for _, line in ipairs(out) do file:write(line .. "\n") end
file:close()
print("probe: wrote " .. #out .. " rows to " .. path)
if client ~= nil and client.exit then client.exit() end
