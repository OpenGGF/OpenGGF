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
| ENTRY / LIFE / LOAD breadth | Short width × donor lifecycle cross-product, checkpoints, death/restart, reset behavior and team ownership | `TestS3kAiz1EntryMatrix`, explicit `openggf.aiz1.entry=true` | All 15 width × donor entry cases pass; intro ownership and two 30-frame rewind replays. Checkpoints, death/restart and load breadth remain missing. |
| ROUTE breadth | Every width and donor, supported main routes and team shapes | `TestS3kAiz1CompatibilityRoutes#axisRouteCompletes`, explicit `openggf.aiz1.routes=true` | 400px/off, 512px/off and 320px/S2 pass; 640px/off, 800px/off and 320px/S1 fail as detailed below. Other teams/main routes remain missing. |
| OBJECT / REWIND opposing spring chain | Live two-spring approach, RIGHT-only rejection, spring-aware crossing, two whole-state replays | `TestS3kAiz1SpringRecovery`, explicit `openggf.aiz1.recovery=true` | 640/off, 800/off and 320/S1; see spring-chain continuation below. Full-route join remains unresolved. |
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

## Route frontier continuation (base `1db888a52`)

Task worktree: `.worktrees/ai-route-frontiers`, branch `bugfix/ai-route-frontiers`;
destination remains `feature/ai-gameplay-capture`. Main workspace stays on
`develop`. This continuation changes test controllers and evidence only.

### Late intro handoff and independent breadth

`Aiz1IntroProgram` preserves every recorded neutral row. When the first
non-neutral row arrives before the live `Level_started_flag`, it supplies
neutral input and holds that row until the owner releases it. The gate runs
once; a later control lock cannot reapply it. Its unit checks cover both early
and late handoffs, preserved row identity and later lock changes.

The wider exit is production behavior: `CutsceneKnucklesAiz1Instance`
routine 12 implements ROM `loc_61F10`'s preceding-render-flag test, then
`loc_61F22` releases control (`loc_61F44` sets `Level_started_flag`). Runtime
viewport culling was retained. The native cutscene releases at engine frame
1,097, 640px at 1,133, and 800px at 1,146. The recorded first input would run
at frame 1,140; only 800px needs seven neutral ticks, then accepts it at 1,147.
The rejected earlier pilot resumed 42 frames early; that approach remains
rejected. Neither coordinates nor recorded trace values select this gate.

`TestS3kAiz1EntryMatrix` independently runs all five widths (320, 400, 512,
640, 800) × three movement donors (off, S1, S2), always Sonic + CPU Tails.
Each production intro must release control, admit input and preserve team
ownership. At first input it captures registered state, advances 30 inputs,
restores and replays the identical inputs twice. Position must advance through
live movement. All 15 cases passed, zero skips; every 800px case held seven
frames, every other case held zero. This is an explicit ROM-backed lane,
`-Dopenggf.aiz1.entry=true`, independent of the long-route opt-in. It requires
all three existing absolute ROM properties. It does not certify other teams,
characters, checkpoint/death behavior or the unregistered state surface.

### Remaining AIZ route frontiers

The combined helper/native/axis check ran nine cases: six passes and the three
known failing axes, zero errors/skips. Native reload is unchanged at 5,174;
400px reloads at 5,181, 512px at 5,190, and native S2 donor at 5,174.
640px and S1 still exhaust at frame 5,507 / row 5,796, respectively
P1 `$1E34,$04DC` and `$1E4B,$04DC`. 800px now passes the first-input assertion
and exhausts later at frame 5,507 / row 5,789, P1 `$1E4D,$04DC`. This advances
its observed frontier; it does not complete that route.

A temporary per-frame live-object survey compared native and alternate axes.
At engine frame 1,431, 640px first differs in player vertical speed:
`-848` versus native `-1104`. The MonkeyDude at x=6,200 has a different active
phase (y=1,048 versus 1,040), and contact/bounce occurs two frames earlier.
The wider activation region changes the interaction; the later spring chain
amplifies the difference. No runtime defect is established by this survey.
S1 first differs at frame 1,517 / input row 1,806: DOWN+JUMP starts native
spindash but makes the S1 moveset jump. Future steering must use the live
object geometry and movement capability, not width/donor names or fitted
position/frame constants. No speculative recovery code was retained.

### HCZ first divergence

A comparison-only survey of the unchanged HCZ pilot matched player integer
x/y, x/y speed and airborne state for rows 0–1,162. Row 1,163 is the first
mismatch: the recording repeats row 1,162's gameplay state, with lag counter
1 and gameplay counter still 1,163; the headless route advances to the next
state. P1 y is 1,530 versus recorded 1,523, vy 2,000 versus 1,944. There are
no recorded Start presses before the eventual death at row 3,477 (3,478
executed frames). Fresh-entry player state is therefore not the first observed
problem for these fields. This establishes an earlier hardware-cadence mismatch,
not a water-mechanic defect or proof that cadence alone causes the later death.

