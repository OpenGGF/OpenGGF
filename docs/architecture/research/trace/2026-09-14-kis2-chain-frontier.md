# KiS2 canonical chain frontier

Base: develop `31a9a6bce`; worktree `.worktrees/kis2-chain-frontier`.
The published 36-segment fixture and its approved bytes remain unchanged.
This work follows the EHZ1 production-ownership stop at BK2 cursor 2003.

## Causes and changes

The chain harness prepared the recorded roster but did not reopen the session
through `TraceReplaySessionBootstrap.resolveReplayModule`, as the standalone
harness already did. It therefore ran the KiS2 movie on a stock S2 module.
Both harnesses now share that launch helper. A short regression observes the
first production pass, verifies the KiS2 module and Knuckles, then aborts with
a private sentinel. The interior-prefix helper was rejected for this check:
it supports special-stage interiors and requires a subsequent armed segment;
using it for the initial level produced a harness assertion, not launch evidence.

`TraceReplayBootstrap.resolveS2TitleCardPreludeFrames` also applied a sidekick
requirement to level objects. Native `Level` calls `RunObjects` once after
placement and on each title-card leave-loop iteration, independently of roster
(`docs/kis2disasm/s2.asm`, `Level` before `Level_MainLoop`; stock S2 agrees).
The object prelude now keeps the existing one leading plus 25 leave-loop passes
for solo teams. The separate sidekick prelude still returns zero without a
sidekick. Its new regression failed with actual zero on the unchanged engine.

Native aux identifies the first enemy as Coconuts (`Obj9D`), not Buzzer. It
reaches Y $238, enters throwing on row 155 and becomes explosion $27 on row
156. `Touch_KillEnemy` / `loc_3F844` adds $100 to rising Y velocity, explaining
native row 156's $0010. No Coconuts timing or hit-response code was changed.

The chip's renderer overlays had not replaced the stock PLC queue table. The
KiS2 `ArtLoadCues` table is at **$33A3FC** in the full lock-on address space:
67 relative word offsets; the first offset $0086 reaches `PlrList_Std1` at
$33A482, including the chip life-icon pointer $33AC46. All 67 lists decode
from the verified lock-on image. `PlrList_Std2` sources are $279A86, $279550,
$33B15E and $33AD40; the last is one 66-pattern grey shield/stars stream at
tile $4BE. Queue fingerprints use physical ROM addresses and destination
**tile** indices. The first EHZ waiting job is Coconuts at $27393C. The module
now serves one chip-backed PLC service to production lifecycle and rewind;
tier one retains the stock service. No disassembly assets are runtime inputs.

Native `Knuckles_BeginClimb` / `Knuckles_Gliding_HitWall` first tests terrain
fit, while the engine accepted every glide wall contact. At row 1807 the ROM
rejects the grab and preserves falling velocity; the old engine entered climb
and zeroed it. The shared helper now checks both wall ends, the left-only
one-pixel exact-fit correction, and the ledge probe's unsigned 0..11 distance
range with the live LRB solid bit. S3K's reverse-gravity branch mirrors that
probe and correction. References: KiS2's `Knuckles_BeginClimb` and S3K
`Knuckles_Gliding_HitWall`, including `.checkFloorCommon` and `.fail`.

The glide floor checks now keep tile-flip-transformed angles. Flat glide
landing retains the glide animation ID while writing the slide mapping frame,
as the ROM does; it does not invent an animation-register write. These changes
add no persistent scalar state. Wall-grab suppression/displacement-detach and
full gameplay rewind route coverage remain separate obligations.

## Measured route and remaining work

The original chain stopped at cursor 2003 with 17,024 comparison errors over
1,260 rows. With launch, prelude, chip queue and glide fixes, the initial EHZ1
segment completes all **3,180 rows** with **92 errors**, zero warnings:
91 initial player-history bootstrap differences and one ring-count difference.
There are no position/velocity, PLC or dynamic-art differences in that segment.
The counts cover different route lengths and must not be treated as matched
full-run totals. The first non-bootstrap mismatch is row **2462**, `rings`,
expected 43, actual 53; native catches up next row.

The native monitor at slot 20 spawns its contents in already-passed slot 16 on
row 2430. The child first rises on row 2431 and grants rings on row 2463.
The existing engine delay is conditional on relative allocation slots. Do not
add a fixed reward delay without explaining the engine's allocation order.

The chain now enters and runs the first special stage, then reaches cursor
**9366** still in `TITLE_CARD` rather than rearming the next EHZ1 segment.
On reconciled tree `c2bc3af5d` (incoming base `6897a6048`), a bounded
return diagnostic reports `EXIT_BACKGROUND`, card frame 86/state timer 9,
`leavePass=26`, and an empty PLC queue at that deadline. The next investigation
is the title-card final-pass/release handoff, not a still-busy art queue. The
initial segment remains at 92 errors after reconciliation.

This is the next structural frontier. Special-stage interiors use the existing
uncompared gameplay policy with art-ledger comparison; reaching the return is
not a claim of special-stage physics parity or emerald success.

The independent short KiS2 EHZ1 fixture improves from 194 to 179 errors, with
91 history bootstrap errors and zero warnings in each run. It is a different
BK2 from the full-run segment and remains red. Its first post-bootstrap
difference is row 289 `player_animation_id` (expected $00, actual $20), with
mapping disagreement on rows 289–290. Its first position mismatch is row 1159
`x` (expected $0938, actual $0940), after a wall-grab animation mismatch begins
at row 1154. Preserve those as independent glide/contact follow-ups rather
than reading the canonical first segment's clean positions as complete coverage.

## Validation

All Maven invocations use `python3 tools/testing/maven_queue.py -Dmse=off`,
`test -B`, and absolute verified ROM properties (`sonic2.rom.path`,
`s3k.rom.path`, and `kis2.rom.path` for KiS2 selections). Tests below are
focused validation, not full-suite or complete-chain passes.

- Solo prelude regression: 12 tests pass after reproducing its failure on base.
- `TestKis2PlcService`: three tests pass, covering all chip lists, both module
  tiers, lifecycle/rewind owner identity, and service/restore/forward replay.
  Eight stock PLC service checks also pass.
- `TestGlideWallGrabTerrain`: three tests pass for exact wall fit, both sides,
  ledge rejection/acceptance boundaries, unequal radii and reverse gravity.
- `TestPlayableSpriteMovement`: 177 tests pass, including the transformed-angle
  and slide-animation regressions.
- `TestKis2CompleteEmeraldRunChain`: launch regression passes; full chain fails
  at the cursor-9366 return boundary described above, zero skips.
