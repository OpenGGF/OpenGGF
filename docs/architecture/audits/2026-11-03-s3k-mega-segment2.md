# Mega returned-AIZ segment 2: object landing and bonus-entry restart

Base: `e6a8b4a34e4c2962c2e10fc65b119eecb77150d4` (`origin/develop` at lane start).
Branch: `bugfix/ai-trace-mega-segment2`. The lead owns integration and the frontier log.

## Executed causes and ROM ownership

The fresh `TestS3kMegaRunChain.multiBonusSpecialStageRoundTrip` baseline reproduces
112 errors in returned AIZ segment 2: 111 animation errors beginning at row186,
plus camera Y at row2002. The player is Knuckles; no sidekick is present.
At186 the ROM changes animation `$20` (glide) to `$00` (walk). Position,
velocity, ground speed, status and camera agree; the mapping remains `$C0`
on that object-landing row.

A temporary read-only print in the ordinary object-landing owner observes
`FloatingPlatformObjectInstance`, player centre `$2A24,$0252`, saved glide
state1, raw animation32, and engine V-int7111 after the landing reset.
The engine has cleared airborne/ability ownership but omitted the raw animation
store. `RideObject_SetRide (sonic3k.asm:42067-42085)` calls Player_TouchFloor
for an airborne landing; `Player_TouchFloor (sonic3k.asm:24375-24379)` dispatches
Knuckles to his own routine. Its `loc_17B6A (sonic3k.asm:32894-32907)` tail
resets any unsigned animation at or above `$20` to walk, even without rolling.
The shared ordinary object landing now performs that store for glide-capable
players, preserving the mapping already produced by the earlier player pass.
Explicit ride-only captures retain their existing opt-out.

On segment2 row2002, an accepted bonus request is already pending after objects,
but the transition predicate is false. The camera probe records X593/Y604
before scrolling and X593/Y603 afterwards: `$0251,$025C` → `$0251,$025B`.
ROM player centre is `$02E6,$02A0`, velocities `$005C,-$04B0`, with no sidekick.
`loc_2D4CA (sonic3k.asm:61959-61973)` writes Restart_level_flag for bonus entry;
`LevelLoop (sonic3k.asm:7926-7929)` branches before DeformBgLayer.
A pending accepted bonus request now makes the existing level-inactive predicate
true, so the existing restart guards hold the camera and skip source-level
screen events. Consuming the request releases only that derived condition;
an independent transition remains inactive.

The same camera probe observes the first bonus entry: Y498→494 at segment0
row4545. Both bugs are branch/state omissions, independent of global-clock
phase. No V-int accounting, timing admission, trace hydration, comparator,
cursor or fixture change is included. The held synchronous-load deficit remains
outside this lane.

## Validation

All measurements use Java25, Lua5.4, no desktop display, surfaceless EGL and
all six absolute ROM properties from the lane brief; the generic S1 property
is also passed to the full profiles. S1/S2/S3&K SHA-1 identities match root guidance.

Before applying corrections, the two focused regression classes execute17 tests:
12 pass and5 fail as intended (four raw animation resets and bonus restart).
The animation cases below `$20` pass, distinguishing the native threshold.

Baseline profiles: r6 886 tests/10 failures/1 error/7 skips; r7 117/84/11/0;
segments70/46/8/0. R6 skips are five explicit opt-in performance/audit checks
and two absent optional bonus round-trip recordings, not missing ROMs.

The baseline collection shell was edited while its already-parsed loop ran;
its trailing read consequently reported an EOF parse error after all three
Maven invocations and report copies completed. Independently summing the copied
XML confirms the counts above. The wrapper passes `bash -n` before reuse.
This collection error did not abort or narrow any Maven selection.

Baseline Maven execution: r6 12m00s, r7 6m05s, segments4m30s. Queue waits are
separate from execution; total delivery wall time is not measured.

The fixed focused command passes109 tests, zero failures/errors/skips, including
all four required keep-green selectors (both LevelLoading classes), ordinary
object contacts and the Knuckles glide-collision contract.

