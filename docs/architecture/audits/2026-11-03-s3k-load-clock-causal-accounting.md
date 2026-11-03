# S3K fresh-load clock accounting investigation

Source base: `54966bab362fe0d76a30b01e55a7a77d68f93b1c`.
Scope: the three early fresh loads in `s3-knux-multibonus-ss`, after the
held-fade repair. Existing measured residual losses are 11, 22 and 20 V-ints.
This investigation separates the ROM's executed load phases from engine
bookkeeping; it does not assign a fitted load budget.

## Source-grounded constraints

Research uses the repository's pinned S3K disassembly
`8b2cf3c4178f2382f844da67798de4befc50c5e0`, read through Git without changing
the user's different local checkout.

All three paths enter `Level` through the restart check in
`LevelLoop (sonic3k.asm:7916-7928)`. The normal load calls the same black fade
through `loc_5FF6 (sonic3k.asm:7555-7562)`;
`Pal_FadeToBlack (sonic3k.asm:5074-5085)` waits 22 times. The engine's bonus
return also has an explicit fade/held-service path, so the return's residual
22 alone is not evidence for another fade.

The locked title path is a queue/movement gate, not a fixed delay:
`loc_62CC (sonic3k.asm:7769-7781)` services direct Kosinski, VSync, sprites,
Nemesis and KosM until the title and Nemesis gates open. Terrain has a separate
queue-owned loop in `LoadLevelLoadBlock (sonic3k.asm:9737-9782)`.

Direct block/chunk decode and layout assembly occur with interrupts enabled:
`loc_6310 (sonic3k.asm:7789-7797)` calls
`LoadLevelLoadBlock2 (sonic3k.asm:38714-38780)` before masking LevelSetup.
`Load_Level (sonic3k.asm:38787-38801)` copies layout data. These routines
contain no explicit VSync wait, but that does not make their native clock cost
zero. `VInt (sonic3k.asm:532-559)` selects the zero-routine handler when no
ordinary handler is armed; `VInt_0_Main (sonic3k.asm:591-603)` increments the
lag counter and still reaches the global V-int increment. Normal handler
service clears that lag counter, so frame-end snapshots alone do not count
all such interrupts.

The post-load 22-step title/palette counters belong to ordinary LevelLoop,
after initial sprite assembly: `loc_6468 (sonic3k.asm:7880-7915)` and
`Obj_TitleCardWait2 (sonic3k.asm:62289-62300)`. They are not an additional
22-frame pre-admission wait.

## Measurements and provenance

The original ROM SHA-1 is `CFBF98C36C776677290A872547AC47C53D2761D6`.
The movie SHA-256 is
`d7485e13f427d1b335cbbb3c405ff6136a77326239b79d3aed61e902078af45c`.
Native observations use an external build of pinned TraceChaser
`703bd92d32634ce5ebac60625b48c60ef5500427` with official BizHawk 2.11.

The native probe replays the original first 9,300 input rows. It records
execute callbacks at load-phase boundaries and the interrupted return PC
when the native zero-routine V-int handler runs. The callback reads the native
exception frame after the saved d0-a6 registers; it implements no CPU timing
model and writes no RAM. The engine probe only prints the live object clock,
actual PLC phase, load generation and cursor before/after existing iterations.
Movie cursors are labels, not elapsed-time measurements.

Frame-end trace snapshots show positive lag-counter increments of 3 on Gumball
entry, 20 on AIZ return and 4 on Slots entry. Because normal VBlank service
clears the counter, these observations are not presumed to be the full load
interrupt census. Execute callbacks distinguish interrupts hidden by resets.

## Engine phase observations

The completed Mega diagnostic replay reproduces the existing stage-exit
boundary failure (one test, one failure, no errors/skips). The live destination
admission clocks remain 5518, 6924 and 9044. Its output independently preserves
the earlier residual clock witness; it is not a green campaign result.

Terrain queue handoff starts at engine clocks 5512 (Gumball), 6897 (returned
AIZ) and 9030 (Slots). Title release occurs at 5518, 6924 and 9044 respectively.
Each release iteration has `LEVEL_TITLE_CARD` ownership and no object-clock
increment. Ordinary title service does increment the clock. Both entry fades
have 21 source-held increments plus the final load-completion increment; the
return likewise services 22 fade increments. The release observation is a
candidate boundary discrepancy, not authority to add a tick: a native matched
boundary is still needed to distinguish setup bookkeeping from elapsed work.