- Matched base `31a9a6bce` in `.worktrees/kis2-chain-baseline`:
  `TestS2Ehz1TraceReplay` fails with 16,388 errors, zero warnings; first row 6
  `dynamic_art.outstanding_transfer_ids`, expected [2], actual [].
  `TestS3kKnucklesSuperEmeraldRunChain` fails because segment 0's `giant_ring`
  exit is not observed. Neither baseline test skips.

Matched updated base `6897a6048` reproduces both trace failures above. The
reconciled current tree has the identical S2 error count and first error. S3K
still misses segment 0's giant ring: 12,679 baseline errors versus 12,616 current,
zero warnings/bootstrap errors, with the same first physics mismatch at row
446 `y_speed` (expected -$0448, actual $0448). These are still red traces;
fewer downstream mismatches do not certify S3K route parity. The four required
S3K loading/bootstrap/decoding/AIZ class names select both identically named
level-loading classes: **58 tests pass, zero skips**. The combined invocation
therefore has 60 tests, two known trace failures, zero errors/skips.

Combined ordinary/guard validation and final integration evidence follow below.

## Integration preparation

Implementation `e8e088432` was reconciled with develop `6897a6048` at
`c2bc3af5d`. Runtime code merged without conflicts. The measurement-hazard
catalogue had competing insertions; both the KiS2 module-resolution lesson
and HCZ dynamic-identity rewind lesson were retained. Incoming code includes
HCZ rewind/contact and object lifecycle changes, so matched S2/S3K traces
were scheduled on the updated base and this reconciled tree.

## Combined validation before integration

`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 8ce626087 --run`
selected all 2,571 ordinary classes and guards on `73f07afd1`. Run
`20260914T180654Z-916cce8a` completed the ordinary lane in 598.03 seconds:
**20,372 tests: 20,353 passed, one failure, zero errors, 18 skips**. The failure
is the same `TestRewindInPlaceObjectRestore#auditSummaryAndKnownClassifications`
audit pin observed on the updated base. The suite exercises all nine new
ordinary regression cases without skips.

The runner stopped before guards because the evidence notes were edited during
validation. Runtime/test/build files and HEAD remained fixed throughout the
ordinary lane; only this research note and the frontier log changed. The lane
completed, but the combined invocation is **incomplete**, not green. Diagnostics
were inspected and acknowledgment requested. The entire tree is frozen for the
remaining checks and integrated validation.

The 18 skips retain the baseline reasons: opt-in audio/rewind/allocation and
rendering probes; opt-in AIZ route/entry/spring matrices; unavailable EGL/OpenGL
checks; optional local S1 audio/timeline reference; and the pre-existing CPZ
spin-tube prerequisite. No required game ROM or KiS2 regression was skipped.

Upstream `24cdc64e6` supplies the already-verified test-only audit correction:
`AnimalFactory` and `PointsFactory` now belong to typed explosion state rather
than final-reference fallthrough. It was merged without conflicts at `382d54d6b`.
The earlier `8ce626087` merge (`73f07afd1`) contained render-rate design/roadmap
prose only. No runtime change followed the completed ordinary lane.

The full guard lane was completed separately on `30878ca6a` using queued Maven
`-Dmse=off -Pguards test -B` with Lua 5.4 and absolute ROM properties:
**667 tests, three failures, zero errors/skips**, 2:56 including compilation.
Two match the base exactly: `TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
expects superseded direct-Maven guidance, and
`TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree` flags
the unchanged `FbzRouteEvidenceProbe#printEvidence` and
`LevelSolidityMapProbe#writeSolidityMap`.

The new `TestPlayableRuntimeAccessGuard` failure correctly rejected direct
`GameServices` access from movement. The wall-grab call now reads reverse
gravity through the existing `sprite.currentGameState()` accessor, preserving
the same gameplay value through the owned runtime boundary. Queued
`-Pguards -Dtest=TestPlayableRuntimeAccessGuard,TestPlayableSpriteMovement,TestGlideWallGrabTerrain,TestRewindInPlaceObjectRestore,TestKis2CompleteEmeraldRunChain`
completed **186 tests: 185 passed, one known chain failure, zero errors/skips**
in 57.563 seconds. The accessor guard, 177 movement cases, three geometry
cases and three corrected audit cases pass; the launch check passes and the
full chain retains cursor 9366. No new guard failure remains after this narrow
correction. The final integrated suite is still required before push.

## Final integrated verification

