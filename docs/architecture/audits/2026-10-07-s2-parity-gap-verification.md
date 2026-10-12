# S2 parity-gap verification — 2026-10-07

Base: `09282b17305cb5794e43a26855cd2b9543b4ff5f` (`develop`).
Worktree: `.worktrees/ai-parity-swarm-20261007-s2`.

## Scope and measurement

Recheck the October 2 inherited special-stage 2/5/6/7 row-zero dynamic-art failures,
then the continuous complete-emerald chain. The native title competition capability
is audited separately. S2 ROM supplied through `${S2_ROM}` (CRC32
`7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`);
Java reports OpenJDK 21.0.12.1.
Commands below use `S2_ROM` for the task-provided absolute ROM path; the verified
identity pins the bytes without committing a machine-local home path. Maven uses the shared queue; broad integration
validation and central frontier/changelog prose belong to the swarm coordinator.

## Initial code and reference findings

The special-stage lanes already invoke `S2SpecialStagePredecessorReplay` before
constructing their special-stage provider. Nonempty opening ledgers cause topology-
selected predecessor level segments and movie gap rows to run; descriptors remain
comparison-only. An initial-ledger mismatch is therefore not sufficient evidence
for an in-stage timing fix. August 9 measurements previously made stages 2 and 7
green by correcting predecessor object-load ordering and the spring routine-0
load pass. Fresh execution must distinguish those old causes from current ones.

`TitleScreenManager.consumeExitAction()` currently emits only `ONE_PLAYER` for
native S2. S3K title menu selection 1 emits `TWO_PLAYER`, and creator providers can
also emit that generic action. `StartupRouteResolver` preserves the token, while
`GameLoop.executeTitleActionRoute` currently groups it with ordinary level startup.
The existing GameLoop regression explicitly expects that fallback. No competition
session/camera/human-P2 capability exists; this is an unsupported-token defect,
not evidence that native S2 exposes a working competition selection.

The shipped S2 title routine `TitleScreen_CheckIfChose2P` (`s2.asm:4560-4576`)
sets `Two_player_mode` and `Two_player_mode_copy` and selects `GameModeID_2PLevelSelect`.
It does not select the ordinary one-player level route. The bounded correction
rejects the unsupported action and reinitializes the existing title provider,
retaining `TITLE_SCREEN` without loading a level. The focused regression covers
both direct host exit and provider callback exit. The original committed test
expected `LEVEL`; this baseline behavior was established by code inspection, not
an executed red regression (the request was still waiting when the fix was applied
in the worktree).
Full competition remains outside this lane.

## Verification results

The source-confirmed title fallback correction passed:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestGameLoop#testDoExitTitleScreenRejectsUnsupportedTwoPlayerWithoutStartingLevel' test
```

At base `09282b173` plus the candidate title/regression edits, fresh
`TEST-com.openggf.TestGameLoop.xml` reports **1 test, 0 failures, 0 errors,
0 skips** (0.633 s); Maven emitted `BUILD SUCCESS`. Both direct host exit and
provider callback were exercised. The first build compiled the worktree from
scratch; sources remained unchanged during compilation.

Expanded startup validation completed with **105 tests, 0 failures/errors/skips**
(`TestGameLoop`: 97; `TestStartupRouteResolver`: 8), Maven `BUILD SUCCESS`,
24.537 s. Fresh XMLs were inspected.

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestGameLoop,TestStartupRouteResolver test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2SpecialStage2TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2SpecialStage7TraceReplay -Dsonic2.rom.path=${S2_ROM} test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2CompleteEmeraldRunChain,TestS2CompleteEmeraldRunPrefix -Dsonic2.rom.path=${S2_ROM} test
```

The four-stage sweep completed **4 tests, 4 failures, 0 errors/skips**,
27.303 s. Fresh XML and special-stage JSON report these errors:

| Stage | Errors | First error |
|---|---:|---|
| 2 | 12488 | row 0 `dynamic_art.outstanding_transfer_ids`, `[0]` vs `[]` |
| 5 | 13339 | same |
| 6 | 16370 | same |
| 7 | 16993 | same |

Each has zero bootstrap errors and zero warnings. All errors are dynamic-art
comparison fields: the inherited transfer is absent through row 125, its
retirement is absent at row 126, and subsequent transfer/edge identities shift
by one. There are no player/track comparison errors or later dynamic-art content
(mapping frame, owner, phase, requests) errors. This confirms the inherited
ledger gap; it does not establish an in-stage physics defect.

Both continuous tests completed **2 tests, 2 failures, 0 errors/skips**,
34.644 s. They stop at special-stage segment 3 with 17071 dynamic-art errors
(first row 0 outstanding IDs `[0]` vs `[]`). The standalone dynamic-art audit of
stage segment 1 is green (5681 comparisons, zero errors). The segment-2 EHZ1
comparison has 21638 errors and first row 6 `dynamic_art.edges` `[0]` vs `[]`.
The segment-0 report also records 44875 errors (43486 physics comparison errors,
including dynamic art; 1389 animation errors), first row 6 `dynamic_art.edges`
`[4]` vs `[]`; that report is not an asserted failure axis in the current harness.
The documentation claiming segments 0–10 green with a segment-11 PLC frontier
is stale for this base. These results are candidate-title-source measurements;
the title switch cannot run in these predecessor gameplay paths.

