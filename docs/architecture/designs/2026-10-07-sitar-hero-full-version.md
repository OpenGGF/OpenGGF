# Sitar Hero full version

## Outcome and constraints

Expand the merged arcade example into a complete rhythm game, using Slay the
Robotnik's persistent game loop, accessible navigation, records, settings and ROM
presentation as the quality reference. The user requested production polish on
2026-10-07 after identifying career and multiplayer as the arcade prototype's
remaining full-version requirements. Their repository policy authorizes routine
design decisions and delivery without repeated approval ceremonies.

The user expanded the scope to every substantive song from the three supplied
ROMs, with parallel Sol workers researching each game's inventory and arrangement.
Short power-up loops and jingles are excluded with explicit rationale. Looping
performances last at least two complete outer arrangement loops (including the
intro) or 120 seconds, whichever is longer. Every non-looping ending/credits
track runs from its beginning to the native driver's final stop. Runtime music
and artwork remain ROM-backed; no copied ROM bytes ship in the mod. The mutable
candidate API remains 0.7.0.

## Player experience

- Title: career tour, quick play, practice, local co-op, local score duel,
  direct-connect play, records, how to play, settings, and return to stock title.
- Seven cosmetic performers, four musical roles, and Easy/Medium/Hard/Expert.
  Easy uses three melodic lanes, Medium four, Hard/Expert five. Direct drums keep
  their authentic pad identities and kick; density varies by difficulty.
- Career groups the available catalogue into three-song venues. The first is
  open; two clears in a venue open the next. Progress is scoped by instrument and
  difficulty and never requires an absent ROM. Quick play has no unlock gate.
- Practice has no failure and cannot change earned records or career progress.
  The initial practice scope is whole-song rehearsal with honest miss accounting.
- Results show score, accuracy, stars, full combo, best streak, previous best and
  retry/next/menu actions. Versioned bounded saves preserve records across visits.
- Local co-op and versus have independent remaps/calibration and two readable
  highways on one consumed-audio clock. Both players play the same selected role
  and difficulty. Co-op shares song survival; versus scores independently.
  The selected part remains audible while either local player sustains it.
- Direct-connect uses explicit host/join, shared-ROM eligibility, selection and
  chart agreement, ready confirmation, synchronized start and visible peer status.
  Each peer judges its own timestamped input against its local consumed audio.
  Peer-reported scores are match data and never earned solo records. Disconnect or malformed traffic aborts the match with a recoverable notice.
- Keyboard/gamepad navigation remains reliable; mouse hover/click/wheel operates
  visible choices. Lists scroll instead of overflowing the 400x224 viewport.
  Reduced flashes and independent player settings improve accessibility.
- Performers use native sprite anatomy layered around their instruments.
  Successful judged notes drive visible strokes and small sound-wave accents;
  idle and paused performers do not mime an unrelated constant beat.
- Finite ROM music preparation has progress and cancellation. Independent
  synthesis runs in a host worker; the scene thread retains ROM loading and
  playback ownership. Duration and PCM memory budgets bound long arrangements.

## Owners and boundaries

The example owns charts, progression, controls, match protocol and presentation.
The engine owns sockets, queues, worker lifetime, bounded messages and ROM PCM.
Scene calls do not wait for DNS, connections, reads or writes. At most one peer
is connected; closing or faulting a scene closes its endpoints and workers.
No raw networking capability is exposed to creator code.

`SceneContext.network()` supplies `SceneNetwork.host(port)` or
`connect(host,port)`. A `ScenePeer` exposes state/error, nonblocking send/poll,
monotonic receive timestamps and close. Messages are at most 4096 characters;
pending queues hold at most 256. Only explicit host/join choices open endpoints.
ROM identity/content is not transmitted; song/chart metadata detects mismatches.
This is direct peer play without matchmaking or a server account service.

`PerformanceResult` and `PlayerProfile` are pure Java. Records separate song,
role and difficulty; incomplete, failed, practice, demo and multiplayer attempts
cannot become normal single-player clears. `CareerProgress` derives unlocks from
the current supplied catalogue rather than storing fragile array indices.

