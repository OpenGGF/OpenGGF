-- Temporary DEZ ObjC7 group-animation probe.
-- Trace frame F = bk2_frame_offset 2747 + F for s2/dez_ending.

local START = tonumber(os.getenv("OGGF_START") or "0")
local STOP  = tonumber(os.getenv("OGGF_STOP")  or "0")
local OUT   = os.getenv("OGGF_OUT") or "tools/bizhawk/trace_output/dez_objc7_group.txt"

emu.limitframerate(false)
client.speedmode(6400)
client.invisibleemulation(true)

local outfile = io.open(OUT, "w")
local function log(s)
    if outfile then outfile:write(s .. "\n") end
end

local function s16(v)
    v = v % 0x10000
    if v >= 0x8000 then v = v - 0x10000 end
    return v
end

local function u8(a) return mainmemory.read_u8(a) end
local function u16(a) return mainmemory.read_u16_be(a) end
local function s16m(a) return s16(u16(a)) end

local OBJ_BASE = 0xB000
local SLOT_SIZE = 0x40
local BODY_SLOT = 17
local HEAD_SLOT = 24
local BODY = OBJ_BASE + BODY_SLOT * SLOT_SIZE
local HEAD = OBJ_BASE + HEAD_SLOT * SLOT_SIZE
local BODY_A0 = 0xFF0000 + BODY

local BK2_OFFSET = 2747

local function slot_line(label, base)
    return string.format(
        "%s id=%02X x=%04X.%04X y=%04X.%04X xv=%04X yv=%04X rtn=%02X r2=%02X map=%02X anim=%02X af=%02X dur=%02X off1F=%02X prev=%02X st=%02X rf=%02X col=%02X cp=%02X",
        label,
        u8(base + 0x00),
        u16(base + 0x08), u16(base + 0x0A),
        u16(base + 0x0C), u16(base + 0x0E),
        u16(base + 0x10), u16(base + 0x12),
        u8(base + 0x24), u8(base + 0x25),
        u8(base + 0x22), u8(base + 0x1C),
        u8(base + 0x1B), u8(base + 0x1E),
        u8(base + 0x1F), u8(base + 0x1D),
        u8(base + 0x2A), u8(base + 0x04),
        u8(base + 0x20), u8(base + 0x21))
end

local function in_window()
    local f = emu.framecount()
    return f >= START and f <= STOP
end

local function trace_frame()
    return emu.framecount() - BK2_OFFSET
end

local function at_group_anim()
    if not in_window() then return end
    local a0 = emu.getregister("M68K A0") or 0
    if a0 ~= BODY_A0 then return end
    local a1 = emu.getregister("M68K A1") or 0
    log(string.format(
        "pc=3E1AA bk2=%d tf=%d A0=%06X A1=%06X %s %s",
        emu.framecount(), trace_frame(), a0, a1,
        slot_line("body", BODY), slot_line("head", HEAD)))
end

event.onmemoryexecute(at_group_anim, 0x03E1AA, "objc7_group_anim")

local function sample()
    if not in_window() then return end
    log(string.format(
        "sample bk2=%d tf=%d %s %s",
        emu.framecount(), trace_frame(),
        slot_line("body", BODY), slot_line("head", HEAD)))
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
