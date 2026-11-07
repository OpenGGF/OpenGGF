# S1 complete-emerald chain: native PLC and initialization timing

## Scope and source

Base develop `34b8830b1856395c4ea7d3f9da02bbee9c3355c7`; recorder repair
`d54cc51406fe392e4084f25cbe8da54af949142c`. This delivery publishes readiness
for the existing S1 Nemesis arm contract. It changes no gameplay comparison,
BK2 input, queue budget, timing kind or reimplementation boundary. The ending
axis is separate and remains unresolved in this checkpoint.

## Why the recorder needed repair

`RunPLC (sonic.asm:1379-1420)` checks its FIFO and active count after entry.
An interrupt between entry and those tests can retire the old decoder; one
before the count write can cross an observation row. Entry-time eligibility
therefore can omit a real arm or emit it too early.

The native observer now watches the accepted `move.w d2,(v_plc_patternsleft).w`
at REV01 PC `0x160A`, opcode `31 C2 F6 F8`, before the head-pointer overwrite
at `0x161C`. Shipped `FixBugs=0` writes before `NemDec_BuildCodeTable`; the
fixed branch writes after it. No CPU/cycle model is added to the engine.
Two interruption controls fail the old observer and pass the repair. All ten
focused native observer cases, including the retail opcode pin, pass.
Independent source review approved this boundary.

The broader native S1 unit selection has 128 passes/40 failures at baseline
and 130 passes/the same 40 failures after repair. Every existing failure
identity and message is exact. These inherited audio/CLI failures are not
claimed as a green native suite. Producer index and reachable-history policy
scanners pass.

## Native evidence and publication

The unchanged `sonic1-complete-withemeralds.bk2` and verified World REV01 ROM
were driven through the native headless harness. A saved state at completed
frame 47000 was loaded for the later bounded probe. At completed frame 47136
(MZ2_3 row 101), entry, accepted-write and following-instruction hooks all ran
in that advance; the frame exposes source 245824 and all 18 patterns.

The dedicated edge is ordinal 59, `pre_main_loop`, fingerprint
`sha256:0495d001d7b7d63f2d70ab32c084cb31f69866a3a401d712c7ecc2861dd2206e`.
The engine observer confirms recorded arm authority and an active matching
18/18 head. Removing the timing streams restores the original three errors.
The bounded chain proof closes all 16,207 MZ2 rows without an error.
Repaired-capture timing bytes through this proof are identical to its already
tested inputs, with source/input equivalence retained in the local receipt.

A full repaired capture completes and passes strict v5 validation. It emits
28 timing streams with 242 edges. All 68 physics/aux payloads reproduce the
previous observations byte-for-byte after decompression. Publication retains
their original compressed files, so all 68 stored files, the movie and manifest
are also unchanged. Forty native output containers already match those files;
28 small output bodies are plain under the native default compression threshold.
Only metadata recording dates change. No observation is edited or fitted.
[The frozen publication test](../../../src/test/java/com/openggf/trace/timing/TestCommittedS1HardwareTimingFixtures.java)
pins the reviewed timing bytes, event counts, ranges and global FIFO ordinals.
A checkpoint near the ending was also saved for the next axis.

## Replay result

| Axis | Before errors / first row | After errors |
| --- | --- | --- |
| MZ2_3 | 3 / 101 | 0 |
| MZ3_2 | 6 / 102 | 0 |
| LZ3 | 3 / 10185 | 0 |
| LZ4 | 12 / 11 | 0 |
| SBZ3 terminal art | 4704 native / 4784 engine edges | unchanged |

The focused chain completes all 34 segments and 10,943 terminal rows.
Exactly four of its 29 complete JSON reports change, each to zero errors;
the other 25 are literal matches. All 15,038 terminal comparison tuples are
literal matches: 10,334 ERROR and 4,704 WARNING. There are no new queue errors
on any compared row and no earlier frontier.

The matched S1 trace selection covers 45 classes/71 cases. Both sources have
70 passing cases and the one chain failure; the chain now reports only its
ending axis. Every other existing test outcome is exact. All 92 report
identities remain: eight queue reports, including cold-start controls, improve
to zero errors; the other 84 are unchanged. S1 has no classes in the r7 or
segment profiles. Standalone/other S1 fixtures are not republished.

## Validation and delivery status

Focused commands use `tools/testing/maven_queue.py -Dmse=off -B`, one fork,
alphabetical order and all six explicit ROM paths from the fleet brief:

