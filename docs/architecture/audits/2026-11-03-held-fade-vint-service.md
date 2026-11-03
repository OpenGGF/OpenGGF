# Held fade V-int service: step 1 qualification

Base: `2698596fcc5165594406f0ba00c92393658c06a3`, current origin/develop at
qualification start. The lane retains its accepted findings commits 347b695dc
and 6f09632e0 and reconciles this updated base without touching the main
workspace. Scope is step 1 of the
[load accounting design](../designs/2026-11-03-synchronous-load-vint-accounting.md).
The synchronous-load ledger and pricing proposals remain on hold.

## Change and discriminating check

`LevelIterationAdmissionController.consumeTransitionFreezeRow` samples movie
row ownership before service. It services the existing physical VBlank and
advances the live object clock regardless of comparator exhaustion. Only
`onLevelFrameAdvanced` remains conditional on that original ownership decision.
There is no new wait count, destination-derived delay, cursor movement,
comparison adjustment or replay-state synchronization.

`Pal_FadeToBlack (sonic3k.asm:5074-5085)` has22 explicit VSync waits. The outer
fade state machine already presents those steps; its last completion step
loads the destination and counts its interrupt on the destination path. The 21
held steps are an observed consequence, not an added budget. S1 and S2 use the
same GameLoop controller for their held-transition path; their independent
special-stage / return / terminal owners remain unchanged.

The prepared-work regression exercises both observer states using a tiny
ROM-backed KosM job and a real timing port. With the old guard, the exhausted
case fails readiness; the owned case and other existing checks pass (5 tests,
1 failure, no errors/skips). With the change, hardware readiness, exactly one
object-clock tick and unchanged conditional movie advancement all pass.
The combined focused checks pass 95 tests with no failures/errors/skips,
including both admission-controller test classes, timing authority and the four
required S3K loading/bootstrap/decoding selections.

An initial lean attempt exhausted the 1 GiB compiler heap before executing
JUnit. The negative control and candidate checks used the normal memory lane;
the compiler OOM is not a test failure or a valid negative control.

## Measured witness and full-profile gate

The focused Mega witness changes cumulative deficits from 32/54/95 to11/33/53.
The existing AIZ errors remain 112 (111 animation, one camera Y). Slots' first
non-camera physics mismatch moves172→268 (Y speed); its error total changes
7723→7891. The walk reaches a missed stage-exit boundary instead of the early
TITLE_CARD ownership rejection. These are still red axes, not a green chain.
The full-profile comparison and increased-error attribution below pass the
lane gate; the profiles retain their measured pre-existing red frontiers.


### Per-load clock witness

Both columns were remeasured: baseline on pinned 2698596fc and candidate on
its reconciled runtime plus this change. Counters are decimal. Admission
values precede destination row 0, and initial equality is seeded bootstrap,
not a measurement of native pre-trace load cost.

| Matched semantic boundary | Native | Before | After | Deficit before→after |
| --- | ---: | ---: | ---: | --- |
| Initial AIZ, before row 0 | 876 | 876 | 876 | 0→0 |
| Gumball restart requested, AIZ row 4545 | 5422 | 5422 | 5422 | 0→0 |
| Gumball admitted | 5529 | 5497 | 5518 | 32→11 |
| Return requested, Gumball row 1277 | 6807 | 6775 | 6796 | 32→11 |
| Returned AIZ admitted | 6957 | 6903 | 6924 | 54→33 |
| AIZ1→AIZ2, returned AIZ row 1629 | 8587 | 8533 | 8554 | 54→33 |
| Slots restart requested, returned AIZ row 2002 | 8960 | 8906 | 8927 | 54→33 |
| Slots admitted | 9097 | 9002 | 9044 | 95→53 |

New losses per load change32→11,22→22,41→20. Exactly42 V-ints are restored;
the 53 residual V-ints are left unpriced, as authorized. No further load-time
work or clock correction was implemented.

### Completed r6 comparison

Before and after each execute 886 tests:868 pass,10 fail,1 error,7 skips.
The normalized per-test statuses/messages and all report JSONs are identical;
the read-only live-comparator error tuple multiset is also identical. Thus
S1, S2 and KiS2 campaign/prefix and halfpipe witnesses do not move. Their
synchronous return/ending gap frontiers remain open. This changes physical
fade service, not the ownership of movie rows or existing special-stage
return clock reconciliation. No new/worsened r6 failure is observed.


### Completed r7 comparison and increased-error gate

Before and after each execute 117 tests:22 pass,84 fail,11 errors,0 skips.
Exactly one test message and one report JSON change: Mega's walk message and
its segment3 Slots report. Every other completed r7 report and test outcome
is identical. No class newly goes red or moves its first error earlier.

