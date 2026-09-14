# FBZ completion

Integration base: `435ec2e68c398bcc17af78e69f3e92b637bec90a` (`develop`).
Development tree: `.worktrees/ai-fbz-completion`, `feature/ai-fbz-completion`.

## Outcome and order

Complete both acts with ROM-backed mechanics, supported route breadth, rewind
coverage and measured presentation. Existing class coverage is not completion.

1. Reproduce the remaining S1 donor elevator squeeze. Test the user's proposed
   ordinary run-up and timed roll using live geometry and ordinary inputs,
   explicitly observing whether the existing assist fires. First use a short
   production-loaded scenario, then integrate the maneuver into the authentic
   complete route. Preserve acquisition, clearance, release and damage checks.
2. Refresh the canonical complete-run trace. Diagnose the earliest causal
   mismatch through production submissions and ROM ordering; do not relax the
   comparison contract. Validate timing changes on independent recordings.
3. Map existing entry, object, event, boss, lifecycle, rewind and complete-route
   coverage into per-act/character matrices. Add missing independent scenarios
   and required breadth; distinguish seeded local checks from authentic routes.
4. Complete reproducible visual evidence tooling, fresh native checkpoint and
   cadence capture, independent state/region review, semantic comparisons, then
   compatibility captures. Repair measured presentation mismatches.
5. Run combined change-based validation and relevant deeper lanes, integrate
   into the current main `develop`, push only `develop`, then account for and
   remove this task's worktree and local branch.

## Current evidence and uncertainties

The September 14 controller handover reports every width/team route completing
Act 2, with only S1 donation failing at the `$1DC0` squeeze. This is inherited
evidence. The existing S1 controller accelerates only to `$0110` before rolling
and seeks a geometry window for the `$0800` assist; it does not establish the
limits of a longer ordinary run-up. The current predictor assumes rolling
friction, while actual car landing invokes standing posture. Real execution is
the deciding evidence.

The starting strict replay was reproduced on `435ec2e68`: 5,666
errors, first row 34, `queue.s3k_kos_direct.busy` expected true/actual false.
That row submits the FBZ Blaster archive body at `$0DC6C4` to `$FFFFD000`.
Submission, first-child coordination and completion need measuring before a
cause can be assigned. Historical July trace outcomes are obsolete.

Visual acceptance is incomplete: native Java executors exist, compatibility
capture rejects, the amendment has no reviewed exact start state or visible
regions, and no reproducible aggregate capture/comparison host exists. The Lua
exporter covers start and five AniPLC series, not all late checkpoints/series.
BizHawk 2.11 preflight passes locally; native display access works when launched in the verified host environment.
Expect substantial capture/tooling work, not a short final verification.

## Validation and ownership

The user explicitly authorized a separate manually tracked 40-minute FBZ test
budget while KiS2 retains the shared receipt (2026-09-14). Preserve KiS2 accounting;
record every FBZ invocation and permit only one combined broad attempt. Reserve
the broad attempt until implementation and focused validation stabilize.
Focused route and trace baselines precede changes. Java 21, Lua 5.4 and
PowerShell preflight passed; all three existing runtime ROM SHA-1s match.
The independent `kis2-tier-two` delivery owns the active shared receipt/lock.
Do not replace or finish its receipt. All invocations are measured under the separately authorized accounting.
Report any mandatory-check/budget conflict before starting those checks.

Root owns route/controller/runtime changes and integration. Independent trace, coverage and native-capture workstreams have explicit bounded
ownership; their measured execution is charged to the same manual budget. Preserve unrelated submodule changes and
`raiscan-0.6-thoughts.md` in the main workspace.

## Measured changes and rejected approaches

The user's ordinary run-up/roll suggestion is feasible. The controller now
waits for an ascending car whose live slope surface will intercept the lower
half of the native catch band after ordinary floor acceleration. It runs from
west of the block, then presses DOWN alone. The engine still performs all
movement, posture changes and solid contacts. No trace state, velocity writes,
spindash, or squeeze assist supplies the crossing.

A brief airborne fall from the raised button is ordinary egress; rejecting all
airborne run-up frames aborted this valid approach. The first shorter roll
probes sometimes cleared the block but later stalled/crushed, so geometric
clearance alone was rejected as success: the test requires the exact car's
acquisition, safe crossing, release and continued movement.

