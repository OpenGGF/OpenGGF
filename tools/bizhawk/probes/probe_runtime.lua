-- Shared lifecycle for new, one-off BizHawk diagnostics.
-- Probe files provide only a semantic stage predicate and declarative hooks.

local ProbeRuntime = {}
local registerHooks

local function requireConfig(config)
    assert(type(config) == "table", "ProbeRuntime.run requires a config table")
    assert(type(config.stage) == "function", "probe config requires stage = function")
    assert(type(config.hooks) == "table" and #config.hooks > 0,
        "probe config requires at least one declarative hook")
    for _, hook in ipairs(config.hooks) do
        assert(type(hook.address) == "number", "probe hook requires a numeric address")
        assert(type(hook.callback) == "function", "probe hook requires a callback")
        assert(hook.kind == nil or hook.kind == "execute" or hook.kind == "write",
            "probe hook kind must be `execute` or observation-only `write`")
    end
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
    local cleanupFailures = {}

    local function cleanupStep(label, action)
        local ok, failure = pcall(action)
        if not ok then
            cleanupFailures[#cleanupFailures + 1] = label .. ": " .. tostring(failure)
        end
    end

    local function closeOutput()
        if closed then return end
        closed = true
        cleanupStep("flush output", function() outfile:flush() end)
        cleanupStep("close output", function() outfile:close() end)
    end

    local function unregisterHooks()
        for _, name in ipairs(registeredNames) do
            cleanupStep("unregister " .. name,
                function() event.unregisterbyname(name) end)
        end
        registeredNames = {}
    end

    local function finish()
        if finished then return end
        finished = true
        unregisterHooks()
        closeOutput()
        cleanupStep("exit client", client.exit)
    end

    local function failureWithCleanup(originalError)
        if #cleanupFailures == 0 then return originalError end
        return tostring(originalError) .. "\ncleanup failures:\n- "
            .. table.concat(cleanupFailures, "\n- ")
    end

    local context = {
        log = function(line)
            outfile:write(tostring(line), "\n")
            outfile:flush()
        end,
        finish = finish
    }

    local ok, originalError = xpcall(function()
        while not finished do
            if not hooksRegistered and config.stage() then
                registerHooks(config.hooks, context, registeredNames, finish)
                hooksRegistered = true
            end
            if movie.isloaded() and movie.mode() == "FINISHED" then
                finish()
                break
            end
            if client.ispaused() then client.unpause() end
            emu.frameadvance()
        end
    end, debug.traceback)
    if not ok then
        finish()
        error(failureWithCleanup(originalError), 0)
    end
    if #cleanupFailures > 0 then
        error(failureWithCleanup("probe cleanup failed"), 0)
    end
end

registerHooks = function(hooks, context, registeredNames, finish)
    for index, hook in ipairs(hooks) do
        local name = hook.name or ("adhoc_probe_hook_" .. index)
        local callback = function()
            local ok, originalError = xpcall(
                function() hook.callback(context) end, debug.traceback)
            if not ok then
                finish()
                error(originalError, 0)
            end
        end
        if hook.kind == nil or hook.kind == "execute" then
            event.onmemoryexecute(callback, hook.address, name)
        else
            event.onmemorywrite(callback, hook.address, name)
        end
        registeredNames[#registeredNames + 1] = name
    end
end

return ProbeRuntime
