-- Where does RunObject's dispatch loop stop on CPZ2 seg10 rows 5550-5566?
--
-- Obj05 lives in LevelOnly_Object_RAM at $FFD000, past Object_RAM_End
-- (docs/s2disasm/s2.constants.asm:1145-1151), and RunObject was shown not to
-- REACH that slot on rows 5554-5564. RunObject jsrs into arbitrary object code
-- with the loop counter d7 live (docs/s2disasm/s2.asm:29832-29843), so an object
-- that fails to preserve d7 truncates the remainder of the pass. This logs a0
-- and d7 at every iteration so the stopping point and the counter's descent can
-- be read directly instead of inferred.
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
local FIRST_FRAME = BK2_FRAME_OFFSET + 5550
local LAST_FRAME  = BK2_FRAME_OFFSET + 5566

-- No mainmemory reads in the hot path: this fires ~$90 times per frame and
-- NLua is known to die on heavy per-frame reads at turbo speed.
local function reg(name)
    local v = emu.getregister("M68K " .. name)
    if v == nil then
        return -1
    end
    return v
end

ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x7F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == CPZ_ZONE
            and mainmemory.read_u8(CURRENT_ACT) == CPZ_ACT2
    end,
    hooks = {
        -- s2.asm:29832 sub_15FCC -- the loop body, before its own id-zero skip.
        { name = "RunObject", address = 0x015FCC,
          callback = function(context)
              local f = emu.framecount()
              if f < FIRST_FRAME or f > LAST_FRAME then
                  return
              end
              context.log(string.format("row=%d a0=%06X d7=%04X",
                  f - BK2_FRAME_OFFSET, reg("A0") & 0xFFFFFF, reg("D7") & 0xFFFF))
              if f >= LAST_FRAME then
                  context.finish()
              end
          end }
    }
})
