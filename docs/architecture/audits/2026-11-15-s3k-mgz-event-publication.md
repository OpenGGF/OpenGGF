# MGZ event publication — 2026-11-15

Trace baseline: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (`develop`).
Review base: `0e1e816c92a4b711cf670f4ddb03739135b9ebf5`; corrections verified 2026-11-16.
Outcome: **ISSUES — focused checks pass; two new trace failures block readiness.**
Scope: MGZ; shared shake API unchanged. No categories/full-suite, merge or PR watch.

## Owners and ROM evidence

- `ShakeScreen_Setup` (sonic3k.asm:104234-104255) owns flag, offset and last offset. Negative flags persist; positive flags decrement; P1 death suppresses sampling.
- `MGZ1_ScreenEvent` (sonic3k.asm:106303-106307) and `MGZ2_ScreenEvent` (sonic3k.asm:106436-106450) consume the preceding offset. MGZ2 also reasserts continuous mode from Events_fg_0.
- `MGZ1BGE_Normal` (sonic3k.asm:106331-106355) and `MGZ2BGE_Normal` (sonic3k.asm:107100-107122) prepare the next sample.
- `MGZ1_Deform` (Lockon S3/Screen Events.asm:989-1024) reads then adds $500 at +$01C. `MGZ2_BGDeform` (Lockon S3/Screen Events.asm:1090-1181) adds $800 then reads +$038.
- `loc_51A04` (sonic3k.asm:107198-107209) clears the cloud accumulator and latches freeze. C→8 re-entry does not clear that latch.
- `loc_51B1C` (sonic3k.asm:107323-107333) tests the preceding rise offset and writes timed $E on completion.
- `loc_512FA` (sonic3k.asm:106465-106469) writes $14 in FG; `ScreenEvents` (sonic3k.asm:102279-102301) dispatches BG afterward. That BG pass prepares flag $13 / offset -5. FG still retains its preceding offset on the arm frame. The previous audit's contrary ordering claim was wrong.
- `loc_3461E` (sonic3k.asm:71011-71020) and `loc_346A2` (sonic3k.asm:71053-71059) write -1 during platform motion and clear it at completion.
- `MGZ2_BackgroundInit` (sonic3k.asm:107021-107078) allocates the Sonic rise SST only before X=$3800. Finished Sonic and state-C loads allocate none.
- `loc_51656` (sonic3k.asm:106795-106803) resets bounds and Events_bg+$10, leaving the global shake word untouched.
- The game-wide shake boolean still has LBZ readers; its compatibility writes and the manager's FBZ-routed flag remain unchanged.

Events own the publication, cloud lifetime, collision differences and S3kScreenShake.
The runtime adapter is stateless; SwScrlMgz retains only derived picture output/buffers.
One framed same-build payload contains scalar state and all three shake words.
The original real-load red witness drew twice and changed shake 0→2; it now passes,
as does registered restore/forward publication. No scalar-only migration is provided.

## Rejected ownership designs

1. Capture state in SwScrlMgz: would leave rendering able to advance gameplay and consume requests.
2. Add another producer/codec to MgzZoneRuntimeState: would split publication and restore ownership.
3. Derive clouds solely from a frame index: cannot represent the persistent freeze/clear lifetime.

## Review corrections

| Finding | Disposition and evidence |
| --- | --- |
| 1 | Fixed. Refresh range is 0..BG_RISE_REFRESH_FOLLOWUP_FRAMES inclusive (8). The real MGZ2 trigger-frame witness captures counter 8, validates and restores byte-exactly. All other checks audited below. |
| 2 | Fixed. $14 is armed before BG setup, giving flag $13 and prepared -5 on the arm frame; applied FG remains its preceding 0. The opening witness requires arm+20; startup calls change $17→$16. The inherited observation delay remains. |
| 3 | Fixed. Both finished late-load branches set the object-finished latch; the SST helper exits before its motion-start camera lock. Two red parameter cases proved false CRASH, flag 14 and min-X changes; all now absent. |
| 4 | Fixed. MGZ joins HCZ in validation before super.restore. The corrupt-payload witness now changes/asserts every base scalar and both event-data arrays, plus all extras. MHZ remains false/unchanged because invalid MGZ rejects the whole transaction; the earlier flipped assertions alone did not prove base atomicity. |
| 5 | Fixed. Retreat no longer clears the global shake flag; a negative-flag retreat witness failed first and now passes. |
| 6 | Coverage restored. State-8 cloud bands plus locked terrain, 512px period, provider routing, C→8 freeze and both-plane shake are tested through publication. These coverage-only tests passed on the reviewed implementation; no production defect was manufactured to make them red. |
| 7 | Rejected as a production regression. MGZ requests RELOAD_TARGET_LEVEL. LevelSeamlessTransitionExecutor.executeReload sends both hosts to LevelActTransitionExecutor.execute; its unconditional initLevelEventsForCurrentZoneAct calls Sonic3kLevelEventManager.initLevel, which creates a new MGZ events instance. init clears cloudAccumulator; act 2 first increments zero by $800. preserveLevelGamestate does not preserve the events instance. |

