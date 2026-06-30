-- Diagnostic: identify the object/routine that negates Sonic's y_vel (rolling
-- bounce) at S1 MZ2 trace f2578 (emu ~31657). COMPARISON-ONLY: reads RAM, writes
-- a log file, never writes game state.
--
-- Window is emu.framecount(). MZ2 bk2_frame_offset=29079 so trace_frame N ~= emu
-- (29079 + N). Override via OGGF_DIAG_START/STOP. Output via OGGF_DIAG_OUT.

local OBJ_TABLE_START = 0xD000
local OBJ_SLOT_SIZE   = 0x40
local SONIC_BASE      = OBJ_TABLE_START

local OFF_ID      = 0x00
local OFF_RENDER  = 0x01
local OFF_X_POS   = 0x08
local OFF_X_SUB   = 0x0A
local OFF_Y_POS   = 0x0C
local OFF_X_VEL   = 0x10
local OFF_Y_VEL   = 0x12
local OFF_INERTIA = 0x14
local OFF_ANIM    = 0x1C
local OFF_STATUS  = 0x22
local OFF_ROUTINE = 0x24
local OFF_SUBTYPE = 0x28

local START_FRAME = tonumber(os.getenv("OGGF_DIAG_START") or "31650")
local STOP_FRAME  = tonumber(os.getenv("OGGF_DIAG_STOP")  or "31662")

local out = {}
local function log(s) table.insert(out, s) end
local function s16(v) v = v % 0x10000; if v >= 0x8000 then v = v - 0x10000 end; return v end
local function rd16(a) return mainmemory.read_u16_be(a) end
local function rd8(a) return mainmemory.read_u8(a) end

local function nearby(sx, sy)
    local parts = {}
    for slot = 1, 0x7F do
        local b = OBJ_TABLE_START + slot * OBJ_SLOT_SIZE
        local id = rd8(b + OFF_ID)
        if id ~= 0 then
            local ox, oy = rd16(b + OFF_X_POS), rd16(b + OFF_Y_POS)
            local dx, dy = s16((ox - sx) % 0x10000), s16((oy - sy) % 0x10000)
            if math.abs(dx) <= 0x40 and math.abs(dy) <= 0x40 then
                parts[#parts+1] = string.format(
                    "s%02X:id=0x%02X sub=0x%02X rtn=0x%02X @%04X,%04X dx=%d dy=%d rnd=0x%02X",
                    slot, id, rd8(b+OFF_SUBTYPE), rd8(b+OFF_ROUTINE), ox, oy, dx, dy, rd8(b+OFF_RENDER))
            end
        end
    end
    return table.concat(parts, " | ")
end

local function dump()
    local path = os.getenv("OGGF_DIAG_OUT")
        or "C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output/s1_bounce.txt"
    local f = io.open(path, "w")
    if not f then print("DIAG: cannot open " .. path); return end
    f:write(table.concat(out, "\n") .. "\n"); f:close()
    print("DIAG: wrote " .. path .. " (" .. #out .. " lines)")
end

print(string.format("S1 bounce diagnostic loaded. emu window [%d,%d].", START_FRAME, STOP_FRAME))
local written = false
while true do
    local f = emu.framecount()
    if f >= START_FRAME and f <= STOP_FRAME then
        local sx, sy = rd16(SONIC_BASE + OFF_X_POS), rd16(SONIC_BASE + OFF_Y_POS)
        log(string.format("f=%d SONIC x=%04X y=%04X yv=%d xv=%d in=%d anim=0x%02X st=0x%02X rtn=0x%02X",
            f, sx, sy, s16(rd16(SONIC_BASE+OFF_Y_VEL)), s16(rd16(SONIC_BASE+OFF_X_VEL)),
            s16(rd16(SONIC_BASE+OFF_INERTIA)), rd8(SONIC_BASE+OFF_ANIM), rd8(SONIC_BASE+OFF_STATUS),
            rd8(SONIC_BASE+OFF_ROUTINE)))
        log("  NEAR " .. nearby(sx, sy))
    end
    if f > STOP_FRAME and not written then written = true; dump(); client.exit(); break end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
