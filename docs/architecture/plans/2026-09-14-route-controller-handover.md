# Live-state route controllers: handover (2026-09-14)

> Continuation on `feature/ai-gameplay-capture`, based at `35488abb6`:
> entry breadth now covers 15 width/donor configurations with two rewind replays,
> the late intro gate preserves native timing, and the opposing spring chain
> now has an independent negative-control/rewind obligation. All seven AIZ route axes
> reach the AIZ2 reload; HCZ continuation is in progress; the [AIZ1 matrix](../validation/levels/s3k-aiz1-sonic.md) records the new
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

## Next steps, in order

1. **Promote the AIZ1 pilot into the per-act matrix.** Remove the
   `openggf.aiz1.pilot` opt-in, make it the native 320 Sonic+Tails row of an
   AIZ act 1 matrix under the level-test standard, add the standard's rewind
   spots (intro handoff, Knuckles cutscene, hollow-tree camera lock, act-2
   reload; `TestRewindAcrossActBoundary` already drives this fixture), and
   link the matrix from `docs/status/level-test-coverage.md`.
2. **AIZ1 width and donor rows** (400/512/640/800, S1/S2 donors). Use
   `FbzRouteEvidenceProbe` as the model for a per-row evidence print. Expect
   widescreen culling and S1 donor physics to be the divergence points; that
   is where the first AIZ live gates belong, authored from live objects only.
3. **HCZ act 1 pilot** on the same recipe; it prices the water hazard family.
4. **Stage interface** only after HCZ1 shows what two zones share; AIZ1
   needed no stages.
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
- Task receipts live in shared Git metadata: a peer session's
  `--finish-task` can finish yours (it happened once this session); start a
  new identity and record the minutes already spent.
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
branches, feature, bugfix and a literal shell-looking feature name). A requested
Python YAML parse was unavailable because PyYAML is not installed; the shell
block was extracted from the workflow directly for these checks.

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