The pilot continues to consume only movie pad inputs. Trace lag rows were not
used to skip gameplay, resample the input program or hydrate state. Hardware
admission belongs to the dedicated timing contract; route control instead needs
live hazard decisions robust to timing differences. A shared stage interface
still lacks two successful zone controllers as evidence. Survey classes and raw
CSV/log output are temporary and removed after documenting these findings.

### Validation scope

Preflight passed using `LUA_BIN=/usr/bin/lua5.4` and the actual pinned base
`1db888a52`. The unchanged change-based runner selects the full ordinary suite
(2,525 classes at initial inspection) plus guards because it does not classify
the package-private test helper. Under proportionate validation this test-only
change is checked through its unit tests, every route consumer affected by the
edit, all axis cases, native route/rewind/reload and the independent entry matrix.
No production algorithm, contract, timing port, build or selection policy changed.
No broad run was launched; the normal broad cost is approximately 34 minutes.
This remains focused validation, with long-route failures explicitly retained.
The shared receipt was occupied at task start; final inspection found the peer
`kis2-tier-two` active (one broad attempt already spent). It was left untouched.
Separate task accounting continues under the user's explicit authorization.

The final combined command used `mvn -Dmse=off`, all three existing absolute ROM
properties, `-Dopenggf.aiz1.routes=true -Dopenggf.aiz1.entry=true
-Dopenggf.hcz1.pilot=true`, and:

```text
-Dtest=TestAiz1IntroProgram,TestInputProgram,TestS3kAiz1RoutePilot,TestS3kAiz1CompatibilityRoutes,TestS3kAiz1EntryMatrix,TestS3kAiz1RouteRewind,TestS3kAiz1ReloadRewind,TestS3kHcz1RoutePilot test
```

Completed: **36 cases, 32 passes, 4 failures, no errors/skips**, 67.883 s.
The failures are the three AIZ long-route axes and HCZ described above. The full
640px and S1 failure messages match the initial untouched-controller check at
`1db888a52` exactly; the 800px failure moved past intro as intended. HCZ remains
at its previously attributed death frontier. No unrelated runtime fix was made.
Independent static review found no blocking issue; it prompted an extension of
the team audit through each 30-frame forward/replay window and clearer timing
prose. A focused matrix rerun verifies that extension; unchanged route checks
are not repeated for prose edits.

The strengthened entry audit passed all 15 cases, zero skips (36.470 s).
Pre-integration aggregate testing: **315.924 seconds (5.265 minutes)**,
including survey setup failures, both survey runs, the pre-wiring controller
check, integrated gate check, entry checks and final combined validation.

Integration commit: **`32217319b`**, fast-forwarded into the destination without
conflicts. Post-integration command: `mvn -Dmse=off
-Dtest=TestAiz1IntroProgram,TestS3kAiz1RoutePilot,TestS3kAiz1EntryMatrix
-Dopenggf.aiz1.entry=true` with all three existing absolute ROM properties and
`test`: **18 passed, zero failures/errors/skips**, 39.854 s. The other
completed checks are unchanged by fast-forward integration; no full-suite or
full-guard claim is made. Final aggregate: **355.778 seconds
(5.930 minutes)**, separately accounted because the peer receipt is active.
Changed-document local links, whitespace and commit policy passed. Raw survey
and Maven diagnostics are consumed and removed; only this compact evidence
record remains.

## Spring-chain continuation (base `0bd7c5317`)

Worktree: `.worktrees/ai-aiz-steering`, branch `bugfix/ai-aiz-steering`;
destination `feature/ai-gameplay-capture`. Main remains `develop`.

This increment adds an independent OBJECT / REWIND obligation at the two
opposing horizontal springs reached by the failing 640px/off, 800px/off and
320px/S1 pad programs. `TestS3kAiz1SpringRecovery`, selected explicitly with
`-Dopenggf.aiz1.recovery=true`, derives the approach and exit from live spring
positions and collision widths. The recorded input prefix and late intro gate
are unchanged. The first spring is observed at `$1F39,$04A0`, the second at
`$1FB9,$0480`; these coordinates are evidence, not controller selectors.

