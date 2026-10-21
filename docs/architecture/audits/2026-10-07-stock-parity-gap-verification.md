# Stock parity gap verification, 2026-10-07

This task checks documented S1, S2 and S3K gaps against the current engine,
then closes bounded, ROM-backed defects. It does not certify completion of
any game. The combined integration base is
`09282b17305cb5794e43a26855cd2b9543b4ff5f` on `develop`.
Three Sol workers own isolated S1, S2 and S3K lanes; the coordinator owns the
S1 SBZ scroll correction, shared ledgers, validation and integration.
The findings below describe the pinned base; candidate implementation is
separately recorded in the validation section.

## Evidence and remaining scope

- **S1:** the historical Final Zone roll-jump animation explanation is stale:
  the current animation owner already retains the roll animation. A fresh
  complete-run replay is needed to identify the current frontier. Title
  starts still retain the previous game's special-stage progress. SBZ Act 1
  still uses the uniform nonzero-act deformation instead of its cloud and
  three building bands.
- **S2:** the special-stage replay helper already reconstructs nonempty
  predecessor ledgers. Consequently the old blanket cold-start explanation
  for stages 2/5/6/7 is insufficient; fresh replay results determine their
  disposition. Native competition remains absent. Stock S2's current title
  provider returns only `ONE_PLAYER`, whereas S3K's provider can return
  `TWO_PLAYER`; the shared title router currently turns that unsupported
  action into an ordinary level start.
- **S3K:** earlier AIZ boss-wait work is already present and must not be
  reintroduced. Current pair/Tails trace frontiers need fresh execution.
  Ribot's active child's Java initialization falls through into its orbit;
  the ROM's `loc_8C396` initialization and `loc_8C594` child creation return
  before `loc_8C41E` orbit dispatch. Native creation-row positions support
  investigating this as a one-dispatch lead.
- **Completion:** S3K still has no `EndingProvider` override. The default
  provider is null and `GameLoop.doEnterEnding()` returns to title, so late
  playable routes do not imply a shipped-ROM ending/credits implementation.
  Audio parity and the unexecuted breadth/rewind obligations in the level
  coverage backlog remain separate completion work.

Lane evidence:
[S1](2026-10-07-s1-parity-gap-verification.md),
[S2](2026-10-07-s2-parity-gap-verification.md),
[S3K](2026-10-07-s3k-parity-gap-verification.md).

## SBZ Act 1 owning routines

The REV01 references are `_inc/DeformLayers (REV01).asm:551-676` and
`_inc/LevelSizeLoad & BgScrollSpeed.asm:280-296,368-377` in the optional
S1 disassembly. These supply constants and branch semantics; no disassembly
asset is used at runtime.

`Deform_SBZ` selects act zero, accumulates the lower black, upper black and
distant brown cameras at `$80`, `$60` and `$40` of `scrshiftx`, respectively,
and advances background Y at `$20` of `scrshifty`. Four cloud words use the
word-width ASR/DIVS/SWAP interpolation, followed by 10 distant, 7 upper and
11 lower building words. `BGScroll_X` selects the first word using
`bgscreenposy & $1F0` and shortens the first sixteen-line group by its low
four bits. SBZ Act 2 and Final Zone select uniform `Deform_SBZ2`; SBZ Act 3 uses the
Labyrinth route remap.

`BgScrollSpeed` initially copies foreground X into all three background
cameras. REV01 `BgScroll_SBZ` initializes Y to
`((screenposy & $7F8) >> 3) + 1`. The current uniform implementation instead
uses `screenposy >> 3`. Fixed vectors cover all 224 packed scroll lines,
fractional positive/negative camera movement, state restoration and the
nonzero-act branch. Cache-window checks must cover every visible band's
source coordinates at supported widths; correct scroll words alone do not
prove that the renderer loaded the corresponding background tiles.

The stock SBZ act matrices remain pending in
[the coverage backlog](../../status/level-test-coverage.md). This local
scroll work adds bounded regression evidence and records inherited gaps;
it is not a route, viewport/donor/team or GPU visual certification.

Independent S1 review accepted the arithmetic, scanline fill, cache-window
coverage and state capture. It also identified limits that remain inherited:
`Sonic1ScrollHandlerProvider.initForZone` is a no-op, so the new uniform-handler
initialization overload is not dispatched by `ParallaxManager.initZone`. Normal
full loads drop the provider and initialize fresh handlers; these component
tests do not establish a reused same-act handler reset. Native `Lamp_LoadInfo`
preserves all three background X words and skips ordinary `BgScrollSpeed`
seeding. Checkpoint background-state restoration and renderer tile residency
therefore remain unverified by this change. No production reload or whole-act
visual parity claim is made.

## Validation and delivery record

Java 21, Lua 5.4 (`LUA_BIN=/usr/bin/lua5.4`) and PowerShell pass the actual
category-runner preflight. The verified World REV01 S1/S2 and locked-on S3K
ROMs are passed by absolute path. Initial focused commands and the pinned
baseline's 3,005-class ordinary/guard run were queued through the shared
Maven scheduler. Queue time is not test execution or passing evidence.

The queued baseline was cancelled before Maven execution after inspection
found the existing shared baseline in `.worktrees/ai-sitar-hero-baseline` at
the exact pinned commit. Its clean source fingerprint is
`9cb0614c4d178cdeea18aacd6c4ba72b41b347bdc5278af6dbfbdf894d5f6095`,
matching this task's clean detached base; its inventory is 3,005 classes,
with one ordinary worker, verified matching ROMs, `DISPLAY=:0` and Lua5.4.
Both completed ordinary and guard lanes were inspected before this reuse
was accepted as baseline evidence. No unrelated process, checkout or diagnostic is
modified or acknowledged by this task.

The title and SBZ candidates were applied from confirmed source discrepancies
while focused requests remained waiting. Those requests will measure candidate
behavior; no executed red-before-fix claim is made. Ribot retains its matched
LBZ replay baseline before assessing the orbit/contact change.

Exact commands, results/skips, compared baseline failures, rejected
approaches and integrated commits will be recorded after their completed
runs are inspected.

The first `-Dmse=off -Dtest=TestSwScrlSbz test` candidate run completed nine
cases, one failure, no errors/skips: the restore-window test incorrectly expected
source X 20 at camera X 100. Literal REV01 arithmetic gives quarter=-25,
DIVS quotient24 and cloud words -25,-24,-22,-21, hence source X 21. The assertion
was corrected from this owning routine; production was unchanged. The rerun
passed all nine cases with no errors/skips. The existing uniform-scroll and
background-ownership selector passed eleven cases with no errors/skips:
`python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestUniformQuarterSpeedScroll,Sonic1BackgroundScrollOwnershipTest test`.
These are twenty focused passes, not a full-suite result.
Test fixture teardown also resets the module/aspect configuration so
the width matrix cannot contaminate later classes in a reused fork.

## Completed ordinary baseline

The read-only shared exact-base invocation was
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 09282b17305cb5794e43a26855cd2b9543b4ff5f --category all --max-minutes 120 --run`
in `.worktrees/ai-sitar-hero-baseline`, run `20261007T091459Z-8c623648`.
Its completed ordinary lane reports 3,003 XML suites, 26,149 tests,
28 failures, zero errors and 61 skips in 4,592.39 seconds (exit 1).
The separate fresh-JVM guard lane completed 672 tests with zero failures,
errors or skips in 209.12 seconds (exit0). Both lanes are completed and inspected;
this is a red baseline, not a full-suite pass.

Skips include opt-in diagnostics/captures, the rewind soak, unavailable OpenGL4.1/EGL paths,
unsupported mod-act platform scenarios and one spin-tube assumption. None of the
61 recorded skip reasons reports a missing S1/S2/S3K ROM. Ordinary execution does
not run the separately tagged trace/native diagnostic profiles.

Failure identity and concrete first mismatch:

| Class | Test | Baseline failure |
| --- | --- | --- |
| `TestFbzSandopolisTimelineHeadless` | `productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice` | SOZ restore cycle 0 sprites: [sprites.sprites[0].state.playerExtra.instaShieldRegistered: A=false B=true] ==> expected: <true> but was: <false> |
| `TestS3kMhzAct2AuthoredRoute` | `incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | late pulley owns Tails ==> expected: <true> but was: <false> |
| `TestS3kMhzAct2AuthoredRoute` | `incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | late pulley owns Sonic ==> expected: <true> but was: <false> |
| `TestDezIncomingFinalRouteCapture` | `coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding` | expected: <96> but was: <0> |
| `TestDezIncomingFinalRouteCapture` | `coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | expected: <96> but was: <0> |
| `TestDezIncomingFinalRouteCapture` | `incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | death at 26706 ==> expected: <false> but was: <true> |
| `TestDezIncomingFinalRouteCapture` | `incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | death at 26750 ==> expected: <false> but was: <true> |
| `TestDezIncomingFinalRouteCapture` | `coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | expected: <96> but was: <0> |
| `TestDezIncomingFinalRouteCapture` | `coldEmeraldTeamClearsBothActsFinalFightAndDoomsday` | death at 53897 ==> expected: <false> but was: <true> |
| `TestDezIncomingFinalRouteCapture` | `coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding` | expected: <96> but was: <0> |
| `TestLrzActTwoColdRouteCapture` | `coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState` | death at input 36526 ==> expected: <false> but was: <true> |
| `TestLrzBossColdRouteCapture` | `coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState` | death at input 36526 ==> expected: <false> but was: <true> |
| `TestLrzKnucklesColdRouteCapture` | `coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace` | expected: <1069> but was: <899> |
| `TestLrzTailsColdRouteCapture` | `coldTailsClearsActOneAndRestoresTraversalFightAndHandoff` | death at input 19460 ==> expected: <false> but was: <true> |
| `TestLrzTailsColdRouteCapture` | `coldTailsRestoresActTwoTraversalToTheMiddleCorridor` | death at input 19460 ==> expected: <false> but was: <true> |
| `TestLrzTailsColdRouteCapture` | `coldTailsCompletesBossActAndReachesPlayableHiddenPalace` | death at input 19460 ==> expected: <false> but was: <true> |
| `TestLrzTailsColdRouteCapture` | `coldTailsCompletesActTwoAndRestoresTheBoulderHandoff` | death at input 19460 ==> expected: <false> but was: <true> |
| `TestLrzWideBossColdRouteCapture` | `coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld` | expected: <2796> but was: <524> |
| `TestMhzPairColdRouteCapture` | `pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad` | the route must observe the actual history-reset boundary ==> expected: <true> but was: <false> |
| `TestMhzWideColdRouteCapture` | `wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay` | [wide-route-19500] restore 0: zone-runtime.stateBytes[2] A=46 B=26; [3] A=-104 B=64 |
| `TestSszColdRouteCapture` | `coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` | death at input 7311 ==> expected: <false> but was: <true> |
| `TestSszColdRouteCapture` | `coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` | death at input 7311 ==> expected: <false> but was: <true> |
| `TestSszSoloColdRouteCapture` | `coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches` | death at 7671 ==> expected: <false> but was: <true> |
| `TestSszSoloColdRouteCapture` | `coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory` | death at 7671 ==> expected: <false> but was: <true> |
| `TestSszTailsColdRouteCapture` | `coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]` | expected: <48> but was: <0> |
| `TestSszTailsColdRouteCapture` | `coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]` | expected: <48> but was: <0> |
| `TestSszTailsColdRouteCapture` | `coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]` | replay 4018: object-manager.usedSlotsBits differs; onlyA slots24,29 |
| `audio.timeline.TestS1GameplayAudioTimelineCli` | `shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup` | expected: <0> but was: <4> |

The runner caps `failed_cases[].message` at 2,048 characters. The SSZ-Tails
rewind assertion exceeds that limit, so the table records its first concrete
fields. The later full-assertion comparison below uses the uncapped first line
of `failed_cases[].detail`; assertion equality does not establish equality of
all serialized world state. The complete
wide-MHZ message is about 341 characters; all six named `stateBytes` differences
were inspected and match the candidate exactly.
Object-blob hashes and Java identities are not portable between JVMs. Any disputed
new or worsened failure requires a bounded matched base/current check.

## Upstream reconciliation

The main workspace advanced to `6d817a9d7` during the swarm. Its sole additional
commit updates the architecture index and four October 7 blueprint documents;
no executable source, tests, POM, hooks or selection policy changed. The exact
`09282b173` executed baseline remains applicable to engine behavior. These
upstream documents are preserved in the combined tree.

## Focused corrections and independent review

S1 title progress ownership (`cf1e3f75c096`) passed 64 focused lifecycle,
state/decorator/API, Javadoc and SDK cases without skips. The additive hook and
its forwarding signature replace only the unpublished 0.7 candidate pin; the
exact regenerated 20,046-line surface matches, with no published-pin or version
change. The maintained samples are covered by the combined ordinary selection.
The native-specific default hook keeps common runtime code semantic and preserves
decorated stage/emerald counts, Continue and other games' progress.

S3K Ribot (`3e7e75785c8b`) passed 68 focused object/child-graph and mandated
loading/bootstrap/decoding cases without skips. Its initialization regression
fails on untouched production. Matched LBZ trace errors fall 4,585→1,665 and
first error moves 23,533→30,582; the remaining Tails Y/animation disagreement
is unattributed. Neither the red LBZ fixture nor the earlier red HCZ full-chain
boundary is represented as completed parity.

Independent S3K review of the proposed shared art-owner registration confirms
that `characterKey().persisted()` supplies the semantic Tails bank for runtime
`tails_p2` and preserves converted Knuckles donor selection. The owner observes
the existing selected mapping frame and publishes ROM-backed art work; it does
not write player physics, animation selection or object state. S3K's host policy
has `supportsPlayerDynamicArtAudit=false`, so this registration correction is
inert for its existing player-art audit. No timing input, trace-created job,
comparator tolerance or new rewind field is introduced.

Independent S1 review accepted the S2 horizontal spring side gate: unsigned
word subtraction and x-flip reject the same contact sides, including equality,
as both ROM player-slot branches. Both participants reach the shared solid
checkpoint loop. The separate proximity path is unchanged. The added focused
regression calls the private contact gate and covers both facings/equality;
unsigned sign-boundary and live P1/P2 checkpoint-dispatch breadth remain limits.

The final S2 trace comparison retains the same measured outcomes after the
spring-side correction: special stages 2/5/6 and EHZ segment 2 compare cleanly;
stage 7, ARZ and the full/prefix chains remain red. Thus the missing spring-side
branch is independently ROM/regression-confirmed, but does not explain the
ARZ/stage 7 frontier. That rejected causal inference is preserved in the S2
lane audit. The shared art registration's non-art disagreement spans were
matched before/after separately; enabling the previously absent body owner
exposes an earlier chain art boundary rather than licensing a gameplay tune.

The combined plan selects 3,007 ordinary classes, all categories, plus fresh-JVM
structural guards. The public module hook and shared playable-art registration
require this normal broad selection. Tool preflight passed in the coordinator's
launch environment. The measured baseline cost is 76.5 minutes ordinary plus
3.5 minutes guards, so the invocation receives 120 minutes excluding queue wait;
the runner's ten-minute no-output timeout remains. A timeout, missing reports,
or prerequisite skip is incomplete coverage, never a passing result.

S2 delivered commit `af0307b73a66` passes 105 title/startup, 14 initializer and 13
spring cases without skips. Its final eight-fixture trace invocation completes
four passes/four assertion failures, zero errors/skips: stages 2/5/6 and EHZ1
segment 2 compare cleanly. The first erroneous EHZ1 damage is attributed to the
engine's Coconuts coconut projectile; the differing ROM lifetime/admission
condition remains to investigate. Exact old/current non-art tuple digests in the
lane audit establish unchanged measured gameplay after semantic art registration.
No stage 7, ARZ, chain, competition or whole-act completion is claimed.

## Completed candidate and guard correction

The combined candidate at `27ea65395744196434444c43093a4f2ba5a5838a` ran:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 09282b17305cb5794e43a26855cd2b9543b4ff5f --max-minutes 120 --run
```

Run `20261007T112413Z-de8b4b48` completed both lanes. Ordinary: 3,005 XML
suites, 26,166 tests, 28 failures, zero errors and 61 skips in 4,457.92 seconds
(exit 1). All failure and skip identities match the executed baseline. Twenty-seven
failure messages match literally; the remaining SSZ-Tails bounded prefix matches
after removing only the independently verified JVM-dependent blob hashes. Its
first mismatch remains replay frame 4,018, `object-manager.usedSlotsBits`, with
slots 24 and 29 only on A. This comparison does not establish equality beyond
the retained prefix. No new or worsened concrete ordinary failure was observed,
and no ROM prerequisite was skipped. This is completed red validation.

The separate fresh-JVM guard lane completed 672 tests, one new failure, zero
errors/skips in 212.43 seconds (exit 1). The module/provider caching guard rejected
the unaudited title lifecycle call. Inspection of
`GameServices.currentOrBootstrapGameModule` confirms active `WorldSession`
resolution first and bootstrap fallback only before a session; the one-shot
`onNewGameFromTitle` call retains no module or provider. The correction adds that
exact source call to the guard's existing audited list and documents the seam.
It changes no runtime behavior or scanning rule and grants no class-wide exemption.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Pguards -Dtest=TestBootstrapModuleProviderCachingGuard test
```

The corrected guard completed seven tests, zero failures/errors/skips (exit 0),
including stale-approval detection and negative scanner fixtures. The unchanged
ordinary lane is not repeated for this audit/Javadoc correction; the required
post-integration guard profile subsequently executed all guards again. Original
candidate diagnostics were inspected, then acknowledged through the runner and
deleted.

## Affected S1 canonical fixtures

The clean detached base and combined candidate both ran the same single-fork
`trace-replay` selector with absolute verified ROM paths. `${OPENGGF_REPO}`
below denotes the main repository root; actual commands used resolved absolute
paths:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical -Dtest=TestS1Sbz1CompleteRunTraceReplay,TestS1Sbz2CompleteRunTraceReplay,TestS1Sbz3CompleteRunTraceReplay,TestS1FzCompleteRunTraceReplay,TestS1Credits05Sbz1TraceReplay,TestS1Credits06Sbz2TraceReplay -Dsonic1.rom.path=${OPENGGF_REPO}/s1.gen -Dsonic2.rom.path=${OPENGGF_REPO}/s2.gen -Ds3k.rom.path=${OPENGGF_REPO}/s3k.gen test
```

Each completed six tests, zero failures/errors/skips (exit 0). Canonical SBZ1,
SBZ2, SBZ3 and FZ reports compare 7,619, 9,594, 8,354 and 4,457 rows respectively,
with zero errors, warnings or bootstrap errors/warnings on both trees. SBZ3's
native report ID is `s1_lz4`; FZ's is `s1_sbz3`. Fixture classes and metadata
confirm these route/native-zone remaps. The isolated FZ replay is
clean on the current baseline and candidate; the earlier full chain still stops
at MZ2 before reaching FZ. Neither result proves general hit-window correctness
of the remaining suppression heuristic or whole-act visual/cache residency.

## Post-integration ordinary validation and concurrent tooling

The reviewed source was merged without conflicts into the main workspace's
existing `develop` branch at `945b74e999c3584ad45b92a711b7a6dace7dc294`.
Existing dirty disassemblies and unrelated local files were preserved.
The main launch environment passed preflight, then executed the same combined
category command above with the pinned `09282b173` base and 120-minute limit.
Run `20261007T130421Z-74206bd5` selected all 3,007 ordinary classes and guards;
its launch fingerprint was
`9d38495193dac9f0868bb4a5c338e93d4f313b438296aca8d77d11132ad211d4`.

