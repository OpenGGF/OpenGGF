-- Diagnostic: capture the exact ROM per-frame mechanism by which the CPU
-- sidekick (Tails) gets its status=Pushing bit and the +0x80 x_speed kick
-- around the OOZ trace divergence (trace frame ~1782, emu frame ~4554).
--
-- COMPARISON-ONLY: reads ROM state, writes a log file. Never writes game state.
--
-- Window is in emu.framecount() terms. OOZ bk2_frame_offset=2772, so
-- trace_frame N ~= emu frame (2772 + N). Override with OGGF_DIAG_START/STOP.
--
-- Output: tools/bizhawk/trace_output/ooz_tails_push.txt

local OBJ_TABLE_START = 0xB000
local OBJ_SLOT_SIZE   = 0x40
local SONIC_BASE      = OBJ_TABLE_START
local TAILS_BASE      = OBJ_TABLE_START + OBJ_SLOT_SIZE

local OFF_ID          = 0x00
local OFF_X_POS       = 0x08
local OFF_X_SUB       = 0x0A
local OFF_Y_POS       = 0x0C
local OFF_X_VEL       = 0x10
local OFF_Y_VEL       = 0x12
local OFF_INERTIA     = 0x14
local OFF_STATUS      = 0x22
local OFF_ROUTINE     = 0x24
local OFF_SUBTYPE     = 0x28
local OFF_STAND       = 0x3D  -- interact: SST index the character stands on

local ADDR_ZONE       = 0xFE10

local START_FRAME = tonumber(os.getenv("OGGF_DIAG_START") or "4540")
local STOP_FRAME  = tonumber(os.getenv("OGGF_DIAG_STOP")  or "4575")

local out = {}
local function log(s) table.insert(out, s) end

local function s16(v)
    v = v % 0x10000
    if v >= 0x8000 then v = v - 0x10000 end
    return v
end

local function rd16(a) return mainmemory.read_u16_be(a) end
local function rd8(a) return mainmemory.read_u8(a) end

local function char_line(name, base)
    return string.format(
        "%s id=0x%02X rtn=0x%02X st=0x%02X x=0x%04X.%04X y=0x%04X xv=%d yv=%d in=%d stand=0x%02X",
        name, rd8(base+OFF_ID), rd8(base+OFF_ROUTINE), rd8(base+OFF_STATUS),
        rd16(base+OFF_X_POS), rd16(base+OFF_X_SUB), rd16(base+OFF_Y_POS),
        s16(rd16(base+OFF_X_VEL)), s16(rd16(base+OFF_Y_VEL)), s16(rd16(base+OFF_INERTIA)),
        rd8(base+OFF_STAND))
end

-- Scan all object slots; report any non-empty object within ~0x50 px of Tails.
local function nearby_objects(tx, ty)
    local parts = {}
    for slot = 0, 0x7F do
        local base = OBJ_TABLE_START + slot * OBJ_SLOT_SIZE
        local id = rd8(base + OFF_ID)
        if id ~= 0 and base ~= SONIC_BASE and base ~= TAILS_BASE then
            local ox = rd16(base + OFF_X_POS)
            local oy = rd16(base + OFF_Y_POS)
            if math.abs(ox - tx) <= 0x50 and math.abs(oy - ty) <= 0x50 then
                table.insert(parts, string.format(
                    "s%02X:id=0x%02X sub=0x%02X rtn=0x%02X @0x%04X.%04X,0x%04X xv=%d",
                    slot, id, rd8(base+OFF_SUBTYPE), rd8(base+OFF_ROUTINE),
                    ox, rd16(base+OFF_X_SUB), oy, s16(rd16(base+OFF_X_VEL))))
            end
        end
    end
    return table.concat(parts, " | ")
end

local function in_window()
    local f = emu.framecount()
    return f >= START_FRAME and f <= STOP_FRAME
end

local written = false
local function dump()
    local path = os.getenv("OGGF_DIAG_OUT")
        or "C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output/ooz_tails_push.txt"
    local f = io.open(path, "w")
    if f == nil then
        print("DIAG: could not open " .. path)
        return
    end
    f:write(table.concat(out, "\n") .. "\n")
    f:close()
    print("DIAG: wrote " .. path .. " (" .. #out .. " lines)")
end

print(string.format("S2 OOZ Tails-push diagnostic loaded. Window emu frames [%d,%d].",
    START_FRAME, STOP_FRAME))

while true do
    if in_window() then
        local tx = rd16(TAILS_BASE + OFF_X_POS)
        local ty = rd16(TAILS_BASE + OFF_Y_POS)
        log(string.format("f=%d zone=%d", emu.framecount(), rd8(ADDR_ZONE)))
        log("  " .. char_line("SONIC", SONIC_BASE))
        log("  " .. char_line("TAILS", TAILS_BASE))
        log("  NEAR " .. nearby_objects(tx, ty))
    end
    if emu.framecount() > STOP_FRAME and not written then
        written = true
        dump()
        client.exit()
        break
    end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
