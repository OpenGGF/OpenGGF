# S3K LBZ act 1 — focused cup elevator coverage

Canonical game/zone/act: Sonic 3 & Knuckles, Launch Base, act 1 (zone 6, act 0).
This matrix records the 2026-10-03 contact correction, not whole-act certification.

| Obligation | Evidence | Remaining scope |
| --- | --- | --- |
| Cup side contact, P1 and CPU participant | `TestLbzCupElevatorSolidDispatch`: real object-manager dispatch stops airborne rolling player at cup edge without capture; repeats after object, solid registry and player restore. | Local native movement case; other character geometry and donors remain open. |
| Cup control and ownership | `TestLbzCupElevatorInstance`: existing movement, capture/release and participant-state checks. | Full-route rewind, load/death boundaries remain open. |
| Recorded Sonic + Tails route | `TestS3kLbzZoneSliceTraceReplay`, 46,075 rows, native 320px, donor off. First mismatch advances from 3714 to 9867. | Still fails; presentation, other widths, solo/Tails/Knuckles routes and donors are not certified. |

## Source and matched trace evidence

Base `ce26682b63`, worktree `trace-special-return`. Native `loc_26EEA`
performs `SolidObjectFull2_1P` after the cooldown/angle gates and before capture,
within each player's control call. `MANUAL_CHECKPOINT` installs a resolver but
does not execute it. The cup had no call to that resolver; seeded standing flags
in older unit tests hid the omitted contact. The fix adds the per-player call
at its native position, preserving P1 contact/capture before P2 processing.

At row 3714 native Sonic is `(11CB,0884)`, X/G speed zero; baseline engine is
`(11CA,0884)`, X speed `-048F`, G speed `-0294`. Y speed `03B0` matches.
Cup centre is `(11A0,0888)`; right side is centre + width `20` + padding `0B`.
Engine diagnostics confirm angle `80`, cooldown zero, outside, solid admitted.
Native aux does not expose angle/cooldown; those are not claimed as native
measurements. The immutable fixture supplies comparison evidence only.

Matched baseline and candidate commands (Java 21, verified ROM CRC `63522553`;
`S3K_REFERENCE_ROM` is the absolute path to the verified user-supplied ROM):

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-segments -Dtest=TestS3kLbzZoneSliceTraceReplay -Dtrace.context.diagnosticChars=full "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLbzCupElevatorInstance,TestLbzCupElevatorSolidDispatch' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
```

Baseline: 6,557 comparison errors, first 3714 `x_speed`. Candidate: 6,316,
first 9867 `tails_air` expected 0 / actual 1. Each completed trace invocation
runs one test, one assertion failure, zero errors/skips. The next mismatch
coincides with rolling-drum deletion and standing ownership; no further fix
is included here. The initial two-case dispatch regression passed without
skips; the final focused object/restore invocation passes all 23 tests, zero failures, errors or skips. An earlier invocation passed both restore cases but exposed one older test without injected services; adding `TestObjectServices` repaired its setup without changing production code.

The change-based plan against `ce26682b63` selects 2,615 classes plus guards.
The parent campaign owns combined validation against its actual integration
base; these local runs are focused proof, not a broad-suite pass.

## Rolling-drum deletion follow-up (2026-10-03)

Base `d8851f0a41`. At row9867 native deletes rolling drum slot4 (`2C3CA`)
but Tails remains status09/onObj04; the engine instead clears the ride and
sets air/status03. Positions and velocities still match at that boundary.
Native CPU despawn follows at9868, while the synthetic release delays it.

`loc_2C3CA` runs both `sub_2C3E8` participant calls before
`Delete_Sprite_If_Not_In_Range`. Its `Delete_Current_Sprite` tail clears the
object SST only; it does not execute `loc_2C48A` rider-release writes.
The drum now selects the existing post-routine range check and leaves live
native P1/P2 state intact on deletion. Dead-native cleanup and extension
unload/omission cleanup remain separate. No shared collision cleanup changes.

`TestLbzRollingDrumDeletion` uses actual object-manager capture and range
unload, verifies the final native flip update and standing state, checks dead
P1/P2 and extension cleanup, and repeats from a pre-capture rewind snapshot.
The older compatibility assertion that unloading releases both native riders
was rejected because it contradicts the native delete tail. Existing compact
participant-relink tests and omitted-extension checks remain in scope.

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLbzRollingDrumDeletion,TestLbzRollingDrumInstance,TestLbzResidualCompatibility' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
```