Later S1 controller obstacles were independently resolved with ordinary inputs:
a full run-up through raised bottom magnetic columns, a jump over the nearby
Blaster, and repeated end-boss jumps. Reversing immediately on a boss hit turned
Sonic into an arm; preserving actual rebound direction reached seven hits.
A speculative early running-jump strategy stalled at the arena corner and was
rejected. Descending jumps now choose ordinary air input using live arm/flame
positions and projected landing clearance. This is test input authoring, not a
change to production physics or collision widths.

`mvn -Dmse=off -B -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical -Pfbz-routes
-Dtest=TestFbzCompatibilityMatrix#donatedMovementProfileCanReachTheMandatoryBossEntryWithoutSpindash
test` with all three absolute verified ROM properties passed **2 tests, zero
failures/errors/skips**, 56.20 seconds, on `9fa8fc0a0` plus the local controller
changes. Both S1 and S2 complete the mandatory route through the final SOZ
request. This is focused route evidence, not complete zone certification.

Production corrections integrated so far: `ef13df370` queues the ROM FBZ enemy
KosM batch; `e8307ddc2` preserves cage animation word writes and horizontal-chain
hand-step writes; `9fa8fc0a0` reads the platform clock's low byte without an
arithmetic increment. Complete V5 replay moved from 5,666 errors / first row 34
queue busy to 4,721 / first row 3888 mapping frame. An independent recording
improved from 5,227 to 5,090 errors; its earlier Tails subpixel frontier remains.
These results remain red and are not ROM parity.

Native execution consumed 276.557 seconds and produced 1,025 RAM observations
and nine diagnostic PNGs in the external task directory. No accepted exact
start or AniPLC series was produced. The exporter incorrectly read `$F60C`,
treated the `$F60E` VDP command template as live display state, supplied an
algorithm name where `memory.hash_region` expects a domain, and mistook a
reused title-card slot for a live title card. Correcting only the address did
not repair the invalid display-state assumption. Keep these diagnostics
separate from accepted visual evidence; do not amend frozen references from
an unpaired frame or infer display readiness from the command template.

Further proven corrections: `8eb04d603` adds an internal zone-owned tumble
presentation policy (public Mod API unchanged) and preserves snake standing
ownership across dynamic position updates. `c31bdbd7a` resets tumble fields when
an airborne rider lands on a moving cage, matching `Player_TouchFloor` for all
native characters. Final strict counts are 4,667 / first 13,585 Y for the complete
recording and 5,065 / first 116 Tails subpixel for the independent recording.
All early cage mapping discrepancies are gone; the next physics owner is a
rolling landing on a magnetic platform (the earlier disappearing-platform attribution was incorrect). Trace parity remains incomplete.

The expanded Act 1 lifecycle method passes all 105 width × donor × team
combinations plus its final native reset. All 96 local arrival delays and 15
width/donor squeeze cases pass. Initial test failures came from assuming a
released cached latch must be null and from applying viewport configuration
before fixture initialization overwrote it. The production authority checks
now inspect actual riding state and the test asserts the effective width.

The active-car rewind spot uncovered a production defect after comparison
excluded only the shared documented nonsemantic fields (`epochAtCapture`,
`bucketsDirty`, `peakSlotCount`). Generic dynamic reconstruction registered
`execOrder` while the new instance still had slot -1; phase-2 field restoration
later supplied its real slot. The car's moving spawn record also has a new
identity after restore, so both riding lookup routes failed. Restoration must
assign every captured dynamic slot before callbacks and execution-table
registration, as it already did for adopted construction children. No saved
contact or gameplay fields are removed from the regression comparison.

The restoration fix now passes all three independent squeeze rewind spots
(before entry, active car, after exit), each with two capture/restore/forward
replay cycles. The no-ROM dynamic-solid regression proves the captured slot,
restored riding authority and exactly one execution tick across two restores.
Focused command: `mvn -Dmse=off -B -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical
-Dtest=TestFbzSqueezeOrdinaryRoll#productionRegistryRestoresAndReplaysTheLocalCrossing,TestObjectManagerRewindSnapshot,TestObjectManagerRewindDynamicClassification,TestFbzVisualExporterGuard
test` with all absolute verified ROM properties and `LUA_BIN=/usr/bin/lua5.4`:
**30 tests, zero failures/errors/skips**, 46.49 seconds. The Lua exporter guard
exercises actual reads, domain restoration on success/failure and refusal to
publish unverified display evidence; it runs in the guard lane, which provisions
Lua, instead of adding a new ordinary-lane dependency.

