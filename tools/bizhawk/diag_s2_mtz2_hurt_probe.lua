local START = tonumber(os.getenv("OGGF_START") or "0")
local STOP  = tonumber(os.getenv("OGGF_STOP")  or "0")
local OUT   = os.getenv("OGGF_OUT") or "tools/bizhawk/trace_output/s2_mtz2_hurt_probe.txt"

emu.limitframerate(false)
client.speedmode(6400)
client.invisibleemulation(true)

local outfile = io.open(OUT, "w")
local function log(s)
    print(s)
    if outfile then outfile:write(s .. "\n") end
end

local function u8(addr) return mainmemory.read_u8(addr) end
local function u16(addr) return mainmemory.read_u16_be(addr) end
local function s16(v)
    v = v % 0x10000
    if v >= 0x8000 then v = v - 0x10000 end
    return v
end
local function reg(name) return (emu.getregister(name) or 0) % 0x1000000 end
local function ram(addr24) return addr24 % 0x10000 end
local function slot(addr24)
    local low = ram(addr24)
    if low < 0xB000 then return -1 end
    return math.floor((low - 0xB000) / 0x40)
end

local FRAMECOUNT = 0xFE04
local MAIN = 0xB000

local function describeObject(addr24)
    local base = ram(addr24)
    return string.format(
        "slot=%d base=%06X id=%02X rtn=%02X col=%02X x=%04X y=%04X status=%02X map=%02X sub=%02X",
        slot(addr24), addr24, u8(base), u8(base + 0x24), u8(base + 0x20),
        u16(base + 0x08), u16(base + 0x0C), u8(base + 0x22),
        u8(base + 0x1A), u8(base + 0x28))
end

local function describePlayer(addr24)
    local base = ram(addr24)
    return string.format(
        "slot=%d base=%06X id=%02X rtn=%02X st=%02X x=%04X.%04X y=%04X.%04X xv=%04X(%d) yv=%04X(%d) g=%04X(%d) yr=%d xr=%d stand=%02X rings=%04X",
        slot(addr24), addr24, u8(base), u8(base + 0x24), u8(base + 0x22),
        u16(base + 0x08), u16(base + 0x0A), u16(base + 0x0C), u16(base + 0x0E),
        u16(base + 0x10), s16(u16(base + 0x10)), u16(base + 0x12), s16(u16(base + 0x12)),
        u16(base + 0x14), s16(u16(base + 0x14)), u8(base + 0x16), u8(base + 0x17),
        u8(base + 0x3D), u16(0xFE20))
end

local function dump(label)
    local f = emu.framecount()
    if f < START or f > STOP then return end
    local a0 = reg("M68K A0")
    local a1 = reg("M68K A1")
    local a2 = reg("M68K A2")
    local d0 = (emu.getregister("M68K D0") or 0) % 0x10000
    log(string.format(
        "f=%d vfc=%d %s pc=%06X d0=%04X a0=%s a1=%s a2=%s main=%s",
        f, u16(FRAMECOUNT), label, reg("M68K PC"), d0,
        describePlayer(a0), describeObject(a1), describeObject(a2), describePlayer(MAIN)))
end

event.onmemoryexecute(function() dump("Touch_Hurt_3F86E") end, 0x03F86E, "mtz2_touch_hurt")
event.onmemoryexecute(function() dump("HurtCharacter_3F878") end, 0x03F878, "mtz2_hurt_character")
event.onmemoryexecute(function() dump("Hurt_Reverse_3F8EE") end, 0x03F8EE, "mtz2_hurt_reverse")

local function sample()
    local f = emu.framecount()
    if f < START or f > STOP then return end
    log(string.format("f=%d vfc=%d SAMPLE main=%s", f, u16(FRAMECOUNT), describePlayer(MAIN)))
end

while true do
    if movie.isloaded() and movie.mode() == "FINISHED" then
        log("MOVIE FINISHED before STOP")
        if outfile then outfile:flush(); outfile:close() end
        client.exit()
        break
    end
    sample()
    if emu.framecount() > STOP then
        log("DIAG DONE")
        if outfile then outfile:flush(); outfile:close() end
        client.exit()
        break
    end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
