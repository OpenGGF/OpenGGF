# S3K AIZ act 1 — Sonic route matrix

Canonical game/zone/act: Sonic 3 & Knuckles, Angel Island, act 1.
Runtime slots: zone 0, act 0; outgoing seamless reload: zone 0, act 1.
The trace metadata uses display act 1; it is not the runtime act index.

Continuation base: `35488abb6` on `feature/ai-gameplay-capture`.
The reviewed input helpers and pilot come from `435ec2e68`; unrelated develop
engine changes were not imported. This is a partial matrix, not level certification.

## Configuration inventory

The representative row is native/off, 320px, Sonic with CPU Tails. Width axes
are 320/400/512/640/800, and movement donors are off/S1/S2, as resolved by
`WidescreenAspect` and `CrossGameFeatureProvider`. Remaining main-character
routes and team shapes require separate obligations; no configuration is
classified unsupported merely because this matrix has not exercised it.

## Obligations and evidence

| Obligation / spot | Contract and setup | Test / lane | Result and gap |
| --- | --- | --- | --- |
| ENTRY / ROUTE | Fresh production intro; recorded pad rows skip the metadata-derived pre-level prefix; first input follows live Level_started_flag; no P1 death; CPU identity/controller/leader and respawn audit; AIZ2 reload | `TestS3kAiz1RoutePilot#act1RouteReachesTheAct2Reload`, ordinary `slow-suite` | Initial continuation run: pass, 1 case, 0 skips, 5,174 frames, 3.306 s case time. No claim of optional-branch or boss attack coverage. |
| EVENT / REWIND intro and Knuckles | Independent fresh fixtures reach live intro/cutscene routines and completion; capture A, advance 90 pad inputs, restore A, replay twice | `TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice`, parameter identities in execution record | Pass: INTRO_HANDOFF, KNUCKLES_WAIT, KNUCKLES_ACTIVE, INTRO_COMPLETE. Initial red run exposed missing palette-helper timer/frame; fixed through RewindStateful. |
| CAMERA / OBJECT / REWIND hollow tree | Approach before ROM capture threshold $2C99; active camera lock $2C60; released bounds $1300..$4000; snapshot comparison includes object graph and world state | Same parameterized test, independent TREE_APPROACH / TREE_LOCK / TREE_RELEASE | Pass: TREE_APPROACH, TREE_LOCK, TREE_RELEASE. Initial red run exposed uncaptured static Events_fg_4; registered a dedicated adapter. |
| LOAD / REWIND AIZ2 | Production seamless reload and new-timeline restore/replay; old timeline rejected under the production boundary policy | `TestS3kAiz1ReloadRewind#seamlessReloadIsolatesHistoryAndNewActReplays`, ordinary | Pass: production root at frame 5,174; backward seek clamps to AIZ2; two 30-frame replays match. The older `TestRewindAcrossActBoundary` is only a smoke check, not evidence for this edge. |
| ENTRY / LIFE / LOAD breadth | Short width × donor lifecycle cross-product, checkpoints, death/restart, reset behavior and team ownership | Pending | Missing coverage. |
| ROUTE breadth | Every width and donor, supported main routes and team shapes | `TestS3kAiz1CompatibilityRoutes#axisRouteCompletes`, explicit `openggf.aiz1.routes=true` | 400px/off, 512px/off and 320px/S2 pass; 640px/off, 800px/off and 320px/S1 fail as detailed below. Other teams/main routes remain missing. |
| OBJECT / EVENT / CAMERA local boundaries | Per-mechanic before/at/after, negative activation, authority, culling and release checks | Existing `TestS3kAiz1SkipHeadless` is a source reference, not new execution evidence | Full obligation audit pending; route completion alone does not discharge local checks. |
| BOSS | Every relevant phase, damage, child graph, defeat and exit | Pending audit | Route is composition evidence only. |
| PRESENT / ORACLE | Native pixels/audio and independent ROM timing comparison | Separate trace/native lanes | Unrun by this delivery; snapshot equality is not rendering certification. |