Independent review found no blocking runtime/controller issues. The slot fix
preserves adopted and slotless behavior, allocator reservations, phase-2
reference restoration and public snapshot/API contracts. The test comparator
retains all gameplay fields, including newer fields omitted by the older
shared comparator's explicit field list; only three documented restore
bookkeeping fields are excluded. Temporary probes were removed.

The change-based plan selects the full ordinary inventory plus structural
guards, with two ordinary workers. Recent repository measurements put this at
about 7–8 minutes; it is not a short check, and the older normalization measured
34 minutes. Preflight passes with Java 21, Lua 5.4 selected explicitly and
PowerShell. An invocation without LUA_BIN correctly failed preflight before
launching any tests; that missing environment variable is repaired.
No broad attempt has been consumed. The unmodified runner selection/execution
can use the user-authorized manual ledger without touching KiS2's shared Git
receipt; neither selection rules nor retry protection are changed.

## Candidate checkpoint and remaining budget

The final exhaustive command `mvn -Dmse=off -B -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical -Pfbz-routes -Dtest=TestFbzCompatibilityMatrix
test`, with the same three verified absolute ROM paths, passes **11 tests,
zero failures/errors/skips**, 62.31 seconds. It covers four additional widths,
five Sonic-main team configurations and both active donors; every route reaches
the mandatory boss/capsule/SOZ handoff. Tails-main and Knuckles-main cold-start
completion are still missing; these Sonic-main results do not certify them.

Manual delivery accounting at this checkpoint:

| Execution | Seconds |
| --- | ---: |
| Root focused runs, including failed iterations and baseline | 694.604 |
| Independent trace lane, including baselines and probes | 743.860 |
| Native capture diagnostics, conservatively charged | 276.557 |
| **Total / 2,400-second cap** | **1,715.021** |
| Remaining | 684.979 |

No combined broad run, integration, push or final worktree cleanup has happened.
The main workspace remains develop with unrelated changes preserved. The single
broad attempt is reserved for the completed aggregate scope. Approximately eight
of the remaining eleven minutes are needed for that run under recent measured
conditions, leaving insufficient execution time for outstanding strict-trace,
native-reference/presentation and missing main-character route work. This is an
explicit validation-budget/completion conflict under the user's forty-minute
cap, not a claim that the local candidate is fully validated or FBZ complete.


## Continued delivery and requested demonstration

The user continued after the proposed increase to an **80-minute aggregate**
manual FBZ validation ceiling. Earlier execution remains charged; the combined
broad attempt is still reserved, and the shared KiS2 receipt remains untouched.

`dbc34c6d5` corrects the magnetic-polarity prelude's live frame-counter read;
`d5420f4ec` applies the same proven byte-address semantics to flame and missile
producers. The platform at the earlier row 13,585 frontier is native `$3B3FA`,
a magnetic platform, not a disappearing platform. `0a078cf87` exposes the
launcher companion's ROM `$20` balance width independently of its collision
parameter `$2B` and its parent's `$10` width.

The requested S1-donation video is `$FBZ_EVIDENCE_ROOT/s1-runup-roll/`
`S1-FBZ-run-up-roll.mp4`: 254 frames at 60 fps (4.233333 seconds), 960 × 672
nearest-neighbour enlargement of 320 × 224 gameplay. The captured runtime is
`6d0de860e`, with S3K FBZ Act 2, active S1 donation and Sonic alone. After the
explicit local start-position setup, all movement uses ordinary pad input.
There are exactly 60 frames before the run-up and 60 after full clearance;
the actual car is acquired and released once. No squeeze assist, spindash,
hurt or death occurs. The retained input script, compiled Input Log, state CSV
and capture receipt make the demonstration reproducible. This is a gameplay
video, not native pixel-parity evidence.

