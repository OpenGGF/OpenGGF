# S2 diagonal launcher capture before release — 2026-11-07

Base: develop `8afbc8b7ca0bc0746824aa8fb9ce95e545898114`. Only the diagonal
Obj85 object changes; the vertical launcher, shared solid controller, CPU,
movement, recorder, fixtures, bootstrap and timing authority are unchanged.

## Native owner and executed discrimination

`Obj85_Diagonal` computes its head from the previous compression, then calls
`loc_2AF06` for P1 and P2 before testing either captured rider's held buttons
and choosing release or empty-spring decompression (s2.asm:58224-58285).
The fresh branch calls the single-character solid helper, seats a captured
rider and returns without running held compression (loc_2AF06/loc_2AF2E,
s2.asm:58286-58321). On the following dispatch, `loc_2B018` launches from that
compression without another seat write (s2.asm:58375-58400). These sites have
no alternate FixBugs branch; shipped `fixBugs = 0` remains the contract.

The engine's automatic solid checkpoint ran after Obj85's aggregate decision.
A fresh P2 capture therefore arrived after the object had already classified
itself as empty and reduced compression. A comparison-only probe of the actual
chain observed this sequence:

| Object callback V-int argument | Observed state |
| --- | --- |
| 135659 | Compression `$14` computes head `$219B/$044E`; the empty tail reduces compression to `$10`, then the later solid callback captures Tails at `$21AE/$043B`. |
| 135660 | The head advances to `$219D/$044C`; Tails is seated again at `$21B0/$0439` and only now marked for launch. Completed trace row10361 first differs. |
| 135661 | Tails launches at `$0A00/-$0A00`; native row10361 already required `$0C00/-$0C00` while retaining the capture position. |

These are the engine callback clock arguments, not an invented trace-row clock.
The complete comparison and callback logs preserve the sampling boundaries.
Native aux corroborates the capture/standing-bit removal and subsequent launch.

The diagonal variant now uses the existing participant-scoped
`MANUAL_CHECKPOINT`: each empty rider takes its native solid/capture call inside
the object's update, using the current head as `d4`. That branch returns before
held compression. The existing shared release decision runs after both riders;
a fresh P2 holding jump can therefore veto P1's release. No new timer, latch,
persistent field, queue work or engine API is introduced.

Four real `ObjectManager`/`DefaultSolidExecutionRegistry` regressions cover fresh
P2 release and the held-input veto for both ordinary and slope-running diagonal
subtypes. All four fail their intended velocity assertions on the base; the
eight previous launcher cases pass. The corrected object passes all12 cases.
The focused component selection passes259 tests with no errors or skips,
including graph rewind, retail semantics, solid checkpoints and source guards.

The first source review then identified two lost automatic-tail behaviors:
capture cooldown had also suppressed solid geometry, and offscreen release no
longer retired its ride. Four additional real-pipeline cases fail those intended
assertions on the initial candidate. Solid resolution now remains active during
the inherited capture cooldown, which gates capture only. Diagonal release
clears OnObject and uses the existing owner-scoped `releaseRidingObject` to
retire only this spring's standing/ride state. Native `loc_2AF7A` explicitly
clears those status bits (s2.asm:58326-58334). The expanded16-case launcher class
passes without errors or skips. A fresh repaired-source capture preserves all54
complete report/owner files and all three testcase outcomes, with CNZ2 still
zero-error and ARZ2 unchanged. Its independent exact actual-row comparison adds
zero ERRORs; it does not borrow the earlier candidate's source hash.

## Matched route result and actual-row gate

The unmodified CURRENT base independently reproduces all27 prior chain/control
payloads. Its whole chain assertion differs from the previous worktree only in
four literal report-path prefixes. The corrected read-only base probe reproduces
all54 JSON/owner files and all three complete testcase outcomes exactly, without
normalization. An initial probe compile failure ran zero tests and is separately
retained; it supplies no runtime evidence.