### Validator range audit

| Check | Stored-value evidence |
| --- | --- |
| Header/version/scalar width | Exact constructor schema width, remaining shake bytes and canonical scalar recapture; no old naked payload admission. |
| BG routine / published routine | Native states 0,4,8,C; all engine writers store within this set. |
| Published boss cursor | Integer.MIN_VALUE marks inactivity; active cursor is masked with & $FFFF. |
| Refresh continuation | Trigger stores 8, later positive countdown decrements to 0. Former max 7 was the only false rejection found. Tests accept 0..8 and reject -1/9. |
| Rise/published offset | Nonnegative; Sonic clamps at $1D0; the ROM's Knuckles finished offset is $220. The $220 upper bound admits both. |
| Camera/collision/applied offset and three shake words | Camera/difference writers cast to short; shake flag is sign-extended word and samples are signed bytes. Signed-word validation admits all produced values. |
| Other scalars | Canonical booleans are checked by recapture; full-width fixed-point/cloud ints retain their wrapping bit patterns rather than imposing fitted limits. |

## Left undone (explicit record-only scope)

- Missing onset st writes: `Tunnelbot_StartRumbleWait` (sonic3k.asm:184875-184880), `MGZMiniboss_StartRumbleWait` (sonic3k.asm:184977-184982), `MGZMiniboss_StartDropFromCeiling` (sonic3k.asm:184997-185005).
- collapseRequestObserved's extra observation dispatch; compare `loc_6C4BE` (sonic3k.asm:142919-142931).
- Opening setup returns instead of executing the first scroll step: `loc_513FA` (sonic3k.asm:106557-106562) falls through to `loc_5140C` (sonic3k.asm:106563-106574).
- Do_ShakeSound gating differs; `Do_ShakeSound` (sonic3k.asm:106407-106426) tests P1 routine, Events_bg+$16, signed global flag and the level clock.

All four predate this PR (checked against e676). They were not changed during review.
Bounded inspection also found the existing carrier suppliers project a pending event
step (`collapseSolidObjectPassScroll`, `collapseFinishesOnPendingEventStep`); these
also exist at e676 and remain unchanged. No causal repair for the new handoff
failures was established. The verified countdown was retained; no tolerance or
route exception was added to hide these failures. Further collapse scheduling/consumer work is needed.

## Commands and outcomes

Java 25; every admitted command found all ROMs (missing=none). Q denotes
`python3 tools/testing/maven_queue.py -Dmse=off`; G denotes
`-Dopenggf.headless.gl=surfaceless-egl`. Literal CSV selector expansions:

- B = `SwScrlMgzTest,TestSonic3kMgz2QuakeEvents,TestSonic3kMgz2BgRiseEvents,TestSonic3kMgz2CollapseEvents,TestSonic3kMgz2EndBossEvents,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,TestS3kMgzScreenPublication,TestMgzComposedRewindState,TestS3kZoneRuntimeStateAdapters,TestS3kMgz2BgRiseHeadless,TestMgzMinibossInstance,TestS3kMgzTriggerPlatformObject`
- R = `TestS3kMgzScreenPublication,TestMgzComposedRewindState,TestSonic3kMgz2CollapseEvents,TestSonic3kMgz2BgRiseEvents,TestSonic3kMgz2QuakeEvents,TestSonic3kLevelEventRewindSnapshot,SwScrlMgzTest`
- T = `*Mgz*TraceReplay,TestS3kMgzF498AirRollPhysics`.

