# MGZ event publication — 2026-11-15 (follow-up 2026-11-16)

Baseline: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (`develop`).
Reviewed heads: `0e1e816c92`, then `5ca768471b1ac199e25ec29f17efae9bf66303f4`.
Outcome: **PASS — 1792 focused/rewind/guard tests; all eleven replay JSON reports exactly baseline-identical.**
Scope: MGZ; shared S3kScreenShake API unchanged. No categories/full-suite, merge or PR watch.
[Original implementation history](https://github.com/OpenGGF/OpenGGF/blob/0e1e816c92a4b711cf670f4ddb03739135b9ebf5/docs/architecture/audits/2026-11-15-s3k-mgz-event-publication.md) and [first review history](https://github.com/OpenGGF/OpenGGF/blob/5ca768471b1ac199e25ec29f17efae9bf66303f4/docs/architecture/audits/2026-11-15-s3k-mgz-event-publication.md) retain all earlier commands/results.

## Owners and ROM evidence

- `ShakeScreen_Setup` (sonic3k.asm:104234-104255) owns flag, offset and last offset. Negative flags persist; positive flags decrement; P1 death suppresses sampling.
- `MGZ1_ScreenEvent` (sonic3k.asm:106303-106307) and `MGZ2_ScreenEvent` (sonic3k.asm:106436-106450) consume the preceding offset. MGZ2 reasserts continuous mode from Events_fg_0.
- `MGZ1BGE_Normal` (sonic3k.asm:106331-106355) and `MGZ2BGE_Normal` (sonic3k.asm:107100-107122) prepare the next sample.
- `MGZ1_Deform` (Lockon S3/Screen Events.asm:989-1024) reads then adds $500 at +$01C; `MGZ2_BGDeform` (Lockon S3/Screen Events.asm:1090-1181) adds $800 then reads +$038.
- `loc_51A04` (sonic3k.asm:107198-107209) clears cloud phase and latches freeze. C→8 does not clear that latch. `loc_51B1C` (sonic3k.asm:107323-107333) tests the preceding rise offset and writes timed $E.
- `loc_3461E` (sonic3k.asm:71011-71020) and `loc_346A2` (sonic3k.asm:71053-71059) write -1 during platform motion and clear it at completion.
- `MGZ2_BackgroundInit` (sonic3k.asm:107021-107078) allocates the Sonic rise SST only before X=$3800. Finished Sonic/state-C loads allocate none.
- `loc_51656` (sonic3k.asm:106795-106803) resets bounds and Events_bg+$10, leaving global shake untouched. The game-wide boolean still has LBZ readers; compatibility/FBZ writes remain.

Events own publication, cloud lifetime, collision differences, redraw continuation and shake.
The runtime adapter is stateless; SwScrlMgz keeps only derived picture output/buffers.
One validated same-build payload contains the scalar state and all three shake words; no migration.
The original real-load red witness drew twice and changed shake 0→2; repeated projections now consume nothing.
The transition's timer/position remain event-owned; its ordinary allocated object owns admission/dispatch.
ObjectManager captures that worker's identity; composed restore/recreate resumes exactly one timer tick.

## Rejected designs

1. Capture SwScrlMgz: leaves rendering able to advance gameplay/consume requests.
2. Add another producer/codec to MgzZoneRuntimeState: splits publication/restore ownership.
3. Derive clouds from frame index: cannot represent persistent freeze/clear lifetime.
4. Revert the arm correction, or retain fitted waits: restores green by cancelling proven ROM errors.
5. Initialize the transition eagerly in the boss callback: ignores allocation failure and lower/already-visited slots. Ordinary ObjectManager allocation supplies the native first-dispatch decision.

## Original review findings

| Finding | Final disposition |
| --- | --- |
| 1 | Fixed. Refresh range is 0..8 inclusive. Real MGZ2 trigger-frame capture validates/restores byte-exactly. Countdown tests accept all 0..8 and reject -1/9. |
| 2 | Fixed and retained. FG arms $14 before BG prepares flag $13 / offset -5; FG still applies its preceding 0. Opening is arm+20, explicitly absent at arm+19. The old extra request dispatch is now removed too. |
| 3 | Fixed. Both completed late-load branches latch finished; the helper exits before camera lock, CRASH or flag 14. Two red parameter cases now pass. |
| 4 | Fixed. MGZ joins HCZ in validation before super.restore. Corrupt-payload tests assert every base scalar, both event arrays and extras. MHZ stays unchanged because the whole transaction is rejected. |
| 5 | Fixed. Retreat preserves global shake. The negative-flag retreat witness remains green. |
| 6 | Coverage restored through publication: state-8 bands/locked terrain, 512px period, provider routing, C→8 freeze, both-plane shake. These coverage-only cases passed before production fixes. |
| 7 | Rejected. RELOAD_TARGET_LEVEL reaches LevelActTransitionExecutor.execute, which unconditionally reinitializes events; manager.initLevel creates a new MGZ instance. init clears phase; MGZ2 first adds $800. preserveLevelGamestate retains no events instance. |

Validator audit: exact header/version/scalar width and canonical booleans; BG routines 0/4/8/C;
boss cursor MIN sentinel or masked 0..$FFFF; refresh 0..8; rise offsets 0..$220 (Sonic $1D0,
Knuckles $220); camera/difference/applied/shake values signed words. Other fixed-point/cloud
ints wrap without fitted limits. The former refresh maximum 7 was the only false rejection found.

## Causal follow-up: why the correct arm change broke green traces

Reverting only finding 2 restored both traces to zero errors, isolating its effect.
The finding itself matches `loc_512FA` (sonic3k.asm:106465-106469), `ScreenEvents`
(sonic3k.asm:102279-102301) and BG setup. The baseline instead contained compensating errors:

- `loc_6C354` (sonic3k.asm:142813-142845) installs $78 and returns; `Obj_Wait` (sonic3k.asm:178040-178049) returns at zero and calls `loc_6C3EC` (sonic3k.asm:142850-142859) on signed underflow. Engine setup spent a tick and its wait exited at zero: impact was two dispatches early. Two independent red witnesses proved 119 vs 120 and premature descent at zero. Setup/underflow are now correct; the initial-execution latch is captured.
- Native aux dates boss routine $E/impact at short-trace row 32537, gfc 32531. The read-only probe dated engine impact at 32535/gfc32529, arm 32536 and opening 32556. Native carriers appear unexecuted at 32557, first running at 32558. Thus the changed Tails Y at 32557 was an early terrain clear/contact, not evidence against the ROM countdown.
- `LevelLoop` (sonic3k.asm:7916-7930) runs Process_Sprites before ScreenEvents; `loc_6C4BE` (sonic3k.asm:142919-142931) writes Events_fg_4 in that same loop. Removing collapseRequestObserved's extra dispatch gives corrected impact/arm 32537 and opening 32557, independently corroborated by matching gfc and aux. Zone-slice counterparts are 35427/35447.
- Correcting that chain exposed rescue two dispatches late at 32896/35786. Native AllocateObject admits Obj_MGZ2_BossTransition in a later free slot on the impact pass; `loc_16328` (sonic3k.asm:30259-30266) immediately decrements $168 to $167. The synthetic engine bridge started next pass and returned before decrementing. A red timer witness proved 360 vs 359. A real allocated worker now uses ObjectManager's slot order/failure behavior, with no extra bridge tick. Recreate/restore tests prove one worker and one suffix tick.
- The resulting later air-attack frontier exposed an existing fitted parent wait: $21 instead of $1F at `loc_6C57C` (sonic3k.asm:142985-142990). The two supposed child phases have no ROM authority to extend this parent timer. A red witness proved 33 vs 31; the literal $1F is restored. Both full traces then match baseline exactly without probes.

Tests that encoded the old delay were corrected: initial descent includes setup/zero dispatches;
transition setup already spends a tick; the visible-Tails setup test explicitly keeps P1 on-screen
so the later loc_16384 rescue branch cannot independently change CPU state. No comparison,
fixture, tolerance, bootstrap, hardware timing input or shared shake behavior changed.
These are existing catalogue lessons (signed countdowns and cancelling early-trigger compensations), not a new skill rule.

## Commands and outcomes (this follow-up)

All test Maven commands ran in the foreground through Q = `python3 tools/testing/maven_queue.py -Dmse=off`;
G = `-Dopenggf.headless.gl=surfaceless-egl`. Every test command found all ROMs, missing=none.
Java 25.0.5.0.3; S3K SHA-1 reverified CFBF98C36C776677290A872547AC47C53D2761D6.
Selectors are literal CSV expansions; output paths below are under target/.

- P = `TestS3kMgzTraceReplay,TestS3kMgzZoneSliceTraceReplay`.
- C = `TestMgzDrillingRobotnikInstance,TestSonic3kMgz2CollapseEvents`.
- E = `TestSonic3kMgz2EndBossEvents`.
- T = `*Mgz*TraceReplay,TestS3kMgzF498AirRollPhysics`.
- B = `SwScrlMgzTest,TestSonic3kMgz2QuakeEvents,TestSonic3kMgz2BgRiseEvents,TestSonic3kMgz2CollapseEvents,TestSonic3kMgz2EndBossEvents,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,TestS3kMgzScreenPublication,TestMgzComposedRewindState,TestS3kZoneRuntimeStateAdapters,TestS3kMgz2BgRiseHeadless,TestMgzMinibossInstance,TestS3kMgzTriggerPlatformObject,TestMgzDrillingRobotnikInstance,TestMgzEndBossKnuxInstance,TestMgzEndBossKnuxAdmission,TestMgzEndBossHandoffHeadless`.
- R = `TestMgzDrillingRobotnikInstance#endBossSetupDispatchDoesNotSpendItsFreshWait+endBossInitialWaitCallsBackOnlyAfterSignedUnderflow,TestSonic3kMgz2CollapseEvents#bossRequestIsConsumedByTheFollowingForegroundPassInTheSameLoop,TestS3kMgzTraceReplay`.
- W = `TestMgzDrillingRobotnikInstance#endBossAirRiseUsesNativeWait,TestSonic3kMgz2EndBossEvents,TestSonic3kMgz2CollapseEvents#transitionUsesItsAllocatedSlotAndRestoresWithoutADuplicateTick`.

Each test command is `Q G -Dtest=<selector> test`, with the following additional properties.
Results are tests/failures/errors/skips. The working candidate is based on 5ca768471b; final runtime code is this commit's contents.

| Selector / candidate | Additional properties | Result; log |
| --- | --- | --- |
| P, arm correction alone reverted | trace.print.summary=true; openggf.surefire.reports=target/mgz-isolate-arm/surefire; openggf.trace.reports=target/mgz-isolate-arm/trace-reports | 2/0/0/0; mgz-isolate-arm.log |
| P, reviewed code + read-only event probe | trace.context.diagnosticChars=full; report properties as above with mgz-probe | 2/2/0/0; mgz-probe.log |
| R, reviewed code + row observer | report properties as above with mgz-cause-red | 4/4/0/0; mgz-cause-red.log |
| C,P, initial-wait/request correction | trace.print.summary=true; report properties as above with mgz-cause-fix | 87/2/0/0; mgz-cause-fix.log; new first tails_x 32896/35786, 1031/1203 grouped errors |
| TestSonic3kMgz2EndBossEvents#transitionSetupFallsThroughToItsFirstWaitTick | none | 1/1/0/0; mgz-rescue-red.log |
| C,E,P, native transition slot correction | trace.print.summary=true; report properties as above with mgz-slot-fix | 126/4/0/0; mgz-slot-fix.log; two old timing-witness failures and air-frontier x_speed 33567/36492, 886/1202 groups |
| W, parent-rise red witness | openggf.surefire.reports=target/mgz-rise-red/surefire | 41/1/0/0; mgz-rise-red.log; slot/rewind and revised event witnesses pass |
| T, final unobserved code | trace.print.summary=true; report properties as above with mgz-final-traces | 12/8/0/0; mgz-final-traces.log; every report exactly baseline-identical |
| B, final source/test revision | openggf.surefire.reports=target/mgz-final-focused/surefire | 383/0/0/0; mgz-final-focused.log |

Saved-report triage uses `Q exec:java -Dexec.mainClass=com.openggf.tools.TraceTriageTool`
with `-Dexec.args=--report "target/mgz-probe/trace-reports/trace/<owner>.json" --fixture "<fixture>"`.
Owners a5c15b2edbc7b5a5 / ac6ab7427e2a2ac6 use `s3k_mgz1-single-` prefix;
fixtures src/test/resources/traces/s3k/mgz / mgz_completerun. Both commands completed successfully (0 tests); logs mgz-triage-short.log / mgz-triage-slice.log.

## Baseline comparison and remaining limits

Retained clean detached e676 baseline's identical T selector/order, Java, MSE/EGL, fixtures,
pom/harness and verified ROM qualify its completed 12/8/0/0 job for reuse.
All eleven final JSON objects are exactly equal, including errors, warnings, bootstrap, fields,
compared rows and completion gates. Short MGZ: 0 errors, 35861 compared / 35912 planned;
zone slice: 0 errors, 39183 / 39398. Frame-498 test also passes.
Unchanged grouped-error counts: KnucklesSuperMgz2 3103; KnucklesSuperMgz 1365;
MegaMgz2 2; MegaMgz3 919; MegaMgz 529; SonicTailsMgz 762;
TailsFullChainMgz2 1549; TailsFullChainMgz3 2902; TailsFullChainMgz 0.
These counts are grouped spans, not raw mismatching rows/fields; eight inherited failures remain.

Left undone: onset st writes in `Tunnelbot_StartRumbleWait` (sonic3k.asm:184875-184880),
`MGZMiniboss_StartRumbleWait` (sonic3k.asm:184977-184982) and `MGZMiniboss_StartDropFromCeiling` (sonic3k.asm:184997-185005);
opening's skipped first scroll step (`loc_513FA`, sonic3k.asm:106557-106562 → `loc_5140C`, sonic3k.asm:106563-106574);
`Do_ShakeSound` gating (sonic3k.asm:106407-106426). These predate this PR and remain record-only.
The inherited pending carrier-scroll/delete projections also remain; they compensate the skipped opening step and are outside this causal repair. The former record-only request delay was necessarily fixed in the causal chain above.
MGZ-owned strict checking passes (73 ok, 16 unverifiable), including three repaired inherited boss citations.
Whole touched-file checking reports 163 stale, 2 out-of-range and 9 unresolved-file citations, all on unchanged historical/shared lines (zero added-line errors); these include the three previously reported non-MGZ manager citations. The unrelated Sonic CD checkout warning remains. No pins moved. Whitespace passes; raw logs stay in target/.
Smoke, independent re-review, wider viewport/donor/team, Knuckles BG movement and native pixel/audio certification remain with the lead.
This follow-up: queue 569 s; Maven execution 940.815 s; user wait 0; other time unmeasured. All jobs completed. Disposable scratch is removed at handoff.

## Lead smoke follow-up (2026-11-16, parent d0fecbca29)

1. Worker coverage is GRAPH, naming TestSonic3kMgz2CollapseEvents: its real ObjectManager/event snapshot recreates one worker, restores the timer and resumes one tick. This is required external-owner coverage, not an exemption or an empty isolated probe.
2. Worker retirement uses ObjectLifetimeOps.deleteNoRespawn; no lifecycle allowance added.
3. Removed both retired currentMgzRuntimeState access permissions; synthetic guard fixtures now exercise the four remaining permissions and still reject caller substitution/multiplicity/removal.
4. Removed the new validator catch and both MGZ restore catches, plus three retired inventory rows. Fixed-schema preflight checks final-array length words against a fresh schema capture before decode; canonical scalar checks remain. Unexpected schema/runtime failures propagate. An exhaustive scalar-word overwrite witness rejects invalid input before owner mutation, without throwing from validation. No new catch allowance was added.
5. Music remains tied to loc_6C3EC (sonic3k.asm:142850-142859), after setup plus 120 non-underflow Obj_Wait dispatches, including zero. Tests assert silence through zero and no movement on callback, rather than merely increasing a loop bound. Visibility is independent: Obj_MGZEndBoss (sonic3k.asm:142785-142792) always draws/touches after its routine. The Sonic $A1 hidden-wait gate is removed, including the zero-pass anomaly; Knuckles' distinct continuation gate is preserved. A new red witness failed on setup dispatch 0 and now proves the waiting pose through zero.

S = `TestEveryObjectRewindRoundTrip,TestRemainingRewindTailInventory,TestRewindHarnessCoverageRatchet,TestZoneEventRuntimeAccessGuard,TestObjectPhysicsStandardizationGuard,TestBroadCatchSwallowingGuard,TestS3kMgzBossMusicTransition`; F = S followed by B above. All commands use Q G and `-Dtest=<selector> test`, without -Pguards; output paths below are under target/.

| Selector | Additional properties | tests/failures/errors/skips; log |
| --- | --- | --- |
| S, red at d0fecbca29 | openggf.surefire.reports=target/mgz-smoke-red/surefire | 1407/9/0/0; mgz-smoke-red.log |
| TestS3kMgzBossMusicTransition#endBossDrawsThroughoutItsInitialWaitIncludingZero | none; G omitted | 1/1/0/0; mgz-visibility-red.log |
| S,TestSonic3kMgz2CollapseEvents,TestMgzComposedRewindState | openggf.surefire.reports=target/mgz-smoke-fix/surefire | 1431/4/0/0; mgz-smoke-fix.log; retired synthetic accesses remained in guard fixtures |
| F | openggf.surefire.reports=target/mgz-smoke-final/surefire | 1792/3/0/0; mgz-smoke-final.log; one fixture signature assertion and two Knuckles visibility regressions, corrected |
| F | openggf.surefire.reports=target/mgz-smoke-final2/surefire | 1792/0/0/0; mgz-smoke-final2.log |
| T | trace.print.summary=true; report properties as above with mgz-smoke-traces | 12/8/0/0; mgz-smoke-traces.log; all eleven full JSON objects exactly equal e676 baseline |

Final log explicitly executes TestZoneEventRuntimeAccessGuard (12), TestObjectPhysicsStandardizationGuard (35), TestBroadCatchSwallowingGuard (4), TestEveryObjectRewindRoundTrip (1347), both inventory/ratchet classes (1 each), and the music class (8): all zero failures/errors/skips. ROM discovery missing=none throughout. The inherited replay/coverage/citation limits above remain; no new replay mismatch. All local jobs completed.
Lead-smoke follow-up time: queue 331 s; Maven execution 486.279 s; user wait 0; other time unmeasured. MGZ-owned strict citations: 81 ok, 17 unverifiable; no stale entries.
