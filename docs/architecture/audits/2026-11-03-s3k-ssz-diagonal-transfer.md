# SSZ diagonal bridge transfers and Tails arrival animation

Baseline: `cff5686b490c0f5b5ef3cf02adf26b59cc55a9e5` (fresh `origin/develop`).
The fixtures named `hpz` are Sky Sanctuary: metadata `zone_id=10`.

## Executed cause

Both first Y disagreements are transfers between permanent `$7B:$80` diagonal
bridges running `loc_44D98`. Tails row2159 moves from slot4 at($A40,$C58)
to slot9 at($AC0,$C38); Sonic row1948 moves from slot10 at($9C0,$C78)
to slot26 at($A40,$C58). Old/new object status changes$88/$80 to$80/$88,
object routine0. Both ROM players are routine2, status$0C (rolling, on-object,
grounded), with zero Y velocity.

`loc_44D98 (sonic3k.asm:90279-90304)` saves the old standing bits before
`SolidObjectTopSloped2`, then clears `Status_InAir` for a previous rider who
is airborne and not jumping. `loc_44EF6 (sonic3k.asm:90420-90448)` repeats
that behavior while collapsing. Java omitted both post-collision branches.

Temporary collision-checkpoint prints proved the execution, rather than just
fitting the displacement. At manager tick1944, Sonic X$A00 leaves the old
bridge with air=true. The next bridge enters with air=true and uncurls him:
radius14→19, Y$C54→$C4A. ROM Y is$C4F. At tick2156, Tails X$A81 likewise
uncurls, radius14→15, Y$C34→$C2E rather than$C2F. Manager ticks are not trace rows.
`RideObject_SetRide (sonic3k.asm:42067-42094)` calls `Player_TouchFloor` only
when clearing an already-set InAir bit, explaining the radius and pose reset.

The correction stays in the diagonal bridge. Its explicit checkpoint runs
between the saved standing-bit read and the re-ground branch. Geometry still
advances before collision during collapse. `loc_44DEC` then tests the newly
written standing bits, choosing P1 before P2 when initiating collapse. No shared
collision predicates or trace comparisons change. No new persistent state is
introduced. FixBugs=0 behavior is retained.

Post-fix prints prove the old bridge's post-checkpoint branch clears air before
the next slot executes. Both next bridges retain rolling and radius14, and
write exactly$C4F/$C2F. All temporary runtime prints are removed.

## Independent animation omission

`Obj_57DCC (sonic3k.asm:116935-116964)` writes1 to `$2E(a1)` when its delay
expires. `$2E` is `object_control` (sonic3k.constants.asm:57). Java set the roll
animation and position but omitted that write, leaving the dormant$83 and its
mapping lock. `loc_138C8 (sonic3k.asm:26291-26304)` skips Animate_Tails when
control bit1 is set. The helper now selects native control1 and releases the
mapping lock at that same boundary; movement remains scripted. The row121
mapping-frame disagreement disappears without changing the animator.

## Validation policy

Full `trace-replay`, `trace-replay-r7`, and `trace-segments` profiles run both
before and after because this task explicitly requires them for object changes.
All runs use Java25, Lua5.4, surfaceless EGL, no desktop display, and all six
absolute ROM properties from the lane brief. No full ordinary/release suite is
requested or claimed. Reports are fresh for each full-profile invocation.

Negative control on unchanged gameplay: five cases, five failures, no errors or
skips. The arrival mapping-lock assertion and both Sonic/Tails viewport transfer
assertions discriminate the omissions. The initial Tails-only route witnesses
were not reached and are not claimed as collision discrimination. The final
component regression uses a bounded checkpoint encounter and the ordinary
roll-entry caller's feet-preserving Y adjustment instead of relying on cold
campaign timing. Intermediate test-arrangement failures are not runtime regressions.

Native/ROM coverage remains bounded to the compared routes and focused checks.
Later SSZ frontiers and inherited viewport/donor/full-act matrix gaps remain open.

## Focused results

- Required S3K keep-green set:60passed, zero failures/errors/skips. Both classes
  named `TestSonic3kLevelLoading` were selected (36+7 tests).
- Arrival26, traversal9 and existing cold routes2: all passed, zero skips.
- Existing collapse presentation4 and compatibility matrix20: all passed, zero skips.
- Final bounded diagonal transfers4: all passed, zero skips; both native teams,
  320/800, every-key restore/forward replay, and actual-jump counterexample.

