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
raw `(Ctrl_2)` at `sonic3k.asm:42788/42952`, whereas Java reads synthesized CPU
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

The wider S3K completion gap was rechecked in the updated assembly at
`08a2446947fa2a70b3a768952a9f0147c248a09c`: `Sonic3kGameModule` still has no
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

The Sitar owner independently compared all 28 failure identities and complete
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