Mega seg0 remains1 error (first 4545 camera_y), seg1 remains16 (first 1300 x),
and seg2 remains112 (first 186 animation). Slots changes7723→7891 errors,
first 172→268 y_speed. Tuple diff removes 7067 and adds 7235 errors;656 tuples
are retained. The first difference removes row 172 y_speed ($0000/$0400).
The first addition is row 268 y_speed ($0000/-$0400). All previously covered
rows 268–960 already had baseline errors; the fixed candidate introduces no
error on a previously clean row in that coverage. It removes 756 errors from
rows 172–267, which are now clean.

At the first added row 268, comparison-only state is:

| State | ROM | Baseline engine | Candidate engine |
| --- | --- | --- | --- |
| Player X/Y | $0460/$0430 | $03DC/$0573 | $0460/$0430 |
| X speed | $0000 | -$0018 | $0000 |
| Y speed | $0000 | $0000 | -$0400 |
| Sidekick present | false | false | false |
| Camera X/Y | $03C0/$03C0 | $033C/$0503 | $03C0/$03C0 |

Thus the new launch error occurs after the baseline's existing row 172 physics
divergence, at a row where the baseline player and camera already disagree.
The candidate's own first physics divergence is later, at 268, not hidden.
The residual53-V-int clock deficit still changes reel/cage behavior.

Of7235 added tuples,6520 are changed errors in previously covered rows and715
are newly reached coverage after the old ownership stop at row 960. The first
newly compared erroneous row is961, after the candidate's own row 268 frontier:
ROM/candidate player X $0572/$0439, Y $0338/$0526, X speed $0000/-$00E6,
Y speed -$0072/$042E; both have no sidekick. Camera X $04D2/$0399 and
Y $02C8/$04B6. The candidate error observations extend through 1151.
This attributes the higher total to a different already-divergent trajectory
and additional comparison coverage; it is not a new early regression or a
rebaselined pass. No runtime correction beyond physical fade service is made.


### Chain/prefix matrix

Counts below are tests/failures/errors/skips. R6 matrices are identical before
and after; r7 matrices are identical in outcome, with Mega's frontier change
qualified above. All suffix RunChain/RunPrefix classes selected by the two
profiles are present, plus the round-trip witnesses.

| Class | Before | After |
| --- | --- | --- |
| TestKis2CompleteEmeraldRunChain | 2/1/0/0 | 2/1/0/0 |
| TestS1CompleteEmeraldRunChain | 1/1/0/0 | 1/1/0/0 |
| TestS1CompleteEmeraldRunPrefix | 2/0/0/0 | 2/0/0/0 |
| TestS1GhzMazeRoundTripChain | 2/0/0/0 | 2/0/0/0 |
| TestS2CompleteEmeraldRunChain | 1/1/0/0 | 1/1/0/0 |
| TestS2CompleteEmeraldRunPrefix | 1/0/0/0 | 1/0/0/0 |
| TestS2EhzHalfpipeRoundTripChain | 2/1/0/0 | 2/1/0/0 |
| TestS3kBonusRoundTripChain | 2/0/0/2 | 2/0/0/2 |
| TestS3kSonicTailsCompleteEmeraldRunChain | 1/0/1/0 | 1/0/1/0 |
| TestS3kSonicTailsCompleteEmeraldRunPrefix | 3/0/0/0 | 3/0/0/0 |
| TestS3kKnucklesSuperEmeraldRunChain | 2/0/1/0 | 2/0/1/0 |
| TestS3kMegaRunChain | 1/1/0/0 | 1/1/0/0 |
| TestS3kTailsFullChainRunChain | 1/0/1/0 | 1/0/1/0 |
| TestS3kTailsFullChainRunPrefix | 3/0/0/0 | 3/0/0/0 |


### Full-profile summary and limits

| Profile | Tests | Failures before→after | Errors before→after | Skips before→after |
| --- | ---: | --- | --- | --- |
| trace-replay | 886 | 10→10 | 1→1 | 7→7 |
| trace-replay-r7 | 117 | 84→84 | 11→11 | 0→0 |
| trace-segments | 70 | 47→47 | 8→8 | 0→0 |

R6's seven unchanged skips are five explicit opt-ins (descriptor benchmark,
two active-payload benchmarks, aux-schema benchmark and report-baseline audit)
and two absent optional bonus round-trip directories. They are not missing
ROM skips. All six required ROM properties use the absolute existing files;
the generic Sonic property also identifies S1. R7 and segments have no skips.
No full ordinary-suite or green trace-profile pass is claimed.

