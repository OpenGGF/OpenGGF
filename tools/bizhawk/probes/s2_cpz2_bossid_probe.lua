-- Is Current_Boss_ID zero at CPZ2 seg10 row 6600?
--
-- Sonic_Boundary's right-hand test widens the boundary by $40 only when
-- Current_Boss_ID is zero (docs/s2disasm/s2.asm:37243-37251):
--
--     move.w  (Camera_Max_X_pos).w,d0     ; 1A990
--     addi.w  #screen_width-24,d0         ; 1A994
--     tst.b   (Current_Boss_ID).w         ; 1A998
--     bne.s   +                           ; 1A99C
--     addi.w  #$40,d0                     ; 1A99E  <-- executes only when NO boss
--     +
--     cmp.w   d1,d0                       ; 1A9A2  <-- always reached
--
-- Hooking the widen instruction makes its execution the answer: run means the id
-- was zero, absent means non-zero. Hooking the compare gives the resulting
-- boundary and x_pos so the arithmetic can be checked rather than assumed. This
-- replaces an inference from a 64-pixel magnitude with a direct observation.
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
local FIRST_FRAME = BK2_FRAME_OFFSET + 6596
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
    context.log(string.format("frame=%d row=%d hook=%-12s d0=%04X d1=%04X",
        f, f - BK2_FRAME_OFFSET, label, reg("D0") & 0xFFFF, reg("D1") & 0xFFFF))
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
        { name = "widen_no_boss", address = 0x01A99E,
          callback = function(context) record(context, "WIDEN+40") end },
        { name = "boundary_compare", address = 0x01A9A2,
          callback = function(context) record(context, "COMPARE") end }
    }
})