The trace command is the same matched LBZ invocation above. The focused run
passes 31 tests, zero failures/errors/skips. Candidate replay has 4,031 errors,
first row18939 `x_speed` expected `0018` / actual `000C`, versus base 6,316
errors first9867 `tails_air`. One replay test fails with zero errors/skips;
there is no earlier comparison failure. No claim of whole-route parity.
The plan against `d8851f0a41` selects the same common/content/gameplay/physics/
rendering/rewind/tooling categories plus guards; combined campaign validation
remains the parent integration's responsibility.
This extends local ownership/rewind coverage; inherited route/configuration
and presentation gaps remain open.

## Cup control handoff follow-up (2026-10-03)

Base `815f76a6d8`: row18939 X/G speed is `0018` natively versus `000C` in
engine; native X subpixel `EA00` versus engine `D200`. Cup-held Sonic starts
moving on the first dispatch after the LBZ1 cutscene release, then the engine
reasserts control suppression and freezes subsequent acceleration.

`loc_26F26` writes `object_control=3` at capture. Held path `loc_26FF4`
only writes position, priority and mapping. `CutsceneKnux_LBZ1/loc_6278A`
clears both native players' control after the cup slot. The native held path
respects that external write while continuing to publish cup position.
Native aux removes range-helper slot39/code627C6 at row18937 and the parent
slot38/Delete_Current_Sprite at18938. With Right held throughout, native speed
is zero through18937, then `000C`, `0018`, `0024` on18938–18940. Engine matches
the first release tick and freezes at `000C` thereafter.
The old engine `holdPlayer` rewrote the control state every tick. No native
control-byte observation is claimed from aux; the owning write sites and
matched velocity/subpixel progression establish this hypothesis.

The independent `heldCupDoesNotReassertControlClearedByLaterObjectAndRewinds`
case enters through the real capture method after standing setup, clears
native control as the later cutscene does, and checks the next held dispatch.
An extension without a handoff must remain controlled. Rewind restores all
three player states and the cup's captured participant state, then repeats.
This is an ownership test, not a second collision admission test.

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLbzCupElevatorInstance#heldCupDoesNotReassertControlClearedByLaterObjectAndRewinds' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
```

Reviving the dormant cutscene-release flag was rejected: native held control
has no such branch, and preserving capture-only write ownership handles any
later external control writer without a cutscene-specific exception.
The old-code regression on `815f76a6d8` fails exactly at the held-control
assertion: expected false, actual true (one failure, zero errors/skips).
The correction removes that repeated write; the original capture write stays.
The older synthetic-inside cutscene-lock test now initializes the complete
capture state instead of relying on the erroneous held write to create it.

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLbzCupElevatorInstance,TestLbzCupElevatorSolidDispatch,TestS3kLbz1CutsceneGraphRewind,TestS3kLbz1KnucklesSequenceHeadless' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
```

