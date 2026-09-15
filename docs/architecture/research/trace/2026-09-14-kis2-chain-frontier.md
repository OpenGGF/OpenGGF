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
and acknowledged. Candidate and integration broad checks are pending.

The earlier suspicion that setY cleared the fraction was rejected by reading
AbstractSprite: it already preserves y_sub. Using NativePositionOps for the
wall's word additions makes ownership explicit; it is not a separate Y-fraction
bug fix. The regression protects the anchor and independent Y word while climbing.