- `-Ptrace-replay -Dtest=TestS1CompleteEmeraldRunChain#ghz1ToScrapBrainAcrossEverySpecialStage test`
- `-Ptrace-replay -Dtest=<45 explicit S1 trace classes> test`
- Ordinary queue, timing, source, gitlink, schema and compression guards.

The ordinary focused guard selection has 400 passing cases versus 345
existing baseline cases. The change-based smoke command is:

```bash
python3 tools/testing/run_categories.py --base 34b8830b1856395c4ea7d3f9da02bbee9c3355c7 --workers 2 --headless-gl surfaceless-egl --run --keep-diagnostics
```

At candidate `394ebb014`, all 91 ordinary batches and the guard batch complete:
37,839 tests, 29 failures, zero errors and 49 allowed skips, with no unexplained
skip or missing ROM. The raw verdict is **FAILED**, reflecting those inherited
ordinary failures; all 836 guard cases pass. This is a bounded smoke selection,
not a full-suite pass.

Fresh pre-task controls cover 456 testcase occurrences. Every one of the
29 candidate failure occurrences matches an existing baseline failure; no
baseline failure is lost. Twenty-five assertion messages are literal matches.
Three differ only in Java Set display order and one only in Java object identity
addresses. The original assertions and their complete messages are retained;
no comparison or test semantics change.

Three Islands requires the same machine-local ROM catalogue, which scans the
working directory in addition to the test ROM properties. A second worktree
without the prepared checkout's existing ROM links produces six failures rather
than eight. Replaying its exact 50-class/two-fork batch in the prepared checkout,
with `src` and `examples` temporarily restored to the exact pre-task source,
reproduces all eight original failures across 367 cases. No ROM is linked,
copied, moved or edited for this control. The candidate's original file hashes
and clean status are restored afterward. Other focused baseline controls cover
the remaining failure identities. No newly red class, earlier frontier or added
error on a clean compared row remains; the delivery gate therefore passes.

The candidate was rebased onto develop `f2ad93c143` before validation. Its
upstream change concerns S3K beta-notice wording; no S1 conflict or behavior
change was introduced. A final fetch finds the same destination. The user
approved publication of recorder `d54cc514` and its OpenGGF pin after this gate.
The four-axis gain was delivered as `394ebb014ffc8ceecc2e064be5017b1feecc6ab7`
and qualification commit `c1029d3b4baa736a44e245f86ad2482cf62a4961`. Separated
post-integration chain and ordinary guard checks passed by matched attribution;
the chain retained only the ending failure.
Detailed commands, raw observations, complete assertions, input hashes and
queue/execution receipts are retained under `/tmp/trace-s1-chain-20261106/`.

## Ending and credits continuation

The next candidate uses TraceChaser `161e45ac81f972559280597a36c0c9d97286fc61`.
`Sonic1EndingLoad`, `Sonic1CreditsScreenLoad` and `Sonic1CreditsDemoLoad` prepare
real ROM-backed operations through `NativeLevelLoadController`. The existing
initialization port may only hold/resume the matching load/phase identity and
select its observed zero-routine service. Actual CPU sprite construction and
uncounted initial object passes belong to production. Scoped execution resources
close before every return to the host; no counter, decoder progress, coordinate
or comparison-row value enters the continuation.

Independent source encoders qualify ending, font and all eight demo selections.
Native saved-state probes reuse the original movie and reload the saved states
near the window. The full observed tail has 724 events: 391 holds, 288 zero-routine
services and 45 resumes across 18 loads. Its original 113-event ending prefix is
byte-identical. Every fixed recorder cut has an opcode control. Actual credits
initialization visits 338 callbacks, including children, from 50 owner/routine
pairs. The reviewed closure excludes Basaran's clock-reading range and fresh
Burrobot's later moving state; ordinary rings are distinct from lost rings.
Reached LZ setup clock reads and unknown callbacks reject capture. Zero-routine
service changes neither gameplay RNG nor the level counter in pre-demo mode.
Ending initial-object interrupts remain unsupported. This is no claim about
chip-level sound ordering or arbitrary callbacks.