Matched predecessor confirmation:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay,TestS2Arz1CompleteEmeraldsSegmentTraceReplay -Dsonic2.rom.path=${S2_ROM} test
```

**2 tests, 2 failures, 0 errors/skips**, 26.125 s. EHZ1 seg2: 10259 errors,
first row 6 outstanding IDs `[0]` vs `[]`; ARZ1: 10114 errors, first row 6
outstanding IDs `[1]` vs `[]`. The standalone comparator counts differ from
continuous-chain comparator counts and must not be merged.

Replay requests originally included an explicit `-Dsurefire.forkCount=1`.
Those waiting requests were cancelled without executing and resubmitted without
the override: the queue treats a command-line fork-count override as machine
exclusive. `pom.xml:37` defaults `surefire.forkCount` to `1`, and trace profiles
consume that property (`pom.xml:382`), preserving single-fork semantics.
The initially queued r7 chain request was also cancelled before execution and
replaced by the S2 release-6 profile. None is a measurement.


## Production owner investigation

`GameplayTeamBootstrap` allocates runtime sidekick identity as
`characterName + "_p" + playerNumber` (lines 108–118). In contrast,
`LevelPlayableArtInitializer.applyPlayableArt` passed `playable.getCode()` into
`createDynamicArtOwner`, whose supported owners are semantic character/bank names.
Thus native `tails_p2` receives no body decision owner, although the separately
installed `tails-tails` owner remains active. Art loading in the same initializer
already resolves `playable.characterKey().persisted()`.

The row-6 ROM submission is `tails`, mapping 18, with three ROM DPLC requests;
the stage-2 opening descriptor is `tails`, mapping 71, ROM source 437856,
VRAM 62464, 512 bytes, fingerprint
`sha256:4d05f5e96c5e96d4d363b098bdd46c98e6d76e68d6d15f9ad7f8efd5302fd157`.
Those are comparison evidence only. Production Tails decisions come from stored
`mapping_frame` through `LoadTailsDynPLC` (`s2.asm:41659-41698`), deduped by
`Tails_LastLoadedDPLC`, then ROM-backed `MapRUnc_Tails` requests are submitted to
`QueueDMATransfer`. `Obj02_Control` always reaches that routine after animation
(`s2.asm:38990-39001`); the inherited descriptor belongs to the movie gap before
the special stage, not a stage-local invented job. Retirement belongs to
`ProcessDMAQueue` in the appropriate V-int handler, already modeled by the
existing dynamic-art service policy.

The bounded correction uses the semantic character key when installing the
existing decision owner. It changes neither timing nor comparison authority.
The focused regression initializes Tails with arbitrary runtime code `tails_p2`,
then exercises the captured production owner and verifies its `tails` DPLC bank
submission. Initial regression attempts hit harness setup errors (a sealed enum deep stub
and a graphics service omitted by the static service mock); those are not
measured behavioral red results.

The repaired focused regression then completed on the original owner source:
**1 test, 1 failure, 0 errors/skips**, 22.276 s; the captured decision owner was
null. Fresh XML identified `sidekickRuntimeCodeStillSubmitsItsCharacterDplcBank`.
The production correction changes only the registration argument from runtime
code to `characterKey().persisted()`; all ROM requests, dedup and DMA clocks remain
with their existing owners.

The change-based plan against the pinned base selects all **3005 ordinary
classes plus guards** because GameLoop is shared. This lane does not narrow that
selection or run a duplicate broad baseline; coordinator owns combined candidate
and post-integration validation. Lane-focused checks remain explicit partial
validation.

The candidate initializer regression class completed **14 tests, 0 failures,
0 errors/skips**, fresh XML 1.063 s, Maven `BUILD SUCCESS` (1:13 min including
production recompilation):

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestLevelPlayableArtInitializerModArt test
```

Matched trace remeasurement uses one default single-fork invocation:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2SpecialStage2TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2SpecialStage7TraceReplay,TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay,TestS2Arz1CompleteEmeraldsSegmentTraceReplay,TestS2CompleteEmeraldRunChain,TestS2CompleteEmeraldRunPrefix -Dsonic2.rom.path=${S2_ROM} test
```

The eight-class remeasurement completed **8 tests, 4 failures, 0 errors/skips**,
Maven `BUILD FAILURE`, 39.374 s. Fresh XML and JSON establish:

| Fixture | Before | After | First error after |
|---|---:|---:|---|
| stage 2 | 12488 | 0 | none |
| stage 5 | 13339 | 0 | none |
| stage 6 | 16370 | 0 | none |
| stage 7 | 16993 | 16993 | row 0 outstanding transfer IDs |
| standalone EHZ1 seg2 | 10259 | 0 | none |
| standalone ARZ1 | 10114 | 3203 | row 1961 `tails_x_speed`, `-0146` vs `0x0A00` |

ARZ1's remaining launch-sized Tails velocity difference precedes the stage-7
entry. An accurate fix requires diagnosing its contact/dispatch owner, not
constructing the missing inherited art descriptor from the manifest.

Both continuous-chain assertions remain red, now stopping at special-stage
segment 1. Its dynamic-art audit has 15713 errors across 5681 comparisons;
segment 0 has 42538 errors and first non-camera physics difference row 737
`sidekick_y`, `0x01EA` vs `0x01E9`. The chain fails two axes (walk and gap) rather
than five; that count is not a pass or by itself evidence of improvement. Giving
Tails its production owner makes its divergent mapping decisions observable;
matched old-owner/current non-art attribution follows below.

S3K's current semantic policy is
`EVERY_CLAIM_WITHOUT_PLAYER_ART_AUDIT(false)` (`GameRules:496`), so this owner
installation remains inert for that consumer. S1 and S2 use their existing ROM
bank profiles; arbitrary runtime IDs now resolve through character identity.
No new field, rewind state, API signature, job kind or clock was introduced.

### Matched physical-field attribution

Only the initializer registration argument was temporarily restored to its old
value after the eight-class run finished, then restored byte-for-byte to the
candidate before the current remeasurement. No command compiled through a source
edit. Commands used the same trace profile, default fork, order and ROM path:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Arz1CompleteEmeraldsSegmentTraceReplay -Dsonic2.rom.path=${S2_ROM} test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay -Dsonic2.rom.path=${S2_ROM} test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay,TestS2Arz1CompleteEmeraldsSegmentTraceReplay -Dsonic2.rom.path=${S2_ROM} test
```

Old-owner runs: each **1 test, 1 failure, 0 errors/skips** (1:15 min ARZ1,
22.989 s EHZ1). Current run: **2 tests, 2 failures, 0 errors/skips**, 1:17 min.
For every non-dynamic-art mismatch, compare the ordered tuples
`(field,start_frame,end_frame,expected_at_start,actual_at_start)` across old/current
reports. All **299 ARZ1 spans** and **723 EHZ1 spans** match exactly. Their
SHA-256 digests are respectively
`2e143827594c398b49ed3aceb1f959e09dd6f11dede71d4609bdf71bcdac9e2b` and
`37f3dcb99de7e9161582754fdc74388db07acf3d2c001bd2d3e6cd8f8e24667f`.
The comparator's cascading annotation is excluded because removing an earlier
art error changes root-error labeling, not observed values. EHZ1's standalone
first physical field is `tails_y_speed` at row 737, `-03AF` versus `-0400`,
identical before/after. ARZ1's row-1961 first physical field also matches exactly.
EHZ1 totals are 12707 old versus 8176 current; these are not chain counts.
This establishes unchanged observed gameplay and newly visible production art
work, despite the continuous assertion stopping earlier.

## Horizontal spring contact gate

The S2 object skill was applied to the remaining ARZ1 launch-sized velocity
frontier. `SpringObjectInstance.applyCheckpointContact` admitted every pushing
horizontal contact, although its comment claimed launch-side admission. The
ROM `Obj41_Horizontal` subtracts player word `x_pos` from spring `x_pos`, uses
`bcs` and the spring's `x_flip` bit, then calls `loc_18AEE` only from the launch
side (`s2.asm:33979-33987` P1, `34002-34010` P2). Unflipped admits player x strictly
above spring x; flipped admits x at or below spring x. These are unsigned word
relations, including the equality edge. `applyHorizontalSpring` also lacked a
side gate, so the omission was confirmed in production rather than inferred from
one fixture's velocity.

Focused regression `horizontalPushLaunchRequiresTheFacingSideIncludingEquality`
completed **1 test, 1 failure, 0 errors/skips**, 20.375 s on the original spring
source. It observed an unflipped back-face pushing contact launch at +0xA00
instead of preserving +0x123. The regression exercises both flips and left,
equal and right x relations, and checks both velocity and inertia. The bounded
correction transcribes the ROM gate before the existing launch helper; it adds no
state and leaves proximity launch ownership separate. It applies to all normal
horizontal S2 spring contacts through native P1/P2 participation, independent of
zone, route, BK2 or frame.

