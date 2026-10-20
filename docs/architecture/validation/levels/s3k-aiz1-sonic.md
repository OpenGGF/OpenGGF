# S3K AIZ act 1 — Sonic route matrix

Original fullrun follow-up (2026-10-17): the unchanged intro/bootstrap and original
controller route now qualify the later normal bridge's exact standing SST at
rows9510–9513. That bridge is in the reloaded Act2 world; its bounded certificate,
source repair, independent owner replay and inherited auxiliary gaps belong
to the [AIZ2 matrix](s3k-aiz2.md#original-fullrun-normal-bridge-standing-sst--2026-10-17).
The separate [R2 three-worker component qualification](s3k-aiz2.md#three-act2-level-size-workers--2026-10-17-r2)
closes the omitted min-Y handoff owner; it adds no cold-route breadth.
This adds no Act1 route/configuration certification.

Canonical game/zone/act: Sonic 3 & Knuckles, Angel Island, act 1.
Runtime slots: zone 0, act 0; outgoing seamless reload: zone 0, act 1.
The trace metadata uses display act 1; it is not the runtime act index.

Continuation base: `35488abb6` on `feature/ai-gameplay-capture`.
The reviewed input helpers and pilot come from `435ec2e68`; unrelated develop
engine changes were not imported. This is a partial matrix, not level certification.

## Level-entry camera (2026-10-02)

`Get_LevelSizeStart/loc_1BF74` subtracts `$A0` from the X focus and `$60` from
the Y focus. It clamps underflow to zero, then clamps to the maximum bound; it
never reads the minimum bound. The engine's forced snap also clamped to the
minimum, which put AIZ1 at `$1308` instead of `$1300`.

`TestSonic3kLevelStartCamera` covers:
- the zero and maximum edges
- MHZ1's separate `$160` focus, which checkpoints bypass
- stable character identity
- signed words

Positioned captures and replays resnap through
`LevelCameraInitialization.recenterPositionedEntry`. Continuous runs keep the
production load camera.

Results: Knuckles opening 12,600→12,566 errors, Mega opening 18→1 and Tails
opening 17→0. Matched S1/S2 report projections are unchanged. ICZ1 Tails bounds
and the CNZ/LRZ/DEZ/HPZ cold intro offsets are outside this fix.

## Configuration inventory

The representative route is native/off, 320px, Sonic with CPU Tails. Current
short entry checks resolve every production `WidescreenAspect` preset
320/352/400/528/800 through `LaunchProfileApplier` and the display resolver,
crossed with off/S1/S2 donation. S1 uses an explicitly supported Sonic CPU;
native/S2 use Tails. Actual ROM renderer/animation, CPU chain and restored main/
camera ownership are checked. The older 512/640 checks remain separately labelled
custom geometry. Historical raw-width full-route and custom fixtures retain their
original Sonic+Tails setup, including S1's physical Tails without donated Tails art;
they are not production-profile or rendered-team certification. Remaining main-
character routes and team shapes require separate obligations; no configuration
is classified unsupported merely because this matrix has not exercised it.

## Obligations and evidence

| Obligation / spot | Contract and setup | Test / lane | Result and gap |
| --- | --- | --- | --- |
| ENTRY / ROUTE | Fresh production intro; recorded pad rows skip the metadata-derived pre-level prefix; first input follows live Level_started_flag; no P1 death; CPU identity/controller/leader and respawn audit; AIZ2 reload | `TestS3kAiz1RoutePilot#act1RouteReachesTheAct2Reload`, ordinary `slow-suite` | Initial continuation run: pass, 1 case, 0 skips, 5,174 frames, 3.306 s case time. No claim of optional-branch or boss attack coverage. |
| EVENT / REWIND intro and Knuckles | Independent fresh fixtures reach live intro/cutscene routines and completion; capture A, advance 90 pad inputs, restore A, replay twice | `TestS3kAiz1RouteRewind#liveSpotRestoresAndReplaysTwice`, parameter identities in execution record | Pass: INTRO_HANDOFF, KNUCKLES_WAIT, KNUCKLES_ACTIVE, INTRO_COMPLETE. Initial red run exposed missing palette-helper timer/frame; fixed through RewindStateful. |
| CAMERA / OBJECT / REWIND hollow tree | Approach before ROM capture threshold $2C99; active camera lock $2C60; released bounds $1300..$4000; snapshot comparison includes object graph and world state | Same parameterized test, independent TREE_APPROACH / TREE_LOCK / TREE_RELEASE | Pass: TREE_APPROACH, TREE_LOCK, TREE_RELEASE. Initial red run exposed uncaptured static Events_fg_4; registered a dedicated adapter. |
| LOAD / REWIND AIZ2 | Production seamless reload and new-timeline restore/replay; old timeline rejected under the production boundary policy | `TestS3kAiz1ReloadRewind#seamlessReloadIsolatesHistoryAndNewActReplays`, ordinary | Pass: production root at frame 5,174; backward seek clamps to AIZ2; two 30-frame replays match. The older `TestRewindAcrossActBoundary` is only a smoke check, not evidence for this edge. |
| ENTRY / LIFE / LOAD breadth | Actual fresh intro and production preset × supported-donor entry, with donor-supported CPU art/ownership; separate legacy geometry | `TestS3kAiz1EntryMatrix`, ordinary independent checks | Fifteen production cases plus six legacy512/640 cases qualified below; actual input/control release, two whole-world30-frame replay cycles and explicit main/camera identity. Checkpoints, death/restart, repeated reset and additional main/team products remain open. |
| ROUTE breadth | Every width and donor, supported main routes and team shapes | `TestS3kAiz1CompatibilityRoutes#axisRouteCompletes`, explicit `openggf.aiz1.routes=true` | All seven axis routes reach the AIZ2 reload alive; see the green-route continuation below. Full width × donor route cross-product and other teams/main routes remain missing. |
| OBJECT / REWIND opposing spring chain | Live two-spring approach, RIGHT-only rejection, spring-aware crossing, two whole-state replays | `TestS3kAiz1SpringRecovery`, explicit `openggf.aiz1.recovery=true` | 640/off, 800/off and 320/S1; see spring-chain continuation below. Full routes now complete; the independent negative-control/rewind obligation remains. |
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


## Green-route continuation (2026-09-14)

Task `route-green-20260914`, pinned base `f1843f54a1`, development tree
`.worktrees/ai-route-green`. This continuation changes private test controllers
and evidence only; production physics, objects and timing remain unchanged.

`Aiz1RouteContinuation` preserves recorded input until exhaustion or a grounded
DOWN+JUMP request that the live moveset cannot execute as spindash. It reads live
springs, platform geometry, control ownership, velocity and capabilities and
returns ordinary pad masks. No width/donor names, trace physics rows, forced
positions or fitted engine-frame gates select behavior.

- The wide routes clear opposing horizontal springs with jump edges, release
  movement-suppressing vine ownership with a fresh jump, and preserve the
  hollow tree's movement-active ride. A generic object-controlled jump gate
  was rejected because it ejects the player from the tree.
- A rollback on the lower uphill loop requests a boost. The controller returns
  to flat ground, turns right, crouches and charges using the live spindash
  table. Charging at an arbitrary wall or while facing left was rejected.
- A moveset without spindash climbs two live vertical springs, aims at the
  upper spring's left half, lands on the moving platform, and uses the upper
  ledge to continue right. Centring the upper spring missed the platform;
  walking straight off the platform returned to the impassable lower loop.
  The last latched solid owner is sticky: a later terrain landing must not be
  described as a new platform contact merely because that reference remains.
- Once the live FireBreath cutscene has locked its ROM arena, the continuation
  brakes near its centre until the production fire transition reloads AIZ2.
  Holding RIGHT instead caused ringless deaths against the right-hand body.

The maintained route checks, with their existing per-frame no-P1-death and CPU
identity/controller/leader/dead-streak audit, passed **7 cases, 0 skips**:

| Axis | Reload engine frame | Recording continuation |
| --- | ---: | --- |
| 320/off | 5,174 | Unused; native test explicitly requires this |
| 400/off | 5,181 | Unused |
| 512/off | 5,190 | Unused |
| 640/off | 7,434 | After program exhaustion |
| 800/off | 7,471 | After program exhaustion; seven intro hold frames |
| 320/S1 | 4,306 | At the unavailable spindash request |
| 320/S2 | 5,174 | Unused |

Command: `mvn -Dmse=off
-Dtest=TestS3kAiz1RoutePilot,TestS3kAiz1CompatibilityRoutes
-Dopenggf.aiz1.routes=true` with the existing absolute S1/S2/S3K ROM properties,
then `test`. Measured wall time: 33.525 seconds. Recorded-row lead is meaningful
only for uninterrupted playback; a cursor parked during live continuation is
not evidence of an engine timing lead. Review added a native-only assertion
that recovery never takes over, so an accidental native regression cannot be
hidden by the new fallback.

The runner plan selects 2,524 ordinary classes plus all guards for the new
unclassified private helper. The proportionate-validation exception applies:
there are no runtime/shared-helper/public-contract changes, and the route,
entry, rewind and local recovery consumers exercise the affected paths directly.
This is focused route validation, not a full ordinary/guard or trace-parity pass.
The existing full-level coverage gaps in the obligation table remain open.

After the native safeguard was added, `mvn -Dmse=off
-Dtest=TestS3kAiz1RoutePilot,TestS3kAiz1EntryMatrix,TestS3kAiz1RouteRewind,TestS3kAiz1ReloadRewind,TestS3kAiz1SpringRecovery,TestAiz1IntroProgram,TestInputProgram
-Dopenggf.aiz1.entry=true -Dopenggf.aiz1.recovery=true` with the same ROM properties
and `test` passed **32 cases, zero failures/errors/skips**, in 58.696 seconds.
This includes native recording preservation, 15 entry configurations, seven
independent rewind windows, reload timeline isolation, three opposing-spring
negative-control/replay cases and both input helpers.

### Trace-campaign object coverage (2026-10-02/03)

These are local object regressions with restore/replay. They do not certify the
viewport, donor or team products for the whole act. Per-fix trace numbers are in
the [frontier log](../../../status/trace-frontier-log.md#2026-10-03--s3k-trace-green-campaign-summary).

| Object/owner | Regression | ROM owner |
|---|---|---|
| Monkey Dude body continuation and timer | `TestMonkeyDudeBadnikInstance#releasedBodyKeepsAnimationCadenceOffscreenAndAcrossRewind` | `Obj_WaitOffscreen/loc_85B02`, `loc_871C2`, `loc_87218` |
| Monkey Dude linked hand | `#handLaunchesOnceFromItsPreviousFractionalChainPositionAcrossRewind`, `#armDoesNotStartAttackAtTheHorizontalRangeBoundary` | `sub_87524`, `sub_8756A`, `sub_87592`, `sub_875B4` |
| Entry-flash ring deletion | `TestSonic3kSSEntryRingFormation`, `TestS3kSsEntryFlashGraphRewind` | `SSEntryFlash_Main` |
| Tails tree release radii | `TestAizHollowTreeTailsRelease` (native aux: radius 15→19→15 at rows 2040, 2053, 2058) | `AIZTree_FallOff`, `Tails_TouchFloor` |

The old Monkey Dude throw repeated every 120 updates and was driven by body
animation. That is wrong: `sub_875B4` sets a once-only bit 0. Native Tails places
the first projectile at `(0x1E64, 0x46A)` after its first movement (physical
frame 6401).

Route prefix pins:
- `TestS3kKnucklesSuperEmeraldRunChain#aiz1ThroughGiantRingIntoFirstSpecialStageRow`
- `TestS3kTailsFullChainRunPrefix`, both the first entry and the full first
  return through segment 3 row 1
- `TestS3kSonicTailsCompleteEmeraldRunPrefix`, through two returns to
  segment 5 row 1

### Fixed fire-reload camera lock — 2026-10-03

`AIZ1BGE_Finish` writes min/max X `$10/$10`, and `Do_ResizeEvents` eases only
max Y. The AIZ request now pins the engine's X smoothing targets as well, so the
loaded defaults cannot move the lock. Without the fix,
`TestSonic3kAIZEvents#eventsFg5StartsFireTransitionAndAppliesSeamlessFlow` fails
after one ordinary boundary tick (expected 16, got 14). The native lock/release
assertions and `TestS3kAiz1ReloadRewind` pass.

## Post-boss handoff prerequisite — 2026-10-12 candidate

The full Sonic + CPU Tails `aiz1_to_hcz_fullrun` fixture exercises AIZ1's
seamless reload, AIZ2 boss/results, button and drawbridge, then HCZ title-load
submission. Over `a0e2b79cc` in `.worktrees/ai-runtime-perf-series`, its 57 inherited
errors become zero across 20,463 compared observations; terminal hardware work
also matches. The alternative `aiz_completerun` remains failing (99→59 errors,
first row 25,589 animation $13/$05) and is excluded from performance routes.

Local checks cover original-owner lifetime, allocation/dispatch boundaries,
consumed input history, signed-control support, camera/display consumers and
drawbridge countdown recreation followed by the same eject. They do not certify
whole-route rewind or viewport/donor/character breadth. Two fresh lossless
handoff captures match all 618 decoded video frames and 494,400 stereo sample
frames; this proves determinism, not native waveform/pixel parity. Matched
updated-base validation is pending; the
[performance validation record](../performance/2026-10-12-runtime-performance-series.md)
records exact commands and scope. No performance score is frozen or claimed.


## Production-preset and actual launch-profile entry — 2026-10-14

Lane `feature/ai-s3k-parity-hpz`, runtime base
`5b2ff8e7851b99dd40998dfc41fbfd21c80031ef`, prior lane HEAD `e0036138a`.
New preset overloads apply the production launch profile and resolve its aspect;
they do not inject raw width. The new AIZ1 entry path requests the actual fresh
level lifecycle, consumes only recorded controller input and waits for the live
intro owner to admit input. Every production row has an actual donor-supported
CPU and decoded ROM art/animation. Historical integer/full-route helper calls
retain their old initialization, input program, CPU fixture and geometry; no
existing route is silently relabelled as a production preset or retuned.
The short independent entry class is now in ordinary validation; its old
`slow-suite`/`openggf.aiz1.entry` opt-in no longer gates this mandatory entry floor.
Expensive full routes retain their existing deeper selection.

The two-act entry cohort first passes 57 cases at 10:05:41 BST (1:51 Maven time):
AIZ1 21 cases/25.20 seconds, HCZ1 36/60.83 seconds, all with zero failures/errors/
skips. Additional production-owner checks pass 45 cases at 10:10:56 BST (1:34),
including all 30 HCZ1 production entry/reset rows and all 15 AIZ1 released-focus rows.
The final AIZ1 immediate-restore and per-cycle focus checks pass the affected 15
cases at 10:14:15 BST (43.523 Maven seconds, 18.45 case seconds). Thus the final
scoped qualification accounts for 57 distinct identities: current 15 AIZ1 plus
current 30 HCZ1 production rows and 12 unchanged custom geometry rows, not one
final-source whole-57 invocation. A new typed-assertion compilation error was
corrected before any tests executed; it was not a runtime/route failure.

The actual production product is five presets × off/S1/S2. S1's explicit Sonic
CPU is supported by `LaunchProfile`; requesting S1 Tails instead normalizes to
None, independently verified in the [HPZ matrix](s3k-hpz-act.md#actual-launch-profile-normalization-and-hpz-entry--2026-10-14).
Both snapshots compare every registered owner; the existing title-card `Pattern`
normalization compares all 64 pixel bytes and omits no field/pixel. Explicit focus
checks cover the live main reference deliberately absent from `CameraSnapshot`.
This is entry and short replay evidence, not raster parity or complete act coverage.

All requests run from the intended worktree through normal shared admission,
Java 25, surfaceless EGL, and original absolute S3K/S1/S2 ROM paths:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null   python3 tools/testing/maven_queue.py -Dmse=off   '-Dtest=TestS3kAiz1EntryMatrix,TestS3kHcz1EntryMatrix'   -Dopenggf.headless.gl=surfaceless-egl   "-Ds3k.rom.path=$PROJECT_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen"   "-Dsonic1.rom.path=$PROJECT_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen"   "-Dsonic2.rom.path=$PROJECT_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test
```

The owner follow-up substitutes
`TestS3kAiz1EntryMatrix#introReleasesInputAndEntryReplaysTwice,TestS3kHcz1EntryMatrix#entryAdmitsMovementAndReplays+repeatedLoadsReplaceOwnersAndRetainPlayableConfiguration`;
the final affected selector is
`TestS3kAiz1EntryMatrix#introReleasesInputAndEntryReplaysTwice`.
Normal worktree-lease and admission waits are separate from these execution times.
No broad/full-suite, new native recording or campaign conformance claim belongs to
this slice. Real checkpoint/death/reset, all supported main/team paths, the new
352/528 complete-route products and remaining per-mechanic/boss/presentation
obligations remain explicit and are not discharged by the short entry checks.
Combined validation/integration remain lead-owned.

## Native MonkeyDude local graph — 2026-10-14

`TestS3kMonkeyDudePlacedGraph` loads the real AIZ1 ROM placement
`X=$1E58,Y=$0490,id=$8E,subtype=$10,flip=1` from table `$1E44DA`, record
`$1E45E2`, admits its five independent arm records and restores/replays the
whole registry for45 ordinary inputs twice at actual production presets
320/352/400/528/800. These are positioned local graph checks; no cold route,
donor/team breadth, rendered pixels/audio or whole-badnik parity is implied.
`TestMonkeyDudeNativeSstGraph` separately covers both orientations, checked
allocation, predecessor identity, offscreen retirement, normal/power replacement
before and after its own INIT, unknown-header rejection and forward replay.
Focused36090 passes104 cases including these13; source/rewind/trace guards90457
pass111. Strict originalAIZ/initialHCZ/both prefixes remain green, but the
continuous chain and alternateAIZ retain their prior frontiers. The
[owning audit](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md) distinguishes
physical occupancy evidence from the remaining body-facing and death-order limits.

The separate body correction replaces the old synthetic turning/32-pixel tests
with literal flip0/1 and native X/touch/render origins across both player sides
and two replay cycles. The real placement checks flip1 and X`$1E58` at all five
production presets. Negative40269 fails2/2; corrected54064 passes18/0/0/0.
Strict39401 retains four green controls and the inherited continuous-chain
failure; alternate80705 retains60 grouped errors. This body conformance repair
adds no cold-route, native pixel/audio or strict-chain closure claim.


## Native fresh-entry RNG — 2026-10-14

`Level/loc_60DE` clears the retained RNG seed before fresh sprite assembly,
including special-stage returns; preview and seamless continuation retain it.
`TestS3kFreshLevelRngState` independently passes four real-entry/preservation/
whole-world RNG replay cases, and the existing99 affected consumers remain
qualified on identical production. Strict original AIZ/initial HCZ/both prefixes
remain green; alternate60 spans and continuous-chain189/12,719/82,067 raw field
mismatches are unchanged. Sampled presence gaps726/888 close; first remaining
segment2 owner discrepancy is projectile SST43 at969. This is source/state
conformance, not a native waveform/pixel or whole-route parity claim. The
[existing audit](../../audits/trace/2026-10-14-s3k-fresh-title-sst.md#fresh-native-rng-clear--separate-follow-up-over-ddbc651e0)
records exact checks and the independently reproduced positioned-ending title
lease gap.


## Native shared projectile lifetime — 2026-10-14

`TestS3kBadnikProjectileNativeLifetime` verifies real higher-slot hand birth,
INIT→motion fallthrough, native inclusive Y/coarse-X cull boundaries and
registered object-manager restoration/replay before, during and after delayed
slot clearing. All13 pass with Monkey/Bloominator/touch/transient consumers and
mandatory startup60 in63437; its one stale anchor inventory passes corrected
73701. Actual placed Monkey graph remains qualified at320/352/400/528/800.
Strict controls remain green, but alternate60 spans and chain189/12,719/82,067
raw mismatches do not move. Sampled physical presence improves969→1055; native
status7 pending-delete versus engine clear at1055 still needs exact clock
attribution. The [existing audit](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#shared-s3k-projectile-native-init-and-retirement)
distinguishes this local owner qualification from cold-route/pixel/audio parity
and the newly unresolved positioned title-art consumer errors.


### 2026-10-14 Act3 title admission follow-up

The new fixed-title graph now honors native Act3 continuation bypass through
existing LOADING carry state. Two internal destinations × actual production
presets320/352/400/528/800 pass independent owner/child admission and48-input
teardown negatives. Affected119 and PLC25 plus actual incoming DEZ7 pass;
both positioned art-lease errors introduced by the earlier fresh-title slice
are resolved without input/oracle changes. Original AIZ/initial HCZ/prefix2
remain green; alternate60 and chain189/12,719/82,067 raw-field frontiers stay
unchanged. [Native evidence and exact checks](../../audits/trace/2026-10-14-s3k-fresh-title-sst.md#act3-continuation-bypass--whole-graph-admission)
do not certify native screenshot/audio or whole-act parity.


### 2026-10-14 full-manager projectile follow-up

The complete manager now leaves native pending-delete projectile SST43 alive
at the coarse-X tail until its next dispatch. Two negative X edges plus four Y
controls, full-slot/collision-list restore and two replay cycles pass in the19
projectile checks; affected613 and guards111 pass. Actual original AIZ, initial
HCZ and both run prefixes remain green. Strict alternate60 grouped spans and
chain189/12,719/82,067 raw fields remain red. Sampled presence improves1055→1688,
where native cork-floor reused-parent allocation differs; second-entry ring/
flash order remains unresolved. [Exact source and evidence](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#manager-dispatch-preserves-the-native-coarse-x-retirement)
separate local owner qualification from pixel/audio and whole-route parity.


### 2026-10-14 actual image spot evidence

Native/engine Monkey1680 and genuine AIZ2 shared-projectile6970 images are
captured and inspected; the old3224 `aiz-projectile` filename is a Bloominator
BODY and earns no projectile credit. Original movie/core hashes, actual provider
bounds, prepared/published SAT/palette and source clocks are retained. HCZ0/30
engine clips now resolve without fixture copies: native0 publishes the title,
engine0 the waterfall; native full-movie HUD carry differs from standalone
bootstrap. [Exact images and limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#actual-native-and-engine-image-spots)
are bounded corroboration, not pixel/priority/waveform parity or joined fresh-
return title certification. No extra native wait or gameplay hydration was used.


## Cork native fragment graph — 2026-10-14

The placed Cork at($28C0,$047C) now retains its own SST as fragment0 and only
allocates N−1 checked higher siblings. Original-controller emerald-chain row1688
has the actual Tails break: source35+41..51 and all12 integer XY births match
the native aux record, including stationary Sonic's release. Independently
constructed native checkpoints cover pressure, old-velocity/fractional movement,
prior-render deletion and two-way recreation/two forward cycles; the component
camera edges cover320/352/400/528/800. A failed declared five-tick physical
approach was rejected and removed, rather than changing contact behavior.
Affected607 checks and111 source/rewind/timing guards pass on the owned Cork
source over `d8025e094`; strict original AIZ/initial HCZ/prefix2 remain green.
[Exact commands, negatives and limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#cork-same-record-fragment-and-icz-table-follow-up).
Whole-act donor/roster/viewport physical contact, complete registered-world Cork
windows and native rendered-pixel/audio parity remain unqualified. Allocation
sampling improves1688→2133 but whole-chain and alternate AIZ frontiers remain red.


## Rhinobot native waiter — 2026-10-14

The placed Rhinobot's $20×$20 empty-map waiter now consumes the preceding full
XY render sign, holds the cleared priority/header until Init, and distinguishes
immediate waiter deletion, range-free release and Slotted next-dispatch clear.
Native original-controller returned-AIZ slot6 at($2820,$0320) stays dormant
1051..2133 and clears2154; the engine now matches those retained observations.
20 independent component camera/header/recreation cases span declared widths
320/352/400/528/800 and both native flips. The affected599 and guard111 checks
pass, with the original AIZ/initial HCZ/two-prefix controls still green.
[Exact scope and commands](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#rhinobot-prior-render-waiter-and-native-range-phases).
Sampled allocation presence moves2133→2897; the alternate/whole-chain strict
frontiers remain red. This does not certify physical contact/registered-world
Rhinobot windows across products or native pixels/audio. Its charge/brake child
owners, slotted art/DPLC pressure and inherited nonnative dash timeout remain
separate source-conformance follow-ups.


## AIZ muzzle-flare native owner — 2026-10-14

Flare Init now precedes the ROM-backed raw cursor; F4 retains its final frame and
physical slot until the next dispatch. Its captured numeric parent3 reads live,
cleared and verified recycled headers with signed offsets/flip bits, and pure
recreation preserves birth metadata. The actual second returned-AIZ barrel pair
now uses19/20 at2897. Independent component19, existing actual-ROM napalm and
scalar/startup/SecondStrike consumers total619 passing checks; source/rewind/
timing guards111 pass on the exact owned candidate over8f7ec20a1.
[Native routines, matched negatives and commands](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-barrel-flare-raw-lifetime-and-physical-parent3).
The five declared component camera widths are320/352/400/528/800, with two-way
recreation/two45-frame cycles; this does not extend full donor/team/product or
registered-world physical route qualification. Unknown nonempty parent headers
remain unsupported. Original AIZ/initial HCZ/prefix2 are green; alternate and
whole-chain strict frontiers remain unchanged red. Sampled segment2 allocation
advances2897→2910 (native-only17/21). Next callback allocation and shot birth
movement remain concrete separate owners; native rendered/palette/SAT/priority
and audio parity are not certified by these fields or prior-build spot images.


## AIZ shot native INIT follow-up — 2026-10-14

Stationary common/Falling Init and cleared ROM raw state preserve native birthY,
Pause/Launch cursor/timer and$60 underflow ordering. Independent11 component
cases plus existing consumers pass630; guards111 pass, with forced recreation
and two45-entry cycles at declared320/352/400/528/800 camera widths. This does
not expand full host/donor/team/world-route qualification. Original AIZ/initial
HCZ/prefix2 pass; alternate/chain strict fields remain unchanged red. Actual
segment2 shot18 birth now816; next missing pair17/21 remains2910. Pure cutscene
shot recreation/exact links and barrel callback remain required next owners.
[Native routines, negative attribution and exact qualification](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-shot-stationary-init-and-cleared-raw-state).
Prior flare paragraph's shot-birth target is superseded by this qualified slice;
its next callback target and rendered/audio/full-product limits remain.


## AIZ shot exact live graph follow-up — 2026-10-14

Pure captured-spawn recreation and explicit exact parent/barrel ObjectRefs now
retain cutscene and fight shots independently of their camera-relative XY.
Nine new component cases plus existing consumers pass639; guards111 pass.
Moving metadata, both-way forced manager recreation, two45-entry cycles and
five declared320/352/400/528/800 cameras are covered. This supersedes the prior
shot factory gap, while full host/donor/roster/registered-world cold-route and
numeric cleared/recycled-parent pressure remain unqualified. The factory-only
change leaves ordinary dispatch unchanged; strict evidence remains scoped to
the preceding INIT source, and barrel callback2910 is required next work.
[Native links, exact negative and qualification](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-shot-pure-recreation-and-exact-live-links).


## AIZ barrel wait callback follow-up — 2026-10-14

Native $1C zero/underflow now allocates on the callback dispatch, before the
fourth-shot signed-counter Return tail. Seven independent activation/threshold/
manager replay cases plus consumers pass646; guards111 pass. Five declared
camera widths and two-way/two45-pass snapshot cycles are component evidence,
not full viewport/donor/team/registered-world route certification. Original
AIZ/initial HCZ/prefix2 remain green; chain/alternate strict errors unchanged.
Sampled segment2 presence advances2910→2977, native-only19. The previously
listed delayed callback target is superseded; physical explosion-controller
creation, shared native byte39 alias and real cutscene FallingShot admission
remain source-backed next obligations. Rendered/audio limits remain separate.
[Native sources and exact bounded qualification](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-barrel-same-dispatch-wait-callback).


## AIZ barrel byte39 follow-up — 2026-10-14

One native byte now supplies firing and drop-position cycling, including signed
Wait reads and unsigned wrap. Nine independent normal/flipped column/header/
replay cases plus consumers pass655; guards111 pass. Five declared component
cameras/two-way/two45-pass manager cycles retain the prior full host/donor/team/
registered-world limits. Original AIZ/HCZ/prefix2 pass; strict chain/alternate
errors and sampled native-only19 at2977 remain. This supersedes the prior byte39
alias obligation; physical Special explosion owner and cutscene FallingShot
admission remain required. No new rendered/audio conformance claim.
[Native source, negative and bounded qualification](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-barrel-shared-native-byte39).


## AIZ physical Special explosion follow-up — 2026-10-14

The actual parent callback creates an independent SST owner for Special/Exp02,
with native Init wait, finite count, checked allocation before RNG, high-word Y,
child-init SFX and next-pass clear. Sixteen local callback/pressure/metadata/
act-clear/manager+real-RNG replay cases and existing consumers pass676; guards111
pass. Five declared camera widths/two45-pass cycles are component evidence,
not full host/donor/team/cold-route/registered-world certification. The previous
physical-controller omission is superseded. Original AIZ/HCZ/prefix2 pass;
strict fields remain unchanged red. Presence advances2977→3067 native-only21,
preceding3066. Real cutscene FallingShot and its explosion-family/pending-clear
remain required. Rendered/SAT/PCM and arbitrary recycled-header pressure stay
unqualified. [Native sources and exact qualification](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-physical-special-explosion-owner).


## AIZ real FallingShot and copied Hitbox lifecycle follow-up — 2026-10-14

The cutscene selects the real FallingShot; Init clears its collision byte,
seven independent Hitboxes copy that role, and floor/F4 callbacks retain their
physical slots through final draw/touch before next-pass clearing. Full spawn
metadata and exact live links survive pure recreation. Sixteen new literal/
component/loaded-world vectors pass, alongside existing actual-ROM napalm4,
standalone7 and startup consumers.692 unchanged affected identities pass in
49960; seven repaired common-only family predicates pass84889 (runtime same),
not one699green invocation. Guards111 pass. Five component cameras and one
loaded-AIZ full registry/two45-pass replay retain cold-route/full viewport/
donor/team, numerical reused-parent and rendered/SAT/PCM breadth limits.

The preceding cutscene Falling/explosion-family omission is superseded. Original
AIZ/initial HCZ/two prefixes pass; strict fields remain unchanged red. Segment2
presence advances3067→3084, native-only18, preceding3081. A common-shot final
Obj_Wait marker (native18 B6 versus engine cleared) is the next confirmed owner.
[Native sources, exact bounded commands and outcomes](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-real-cutscene-fallingshot-and-copied-explosion-lifecycle).


## AIZ common-shot final wait follow-up — 2026-10-14

Common SIMPLE shots retain their native final Go_Delete marker through Draw,
then clear on the next dispatch without movement. Their MoveWait has no local
screen cutoff. Eight independent zero/underflow/offscreen/graph/loaded-world
vectors and all previous699 consumers pass in one707-case focused invocation;
guards111 pass. Five component cameras and declared full-registry two45-pass
replay retain full cold-route/viewport/donor/team/rendered/audio and numeric
parent-pressure limits. The preceding common-shot marker obligation is closed.

Original AIZ/initial HCZ/two prefixes remain green; strict fields remain red and
unchanged. Numeric slot presence advances3084→3244, preceding3209, at the AIZ
transition context. Native-only24..37/engine-only4 need full pointer/scene-boundary
attribution; low code bytes do not prove old actor survival. No transition fix
or shared allocator/clock change is inferred. [Native owner, exact negatives,
commands and outcomes](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-common-simple-shot-final-native-wait-and-physical-retirement).


## Native retained-scene handoff follow-up — 2026-10-14 local candidate

The previous3244 context omission is attributed and repaired locally:
AIZ1BGE_Finish retains physicalSST5..17; only active SuperTails stars102..105
receive object offsets. The cutscene graph retires through captured38bit4/code
markers and clears on the next dispatch. The transition floor likewise retains
its X7FFF/Delete_Current solid tail and status7 edge-balance suppression.
Debris Wait underflow swaps code without an early draw.827 affected/startup
cases and111 guards pass; five ROM-loaded production presets320/352/400/528/800
retain exact resource/slot/position identities and complete two45-frame full-world
cycles around receipt, marking and clearing. Native SuperTails four-slot graph,
full cold/donor/roster/rendered/audio breadth remain separate limits.

OriginalAIZ and initialHCZ pass; alternate returns its prior60 spans. Slot-key
presence advances3244→3887, preceding3858, native-only52/58. Former returned-AIZ
segment6 raw189 closes to0, but a new earlier results seam leaves the second
prefix red: segment4 rings111/0 at7589/raw51. Segment8 has raw1765/first6000 P2X
4997/4996; full chain still loses production ownership atinput68801/loadgen9.
This local candidate awaits the qualified paired RESULTS/core dependency and
matched composition, and is not an integrated or overall-green route claim.
[Exact native owners, rejected premises, commands and limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#native-aiz-retained-scene-and-transition-floor-handoff--2026-10-14).


### Current retained results/title follow-up (2026-10-14)

Paired core is composed in d3ca6c022. AIZ results retain their native title SST;
real table children own reset/retirement through movement/render latches. Five
production presets pass full-registry forced recreation/two45-pass cycles and
ROM art, plus checked failure/partial-prefix/exact-parent cases. Affected137
and guards100 pass. OriginalAIZ/initialHCZ and both return prefixes pass;
segment4 rings51 errors→0. Segment8 raw1765/first6000 P2X4997/4996,
input68801/loadgen9 walk-stop and alternate60 spans remain. This updates the
preceding pending dependency/ring-seam claim; cold/donor/team/rendered/audio,
SuperTails and native pressure/cache limits remain.
[Native owner and exact evidence](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#aiz-retained-resultstitle-sst-and-real-child-latches--2026-10-14).


Current follow-up (2026-10-15): optional physical title labels exposed an actual
hidden-monitor conversion SST leak. Native loc83760 keeps that SST occupied;
the Java replacement now detaches its obsolete handle before manager cleanup.
Negative52375 fails; five production-preset local manager/recreation/two45-pass
world cases plus three components pass51420. Final24540 passes37 including all
12 title/transfer graph cases and existing monitor consumers. Canonical45615
retains original AIZ/initial HCZ/both-prefix passes and now completes physical
labels without duplicate owners. AIZ5 raw1765 and returned HCZ input68801 stop,
alternate60 spans, broader cold/donor/team, header pressure and rendered/audio
limits remain open. See the owning audit's hidden-monitor section; no natural
cold-signpost completion is inferred from the declared local contact.

## Explicit JUnit obligation pilot (2026-10-15, candidate)

The standalone [pilot](../../../../tools/testing/check_route_obligations.py)
binds the existing AIZ1 and HCZ1 representative route, selected two-cycle rewind
and outgoing act-reload checks to an [exact expected inventory](../../../../tools/testing/aiz-hcz-route-obligations.json).
The 26 cases are independent AIZ1/HCZ1 checks, including HCZ horizontal-admission
rewind at the five widths already named by that test; they do not constitute a
continuous AIZ-to-HCZ route or certify the remaining act/configuration products.

```sh
python3 tools/testing/check_route_obligations.py --preflight --rom s3k=/absolute/path/to/existing/s3k-ROM.gen
python3 tools/testing/check_route_obligations.py --run --rom s3k=/absolute/path/to/existing/s3k-ROM.gen
```

The first command checks the canonical original-ROM SHA-1, committed input
fixtures and current source class/method references without executing tests.
The second also checks Java 25, queues exactly the six inventoried classes,
removes only their required `slow-suite` exclusion, verifies fresh XML case
identities and removes the temporary reports even on failure. No opt-in breadth
properties are needed by these six classes. A missing, skipped, failed,
duplicate or unexpected selected identity fails the lane. Existing reports can
be inspected with `--reports DIR`; the caller must supply fresh reports from
the exact tested build, command and fixtures. The tool does not infer their
build identity or freshness. Enum inventory bindings accept plain constants
only; constructor arguments, bodies and other unsupported declarations fail
closed before Maven. Integer sources bind to the selected method's own annotation,
ignore comments/literals and reject unsupported expressions. Ordinary developer skips and unrelated report identities remain
unchanged. The general LTS-04/LTS-05 migration remains open.

Observed on the remediation candidate at base `1f612ff7271d175ea17eb775e786845d5b2ee12a`:
all **26 exact pilot cases passed**, with zero failures, errors or skips
(Maven execution 93 seconds; queue wait is recorded separately). This qualified
the parameterized XML identities. The later annotation-binding hardening retains
those same identities and passes 24 Python safety checks plus canonical
preflight. See the [task record](../../plans/2026-10-15-architecture-debt-remediation.md#runtime-validation-handoff-2026-10-16)
for commands and limits. This is bounded pilot evidence; remaining matrix
obligations and general enforcement stay open.

## AnPal_AIZ1 gate, carried read and branch order — 2026-10-17

AnPal_AIZ1's counters+$02 cycle (the water colours on line 4, 12-14) now follows the
ROM's `Palette_cycle_counters+$00` gate (3217) instead of `Level_started_flag`. The plane
intro sets the gate at Sonic's x `$13D0` (loc_67712), and the AIZ1 Knuckles cutscene clears
it on exit (loc_61F22). Both writes take effect after that frame's palette call. A carried
fire-branch value is read before it wraps (3219-3223), and the fire/normal choice uses
AIZ1_Resize's decision from the previous frame (loc_1C46E). The counters belong to the
shared AnPal bank, so AIZ2 inherits the corrected phase. Tests:
- `TestS3kAiz1AnPalBoundaries`: old-source red 2/2.
- `TestS3kAiz1AnPalGateWrites`: object phase, two restore/forward-replay cycles and the
  fresh-load clear.

Native trickle equality across the act change is recorded in the
[AIZ2 matrix](s3k-aiz2.md#aiz1-boundaries-behind-the-inherited-phase--2026-10-17). The
Knuckles AIZ1 start has no plane intro, so its gate stays clear; the witness below
compares it with native.

### Knuckles AIZ1 start witness — 2026-10-17

Native: the no-write harness on the Knuckles complete-run BK2 from its AIZ1 entry
(frames 810-1110; frame 810 is Level_frame_counter 1). Engine: a headless Knuckles AIZ1
boot driven by the same BK2. Paired by Level_frame_counter 1-300:
- The AnPal bank is equal on all 300 frames. counters+$00 stays clear, as no plane intro
  runs, and counters+$02 advances with every normal run.
- The AnPal_AIZ1 destination words are equal on 279. Frames 1-17 are the level fade,
  which native writes into `Normal_palette` and the engine presents separately.
- On frames 35-38, native shows L4c12-14 at the base colours. AIZ1_Resize's camera-`$1308`
  `LoadPalette_Immediate PalID_AIZ` reloaded them (sonic3k.asm:38919-38929).

Fixed 2026-10-17 in `Sonic3kAIZEvents`:
- The non-intro start keeps its `Pal_AIZ` preload but no longer marks routine 0's
  `$1308` reload done.
- The reload adds the `Player_mode` 3 `word_4FAE4` colours at line 4 colours 8-11
  (38928-38935).
- Knuckles' AIZ1_ScreenInit colours (`Adjust_AIZ1Chunks2`, 104337-104338 and
  104498-104502) go to the Target palette and the level palette.
- The engine-only routine-4 palette re-apply now runs only when the plane intro ran. ROM
  routines 2 and 4 load no palette, and on non-intro starts the re-apply wiped those
  colours on the first frame.

`TestS3kAiz1MainPaletteReload` derives the expected words from the ROM's `PalPoint` entry
and `word_4FAE4`. It covers Knuckles across the camera boundary with the complete-run
input, a Sonic control, two restore and forward-replay cycles, and a fresh load. It fails
2 of 3 on `9183561ea` sources: Knuckles' colours are absent before the boundary, and no
reload happens at it. With the change, Normal_palette lines 2-4 equal native on all 38
counters from 23 to 60, the boundary frames included. Evidence:
`spot-checks/aiz-palette-bank-v4/`.

Evidence and native pictures (`native-knux-aiz1-lfc34/35/39`):
`spot-checks/aiz-palette-bank-v3/`.


## Physical miniboss control follow-up — 2026-10-17 R3

The [AIZ2 R3 record](s3k-aiz2.md#physical-miniboss-handoff-and-alternate-ring-blocker--2026-10-17-r3)
keeps matched R1/R2 attribution and the complete native handoff correction.
Final1878 runtime passes original AIZ20463 rows, exact four standing rows/all20
assertions and the two-special-stage prefix;77 focused cases include twice-forced
full-world recreation. The distinct alternate remains UNQUALIFIED:11 ring errors,
first13740 rings1/2, ten newly exposed spans without native ring-position/writer
evidence. Native four-debris/full-SST, missing aux, configuration/BK2/cold/fullchain
limits remain; this is no act or integration certificate.
