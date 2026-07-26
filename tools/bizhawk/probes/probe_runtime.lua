-- Shared lifecycle for new, one-off BizHawk diagnostics.
-- Probe files provide only a semantic stage predicate and declarative hooks.

local ProbeRuntime = {}
local registerHooks

local function requireConfig(config)
    assert(type(config) == "table", "ProbeRuntime.run requires a config table")
    assert(type(config.stage) == "function", "probe config requires stage = function")
    assert(type(config.hooks) == "table" and #config.hooks > 0,
        "probe config requires at least one declarative hook")
end

function ProbeRuntime.run(config)
    requireConfig(config)

    emu.limitframerate(false)
    client.speedmode(6400)
    client.invisibleemulation(true)
    if client.SetSoundOn then
        pcall(client.SetSoundOn, false)
    end

    local outputPath = assert(os.getenv("OGGF_OUT"),
        "OGGF_OUT must name an absolute diagnostic output path")
    local outfile = assert(io.open(outputPath, "w"))
    local registeredNames = {}
    local hooksRegistered = false
    local finished = false
    local closed = false

    local function closeOutput()
        if closed then return end
        closed = true
        outfile:flush()
        outfile:close()
    end

    local function unregisterHooks()
        for _, name in ipairs(registeredNames) do
            event.unregisterbyname(name)
        end
        registeredNames = {}
    end

    local function finish()
        if finished then return end
        finished = true
        unregisterHooks()
        closeOutput()
        client.exit()
    end

    local context = {
        log = function(line)
            outfile:write(tostring(line), "\n")
            outfile:flush()
        end,
        finish = finish
    }

    while not finished do
        if not hooksRegistered and config.stage() then
            hooksRegistered = true
            registerHooks(config.hooks, context, registeredNames)
        end
        if movie.isloaded() and movie.mode() == "FINISHED" then
            finish()
            break
        end
        if client.ispaused() then client.unpause() end
        emu.frameadvance()
    end
end

registerHooks = function(hooks, context, registeredNames)
    for index, hook in ipairs(hooks) do
        assert(type(hook.address) == "number", "probe hook requires a numeric address")
        assert(type(hook.callback) == "function", "probe hook requires a callback")
        local name = hook.name or ("adhoc_probe_hook_" .. index)
        registeredNames[#registeredNames + 1] = name
        local callback = function() hook.callback(context) end
        if hook.kind == nil or hook.kind == "execute" then
            event.onmemoryexecute(callback, hook.address, name)
        elseif hook.kind == "write" then
            event.onmemorywrite(callback, hook.address, name)
        else
            error("probe hook kind must be `execute` or `write`")
        end
    end
end

return ProbeRuntime