Existing chart calls default to Medium. The original three songs retain their
authored musical ownership; new difficulty calls change density/lane abstraction
without inventing chip voices or shifting attacks off ROM timestamps.

## Implementation and verification plan

Pinned integration base: `09282b17305cb5794e43a26855cd2b9543b4ff5f` (`develop`).
Parent tree: `.worktrees/ai-sitar-hero-full`, `feature/ai-sitar-hero-full`.

1. Three game-specific Sol workers own native song inventories, arrangement
   providers, probes and ROM evidence. The earlier twelve-song draft is preserved
   as a seed. The integrator owns shared catalogue/curator changes. Verify all
   seven ROM subsets and every supported song/role/difficulty chart: authentic
   attacks, selected audio ownership, lane/density bounds and full-song tails.
2. Career worker owns result/profile/progression/no-fail rules and pure-model
   tests. Verify corrupt/versioned saves, independent records, difficulty/role
   unlocks, missing-ROM catalogue changes and finite no-fail sessions.
3. Network worker owns the scene facade, host lifecycle, candidate pin/SDK/docs
   and loopback tests. Verify cancellation, malformed/oversized framing, bounded
   queue overflow, refusal, disconnect, ordering and endpoint teardown.
4. Integrator owns scene/screens, controls, two-player/match consumer, packaging,
   documentation and acceptance captures. Start with failing scene-route tests;
   add shared-clock independent input, practice eligibility, pointer navigation,
   ready/mismatch/start/disconnect/rematch tests and visible end-to-end captures.
   A separate Sol performer worker owns ROM arm masks, layered instrument poses,
   hit-driven animation and anatomy/occlusion captures for all seven actors.
5. Run focused checks before freezing code. Inspect the combined category plan
   from the pinned base, run Java 21/Lua 5.4/PowerShell preflight, and compare
   ordinary and guard results with a matched immutable baseline. Shared API/host
   changes require broad validation. Check skips and complete failure identities.
6. Reconcile upstream develop by intent, integrate without switching the main
   workspace branch, verify the integrated tree, push only develop, and remove
   only fully merged/accounted task trees and branches.

## Evidence and decisions

Initial preflight with the default Lua failed before executing tests because it
was not Lua 5.4. Repeating with `LUA_BIN=/usr/bin/lua5.4` passed all prerequisites.
Slay the Robotnik is the presentation/completeness reference; it does not itself
provide multiplayer transport. The scene networking seam is therefore an actual
host extension, not a cosmetic online menu.

Physical audio/controller latency remains a calibration boundary. Offline
captures establish scene flow, art and audio content, not hardware latency or
exact Guitar Hero III executable parity. No balance certification is inferred
from automated full-song play.

Career model `f75fba41a` and bounded network host `fc734561e` are integrated in
the parent tree. The initial catalogue seed `f148434b4` was preserved before
expanding to game-specific inventories. Host commit `741fdb3c7` adds cancellable
full/part rendering and repairs implicit saved-duration reuse; the S1/S2 providers
`e9decec62` and `f975bc903` were merged without conflicts. Main develop remains
unchanged pending combined verification.

Focused host/sequencer command (parent tree at the host change, all three exact
main-ROM properties supplied): `maven_queue.py -Dmse=off
-Dtest=TestSmpsSequencerCadence,TestSmpsSequencerSnapshot,TestSceneMusicRom,TestSitarHeroArcade
test -B` completed 37 tests with zero failures/errors/skips. The synthetic
SavedDuration test first failed (8 expected ticks, 4 actual) and passed after
copying scaled saved track RAM. API signature/SDK/Javadoc and controls checks
passed. Enlarging the career save exposed a test still asserting the old 1,024
line cap; correcting that boundary to 4,096 produced a 12-test career pass.