The corrected replay has 3,304 errors, first row18945
`player_mapping_frame` expected `0096` / actual `0055`, versus the base's
4,031 errors / first18939 `x_speed`. It completes with one assertion failure,
zero errors/skips; there is no earlier comparison failure. At the new jump
boundary, position, velocity and status match but the old cup mapping remains.
No mapping correction is included in this control-write milestone.
Focused coverage completes 57 distinct checks with zero skips: 35 companion
checks passed in the combined invocation (2 solid-dispatch, 3 graph-rewind,
30 LBZ1 headless), and the affected cup class passes all 22 after repairing
the new regression's gameplay-session setup for player-timer restore. The
first combined invocation had one setup error on the second rewind pass;
an intermediate cleanup edit failed compilation by calling a private helper.
The final class rerun uses public session cleanup and passes. Production was
unchanged throughout these fixture repairs. This is focused coverage across
two completed invocations, not a broad-suite pass.

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLbzCupElevatorInstance' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
```
 The change-based plan against
`815f76a6d8` selects common/content/gameplay/physics/rendering/rewind/tooling
plus guards; parent integration owns combined validation.

## Cup animation-bit handoff follow-up (2026-10-03)

The matched baseline is the preceding control-only content in worker
`trace-special-return` (HEAD `815f76a6d8` plus the control patch applied to
`develop`). Baseline: 3,304 errors, first row18945 mapping `0096` versus `0055`.

`Sonic_Control/loc_10C62` and the corresponding Tails animation gate read
object_control bit1. Cup capture `loc_26F26` writes `$03`; held `loc_26FF4`
only publishes mapping and does not write that byte. NPC helper `sub_62800`
writes `$81`, retaining movement suppression but clearing animation suppression.
Parent exit `loc_6278A` writes zero. The engine's persistent raw-mapping flag
must follow those exact writes rather than every call that sets a mapping frame.

The cup now claims animation suppression only at capture; helper and parent
clear it at their full-byte writes. No shared animation API or forced jump
mapping was changed. Reviving `publishInitialJumpMapping` was rejected: native
Animate already runs in the player slot before the cup's jump-release branch.

The independent real-helper test fails on old code at the `$81` bit1-clear
assertion (one failure, zero errors/skips). Corrected tests cover native P1/P2,
an unaffected extension, subsequent cup hold, and restore/replay; existing
headless cutscene exit assertions include both native animation gates.

The same four-class focused command listed above now passes **58 tests**, zero
failures/errors/skips. The same matched trace command completes with **3,303
errors**, first **row21662 `player_animation_id`**, expected `0005` / actual
`0013`: one assertion failure, zero errors/skips. No earlier comparison error
is introduced; the complete NPC interval and jump mapping now match. The next
animation mismatch occurs during the Act1 ending sequence and remains open.
Viewport/donor/full-roster and whole-route parity gaps are unchanged. Combined
validation belongs to the direct-develop integration; these are focused results.


## Miniboss fatal-hit dispatch follow-up (2026-10-03)

Baseline: `trace-special-return`, HEAD `815f76a6d8` plus the preceding
control and animation-bit patches exported for direct-develop integration.
The unchanged Sonic + Tails movie has 3,303 errors, first row21662
`player_animation_id` (`0005` versus `0013`). The bounded probe compares
production object state with native `object_state` events; it does not feed
either observation into gameplay.

| Owner boundary | Native row | Old engine row |
|---|---:|---:|
| Fatal hit installs `Wait_NewDelay` | 21324, `$3F` | 21324, already `$3E` |
| End-sign controller begins | 21388 | 21387 |
| End sign is allocated/initialized | 21508 | 21507 |
| End sign lands | 21597 | 21596 |
| Landed countdown expires | 21662 | 21661 |
| Ending pose is applied | 21663 | 21662 |

`loc_7289A` installs `Wait_NewDelay`; `BossDefeated` initializes `$2E=$3F`
and returns. The engine's attack callback starts defeat before the owner
dispatch, so that dispatch must retain installation state instead of running
the replacement wait. A captured pending-dispatch flag owns this distinction.
The timer constant, signpost falling/landing/countdown and results delays are
unchanged. A fitted signpost delay was rejected because the whole lead starts
at the fatal hit and propagates unchanged. Panels and the explosion child's
creation pass still execute, with later emissions every three dispatches.

An independent parameterized regression uses the fixture's real Sonic and
Tails as sixth-hit attackers. On old production both cases fail exactly at
`$3F` expected / `$3E` actual (two failures, zero errors/skips). The test also
checks same-pass first explosion, subsequent spacing, underflow-only handoff,
fresh object recreation and full-registry restore/replay. This is explicit
snapshot restoration, not a claim about recording live rewind history.
The initial 118-check invocation found a pre-existing restore gap: the boss's
DEFERRED explosion helper restored as null. An owner-local `RewindStateful`
adapter now reuses its existing snapshot and rebinds the shared RNG. No shared
controller, policy table or snapshot API changes are needed.

Validation (all queued in this worker with the verified reference ROM):

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestS3kLbz1MinibossAndTransitionHeadless,TestS3kLbzMinibossGraphRewind,TestLbzMinibossPartBuckets,TestS3kBossExplosionController,TestS3kBossExplosionChild,TestS3kBossDefeatSignpostFlow,TestS3kSignpostInstance,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestS3kLbz1MinibossAndTransitionHeadless,TestS3kLbzMinibossGraphRewind,TestGenericFieldCapturer,TestRewindPolicyRegistry' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-segments -Dtest=TestS3kLbzZoneSliceTraceReplay -Dtrace.context.diagnosticChars=full "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
```