The ordinary lane completed 3,005 XML suites / 26,166 tests, 28 failures,
zero errors and 61 skips in 4,313.69 seconds (lane exit 1). All 28 failing
identities and all 61 skip identities/reasons match the executed baseline.
Twenty-seven failure messages match literally. The initial SSZ-Tails comparison
used the retained 2,048-character prefix, removing only the verified
JVM-dependent blob hashes. A subsequent independent comparison by the baseline's
owning coordinator checked the full concrete assertion: the current
`failed_cases[].detail` first line contains 2,952 characters; stripping only
`org.opentest4j.AssertionFailedError: ` and normalizing only
`RewindObjectStateBlob@hex` to `RewindObjectStateBlob@HASH` produces 2,907
characters matching the full baseline assertion retained before acknowledgement.
Thus all 28 failure identities and concrete assertions match, with this single
verified normalization. This does not claim equality of unreported world state.
No new or worsened failure or ROM-prerequisite skip was observed. The owning
coordinator also independently checked all 61 skip identities and first-line
reasons; no ROM skips or omitted reports were found.

During ordinary execution, an independently authorized task integrated Maven
throughput/diagnostic tooling at `63fea861e`, followed by its evidence-only
`5bc5f4fa6` commit. The complete diff was inspected: engine Java, Java tests,
POM, ROM inputs, hooks, category selection and the category runner are unchanged.
The already-running ordinary JVM therefore measures the integrated engine
source above. The tooling coordinator independently verified this complete
changed-path/source-equivalence check and reused the completed ordinary lane,
cancelling only its own duplicate baseline. Its candidate and tooling/guard
qualification remained independently owned. The upstream scheduler, helper and
documentation changes are preserved; their separate verification is recorded in the
[tooling research](../research/2026-10-07-maven-focused-throughput.md).

The category runner correctly stopped before its next lane because the workspace
fingerprint changed (wrapper exit 2). This invocation is completed ordinary
validation with an unexecuted guard lane, not a completed combined run.
Current-tooling preflight passed again. The required complete fresh-JVM guard
profile was submitted independently on current `develop`:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

On `9583f244783d4f2a827afe9d0bba1874c43ce37d`, this profile completed at
2026-10-07 14:52:30 UTC with exit 0 / `BUILD SUCCESS`: 86 suites, 672 tests,
zero failures, errors or skips, including all seven corrected module/provider
caching guard cases. Maven execution took 3 minutes 35 seconds after a
1,862-second queue wait. Fresh XML reports from 14:49:17–14:52:30 UTC were
inspected separately from stale reports left by earlier invocations. These
completed separate ordinary and guard profiles satisfy the required engine
validation; the ordinary lane remains red with the unchanged inherited failures.
The inspected ordinary diagnostics were acknowledged through the runner and
deleted (exit 0); no unrelated run was acknowledged or logs archived.

## Next work from the verified remaining gaps

1. Keep S3K AIZ → HCZ stable: diagnose the fresh chain's HCZ row 0 fractional Y
   carry (`$0000` native, `$3800` engine) and missed giant-ring exit through the
   actual stage-return/load owner. Historical AIZ wait corrections already exist.
   LBZ's next independent frontier is row 30,582 Tails Y/CPU behavior after the
   Ribot lead is removed.
2. Close S2's earlier EHZ1 coconut damage before downstream stage 1 art symptoms:
   compare Obj9D throw/child allocation, Obj98 initialization, visibility/deletion
   and coconut fall dispatch. Separately diagnose ARZ1 row 1,961 Tails launch
   state before the stage 7 inherited ledger. Preserve the now-green stages 2/5/6
   and EHZ1 segment 2. Native S2 competition remains separate completion work.
3. Find S1's owning art submission discrepancy and actual MZ2 giant-ring exit
   boundary. The current full chain stops at segment 12; the standalone FZ
   fixture passes. Do not repeat the existing roll-jump selector fix or use that
   isolated pass to certify general boss behavior.
4. Finish S3K's real ending/credits provider and finale-to-terminal-state flows,
   alongside bounded repair of the independently red SSZ/LRZ/MHZ/DEZ cold-route
   and rewind obligations. Source/component presence is not a cold-route pass.
5. Complete the advertised per-act/character matrices, checkpoint/load/respawn
   rewind and native presentation, then qualify the separate audio oracle and
   release campaign gates. Ordinary-suite results do not replace trace/native/audio
   profiles.

These are evidence-backed follow-ups, not claims that all remaining entries have
been freshly reproduced or that this swarm certifies whole-game completion.

## Continued swarm from the delivered base

The user requested continued work after the first delivery. The second round
pins `5b3a63641033506fc0d89ad5188a0c97fae29089` on the unchanged main-workspace
`develop` branch. Fetch and fast-forward pull completed with no incoming changes.
Three saved Sol workers are reused in new isolated worktrees for S1 campaign
art/MZ2 exit, S2 EHZ coconut damage and S3K HCZ startup/handoff. The coordinator
independently investigates the inherited FBZ → SOZ rewind registration failure.
Shared runtime owners are assigned explicitly before edits; centralized release,
frontier and coverage prose remain coordinator-owned.

The complete `945b74e999c` → pinned-base engine Java, Java tests, POM, hooks and
category-runner diff is empty. The prior completed 26,166-test ordinary lane
therefore remains the engine baseline; the full guard source also matches the
completed `9583f2447` profile. Each target still receives a fresh matched replay
or regression baseline. The actual Java 21 / Lua 5.4 / PowerShell preflight
passed in the new integration worktree, and all three root ROM identities were
reverified before their absolute paths were assigned to the workers.

During the queue wait, another authorized delivery advanced main `develop` to
`4cfb745646d9439cdb9c07d53d0670dd0fb3fe58`, followed by
`2fc65c8479570f16ebd9830115485ee369c2b1e6`. Its shared changes add an opt-out
invincibility-expiry music rule, enabled for all three stock games, and update the
unpublished Mod API signature pin alongside Sonic Survivors. The later change
optimizes the Infinite Sonic test driver and adds memory-investigation tooling;
its complete engine Java/POM/hooks/category-selection diff from `4cfb745` is
empty. The target baselines remain frozen at `5b3a636`; the earlier exact
source-equivalence qualification applies to that pin, not the updated destination.
Main and the original integration worktree were fast-forwarded without changing
their branches. The candidate player change was reconciled with the upstream
music gate; six other candidate files survived byte-for-byte. A temporary owned
Git ref protected the transfer and was removed after verification. The actual
destination still requires qualification before delivery.

A separate authorized owner completed a fresh matched engine baseline at clean
`4cfb745646d9439cdb9c07d53d0670dd0fb3fe58`, run
`20261007T180233Z-d9b56478`, with
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 4cfb745646d9439cdb9c07d53d0670dd0fb3fe58 --category all --max-minutes 120 --run`.
The normal ordinary command used all three verified absolute ROM paths: 3,007
selected classes, 3,005 XML suites, **26,222 tests / 28 failures / zero errors /
62 skips**, 5,157.73 seconds. Fresh guards completed 86 XML suites and **672
passes**, zero failures/errors/skips, 221.57 seconds. Before raw XML cleanup the
coordinator independently inspected every failure's complete first assertion
line: 27 match the earlier baseline literally; the full SSZ line matches after
only the already-verified blob-hash normalization. All 61 earlier skip identities
and first-line reasons match the owner's retained summary; the additional skip
is `TestSonicSurvivors#balanceProbe`, an opt-in diagnostic. No ROM skips or omitted
entries were reported. The owner consumed and acknowledged only its own run;
no logs were copied or archived by this task.

This baseline is source-qualified for the unchanged engine and failing tests
through `2fc65c8`; its Infinite Sonic fixture is the older variant. The new
cache/eviction/reload equivalence request was cancelled before admission to
consolidate final validation and release the assembly source freeze; it produced
no test verdict. The final ordinary candidate must execute the complete updated
fixture, including that regression. A separately owned, still-running Sitar
candidate at `53a742c58425209b5cce35ea30bcd80c37e8fc43`, run
`20261007T195912Z-7068993c`, subsequently supplied fresh Linux class evidence:
238 cases, 206 passes, zero failures/errors and 32 skips. The coordinator
independently compared the class source byte-for-byte with this assembly
(SHA-256 `c0d8c7db1a859085e2f874b3ed900258125197447e1135f1cbc996ff7e8a3474`),
checked the new driver-observation case has no failure/error/skip and matched
all 32 skip identities/first-line reasons literally against the fresh baseline.
This closes the separate fixture/platform qualification; it does not certify
either whole candidate suite. The later main
`37a57ebdb` changes only diagnostic cleanup locking, its Python tests and prose;
its full engine Java/Java-test/POM/hooks/selection-policy diff from `2fc65c8` is
empty. The isolated delivery branch was fast-forwarded to that update, then
merged local S1 and Coconuts commits as `c4d52d7f1` and `5ab049ec8`, with no
conflicts and byte-identical production paths compared with the frozen staging
tree. The final HCZ harness commit was then merged without conflicts as
`08a2446947fa2a70b3a768952a9f0147c248a09c`. This is worktree assembly;
the continued round is not integrated into main
or pushed, and current tooling still receives final fresh guards.

Initial source candidates are bounded and remain hypotheses until executed
evidence confirms their path: Coconuts selects P1 rather than the ROM's nearest
player; the production results-transition overload runs a player warm-up that
its simpler overload explicitly defers; the rewind post-restore power-up callback
forces a captured pending insta-shield into registered state. HCZ's existing
fresh-load admission/initial Process_Sprites boundary needs clock and dispatch
measurement before attributing its row-0 fractional Y mismatch.

Fresh trace and focused regression requests use the shared Maven queue. Waiting
requests are not measurements, and no heap/profile override or unrelated job
cancellation is used to accelerate this round. Candidate fixes and completed
results will be recorded here after inspection.

The duplicate focused FBZ → SOZ baseline request returned exit 130 with
"Maven request cancelled; validation is incomplete" at 17:40 UTC, before
execution and without a `target/` directory. The coordinator did not cancel it;
the signal's source is unknown. The completed ordinary lane already measured
the exact failure on engine source identical to `5b3a636`, so no duplicate is
resubmitted. Other owners' requests, processes and diagnostics remain untouched.

### Candidate preparation while measurements wait

An older ordinary memory diagnostic reported another 45–105 minutes of expected
execution on 2026-10-07. A fifth owned worktree, based on the same pinned commit,
therefore held candidate source separately from the four frozen test worktrees.
This preserved the queued baseline and red-regression source while permitting
implementation and bounded independent review. Once the root regression proof
completed, the original integration tree was returned to clean updated-base
production/test source, and the staging tree was frozen for candidate verification.

The S1 candidate removes the early warm-up from the production five-argument
results entry and arms the existing art hold only after the real release prelude.
Review exposed `preMainLoopHoldBoundaryRow` as missing from the service's captured
state; a regression arms a later hold, restores the earlier one, and releases it
without destination admission. The candidate adds that field to capture/restore.
The S1 changes are locally committed as `0268a6eef7b69b108d1ddc69170115eb36579ad2`.
Its 36-case focused regression selection changed from three intended failures to
zero failures, errors or skips. The canonical full chain still stops at segment
12, but segment 8's 6,525 errors become zero and the first two native art-gap
movie rows align exactly. Segment 7 is **MZ1**, not GHZ3; its remaining animation
frontier is row 4. Segment 12 retains a row-87 X-speed difference. These are
partial improvements, not a completed campaign. A bounded source check finds
no independent air-drag or roll-lock defect in the saved MZ2 row: positive
`y_speed=$03A8` correctly skips native drag, and status `$06` does not set the
roll-jump lock. The `$18` X-speed and `$1800` subpixel deltas are consistent with
one extra Right-held acceleration. The verified shared-gap helper is merged into
the S1 lane at `9b8052b8fe8f24bb38f745298fb5a55551fa5fbe`; its matched campaign
replay completes three tests with one failure and zero errors/skips in
41.531 seconds. All compared segment totals and first errors remain unchanged:
MZ1 segment 7 has 5,466 errors, MZ2 segment 12 has 196,129, and the same three
art-gap failures remain. The prefix's two tests pass. No movement edit follows
from that unchanged result alone.

The S2 change, locally committed as `8dba700f3a63379bef95b024194d6e2224d1a390`,
uses the existing native P1/P2 signed-word nearest query and the literal unsigned
`$60`/`$C0` window, preserving the supplied-player fallback for direct object
tests without injected services. Its four-case regression selection changes
from two intended failures to zero failures, errors or skips. EHZ1 segment 1's
8,176 errors, the full/prefix opening's 42,538 errors and SS1's 15,713 art errors
all become zero. The complete-emerald prefix passes; existing EHZ1 segment 2 and
special stages 2/5/6 remain green. The full chain advances from segment 1 to
segment 17 and remains red. Newly reachable CPZ/ARZ/SS7 errors are not labelled
regressions without a matched baseline. A standalone CPZ1 replay independently
reproduces row 4,394's local tube timing difference, so it is not explained solely
by the earlier special-stage return gap.

The follow-up reproduces the native two-`DIVS` 8.8 duration word and byte
countdown, including a legal zero-duration waypoint. The independently decoded
`word_22B40` path moves from relative `$D4,$6C` to `$DB,$68`: at native speed
`$800`, its duration word is `$00E0`, whose high byte is zero. Clamping it to one
adds a movement dispatch, yielding the observed first row-4,394 X `$255C`
instead of `$255B` and Y speed `-$492` instead of the next segment's `-$600`.
The completed ten-fixture trace selection reports one chain assertion failure,
zero errors/skips: standalone CPZ1's 5,318 errors and chain CPZ1/CPZ2's
26,735/15,553 errors all become zero; both CPZ2 standalone fixtures also pass.
EHZ, prefix and stages 2/5/6 remain green. ARZ row 1,961 and SS7 art remain
unchanged, as does the earlier return-gap clock disagreement. The arithmetic
regressions pass, but a new rewind unit case initially omitted the required
identity-table capture context. After repairing that fixture, the unchanged
production candidate passes all seven focused cases with zero failures,
errors or skips. The qualified correction and mirrored routine pitfall are
committed as `e87d41553eac5b2844599c5f06c5aa4f9d0c3392`, then merged locally
into the combined tree as `68193f731a47c40f536394aa4930ce5b54734fc3`.

A temporary HCZ dispatch probe identified the first
ordinary physics pass at input 53,607, before the advertised destination input
window at 53,608; comparator attachment then labelled the next pass as row 0.
Attaching the comparator earlier was rejected because it would compare the wrong
input. The existing shared-gap owner suppresses destination physics while crossing
that pre-window input row. A real-ROM regression fails against the old raw-step
helper and verifies unchanged clocks/physics during the gap, then the native first
ordinary pass (`Y=$0020`, fractional Y `$0000`, speed `$0038`). The harness
candidate reduces HCZ's 32,343 errors to 563 and moves its first error from row 0
to row 653, retaining all 3,574 compared rows, zero warnings and unchanged earlier
segments. The giant-ring exit remains missed. The final strict admission guard
rejects denied admission at the advertised offset before a suppressed gap step
can consume that row. Commit `45c6eed2d6e3a85442cd42e12b2c74c347ec9c8d` passes
66 focused cases, zero failures/errors/skips, including both real-ROM gap cases
and all four mandatory S3K controls. Its final matched chain preserves exactly
the same 563-error profile: 477 physics, 86 animation, first row 653 Tails Y
`$0585/$0586`, all 3,574 rows, 55 lag rows, zero warnings/bootstrap errors.
The matched S1 helper replay retains its exact earlier frontiers; S2
qualification of the shared test helper is still required.
A bounded read-only investigation independently localizes the first HCZ Tails
Y difference to a bar-input discrepancy: native vertical/horizontal checks read
raw `(Ctrl_2)` at `sonic3k.asm:42828/42952`, whereas Java reads synthesized CPU
directions. At row 653 P2 is neutral and CPU logical Down is set; integer Y alone
becomes `$0585/$0586`, with matching fractions, speed, status and animation.
The object-only correction uses the existing raw-controller API. Both new
axis cases fail against the old object, then the candidate passes 75 focused
cases with zero failures, errors or skips, including all four mandatory S3K
startup controls. Standalone HCZ becomes fully green on its compared surface;
the matched chain removes exactly 75 Y observations and retains 488 errors
(402 physics, 86 animation). Its first animation difference is row 3,531;
its first physics difference is row 3,532 primary X `1457/1452`. All 3,574
rows still complete, with 55 lag rows and zero warnings/bootstrap errors;
the earlier AIZ profiles remain unchanged and the giant-ring exit remains
missed. Commit `b3eff6209ed90d42f88b0264b15f4d8d841e852a` is merged locally as
`efb8713739b72bceabac549cc6daf30277a7c846`. The bar-state restore regression
does not supply whole-world replay or missing auxiliary-schema coverage.
A subsequent read-only check shows the native giant ring captures Sonic at
row 3,531 while the engine has no corresponding nearby ring. AIZ and HCZ reuse
collected-ring bit one; the native full `SaveGame` clears this mask even in
No Save mode, whereas Java currently requests persistence alone. The verified
locked-on ROM bytes at `$C4CC` are `42B8 FF92 4E75` (`CLR.L $FF92; RTS`).
Runtime mask evidence is still being collected before changing that owner.
Level reloads, seamless act changes and generic persistence requests are not
equivalent native clear boundaries.

The initial shield proposal preserved the captured registration flag but only
invalidated the surviving handle's art. It was rejected during source review:
ObjectManager intentionally drops abandoned future objects without destroying
them, leaving a later attack cursor and slot in a still-pending player handle.
The replacement candidate captures an unregistered handle through the existing
`ObjectSubclassRewindExtra` value extension; registered handles remain owned by
ObjectManager. A real-handle/manager regression covers both surviving and destroyed
future handles, exact pending state, absence from the restored manager and next-tick
registration. The public immediate rebuild helper and snapshot/handle signatures
remain unchanged. The existing FBZ → SOZ test must still verify two destination
restores and two eight-frame forward replays; its seeded EXIT_READY boundary does
not certify the incoming full boss/capsule route.

The root's first new-test attempt failed compilation, so it measured no behavior.
The next attempt changed the bootstrap module while leaving an already-open S2
world active; its mixed red/control results were rejected. Using
`TestEnvironment.configureGameModuleFixture` supplies the live S3K world. The
corrected unchanged-production run completed at 2026-10-07 18:05:46 UTC with 12
tests, four intended failures, zero errors/skips and a passing registered-shield
control. The updated-base candidate selection includes those tests, the real
FBZ/SOZ forward replays, persistent visual-rebuild controls and all four mandatory
S3K startup classes. The completed focused command at updated destination
`2fc65c8479570f16ebd9830115485ee369c2b1e6` was
`DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestAbstractPlayableSpriteRewindCapture,TestAuxiliaryDynamicPowerUps,TestFbzActTransitionHeadless,TestFbzSandopolisTimelineHeadless,TestFbzToSandopolisTransition,TestS3kAiz1SkipHeadless,TestShieldAnimationArtLifecycle,TestShieldRewindRestore,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSonic3kLevelLoading test`,
with all three verified absolute root ROM properties. It finished at
2026-10-07 18:40:59 UTC with **111 tests, one failure, zero errors/skips**.
All twelve shield regressions pass and the first complete destination restore
now matches. The FBZ/SOZ test then reaches its previously hidden forward check:
cycle zero differs only in five dynamic-art clocks, each replayed value one
higher (`latestFrame` 47/48, `logicalFrame`, `nextPublicationFrame`,
`movieLogicalFrame` and `unannouncedRows` 48/49). The exact floor-zero and both
eight-frame comparisons remain unchanged. Destination forward replay is still
open. The completed read-only retry probe at 19:59:11 UTC identifies the owning
boundary: original step zero returns `GAMEPLAY_FRAME` with a pending fresh-level
boundary and no clock advance (41/41); after restore, it has no pending boundary
and advances 41/42. Neither trajectory performs a setup-only retry or active
fade. The controller's deferred player/camera/initial-pass state was absent from
rewind capture. Source ordering further establishes that `LEVEL_LOAD` resets and
captures floor zero inside the inner load, before the fresh controller assigns
its pending boundary. Restoring the new adapter's null floor state therefore
clears the stale pending boundary, rather than reconstructing an assembly at
that floor. Explicit later registry captures retain the complete pending state
and publication flag through the same private adapter; restore only assigns
values. The unchanged floor test does not prove that positive pending-state
half, so a separate production publish/complete regression is running against
the old production code before its fix is merged. The temporary probe was
removed. The updated focused selection adds sanctuary/continuation adapter
controls and completes at 2026-10-07 21:30:35 UTC with **125 tests, zero
failures, errors or skips**; the unchanged destination test passes both complete
eight-frame replay cycles. Its command is the earlier focused command plus
`TestLevelTransitionCoordinatorPeeks,TestLevelContinuationCarry`, with the same
three verified absolute ROM properties and supported profile-free `--lean` lane.
No comparison fields or clocks were compensated. The commit hook then projected
the shield payload's private nested record as a detectable Mod API declaration
change. Moving that value record into its own unannotated package-private file
passes the normal hook check. The exact shield/signature selection then passes
21 cases with zero failures, errors or skips and confirms the existing API pin.
Root commit `3190a0ff8d21cd2e5b4263f616e2182f8ceb21ad` is merged locally as
`3cc64352a9af8294a5da0e09a96ef97699c17566`. No signature pin, API version or
hook is edited.

