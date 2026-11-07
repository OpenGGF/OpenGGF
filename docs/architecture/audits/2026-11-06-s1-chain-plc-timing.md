# S1 complete-emerald chain: native PLC arm timing

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
Final integration and bounded post-integration checks remain delivery steps.
Detailed commands, raw observations, complete assertions, input hashes and
queue/execution receipts are retained under `/tmp/trace-s1-chain-20261106/`.