## Native phase census and exact partition

The native probe completed 9,300 original movie input rows, four loads and
408 observations. The three restart clocks (5422, 6807, 8960) and pre-loop-setup
clocks (5529, 6957, 9097) independently match the existing recording witness.
The CSV marker named `level_loop` is exactly `$0064DC` / `loc_64DC`, before
palette fill and counter initialization, not the later `LevelLoop` label.
The table ends at this measured pre-loop setup boundary. Its clocks also match
the previously recorded pre-row-zero witness; the probe does not separately
observe the later `LevelLoop` instruction.

The initial AIZ load is also captured as a control; it precedes the chain's
seeded comparison start and is not charged again.

| Clock-bearing span | Gumball ROM / engine | AIZ return ROM / engine | Slots ROM / engine |
| --- | ---: | ---: | ---: |
| Black fade | 22 / 22 | 22 / 22 | 22 / 22 |
| Locked-title explicit waits | 70 / 69 | 80 / 80 | 83 / 82 |
| Terrain queue explicit waits | 10 / 5 | 26 / 26 | 26 / 13 |
| Other native zero-routine interrupts | 5 / 0 | 22 / 0 | 6 / 0 |
| Restart to pre-loop setup | 107 / 96 | 150 / 128 | 137 / 117 |
| New deficit | 11 | 22 | 20 |

These rows partition every increment: 33 zero-routine interrupts, 18 fewer
terrain-wait ticks and two fewer locked-title ticks account for all 53. The
return's 22 is entirely its zero-routine interrupts; its explicit waits align.
The two bonus entries have independent queue/title timing discrepancies.
Treating all 53 as synchronous decompression would double-charge the 20 ticks
belonging to those explicit-wait differences.

**Sampling correction:** the temporary engine terrain marker runs inside the
provider update, before `advanceTitleCardVblankOnly()`. Its matching completed
iteration clocks are 5513, 6898 and 9031, not the printed pre-tick 5512, 6897
and 9030. Engine title spans therefore run 5444→5513, 6818→6898 and 8949→9031;
terrain spans end at 5518, 6924 and 9044. Ignoring this order would misassign
one tick per load between title and terrain. The title-release zero-tick
observation is not permission for a blanket extra tick: the return already
has the correct explicit-wait total.

The zero-routine census is source-localized:

| Interrupted work | Gumball | AIZ return | Slots |
| --- | ---: | ---: | ---: |
| Pending interrupt after display-clear unmask, return PC `$006014` | 1 | 1 | 1 |
| Direct block/chunk decode and layout copy | 2 | 20 | 3 |
| Pending interrupt after LevelSetup unmask, return PC `$00633A` | 1 | 1 | 1 |
| Initial sprite/object work before first LevelLoop | 1 | 0 | 1 |
| Total | 5 | 22 | 6 |

The direct-work samples land in `Kos_Decomp (sonic3k.asm:2587-2680)` and,
for one Gumball sample, `Load_Level (sonic3k.asm:38787-38803)` at `$01C382`.
The late Gumball sample interrupts the allocation scan at `$01BB10`, in
`AllocateObjectAfterCurrent (sonic3k.asm:37957-37984)`. The late Slots sample
interrupts the `$4000`-byte clear at `$04B6B6`, in
`sub_4B6AA (sonic3k.asm:98603-98610)`. Those are ordinary synchronous owners,
not invented queue jobs. The six unmask samples are deferred interrupt service;
charging every physical video frame spent masked would overcount.

The 3/20/4 visible lag-counter increases in the original frame-end snapshots
were incomplete: this callback census finds 5/22/6. It includes increments
cleared by later normal handlers and the final pre-admission service that the
source/destination segment snapshots do not separately expose.

The native source is preserved as
[`S3kLoadClockProbe.cs`](../../../tools/bizhawk/S3kLoadClockProbe.cs).
Its fixed 9,300-row window is this investigation's bounded witness, not a game
runtime condition or a general load budget. Raw native CSV is 18,058 bytes,
408 rows, SHA-256
`ca0aa7dbdfa6793756f6bbb3fe6f742d0c7b2c5a105d8b53a33f6c49119e076e`.
It remains with the source, pinned host build and engine-probe patch in the
owned external task directory. No canonical fixture is replaced.

