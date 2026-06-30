-- Diagnostic: hook writes to Sonic's y_pos (work RAM 0xFFD00C) to see, mid-frame,
-- which PC writes Sonic's Y during the GHZ collapsing-ledge landing and what value.
-- Resolves whether the ledge's SlopeObject lands Sonic at the same frame the
-- engine does, and what position it evaluates. COMPARISON-ONLY (reads + logs).
--
-- Window in emu.framecount(). GHZ1 offset 788 -> trace f2573 ~= emu 3361.
-- Output: tools/bizhawk/trace_output/s1_ypos_writes.txt

local YPOS_ADDR = 0xFFD00C        -- 68K system-bus address of Sonic y_pos (word)
local XPOS_BUS  = 0xFFD008        -- Sonic x_pos
local START_FRAME = tonumber(os.getenv("OGGF_DIAG_START") or "3358")
local STOP_FRAME  = tonumber(os.getenv("OGGF_DIAG_STOP")  or "3365")

local out = {}
local function log(s) table.insert(out, s) end

local function in_window()
    local f = emu.framecount()
    return f >= START_FRAME and f <= STOP_FRAME
end

-- mainmemory base for Sonic (work RAM offset)
local function rd16(off) return mainmemory.read_u16_be(off) end

local function on_ypos_write()
    if not in_window() then return end
    local pc = emu.getregister("M68K PC") or 0
    -- y_pos is mid-write; read current mainmemory snapshot (best-effort)
    local y = mainmemory.read_u16_be(0xD00C)
    local x = mainmemory.read_u16_be(0xD008)
    local yv = mainmemory.read_u16_be(0xD012)
    log(string.format("f=%d WRITE y_pos pc=0x%06X y=%04X x=%04X yvel=%04X",
        emu.framecount(), pc % 0x1000000, y, x, yv))
end

local function dump()
    local path = os.getenv("OGGF_DIAG_OUT")
        or "C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output/s1_ypos_writes.txt"
    local f = io.open(path, "w")
    if not f then print("DIAG: cannot open " .. path); return end
    f:write(table.concat(out, "\n") .. "\n"); f:close()
    print("DIAG: wrote " .. path .. " (" .. #out .. " lines)")
end

-- Try to register a write hook; fall back to per-frame end snapshot if unsupported.
local hooked = false
if event and event.onmemorywrite then
    local ok = pcall(function()
        event.onmemorywrite(on_ypos_write, YPOS_ADDR)
        event.onmemorywrite(on_ypos_write, YPOS_ADDR + 1)
    end)
    hooked = ok
end
print("S1 y_pos write diagnostic loaded. hooked=" .. tostring(hooked)
    .. " window [" .. START_FRAME .. "," .. STOP_FRAME .. "]")

local written = false
while true do
    if in_window() then
        log(string.format("f=%d FRAME-END y=%04X x=%04X yvel=%04X st=%02X",
            emu.framecount(), rd16(0xD00C), rd16(0xD008), rd16(0xD012),
            mainmemory.read_u8(0xD022)))
    end
    if emu.framecount() > STOP_FRAME and not written then
        written = true; dump(); client.exit(); break
    end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
