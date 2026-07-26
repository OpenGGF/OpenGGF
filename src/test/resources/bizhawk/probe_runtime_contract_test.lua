local runtimePath = assert(arg[1], "expected probe runtime path")

local function check(condition, message)
    if not condition then error(message, 2) end
end

local function newEnvironment(options)
    options = options or {}
    local state = {
        callbacks = {},
        registered = {},
        unregistered = {},
        stageCalls = 0,
        exits = 0,
        closes = 0,
        frames = 0,
        setupCalls = 0
    }

    os.getenv = function(name)
        if name == "OGGF_OUT" then return "/tmp/probe-runtime-contract.csv" end
        return nil
    end
    io.open = function()
        return {
            write = function() end,
            flush = function() end,
            close = function()
                state.closes = state.closes + 1
            end
        }
    end
    client = {
        speedmode = function() state.setupCalls = state.setupCalls + 1 end,
        invisibleemulation = function() state.setupCalls = state.setupCalls + 1 end,
        SetSoundOn = function() end,
        ispaused = function() return false end,
        unpause = function() end,
        exit = function() state.exits = state.exits + 1 end
    }
    movie = {
        isloaded = function() return options.movieFinished == true end,
        mode = function() return options.movieFinished and "FINISHED" or "PLAY" end
    }
    event = {
        onmemoryexecute = function(callback, _, name)
            if options.failRegistrationAt == #state.registered + 1 then
                error("registration boom")
            end
            state.registered[#state.registered + 1] = name
            state.callbacks[name] = callback
        end,
        onmemorywrite = function(callback, _, name)
            state.registered[#state.registered + 1] = name
            state.callbacks[name] = callback
        end,
        unregisterbyname = function(name)
            state.unregistered[#state.unregistered + 1] = name
        end
    }
    emu = {
        limitframerate = function() state.setupCalls = state.setupCalls + 1 end,
        frameadvance = function()
            state.frames = state.frames + 1
            if options.invokeCallback and state.callbacks[options.invokeCallback] then
                state.callbacks[options.invokeCallback]()
            end
        end
    }
    return state
end

local function runPrevalidationFailure()
    local state = newEnvironment()
    local runtime = dofile(runtimePath)
    local ok, failure = pcall(runtime.run, {
        stage = function() return true end,
        hooks = {
            { address = 0x100, callback = function() end },
            { address = "not an address", callback = function() end }
        }
    })
    check(not ok and tostring(failure):find("numeric address", 1, true),
        "invalid hook configuration was not rejected")
    check(state.setupCalls == 0 and #state.registered == 0,
        "hook configuration was not fully validated before runtime setup")
end

local function runStageGating()
    local state = newEnvironment({ invokeCallback = "adhoc_probe_hook_1" })
    local runtime = dofile(runtimePath)
    runtime.run({
        stage = function()
            state.stageCalls = state.stageCalls + 1
            return state.stageCalls == 2
        end,
        hooks = {{
            address = 0x100,
            callback = function(context) context.finish() end
        }}
    })
    check(state.stageCalls == 2, "hook registered before semantic stage gate")
    check(#state.registered == 1, "stage-gated hook was not registered")
    check(state.exits == 1 and state.closes == 1, "normal finish did not clean up once")
end

local function runStageFailure()
    local state = newEnvironment()
    local runtime = dofile(runtimePath)
    local ok, failure = pcall(runtime.run, {
        stage = function() error("stage boom") end,
        hooks = {{ address = 0x100, callback = function() end }}
    })
    check(not ok and tostring(failure):find("stage boom", 1, true),
        "stage failure was replaced or swallowed")
    check(state.exits == 1 and state.closes == 1, "stage failure did not clean up")
end

local function runPartialRegistrationFailure()
    local state = newEnvironment({ failRegistrationAt = 2 })
    local runtime = dofile(runtimePath)
    local ok, failure = pcall(runtime.run, {
        stage = function() return true end,
        hooks = {
            { name = "first", address = 0x100, callback = function() end },
            { name = "second", address = 0x200, callback = function() end }
        }
    })
    check(not ok and tostring(failure):find("registration boom", 1, true),
        "registration failure was replaced or swallowed")
    check(#state.unregistered == 1 and state.unregistered[1] == "first",
        "partial registration was not unregistered")
    check(state.exits == 1 and state.closes == 1, "registration failure did not clean up")
end

local function runCallbackFailure()
    local state = newEnvironment({ invokeCallback = "failing" })
    local runtime = dofile(runtimePath)
    local ok, failure = pcall(runtime.run, {
        stage = function() return true end,
        hooks = {{
            name = "failing",
            address = 0x100,
            callback = function() error("callback boom") end
        }}
    })
    check(not ok and tostring(failure):find("callback boom", 1, true),
        "callback failure was replaced or swallowed")
    check(#state.unregistered == 1 and state.unregistered[1] == "failing",
        "callback failure did not unregister hooks")
    check(state.exits == 1 and state.closes == 1, "callback failure did not clean up once")
end

runStageGating()
runPrevalidationFailure()
runStageFailure()
runPartialRegistrationFailure()
runCallbackFailure()
print("probe runtime behavioral contract passed")
