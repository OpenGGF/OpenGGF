-- Diagnostic: capture ROM sub_F61C return (d1 = wall distance) for the CPU
-- sidekick (Tails) at the Tails_InputAcceleration_Path wall probe.
--
-- Hook PC 0x14BB4 = the instruction after `bsr.w sub_F61C` in loc_14BA8
-- (sonic3k.asm). At that point D1 holds the signed wall distance the ROM just
-- computed; `tst.w d1 / bpl` means d1 >= 0 -> NO push, d1 < 0 -> push
-- (right-wall push at loc_14C00, sonic3k.asm).
--
-- This is COMPARISON-ONLY diagnostic capture: it reads ROM state and writes a
-- log file. It never writes engine/game state.
--
-- Output: tools/bizhawk/trace_output/tails_wallprobe.txt
-- Run via EmuHawk --lua this script --movie <bk2> <rom>.

local HOOK_PC       = 0x14BB4   -- after bsr.w sub_F61C in Tails wall path
local TAILS_BASE    = 0xB04A    -- Sidekick object (Player_2) work-RAM low addr
local OFF_X_POS     = 0x10
local OFF_X_SUB     = 0x12
local OFF_Y_POS     = 0x14
local OFF_Y_SUB     = 0x16
local OFF_X_VEL     = 0x18
local OFF_GROUND_VEL= 0x1C
local OFF_ANGLE     = 0x26
local OFF_STATUS    = 0x2A
local ADDR_ZONE     = 0xFE10
local ADDR_ACT      = 0xFE11
local ADDR_GFC      = 0xFE08    -- Level_frame_counter
local ADDR_CAMERA_X = 0xEE78

local FILTER_ZONE = tonumber(os.getenv("OGGF_DIAG_ZONE") or "")  -- nil = all zones
local STOP_FRAME  = tonumber(os.getenv("OGGF_DIAG_STOPFRAME") or "") -- nil = movie end

local out = {}
local function log(s) table.insert(out, s) end

local function s16(v)
    v = v % 0x10000
    if v >= 0x8000 then v = v - 0x10000 end
    return v
end

log("frame,gfc,zone,act,camx,a0,d0,d1_dist,tx_pos,tx_sub,tx_vel,ty_pos,angle,status,gvel,push")

event.onmemoryexecute(function()
    local a0 = (emu.getregister("M68K A0") or 0) % 0x10000
    local d0 = (emu.getregister("M68K D0") or 0) % 0x10000
    local d1 = s16(emu.getregister("M68K D1") or 0)
    local tx_pos = mainmemory.read_u16_be(a0 + OFF_X_POS)
    local tx_sub = mainmemory.read_u16_be(a0 + OFF_X_SUB)
    local ty_pos = mainmemory.read_u16_be(a0 + OFF_Y_POS)
    local tx_vel = s16(mainmemory.read_u16_be(a0 + OFF_X_VEL))
    local gvel   = s16(mainmemory.read_u16_be(a0 + OFF_GROUND_VEL))
    local angle  = mainmemory.read_u8(a0 + OFF_ANGLE)
    local status = mainmemory.read_u8(a0 + OFF_STATUS)
    local zone   = mainmemory.read_u8(ADDR_ZONE)
    local act    = mainmemory.read_u8(ADDR_ACT)
    local gfc    = mainmemory.read_u16_be(ADDR_GFC)
    local camx   = mainmemory.read_u16_be(ADDR_CAMERA_X)
    local push   = (d1 < 0) and 1 or 0
    if FILTER_ZONE ~= nil and zone ~= FILTER_ZONE then return end
    log(string.format(
        "%d,%d,%d,%d,0x%04X,0x%04X,0x%04X,%d,0x%04X,0x%04X,%d,0x%04X,0x%02X,0x%02X,%d,%d",
        emu.framecount(), gfc, zone, act, camx, a0, d0, d1,
        tx_pos, tx_sub, tx_vel, ty_pos, angle, status, gvel, push))
end, HOOK_PC)

print("diag_tails_wallprobe: hooking 0x" .. string.format("%05X", HOOK_PC))

local total = movie.length()
print("movie length = " .. tostring(total))

while true do
    local f = emu.framecount()
    if total ~= nil and total > 0 and f >= total then
        break
    end
    if STOP_FRAME ~= nil and f >= STOP_FRAME then
        break
    end
    -- Safety cap in case movie.length() is unavailable.
    if f > 60000 then break end
    emu.frameadvance()
end

local path = os.getenv("OGGF_DIAG_OUT") or "trace_output/tails_wallprobe.txt"
local file = io.open(path, "w")
for _, line in ipairs(out) do file:write(line .. "\n") end
file:close()
print("diag_tails_wallprobe: wrote " .. #out .. " probe rows to " .. path)
client.exit()