The first combined focused run was101tests,99passed and two Tails-only new-test
witness failures. Subsequent bounded-test arrangement iterations are separated
from runtime findings above; acceptance uses the final four-case run. No runtime
source changed after the first candidate trace measurement. Temporary probes
were removed before the focused checks and full after-profile campaign.

## Exact commands

Environment for every Maven invocation:

```bash
unset DISPLAY WAYLAND_DISPLAY JDK_JAVA_OPTIONS MAVEN_ARGS LD_LIBRARY_PATH
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
export JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl
export PATH="$HOME/.local/bin:$PATH"
ROM_FLAGS=(
  -Dsonic1.rom.path=$HOME/code/projects/OpenGGF/s1.gen
  -Dsonic2.rom.path=$HOME/code/projects/OpenGGF/s2.gen
  -Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen
  -Dkis2.rom.path=$HOME/code/projects/OpenGGF/kis2.gen
  -Dsonic1.rev00.rom.path=$HOME/code/projects/OpenGGF/s1-r0.gen
  -Dsonic2.rev00.rom.path=$HOME/code/projects/OpenGGF/s2-r0.gen
)
```

The S3&K SHA-1 was independently checked:
`cfbf98c36c776677290a872547ac47c53d2761d6`.

Full-profile commands, each run once before and once after:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-segments -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical "${ROM_FLAGS[@]}" test
```

The after commands additionally set `-Dtrace.context.radius=220` for the
first-added-error attribution below. That changes report context only. Fresh
`target/surefire-reports` and `target/trace-reports` were used for each full
profile. The narrow bridge check ran between the first and second after profiles;
its known own XML was consumed and retired before r7 execution.

Initial before and candidate target measurements (candidate still had the
non-behavioral collision prints):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical '-Dtest=TestS3kTailsFullChainHpzSegmentTraceReplay,TestS3kSonicTailsHpzSegmentTraceReplay' "${ROM_FLAGS[@]}" test
```

The candidate focused run retained unrelated prior report files. Its two target
report ownership keys were explicitly selected, not counted together with those
stale files; acceptance uses the fresh full-profile reports.

Deliberate negative control, before applying the runtime correction:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B '-Dtest=TestS3kSszDiagonalTransfers,TestS3kSszArrivalHeadless#theSidekickParksOffScreenUntilTheArrivalHelperReleasesHer' "${ROM_FLAGS[@]}" test
```

Focused affected and required coverage:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B '-Dtest=TestS3kSszDiagonalTransfers,TestS3kSszArrivalHeadless,TestS3kSszTraversalPlatforms,TestS3kSszColdRoutes,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B '-Dtest=TestS3kSszDiagonalTransfers,TestSszBridgeCollapsePresentation,TestS3kSszCompatibilityMatrix' "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B '-Dtest=TestS3kSszDiagonalTransfers' "${ROM_FLAGS[@]}" test
```

The last selector was used during bounded-encounter arrangement and then for the
final successful four-case check. No completed unaffected coverage was repeated.
These ROM-backed encounter tests retain normal queue admission, not lean.

```bash
python3 tools/disasm/disasm_citations.py check --strict src/main/java/com/openggf/game/sonic3k/objects/SszCollapsingBridgeDiagonalObjectInstance.java src/main/java/com/openggf/game/sonic3k/objects/SszTailsArrivalHelperObjectInstance.java docs/architecture/audits/2026-11-03-s3k-ssz-diagonal-transfer.md
git diff --check
```

Both checks pass; strict citation result:6ok. Queue waits, execution and total
wall time were not aggregated into a delivery duration. Queue delay was reported
separately while requests remained live; concurrent waits are not additive.
## Full r7 per-class results

