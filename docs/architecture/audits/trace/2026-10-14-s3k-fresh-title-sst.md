# S3K fresh-title SST ownership — 2026-10-14

Source baseline `5b2ff8e7851b99dd40998dfc41fbfd21c80031ef`, isolated
`.worktrees/ai-s3k-parity-trace`. The shipped locked-on ROM is SHA1
`CFBF98C36C776677290A872547AC47C53D2761D6`, CRC32 `63522553`.
Comparison fixtures remain V5 and unchanged; no comparison row supplies gameplay.

## Matched current frontiers

All commands below use Java25, the normal local Maven queue, `-Dmse=off`,
absolute `-Ds3k.rom.path`, and `JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`.
The listed selectors replace `<selectors>` in:

```sh
python3 tools/testing/maven_queue.py -Dmse=off <profile> \
  '-Dtest=<selectors>' '-Ds3k.rom.path=<absolute verified ROM>' test -B
```

| Baseline selector / lane | Completed result | Earliest mismatch |
| --- | --- | --- |
| `TestS3kAizTraceReplay#replayMatchesTrace,TestS3kSonicTailsHczSegmentTraceReplay#replayMatchesTrace,TestS3kSonicTailsCompleteEmeraldRunPrefix`, `-Ptrace-replay` | 11784: 4/0/0/0, 08:09:38 BST | Original AIZ 20,463 and initial HCZ 3,519 compared observations: no errors/warnings/bootstrap differences; both prefixes pass. |
| `TestS3kAizZoneSliceTraceReplay#replayMatchesTrace,TestS3kSonicTailsHcz2SegmentTraceReplay#replayMatchesTrace`, `-Ptrace-segments` | 51040: 2/2/0/0, 08:12:35 BST | Alternate AIZ: 60 spans (25 physics, 35 animation), row25,589 animation `$13/$05`. Standalone returned HCZ: 3,433 spans, row0 Y-speed `$00/$0E`; this bootstrap is not the actual returned chain. |
| `TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds`, `-Ptrace-replay` | 23744: 1/1/0/0, 08:18:10 BST | Segment6: 189 physics field mismatches, row3,319 Tails X `$31C1/$31CA`. Segment8: 12,719 (12,604 physics,115 animation), row1,583 Tails X `$366C/$3674`. Initial HCZ segment9 is green. Returned HCZ segment11: 82,067, mapping row1,507 `$63/$95`, Y row1,510 `$7D6/$7DF`; stops after5,725/14,208 rows at input68,801 after losing LEVEL ownership. |

Standalone `DivergenceReport.totalErrorCount()` counts compressed mismatch spans.
The continuous chain `LiveTraceComparator.errorCount()` counts each mismatched
field on each row; its 189/12,719/82,067 totals are raw field mismatches. Neither
metric counts distinct incorrect rows.
The October12/13 alternate count59 is historical; current matched baseline is60.
No later standalone bootstrap is substituted for continuous-chain evidence.

## Earliest causal clock and slot observations

A read-only native GPGX adapter replays the unchanged Genesis inputs, records
frame-end RAM and execution callbacks, and never writes native RAM. The movie is
`src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2`,
SHA256 `ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`.
TraceChaser source is `e0a2443e086ca657a49227c5467eeecd06e40ece`.
Native jobs14387 and5837 completed through ordinary queued `exec:exec`, with
39057 and35735 original inputs respectively. Their callback clock is aligned by
`Level_frame_counter` and `ChangeRingFrame` call ordinal, not an emulator frame
label alone. Native callback `$780A` adds `$0180` to the persistent AIZ vine
angle `$FEBA`.

Native and engine agree through input12840 / level-counter`$0FAD`. Native
sets `Game_mode=$34` on that entry row and stops the Level loop. Engine runs
one additional normal level pass at12841 /`$0FAE`, advancing `$EE80` to`$F000`.
The cause precedes the later vine carry difference; changing vine geometry,
ring-angle persistence or a local carry offset would fit the symptom.

At second special-stage entry native ring SST18 allocates flash SST22 above
itself, so the new flash initializes in the same dispatch at input12798 /
`$0F83`. Engine ring25 allocates flash17 below itself and initializes next pass.
The flash's own43 dispatches already match: init, nine animation passes,33
wait passes (`$20` through underflow). Changing this wait or forcing all flashes
AfterCurrent is rejected.

