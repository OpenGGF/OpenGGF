-- Diagnostic probe (COMPARISON-ONLY, read-only): MZ3 f9917 slot-cadence.
-- Captures the ROM spawn/free/alloc timeline for slots 0x40-0x70 over the
-- window where the rings + Basarans near the player at f9917 spawn, to find
-- the first persistent slot-occupancy divergence vs the engine.
--
-- Hooks:
--   0xE10E OPL_MakeItem _move.b d0,obID(a1)  -> OPL spawn: slot=(a1), id=d0
--   0xDCCE DeleteObject                       -> free: slot=(a0), id
-- Plus per-frame change-log of obID at slots 64..110.
--
-- Frame mapping: MZ3 bk2_frame_offset = 43443 -> trace f9917 = emuf 53360.

local V_OBJSPACE = 0xFFD000
local PB_BASE    = 0xD000   -- mainmemory window offset for v_objspace (0xFFD000)
local SLOT_SIZE  = 0x40
local OFF_ID     = 0x00
local OFF_X      = 0x08
local OFF_Y      = 0x0C
local OFF_2NDR   = 0x25   -- ob2ndRout
local OFF_OFF36  = 0x36   -- bas_sonicY

local PC_OPL_SPAWN = 0xE10E
local PC_DELETE    = 0xDCCE

local BK2_OFFSET = 43443
local F_LO = 8600
local F_HI = 8665
local WIN_LO = BK2_OFFSET + F_LO
local WIN_HI = BK2_OFFSET + F_HI
local STOP_AT = WIN_HI + 4

local SLOT_FROM = 0
local SLOT_TO   = 110

local out = {}
local function log(s) table.insert(out, s) end
local function tf(ef) return ef - BK2_OFFSET end
local function reg(name) return (emu.getregister(name) or 0) % 0x1000000 end
local function in_win(ef) return ef >= WIN_LO and ef <= WIN_HI end
local function soff(addr) return (addr % 0x1000000) - V_OBJSPACE end
local function slot_of(addr) return math.floor(soff(addr) / SLOT_SIZE) end
local function base(slot) return PB_BASE + slot * SLOT_SIZE end
local function id_at(slot) return mainmemory.read_u8(base(slot) + OFF_ID) end
local function x_at(slot) return mainmemory.read_u16_be(base(slot) + OFF_X) end
local function y_at(slot) return mainmemory.read_u16_be(base(slot) + OFF_Y) end

event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a1 = reg("M68K A1")
    local s = slot_of(a1)
    local d0 = (emu.getregister("M68K D0") or 0) % 0x100
    log(string.format("  OPLSPAWN tf=%d slot%d id=0x%02X @%04X,%04X",
        tf(ef), s, d0, x_at(s), y_at(s)))
end, PC_OPL_SPAWN)

event.onmemoryexecute(function()
    local ef = emu.framecount()
    if not in_win(ef) then return end
    local a0 = reg("M68K A0")
    local s = slot_of(a0)
    if s < 1 or s > 0x7F then return end
    log(string.format("  DELETE   tf=%d slot%d id=0x%02X @%04X,%04X",
        tf(ef), s, id_at(s), x_at(s), y_at(s)))
end, PC_DELETE)

local prev = {}
for s = SLOT_FROM, SLOT_TO do prev[s] = -1 end

local function frame_changes(ef)
    for s = SLOT_FROM, SLOT_TO do
        local id = id_at(s)
        if id ~= prev[s] then
            log(string.format("CHG tf=%d slot%d : 0x%02X -> 0x%02X @%04X,%04X",
                tf(ef), s, (prev[s] < 0 and 0 or prev[s]), id, x_at(s), y_at(s)))
            prev[s] = id
        end
    end
end

local function full_snap(ef)
    log(string.format("== SNAP tf=%d ==", tf(ef)))
    for s = SLOT_FROM, SLOT_TO do
        local id = id_at(s)
        if id ~= 0 then
            log(string.format("   slot%d id=0x%02X @%04X,%04X r2=0x%02X off36=%04X",
                s, id, x_at(s), y_at(s),
                mainmemory.read_u8(base(s)+OFF_2NDR),
                mainmemory.read_u16_be(base(s)+OFF_OFF36)))
        end
    end
end

if client ~= nil then
    if emu.limitframerate then emu.limitframerate(false) end
    if client.invisibleemulation then client.invisibleemulation(true) end
end

print(string.format("MZ3 slot probe armed. Window emuf [%d,%d] (tf %d-%d).",
    WIN_LO, WIN_HI, F_LO, F_HI))

local total = movie.isloaded() and movie.length() or 0
local last = -1
while true do
    local f = emu.framecount()
    if STOP_AT > 0 and f >= STOP_AT then break end
    if total > 0 and f >= total then break end
    if client ~= nil and client.speedmode then
        if f < WIN_LO - 30 then client.speedmode(6400) else client.speedmode(100) end
    end
    if in_win(f) and f ~= last then
        last = f
        frame_changes(f)
        if tf(f) >= 8654 and tf(f) <= 8660 then full_snap(f) end
    end
    if client.ispaused and client.ispaused() then client.unpause() end
    emu.frameadvance()
end

local path = os.getenv("OGGF_DIAG_OUT") or "mz3_slot_probe.txt"
local file = io.open(path, "w")
file:write("== MZ3 slot probe (bk2_offset 43443, trace f9917 = emuf 53360) ==\n")
if #out == 0 then file:write("(no rows -- window/PCs need adjusting)\n") end
for _, line in ipairs(out) do file:write(line .. "\n") end
file:close()
print("probe: wrote " .. #out .. " rows to " .. path)
if client ~= nil and client.exit then client.exit() end
