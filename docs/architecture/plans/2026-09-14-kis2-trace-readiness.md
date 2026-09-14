# KiS2 gap closure before the full-game trace

Base: `ae767f351` on develop. Requested outcome: implement the remaining
ROM-proven KiS2 differences that can be verified before a new full-game BK2,
with production ROM assets and preserved stock-game behaviour. The earlier
[presentation delivery](2026-09-14-kis2-presentation-super.md) remains the baseline.

## Work and ownership

- Movement: glide/climb collision, superspeed, pushing/skidding, balance and
  fresh-button Super activation; semantic rules and focused regressions.
- Zone/contact mechanics: wind tunnels, held-player objects, boss touch box,
  solid contacts, and catalogue checks for already equivalent implementations.
- Presentation: CNZ slot pictures, results lifecycle, title/demo/menu feasibility,
  and review of purported ending gaps against the actual KiS2 branches.
- Integration: saved checkpoint rings/extra-life flags through reload and rewind,
  candidate API pins, combined tests, discrepancy catalogue and delivery.

Separate worktrees own each stream. Shared rule edits are coordinated; runtime
assets never come from the optional `docs/kis2disasm` research tree (`c336fed`,
`gameRevision=3`, `fixBugs=0`). Do not invent gameplay state from traces or
claim complete route coverage before the user records the chain movie.

## Validation and delivery

The combined validation selection is pinned to the pre-task base; focused tests
precede the aggregate category run plus guards. At task start the old shared
receipt was occupied by `route-green-20260914`, so no tests were run against it.
Upstream `5e3700a04` replaced receipts with an automatic Maven queue during this
work; it is integrated before validation and all subsequent Maven work queues. Changes to public candidate
rules/snapshots use canonical constructors and update normalized signatures.

Integrate into the main workspace's existing develop branch, push only develop,
and clean the task worktrees after accounting for all changes. Preserve unrelated
user files and dirty research submodules.

## Evidence and decisions

- `Obj79_SaveData` / `Obj79_LoadData` explicitly bank and reinstate saved rings
  and extra-life flags. KiS2 omits stock S2's clearing instructions. Restoring
  live pre-death rings was rejected: it is not what the ROM loads.


## Implemented scope and residuals

| Area | Delivered implementation / targeted checks | Remaining evidence or architecture |
| --- | --- | --- |
| Movement | superspeed, full-word skid, facing push, balance restart, temporary glide radii, idle climb branch; actual BK2 A→A+B input publication and rewind state | wall-grab geometry/displacement detach; full chain comparisons |
| Checkpoint | saved rings and 1-up flags through reload, direct special-stage return and rewind; stock S2 and new-act clearing | complete route/death sequence recording |
| Zone contacts | wind min/clamp, held-vine pinning, propeller clear, grounded pillar squash, boss duck distinction and solid rules | additional per-zone routes |
| Presentation | slot face, level-select title code, Super sound-test handoff, independent results headings and completion clocks | attract owner, native debug placement/maps, exact VDP masks, perfect bonus and tally sound clocks |
| Coverage | focused state/lifecycle/input regressions | full emerald routes, complete powered-form gameplay rewind, donor/viewport/team breadth |

Worker commits: movement `73e00e748` + input review `7894b4d31`; zone/contact
`e6c371898`; presentation `32af6906b` + render-independent results
`793b9e978`. Integration composes the wind profile and optional slot-art overlay
in one provider, used in both tiers; chip-only art stays optional. Ring/boss
rules are wired by the integration owner. Public candidate records use canonical constructors, with stock defaults explicit
in the owning factories; the mutable 0.7 pin is regenerated without publication.

Review fixed results offsets for a failed attempt with all emeralds, and moved
message lifecycle initialization out of rendering. Late art loading must not
restart timing. The ordinary result wait also retains the final DisplayOnly pass.
Checkpoint review confirmed activation-time banks rather than live death values.

The existing EHZ1 bootstrap/history divergence is not fixed by seeding from its
rows. It needs production prelude timing evidence; the new full-game movie remains
the next independent reference. Ending walking cadence was removed from the KiS2
backlog because forced Ending_Routine=0 makes that shared S2 branch unreachable.