Affected local obligations: horizontal S2 spring contact, both flips, both
sides/equality, native sidekick participation; preserve existing launch subpixel
and proximity tests. Inherited EHZ1 row-737 motion divergence remains a separate
contract. Central act/coverage/frontier updates are coordinator-owned.

The full spring regression class then completed **13 tests, 0 failures,
0 errors/skips**, fresh XML 0.297 s, Maven `BUILD SUCCESS` (1:15 min with
recompilation):

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSpringObjectInstance test
```

The EHZ1 row-737 non-art identity provides decisive narrowing: engine Tails
switches to Hurt (routine 4, raw animation 0x1A, x velocity -0x200, y velocity
-0x400, inertia zero) while ROM remains rolling airborne in routine 2, animation
2. Native `cpu_state` still has held jump input 0x14. Thus interpreting -0x400 as
an input/jump-height cap alone is rejected; the engine's damage helper also writes
that velocity. The remaining frontier requires identifying the production touch
source and its ROM admission/lifetime/invulnerability state. Nearby recorded
post-frame objects include the defeated enemy's explosion, animals and points,
but comparison data alone cannot establish which engine object delivered damage.
No new touch, physics or timing change is justified without that owner probe.

The spring regression was renamed after its completed checks from
`...IncludingWordBoundary` to `...IncludingEquality`, accurately describing
the exercised spring-position equality edge. Assertions and behavior are unchanged;
no repeat was required solely for that rename.

### Final three-fix trace candidate

The same eight-class/default-single-fork command completed after the spring
correction and test rename, with production source frozen during compilation:
**8 tests, 4 failures, 0 errors/skips**, 40.370 s, Maven `BUILD FAILURE`.
All four previously passing candidate fixtures remain green (stages 2/5/6 and
EHZ1 seg2). Stage 7 stays 16993 errors at row 0; ARZ1 stays 3203 errors at
row 1961. The chain/prefix remain red at segment 1 (15713 art mismatches) with
segment-0 physical report 42538 errors and first row 737 sidekick y difference.

The spring gate is a separately confirmed shipped-branch omission and its
regression passes. It does **not** explain or close the measured ARZ1/stage-7
frontier. The initial causal suggestion based on a launch-sized velocity is
rejected by the unchanged matched result. No proximity/physics/timing constants
were tuned to force that route green.

### Bounded damage-owner probe and final dispositions

A temporary subclass of the existing EHZ1 seg1 replay enabled the existing touch
overlay and logged the first naturally Hurt sidekick, without altering inputs,
gameplay, timing, comparison or fixture values:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dtest=TestS2Ehz1DamageOwnerProbe -Dsonic2.rom.path=${S2_ROM} test
```

Completed **1 test, 1 failure, 0 errors/skips**, 23.907 s; fresh XML 2.833 s.
It reproduces 8176 errors and the same row-737 first field. At the engine's
post-step level clock 738, the touch owner is slot 17, spawn object 0x9D/subtype
0x1E, collision position (1077,474), flags 0x8B, 8x8 hurt radius, overlapping.
`CoconutsBadnikInstance` creates its coconut `BadnikProjectileInstance` using
the parent's spawn descriptor, explaining the diagnostic's 0x9D label; the
runtime projectile category/size matches `Obj98_SubObjData`'s 0x8B
(`s2.asm:74861`). The next task should compare production Coconuts throw,
child allocation, deferred `Obj98_Init`, render-on-screen deletion and
`Obj98_CoconutFall` movement (`s2.asm:74778-74822`, `Obj9D:75238+`) against the
ROM, rather than adjusting Tails physics. The probe establishes the engine
damage owner; it does not yet establish which projectile timing/admission
condition differs. Its source, compiled class and probe XML/text reports were
removed after extraction; no diagnostic implementation is committed.

- Verified fixed: unsupported generic TWO_PLAYER title fallback; missing native
  sidekick DPLC owner; omitted horizontal spring pushing launch-side gate.
- Verified trace closure: special stages 2/5/6 and EHZ1 seg2.
- Verified remaining: stage 7 inherited ledger gap; ARZ1 row-1961 Tails horizontal
  launch divergence; EHZ1 seg1 row-737 erroneous coconut damage and downstream
  stage-1 art ledger `[0]` versus `[0,1]` at row 0.
- Stale: documented current chain/prefix segments 0–10 green, inherited August
  stage-2/7 causal diagnoses as explanations of this base's measured failures.
- Not remeasured in this lane: halfpipe roundtrip, September CPZ2 physics closure,
  later chain PLC/load-clock frontiers and KiS2 mode behavior. Existing timing
  documentation is not evidence that a current earlier gameplay divergence is a
  non-frame-derivable limit; no new such exemption is asserted.

Seven files are delivered: GameLoop/title regression, playable-art initializer/
registration regression, S2 spring/contact regression, and this audit. Central
release, discrepancy, frontier and coverage prose remain coordinator-owned.
All local Maven sessions completed; no lane process or temporary probe remains.
Combined broad candidate/post-integration verification, merge, push and worktree
cleanup remain the coordinator's delivery responsibility.

## Round 2: EHZ1 coconut targeting

This continuation starts from pinned integrated base
`5b3a63641033506fc0d89ad5188a0c97fae29089` on
`bugfix/ai-trace-s2-ehz-coconut-20261007-r2`. Its measurements are distinct from
the preceding round. The coordinator owns combined broad verification,
central discrepancy/frontier/release prose and integration.

### ROM owner and bounded hypothesis

`Obj9D_Idle` calls `Obj_GetOrientationToPlayer` before its attack and idle-timer
decisions (`s2.asm:75258-75278`). The helper chooses MainCharacter or Sidekick
by absolute signed-word X distance, retaining MainCharacter on a tie
(`s2.asm:72962-72991`). Its selected object-minus-player delta drives both
orientation and the literal unsigned word window `(d2 + $60) < $C0`.
Consequently target offsets -$5F through +$60 are admitted; -$60 and +$61 are
excluded. Equality keeps orientation index zero.

The base Java idle routine instead reads only its supplied main player,
uses a strict X comparison and an absolute-distance gate. The existing
`ObjectPlayerQuery.nearestByRomX(NATIVE_P1_P2, ...)` already supplies the
ROM selection and tie semantics, so this hypothesis needs no shared query,
player, timing or physics change. Coconuts' throw offsets and velocities
already match `Obj9D_CreateCoconut`/`Obj9D_ThrowData`
(`s2.asm:75368-75404`); reversing them is rejected by that literal reference.

Separately, `Obj98_Main` tests the previous render-on-screen bit and deletes
before dispatching movement (`s2.asm:74794-74802`); the generic Java projectile
checks screen bounds after movement. That observation alone does not establish
the row-737 cause and is not grounds for an unmeasured projectile rewrite.
`Obj98_Init` returns through LoadSubObject, and `Obj98_CoconutFall` adds $20
gravity before ObjectMove (`s2.asm:74790-74822`).


### Fresh pinned baseline and regression red