Exact lane commands, complete observed frontiers and rejected approaches remain
in the [S1](2026-10-07-s1-parity-gap-verification.md),
[S2](2026-10-07-s2-parity-gap-verification.md) and
[S3K](2026-10-07-s3k-parity-gap-verification.md) audits. These local commits and
candidate measurements have not yet been integrated or pushed in this round.

The wider S3K completion gap was rechecked in the reconciled assembly at
`ae3399c2b81d724cc5ba650c6ca9ec1499c6eaae`: `Sonic3kGameModule` still has no
`getEndingProvider()` override, the default in `GameModule` returns null, and
`GameLoop.doEnterEnding()` returns to title for that value. Closing the local
trace and rewind defects in this round does not implement the shipped-ROM ending.

### Updated Sitar destination and baseline qualification

Main subsequently advanced to `be3c3141808c98d4c56a3dfb0fee19e241720154`
with the Sitar integration. This contains substantive audio and scene/API
changes, so the earlier `4cfb` source-equivalence claim alone does not qualify
that destination. The completed Sitar candidate supplies an updated baseline:
`53a742c58425209b5cce35ea30bcd80c37e8fc43`, run
`20261007T195912Z-7068993c`, selected 3,020 ordinary classes and completed
3,018 suites / 26,373 tests / 28 failures / zero errors / 62 skips in
4,941.56 seconds. Its separate fresh guards completed 86 suites / 672 tests
with zero failures, errors or skips in 209.63 seconds. Both lanes are terminal;
ordinary exit one represents inherited failures, without timeout or omissions.