Deliberately reverting only the object-landing store while keeping the camera
correction restores111 segment2 animation errors and zero physics errors.
Segment0 is already zero; Gumball stays16 and Slots stays7891. Reapplying the
store before the final three-profile run supplies the discriminating control
without any comparison relaxation. The clock-dependent Slots frontier remains
row268 y_speed; it is outside this correction and the load-time ruling stays held.

Temporary execution prints are removed before the negative/fixed focused runs.
A shared read-only live-comparison observer, identical on both profile sides,
records every error tuple and the associated compared fields for the gate.
It is removed before delivery; it never feeds gameplay or changes severity.

## Commands

```bash
unset DISPLAY WAYLAND_DISPLAY JDK_JAVA_OPTIONS MAVEN_ARGS LD_LIBRARY_PATH
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
export JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl
export PATH="$HOME/.local/bin:$PATH"
R="$HOME/code/projects/OpenGGF"
rom_flags=("-Dsonic1.rom.path=$R/s1.gen" "-Dsonic2.rom.path=$R/s2.gen"
  "-Ds3k.rom.path=$R/s3k.gen" "-Dkis2.rom.path=$R/kis2.gen"
  "-Dsonic1.rev00.rom.path=$R/s1-r0.gen" "-Dsonic2.rev00.rom.path=$R/s2-r0.gen")
# Every complete profile, once before and once after:
for profile in trace-replay trace-replay-r7 trace-segments; do
  python3 tools/testing/maven_queue.py -Dmse=off -B "-P$profile" \
    -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
    "${rom_flags[@]}" "-Dsonic.rom.path=$R/s1.gen" test
done
# New regressions on the old implementation (five expected failures):
python3 tools/testing/maven_queue.py --normal -Dmse=off -B \
  '-Dtest=TestObjectSolidContactController,TestLevelTransitionCoordinator' \
  "${rom_flags[@]}" test
# Fixed focused regressions and keep-green set:
python3 tools/testing/maven_queue.py --normal -Dmse=off -B \
  '-Dtest=TestObjectSolidContactController,TestLevelTransitionCoordinator,TestKnucklesGlideCollisionContract,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "${rom_flags[@]}" test
# Initial Mega baseline; also the deliberate landing-store revert control
# (the latter additionally used --normal):
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS3kMegaRunChain "${rom_flags[@]}" test
```

Generated Surefire/trace reports are cleared before each profile measurement.
All temporary report snapshots and observer logs are consumed and deleted before
delivery. No pass cache or validation receipt is added.

## Mega axes and limits

The baseline Surefire assertion contains two axes: `[segment-physics]` for
segment2's112 errors, and `[walk-failure]` because the Slots interior
`stage_exit` is never observed within its boundary window. Segment snapshots
also retain the inherited Gumball/Slots errors. The target is the compared
returned-AIZ physics/animation axis, not certification of the entire movie.
The live report's inherited `complete=false` status is distinct from its error
count; no completion predicate is relaxed by this change.

## Independent movie witness

The complete release-6 comparison also improves KiS2's EHZ2 segment13 in
`TestKis2CompleteEmeraldRunChain`:1068→1055 errors. Exact tuple diff removes13
and adds0. The first removal is row1962 player_animation_id (ROM0/engine`$20`);
the first remaining error is row2275 camera_x (ROM`$2AB0`/engine`$2AAF`).
The first non-camera physics frontier moves1963 dynamic_art.edges→2578
queue.s2_nemesis_plc.busy. Four animation errors and nine matching DPLC ledger
errors disappear, without changing that ledger or its timing authority.
This independently corroborates the native object-landing store in another BK2.
The class remains red; its earlier segment2 rings frontier at826 is unchanged.
Every other release-6 report is identical, and every class outcome is unchanged.

The candidate Mega report has zero segment2 errors in both verification groups.
Its segment-physics failure disappears; Surefire now reports only the unchanged
missing Slots `stage_exit` assertion. Segment0's camera error also disappears.

| Mega segment | Before errors / first error | Candidate |
| --- | --- | --- |
| 0, opening AIZ | 1 /4545 camera_y | 0 |
| 1, Gumball | 16 /1300 x | unchanged |
| 2, returned AIZ | 112 /186 player_animation_id; camera_y at2002 | 0 |
| 3, Slots | 7891 /268 y_speed | unchanged |