Runtime/accessor commit `280d1f477` integrated into develop at
**`8fff63a7078ba871f852978d520685c099db7803`**, on destination `24cdc64e6`,
without conflicts. Main remained on develop; unrelated user files and the three
dirty disassembly submodules were preserved. The task worktree fast-forwarded
to that exact integrated commit and stayed clean throughout validation.

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 24cdc64e6 --run
```

Actual-environment preflight passed. Run `20260914T183031Z-9c8a025d` completed
both lanes without a workspace-change interruption:

- Ordinary: **20,372 tests — 20,354 passed, 18 skipped, zero failures/errors**,
  617.07 seconds. The skip identities and reasons match those inspected above;
  no required ROM or new KiS2 regression was skipped.
- Guards: **667 tests — 665 passed, two failures, zero errors/skips**,
  176.75 seconds. The two failures match the baseline by exact identity and
  payload: stale direct-Maven guidance expectations and the two unchanged
  assertion-free diagnostic probes listed above. The new playable-runtime
  access guard passes, and the upstream audit correction is green.

This is a full ordinary-suite pass with two inherited guard failures, not an
all-gates-green or complete KiS2-chain claim. The canonical first-segment/return
frontier and the independent short recording's residual differences remain as
recorded above. Final follow-up changes only this evidence and the frontier log;
local link targets and whitespace are checked without repeating engine tests.


## First special-stage return continuation

Worktree `.worktrees/kis2-special-return`, pinned base `9aada6ce6`.
The native KiS2 `Level` leave loop (`s2.asm:5374-5405`) dispatches
`RunObjects`, `BuildSprites`, and `RunPLC_RAM`, tests the background object,
and clears the control locks before its next `WaitForVint`. The existing
engine counted all 26 player/object passes correctly but tested release on
the following provider update. An internal `TitleCardLoopTail` now completes
the locked iteration after its object and PLC work, preserving the last pass
and releasing without another physical row. S1/S3K providers retain their
existing lifecycle; no creator API method or trace timing input was added.

A first queued focused run (`-Ptrace-replay -Dsurefire.forkCount=1
-Dtest=TestTitleCardManagerNativeExitTiming,TestKis2CompleteEmeraldRunChain`,
with absolute S2/KiS2 ROM properties) passes both native timing regressions
and the patch-launch check, with no skips. The chain gets beyond the former
cursor-9366 physical-walk overrun and fails at return admission:
`production did not publish a level-load receipt`. Segment 0 retains exactly
92 errors (91 history bootstrap, one ring-reward row) and zero warnings.
This isolates a second missing connection: `LevelManager`'s synchronous
reload used the raw playback activation rather than the existing wrapper
that publishes a completed production load receipt. The reload now uses
that wrapper, and `SpecialStageTransitionSupport` marks the interior-return
cause before loading. Receipts observe the production generation and level
identity only; they cannot restore gameplay from trace rows.

The combined runtime candidate now passes two special-stage return admissions.
`seg2_ehz1` completes all 1,316 rows with one error: row 826 `rings`, expected
95, actual 85; no bootstrap errors or warnings. Both represented special-stage
art ledgers have zero errors. Both return gaps retain their first dynamic-art
edge 39 movie frames early (9340/9301 and 16756/16717). These newly reachable
gaps have no matched continuous baseline because the old chain stops earlier;
this is exposed evidence, not attributed regression or proven art parity.

`seg3_ehz1` reaches row 2522, BK2 cursor 19304, then raises the production
art-publication atomicity assertion (delivery serial 18212 unchanged). Its
partial report has 1,524 errors, zero bootstrap errors/warnings; the first
non-camera physics error is row 2230 `x_sub` ($21F5 versus $F800). Diagnose
that earlier movement divergence before treating the later assertion as an
independent cause. The fixture remains unchanged and the full chain remains
red. SS interiors compare art, not gameplay physics.

The full three-game title lifecycle test already counted the passes before
release; its old assertion expected 26 *before* the release iteration and
therefore codified the extra empty iteration. It now asserts 25 before the
release step and 26 after it, while preserving its next-step counter check.
Additional tests exercise same-iteration release after rewind and a real
synchronous S2 return's generation/cause receipt. Broader verification follows.


Focused combined regressions used queued Maven `-Dmse=off -Pguards
-Dsurefire.forkCount=1
-Dtest=TestTitleCardManagerNativeExitTiming,TestTitleCardObjectExecution,TestSpecialStageReturnLoadReceipt,TestSonic2TitleCardManagerRewind,TestGameLoopHardwareTimingBoundaries,TestRunLevelLoadTracker,TestTraceSessionLauncherRunBranch,TestHardwareTimingAuthorityGuard,TestS2Ehz1TraceReplay,TestS3kKnucklesSuperEmeraldRunChain test`
with all four absolute ROM properties. **81 tests: 79 passed, two known trace
failures, zero errors/skips**, 48.691 seconds. S2 retains 16,388 errors and
first row 6 `dynamic_art.outstanding_transfer_ids`; S3K retains 12,616 errors,
first row 446 `y_speed` (-$0448/$0448), and the missing first giant-ring exit.
The title-card, rewind, real reload receipt, launcher/coordinator, and timing
authority checks pass. No required ROM test skipped.

The pinned base's source, tests, POM, hooks and testing tools are identical to
already-verified `2e11a08a8` (checked with `git diff`), whose ordinary/guard
results are recorded in the FBZ2 laser-room graphics audit: 20,378 ordinary
tests, zero failures/errors, 18 skips; 667 guards, the same two known failures
(`TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
and `TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree`).
This existing completed baseline is retained instead of repeating unchanged
checks. Preflight passes with Java 21, Lua 5.4 and PowerShell. Candidate
combined validation and post-integration verification remain required.


Candidate `c85e17571` completed queued combined validation
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 9aada6ce693d6a34d50e68552a7f8436c8ee2241 --run`
(`20260914T195114Z-c12f4900`). All 2,573 ordinary classes completed:
**20,381 tests, 20,363 passes, zero failures/errors, 18 baseline skips**,
606.94 seconds. The guard lane completed **667 tests, 665 passes, the same
two baseline failures, zero errors/skips**, 178.65 seconds. Failure messages
match the stale direct-Maven guidance and the two unchanged assertion-free
probes exactly. No new failure; diagnostics inspected and acknowledgment
queued. All new regression tests ran without skipping.

Incoming develop `2c7630066` adds HCZ viewport/donor route coverage and native
miniboss camera framing. Runtime files do not overlap this change. The shared
frontier log had an append conflict; both evidence entries are preserved.
Post-integration verification will cover the combined runtime.


### Integrated return-fix verification

Runtime commit `c85e17571` integrated at
`aa3ccf04a95c2343944d9b63c18cf9cff4dd9322` on destination `2c7630066`.
Queued command:
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 2c76300668aaab6a7a962782294f3f2b6f8ba67a --run`.
The exact integrated commit was checked in `.worktrees/kis2-special-return`;
main's unrelated dirty FBZ work was preserved. After waiting for the HCZ
validation slot, run `20260914T201953Z-a4e26344` completed all 2,574 ordinary
classes: **20,411 tests, 20,393 passes, zero failures/errors, 18 baseline
skips**, 716.36 seconds. All 15 incoming HCZ compatibility routes pass.
Guards: **667 tests, 665 passes, two exact baseline failures, zero
errors/skips**, 169.88 seconds. Failure identities and messages are the same
stale direct-Maven documentation expectations and unchanged assertion-free
probes recorded above. Skip identities/reasons match the candidate and base;
no new required ROM check skipped. No new or worsened failure was observed.

Candidate diagnostics were automatically deleted when integrated validation
acquired the queue. Both acknowledgment requests were submitted; integrated
diagnostics were inspected before requesting their deletion. The full KiS2
chain remains red at the newly recorded frontier, independently of the clean
ordinary suite and known guard failures.

### Next causal lead: wall-grab anchor

Read-only investigation during the queue wait found the first new movement
error at `seg3_ehz1` row 2230 coincides with the initial wall grab. Native
`x_sub` becomes `$21F5`, exactly `x_pos`; the engine retains `$F800`.
`Knuckles_BeginClimb` (`docs/kis2disasm/s2.asm:38311-38315`) explicitly stores
`move.w x_pos(a0),x_sub(a0)`. `Knuckles_Climbing_Wall` (:38502-38505) then
compares those words and detaches if an object has displaced Knuckles.
S3K's `Knuckles_Gliding_HitWall` uses the same `x_pos+2` alias. This is an
intentional RAM-field reuse, not a fractional rounding discrepancy. The next
fix should reproduce the anchor store and its displacement check, with
shared S3K and rewind regressions, before investigating the downstream row
2522 publication assertion. No wall-anchor implementation change was made
in this return-handoff delivery.


## 2026-09-15 — wall-grab anchor continuation