Both queued original requests executed after a long resource wait; production
source remained exactly the pinned base throughout compilation and execution.
These waits are not measurements. Java 21, the same verified S2 ROM and the
unchanged default single-fork trace profile were used; the POM's shared
`surefire.forkCount` defaults to 1, with no CLI override.

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay,TestS2CompleteEmeraldRunChain,TestS2CompleteEmeraldRunPrefix -Dsonic2.rom.path=${S2_ROM} test
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestCoconutsInitialization test
```

The replay command completed with Maven failure: **3 tests, 3 failures, 0
errors/skips**, 1:31 min. Fresh XML times: chain 7.797 s, prefix 5.494 s,
standalone EHZ1 seg1 1.515 s. Standalone remains **8176 errors**, first row
**737 `tails_y_speed`**, ROM `-03AF` versus engine `-0400`. Each chain's
segment-0 JSON remains **42538 errors**, first row737 `sidekick_y`,
`01EA` versus `01E9`; each segment-1 art JSON remains **15713 errors**,
first row0 `dynamic_art.outstanding_transfer_ids`, `[0]` versus `[0, 1]`.
The walk stops in segment1 and the shared dynamic-art gap also fails.

The regression red completed with Maven failure: **4 tests, 2 failures, 0
errors/skips**, 24.188 s (fresh XML 0.969 s). The inherited initialization
and idle-expiry cases pass. The added nearer-sidekick case fails on facing;
the main-tie/range-window case fails on X equality. No executed red claim
is made for later assertions in those failing test bodies.

The candidate changes only Coconuts idle targeting, signed-word orientation
and the unsigned word range gate. It uses optional injected services and
retains the existing supplied-player path in direct-object contexts without
services. It adds no state fields; the existing badnik facing and Coconuts
snapshot already carry the decision. The new regression resumes a captured
throw decision and compares forward continuation after restoration.

Native comparison-only auxiliary evidence: the relevant parent appears at
row173, slot23, X0427/Y01D8. Native Obj98 children appear at row233
X0432/Y01AC and row548 X041C/Y01C3; no new Obj98 appears through row738.
The parent's observed routine changes include climbing→idle at679,
idle→climbing at696, and climbing→idle at712. These observations narrow the
attack-history question; they do not make the target correction a measured
explanation of the damaging engine shot.

Develop advanced concurrently to `4cfb745646d9439cdb9c07d53d0670dd0fb3fe58`.
Coconuts is unchanged upstream. This lane retains the pinned baseline inputs;
actual-destination reconciliation and broad qualification remain coordinator-owned.


### Candidate verification and dispositions

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestCoconutsInitialization test
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay,TestS2CompleteEmeraldRunChain,TestS2CompleteEmeraldRunPrefix,TestS2SpecialStage2TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay -Dsonic2.rom.path=${S2_ROM} test
python3 tools/testing/run_categories.py --base 5b3a63641033506fc0d89ad5188a0c97fae29089
```

The focused candidate completes with **4 tests, 0 failures/errors/skips**,
Maven success, 1:13 min including source/test recompilation, fresh XML 0.640 s.
All assertions in the range-window loop and the rewind continuation execute.

The seven-fixture candidate completes with **7 tests, 1 failure, 0
errors/skips**, 49.193 s, Maven failure solely in the full chain. Fresh XML:
full chain 12.312 s; prefix 7.336 s; EHZ1 seg1 1.410 s; EHZ1 seg2 1.068 s;
stages2/5/6 0.968/1.047/1.485 s. Source is frozen during both commands.

- **Closed:** standalone EHZ1 seg1 **8176→0** errors; no first mismatch remains.
  The matched chain segment-0 physics report **42538→0** and segment-1 art
  report **15713→0** in both full and prefix drives. The erroneous coconut
  damage is causally removed by the parent targeting/orientation/range fix.
- **Preserved:** EHZ1 seg2 and standalone stages2/5/6 remain at zero errors,
  with no ROM skip. The continuous prefix target passes; its gap report has
  ten compared gaps and zero failures.
- **Newly exposed full-chain frontier:** walk stop advances from segment1 to
  segment17 (`ss_7`). Full-chain physical reports are zero through segment11
  (`seg7_ehz2`); segment12 (`seg8_cpz1`) has **26735** errors, first non-camera
  mismatch row4394 `x`, ROM255B versus engine255C. Segment13 (`seg9_cpz2`)
  has **15553**, first row4859 `x`, 04DB/04DC. Segment15 (`seg10_cpz2`) is
  zero; segment16 (`seg11_arz1`) has **19884**, first row1961
  `sidekick_x_speed`, -0146/0A00. Segment17 art has **22405**, first row0
  outstanding-transfer IDs `[0]`/`[]`. These later counts have no matched
  pre-fix measurement because the baseline stopped at segment1; they are not
  presented as new regressions or inherited-count equivalence.
- **Earlier full-chain gap frontier:** its report has seventeen gaps and
  twelve failures. The first is `ss → seg2_ehz1`, starting with
  `run_gap.edge[0].movie_logical_frame`, expected10308 versus actual10268.
  This differs from the passing prefix drive's gap result. Neither aggregate
  totals nor the passing prefix erase the full chain's clock/ledger failures;
  timing/admission investigation is separate coordinator-owned work.
- **Rejected as this damage's explanation:** a generic Obj98 projectile
  rewrite is unnecessary to close row737. Its independently observed
  before/after-movement deletion difference remains unimplemented and is not
  certified by this route's pass.

The category plan selects **2640/3007 ordinary classes plus guards**. It is
inspected, not executed in this worker. The coordinator owns the combined
baseline/candidate/post-integration broad validation, actual destination
reconciliation, merge and push. Focused checks are not a broad-suite pass.
No comparison tolerance, trace data, timing authority, object allocation,
player physics or projectile movement changed. No fixture- or frame-based
behavior, fitted constant or gameplay hydration was added. The registered
owner is S2-specific; KiS2's shared consumer has not been replay-measured in
this round. Existing S2 nearest-player and orientation pitfalls already
cover this lesson, so skills/guidance are unchanged.

Three lane files are delivered: Coconuts owner, its initialization/targeting/
rewind regression, and this audit. All four local Maven requests have completed;
no temporary probe or lane process remains. Central documentation and final
integration/cleanup remain coordinator-owned.

### Round 2 follow-up: CPZ1 controlled-path frontier

This follow-up starts from the separately delivered EHZ owner commit
`8dba700f3a63379bef95b024194d6e2224d1a390` in the same lane worktree.
The earlier chain CPZ1 segment 12 frontier was row 4394, x `$255B` versus
`$255C`. A standalone replay, with no preceding special-stage gaps,
reproduced the same row and x values and first reported y speed `-$0600`
versus `-$0492`. Therefore the earlier `ss -> seg2_ehz1` clock gap
(expected 10308, actual 10268) is not necessary to reproduce this local
physical divergence. That timing gap remains a separate unresolved claim.