The [Sitar terminal comparison](../designs/2026-10-07-sitar-hero-full-version.md#completed-repaired-candidate-and-qualified-baseline)
records the owner's independent comparison of all 28 failure identities and complete
first assertion lines with the durable `4cfb` result: 27 match literally,
and the full SSZ assertion matches after only exception-prefix removal and
the already-verified object-blob hash normalization. All 62 skip identities
and first causal reasons match; no ROM prerequisite was skipped. The parity
coordinator read that durable comparison and independently verified the complete
candidate-to-`be3` engine Java, Java-test/resource, POM, hook and API-pin diff
is empty. Only five previously qualified Python testing-cleanup/prose files
differ. The completed candidate therefore qualifies the updated engine baseline;
it is not described as a suite measured at `be3`.

The Sitar owner's mandatory post-integration request is running against frozen
main `be3`. This task preserves that freeze, including unrelated dirty
submodules and untracked files, and does not cancel or acknowledge that request.
Its parity commits remain private pending reconciliation, the remaining focused
fixes, combined validation and the normal delivery flow.


The updated destination was merged privately as
`ae3399c2b81d724cc5ba650c6ca9ec1499c6eaae` after evidence-only commit
`42c5ce4a154694e3197f8cb481700a837c458901`. The normal merge had no conflicts;
all 25 parity production, Java-test and mirrored-skill paths checked against
the pre-merge assembly remained byte-for-byte unchanged. The main branch is
still reserved for its owner's already-running post-integration validation.

The S1 read-only follow-up identifies the recorded release rows behind two
remaining frontiers. Actual BK2 rows 27,469/27,470 hold Up, then 27,471/27,472
release it; MZ1 row four records neutral movie input and native Wait `$05`,
mapping `$01`. Rows 47,119/47,120 hold Right, then 47,121/47,122 release it;
MZ2 row 87 records neutral movie input. Adjacent lag-state rows are false with
unchanged counters, and these segments have no recorded pre-level input prefix.
TraceChaser's recorder derives this CSV input from the BK2 row, with raw input
only as a fallback. Neither fixture includes the ROM's latched Ctrl_1 held/new
bytes in auxiliary snapshots. This establishes physical release rows, not the
native body latch, the engine's actual consumed cursor or its live logical flags;
those observations remain unavailable in the retained fixtures/diagnostics.
A subsequent native capture resolves the missing ROM-side observation. The
existing TraceChaser GPGX host replayed the original movie with its original sync
settings and inputs against the verified S1 World REV01 ROM, without state loads,
RAM writes, input substitutions or fixture publication. Its comparison-only
execute callbacks observe `$13338`, immediately after the ROM copies raw
`$F604/$F605` to logical `$F602/$F603`, and the dispatched movement entry. Verified
ROM bytes at `$13332` are `31F8F604F602`; ground and rolling-air movement entries
are `$134EC` and `$1355A` respectively. All nine surrounding MZ1 frame-end rows
(movie 27,467–27,475) and all eleven MZ2_3 rows (47,115–47,125) match the
committed positions, subpositions, velocities, inertia, status, animation and
mapping exactly. MZ2_3 has offset 47,034; this observation does not concern the
separate `mz2` capture with offset 42,308.

At movie row 27,471 / MZ1 row four, both raw and logical held/new pairs are
`$00/$00` at the copy boundary and ground movement entry. At movie row 47,121 /
MZ2_3 row 87, both pairs are `$00/$00` at the copy boundary and rolling-air entry;
entry x-speed is `$0495`, and frame-end x remains `$00DD.7E00` with unchanged
x-speed `$0495` and y-speed `$03A8`, exactly matching the committed row. This
rules out a stale native body latch at these frontiers. The engine's actual
consumed cursor, applied offset and live sprite input still require its assigned
comparison-only publication probe; this native observation alone authorizes no
movement adjustment.

The native capture completed with exit zero in 115.894 seconds. Original movie
SHA-256 `f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`
remained unchanged; host SHA-256 is
`5e455b0cb3fa52d6415ef64677b8079b088204eefe971a37573667bb59efe917` and sampler
source SHA-256 is
`5b801b58479513f022e07724449292028604bcc4a56a5b0d798d51b06e0e5f00`.
Generated observations and provenance reside in the external task directory
`parity-r2-s1-controller-latch-20261007`; no recorder source or canonical fixture
changed. The raw CSV heading `vfc` actually denotes the level frame counter at
`$FE04`, not the vblank counter. The prior GUI approach was rejected: Mesa EGL
crashed during initialization before producing observations, so its process exit
supplies no gameplay evidence. The headless approach validated the existing
BizHawk 2.11 installation and produced actual body and frame-end observations.

The independent S3K native mask observation likewise uses the original full
movie and verified locked-on ROM through that existing headless host. The movie
contains no active P2 input; all original P1, Power and Reset inputs and sync
settings are preserved. Callbacks at `$C4CC` and `$C4D0` bracket the verified ROM
bytes `42B8FF924E75` (`clr.l ($FF92).w; rts`). At movie row 52,944, AIZ2 level
counter 6,492, the mask changes from `$0000003A` to `$00000000` while player state
is unchanged. That player state matches AIZ_5 row 6,512 exactly. Earlier mask
observations retain bit one across the seamless AIZ1 → AIZ2 transition at movie
12,060 and retain the growing mask across special-stage returns and level reloads.
The clear is a full `SaveGame` boundary, not a generic zone or act load.

The mask remains zero at HCZ entry. All eleven HCZ rows 3,527–3,537 (movie
57,135–57,145, segment offset 53,608) match committed player position,
subposition, velocities, inertia, status, animation, mapping, level frame counter
and vblank word. Player object control changes `$00` → `$53` at row 3,531 while
the mask remains zero; bit one is set when the movie enters special-stage mode
`$34` at 57,182. This distinguishes the native player-capture phase from the
later collected-mask write. The queued engine probe still must establish its
actual retained mask and ring-deletion dispatch before qualifying a correction.

The native observation completed with exit zero in 101.719 seconds, with one
paired full `SaveGame` callback. Original movie SHA-256
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`
remained unchanged; sampler source SHA-256 is
`108fba2b21adfa3a87d5df2750a1d90024867112466ec264477db0cf3822925d`.
The external task directory `parity-r2-s3k-ring-mask-20261007` retains its native
observations and provenance. This added no RAM writes, state loads, input
substitutions, producer-source changes or canonical fixture publication.

### Published actual-main qualification

The mandatory actual-main run `20261007T213945Z-2b9d2b4b` measured
`be3c3141808c98d4c56a3dfb0fee19e241720154`, using integration base
`37a57ebdbe62864737f39e7b14c72932fbaf3d74`. It completed 3,020 selected
ordinary classes / 3,018 reports / 26,373 tests / 28 failures / zero errors /
62 skips in 6,523.21 seconds. Fresh guards completed 86 reports / 672 tests
with zero failures, errors or skips in 214.76 seconds. Neither lane timed out
or omitted selected cases.

The owner and validator compared all 28 complete concrete first assertion
lines and all 62 literal first skip reasons with the qualified `4cfb` baseline:
27 assertions match literally; the complete integrated SSZ first line, 2,951
characters, matches after only the exception-prefix removal and verified
`RewindObjectStateBlob@hex` normalization. They report zero new, worsened or
unattributed cases and no ROM skips. The parity coordinator read the user's
terminal outcome and the durable Sitar design after the owner consumed and
acknowledged the run; it does not claim a fresh live read of those deleted
diagnostics. Independently retained `4cfb` concrete assertions and skip reasons
remain the expected comparison values qualified by that complete comparison.

Published `develop` is `a87271f4300f22d280ab6f60c32ce411cd82aec6`. A complete
`be3` to `a872` changed-path check finds only the dated Sitar design; engine,
tests and build policy are unchanged. Fetch and fast-forward-only pull confirmed
that published head with unrelated main dirt preserved. The main freeze is
released. This is accepted inherited-failure qualification, not a green full
suite; `a872` is the destination base for the parity delivery selection.

Additional direct locked-on ROM bytes establish the full SaveGame branches:
`$C434: 4A78FFAE66000092` tests SK-alone and branches to `$C4CC`;
`$C43C: 2038E6606700008A` reads the save pointer and branches to `$C4CC`
when zero. The completed native CSV omitted those fields, so its actual chosen
save branch is unobserved. This byte check corroborates the No Save and
SK-alone contract without claiming a separately measured branch or rerunning
the completed native movie.

### Live engine input publication proof

The frozen comparison-only S1 engine probe completed at private assembly
`ef6fdfb93275844c2af0bfd2a59f356a1900007b`: the canonical full chain and two
prefixes ran three tests, one inherited chain failure, zero errors/skips. The
MZ1 segment-seven total remains 5,466; MZ2_3 segment twelve remains 196,129.
The probe is causal evidence, not a frontier improvement or passing chain.

At movie rows 27,471 and 47,121, BK2 identity, prepared and applied identities,
zero offset, validation held mask, applied held mask and the actual InputHandler
logical override all agree on neutral input. Nevertheless the dispatched sprite
movement consumes Up with forced mask `$0001`, then Right with forced mask
`$0008` and the legacy forced-Right flag. These masks persist into the next
neutral rows. At 47,121 engine x-speed changes `$0495` → `$04AD`, producing
`$00DD.9600`; native x-speed remains `$0495`, producing `$00DD.7E00` with
identical y-speed `$03A8`. At 27,471 engine retains LookUp `$07` / mapping `$05`,
while native is Wait `$05` / mapping `$01`. Neither engine frame is control
locked, movement locked or input suppressed. The owning common load method
seeds a BK2 row into persistent scripted forced-input fields; the logical bridge
and cursor were already correct. All three instrumented sources were restored
from exact HEAD blobs and the comparison-only helper removed after terminal
inspection. The production correction belongs at publication, not movement.

### Live engine ring-mask proof

The frozen S3K engine probe completed one canonical chain, one inherited
failure, zero errors/skips. The actual mask grows to `$0000003A` across AIZ
special stages and survives AIZ results completion, HCZ load, title and release.
HCZ initializes its ring at `$1440/$05C0` with bit one set and deletes it as
already collected. The native observation clears the mask to zero at full
SaveGame movie row 52,944. This proves the retained-mask/ring-deletion cause;
the exact Results SaveGame gate is separately identified from source. Sampling
has no reset, input substitution or runtime writes. Both instrumented sources,
helper classes and temporary probe diagnostics were accounted for and removed.
The clear is scoped to verified full SaveGame callers, with special-stage,
lives-only, generic persistence, seamless handoff and death/reload preserving
collection state. Qualification of the correction is recorded separately below.

### Shared scheduled-input publication correction

Private composition merged the separately qualified deferred-publication
regression as `0114edf03bb64932d3ae40821f2ba669a5e99577`. Root then removed
common-load BK2 writes to sprite forced-mask and forced-jump fields. Successful
activation invokes the existing live GameLoop logical-input bridge through a
gameplay-scoped callback and the unannotated `ScheduledPlaybackInputOps` host
bridge. The three context methods remain package-private. Its stable method reference reads the current input
handler; a separately tracked context and identity-checked detach preserve a
newer loop's registration. Callback ownership is cleared before reentrant
teardown hooks and rebound on each ready refresh. It is resource plumbing,
not gameplay rewind state; no input cursor, physics row or trace auxiliary value
is synthesized.

The S1 worker independently reviewed the initial four-file change and lifetime
cases with no material objection. Actual pin verification caught its API
visibility mistake: `GameplayModeContext` is annotated, so its three new public
callback methods entered the candidate surface. Root rejected that shape and
retained the existing pins, using package-private context methods plus the
unannotated host bridge. Existing Engine and headless boot attach
replacement contexts explicitly. External session replacement without attaching
a loop remains an existing resolver limitation; this change does not redesign
that resolver. The regressions exercise real level reload publication without
an Engine-global loop, real held/release bodies and cursor advancement, logical
repress edge publication, handler replacement, preserved scripted Right,
older-loop detach, callback identity and teardown reentry. They do not claim a
second executed jump from the repress snapshot.

Root old-code unit requests `20200`, `28132`, `31210` and `42359` were cancelled
while queued with exit130, before Maven admission; none is an executed unit RED.
The first requests had fixture lifecycle/accessor corrections. The completed
canonical live probe `87248` remains the reproduced causal baseline. The final
candidate focus command is `DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3
tools/testing/maven_queue.py --lean -Dmse=off
-Dtest=TestScheduledLevelPlaybackInput,TestScheduledPlaybackInputPublisher,TestS1VisualPlaybackControlLock,TestLogicalInputControlLockLatch,TestGameLoopFreshLevelHandoff,TestTraceSessionLauncherRunBranch,TestModApiSignatureSurface,TestFreshLevelBoundaryRewindHeadless,TestFbzSandopolisTimelineHeadless,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`,
with all three verified absolute root ROM properties. First candidate session
90783 stopped at test compilation because two new regression calls used
nonexistent `GameServices.levelManager()`; no test executed. The actual `level()`
accessor was verified in its declaration and substituted. Retry 92089 at private
composition `0a457816c` executes 132 tests, with all eight new behavioral and
lifetime cases passing, zero errors/skips, and one candidate-signature failure
from the three public context methods. It is not a passing selection.

After composing FullSave commit `828bc94d8` as `45a941423`, the host-only bridge
correction's actual focused selection is `TestScheduledLevelPlaybackInput,
TestScheduledPlaybackInputPublisher,TestModApiSignatureSurface,
TestS3kFullSaveGameBoundary,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,
TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils` with the same ordinary
lean wrapper, environment and absolute ROM properties. Those selectors exercise
attach/detach/publication through real load behavior, both lifetime cases,
normalized API pins, FullSave and every mandatory startup keeper. The previously
passing unrelated controls are not repeated merely to retest forwarding and
visibility. Terminal session `48540` passes **82 tests in nine reports, zero failures,
errors or skips**, in 85 seconds of Maven execution. The selectors match two
existing level-loading classes. All eight new publication/lifetime cases, nine
API-pin cases, five FullSave cases and sixty mandatory startup keepers pass.
Source hashes remain frozen through completion. This is focused qualification,
not a whole-suite pass.

### Final composed canonical replay, 2026-10-08

Private composition `45a9414233553b0fed9dd0b764c06e3dadafd44f` includes
Spring commit `36ce205da2d978ee82a243aa9d07538e8b514dbe`, FullSave commit
`828bc94d8206fa65fd58433569b9d9ca3e73c522`, and positive deferred-publication
regression `a33b8029d3256ba9fbb82e3228ad1b9ed8a4d7fc`. The root input correction
above is the additional frozen working change. Terminal request `71207`
executes sixteen cases in fifteen selected classes: **three assertion failures,
zero errors and zero skips**, in 108 seconds of Maven execution, finishing at
2026-10-08 03:14:26 UTC. All thirteen other cases pass, including both S1 prefix
cases, S2 ARZ/CPZ/EHZ/special-stage/prefix controls and standalone S3K HCZ.
Both source hashes and terminal reports were inspected. These are canonical
trace-profile results, separate from ordinary/guard qualification.

Exact selector and launch shape (the actual invocation used verified absolute
original ROM paths; `${OPENGGF_ROM_ROOT}` denotes their existing root directory):

```sh
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.runOrder=alphabetical \
  "-Dtest=TestS1CompleteEmeraldRunChain,TestS1CompleteEmeraldRunPrefix,TestS2Arz1CompleteEmeraldsSegmentTraceReplay,TestS2Cpz1Seg8CompleteEmeraldsSegmentTraceReplay,TestS2Cpz2Seg9CompleteEmeraldsSegmentTraceReplay,TestS2Cpz2Seg10CompleteEmeraldsSegmentTraceReplay,TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay,TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay,TestS2SpecialStage2TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2CompleteEmeraldRunPrefix,TestS2CompleteEmeraldRunChain,TestS3kSonicTailsHczSegmentTraceReplay,TestS3kSonicTailsCompleteEmeraldRunChain" \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

**S1:** the full-chain stop advances from segment twelve to segment thirty-three.
Segment seven falls from 5,466 to **192** physics differences; animation,
bootstrap and warnings are zero, first non-camera movement mismatch row3,261
Y `$03CB/$03D5`. Segment eight remains zero. Segment twelve falls from 196,129
to **three auxiliary queue-state comparisons**, all at row101:
`queue.s1_nemesis_plc.prepared`, `queued_fingerprints` and `remaining_work`.
They are classified in the comparator's physics group; they are not three
movement errors. Its complete report has zero animation/bootstrap/warnings,
and the previously blocked giant-ring exit succeeds. All three previously
reachable early art-gap failures close. The longer walk exposes seven later
art-gap failures, beginning LZ3→SLZ1 edge ordinal78,614/78,616 and transfer
ID39,307/39,308. Subsequent gap failures are SLZ1→SLZ2, SLZ2→SLZ3,
SLZ3→SBZ1, SBZ1→SBZ2, SBZ2→LZ4 and LZ4→SBZ3; all33 gap observations
were inspected. Newly reached downstream errors are retained as frontiers,
not described as regressions against formerly unexecuted route coverage.

| S1 segment | Source closure | Physics-group errors | Animation errors | First non-camera comparison (native/engine) |
| --- | --- | --- | --- | --- |
| 7 | complete | 192 | 0 | row 3261 `y` 0x03CB/0x03D5 |
| 12 | complete | 3 | 0 | row 101 `queue.s1_nemesis_plc.prepared` true/false |
| 15 | complete | 6 | 0 | row 102 `queue.s1_nemesis_plc.prepared` true/false |
| 22 | complete | 44 | 0 | camera-only; no non-camera mismatch |
| 25 | complete | 10 | 0 | row 2705 `y` 0x0452/0x0453 |
| 26 | complete | 10202 | 10 | row 3838 `y` 0x0655/0x0653 |
| 27 | complete | 3519 | 361 | row 1771 `rings` 1/0 |
| 29 | complete | 890 | 566 | row 8557 `x_sub` 0xF500/0x0000 |
| 31 | complete | 1 | 0 | row 7821 `rings` 9/10 |
| 32 | complete | 12 | 0 | row 11 `queue.s1_nemesis_plc.prepared` true/false |
| 33 | incomplete | 2615 | 577 | row 356 `dynamic_art.edges` [264, 265]/[] |

The eighteen failing chain axes comprise one walk failure, ten completed
segment assertions and seven art-gap assertions. Segment33's partial report
is additional incomplete evidence, not a completed failing segment axis.
The production walk loses ownership in `TITLE_CARD`, loadGeneration24,
progressionZone6/romZone5/act0, BK2 cursor210,395. Earliest remaining
movement work is MZ1 row3,261; the small Nemesis queue frontier and the newly
reached later route/transition failures remain separate tasks.

**S2:** the all-subtype initialization return closes standalone ARZ's 3,203
errors, chain ARZ's19,884 and the following SS7 art's22,405; the root combined
run reproduces zero compared physics/animation/art for reached segments.
The Coconuts/CPZ/standalone-stage controls and prefix remain passing.
The chain still has eleven axes: ten art-gap clock mismatches and the physical
walk remaining in `SPECIAL_STAGE_RESULTS` at movie cursor101,691, SS7.
Earliest gap `ss→seg2_ehz1`, edge0 `movie_logical_frame`, expects10,308 and
observes10,268. The subsequent failing boundaries are `ss_2→seg3_ehz1`,
`ss_3→seg4_ehz1`, `seg4_ehz1→seg5_ehz2`, `ss_4→seg6_ehz2`,
`ss_5→seg7_ehz2`, `seg7_ehz2→seg8_cpz1`, `seg8_cpz1→seg9_cpz2`,
`ss_6→seg10_cpz2` and `seg10_cpz2→seg11_arz1`. The complete gap report
contains17 observed boundaries and ten failed comparisons. The full run's
post-SS7 content remains unreached; this is not S2 completion.

**S3K:** HCZ segment9 has zero physics/animation/bootstrap/warnings across its
complete3,574-row source, with55 lag rows; its standalone test also passes all
3,519 executed rows. FullSave clears the native mask at the preserved caller
gates and the HCZ giant-ring handoff now reaches segment11. AIZ segment6
retains189 differences, first row3,319 `sidekick_x` `$31C1/$31CA`; segment8
retains13,254 (13,113 physics and141 animation), first row1,583 `sidekick_x`
`$366C/$3674`. The chain's three axes are those two segment assertions and
loss of production ownership in segment11, `LEVEL`, loadGeneration9,
progressionZone1/romZone1/act0, cursor68,801. Segment11's partial report has
82,067 comparisons (69,393 physics and12,674 animation), first non-camera
row1,510 Y `$07D6/$07DF`, fifty lag rows, zero bootstrap/warnings and
`complete=false`. Its source closure and remaining hardware-completion
obligations are not certified by this partial report. No S3K dynamic-art,
pixel or audio parity is inferred where that fixture lacks the comparison.

The next delivery check is the actual combined change-based ordinary/guard
selection against published `a872`. Positive FBZ→SOZ restoration is separately
qualified at both captured publication phases by sixteen focused passing
cases and two matched old-owner assertion failures. Local consumer checks and
these trace improvements do not discharge the matrices' full-route, donor,
roster, viewport, load/respawn, rewind or native-presentation obligations.

### Updated delivery destination before broad execution

While the first candidate request92466 was queued, fetch observed published
`33d3976c53304dbbea1c695914ecdd7bfc64cf9d` and actual-main
`ae2dfd3dd75d7a4dc7ece3bdebbdebbb41fcdb36`. Only this task's still-unadmitted
request was cancelled, exit130: no Maven admission, tests or diagnostic run
directory. No other job was cancelled or acknowledged.

Published throughput changes are qualified separately by364 focused cases,
zero failures/errors/skips. Their source/test ownership and draw-only capture
improvements are recorded in [the existing memory investigation](../research/2026-10-07-ordinary-suite-memory-cause.md#integrated-natural-collection-control).
Sitar's mod-only polish has141 updated-base passing cases and148 integrated
passing cases in fourteen reports; all141 prior identities/statuses remain,
with seven new passing presentation/audio regressions. The owner explicitly
uses proportionate validation for that mod change and retains its queued fresh
guards. Main stays frozen for that owner's verification; no whole-suite pass
at either intervening revision is inferred.

Private composition merged actual-mainae2 as
`358081604650e9f2d13085064c77af89f70a9c34` with no conflicts. All45 parity
production/test/mirrored-skill paths remain byte-identical to386e2ad66; prior
focused and canonical qualification therefore remains applicable to those
unchanged implementations. The actual-base plan now selects3,027 ordinary
classes and allguards. Full source is frozen for the combined candidate run,
with a150-minute execution limit excluding queue waits and a10-minute
no-output limit. Either timeout leaves qualification incomplete.

The completedbe3 full comparison remains the concrete inherited-failure/skip
expectation, supplemented by the intervening scoped qualifications above.
Those do not establish source equivalence betweenbe3 andae2 or a freshly
measured full baseline atae2. The combined run will compare full assertions,
types and literal skip reasons; any disputed case requires a bounded matched
current-destination check, rather than silent normalization or a totals-only
claim. Its own post-integration actual-main full run remains required. The
Sitar owner will separately supply its terminal guard and published successor;
this task will preserve that verification and reconcile its evidence-only head.

### Completed candidate ordinary lane and guard repairs

Candidate `7d87e02a1c75b80c8f5969ab6654ee1cf3976b7a`, measured against
`ae2dfd3dd75d7a4dc7ece3bdebbdebbb41fcdb36`, completes owned run
`20261008T034810Z-47c3194d`. The command is:

```sh
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base ae2dfd3dd75d7a4dc7ece3bdebbdebbb41fcdb36 --run --max-minutes 150
```

The full ordinary selection contains 3,027 source classes and produces
3,025 reports / 26,422 tests / **27 failures, zero errors, 62 skips** in
5,221.08 seconds; Maven exits 1. Every remaining failure retains its qualified
baseline identity, kind, type and full concrete first assertion line: 26 match
literally, and the named SSZ Tails parameterized case matches after stripping
only the exception prefix and replacing verified `RewindObjectStateBlob@hex`
identities with `@HASH`. Its complete 2,952-character first line normalizes to
2,907 characters, exactly matching the retained baseline, rather than a capped
message prefix. All 62 skip identities and first causal lines match literally;
fresh XML corroborates all 62, with no ROM skips or omitted diagnostics. The
sole removed failure is
`TestFbzSandopolisTimelineHeadless#productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice`,
which passes without a skip. Both lanes finish without timeout. This is an
inherited ordinary-failure qualification, not a green whole-suite result.

The same invocation's separate fresh guard JVM produces 86 reports / 672
cases / **two failures, zero errors, zero skips**, in 211.56 seconds. Both
failures are caused by this composition and block integration:

- `TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction`:
  the pending-shield snapshot implementation grows `AbstractPlayableSprite`
  to 3,261 effective lines against its unchanged 3,258 budget.
- `TestSingletonLifecycleGuard#ambientGameplayModeSetupsDoNotGrowWithoutLifecycleTriage`:
  `TestScheduledPlaybackInputPublisher#configureServices` directly configures
  ambient engine services instead of using the central fixture reset.

The bounded repair moves the pending-shield capture/restore branches into the
existing package-private `PendingInstaShieldRewindExtra` collaborator, retaining
its record component, snapshot interface, conditions, recreation side effects,
exception text and deferred registration order. Publisher tests use
`TestEnvironment.resetAll()` before and after each case. No guard budget,
public API, descriptor, pin or timing/physics behavior changes. Affected
shield/input/rewind/API regressions and separate fresh guards must qualify
these repairs before integration; the actual-main full run remains required.
The consumed owned diagnostic directory was acknowledged and removed.

Repair qualification measures the frozen three-file follow-up to `7d87e02a`.
Focused request `52813` completes at 2026-10-08 05:26:03 UTC: ten fresh XML
reports / **56 cases, zero failures/errors/skips**, Maven exit 0, 81 seconds
execution after 217 seconds queued. Its selectors are `TestShieldRewindRestore`,
`TestShieldRewindPendingRestore`, `TestAbstractPlayableSpriteRewindCapture`,
`TestPlayableSubclassRewind`, `TestPlayableSpriteRewindState`,
`TestFbzSandopolisTimelineHeadless`, `TestFreshLevelBoundaryRewindHeadless`,
`TestModApiSignatureSurface`, `TestScheduledLevelPlaybackInput` and
`TestScheduledPlaybackInputPublisher`, using the queue wrapper, `-Dmse=off`
and all three verified absolute ROM properties. Both publication phases and
the repaired FBZ→SOZ full-registry replay remain passing.

Separate fresh request `40077` runs
`DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B`.
It completes at 2026-10-08 05:29:58 UTC: **86 fresh XML reports / 672 cases,
zero failures/errors/skips**, Maven exit 0, 206 seconds execution, no queue
wait. Both formerly failing guard identities now pass; all three repair source
hashes remain unchanged through both invocations. These bounded repairs are
qualified narrowly after the completed red broad run, following the repository's
regression-repair policy. The combined actual-main full run still verifies the
final integrated source. The prior canonical results are reused for unchanged
gameplay behavior; this structural repair does not establish a new canonical
trace-profile measurement.

### Actual-main delivery qualification

The Sitar owner released its source freeze after 148 integrated focused cases
and 672 fresh guard cases passed without failures/errors/skips at exact `ae2`.
Published successor `09cfcc0f882305d6a0c6a4fc05b600d3ca27a888` changes only
the Sitar example README, dated polish evidence and measurement hazards. It
establishes no new full ordinary baseline. Private merge
`3d10f507efb35069ee4de7a7fa3047de25518890` retains both stock-input hazards
and the independently added Sitar preparation hazard at their sole prose
conflict; no engine/test/resources/POM/API delta accompanies that merge.
Guard repairs are committed as `98137d8516a06e546185aa070bf0bf574473c04a`.

Normal integration into the existing `develop` branch produces
**`5d1ff9b8206594ee1c979ae5174328dd9134ffd4`**, whose complete committed tree
is identical to the qualified private composition. Main's three dirty
disassembly submodules and four unrelated untracked files are preserved.
The source remains frozen through mandatory owned run
**`20261008T053400Z-a3c0531f`**, against the actual published destination:

```sh
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 09cfcc0f882305d6a0c6a4fc05b600d3ca27a888 --run --max-minutes 150
```

The inspected plan selects the **full 3,027-class ordinary inventory and all
fresh guards**, with one normal ordinary fork, no queue wait and the previously
stated 150-minute execution / ten-minute no-output stopping rules. Its normal
Maven command has no narrowed test selection and uses all three original,
verified absolute ROM paths. Terminal summaries are written at
2026-10-08 07:04:34.078316 UTC:

| Actual-main lane | Reports | Cases | Failures | Errors | Skips | Execution seconds | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Full ordinary | 3025 | 26422 | 27 | 0 | 62 | 5144.88 | 1 |
| Separate fresh guards | 86 | 672 | 0 | 0 | 0 | 230.04 | 0 |

Both lanes complete without timeout or omitted diagnostics. All 27 remaining
failure identities, kinds, types and full concrete first assertion lines match
the qualified inherited expectation: **26 literally**, and the named SSZ
Tails case after only exception-prefix removal and verified
`RewindObjectStateBlob@hex` normalization. Its complete 2,952-character first
line becomes the same 2,907-character assertion retained from the qualified
baseline. All **62 skip identities and first causal lines match literally**;
there are no new/worsened/unattributed cases or ROM skips. The sole removed
failure is the freshly passing, unskipped FBZ→SOZ timeline restoration case.
All 138 Sitar cases, thirteen new input/save controls, Infinite Sonic's 206
passing cases (including its driver-observation case) pass; Infinite's 32 skips remain
literal inherited matches. The two formerly failing structural guards pass
with unchanged budgets and lifecycle policy.

This accepts inherited ordinary failures; it is **not a green whole-suite or
full-game parity claim**. The separate canonical trace-profile measurement above
retains its three open chains, with the newly reached S1/S3K frontiers and S2
clock/results frontiers unchanged by the structural follow-up. The source freeze
is released after terminal comparison. The exact consumed diagnostic run is
acknowledged and its directory removed; no raw logs are archived. The final
follow-up changes evidence/current status prose only and therefore uses relevant
links, syntax, mirror and policy checks rather than repeating engine tests.


## Round 3 continuation — updated base and native PLC boundary

The requested continuation starts privately from published develop
`098053c4a01c2af283ca6797463bb5051442ef0b` on 2026-10-08. Its complete delta
from the previous published `c039c0091` is the independently qualified SOZ
capture throughput change, standalone JFR reader and accompanying prose.
There is no production Java, POM, hook, workflow or selection-policy delta.
The two changed SOZ fixtures retain their inputs, frame counts, assertions,
case identities and drawing; discarded readbacks and duplicate diff computation
are removed. The upstream matched 32 cases and integrated 23-case subset pass
without skips. This is focused qualification, not a new full ordinary pass.
The previous actual-main run at `5d1ff9b82` remains the exact source of the
27 full failure assertions and 62 literal skip expectations; neither changed
SOZ fixture is among those failures or skips. Final combined parity qualification
is still pending and must name its actual source and destination.

Three saved native Sol workers resume in new isolated worktrees: S1 MZ1 death,
S2 special-stage results/return, and returned S3K HCZ. Root retains integration,
shared ledgers and unassigned shared runtime ownership. Main remains on develop,
with its three dirty disassembly submodules and four user-authored untracked
files preserved. The separate Sitar feedback owner reports only mod and generic
scene-music cue scope; its private `3afd81879` full run and all unrelated jobs
are preserved. Integration/source-freeze coordination remains mandatory.

### S1 queue arm: accepted native observation, no runtime correction

The complete original S1 movie, sync settings and verified World REV01 ROM are
replayed through the existing BizHawk 2.11/GPGX headless host, without RAM writes,
input substitution, fixture publication or gameplay hydration. The diagnostic
observes `RunPLC` entry `$0015E4`, the instruction after its count write `$00160E`,
and its return `$001638`, plus frame ends. The ROM bytes at `$00160A` are
`31 C2 F6 F8` (`move.w d2,(v_plc_patternsleft).w`), followed by the code-table
call. This is the shipped `FixBugs=0` branch of `RunPLC`; the fixed branch moves
that write after table preparation.

The corrected capture completes with exit 0 in95.815 seconds, 38 observations,
one arm, 71,641 execute callbacks and thirteen consecutive frame-end samples.
Every compared player position, fraction, velocity, inertia, status, routine,
animation and mapping value, plus `v_framecount` and the low word at `$FE0E` of `v_vblank_count`, matches
committed MZ2_3 rows95–107 literally. Native zone/act are2/1; the manifest's act2
is a one-based label, not a RAM value. The initial act2 assertion rejected the
first sample before accepted data; that failed attempt is not evidence.

At original BK2 index47,135 (completed frame47,136 / segment row101),
`RunPLC` enters with zero patterns and descriptor source `$03C040`, destination
`$B000`. Before `NemDec_BuildCodeTable` it writes eighteen patterns; the same
row's frame end has gameplay counter102 and VBlank counter46,764. The call
returns during index47,136 / row102, after VBlank advances to46,765 while the
gameplay counter remains102. Thus the observed lag interrupted the very call
that had already exposed its arm. The fixture's prepared=true/remaining18 is
correct, whereas the current counter-lookahead hold defers it one row.

This disproves treating a held gameplay counter as proof that `RunPLC` has not
armed. It does not justify moving every arm earlier, using queue comparison
values as readiness input, widening comparisons or fitting a row-specific
exception. The existing S1 hardware-timing kind is implemented, but this fixture
contains no corresponding stream. The three queue comparisons remain an open
boundary until a matching native timing stream or another general production
mechanism establishes the arm's service identity. No PLC/timing/fixture behavior
changes in this continuation are claimed from this diagnostic.

Durable source, provenance, observations and completion marker remain in the
external task directory `parity-r3-s1-plc-arm-20261008`, capture `capture-headless-v2`.
The sampler source SHA-256 is
`22b8ec61b6e0392e21e80685d7e2eabfc171ccb979040752f657d97542184014`;
the original movie SHA-256 remains
`f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`.
The recurring measurement hazard is recorded in the existing trace briefing.

### Fresh round-3 frontier checks

The three workers independently replay the unchanged stock production at
`098053c4a`; these are terminal trace-profile checks, separate from ordinary
suite qualification. All use the original absolute, identity-verified ROMs and
the unchanged compressed fixtures.

| Lane | Cases / failures / errors / skips | Reproduced frontier |
| --- | --- | --- |
| S1 full chain and standalone MZ1 | 2 / 1 / 0 / 0 | Chain MZ1 segment 7 retains 192 physics-group differences, zero animation differences and complete comparison; first row 3261 Y `$03CB/$03D5`. Standalone MZ1 passes. |
| S2 full chain, prefix and standalone SS7 | 3 / 1 / 0 / 0 | Prefix and SS7 pass. The chain retains the SS7 results walk at cursor 101691 and ten gap-clock axes; the first return edge remains native 10308 / engine 10268. Every reached segment through 17 still has zero physics/art differences. |
| S3K Sonic+Tails full chain | 1 / 1 / 0 / 0 | Returned HCZ segment 11 retains 69,393 physics and 12,674 animation differences and loses ownership at cursor 68801. The first comparison of any kind is mapping row 1507, native `$63` / engine `$95`, before the first Y difference at row 1510. |

S1's bounded fourteen-case death regression runs against the old production
first: ten intended failures, zero errors/skips, Maven 24.864 seconds. The
native rolling-floor reset is missing before death velocity; ordinary standing
and already-correct reverse/ceiling controls distinguish that omission from a
blanket position adjustment. Candidate focused and canonical checks remain
pending at this checkpoint. Later S1 segment totals are sums of physics and
animation groups, not new physics-only counts.

S2's source proof is the stock `Obj6F_TallyScore` gate: successful acquisition
with exactly seven emeralds, except native `Player_mode == 2`, selects routine
`$30` immediately. Its leave/init/return/latch/hold/display sequence is 210
subsequent dispatches, not an arbitrary replacement timer. The earlier Perfect
branch has no stock results input in the current model and remains unsupported;
live results rewind is excluded by the existing GameLoop mode gate. Neither
limitation is silently extended by this bounded correction. The new regression
is queued against unchanged production; no candidate pass is claimed yet.

The S3K native fan/belt observation completes with 1,431 samples and seventeen
matching surrounding player rows. At the first span the fan is already active,
rejecting a timer-start explanation. ROM `$30834` contains one `MoveSprite2`
call before `Draw_Sprite`; 565 same-slot/code bubble observations move by exactly
eight pixels upward. The engine's doubled motion is independently incorrect.
Native fan slot 10 precedes belt slot 91; the engine baseline has fan slot 24
after belt slot 11. Whether correcting bubble occupancy explains that reversed
ordering requires the pending matched engine probe and one-variable candidate
replay. No dispatcher override, timer fit or full HCZ closure is claimed.

While these checks wait, another owner integrates mod framework readiness into
main at `bf7c56e1986fd8ca4a4c5e0cafdf3087e73b545a`; its actual-main run and
publication are pending. Root explicitly preserves that source freeze and all
Sitar jobs. Round-3 trees remain private on `098053c4a` until the actual published
successor and qualification can be reconciled. In particular, upstream character
callback/specification changes must be composed with the separate death-reset
hunk before final validation. This checkpoint is evidence of verified gaps and
pending candidates, not delivery or a new whole-suite pass.

### Additional native bubble lifetime evidence and queue interruption

The separate HCZ bubble lifecycle capture completes with 50,341 observations:
24,404 entries at ROM `$30834` and 25,937 frame-end slot samples. Root independently
matches all 630 represented frame ends against the existing HCZ2 fixture for
player X/Y/mapping, camera X/Y, gameplay counter and VBlank low word, with zero
differences. The original BK2 index minus 63,075 selects the fixture row.
There are 11,900 same-slot/code updates that survive more than 64 pixels above
the camera while still below the water surface, each moving upward by eight
pixels. This directly rejects the engine's camera-based bubble retirement.
All 254 sampled surface-eligible entries retire or reuse their slot by frame
end (247 absent, seven reused); none retains the same owner. The longest
contiguous observed lifetime is 100 updates, so this capture does not exercise
the engine's 120-update cap. The owning ROM routine has no such cap, and
`Draw_Sprite` only enqueues drawing; that source proof is distinct from observed
lifetime coverage.

All 281 frame-end first-appearance or same-slot reset samples with a preceding
frame-end sample have `Level_frame_counter & 3 == 0`; their VBlank residues vary.
This corroborates the ROM's gameplay-counter gate, but does not identify every
allocation call or independently prove random-number ordering. The native
routine allocates before drawing randomness; the existing reserved-slot factory
can reproduce that order without changing shared object allocation. Speed,
lifetime, clock and saturated-pool controls are to be tested independently before
claiming their contribution to the returned HCZ frontier. Durable source and
accepted observations are in external task directory
`parity-r3-s3k-fan-conveyor-20261008`, capture `bubble-lifecycle-v1`; sampler
SHA-256 is `b59b0b952980f2526b70cd1e81ccf187ce83df08708bfcd2392c39d906327444`.

The first S2 regression request, session60545, ends while queued at
2026-10-08T10:29:32Z: exit130, 2,447.2 seconds waiting, zero execution/hold time.
It therefore provides no red test result. Neither root nor the worker requested
cancellation. Read-only inspection establishes that the wrapper reports this
outcome for an interrupt/termination signal; the sender is unobserved, and the
parent app process remains alive. No queue timeout or internal cancellation
mechanism is established. The unchanged command is retried as session9560;
no queue code, lock or foreign job is changed.

### Canonical S1 timing capture: native and loader qualification, publication withheld

Root uses the `bizhawk-headless-trace` skill and an isolated producer checkout
at pinned TraceChaser `e0a2443e086ca657a49227c5467eeecd06e40ece`. Its verified
Roslyn build and seven `S1PlcHardwareTimingObserver` tests pass with zero
failures/skips. The complete original 225,101-input movie is then captured with
`--mode trace --run-id s1-sonic-complete-withemeralds --load-queue-state
--compress-threshold 1`, explicit producer/consumer/fixture roots and the
original absolute S1 ROM. Session67758 completes exit 0 with BizHawk2.11,
34 segments and twelve transitions; the owning process tree is absent.
There are no input substitutions, RAM writes, observation edits or new
recorder behavior in this candidate.

The whole manifest is literally unchanged. All 34 decompressed physics streams
(208,586 represented rows) and all 34 decompressed auxiliary streams
(2,755,825 events) are byte-identical to the old fixture. Every metadata delta
is solely `recording_date`, August4 to October8. Differences in some stored
gzip bytes are encoding differences, not changed observations. The added
28 level timing streams contain 242 canonically ordered events with gapless
run-wide ordinals0–241. MZ2_3 row101/ordinal59 has fingerprint
`sha256:0495d001d7b7d63f2d70ab32c084cb31f69866a3a401d712c7ecc2861dd2206e`;
root independently computes it from the earlier native source `$03C040`,
destination tile `$580` and eighteen-pattern ROM header. This corroborates
the same arm without deriving gameplay expectations from the timing stream.
Root also independently reads all 32 ROM cue lists at `$01DD86`: 203 entries,
150 unique descriptor identities. Every one of the 242 native events belongs
to that ROM-defined set (38 distinct observed identities, zero unmatched).
This establishes descriptor membership; it does not substitute for proving
execution order, row ownership or a matching engine submission in replay.

Accepted output remains outside the repo in
`parity-r3-s1-canonical-timing-20261008/capture-v5`: 131 files, 41,932,357 bytes.
The ordered JSON inventory (path/bytes/SHA-256, sorted keys, compact separators)
has SHA-256 `3590bd88eb32c644af297fc8175cbe0abdd925a06f1277b86c4c779f2032e6a2`.
The fresh producer executable is
`ecea6c71c94f7800afe303a76da7d70e2ea98aec56756fbeb33383dece0ae178`, distinct
from the earlier diagnostic binary. The original BK2 remains intact. The
complete private fixture candidate contains it plus every captured segment;
timing streams are losslessly gzip-compressed with zero timestamp. Its
132-file, 41,989,269-byte inventory SHA-256 is
`b94858c17f194983ea9abd370e9c62b83a8f99fd5b3526171af89811eea96f39`.
The pinned producer's read-only `traces/validate_trace_v5.py <capture-v5>
--require-frame-keyed-auxiliary` completes exit 0 in session56500. It validates
the accepted native output, separately from the pending Java consumer checks.
The same validator also completes exit 0 against the installed package in
session75476, exercising the 28 compressed timing streams. The pinned producer
already supports that storage encoding; no producer source or pin changes.

The existing timing loader otherwise silently ignores `.gz` siblings. Root
adds the existing trace-file resolver and strict gzip decoding, preserving
plain-file precedence, exact UTF-8/framing/range checks and the v5 authority
registry. Six regressions cover equal edges/policies, empty recorded authority,
invalid UTF-8/framing/range, damaged checksum, zero-byte gzip and sibling
precedence. Initial eleven-class focus89934 completes with 111 passing cases,
zero errors/skips, and eleven fresh XML suites. A subsequent one-case
regression72515 reproduces a truncated zero-byte gzip being accepted as
recorded-empty timing: the expected rejection is absent. Restricting the old
empty-file shortcut to the plain filename retains that compatibility while
decoding every compressed file strictly. Final focus92204 completes at
2026-10-08T12:13:15Z with eleven fresh suites and 112 passing cases, zero
failures/errors/skips. Both focus commands use queued `--lean -Dmse=off` Maven
with the loader, S1 arm, trace-data, loading-contract, manifest, compression,
movie-alignment, positive-input, authority, interstitial and run-coordinator
selectors. No authority registry or matching semantics change.

The normal `-Ptrace-replay` canonical chain, two prefix controls and standalone
MZ1 control run together as session25381 with the original absolute
S1 ROM. Production death handling remains unchanged in this timing-only tree;
the separate S1 worker qualifies its radius correction against the old timing
fixture. At 2026-10-08T12:15:42Z the replay completes exit1: four cases, one
chain failure, zero errors/skips; both prefix cases and standalone MZ1 pass.
MZ2_3 and MZ3_2 become complete with zero physics/animation/bootstrap errors
and zero warnings (previously three and six comparator errors). The original
MZ1 death mismatch remains 192 errors at row3261. LZ3 improves from 10,212 to
10,209 total errors, but the later route is not qualified: SLZ1 increases from
3,880 to 5,214, and SLZ2 acquires 8,606 errors. SLZ1 already misses the ring at
row1771; at row4570 the native completion `NEMESIS_PLC_QUEUE#170`, fingerprint
`sha256:766b2fc7fa7662ce89c289f933718b2ffd110d031b70d4e196be0de4a06741b8`,
has no prepared engine job. At the following completion the engine still
owns the late job170. The strict port retains these unmatched completions and
fails run closure. The terminal engine mode is LEVEL rather than the manifest's
TITLE_SCREEN. These downstream changes block publishing the whole fixture.

Root verifies the exact installed inventory before restoring only this run's
tracked package to its original state and removing the 28 owned new compressed
timing files. The complete accepted native capture and original movie remain
in the external task directory. No edges are dropped, renumbered or fitted to
engine behavior; no gameplay state, new work or substitute readiness is supplied.
The native reference can support a future correction of the earliest remaining
production frontier, after which whole-run timing qualification must be repeated.
The independently passing gzip transport fix remains in the delivery candidate.
There is no published S1 timing fixture, closed three-comparison frontier,
runtime PLC correction or new whole-suite qualification at this checkpoint.


### Round 3 composed candidate against the published framework

The three reused Sol workers finish their bounded source lanes independently:
S1 `1f7e16cab7d0`, S2 `1604e7f7e790`, S3K `fa2ac5e67b91`. Root retains their
actual commit ancestry in the private integration branch rather than leaving
cherry-picked worker histories unmerged. Published framework successor
`d740b7a0fadd97b2e7c104d56481a0235bdffb4c` is fetched and merged without
conflicts; the main `develop` checkout fast-forwards normally and reports
already up to date. Main's three dirty disassemblies, four unrelated untracked
files and every foreign worktree/job remain untouched. Framework source freeze
is explicitly released by its owner at12:43:48Z. Sitar's separate frozen
candidate `984cb8e2`, run20261008T123028Z-dbe57c8c/session16502, is preserved;
there is no implied authority to edit or cancel that lane.

Private composition `84f0c20f11c689a6f7969edfcbd1da349a2fb3e0` contains:

- Cross-game native death floor/radius reset, preserving centres, fractions,
  reverse-gravity semantics and existing hurt behavior. S1's complete MZ1
  segment7 closes all192 differences. The inherited segment33 premature-death
  route changes from3192 to3203 differences and cursor210395 to210396. Matched
  old/candidate canonical probes account for the exact +9physics/+2animation:
  one new common-row Y-speed mismatch plus one newly represented end row.
  This is attributed downstream propagation, not an unchanged or green route.
- Stock S2 seventh-emerald leave/init/return/hold/display phases, exact-seven
  and Tails-alone gates, native main-message draw suppression and continued
  emerald children. The results walk closes, reaching ARZ1/ARZ2. KiS2's existing
  presentation policy remains separate. Perfect-input support and live results
  rewind stay open; no stock driver, sequencer, GameLoop or fade changes.
- Fan-only S3K child movement, water retirement, gameplay clock and allocation
  before randomness. Native speed/lifecycle observations and independently
  failing regressions establish each local correction. Returned HCZ and earlier
  AIZ profiles remain unchanged; fan causation of the conveyor frontier is not
  established. No shared allocator or unrelated bubbler changes are included.
- Independently qualified strict compressed timing transport. The fresh whole
  S1 timing fixture remains withheld for the kill evidence above; the original
  committed comparison fixture is restored, including removal of the exact
  28 stale owned compressed resource copies from this worktree's build output.

The updated framework automerge changes no intended parity behavior. The
`AbstractPlayableSprite` delta against actual `d740` is only the native death
call. The package-private radius helper adds no exported API. Root focus4920
finishes at13:04:13Z, Maven exit 0: **31 fresh XML suites, 275 cases,
zero failures/errors/skips**. The original absolute paths for all three verified
ROMs are supplied. Four mandatory S3K startup selectors, geometry/custom-profile
consumers and exact API reflection remain green. Command, from
`.worktrees/ai-parity-swarm-20261008-r3-integration`:

```sh
OPENGGF_ROM_ROOT=/absolute/path/to/OpenGGF
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestDeathRadiusTransition,TestHurtAnimationPublication,TestDeathRestartRoutineParity,TestAbstractPlayableSpriteRewindCapture,TestHCZCGZFanObjectInstance,TestS3kHczCgzFanGraphRewind,TestHCZConveyorBeltObjectInstance,TestSonic2SpecialStageSuperResults,TestSonic2SpecialStageResultsTallyCadence,TestSonic2SpecialStageResultsPlcReadiness,TestSonic2SpecialStageResultsWidescreenCommands,TestSplitNameResultsMessages,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestHardwareTimingStreamLoader,TestSonic1PlcArmTiming,TestTraceDataHardwareTiming,TestTraceV5LoadingContract,TestTraceRunManifest,TestTraceFixtureCompressionGuard,TestTraceFixtureMovieAlignmentGuard,TestTraceV5PositiveInputGuard,TestHardwareTimingAuthorityGuard,TestHardwareTimingInterstitialStream,TestTraceRunHardwareTimingCoordinator,TestPlayableGroundTransitions,TestPhysicsProfileEditing,TestModApiSignatureSurface' \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

Root submits the three normal-profile canonical chains and controls together
as session71369, fifteen selectors/seventeen expected cases, alphabetical
order and the same original ROMs. This is a separate `-Ptrace-replay` lane,
without lean mode, fork/heap overrides, widened tolerances or injected state.
It finishes at13:34:11Z, Maven exit1/2:34 execution (1589seconds queue
waiting excluded): fifteen fresh XML suites, **17 cases, four qualified
assertion failures, zero errors/skips**. The failures are the three existing
complete-chain identities plus the matched inherited returned-HCZ standalone.
All thirteen controls pass. Composed reports exactly retain the worker lane
profiles: S1 MZ1=0, MZ2_3=3, segment33=3203; S2 ARZ1=119/ARZ2=47450;
S3K AIZ6=189/AIZ8=13254/HCZ9=0/returnedHCZ11=82067 and standalone3454.
First mismatch fields/values and incomplete ownership boundaries agree with
the lane evidence. The runtime/API framework integration introduces no new
trace frontier in this selection. This is composed domain qualification with
inherited and attributed failures, not a green complete-run claim. The exact
union command is recorded in the [frontier log](../../status/trace-frontier-log.md#2026-10-08--stock-parity-swarm-round-3-native-fixes-and-remaining-frontiers).

#### Updated-base evidence and required broad qualification

The framework owner's mandatory actual-main source run at
`bf7c56e1986fd8ca4a4c5e0cafdf3087e73b545a`, run20261008T103708Z-730bd986,
is reused as the updated ordinary/guard baseline. It completes 3056 selected
ordinary classes, 3054 reports, 26514 cases, **27 inherited failures,
zero errors and62 literal inherited skips**, 4913.15seconds. Separate fresh
guards complete87 reports/674 cases, all pass without skips, 208.35seconds.
The owner compares all27 complete concrete first assertions and62 literal
skip reasons with the previously qualified baseline:26 literal assertions plus
the full2952-character SSZ first line after only removing its exception prefix
and normalizing the independently verified `RewindObjectStateBlob@hex` to
`@HASH`. No new/worsened/unattributed cases, ROM skips, omissions or timeouts.
This is accepted inherited-failure qualification, not a green whole suite.
See [framework delivery evidence](../plans/2026-10-07-mod-framework-product-readiness.md).

Root independently checks all eight changed paths from `bf7` to published
`d740`: release-tree/policy helpers, the exact authored-fixture allowlist,
Python policy tests and two existing prose files. There is **zero engine Java,
Java-test, resource, POM or category-runner delta**. The successor's owner
qualifies32 shell/PowerShell policy cases and137 focused guards and verifies
the remote develop SHA. Thus this reuse establishes source-equivalent engine
baseline provenance at actual published `d740`; it is not a freshly rerun
whole suite at `d740`. No foreign diagnostics are acknowledged by root.

The unmodified runner plan against actual `d740` selects **all3058 ordinary
classes plus separate fresh guards**. Its launch-environment preflight passes
Java21, Lua5.4 and PowerShell. Shared death/radius behavior and timing transport
require that normal combined run; no proportionate scope exception is taken.
Finish focused/domain fixes and prose before freezing the exact candidate.
Expected ordinary cost is80–110minutes plus about4minutes guards, based on the
completed recent full runs. Use150minutes admission-excluded and the unchanged
ten-minute no-output timeout. A timeout, omitted report, ROM skip or any new,
worsened or unattributed ordinary failure blocks integration. Compare test
identity/type/full first assertion and every literal skip reason, not totals.
Mandatory actual-main qualification, push and owned cleanup remain required.


### Round 3 private composition with integrated Sitar source

Sitar privately qualifies frozen source `984cb8e2e317848344883879088762ebac7cbc88`
and combines the exact published framework policy inputs at `c615362c4552`. Its
ordinary run20261008T123028Z-dbe57c8c completes3058 selected classes/3056
reports/26535 cases,27 inherited failures, zero errors and62 literal inherited
skips in4978.39seconds; separate fresh guards complete87 reports/674 passing
cases, zero skips in222.71seconds. The owner compares26 literal complete first
assertions plus the full SSZ assertion after only the verified blob-hash
normalization, and all62 literal skip identities/reasons. There are no omissions,
timeouts or ROM skips. This is that owner's private candidate evidence, not
actual-main qualification or qualification of the later parity composition.

Sitar integrates into main `develop` at
`eaafa6ee053f5624c00a78652e841d8f78598f5d`, pinned base
`d740b7a0fadd97b2e7c104d56481a0235bdffb4c`. Its already-owned session21395
is admitted at14:11:37Z as run20261008T141137Z-f3777bdd: all3058 ordinary
classes and separate fresh guards under the150-minute admission-excluded cap.
Main source and publication remain frozen until the owner's terminal result
and delivery. Root preserves that run, all foreign work and the original clean
parity candidate `1f3bf97d5c67bdade2f54429e9e00d4740e76cf7`, whose
run20261008T135710Z-ec467d13/session57481 is separately executing against
published `d740`. No duplicate baseline invocation or foreign acknowledgment
is submitted.

Root creates a second isolated worktree,
`.worktrees/ai-parity-swarm-20261008-r3-composition`, at exact integrated main
source and merges the original parity branch conflict-free at
`2683eb992989e33866e91eb5b18b2a4a62465abd`. Both source ancestries and the
independent changes to the existing changelog and implementation-pitfall
catalogue are preserved. Against `eaafa6ee`, all Sitar production/test/API
inputs remain unchanged: the existing `PLAYHEAD` and `cuePart` pin additions
are retained exactly. No stock music-driver or sequencer change is added.

The combined focus uses the prior30 parity/timing/geometry/API selectors plus
Sitar's20 audio/model/protocol/API/packaging/documentation selectors, deduplicating
`TestModApiSignatureSurface`:49 selectors, the three original absolute ROM
paths and one supported queued `--lean -Dmse=off` invocation. Its session61683
checks actual compiled combined consumers in the new worktree's own `target/`.
The unchanged category plan against `eaafa6ee` selects all3060 ordinary classes
and separate fresh guards; actual launch preflight passes Java21/Lua5.4/
PowerShell. This plan and tool check execute no engine tests.

The updated published destination, its terminal baseline evidence and the
combined candidate's normal ordinary/fresh-guard results remain required. Shared death/radius and timing transport still take normal
validation, not the proportionate exception. A terminal result for the original
`d740` candidate alone will not qualify the new combined Sitar source. Main
integration, its mandatory actual-main qualification, push and owned cleanup
are not yet claimed.


#### Read-only next-frontier resumption

Root reuses the same three Sol conversations for a bounded read-only pass while
qualification runs. No worker starts another build/capture, edits source or
creates a commit. The findings refine next work without changing the frozen
parity candidate or either owner's verification.

- **S1 MZ2_3 row101:** old timing input is absent. Held gameplay counter0066
  with advancing VBlank B6AC→B6AD reaches the untimed held-tail branch in
  `PlcFrameLifecycleCoordinator.prepareAfterLoop`, withholding preparation.
  Native `RunPLC` at1379–1415 writes eighteen remaining patterns before
  table construction under `FixBugs=0`; the measured arm precedes the lag
  interrupt. Row shape cannot locate the interrupt before or after that write.
  A future real service/coordinator discriminator should compare identical
  held classification with matching recorded readiness admitted versus absent,
  preserving kind/ordinal/fingerprint/boundary rejection. Existing isolated
  arm tests and generic held-tail tests cover the pieces, not this combination.
  No counter-only production fix or publication of the withheld whole fixture
  is established.
- **S2 ARZ1 row4213:** `Obj0D_Main` clears the HUD timer as Sonic crosses
  signpost X298C at row4212. Native `Sonic_RevertToNormal` at1ABF2 writes
  `prev_anim=Run(1)` (`11 7C 00 01 00 1D`) before same-pass `Sonic_Animate`,
  restarting still-selected Roll(2) at mapping3D. Engine
  `Sonic2SuperStateController.onRevertStarted` restores the set without that
  sentinel; `SpriteManager` performs Super work through `tickStatus` after
  animation/touch rather than native `Sonic_Super` before animation. Mapping
  continues to41. No earlier compared gameplay difference is reported, but
  hidden animation state is not in the recording. The next bounded regression
  must use a real tick: newly paused signpost timer, mid-cycle Super Roll,
  unchanged movement profile for that tick, same-tick3D then41, and a Run(1)
  equality control. Callback-only coverage cannot establish dispatch order.
  This is a source-backed next hypothesis awaiting regression proof, not a
  delivered reversion fix.
- **S3K returned HCZ:** native fan slot10 writes ground velocity1 before
  belt slot91 at row1505. Belt phase0C→12 at1507 chooses mapping0063 while
  Y remains07DF; phase1E→24 at1510 chooses0064 and Y07CB+0B=07D6. Engine
  observed marker0/phase0 selects mapping0095 and Y07CB+14=07DF from the
  same tables. The two first errors correspond to different thresholds of
  that pose phase; the missing-marker cause is still unproven. Object-owned
  mapping publication and controlled movement make primary animation overwrite
  less likely, but actual runtime ownership flags were not sampled. Native
  unconditional samples first observe Main at1247 with timer/toggle0/0; they
  do not capture Init or prove earlier lifetime. Next probe must observe
  unconditional engine fan entry/init/retirement, pre/post marker and belt-entry
  state, aligned to episode1247 and1499–1515. Active-only logging, slot reversal
  alone and the rejected fitted timer increment do not establish causality.
  The delivered fan corrections still show no trace-frontier improvement.


#### Destination test-input update during Sitar qualification

A separate owner merges the shorter solo-Sonic Sandopolis controller route at
`6124a524eef9b42efb800d5bcb95376147507c9e`,14:14:25Z, after Sitar's
14:11:37 admission. The six paths are one existing Java test, its BK2, the
authored controller script and three prose files. Engine, Sitar/API, POM and
hooks remain equivalent to `eaafa6ee`, but `TestSozColdRouteCapture` reads
its BK2 directly from the source tree. Original validation inputs were not
retained, so the owner identifies its exact runner3994735/cwd/argv/stdout and
interrupts only that process. Session21395 exits130; runner and Java3998156
are absent; status is incomplete with no results and no broad pass.

The owner preserves the merged commit and the other owner's queued focused
fixture check, reads its updated-base73 passing cases and coordinates an
extended main tracked-input/publication freeze. It will replace only its
invalid actual-main request once at `6124a524`, retaining original integration
base `d740` and the150-minute normal combined cap. Root's frozen candidate
`1f3`/session57481 remains unchanged. The updated fixture will be privately
reconciled after the already-owned combined focus completes and its owner's
terminal fixture evidence is available. No source-equivalent baseline claim
is made for an input-mutated run.


#### Combined focus terminal and input reconciliation

Queued session61683 completes at `2026-10-08T15:05:37Z`, Maven exit 0 /
BUILD SUCCESS,11:13 execution after1924seconds queue waiting: **50 fresh XML
suites,476 cases, zero failures/errors/skips**. All49 requested selectors
are represented (the loading selector matches two packages); fresh XML mtimes
span14:55:40–15:05:37Z. The API reflection 9, SDK 10, Javadoc 7, release-policy13
and documentation-link case pass alongside all parity/timing regressions,
Sitar's152 cases, native music/cues 18 and the four mandatory S3K startup
selectors. This is focused combined-source qualification at exact `2683eb992`,
not a full ordinary pass.

The exact combined command, with machine-local paths normalized only to the
three original root filenames, is:

```sh
OPENGGF_ROM_ROOT=/absolute/path/to/OpenGGF
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestDeathRadiusTransition,TestHurtAnimationPublication,TestDeathRestartRoutineParity,TestAbstractPlayableSpriteRewindCapture,TestHCZCGZFanObjectInstance,TestS3kHczCgzFanGraphRewind,TestHCZConveyorBeltObjectInstance,TestSonic2SpecialStageSuperResults,TestSonic2SpecialStageResultsTallyCadence,TestSonic2SpecialStageResultsPlcReadiness,TestSonic2SpecialStageResultsWidescreenCommands,TestSplitNameResultsMessages,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestHardwareTimingStreamLoader,TestSonic1PlcArmTiming,TestTraceDataHardwareTiming,TestTraceV5LoadingContract,TestTraceRunManifest,TestTraceFixtureCompressionGuard,TestTraceFixtureMovieAlignmentGuard,TestTraceV5PositiveInputGuard,TestHardwareTimingAuthorityGuard,TestHardwareTimingInterstitialStream,TestTraceRunHardwareTimingCoordinator,TestPlayableGroundTransitions,TestPhysicsProfileEditing,TestModApiSignatureSurface,TestSitarHeroArcade,TestSitarHeroCareer,TestSitarHeroCharts,TestSitarHeroControls,TestSitarHeroFeedback,TestSitarHeroModel,TestSitarHeroOnlineMatch,TestSitarHeroPerformers,TestSitarHeroS1SongCatalogue,TestSitarHeroS2SongCatalogue,TestSitarHeroS3kSongCatalogue,TestSitarHeroStory,TestSitarHeroWorldTour,TestSceneMusicRom,TestScenePartCues,TestModApiSdkPackager,TestModApiJavadocTool,TestModdingDocumentationLinks,TestModApiReleasePolicy' \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

Root privately merges qualified main input update `6124a524` without conflicts,
preserving its exact Java test/BK2/controller-script bytes. Its owner reports
the existing post-merge check terminal at14:54:23Z:73 cases, zero failures/
errors/skips including all nine SOZ routes, exact prior identities/outcomes
and unchanged qualified input bytes. Sitar independently checks source
equivalence of engine/examples/API/POM/hooks to `eaafa6ee` and fixture equality
to qualified `c02`. The replacement actual-main session75501 is submitted once
against6124 with original pre-integration based740, all3058 ordinary classes
and fresh guards under150minutes admission-excluded; it is queued, with no
terminal result claimed. The invalid f3777bdd run is consumed/acknowledged
exit 0 and absent. Main's extended tracked-input/publication freeze is preserved.

The completed normal17-case trace selection at `84f0c20f` remains applicable:
all stock gameplay/physics, walkers, drivers and trace fixture/movie inputs
are unchanged from frozen `1f3` through the Sitar composition. The production
upstream delta is four scene-mod files (one has only Javadoc changes), with no
references to the new scene-music types in the selected stock/trace paths.
Sandopolis changes an ordinary capture test and its separate route input,
not any of these fifteen trace selectors or fixtures. Fresh476-case focus
exercises the changed audio/API consumers. No repeated unchanged trace run is
submitted; the original13:34:11Z result remains17 cases/four qualified
failures/zero errors/skips, with the same explicit inherited/attributed limits.

The held-iteration and generic-coordinator Javadocs are corrected to match the
observed native early arm: a lag counter shape cannot prove `RunPLC` has not
published its count. Removing block comments gives literal before/after
executable-source equality in both files; no classification, admission, test
assertion or dispatch behavior changes. The unsafe cost-based inference is
removed while the existing untimed fallback and recorded authority remain.

Full assertions are compared from `failed_cases[].detail` first lines, not
capped messages. Skip identities and literal first-line reasons are compared
to the retained baseline table; unreported world state and stack-trace suffix
equality are not claimed. The original private full run remains executing
with its own unchanged regular-file Sandopolis input matching frozen1f3.
Updated-base terminal qualification, final combined ordinary/fresh guards,
actual-main integration/qualification, develop push and cleanup remain pending.


At15:06:46Z the sole replacement actual-main request is admitted as
run20261008T150646Z-9377cfcd/session75501 at exact6124, pinned pre-integration
based740:3058/3058 ordinary classes, one worker plus fresh guards,150-minute
admission-excluded cap. The owner verifies plan/actual ROM properties and
fresh hashes of the three original absolute main files. Both Sitar and the
fixture owner hold tracked main inputs/commits/push until terminal delivery.
Root preserves that source/publication freeze.

The final private source composition after the conflict-free6124 merge is
`6bbdb294df792521d5b80a5f06c14932a8680fc9`; only the evidence above and two
comments-only native-timing clarifications follow it. Its unmodified plan
against actual destination6124 selects all3060 ordinary classes plus fresh
guards. Shared death/radius and timing transport require the normal combined
run. Expected cost remains80–110minutes ordinary plus about4minutes guards;
use150minutes excluding admission and the unchanged ten-minute no-output rule.
Freeze the entire exact candidate tree through both lanes. Any timeout, missing
required suite, ROM skip or new/worsened/unattributed assertion blocks
integration. Baseline acceptance waits for the owner's completed updated-main
qualification; no result is inferred from its still-executing request.

#### Round 3 normal private qualification and published-source reconciliation

The original frozen candidate `1f3bf97d5c67bdade2f54429e9e00d4740e76cf7`,
base `d740b7a0`, completes normal run `20261008T135710Z-ec467d13` at
15:29:31Z:3058 selected classes,3056 ordinary reports,26549 cases,27 inherited
failures, zero errors and62 inherited skips; ordinary5306.89seconds, exit1.
Separate fresh guards produce87 reports/674 passing cases, zero skips,
234.28seconds. Every failure identity/type/full first assertion and every skip
identity/first causal reason match the qualified baseline:26 literal assertions
and the complete SSZ2952-character line after only exception-prefix removal
and verified `RewindObjectStateBlob@hex` normalization to2907 characters.
The whole-tree fingerprint stays
`5c455596c4f6f63ad8560cc60b921234cbf17b27b94d93c3a357d7ab0eb97a53`.
The source inventory/report difference is accounted for: one abstract base,
seven helpers and23 explicitly excluded tagged classes, plus29 nested XML
suites, give3058−1−7−23+29=3056; eleven package/path aliases map to present
suites. No unexplained ordinary omission, timeout or ROM skip exists.
Exact consumed diagnostics are acknowledged exit 0 and removed.

The updated main baseline is qualified by its owner using completed ordinary
run `20261008T153213Z-5cd93bac` at `6124a524`/base `d740`:3058 selected,
3056 reports,26535 cases,27 failures/zero errors/62 literal inherited skips,
5115.06seconds, Maven exit1. Earlier incomplete invocations are not evidence.
The research-only `17ae561a` HEAD change preserves all ordinary executable,
test and build bytes and the known dirty-input fingerprint, but correctly
makes the outer runner exit2 before guards. A separate fresh normal guard
profile at17ae completes17:15:24Z, exit 0:87 reports/674 cases, zero failures,
errors or skips. These are qualified separate lanes, not a completed combined
run. The owner publishes evidence-only `378c1d715`, independently confirms
remote develop and completes owned cleanup/releasing the main hold. See the
[Sitar actual-main qualification](../plans/2026-10-08-sitar-hero-fumble-feedback.md).

The final private normal command at exact
`53d63fb5c68cc4ea272964befde84ffc19568613`, base
`6124a524eef9b42efb800d5bcb95376147507c9e`, is:

```sh
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 6124a524eef9b42efb800d5bcb95376147507c9e --max-minutes 150 --run
```

The native request loses its session before admission (exit143, no run or
surviving owned request). Only that absent request is replaced once under a
retained bounded user-service supervisor with explicit exit metadata, Java21,
Lua5.4 and the same normal runner limits. Run `20261008T165747Z-3db0a1c5`
admits16:57:47Z and completes18:28:09Z, outer/Maven exit1: **3060 selected,
3058 ordinary reports,26570 cases,26 inherited failures, zero errors and62
inherited skips**, ordinary5159.09seconds. Fresh guards: **87 reports/674
passing cases, zero failures/errors/skips**,262.94seconds. Whole-tree
fingerprint remains
`564e503d6191ba51e8732c0e2dc6319de6beb977ff27add0464cef06edc3995c`.
The actual command uses the three original absolute main ROM files; fresh
SHA-1/CRC32 identities match before and after execution. Both lanes complete
without timeout, diagnostic omission or ROM skip. Exact diagnostics are
consumed/acknowledged exit 0 and deleted; owning supervisor/runner are absent.

All26 remaining failure identities/types/full first assertions match:
25 literally plus the complete SSZ2951-character line, which becomes the
same2907 characters after only verified blob-hash and exception-prefix
normalization. All62 skip identities/first causal reasons match literally.
The absent failure is
`TestS1GameplayAudioTimelineCli#shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup`;
its complete class passes7 cases in fresh ordinary Maven output. This is
**an attributed launch-environment resolution, not a source fix**. The script
and Java test are identical on published base and candidate (SHA-256
`729d951e6d1342d4351046b107297314449b0ea50b820d46f6a22eadcfe0e540` and
`91ab13c9675a5d4db8932aea4d0c369c416ae2a6ae2a6559d4d8f3dcc882cfc9`).
Its first failed production command, the absolute Bash launcher `--help`, is
checked on both trees with fake PATH tools: each returns4/rejects the native
app's `LD_LIBRARY_PATH`, and each returns0/help with that variable absent;
fake tools never execute. Only that variable's presence changes. This matched
first-assertion branch check explains the baseline's expected0/actual4 without
another full suite or source edit. Zero new, worsened or unattributed cases
remain. This qualification accepts inherited failures; it is not a green
ordinary-suite claim.

After terminal inspection, published `ad3d6a996` and native alias successor
`913c5a351` are merged privately, conflict-free, at `f1d05e7b7` and
`e78b2092f`. Shared changelog and hazard/pitfall prose retain both owners'
changes. The complete imported delta is documentation, standalone native
feasibility/Windows packaging tools and one experimental Windows workflow.
Index comparisons against tested53d prove no engine Java, tests/resources,
examples, POM, `.mvn`, hooks, API-policy or category-runner difference.
The normal change-based plan against actual published913c still selects all
3060 ordinary classes plus fresh guards. Completed ordinary and domain
evidence therefore remains applicable, but the new/changed workflow is a
guard input: run one fresh normal private guard profile before integration.
Actual-main normal combined qualification, push and owned cleanup remain
pending. Native Windows artifacts retain their separate owner's qualification
limits; this stock-parity work makes no Windows delivery claim.

At exact `163b782ee596d999cf15e60e1a2a81124ab16521`/published base913c,
the once-submitted separate normal private command is:

```sh
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py \
  -Dmse=off -Pguards test -B
```

The actual invocation also passes the three original absolute ROM properties;
its retained user service preflights Java21/Lua5.4/PowerShell and bounds total
lifetime without changing the Maven profile. After178seconds queue waiting,
it completes `2026-10-08T19:08:05Z`, Maven exit 0/BUILD SUCCESS,4:00 execution:
**87 fresh XML suites,674 cases, zero failures/errors/skips**. All fresh XML
mtimes are after the owning invocation began. Before/after source fingerprint
is literally identical:
`2c1e7e79ad8b07057e64a24fc90720412e317f195ac93de5f34acb9f8204affb`.
Owned supervisor/runner PIDs are absent after terminal. This closes the
published experimental-workflow guard obligation; unchanged ordinary/domain
results retain their exact earlier attribution. No Windows artifact result
is inferred. Other active gameplay/native owners agree to preserve main
tracked inputs/HEAD/publication for upcoming parity integration. The SOZ
owner had already announced a test-only78-case-qualified follow-up before
this hold, then explicitly confirms that no change/check has reached main:
all private invocations are terminal and integration is deferred until the
parity hold is released. Its private update is preserved without composition.

Published `f5de9524a943d55191dbf798d405ca8e8e20ca9e` follows913c with only
three standalone native-feature/build/prose paths. Private merge `8bdf6d4d0`
is conflict-free. Engine/test/resources/examples/POM/hooks/API/testing and
workflow input comparisons against qualified163b are empty, so both normal
ordinary and fresh guard evidence remain applicable without another unchanged
private invocation. Main local/remote f5de match after fetch/fast-forward pull;
the original three dirty submodules and four untracked user paths remain.
Pin f5de as the actual pre-integration base and run normal combined actual-main
qualification after merging the private parity branch. Hold tracked inputs,
HEAD/commits and publication through terminal, then evidence/push/cleanup.
Actual-main parity integration, qualification, push and cleanup remain pending.

#### Round 3 actual-main qualification

The conflict-free actual-main merge is
`863683b092f71a8be4bbd928ed4afe86c545e069`, first parent and pinned
pre-integration base `f5de9524a943d55191dbf798d405ca8e8e20ca9e`;
its integrated index exactly matches qualified private composition
`903d3975796bcefb563931e674d46af4eea055af`. Main stays on develop,
preserving the three dirty disassemblies and four untracked user paths.
The once-submitted normal command is:

```sh
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base f5de9524a943d55191dbf798d405ca8e8e20ca9e --max-minutes 150 --run
```

A retained bounded user-service supervisor preserves command lifetime and
explicit exit metadata. Submission is 19:16:47Z, normal admission 19:45:59Z
as `20261008T194559Z-9deb33e8`, and terminal 21:09:04Z on
2026-10-08. The unmodified selection is all **3060 ordinary classes**,
one worker and separate fresh guards; the 150-minute execution cap excludes
queue waiting. Java 21/Lua 5.4/PowerShell preflight passes in that launch
environment. Actual ordinary and guard commands use the three original
absolute main ROM filenames; fresh SHA-1/CRC32 identities match the repository
reference table. No alternate selection, heap/fork override or ROM alias is
used in this normal qualification.

| Completed lane | Reports | Cases | Failures | Errors | Skips | Execution seconds | Maven exit |
|---|---:|---:|---:|---:|---:|---:|---:|
| Ordinary | 3058 | 26570 | 26 | 0 | 62 | 4748.93 | 1 |
| Fresh guards | 87 | 674 | 0 | 0 | 0 | 234.79 | 0 |

Both lanes complete without timeout, omitted diagnostics or ROM skips.
The runner's overall result is `failed` / exit1 because ordinary retains
inherited failures; this is **accepted inherited-failure qualification,
not a green ordinary-suite claim**. All 26 failure identities/types/full
first assertions match the qualified 53d private result: 25 literally, plus
the complete SSZ 2952-character first line after only exception-prefix removal
and verified named blob-hash normalization to 2907 characters. All 62 skip
identities and first causal reasons match literally. There are zero new,
worsened or unattributed failures or changed/new skips. The previously absent
CLI failure remains the explicitly attributed launch-environment difference
described above, with no source-fix claim.

Fresh ordinary XML observed during this owning invocation confirms the
death-radius 14, S2 super-results 8, HCZ fan 12 and fan-graph rewind 2 cases,
all 13 Sitar classes/152 cases, host music/cues 18, API reflection 9,
SDK packaging 10, Javadoc 7, CLI shell 7 and strict timing-loader 19 passing
without skips. The four required S3K selectors cover 60 passing cases across
five XML suites (both level-loading packages are included). These are actual
main observations, not copied private reports.

Initial, admission-plan, ordinary-boundary, terminal and independently
reconstructed current source fingerprints are identical:
`8f516b7601c7d9c1cf5e03cb6c282e5b17ce79ce8d80717b76fccf6c6f43bb16`.
All tracked validation inputs and HEAD remain frozen through both lanes.
The owning supervisor/runner are absent after terminal; the exact consumed
run is acknowledged exit 0 and its entire diagnostic directory is absent.
No foreign request, process, source or publication is changed.

The completed separate 17-case canonical trace qualification retains four
qualified red frontiers and 13 passing controls, as recorded above. S1's
native-correct death-radius change propagates the documented additional
nine physics/two animation mismatches after an inherited early death; it
does not close that route. Next work remains the combined S1 PLC-arm/service
boundary, S2 same-tick super-animation publication, and S3K fan-to-conveyor
ownership/lifecycle evidence. Stock parity and later-route completion are
still incomplete.

#### Actual-main full assertion and skip summary

This light comparison summary is the complete negative-case projection for
actual-main 863/run9deb: each fully qualified case identity, failure kind/type
and full first assertion, followed by every skip identity and literal first
causal reason. It comes from `failed_cases[].detail`, never the capped
message. Stack-trace suffixes are not compared or retained. Only the marked
SSZ row removes the exception prefix and replaces verified
`RewindObjectStateBlob@hex` with `RewindObjectStateBlob@HASH`; its full
normalized assertion is 2907 characters. The other 25 assertions and all 62 reasons
are literal first lines. The immutable tested source, rather than a future
documentation successor's HEAD, owns these outcomes.

| Failure case | Kind/type | Complete first assertion |
|---|---|---|
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: late pulley owns Tails ==> expected: <true> but was: <false>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: late pulley owns Sonic ==> expected: <true> but was: <false>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: expected: <96> but was: <0>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: expected: <96> but was: <0>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at 26706 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at 26750 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: expected: <96> but was: <0>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at 53897 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: expected: <96> but was: <0>` |
| `com.openggf.tools.TestLrzActTwoColdRouteCapture#coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at input 36526 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzBossColdRouteCapture#coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at input 36526 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: expected: <1069> but was: <899>` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsClearsActOneAndRestoresTraversalFightAndHandoff` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at input 19460 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsRestoresActTwoTraversalToTheMiddleCorridor` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at input 19460 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesBossActAndReachesPlayableHiddenPalace` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at input 19460 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesActTwoAndRestoresTheBoulderHandoff` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at input 19460 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzWideBossColdRouteCapture#coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: expected: <2796> but was: <524>` |
| `com.openggf.tools.TestMhzPairColdRouteCapture#pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: the route must observe the actual history-reset boundary ==> expected: <true> but was: <false>` |
| `com.openggf.tools.TestMhzWideColdRouteCapture#wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: [wide-route-19500] restore 0 zone-runtime: [zone-runtime.stateBytes[2]: A=46 B=26, zone-runtime.stateBytes[3]: A=-104 B=64, zone-runtime.stateBytes[6]: A=38 B=21, zone-runtime.stateBytes[7]: A=-44 B=-32, zone-runtime.stateBytes[10]: A=31 B=17, zone-runtime.stateBytes[11]: A=16 B=-128] ==> expected: <true> but was: <false>` |
| `com.openggf.tools.TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at input 7311 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at input 7311 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at 7671 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: death at 7671 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: expected: <48> but was: <0>` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]` | `failure/org.opentest4j.AssertionFailedError` | `org.opentest4j.AssertionFailedError: expected: <48> but was: <0>` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]` | `failure/org.opentest4j.AssertionFailedError` | `replay at 4018 object-manager: [object-manager.usedSlotsBits differs, object-manager.usedSlotsBits.onlyA: 24, 29, object-manager.dynamic[6][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=125, kind=DYNAMIC]] missing in B (A=DynamicObjectEntry[className=com.openggf.game.sonic3k.objects.badniks.EggRoboJetFlameChildInstance, spawn=ObjectSpawn[x=1291, y=2332, objectId=0, subtype=0, renderFlags=1, respawnTracked=false, rawYWord=0, layoutIndex=-1, ownerModId=null, objectKey=null], slotIndex=6, state=PerObjectRewindSnapshot[destroyed=false, destroyedRespawnable=false, hasDynamicSpawn=true, dynamicSpawnX=1291, dynamicSpawnY=2332, preUpdateX=1291, preUpdateY=2332, preUpdateValid=true, preUpdateCollisionFlags=-1, skipTouchThisFrame=false, solidContactFirstFrame=false, slotIndex=6, respawnStateIndex=-1, badnikExtra=null, badnikSubclassExtra=null, objectSubclassExtra=RewindExtra[parentId=null, x=1291, y=2332, mappingFrame=5, hFlip=true], playerExtra=null, genericState=null, compactGenericState=com.openggf.game.rewind.schema.RewindObjectStateBlob@HASH], playerOwner=null, objectId=ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=125, kind=DYNAMIC], ownerModId=null, rewindableAuxiliary=false]), object-manager.dynamic[28][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=126, kind=DYNAMIC]] missing in B (A=DynamicObjectEntry[className=com.openggf.game.sonic3k.objects.badniks.EggRoboGunArmChildInstance, spawn=ObjectSpawn[x=1307, y=2300, objectId=0, subtype=0, renderFlags=1, respawnTracked=false, rawYWord=0, layoutIndex=-1, ownerModId=null, objectKey=null], slotIndex=28, state=PerObjectRewindSnapshot[destroyed=false, destroyedRespawnable=false, hasDynamicSpawn=true, dynamicSpawnX=1307, dynamicSpawnY=2300, preUpdateX=1307, preUpdateY=2300, preUpdateValid=true, preUpdateCollisionFlags=-1, skipTouchThisFrame=false, solidContactFirstFrame=false, slotIndex=28, respawnStateIndex=-1, badnikExtra=null, badnikSubclassExtra=null, objectSubclassExtra=RewindExtra[parentId=null, x=1307, y=2300, cooldown=-1, hFlip=true], playerExtra=null, genericState=null, compactGenericState=com.openggf.game.rewind.schema.RewindObjectStateBlob@HASH], playerOwner=null, objectId=ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=126, kind=DYNAMIC], ownerModId=null, rewindableAuxiliary=false]), object-manager.dynamic[27][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=128, kind=DYNAMIC]].slotIndex: A=27 B=6, object-manager.dynamic[27][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=128, kind=DYNAMIC]].state.slotIndex: A=27 B=6, object-manager.dynamic[33][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=129, kind=DYNAMIC]].slotIndex: A=33 B=27, object-manager.dynamic[33][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=129, kind=DYNAMIC]].state.slotIndex: A=33 B=27] ==> expected: <true> but was: <false>` (SSZ normalization only) |

| Skipped case | Literal first causal reason |
|---|---|
| `com.openggf.audio.TestSmpsRepeatedPlaybackBenchmark#repeatedPublicMusicAndSfxPlaybackEmitsStableRawSamples` | `System property [openggf.audio.repeatedPlaybackBenchmark] does not exist` |
| `com.openggf.game.rewind.TestLiveRewindCheckpointCost#compareCheckpointCadencesOnTheSameRecordedRoute` | `System property [openggf.checkpoint.measure] does not exist` |
| `com.openggf.game.rewind.TestRewindTorture#tortureProgressiveLongRewinds` | `org.opentest4j.TestAbortedException: Assumption failed: Long-running soak profile; excluded from normal runs — run manually with -Drewind.soak=true` |
| `com.openggf.game.rewind.TestS3kRewindAllocationProbe#measure` | `System property [openggf.rewind.alloc.measure] does not exist` |
| `com.openggf.game.sonic3k.objects.TestS3kAiz1CompatibilityRoutes#axisRouteCompletes(int, String)` | `System property [openggf.aiz1.routes] does not exist` |
| `com.openggf.game.sonic3k.objects.TestS3kAiz1EntryMatrix#introReleasesInputAndEntryReplaysTwice(int, String)` | `System property [openggf.aiz1.entry] does not exist` |
| `com.openggf.game.sonic3k.objects.TestS3kAiz1SpringRecovery#liveSpringJumpCrossesAndReplaysWhileWalkingIsRejected(int, String)` | `System property [openggf.aiz1.recovery] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozAct1VictoryCapture#captureVictoryAndHandoff` | `System property [soz.act1.victory.capture] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozColdAct1Capture#fixedControllerRouteReachesVisiblePlayableAct2FromColdAct1` | `System property [soz.cold.act1.capture] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozColdAct2Capture#fixedControllerRouteReachesVisibleLavaReefFromColdAct2` | `System property [soz.cold.act2.capture] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozEndBossVictoryCapture#captureBattleAndLrz` | `System property [soz.endboss.victory.capture] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozMinibossCapture#positionedAwakening` | `System property [soz.miniboss.capture] does not exist` |
| `com.openggf.graphics.TestArenaMaskRenderer#nativeWidthCentrePixelsFrameCadenceAndGlStateSurviveCaptureFbo` | `org.opentest4j.TestAbortedException: Assumption failed: assumption is not true` |
| `com.openggf.graphics.TestBackgroundScrollWrapPixels#integerScrollNeverSamplesOutsideTheRenderedPeriod` | `org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable` |
| `com.openggf.graphics.TestForegroundWindowRendering#windowReplacesScrolledForegroundAndItsPriorityMask` | `org.opentest4j.TestAbortedException: Assumption failed: Surfaceless EGL unavailable (try EGL_PLATFORM=surfaceless)` |
| `com.openggf.graphics.TestScrollBufferUploadNative#arraysAndViewsUploadExactValuesAcrossResourceAndContextRecreation` | `System property [openggf.scrollNative] does not exist` |
| `com.openggf.graphics.TestShaderPixelCentreSampling#pixelCentresSurviveNativeIntegerAndFractionalScalingWithViewportOffsets` | `org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable` |
| `com.openggf.graphics.TestSlotWindowGpuPassNative#pixelsAndDrawStateSurviveResizeCleanupAndContextRecreation(boolean)` | `System property [openggf.slotNative] does not exist` |
| `com.openggf.graphics.shaderlib.TestDisplayShaderPackDiagnostics#writeCompatibilityReportForLocalShaderPack` | `org.opentest4j.TestAbortedException: Assumption failed: Set -Dshaderlib.diagnostic.enabled=true to scan a local shader pack` |
| `com.openggf.level.TestLevelRendererBackgroundSamplingPerformance#captureLiveBackgroundSamplingScenes` | `org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.capture.backgroundSampling=true` |
| `com.openggf.level.TestLevelRendererBackgroundSamplingPerformance#postWarmupRenderSamplingAllocationProbe` | `org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.measure.backgroundSampling=true` |
| `com.openggf.level.objects.TestObjectRewindTypeSafetyDispatchPerformance#measureMixedRouteDispatchAllocationAndTime` | `System property [openggf.performance.rewindDispatch.measure] does not exist` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[11]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[12]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[19]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[20]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[21]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[22]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[23]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[24]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[25]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[26]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[27]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[28]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[29]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[30]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[31]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[32]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[33]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[34]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[35]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[36]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[11]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[12]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[19]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[20]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[21]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[22]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[23]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[24]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[33]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[34]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[35]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[36]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestSonicSurvivors#balanceProbe` | `org.opentest4j.TestAbortedException: Assumption failed: opt-in diagnostic` |
| `com.openggf.tests.TestCPZObjectBugs#testSpinTubeForcesRolling` | `org.opentest4j.TestAbortedException: Assumption failed: Spin tube at (1920,896) did not capture/release Sonic` |
| `com.openggf.tests.TestSozAct1ArenaCapture#naturalArenaAdmission` | `System property [soz.act1.capture] does not exist` |
| `com.openggf.tests.TestSozConnectedMechanismCapture#capture(Scene)` | `System property [soz.connected.capture] does not exist` |
| `com.openggf.tests.TestSozPostBossRedrawCapture#capture(int)` | `System property [soz.postboss.redraw.capture] does not exist` |
| `com.openggf.tools.TestS3kSlotsGlassNative#glassOccludesPlayerAfterRealBonusFrame(String, int)` | `System property [openggf.test.gl.native] does not exist` |
| `com.openggf.tools.audio.parity.TestS1OpenGgfAudioCapture#capturesTheCompleteReferenceControlledInterval` | `org.opentest4j.TestAbortedException: Assumption failed: local deterministic BizHawk reference required` |
| `com.openggf.tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineCapture#captureRequestedOutput` | `org.opentest4j.TestAbortedException: Assumption failed: no local OpenGGF timeline capture was requested` |

Publication and accounted-for owned worktree/agent cleanup follow this
qualification; their final observed outcome is recorded below.

#### Independent remote publication after the original main qualification

The completed actual-main qualification above remains attributed to863/run9deb.
Publication of its evidence-only successor `5662ad2c2` is blocked before any
push: remote develop independently advances to
`d4993a7307241bf90f004e0d7cf90936f075cf46` through Starfall Frontier and
Eggman's Sky merges while local main remains frozen. Fetch preserves all
local source and known dirt. The complete incoming delta is 123 paths,
including 13 engine Java files, new/changed creator-scene tests, two example
mods and 22 API signature-pin lines, alongside existing engineering/prose.
There is no POM, hooks, selection-policy or testing-runner change. This is
not source-equivalent to the completed 863 validation.

Private reconciliation retains both source histories. All parity production
and Java-test paths are disjoint from the incoming changes; shared changelog,
measurement hazards and implementation pitfalls merge by retaining both
owners' entries. A separate clean updated-remote worktree at exact d499 uses
its canonical original absolute main ROM files and normal full ordinary plus
fresh guards, selected against pre-import f5de. The combined parity candidate
uses the unmodified normal plan against actual destination d499. Fresh focus
covers incoming creator scenes, detached ROM art/rendering, startup audio,
API/SDK/Javadoc consumers and S3K startup before combined qualification.
The published remote's earlier noncanonical-ROM, incomplete broad result
is not reused as a canonical full baseline. No new/worsened/unattributed
failure or unexplained skip can be accepted by totals alone.

The earlier complete negative-case table is retained as an immutable source
comparison. Updated-base, combined private and actual-main results will be
recorded with their own exact source and command before final publication.
Foreign private requests and native captures remain untouched; local main
and publication hold continue while this source-changing reconciliation is
qualified.

Private composition `d8f7ab7205935a608948ea6813b38e281df0cce8` passes
its once-submitted queued focus at 2026-10-08T21:48:57Z, Maven exit 0 /
BUILD SUCCESS, 2:02 execution after normal queue waiting. All 20 selectors
are represented in 21 fresh XML suites (level loading matches two packages):
146 cases, 145 passed, zero failures/errors and one explicit GPU skip.
The skipped identity is
`com.openggf.mods.scene.host.TestSceneRenderer#changingBatchSizesPreservesEveryPixelAndStreamingUpdates`;
its literal first causal reason is
`org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable`.
No ROM case skips. This does not certify the skipped GPU pixel behavior.
API reflection 9, SDK 10, Javadoc 7, both creator mods 22, startup audio 2,
detached pictures 2, S3K backdrops 10, texture cache 5, host music/cues 18
and required S3K startup 60 all pass. Fresh reports are selected by mtimes
after the owning invocation, not earlier target output. Before/after source
fingerprint is identical:
`2d7b785be8c2eb4dea26ef7564ab07136fa735f7116bc8cfc592fd7ed874f78d`.
Owning focus supervisor/runner are absent after terminal.

The exact focused command, with only machine-local path spelling normalized,
is:

```sh
OPENGGF_ROM_ROOT=/absolute/path/to/OpenGGF
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestEggmansSkyStartupAudio,TestEggmansSkyFauna,TestEggmansSkyQualityOfLife,TestEggmansSkyScene,TestStarfallFrontierExample,TestStarfallFrontierScene,TestModSceneLauncherAudio,TestSceneBackdropS3k,TestDetachedStockScenePictures,TestSceneRenderer,TestSceneTextureCache,TestModApiSignatureSurface,TestModApiSdkPackager,TestModApiJavadocTool,TestSceneMusicRom,TestScenePartCues,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The canonical updated-base normal run admits at 21:47:48Z as
`20261008T214748Z-737dcb35`, exact d499/base f5de, all 3066 ordinary
classes plus fresh guards, one worker and 150 minutes excluding admission.
The combined candidate's unmodified plan against actual destination d499
selects all 3068 ordinary classes plus fresh guards. Expected execution cost
is 85–110 minutes ordinary plus 4–5 minutes guards for each invocation; queue
waiting is additional. A timeout, unexplained omission, ROM skip or new/
worsened/unattributed failure blocks integration. The incoming GPU skip must
match the updated canonical baseline by identity and literal reason. Final
combined private and actual-main qualification, publication and cleanup
remain pending.

## Updated remote baseline and combined candidate terminal qualification

The canonical updated baseline is tested at
`d4993a7307241bf90f004e0d7cf90936f075cf46`, selected against
`f5de9524a943d55191dbf798d405ca8e8e20ca9e`. Run
`20261008T214748Z-737dcb35` admitted at 2026-10-08T21:47:48Z and
finished at 2026-10-08T23:19:18Z. The combined candidate is tested at
`94bbd3bda56c3dcc81cfcc89a750b8955cb14dc6` against the actual
published destination `d4993a7307241bf90f004e0d7cf90936f075cf46`.
Run `20261008T232550Z-db7081e2` admitted at
2026-10-08T23:25:50Z and finished at 2026-10-09T00:51:22Z.

| Exact source/lane | Selected classes | Fresh XML reports | Cases | Failures | Errors | Skips | Execution seconds | Maven exit |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| d499 ordinary | 3,066 | 3,064 | 26,563 | 26 | 0 | 63 | 5,238.46 | 1 |
| d499 fresh guards | — | 87 | 674 | 0 | 0 | 0 | 251.60 | 0 |
| 94bb ordinary | 3,068 | 3,066 | 26,598 | 26 | 0 | 63 | 4,870.86 | 1 |
| 94bb fresh guards | — | 87 | 674 | 0 | 0 | 0 | 260.05 | 0 |

Both invocations use the normal, unmodified ordinary command and a separate
fresh guard JVM, one worker, canonical original absolute main ROM filenames,
and the 150-minute admission-excluded cap. Java 21, Lua 5.4 and PowerShell
preflight pass. All three ROM SHA-1/CRC identities match. No lean, heap or
fork overrides are added to either broad invocation.

The commands, from the separate updated-base and composition worktrees, are:

```sh
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base f5de9524a943d55191dbf798d405ca8e8e20ca9e --max-minutes 150 --run
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base d4993a7307241bf90f004e0d7cf90936f075cf46 --max-minutes 150 --run
```

All 26 failure identities, kinds and types match the qualified 863 reference
on the updated baseline and the candidate. Twenty-five full first assertion
lines match literally. The complete SSZ first line is 2,952 characters on
both new runs; removing only the exception prefix and replacing the two
verified `RewindObjectStateBlob@hex` identities with `@HASH` yields the
same 2,907-character assertion. No other value or text is normalized.
The 62 prior skip identities and literal first reasons are preserved, with
exactly one incoming renderer skip: the identity and OpenGL 4.1 reason
recorded above. All 63 skip identities/reasons match literally between d499
and 94bb. There are no new, worsened or unattributed ordinary negative
cases, duplicate negative identities, omitted diagnostics, timeouts or ROM
skips. This is inherited-failure qualification, not a green ordinary suite.

The candidate adds exactly 35 passing cases over the updated baseline:
death-radius 14, S2 super-results 8, seven additional fan cases and six
additional strict timing-loader cases. Fresh suites confirm fan 12,
fan-graph rewind 2, timing loader 19, API reflection 9, SDK 10 and Javadoc 7
pass without skips. All 13 Sitar suites/152 cases and host music/cues 18
pass without skips. Required S3K startup 60, incoming creator scenes,
detached stock art and startup-audio checks pass; the explicit renderer GPU
skip remains a coverage limit. SDK/Javadoc counts are also confirmed by the
fresh owning ordinary Maven completion lines.

Before/after fingerprints remain identical within each frozen invocation:

- d499: `6de3884fad261de2a57bcd6e0209cd146b4106a8ec09dd3e2cfbb07941ae8833`.
- 94bb: `c2938174a1f4ad637345cd87ed4fae8644c38cd7be1edc608dae198db1f5324a`.

Both owning supervisor/runner pairs are absent, and their service control
groups are collected. Complete results were consumed before acknowledging
each exact run with exit 0; both run directories are absent. No foreign
request, process or diagnostic was acknowledged or cancelled.

The combined private source is qualified. Its evidence-only successor may
be integrated into the held main develop branch with d499 as the pinned
validation base. Updated actual-main normal ordinary plus fresh guards,
publication and owned cleanup remain required; the earlier 863 main result
does not certify the incoming engine/API composition.

## Updated actual-main qualification

The held develop branch integrates the qualified evidence successor
`0135fac91da0f85f4ac4d91c641f41acd88a7d18` conflict-free at
`b317e94ebdce60c6f81553113543295c75b1d826`, with first parent
`5662ad2c2291bc9c5578094b5ad37a7ee31e55e2`. Its index and committed
tree match the qualified private composition exactly. The actual pinned
published validation base is
`d4993a7307241bf90f004e0d7cf90936f075cf46`; the seven known unrelated
dirty/untracked main paths and their bytes remain preserved.

The one normal actual-main command was submitted at 2026-10-09T00:56:57Z,
admitted at 01:56:57Z as `20261009T015657Z-e417e53f`, and finished at
03:23:31Z. Queue waiting is excluded from the 150-minute execution cap.
Java 21, Lua 5.4 and PowerShell preflight pass. The unmodified plan selects
all 3,068 ordinary classes, one worker and separate fresh guards. Actual
ordinary and guard commands use the three original absolute main ROM
filenames, independently rehashed at submission and admission to the
canonical SHA-1/CRC identities. No lean, heap or fork overrides are added.

```sh
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base d4993a7307241bf90f004e0d7cf90936f075cf46 --max-minutes 150 --run
```

| Exact b317 lane | Selected classes | Fresh reports | Cases | Failures | Errors | Skips | Execution seconds | Maven exit |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Ordinary | 3,068 | 3,066 | 26,598 | 26 | 0 | 63 | 4,963.90 | 1 |
| Separate fresh guards | — | 87 | 674 | 0 | 0 | 0 | 230.26 | 0 |

The normal runner and retained supervisor finish with exit 1/status
`failed` because the inherited ordinary failures remain. Both lanes are
complete. Against the canonical d499 result, all 26 failure identities,
kinds and types match; 25 complete first assertions match literally.
The SSZ first line is 2,952 characters. Removing only the exception prefix
and replacing the two verified `RewindObjectStateBlob@11f491d5` and
`RewindObjectStateBlob@a1a53d79` hashes with `@HASH` yields the same
complete 2,907-character assertion. Every concrete assertion field is
retained. All 63 skip identities and first causal reasons match literally,
including the explicitly unavailable OpenGL 4.1 renderer case. There are
zero new, worsened or unattributed failures, duplicate negative identities,
omitted diagnostics, timeouts or ROM skips. This is inherited-failure
qualification, not a green whole-suite or completed stock-ROM parity claim.

Fresh actual-main suites confirm death-radius 14, S2 super-results 8, fan
12, fan-graph rewind 2, timing loader 19, API reflection 9, SDK 10 and
Javadoc 7 pass without skips. All 13 Sitar suites/152 cases, host music/cues
18 and the five required S3K suites/60 cases pass without skips. Incoming
creator scenes, detached stock art, startup-audio and texture-cache checks
pass. SDK/Javadoc are also confirmed by fresh owning ordinary Maven
completion lines. The renderer's explicit GPU skip remains a coverage limit;
separate trace/native frontier results above remain red or incomplete as
recorded and are not certified by ordinary counts.

Initial, admitted-plan, final and independently current main fingerprints
are identical:
`e9d660f01a5b0b602789bfc3a56c84e54d74320625d0d6846dbf2bc3017a850a`.
The exact supervisor 1012438, runner 1012652 and owning Maven 1070806 are
absent; the service is collected with MainPID 0 and no control group.
Complete results were consumed before acknowledging only
`20261009T015657Z-e417e53f` with exit 0; its run directory is absent.
No foreign job, request or diagnostic was changed, acknowledged or cancelled.
Publication and owned worktree/generated-artifact cleanup follow this
terminal qualification; their observed outcome is recorded below.

## Updated actual-main full assertion and skip summary

This light complete negative-case table belongs to tested actual-main
`b317e94ebdce60c6f81553113543295c75b1d826`, run
`20261009T015657Z-e417e53f`, against canonical published
`d4993a7307241bf90f004e0d7cf90936f075cf46`. A later prose-only delivery
successor may reuse it only after verifying zero executable/test/build/API
delta. The earlier 863 table remains immutable and applies only to its own
tested source. Assertions come from the complete first line of
`failed_cases[].detail`, not the capped `message`; raw stack traces and
logs are not archived. Only the explicitly marked SSZ row uses the verified
exception-prefix/blob-hash normalization described above.

### All 26 complete inherited assertions at updated actual-main

| Test identity | Kind / type | Complete first assertion |
|---|---|---|
| <code>com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)&#91;2&#93;</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: late pulley owns Tails ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;</code> |
| <code>com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)&#91;3&#93;</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: late pulley owns Sonic ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;</code> |
| <code>com.openggf.tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at 53897 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: expected: &lt;96&gt; but was: &lt;0&gt;</code> |
| <code>com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: expected: &lt;96&gt; but was: &lt;0&gt;</code> |
| <code>com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: expected: &lt;96&gt; but was: &lt;0&gt;</code> |
| <code>com.openggf.tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: expected: &lt;96&gt; but was: &lt;0&gt;</code> |
| <code>com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)&#91;1&#93;</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at 26706 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)&#91;2&#93;</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at 26750 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestLrzActTwoColdRouteCapture#coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at input 36526 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestLrzBossColdRouteCapture#coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at input 36526 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: expected: &lt;1069&gt; but was: &lt;899&gt;</code> |
| <code>com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsClearsActOneAndRestoresTraversalFightAndHandoff</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at input 19460 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesActTwoAndRestoresTheBoulderHandoff</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at input 19460 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesBossActAndReachesPlayableHiddenPalace</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at input 19460 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsRestoresActTwoTraversalToTheMiddleCorridor</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at input 19460 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestLrzWideBossColdRouteCapture#coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: expected: &lt;2796&gt; but was: &lt;524&gt;</code> |
| <code>com.openggf.tools.TestMhzPairColdRouteCapture#pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: the route must observe the actual history-reset boundary ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;</code> |
| <code>com.openggf.tools.TestMhzWideColdRouteCapture#wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: &#91;wide-route-19500&#93; restore 0 zone-runtime: &#91;zone-runtime.stateBytes&#91;2&#93;: A=46 B=26, zone-runtime.stateBytes&#91;3&#93;: A=-104 B=64, zone-runtime.stateBytes&#91;6&#93;: A=38 B=21, zone-runtime.stateBytes&#91;7&#93;: A=-44 B=-32, zone-runtime.stateBytes&#91;10&#93;: A=31 B=17, zone-runtime.stateBytes&#91;11&#93;: A=16 B=-128&#93; ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;</code> |
| <code>com.openggf.tools.TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at input 7311 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at input 7311 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at 7671 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: death at 7671 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt;</code> |
| <code>com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)&#91;2&#93;</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | SSZ normalization only: <code>replay at 4018 object-manager: &#91;object-manager.usedSlotsBits differs, object-manager.usedSlotsBits.onlyA: 24, 29, object-manager.dynamic&#91;6&#93;&#91;ObjectRefId&#91;slotIndex=-1, generation=0, spawnId=-1, dynamicId=125, kind=DYNAMIC&#93;&#93; missing in B (A=DynamicObjectEntry&#91;className=com.openggf.game.sonic3k.objects.badniks.EggRoboJetFlameChildInstance, spawn=ObjectSpawn&#91;x=1291, y=2332, objectId=0, subtype=0, renderFlags=1, respawnTracked=false, rawYWord=0, layoutIndex=-1, ownerModId=null, objectKey=null&#93;, slotIndex=6, state=PerObjectRewindSnapshot&#91;destroyed=false, destroyedRespawnable=false, hasDynamicSpawn=true, dynamicSpawnX=1291, dynamicSpawnY=2332, preUpdateX=1291, preUpdateY=2332, preUpdateValid=true, preUpdateCollisionFlags=-1, skipTouchThisFrame=false, solidContactFirstFrame=false, slotIndex=6, respawnStateIndex=-1, badnikExtra=null, badnikSubclassExtra=null, objectSubclassExtra=RewindExtra&#91;parentId=null, x=1291, y=2332, mappingFrame=5, hFlip=true&#93;, playerExtra=null, genericState=null, compactGenericState=com.openggf.game.rewind.schema.RewindObjectStateBlob@HASH&#93;, playerOwner=null, objectId=ObjectRefId&#91;slotIndex=-1, generation=0, spawnId=-1, dynamicId=125, kind=DYNAMIC&#93;, ownerModId=null, rewindableAuxiliary=false&#93;), object-manager.dynamic&#91;28&#93;&#91;ObjectRefId&#91;slotIndex=-1, generation=0, spawnId=-1, dynamicId=126, kind=DYNAMIC&#93;&#93; missing in B (A=DynamicObjectEntry&#91;className=com.openggf.game.sonic3k.objects.badniks.EggRoboGunArmChildInstance, spawn=ObjectSpawn&#91;x=1307, y=2300, objectId=0, subtype=0, renderFlags=1, respawnTracked=false, rawYWord=0, layoutIndex=-1, ownerModId=null, objectKey=null&#93;, slotIndex=28, state=PerObjectRewindSnapshot&#91;destroyed=false, destroyedRespawnable=false, hasDynamicSpawn=true, dynamicSpawnX=1307, dynamicSpawnY=2300, preUpdateX=1307, preUpdateY=2300, preUpdateValid=true, preUpdateCollisionFlags=-1, skipTouchThisFrame=false, solidContactFirstFrame=false, slotIndex=28, respawnStateIndex=-1, badnikExtra=null, badnikSubclassExtra=null, objectSubclassExtra=RewindExtra&#91;parentId=null, x=1307, y=2300, cooldown=-1, hFlip=true&#93;, playerExtra=null, genericState=null, compactGenericState=com.openggf.game.rewind.schema.RewindObjectStateBlob@HASH&#93;, playerOwner=null, objectId=ObjectRefId&#91;slotIndex=-1, generation=0, spawnId=-1, dynamicId=126, kind=DYNAMIC&#93;, ownerModId=null, rewindableAuxiliary=false&#93;), object-manager.dynamic&#91;27&#93;&#91;ObjectRefId&#91;slotIndex=-1, generation=0, spawnId=-1, dynamicId=128, kind=DYNAMIC&#93;&#93;.slotIndex: A=27 B=6, object-manager.dynamic&#91;27&#93;&#91;ObjectRefId&#91;slotIndex=-1, generation=0, spawnId=-1, dynamicId=128, kind=DYNAMIC&#93;&#93;.state.slotIndex: A=27 B=6, object-manager.dynamic&#91;33&#93;&#91;ObjectRefId&#91;slotIndex=-1, generation=0, spawnId=-1, dynamicId=129, kind=DYNAMIC&#93;&#93;.slotIndex: A=33 B=27, object-manager.dynamic&#91;33&#93;&#91;ObjectRefId&#91;slotIndex=-1, generation=0, spawnId=-1, dynamicId=129, kind=DYNAMIC&#93;&#93;.state.slotIndex: A=33 B=27&#93; ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;</code> |
| <code>com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)&#91;1&#93;</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: expected: &lt;48&gt; but was: &lt;0&gt;</code> |
| <code>com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)&#91;2&#93;</code> | <code>failure</code> / <code>org.opentest4j.AssertionFailedError</code> | <code>org.opentest4j.AssertionFailedError: expected: &lt;48&gt; but was: &lt;0&gt;</code> |

### All 63 literal inherited causal skip reasons at updated actual-main

| Test identity | Literal first causal reason |
|---|---|
| <code>com.openggf.audio.TestSmpsRepeatedPlaybackBenchmark#repeatedPublicMusicAndSfxPlaybackEmitsStableRawSamples</code> | <code>System property &#91;openggf.audio.repeatedPlaybackBenchmark&#93; does not exist</code> |
| <code>com.openggf.game.rewind.TestLiveRewindCheckpointCost#compareCheckpointCadencesOnTheSameRecordedRoute</code> | <code>System property &#91;openggf.checkpoint.measure&#93; does not exist</code> |
| <code>com.openggf.game.rewind.TestRewindTorture#tortureProgressiveLongRewinds</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: Long-running soak profile; excluded from normal runs — run manually with -Drewind.soak=true</code> |
| <code>com.openggf.game.rewind.TestS3kRewindAllocationProbe#measure</code> | <code>System property &#91;openggf.rewind.alloc.measure&#93; does not exist</code> |
| <code>com.openggf.game.sonic3k.objects.TestS3kAiz1CompatibilityRoutes#axisRouteCompletes(int, String)</code> | <code>System property &#91;openggf.aiz1.routes&#93; does not exist</code> |
| <code>com.openggf.game.sonic3k.objects.TestS3kAiz1EntryMatrix#introReleasesInputAndEntryReplaysTwice(int, String)</code> | <code>System property &#91;openggf.aiz1.entry&#93; does not exist</code> |
| <code>com.openggf.game.sonic3k.objects.TestS3kAiz1SpringRecovery#liveSpringJumpCrossesAndReplaysWhileWalkingIsRejected(int, String)</code> | <code>System property &#91;openggf.aiz1.recovery&#93; does not exist</code> |
| <code>com.openggf.game.sonic3k.objects.TestSozAct1VictoryCapture#captureVictoryAndHandoff</code> | <code>System property &#91;soz.act1.victory.capture&#93; does not exist</code> |
| <code>com.openggf.game.sonic3k.objects.TestSozColdAct1Capture#fixedControllerRouteReachesVisiblePlayableAct2FromColdAct1</code> | <code>System property &#91;soz.cold.act1.capture&#93; does not exist</code> |
| <code>com.openggf.game.sonic3k.objects.TestSozColdAct2Capture#fixedControllerRouteReachesVisibleLavaReefFromColdAct2</code> | <code>System property &#91;soz.cold.act2.capture&#93; does not exist</code> |
| <code>com.openggf.game.sonic3k.objects.TestSozEndBossVictoryCapture#captureBattleAndLrz</code> | <code>System property &#91;soz.endboss.victory.capture&#93; does not exist</code> |
| <code>com.openggf.game.sonic3k.objects.TestSozMinibossCapture#positionedAwakening</code> | <code>System property &#91;soz.miniboss.capture&#93; does not exist</code> |
| <code>com.openggf.graphics.shaderlib.TestDisplayShaderPackDiagnostics#writeCompatibilityReportForLocalShaderPack</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: Set -Dshaderlib.diagnostic.enabled=true to scan a local shader pack</code> |
| <code>com.openggf.graphics.TestArenaMaskRenderer#nativeWidthCentrePixelsFrameCadenceAndGlStateSurviveCaptureFbo</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: assumption is not true</code> |
| <code>com.openggf.graphics.TestBackgroundScrollWrapPixels#integerScrollNeverSamplesOutsideTheRenderedPeriod</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable</code> |
| <code>com.openggf.graphics.TestForegroundWindowRendering#windowReplacesScrolledForegroundAndItsPriorityMask</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: Surfaceless EGL unavailable (try EGL&#95;PLATFORM=surfaceless)</code> |
| <code>com.openggf.graphics.TestScrollBufferUploadNative#arraysAndViewsUploadExactValuesAcrossResourceAndContextRecreation</code> | <code>System property &#91;openggf.scrollNative&#93; does not exist</code> |
| <code>com.openggf.graphics.TestShaderPixelCentreSampling#pixelCentresSurviveNativeIntegerAndFractionalScalingWithViewportOffsets</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable</code> |
| <code>com.openggf.graphics.TestSlotWindowGpuPassNative#pixelsAndDrawStateSurviveResizeCleanupAndContextRecreation(boolean)</code> | <code>System property &#91;openggf.slotNative&#93; does not exist</code> |
| <code>com.openggf.level.objects.TestObjectRewindTypeSafetyDispatchPerformance#measureMixedRouteDispatchAllocationAndTime</code> | <code>System property &#91;openggf.performance.rewindDispatch.measure&#93; does not exist</code> |
| <code>com.openggf.level.TestLevelRendererBackgroundSamplingPerformance#captureLiveBackgroundSamplingScenes</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.capture.backgroundSampling=true</code> |
| <code>com.openggf.level.TestLevelRendererBackgroundSamplingPerformance#postWarmupRenderSamplingAllocationProbe</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.measure.backgroundSampling=true</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;11&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;12&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;19&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;20&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;21&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;22&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;23&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;24&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;25&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;26&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;27&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;28&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;29&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;30&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;31&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;32&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;33&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;34&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;35&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)&#91;36&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;11&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;12&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;19&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;20&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;21&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;22&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;23&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;24&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;33&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;34&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;35&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)&#91;36&#93;</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: act places no platforms</code> |
| <code>com.openggf.mods.code.TestSonicSurvivors#balanceProbe</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: opt-in diagnostic</code> |
| <code>com.openggf.mods.scene.host.TestSceneRenderer#changingBatchSizesPreservesEveryPixelAndStreamingUpdates</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable</code> |
| <code>com.openggf.tests.TestCPZObjectBugs#testSpinTubeForcesRolling</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: Spin tube at (1920,896) did not capture/release Sonic</code> |
| <code>com.openggf.tests.TestSozAct1ArenaCapture#naturalArenaAdmission</code> | <code>System property &#91;soz.act1.capture&#93; does not exist</code> |
| <code>com.openggf.tests.TestSozConnectedMechanismCapture#capture(Scene)</code> | <code>System property &#91;soz.connected.capture&#93; does not exist</code> |
| <code>com.openggf.tests.TestSozPostBossRedrawCapture#capture(int)</code> | <code>System property &#91;soz.postboss.redraw.capture&#93; does not exist</code> |
| <code>com.openggf.tools.audio.parity.TestS1OpenGgfAudioCapture#capturesTheCompleteReferenceControlledInterval</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: local deterministic BizHawk reference required</code> |
| <code>com.openggf.tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineCapture#captureRequestedOutput</code> | <code>org.opentest4j.TestAbortedException: Assumption failed: no local OpenGGF timeline capture was requested</code> |
| <code>com.openggf.tools.TestS3kSlotsGlassNative#glassOccludesPlayerAfterRealBonusFrame(String, int)</code> | <code>System property &#91;openggf.test.gl.native&#93; does not exist</code> |

## Publication and owned cleanup

The qualified integration and complete updated-main negative-case table are
pushed to develop at `9c563bf3067ec6739bb1d0533d431287dff8338d`;
an independent remote-ref query matches that full SHA. Its delta from tested
b317 changes only this audit, with zero executable, test, build or API change.
Normal commit/push hooks and the CI push policy validator pass.

After that verified push, all six owned R3 worktrees are inspected clean and
fully merged, then removed with their local task branches and exact worktree
metadata. Ignored contents are accounted for: Maven output, Python caches,
generated rewind-probe reports and ROM image-cache manifests, post-checkout
symlinks, and an identical config example. No unknown source or unmerged
change is discarded. Unrelated worktree metadata is identical before/after.

Both owned main wrapper/status pairs are removed after their exact category
runs were consumed and acknowledged. No owning process remains. The three
durable native-capture directories remain outside the repository. The seven
known main dirty/untracked paths and their bytes, unrelated jobs/requests,
and app-owned saved agent conversations are preserved. No provider state is
deleted because those conversations belong to the app, not this run.

This final follow-up changes audit prose only; completed engine checks are
not repeated. Its prepared message and lifecycle registry remain through the
verified push, then the small lifecycle cleanup receipt records their exact
teardown outside the removed registry.
