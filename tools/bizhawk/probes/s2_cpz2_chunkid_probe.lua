-- What does FindWall read at the CPZ2 seg10 row-6600 wall?
--
-- FindWall (docs/s2disasm/s2.asm) calls Find_Tile, takes the layout word at
-- (a1), masks the low ten bits for the 16x16 tile id, and only then tests the
-- solidity bit d5 (= lrb_solid_bit, $D) IN THAT WORD before consulting
-- Collision_addr. So three values decide the wall: the id, the solidity bit in
-- the word, and the collision-array entry the id maps to. loc_1E9D0 is the
-- solid path and loc_1E9C2 the no-collision path, so which one is taken is
-- itself the answer to "does the ROM think this tile is solid".
--
-- NOTE ON NAMING: the S2 disassembly calls the 16x16 unit a "block" and the
-- 128x128 unit a "chunk". This codebase inverts both -- Chunk is 16x16, Block is
-- 128x128 (CLAUDE.md). The value logged here as `tile_id` is the ROM's blockID
-- and this codebase's CHUNK id.
--
-- Read/log only. Nothing here writes emulated memory, input, registers or
-- savestates, and nothing it produces is an engine input.

local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE    = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT  = 0xFE11
local CPZ_ZONE = 0x0D
local CPZ_ACT2 = 0x01

local BK2_FRAME_OFFSET = 82342
local FIRST_FRAME = BK2_FRAME_OFFSET + 6597
local LAST_FRAME  = BK2_FRAME_OFFSET + 6603

local function reg(name)
    local v = emu.getregister("M68K " .. name)
    if v == nil then
        return -1
    end
    return v
end

local function record(context, label)
    local f = emu.framecount()
    if f < FIRST_FRAME or f > LAST_FRAME then
        return
    end
    local d4 = reg("D4") & 0xFFFF
    context.log(string.format(
        "frame=%d row=%d hook=%s word=%04X tile_id=%04X d5=%02X d3=%04X d2=%04X",
        f, f - BK2_FRAME_OFFSET, label,
        d4, d4 & 0x3FF, reg("D5") & 0xFF,
        reg("D3") & 0xFFFF, reg("D2") & 0xFFFF))
    if f >= LAST_FRAME then
        context.finish()
    end
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x7F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == CPZ_ZONE
            and mainmemory.read_u8(CURRENT_ACT) == CPZ_ACT2
    end,
    hooks = {
        -- solid path: the layout word's solidity bit was set.
        { name = "FindWall_solid", address = 0x01E9D0,
          callback = function(context) record(context, "SOLID") end },
        -- no-collision path: id zero, or the solidity bit clear.
        { name = "FindWall_nocollision", address = 0x01E9C2,
          callback = function(context) record(context, "NOCOLLISION") end }
    }
})
