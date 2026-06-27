-- Diagnostic probe (COMPARISON-ONLY, read-only): SYZ3 f6358 slot-occupancy /
-- ObjPosLoad spawn-vs-free timeline, via BizHawk M68K PC-execute hooks.
--
-- GOAL: at trace f6358 the engine is wall-stopped 1px short (x=0x1B9E vs ROM
-- 0x1B9F) by a sliding switch-gated FloatingBlock (Obj0x56). ROM slot order is
-- Button(Obj0x32)=slot37 < FloatingBlock=slot38 (Button sets switch $F, block
-- reads it SAME frame); the ENGINE has FloatingBlock=slot37 < Button=slot40 -
-- INVERTED. This probe captures the ROM spawn/free->slot timeline to find the
-- FIRST object the engine places in a different slot than ROM, and why
-- (upstream occupancy cascade in slots 37-39).
--
-- Captured (all read-only):
--   (A) OPL spawn obID write (PC 0xE10E _move.b d0,obID(a1)): a1=new slot,
--       d0=obID, slot's obX, v_screenposx. -> ObjPosLoad layout spawns.
--   (B) FindFreeObj return (PC 0xE12E rts, a1=found slot) and FindNextFreeObj
--       return (PC 0xE14A rts, a1=found slot) -> the slot the scan chose
--       (covers child spawns too).
--   (C) DeleteObject (PC 0xDCCE, a0=slot freed): slot + its current obID/X.
--   (D) per-frame occupancy snapshot of obID for slots 30..47, logged only
--       when the occupancy changes from the previous frame.
--
-- ROM PCs from docs/s1disasm/sonic.lst:
--   0xE10E  OPL_MakeItem .no_respawn_bit  _move.b d0,obID(a1)  (a1=slot,d0=id)
--   0xE11A  FindFreeObj entry      0xE12E  FFree_Found rts (a1=slot)
--   0xE130  FindNextFreeObj entry  0xE14A  NFree_Found rts (a1=slot)
--   0xDCCE  DeleteObject           (a0=slot being cleared)
-- RAM (docs/s1disasm/_Variables.asm + sonic.lst):
--   v_objspace(player)=0xFFD000  v_lvlobjspace(slot32)=0xFFD800
--   v_screenposx=0xFFF700  v_framecount=0xFFFE04  v_objstate=0xFFFC00
--
-- Frame mapping: SYZ3 bk2_frame_offset = 79731 -> trace f6358 = emuf 86089.

local V_OBJSPACE = 0xFFD000
local SLOT_SIZE  = 0x40
local OFF_ID     = 0x00
local OFF_X_POS  = 0x08
local OFF_Y_POS  = 0x0C
local MM_SCREENX = 0xF700   -- v_screenposx low 16 bits
local MM_FRAMECT = 0xFE04   -- v_framecount

local PC_OBID        = 0xE10E
local PC_FFREE_FOUND = 0xE12E
local PC_NFREE_FOUND = 0xE14A
local PC_DELETE      = 0xDCCE

local BK2_OFFSET = 79731
local WIN_LO = BK2_OFFSET + 5000   -- trace f5000
local WIN_HI = BK2_OFFSET + 6360   -- trace f6360
local STOP_AT = tonumber(os.getenv("S1_STOP_AT_FRAME") or tostring(WIN_HI + 5))

local SNAP_LO_SLOT = 30
local SNAP_HI_SLOT = 47

local out = {}
local function log(s) table.insert(out, s) end
local function tf(ef) return ef - BK2_OFFSET end
local function reg(name) return (emu.getregister(name) or 0) % 0x1000000 end
local function in_win(ef) return ef >= WIN_LO and ef <= WIN_HI end
local function slot_off(slotaddr) return slotaddr % 0x10000 end
local function slot_index_of(addr) return math.floor(((addr % 0x1000000) - V_OBJSPACE) / SLOT_SIZE) end
local function id_at_slot(s)  return mainmemory.read_u8(slot_off(V_OBJSPACE + s * SLOT_SIZE) + OFF_ID) end
local function x_at_slot(s)   return mainmemory.read_u16_be(slot_off(V_OBJSPACE + s * SLOT_SIZE) + OFF_X_POS) end
local function y_at_slot(s)   return mainmemory.read_u16_be(slot_off(V_OBJSPACE + s * SLOT_SIZE) + OFF_Y_POS) end
local function id_at_addr(a)  return mainmemory.read_u8(slot_off(a) + OFF_ID) end
local function x_at_addr(a)   return mainmemory.read_u16_be(slot_off(a) + OFF_X_POS) end
local function screenx()      return mainmemory.read_u16_be(MM_SCREENX) end