Two candidate cold Act 2 character drivers now exercise native Tails and
Knuckles independently. Their initial failures exposed Sonic-specific test
waypoints (centre height and jump reach); corrections use actual radii and
ordinary inputs, with all encounter/completion assertions retained. Both now pass every completion assertion; the final native-main evidence below
supersedes their earlier controller failures. The separate cold Act 1 candidate's raw
recorded pad sequence also remains incomplete: matching the opening input
latency improves the route, but later platform phase differences still cause
a missed jump. No comparison rows supply gameplay values to these drivers.


The temporary raw-movie Act 1 controller was not promoted as a passing test.
Delaying its recorded inputs by one frame matched the opening native position
samples through row 400, but later unrecorded load timing and floating-platform
phase made a fixed pad sequence miss the lower outdoor gap. Live landing and
predicted platform steering advanced the probe to `$0C5C` before death at frame
3,395; it did not establish a complete route. These are rejected controller
attempts, not reasons to alter production physics. The committed strict replay
remains the separate timing-aware oracle, and Act 1 completion remains open.

The production setup animation fix passed 102 focused tests, zero
failures/errors/skips, in 66.63 seconds: `TestFbzAnimatedTiles`,
`TestTraceReplayStartPositionPolicy`, `TestS3kMhzPatternAnimation`,
`TestS3kAiz1SkipHeadless`, both classes named `TestSonic3kLevelLoading`,
`TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`, and
`TestFbzVisualGameplayAdvanceContract`. The subsequent neutral method rename
and replay handoff-order reconciliation require aggregate validation.

The fresh paired engine start capture now accepts the independently observed
native state at level frame 35, including all raw animation timer/frame bytes.
The engine image is not identical: the complete-run native HUD carries score
and lives, while the engine cold start does not, and cloud phase still differs.
State acceptance therefore does not certify native pixel parity. Both images
remain outside the committed source tree in the task evidence directory.


### Act 1 normal-attack ownership correction

A bounded live replay probe on `190cbd408` plus setup-animation changes confirmed
that the miniboss activates at row 19,793: the production KosM job uses archive
`$1652B4`, payload `$1652B6`, destination `$A5C0`. Missing activation was rejected
as the explanation for the remaining queue comparison. At row 20,348, the engine
left terminal chain touched Sonic while the native five links were horizontal.

The owning native routines exposed a group of incorrect state ports:
`sub_6F830` clamps an unsigned angle bound instead of requiring exact equality;
`loc_6F338` uses that same clamp for the arm. `NORMAL_HOLD` is only `Obj_Wait`,
and `loc_6F360` clears arm bit 3 before root bit 2. Normal-fan terminal impact
writes root readiness, while arm readiness belongs to the later recycle tail.
`sub_6F8C8` also clamps overshoot, and `sub_6F8F2` deliberately differs at equality
(left accepts `$80`, right requires crossing). Focused boundary and complete
hold/recycle ownership regressions accompany those source-derived corrections.
The bounded probe was diagnostic only: its intentional early stop failed terminal
closure and is not reported as a passing replay. Full comparison remains required.


### Native-main Act 2 completion

`TestFbzMainCharacterCompletion` now proves separate native Tails and Knuckles
cold-start routes through every mandatory interaction, final boss defeat,
capsule release and the SOZ Act 0 request. The latest Knuckles method passed
with zero failures/errors/skips (`-Pfbz-routes`, explicit method selector,
`surefire:test`, 8.48 seconds including Maven; `190cbd408` plus local changes).
Tails passed in the preceding two-method run; that run's Knuckles failure was
an exhausted Sonic-specific route budget after already defeating the boss.

The lower-jump controller uses real spring/ledge support, ordinary native glide,
stopping distance and the boss's ROM-derived flame motion. Confirmed hits reset
a no-progress watchdog; the complete route remains bounded at 35,400 frames,
ten seconds before time-over. The capsule driver can jump from its actual body
support to the live button. No runtime movement, collision or damage allowance
was altered to make these routes pass. Failed earlier controllers (static flame
projection, fixed Sonic fight duration and repeating the Sonic capsule approach
from a successful body landing) are retained here as rejected approaches.


### Defeat fallthrough and upstream reconciliation

