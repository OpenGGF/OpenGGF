local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"),
    "run through run_bizhawk_lua so OGGF_BIZHAWK_PROBE_RUNTIME is absolute")
local ProbeRuntime = dofile(runtimePath)

ProbeRuntime.run({
    stage = function()
        return mainmemory.read_u8(0xFFF600) == 0x0C
    end,
    hooks = {{
        address = 0x123456,
        callback = function(context)
            context.log("nested probe contract example")
            context.finish()
        end
    }}
})