-- (A) OPL spawn obID write: a1=new slot, d0=obID
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    local s = slot_index_of(a1)
    local d0 = (emu.getregister("M68K D0") or 0) % 0x100
    log(string.format("OPLSPAWN  emuf=%d tf=%d slot=%d id=0x%02X x=0x%04X scrx=0x%04X",
        ef, tf(ef), s, d0, x_at_addr(a1), screenx()))
end, PC_OBID)

-- (B) FindFreeObj return: a1=found slot (valid only if obID still 0 & in range)
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    local s = slot_index_of(a1)
    if s < 0 or s > 0x7F then return end
    log(string.format("FFREE-RET emuf=%d tf=%d slot=%d (curId=0x%02X) scrx=0x%04X",
        ef, tf(ef), s, id_at_addr(a1), screenx()))
end, PC_FFREE_FOUND)

event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    local s = slot_index_of(a1)
    if s < 0 or s > 0x7F then return end
    log(string.format("NFREE-RET emuf=%d tf=%d slot=%d (curId=0x%02X) scrx=0x%04X",
        ef, tf(ef), s, id_at_addr(a1), screenx()))
end, PC_NFREE_FOUND)

-- (C) DeleteObject: a0 = slot being cleared
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a0 = reg("M68K A0")
    local s = slot_index_of(a0)
    if s < 0 or s > 0x7F then return end
    log(string.format("DELETE    emuf=%d tf=%d slot=%d id=0x%02X x=0x%04X",
        ef, tf(ef), s, id_at_addr(a0), x_at_addr(a0)))
end, PC_DELETE)

-- (D) per-frame occupancy snapshot of slots 30..47, on-change only
local last_snap = nil
local function occ_string()
    local parts = {}
    for s = SNAP_LO_SLOT, SNAP_HI_SLOT do
        local id = id_at_slot(s)
        if id ~= 0 then
            table.insert(parts, string.format("%d=0x%02X@%04X", s, id, x_at_slot(s)))
        end
    end
    return table.concat(parts, " ")
end
local function frame_snapshot(ef)
    local occ = occ_string()
    if occ ~= last_snap then
        last_snap = occ
        log(string.format("OCC       emuf=%d tf=%d scrx=0x%04X | %s", ef, tf(ef), screenx(), occ))
    end
end

print(string.format("SYZ3 slot probe armed. Window emuf [%d,%d] (tf %d-%d). Stop %d.",
    WIN_LO, WIN_HI, tf(WIN_LO), tf(WIN_HI), STOP_AT))

if client ~= nil then
    if emu.limitframerate then emu.limitframerate(false) end
    if client.invisibleemulation then client.invisibleemulation(true) end
end

local total = movie.isloaded() and movie.length() or 0
local turbo = true
if client and client.speedmode then client.speedmode(6400) end
while true do
    local f = emu.framecount()
    if STOP_AT > 0 and f >= STOP_AT then break end
    if total > 0 and f >= total then break end
    if turbo and f >= WIN_LO - 2 then
        turbo = false
        if client and client.speedmode then client.speedmode(100) end
    end
    if in_win(f) then frame_snapshot(f) end
    if client.ispaused and client.ispaused() then client.unpause() end
    emu.frameadvance()
end

local path = os.getenv("OGGF_DIAG_OUT") or "syz3_slot_probe.txt"
local file = io.open(path, "w")
file:write("== SYZ3 slot-occupancy/ObjPosLoad probe (bk2_offset 79731, trace f6358 = emuf 86089) ==\n")
if #out == 0 then file:write("(no rows -- window/PCs need adjusting)\n") end
for _, line in ipairs(out) do file:write(line .. "\n") end
file:close()
print("probe: wrote " .. #out .. " rows to " .. path)
if client ~= nil and client.exit then client.exit() end