The later sign/results frontier exposed another direct ROM porting omission.
`loc_6F9DE` tail-calls `BossDefeated_StopTimer`, which falls through the assembler's
end-of-function banner into `BossDefeated`. That helper seeds `$2E=$3F`, awards
100 native score units (1,000 displayed points), and clears the render visibility
bit before returning. The engine had only paused the clock and converted the
boss on the next update. The correction uses the existing captured timer and
awards the points once; conversion now follows 64 subsequent boss dispatches.
No persistent renderer state is invented for the ROM's transient draw flag.

The six boss lifecycle tests passed with exact wait and score assertions.
The first focused run also passed 35 chain, nine defeat-child and 46 rewind
checks; five Act 1 integration rows exposed their obsolete four-update conversion
watchdogs. Those two watchdog sites now allow the native 64 updates, retaining
all worker-prefix, sign, results, event and transition assertions.

`0fe1d2990` records the setup, native-character route, arm-cycle and capture-tool
increment. `a8419498b` reconciles it with develop `ae767f351`: source merged cleanly,
and the sole conflict was append-only trace evidence, resolved by keeping both
FBZ and KiS2 entries. The destination's executable tree is identical to the
already validated KiS2 candidate `74006f23b` (only its verification prose differs).
Its documented full ordinary baseline passed 19,926 tests with 14 skips; guards
had the three specifically attributed failures recorded in the KiS2 plan.


The repaired lifecycle rerun passed all five affected Act 1 rows; both full strict
recordings remain red at the exact frontiers documented in the trace log.
Before combined validation, manual accounting totals 67.351
minutes of the authorized 80-minute cap, with zero broad attempts consumed.
The unmodified change-based selection against the original pinned base includes
2,538 ordinary candidate classes and all structural guards, using two ordinary
workers. Recent code-identical destination validation cost 636.43 seconds; budget
roughly eleven minutes and reserve one minute for bounded follow-up. The shared
task receipt remains untouched. The one combined attempt may not be repeated.


The final focused plunger check passed 36 tests, zero errors/skips, including real
standing-contact retention after the production signpost ending pose and rejection
of new controlled contacts. Complete replay reaches the next main physics frontier
at row 22,397, initially described as a one-row world-coordinate rebase
difference. The remaining-items investigation below corrects that diagnosis to
a persistent results-art readiness stall; the overall trace remains red. The detailed counts and rejected two-hook attempt are in the frontier
log. The paired native start/cadence and full Act 1 route acceptance remain open.

### Combined validation and final strict confirmation

`38e2e75aa` cleanly merges develop `5e3700a04`, including the automatic Maven
queue. That upstream change modifies the Python runner and its guidance, not
Java, POM or test selection policy. The user-authorized separate FBZ accounting
still retains its original base, 80-minute aggregate ceiling and one combined
attempt. Queue wait is recorded separately from execution. One obsolete wrapper
was cancelled while waiting (514.146 seconds, no Maven launch); its replacement
acquired the queue and completed without spending that wait as validation time.

The final independent strict command on `38e2e75aa`, queued Maven with
`-Dmse=off -B -Ptrace-replay-r7
-Dtest=TestS3kSonicTailsFbzSegmentTraceReplay surefire:test` and verified absolute
ROM properties, took 12.384 seconds: one failed test, no errors/skips,
**5,109 comparison errors, zero warnings, 33,712 rows**, first row 116 Tails
subpixel `$D000/$B800`. Together with the complete recording on `c84e63ab0`'s
source, the final strict pair remains red; this is not zone certification.

The one combined invocation used the unmodified runner's selection/execution
inside its automatic queue, with original base
`435ec2e68c398bcc17af78e69f3e92b637bec90a`, two ordinary workers, all guards,
and an 11.305-minute execution cap. Java 21, Lua 5.4 and PowerShell preflight
passed. Run `20260914T095245Z-c0c6fbd8` completed against clean `38e2e75aa`:

- Ordinary: 2,538 candidate classes, 2,539 reports, **20,098 tests; 20,082
  passed, one failure, one error, 14 skips**, 254.59 seconds.
- Guards: **667 tests; 663 passed, four failures, no errors/skips**,
  172.63 seconds. Whole wrapper execution cost 427.410 seconds.
- Skips were opt-in audio/rewind/performance/capture probes, unavailable EGL
  or local native audio references, the opt-in AIZ1 pilot, and the unchanged
  CPZ spin-tube capture assumption. No missing-ROM skip occurred.