The stage-exit failure and remaining Gumball/Slots reports prevent a whole-chain
pass. No V-int deficit is compensated, and this does not qualify the later
unvisited campaign segments.

## Full-profile gate and the higher HPZ22 count

| Profile | Tests | Failures before→after | Errors before→after | Skips before→after |
| --- | ---: | --- | --- | --- |
| trace-replay | 886 | 10→10 | 1→1 | 7→7 |
| trace-replay-r7 | 117 | 84→84 | 11→11 | 0→0 |
| trace-segments | 70 | 46→46 | 8→8 | 0→0 |

No test newly goes red and no first error moves earlier. Exact live-comparison
multiset diff is203656→203530 error tuples:126 removed, zero added. The first
removal is opening-AIZ row4545 camera_y; the other removals are returned-AIZ112
and KiS2 EHZ2 thirteen. Every report/message outside the described Mega, KiS2
and HPZ22 changes is identical.

`TestS3kSonicTailsHpz22SegmentTraceReplay` has a higher grouped report count in
both r7 and segments:489→493, first error unchanged at row0 y_speed
(ROM`$0038`/engine0). This is explicitly attributed, not suppressed or rebased.
A temporary knockout of only the new restart predicate reproduces the original
489 report byte-for-byte. The accepted SLOT_MACHINE request appears at row1980
on an already-diverged route; the standalone driver leaves it unconsumed.
The old predicate lets source gameplay continue. The corrected predicate
makes that source inactive, as Restart_level_flag requires, and the existing
RecordingFrameDriver gate holds it instead. Why this standalone route differs
from the recording before then is outside this fix.

The full matched per-frame comparison captures1991 rows on each side:
18408→18412 ERROR tuples, removing12 and adding16. All changed tuples occur
at1981,2004,2106; no added tuple is on a previously clean row. The first differing
and first added row is1981, adding y/y_sub/y_speed errors and changing already
erroneous camera/sidekick values. The first removed tuple is also1981 (camera_x).
The old row already differs in player X, camera X, sidekick state and rings:

| Row1981 state | ROM | Old engine | Candidate engine |
| --- | --- | --- | --- |
| Player X/Y | `$09B0/$032D` | `$09AE/$032D` | `$09AE/$032C` |
| Player X/Y speed | `$0000/$00D0` | `$0000/$00D0` | `$0000/$0098` |
| Player Y fraction | `$5800` | `$5800` | `$C000` |
| Sidekick present | true | true | true |
| Sidekick X/Y | `$099F/$033E` | `$09AE/$0327` | `$09AE/$0326` |
| Sidekick X/Y speed | `$0000/-$02B0` | `$0000/$0000` | `$0000/$0000` |
| Camera X/Y | `$098F/$0320` | `$098D/$0320` | `$098B/$0320` |
| Rings (decimal) | 261 | 27 | 27 |

At2004 and2106 the baseline also already differs in player position, rings and
sidekick presence. The new camera_x error at2004 is therefore after the existing
route divergence, not a new clean-state failure. No unrelated shrine/bootstrap
correction is attempted. The lane's higher-count attribution gate is satisfied;
no green shrine or complete-campaign qualification is claimed.

## Chain/prefix matrix

Counts are tests/failures/errors/skips, identical before and after. Full profiles
include every RunChain/RunPrefix class, plus these round-trip witnesses.

| Class | Before = after |
| --- | --- |
| TestKis2CompleteEmeraldRunChain | 2/1/0/0 |
| TestS1CompleteEmeraldRunChain | 1/1/0/0 |
| TestS1CompleteEmeraldRunPrefix | 2/0/0/0 |
| TestS1GhzMazeRoundTripChain | 2/0/0/0 |
| TestS2CompleteEmeraldRunChain | 1/1/0/0 |
| TestS2CompleteEmeraldRunPrefix | 1/0/0/0 |
| TestS2EhzHalfpipeRoundTripChain | 2/1/0/0 |
| TestS3kBonusRoundTripChain | 2/0/0/2 |
| TestS3kSonicTailsCompleteEmeraldRunChain | 1/0/1/0 |
| TestS3kSonicTailsCompleteEmeraldRunPrefix | 3/0/0/0 |
| TestS3kKnucklesSuperEmeraldRunChain | 2/0/1/0 |
| TestS3kMegaRunChain | 1/1/0/0 |
| TestS3kTailsFullChainRunChain | 1/0/1/0 |
| TestS3kTailsFullChainRunPrefix | 3/0/0/0 |