Segment-boot test statuses/messages and every normalized report JSON are
identical. These direct binder replays do not use the live-chain observation
hook; their normal report error lists, rather than a nonexistent live log,
were compared. The diagnostic collection shell attempted to move that absent
log and subsequently reported a shell parse error after the candidate segment
run. All three Maven invocations had already completed; their finished
summaries, XML counts and copied report directories were inspected directly.
This collection error did not abort or narrow a Maven selection.

Both trees used identical temporary, read-only live-comparator error logging.
It captured error tuples and associated player/sidekick/camera comparison
values; it never supplied gameplay inputs or changed severity. Full ordinary
binder report JSONs and exact per-test messages were also compared. A separate
read-only chain observation produced the clock table. All probe code is
removed from the delivery. Only the shared controller, its focused regression,
changelog and this audit are added relative to the accepted lane plus updated
base. The detached baseline checkout was restored and removed after inspection.

## Commands and timing

Use the lane's headless environment:

```bash
unset DISPLAY WAYLAND_DISPLAY JDK_JAVA_OPTIONS MAVEN_ARGS LD_LIBRARY_PATH
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
export JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl
export PATH="$HOME/.local/bin:$PATH"
R="$HOME/code/projects/OpenGGF"
rom_flags=("-Dsonic1.rom.path=$R/s1.gen" "-Dsonic2.rom.path=$R/s2.gen"
  "-Ds3k.rom.path=$R/s3k.gen" "-Dkis2.rom.path=$R/kis2.gen"
  "-Dsonic1.rev00.rom.path=$R/s1-r0.gen" "-Dsonic2.rev00.rom.path=$R/s2-r0.gen"
  "-Dsonic.rom.path=$R/s1.gen")
# Each complete profile, once before on pinned origin/develop and once after:
for profile in trace-replay trace-replay-r7 trace-segments; do
  python3 tools/testing/maven_queue.py -Dmse=off -B "-P$profile" \
    -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
    "-Dopenggf.debug.comparisonSurvey=$PWD/target/comparison-survey.jsonl.gz" \
    "${rom_flags[@]}" test
done
# Negative control on the old guard. Initial --lean compilation OOM'd;
# this exact normal-lane run then executed the discriminating assertion:
python3 tools/testing/maven_queue.py --normal -Dmse=off -B \
  -Dtest=TestLevelIterationHardwareTimingAdmissionOrder test
# Fixed focused selection:
python3 tools/testing/maven_queue.py --normal -Dmse=off -B \
  '-Dtest=TestLevelIterationHardwareTimingAdmissionOrder,TestLevelIterationAdmissionController,TestHardwareTimingAuthorityGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$R/s3k.gen" test
# Per-load clock witness, before and after:
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS3kMegaRunChain -Dopenggf.debug.loadClockSurvey=true \
  "-Ds3k.rom.path=$R/s3k.gen" test
# Clean-source compilation after removing all temporary probes:
python3 tools/testing/maven_queue.py -Dmse=off compile
```

The two debug properties above enabled temporary observation sites, not new
committed features. Fresh Surefire/trace report directories were used per
profile; diagnostic snapshots were kept only for this before/after comparison
and consumed before delivery. JVM completion closed compressed live logs.
All diagnostic captures are removed after the findings are recorded here.

| Profile | Before execution / queue wait | After execution / queue wait |
| --- | --- | --- |
| trace-replay | 11m43s / 395s | 10m51s / 515s |
| trace-replay-r7 | 6m02s / 708s | 6m02s / 82s |
| trace-segments | 4m25s / 468s | 4m30s / 43s |

Execution includes Maven preparation and tests. Queue waits are separately
reported by the wrapper. These intervals overlap with independent work;
their sum is not elapsed delivery time. Total wall time was not measured.

## Frontier-log text for the integrating lead

2026-11-03 — Held fade V-int service, base2698596fc: remove comparator
exhaustion from physical VBlank service while retaining its original cursor
ownership gate. Mega per-load cumulative deficits32/54/95→11/33/53;
Slots first physics row172 y_speed→268 y_speed, errors7723→7891.
Tuple gate removes7067/adds7235; first removal172, first addition268 after
the baseline's existing physics divergence.715 additions are beyond old
coverage row960. Row268 candidate position/camera match ROM, but Y speed is
-$0400 versus0. Segment2's112 errors remain unchanged. Walk now misses the
stage-exit boundary rather than leaving production ownership early.
Full before/after r6:886/10F/1E/7S; r7:117/84F/11E/0S;
segments:70/47F/8E/0S. Only Mega changes; S1/S2/KiS2 and every other
chain/prefix outcome/report remain unchanged. Focused checks95 pass,0 skips.
No synchronous-load ledger/pricing work, cursor advance or comparison
relaxation. See this audit for exact state, commands and limits.