The two ordinary failures were test contracts exposed by this delivery:
`TestFbzBossGraphRewind#act1MinibossFullNativeGraphRoundTripsAndReplaysDeterministically`
threw `Kosinski module queue is unavailable` because its graph-only stub had
not explicitly omitted the new ROM art service. The harness now returns null,
as the existing ROM-less miniboss rewind harness does; all graph assertions stay.
`TestSidekickCpuControllerLevelStart#levelBoundaryKillPreservesRomOnObjectBitOnEntryFrame`
expected a forced Death owner (`24`) instead of `-1`. S2 `KillCharacter` writes
the animation once and `Obj02_Dead` does not repeat it; the test now matches that
contract. Neither correction changes runtime behavior.

Three guard failures match the recorded KiS2/destination baseline exactly:
`TestBuildToolingGuard#traceChaserStaysExactOptionalAndOutsideOrdinaryBuilds`
and `TestTraceChaserBoundaryGuard#exactGitlinkAndNonFloatingConfigurationAreTracked`
expect gitlink `4fb6d080` while the base pins `9fd957bb`;
`TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree` flags
the unchanged `FbzRouteEvidenceProbe#printEvidence` and
`LevelSolidityMapProbe#writeSolidityMap`. The fourth,
`TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`,
still requires five exact direct-Maven/task-receipt strings removed by upstream's
queue guidance. Its matched destination check and the two focused test corrections
are recorded below. Consumed combined diagnostics were acknowledged and deleted;
no broad retry is used.

The matched guard command on unchanged main-workspace develop `5e3700a04`,
queued Maven `-Dmse=off -B -Pguards
-Dtest=TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap
test`, failed its one test with exactly the same five missing strings, no
errors/skips (18.595 seconds). Both guidance files and the guard source are
byte-identical to the candidate. This fourth failure is attributed to upstream;
it is not repaired as unrelated FBZ work.

The post-broad focused command, queued Maven `-Dmse=off -B
-Dtest=TestFbzBossGraphRewind,TestSidekickCpuControllerLevelStart,TestSidekickCpuDespawnParity
test`, passed **80 tests, zero failures/errors/skips** in 19.246 seconds:
three whole-graph rewind checks, sixteen level-start checks and 61 despawn
checks. The candidate's runtime tree is unchanged from the combined run;
only the two test contracts and this evidence changed afterward. This closes
the delivery's new ordinary failures without claiming that the red broad run
was green. Aggregate execution is **4,556.966 seconds / 75.949 minutes** of
the agreed 80-minute ceiling, one broad attempt. Queue waits are excluded.

Task cleanup audit: the clean timing branch's first five source/test changes
are represented by `157011549` → `ef13df370`, `5b7b60a70` → `e8307ddc2`,
`a083220ff` → `9fa8fc0a0`, `0597da5a1` → `8eb04d603`, and
`54d9ff443` → `c31bdbd7a`. Later timing commits are exact cherry-picks;
subsequent candidate differences are the reviewed setup/boss/route additions
and upstream integration. The clean older route-test tip `87d564454` is already
an ancestor of develop. These task branches contain no unknown uncommitted work.
The requested durable S1 video and native visual candidates remain outside the
repository under `$FBZ_CAPTURE_ROOT/fbz-completion-20260914/`.

Integration uses develop without switching the main workspace branch. The
validated runtime tree and the focused test corrections are the delivery
candidate; full Act 1 route proof, strict trace parity and accepted paired visual
evidence remain outstanding. Push and cleanup outcomes are reported with the
integrated commit, rather than being inferred from this local validation.

### Integration with the concurrent route delivery

The final FBZ test/evidence commit is `cb3c89f71`. Another session advanced
develop to `fd45b8b0c` between the final fetch and merge, integrating AIZ/HCZ
routes, AIZ rewind owners and the feature-branch CI destination fix. FBZ merged
cleanly as **`42c797e54`**, retaining both deliveries. Its shared S3K event-manager
change adds the AIZ tree rewind adapter independently of FBZ's art/setup changes;
no conflict required changing either behavior. Therefore the integrated tree
was not assumed identical to the earlier broad candidate.