The final clean-source consumer/regression selection passes62 tests with zero
failures/errors/skips (17 overlap the original109 focused cases). It covers
transition peeks, bonus state restoration, canonical frame boundaries, recording
input/hardware/art, controlled play, live rewind admission and both new regressions.
The original109 already cover the full requested S3K keep-green set. This is
focused plus full trace-profile validation, not a full ordinary-suite pass.

Additional exact commands (same environment/ROM array as above):

```bash
# Matched standalone control: temporary test-only full FrameComparison observer;
# the old predicate property temporarily bypasses only the new derived condition.
python3 tools/testing/maven_queue.py --normal -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS3kSonicTailsHpz22SegmentTraceReplay \
  -Dopenggf.debug.bonusExitAttribution=true "${rom_flags[@]}" test
# Repeat that exact command with -Dopenggf.debug.oldBonusExitPredicate=true.
# First bounded capture sampled four rows; final capture observes every compared row.
# Both temporary properties and their implementations are removed afterwards.
python3 tools/testing/maven_queue.py --normal -Dmse=off -B \
  '-Dtest=TestLevelTransitionCoordinatorPeeks,TestBonusStageTransitionCoordinator,TestLevelFrameHardwareTimingBoundaries,TestRecordingFrameDriverInputOnly,TestRecordingFrameDriverHardwareTiming,TestRecordingFrameDriverDynamicArt,TestControlledFrameRuntime,TestLiveRewindStepperAdmission,TestObjectSolidContactController,TestLevelTransitionCoordinator' \
  "${rom_flags[@]}" test
# Clean-source Mega after observer removal (also --normal):
python3 tools/testing/maven_queue.py --normal -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS3kMegaRunChain "${rom_flags[@]}" test
python3 tools/disasm/disasm_citations.py check --strict \
  docs/architecture/audits/2026-11-03-s3k-mega-segment2.md
```

| Profile | Before execution / queue wait | After execution / queue wait |
| --- | --- | --- |
| trace-replay | 12m00s /274s | 11m54s /311s |
| trace-replay-r7 | 6m05s /171s | 6m07s /156s |
| trace-segments | 4m30s /38s | 4m34s /30s |

These intervals include Maven preparation and diagnostic observation. They overlap
other work; their sum is not elapsed delivery time or a performance benchmark.

## Frontier-log text for the integrating lead

2026-11-03 — Mega returned-AIZ segment2, base e6a8b4a34:
112 errors (111 animation, camera_y at2002), first186 player_animation_id →0.
Knuckles lands on FloatingPlatform at `$2A24,$0252`; ordinary object landing
omitted Knux_TouchFloor's unsigned anim≥`$20` walk reset. Bonus entry also
missed LevelLoop's restart-before-camera branch; camera `$025C→$025B` now holds.
Opening segment0 camera error1→0. Gumball16 and Slots7891/row268 y_speed remain;
Mega now fails only the existing missing Slots stage_exit. No clock pricing or
compensation for the held53-V-int load deficit. Negative landing-store control
restores111 animation errors. KiS2 EHZ2seg13 improves1068→1055, first1962
animation→2275 camera; live tuple gate removes126/adds0. HPZ22 standalone
489→493 grouped errors, first0 unchanged: matched old-predicate control reproduces
489; corrected restart freezes an unconsumed Slots request after existing
route divergence. Full1991-row tuple diff removes12/adds16 only at1981/2004/2106,
none on a previously clean row; see audit state table. Full before/after
r6:886/10F/1E/7S; r7:117/84F/11E/0S; segments:70/46F/8E/0S.
Focused109 and clean consumer62 pass,0 skips (17 overlap). No class newly red;
no earlier frontier. Lead owns integration and the frontier log.

Final source check after removing all instrumentation repeats Mega with one
expected inherited failure, zero errors/skips. All four segment report JSONs
are identical to the completed candidate profile, including seg0/seg2 zero.
The only assertion is the missing Slots interior exit; no segment-physics axis
remains. New audit citations pass strict checking; staged whitespace checks pass.
