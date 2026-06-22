-- ============================================================================
-- FAST self-exiting BizHawk diag template. COPY this into your worktree and
-- fill in ONLY the two marked sections (RAM/register reads + optional PC hooks).
-- Do NOT hand-roll a diag lua from diag_s1_ledge_land.lua — that template has
-- `while true do emu.frameadvance() end` with NO client.exit() and NO speed
-- settings, so it runs at real-time, renders, and leaves EmuHawk hanging at
-- multiple GB. This template fixes all three.
--
-- Run:
--   OGGF_START=<firstCaptureFrame> OGGF_STOP=<lastCaptureFrame> \
--   OGGF_OUT=tools/bizhawk/trace_output/<unique>.txt \
--   "docs/BizHawk-2.11-win-x64/EmuHawk.exe" --chromeless \
--       --lua "tools/bizhawk/<your_copy>.lua" \
--       --movie "<bk2>" "<rom>.gen"
--
-- BizHawk frame for trace frame F = bk2_frame_offset (from metadata.json) + F.
-- ============================================================================

local START = tonumber(os.getenv("OGGF_START") or "0")
local STOP  = tonumber(os.getenv("OGGF_STOP")  or "0")
local OUT   = os.getenv("OGGF_OUT") or "tools/bizhawk/trace_output/diag.txt"

-- ---- FAST HEADLESS (this is what makes it ~100x faster + low memory) --------
emu.limitframerate(false)        -- remove the 60fps cap
client.speedmode(6400)           -- run at 6400%
client.invisibleemulation(true)  -- SKIP rendering: big speedup + low memory

local outfile = io.open(OUT, "w")
local function log(s)
    print(s)
    if outfile then outfile:write(s .. "\n") end
end

local function s16(v) v = v % 0x10000; if v >= 0x8000 then v = v - 0x10000 end; return v end

-- ============================================================================
-- USER SECTION 1 (optional): PC hooks. Use when you need register values at a
-- specific ROM instruction. Example:
--   local function at_hook()
--       local f = emu.framecount()
--       if f < START or f > STOP then return end
--       local d0 = s16(emu.getregister("M68K D0") or 0)
--       log(string.format("f=%d HOOK d0=%d", f, d0))
--   end
--   event.onmemoryexecute(at_hook, 0x00XXXX, "hook")
-- ============================================================================


-- ============================================================================
-- USER SECTION 2: per-frame RAM sampling in [START, STOP]. Fill in the reads
-- you need. S1 Sonic RAM = 0xD000 block (y_pos 0xD00C, y_sub 0xD00E, y_vel
-- 0xD012, x_pos 0xD008, ground_vel 0xD01C, status 0xD022, angle 0xD026).
-- S2/S3K player + object slots differ — read the addrs your capture needs.
-- ============================================================================
local function sample()
    local f = emu.framecount()
    if f < START or f > STOP then return end
    -- EXAMPLE (replace with your addresses):
    -- local yPos = mainmemory.read_u16_be(0xD00C)
    -- local yVel = s16(mainmemory.read_u16_be(0xD012))
    -- log(string.format("f=%d y=%04X yVel=%d", f, yPos, yVel))
end

-- ---- main loop: sample, then SELF-EXIT (fast + clean, never hangs) ----------
while true do
    if movie.isloaded() and movie.mode() == "FINISHED" then
        log("MOVIE FINISHED before STOP — exiting")
        if outfile then outfile:flush(); outfile:close() end
        client.exit()
        break
    end
    sample()
    if emu.framecount() > STOP then
        log("DIAG DONE — exiting")
        if outfile then outfile:flush(); outfile:close() end
        client.exit()
        break
    end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