The bounded post-integration command ran in the main workspace on `42c797e54`
through the automatic queue (all three verified absolute ROM properties supplied):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B \
  -Dtest=TestS3kAiz1RoutePilot,TestS3kAiz1CompatibilityRoutes,TestS3kHcz1RoutePilot,TestS3kAiz1EntryMatrix,TestS3kAiz1RouteRewind,TestS3kAiz1ReloadRewind,TestS3kAiz1SpringRecovery,TestAiz1IntroProgram,TestAizIntroPaletteCycler,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestFbzAnimatedTiles,TestFbzBossGraphRewind,TestFbzSqueezeOrdinaryRoll \
  -Dopenggf.aiz1.routes=true -Dopenggf.aiz1.entry=true \
  -Dopenggf.aiz1.recovery=true -Dopenggf.hcz1.pilot=true test
```

**223 tests passed, zero failures/errors/skips**, 116.448 seconds including Maven.
This covers the newly combined route/setup/rewind consumers and retains the four
required S3K bootstrap/loading/decoding/AIZ checks. The other delivery's affected
CI/build and rewind guards are recorded in its route-controller handover; the
same four unrelated baseline guard failures remain open. This focused integrated
result is not another full-suite pass. No runtime or test change follows it.

Final separate FBZ accounting: **4,673.415 seconds / 77.890 minutes**, one combined
broad attempt, within the agreed 80-minute ceiling. Queue waits did not consume
execution time. Whitespace, mirrored guidance/skills, proposal JSON and the
destination push policy were checked; this final follow-up changes prose only.

### Wall-spike rendering follow-up

The user's review of the S1 squeeze video exposed incorrect wall-spike art.
This separate follow-up starts from develop `ad68609e9` in
`bugfix/ai-fbz-wall-spikes`. `Obj_Spikes` initially uses shared upright art
`$49C`, overrides it with FBZ's animated `$200` bank, then `loc_23FD0` restores
shared `$494` art for mapping frames 4–7. The previous registration applied
`$200` as the base of the ordinary combined sheet: sideways pieces sampled
`$200`, while upright pieces incorrectly sampled `$208`. Rotating mappings or
changing spike collision/placement would treat the symptom, not that ROM branch.

A dedicated FBZ sheet builder retains the existing eight ROM mapping frames,
compact 16-pattern layout and flips, but binds upright patterns to `$200–$207`
and sideways patterns to `$494–$49B`. Both exact ranges remain registered for
GPU refresh; unrelated neighboring tiles do not invalidate this sheet.
There is no shared decoder, collision, timing or input change.

The new real-ROM orientation regression failed before the fix at Act 1/frame 0,
tile 0. Afterward, queued Maven with `-Dmse=off -B`, the existing absolute S3K
ROM property, and
`-Dtest=TestSonic3kObjectArtProvider,TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard,TestFbzAnimatedTiles,TestFbzPlcArtHandoffs,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed **161 tests, zero failures/errors/skips**, Maven 52.348 seconds.
This includes the complete ROM-conditional art-registry crawler, exact geometry
and pattern identity for every spike frame in both acts, refresh boundaries,
animation/rewind, and the required S3K loading checks.

The change plan against `ad68609e9` selects all 2,553 ordinary classes and guards
because these art builders live in shared files. Proportionate validation replaces
that broad run here: the only changed production selection is FBZ's spike-sheet
builder, with both source banks, mapping shape, renderer refresh and unaffected
S3K loading directly exercised. Java 21/Lua 5.4/PowerShell preflight passed.
This is focused validation, not a full-suite or strict replay pass.

The production capture was repeated with the original input, S1 donation active,
and the same `$1CF0,$076C` local setup. All 582 comparable gameplay-state rows
match the previous video exactly, including position, velocity, camera, animation,
rolling, hurt/death and spindash fields. Reviewed frames 328 and 498 show the
correct sideways spikes. The replacement MP4 contains frames 328–581: 254 frames
at 60 fps, 960×672, 4.233 seconds, with exactly 60 intro and 60 trailing frames.
Captures and reproduction inputs remain in the external `fbz-wall-spikes-20260914`
task directory. Full FBZ visual checkpoint acceptance remains open.

### Remaining-items continuation after video approval