The first earlier occupancy gap is native fresh title SST8 plus children9–12
at the AIZ1 special-stage return (run segment2). Native `Level/loc_62B6` writes `Dynamic_object_RAM+5` before
`loc_6468 Load_Sprites`; the dynamic base is absolute3. Initial sprite dispatch
is followed by `loc_64DC` seeding `$2E=$16`. `Obj_TitleCardWait2` reads the child
count before higher-slot children; each child consumes previous draw visibility,
so the lower-slot owner tears down only on the next pass after the last child.
This lifecycle survives optional title presentation. D01 and competition
`Obj_TitleCardInit` delete before child/archive admission; `$1701` bypasses the
owner entirely. Real AIZ1 introduction remains a separate semantic admission.

The read-only runtime was corroborated against the official TraceChaser-pinned
BizHawk2.11 archive SHA256
`cdaf9650d880bae660d63a388430f630b8d8a96b1ba59ebf0e0195a645c3bab8`.
Assembly version2.11.0.0 and all30 required Lua API bindings pass. Actual managed
`BizHawk.Emulation.Cores.dll` is `0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7`;
native `gpgx.wbx.zst` is `c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12`.
The earlier local runtime's consumed files match the verified official bytes.
This is native behavior corroboration, not a self-certifying trace fixture.

## Implemented slice and qualification

A load-scoped S3K collaborator installs fixed SST8 and its actual four children
before placements. The owner activates after the existing initial dispatch,
retains the22-pass wait and native stagger, then publishes the existing
art/Hyudoro teardown while still occupying its slot. Children capture their
own native screen position, prior visibility and exact parent2 identity.
Pure recreation allocates no duplicate children. The existing title manager
still owns optional rendering and runtime art; in-level and carried-results
owners retain their existing contract. No generic queue, allocator, angle,
hardware timing authority, public API or trace comparison behavior changes.

`TestS3kFreshTitleCardSstGraph` initially fails4/4 on the clean baseline because
SST8 is a placement or empty. Its corrected native admission and full graph
recreation pass with the existing teardown and in-level-title consumers.
A transient candidate's D01 check exposed unset `LevelLoadContext` zone/act:
using the actual admitted destination corrects that production path, rather
than weakening the oracle.

Completed affected checks before the final preset-only test correction:

- 12131: title graph4 + teardown11 + in-level progression3 =18/0/0/0.
- 2190:29 cases, only newly added D01 failure; all graph replay, fresh boundary,
  GameLoop handoff and carried-results checks pass. The admission correction
  follows this negative.
- 19719: corrected graph7 + `TestS3kEndingNativeObjectClear`2 +
  `TestS3kEndingLifecycleCapture`9 =18/0/0/0.
- 94207:98/0/0/0; mandatory startup60, PLC art rewind25 and bounds3 remain
  qualified. Its graph10 used three extra custom widths and was not a claim
  about the five production presets.
- 35745: rewind architecture, source architecture, trace invariant and hardware
  authority guards =111/0/0/0.
- 2446: originalAIZ, initialHCZ and first-entry prefix =3/0/0/0 at09:26:31 BST.

45290 completes the final graph-only check10/0/0/0 at09:32:19 BST,
26.907 seconds execution and zero queue wait. It uses actual `WidescreenAspect` presets
320/352/400/528/800 and asserts the camera width before and after native load.
Two independent graphs, pure owner/child recreation, exact whole-registry
capture equality and two forward replay cycles cover retirement at each width.
These are graph/lifecycle checks, not native pixels/audio or whole route products.
Actual incoming seven ending cases remain lead-owned composed-consumer coverage.

Post-repair strict job8113 completes2 cases/one expected chain failure at09:10:58
BST. The two-return prefix passes. The chain retains exactly189/12,719/82,067
raw field mismatches and the input68,801 stop. Fresh SST presence now matches through AIZ return segment2
row126, but at127 native MonkeyDude has five actual arm recordsSST10–14 while
the engine aggregates those parts inside the body. Ring25/flash17 still differs
from native18/22. This title slice closes a native ownership omission but does
not move a strict trace frontier. The next slice must implement those real
MonkeyDude dispatch/lifetime owners before attributing the downstream flash.