## Validation record

Production and test sources initially compiled with
`mvn -Dmse=off -DskipTests test-compile -B`; compilation is not a test pass.
Tool preflight succeeds with Java 21 and `LUA_BIN=/usr/bin/lua5.4`.


The first focused run selected 444 tests: 441 passed, two failed and one errored,
with no skips. Review corrected the legacy collision-equality expectation and
injected the missing ROM services into the late-art fixture. The checkpoint
regression exposed an actual load-order hazard: player rules are still bootstrap
rules before the first runtime rebind. Ring restoration now reads the resolved
host module's rule; KiS2 overrides the delegating module's otherwise-stock rule
accessor. No gameplay values are borrowed from trace rows.

The corrected provider and no-render/late-art results classes passed (10 tests,
zero skips). Host-owned checkpoint reload plus post-load regressions passed
(22 tests, zero skips). The combined and trace results are recorded below.

Packaging `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B`
passed, producing engine, SDK and SDK Javadoc jars (tests explicitly skipped).
Matched EHZ1 replay on `ba3d9d592` versus `5e3700a04` compared 1,817 rows:
300→194 comparison errors, unchanged 91 bootstrap errors and zero warnings/skips.
The existing animation blip remains at 289; first runtime physics difference now
occurs at 1154 Y rather than the missed ring at 284. See the frontier log.

Combined selection on `6049cb65a` used
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base ae767f35167fd3774e4295ab6043aebb65a7d385 --run`
(2,544 selected classes). Ordinary: 19,959 tests, 19,945 passed, 14 skipped,
no failures/errors (432.55 seconds). The skips are opt-in diagnostics, soak,
native graphics/reference audio and the existing CPZ spintube assumption; no
KiS2 test skipped. Required S3K loading/bootstrap/decoding/AIZ checks passed.
Guards: 665 tests, 657 passed, eight failed, no skips/errors (170.04 seconds).

Four failures introduced here prompted structural corrections: immutable object
profiles now declare constructor/recreation ownership with recreation regressions;
movement rules are grouped into air and ground-pose records under the standard
20-component limit; KiS2 construction uses the owned rule factory; candidate
records expose canonical constructors without provisional compatibility shims.
These changes preserve the tested gameplay decisions.

A bounded four-method guard run on unchanged base `5e3700a04` reproduced the
other four failures with exactly matching test identities and messages: two stale
TraceChaser gitlink expectations, old direct-Maven documentation expectations,
and assertion-free FBZ/solidity diagnostic probes. These upstream failures remain
outside this delivery. Final focused guard/gameplay verification follows.

Final gameplay/API regression command (through `maven_queue.py`, `-Dmse=off`,
absolute S2/S3K ROM properties, `test -B`) selected
`TestKis2MovementRules,TestKis2PhysicsProvider,TestKis2HeadlessBoot,TestKis2ObjectLaunchProfiles,TestS2VineSwitchMultiSidekick,TestCheckpointStateRewind,TestLevelLoadContext,TestPostLoadAssemblyBehavior,TestRingManager,TestSidekickCpuDespawnParity,TestModApiSignatureSurface,TestS3kMovingCrouch`:
167 passed, no failures/errors/skips (22.290 seconds). Final
`python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B`
passed (36.373 seconds), including engine, SDK and SDK Javadoc artifacts.

Final guard command used `LUA_BIN=/usr/bin/lua5.4`, `maven_queue.py -Dmse=off
-Pguards -Dtest=TestRewindCoverageGuard,TestNoProvisionalModApiShims,TestArchitecturalSourceGuard,TestPerGameRuleArchitectureGuard test -B`:
80 passed, no failures/errors/skips (20.656 seconds). This is focused verification
of the post-broad corrections, not a second broad-suite pass. The four matched
upstream guard failures and the trace divergence remain. Changed Markdown links,
AGENTS/CLAUDE mirrors and `git diff --check` passed; consumed diagnostics were
removed.