The immutable baseline tree at `09282b173` selected all 3,005 ordinary classes
plus fresh-JVM guards. Java 21/Lua 5.4/PowerShell preflight passed. Its category
run started with a 120-minute invocation cap and ten-minute no-output limit;
The completed ordinary lane ran 26,149 tests: 28 failures, zero errors and
61 skips in 4,592.39 seconds. The fresh guard JVM ran 672 tests: zero
failures/errors/skips in 209.12 seconds. All 28 failure identities and messages
were inspected. The one message longer than the runner's 2,048-character JSON
limit was recovered completely from the retained Maven summary; comparison
ignores only nondeterministic Java object identity hashes. Skips are diagnostic,
graphics/platform or route prerequisites; the ROM-backed catalogue checks did
not silently skip because of missing ROM paths. Diagnostics were acknowledged and deleted after inspection. Candidate and
integrated comparisons remain pending.

The performer worker landed `3d576ca2c`, merged as `f71d88a3f`: all seven
characters and four instruments have independently cut native arm/hand layers.
Its 44 focused checks passed without skips; 28 actor/role acceptance combinations
and 48 exact-pixel reconstructions established native anatomy and foreground
occlusion. Root gestures are wired only to newly successful judgments, including
chords/HOPOs/autoplay, with separate P2 instances even for identical characters.
Paused drawing clears transient gestures. Twenty-three direct Java 21 packaged
scene checks pass, including successful-hit animation, distinct local controls,
Results calibration, unready guest retry, wheel precedence and same-ROM scenery
fallback. These used current example sources and the previous compiled host;
final Maven validation of the combined sources remains required.

A static Sol host review of `741fdb3c7` found one material issue: cancelling a
full/part job left it in the pending slot, so identical requests returned the
cancelled job and direct start remained blocked. The root repair releases the
owner's slot and discards failed/cancelled terminal jobs before reuse. Regression
cases cover the exact same full/part request and direct start after cancellation.
The review found no other material networking/synthesis ownership/publication
blocker and corroborated SavedDuration behavior in native owners. It did not run
builds or tests; the repair subsequently passed in the combined 72-test run.

The integrated public S1 library retains eleven substantive selections. Native
surveys retain all thirteen composed entries, but the 541-frame Title and
561-frame Continue are excluded as short menu cues, consistently with the S2
short-cue policy. Ending remains the 1,081-frame natural-ending exception. S2
contributes twenty-two substantive tracks including its 4,406-frame Ending and
9,527-frame Credits. Scrap Brain uses 8,640 frames (144 seconds). The independent S3K provider contributes 46 public songs after excluding the
two short title cues and two Knuckles character cues. Its exact duration units preserve fractional-quarter
pickups and loops; native tempo anchors preserve the final S&K medley.

Shared chart curation now repeats ownership sections with each complete loop,
uses a native quarter clock without requiring DAC (S1 Special Stage has pitched
FM6 and no percussion), and maps the S1/S2 credits' tempo/medley anchors. S1
$88..$8B timpani variants map to real sample$83; S2 tom/timpani/bongo rate aliases
retain their underlying sample families rather than misleading macro names.
Difficulty thinning, pitch windows, HOPO intervals and sustain ticks use the
local musical quarter while every gem retains its exact native attack sample.

A first actual-GL scene capture compiled and validated the current external mod,
opened all three verified ROMs, and reached title, performer, instrument,
difficulty, song list, help, records, settings and direct-connect screens. Four
120-second GHZ roles reached actual playback, pause, and hit-driven native
animation; local co-op showed separate Sonic/Robotnik performers beside independent
highways. No scene fault finding was reported. Capture runtime used the last
compiled host and the current example sources, so this is presentation evidence,
not validation of the uncompiled host retry repair. Images reside in the explicit
external task directory `$SITAR_CAPTURE_ROOT/ui-v1`.
An obsolete three-step breadcrumb was identified for follow-up; geometry and
native hand/instrument layering were visually inspected at 400x224.

The first capture identified the obsolete three-step breadcrumb; it now includes
the difficulty step. Calibration chooses an actual percussion song from the
selected game's library (with a cross-library fallback) and prepares only thirty
seconds. This allows calibration after choosing S1 Special Stage's drumless FM6
arrangement while retaining that selected song. A consumer regression covers
the source music ID, bounded preparation and preserved song choice. Player-two
calibration ignores player-one's default key/pad and saves its own settings file.

