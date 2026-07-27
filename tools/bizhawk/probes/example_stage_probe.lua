local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

local GAME_MODE = 0xF600
local CURRENT_ZONE = 0xFE10
local CURRENT_ACT = 0xFE11

-- ProbeRuntime owns client.exit(); probes finish through the supplied context.
ProbeRuntime.run({
    stage = function()
        return (mainmemory.read_u8(GAME_MODE) & 0x0F) == 0x0C
            and mainmemory.read_u8(CURRENT_ZONE) == 0
            and mainmemory.read_u8(CURRENT_ACT) == 0
    end,
    hooks = {
        {
            name = "example_aiz1_process_sprites",
            address = 0x01AADA,
            callback = function(context)
                context.log(string.format("frame=%d pc=%08X",
                    emu.framecount(), emu.getregister("M68K PC") or 0))
                context.finish()
            end
        }
    }
})