Executed baseline command:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Cpz1Seg8CompleteEmeraldsSegmentTraceReplay -Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen test
```

Session 9853 completed with Maven exit 1: one test, one failure, zero errors
and skips, 30.462 seconds. Fresh comparison JSON reported 5318 errors:
5134 physics and 184 animation, zero bootstrap errors/warnings, 6613 rows.
Physics first diverged at row 4394; animation first diverged at row 4834,
ID `$0010` versus `$0002`. Alternating transport movement and waypoint
writes implicate Object1E rather than ordinary free movement.

ROM owner `loc_22902` / `loc_22952` (`docs/s2disasm/s2.asm`:48820-48874)
performs two sequential signed divisions: dominant-axis distance shifted
by 16 divided by signed speed, then cross-axis distance shifted by 16
divided by the first quotient's signed word. It stores `ABS.W` of that
quotient at `2(a4)`. Entry/main traversal `loc_2271A` / `loc_227FE`
(48636-48640, 48714-48718) decrements the stored word's high byte with
`SUBQ.B` and tests `BPL`. Zero is legal and advances the waypoint on the
following dispatch without an extra movement step. The old Java owner
collapsed the divisions algebraically and clamped the frame counter to
one. The neighboring MTZ tube independently models the sequential
divisions and zero high-byte duration (`loc_27374`).

Executed red command:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestCPZSpinTubeObjectInstance test
```

Session 97949 waited 1852 seconds before admission and completed with exit
1, seven tests, one failure, zero errors/skips, 27.695 seconds Maven and
0.331 seconds XML. The executed failure was the legal tiny-segment counter:
expected zero, actual one. The later expanded `$9/-$8` at speed `$700`
quantization assertion and rewind assertion were added after this red;
they are not claimed as executed red evidence.

The candidate preserves the existing per-player duration field/map.
`DefaultObjectRewindPolicies` already captures `characterStates`; no state
or shared physics/harness owner was added. The bounded path arithmetic does
not emulate Motorola `DIVS` overflow outside the ROM waypoint domain.
Candidate focused and matched replay outcomes are pending below.

Direct table attribution: `word_22B40` in `docs/s2disasm/misc/obj1E_a.asm`
lines 131-138 contains `$C0/$70`, `$CA/$6F`, `$D4/$6C`, `$DB/$68`,
`$E3/$62`. With tube origin `$2480/$0500`, these are the native observed
waypoints `$2540/$0570`, `$254A/$056F`, `$2554/$056C`, `$255B/$0568`,
`$2563/$0562`. The `$D4/$6C -> $DB/$68` segment is seven pixels across
and four up: duration word `$00E0`, high byte zero. The old clamp inserts
an eight-pixel movement to `$255C` at y speed `-$0492`; the native dispatch
snaps to `$255B` and computes the next eight-across/six-up segment's
`-$0600` velocity. This accounts for both row-4394 fields without fitted
values or timing hydration. At production speed `$800`, the first division
is exact for these distances, so algebraic cross-velocity collapse is
not independently blamed for this replay. The non-`$800` assertion tests
the literal routine semantics; the zero-duration clamp is the causal defect.

Candidate replay command (unchanged production throughout execution):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Cpz1Seg8CompleteEmeraldsSegmentTraceReplay,TestS2Cpz2Seg9CompleteEmeraldsSegmentTraceReplay,TestS2Cpz2Seg10CompleteEmeraldsSegmentTraceReplay,TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay,TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay,TestS2SpecialStage2TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2CompleteEmeraldRunPrefix,TestS2CompleteEmeraldRunChain -Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen test
```

Session 8053 waited 4590 seconds, completed with Maven exit 1, 10 tests,
one failure, zero errors/skips, 50.336 seconds. Fresh per-class XML and
comparison JSON were inspected. Standalone CPZ1 changed **5318 -> 0**;
both CPZ2 slices report zero errors, warnings and bootstrap errors. EHZ1
segments 1/2, special stages 2/5/6 and the prefix stayed green. Per-class
XML seconds: chain 11.866, prefix 7.267, CPZ1 2.037, CPZ2 segments 9/10
1.674/2.057, EHZ1 segments 1/2 1.058/0.971, stages 2/5/6
0.927/0.989/1.382. The CPZ2 standalone outcomes are candidate controls,
not measured before/after standalone claims.

Matched chain segment 12 (CPZ1) changed **26735 -> 0** and segment 13
(CPZ2) **15553 -> 0**. Reports remain zero through segment 15. The full
chain stays red with 13 axes, stopping in special-stage segment 17. Its
first remaining physical frontier is segment 16 `seg11_arz1`, row 1961,
`sidekick_x_speed` native `-$0146`, engine `$0A00`, 19884 errors, unchanged
from the `8dba700f3` run. Segment 17 special-stage art remains 22405 errors,
first row zero outstanding IDs `[0]` versus `[]`, also unchanged. Earliest
shared gap still reports `ss -> seg2_ehz1`, `movie_logical_frame` expected
10308, actual 10268. Closing CPZ1 while leaving that earlier gap unchanged,
together with the standalone reproduction and ROM waypoint arithmetic,
separates this physical defect from the gap. The chain is not green;
aggregate axis reduction is not used as a pass claim.

The first candidate focused request, session 1303, waited 4569 seconds and
completed with seven tests, zero failures, one error, zero skips, 1:11 Maven
and 0.217 seconds XML. Arithmetic assertions passed; the newly added rewind
fixture incorrectly called capture without a player identity table. The
production contract requires one for player-keyed maps. After replay 8053
completed, the test alone was corrected to register the main player in
`RewindIdentityTable` and use its capture/restore context. Production did
not change; the corrected focused request is recorded below when complete.

Rejected alternatives: attributing CPZ1 to the special-stage gap, changing
shared player movement or admission, fitting a curve velocity to a trace,
and retaining a minimum-one duration. The first two are unnecessary for
the independently reproduced ROM-backed owner defect; the latter two
contradict `loc_22902` and the actual waypoint table. No comparison tolerance,
trace payload, timing authority, fixture gate or hydration changed. This
follow-up adds a reusable P87 lesson to both S2 object-skill mirrors; the
coordinator owns central release/frontier/coverage prose and broad checks.

Change-based plan inspected, without launching a worker broad run:

```bash
python3 tools/testing/run_categories.py --base 8dba700f3a63379bef95b024194d6e2224d1a390
```

It selected 2640 of 3007 ordinary classes plus guards. The coordinator owns
combined broad validation against the actual updated destination; these
focused and trace results are not a broad-suite pass. No diagnostics run
was created by this dry plan. KiS2 is an unmeasured shared S2-object consumer
in this follow-up. The unit exercise uses a direct private arithmetic call
and captured counter restoration; live CPZ1/CPZ2 replays provide traversal
coverage. No out-of-ROM-path quotient-overflow behavior is certified.

Direct binary corroboration used the verified absolute `s2.gen` path:
a 30-byte big-endian word read at ROM offset `$22B40` returned
`0070 0010 0010 0010 0070 00C0 0070 00CA 006F 00D4 006C 00DB 0068 00E3 0062`.
Thus the referenced short curve is present in the shipped ROM, not merely
in the read-only dirty disassembly checkout. This bounded read creates no
probe artifact and is reproducible from the committed owner offset.

A bounded scan of the existing entry/main arrays found maximum adjacent
waypoint dominant distances of 208/928 pixels, respectively. At `$800`,
these yield duration words `$1A00`/`$7400`, within signed `DIVS.W` quotient
range. Thus ordinary adjacent paths do not require overflow emulation;
unusual external/corrupted path inputs remain outside the verified domain.
The inline scan is regenerable in minutes and creates no saved probe.

The corrected normal-shape unit request 14575 remained waiting and was not
a measurement. At the coordinator's request, a read-only process check
confirmed wrapper PID 2023210 had the owned worktree cwd and no children;
only that owned waiting request was cancelled (exit 130, validation
incomplete). Exact selector and source inputs were preserved and resubmitted
as the supported profile-free focused shape:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestCPZSpinTubeObjectInstance test
```