## Commands and accounting

Use `mvn -Dmse=off -Dtest=TestS3kAiz1RoutePilot,TestS3kAiz1RouteRewind
-Ds3k.rom.path=/absolute/path/to/the/existing/rom.gen test` (one shell line).
Inspect every parameter identity and skip count; a missing ROM is unverified.

The user authorized separate focused accounting while the shared receipt is
owned by `kis2-trace-fixture`; elapsed time will be recorded when available.
Preflight passed with `LUA_BIN=/usr/bin/lua5.4 python3
tools/testing/run_categories.py --base 35488abb6 --preflight`.
The unchanged runner plan selects 2,518 ordinary classes plus all guards because
of the imported shared helpers and an unclassified event-manager registration.
Focused validation replaces that broad run under the proportionate-validation
policy: production changes only capture existing AIZ scalar state and retain a
helper's service binding across recreation; no movement/timing algorithm, art
bytes, camera behavior, selection policy or public Mod API changed. Native
production replay exercises the affected owners, and short helper/graph/schema
checks cover construction and restoration. The captures are width/donor-independent
scalar ownership; full-route width/donor sensitivity remains visible below.
This is focused validation, not a full ordinary or full guard-suite pass.

## Measured execution and open frontiers (2026-09-14)

Development tree: `.worktrees/ai-route-controller-continuation`, base `35488abb6`
plus this delivery. The imported native recipe reaches the same frame 5,174 as
the handover without runtime changes. Final focused set before integration:
118 tests, 117 passed, one HCZ pilot failure, zero skips. This includes both
`TestSonic3kLevelLoading` classes, `TestSonic3kBootstrapResolver`,
`TestSonic3kDecodingUtils`, `TestS3kAiz1SkipHeadless`, `TestAizIntroPaletteCycler`,
`TestAizPlaneIntroInstance`, `TestS3kAizIntroGraphRewind`,
`TestS3kAizIntroEventsHeadless`, `TestLiveRewindBoundaryPolicy`, `TestInputProgram`
and all three native route/rewind/reload classes. Invocation wall time 41.582 s.

The dedicated guards ran in a separate Maven/JVM invocation:
`mvn -Dmse=off -Pguards
-Dtest=TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard,TestHelperStateRewindCoverageGuard
test`: 3/3 passed, zero skips, 22.140 s. These are the three named guards, not
the entire guard suite.

The axis command is `mvn -Dmse=off -Dopenggf.aiz1.routes=true
-Dtest=TestS3kAiz1CompatibilityRoutes -Ds3k.rom.path=... -Dsonic1.rom.path=...
-Dsonic2.rom.path=... test`; use existing absolute ROM paths. Six rows ran,
three passed, three failed, no skips (30.656 s). Required donor files fail closed.

| Row | Observed outcome / first failing assertion |
| --- | --- |
| 320/off representative | Reload frame 5,174, recorded row 5,463 |
| 400/off | Reload reached |
| 512/off | Reload reached |
| 640/off | Program exhausted at frame 5,507 / row 5,796; P1 $1E34,$04DC |
| 800/off | First non-neutral row 1,428 arrives before live Level_started_flag; frame 1,139, P1 $138A,$041B |
| 320/S1 | Program exhausted at frame 5,507 / row 5,796; P1 $1E4B,$04DC |
| 320/S2 | Reload reached |

These are assertion frontiers, not claims about the first physics divergence.
A separate detached `.worktrees/ai-route-controller-baseline` at **35488abb6**
ran the same six axis cases plus the HCZ pilot against the untouched production
engine. All seven case outcomes and the **full four failure messages** were
byte-identical to the development tree (66.526 s baseline invocation). The new
capture paths do not cause those route failures. No fitted recovery or engine
physics change was introduced to make a pad program pass.