The focused root request was cancelled while still queued, before Maven ran, to
include those newly identified changes; no pass is inferred from the cancellation.
The replacement request covers scene, full-song charts, controls/career/model and
the host retry regression with all three absolute canonical ROM properties.

The S3K native inventory established that Sonic 3 Credits loops, unlike the
S&K final credits medley. Its native loop form therefore uses the ordinary
two-loop/two-minute policy. The S3 provider also identified authentic cross-song
FM calls from Sonic 3 Ending into earlier Title-bank phrases (609-frame true
end versus 514 in the bounded production slice). The S3 worker is explicitly
authorized to repair `Sonic3kSmpsData`'s semantic program bank view and test
earlier-header calls generally, without a music-ID carve-out. No incomplete
ending is accepted as a delivered remaining discrepancy.

A read-only Sol consumer review identified four concrete edge cases: calibration
from Results discarded the completed attempt; cancelling guest loading prevented
reconfirming that same round; pointer hover could undo wheel movement; mapped
Genesis directions could cancel raw menu arrows. The root preserves completed
sessions/results during calibration, reopens unready guest confirmation after
cancellation, gives wheel movement precedence, and owns raw key/pad navigation
with an 18-tick initial/6-tick held repeat. New consumer cases exercise each
trigger. It also corrected two test references to the actual `credits-s2` ID.
The pending focused request was replaced before execution to include the repairs.
The review was static-only; no additional review ceremony or pass is inferred.

The independent protocol worker landed `cc77aeb49`: Maven verifies 20 protocol
checks (including actual `ManagedSceneNetwork` loopback endpoints), five
packaged-controls checks, ten model checks and sixteen transport checks: 51
unique tests, zero skips. The packaging validator rejected a static collection;
constructing the supported-ID list inside validation corrected it and the
packaged rerun passed. Asymmetric latency regression coverage accepts legitimate
peer controls/telemetry before the receiver-local start estimate while preserving
local count-in ownership. This is focused validation, not a combined-suite pass. Internal SH1 adds start/control acknowledgments while
preserving its public consumer methods. Pause is the OR of per-player intents;
resume releases only the caller's request and completion releases its own intent.
Both players must release if both paused. Root `started()` usage already follows
`startDue()`, and unready guest loading cancellation permits a same-round retry.

The updated capture tool `26b2f2af4` is integrated as `2b9a27802`. Direct Java 21
all-ROM, S1 and S3K visits passed against the temporary 37-song library. The S2
visit completed performer/menu/settings/calibration checks but correctly rejected
Emerald Hill's missing scene-art profile. Scenery now selects an available picture
profile from the same ROM when the selected song's zone is unsupported, without
changing its music or chart. A consumer regression covers S2 and S3K unsupported
zones. The tool's unused queued Maven compilation was cancelled before execution;
the combined candidate build will compile the committed tool. Final provider and
scenery acceptance refresh is still required.

The parent S1/S2 native run at `59c8be076` completed 21 tests: 20 pass, one fail,
zero skips. S1 Credits PSG2 expected 503 native attacks but production still
produced 599 after `741fdb3c7`; this is an unresolved causal driver discrepancy,
not an accepted short catalogue duration. The original S1 worker resumed with
shared sequencer ownership to trace and repair it. Its direct probe identified
96 extras: 32 repetitions of three duration-only commands following a rest with
saved PSG frequency $FFFF. Native PSGDoNoteOn suppresses these under RESET;
S3K KEEP differs. The repair passes all 18 S1 checks, ten cadence, two snapshot
and twelve generated FM/PSG/DAC divider/rest/replay regressions (42 total, zero
skips). PSG/2 noise and DAC stop at native 6992; PSG/1 reaches native 7600 and
7,601 total packets. There was no 608-service global tail gap.

