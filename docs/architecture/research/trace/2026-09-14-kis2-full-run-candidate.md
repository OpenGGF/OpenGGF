# KiS2 all-emeralds full-run candidate

User supplied `docs/BizHawk-2.11-linux-x64/Movies/kis2-full-run-all-emeralds.bk2`.
BK2 SHA-256: `36a0353b249edcf7a229b8682cb93f8563c383fe5a65e92030be2e5acf2c20c0`;
268,301 input rows, BizHawk 2.11 / Genplus-gx, power-on movie (no embedded state).
ROM: 3,407,872-byte KiS2 lock-on dump, SHA-1
`6CD0537A3AEE0E012BB86D5837DDFF9342595004`.

## Candidate, not a published chain fixture

Durable capture root: `$KIS2_CAPTURE_ROOT` (external task directory `kis2-full-run-20260914`).
`full-run/` holds 36 segments, 35 transitions, the source BK2 and manifest:
110 files, 18,052,870 stored bytes, 248,042 compared-input gameplay/SS rows.
29 level segments cover EHZ1 through DEZ; seven special stages have native
RunObjects-pass observations. Two death restarts and 19 level advances are
recorded. All seven SS returns show emerald counts 1 through 7; rings restored
are 65, 95, 121, 60, 69, 82 and 68. The final level segment ends at movie offset
253,736; subsequent ending-mode frames are outside the S2 run contract.

Every row's input mask was checked against BK2 offset + row; frame counters,
metadata identity, per-segment row counts and aux ranges were checked in full.
Every payload is compressed. `candidate-inventory.json` seals all stored and
logical hashes/sizes plus per-segment event counts. Inventory SHA-256:
`3a0743813aff46294deb9bd454f44a573e6d9290d6e226b14e159b44a3f8f367`.
The first segment (offset 742, 3,180 rows) matches existing plain KiS2 capture
for the entire physics and aux streams after CRLF/LF normalization; run mode
uses CRLF while plain mode uses LF. Metadata differs by declared run identity.

## Recorder changes and independent evidence

Based on TraceChaser `9fd957b`, isolated branch `feature/ai-kis2-full-run` adds
KiS2 run identity, special-stage identity and fixed chip observer addresses.
Exact-ROM SHA-1 gates capture. Independent ROM/code review verified shared RAM
semantics and these PCs: ReadJoypads RTS `$300E52`, V-int caller `$300592`,
pre-start RunObjects return `$304CE0`, recurring return `$304D58`. The stock
S2 terminal-pass rules remain valid. Four focused KiS2 tests passed, including
wrong-ROM rejection, both loop observations and restored checkpoint rings.
CLI rejects unsupported audit/profile requests; stock S2 defaults are retained.

## Frontier and prerequisite

Queued Maven on develop `ad68609e9`, `-Dmse=off -Dtest=TestKis2Ehz1TraceReplay`
with `-Dopenggf.trace.candidate.dir=<capture-root>/first-segment` and verified
absolute S2/S3K ROM properties compared all 3,180 rows: 466 comparison errors,
91 bootstrap errors, no skipped test. First gameplay mismatch is row 156,
`y_speed`, expected `$0010`, actual `-$00F0`; bootstrap still first disagrees
on `player_history.pos`. An initial invocation skipped because the source BK2
had not yet been copied beside the scratch segment; it is not a passing run.

The new `TestKis2CompleteEmeraldRunChain` accepts a scratch run via
`-Dopenggf.trace.kis2.run.dir=<capture-root>/full-run`. Its first invocation
executed one test, zero skips, and correctly failed manifest validation:
`trace_schema 5 segment omits dynamic-art capability`. No chain gameplay rows
were compared. Nothing has been installed in canonical fixture storage.

The missing native audit cannot be obtained by relocating stock S2 addresses:
KiS2 `LoadSonicDynPLC` converts S&K art through `$317540` into `$FFF100`,
combines all DPLC runs into one DMA, and tail-jumps to QueueDMATransfer rather
than returning through the stock decision RTS. Its SS DPLC source calculation
uses tile offsets shifted by five, not stock S2's custom shift-by-one format.
Producer and consumer owner sets also omit Knuckles. Supporting a valid chain
therefore needs explicit converted-art aggregation, RAM-source descriptors,
tail-call lifecycle, SS decoding and Knuckles ownership at both ends. Do not
weaken the v5 validator or fabricate capability/timing observations.

Source detail: decision `$317414`, direct entry `$31741A`, pilot caller `$333D9E`,
no-work return `$31753E`, successful tail JMP `$317538`, queue entry/return/process
`$301158/$3011B4/$3011B6`, DPLC/art `$14BD0A/$1200E0`; SS wrapper/entry/return
`$32CCC6/$32CCF0/$32CD38`, DPLC `$32D728`, source RAM `$FF0000`.

## Validation limits and retained state

Native full verification reported 765 passes, 64 failures and four skips before
stalling for ten minutes without output; it was terminated and is incomplete.
Failures include legacy fixture-root lookups and unavailable/mismatched audio
observer inputs. A matched run of the stock S2 run tests on recorder base
`9fd957b` passed 14 and reproduced the same `input_sample_frame:1` aux-event
expectation failure. Other native failures remain individually unattributed.
The four focused KiS2 tests pass; recorder repository/history policy scans pass.

The new art-transfer prerequisite is a producer/consumer implementation task,
not a fixture flag change. The user has been asked whether to take it on now or
retain this candidate for a separate follow-up. Source work is preserved locally
in the TraceChaser and OpenGGF `feature/ai-kis2-full-run` worktrees. No submodule
pointer change, canonical fixture installation, integration or push is claimed.
