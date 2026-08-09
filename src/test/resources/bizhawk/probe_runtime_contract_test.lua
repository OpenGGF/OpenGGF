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
        flushes = 0,
        closes = 0,
        frames = 0,
        setupCalls = 0,
        callbackArguments = nil
    }

    os.getenv = function(name)
        if name == "OGGF_OUT" then return "/tmp/probe-runtime-contract.csv" end
        return nil
    end
    io.open = function()
        return {
            write = function() end,
            flush = function()
                state.flushes = state.flushes + 1
                if options.failFlush then error("flush boom") end
            end,
            close = function()
                state.closes = state.closes + 1
                if options.failClose then error("close boom") end
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
            if options.failUnregister == name then error("unregister boom " .. name) end
        end
    }
    emu = {
        limitframerate = function() state.setupCalls = state.setupCalls + 1 end,
        frameadvance = function()
            state.frames = state.frames + 1
            if options.invokeCallback and state.callbacks[options.invokeCallback] then
                state.callbacks[options.invokeCallback](0x1234, 0x56)
            end
        end
    }
    return state
end

local function runCallbackArgumentForwarding()
    local state = newEnvironment({ invokeCallback = "arguments" })
    local runtime = dofile(runtimePath)
    runtime.run({
        stage = function() return true end,
        hooks = {{
            name = "arguments",
            address = 0x100,
            callback = function(context, address, value)
                state.callbackArguments = { context, address, value }
                context.finish()
            end
        }}
    })
    check(state.callbackArguments ~= nil
            and type(state.callbackArguments[1].movieFinished) == "function"
            and state.callbackArguments[2] == 0x1234
            and state.callbackArguments[3] == 0x56,
        "hook callback did not receive context followed by all BizHawk arguments")
end

local function runWindowsSiblingPath()
    -- Break caught: Windows supplies backslashes in the runtime path, so the S1
    -- observer fails to derive its adjacent audio contract before hooks register.
    newEnvironment()
    local runtime = dofile(runtimePath)
    local actual = runtime.siblingPath(
        [[C:\OpenGGF\tools\bizhawk\probes\probe_runtime.lua]],
        "audio/s1_audio_parity_contract.lua")
    check(actual == "C:/OpenGGF/tools/bizhawk/audio/s1_audio_parity_contract.lua",
        "Windows runtime path did not normalize to the audio contract")
end

local function runDefaultMovieFinish()
    local state = newEnvironment({ movieFinished = true })
    local runtime = dofile(runtimePath)
    local onFrameCalls = 0
    runtime.run({
        stage = function() return true end,
        hooks = {{ address = 0x100, callback = function() end }},
        onFrame = function() onFrameCalls = onFrameCalls + 1 end
    })
    check(onFrameCalls == 0 and state.frames == 0 and state.exits == 1 and state.closes == 1,
        "default probes must finish immediately when the movie finishes")
end

local function runContinueAfterMovie()
    local state = newEnvironment({ movieFinished = true })
    local runtime = dofile(runtimePath)
    local onFrameCalls = 0
    runtime.run({
        stage = function() return true end,
        hooks = {{ address = 0x100, callback = function() end }},
        continueAfterMovie = true,
        onFrame = function(context)
            onFrameCalls = onFrameCalls + 1
            check(context.movieFinished(), "movieFinished did not expose movie completion")
            if onFrameCalls == 2 then context.finish() end
        end
    })
    check(onFrameCalls == 2 and state.frames == 1,
        "continueAfterMovie did not allow onFrame to run until it finished")
    check(state.exits == 1 and state.closes == 1,
        "continued movie probe did not clean up after explicit finish")
end

local function runOnFrameLifecycle()
    local state = newEnvironment()
    local runtime = dofile(runtimePath)
    local onFrameCalls = 0
    runtime.run({
        stage = function() return true end,
        hooks = {{ address = 0x100, callback = function() end }},
        onFrame = function(context)
            onFrameCalls = onFrameCalls + 1
            if onFrameCalls == 2 then context.finish() end
        end
    })
    check(onFrameCalls == 2 and state.frames == 1,
        "onFrame must run once before each frameadvance and may finish the probe")
end

local function runOptionalFieldValidation()
    local state = newEnvironment()
    local runtime = dofile(runtimePath)
    local ok, failure = pcall(runtime.run, {
        stage = function() return true end,
        hooks = {{ address = 0x100, callback = function() end }},
        continueAfterMovie = "yes"
    })
    check(not ok and tostring(failure):find("continueAfterMovie", 1, true),
        "continueAfterMovie type was not validated")
    check(state.setupCalls == 0, "optional fields were validated after runtime setup")

    state = newEnvironment()
    runtime = dofile(runtimePath)
    ok, failure = pcall(runtime.run, {
        stage = function() return true end,
        hooks = {{ address = 0x100, callback = function() end }},
        onFrame = true
    })
    check(not ok and tostring(failure):find("onFrame", 1, true),
        "onFrame type was not validated")
    check(state.setupCalls == 0, "onFrame was validated after runtime setup")
end

local function runCleanupFailures()
    local state = newEnvironment({
        invokeCallback = "first",
        failFlush = true,
        failClose = true,
        failUnregister = "first"
    })
    local runtime = dofile(runtimePath)
    local ok, failure = pcall(runtime.run, {
        stage = function() return true end,
        hooks = {
            { name = "first", address = 0x100,
                callback = function() error("original callback boom") end },
            { name = "second", address = 0x200, callback = function() end }
        }
    })
    local message = tostring(failure)
    check(not ok and message:find("original callback boom", 1, true),
        "cleanup failure replaced the original callback failure")
    check(message:find("flush boom", 1, true)
            and message:find("close boom", 1, true)
            and message:find("unregister boom first", 1, true),
        "cleanup failures were not recorded")
    check(#state.unregistered == 2 and state.unregistered[2] == "second",
        "one unregister failure prevented later hook cleanup")
    check(state.flushes == 1 and state.closes == 1 and state.exits == 1,
        "one cleanup failure prevented later cleanup operations")
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
runCallbackArgumentForwarding()
runWindowsSiblingPath()
runDefaultMovieFinish()
runContinueAfterMovie()
runOnFrameLifecycle()
runOptionalFieldValidation()
runPrevalidationFailure()
runStageFailure()
runPartialRegistrationFailure()
runCallbackFailure()
runCleanupFailures()
print("probe runtime behavioral contract passed")
