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

The runner caps retained failure-message prefixes at 2,048 characters. The wide-MHZ
and SSZ-Tails rewind assertions exceed that limit, so the table records their
first concrete fields, not a claim that their full serialized state matched.
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
