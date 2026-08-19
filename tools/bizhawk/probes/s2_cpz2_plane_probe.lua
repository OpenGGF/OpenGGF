-- Which collision plane and solidity bit does the ROM's Sonic hold at the
-- CPZ2 seg10 row-6600 wall, and where does its right-wall probe land?
--
-- Sonic_DoLevelCollision selects Primary_Collision when top_solid_bit is $C and
-- Secondary_Collision otherwise, then passes lrb_solid_bit to the scan
-- (docs/s2disasm/s2.asm:37889-37895). The engine holds top=$0C / lrb=$0D at this
-- frame and its right push sensor reports no wall, so if the ROM is on the
-- secondary plane the wall exists only there and the engine's whole-wall miss is
-- explained. The fixture carries no solid-bit columns, so this cannot be read
-- from the recording.
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

local MAIN_CHARACTER = 0xB000
local OFF_X_POS         = 0x08
local OFF_TOP_SOLID_BIT = 0x3E   -- s2.constants.asm:70
local OFF_LRB_SOLID_BIT = 0x3F   -- s2.constants.asm:71

local BK2_FRAME_OFFSET = 82342
local FIRST_FRAME = BK2_FRAME_OFFSET + 6594
local LAST_FRAME  = BK2_FRAME_OFFSET + 6612

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x7F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == CPZ_ZONE
            and mainmemory.read_u8(CURRENT_ACT) == CPZ_ACT2
    end,
    hooks = {
        -- s2.asm CheckRightWallDist_Part2 (loc_1EEE4): d3 already holds x_pos and
        -- is about to take the fixed +$A. Sampling here gives the probe's own
        -- input alongside the plane bytes that selected the collision array.
        { name = "CheckRightWallDist_Part2", address = 0x01EEE4,
          callback = function(context)
              local f = emu.framecount()
              if f < FIRST_FRAME or f > LAST_FRAME then
                  return
              end
              local d3 = emu.getregister("M68K D3") or 0
              context.log(string.format(
                  "frame=%d row=%d pc=%06X d3=%04X x=%04X top=%02X lrb=%02X",
                  f, f - BK2_FRAME_OFFSET,
                  (emu.getregister("M68K PC") or 0) & 0xFFFFFF,
                  d3 & 0xFFFF,
                  mainmemory.read_u16_be(MAIN_CHARACTER + OFF_X_POS),
                  mainmemory.read_u8(MAIN_CHARACTER + OFF_TOP_SOLID_BIT),
                  mainmemory.read_u8(MAIN_CHARACTER + OFF_LRB_SOLID_BIT)))
              if f >= LAST_FRAME then
                  context.finish()
              end
          end }
    }
})
