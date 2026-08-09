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

local function observation(declared, initialized, normal, special)
    return {accepted = true, declared_roles = declared, initialized_roles = initialized, headers = headers(normal, special)}
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
    check(timeline:dispatch(request, observation({"FM3"}, {"FM3"}, {FM3 = true})), "accepted normal SFX dispatch was rejected")
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
    local rejectedObservation = observation({"FM3"}, {"FM3"}, {FM3 = true})
    rejectedObservation.accepted = false
    check(not timeline:dispatch(lower, rejectedObservation), "lower-priority dispatch was accepted")
    local rejected = timeline:closeTick(headers({FM3 = true}))
    equals(#rejected.requests, 0, "rejected dispatch became a semantic request")
    equals(rejected.owners.FM3.sound_id, 0xA2, "lower-priority rejection changed final owner")

    timeline:beginTick(862, 2)
    timeline:queue(0, 0xA4)
    local equal = timeline:consume(0, 4)
    check(timeline:dispatch(equal, observation({"FM3"}, {"FM3"}, {FM3 = true})), "equal-priority replacement was rejected")
    local replacement = timeline:closeTick(headers({FM3 = true}))
    equals(replacement.requests[1].arbitration[1].displaced_owner.sound_id, 0xA2,
        "equal-priority replacement did not retain displaced identity")
    equals(replacement.owners.FM3.sound_id, 0xA4, "equal-priority replacement did not transfer owner")
    equals(replacement.requests[1].request_ordinal, 2, "accepted requests were not monotonic")

    timeline:beginTick(863, 3)
    timeline:queue(1, 0xD0)
    local special = timeline:consume(1, 9)
    check(timeline:dispatch(special, observation({"FM4"}, {"FM4"}, {FM3 = true}, {FM4 = true})),
        "special SFX dispatch was rejected")
    local specialFrame = timeline:closeTick(headers({FM3 = true}, {FM4 = true}))
    equals(specialFrame.owners.FM4.owner_class, "SPECIAL_SFX", "special SFX did not own FM4")

    timeline:beginTick(864, 4)
    timeline:queue(1, 0xA5)
    local normal = timeline:consume(1, 9)
    check(timeline:dispatch(normal, observation({"FM3", "FM4"}, {"FM3", "FM4"}, {FM3 = true, FM4 = true}, {FM4 = true})),
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

local function runSourceDerivedDispatchBoundaries()
    -- Break caught: dispatch entry and final class-wide headers are mistaken for the ROM's per-track
    -- initialization boundary, which transfers unrelated SFX roles or accepts early returns.
    local timeline = Contract.newTimeline(0x81)
    timeline:beginTick(860, 0)
    timeline:queue(1, 0xA4)
    local fm4Only = timeline:cycle(4)[1]
    check(timeline:dispatch(fm4Only, {
        accepted = true, declared_roles = {"FM4"}, initialized_roles = {"FM4"}, headers = headers({FM3 = true, FM4 = true})
    }), "FM4 initialization boundary was not accepted")
    local fm4Frame = timeline:closeTick(headers({FM3 = true, FM4 = true}))
    equals(fm4Frame.requests[1].requested_roles[1], "FM4", "declared FM4 role was not retained")
    equals(fm4Frame.owners.FM3.owner_class, "MUSIC", "unrelated final active FM3 was transferred to SFX")
    equals(fm4Frame.owners.FM4.sound_id, 0xA4, "initialized FM4 did not receive SFX identity")

    timeline:beginTick(861, 1)
    timeline:queue(1, 0xD0)
    local special = timeline:cycle(9)[1]
    check(timeline:dispatch(special, {
        accepted = true, declared_roles = {"FM4"}, initialized_roles = {"FM4"},
        headers = headers({FM4 = true}, {FM4 = true})
    }), "initialized special SFX was discarded merely because normal SFX owns FM4")
    local blocked = timeline:closeTick(headers({FM4 = true}, {FM4 = true}))
    check(not blocked.requests[1].arbitration[1].acquired,
        "special FM4 record did not preserve acquired=false while normal SFX owns it")
    equals(blocked.requests[1].arbitration[1].final_owner.owner_class, "NORMAL_SFX",
        "blocked special FM4 did not retain normal final ownership")

    timeline:beginTick(862, 2)
    timeline:queue(1, 0xA7) -- Push returns at $722C4 when f_push_playing is already set.
    local push = timeline:cycle(9)[1]
    check(not timeline:dispatch(push, {
        accepted = false, declared_roles = {"FM3"}, initialized_roles = {}, headers = headers({FM4 = true})
    }), "already-playing push early return became an accepted request")
    equals(#timeline:closeTick(headers({FM4 = true})).requests, 0,
        "push early-return emitted a semantic request")

    timeline:beginTick(863, 3)
    timeline:queue(0, 0xA1)
    timeline:queue(1, 0xA2)
    local rejectedCycle = timeline:cycle(3)
    equals(#rejectedCycle, 2, "CycleSoundQueue did not surface both diagnostic candidates")
    timeline:queue(2, 0xA3)
    local later = timeline:cycle(4)
    equals(#later, 1, "rejected queue candidates were not cleared at CycleSoundQueue")
    equals(later[1].sound_id, 0xA3, "later slot reused stale rejected queue content")
    timeline:closeTick(headers({FM4 = true}))

    -- $88 uses Sound_PlayBGM's backup path ($71FD2..$7202C); it is a real request in the
    -- pinned interval, changes all fixed comparable music roles, then $72B14 restores $81.
    timeline:beginTick(864, 4)
    timeline:queue(0, 0x88)
    local oneUp = timeline:cycle(0)[1]
    check(timeline:dispatch(oneUp, {
        accepted = true, declared_roles = {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"},
        initialized_roles = {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"}, headers = headers()
    }), "$88 accepted BGM load did not become a music request")
    local oneUpFrame = timeline:closeTick(headers())
    equals(oneUpFrame.requests[1].sound_class, "MUSIC", "$88 was not classified as MUSIC")
    equals(oneUpFrame.owners.FM3.sound_id, 0x88, "$88 did not take over FM3")
    equals(oneUpFrame.owners.PSG3.sound_id, 0x88, "$88 did not take over PSG3")

    timeline:beginTick(865, 5)
    timeline:restoreMusic()
    local restored = timeline:closeTick(headers())
    equals(restored.owners.FM3.sound_id, 0x81, "$72B14 restoration did not reinstate pre-$88 music")
    equals(restored.owners.PSG3.sound_id, 0x81, "$72B14 restoration did not reinstate all fixed roles")
end

local function runPlaySegaLifecycle()
    -- Break caught: PlaySegaSound's return-address tampering bypasses $71C4C but leaves an
    -- UpdateMusic invocation open, corrupting the next complete tick.
    local lifecycle = Contract.newInvocationLifecycle()
    lifecycle:entry(0x00FF2000, 12)
    equals(lifecycle:playSegaAbnormalExit(), "reset", "PlaySega abnormal return did not reset lifecycle")
    equals(lifecycle:entry(0x00FF2010, 13), "open", "post-PlaySega UpdateMusic entry remained active")
    lifecycle:close()
end

local function runSelectedIdentityAndDormantQueueBoundaries()
    -- Break caught: $72222/$72342 repurpose D7 as DBF's track counter after PlaySoundID,
    -- replacing the selected queued SFX identity, or a pre-window cycle leaks into GHZ1.
    local timeline = Contract.newTimeline(0x81)
    timeline:beginTick(860, 0)
    timeline:queue(1, 0xA0)
    local selected = timeline:cycle(4)[1]
    Contract.assertSelectedIdentity(selected, 0xA0)
    local dbfTrackCount = 0
    check(not pcall(function() Contract.assertSelectedIdentity(selected, dbfTrackCount) end),
        "normal initializer accepted its DBF D7 track count as the selected sound ID")
    check(timeline:dispatch(selected, {
        accepted = true, declared_roles = {"FM3"}, initialized_roles = {"FM3"},
        headers = headers({FM3 = true}), init_loop_d7 = 0 -- realistic DBF loop counter, not a sound ID
    }), "accepted normal initialization was rejected")
    equals(timeline:closeTick(headers({FM3 = true})).requests[1].sound_id, 0xA0,
        "DBF D7 loop counter corrupted the original selected SFX ID")

    local queueBuffer = Contract.newQueueBuffer()
    queueBuffer:write(0, 0x81, 859)
    equals(#queueBuffer:cycle({0x81, 0, 0}, false, 0x80), 0,
        "dormant pre-window cycle exposed semantic candidates")
    equals(queueBuffer:baselineMusicId(), 0x81, "dormant cycle lost frame-860 $81 provenance")
    queueBuffer:write(1, 0xA2, 860)
    local retained = queueBuffer:cycle({0, 0xA2, 0}, true, 0x80)
    equals(#retained, 1, "first retained cycle did not discard dormant queue state")
    equals(retained[1].sound_id, 0xA2, "dormant queue observation poisoned first retained cycle")
end

local function runCycleSoundQueueDeferral()
    -- FixBugs = 0 source: $71F12 clears every slot; once $71F2C selected
    -- $A1 into v_sound_id, $71F22 requeues later $A2 into queue0. It must
    -- remain the same correlated request at the next observed queue0 cycle.
    local queueBuffer = Contract.newQueueBuffer()
    queueBuffer:write(0, 0xA1, 860)
    queueBuffer:write(1, 0xA2, 860)
    local firstCycle = queueBuffer:cycle({0xA1, 0xA2, 0}, true, 0x80)
    equals(#firstCycle, 2, "first CycleSoundQueue did not observe both queued requests")
    equals(firstCycle[1].queue_ordinal, 1, "first queued request ordinal was not retained")
    equals(firstCycle[2].queue_ordinal, 2, "later queued request ordinal was not retained")

    local timeline = Contract.newTimeline(0x81)
    timeline:beginTick(860, 0)
    for _, request in ipairs(firstCycle) do timeline:queue(request.slot, request) end
    local initialCandidates = timeline:cycle(4)
    equals(initialCandidates[1], firstCycle[1], "timeline replaced the selected $A1 correlation")
    equals(queueBuffer:consume(0xA1), firstCycle[1], "PlaySoundID did not resolve original $A1")
    check(timeline:dispatch(initialCandidates[1], {
        accepted = true, declared_roles = {"FM3"}, initialized_roles = {"FM3"}, headers = headers({FM3 = true})
    }), "selected $A1 did not dispatch")
    equals(timeline:closeTick(headers({FM3 = true})).requests[1].request_ordinal, 1,
        "selected $A1 did not receive the first semantic request ordinal")

    timeline:beginTick(861, 1)
    local secondCycle = queueBuffer:cycle({0xA2, 0, 0}, true, 0x80)
    equals(#secondCycle, 1, "next queue0 cycle did not expose deferred $A2")
    equals(secondCycle[1], firstCycle[2], "deferred $A2 was recreated instead of correlated")
    equals(secondCycle[1].queue_ordinal, 2, "deferred $A2 lost its original queue ordinal")
    timeline:queue(secondCycle[1].slot, secondCycle[1])
    local deferredCandidate = timeline:cycle(4)[1]
    equals(queueBuffer:consume(0xA2), firstCycle[2], "next PlaySoundID lost deferred $A2 identity")
    check(timeline:dispatch(deferredCandidate, {
        accepted = true, declared_roles = {"FM4"}, initialized_roles = {"FM4"}, headers = headers({FM4 = true})
    }), "deferred $A2 did not dispatch")
    equals(timeline:closeTick(headers({FM4 = true})).requests[1].request_ordinal, 2,
        "deferred $A2 did not receive the next semantic request ordinal")

    local rejected = Contract.newQueueBuffer()
    rejected:write(0, 0xA3, 862)
    equals(#rejected:cycle({0xA3, 0, 0}, true, 0x80), 1,
        "priority-rejected candidate was not initially observed")
    equals(#rejected:cycle({0, 0, 0}, true, 0x80), 0,
        "priority-rejected candidate leaked into a later cycle without PlaySoundID")
end

local function runDuplicateQueuedIdentityDeferral()
    -- Break caught: correlating PlaySoundID by sound byte chooses the later
    -- duplicate, although the first request filled v_sound_id and the second
    -- request followed the unconditional $71F22 queue0 deferral path.
    local queueBuffer = Contract.newQueueBuffer()
    queueBuffer:write(0, 0xA1, 860)
    queueBuffer:write(1, 0xA1, 860)
    local firstCycle = queueBuffer:cycle({0xA1, 0xA1, 0}, true, 0x80)
    equals(#firstCycle, 2, "duplicate-ID cycle did not retain both request identities")
    equals(firstCycle[1].queue_ordinal, 1, "first duplicate lost source-order ordinal 1")
    equals(firstCycle[2].queue_ordinal, 2, "second duplicate lost source-order ordinal 2")
    equals(queueBuffer:consume(0xA1), firstCycle[1],
        "PlaySoundID selected the later duplicate instead of source-order ordinal 1")

    local secondCycle = queueBuffer:cycle({0xA1, 0, 0}, true, 0x80)
    equals(#secondCycle, 1, "deferred duplicate did not reappear on the next cycle")
    equals(secondCycle[1], firstCycle[2], "deferred duplicate lost its original request identity")
    equals(secondCycle[1].queue_ordinal, 2, "deferred duplicate lost source-order ordinal 2")
    equals(queueBuffer:consume(0xA1), firstCycle[2],
        "next PlaySoundID did not consume the original deferred ordinal 2")
end

runQueueAndContention()
runLifecycleAndDiagnostics()
runSourceDerivedDispatchBoundaries()
runPlaySegaLifecycle()
runSelectedIdentityAndDormantQueueBoundaries()
runCycleSoundQueueDeferral()
runDuplicateQueuedIdentityDeferral()
print("S1_GAMEPLAY_AUDIO_TIMELINE_CONTRACT_OK")