Pinned base `dedd18877da190e65aeb74970929e2f2b4ece6c3`, implementation tree
`.worktrees/kis2-wall-anchor`, clean baseline `.worktrees/kis2-wall-baseline`.
`Knuckles_BeginClimb` writes the native X word into `x_sub`; S3K uses the same
`x_pos+2` alias. The engine previously retained its old fraction and pinned
the sprite back to a separate top-left anchor on every climb update. The
candidate stores the alias, detaches before velocity clears if the native X
word changed or the player is standing on an object, and otherwise clears
climb velocities. The grab retains `anim(a0)` while selecting mapping `$B7`.
No new rewind state or public API surface is required: native position words
are already captured. Existing legacy wall-anchor accessors remain intact.

Queued baseline regression command `python3 tools/testing/maven_queue.py
-Dmse=off -Dtest=TestKis2MovementRules test` completed 12 tests with three
expected new failures, zero errors/skips, 49.093 seconds. The store test saw
`x_sub=$ABCD` instead of `$9100`; displacement and rewind-forward checks
remained in climb state 4 instead of detaching to state 2. Existing tests
that construct a climbing player are updated to seed the native anchor word.
Both rule sets and both grab directions are exercised, including the unsigned
native word above `$7FFF`, carried-player detach and an independent Y fraction.

Final focused replay and broad results follow below.