## Limits and next correction owners

These measurements partition this movie's discrepancy; they are not constants
for another route or a new synchronous-load price table. The initial native
AIZ control and the returned AIZ have the same 20 direct-work interrupts, but
another BK2 and phase alignment remain necessary for any generalized runtime
correction.

The source locations for subsequent work are distinct:

- Explicit terrain/title waits: the existing S3K title and physical queue owners,
  including which recorded rows own readiness during entry/return choreography.
- Direct load and initial-object work: production load phases, with cache-hit
  equivalence and interrupt-mask/unmask boundaries preserved. Do not fabricate
  queue jobs for work the native ROM executes synchronously.
- Setup-only release: keep its separate contract. The returned level already
  has the correct explicit-wait count; a generic release tick is unsupported.

The final diagnostic confirms the terrain submission state:

| Load | Recorded-row authority at submission | Overall admission policy |
| --- | --- | --- |
| Gumball entry | inactive | RECORDED |
| AIZ return | active | RECORDED |
| Slots entry | inactive | RECORDED |

This is not a switch of the overall policy. `HardwareTimingService.submit`
marks work created outside represented rows as unrepresented; `service` and
`captureCoordinatorPreparation` advance/release such work through the engine's
preparation budget rather than a recorded completion edge. The port's
`enterUnrepresentedGap` disables that edge authority. Terrain is queued
immediately after the observed marker in
`Sonic3kTitleCardManager.publishFreshLevelRuntimeArtHandoffIfNeeded`.
The return preserves represented readiness and its 26-tick terrain wait aligns;
the bonus-entry batches take the fallback path and their waits are shorter.

The first correction owner to examine is therefore source-tail/readiness
admission across bonus entry, together with the existing queue's fallback
pacing. Preserve production submission, fingerprint and service-boundary
matching. Do not compensate this with a synchronous-load delay or ordinal
shifting. The two shorter title spans are separately measured; their exact
producer-level cause was not isolated by the terrain-submission observation.

No runtime correction is included.
The [earlier ledger proposal](../designs/2026-11-03-synchronous-load-vint-accounting.md)
remains deferred, and cannot absorb the explicit-wait discrepancies.

## Bonus-entry ownership follow-up

The follow-up starts from `278401593`. Headless source comparison ends at
`levelLoopRowCount()`, while the descriptor retains the full recorded
`rowCount()`. Those are different boundaries. On bonus entry,
`completeSourceTailReportingOnFailure()` completes the gameplay portion;
`prepareIntoInterior()` then declares an unrepresented timing gap and seeks
the destination offset before waiting for its title load. That skips the
source's represented load rows while production is still submitting work.
`PhysicalMovieRowDrive` already supports preparing and publishing one such
physical movie row without inventing a V-int.

A rejected diagnostic detached gameplay comparison at its existing logical
boundary and retained the source timing schedule while driving every remaining
source row. It completed one Mega test with one assertion failure, no errors
or skips: segment 0 lost production ownership in `BONUS_STAGE` at shared BK2
cursor 5565, before the source coverage end 5569. This is kill evidence for
blindly driving to `rowCount()`: native title release can happen before that
recorded endpoint, and another production iteration then runs bonus gameplay
against source-tail input. It does not justify weakening the ownership check,
waiting a fitted number of frames, or replaying recorded gameplay values.

The refined experiment retained source readiness only while native fade/title
production still owned the load, bounded by actual source coverage. It stopped
at native bonus-mode release, then used the existing close/gap/destination-offset
handoff. No gameplay values, budgets, completion identities or service boundaries
were changed. The production timing service and replay port were unchanged.

The completed refined replay ran three tests: the bounded first-Gumball
membership witness passed; the original Mega chain and a cold-start control
from the independent super-emerald movie failed. There were no errors or skips.
The two bonus terrain submissions now observed active recorded-row authority.
Their actual queue jobs remained represented in the passing Gumball witness.

