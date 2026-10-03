# S3K trace green campaign — 2026-10-02

## Scope and baseline

The requested endpoint is every S3K trace green, with no regression in S3K,
S1, or S2 traces. Advance and push each verified frontier. Segments remain
diagnostic measurements; run chains must also pass. No comparator weakening,
fixture-derived gameplay state, or fitted delays are permitted.

Integration base: develop `67c850fc51`. Worktree:
`.worktrees/trace-s3k-green`, branch `bugfix/ai-trace-s3k-green`.
The main workspace remains on its original branch.

Use Java 21, the queued Maven wrapper, and absolute ROM properties. This host
has Maven under `/usr/share/idea/plugins/maven-plugin/lib/maven3/bin` and
requires `LUA_BIN=/usr/bin/lua5.4`; the default Lua is not 5.4. Tool preflight
passed with that selection and the installed PowerShell on PATH.

## ROM identity

The original root S3K ROM is CRC32 `0C06AA82`, SHA-1
`b711a909cce238ca4af3e517a2edca306228efa5`, MD5
`cfcc692427348e58682230a27d9e365d`. Following the user's request, a separate
reference was obtained from
[Archive.org](https://archive.org/details/sonic-and-knuckles-sonic-3_202309)
outside the repository. Its independently computed hashes match the fixture:
CRC32 `63522553`, SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6`, MD5
`c5b1c655c19f462ade0ac4e17a844d10`.

Both images contain 4,194,304 bytes. The sole difference is offset `0x2001F0`:
the original has `0x4A`, the reference `0x55`. No existing ROM was changed.
Both four-chain runs completed with the same failure messages; the image
difference does not explain these measured frontiers.

## Initial chain measurements

Command (with the discovered absolute ROM paths supplied):

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain,TestS3kKnucklesSuperEmeraldRunChain,TestS3kMegaRunChain,TestS3kTailsFullChainRunChain \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" \
  "-Ds3k.rom.path=$S3K_ROM" test
```

At the integration base, with the matching reference ROM: four tests, three
failures, one error, zero skips. This is a focused chain baseline, not a fleet
pass. Explicit test selection includes the Sonic+Tails class alongside the
three deferred chains; none has the excluded `trace-scope-r6` tag.

| Chain | Observed frontier |
|---|---|
| Knuckles super emeralds | AIZ giant-ring exit never observed; segment 0 has 12,600 errors, first non-camera mismatch row 446 `y_speed` |
| Multibonus | Segment 0 has 18 camera errors; gumball segment 1 first non-camera mismatch row 1 `x`, then duplicate `VINT_SERVICE` at raw frame 1053 during title-card update |
| Sonic+Tails emeralds | AIZ segment 0 has zero errors; special-stage return exhausts physical interval at movie cursor 8817 while still in `TITLE_CARD` |
| Tails full chain | AIZ segment 0 has 17 camera errors; special-stage return exhausts physical interval at movie cursor 6221 |

Sonic+Tails return diagnostic: title state `DISPLAY`, state timer 56, hold
90, art loaded, no pending title art handles. The assertion remains strict;
the diagnostic supplies no state to gameplay. Investigate native `Level`
`loc_62CC` title/PLC admission and `loc_64DC` timer overwrite before changing
the return lifecycle. Merely reducing a hold to fit the gap is not evidence.

The initial camera discrepancy in the non-intro AIZ starts is `0x1308`
versus `0x1300`; it reconverges after the opening rows. Native
`Get_LevelSizeStart/loc_1BF74` subtracts `0xA0` and clamps to zero/max,
whereas the engine's generic forced camera snap also clamps to the minimum.
Any correction must preserve native cold-load focus overrides and runtime
resnap semantics.

## Fleet baseline

The unchanged-production baseline runs the three profiles separately, each
with one fork, alphabetical order, matching ROMs, and fresh profile-specific
Surefire reports: `trace-replay`, `trace-replay-r7`, `trace-segments`.
Their filename selections contain 216, 110, and 69 top-level test classes
respectively; nested tag gates further determine executed cases. Completed
counts, skips, failure messages, and comparator profiles must be inspected
before using them for a no-regression claim.

Completed baseline results (all three Maven processes exited normally with
reported test failures, not fork crashes):

| Profile | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| trace-replay | 858 | 52 | 0 | 19 |
| trace-replay-r7 | 113 | 85 | 10 | 0 |
| trace-segments | 70 | 53 | 7 | 0 |

The 19 release-profile skips include five opt-in performance/report audits,
a missing KiS2 lock-on ROM, two unrecorded bonus-roundtrip fixtures, and 11
Sonic 2 special-stage cases incorrectly requiring a literal `s2.gen` despite
a valid `sonic2.rom.path`. The latter checks are being repaired against the
resolved file and measured separately on baseline and candidate; creating a
ROM filename alias would conceal the defect. The remaining skips are explicit
coverage limits, not passes.

No full-green or no-regression claim is made yet.


## First candidate frontiers

`5566b8db17` (worker `cc0cbed9da`) completes the initial object pass at
bonus title release and begins interior comparison at row zero. Native
`loc_6468` runs `Process_Sprites` before `LevelLoop` for bonus zones too.
The prior retained setup token consumed the first gameplay input as setup;
the harness additionally assumed a now-removed same-step gameplay fallthrough.
The gumball segment of `TestS3kMegaRunChain` moves from 8,021 errors / first
row 1 `x` to 12 errors / first row 1276 `x`. Its later duplicate VINT boundary
moves from raw 1053 to 1296. The chain remains red. Worker focused command
`maven_queue.py -Dmse=off -Dtest=TestS3kInitialObjectSetupLifecycle,TestGameLoop
-Ds3k.rom.path=<reference> test` passed 108 tests with zero skips.

`56afa227ad` (worker `8f1e894d3b`) adds a game-internal level-start camera
capability implemented by the existing S3K zone provider. Shared orchestration
calls the capability before resetting object placement. Ordinary forced camera
snaps and S1/S2 behavior are unchanged. MHZ1's native separate focus register
is preserved explicitly instead of relying on minimum-bound clamping.
Knuckles AIZ segment 0 errors fall from 12,600 to 12,566; first non-camera
row 446 `y_speed` remains. Multibonus segment 0 falls from 18 errors to one,
first row 4545 `camera_y`. The worker's MHZ slice retains 3,191 errors and
first row 6958 `rings`. Focused final-scope camera/DEZ validation passed 17
cases; a preceding broader candidate passed 140 cases, including real MHZ
Sonic, Tails, and wide cold routes. Those preceding results are not attributed
to the final narrowed source. Character identity follow-up and combined
regression verification remain pending.

Inherited camera gaps remain separate: ICZ1 Tails' complete start/bounds
bootstrap is absent, so a camera-only partial override was rejected. Cold
intro offsets for other zones are outside the initial AIZ/minimum correction.


The initial camera candidate also makes Tails full-chain segment 0 green:
17→0 errors, `complete=true`. Its chain still fails at destination 6221.
The follow-up review replaced an instance-name check with character identity.
A worker edited that source while an earlier Maven compilation was live;
the class file was then newer than the edit although it still contained the
old method body, causing Maven to report “Nothing to compile.” `javap`
identified the stale `getCode` call. Treat that failing identity test as an
invalid candidate build until the changed source is explicitly recompiled;
do not attribute it to the new implementation.


Supplementary S2 baseline at `67c850fc51` with only the three ROM-availability
guards repaired: queued `-Ptrace-replay -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical` selection
`S2SpecialStageFinishBoundaryMappingTest,S2SpecialStageReplayDeterminismTest,TestS2SpecialStage1TraceReplay,TestS2SpecialStage2TraceReplay,TestS2SpecialStage3TraceReplay,TestS2SpecialStage4TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2SpecialStage7TraceReplay,TestS2SpecialStageTraceReplay`
with the absolute `sonic2.rom.path`: 12 tests, four failures, zero errors/skips.
Eleven previously skipped cases now run (the twelfth was already ROM-independent).
Stages 2/5/6/7 have 12488/13339/16370/16993 errors respectively, first row 0
`dynamic_art.outstanding_transfer_ids`; these are baseline failures, not caused
by the S3K candidate. Remaining cases pass, including deterministic replay and
both terminal-pass mapping checks.


Camera follow-up `d9fd107179` (worker `e8f2c43ed4`) replaces instance names
with character identity and separates explicitly positioned captures from cold
load camera initialization. Its 22 focused tests pass without skips, including
an actual positioned MHZ capture. Applying that same positioned operation to
run-chain metadata restores the 17 AIZ camera errors. A subsequent uncommitted
three-context experiment restores AIZ but adds an MHZ slice row-zero camera
error. Neither result establishes a no-regression camera candidate. The owning
cold-start versus positioned-bootstrap contract is under investigation; no
coordinate or fixture-name predicate is accepted.

`7e256310e4` (worker `ca79e59364`) corrects Monkey Dude's body continuation
and raw animation timer. Once `Obj_WaitOffscreen` restores operation `$8715A`,
the body keeps executing beyond the initial visibility window. Native
`loc_871C2`/`loc_87218` reset the raw timer to zero. The timer-only experiment
was rejected as incomplete: it exposed an earlier row 419 hurt and 14,717
errors, while the engine's repeated visibility gate had lost 66 body dispatches.
Correcting both owners yields 41 Knuckles segment-zero errors, with first
non-camera disagreement at row 1615 `queue.s3k_kos_direct.busy`, versus baseline
12,600 / row 446 `y_speed`. The worker has no camera fix; remaining camera
errors are included. The chain now reaches the uncompared interior and exhausts
the return interval at destination 8423.

Worker selection `maven_queue.py -Dmse=off
-Dtest=TestMonkeyDudeBadnikInstance,TestS3kKnucklesSuperEmeraldRunChain
-Ds3k.rom.path=<reference> test` exercised three passing object regressions
(including offscreen continuation and restore) plus the still-failing chain.
The required S3K initialization/loading/bootstrap/decoding selection then passed
59 cases with zero skips. These are focused results on the worker commit;
combined campaign and cross-game verification remain outstanding. The retained
worker XML confirms the selector but not its Maven profile; the worker matrix
records `trace-replay-r7`. Root independently reproduced the 41-error segment
and return failure at 8423 using explicit `-Ptrace-replay` on `86c24c1040`
plus a temporary observation-only flash probe, removed after measurement.


## Native results/return phase partition

Worker `c0ac004516` models the native sixty-VBlank `Demo_timer` exit instead
of waiting for the player spin's 96-tick timer plus a generic fade. It also
restores title resource ownership and the later 22-tick gameplay tail. Its
focused GameLoop/initial lifecycle/snapshot/title batches pass 93/19/8/10 cases
respectively, with no skips; combined trace verification is separate.

Native BizHawk captures using the verified reference and original BK2 live at
`$HOME/captures/s3k-special-return-20261002/native3/` and `native4/`.
The first two captures used a stale recorder alias for Kosinski RAM and their
Kosinski columns are invalid. The corrected locked-on addresses are `$FF60`
for modules-left and `$FF64` for the module queue; `$FF04` was not authoritative.
Independently verified mode/title/Nemesis fields remain useful. The measurement
hazard is recorded in the existing briefing catalogue by the worker.

Corrected observations: results resources drain at movie row 7411, owner
installation/init occurs at 7412/7413, return title owner appears at 8687,
title archives drain by 8696 and child creation occurs at 8697. Children reach
idle at 8726; Nemesis finishes at 8767; terrain KosM work drains at 8793.
The remaining interval is not an inferred title or palette wait:
`LoadLevelLoadBlock2` directly decompresses blocks/chunks through `Kos_Decomp`
at 8793–8812, then `Setup_TileRowDraw`/`VInt_VRAMWrite` fill tile planes at
8813–8816. Mode LEVEL and title timer 22 appear at 8817 (still a lag row);
the first level iteration follows at 8818. Existing per-row lag admission
must preserve these CPU-work intervals without introducing a fitted 24-tick
countdown. These observations supply evidence, never gameplay state.


## Entry-flash callback order

On `86c24c1040` plus the flash correction, queued `-Dmse=off -Ptrace-replay
-Dsurefire.forkCount=1
-Dtest=TestSonic3kSSEntryRingFormation,TestS3kSsEntryFlashGraphRewind,TestS3kKnucklesSuperEmeraldRunChain,TestS3kSonicTailsCompleteEmeraldRunPrefix
-Ds3k.rom.path=<reference> test` completed 31 cases: 30 pass, one known Knuckles
return failure at 8423, no errors/skips. The initial queue request was terminated
143 before Maven started; only the completed retry is validation evidence.

`SSEntryFlash_Main` calls `Animate_RawAdjustFlipX` before inspecting its advanced
counter and changed mapping. Java previously inspected the old index. A new
independent regression failed on the old implementation because the parent was
not marked on its third animation advance. The corrected object/graph suites
pass 25+4 cases, including recreation and replay across the deletion edge.
Knuckles segment-zero errors fall 41→34, all remaining errors being opening
camera X; no non-camera mismatch remains. Sonic+Tails' existing giant-ring
prefix stays green with zero opening-segment errors. The flash completion still
uses 43 object dispatches; no transition wait or recorded input changed.


## Integrated camera and results candidates

Camera correction `ee8565ab6e` (worker `5b4dbc2f28`) preserves the camera
established by production loading for continuous runs while retaining existing
compatibility player/ground bootstrap. Isolated declared-position replay and
start-at-segment diagnostics retain their positioned initialization. Removing
all compatibility player setup was rejected: S2 gained 26 bootstrap history-Y
mismatches (`$290` versus `$28F`), despite unchanged compared gameplay. The
bounded correction restores exact baseline equality across twelve S1/S2 report
projections, including zero bootstrap errors. Both isolated MHZ checks remain
unchanged. The worker's new Tails opening-through-first-special-stage pin and
startup checks pass 29 cases with zero skips. Root additionally registers the
new prefix class in the r7 profile's explicit include list.

Results prelude `df0b6eaab2` (worker `8888b57370`) submits the four native KosM
parents and Nemesis ring-HUD job before installing the results owner. Its
14 resource iterations arise from ROM work: the 38-pattern HUD entry and
VInt_1E's three-pattern service budget, including its preparation boundary.
No elapsed-frame constant supplies readiness. Focused preparation/tally/handoff
checks and required S3K/mapping checks pass; the worker measured 41 focused,
60 required/mapping, and a later strengthened 23-case batch, all without skips.
With `c0ac004516`, Sonic+Tails advances past the old title block to a return
hardware-ordinal rejection (next45 versus recorded direct span27..42).
Knuckles advances beyond return8423 into segment2, which has 37,767 errors
and later loses production ownership at movie12011. These are later frontiers,
not green chains.

Tails still stops at6221 in SPECIAL_STAGE_RESULTS. Rechecking the retained
matched baseline r7 summary confirms the baseline was also SPECIAL_STAGE_RESULTS;
an earlier conversational TITLE_CARD attribution was incorrect. The identical
cursor alone was insufficient evidence, but the saved mode resolves this
particular attribution. Native Tails capture independently identifies an existing
results-bonus condition error; that follow-up remains outside this frozen batch.

Terrain selection `56b7972f99` (worker `ce26682b63`) fixes the exact two extra
Sonic+Tails jobs. The loaded level now retains immutable resolved terrain sources;
delayed submission no longer re-resolves the consumed Saved2 flag. An independent
real-return load/clear/submit regression fails before the change with secondary
source `$3A647C` (intro, seven modules) instead of `$3A944E` (return, five).
The live chain ledger corroborates results direct27..31, title32..35, primary
terrain36..37, then erroneous secondary38..44. The fixed regression, mapping
guard and ten title-queue cases pass without skips. No public API or timing
admission changes are part of this correction. Acceptance of an already consumed
recorded ordinal span is a separate follow-up, requiring exact production receipts.


## Combined frontier check and first frozen sweep

On `56b7972f99` plus the prefix registration/assertion changes, queued
`-Dmse=off -Ptrace-replay-r7 -Dsurefire.forkCount=1
-Dtest=TestS3kKnucklesSuperEmeraldRunChain,TestS3kTailsFullChainRunPrefix,TestS3kSonicTailsCompleteEmeraldRunChain,TestS3kTailsFullChainRunChain
-Ds3k.rom.path=<reference> test` completes five cases, zero skips: two green
opening-to-special-stage pins, two full-chain failures and one full-chain error.
Knuckles opening segment0 is complete with zero errors (baseline12,600);
Tails opening is complete with zero errors (baseline17). Knuckles later loses
segment2 ownership at movie12011. Sonic+Tails now reaches a fully consumed span
rejection (`KOS_MODULE_QUEUE` next24 versus recorded14..23), after the terrain
selection repair removed the extra work. Tails remains at6221 in results,
matching the baseline mode. No claim of full-chain success is made.

The first candidate batch is frozen here for separate r6, r7 and segment profile
comparison, followed by normal change-based ordinary/guard validation. The new
Tails prefix is explicitly included in r7; the Knuckles pin is in its already
included chain class. Subsequent bonus-condition and consumed-span work stays
in worker branches during this measurement.


## Frozen first-batch trace comparison — 2026-10-03

Candidate `6e6f13036f`, `.worktrees/trace-s3k-green`; integration baseline
`67c850fc51`. Three queued, single-fork alphabetical profile runs used absolute
verified S1/S2/S3K ROM paths and separate fresh Surefire report directories:
`python3 tools/testing/maven_queue.py -Dmse=off -P<profile>
-Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical
-Dopenggf.surefire.reports=<fresh-target-directory>
-Dsonic1.rom.path=<reference> -Dsonic2.rom.path=<reference>
-Ds3k.rom.path=<reference> test`. Source/POM hashes remained unchanged across
all three completed invocations; no compiler, fork or OOM failure occurred.

| Profile | Baseline tests / failures / errors / skips | Candidate tests / failures / errors / skips |
| --- | --- | --- |
| trace-replay | 858 / 52 / 0 / 19 | 858 / 55 / 1 / 8 |
| trace-replay-r7 | 113 / 85 / 10 / 0 | 115 / 85 / 10 / 0 |
| trace-segments | 70 / 53 / 7 / 0 | 70 / 53 / 7 / 0 |

No formerly passing case became failing. The apparent r6 failure increase is
the independently matched S2 availability repair: eleven previously skipped
cases now execute; the separate unchanged-production check has four of those
failures. Sonic/Tails changes from its old return-boundary assertion to a later
completed-span rejection (direct next 43 versus recorded 27..42). All 37 freshly
written chain-report projections match their baseline exactly. The remaining
eight skips are five opt-in audits, the missing KiS2 ROM, and two unrecorded
S3K bonus-roundtrip placeholders. These skips are not passes.

R7 adds two passing opening pins. Knuckles segment0 is complete/zero errors
(previously 12,600), Tails segment0 zero (previously 17), Mega opening 18→1 and
gumball 8,021→12 with first non-camera mismatch 1→1276. Mega's duplicate VINT
frontier moves 1053→1296. Knuckles now reaches segment2 and eventually loses
ownership at movie 12011; that newly reached segment has 37,767 errors, first
row 34 direct-queue busy. Tails still stops at 6221 in SPECIAL_STAGE_RESULTS,
exactly the baseline mode. Existing isolated-segment case outcomes and first
failure messages remain unchanged.

Comparison scope: all case statuses/counts/first failure messages, plus retained
chain-report projections (including recent mismatch detail). The temporary
first-batch collector recognizes camel-case chain reports; it did not retain
the standalone snake-case report payloads from the original baseline. The
current candidate's standalone reports now have bounded comparison fingerprints
for the next batch. Do not present this as byte-for-byte baseline comparison of
every standalone report, or as an all-green trace sweep.

Normal delivery validation follows against the actual 67c850 integration base:
2,958 ordinary classes plus separate structural guards. Tool preflight passed
(Java21, Lua5.4, PowerShell); the ordinary execution is in progress. Subsequent span,
perfect-bonus, cup and AIZ-lock fixes remain excluded from this frozen batch.


Milestone delivery correction (2026-10-03): the user requires each ready
frontier milestone to be committed, integrated into `develop`, and pushed.
Publishing only `bugfix/ai-*` branches is not delivery. The first frozen batch
was published at `6e6f13036f` while its ordinary/guard validation continued;
an isolated `develop` checkout now owns integration, leaving the user's main
workspace branch untouched. Completed task worktrees are removed after their
changes and useful evidence are integrated. Active tests and unfinished changes
remain protected until completion. Neither a push nor partial validation is an
all-green claim.


Ordinary validation limit: run `20261002T234336Z-c89570b4` at frozen engine
`6e6f13036f` timed out after 2,400.65 seconds during
`TestDezIncomingFinalRouteCapture`. Its 2,754 completed class reports contain
23,721 cases, one failure, one error and 130 skips. The failure is
`TestRemainingRewindTailInventory` (expected 1,315/1,072 total/passed objects,
actual 1,316/1,073); baseline attribution is pending. The error is
`TestSonic2VisibleTitleReleasePlcOrdering`: its Mockito title provider does not
invoke the default `shouldCompleteFreshLevelTransitionBoundary` delegation.
The isolated delivery checkout enables that real default method and queues a
focused recheck. Production camera handling is unchanged by this test repair.

Skips include legacy hard-coded `s2.gen`/`s3k.gen` availability checks, unavailable
KiS2 lock-on data, opt-in benchmarks/captures, unavailable graphics contexts,
and a spin-tube route assumption. They are not counted as passes. The S3K ROM
was supplied through `SONIC_3K_ROM_PATH`; the runner emitted explicit S1/S2
properties but did not discover the external S3K file. Tests that demand the
explicit S3K property need separate verification with that property.

The ordinary lane did not reach guards. Prose edits during execution also
changed the runner's whole-tree fingerprint, although runtime/test/POM/fixture
sources remained unchanged. Future category invocations freeze prose as well.
The remaining 221 candidate classes, starting at the interrupted capture in
the POM's alphabetical order, are queued through `maven_queue.py` with their
full original scope and all three absolute ROM properties; the category plan
itself is not narrowed. Separate fresh guards are queued in the isolated
`develop` checkout. This is incomplete validation, not a full-suite pass.


Matched failure attribution completed at baseline `67c850fc51` in
`.worktrees/trace-regression-base`: queued
`-Dtest=TestRemainingRewindTailInventory,TestSonic2VisibleTitleReleasePlcOrdering`
with all three absolute ROM properties completes two cases, one failure, no
errors/skips. The inventory mismatch is identical (1,316 total / 1,073 passed)
and inherited; the S2 title ordering test passes on baseline. Its candidate
error is therefore this batch's test-fixture regression, pending the focused
real-default-method repair check. No inventory pin is changed to hide the
inherited failure.


Delivery check at `6e6f13036f` plus the S2 mock correction, isolated
`.worktrees/trace-s3k-develop-delivery`: queued
`-Dtest=TestSonic2VisibleTitleReleasePlcOrdering` passes one case, no skips.
A fresh queued `-Pguards test -B` completes 672 cases, one failure, no errors
or skips. Its sole violation is `LrzFlameObjectInstance.getShieldReactionFlags`
(`TOUCH_PROFILE_HOOK_WITHOUT_PROFILE`). The flagged source and guard source are
byte-identical to `67c850fc51`; this attribution is a source comparison, not a
separate baseline execution of the guard. The remaining ordinary capture tail
and three explicit-S3K-property classes continue separately. Delivery therefore
has known validation failures/limits and is not certified fully green.


## AIZ fixed horizontal reload bounds — next batch, 2026-10-03

Worktree `.worktrees/trace-s3k-aiz-camera`, branch
`bugfix/ai-s3k-aiz-camera`, candidate over frozen `6e6f13036f`.
`AIZ1BGE_Finish` writes current min/max X `$10/$10`; native
`Do_ResizeEvents` only eases maximum Y. The transition executor correctly
distinguishes current and target writes, but the AIZ request omitted the
engine's horizontal targets, leaving the loaded defaults to move the lock.
The fix pins those two engine targets in the AIZ request. It does not claim
that the ROM writes its stored target X words, nor alter shared camera rules.

The independent existing fire-transition test now ticks boundary easing after
reload. On unchanged production it fails `expected 16, actual 14` (one test,
zero skips). After the fix, queued `-Dmse=off -Ptrace-replay
-Dtest=TestSonic3kAIZEvents,TestS3kAizTraceReplay,TestS3kReplayReferenceClosureIntegration,TestS3kAiz1ReloadRewind,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
-Ds3k.rom.path=<verified-reference> test` completes 127 cases: 125 pass,
two inherited full-trace failures, no errors or skips. The short event suite,
both camera-lock assertions, production reload timeline isolation and all four
required S3K classes pass.

Matched frozen-candidate reports show AIZ 59→57 errors, first 5497 camera X→20302
player animation, and reference-closure 101→99, first 6302 camera X→25589 player
animation. Each removes exactly two camera mismatch spans; after excluding
the derived `cascading` classification, no mismatch span is added or changed.
The remaining animation/physics failures are inherited. This is focused
validation, not a full green route or ordinary-suite claim. The change-based
plan against `6e6f13036f` selects 2,958 classes plus guards; delivery validation is combined
with the next campaign batch, whose shared timing change already needs that
normal broad selection. Wider/donor/team and whole-act coverage gaps remain.


## Completed-span verification and combined AIZ replay — 2026-10-03

Original `bb7f8777b1`, integration equivalent `d3341faf90`, verifies an
interstitial span already completed and claimed by production against every
recorded kind, ordinal and full fingerprint. It neither creates work nor
changes readiness, ordinals or gameplay state. Mixed, partial, missing, extra,
unclaimed and mismatched spans remain rejected atomically. Worker checks:
84 timing/stream cases and 25 authority guards pass without skips; nine
coordinator cases pass. The walker check retains its independently reproduced
malformed two-column CSV failure (37 of 38 pass).

At `d3341faf90` in `.worktrees/trace-s3k-aiz-camera`, queued
`-Ptrace-replay -Dsurefire.forkCount=1
-Dtest=TestS3kSonicTailsCompleteEmeraldRunChain -Ds3k.rom.path=<reference> test`
completes its one chain case with one error, no skips. The second gameplay
segment is now complete with zero errors/warnings and zero bootstrap errors,
removing the previous 46 camera differences. The next handoff rejects pending
KosM parent 30 (MonkeyDude art). The earliest recorded admission failure is
its direct child 56 at raw 3290 PRE_MAIN_LOOP, a lag row; later queue failures
cascade from that missed completion. Pending ownership alone did not establish
that a provider lost the job. A bounded boundary probe is the next diagnostic.
Cross-game traces and combined ordinary validation of this next batch remain
outstanding; the chain itself is not green.


## Perfect bonus follows remaining rings — 2026-10-03

Original `7eff0b147c` / fresh-base equivalent `ca820e7f62` captures the native
remaining-ring word before special-stage reset and before asynchronous results
preparation. `loc_2E3DA` awards the 5,000-point perfect bonus only when that word
is zero; earning the emerald is independent. Native Tails collects12 and leaves52,
so its bonus is zero. Removing the incorrect extra tally reaches native results
completion6068/title6091; no fitted title delay is introduced.

Worker validation:45 results cases pass;64 route/required cases contain61 passes
and three existing trace failures, no skips. A fresh full-owner chain at
`0e50fed9f6` (`.worktrees/trace-s3k-tails-frontier`, queued r7 Tails chain with
verified reference ROM) keeps openingsegment0 green and now completes returned
segment2 with41,653 errors, firstrow199 y449 versus448 and an incorrect hurt
response. The earlier partial worker had firstrow1400; it lacked the root
MonkeyDude changes and is not the combined baseline. Native corroboration now
points to the coconut launch/chain trajectory, under separate investigation.
The perfect-bonus change advances the old results-mode6221 boundary; it does
not certify the newly reachable route as green.


### 2026-10-03: Mega gumball exit ownership

Worker `.worktrees/trace-s3k-mega-exit` starts from frozen `6e6f13036f` plus
`d5f9613e5b` (the already-claimed timing-span fix). Its diagnostic baseline
reproduced 12 bonus-segment mismatches, first row 1276 `x` (`$102` versus `$103`),
and duplicate `VINT_SERVICE` at raw row 1296. Opening AIZ retains one camera-Y
error at row 4545; it is independent of this fix.

A temporary callback probe showed `interceptPitDeath` requesting exit at player
`($103,$32F)`; the real gumball exit child never fired. The ordinary player pass
then finished at Y `$33E` and froze. Native `Obj_GumballMachine` sets
`Disable_death_plane`; `Player_Boundary_CheckBottom` returns without setting
restart. Native exit child `loc_61050` at Y `$368` instead waits for its `$358`
lower bound, reached at row 1277, Y `$35C`. This explains the two missing
movement passes without fitting a delay. The callback probe was removed.

The production correction preserves death suppression without requesting an
exit. `word_610AE` is also decoded as left/width/top/height, giving X
`[-$100,+$100)` and Y `[-$10,+$30)`; the previous trigger and its tests had
incorrectly treated widths as inclusive endpoints. No new persistent state,
trace input exception, or comparator tolerance is introduced.

The next headless issue remains separate: the compared bonus interior uses a
bare engine step, and `TITLE_CARD` stops pumping its playback-row observer.
The timing observer consequently retains the final bonus raw row and rejects
the next title VINT as a duplicate. Merely disabling recorded admission would
be wrong: this bonus segment records real return-title/terrain completions
from raw row 1302. `doExitBonusStage` also still uses the older immediate level
load/title initialization path, so resource ownership needs investigation once
the physical-row driver reaches it.


Candidate verification removes all 12 reached bonus errors; the report stays
incomplete because the next title-card duplicate VINT now occurs at raw 1298
(previously 1296). All 11 focused regressions and 63 required S3K/bonus boot
checks pass with zero skips. Independent original gumball remains green;
Sonic/Tails gumball retains exactly 78 errors, first row 209 `tails_x`, matching
the frozen `6e6f13036f` baseline. Commands and remaining frontiers are in the
2026-10-03 frontier-log entry. The change-based plan over `6e6f13036f` includes
the shared timing milestone and selects the full ordinary suite; that combined
validation belongs to integration, not this focused worker result.


## Resumed delivery and route scope — 2026-10-03

The user resumed the goal and clarified its final scope: fix Sonic, Tails and
Sonic+Tails routes; finish fixes already in progress, including Knuckles work
already underway, then exclude new Knuckles investigations. Other traces remain
regression checks. Implementation and milestone delivery use the isolated
`develop` checkout directly. Ready fixes are committed/pushed to `develop`, and
finished worktrees are removed after preserving unfinished work and evidence.
The model goal tool exposes status changes only. After the user requested the
CLI control, the installed app-server protocol `thread/goal/set` updated the
saved objective and active status to this scope, preserving accumulated usage.

The integrated `c60df46ed6` sweep completes all three profiles with unchanged
source hashes: r6 858/53/1/8, r7 115/85/10/0, segments70/53/7/0
(tests/failures/errors/skips). Compared with frozen6e, no formerly passing case
regresses; two AIZ reload-camera assertions become green. The combined focused
run completes143 cases with one test-isolation error and no skips: the new
bonus death-plane test inherited a Sonic2 gameplay context. Rebuilding its
fixture through `configureGameModuleFixture(SONIC_3K)` fixes the owner rather
than casting or modifying runtime behavior. The matched audio-predecessor plus
bonus-test check passes4 cases with no skips.

The unfinished ordinary tail at6e completes1,530 cases with12 failures and3
skips. LRZ, SOZ and SSZ cold-route/rewind failures need bounded matched baseline
attribution; they are currently unattributed, not claimed inherited. The earlier
23,721-case prefix and its130 skips remain separate measurements. Neither run
is a full-suite green result.


## Sonic+Tails LBZ cup control handoff — 2026-10-03

The shipped cup capture (`loc_26F26`) writes object_control=$03 once; held
`loc_26FF4` publishes position/presentation without rewriting that control.
Removing the engine's repeated hold write lets the later NPC cutscene release
(`loc_6278A`) remain authoritative. This affects the Sonic+Tails recording;
NPC Knuckles is not a playable-Knuckles route. The old-code regression fails
exactly on reasserted control. Corrected worker checks pass57 distinct cases,
including native P1/P2, extension isolation and rewind; an initial test setup
error was repaired before delivery. The exact source/tests patch applied to
`develop` passes the same57 cases with zero failures/errors/skips via queued
`-Dtest=TestLbzCupElevatorInstance,TestLbzCupElevatorSolidDispatch,
TestS3kLbz1CutsceneGraphRewind,TestS3kLbz1KnucklesSequenceHeadless`, verified
absolute S3K ROM property and fresh `target/lbz-control-integrated-reports`.

Matched worker `-Ptrace-segments -Dtest=TestS3kLbzZoneSliceTraceReplay` advances
4,031→3,304 errors, firstrow18939 x_speed→18945 player_mapping_frame (96 versus55).
Positions/velocities at the prior frontier now agree. The next raw-animation
suppression ownership issue remains open; no forced jump frame is introduced.


## Matched inherited failures — 2026-10-03

A detached baseline checkout at `67c850fc5132156acede8687ca169ada074f795f`
ran the twelve failing cold-route methods plus the single LRZ structural guard
through queued Maven, with verified absolute ROM paths and fresh reports.
The bounded run completes18 cases:12 failures,0 errors/skips. Ten LRZ/SSZ
cold-route failures reproduce the exact current6e assertion and input row;
the eleventh, SSZ solo-Tails rewind at4018, reproduces the exact structural
difference after ignoring only JVM blob identity strings. The LRZ flame
`TOUCH_PROFILE_HOOK_WITHOUT_PROFILE` guard also reproduces identically.
These failures are inherited from the integration base. SOZ solo cold-route
parameter5 passes the isolated baseline method. The identical queued method
on frozen6e completes5 cases with1 failure/0 errors/skips, reproducing death
at15657. This is a confirmed earlier-batch regression on the Knuckles SOZ1
bonus-return cold route, not inherited. Repairing it falls under preservation
of passing routes and finishing the shared work already underway; investigation
is assigned without opening a new Knuckles parity frontier.

Command: `python3 tools/testing/maven_queue.py -Dmse=off
-Dtest=<twelve failing methods plus the LRZ guard method>
-Dsonic1.rom.path=<absolute verified S1> -Dsonic2.rom.path=<absolute verified S2>
-Ds3k.rom.path=<absolute verified S3K>
-Dopenggf.surefire.reports=target/paired-baseline-reports test`.
This attributes particular failures; it does not turn the incomplete ordinary
validation or red guard suite into a passing delivery.


## Live suppressed-row queue closure — 2026-10-03

The AIZ worker's bounded observer probe at raw3288–3292 showed that a LEVEL
lag iteration in the live loop reached only VINT. Prepared direct child56
remained unready and MODULE30 could not complete. Native `LevelLoop` still
reaches its `Process_Kos_Module_Queue`/`Process_Kos_Queue` tail
(`sonic3k.asm:7908/7887`). Reusing `TraceSuppressedRowClosure` joins the live
loop to the recording and standalone drivers' existing semantic closure; it
services POST_OBJECTS/PRE_MAIN_LOOP and title/event VBlank state without
dispatching gameplay. The temporary row-specific print probe is discarded.

Candidate over `3fa9c0a88a`, queued `-Ptrace-replay -Dsurefire.forkCount=1
-Dtest=TestGameLoop,TestTraceSuppressedRowClosure,TestHardwareTimingAuthorityGuard,
TestS3kSonicTailsCompleteEmeraldRunChain,TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
with all three verified absolute ROM properties and fresh reports completes
184 cases:183 pass, one full-chain assertion fails, zero errors/skips.
Sonic+Tails gameplay segments2 and4 now complete with zero comparison/bootstrap
errors. Segment6 completes with189 physics errors, first3319 sidekick_x;
segment8 completes with13,265 physics errors, first1583 sidekick_x.
HCZ segment9 stops at5121 after a runtime art FIFO failure.

Native AIZ_5 tail requests four title archives and two terrain archives
(MODULE97–102, direct144–155). Those production jobs are absent, shifting
the correctly fingerprinted next HCZ job to97 instead of103.
`GameLoop.doZoneAct` uses the generic loader; `RecordingFrameDriver` uses
the existing fresh title-card boundary. The next investigation must restore
that owning lifecycle, not resize the FIFO or resynchronize ordinals.
The frozen three-profile sweep completes r6 858/54/0/8, r7 115/85/10/0,
segments70/53/7/0 (tests/failures/errors/skips), with unchanged source hashes.
No previously passing case regresses against integratedc60. Common standalone
mismatch fingerprints remain exact except the independently delivered LBZ cup
control handoff (4,031→3,304); common chain reports remain exact. The Sonic+Tails
chain now reaches a comparison assertion rather than infrastructure failure,
and four additional segment reports exist. Source timing authority stays
inside prepared, production-submitted ROM jobs. The ordinary combined delivery
selection still spans the full suite and guards; its prior incomplete results
and inherited failures remain explicit, not represented as a green run.


The SOZ single-case A/B on `4435cfb5b8` isolates the regression to
`PostTitleCardDestination`: restoring only the old setup-pass placement passes
the original full cold route and rewind assertions (1case,0 skips). Both
candidates enter Pachinko at3204 and release the title at3311. At3312 the
corrected engine executes the held LEFT (x319/vx-12/air1/map7); the old
engine spends that gameplay input on setup (x320/vx0/air0/map0). Native
`loc_6468` performs `Process_Sprites` before `LevelLoop` for bonus zones too.
That correct first dispatch shifts the engine-authored route's return load
3566 vs3573, and SOZ resume3691 vs its documented3698. Restoring the wrong
runtime cadence is rejected; re-author the controller-only cold-route movie
against the corrected production behavior. Native parity fixtures and their
comparisons remain authoritative and unchanged.


## Solo-Sonic reference coverage — 2026-10-03

The scoped S3K metadata inventory finds274 fixture metadata files:83
Sonic+Tails,70 soloTails,121 soloKnuckles, and no main-Sonic fixture with an
empty sidekick list. Existing Sonic-led native comparisons therefore exercise
Sonic+Tails; they must not be described as solo-Sonic native parity. Solo-Sonic
cold-route regressions remain in scope, and the missing native reference route
is an inherited coverage gap rather than a green certification. New Knuckles
parity frontiers remain excluded; the shared bonus adapter and authored SOZ
regression repair finish work already underway.


## Integrated Monkey Dude child chain — 2026-10-03

Accepted worker patch over `0e50fed9f6` applies to `develop` over `043006aeb8`.
The native five-child arm retains fractional positions, follower delays and
once-only hand release; no body-animation throw, fitted cooldown or vertical
target gate remains. Source owners and rejected approximations are in the
AIZ matrix and mirrored object-pitfall catalogue.

Queued `-Ptrace-replay-r7 -Dsurefire.forkCount=1
-Dtest=TestMonkeyDudeBadnikInstance,TestS3kAizTraceReplay,
TestS3kSonicTailsCompleteEmeraldRunPrefix,TestS3kTailsFullChainRunChain,
TestS3kSonicTailsAiz*SegmentTraceReplay,TestS3kTailsFullChainAiz*SegmentTraceReplay,
TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils` with verified absolute S3K ROM and fresh reports
completes94 cases:83 pass,11 expected trace assertions fail,0 errors/skips.
No selected formerly passing case regresses against the frozen lag sweep.
Required startup, object regressions and both Sonic+Tails prefix pins pass.

Solo-Tails gameplay segment2 advances41,653→886 errors, first199 y→2058 y
(native033A/engine033C), complete with0 bootstrap errors. Opening segment0
remains zero-error. Newly reached later segments4 and6 retain the worker's
1,783/67,150 mismatches, first4930/101 x; the full chain remains red.
The next radius-release fix is independently ready; it is not included here.
This is focused native/domain validation; combined ordinary/guard limitations
recorded above remain in force.


## Integrated Tails tree release — 2026-10-03

`AIZTree_FallOff` literal x9/y19 must survive until Tails actually lands;
`Tails_TouchFloor` then restores15. The previous standing-default helper
shortens the falling sensors early. No shared movement code changes. The
real-Tails release/terrain/restore regression fails old code19vs15 and passes
with this one owning write. Local snapshot replay does not certify whole-world
recreation or width/donor/team breadth; the AIZ matrix retains these gaps.

Integrated over `b217fe6bd8`, queued `-Ptrace-replay-r7 -Dsurefire.forkCount=1
-Dtest=TestAizHollowTreeTailsRelease,TestAizHollowTreeObjectInstance,
TestS3kAizTraceReplay,TestS3kSonicTailsCompleteEmeraldRunPrefix,
TestS3kTailsFullChainRunChain,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,
TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils` with verified absolute
S3K ROM/fresh reports completes88 cases:86 pass,2 expected trace assertions,
0 errors/skips. The ordinary AIZ native trace retains57 errors, first20302
player_animation_id, unchanged from the immediately prior Monkey candidate.
Both Sonic+Tails pins and required startup/object regressions pass.

Solo-Tails segment2 now completes3,886 rows with ZERO comparison/bootstrap
errors (886→0). Opening segment0 staysgreen. Segments4/6 retain1,783/67,150
errors, first4930/101 x. The full chain remainsred at the segment6 giant-ring
exit, with later segments outside reached coverage. A separate extended Tails
prefix pin will defend this newly green return.


### Integrated SOZ controller refresh (2026-10-03)

The three-file authored-route refresh from `trace-s3k-mega-exit` is integrated
over develop `0943caba1f`. Its native bonus return consumes the first input
row in the already-correct production setup pass. Removing seven surplus
locked-title Right rows repairs that completed Knuckles investigation; no new
Knuckles frontier is opened. The route matrix records the baseline/input
attribution and retained full-world replay obligations.

Queued r7 single-fork `TestSozColdRouteCapture#soloColdActCompletesWithTraversalReplayAndPlayableDestination`
with the verified absolute S3K ROM executes five parameters: all pass, zero
failures/errors/skips. The same checks after the two Tails prefix tests in one
fork instead produce five early rewind failures; both prefix tests pass. The
isolated candidate pass rules out this route refresh as the cause of those
combined failures. Cleanup/state ownership is still being investigated; neither
this focused run nor the red combined run constitutes full-suite validation.