The initial invocation ran 118 checks: 116 passed and the two new cases failed
only after fresh restore at the missing explosion assertion, zero errors/skips.
After adding the adapter, the affected encounter/graph plus generic capture and
policy rerun passes **55/55**, zero failures/errors/skips. The 102 unchanged
companion checks from the initial run passed, including all four mandatory S3K
classes; this is 157 distinct focused checks across the two invocations, not a
full ordinary-suite result. The restored tests compare identical child positions
as well as timer, emission count and handoff state.

Both the timer-only replay and the final adapter-inclusive replay complete with
**3,229 errors**, first **row22188 `player_animation_id`** (`0005` versus `0013`),
one assertion failure and zero errors/skips. No earlier comparison error is
introduced across all 46,075 rows. The new boundary is later in the results/
control-release sequence; it remains open. The change-based plan against
`815f76a6d8` selects 2,616 ordinary classes plus guards across
common/content/gameplay/physics/rendering/rewind/tooling. Direct-develop
integration owns the combined category/guard validation. Viewport/donor/full
roster and complete-route parity remain inherited gaps.


## Shared results child/publication lifecycle follow-up (2026-10-03)

Worker `trace-special-return`, `815f76a6d8` plus the preceding control, mapping
and fatal-hit candidates. This is a separate shared-results patch for direct
`develop` integration; it does not alter the accepted preceding patches.

`Obj_LevelResultsWait2` tests the live twelve-child `$30` count. `loc_2DD06`
clears `_unkFAA8`, replaces the code pointer with `Obj_TitleCard`, and returns.
`Obj_EndSignControlAwaitStart` restores native P1/P2 only after observing the
cleared latch in its slot; `Obj_TitleCardInit` runs on the following owner
dispatch. The engine already has twelve real `S3kResultsElementObjectInstance`
children. Its inherited carried render-tail counter duplicated their completed
retirement. A pre-publication control-readiness shortcut and same-publication
title-init shortcut concealed parts of that delay in other routes. Remove these
three coupled shortcuts without changing countdown constants, adding route
predicates, or removing the public transition request's compatibility plumbing.
ICZ2's separate folded `loc_71DE2` hook remains unchanged. The short-path title
manager reset arithmetic is preserved separately from actual title initialization;
its native display-reset overlap still needs separate evidence. An initial
candidate changed its requested reset from39 to40; that unproven policy change
was rejected and restored to39 before the final affected checks.

Native auxiliary events and bounded engine probes give:

| LBZ boundary | Native row | Previous candidate | Corrected |
|---|---:|---:|---:|
| Final real results child deleted | 22187 | 22187 | 22187 |
| Results owner publishes `_unkFAA8` clear | 22188 | 22191 | 22188 |
| End-sign owner restores ending pose/control | 22188 | 22190 | 22188 |
| Mutated title owner initializes | 22189 | 22192 | 22189 |

The old-code run fails the real-child publication unit and both ROM-backed
MGZ/LBZ carry regressions: three assertion failures, zero errors/skips. The unit
no longer forces the private retirement counter to zero. Production manager
reload retains all twelve children; fresh registry restore recreates them and
replays the same child retirement, publication, title parents, held enemy-art
admission and reset/exit ownership. Lower-slot control polling must wait for
publication; the later-slot operation restores both Sonic and Tails.

Matched `trace-segments` runs drive the six complete-run fixtures with the
verified locked-on ROM. Every replay ends with an assertion failure and zero
errors/skips; these are not green routes:

| Fixture | Before errors / first row | After errors / first row | Boundary coverage |
|---|---|---|---|
| LBZ | 3229 / 22188 animation | 2991 / 22227 rings | Real retirement, control and title init; 46075 rows |
| HCZ | 4699 / 9482 air | identical error spans | Real retirement/publication/init; 29302 rows |
| MGZ | 10046 / 5255 Tails ground speed | 10634 / same first | Real retirement/publication/init; 39199 rows; attribution below |
| CNZ | 5671 / 9190 camera Y | identical error spans | No results owner reached; 39895 rows |
| ICZ | 1287 / 15940 Tails X speed | identical error spans | Both results boundaries, retained ICZ2 hook; 25226 rows |
| MHZ | 3191 / 6958 rings | identical error spans | Parent created, retirement not reached; 28004 rows |

MGZ is explicitly an increased mismatch count. Its existing missing-ring
error begins at9260; native results enter with59 rings while the engine has58.
Both create all twelve children on15982. The engine probe records time bonus10
and ring bonus580; `loc_2DBA8` multiplies ring count by10 and `loc_2DC6E`
removes10 per tally dispatch. This naturally shortens the engine tally and
child lifetime by one dispatch: last child16509 versus native16510. The old
extra retirement dispatch masked that dependency. The corrected publication
is16510 versus native16511, and newly introduced error membership starts16510,
never earlier. No expected ring count is injected and no compensating delay
is added. An independent production-owner oracle loads58 and59 rings as test
inputs, carries all twelve children through the actual MGZ reload, and proves
59 versus60 tally dispatches including the zero-increment completion pass,
one extra child-lifetime dispatch, and publication on the next parent dispatch
in both cases. The earlier missing-ring gameplay frontier remains open.

Focused validation covers212 distinct ordinary cases across completed runs,
all ultimately passing with zero skips. This includes the four mandatory S3K
checks, real MGZ/LBZ carry/rewind, ICZ2's61 cases, P1/P2 control release, CNZ's17
real event/carry/rewind cases and a controller-only MHZ completion with rewind.
The first focus had193/194 pass: the new unit used a carry request without title
publication ownership, so publication passed but the requested title was
correctly suppressed. Fixing that test setup made all16 result units pass.
The final policy-preservation focus passes21 cases; the final independent
ring-oracle run passes all5 cases in `TestS3kMgzLbzCarriedResultsTitleOwnership`.
A first temporary probe failed compilation because reflection exceptions were
not handled; it supplied no measurement. All temporary harness edits are removed.
Java/POM files were frozen throughout each queued/running measurement.