`TestS3kHcz1RoutePilot` is an opt-in third-zone experiment, invoked with
`-Dopenggf.hcz1.pilot=true -Dtest=TestS3kHcz1RoutePilot` and the S3K ROM path.
It starts HCZ1 through production fresh-entry setup, reads the shared movie
named by metadata, and supplies pad input only. It reaches water, then P1 dies
at frame/row 3,478 at $0EA7,$0823. Fresh-level lifecycle setup does not change
that result. This is not HCZ1 route completion or proof of a water-mechanic bug;
bootstrap/cadence and first divergence still need investigation. It gives no
basis for extracting a shared stage interface yet.

The seven AIZ rewind spots completed two restore/replay cycles each with the
whole registered snapshot, using the existing semantic snapshot diff. Title-card
patterns are normalized to their 64 pixel bytes in the test because the general
diff falls back to Pattern identity; no field or pixel is excluded. The 90-frame
windows were independently reached and took 11.48 s across seven cases in the
first all-green run. Setup crosses the real route; these tests complement rather
than replace the cheaper local interaction checks still missing from this matrix.

Remaining work: live-state AIZ gates at the three measured frontiers; HCZ entry
and route-frontier investigation; before/active/after coverage for other event,
boss, checkpoint and world families; width × donor lifecycle breadth; all team
and main-character routes; native presentation/oracle evidence. The FBZ shared
InputRun migration and qualified-@ModApi hook follow-up from the original
handover remain separate work; the selected branch lacks the later FBZ route
prerequisites, which were intentionally not imported.


## Integration and branch placement

Implementation commit: `bff3e1a7a`. At the user's request, the main workspace
was restored to `develop` at `435ec2e68` (already up to date), and
`feature/ai-gameplay-capture` was moved into the continuation worktree and
fast-forwarded to the implementation. No merge conflict occurred. Disassembly
HEAD/status/diff fingerprints and the user's notes-file hash matched before and
after switching the main folder; no hard reset or dirty-content discard occurred.
The work is intentionally **not merged into develop** by this delivery.

Post-integration focused validation on `bff3e1a7a`: **124 cases, 120 passed,
4 baseline-identical failures, 0 errors, 0 skips**, 84.702 s invocation wall time.
The four failures are exactly the three AIZ axis rows and HCZ pilot above;
case identities and full failure messages were compared again with the untouched
base. The three focused guards had already passed on identical source; they
were not repeated. ROM SHA-1 identities for S1 REV01, S2 REV01 and S3K matched
the repository reference table.

The combined command used `mvn -Dmse=off` with the class list below, both
`-Dopenggf.aiz1.routes=true` and `-Dopenggf.hcz1.pilot=true`, the existing absolute
`-Ds3k.rom.path`, `-Dsonic1.rom.path`, `-Dsonic2.rom.path`, and `test`:

```text
-Dtest=TestS3kAiz1RoutePilot,TestS3kAiz1RouteRewind,TestS3kAiz1ReloadRewind,TestInputProgram,TestAizIntroPaletteCycler,TestAizPlaneIntroInstance,TestS3kAizIntroGraphRewind,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kAizIntroEventsHeadless,TestLiveRewindBoundaryPolicy,TestS3kHcz1RoutePilot,TestS3kAiz1CompatibilityRoutes
```

Aggregate measured testing: **505.208 seconds (8.420 minutes)**, including
438.682 s focused/iteration/integration and 66.526 s matched baseline. This
includes failed setup/debugging runs, not just green checks. The user authorized
separate accounting because the shared receipt belongs to `kis2-trace-fixture`;
the final status attempt found its task lock held by another worktree. That
receipt and lock were left untouched. This document retains the accounting;
consumed raw diagnostics are removed rather than archived.

One independent static review found no high/medium issues. Its coverage limit:
these tests compare registered snapshots at restore/replay endpoints, not every
intermediate frame or unregistered state. `SidekickAudit` tolerates empty-team
suppression windows and bounds observed dead streaks; it does not prove all
possible terminal deaths recover. Documentation links and whitespace were checked.
