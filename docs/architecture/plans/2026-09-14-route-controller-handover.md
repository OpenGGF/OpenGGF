# Live-state route controllers: handover (2026-09-14)

> Continuation on `feature/ai-gameplay-capture`, based at `35488abb6`:
> entry breadth now covers 15 width/donor configurations with two rewind replays,
> the late intro gate preserves native timing, and the opposing spring chain
> now has an independent negative-control/rewind obligation. All seven AIZ route axes
> reach the AIZ2 reload, and the HCZ pilot reaches its production act-2 reload; the [AIZ1 matrix](../validation/levels/s3k-aiz1-sonic.md) records the new
> route/rewind evidence and remaining frontiers. The history below describes
> the original develop delivery; only its reviewed helpers, pilot and documents
> were imported into this branch.

Handover from the session that produced the route-controller write-up, the
shared route primitives and the AIZ act 1 pilot. Everything below is on
`develop` at `643e5f332`; nothing is uncommitted.

## Where things are

| Artefact | Location |
|---|---|
| Technique write-up and application assessment | [research/2026-09-13-live-state-route-controllers.md](../research/2026-09-13-live-state-route-controllers.md) |
| AIZ1 pilot result, rejected approach and kill evidence | [validation/2026-09-13-aiz1-route-pilot.md](../validation/2026-09-13-aiz1-route-pilot.md) |
| Shared primitives | `src/test/java/com/openggf/tests/route/` (`InputRun`, `InputProgram` + `Cursor`, `RouteSteering`, `ObjectLifetimeFrames`, `RecentFrameLog`, `SidekickAudit`, `TestInputProgram`) |
| FBZ2 route controller (first zone, hybrid) | `src/test/java/com/openggf/game/sonic3k/objects/TestFbzAct2TraversalPreboss.java`; matrix in `src/test/java/com/openggf/tests/TestFbzCompatibilityMatrix.java` (`-Pfbz-routes`) |
| AIZ1 pilot (second zone) | `src/test/java/com/openggf/game/sonic3k/objects/TestS3kAiz1RoutePilot.java`, opt-in |
| FBZ2 byte-identity probe | `src/test/java/com/openggf/tests/FbzRouteEvidenceProbe.java`, opt-in |
| Helper index | [agent-workflow/README.md](../../agent-workflow/README.md), "Test harness helpers" |
| Level-test standard and backlog | [level-test-standard.md](../../guide/contributing/level-test-standard.md), [level-test-coverage.md](../../status/level-test-coverage.md) |

Commit trail (all on `develop`): write-up `278c5baf7`; primitives extraction
`610464952`; review fixes `ad40500d7`; evidence probe `921626c82`; pilot
rewrite `27dd957b3`; merge `643e5f332`. Feature branches
`feature/ai-route-primitives` and `feature/ai-aiz1-route-pilot` are pushed and
fully merged; they can be deleted.

## State of the technique

- **FBZ2**: 23 of 24 matrix rows green at develop (every width and team
  completes act 2 to the SOZ request; only the S1 donor row is red, at the
  `$1DC0` Obj28 squeeze). CPU Tails deaths on the route are ROM behaviour.
- **AIZ1**: the native 320 Sonic+Tails route completes to the act-2 reload in
  about 3 s from the fixture's own BK2 with no per-hazard gates. The earlier
  "dense hazard chain" finding was a pilot bug (program resumed 42 frames
  early) and is withdrawn; see the validation record.
- **Key rule learned**: a fixture with a recorded pre-level prefix (289 rows
  on `aiz1_to_hcz_fullrun`) plays recorded row `r` on engine frame
  `r - prefix`; the sanctioned trace replay never ticks those rows.
  `TraceReplayBootstrap.preLevelFrameCountForTraceReplay(trace)` gives the
  count; `InputProgram.fromRecording(movie, offset + prefix, offset + end)`
  gives the program. Preserve recorded neutral rows. Only when the first non-neutral row arrives
  before `Camera.isLevelStarted()` may the controller hold that row until the
  live owner releases control; never resume early.

## Commands

```bash
# AIZ1 pilot (the default Maven silent extension swallows -D without -Dmse=off)
mvn -Dmse=off -Dopenggf.aiz1.pilot=true "-Dtest=TestS3kAiz1RoutePilot" \
  "-Ds3k.rom.path=/abs/s3k.gen" test

# FBZ2 evidence byte-identity (run before and after a refactor, diff the 11 lines)
mvn -Dmse=off -Dopenggf.fbz.evidence=true "-Dtest=FbzRouteEvidenceProbe" \
  "-Ds3k.rom.path=/abs/s3k.gen" "-Dsonic1.rom.path=/abs/s1.gen" "-Dsonic2.rom.path=/abs/s2.gen" test
# the lines go to Maven stdout: redirect the run to a log and grep "^EVIDENCE\|^FAIL" there

# FBZ2 matrix (slow; ~24 rows)
mvn -Dmse=off -Pfbz-routes test -Ds3k.rom.path=... -Dsonic1.rom.path=... -Dsonic2.rom.path=...
```

`LUA_BIN=/usr/bin/lua5.4` is needed for the runner preflight. ROMs are
symlinked into every worktree by the post-checkout hook.

## Validation state to inherit