| Class | Errors before → after | First physics before → after | First animation before → after |
| --- | ---: | --- | --- |
| `TestS3kSonicTailsHpz222SegmentTraceReplay` | 1758 → 1758 | 0 `rings` → 0 `rings` | 889 `tails_animation_id` → 889 `tails_animation_id` |
| `TestS3kSonicTailsHpz22SegmentTraceReplay` | 489 → 489 | 0 `y_speed` → 0 `y_speed` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kSonicTailsHpz2SegmentTraceReplay` | 1103 → 1103 | 0 `rings` → 0 `rings` | 619 `player_animation_id` → 619 `player_animation_id` |
| `TestS3kSonicTailsHpz3SegmentTraceReplay` | 283 → 283 | 0 `rings` → 0 `rings` | 985 `player_animation_id` → 985 `player_animation_id` |
| `TestS3kSonicTailsHpzSegmentTraceReplay` | 1808 → 2201 | 1948 `y` → 2056 `tails_y` | 121 `tails_mapping_frame` → 2253 `player_animation_id` |
| `TestS3kTailsFullChainHpz222SegmentTraceReplay` | 237 → 237 | 0 `camera_x` → 0 `camera_x` | 482 `player_animation_id` → 482 `player_animation_id` |
| `TestS3kTailsFullChainHpz22SegmentTraceReplay` | 709 → 709 | 0 `y_speed` → 0 `y_speed` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kTailsFullChainHpz2SegmentTraceReplay` | 932 → 932 | 0 `x` → 0 `x` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kTailsFullChainHpzSegmentTraceReplay` | 740 → 729 | 2159 `y` → 2451 `y_speed` | 2159 `player_animation_id` → 2451 `player_animation_id` |

## Error-list gate and higher Sonic/Tails total

The table above is the full r7 result, measured from fresh reports with zero
skips. Names `hpz`/`hpz2`/`hpz3` carry zone10; the conservatively retained
`hpz22`/`hpz222` neighbors carry other internal zones. The r6 campaign has no
changed report JSON whatsoever. R7 changes exactly the two target JSON reports;
all other report JSON and non-target failure/error identities are identical.

Report `error_count` counts compressed error spans, not individual field/row
pairs. Sonic/Tails rises1808→2201 spans while mismatching field/row pairs fall
137906→121548. Tails falls740→729 spans,43467→37028 field/row pairs. These
secondary counts explain the span fragmentation; they do not replace or relax
any comparison or the required error-count reporting.

Expanding every error span to `(row,field)` for the gate:

| Route | First differing row | First added row/fields | Removed / added field-row pairs |
| --- | --- | --- | ---: |
| Sonic/Tails |121: removes `tails_mapping_frame` |2253: adds `player_animation_id`, `rolling` |28984 /12626 |
| Tails |2159: removes `y`, `camera_y`, `rolling`, `player_animation_id`, `status_byte` |2451: adds `player_animation_id`, `rolling` |8251 /1812 |

There are no additions before those first-added rows. They are beyond existing
baseline physics divergence (1948/2159), not additions in a previously matching
baseline prefix. The Sonic/Tails baseline is still wrong in Y and Y speed at2253
(the Y span began2188); the Tails baseline already has a Y-speed mismatch at2451.
The corrected bridge preserves rolling, but the later unresolved column landing
fails to clear it. The old incorrect early uncurl had masked those later pose
errors. This attributes the extra pose spans without treating the later column
as fixed. No class newly fails or has an earlier first error.

At the first-added rows, exact completed candidate context values (ROM / engine):

| Row / route | Player `(x,y)` | `(x_speed,y_speed,g_speed)` | Air / roll / status | Sidekick `(x,y)` and velocity | Camera `(x,y)` |
| --- | --- | --- | --- | --- | --- |
|2253 Sonic/Tails |`($1161,$A3C)` / `($1161,$A3F)` |`(-$C5,0,-$C5)` / `(-$C5,$300,-$1BA)` |`0/0/$08` / `1/1/$06` |both `($1170,$A51)`, velocity`(0,0)`, air1, roll0, status3 |`($10D1,$9E1)` / `($10D1,$9E7)` |
|2451 Tails |both `($1164,$A40)` |`(-$C4,0,-$C4)` / `(-$C4,$370,-$1A8)` |`0/0/$09` / `1/1/$07` |absent in both |`($10D4,$9E0)` / `($10D4,$9E2)` |

Both ROM players land on the column at`($1158,$A71)`, object routine1,
`loc_44B30`, status$08 (Sonic/Tails slot8, Tails slot9). Its phase/height owner is
not diagnosed by this change. The Sonic/Tails first physics error after the fix
is an unchanged isolated P2 Y disagreement at2056: ROM`-$7`, engine`$FF9`.
The same pair exists in baseline; it is not suppressed or demoted. P1 otherwise
matches through2252 and first disagrees at the column landing2253. Tails matches
through2450. These are later unresolved frontiers, not trace passes.

Inherited non-comparison errors retain their exact test identity and message:

- r6: `TestS3kSonicTailsCompleteEmeraldRunChain.aiz1ToDoomsdayCollectingEverySevenEmeralds`
  lacks recorder-observed `expected_movie_end_mode`.
- r7: `TestS3kKnucklesSuperEmeraldRunChain.aiz1ToDoomsdayAcrossEverySpecialAndBonusStage`
  and `TestS3kTailsFullChainRunChain.tailsFullChainAllEmeraldsRoundTrip` lack the
  same campaign-end provenance. `TestS3kTailsFullChainFbz2SegmentTraceReplay.replayMatchesTrace`
  has the existing KosM FIFO-capacity error. Eight
  `TestS3kTailsFullChainSs{8,9,10,11,12,13,14,15}SpecialStageTraceReplay.replayProducesFaithfulReport`
  cases retain unmatched hardware-completion closure errors.
- Segments baseline: `TestS3kSonicTailsAiz2SegmentTraceReplay.replayMatchesTrace`
  has the rewind-reference closure error at row996. Seven
  `TestS3kSonicTailsSs{8,9,10,11,12,13,14}SpecialStageTraceReplay.replayProducesFaithfulReport`
  cases have unmatched hardware-completion closure errors.

The seven r6 skips retain their identities:
`TestTraceBaselineRegression.knownRedTraceFrontiersDoNotRegress`,
`TestTraceDataAuxSchemaPerformance.measuresPostLoadMissingSchemaQueriesOnLargestFixture`,
`TestS3kBonusRoundTripChain.gumballRoundTrip`,
`TestS3kBonusRoundTripChain.pachinkoRoundTrip`,
`TestTraceRunActivePayloadPerformance.allSegmentsStayWithinFixedDescriptorAndInstalledCaps`,
`TestTraceRunActivePayloadPerformance.installedRootModelIncludesTheRealPreparedLaunchGraph`,
and `TestTraceRunDescriptorPlanningPerformance.compactDescriptorsRetainLessHeapWithoutOwningEagerPayloads`.
These are explicit optional/diagnostic/benchmark skips, not silently missing ROMs.

## Completed full-profile results

| Profile | Tests | Failures before → after | Errors before → after | Skips before → after |
| --- | ---: | ---: | ---: | ---: |
| `trace-replay` |886 |10→10 |1→1 |7→7 |
| `trace-replay-r7` |117 |84→84 |11→11 |0→0 |
| `trace-segments` |70 |47→47 |8→8 |0→0 |

All three commands complete with exit1 because their inherited comparison-red
and error cases remain. Exact full JSON comparison finds zero changed r6 reports,
exactly the two target reports changed in r7, and exactly the Sonic/Tails target
report changed in segments. All other report JSON is identical, not merely equal
in error totals. Non-target JUnit result identities/messages and all skips match.
The segment profile reproduces every Sonic/Tails row of the r7 table above;
its target report JSON is identical to r7. Target compared-row counts are
unchanged:7474 Sonic/Tails and5865 Tails. No profile is claimed green.

No earlier first error, newly failing trace class, or added comparison in the
previously agreeing baseline prefix was found. The increased Sonic/Tails span
total is explicitly attributed above. Later column landing behavior remains
unresolved. This is not full-game, complete-campaign or public-release certification.

## Proposed frontier-log entry

2026-11-03 — SSZ diagonal bridge transfers: at baseline`cff5686b4`, prove both
`hpz` zone10 Y frontiers are the omitted post-solid re-grounding branch in
`loc_44D98`/`loc_44EF6`. Preserve the native previous-standing/non-jumping
predicate in the object checkpoint, preventing `RideObject_SetRide` from
uncurling a walking-off rider. Tails740→729 spans, first2159`y`→2451`y_speed`;
Sonic/Tails1808→2201 spans, physics1948`y`→2056`tails_y` (unchanged isolated
P2 wrap disagreement), P1 column frontier2253. Fix the independent arrival helper's
control1 write so animation row121 disappears. Higher Sonic/Tails total is
attributed by full error-list diff: first additions2253 animation/rolling, beyond
existing baseline physics divergence; no earlier/newly-red class. Full r6/r7/segment
profiles before/after preserve all other JSON and inherited result identities.
Required S3K60pass; local arrival/traversal/cold-route37pass, collapse/compatibility24pass,
and final team/viewport transfer/rewind/jump4pass, zero focused skips. See this
audit for exact commands, negative-control limits and first-added-row state values.