| Measured boundary | Native | Original engine | Refined experiment |
| --- | ---: | ---: | ---: |
| Gumball pre-loop setup / engine admission | 5529 | 5518 | 5524 |
| Returned AIZ pre-loop setup / engine admission | 6957 | 6924 | 6930 |
| Slots pre-loop setup / engine admission | 9097 | 9044 | 9064 |

The experiment gains 6 and 14 ticks on the two bonus entries, 20 overall.
Its cumulative admission deficits are 5, 27 and 33; the new per-load losses
are exactly the measured 5, 22 and 6 zero-routine interrupts. This scalar
agreement does **not** certify the explicit phases:

| Explicit span | Gumball native / experiment | AIZ return native / experiment | Slots native / experiment |
| --- | ---: | ---: | ---: |
| Locked-title waits | 70 / 69 | 80 / 80 | 83 / 82 |
| Terrain waits | 10 / 11 | 26 / 26 | 26 / 27 |

The terrain markers are still sampled before their enclosing tick: completed
handoff clocks are 5513, 6904 and 9037. Release clocks are 5524, 6930 and 9064.
Both bonus titles remain one tick short; recorded readiness adds one extra
terrain tick that cancels each title error in the admission total. The loss of
row authority explains a real queue-admission defect, but repairing that alone
is not evidence that title ownership is correct.

The Mega experiment stops earlier than the original stage-exit-boundary failure:
segment 3 loses production ownership in `TITLE_CARD`, shared BK2 cursor 10086,
with the returned AIZ world already loaded. Slots first differs at row 264,
`rings` ROM 75 / engine 74, versus the original first noncamera mismatch at
row 268, `y_speed` ROM 0 / engine -$0400. The new Slots report's `errorCount`
6315 covers only the partial prefix through row 943; it cannot be ranked against
the original 7891 over the longer comparison. Initial and returned AIZ gameplay
reports remain zero-error partial reports, not completed physical-tail proofs.

The independent movie control cold-started at segment 4 `aiz_3` of
`s3k-knuckles-complete-superemeralds`. It fails before reaching Pachinko:
source ownership is lost at BK2 cursor 22129 after an AIZ act change. Its first
comparison already differs at row zero, rings ROM 3 / engine 0. Therefore this
cold start is not a valid continuous-carry control and supplies no generalization
claim for the bonus-entry correction. Do not hydrate its missing carry state to
make the experiment pass; qualify from continuous production instead.

All candidate Java changes and the temporary clock prints were removed.
The exact rejected diagnostic patch remains in the owned external directory,
SHA-256 `3ea63d6152493350caf6c67aa7e4cb5e8ea0142c70bbc41febd602682db62296`.
It applies to `278401593`, introduces the bounded witness methods named above,
and is not part of delivered engine or test code. Its refined Maven selection
was `-Dtest=TestS3kMegaRunChain,TestS3kKnucklesSuperEmeraldRunChain#firstPachinkoLoadRetainsRecordedSourceAuthority`,
using the reproduction environment below. Maven execution was 57.244 seconds;
the last observed queue wait was 602 seconds, so exact queue and total wall time
are unknown. The rejected full-tail diagnostic executed for 101 seconds, after
a last observed queue wait of 1083 seconds. A preparatory invocation stopped at
test compilation on an incorrect enum name; after correction, build-only
`test-compile` passed in 32.962 seconds before the credited replay.

The production launcher is a separate adapter obligation. Its completion test
uses `comparator.isComplete()`, and title admission can explicitly deactivate
represented row authority after the outer physical driver prepared it. The
headless correction alone cannot establish production parity. A production
correction must preserve the active physical owner through title admission and
complete semantic destination admission before the next bonus gameplay tick.

The next correction needs three separately verified owners: the bonus title's
one-tick-early producer gate; semantic accounting for direct decode/layout,
unmask service and initial-object work; and one source-tail handoff contract
implemented by both adapters. Preserve cache-hit equivalence and production job
identity. None calls for CPU/cycle simulation or a fitted per-load delay. The
5/22/6 census remains evidence about this movie, not runtime constants. Require
phase correctness and a distinct continuous BK2 before promoting the handoff.

Delivery includes documentation only, on updated `develop` base `5e534b0c2`.
Upstream adds opt-in network tests and verified-ROM discovery tooling; it changes
none of the gameplay or replay-owner Java measured by these probes. The rejected
experiments remain attributed to `278401593` plus their diagnostic patches.