Session 31346 is the replacement; no unrelated request, lock, profile,
selector or queue policy was changed. Its completed result follows below.

Session 31346 admitted after 2437 seconds and completed successfully on the
unchanged production candidate with the repaired identity-table fixture:
7 tests, 0 failures, 0 errors, 0 skips (fresh XML 0.272 seconds; Maven
24.008 seconds). This closes the focused arithmetic/zero-counter/rewind
check; the earlier seven-case setup error is not a production failure.

A bounded read-only ARZ review also confirms that the remaining row 1961
sidekick speed mismatch occurs in the independent standalone replay recorded
in the prior campaign (3203 errors), not only in the chain. Native spring
slot 18 at $0988/$0360 changes Obj41 routine 00 to 04 at row 1961 without
launching; native launch follows at 1962. Obj41_Init returns after preparing
the horizontal routine, whereas the current S2 SpringObjectInstance.update
initializes and executes contact work in the same call. This is an actionable
owner hypothesis; engine allocation and first-execution timing must be
measured before a causal fix. No spring source or extra test was changed
for this review. The next investigation is separately authorized.


### ARZ spring reload: Obj41 init-only dispatch follow-up

This follow-up starts from qualified CPZ commit `e87d41553`. A comparison-only,
temporary Spring logging probe (session 7873) ran the independent ARZ selector:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Arz1CompleteEmeraldsSegmentTraceReplay -Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen test
```

It waited 316 seconds, then reproduced 1 test / 1 failure / 0 errors / 0 skips
(XML 2.715 seconds): 3203 comparison errors, zero warnings, first row 1961
Tails x speed native `-0146`, engine `0A00`. All probe code was removed after
extracting the owning boundary. The first incarnation initialized at V-int
24756 with both players distant; the reloaded incarnation initialized at
25704 with Tails centre `0993/0361`, velocity `-0146`. Its next logged launch
read `099B/0361`, velocity zero, after the solid correction. Native aux slot
18 at `0988/0360` was routine 00 at row 1960, routine 04 at row 1961, and
launched only at row 1962. This establishes a local reload/first-dispatch
frontier independently of the chain bootstrap and its earlier gaps.

The owner is `Obj41_Init` / `Obj41_Init_Subtypes` / `Obj41_Init_Common`
(`s2.asm` 33824–33888, native labels `loc_188A8` through `loc_18954`). All
five native type branches prepare the active routine and end at the common
`rts`; none runs animation, SolidObject or the launch path during that pass.
The S2 owner previously initialized and ran its active routine in one Java
update. The bounded correction returns immediately after first initialization.
S1 and S3K use separate spring owners. No shared physics, manager, comparator,
input, timing or bootstrap state changes are involved. The existing init-return
lesson in S2 pitfall P69 already covers this dispatch rule; no duplicate skill
entry was required.

The regression uses real ObjectManager solid dispatch at the recorded native
contact coordinates, requiring unchanged player velocity/position on the init
pass and the `0A00` launch on the following active pass. It captures/restores
before and after initialization, verifying the existing captured initialized
phase rather than adding rewind state. Five additional subtype cases verify
that init makes no solid-registry calls. The existing diagonal contact-only
fixture explicitly starts at the active routine; its native contact assertions
are unchanged.

The exact focused red command was:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestSpringObjectInstance#horizontalInitializationReturnsBeforeFirstContactAndRestoresDispatchPhase test
```

Session 61489 waited 10677 seconds and ended 1/0/1/0: fixture manager injection
was overwritten by the legacy constructor, producing a missing-object-manager
NPE in trigger. Session 89600 waited 392 seconds and ended 1/0/1/0 after that
repair: the explicit full manager constructor also required a camera. These
are setup errors, not intended assertion reds. Session 4748 admitted immediately
and reproduced the intended 1/1/0/0 red (XML 0.291 seconds, Maven 26.482 seconds):
first-pass velocity expected -326, actual 2560, on unchanged production.

Candidate focused session 31715 used:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestSpringObjectInstance,TestCPZSpinTubeObjectInstance,TestCoconutsInitialization test
```

It waited 178 seconds, then returned 25/1/0/0: the first-pass assertions passed,
CPZ seven cases and Coconuts four cases passed, and all 13 prior Spring cases
passed. The new next-pass assertion retained -326 because its manager update
camera argument was still zero despite a camera object at `0800`. The manager's
normal MarkObjGone retirement unloaded the spring at `0988` after init; old
same-pass launch had hidden that fixture mistake. After the frozen trace run
completed, only that new fixture's camera argument was aligned to `0800` and an
admitted-object assertion added. Native motion/contact expectations remain
unchanged. Production stayed frozen.

Matched candidate trace session 96278 ran:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2Arz1CompleteEmeraldsSegmentTraceReplay,TestS2Cpz1Seg8CompleteEmeraldsSegmentTraceReplay,TestS2Cpz2Seg9CompleteEmeraldsSegmentTraceReplay,TestS2Cpz2Seg10CompleteEmeraldsSegmentTraceReplay,TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay,TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay,TestS2SpecialStage2TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2CompleteEmeraldRunPrefix,TestS2CompleteEmeraldRunChain -Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen test
```

It waited 538 seconds and ended 11 tests / 1 failure / 0 errors / 0 skips,
Maven 53.239 seconds. Fresh XML and JSON confirm standalone ARZ 3203 → 0
across all 3420 rows, bootstrap errors/warnings zero. Chain ARZ 19884 → 0;
downstream SS7 dynamic-art errors 22405 → 0 (8498 comparisons). Every physical
and dynamic-art segment report through segment 17 is zero. CPZ1/2, EHZ1
segments 1/2, special stages 2/5/6 and the prefix remain green. This closes the
local ARZ physical mismatch and its inherited SS7 art symptom.

The full chain still fails on 11 axes: the earliest shared gap retains movie
clock native 10308 versus engine 10268 (`ss` → `seg2_ehz1`), and the SS7
results walk exceeds destination 101691. Removing the two local comparison
axes does not certify the chain or explain the remaining gap clocks. No trace
rows create work or supply gameplay state. The prior proximity-tuning hypothesis
remains rejected; the measured cause is init dispatch after reload.

Final focused session 93357 uses the unchanged three-class command above with
the repaired camera fixture and added native subtype cases; its completed
result follows below. Coordinator owns actual-destination broad validation,
central frontier/release prose and integration. This lane remains local.


