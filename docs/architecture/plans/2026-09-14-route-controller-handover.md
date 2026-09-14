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