At each independently reached approach, capture A. A 300-frame RIGHT-only
control fails to cross the first spring and receives its leftward launch.
Restore A, then choose RIGHT/JUMP from the live opposing-spring approach,
hold each jump through flight and release on landing. Steering must consider
both springs: a single-target attempt crosses the first but is thrown back by
the second. Require P1 to finish beyond the entire chain, without death/load,
and audit CPU team identity, controller ownership, leader chain and observed
dead-streak duration. Restore A and replay the generated 300 pad masks twice;
compare the whole registered snapshot at each restore and endpoint. This
checks engine replay for those inputs; it does not rewind the test-side policy,
prove per-frame equality or prove every late sidekick death has recovered.

The four-collision-half-width approach margin and five-second local observation
window are authored test bounds, not tuned engine physics. X-flipped horizontal
spring dispatch/launch follows `Obj_Spring` / `sub_23190`; the test uses the
live subtype and render flags. Trace physics and aux rows never drive gameplay.

### Rejected full-route recoveries

The original full-route assertions remain intact and still have the four known
frontiers (three AIZ axes, HCZ). No generic tail recovery was integrated.
Temporary ordinary-input probes established the following limits:

| Attempt | Evidence / decision |
| --- | --- |
| RIGHT after the recorded program ends | All three axes cycle against the first opposing spring; no reload in 16,000 total frames. |
| Jump from the live horizontal-spring approach | Clears the spring chain in all three axes; later stops at `$205D,$043D`. Retained only as the independent local obligation above. |
| Add a grounded stationary-position jump | 640px/S1 reach a later loop near `$2B39,$0443`; 800px can remain attached to a vine. Not a complete route. |
| Pulse jump while object-controlled | Releases the vine (the owner reads A/B/C press edges in `sub_220C2` / `loc_22136`), but all three still fail the lower loop. Not retained. |
| Global furthest-X watchdog | Jumps too aggressively and degrades progress into the `$2226..$22D5` area. Rejected. |
| Jump at the loop's invisible collision blocks | Does not establish loop traversal. Rejected. |
| Downhill / approach rolling | The first probe incorrectly held RIGHT+DOWN, which cannot enter a roll; `SonicKnux_Roll` and the S1/S2 equivalents require no left/right held. Corrected DOWN-only probes still do not complete the loop. Rejected. |

The probes approach a lower path, lose forward speed on the loop and reverse;
this is not evidence of a runtime physics defect. The next route work
should establish a valid live join into the upper route or a complete lower-loop
traversal before extracting or enabling a general recovery stage. A coarse
sampled position beyond one object is insufficient evidence of stable recovery:
the neighboring spring can return the player. The local negative-control and
whole-chain exit checks are retained to prevent repeating that mistake.

### Validation scope and accounting

The unchanged runner plan selected 1,616 ordinary common/gameplay classes plus
guards. This increment adds one explicit test class, changes no production,
shared helper, build or selection policy, and exercises its complete setup,
negative control and restore/replay paths directly. Focused validation applies
under the proportionate policy; no broad attempt was launched. Actual preflight
passed with `LUA_BIN=/usr/bin/lua5.4` against pinned base `0bd7c5317`. Existing
complete-route failures are not reclassified as passes by this local obligation.
Separate accounting remains authorized while the peer receipt is active; all
probe and failed-check elapsed time is included, not just the final passes.

Final development checks used `mvn -Dmse=off` with the existing absolute S1,
S2 and S3K ROM properties. `-Dtest=TestS3kAiz1SpringRecovery,TestS3kAiz1RoutePilot,TestAiz1IntroProgram
-Dopenggf.aiz1.recovery=true test`: **6 passed, zero errors/failures/skips**,
28.183 s. The review-strengthened spring activation check then passed all three
rows (28.333 s). The main route runner and existing full-route assertions were
not edited; no new full-route pass is claimed.

Review found no blocking restore/input-reference issue. It prompted the explicit
ROM-strength rebound check; its remaining coverage limits are stated above.
Pre-integration aggregate testing: **522.227 seconds (8.704 minutes)**,
including every exploratory and failed run. The shared receipt now belongs to
`kis2-presentation-super-completion` and was left untouched. Local links and
whitespace checks passed. Temporary probe code and raw logs are removed after
the compact findings are recorded here.

Integrated **`c160508fa`** into `feature/ai-gameplay-capture` by fast-forward,
without conflicts. Post-integration `mvn -Dmse=off
-Dtest=TestS3kAiz1SpringRecovery -Dopenggf.aiz1.recovery=true` with all three
existing absolute ROM properties and `test`: **3 passed, zero failures/errors/skips**,
26.571 s. The whole chain was crossed 83/88/88 frames after capture for
640/off, 800/off and 320/S1 respectively, and remained crossed at the 300-frame
endpoint; each replay matched twice. Final aggregate testing: **548.798 seconds
(9.147 minutes)**, separately accounted. This is focused local evidence,
not a full-suite pass or complete-route delivery. No production source changed.