The consumed candidate JSON identities under `target/trace-reports/` were:
`trace/s2_arz1-single-21a00e9f0f325d9b.json`;
`run-chain/s2-sonic-tails-complete-emeralds_seg16-segment-16-b4a287e72ed8a6a7.json`;
`run-chain/s2-sonic-tails-complete-emeralds_seg17_dynamic_art-segment-17-dynamic-art-c23f0fc6118bf835.json`;
and the full-chain gap report
`run-chain/s2-sonic-tails-complete-emeralds_dynamic_art_gap-dynamic-art-gap-63186ce890be658f.json`
(17 gaps, 10 gap failures). The prefix gap report suffix `e1ed80868c3bec14`
is a different identity (10 gaps, zero failures); its success is not full-chain
success. Raw outputs stay temporary under target.

The remaining 11-axis frontier consists of the SS7 results walk failure plus
these ten gap clock axes (first error within each gap, native/engine):

| Boundary | First edge | Movie logical frame native/engine |
| --- | --- | --- |
| ss → seg2_ehz1 | 0 | 10308/10268 |
| ss_2 → seg3_ehz1 | 0 | 20220/20180 |
| ss_3 → seg4_ehz1 | 0 | 31446/31406 |
| seg4_ehz1 → seg5_ehz2 | 8 | 32921/32920 |
| ss_4 → seg6_ehz2 | 0 | 46347/46309 |
| ss_5 → seg7_ehz2 | 0 | 57004/56966 |
| seg7_ehz2 → seg8_cpz1 | 6 | 61196/61195 |
| seg8_cpz1 → seg9_cpz2 | 5 | 67986/67985 |
| ss_6 → seg10_cpz2 | 0 | 82295/82259 |
| seg10_cpz2 → seg11_arz1 | 7 | 89590/89589 |

Each field is `run_gap.edge[N].movie_logical_frame`. The walk failure is
`uncompared-interior physical walk exceeded destination 101691`, segment
`ss_7`, mode `SPECIAL_STAGE_RESULTS`, movie cursor 101691. Later windows
remain unmeasured in this invocation. This lane will not change the shared
driver/results authority to chase that remaining frontier.


Session 93357 waited 5263 seconds and passed 30 tests / 0 failures / 0 errors /
0 skips (Maven 24.585 seconds). Fresh XML: Spring 19 cases, 0.372 seconds;
CPZ seven, 0.255 seconds; Coconuts four, 0.235 seconds. The native first-pass,
next active pass, admitted-object and before/after-init rewind assertions all
pass. The expanded subtype cases were candidate coverage, not executed red
claims. Initialization already belongs to the existing object rewind schema;
no new snapshot policy was required.

Dry selection inspection:
`python3 tools/testing/run_categories.py --base e87d41553eac5b2844599c5f06c5aa4f9d0c3392`
selected 2640/3007 ordinary classes plus guards. This worker did not execute
that broad plan: root owns the combined actual-destination baseline/candidate/
integrated validation. Focused and domain trace results above are not a broad
suite pass. KiS2 is unmeasured. The direct rewind regression covers object
initialization phase; the real standalone/full-chain ARZ trace covers the
actual CPU Tails launch, but this follow-up adds no live timeline rewind route.
Registry expiry was checked at completion, 2026-10-08 02:31:51 UTC, before
its authoritative 15:39:46 UTC expiry. No owned Maven request remains pending;
all temporary Spring probe changes and commit-message files are removed.


## Round 3 — stock seventh-emerald results sequence (2026-10-08)

Exact base: `098053c4a01c2af283ca6797463bb5051442ef0b`.
Owned branch/worktree: `bugfix/ai-trace-s2-frontier-20261008-r3` /
`.worktrees/trace-s2-frontier-20261008-r3`. Hooks installed; Java 21.0.12.1,
`DISPLAY=:0`, `LUA_BIN=/usr/bin/lua5.4`. This round's `S2_ROM` is the original
root `Sonic The Hedgehog 2 (W) (REV01) [!].gen`, freshly verified CRC32
`7B905383` / SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.
No ROM aliases, submodule changes, or shared timing/driver changes were made.

### Native owner and rejected alternatives

Stock `Sonic2SpecialStageDataLoader.createResultsMessages` returns null;
KiS2 overrides it with `SplitNameResultsMessages`. The stock fallback therefore
paid the ordinary $78 wait before a stationary $B4 display, totaling 300
post-tally passes. It omitted stock Obj6F's movement and replacement sequence.
The committed foreign fade branch `26b8a3baa61700db50dc01e643e0c103c633cd53`
was inspected read-only, is not an ancestor of this base, and was not imported.
Its shared fade/music/PLC changes are separate from this local results branch.

The read-only main disassembly `docs/s2disasm/s2.asm` identifies:

- `Obj6F_TallyScore` ($14580, lines 28376–28420): after all countdowns are
  already zero, the non-perfect path checks `Player_mode != 2` (Tails alone),
  `Got_Emerald != 0`, and byte `Emerald_count == 7`; it writes routine $30
  directly, without the $78 wait. The earlier perfect flag branch instead
  selects $24/$5A and is not represented by this results owner's inputs.
- `Obj6F_InitAndMoveSuperMsg` ($146A6): move main/heading to their sources
  at $20 pixels/pass; once main is already at source, replace their mapping
  frames with $1B/$1A and subtract 8 from their Y coordinates. Allocate the
  later Super Sonic slot at hardware X=0, Y=$B4, target X=$120, frame $1C.
  The later heading and allocated slot execute their $14 movement on this
  same pass; the main only changes to $34 and moves on its next dispatch.
- `Obj6F_MoveAndDisplay` ($14736) uses `Obj34_MoveTowardsTargetPosition`
  ($13E1C), speed $10. Arrival is tested before movement; the following pass
  latches $B4 and routine $20. `Obj6F_TimedDisplay` ($14572) predecrements
  that word; routine $22/DisplayOnly executes on the next pass.

Direct bytes in the verified ROM corroborate the branch and literals:
$146A6 begins `11 7C 00 32 00 64 30 28 00 08 B0 68 00 32`; the replacement
writes $14/$1A/$34/$1B and child $120/$B4/$14/$1C. $14736 begins
`30 28 00 08 B0 68 00 30 66 00 F6 DC 31 7C 00 B4 00 1E 11 7C 00 20 00 24`.
The resulting native duration is 9 leave passes + 1 replacement pass + 18
main return passes + 1 arrival latch + 180 countdown passes + 1 DisplayOnly
pass = 210. This derives from object dispatch order, not cursor 101691.

The fix stays inside `SpecialStageResultsScreenObjectInstance`: primitive
stock pose phases, direct exhausted-tally branch, exact equality and existing
`ActiveGameplayTeamResolver` Tails-alone gate. KiS2's split-name layout and
predicate remain unchanged. Stock emerald children continue displaying and
mapping/art bytes still come through the existing ROM render pipeline.
Movement routines suppress DisplaySprite when hardware X > $200; the new
main visibility latch preserves this on leave/return, including equality at
$200, while the replacement routine deliberately displays at hardware X=$240.
The actual ROM-art command regression exercises native width 320 and width
528. An earlier assertion that native-width clipping alone would hide the
suppressed text was rejected: partial mapping pieces can remain onscreen.

