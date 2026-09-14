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

## Initial frontier and prerequisite

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

## Art-transfer prerequisite implementation

The user authorized the prerequisite after the initial capture exposed the
missing capability. Normal art now publishes one `knuckles` RAM request from
`$FFF100` to `$F000`, whose length is the sum of the selected DPLC tile runs.
The native observer verifies every converted byte against the ROM art and
256-byte conversion table, verifies the accepted queue request, and closes
its tail-called decision at `$3011B4`. Suppressed, empty and queue-full decisions
do not invent a transfer. Completion is observed at `$3011B6`.

Special stages use owner `ss-knuckles`, standard tile indices shifted by five,
and the shared decision return `$32CD38`. Java validates these callback sites
per owner, retaining stock-game callback restrictions. The v5 schema and timing
input contract are unchanged. Production receives an immutable art profile from
the active module; original ROM DPLC requests still select rendered tiles while
the lifecycle records the aggregate transfer. Existing snapshots preserve the
ledger, deduplication and held-load state. No observed transfer creates engine
work or hydrates gameplay state.

Independent read-only ROM/code review found no concrete defect in these paths.
Native focused verification passed 10 KiS2 tests, 21 stock S2 observer tests and
nine dynamic-art state tests, with no skips. These are focused results, not a
native full-suite pass. The previous broad validation limits above remain.

The full-run Java test accepts `-Dkis2.rom.path=<absolute lock-on dump>` through
the production ROM catalogue, so isolated worktrees use the actual chip. The
new capture is kept separately at `$KIS2_CAPTURE_ROOT/full-run-art-audited`;
the original unaudited candidate remains intact for comparison.

## Sealed audited capture

TraceChaser implementation: `e0a2443e086ca657a49227c5467eeecd06e40ece`,
merged and pushed to its `main`. The capture used the reviewed observer with
its corrected ROM-art bounds; the subsequent owner/profile rejection check
only strengthens validation and does not change emitted observations. Every
captured edge's owner and callback was independently checked against that
final restriction.

`full-run-art-audited/` contains 110 files, 23,905,326 stored bytes, 36 segments,
and 248,042 rows. Inventory `art-candidate-inventory.json` SHA-256:
`e9a3d82132e65a485ff14b61de5a517bf43846061d02a710949c7c8ea50f3c9b`.
Whole-file comparisons establish identical physics rows, existing aux events,
and non-art manifest fields against the initial candidate. Metadata changes are
limited to `aux_schema_extras` and explanatory `notes`; added aux events are
`dynamic_art_transfer_state` and level `load_queue_state`.

The audit contains 126,390 normal Knuckles and 18,626 SS Knuckles segment edges,
plus 79 run-gap edges. The last gap records one accepted pending transfer at
movie frame 253,736, where the recorded level route ends; no later completion is
invented. All normal requests use the converted RAM bank and remain within its
`$500`-byte capacity. Source BK2 and all payload stored/logical hashes are sealed.
This is a candidate, not a claim of gameplay parity or canonical publication.

Additional native `test.sh --no-gates --jobs 1` at `e0a2443` completed with
734 passes, 57 failures and 35 skips. All 57 failures were reproduced by bounded,
matched failing-family checks on unchanged `9fd957b`, comparing test identities
and failure messages (normalizing temporary-directory identifiers). These cover
stale source/fixture paths, audio-observer prerequisites, Lua vector assumptions,
and existing special-stage observer expectations. The skipped ROM-dependent CLI
cases and omitted gate tier are not passing coverage.

## Consumer verification and next frontier

Java verification used `feature/ai-kis2-full-run` at `d94ac94c2` plus the
prerequisite working changes, pinned against integration base `51677cdd2`.
All Maven commands used `python3 tools/testing/maven_queue.py`, Java 21,
`-Dmse=off`, and verified absolute ROM paths. Initial focused selection
`TestDynamicArtTransferTrace,TestDynamicArtLifecycleService,TestKis2GamePatchResolution,TestKis2SpecialStageDataLoader,TestKis2HeadlessBoot`
passed 71 tests without skips after correcting a missing test import.

Combined `LUA_BIN=/usr/bin/lua5.4 run_categories.py --base 51677cdd2 --run`
completed 20,152 ordinary tests (two failures, five errors, 17 skips; 478 seconds)
and 667 guard tests (four failures, no errors/skips; 173.74 seconds). The ordinary
errors exposed a real initialization regression: controller-only special-stage
setup has no art loader. Priming now requires the loader, matching the existing
publication path; no substitute owner is invented. The three affected classes
were `Sonic2SpecialStageComparisonStateTest`, `Sonic2SpecialStageSwapFlagTest`,
and `Sonic2SpecialStageTeamSetupTest`.

The corrected focused run added those three classes and both audio CLI classes
to the initial selection: **101 tests passed, no failures/errors/skips**. The
recorder gitlink and its two exact-pin guards were updated together to `e0a2443`;
`-Pguards -Dtest=TestTraceChaserBoundaryGuard,TestBuildToolingGuard#traceChaserStaysExactOptionalAndOutsideOrdinaryBuilds`
passed all 13 tests with no skips. These are narrow corrections after the broad
run, not a second broad green result.

Four unrelated failures were matched by identity and exact failure message on
an isolated unchanged `51677cdd2` checkout (four tests, four failures, no skips):

- `TestBuildToolingGuard.supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`:
  stale assertions still require the pre-queue Maven guidance.
- `TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree`:
  existing `FbzRouteEvidenceProbe#printEvidence` and
  `LevelSolidityMapProbe#writeSolidityMap` have no recognized assertion.
- `TestCompleteRunAudioCli.freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`:
  inherited `JAVA_TOOL_OPTIONS` output precedes its expected stderr prefix.
- `TestS1GameplayAudioTimelineCli.shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup`:
  the shell rejects the same ambient `JAVA_TOOL_OPTIONS` as designed.

The two CLI classes pass without that ambient variable, as verified in the
101-test correction run. The two unrelated source guards remain baseline failures.
Broad skips include explicitly opt-in measurements/routes, unavailable surfaceless
EGL, an existing CPZ spin-tube assumption, and unrequested audio-reference captures.

Queued `-Dtest=TestKis2CompleteEmeraldRunChain
-Dopenggf.trace.kis2.run.dir=$KIS2_CAPTURE_ROOT/full-run-art-audited
-Dkis2.rom.path=<absolute lock-on dump>` with absolute S2/S3K properties validated
**all 36 segment payloads and the entire dynamic-art gap ledger**, then entered
production gameplay. One test failed, zero skipped: segment 0 lost production
ownership before source closure, `mode=TITLE_CARD`, `loadGeneration=3`, EHZ1,
**BK2 cursor 2003**. This removes the former missing-capability blocker. The
structural stop does not identify the earliest physics mismatch; the earlier
standalone row-156 result above belongs to its separate historical invocation.
Canonical installation remains subject to approval of the sealed candidate.