## Reproduction and verification

The engine probes only add prints at `GameLoopPlcLifecycle.runLogicalIteration`
and the terrain-start branch of `Sonic3kTitleCardManager.update`. The second
observation additionally prints `recordedAuthorityRepresentsRow()` and
`admissionPolicy()` at that branch. The read-only patch remains with the native
capture in the external task directory. No diagnostic Java is retained in the
delivered runtime.

Both engine observations use this command from the isolated worktree, with
`S3K_ROM` set to the absolute existing original ROM:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS -u MAVEN_ARGS \
  -u LD_LIBRARY_PATH JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true \
  -Dsurefire.runOrder=alphabetical -Dtest=TestS3kMegaRunChain \
  "-Ds3k.rom.path=$S3K_ROM" test
```

For the native observer, prepare an external `TASK` directory and archive the
pinned TraceChaser sources there. Use its official deterministic `build.sh`,
with `BIZHAWK_HOME` naming the verified official 2.11 installation, under the
build/activity leases described in [the native observation guide](../../../tools/bizhawk/README.md#native-headless-rendered-observations).
Compile the preserved C# file using that build's harness assembly and the
installation's BizHawk dependencies:

```bash
mono /usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe /nologo /deterministic+ \
  /langversion:7.3 "/out:$TASK/LoadClockProbe.exe" \
  "/reference:$TASK/tracechaser/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe" \
  "/reference:$BIZHAWK_HOME/dll/BizHawk.Emulation.Cores.dll" \
  "/reference:$BIZHAWK_HOME/dll/BizHawk.Emulation.Common.dll" \
  "/reference:$BIZHAWK_HOME/dll/Newtonsoft.Json.dll" \
  /reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll \
  tools/bizhawk/S3kLoadClockProbe.cs
```

Launch the following argument vector through `maven_slot` from the intended
worktree, passing `**inherited_slot(leases)` to `subprocess.run`. Set
`BIZHAWK_HOME` explicitly in the child environment as well as `MONO_PATH`
(pinned harness output plus installation `dll`) and `LD_LIBRARY_PATH`
(installation `dll`), with display variables removed:

```bash
mono "$TASK/LoadClockProbe.exe" "$S3K_ROM" \
  "$ROOT/src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/s3-knux-multibonus-ss.bk2" \
  "$TASK/native.csv"
```

The output must be absent. Require zero process exit and the completed
9,300-row/four-load/408-marker census; a nonempty partial CSV is insufficient.
The preserved probe source SHA-256 is
`3f0bb676723492f51cd3fbaf4f8fb4a2fc9152ed604d14faf5a3aa6a015946c1`.
Its bytes match the compiled/executed source.

Two native launch attempts failed before observations: the first misused the
Python lease helper's return type, and the second omitted `BIZHAWK_HOME` from
the run environment. The corrected launcher passed official 2.11 runtime
preflight and completed the capture. The successful subprocess exited zero;
queue telemetry calls its wrapper `interrupted` because the wrapper raised
`SystemExit(0)` inside the lease. Call `sys.exit` after leaving that context in
reproductions. This telemetry label is not the native process result.

Observed queue waits were 814.1 and 934.4 seconds for the failed native
startups, then 614.2 seconds for the successful capture. Their execution holds
were 0.0, 0.2 and 34.6 seconds respectively. The first engine probe waited
777 seconds and Maven reported 104 seconds execution. These intervals overlap;
their sum is not delivery wall time, which was not measured.


The final engine observation also completed one test with the same stage-exit
assertion failure, zero errors and zero skips. The Slots report retains
`errorCount=7891` and first non-camera mismatch row268, `y_speed`
(ROM `$0000`, engine `-$0400`). This is the reported frontier, not a new
error-tuple equivalence qualification. Its queue wait was 746 seconds and
Maven reported 110 seconds execution. Both completed observations retain the
same admission clocks. No ordinary smoke or full trace profile was run for
these read-only probes; the delivered change contains no Java runtime edits.

Validation: native compile/execution and literal ROM/movie/control-boundary
checks; independent source/CSV/engine arithmetic review; strict pinned
citations, links, whitespace and instruction-mirror checks. The checked
initial AIZ control is additional evidence within this movie, not a second
BK2 or a general load-time certification.