The user authorized the remaining ordinary Act 1 route, strict replay and visual
acceptance work after approving the corrected wall-spike video. The integration
base is develop `51677cdd2`; the coordinating tree is
`feature/ai-fbz-remaining`. Separate local worktrees own the ordinary input
controller, Tails replay frontiers, and native visual comparisons. The coordinator
owns the ending flow and combined verification. This continuation preserves the
frozen visual proposal and distinguishes new evidence from acceptance.

A read-only results-owner probe overturns the earlier description of frame 22397
as an isolated coordinate-rebase difference. On the base, results remain in their
creation state with art unready: Act 2 never reloads. Row 22398 is an un-compared
lag row, not recovery. The three results fingerprints match the native jobs,
but their ordinals are 265/266/267 instead of 266/267/268. `loc_6EEA8` queues
`ArtKosM_FBZMiniboss` (`$1652B4` → VRAM `$A5C0`); the engine's legacy DMA queue
submission never entered the physical hardware-timing ledger. Adding the native
parent to the canonical queue and claiming its prepared result restores the
results-art completion and synchronous reload at row 22397. The existing DMA
journal remains responsible for pattern writes and their rewind history. No
trace row supplies gameplay state or bypasses readiness admission.

The same probe finds results initialized one dispatch early: FBZ's sign omitted
the existing grounded-results/native-control-slot contract. Retaining the boss's
actual control boundary makes a lower first-free results slot wait for the next
object pass, matching `Obj_EndSignResults`'s `AllocateObject`. The engine's free
slot is 4 versus native 5 in this recording; the slot difference remains distinct
from the corrected dispatch boundary and is not hidden with a reserved slot.

The newly reached reload then exposes a second ownership error: it submits the
three FBZ enemy-art parents immediately. `FBZ1BGE_Normal` calls `Load_Level` and
`LoadSolids`, while the later title teardown owns `LoadEnemyArt`. The event request
now uses the existing `TITLE_OWNER` admission policy, as other ordinary act
handoffs do. The transition regression checks that the complete three-entry batch
is retained but no physical handles have been submitted at the resource reload.

Focused ending verification (queued Maven, Java 21, absolute main-workspace
S3K ROM): `-Ptrace-replay-r7 -Dtest=TestS3kFbzCompleteRunTraceReplay,TestFbzAct1Miniboss,TestFbzMinibossRewind test`
passed 53 boss/rewind tests, with the expected strict replay failure and zero
skips (64 seconds). The allocation follow-up passed 26 sign/boss tests, zero
skips; the transition/title-owner follow-up passed 13 transition/PLC tests, zero
skips. Strict complete errors move 4501 → 4361 → 4353 → 4323; all runs cover
44,152 rows with zero warnings. The unchanged overall first error is still row
16600 Tails animation, pending the parallel Tails fixes. Queue parity now reaches
row 22868, where the next title publication/control-release boundary differs.
The temporary read-only observer has been removed. These are focused results,
not full-suite or strict-replay passes.

The combined launcher/Tails and ending tree initially reports 3502 errors, first
20795 Tails landing. `Restore_PlayerControl2` also writes Wait to both animation
bytes, clears the animation clocks and Status_InAir, and leaves controller locks,
velocities and stood-on identity untouched. The retained FBZ boss now performs
those writes. `loc_2DD06` only replaces the results SST's code pointer; the newly
appended TITLE_CARD_INIT phase defers the art submission until its next dispatch.
The focused title/child run passed 37 tests, zero skips, with the expected strict
failure (3493 errors, still first20795); title queue parity then reaches26025.

The next large main-player displacement is caused by the transition preserving
engine-only horizontal easing targets in Act1 coordinates while rebasing the
current bounds. During the roughly470-frame results wait, the left bound creeps
right; the first ordinary player boundary check then clamps Sonic to$03DE. Both
horizontal targets now translate with their corresponding world bounds. The
old transition test's raw-target-preservation expectation was incorrect; it now
asserts translated targets while vertical targets remain unchanged. All12
transition/rewind checks pass, zero skips (queued Maven69seconds). The complete
replay resumes its Act2 input route and reports5508 errors, still first20795.
The increased total reflects a different downstream route, not a verdict about
the correction; main X now agrees until23035, and the next camera boundary
frontier is23014. Native title/worker handoff is23011, while the engine's title
provider completes23021. The remaining carried-title/worker ordering is open.
Temporary title observers are removed from the committed tests.
