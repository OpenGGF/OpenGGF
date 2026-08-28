local contractPath = assert(arg[1], "contract path is required")
local Contract = dofile(contractPath)

local function assertEqual(expected, actual, label)
    assert(expected == actual, string.format("%s: expected %s, got %s",
        label, tostring(expected), tostring(actual)))
end

local function assertRoles(actual, expected, label)
    assertEqual(#expected, #actual, label .. " count")
    for index, role in ipairs(expected) do assertEqual(role, actual[index], label .. " role") end
end

-- Queue writes are observations, not admissions; the selected request keeps its original frame.
local delayed = Contract.newPriorityModel(0x87)
local delayedRequest = delayed:request(100, 0xB5, "normal_sfx", 0, 0x70)
local delayedService = delayed:service(103)
assertEqual(100, delayedService.decisions[1].request_frame, "delayed request frame")
assertEqual(103, delayedService.frame, "delayed service frame")
assert(delayedService.decisions[1].accepted, "delayed request must be accepted")

-- Duplicate bytes retain distinct queue ordinals; later source-order work is deferred to queue0.
local duplicates = Contract.newPriorityModel(0x87)
local first = duplicates:request(200, 0xB5, "normal_sfx", 0, 0x70)
local second = duplicates:request(200, 0xB5, "normal_sfx", 1, 0x70)
local duplicateFirst = duplicates:service(200)
assertEqual(first.ordinal, duplicateFirst.decisions[1].request_ordinal, "first duplicate identity")
assertEqual(second.ordinal, assert(duplicates:deferredQueue0()).ordinal, "deferred duplicate identity")
local duplicateSecond = duplicates:service(201)
assertEqual(second.ordinal, duplicateSecond.decisions[1].request_ordinal, "second duplicate identity")

-- Once v_sound_id is occupied, a later queue byte is physically copied to queue0 before
-- its priority is examined. Its priority decision therefore belongs to the next service.
local deferredPriority = Contract.newPriorityModel(0x87)
deferredPriority:setPriority(0x60)
local selectedHigh = deferredPriority:request(205, 0xA0, "normal_sfx", 0, 0x70)
local deferredLow = deferredPriority:request(205, 0xA1, "normal_sfx", 1, 0x10)
local firstPriorityService = deferredPriority:service(205)
assertEqual(1, #firstPriorityService.decisions, "only selected request dispatches in first service")
assertEqual(selectedHigh.ordinal, firstPriorityService.decisions[1].request_ordinal,
    "first eligible request selected")
assertEqual(deferredLow.ordinal, assert(deferredPriority:deferredQueue0()).ordinal,
    "later lower-priority byte deferred before lookup")
local deferredDecision = deferredPriority:service(206).decisions[1]
assertEqual(deferredLow.ordinal, deferredDecision.request_ordinal, "deferred priority identity")
assert(not deferredDecision.accepted and deferredDecision.reason == "lower_priority",
    "deferred lower priority is rejected on its own service")

local priority = Contract.newPriorityModel(0x87)
priority:setPriority(0x70)
priority:request(210, 0xA0, "normal_sfx", 0, 0x60)
local lower = priority:service(210).decisions[1]
assert(not lower.accepted and lower.reason == "lower_priority", "lower priority must be rejected")
priority:request(211, 0xA1, "normal_sfx", 0, 0x70)
assert(priority:service(211).decisions[1].accepted, "equal priority must replace")
priority:setPriority(0x55)
priority:request(212, 0xD0, "special_sfx", 0, 0x80)
assert(priority:service(212).decisions[1].accepted, "negative special priority still dispatches")
assertEqual(0x55, priority:priority(), "bit-7 priority must not replace global priority")

-- FixBugs=0 tests only queue0/queue1 to trigger a cycle, but cycles consume queue2 in source order.
local queue2 = Contract.newPriorityModel(0x87)
local third = queue2:request(220, 0xB5, "normal_sfx", 2, 0x70)
assertEqual(0, #queue2:service(220).decisions, "queue2-alone service")
assertEqual(third.ordinal, queue2:pending(2).ordinal, "queue2 remains pending")
local trigger = queue2:request(221, 0xA1, "normal_sfx", 1, 0x70)
local triggered = queue2:service(221)
assertEqual(trigger.ordinal, triggered.decisions[1].request_ordinal, "queue1 source-order selection")
assertEqual(third.ordinal, assert(queue2:deferredQueue0()).ordinal, "queue2 participates when triggered")

-- $88 saves music after clearing music override bits, kills normal SFX, and preserves special SFX.
local oneUp = Contract.newPriorityModel(0x87)
oneUp:setNormalSfx("FM3", 0xB5)
oneUp:setNormalSfx("PSG1", 0xA1)
oneUp:setSpecialSfx("FM4", 0xD0)
oneUp:setSpecialSfx("PSG3", 0xD0)
oneUp:request(3698, 0x88, "music", 0, 0x90)
local admitted = oneUp:service(3699)
assert(admitted.decisions[1].accepted and admitted.decisions[1].reason == "one_up_save",
    "$88 must be admitted through the save path")
assertEqual(0x87, oneUp:savedMusic().sound_id, "saved music identity")
assert(oneUp:savedMusic().override_bits_cleared, "saved music override bits must already be clear")
assertEqual(0, oneUp:normalSfxCount(), "$88 normal-SFX kills")
assertEqual(2, oneUp:specialSfxCount(), "$88 special-SFX preservation")
assertRoles(oneUp:effectiveMusicRoles(), {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"},
    "$88 effective roles")

-- Normal and special blocked paths have distinct global-priority side effects during one-up.
oneUp:setPriority(0x66)
oneUp:request(3702, 0xB5, "normal_sfx", 0, 0x70)
local blockedNormal = oneUp:service(3702).decisions[1]
assertEqual("one_up", blockedNormal.blocked_by, "normal one-up block")
assertEqual(0, oneUp:priority(), "normal blocked path clears priority")
oneUp:setPriority(0x55)
local blockedSpecialRequest = oneUp:request(3703, 0xD0, "special_sfx", 0, 0x80)
assertEqual("one_up", blockedSpecialRequest.blocked_by, "special write-time block classification")
local blockedSpecial = oneUp:service(3703).decisions[1]
assertEqual("one_up", blockedSpecial.blocked_by, "special one-up block")
assertEqual(0x55, oneUp:priority(), "special blocked path preserves global priority")
oneUp:request(3704, 0x88, "music", 0, 0x90)
assertEqual("repeated_one_up", oneUp:service(3704).decisions[1].reason, "repeated $88 outcome")
assertEqual(0x87, oneUp:savedMusic().sound_id, "repeated $88 keeps original save")

local restore = oneUp:beginOneUpRestore(3910)
assertEqual(0x87, restore.restored_music_id, "restored music identity")
assertEqual(40, restore.fade_steps, "restore attenuation count")
assertRoles(restore.effective_roles, {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"},
    "$87 restored roles")
oneUp:setPriority(0x44)
oneUp:request(3910, 0xB5, "normal_sfx", 0, 0x70)
assertEqual("fade_in", oneUp:service(3910).decisions[1].blocked_by, "normal fade block")
assertEqual(0, oneUp:priority(), "normal fade block clears priority")
oneUp:setPriority(0x33)
oneUp:request(3911, 0xD0, "special_sfx", 0, 0x80)
assertEqual("fade_in", oneUp:service(3911).decisions[1].blocked_by, "special fade block")
assertEqual(0x33, oneUp:priority(), "special fade block preserves global priority")
local attenuationSteps, services = 0, 0
while oneUp:fadeActive() do
    local step = oneUp:advanceFadeService()
    services = services + 1
    if step.attenuated then attenuationSteps = attenuationSteps + 1 end
    assert(services <= 121, "40-step fade did not terminate at the shipped cadence")
end
assertEqual(40, attenuationSteps, "fade attenuation services")
assertEqual(121, services, "fade delay cadence")

local fadeOut = Contract.newPriorityModel(0x87)
fadeOut:setFadeOut(true)
fadeOut:setPriority(0x42)
fadeOut:request(3920, 0xB5, "normal_sfx", 0, 0x70)
assertEqual("fade_out", fadeOut:service(3920).decisions[1].blocked_by, "normal fade-out block")
assertEqual(0, fadeOut:priority(), "normal fade-out clears priority")
fadeOut:setPriority(0x42)
fadeOut:request(3921, 0xD0, "special_sfx", 0, 0x80)
assertEqual("fade_out", fadeOut:service(3921).decisions[1].blocked_by, "special fade-out block")
assertEqual(0x42, fadeOut:priority(), "special fade-out preserves global priority")

local oracle = Contract.extraLifeOracle()
assertEqual(0x88, oracle[3698].queued_music_id, "3698 oracle")
assertRoles(oracle[3699].effective_roles, {"FM3", "FM4", "FM5", "PSG1", "PSG2", "PSG3"},
    "3699 oracle")
assertEqual("one_up", oracle[3702].normal_sfx_blocked_by, "3702 oracle")
assertEqual(0x87, oracle[3910].restored_music_id, "3910 oracle")

-- Song ownership comes from header loop counts, including DAC/FM6 and zero-PSG songs.
assertRoles(Contract.deriveMusicRoles(7, 3),
    {"DAC", "FM1", "FM2", "FM3", "FM4", "FM5", "FM6", "PSG1", "PSG2", "PSG3"},
    "seven FM/DAC header")
assertRoles(Contract.deriveMusicRoles(6, 0),
    {"DAC", "FM1", "FM2", "FM3", "FM4", "FM5"}, "zero PSG header")
local shippedRestore = Contract.fixBugsZeroDacRestore(7)
assert(not shippedRestore.writes_dac_disable and not shippedRestore.restores_dac_pan,
    "FixBugs=0 must omit bug-fixed restore writes")
local sevenTrackRestore = Contract.newPriorityModel(0x89)
sevenTrackRestore:request(3950, 0x88, "music", 0, 0x90)
assert(sevenTrackRestore:service(3950).decisions[1].accepted,
    "seven-FM/DAC song must enter the same one-up save path")
assertEqual(0x89, sevenTrackRestore:beginOneUpRestore(4161).restored_music_id,
    "seven-FM/DAC restore identity")

-- Stop/death/restart/act transitions clear pending source state through explicit lifecycle records.
for _, kind in ipairs({"stop_all", "death", "restart", "act_transition"}) do
    local transitions = Contract.newPriorityModel(0x87)
    transitions:request(4000, 0xB5, "normal_sfx", 0, 0x70)
    transitions:setPriority(0x70)
    local event = transitions:transition(4001, kind)
    assertEqual(kind, event.kind, kind .. " lifecycle kind")
    assertEqual(0, transitions:priority(), kind .. " clears priority")
    assertEqual(0, transitions:pendingCount(), kind .. " clears queues")
end
local stopCommand = Contract.newPriorityModel(0x87)
stopCommand:request(4010, 0xB5, "normal_sfx", 2, 0x70)
stopCommand:request(4010, 0xE4, "command", 0, 0x7F)
local stopped = stopCommand:service(4010).decisions[1]
assert(stopped.accepted and stopped.reason == "stop_all", "$E4 must run the stop-all path")
assertEqual(0, stopCommand:pendingCount(), "$E4 clears pending queue state")

-- Frame/service cardinality is independent: transition gaps can have zero or multiple services.
local ledger = Contract.newFrameServiceLedger(5000, 5003)
ledger:record(5001, {kind = "UpdateMusic", ordinal = 1})
ledger:record(5001, {kind = "UpdateMusic", ordinal = 2})
ledger:finish()
assertEqual(0, #ledger:services(5000), "zero-service frame")
assertEqual(2, #ledger:services(5001), "multiple-service frame")
assertEqual(0, #ledger:services(5002), "terminal gap frame")

-- Stack-changing exits cannot silently abandon an invocation, including $E1.
local lifecycle = Contract.newInvocationLifecycle()
assertEqual("open", lifecycle:entry(0xFF1000, 6000), "service open")
assertEqual("retry", lifecycle:entry(0xFF1000, 6000), "DAC-busy retry")
assertEqual("continue", lifecycle:stopTrackDoubleReturn(0x71BE6), "track helper continuation")
assertEqual("close", lifecycle:stopTrackDoubleReturn(0x123456), "track helper service close")
assertEqual("open", lifecycle:entry(0xFF1000, 6001), "second service open")
assertEqual("abnormal_close", lifecycle:playSegaOutcome(true), "$E1 observed outcome")
assertEqual("outside_armed_epoch", Contract.playSegaOutcome(false), "$E1 absence outcome")
assertEqual("open", lifecycle:entry(0xFF1000, 6002), "normal-return service open")
assertEqual("close", lifecycle:close(), "ordinary DoStartZ80 return")
assertEqual("open", lifecycle:entry(0xFF1000, 6003), "BGM service open")
assertEqual("close", lifecycle:playBgmDoubleReturn(), "Sound_PlayBGM double return")
assertEqual("open", lifecycle:entry(0xFF1000, 6004), "restore service open")
assertEqual("close", lifecycle:fadeInToPreviousDoubleReturn(), "cfFadeInToPrevious double return")
assertEqual("open", lifecycle:entry(0xFF1000, 6005), "special-stop service open")
assertEqual("continue", lifecycle:stopSpecialDoubleReturn(0x71C38), "special-stop continuation")
assertEqual("close", lifecycle:stopSpecialDoubleReturn(0x123456), "special-stop close")

-- Native DAC ownership is explicitly Z80-typed and independent of M68K UpdateMusic.
local dac = Contract.nativeDacServiceContract()
assertEqual("Z80", dac.dpcm.source_cpu, "DPCM owner")
assertEqual(0x77, dac.dpcm.begin_pc, "DPCM begin")
assertEqual(0xAC, dac.dpcm.end_pc, "DPCM completion")
assertEqual(2, dac.dpcm.ym2a_writes, "DPCM paired writes")
assertEqual("Z80", dac.sega.source_cpu, "SEGA PCM owner")
assertEqual(0xC1, dac.sega.begin_pc, "SEGA begin")
assertEqual(0xD0, dac.sega.end_pc, "SEGA completion")
assert(not dac.requires_m68k_parent, "asynchronous Z80 services must not require an M68K parent")

print("S1_COMPLETE_RUN_AUDIO_CONTRACT_OK")
