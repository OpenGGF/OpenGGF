-- Round 69 ARZ2 Obj3E/Obj28 diagnostic.
-- Captures the slot $1B prison animal around trace f7060-f7100.

local START = tonumber(os.getenv("OGGF_START") or "0")
local STOP  = tonumber(os.getenv("OGGF_STOP")  or "0")
local OUT   = os.getenv("OGGF_OUT") or "tools/bizhawk/trace_output/diag_s2_arz2_round69_obj28_slot1b.txt"
local TRACE_OFFSET = tonumber(os.getenv("OGGF_TRACE_OFFSET") or "7998")

emu.limitframerate(false)
client.speedmode(6400)
client.invisibleemulation(true)
if client.SetSoundOn then
    pcall(client.SetSoundOn, false)
end

local outfile = io.open(OUT, "w")
local function log(s)
    print(s)
    if outfile then outfile:write(s .. "\n") end
end

local function s16(v) v = v % 0x10000; if v >= 0x8000 then v = v - 0x10000 end; return v end
local function u8(addr) return mainmemory.read_u8(addr) or 0 end
local function u16(addr) return mainmemory.read_u16_be(addr) or 0 end

local OBJ_TABLE = 0xB000
local SLOT_SIZE = 0x40
local ADDR_VFC = 0xFE04
local ADDR_VINT_LOW = 0xFE0E
local ADDR_RNG_HI = 0xF636
local ADDR_RNG_LO = 0xF638

local function slot_addr(slot) return OBJ_TABLE + slot * SLOT_SIZE end
local function slot_of(reg)
    return math.floor((((reg or 0) & 0xFFFF) - OBJ_TABLE) / SLOT_SIZE)
end

local function slot_line(label, slot)
    local a = slot_addr(slot)
    return string.format(
        "%s s%02X id=%02X rf=%02X rtn=%02X rs=%02X x=%04X.%04X y=%04X.%04X xv=%04X(%d) yv=%04X(%d) map=%02X afdur=%02X sub=%02X o29=%02X o30=%04X o32=%04X o34=%04X o36=%04X o38=%02X",
        label, slot, u8(a), u8(a + 0x01), u8(a + 0x24), u8(a + 0x25),
        u16(a + 0x08), u16(a + 0x0A), u16(a + 0x0C), u16(a + 0x0E),
        u16(a + 0x10), s16(u16(a + 0x10)), u16(a + 0x12), s16(u16(a + 0x12)),
        u8(a + 0x1A), u8(a + 0x1E), u8(a + 0x28), u8(a + 0x29),
        u16(a + 0x30), u16(a + 0x32), u16(a + 0x34), u16(a + 0x36), u8(a + 0x38))
end

local function context(label)
    local f = emu.framecount()
    if f < START or f > STOP then return end
    local trace = f - TRACE_OFFSET
    local pc = emu.getregister("M68K PC") or 0
    local a0 = emu.getregister("M68K A0") or 0
    local a1 = emu.getregister("M68K A1") or 0
    local d0 = emu.getregister("M68K D0") or 0
    local d1 = emu.getregister("M68K D1") or 0
    local a0slot = slot_of(a0)
    local a1slot = slot_of(a1)
    log(string.format(
        "bk2=%d trace=%d vfc=%04X vint=%04X pc=%06X %-18s a0=%04X/s%02X a1=%04X/s%02X d0=%04X(%d) d1=%04X(%d) rng=%04X%04X",
        f, trace, u16(ADDR_VFC), u16(ADDR_VINT_LOW), pc, label,
        a0 & 0xFFFF, a0slot, a1 & 0xFFFF, a1slot,
        d0 & 0xFFFF, s16(d0), d1 & 0xFFFF, s16(d1), u16(ADDR_RNG_HI), u16(ADDR_RNG_LO)))
    if a0slot >= 0x10 and a0slot <= 0x30 then log("  " .. slot_line("a0", a0slot)) end
    if a1slot >= 0x10 and a1slot <= 0x30 then log("  " .. slot_line("a1", a1slot)) end
    log("  " .. slot_line("slot1B", 0x1B))
    log("  " .. slot_line("slot1C", 0x1C))
end

event.onmemoryexecute(function() context("Obj28_Main") end, 0x011ADE, "r69_obj28_main")
event.onmemoryexecute(function() context("Obj28_Walk") end, 0x011B38, "r69_obj28_walk")
event.onmemoryexecute(function() context("Obj28_Fly") end, 0x011B74, "r69_obj28_fly")
event.onmemoryexecute(function() context("Obj28_Prison") end, 0x011BF4, "r69_obj28_prison")
event.onmemoryexecute(function() context("Obj3E_InitAnimals") end, 0x03F2FC, "r69_obj3e_initial")
event.onmemoryexecute(function() context("Obj3E_Random") end, 0x03F3A8, "r69_obj3e_random")
event.onmemoryexecute(function() context("AllocateObject") end, 0x017FDA, "r69_allocate")
event.onmemoryexecute(function() context("DeleteObject") end, 0x0164E4, "r69_delete")
event.onmemoryexecute(function() context("ObjectMoveFall") end, 0x016532, "r69_move_fall")
event.onmemoryexecute(function() context("ObjectMove") end, 0x01654E, "r69_move")
event.onmemoryexecute(function() context("ObjCheckFloor") end, 0x019C92, "r69_floor")

local last = ""
local function sample()
    local f = emu.framecount()
    if f < START or f > STOP then return end
    local trace = f - TRACE_OFFSET
    local line = string.format(
        "bk2=%d trace=%d vfc=%04X vint=%04X rng=%04X%04X | %s | %s | %s | %s | %s | %s | %s | %s | %s",
        f, trace, u16(ADDR_VFC), u16(ADDR_VINT_LOW), u16(ADDR_RNG_HI), u16(ADDR_RNG_LO),
        slot_line("s16", 0x16), slot_line("s17", 0x17), slot_line("s18", 0x18),
        slot_line("s19", 0x19), slot_line("s1A", 0x1A), slot_line("s1B", 0x1B),
        slot_line("s1C", 0x1C), slot_line("s1D", 0x1D), slot_line("s1E", 0x1E))
    if line ~= last then
        log(line)
        last = line
    end
end

while true do
    if movie.isloaded() and movie.mode() == "FINISHED" then
        log("MOVIE FINISHED before STOP - exiting")
        if outfile then outfile:flush(); outfile:close() end
        client.exit()
        break
    end
    sample()
    if emu.framecount() > STOP then
        log("DIAG DONE - exiting")
        if outfile then outfile:flush(); outfile:close() end
        client.exit()
        break
    end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