The review-fix delivery ran under task receipt `aiz1-review-fixes-2`
(base `923f14188`, finished) with focused validation only: 11-row FBZ2
evidence byte-identical before/after, 67 ordinary tests over every consumer
of the route package, the pilot, and CI's push-policy validator on the
develop merge. No broad category run and no `-Pguards` run were made for
these test-only changes. A future delivery that touches production code
starts its own receipt; the runner's change-based plan will select the full
ordinary suite whenever `src/test/java/com/openggf/tests/route/` changes
("shared test infrastructure"), which is disproportionate for helper-only
edits when the evidence probe plus the consumers are green; say so in the
report rather than running 34 minutes.

## Original next steps and current disposition

1. **Delivered: promote the AIZ1 pilot into the per-act matrix.** Remove the
   `openggf.aiz1.pilot` opt-in, make it the native 320 Sonic+Tails row of an
   AIZ act 1 matrix under the level-test standard, add the standard's rewind
   spots (intro handoff, Knuckles cutscene, hollow-tree camera lock, act-2
   reload; `TestRewindAcrossActBoundary` already drives this fixture), and
   link the matrix from `docs/status/level-test-coverage.md`.
2. **Implemented: AIZ1 width and donor rows** (400/512/640/800, S1/S2 donors). Use
   `FbzRouteEvidenceProbe` as the model for a per-row evidence print. Expect
   widescreen culling and S1 donor physics to be the divergence points; that
   is where the first AIZ live gates belong, authored from live objects only.
3. **Implemented: HCZ act 1 pilot** using the same ordinary-input authority; the
   [HCZ1 matrix](../validation/levels/s3k-hcz1-sonic.md) records the water route and
   live miniboss/reload, plus inherited coverage gaps.
4. **Stage interface deferred.** The two completed routes share the existing
   input/steering/audit primitives, while their ownership and navigation gates
   remain zone-specific. A new generic stage API is not needed for these routes;
   extracting one would add an unproven contract beyond this green-route delivery.
5. Small follow-ups: `TestFbzCompatibilityMatrix` still declares its own
   `InputRun`/`stepMask` (use the shared types); fold the row-offset rule into
   the research doc's cost model (section 4); the commit hook only detects
   the short-form `@ModApi` annotation, so fully qualified
   `@com.openggf.game.ModApi` classes (e.g. `SidekickCpuController`) escape
   the signature-pin coupling; grep both spellings before adding public
   members.

## Hazards specific to this work

- Worktree-isolated sessions cannot run `git -C`, heredocs or `$(...)` around
  git; write scripts under `target/` and call them by absolute path.
- The shared-receipt interference recorded above was removed by the Maven queue
  cleanup. Submit category runs or `tools/testing/maven_queue.py` commands; they
  wait automatically, without task identities or manual time accounting.
- Trace data is comparison-only. The pilot reads the trace for metadata, the
  pre-level count and the act-change row; it never hydrates engine state
  from physics rows, and neither should a successor.
- The route controllers assert milestones from live objects and never key on
  width, donor or frame index; keep it that way when adding rows.


## CI blockers found during green-route delivery