Reproduction commands (prefix each with the repository Maven/Lua environment;
`ROM` below is the verified absolute reference path):

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kResultsScreenObjectInstance,TestS3kResultsKosQueueAndChildren,TestS3kResultsKosQueueRewind,TestS3kResultsElementObjectInstance,TestS3kResultsCameraBoundsPolicy,TestS3kMgzLbzCarriedResultsTitleOwnership,TestS3kBossDefeatSignpostFlow,TestS3kSignpostInstance,TestS3kIczAct1TransitionHeadless,TestS3kIczEndBossObject,TestS3kMhzAuthoredRoute,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$ROM" test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-segments \
  '-Dtest=TestS3kResultsScreenObjectInstance,TestS3kCnzAct1EventFlow,TestS3kLbzZoneSliceTraceReplay#replayMatchesTrace,TestS3kHczZoneSliceTraceReplay#replayMatchesTrace,TestS3kMgzZoneSliceTraceReplay#replayMatchesTrace,TestS3kCnzZoneSliceTraceReplay#replayMatchesTrace,TestS3kIczZoneSliceTraceReplay#replayMatchesTrace,TestS3kMhzZoneSliceTraceReplay#replayMatchesTrace' \
  "-Ds3k.rom.path=$ROM" test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-segments \
  '-Dtest=TestS3kResultsScreenObjectInstance,TestS3kMgzLbzCarriedResultsTitleOwnership,TestS3kMhzAuthoredRoute,TestS3kMgzZoneSliceTraceReplay#replayMatchesTrace' \
  "-Ds3k.rom.path=$ROM" test
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kMgzLbzCarriedResultsTitleOwnership' "-Ds3k.rom.path=$ROM" test
```

The final worker change-based plan (including the preceding unintegrated worker
patches) selects2616 ordinary classes plus separate guards. Normal combined
validation belongs to the direct-develop integration; the212 cases above are
focused validation only. CNZ/MHZ trace retirement gaps are not replaced by a
claim of trace parity; their independent completion/rewind checks cover the
changed production lifecycle with the stated limits. LBZ's next ring reset
boundary at22227 remains open, as do the route/viewport/donor/roster matrix gaps.


### 2026-10-03 retained title counter reset

Worker `trace-s3k-retained-title-wait`, base `d427fdd9ba`, fixes the ordinary
retained results/title owner's counter-reset gate. `Obj_TitleCardWait`
(`sonic3k.asm:62255–62278`, `loc_2D810`) clears the children's `$34` movement
latch and returns before resetting global Timer/Ring_count on the next stationary
poll. Its reset does not rewrite the title owner's `$2E` presentation countdown.
The existing native wait gate now accepts this retained owner directly; explicit
carried policies and the short-results-child policy (including MHZ's inherited
39-dispatch reset) remain unchanged. Both held-counter ownership flags remain set.

Native committed LBZ physics/aux data publishes the title at 22188, initializes it
at 22189 and creates children at 22199. The act child is then at `$0334` after its
first movement from `$0344`; its target is `$0184`. With uninterrupted dispatches,
its final movement is 22226, the parent clears movement at 22227, and resets rings
at 22228. The aux schema does not record `$34` itself: the latch sequence is derived
from the owning ROM routine, observed child creation/position and counter deltas.
The engine probe confirmed the same creation and movement phases (screen X is
native X minus 128), but the old countdown reset rings one poll early.

| Row | Engine child/title state | Native rings | Before | After |
|---|---|---:|---:|---:|
| 22199 | art ready; act child X 692 |46|46|46|
| 22226 | all children at target; DISPLAY timer 0 |46|46|46|
| 22227 | first stationary parent poll; timer 1 |46|0|46|
| 22228 | reset poll; timer 2 |0|0|0|

Focused command (absolute reference ROM supplied):
`python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay-r7 -Dtest=TestSonic3kTitleCardKosQueue#retainedResetWaitsForArtAndLastChildMovementAndRestoresThatGate,TestS3kMgzLbzCarriedResultsTitleOwnership#lbzCarriedResultsHoldsEnemyAdmissionUntilItsTitleOwnerPoll -Ds3k.rom.path=<absolute-reference-ROM> test`.
Both cases passed as part of the final three-case invocation that also selected
`TestS3kLbzZoneSliceTraceReplay`:2 passes,1 inherited trace failure,0 errors/skips.
The new regression with the old clamp restored failed specifically because the
owner reset counters while real ROM archives were still pending (1 failure,
0 errors/skips). It also verifies rewind at the final movement latch, retained
ownership and an unchanged presentation clock after reset.

The full LBZ 46075-row trace improves 2991→2990 errors,0 warnings; first divergence
moves from 22227 rings to 22258 camera_x (`$04A0` native, `$04A3` engine). Comparing
all error spans except the derived cascading annotation removes only the single
rings error and adds none. An earlier six-zone comparison kept CNZ 5671, HCZ 4699,
ICZ 1287, MGZ 10634 and MHZ 3191 error/warning arrays identical to baseline. Its
focused selection passed 107 cases (including title rewind, actual 58/59-ring
carried ownership, MHZ completion and mandatory S3K loading checks); a new fixture
setup error was repaired and verified by the final invocation. No skips occurred.

Rejected intermediate approach: selecting the native gate while unconditionally
restarting `stateTimer` fixed rings but added 8 queue-field errors at 22331
(total 2998). Preserving the retained owner's independent presentation clock
removed those new errors. This preserves the inherited presentation policy;
it does not certify complete native `Obj_TitleCardWait2` timing. No fixture,
comparator, public request builder, queue capacity or ordinal changed. The
comparison-only diagnostic was removed. Remaining camera/animation discrepancies
are unresolved. These are focused/domain checks, not a full ordinary-suite pass;
root owns combined delivery validation.
