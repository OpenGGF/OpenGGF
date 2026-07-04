-- Probe: S2 MTZ3 f15295-15330 control-lock / move_lock / logical-input state.
-- Copied from diag_template_fast.lua; USER sections filled for the capsule-area
-- input-response divergence (trace f15309: ROM ignores 3 frames of right press).

local START = tonumber(os.getenv("OGGF_START") or "0")
local STOP  = tonumber(os.getenv("OGGF_STOP")  or "0")
local OUT   = os.getenv("OGGF_OUT") or "tools/bizhawk/trace_output/diag.txt"

-- ---- FAST HEADLESS ----------------------------------------------------------
emu.limitframerate(false)        -- remove the 60fps cap
client.speedmode(6400)           -- run at 6400%
client.invisibleemulation(true)  -- SKIP rendering: big speedup + low memory
if client.SetSoundOn then
    pcall(client.SetSoundOn, false)
end

local outfile = io.open(OUT, "w")
local function log(s)
    print(s)
    if outfile then outfile:write(s .. "\n") end
end

local function s16(v) v = v % 0x10000; if v >= 0x8000 then v = v - 0x10000 end; return v end

-- ---- per-frame RAM sampling in [START, STOP] --------------------------------
local function sample()
    local f = emu.framecount()
    if f < START or f > STOP then return end
    local ctrl1     = mainmemory.read_u16_be(0xF604) -- Ctrl_1 held|press
    local logical   = mainmemory.read_u16_be(0xF602) -- Ctrl_1_Logical
    local ctrlLock  = mainmemory.read_u8(0xF7CC)     -- Control_Locked
    local moveLock  = mainmemory.read_u16_be(0xB02E) -- Sonic move_lock
    local objCtrl   = mainmemory.read_u8(0xB02A)     -- Sonic obj_control
    local inertia   = s16(mainmemory.read_u16_be(0xB014))
    local xLong     = mainmemory.read_u32_be(0xB008) -- x_pos.x_sub
    local status    = mainmemory.read_u8(0xB022)
    local status2   = mainmemory.read_u8(0xB02B)
    local anim      = mainmemory.read_u8(0xB01C)
    log(string.format("f=%d ctrl1=%04X logi=%04X lock=%02X mvlk=%04X objc=%02X in=%d x=%08X st=%02X st2=%02X anim=%02X",
        f, ctrl1, logical, ctrlLock, moveLock, objCtrl, inertia, xLong, status, status2, anim))
end

-- ---- main loop: sample, then SELF-EXIT ---------------------------------------
while true do
    if movie.isloaded() and movie.mode() == "FINISHED" then
        log("MOVIE FINISHED before STOP -- exiting")
        if outfile then outfile:flush(); outfile:close() end
        client.exit()
        break
    end
    sample()
    if emu.framecount() > STOP then
        log("DIAG DONE -- exiting")
        if outfile then outfile:flush(); outfile:close() end
        client.exit()
        break
    end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
