-- Diagnostic (COMPARISON-ONLY, read-only): trace the AIZ collapsing platform
-- on-object/standing-bit interaction with Player_1 around the jump frame.
-- The trace first-divergence is at trace-frame 0xCF5 (3317): ROM keeps
-- Status_OnObj on the jump frame (status 0x0E) and clears it the NEXT frame
-- (0x06), while the engine clears it on the jump frame.
--
-- We can't key on Level_frame_counter (recorder uses a relative trace_frame),
-- so we trigger on the player's stable jump x_sub value (0x4C00) which the
-- physics.csv shows for frames 0xCF4/0xCF5. We log, in execution order:
--   * loc_1E338 (0x1E338): SolidObjectTopSloped2_1P UNSEAT branch fires
--   * loc_205A6 (0x205A6): the platform main routine runs
--   * a periodic Player_1 status snapshot tagged with x_sub
--
-- Read-only: never writes game/emulator state.

local P1_BASE       = 0xFFB000   -- 68k RAM mirror; Player_1 work RAM
local OFF_X_POS     = 0x10
local OFF_X_SUB     = 0x12
local OFF_Y_POS     = 0x14
local OFF_Y_VEL     = 0x1A
local OFF_STATUS    = 0x2A

local HOOK_UNSEAT   = 0x1E338
local HOOK_PLAT_RUN = 0x205A6
local HOOK_JUMP_INAIR = 0x1184C  -- Sonic_Jump bset Status_InAir region (approx)

-- Player_1 work RAM is at $FFFFB000; mainmemory for 68k is the 64KB work RAM
-- window, so read at 0xB000.
local PB = 0xB000

local out = {}
local function log(s) table.insert(out, s) end

local function xsub() return mainmemory.read_u16_be(PB + OFF_X_SUB) end
local function pstatus() return mainmemory.read_u8(PB + OFF_STATUS) end

-- Trigger window: player x_sub at the jump (0x4C00) per physics.csv 0xCF4/0xCF5.
-- Allow a small band so we catch a couple of frames either side.
local armed = false
local seen_count = 0
local function xpos() return mainmemory.read_u16_be(PB + OFF_X_POS) end
-- Specific to the divergence: physics.csv 0xCF4/0xCF5 show x=0x2487 x_sub=0x4C00
-- with Status_OnObj set. Trigger only there.
local function in_jump_window()
    return xpos() == 0x2487 and xsub() == 0x4C00
end

log("== execution-order trace around AIZ collapse jump (player x_sub ~0x4C00) ==")

-- Capture every unseat for objects in the AIZ collapsing-platform RAM band
-- (roughly 0xB100..0xB400) during the emuf window, regardless of player x.
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if ef < 3820 or ef > 3835 then return end
    local a0 = (emu.getregister("M68K A0") or 0) % 0x10000
    if a0 < 0xB100 or a0 > 0xB400 then return end
    local a1 = (emu.getregister("M68K A1") or 0) % 0x10000
    local pre = mainmemory.read_u8(a1 + OFF_STATUS)
    log(string.format("  UNSEAT(loc_1E338) emuf=%d a0=0x%04X a1=0x%04X p_status_pre=0x%02X p1_xsub=0x%04X",
        ef, a0, a1, pre, xsub()))
end, HOOK_UNSEAT)

-- Capture the collapse-release sub_205FC bclr Status_OnObj (loc_205DE path).
event.onmemoryexecute(function()
    local ef = emu.framecount()
    if ef < 3820 or ef > 3835 then return end
    local a0 = (emu.getregister("M68K A0") or 0) % 0x10000
    local a1 = (emu.getregister("M68K A1") or 0) % 0x10000
    local pre = mainmemory.read_u8(a1 + OFF_STATUS)
    log(string.format("  RELEASE(sub_205FC) emuf=%d a0=0x%04X a1=0x%04X p_status_pre=0x%02X",
        ef, a0, a1, pre))
end, 0x205F8)

local function plat_log(label)
    return function()
        local ef = emu.framecount()
        if ef < 3820 or ef > 3835 then return end
        local a0 = (emu.getregister("M68K A0") or 0) % 0x10000
        local objstatus = mainmemory.read_u8(a0 + OFF_STATUS)
        local trig = mainmemory.read_u8(a0 + 0x3A)
        local timer = mainmemory.read_u8(a0 + 0x38)
        log(string.format("  %s emuf=%d a0=0x%04X obj_status=0x%02X $3A=0x%02X $38=0x%02X p1_status=0x%02X p1_xsub=0x%04X",
            label, ef, a0, objstatus, trig, timer, pstatus(), xsub()))
    end
end
event.onmemoryexecute(plat_log("PLATRUN(loc_205A6)"), HOOK_PLAT_RUN)
event.onmemoryexecute(plat_log("SOLIDSTAY(loc_205DE)"), 0x205DE)

print("diag: trigger on player x_sub in [0x4A00,0x4E00]")

local total = movie.length()
while true do
    local f = emu.framecount()
    if total ~= nil and total > 0 and f >= total then break end
    if f >= 3820 and f <= 3836 then
        log(string.format("FRAME-START emuf=%d p1_status=0x%02X xpos=0x%04X xsub=0x%04X y=0x%04X yvel=0x%04X",
            f, pstatus(), xpos(), xsub(),
            mainmemory.read_u16_be(PB + OFF_Y_POS),
            mainmemory.read_u16_be(PB + OFF_Y_VEL)))
    end
    if f > 3836 then break end
    if f > 60000 then break end
    emu.frameadvance()
end

local path = os.getenv("OGGF_DIAG_OUT") or "trace_output/aiz_collapse_onobj.txt"
local file = io.open(path, "w")
for _, line in ipairs(out) do file:write(line .. "\n") end
file:close()
print("diag: wrote " .. #out .. " rows to " .. path)
client.exit()
