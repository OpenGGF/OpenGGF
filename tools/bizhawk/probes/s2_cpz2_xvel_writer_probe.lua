-- WHO zeroes Sonic's x_vel at CPZ2 seg10 row 6600?
--
-- The premise under test is that a solid object stops him: something zeroes
-- x_vel and corrects x_pos two pixels before the terrain pass, which is the
-- SolidObject_StopCharacter / SolidObject_AtEdge signature
-- (docs/s2disasm/s2.asm:35428-35446). That is an argument from shape. This
-- measures the writer instead: a memory-write hook on MainCharacter+x_vel
-- captures the PC of the instruction that performs the write, so the routine is
-- identified without assuming which one it is. A previous line of work spent
-- nine rounds on a founding inference nobody tested, so the cheapest disproof
-- comes first here.
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
local OFF_X_POS = 0x08   -- s2.constants.asm:19
local OFF_X_VEL = 0x10   -- s2.constants.asm:28
local OFF_INERTIA = 0x14 -- s2.constants.asm:49

local BK2_FRAME_OFFSET = 82342
local FIRST_FRAME = BK2_FRAME_OFFSET + 6597
local LAST_FRAME  = BK2_FRAME_OFFSET + 6602

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x7F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == CPZ_ZONE
            and mainmemory.read_u8(CURRENT_ACT) == CPZ_ACT2
    end,
    hooks = {
        -- A write hook observes the write and never authorises mutation.
        { name = "x_vel_write", kind = "write", address = 0xFFB010,
          callback = function(context)
              local f = emu.framecount()
              if f < FIRST_FRAME or f > LAST_FRAME then
                  return
              end
              context.log(string.format(
                  "frame=%d row=%d pc=%06X x_vel=%04X inertia=%04X x_pos=%04X",
                  f, f - BK2_FRAME_OFFSET,
                  (emu.getregister("M68K PC") or 0) & 0xFFFFFF,
                  mainmemory.read_u16_be(MAIN_CHARACTER + OFF_X_VEL),
                  mainmemory.read_u16_be(MAIN_CHARACTER + OFF_INERTIA),
                  mainmemory.read_u16_be(MAIN_CHARACTER + OFF_X_POS)))
              if f >= LAST_FRAME then
                  context.finish()
              end
          end }
    }
})