| Witness | Base | Candidate |
| --- | --- | --- |
| Chain CNZ2 | 49,194 actual ERRORs, first10361 Tails X | **Zero ERRORs or warnings on all13,045 compared rows**, report complete, bootstrap0 |
| Chain ARZ2 | 4,442 ERRORs, first5512 X | Identical6,409 rows and every observed field |
| CNZ2 / ARZ2 level-select controls | Both pass | Both pass |
| Complete chain | Eleven failing axes; HTZ2 boundary155218 | Ten failing axes; same HTZ2 boundary155218 |

Exact CNZ2 comparison removes49,194 ERROR tuples and adds zero. All13,045 row
identities are common, with no internal gap; none of the10,361 baseline-error-free
rows gains an error. Shared expected values are unchanged. There are conditional
dynamic-art field-set changes on1,071 rows, totaling12,823 absent/new field
locations; those remain explicit coverage differences, never presumed matches.
Every candidate observed field agrees, including its animation and art traffic.

Only CNZ2 and the aggregate art-gap report change among the54 complete files.
Both already reached HTZ reports remain identical. The same six art-gap boundary
failures persist: their complete explicit error-field observations fall68 to42,
with no new error location or changed shared expected value. Thirty-two existing
error values change after the corrected CNZ2 DPLC traffic. This comparison uses
the actual serialized observations, not expanded grouped spans; fields absent
from a gap failure report remain unknown.

ARZ2's capsule contact, the six gap failures, HTZ1/HTZ2 physical mismatches and
the missing HTZ2 level advance remain open. A green CNZ2 segment is not a green
campaign, a pixel certificate, or certification of inherited donor/team/viewport
and cold-route gaps.
The inherited synthetic capture cooldown is retained as an admission limit;
this correction does not certify immediate native recapture during that window.

## Reproduction, restoration and limits

All immutable command arrays, environment/source/ROM hashes, full reports,
uncapped assertions, actual JSONL gzip streams, tuple/coverage receipts and the
four red assertions are under `/tmp/trace-frontiers-20261107/`. Key receipts are
`s2-observer2-equivalence.json`, `s2-final-cnz2-raw-gate.json`,
`s2-cnz2-shared-expected-and-retention.json`, `s2-arz2-raw-gate.json` and
`s2-gap-explicit-field-delta.json`. The repaired-source retention receipt is
`s2-final-raw-retention.json`; its ARZ2 gate is `s2-final-arz2-raw-gate.json`.
Gzip retention preserves every original
comparison; the retention receipt maps the original analysis paths to the
retained compressed streams and records decompressed hashes.

Each route measurement selects exactly
`TestS2CompleteEmeraldRunChain,TestS2Arz2LevelSelectTraceReplay,TestS2Cnz2LevelSelectTraceReplay`
under `-Ptrace-replay`, one alphabetical fork, Java25, Lua5.4, surfaceless EGL,
`-Dmse=off` and all seven original absolute ROM properties. Every Maven invocation
uses the worktree's queue wrapper; missing ROMs are zero. Runtime state never
comes from compared rows.

Final clean-source qualification runs307 cases:306 pass, one inherited strict
whole-chain assertion failure, zero errors or skips. This includes the263-case
expanded component selection, both passing level-select controls and41 timing /
producer-boundary guard cases. All54 report/owner files and the three common full
testcase outcomes equal the final observed candidate exactly, without message
normalization. Both rebuilt classes contain no observer marker; source and class
hashes are recorded. There is no pending observer rebuild.

`s2-timing-phases.json` separates every immutable invocation's queue, Maven
phase displays and command wall time, including the zero-test failed probe.
Reported queue wait totals2,659 seconds; Maven phase execution is approximately
1,108.617 seconds (minute displays are rounded); driver wall totals3,783.555
seconds. User-response wait is zero; remaining overhead and off-job investigation
are not independently timed. These sums are work accounting, not a claim about
the wave's elapsed time or other lanes.

Combined profiles, delivery and cleanup remain lead-owned until their receipts
are recorded. This is bounded verification, not a full-suite pass.
