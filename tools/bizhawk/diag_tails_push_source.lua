-- Diagnostic: identify which ROM routine sets/clears Status_Push on the CPU
-- sidekick (Tails) around the AIZ f4234 divergence.
--
-- COMPARISON-ONLY: reads ROM state, writes a log file. Never writes game state.
--
-- Hooks (sonic3k.asm addresses == ROM PC):
--   0x1E06E loc_1E06E  SolidObject side-contact push block (bset Status_Push,status(a1))
--   0x1E0C2 sub_1E0C2  SolidObject push clear (bclr Status_Push,status(a1))
--   0x14C00 loc_14C00  Tails_InputAcceleration_Path right-wall push
--   0x113D6 loc_113D6  Sonic_Move right-wall push
-- Also logs per-frame Tails SST at end of frame.
--
-- Output: tools/bizhawk/trace_output/tails_push_source.txt

local TAILS_BASE     = 0xB04A
local OFF_X_POS      = 0x10
local OFF_X_SUB      = 0x12
local OFF_GROUND_VEL = 0x1C
local OFF_ANGLE      = 0x26
local OFF_STATUS     = 0x2A
local OFF_OBJCTRL    = 0x2E   -- object_control (approx; not critical)
local ADDR_ZONE      = 0xFE10
local ADDR_GFC       = 0xFE08

local START_FRAME = tonumber(os.getenv("OGGF_DIAG_START") or "4720")
local STOP_FRAME  = tonumber(os.getenv("OGGF_DIAG_STOP")  or "4760")

local out = {}
local function log(s) table.insert(out, s) end

local function s16(v)
    v = v % 0x10000
    if v >= 0x8000 then v = v - 0x10000 end
    return v
end

local function in_window()
    local f = emu.framecount()
    return f >= START_FRAME and f <= STOP_FRAME
end

local function hook_push(label)
    return function()
        if not in_window() then return end
        local a0 = (emu.getregister("M68K A0") or 0) % 0x10000
        local a1 = (emu.getregister("M68K A1") or 0) % 0x10000
        local d1 = s16(emu.getregister("M68K D1") or 0)
        local t_status = mainmemory.read_u8(TAILS_BASE + OFF_STATUS)
        local t_gv = s16(mainmemory.read_u16_be(TAILS_BASE + OFF_GROUND_VEL))
        local zone = mainmemory.read_u8(ADDR_ZONE)
        local obj_id = mainmemory.read_u8(a0 + 0x00)
        local obj_subtype = mainmemory.read_u8(a0 + 0x28)
        local obj_x = mainmemory.read_u16_be(a0 + OFF_X_POS)
        local obj_y = mainmemory.read_u16_be(a0 + 0x14)
        log(string.format("HOOK f=%d zone=%d %s a0=0x%04X objId=0x%02X objSub=0x%02X obj@=0x%04X,0x%04X a1=0x%04X d1=%d tails_status=0x%02X tails_gv=%d isTailsA1=%s",
            emu.framecount(), zone, label, a0, obj_id, obj_subtype, obj_x, obj_y, a1, d1, t_status, t_gv,
            tostring(a1 == TAILS_BASE)))
    end
end

event.onmemoryexecute(hook_push("SolidPush_1E06E"), 0x1E06E)
event.onmemoryexecute(hook_push("SolidClear_1E0C2"), 0x1E0C2)
event.onmemoryexecute(hook_push("PathRightWall_14C00"), 0x14C00)
event.onmemoryexecute(hook_push("PathOtherWall_14BF4"), 0x14BF4)
event.onmemoryexecute(hook_push("SonicMoveWall_113D6"), 0x113D6)

print("diag_tails_push_source: hooks installed; window=" .. START_FRAME .. ".." .. STOP_FRAME)

local total = movie.length()
while true do
    local f = emu.framecount()
    if total ~= nil and total > 0 and f >= total then break end
    if f > STOP_FRAME + 2 then break end
    -- end-of-frame Tails SST snapshot
    if in_window() then
        local zone = mainmemory.read_u8(ADDR_ZONE)
        log(string.format("ENDFRAME f=%d zone=%d tails_x=0x%04X tails_xsub=0x%04X tails_gv=%d tails_angle=0x%02X tails_status=0x%02X gfc=0x%04X",
            f, zone,
            mainmemory.read_u16_be(TAILS_BASE + OFF_X_POS),
            mainmemory.read_u16_be(TAILS_BASE + OFF_X_SUB),
            s16(mainmemory.read_u16_be(TAILS_BASE + OFF_GROUND_VEL)),
            mainmemory.read_u8(TAILS_BASE + OFF_ANGLE),
            mainmemory.read_u8(TAILS_BASE + OFF_STATUS),
            mainmemory.read_u16_be(ADDR_GFC)))
    end
    emu.frameadvance()
end

local path = os.getenv("OGGF_DIAG_OUT") or "trace_output/tails_push_source.txt"
local file = io.open(path, "w")
for _, line in ipairs(out) do file:write(line .. "\n") end
file:close()
print("diag_tails_push_source: wrote " .. #out .. " rows to " .. path)
client.exit()