## Fresh native RNG clear — separate follow-up over ddbc651e0

`Level/loc_60DE` unconditionally clears `Lag_frame_count,$58`: `$F62A..$F681`
includes `RNG_seed` at `$F636`. This precedes new sprite dispatch and includes
ordinary stage-return, death/restart and ending loads. Neither an in-place
seamless continuation nor preview executes this native Level entry. The
S3K profile uses the existing FULL/fresh-assembly semantic admission
(`permitsInitialProcessSpritesRequest`) to clear the retained `GameRng` owner,
without resetting V-int, the persistent AIZ vine angle, shared RNG arithmetic,
or comparison data. The nine-line production candidate has SHA256
`fec34929b7207acba3961f2b0cb883a5dd0938f24978452e511d3861dcef2639`.

Read-only existing `aiz_2` aux evidence records fresh seed0, then native Monkey
root at row159 produces `$8010B493`; an animal at176 produces `$6037B943`.
The old engine inherits special-stage seed `$6E37EB8B` and consequently consumes
a different air-bubble sequence. The temporary engine observer samples PRE_ROW,
whereas native aux is completed-row state: align actual level counter and owner
call, never subtract movie labels alone. `Random_Number` returns folded D0 low
`$8010`, while the swapped stored seed is `$8010B493`; the first candidate test
incorrectly expected stored low `$B493` as D0 and was corrected from source.

`TestS3kFreshLevelRngState` covers actual HCZ1 and native D01 fresh entry, complete
registered-world restoration and two RNG replay cycles, plus actual preview and
AIZ seamless reload negatives. D01 deliberately has no ordinary title boundary;
its seed clear remains owned by native Level entry. Baseline64532 is4/2/0/0;
corrected3259 passes4/0/0/0 at10:49:57 BST,25.926s execution/59s queue.
Candidate17153 has103 cases:99 affected consumers and the two preservation
negatives pass; its two new replay cases fail only the incorrect D0 oracle.
Those99 consumers include fresh boundary2, init profile6, golden RNG/rewind,
title graph10, ending lifecycle9, native clear2 and mandatory startup60.
The production candidate is unchanged between17153 and3259.

Commands retain Java25, surfaceless EGL, verified absolute locked-on ROM and
normal queued Maven:
`python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestS3kFreshLevelRngState,TestFreshLevelBoundaryRewindHeadless,TestSonic3kLevelInitProfile,TestGameRngGoldenSequence,TestGameRngRewindSnapshot,TestS3kFreshTitleCardSstGraph,TestS3kEndingLifecycleCapture,TestS3kEndingNativeObjectClear,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' '-Ds3k.rom.path=<verified absolute ROM>' test -B`.
3259 selects only the new RNG class.

Actual incoming64382 (`-Dtest=TestDezIncomingFinalRouteCapture`) completes7 cases
at10:52:39 BST,108s execution/0s queue: all five cold ordinary/team/Tails/wide/
emerald ending compositions pass, but the two positioned320/800 fight cases
error with `title owner is missing its runtime-art admission lease`. Matched
38983 selects `TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase`
with this production profile restored to exact ddbc HEAD bytes; it completes
2/0fail/2errors/0skip at10:59:19 BST,75s execution/66s queue, with the identical
lease errors. This is already present on ddbc and exonerates the RNG change only. It was
not established as pre-existing on pinned5b2 and remained a new unresolved
fresh-title consumer regression for this follow-up.

Strict89375 uses the exact85471 selector/profile in the Monkey audit:5/1/0/0
at10:55:02 BST,105s execution/145s queue. Original AIZ, initial HCZ and both
prefixes pass. The continuous chain retains exactly189/12,719/82,067 raw field
mismatches, the same first fields and input68,801 stop. Alternate33205 uses
`-Ptrace-segments -Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace`:1/1/0/0
at10:53:16 BST,35.686s execution/107s queue,60 grouped spans and first25,589
animation`$13/$05`. Closing the earlier presence gaps at726 and888 is causal
graph improvement; no strict trace frontier closes. The first sampled remaining
segment2 physical mismatch is native projectile SST43 at969, `$272B/$0444`,
56px above cameraY`$047C`. Native `Sprite_CheckDeleteTouchXY` admits Y−128..384;
the current Java projectile culls immediately outside48px. That owner is the
next separately bounded source/lifetime slice. Second-entry ring/flash still
remains engine25/17 versus native18/22.