Results are tests/failures/errors/skips. All local Maven jobs completed.

| Command | Result | Log under target/ |
| --- | --- | --- |
| `Q G -Dtest=R test` on reviewed production code | 110/7/0/0. Six effective failing cases; initial real-trigger test stopped at a setup precondition. Coverage tests 8/8 pass. | mgz-review-red.log |
| `Q G '-Dtest=TestS3kMgzScreenPublication#triggerFramePayloadValidatesAndRestoresExactly' test` | 1/1/0/0. After normal entry, actual trigger counter 8 is rejected and restore retains the changed offset. | mgz-review-trigger-red.log |
| `Q G -Dtest=B test` after corrections | 277/0/0/0 | mgz-review-focused.log |
| `Q G -Dtest=T test` after corrections | 12/10/0/0; two new failures below | mgz-review-traces.log |

The retained clean e676 baseline job used the identical T command, concrete order,
Java 25.0.5.0.3, MSE off and surfaceless EGL. ROM SHA-1 was reverified as
CFBF98C36C776677290A872547AC47C53D2761D6. Fixtures, pom and trace harness are unchanged.
Its 12/8/0/0 result was reused rather than rerunning a matching expensive job.
[Original implementation's complete command history and intermediate corrections](https://github.com/OpenGGF/OpenGGF/blob/0e1e816c92a4b711cf670f4ddb03739135b9ebf5/docs/architecture/audits/2026-11-15-s3k-mgz-event-publication.md).

## Trace comparison and readiness blocker

Counts are reported grouped errors, not raw row/field mismatch totals.
All 11 reports reached comparison gates with unchanged planned row counts;
frame-498 passes. Nine report error/warning lists, rows, bootstrap counts and gates
match baseline exactly. Two reports differ:

| Replay class suffix (all start TestS3k) | Base → review errors | First error |
| --- | --- | --- |
| MgzTraceReplay | 0 → 1121 | 32557 tails_y: $743 vs $740 |
| MgzZoneSliceTraceReplay | 0 → 1584 | 35447 tails_y: $741 vs $740 |
| KnucklesSuperMgz2SegmentTraceReplay | 3103 → 3103 | 223 rings |
| KnucklesSuperMgzSegmentTraceReplay | 1365 → 1365 | 1131 rings |
| MegaMgz2SegmentTraceReplay | 2 → 2 | 1854 y |
| MegaMgz3SegmentTraceReplay | 919 → 919 | 267 rings |
| MegaMgzSegmentTraceReplay | 529 → 529 | 1516 g_speed |
| SonicTailsMgzSegmentTraceReplay | 762 → 762 | 13848 camera_y |
| TailsFullChainMgz2SegmentTraceReplay | 1549 → 1549 | 82 x_speed |
| TailsFullChainMgz3SegmentTraceReplay | 2902 → 2902 | 692 rings |
| TailsFullChainMgzSegmentTraceReplay | 0 → 0 | none |

Short MGZ compares 35,861 rows in both runs (planned 35,912). Zone slice changes
39,183→39,199 compared rows (planned 39,398); this coverage difference is not hidden.
At short MGZ's first disagreement the engine already reports a collapse carrier
contact while the ROM state differs. Saved aux first advertises a native collapse
carrier at 32558; that observation alone does not prove its allocation phase.
The exact interaction with inherited scheduling/consumer bridges is not isolated.
The ROM-correct arm order was retained; these regressions block readiness.

MGZ-owned strict citation checking passes. Combined checking still reports three
inherited stale citations in unchanged non-MGZ manager comments (original lines
667,2294,2297), plus the unrelated Sonic CD reference-checkout warning. No pins moved.
Whitespace checks pass. Smoke, independent re-review, wider viewport/donor/team,
Knuckles BG movement and native pixel/audio certification remain with the lead.
Raw reports/logs and the clean baseline remain under their worktrees' target/.
Review queue wait: 1743 s; Maven execution: 279.396 s; user wait: 0 s.
Development/tool time is unmeasured. Disposable task scratch is removed at handoff.
