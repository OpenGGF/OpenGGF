-- Diagnostic probe (COMPARISON-ONLY, read-only): SBZ2 f6839 co-located ObjPosLoad
-- spawn-order / slot-occupancy, via BizHawk M68K PC-execute hooks.
--
-- GOAL: at trace f6839 the @158D Bomb (id 0x5F) is at ROM slot 0x44 (68) but
-- engine slot 0x43 (67) -- one slot low (field obj_s44_slot exp 0x44 act 0x43).
-- Hypothesis: ROM spawns a co-located object (saw/conveyor) in the SAME
-- ObjPosLoad pass at x=0x1380 (consecutive slots), pushing the bomb up by one;
-- the engine defers that co-located object a frame, so the bomb takes its slot.
-- This probe captures the ROM spawn->slot timeline at the x~0x1380-0x1600 column
-- load to find the EXACT layout-table order ROM uses for same-X objects.
--
-- Captured (all read-only):
--   (A) OPL spawn obID write (PC 0xE10E): a1=new slot, d0=obID, slot's obX, scrx
--   (B) FindFreeObj return  (PC 0xE12E rts, a1=found slot)
--   (C) FindNextFreeObj ret (PC 0xE14A rts, a1=found slot)
--   (D) DeleteObject (PC 0xDCCE, a0=slot freed): slot + obID + X
--   (E) per-frame occupancy snapshot of obID for slots 60..90, on-change only
--
-- ROM PCs (docs/s1disasm/sonic.lst):
--   0xE10E  OPL_MakeItem _move.b d0,obID(a1)  (a1=slot,d0=id)
--   0xE12E  FFree_Found rts (a1=slot)   0xE14A NFree_Found rts (a1=slot)
--   0xDCCE  DeleteObject (a0=slot)
-- RAM: v_objspace=0xFFD000  v_screenposx=0xFFF700  v_framecount=0xFFFE04
--
-- Frame mapping: SBZ2 bk2_frame_offset = 171193 -> trace f6839 = emuf 178032.

local V_OBJSPACE = 0xFFD000
local SLOT_SIZE  = 0x40
local OFF_ID     = 0x00
local OFF_X_POS  = 0x08
local OFF_Y_POS  = 0x0C
local MM_SCREENX = 0xF700

local PC_OBID        = 0xE10E
local PC_FFREE_FOUND = 0xE12E
local PC_NFREE_FOUND = 0xE14A
local PC_DELETE      = 0xDCCE

local BK2_OFFSET = 171193
local WIN_LO = BK2_OFFSET + 6000
local WIN_HI = BK2_OFFSET + 6850
local STOP_AT = tonumber(os.getenv("S1_STOP_AT_FRAME") or tostring(WIN_HI + 5))

local SNAP_LO_SLOT = 60
local SNAP_HI_SLOT = 90

local out = {}
local function log(s) table.insert(out, s) end
local function tf(ef) return ef - BK2_OFFSET end
local function reg(name) return (emu.getregister(name) or 0) % 0x1000000 end
local function in_win(ef) return ef >= WIN_LO and ef <= WIN_HI end
local function slot_off(a) return a % 0x10000 end
local function slot_index_of(addr) return math.floor(((addr % 0x1000000) - V_OBJSPACE) / SLOT_SIZE) end
local function id_at_slot(s)  return mainmemory.read_u8(slot_off(V_OBJSPACE + s * SLOT_SIZE) + OFF_ID) end
local function x_at_slot(s)   return mainmemory.read_u16_be(slot_off(V_OBJSPACE + s * SLOT_SIZE) + OFF_X_POS) end
local function y_at_slot(s)   return mainmemory.read_u16_be(slot_off(V_OBJSPACE + s * SLOT_SIZE) + OFF_Y_POS) end
local function id_at_addr(a)  return mainmemory.read_u8(slot_off(a) + OFF_ID) end
local function x_at_addr(a)   return mainmemory.read_u16_be(slot_off(a) + OFF_X_POS) end
local function y_at_addr(a)   return mainmemory.read_u16_be(slot_off(a) + OFF_Y_POS) end
local function screenx()      return mainmemory.read_u16_be(MM_SCREENX) end

event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    local s = slot_index_of(a1)
    local d0 = (emu.getregister("M68K D0") or 0) % 0x100
    log(string.format("OPLSPAWN  emuf=%d tf=%d slot=%d(0x%02X) id=0x%02X x=0x%04X y=0x%04X scrx=0x%04X",
        ef, tf(ef), s, s, d0, x_at_addr(a1), y_at_addr(a1), screenx()))
end, PC_OBID)

event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    local s = slot_index_of(a1)
    if s < 0 or s > 0x7F then return end
    log(string.format("FFREE-RET emuf=%d tf=%d slot=%d(0x%02X) (curId=0x%02X) scrx=0x%04X",
        ef, tf(ef), s, s, id_at_addr(a1), screenx()))
end, PC_FFREE_FOUND)

event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    local s = slot_index_of(a1)
    if s < 0 or s > 0x7F then return end
    log(string.format("NFREE-RET emuf=%d tf=%d slot=%d(0x%02X) (curId=0x%02X) scrx=0x%04X",
        ef, tf(ef), s, s, id_at_addr(a1), screenx()))
end, PC_NFREE_FOUND)

event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a0 = reg("M68K A0")
    local s = slot_index_of(a0)
    if s < 0 or s > 0x7F then return end
    log(string.format("DELETE    emuf=%d tf=%d slot=%d(0x%02X) id=0x%02X x=0x%04X",
        ef, tf(ef), s, s, id_at_addr(a0), x_at_addr(a0)))
end, PC_DELETE)

local last_snap = nil
local function occ_string()
    local parts = {}
    for s = SNAP_LO_SLOT, SNAP_HI_SLOT do
        local id = id_at_slot(s)
        if id ~= 0 then
            table.insert(parts, string.format("%d(0x%02X)=0x%02X@%04X", s, s, id, x_at_slot(s)))
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

print(string.format("SBZ2 slot probe armed. Window emuf [%d,%d] (tf %d-%d). Stop %d.",
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

local path = os.getenv("OGGF_DIAG_OUT") or "sbz2_slot_probe.txt"
local file = io.open(path, "w")
file:write("== SBZ2 slot-occupancy/ObjPosLoad probe (bk2_offset 171193, trace f6839 = emuf 178032) ==\n")
if #out == 0 then file:write("(no rows -- window/PCs need adjusting)\n") end
for _, line in ipairs(out) do file:write(line .. "\n") end
file:close()
print("probe: wrote " .. #out .. " rows to " .. path)
if client ~= nil and client.exit then client.exit() end