The anchor-only candidate's queued `-Ptrace-replay -Dsurefire.forkCount=1
-Dtest=TestKis2MovementRules,TestPlayableSpriteMovement,TestAbstractPlayableSpriteRewindCapture,TestKis2CompleteEmeraldRunChain,TestKis2Ehz1TraceReplay test`
(with absolute S2/KiS2 ROM properties) completed 205 tests: 203 passes,
two red traces, zero errors/skips, 55.940 seconds. The short KiS2 fixture
improves from the previous 179 errors to 102. Chain segment 0 and the first
returned EHZ1 segment retain 92 and one errors respectively. The next third
EHZ1 difference moves from row 2230 `x_sub` to row 2255 `x` ($21F8/$2200),
with 1,443 partial-report errors. The later art-publication stop shifts one
row to 2523; the two return-art submission gaps remain 39 movie frames early.

The newly exposed row is the second ledge-climb slot: native row 2254
selects `$BD` and adds (+3,-3), then holds that position through row 2259.
Row 2260 consumes (+8,-10). The engine's existing table contains the timer
byte 6 but never used it, so it consumed that second entry on row 2255.
The candidate now models `Knuckles_Climbing_Onto_Ledge` / `Knuckles_Climb_Ledge`:
movement polls the existing animation timer; the animation phase decrements
it after movement, including the entry slot. The table's word additions
preserve both low words, S3K reverses Y under reverse gravity, and the final
entry grounds in the same slot, including the left-facing -1 X adjustment.
Mapping `$BD` prevents restarting an already-started entry. Regressions cover
both rule sets, directions, six-slot holds, low words, native finish, reversed
Y and a mid-hold capture/restore with identical forward completion.


The combined wall/ledge candidate completed queued Maven focused validation
with `-Ptrace-replay -Dsurefire.forkCount=1` and
`-Dtest=TestKis2MovementRules,TestPlayableSpriteMovement,TestAbstractPlayableSpriteRewindCapture,TestKis2CompleteEmeraldRunChain,TestKis2Ehz1TraceReplay,TestS2Ehz1TraceReplay,TestS3kKnucklesSuperEmeraldRunChain`,
using the existing absolute KiS2/S2/S3K ROM paths: 211 tests, 207 passes,
four inherited red trace tests, zero errors/skips, 1:08 Maven time. All 206
movement/rewind checks pass. The first ledge attempt failed compilation for a
missing NativePositionOps import; no tests ran until the import was corrected.

The short KiS2 fixture now has 93 errors: 91 history-bootstrap differences and
two animation mismatch records beginning at row 289 (animation $00/$20 and
mapping $25/$C0), zero warnings. The matched baseline has 179 errors.
Chain segment 4 (`seg3_ehz1`, 2,525 rows) and segment 6 (`seg4_ehz1`, 2,392 rows)
now complete with zero compared errors, including physics and animation.
The third SS interior art ledger compares 6,663 rows with zero errors; its
physics remains uncompared. The new stop is the missing semantic
`level_advance` boundary after segment 6, whose input starts at BK2 offset
26,145. Earlier segment 0/2 ring differences remain unchanged. A third return
art submission gap is now exercised and is also 39 movie frames early
(expected 26,119; actual 26,080).

Matched baseline/current stock S2 and S3K control reports are identical:
S2 has 16,388 errors, first row 6 outstanding transfer IDs `[2]`/`[]`;
S3K has 12,616 errors, first physics row 446 Y speed -$0448/+$0448 and the
same missing first giant-ring boundary. These remain red controls, not parity
certificates. The report filename `s2_ehz1` is also used by the short KiS2 test;
its owner-specific suffix distinguishes it from stock S2.

Baseline `dedd18877` full ordinary validation completed 20,412 tests with
zero failures/errors and 18 inspected skips (704.9 seconds); 667 guards
completed with two failures and no errors/skips (170.36 seconds). Failures are
`TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
(stale direct-Maven guidance expectations) and
`TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree`
(existing FbzRouteEvidenceProbe#printEvidence and
LevelSolidityMapProbe#writeSolidityMap). Baseline diagnostics were inspected
and acknowledged. Candidate and integration broad results follow below.

The earlier suspicion that setY cleared the fraction was rejected by reading
AbstractSprite: it already preserves y_sub. Using NativePositionOps for the
wall's word additions makes ownership explicit; it is not a separate Y-fraction
bug fix. The regression protects the anchor and independent Y word while climbing.


Implementation `f9e17bcc7` merged without conflicts as
`5fed74d4200536d02d6a13774616c9c78433e0fb`, retaining the intervening FBZ
miniboss visual delivery (`9aa24c795`, integrated `094337a0b`, verification
`f47ccece2`). The KiS2 implementation/test files are unchanged by integration.
Queued combined command, in the isolated implementation tree for both runs:
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base dedd18877da190e65aeb74970929e2f2b4ece6c3 --run`.
Preflight passed in the actual launch environment. The runner selected all
2,574 ordinary classes and all guards; neither run timed out.

- Candidate `f9e17bcc7`: 20,419 ordinary tests, zero failures/errors, 18 skips
  (685.18 seconds); 667 guards, two failures, zero errors/skips (170.36 seconds).
- Integrated `5fed74d42`: 20,421 ordinary tests, zero failures/errors, 18 skips
  (716.10 seconds); 667 guards, two failures, zero errors/skips (170.25 seconds).

All skip identities match the baseline. Guard failure identities and messages
match the two baseline failures documented above exactly. No new or worsened
failure was observed. This is a completed full ordinary/guard check with inherited
guard failures, not an entirely green suite. Matched focused replay evidence
remains valid for the unchanged KiS2/S2/S3K movement and animation code; the
chain remains red at the missing level-advance boundary. Results were inspected
and submitted for runner acknowledgement before cleanup.


## 2026-09-15 — results-driven act-load classification

Base `2b2bf8e2818f424106a9494523bdf8fae53f082b`, tree
`.worktrees/kis2-act-transition`. A temporary boundary probe disproved the PLC
stall hypothesis for the EHZ1 exit: at cursor 28,659 the engine had a new EHZ2
level (zone 0, act 1), no results/signpost objects, no active fade, and an empty
PLC queue. The completed-load receipt was generation 6 with cause `ORDINARY`.
`BoundaryProbe.matchesArmedSignal` correctly requires `LEVEL_ADVANCE`, so it
rejected the successful load. The probe was removed after attribution.

`DefaultObjectServices.advanceToNextLevel` invokes `LevelManager` directly from
the results fade callback. The level manager now wraps only its actual successor
load in the existing `TraceSessionLauncher.runLevelAdvanceLoad` classification
helper. Time-attack menu and terminal credits branches return before it. The
load, queue service, timers, gameplay values and fixture are unchanged. Native
`Obj3A/loc_14270` -> `loc_1429C` chooses the successor and raises
`Level_Inactive_flag`; this change identifies that production action rather than
substituting a transition from trace data. No API or snapshot changes.

New `TestLevelAdvanceLoadReceipt` initially ran two tests with one expected
failure (`LEVEL_ADVANCE` versus `ORDINARY`), no errors/skips, 18.696 seconds.
It exercises a real EHZ1-to-EHZ2 load, confirms that the classification is consumed
once before an ordinary reload, and checks that time-attack menu return does not
classify a later unrelated load. Existing terminal-progression tests remain in
the focused selection.

Candidate command: `python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay
-Dsurefire.forkCount=1
-Dtest=TestLevelAdvanceLoadReceipt,TestSpecialStageReturnLoadReceipt,TestLevelManagerEndProgression,TestLevelEntryPathsHeadless,TestRunLevelLoadTracker,TestTraceRunPlaybackCoordinator,TestKis2CompleteEmeraldRunChain,TestS1CompleteEmeraldRunChain,TestS2CompleteEmeraldRunChain test`
with existing absolute S1/S2/KiS2 ROM paths. Result: 56 tests, 53 passes, three
red chains, no errors/skips, 1:14 Maven time. The S1/S2 controls were also measured
on unchanged runtime before the fix (two red chains, zero errors/skips,
33.444 seconds); every normalized control report matches exactly afterward.
S1 still stops at segment 12's missing giant-ring boundary (196,213 errors,
first row 0 dynamic-art edges); S2 still stops on segment 3 special-stage art
(17,071 errors), with segment 2 retaining 21,638 errors, first row 6 art edges.

KiS2 now completes comparison of EHZ2 segment 7 (`seg5_ehz2`, 3,561 rows), with
13,978 errors (13,437 physics/aux, 541 animation), no warnings/bootstrap errors.
The first mismatch is row 50 `queue.s2_nemesis_plc.busy`, expected false/actual
true; the engine reports prepared work with 68 patterns remaining and four
queued fingerprints. It misses this segment's starpost-special exit. The newly
exercised act-change gap has ten art edges versus two, with first mapping $07
versus $56 and first edge at movie 28,558 versus 28,683. This earlier gap evidence
must be investigated before attributing the later queue/physics cascade to a
local EHZ2 object. Earlier EHZ1 reports and the three 39-frame return-art gaps
remain unchanged; SS interior physics remains uncompared.

Queued `-Dmse=off -Pguards
-Dtest=TestHardwareTimingAuthorityGuard,TestTraceReplayInvariantGuard test` passed
35 tests, no failures/errors/skips, 22.729 seconds. The change-based plan selects
all 2,575 ordinary classes because LevelManager is shared. Proportionate
validation applies to this observation-only classification: real load/reload and
non-load paths, tracker consumption, coordinator policy, matched affected chains
and authority guards directly cover its consumers. This is focused validation,
not a new full-suite pass. The prior integrated full run at `5fed74d42` remains
historical evidence; the task base differs from it only in documentation.


Read-only follow-up while integration validation queued: native queue snapshots
for all four EHZ1 entries and the first EHZ2 entry first become busy at row 52,
with 68 patterns remaining and the same four queued fingerprints. The EHZ2
engine's first mismatch at row 50 has exactly those fingerprints. This narrows
the next lead to an early submission/pass boundary rather than absent chip PLC
data. `TitleCardManager.advanceZoneNamePieceTail` owns the delayed zone-name
exit and `queueExitPlcs`; inspect its first production pass across an act load
and the earlier gap publication before altering queue service or adding delays.
The owning native routine is KiS2 `Obj34_WaitAndGoAway` /
`Obj34_LoadStandardWaterAndAnimalArt` (s2.asm:28927-28960).

The chip ROM confirms that fingerprint match: PLC 2 begins with the 68-pattern
explosion at $27B592 -> VRAM $B480, followed by $27393C (14 patterns) and
$27AEE2 (10); PLC 50 contributes $27F0A2 (20) and $27EF60 (16). The latter four
produce the four observed queued fingerprints through QueueDiagnosticSnapshot's
OQDF encoding. These are the existing standard-water and EHZ animal submissions
from the title-card tail, not a newly required KiS2 decoder or table.


Implementation `c53b27aca` integrated without conflicts as
`94a41febdde969bb862e2b3c1e142d60fcdb7429`, retaining upstream FBZ fresh-load and
sprite-publication work (`562e35e37`). The shared LevelManager edits occupy
different paths; all upstream code and both frontier-log entries were retained.
The exact integrated tree reran the same focused load/coordinator/chain command:
56 tests, 53 passes, the same three red chains, zero errors/skips, 1:14 Maven
time. Every normalized chain report is identical to the candidate. The same
35 trace authority guards pass with zero failures/errors/skips (22.448 seconds).
No new or worsened result was observed. This remains focused validation under
the documented exception; no new full-suite result is claimed. Final follow-up
changes only this evidence record and the frontier log.


### 2026-09-15: retain the results-driven act-entry title owner

Continuation base `59d5b8881`, worktree `.worktrees/kis2-ehz2-frontier`.
The matched baseline still stops at segment 7 `seg5_ehz2`: 13,978 errors,
first row 50 `queue.s2_nemesis_plc.busy`, with the expected starpost-special
exit unobserved. A temporary read-only title snapshot probe locates the two
extra tail dispatches at movie cursors 28558 and 28571. The headless direct
results advance omits the locked card, arms its 45-pass tail inside the load,
and reaches destination row 0 with only 43 waiting passes left. Cold entry
and the three special-stage returns start row 0 with all 45 passes intact.

`advanceToNextLevel` calls the generic headless `loadCurrentLevel` path. That
path deliberately omits presentation for standalone host loads; unlike death
restarts and explicit zone transitions, this direct results caller did not
request retention of the native title owner. KiS2 `loc_1429C` writes the next
`Current_ZoneAndAct`, clears checkpoints and sets `Level_Inactive_flag`
(s2.asm:29338-29343). `Level_MainLoop` then branches back to `Level:`
(:5420), including its locked title-card and leave loops. The fix requests
that existing owner through the per-load headless-presentation flag. It does
not alter title-card duration, PLC service, chip tables or fixture data.

The regression extends `TestLevelAdvanceLoadReceipt` to require one title-card
request after a direct results advance and none after a subsequent unrelated
standalone headless reload. Before the fix, queued Maven
`-Dmse=off -Dtest=TestLevelAdvanceLoadReceipt` with the absolute S2 REV01 ROM
path ran two tests: one expected failure on the missing title-card request,
no errors/skips (46.298 seconds including initial compilation). The matched
KiS2 chain baseline ran two tests: launch verification passed and the known
chain failed, no errors/skips (27.356 seconds). Tool preflight passes with
`LUA_BIN=lua5.4`; the unqualified system Lua fails the version check before
any tests run.


Retaining the title card alone passes the load regression but still permits one
extra gameplay pass: the card releases at cursor 28706, and cursor 28707 consumes
the gap's still-armed source-loop flag. The resulting segment has 30,376 errors,
first queue mismatch row 51; first movement mismatch row 1030 is a one-pixel X
shift while riding an ARZ platform, with identical velocities and fractions.
This intermediate candidate is not the delivered behavior.

The source-loop flag was consumed only after admission in `GameLoop`, but a
locked or releasing title-card iteration returns `SETUP_ONLY` before reaching
that code. `LevelIterationAdmissionController` now consumes the flag when the
current loop is TITLE_CARD: that loop already belongs to the destination load
and cannot leave a source gameplay pass for its successor. A new caller-level
regression covers both locked and releasing setup-only title rows. Before the
fix it fails with the source pass incorrectly admitted; queued focused Maven
ran one test, one expected failure, no errors/skips (18.524 seconds).

With both fixes, the tail holds at 45 through cursors 28707/28708 and first
advances on destination row 0, cursor 28709. The combined focused command
`python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay
-Dsurefire.forkCount=1
-Dtest=TestLevelAdvanceLoadReceipt,TestGameLoopFreezeContractWiring,TestLevelIterationAdmissionController,TestKis2CompleteEmeraldRunChain`
with absolute KiS2/S2 ROM properties ran 15 tests: 14 passes and the now-later
red chain, no errors/skips (56.791 seconds). The temporary probes were then
removed from both the comparator and KiS2 test before final validation.

Measured frontier:

- Segment 7 `seg5_ehz2`: all 3,561 physics/animation/art/queue rows compare with
  zero errors; its EHZ1-to-EHZ2 dynamic-art gap also matches. The previous
  13,978-error segment and unobserved fourth-special-stage entry are cleared.
- Newly reached special-stage interiors 8 and 10 compare 6,662 and 6,209 art
  ledger rows with zero errors. Interior gameplay/physics remains uncompared.
- Segment 9 `seg6_ehz2`: all 1,177 rows complete, 6,140 errors (5,570 physics/aux,
  570 animation), first row 200 Y expected `$0376`, actual `$0375`. At that
  wall contact the ROM retains animation `$20`/mapping `$B8`; the engine uses
  `$21`/`$CA`. Both have X `$1475`, X fraction `$1475`, Y fraction `$A800`,
  zero velocities and 60 rings. This segment still reaches the fifth special
  stage. Investigate this earlier disagreement before the later frontier.
- Segment 11 `seg7_ehz2`: all 2,215 rows complete, 28,200 errors (25,788
  physics/aux, 2,412 animation); first row 212 Y speed expected `$0528`, actual
  `-$0528`. Position/fractions and roll animation match there, with an animal
  near the player. The sixth-starpost-special boundary at movie cursor 48882
  is not observed. No local collision fix is claimed by this delivery.
- The stopping boundary moves from 32271 to 48882, 16,611 movie frames farther.
  There are 3,392 additional compared gameplay rows and 12,871 additional
  art-only interior rows. EHZ1 segment results remain unchanged. The first
  three return-art gaps remain 39 movie frames early; fourth/fifth returns
  are 37/38 early, and the segment-9 cascade also leaves ledger differences.


Source fix `36479a2fc` reconciles current develop `1cfe9ef82` without conflicts
as `d31238136`; the incoming support/configuration refactor is retained. The
probe-free focused command on that merged tree was (ROM variables denote the
verified absolute paths to the existing root dumps):

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay \
  -Dsurefire.forkCount=1 \
  '-Dtest=TestLevelAdvanceLoadReceipt,TestGameLoopFreezeContractWiring,TestLevelIterationAdmissionController,TestLevelEntryPathsHeadless,TestLevelManagerEndProgression,TestKis2CompleteEmeraldRunChain,TestS1CompleteEmeraldRunChain,TestS2CompleteEmeraldRunChain' \
  "-Dkis2.rom.path=$KIS2_ROM" \
  "-Dsonic2.rom.path=$S2_ROM" \
  "-Dsonic1.rom.path=$S1_ROM" test
```

Result: 43 tests, 40 passes and three red chains, no errors/skips, 1:12 Maven
time. All 15 parsed KiS2 reports match the probe candidate exactly. All 16 stock
S1/S2 reports match the retained pre-task baseline reports exactly, including
fields, values, spans and bootstrap/verification groups. S1 still stops at
segment 12 `mz2_3` (giant-ring boundary), S2 at its second special-stage art
comparison. These are unchanged red controls, not parity claims.

Separate shared-admission S3K coverage:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 \
  '-Dtest=TestS3kFbzCompleteRunTraceReplay,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$S3K_ROM" test
```

All 59 tests pass, no errors/skips (37.194 seconds); complete FBZ replay passes
in 16.80 seconds. The change-based plan against pre-task `59d5b8881` selects all
2,585 ordinary classes and guards because the admission owner is shared.
Normal combined validation is required; the earlier observation-only receipt
fix's proportionate-validation exception is not used for this timing change.


Combined candidate validation at `0d0965c8b` completed with
`LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base 59d5b8881 --run`
(run `20260915T100304Z-dabf6019`). Ordinary: 2,585 reports, 20,467 tests, zero
failures/errors, 18 inspected skips (729.31 seconds). Guards: 84 reports, 668
tests, two failures, zero errors/skips (172.20 seconds). The two class/method
identities and complete assertion messages exactly match the retained baseline:
`TestBuildToolingGuard#supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
expects superseded direct-Maven guidance, and
`TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree` flags
existing `FbzRouteEvidenceProbe#printEvidence` and
`LevelSolidityMapProbe#writeSolidityMap`. No new or worsened failure is observed;
the combined command remains red for those inherited guards.

The current-base full result at `1cfe9ef82`, recorded by the concurrent
simplification delivery, was 20,466 ordinary tests with zero failures/errors,
18 skips and the same 668 guards/two failures. The candidate adds one passing
regression. Skips are opt-in benchmarks/soaks/routes/captures, unavailable EGL/GL
probes, local audio references and the existing CPZ spin-tube prerequisite;
no stock-ROM test is skipped for a missing dump. The tree stayed frozen through
both lanes. Inspection and diagnostic acknowledgment are complete; the run directory was deleted.


### Integrated verification (2026-09-15)

`316788395` merges the KiS2 fixes into develop. Incoming Sandopolis quicksand,
its corrected rewind inventory (`7fae85a69`), and resource-aware Maven admission
through `f5e847931` are preserved. The changelog and measurement catalogue merged
without conflicts. The isolated task tree was fast-forwarded to that exact
integration commit and frozen for verification.

`LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base 59d5b8881 --run`
completed run `20260915T105538Z-081bddaa`: all 2,588 ordinary reports,
20,488 tests, zero failures/errors and 18 skips in 750.76 seconds; 84 guard
reports, 668 tests, three failures, no errors/skips in 174.06 seconds. All three
class/method identities and full assertion messages exactly match the incoming
`6cd8ec188` full baseline: the two guards above plus
`TestRewindArchitectureGuard#objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage`
for two quicksand `@RewindTransient` annotations. That baseline's ordinary
inventory-count failure is corrected by the upstream inventory follow-up and
passes here. All 18 skip identities and reasons match the baseline, with no
missing-ROM skips. No new or worsened failure is observed. This is an
ordinary-suite pass with inherited red guards, not a green full delivery gate.

The integrated replay command was:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay \
  -Dsurefire.forkCount=1 -Dtest=TestKis2CompleteEmeraldRunChain \
  "-Dkis2.rom.path=$KIS2_ROM" "-Dsonic2.rom.path=$S2_ROM" test
```

It completed two tests, one expected chain failure, zero errors/skips in
27.176 seconds. All 15 normalized KiS2 report hashes exactly match the clean
candidate: segment 7 is zero-error for 3,561 rows; the chain reaches segment 11
and misses its sixth special-stage entry at cursor 48,882. The gain remains
16,611 movie frames beyond the previous cursor 32,271 boundary. Next investigate
segment 9 row 200's wall-climb disagreement before the later segment 11 collision
cascade. Special-stage interiors remain art-only comparisons. The inspected
post-integration diagnostic directory was acknowledged and deleted.


### Wall-contact continuation (2026-09-15, base `b8d0ae91b`)

The next user-requested delivery starts in `.worktrees/kis2-wall-frontier`,
branch `bugfix/ai-kis2-wall-frontier`, at updated develop `b8d0ae91b`.
A read-only baseline replay reproduces all 15 normalized KiS2 reports from
`316788395`: segment 9 first disagrees at row 200 (Y `$0376` / `$0375`),
and the chain stops at segment 11's cursor 48,882 special-stage boundary.
The updated base's complete ordinary/guard verification is recorded at
`7480ef0de` in the second-pass simplification plan (the later commits are prose).

Down is held at the first wall disagreement. Immediately before it, Knuckles
is at `$1475,$0375`, double-jump flag 4 and mapping `$B8`. The lower wall probe
is `$147F,$0380`: `ObjectTerrainUtils` returns its no-collision sentinel 32767,
while the existing player `GroundSensor` returns native distance zero. The
legacy result sends the engine to flag 2 / falling frame `$CA`; the ROM retains
the wall and takes its next one-pixel downward step. This is an incorrect
detach, not an animation-table error or an upward ceiling collision.

KiS2 `GetDistanceFromWall` (s2.asm:38821) and S3K's identically named routine
load the live `lrb_solid_bit`, use the X radius, and subtract one before the
left radius/mirror entry. Their `FindWall` extension path must process signed
widths. A small synthetic terrain regression places a negative-width tile in
the extension: `FindWall2` returns -16 and the caller adds 16, retaining exact
contact. The previous helper discards negative extension widths. The focused
regression fails on the old owner with expected climb flag 4, actual 2.
The combined numeric probe/regression command completed three tests, two
failures (that regression plus the existing chain), zero errors/skips in
30.810 seconds. No physics, aux, timing or manifest data was edited.

The candidate routes only climbing wall contact through the existing native
player sensor, using a small `GlideWallGrabTerrain` adapter. It preserves finite
empty-tile distances and the left pre-mirror offset. Ceiling/floor branches are
not changed by this candidate. The regression now also covers left-facing
contact and checks integer movement plus both fractional words. The initial
candidate passed 49 focused movement/sensor tests and reduced segment 9 from
6,140 to 2,186 errors, exposing the next difference at row 360's wall release.

`Knuckles_LetGoOfWall` in both ROMs writes animation and previous animation
$2121, mapping $CB, duration 7 and frame index 1. The engine restarted at $CA.
The release now publishes that native cursor; failed initial grabs retain their
separate `Knuckles_BeginClimb.fail` behavior. Its focused regression failed on
the old cursor and passes with the change. The resulting 18 movement tests pass
and segment 9 now compares all **1,177 rows with zero errors**. Segment 7 also
remains zero. The chain still stops at cursor 48,882 pending the collision fix.

A read-only native GPGX replay of the original BK2 confirms segment 11's first
bounce is two rows early in Java: at row 212 native Y speed is $0528 while
Java has already negated it. The native Coconuts at X $1A57 is climbing at Y
$0399; Java is throwing at Y $0391. Native completed movie frame 46,837 leaves
its idle timer at zero with Knuckles at X $19F7 (distance $60); frame 46,838
sees X $19F9 and starts throwing. Java expires that timer one pass earlier and
starts climbing before the player enters range.

`Obj9D_Init` calls `LoadSubObject`, writes timer $10, then returns without
executing `Obj9D_Idle`. The engine constructor initialized the fields but its
first update ran Idle immediately. An explicit initializing state preserves
that return and is captured by the existing rewind state ordinal, without
changing the snapshot record. Two focused regressions reproduced the old
behavior: first-pass timer 15 rather than 16, and no throw when entering range
on idle expiry. Native probing only read RAM after movie-driven advances;
no fixture values or gameplay hydration were used. Candidate validation follows.

The Coconuts fix passes 28 focused movement/object/rewind tests. A subsequent
chain replay confirms the native object Y/timer and the row-214 bounce, moving
segment 11's first physics difference from row 212 to row 240. That next row
missed a wall grab: at `$1A7A,$0364`, native player `FindWall` returns -5 while
`ObjectTerrainUtils` returns 32767. The live LRB bit is 15; the object helper
checks bit 13. Its signed-width state machine also differs, independently
reproduced with synthetic geometry. Glide wall checks now use the existing
player sensor with `CheckLeft/RightWallDist`'s fixed ten-pixel offsets, distinct
from climbing's radius and left-minus-one entry. Native word corrections retain
fractional position. The regression covers both directions and both solidity
paths; it fails on the old contact path with glide flag 1 rather than climb 4.
The corrected 19-test movement selection passes; the 3 wall-alignment and 29
sensor tests also pass. A temporary diagnostic initially failed to compile
because `scanWorld` is package-private; reflection fixed the diagnostic without
changing production visibility. All temporary Java logging was removed.

Clean queued chain replay with `-Ptrace-replay -Dsurefire.forkCount=1
-Dtest=TestKis2CompleteEmeraldRunChain` (verified KiS2/S2 ROM properties) completes
two tests, one existing chain assertion, zero errors/skips in 49.102 seconds
including rebuild. Segment 9 remains zero-error; segment 11 improves from
28,200 to 21,101 errors with its first physics difference now row 398, Y
`$0314` expected versus `$030F` actual. The boundary is still cursor 48,882.

Row 398 jumps away from the wall. Both KiS2 and S3K `.notMoving` write rolling
radii 7/14, rolling status, animation 2 and velocities, with no position write.
The engine's visual-box shrink in `setRolling` moves its center by five pixels.
The wall jump now preserves both native centers around that representation
change. The focused regression reproduced expected Y 512 versus actual 507
before the correction, with both games and both wall-facing directions covered.

Wall-jump position correction passes all 20 movement regressions; all 177 shared
movement tests also pass. A test initially lacked the roll animation profile,
which was supplied explicitly rather than changing production behavior to suit
that fixture. Clean chain replay completes two tests with one chain assertion,
zero errors/skips in 27.708 seconds. It now crosses the sixth special-stage
entry and return and reaches segment 13 (`seg8_ehz2`) at **BK2 cursor 58,451**:
**9,569 frames beyond cursor 48,882**. Segment 9 remains clean; segment 11 has
2,094 errors beginning row 398's animation/art publication; the newly reached
segment 13 first differs at row 957, X speed -$0448 versus -$0200, then loses
production ownership to a title card at cursor 58,451. Its partial report has
7,203 errors. The sixth SS art ledger is clean; its return retains a 38-frame
early submission plus propagated art ordinal/fingerprint differences.

The residual wall-jump animation error starts because `setGlideAnimation` only
forced the selected animation; the manager's native previous-animation value
remained 2 from the preceding roll. On the jump, animation 2 therefore failed
to restart and retained climb mapping $B9 instead of $9A. Both
`Knuckles_DoGlidingAnimation` and the S3K equivalent explicitly write anim/prev
$2020, duration $20 and frame index zero. The owner now publishes those writes.
The focused regression first reproduced expected animation $20 versus actual 2;
it also exercises the subsequent ordinary script restart on the wall-jump pass.


Final focused selection `TestKis2MovementRules,TestPlayableSpriteMovement,
TestPlayableSpriteAnimation,TestCoconutsInitialization` passes all **248 tests**,
zero failures/errors/skips, 41.065 seconds including rebuild. The clean four-class
trace control command in the frontier log completes five tests with four known
red trace assertions, zero errors/skips, 39.194 seconds. Segment 11 now reaches
row **1590** before its first non-camera difference (X $220A/$2206), down to
**1,889 errors**. The wall-jump animation/art cascade at row 398 is removed.
The structural frontier remains segment 13 at cursor **58,451**, a measured
**9,569-frame gain**; its row-957 X-speed difference is the next investigation.
Short KiS2 remains at 93 errors (91 bootstrap), stock S2 at 16,388, and S3K
Knuckles at 12,616, with their prior first mismatches. Broad validation and
integration are pending; these findings do not certify a complete chain.

Matched baseline controls ran on develop's integrated sand-rock source
`8cb81eb29` (the subsequent `d3cd963a4` is documentation only): all four
normalized independent KiS2/S2/S3K reports exactly match this candidate,
including complete error payloads. The command selects the three control
classes above without the KiS2 full chain: three known red assertions,
zero errors/skips, 60 seconds including rebuild. The updated base now includes
SOZ vine/sand-rock and S3K retained-camera presentation work. Required full
baseline/development/integration verification is being completed through the
Maven queue; no additional control regression is observed.

Validation of source `f56bd4f5c`, reconciled with develop in `a24aa6609`:

- Updated-base full ordinary run `20260915T131945Z-ef899b66` at
  `d3cd963a4`: 20,535 tests, one failure, zero errors, 19 skips (704.89 s).
  The runner stopped before guards because upstream changed the main tree
  during validation. Separate queued guards completed: 668 tests, two failures,
  zero errors/skips. This is a completed ordinary run plus separate guards,
  not an uninterrupted two-lane baseline.
- Development run `20260915T133130Z-e45f325e` at `a24aa6609`,
  `LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base
  d3cd963a4d14a8a5bfd0bac1967a983906966b4f --run`: 20,542 ordinary tests,
  one failure, zero errors, 19 skips (757.74 s); 668 guard tests, two failures,
  zero errors/skips (184.38 s). Every failure identity and message, and every
  skipped-case record, matches the baseline. Seven additional tests pass.
- The ordinary failure was
  `TestSonic3kObjectProfile.cnzPlacedActorsAreMarkedImplementedForS3klLevelsOnly`:
  its old CNZ-only assertion rejected the newly implemented SKL object $44.
  Upstream corrected this test in `79e20e6bb`; merge `60f232249` includes it
  and develop `342f01cb4`. Queued focused `-Dtest=TestSonic3kObjectProfile test`
  passes all six tests with zero failures/errors/skips (18.002 s).
- Both inherited guard failures are unrelated to this change:
  `TestBuildToolingGuard.supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
  still demands guidance replaced by Maven queues, and
  `TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree`
  flags `FbzRouteEvidenceProbe.printEvidence` and
  `LevelSolidityMapProbe.writeSolidityMap`. Neither is repaired here.

Consumed category diagnostics were inspected and acknowledged. Integration
verification remains pending; existing trace discrepancies are not waived.