The same semantic fix exposed three false Oil Ocean PSG0 attacks in the S2
independent reference: service 613/3094/5575, offset $30B, note $80. Native
zPSGDoNoteOn checks the signed saved frequency and suppresses these silent rest
continuations. S2 reference repair `553dbbad6` passes its two native semantic regressions and
all 22 complete requested streams/natural endings (three tests, zero skips).
Production behavior was retained; the oracle now follows the owning routines.
The pre-existing unverified S2 note-fill rest-bit state gap is recorded in the
S2 evidence and is outside this attack-stream/timing verification. Earlier root
requests were cancelled before execution while final source integration continued;
the combined 72-test run subsequently passed.

### Exact baseline failure identities

Command and immutable commit are recorded above. The longer SSZ failure is
summarized here by every differing object/slot; its full message was inspected
and retained in coordinator memory for matched comparison, not archived as a log.

| Class | Test | Failure |
|---|---|---|
| tests.TestFbzSandopolisTimelineHeadless | productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice | SOZ restore cycle 0 sprites: [sprites.sprites[0].state.playerExtra.instaShieldRegistered: A=false B=true] ==> expected: <true> but was: <false> |
| tests.TestS3kMhzAct2AuthoredRoute | incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2] | late pulley owns Tails ==> expected: <true> but was: <false> |
| tests.TestS3kMhzAct2AuthoredRoute | incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3] | late pulley owns Sonic ==> expected: <true> but was: <false> |
| tools.TestDezIncomingFinalRouteCapture | coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding | expected: <96> but was: <0> |
| tools.TestDezIncomingFinalRouteCapture | coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding | expected: <96> but was: <0> |
| tools.TestDezIncomingFinalRouteCapture | incomingFinalFightRestoresAndReplaysEveryPhase(int)[1] | death at 26706 ==> expected: <false> but was: <true> |
| tools.TestDezIncomingFinalRouteCapture | incomingFinalFightRestoresAndReplaysEveryPhase(int)[2] | death at 26750 ==> expected: <false> but was: <true> |
| tools.TestDezIncomingFinalRouteCapture | coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding | expected: <96> but was: <0> |
| tools.TestDezIncomingFinalRouteCapture | coldEmeraldTeamClearsBothActsFinalFightAndDoomsday | death at 53897 ==> expected: <false> but was: <true> |
| tools.TestDezIncomingFinalRouteCapture | coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding | expected: <96> but was: <0> |
| tools.TestLrzActTwoColdRouteCapture | coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState | death at input 36526 ==> expected: <false> but was: <true> |
| tools.TestLrzBossColdRouteCapture | coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState | death at input 36526 ==> expected: <false> but was: <true> |
| tools.TestLrzKnucklesColdRouteCapture | coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace | expected: <1069> but was: <899> |
| tools.TestLrzTailsColdRouteCapture | coldTailsClearsActOneAndRestoresTraversalFightAndHandoff | death at input 19460 ==> expected: <false> but was: <true> |
| tools.TestLrzTailsColdRouteCapture | coldTailsRestoresActTwoTraversalToTheMiddleCorridor | death at input 19460 ==> expected: <false> but was: <true> |
| tools.TestLrzTailsColdRouteCapture | coldTailsCompletesBossActAndReachesPlayableHiddenPalace | death at input 19460 ==> expected: <false> but was: <true> |
| tools.TestLrzTailsColdRouteCapture | coldTailsCompletesActTwoAndRestoresTheBoulderHandoff | death at input 19460 ==> expected: <false> but was: <true> |
| tools.TestLrzWideBossColdRouteCapture | coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld | expected: <2796> but was: <524> |
| tools.TestMhzPairColdRouteCapture | pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad | the route must observe the actual history-reset boundary ==> expected: <true> but was: <false> |
| tools.TestMhzWideColdRouteCapture | wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay | [wide-route-19500] restore 0 zone-runtime: [zone-runtime.stateBytes[2]: A=46 B=26, zone-runtime.stateBytes[3]: A=-104 B=64, zone-runtime.stateBytes[6]: A=38 B=21, zone-runtime.stateBytes[7]: A=-44 B=-32, zone-runtime.stateBytes[10]: A=31 B=17, zone-runtime.stateBytes[11]: A=16 B=-128] ==> expected: <true> but was: <false> |
| tools.TestSszColdRouteCapture | coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents | death at input 7311 ==> expected: <false> but was: <true> |
| tools.TestSszColdRouteCapture | coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport | death at input 7311 ==> expected: <false> but was: <true> |
| tools.TestSszSoloColdRouteCapture | coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches | death at 7671 ==> expected: <false> but was: <true> |
| tools.TestSszSoloColdRouteCapture | coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory | death at 7671 ==> expected: <false> but was: <true> |
| tools.TestSszTailsColdRouteCapture | coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1] | expected: <48> but was: <0> |
| tools.TestSszTailsColdRouteCapture | coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2] | expected: <48> but was: <0> |
| tools.TestSszTailsColdRouteCapture | coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2] | replay at 4018: slots 24/29 only in A; Egg Robo flame/arm objects 125/126 missing in B; objects 128/129 restored to different slots |
| tools.audio.timeline.TestS1GameplayAudioTimelineCli | shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup | expected: <0> but was: <4> |

