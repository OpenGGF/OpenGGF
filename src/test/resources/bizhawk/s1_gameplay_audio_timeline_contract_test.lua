local contractPath = assert(arg[1], "expected gameplay-audio timeline contract path")
local Contract = dofile(contractPath)

local function check(condition, message)
    if not condition then error(message, 2) end
end

local function equals(actual, expected, message)
    check(actual == expected, message .. "\nexpected: " .. tostring(expected)
        .. "\nactual:   " .. tostring(actual))
end

local function owner(ownerClass, soundId, ordinal)
    return {owner_class = ownerClass, request_ordinal = ordinal, sound_id = soundId}
end

local function owners(fm3, fm4, fm5, psg1, psg2, psg3)
    return {FM3 = fm3, FM4 = fm4, FM5 = fm5, PSG1 = psg1, PSG2 = psg2, PSG3 = psg3}
end

local function musicOwners()
    local music = owner("MUSIC", 0x81, 0)
    return owners(music, music, music, music, music, music)
end

local function headers(normal, special)
    return {normal = normal or {}, special = special or {}}
end

local function runQueueAndContention()
    -- Break caught: queue slot overwrites, delayed consumption, priority rejection, or role ownership
    -- are collapsed before a request can be correlated with its accepted dispatch.
    local timeline = Contract.newTimeline(0x81)
    timeline:beginTick(860, 0)
    timeline:queue(1, 0xA1)
    timeline:queue(1, 0xA2)
    local request = timeline:consume(1, 4)
    equals(request.sound_id, 0xA2, "queue overwrite did not retain the last request")
    equals(request.consumed_tick, 0, "queue consumption tick was not retained as diagnostic")
    check(timeline:dispatch(request, headers({FM3 = true})), "accepted normal SFX dispatch was rejected")
    local first = timeline:closeTick(headers({FM3 = true}))
    equals(first.requests[1].request_ordinal, 1, "first accepted request ordinal was not one")
    equals(first.requests[1].arbitration[1].role, "FM3", "normal SFX did not request FM3")
    check(first.requests[1].arbitration[1].acquired, "music-owned FM3 was not acquired")
    equals(first.requests[1].arbitration[1].displaced_owner.owner_class, "MUSIC",
        "music owner was not reported as displaced")
    equals(first.owners.FM3.owner_class, "NORMAL_SFX", "normal SFX did not own FM3")

    timeline:beginTick(861, 1)
    timeline:queue(2, 0xA3)
    local lower = timeline:consume(2, 3)
    check(not timeline:dispatch(lower, headers({FM3 = true})), "lower-priority dispatch was accepted")
    local rejected = timeline:closeTick(headers({FM3 = true}))
    equals(#rejected.requests, 0, "rejected dispatch became a semantic request")
    equals(rejected.owners.FM3.sound_id, 0xA2, "lower-priority rejection changed final owner")

    timeline:beginTick(862, 2)
    timeline:queue(0, 0xA4)
    local equal = timeline:consume(0, 4)
    check(timeline:dispatch(equal, headers({FM3 = true})), "equal-priority replacement was rejected")
    local replacement = timeline:closeTick(headers({FM3 = true}))
    equals(replacement.requests[1].arbitration[1].displaced_owner.sound_id, 0xA2,
        "equal-priority replacement did not retain displaced identity")
    equals(replacement.owners.FM3.sound_id, 0xA4, "equal-priority replacement did not transfer owner")
    equals(replacement.requests[1].request_ordinal, 2, "accepted requests were not monotonic")

    timeline:beginTick(863, 3)
    timeline:queue(1, 0xD0)
    local special = timeline:consume(1, 9)
    check(timeline:dispatch(special, headers({FM3 = true}, {FM4 = true})),
        "special SFX dispatch was rejected")
    local specialFrame = timeline:closeTick(headers({FM3 = true}, {FM4 = true}))
    equals(specialFrame.owners.FM4.owner_class, "SPECIAL_SFX", "special SFX did not own FM4")

    timeline:beginTick(864, 4)
    timeline:queue(1, 0xA5)
    local normal = timeline:consume(1, 9)
    check(timeline:dispatch(normal, headers({FM3 = true, FM4 = true}, {FM4 = true})),
        "normal SFX over special SFX was rejected")
    local normalFrame = timeline:closeTick(headers({FM3 = true, FM4 = true}, {FM4 = true}))
    equals(normalFrame.owners.FM4.owner_class, "NORMAL_SFX", "normal SFX did not outrank special SFX")
    equals(normalFrame.requests[1].arbitration[2].displaced_owner.owner_class, "SPECIAL_SFX",
        "normal-over-special arbitration lost the displaced special owner")

    timeline:beginTick(865, 5)
    local restored = timeline:closeTick(headers())
    equals(restored.owners.FM3.owner_class, "MUSIC", "music was not restored after normal SFX ended")
    equals(restored.owners.FM4.owner_class, "MUSIC", "music was not restored after special SFX ended")
    equals(Contract.canonicalJson(restored.owners),
        '{"FM3":{"owner_class":"MUSIC","request_ordinal":0,"sound_id":129},"FM4":{"owner_class":"MUSIC","request_ordinal":0,"sound_id":129},"FM5":{"owner_class":"MUSIC","request_ordinal":0,"sound_id":129},"PSG1":{"owner_class":"MUSIC","request_ordinal":0,"sound_id":129},"PSG2":{"owner_class":"MUSIC","request_ordinal":0,"sound_id":129},"PSG3":{"owner_class":"MUSIC","request_ordinal":0,"sound_id":129}}',
        "final owner vector was not canonical")
    equals(timeline:terminal().request_count, 4, "terminal accepted-request count was wrong")
end

local function runLifecycleAndDiagnostics()
    -- Break caught: a DAC busy retry on the same stack pointer becomes two ticks, or diagnostic
    -- ticks regress even though they are deliberately excluded from semantic equality.
    local lifecycle = Contract.newInvocationLifecycle()
    equals(lifecycle:entry(0x00FF1000, 10), "open", "first UpdateMusic entry did not open")
    equals(lifecycle:entry(0x00FF1000, 10), "retry", "same-stack DAC retry opened another tick")
    equals(lifecycle:close(), "close", "UpdateMusic did not close")
    local ok = pcall(function() lifecycle:entry(0x00FF1004, 11); lifecycle:entry(0x00FF1008, 11) end)
    check(not ok, "different-stack nested UpdateMusic entry was accepted")

    local timeline = Contract.newTimeline(0x81)
    timeline:beginTick(860, 3)
    timeline:closeTick(headers())
    local monotonic = pcall(function() timeline:beginTick(861, 2) end)
    check(not monotonic, "diagnostic tick regression was accepted")
end

runQueueAndContention()
runLifecycleAndDiagnostics()
print("S1_GAMEPLAY_AUDIO_TIMELINE_CONTRACT_OK")