No fitted timer, comparator weakening, trace hydration, KiS2 layout reuse,
shared fade adjustment, or new rewind API was introduced. Results mode is
excluded by `GameLoop.isSpecialStageRewindable()`; this standalone overlay
is not an AbstractObjectInstance and has no existing captured-state contract.
The new primitive phases do not claim live results rewind support. The
existing initialization/slot-order pitfall applies; no new mirrored skill
contract was needed. Missing ROM tests skip; malformed/missing presentation
services are not certified by these tests. Perfect results remain an inherited
unsupported branch, not a flag inferred from the fixture's initial snapshot.

### Commands and measured outcomes

All commands used the shared queue from this worktree. `${S2_ROM}` below is
passed as its verified absolute path, never a generated link. Profile defaults
retain one Surefire fork; no CLI forkCount or heap/priority override was used.

Baseline (session 85424; waited 1604 seconds; Maven 95 seconds):

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2CompleteEmeraldRunChain,TestS2CompleteEmeraldRunPrefix,TestS2SpecialStage7TraceReplay "-Dsonic2.rom.path=${S2_ROM}" test
```

Fresh XML: 3 tests / 1 failure / 0 errors / 0 skips. Prefix and SS7 passed.
Full chain reproduced the inherited 11 axes: SS7 results walk stuck at cursor
101691 plus the ten gap identities in the preceding table. Every reached
segment 0–17 report had zero comparator errors. Earliest gap remains edge 0
`movie_logical_frame` 10308/10268. This is a compared clock discrepancy,
not proof of a forty-gameplay-frame delay; its shared owner remains reserved.

The focused regression command was:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestSonic2SpecialStageSuperResults "-Dsonic2.rom.path=${S2_ROM}" test
```

Session 60545 was unexpectedly cancelled while WAITING (exit 130, hold zero,
2447.2 seconds); no test ran and this is not RED evidence. No worker cancellation
was sent. Root's read-only inspection found only external SIGINT/SIGTERM paths
could produce the wrapper message; the sender is unobserved. Exact retry 9560
waited 5865 seconds, then yielded 5 tests / 0 failures / 5 setup errors / 0 skips:
clearing the fixture session removed the active WorldSession. Corrected setup
opens `GameModuleRegistry.getCurrent()` explicitly after clearing. RequiresRom's
`TestEnvironment.configureRomFixture` has already detected the S2 module from
this verified ROM. This is the existing active-session measurement hazard,
not a production failure.

Corrected old-production RED 74962 waited 42 seconds: 5 / 3 / 0 / 0. Intended
failures were exhausted tally selecting state 3 instead of 10, completion still
false on post-tally pass 210, and Tails-alone still incomplete after the ordinary
exit. Count-six and GotEmerald-false controls passed. The candidate additionally
covers above-seven equality, exact native pose boundaries, and native/wide draw
visibility; those expanded assertions are candidate coverage, not executed RED.

Focused candidate selector:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestSonic2SpecialStageSuperResults,TestSonic2SpecialStageResultsTallyCadence,TestSonic2SpecialStageResultsPlcReadiness,TestSonic2SpecialStageResultsWidescreenCommands,TestSplitNameResultsMessages "-Dsonic2.rom.path=${S2_ROM}" test
```

Intermediate 38091 passed 19 / 0 / 0 / 0. Expanded render attempt 25376 was
20 / 0 / 1 / 0 because its new render fixture omitted injected RomManager.
After injecting the verified ROM and engine ROM service, final 5539 passed
20 / 0 / 0 / 0 (Maven 24.687 seconds): eight new stock tests, three tally,
three FIFO, two widescreen, four KiS2 split-message tests. No production change
was made to compensate for either fixture setup error.

Matched replay and affected controls, sessions 27967 and final 64247:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS2CompleteEmeraldRunChain,TestS2CompleteEmeraldRunPrefix,TestS2SpecialStage2TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2SpecialStage7TraceReplay,TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay,TestS2Cpz1Seg8CompleteEmeraldsSegmentTraceReplay "-Dsonic2.rom.path=${S2_ROM}" test
```

Both completed 8 / 1 / 0 / 0. Final Maven time 48.168 seconds. All seven
standalone/prefix controls pass; full chain passes the SS7 results return and
now reaches later ARZ segments. Its 14-axis assertion is a newly exposed
frontier, not a full-chain pass or an aggregate-count improvement:

- Segment 18 (`seg12_arz1`) completes with 119 aggregate comparator errors:
  PHYSICS group 102 (including art edges), ANIMATION 17. First row 4213,
  `dynamic_art.edge[2].mapping_frame`, native 61 / engine 65; player mapping
  frame is also $3D/$41. This does not imply 102 coordinate-physics errors.
- Segment 19 (`seg13_arz2`) is incomplete, 47450 errors (PHYSICS 45453,
  ANIMATION 1997), first row 2175 `x_sub`, native $8800 / engine $5800.
  The walk loses production ownership at cursor 110617 in TITLE_CARD,
  level generation 15 / progression zone 2 / ROM zone 15 / act 1.
- Twelve gap axes remain. The previous ten identities/first fields remain
  as in the preceding table; newly reached SS7 → seg12_arz1 edge 0 is
  101663/101626, and seg12_arz1 → seg13_arz2 edge 8 is 106743/106742.
  Both candidate invocations' first-ten failure strings have SHA-256
  `502f2f0349a22a6c7d8c0b97daf96ac80790abff8060c456927b227111d380bb`.
  A fresh baseline digest was not extracted before report overwrite, so
  no stronger whole-string baseline digest comparison is claimed.

Segments 0–17 remain zero. Exact JSON identities below `target/trace-reports/`
are `run-chain/s2-sonic-tails-complete-emeralds_seg18-segment-18-f267fee740938dd4.json`,
`run-chain/s2-sonic-tails-complete-emeralds_seg19-segment-19-54fbcdedc41a859b.json`,
and the existing full-chain gap suffix `63186ce890be658f`. Raw reports stay
workflow-temporary. No downstream ARZ/animation/driver fix was attempted.

Final owner SHA-256:
`0a0206cf29e157014b8d3d25aa94180e1ce6ed2d6a1b176132f9d26589343e8f`;
base owner: `933076d37c21e8befa20e305a07417917d09a736c6bf67fbad8f20dc8532032b`.
Dry change selection `python3 tools/testing/run_categories.py --base
098053c4a01c2af283ca6797463bb5051442ef0b` selects 2500/3028 ordinary classes plus
guards. This worker did not run that broad plan: root owns actual-destination
combined baseline/candidate/integrated qualification and central release,
frontier, discrepancy, and hazard prose. Focused and domain checks above are
partial validation. No owned Maven request remains pending.
Registry checked at completion, 2026-10-08 12:30 UTC, before authoritative
expiry 2026-10-09 09:16:06 UTC. No branch was pushed or integrated by this lane.