The pre-task feature head `f1843f54a1` had a red push run
[34822783943](https://github.com/OpenGGF/OpenGGF/actions/runs/34822783943):
18,661 tests, two failures, zero errors, 2,738 skips. These failures predate the
new route controllers:

- `TestObjectPlacementEncoding.commonParserPreservesDescendingFullXOrderInsideOnePlacementColumn`
  expected descending ring X order, contrary to `RingsMgr_SortRings` and the
  already-correct parser. Develop's focused correction `49fb9d942` was imported
  as `cecebd2b8`, preserving the separate ROM object-order assertion.
- `TestModApiReleasePolicy.destinationPropertyIsOptionalButMustAgreeWhenPresent`
  received `feature/ai-gameplay-capture` from the push workflow. The optional
  property accepts release integration destinations, not feature refs. The push
  command now adds it only for `master`, `develop` and `next`; descriptor checks,
  canonical destination agreement, PR validation and Maven test selection remain
  intact. No policy descriptor or API pin changed.

Focused verification: `mvn -Dmse=off
-Dtest=TestObjectPlacementEncoding,Sonic2RingPlacementTest,TestRingViewportWindow,TestModApiReleasePolicy
-DmodApi.destinationBranch=develop test` with existing absolute ROM properties:
**34 cases, zero failures/errors/skips**, 19.554 seconds. The actual push script
also passed `bash -n` and six stub-Maven argument scenarios (three integration
branches, feature, bugfix and a literal shell-looking feature name). The shell block
was extracted from the workflow directly for these checks.

Validation remains proportionate: the added CI change repairs the optional
branch-context argument only, without changing build commands, test selection,
release rules or production behavior. The existing remote run supplies the
pre-change failure identities; a new push must still pass its normal CI gate.


Review also identified the existing structural assertion requiring the old push
argument. `TestBuildToolingGuard` now recognises only the CI push step's explicit
canonical-branch dispatch; PR and release destination requirements remain intact.
`mvn -Dmse=off -Pguards -Dtest=TestBuildToolingGuard test` passed all **118 cases,
zero failures/errors/skips**, in 51.789 seconds in a fresh guard JVM. This is the
build-tooling guard class, not the full structural-guard suite.


## Green-route implementation record

`f11cff617` completes the seven AIZ width/donor axes; native 320px retains
the recorded-only assertion. `d9402b76a` completes the HCZ water route and
six-hit miniboss through the production act-2 reload. `0139a19c3` repairs
the optional CI destination argument and its structural guard; `cecebd2b8`
imports the existing develop ring-order test correction. No runtime physics
or trace-authority contract changed.

The final change-based plan selects 2,524 ordinary classes plus guards because
the private controller and CI/guard paths fall back to broad classification.
Focused validation is used under the repository's proportionate exception:
these edits are private route logic, a corrected assertion, and an optional
branch-context argument; relevant route consumers, edge/reload/rewind checks,
argument scenarios and the complete affected guard class were exercised.
Test selection, Maven flags and release policy are unchanged. This is not a
local full-suite pass. The normal feature push CI remains required.

HCZ's final cleaned controller passed in 24.81 seconds, at frame 12,583 with
zero failures/errors/skips and identical emitted pads to its pre-cleanup
success. HCZ investigation/checks measured 1,256.97 seconds; its receipt
conservatively charged 1,328.16 seconds (71.19 seconds overcount, retained
rather than rewriting shared accounting).


Post-integration verification on `feature/ai-gameplay-capture` merge
`5bcd104e8`: `mvn -Dmse=off
-Dtest=TestS3kAiz1RoutePilot,TestS3kAiz1CompatibilityRoutes,TestS3kHcz1RoutePilot
-Dopenggf.aiz1.routes=true -Dopenggf.hcz1.pilot=true test`, with all three
existing absolute ROM paths, passed **8 cases, zero failures/errors/skips**
in 37.576 seconds. No integration conflicts occurred. Main remains on
`develop`; its existing dirty reference submodules and user note are preserved.


## Integration into develop

The user authorized integration of feature head `d632c63da` into updated
`develop` base `5e3700a04`. Overlapping documentation was reconciled by
retaining develop's KiS2/HCZ/FBZ findings, Maven queue instructions and shared
helper backlog note, alongside the completed route evidence. The older AIZ
pilot was replaced by the verified matrix representative with native
recorded-only assertion and live continuation for compatibility rows.
The ring-order correction was already present on develop.

The actual integration adds AIZ-specific snapshot values/registration, private
route tests and the optional CI branch argument; it does not change shared
rewind algorithms, physics, Maven selection or release contracts. The
change-based plan falls back to 2,543 ordinary classes plus guards.
Proportionate validation exercises all eight routes against develop's newer
HCZ implementation, entry and rewind/reload/spring obligations, required S3K
loading checks, and affected rewind/build guards. Existing feature CI evidence
is retained; the merged develop push must pass its own smoke gate.

The previous receipt figures above are historical. Develop now queues Maven
commands automatically and no longer registers task receipts or cumulative
budgets. The main workspace remains on develop throughout integration; its
pre-existing dirty disassembly submodules and user note are not staged.

Merged-tree focused command (all three existing absolute ROM properties supplied):

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestS3kAiz1RoutePilot,TestS3kAiz1CompatibilityRoutes,TestS3kHcz1RoutePilot,TestS3kAiz1EntryMatrix,TestS3kAiz1RouteRewind,TestS3kAiz1ReloadRewind,TestS3kAiz1SpringRecovery,TestAiz1IntroProgram,TestAizIntroPaletteCycler,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils \
  -Dopenggf.aiz1.routes=true -Dopenggf.aiz1.entry=true \
  -Dopenggf.aiz1.recovery=true -Dopenggf.hcz1.pilot=true test
```

Result: **101 cases, zero failures/errors/skips**, Maven 1:40 including
compilation. HCZ retains frame 12,583 and emitted-pad hash
`-3592398407471656479` against the newer develop implementation. Preflight
passed with `LUA_BIN=/usr/bin/lua5.4`; the default Lua executable failed the
version check before any tests ran. The Maven queue required Git-metadata
write access, then waited and ran normally.

Affected guards: `LUA_BIN=/usr/bin/lua5.4 python3
tools/testing/maven_queue.py -Dmse=off -Pguards
-Dtest=TestBuildToolingGuard,TestStaticStateRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestZoneEventRewindSchemaGuard
-DmodApi.destinationBranch=develop test`: **138 cases, 2 failures, zero
errors/skips**, Maven 52.021 seconds. All 20 rewind checks and 116 build
checks passed. The two remaining assertions are inherited from the pinned
develop base:

- `TestBuildToolingGuard.supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
  still requires the old direct-Maven commands, separate-worktree concurrency
  wording and printed-pinned-base receipt guidance, while the current docs
  prescribe the queue.
- `TestBuildToolingGuard.traceChaserStaysExactOptionalAndOutsideOrdinaryBuilds`
  expects gitlink `4fb6d0802cc6ad27f07dd845a1b98ea84d2c7b0e`, while develop
  already pins `9fd957bbb47d9f98c725038424783482d3520cb0`.

Attribution is by exact source/input comparison, not a baseline Maven run:
both complete assertion bodies are identical to `5e3700a04`, as are
AGENTS/CLAUDE, both hook installers, POM and .gitmodules; the indexed
TraceChaser pin also matches that base. Neither assertion nor its failing
input changed in this merge. They remain visible follow-ups rather than
unrelated changes in the route delivery. No full-suite or all-guards pass
is claimed. Conflict-marker, Markdown local-link and whitespace checks pass.

## HCZ rewind and viewport/donor follow-up (2026-09-14)

The requested follow-up starts from develop `14d902d3900104108b3f550d6f8d4841deddabc3`
in `.worktrees/ai-hcz-route-coverage`, branch `feature/ai-hcz-route-coverage`.
The main workspace stays on develop and unrelated disassembly/user files are preserved.
Current queue-based validation policy supersedes the historical receipts above.

Added obligations are independent HCZ water/shield/bridge/fan/conveyor/spring/boss
restore/replay windows, actual act-2 timeline isolation, and the full 15-configuration
viewport/donor entry and repeated-reset matrix. The private route helper was first
extracted with the native 12,583-frame pad hash unchanged. That equivalence applies
to extraction, not later input-controller refinements.

Two rewind failures had distinct causes and fixes:

- At native frame 2,986, dormant insta-shield identity 1 and active bubble identity
  450 both used slot 100. Restore changed insertion order, not their state. Pair
  dynamic entries by stable identity and compare all fields. Reject duplicate IDs,
  missing entries and altered slot/class/state/owner/auxiliary fields. Suppressing
  insta-shield recreation was rejected because it would remove captured state.
- At native fan frame 8,267, CPU Tails retained a destroyed bridge contact while
  slot 8 had another live owner. Immediate restore passed, but forward replay
  changed `releasedUnderwaterPushConsumed`. Capture the released-contact provenance,
  clear future Java references and prefer the recorded slot when relinking. Do not
  bind a nearby same-type object or a same-type replacement of the deleted owner.
  The unpublished 0.7 signature pin includes the new snapshot component/accessor;
  the candidate version is unchanged.

Focused evidence before the final controller refinements (all required existing
absolute ROM properties supplied, through `tools/testing/maven_queue.py -Dmse=off`):

- `-Dtest=TestRewindSnapshotDiffDynamicIdentity,TestSpriteManagerRewindCapture,TestSidekickCpuFollowParity`:
  4 comparator, 5 manager and 111 CPU cases passed with zero skips.
- `-Dtest=TestS3kHcz1RoutePilot,TestS3kHcz1RouteRewind,TestS3kHcz1ReloadRewind,TestModApiSignatureSurface`:
  21 cases passed, zero failures/errors/skips. Ten rewind windows were reached
  independently at frames 249, 2986, 2987, 3221, 8267, 8487, 8703, 9633, 10244 and
  12158. Each checked complete registered state immediately and after the same
  30 inputs, twice. The production reload established the new timeline root at 12583.
- `-Dtest=TestS3kHcz1EntryMatrix`: 30 entry/replay and repeated-reset cases passed,
  zero failures/errors/skips, in a grouped navigation run. Each of five widths
  (320/400/512/640/800) and three donors (off/S1/S2) ran both obligations.

Controller experiments remain ordinary pad decisions over live state and ROM-loaded
placement geometry. Repeated dry rollback needs a fresh lower run-up; keeping its
old once-only latch loops at the first dry bend. A missing-spindash moveset must
release the charge phase and walk. Preserving early water momentum and directly
attacking the paired Blastoid completes the 320/off, 400/off, 512/off and 320/S2
route probes. An earlier unconditional bridge-attack change without the walking
approach regressed native timing (death at frame 6902), so it was rejected alone.

At the upper ring monitor, a Blastoid can remove the newly collected rings during
rebound. Predictive steering alone, delayed jump release, lost-ring pursuit and
preemptive attack did not solve the old 400px prefix: later deaths were at frames
7780, 7708, 7749 and 7402 respectively. Lost-ring pursuit starts too late after hurt
control releases; nearby rings can already fall below the platform. These probes
are temporary; durable decisions and regression checks live in the route helper,
rewind tests and the existing pitfalls/measurement-hazard catalogues.


### Survey outcome and retained delivery scope

Later lower-bridge probes established that Down+Right cannot enter a roll: the
ROM roll gate requires neutral left/right. Down-only roll plus projected upper
steering reached the lower shield and final curve for S1, but the missing-spindash
final run-up remained unresolved. Early/late jumps met the lower Blastoid's upward
projectile arc; insufficient rolling momentum unrolled before the body. These
are controller frontiers, not evidence for adjusting runtime physics.

The 800px survey reached the boss waiting routine, with camera X below the arena
lock. Walking stalled in the current and drowned; repeated jumping stalled on
upper terrain. Boss activation can precede arena admission in a wider view.
A runtime camera-contract change is not justified by these input probes alone.

The maintained delivery therefore retains the native controller and adds the
verified rewind, actual transition and full entry/reset cross-product obligations.
Temporary full-route probes and the unfinished compatibility-route class are
removed; the corresponding full-route matrix cells remain open, not skipped or
claimed green. No route-authoring experiment changes runtime movement or asset data.

Final retained-controller focused group (all three absolute ROM properties,
`maven_queue.py -Dmse=off -Dtest=TestS3kHcz1RoutePilot,TestS3kHcz1RouteRewind,TestS3kHcz1ReloadRewind,TestS3kHcz1EntryMatrix,TestRewindSnapshotDiffDynamicIdentity,TestSpriteManagerRewindCapture,TestSidekickCpuFollowParity,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`):
**229 cases, zero failures/errors/skips**. This includes both matching level-loading
class names. Native route and independent rewind windows retain the original
12,583-frame route and recorded checkpoints. The subsequent contact review adds
focused despawn and live-dynamic membership regressions before broad validation.

Review found that the released-contact marker must also feed offscreen CPU despawn,
not just underwater push decisions. It also must inspect the complete active object
collection, including dynamic objects; the placement map alone omits live platforms.
Those cases now verify capture/restore and subsequent decisions directly.

Contact-review verification: `maven_queue.py -Dmse=off
-Dtest=TestSpriteManagerRewindCapture,TestSidekickCpuDespawnParity,TestSidekickCpuFollowParity test`:
**182 cases, zero failures/errors/skips**, 48.326 seconds including compilation.
This includes six manager, 65 despawn and 111 follow cases.

### Combined validation and guard correction

Updated develop `31a9a6bce` merged cleanly into the candidate; incoming FBZ chain
rendering and coverage prose were preserved. Preflight passed with
`LUA_BIN=/usr/bin/lua5.4`. The normal runner selected all **2,567 ordinary classes**
and all guards from pinned base `14d902d39`:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 14d902d3900104108b3f550d6f8d4841deddabc3 --run
```

Run `20260914T170709Z-3234639a`, candidate `db56dd8a161709ddd2902d3563e006056deea11d`:
**20,355 ordinary cases, 20,338 passed, 17 skipped, no failures/errors**, 625.13 seconds.
All 42 maintained HCZ cases executed with zero skips; AIZ's native route, seven
rewind spots and production reload also passed. The 17 skips comprise 13 opt-in
route/diagnostic/performance cases, two unavailable graphics contexts, the inherited
CPZ spin-tube capture assumption and one unavailable local audio reference. Separate
trace/native lanes and opt-in AIZ compatibility/entry/spring breadth are not certified
by this ordinary run.

Guards: **667 cases, 663 passed, four failures, zero errors/skips**, 174.44 seconds.
Two new failures are corrected locally and verified narrowly: the test object update
parameter must say `vIntRunCount`, and the released-contact logic must be extracted
rather than grow `AbstractPlayableSprite` beyond its existing size limit. No guard
limits or allowlists are raised.

The other two failures match the pre-task baseline by assertion and input identity:

- `TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
  still requires direct Maven, separate-worktree concurrency and printed-pinned-base
  receipt prose, while unchanged AGENTS/CLAUDE prescribe the shared queue.
- `TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree`
  flags unchanged `FbzRouteEvidenceProbe#printEvidence` and
  `LevelSolidityMapProbe#writeSolidityMap`. The complete scanned `com/openggf/tests`
  subtree is unchanged from the pinned base.

These identities also appear in the earlier integrated FBZ guard result recorded
in `2026-09-14-fbz-completion.md`. No all-guards-green or full-level certification
claim is made. Results/skips/failure payloads were inspected before acknowledgment;
raw diagnostics are not retained.

Post-guard correction verification uses the same existing absolute S3K ROM:

- `maven_queue.py -Dmse=off -Dtest=TestSpriteManagerRewindCapture,TestSidekickCpuDespawnParity,TestSidekickCpuFollowParity,TestS3kHcz1RouteRewind,TestS3kHcz1RoutePilot,TestModApiSignatureSurface test`:
  **202 passed, zero failures/errors/skips**, 1:29 including compilation.
- `LUA_BIN=/usr/bin/lua5.4 maven_queue.py -Dmse=off -Pguards -Dtest=TestArchitecturalSourceGuard,TestObjectUpdateClockTerminologyGuard,TestHelperStateRewindCoverageGuard,TestStaticStateRewindCoverageGuard test`:
  **75 passed, zero failures/errors/skips**, 59.624 seconds in a fresh JVM.

The stateless `LatchedSolidContactSupport` extraction retains snapshot ownership
on the player and the same public signatures. Lifecycle resets call its binding
operation directly, retaining the old direct-field semantics even for a creator
subclass overriding the public setter. No capture state is moved into an untracked
helper, and the large-class budget is unchanged.


### Actual bridge-trigger rewind boundary

The original FIRST_BRIDGE/SECOND_BRIDGE selectors captured controller approach
stages, not the triggered collapse. Requiring the ROM-loaded bridge's live owner
and corresponding trigger bit moved the checkpoints to frames **3,124 and 4,204**.
The stronger ten-case run failed exactly those two replay windows (no errors/skips):
a freshly created explosion restored with null child factories, so it never spawned
the expected animal/points children. RNG and subsequent dynamic allocation identities
then diverged. Immediate restore still passed.

The correction captures the exact configured factory references together with all
seven mutable explosion fields in a typed subclass snapshot. It does not replace
custom factories with game-selected defaults or retain the original explosion owner;
rendering and spawned children use restore-time services. Focused regressions exercise
both custom allocation orders, passed-slot deferral, pending sound, animation/deletion
and initialized restoration without duplicate children. The stronger bridge selectors
remain the maintained checks.

After the explosion correction, `maven_queue.py -Dmse=off
-Dtest=TestS3kHcz1RouteRewind,TestS3kHcz1RoutePilot,TestS3kHcz1ReloadRewind,TestS3kHcz1EntryMatrix test`
with all three existing absolute ROM paths passed **42 cases, zero failures/errors/skips**,
2:17 including compilation. Both actual bridge windows pass two restore/replay cycles;
the native route still completes at frame 12,583. These are focused results on the
post-`db56dd8a1` corrections, not a second broad-suite result.

Updated-develop matched baseline check before integration:
`LUA_BIN=/usr/bin/lua5.4 maven_queue.py -Dmse=off -Pguards
-Dtest=TestBuildToolingGuard,TestNoAssertionFreeDiagnostics test` completed
**119 cases: 117 passed, the same two failures, no errors/skips**, 51.543 seconds.
Develop advanced from `71603cbb6` to `f658cc5db` during the queued check only through
render-rate design/roadmap prose; all scanned assertion inputs were unchanged.
The failing identities and payloads match the combined candidate run above.

Explosion consumer verification:
`maven_queue.py -Dmse=off -Dtest=TestExplosionObjectInstance,TestDestructionEffects,TestAnimalObjectRngOwnership,TestSonic2AnimalObjectTiming test`
passed **15 cases, no failures/errors/skips**, 20.186 seconds. This includes the
S1 explosion subtype's extra scalar state and initialized-child latch, as well as
S2 deferred animal RNG timing. A subsequent structural check required explicit
factory disposition and override triage: factory references are excluded from the
generic scalar codec because the typed explosion extra captures and restores them.
They are not omitted from rewind or added to the unresolved field-debt baseline.

The fresh-JVM registration rerun,
`LUA_BIN=/usr/bin/lua5.4 maven_queue.py -Dmse=off -Pguards
-Dtest=TestExplosionObjectInstance,TestRewindArchitectureGuard,TestRewindFieldDispositionGuard test`,
passed **13 cases, no failures/errors/skips**, 49.178 seconds. The prior relevant
guard group also passed size, clock, transient, helper and static-state checks.
The two context-aware overrides have a documented architecture triage entry;
subtype generic scalar capture remains active. Local unpublished task commits are
folded together so the candidate API signature change and its source stay in one
reviewable commit after the size-guard extraction.


### Integrated develop verification

Final source commit `abe7497fd` merged into develop as `6897a6048`. Reconciliation
preserved the incoming slots bonus-stage player-priority fix and FBZ/render-rate
prose without conflicts. Actual-environment preflight passed. The required
post-integration command used the original pinned base:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 14d902d3900104108b3f550d6f8d4841deddabc3 --run
```

Run `20260914T174633Z-16a4b83d` selected all **2,569 ordinary classes** and guards.
Ordinary completed **20,363 cases: 20,344 passed, one failure, no errors, 18 skips**,
627.25 seconds. All 42 maintained HCZ cases and AIZ's native route, seven rewind
windows and reload passed without skips. The extra skip versus the earlier run is
the incoming opt-in `TestS3kSlotsGlassNative`; the other 17 retain the reasons
recorded above. No separate trace/native or full-route configuration certification
is implied.

The sole ordinary failure was
`TestRewindInPlaceObjectRestore#auditSummaryAndKnownClassifications`: its pinned
set still included `AnimalFactory` and `PointsFactory` as final non-captured
references. Those two types now restore through typed explosion state and no
longer belong to that structural fallthrough set. The correction removes those
two obsolete entries and documents why; it does not widen reuse eligibility or
change runtime code. The failed class is verified narrowly after the broad run.

Guards completed **667 cases: 665 passed, the same two baseline failures,
no errors/skips**, 174.37 seconds. Exact failing test identities and payloads
match the pre-integration check above. Both lanes completed; the wrapper then
returned exit 2 because develop advanced concurrently to `8ce626087`. That commit
only changes the independent render-rate design and roadmap prose; `git diff
6897a6048 8ce626087 -- src pom.xml .github .githooks tools` is empty. The completed
lane results therefore describe the same runtime/test/build inputs, with an explicit
workspace-change caveat; they are not reported as a successful wrapper run.
Results and all skips were inspected before acknowledging the diagnostic directory.

The narrow correction check,
`maven_queue.py -Dmse=off -Dtest=TestRewindInPlaceObjectRestore
-Dsonic2.rom.path=/absolute/path/to/the/existing/S2-ROM.gen test`, passed
**three cases, no failures/errors/skips**, 50.326 seconds including compilation
(queue waiting excluded). No production files changed after the completed integrated
lanes. Only the matched inherited guard failures remain; full-route viewport/donor,
other-character/team, checkpoint/death and presentation/oracle obligations stay open.
The broad diagnostics were acknowledged and deleted. Final delivery merges this
test/documentation correction into develop; the feature branch remains local.


## Full-route viewport/donor completion (follow-up)

The user requested completing the remaining full-route breadth after the preceding
delivery. Pinned base: `24cdc64e6697b7624e669abea2f37a35abed94b2`; main remains
on develop, with work isolated in `.worktrees/ai-hcz-full-route-matrix`.
`TestS3kHcz1CompatibilityRoutes` exercises all five widths crossed with off/S1/S2,
using the same production start, ordinary pad controller, CPU-team checks,
death/drowning rejection, six-hit miniboss and actual act-2 reload assertions.

Baseline matrix: **15 cases, two passed, 13 failures, no errors/skips**.
Native 320/off and 320/S2 both complete at frame 12,583. Remaining frontiers:

| Configuration | Baseline frontier |
| --- | --- |
| 320/S1 | second bridge; drowning at frame 5,147 |
| 400/off and S2 | lower-tunnel run-up; 20,000-frame watchdog |
| 400/S1 | second bridge; drowning at frame 5,140 |
| 512/all donors | first-fan route missed; death at frame 3,014 |
| 640/off and S2 | repeated lower-tunnel loop; 20,000-frame watchdog |
| 640/S1 | second bridge; drowning at frame 4,974 |
| 800/all donors | second bridge; drowning at frame 4,482 |

The first navigation revision preserves FIRST_FAN water momentum, enables direct
jump attacks for both early bridge-trigger enemies, resets the lower-track latch
on a new dry rollback, and retains the final run-up until the miniboss actually
enters routine 4. A live waiting boss is not arena admission: its production owner
waits for both camera bounds, so wider activation can precede the lock.

Focused navigation probes rejected unconditional FIRST_FAN water walking: S1
stalls on the approach ledge at `(0F31,078C)` and drowns at frame 3,027.
The pending matrix instead limits walking to the moving corridor between the fan
ledge and monitor approach, retaining rising jump holds. This bounded alternative
is not yet validated. A briefly considered LOWER_TUNNEL walking override was
removed before execution; it had no supporting measurement.

The S1 prefix can traverse the dry loops when the test controller checks the live
capability and releases its spindash phase for a character without spindash.
Projected-position steering at the upper bridge also avoids the side collision
observed at frame 7,019. That probe then reached the lower bridge at frame 7,040,
but its grounded jump met a Blastoid projectile at frame 7,112. A grounded rolling
approach is the next experiment; it is not yet a passing route.

The wide-arena probe establishes that boss routine 2 must retain navigation:
`HczMinibossInstance.updateWaitTrigger` / ROM `loc_69EDA` requires camera
`X >= 3680` and `Y >= 0638`. At width 800, the remaining stall is on wet uphill
terrain around player `375B..377E,06DE..06EB`, not a demonstrated wall or runtime
collision defect. Suppressing underwater jump pulses alone did not clear it.
A capability-backed underwater charge is prepared for off/S2; S1 needs an ordinary
momentum route. All of these experiments change test-owned pad steering only.

The first revised maintained matrix completed in 68.22 test seconds: **15 cases,
two passed (400/off and 400/S2), 13 failures, no errors/skips**. Both passes
reloaded at frame 12,714 (pad hash `9114353925844004877`). This revision regressed
the native route, so it is not an accepted replacement. Its bounded water walk
changed the lower-loop approach; native and 640px crouched on a shelf above the
controller's lower-track target. Widening the flat-angle gate alone did not help.
Low-speed left pulses then oscillated on the shelf; sustained left recovered but
repeated the loop. These outcomes identify controller recovery problems, not a
physics discrepancy. The next probe restricts recovery to slow rollback.

A focused 512/off route passed after projected steering retained the spring/fan
lift corridor at `finalCurve.x()+32`. The same three-case probe still failed
320/off and 640/off at the earlier loop (three cases, two failures, no skips).
Separately, S1 passed the lower Blastoid using a Down-only grounded roll at a
nearby approach speed, and cleared the final curve with an additional 192px of
ordinary acceleration. Neither result yet certifies the full S1 route.

The 800px underwater-charge experiment disproved insufficient speed as the sole
arena blocker: repeated releases climbed to `y=0561` with ground speeds around
3,000, while the player remained clamped at `x=3795` and camera at `x=3605`.
`DeadzoneGeometry.rightEdge(width)` centres the focus at half the viewport width;
the unchanged ROM camera-origin trigger therefore demands an unreachable player
position at 640/800px. A local native-framing conversion for HCZ miniboss camera
observations is now under regression testing, retaining the ROM world-bound
writes. This is a production correction and requires normal combined validation;
the earlier test-only proportionate scope no longer describes the delivery.

### Converged off/S2 routes and arena correction

The experimental slow-rollback and sustained-left loop recovery variants did not
clear the repeated loop and were removed. The accepted controller retains the
established rollback algorithm. It explicitly authors a running early-water
corridor for 400/512px and the jumping approach for 320/640/800px. These are test
input strategies, not runtime viewport conditions or trace-driven state changes.
The lower Blastoid approach uses a grounded Down-only roll; upper-bridge and fan
lift steering use projected player X. S1 receives an additional 192px of ordinary
run-up because its live capability has no spindash.

With the camera fix, the combined off/S2 probe passes **all ten routes, no
failures/errors/skips**, 40.649 test seconds / 57.827 Maven seconds (queue excluded).
Both donors produce the same completion frame for each width:

| Width | Off / S2 production reload frame |
| --- | --- |
| 320 | 13,007 |
| 400 | 11,298 |
| 512 | 11,831 |
| 640 | 11,386 |
| 800 | 10,909 |

The camera regression first produced exactly eight wide-viewport failures among
17 cases; with the private, stateless native-framing helper all 17 passed. The
33 short miniboss consumer cases also passed. A matched native-controller check
used the original `24cdc64e6` helper with only the runtime camera fix and retained
frame 12,583 / hash `-3592398407471656479` exactly. Native behavior is unchanged.
The independent wide-route probe completed 800/off and 800/S2 at frame 11,024;
its controller lacks the final shared spring/bridge refinements, explaining the
different frame count rather than implying a physics change.

The root focused command selecting the native route, existing rewind/reload and
entry/reset cases, six miniboss test classes, and the four mandatory S3K foundation
class names passed **137 cases, no failures/errors/skips**, 2:05 Maven wall time.
This run predates the final safe-core fight steering. Its four new wide rewind
cases captured after admission; review correctly identified that they did not
exercise the changed gate. They are replaced by five independent before-lock
captures that must cross horizontal admission during the saved-input replay.
That stronger check is pending; the post-admission pass is not presented as proof
of rewind across the trigger.

The strengthened horizontal-admission check passed all five widths: **five cases,
no failures/errors/skips**, 36.251 Maven seconds. Each captures before the lock,
advances 30 saved pad inputs across it, restores immediately and replays twice;
assertions require the final native `3680` min/max X bounds. The temporary route
probe and its compiled class were removed after its results were consumed.

S1 exposed a separate controller mistake in the fight: reading only the current
engine touch-region list treats the ROM's odd-V-int flicker as retraction. The
first combined matrix using that rule passed seven and failed eight of 15 cases,
including regressions in 512/800 off/S2. The corrected test controller requires
absence in two consecutive observations before S1 approaches the closed core;
off/S2 retain their previously passing side/above approach. Native S1 then passed
at frame 11,481. No boss collision or timing code changed for this steering fix.

The 512/S1 diagnostic ruled out drowning: air resets to 30 on leaving water at
frame 4,237 and remains full until the dry death at `(29BF,056C)`. The ROM placement
at `(29D0,0570)` is Turbo Spiker (`96`, subtype `20`); S1 was walking into it with
no rings, whereas the spindash-capable route arrives rolling. An ordinary fresh
jump over a nearby live Turbo Spiker completes the 512/S1 route at frame 14,773
(one case, no failures/errors/skips, 23.999 Maven seconds). This is test-owned
obstacle avoidance; neither movement constants nor enemy behavior changed.
The temporary air probe and its compiled class were removed after inspection.

### Maintained matrix green

The remaining wide-S1 deaths were verified against live object owners: the upper
Blastoid's projectile removes rings during the monitor rebound, then the lower
Blastoid's three-shot stream kills the unprotected falling player. Air and camera
bounds were healthy. Left/right steering during the drop, left-edge entry and the
original non-projected upper steering all failed. Landing to the upper Blastoid's
left and jumping at it before approaching the monitor solved both wide routes.
The combined helper retains no experimental gap latch and writes only pad inputs.

The maintained `TestS3kHcz1CompatibilityRoutes` now passes **15/15, zero
failures/errors/skips**, 61.251 test seconds / 1:18 Maven time. Invocation below uses `S3K_ROM`, `S1_ROM`, and `S2_ROM` set to the existing,
verified absolute ROM paths (machine-local paths omitted by repository policy):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestS3kHcz1CompatibilityRoutes \
  "-Ds3k.rom.path=$S3K_ROM" \
  "-Dsonic1.rom.path=$S1_ROM" \
  "-Dsonic2.rom.path=$S2_ROM" test
```

Completion frames are recorded in the [HCZ1 matrix](../validation/levels/s3k-hcz1-sonic.md#full-route-viewportdonor-completion).
Both the no-spindash obstacle jump and wide pre-monitor attack are active in this
combined result; earlier isolated S1 probe frame counts therefore differ.
Final controller review found no gameplay-state writes, weakened assertions,
new shared ownership or teardown defect. The five admission rewind checks cross
the changed gate. Broad/integrated verification and delivery remain pending.

### Integration and validation authorization

Implementation commit `a8549acbc` was reconciled with updated develop in
`1d9905226`. Only the append-only trace-frontier log conflicted; both the incoming
KiS2 evidence and HCZ evidence were retained. Code merged without conflicts.
Integration into the main develop workspace produced `2c7630066`; the isolated
task tree has identical committed content (`git diff HEAD 2c7630066` is empty).
Unrelated main-workspace changes and dirty reference submodules were preserved.

The combined plan selects 2,573 ordinary classes plus all guards, with a 40-minute
execution limit and 10-minute no-output timeout. Java 21, Lua 5.4 and PowerShell
preflight passed. The main-workspace command was canceled while still queued,
before Maven started, after an independent FBZ delivery began editing runtime
files there. No broad-suite result was produced.

Moving that required run to the identical isolated tree was rejected by automatic
approval review. Current checked-in guidance from `b06618d70` explicitly removes
the old one-attempt/receipt gate, but the reviewer treats the older user-supplied
AGENTS text in this conversation as authoritative. Re-review with the policy
commit and the zero-execution cancellation evidence was also rejected. No raw
Maven workaround or policy/lock change was attempted. An explicit user validation
exception is required before the broad run can proceed. Integration remains local;
push, final verification and worktree cleanup are pending. The completed focused
results above are not a substitute claim of a completed broad delivery check.


The user subsequently authorized continuing past the supplied limits. The queued
combined run completed on `1d9905226`, whose tree equals integrated `2c7630066`:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 24cdc64e6697b7624e669abea2f37a35abed94b2 --preflight
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 24cdc64e6697b7624e669abea2f37a35abed94b2 --run
```

Run `20260914T200510Z-42dcc6a4` completed all 2,573 ordinary classes:
**20,408 tests, 20,390 passes, zero failures/errors, 18 skips**, 713.41 seconds.
All 15 full HCZ routes and all 15 route rewind cases passed without skips
(73.71 and 53.83 seconds respectively). Guards completed **667 tests, 665 passes,
two inherited failures, zero errors/skips**, 169.46 seconds. Overall exit was 1.
The failures remain `TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
(stale direct-Maven prose expectations) and
`TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree`
(`FbzRouteEvidenceProbe#printEvidence` and `LevelSolidityMapProbe#writeSolidityMap`).
Their messages match the recorded matched baseline; relevant documentation,
scanned test tree, POM, hooks and tooling are unchanged from `9aada6ce6`.

All 18 skips were inspected: 14 explicit opt-in benchmark/diagnostic/route checks,
two unavailable graphics contexts, the existing CPZ spin-tube premise, and the
missing local deterministic S1 audio reference. No HCZ case skipped. The ordinary
suite is green; guards and native trace parity retain the documented baseline
failures. Results were consumed and acknowledgment requested.

While validation ran, develop incorporated KiS2 return/title-card work in
`aa3ccf04a`. Its independent combined run also had zero ordinary failures and the
same two guard failures. Its new loop-tail hook is implemented only by the S2
title card, but its shared load-receipt call warrants bounded combined HCZ reload
and title-card verification after reconciliation. A second full suite is not
needed to repeat both completed broad runs.