The ending actor is initialized at the native production cut rather than
admitting the old atomic level load early. Native counted entry and palette
waits then release ordinary movement. EndSonic owns its subsequent actor slot,
animation restart and flower reveal. The logo's delay decrements immediately on
fall-through from `ESth_Delay` to `ESth_GotoCredits`; its owner sets game mode
without deleting the object. The actual credits setup later clears Object RAM.
The user approved correcting the ordinary unit test and clarified that future
unit defects encoding non-ROM behavior may be repaired routinely; trace
comparison and assertion semantics remain reserved and unchanged.

Credits page setup, four hidden level waits, palette loops and `Level_FDLoop`
have separate production owners. Demo initial assembly preserves inherited
level/sprite clocks independently before their native post-pass clears. The
active ending provider is passed through a bounded caller scope, not obtained
from the module's provider factory. `VBlank_Title` omits Sonic DMA, retaining a
last-demo pending DPLC until later production replaces it. `Level_FDLoop` runs
objects and placement without LZ water features, events or global oscillation.
That distinction prevents an erroneous slide release during LZ's final fade.
Secondary level PLC submission and `Level_ClrCardArt` retain their actual loop
boundaries, without bootstrap queue drains or simulated palette iterations.

A scratch-input full-chain proof passes all 34 segments and 10,943 terminal rows,
with 4,704 native and engine art edges. Earlier candidates are retained in the
local task artifact: factory ownership prevented demo phase admission; a shared
fresh-epoch assertion rejected valid inherited clocks; title-screen DMA created
extra art edges; and running water features during the final fade changed LZ
movement. Each was corrected at its ROM owner. No measured delay, CPU pricing,
new timing kind, BK2 edit or weakened trace assertion was used.

Canonical full capture, final S1 outcome comparison and smoke qualification are
recorded below when complete. Producer publication and the new develop pin are
separate reserved decisions; the earlier approval named only `d54cc514`.

### Canonical publication and final candidate qualification

Canonical native capture on producer `161e45ac` completed successfully using the
original movie and `--load-queue-state --initialization-timing`. All 68 logical
physics/aux payloads, all 28 queue streams and the BK2 match the prior canonical
publication. The installed publication retains the original 68 compressed files
literally. Its only metadata delta is 34 recording dates; the manifest adds the
terminal admission census. The initialization companion also matches the
independently qualified saved-tail capture byte-for-byte: 5,402 stored bytes,
SHA-256 `37a5daf04174f434c6e2aceabe75489336bb503b436da571e561e6fb60dba3f8`,
724 events/18 loads (391 HOLD, 288 zero-routine interrupts, 45 RESUME). The
terminal census has 147 runs/10,943 rows, including 710 lag rows. Strict v5
frame-keyed auxiliary validation passes.

The unchanged original target passes against these canonical fixtures. The
45-class S1 trace selection passes all 71 cases without skips. The other 70
existing testcase identities, outcomes and messages are exact to pre-task base
`34b8830b1`. All 92 comparison reports remain; 89 bodies are exact to the queue
fix delivery. Each of the other three only removes its terminal-art failure
string; every gap field and other failure is unchanged. Relative to pre-task,
eight queue reports and those three terminal reports improve; 81 bodies are exact.
The complete chain reaches all 34 segments and 10,943 tail rows with 4,704 native
and engine edges. No other S1 trace test changes outcome.

The initial focused set exposed two obsolete ordinary unit expectations: its
phase table lacked the newly modeled three-tile `Level_DelayLoop` service, and
its fade source guard expected the old concurrent API call instead of direct
whiteout plus the blocking reveal. Both are corrected with owning ROM citations,
under the user's clarified unit-defect ruling. Trace assertions are unchanged.
Focused rerun and final smoke results follow below after completion.

The focused ordinary selection passes all 628 cases across 63 explicitly
requested classes, with zero skips. It covers fixture/schema/compression/gitlink
and timing-authority guards, unchanged source budgets, every new owner, affected
rewind boundaries and the four S3K keep-green obligations (both level-loading
classes). The broader smoke gate is pending; this is a bounded focused pass.

The normal commit hook also caught an unintended public fade overload. The
counted native reveal now uses the existing internal graphics bridge and a
package-private overload; the Mod API descriptor and signature pin are unchanged.
After that access repair, the repeated S1 profile again passes 71/71, and every
one of its 92 comparison report bodies is byte-identical to the earlier green
candidate. The final focused set, expanded with compiled Mod API signatures and
general fade tests, passes all 644 cases across 65 classes with zero skips.
The final frozen-source change-based smoke gate remains pending.