S3K provider/bank execution `64c7b25b6` passed 63 unique focused tests without
skips, including both complete music tables and earlier-header bank calls.
The public library has 79 songs (11 S1, 22 S2, 46 S3K). The provider retains
50 research entries: two title cues and two Knuckles cues are excluded centrally.
The S&K character form is 36 quarters/922 frames; the S3 form repeats an
eight-/four-quarter FM motif over a fourfold 16-quarter clap phrase. Native
cutscene callers and limited musical development support this exclusion under
the user's short-loop instruction; merely padding them to two minutes was rejected.

Shared curation uses exact fractional intro/loop quarters (Data Select 1.75,
Final Boss 26.5+69.5, Desert Palace 2302/24). Loop ownership sections use those
fractions; final S&K Credits maps its quarter-beat pickup from native tempo
anchors. All named S3K percussion families are mapped; the seven speech, scratch
and ambient sample IDs are excluded from drum strikes. Echoed claps $B2/$B3
remain pad hits. Unsupported song zones use a supported same-ROM concert stage.

Nine direct Java 21 packaged non-ROM chart checks passed after combining all
three providers: seven ROM subset/roster combinations, all 79 forms, real role
absence, fractional loop boundaries, native credits tempo anchors, DAC identities
and shortened independent clocks. Final root focused Maven command is
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off
-Dtest=TestSceneMusicRom,TestSitarHeroArcade,TestSitarHeroCareer,TestSitarHeroModel,TestSitarHeroControls,TestSitarHeroCharts
test -B`, with all three absolute main-checkout ROM properties. It was admitted
after 26 seconds; sources were frozen through completion. Actual tool preflight
passed Java 21, Lua 5.4 and PowerShell.

The combined parent command completed successfully: 72 tests, zero failures,
errors or skips, 9:46 Maven time. The all-79-song chart class took 511.2 seconds;
all supplied-ROM instrument/difficulty matrices passed. The host retry repair
passed its same-request full/part cancellation and direct-start regressions.

A final packet-clock regression then reproduced one-sample rounding at 8 kHz:
Data Select's native service56 boundary is sample7466, while the chart anchor
rounded to7467. Chart anchors now use the same integer frame*rate/60 cursor as
the host and anchor each repeated loop separately. Direct packaged regressions
verify fractional pickups, repeated boundaries and credits handoffs. Normal
48/44.1 kHz boundaries are unchanged. Focused clock verification and the required
combined broad matrices are recorded below when completed.

The focused packet-clock Maven rerun passed all three selected cases, zero skips,
22.303 seconds Maven time. Ten direct packaged non-ROM chart checks pass. The
normal change-based plan selects all 3,015 ordinary candidate classes and fresh
JVM guards because this delivery changes shared audio and scene API/host contracts.
The observed baseline cost was 76.5 minutes ordinary plus 3.5 minutes guards;
full-song chart matrices add several minutes. The candidate run uses a 120-minute
cap excluding queue wait, with a ten-minute no-output stopping rule. New or
worsened failures block integration; every inherited failure is compared by
identity and full message, including the recovered long SSZ message.