Final profile/source/rewind/trace/hardware guards69781 pass111/0/0/0 at
11:05:16 BST,76s execution/0s queue. This is affected validation, not a
broad smoke or full-suite pass.


## Act3 continuation bypass — whole graph admission

The positioned DEZ cases exercise native `loc_63C14` and `loc_7F310`:
`Act3_flag` is set before `StartNewLevel` for internal `$1600/$1700`.
At `Level/loc_62B6` that flag skips the entire fixed title graph. The existing
`LevelContinuationCarry` already retains the equivalent LOADING phase and
uses it to suppress the initial presentation. The new fresh-SST collaborator
had admitted SST8 and four children despite that bypass; their later teardown
attempted to consume a title-art lease which the continuation never created.

The repair exposes a read-only query of that existing semantic carry phase
and uses it during S3K graph preparation. It adds no captured state, lease
fallback, zone/frame/movie condition or queue behavior. Ordinary fresh entry,
D01 exclusion, preview and seamless admission remain under their previous
owners. REQUESTED/LOADING/LOADED assertions prove that the query neither
consumes nor changes carry state.

The first short draft2177 incorrectly expected a title graph after an ordinary
positioned fixture boot; that boot deliberately requests no fresh runtime art.
It failed2/2 and was discarded as a wrong oracle. Correct native-continuation
negative37991 fails2/2 because actual `$1600/$1700` loads retain unwanted title
objects. The final independent admission class checks both destinations at
actual presets320/352/400/528/800, camera width before/after load, absence of
both owner and children, no pending title/fresh boundary, and48 ordinary input
updates without an orphan teardown. These declared bridge checks are separate
from actual incoming fights.

Completed candidate validation over projectile3ac656deb, retaining Java25,
surfaceless EGL and the absolute verified locked-on ROM:

- 58480:119/0/0/0 at11:33:58 BST,152s execution/316s queue. New admission10,
 carry7, title graph10, fresh boundary2, RNG4, init profile6, API pins9,
 ending lifecycle9/native clear2 and mandatory startup60.
- 72545:32/0/0/0 at11:38:14 BST,129s execution/24s queue; exact
 `TestSonic3kPlcArtRewindSnapshot`25 and `TestDezIncomingFinalRouteCapture`7.
 Both actual positioned320/800 fights and all five cold compositions pass.
 No movie, fight oracle, or lease fallback changed.
- 45693:canonical strict five cases,5/1/0/0 at11:43:35 BST,98s execution/21s
 queue. Original AIZ, initial HCZ and both prefixes pass. Chain segments6/8/11
 retain189/12,719/82,067 raw mismatched fields, the same first fields and the
 input68,801 LEVEL-ownership stop.
- 68188:`-Ptrace-segments -Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace`,
 1/1/0/0 at11:47:19 BST,37.436s execution/1s queue. Alternate retains60 grouped
 spans, first25,589 animation`$13/$05`.

58480's exact class selector was
`TestS3kFreshTitleCardCaptureLifecycle,TestLevelContinuationCarry,TestS3kFreshTitleCardSstGraph,TestFreshLevelBoundaryRewindHeadless,TestS3kFreshLevelRngState,TestSonic3kLevelInitProfile,TestModApiSignatureSurface,TestS3kEndingLifecycleCapture,TestS3kEndingNativeObjectClear,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
It also contained a nonexistent shortened PLC class name; that name supplied
no coverage. Exact72545 separately supplies the real25 PLC cases. Strict45693
uses the canonical selector/profile recorded in the Monkey audit. These are
affected checks, not a broad smoke/full-suite or strict trace closure claim.

Final affected source/rewind/trace/hardware guards14085 pass111/0/0/0 at
11:49:37 BST,33.532s execution/21s queue. No new consumer or strict regression
is introduced by this admission correction. Native screenshot spot checks
remain separately pending.
