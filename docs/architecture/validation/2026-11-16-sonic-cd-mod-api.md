# Sonic CD Mod API/runtime host

Implementation branch: `feature/ai-scd-mod-api`, based on fetched
`origin/develop` `3a868d33d9cb59137d55b50eb4492c5b137e2792`.
Owner: implementation agent, OpenAI Codex GPT-6.1-sol. No delegated workers.
Stageplay, route tapes, editor projects and content import are separate lanes.

## Contracts

- `scd` patch manifests and `any` plans use the existing registration transaction,
  derived ownership and callback fault boundary. Native decoded placement patches
  retain `CdLevel`, geometry and native ring ownership.
- Native run character admission is module-owned. Hosted runs open disc media,
  rather than sending the selected CD game through cartridge detection.
- File-addressed art goes through `GameDataSource`/`HostedGameMedia`, then the
  existing bounded decoders. Scene program views use exact ISO names and
  file-relative offsets. No ROM/disc asset is committed or replaced by standalone
  assets. Program object art publishes only after owned preparation succeeds.
- Music overrides address native sub-CPU music commands, including CDDA and Past
  PCM music. Native music suppression preserves FM and PCM effect output. Stop,
  fade and CDDA pause/resume requests control the active streamed replacement.
- CD art overlays keep the native program owner available via module services;
  drawing still uses the resolved art provider. Native goal owners raise only the
  shared observer signal, never the ROM end flag used by player physics.
- The descriptor remains unpublished candidate `0.7.0`, with no published
  baseline. `ModApiVersion` describes the additions and the normalized `0.7` pin
  is regenerated using `ModApiSignatureSurface --snapshot`.

## Explicit limits

The stacked `feature/ai-scd-donor-hosting` follow-up replaces the cartridge-only
host animation read with an engine-owned `HostAnimationSource` service. CD loads
native scripts from its prepared stage program, while drawings, palettes, life
icons and name lettering remain owned by the cartridge donor. Its first $1F
animation entries retain the S1 semantic order. Native extended drawings (mini,
3D and give-up) have no cartridge drawings: donor movement poses remain in use;
the frame-triggered CD give-up is disabled for donated sheets rather than reading
unrelated donor frame numbers. Plain CD Sonic follows its unchanged native path.

The supported matrix is S1 Sonic; S2 Sonic/Tails; S3K Sonic/Tails/Knuckles.
S1 Tails/Knuckles and S2 Knuckles are rejected with an explicit missing-character
reason. CD cannot donate: the provider rejects non-cartridge donors before opening
secondary media. No missing-ROM fallback is used. CD Game Setup saves a separate
`launch.cd` profile and applies its team at the hosted exit boundary.

Follow-up verification is in progress; the original verification below belongs to
PR #350, not to the donor-hosting branch. Initial six combination tests all failed
at the inherited host refusal, then all six boot/movement tests passed. Native
act-end (25) and strict R11A replay (1) comparisons were selected but skipped for
absent optional local native recordings. Named facing/construction guards passed.

Flat authored CD zones admit owned objects and one collision path with 16x16-block
terrain. Native objects/rings require their stage's program/PLC/state context and
are refused there; a native decoded-level patch preserves that context. Stock
insertion anchors, authored water/deformation and hosted arenas are not exposed.
Common CD provider art keys are catalogued; stage-specific sheets remain expert
art-provider overrides. Program object art intake supports direct mappings, not
DPLC flattening. Scene character decoding retains native CD word-count DPLCs.

## Evidence and rejected approaches

The initial manifest/scene tests failed on CD refusals. A subsequent real disc boot
found an owned addition in the decoded inventory which never updated: simply
appending entries is wrong for CD's stored-order spawner. The placement encoder
now merges owned additions before the native scan boundary, preserving native
relative order and placement identities. The same compiled example mod binds
stock PP monitors and adds a visible marker using disc-loaded art.

A failing goal-post test showed that accepting a CD run did not make its finish
observable. The existing native timer-stop sites now emit the shared completion
signal, with no native physics/end-flag change.

Booting a program-art decorator exposed native CD art-owner assumptions:
`CdHud.writeDigit` cast the resolved overlay to `CdObjectArtProvider`. Native art
consumers now ask the module for their program owner; the integration test waits
for the title card to release control naturally rather than unlocking the player.

The broad-catch guard rejected extending scene art's old log-and-continue handlers.
Those handlers were removed and their legacy inventory rows retired; source
failures now propagate. A comment added to the release descriptor was rejected by
its strict key=value parser and removed; candidate version/topology are unchanged.

## Verification record

All Maven execution uses `tools/testing/maven_queue.py -Dmse=off`; GL boots select
`-Dopenggf.headless.gl=surfaceless-egl`, with `DISPLAY` and `WAYLAND_DISPLAY` unset. The wrapper found `soniccd.disc.path` and
reported no missing media. Seven Past-music direct native comparisons and thirteen
SSZ boss native comparisons require optional local recordings; their skips do not
mean disc-backed runtime coverage was skipped.

Focused selections include manifest/registration/fault boundaries, CD runtime and
program art, flat zones and policies, music metadata and output separation,
scene-run admission, all CD payload loading, native common goal objects, CD audio
live path/FM queue/session isolation, stock art catalogs, Mod API pin/surface/
release/runtime policies, and named broad-catch/per-game/game-policy guards.
The API/guard repair selection passed 48 tests with no failures or skips.
The initial focused selection passed **41 tests, zero failures/errors/skips**:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestSonicCdModRuntime,TestCdModMusic,TestCdModZones,TestStockArtOverrideCatalog,TestSonicCdCommonObjects2,TestModApiSignatureSurface" test
```

The separate CD output-separation selection passed all five `TestCdAudioMixGains`
tests, including replacement suppression preserving FM/PCM effects. The smoke
selection is 4,976 classes with two workers, bounded to smoke
plus guards, excluding exhaustive/trace/native profiles. Its command timeout is
40 minutes of execution per invocation; queue wait is separate. New/worse
failures block delivery and are compared with the same test identity on the base.

Full-tree citation checking reports inherited stale/unavailable references and
local disassembly checkouts different from the pinned references. It is not a
clean whole-tree citation claim; staged-citation validation applies at commit.

## Restore correction before final smoke

The first smoke attempt was deliberately stopped for a newly identified audio
selection restore defect: `VERDICT INCOMPLETE`, 8/100 ordinary batches, 3,889
tests, zero failures/errors, three allowed skips, guards not run. The owned runner
stopped Maven and cleaned temporary output; inspected diagnostics were acknowledged.
A new regression test then failed because restoring the CD audio state from
before an override still routed a later pause to that replacement.

The correction keeps suppression in the existing CD sub-CPU audio `State`, with
compatibility constructors, and removes the duplicate flag from the audio
profile. This is confined to the existing CD audio owner; no general rewind or
widescreen implementation changes. Native-versus-streamed selection is restored,
while configured mix gains remain presentation configuration. The correction's
focused selection passed **35 tests, zero failures/errors/skips**:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestCdModMusic,TestCdAudioMixGains,TestCdAudioLivePath,TestSonicCdModRuntime,TestModApiSignatureSurface" test
```

## Completed smoke and baseline attribution

Command (task-owned `TMPDIR` and Java temporary directory):

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 3a868d33d9cb59137d55b50eb4492c5b137e2792 --workers 2 --headless-gl surfaceless-egl --run
```

The complete bounded smoke ran on `a5958ae56832` before the final repairs below.
Its raw result remains **VERDICT FAILED**: 4,976/4,976 candidate classes,
100/100 ordinary batches, 1/1 guard batches; 41,310 tests, 52 failures, 2 errors,
562 skips (559 allowed, 3 unexplained), missing ROM properties: none.
It is not a full-suite pass or a passing smoke claim.

- Fourteen failures were stale three-game expansion/label assertions. They now
  expect four games, while cartridge-only example patches remain scoped to their
  actual games; no CD registration is invented for the Stageplay follow-up.
- Two architecture failures exposed a new game-to-mods dependency cycle. The
  game-owned `MediaSceneArt.View` now carries bounded source facts and exact disc
  program identity. Only the mod presentation host constructs `SceneRomArt`.
  Neither architecture guard nor its frozen baseline was relaxed.
- The remaining 38 failure/error identities reproduce on the exact base:
  109 baseline tests, 36 failures, 2 errors, zero skips. Comparison matched test
  identity, failure/error kind, exception type and assertion, normalizing only
  runtime object hashes and unordered expected mark-set display order.
- Seven of those 38 failures came from the inherited `DISPLAY=:0` environment.
  All seven pass with both display variables unset. The three unexplained reel
  skips also execute and pass under that environment. No skip policy was changed.
- The remaining **29 failures and 2 errors are baseline failures** listed below.
  No new or worsened failure remains in the inspected ledger after focused repair.

The final repair selection passed **216 tests, zero failures/errors/skips**:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestAnyGameSceneRegistration,TestExampleMutatorsPackage,TestModRegistrationRuntime,TestMutatorRegistration,TestTypingHedgehogPackage,TestModManagerScreen,TestSonicCdModRuntime,TestCdModMusic,TestArchUnitRules,TestModApiSignatureSurface,TestBroadCatchSwallowingGuard,TestRomSoundCatalog,TestSonic2NativeCompetitionHost,TestCompetitiveHalfpipeEgl,TestTestReelExtension" test
```

After adding the packaged example proof, `TestSonicCdModRuntime` passes **7 tests,
zero failures/errors/skips**. The example manifest passes production `GgfModCli
validate` with **zero findings**. The test loads the immutable JAR through
`ModClassLoaderFactory`, applies its patch, boots CD and observes its owned object
in the native `CdLevel`. Its parent loader excludes creator fixture classes to
prove the object comes from packaged bytes rather than the test classpath.

Baseline comparison used five queued selections on the unmodified base:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestSonic2NativeCompetitionHost,TestCompetitiveHalfpipeEgl,TestAizCollectorPublicRewind,TestThreeIslandsScene,TestPrivateLobbyScreenRendering" test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestLrzSszCheckpointRoutes,TestS3kCnz1MinibossCompletionEdge" test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestS3kFreshLevelRngState,TestS3kIcz1PhysicalLowerPlatformReplay,TestS3kMhzAct2AuthoredRoute,TestS3kMhzAct2EntryHeadless,TestS3kMhzAuthoredRoute,TestS3kMhzTailsAuthoredRoute,TestS3kMhzTeamAuthoredRoute,TestS3kMhzWideAuthoredRoute" test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestDezIncomingFinalRouteCapture,TestIcz2ColdCubeBridgeRoute" test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestS3kCompleteRunReferenceProducer#fixedProducerRejectsEveryMismatchedTypedRequestBeforePublication" test
```

Full-smoke preparation took 42.11 seconds, summed batch execution 2,120.7 seconds,
and reported completed queue wait 2,598 seconds. User wait was zero; other wall-time
and baseline/focused overhead are not measured by that smoke ledger.

### Exact baseline failure identities

The following assertions matched the smoke. Assertion excerpts omit long route
state dumps; no assertion was relaxed. The first seven environment cases are
subsequently green in the focused repair run.

| Test identity | Baseline assertion/error |
| --- | --- |
| `TestSonic2NativeCompetitionHost#freshNativeObjectRamClearBelongsOnlyToTheActualHostInitializer` | expected: &lt;null&gt; but was: &lt;:0&gt; |
| `TestSonic2NativeCompetitionHost#menuStartDoesNotBecomeANewPressAtEverySynchronousTitleWait` | expected: &lt;null&gt; but was: &lt;:0&gt; |
| `TestSonic2NativeCompetitionHost#sixRealActsKeepIndependentHumansCanonicalTimersRewindAndTheActLedger` | launch this EGL test with DISPLAY unset ==&gt; expected: &lt;null&gt; but was: &lt;:0&gt; |
| `TestCompetitiveHalfpipeEgl#nativeShadowOperatorsSelectTheSameRomPlanesAtDacIntensityBeforeProfilesAndFade` | expected: &lt;null&gt; but was: &lt;:0&gt; |
| `TestCompetitiveHalfpipeEgl#failedNativeWinnerAllocationRecolorsAlreadyQueuedSonicAndRestoresTheSameFramebuffer` | expected: &lt;null&gt; but was: &lt;:0&gt; |
| `TestCompetitiveHalfpipeEgl#realHalfpipeBootsAndReplaysBothHumansThroughTheDefaultNativeShadowPath` | expected: &lt;null&gt; but was: &lt;:0&gt; |
| `TestCompetitiveHalfpipeEgl#nativeObj5BSpillsRenderBothOwnerBirthPosesAndReplayRecreatedSlotsThroughRisingAndFalling` | expected: &lt;null&gt; but was: &lt;:0&gt; |
| `TestAizCollectorPublicRewind#publicIntroBeforeInitRoundTripsWithoutACollector(boolean)[1]` | AizPlaneIntroInstance: services not available — object must be created through ObjectManager |
| `TestAizCollectorPublicRewind#publicIntroBeforeInitRoundTripsWithoutACollector(boolean)[2]` | AizPlaneIntroInstance: services not available — object must be created through ObjectManager |
| `TestThreeIslandsScene#tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot` | expected: &lt;true&gt; but was: &lt;false&gt; |
| `TestThreeIslandsScene#aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches` | expected: &lt;DUNGEON&gt; but was: &lt;STORY&gt; |
| `TestThreeIslandsScene#startupIsPlayableAndArrivalDialogueAndRestStayInTheField` | expected: &lt;FIELD&gt; but was: &lt;STORY&gt; |
| `TestThreeIslandsScene#greenHillMysteriesUseRealInteractionsAndPersistThroughContinue` | expected: &lt;FIELD&gt; but was: &lt;STORY&gt; |
| `TestThreeIslandsScene#physicalTrailsConnectFieldsAndBattleUsesTheEncounterField` | cannot walk straight into Star Light ==&gt; expected: &lt;threeislands.screen.FieldScreen@&lt;instance&gt;&gt; but was: &lt;threeislands.screen.StoryScreen@&lt;instance&gt;&gt; |
| `TestThreeIslandsScene#allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards` | ghz enters through its actual doorway ==&gt; expected: &lt;DUNGEON&gt; but was: &lt;MENU_ROOT&gt; |
| `TestThreeIslandsScene#travellerDialogueWaitsForTheDiscoveryOrRescueItDescribes` | expected: &lt;STORY&gt; but was: &lt;MENU_ROOT&gt; |
| `TestThreeIslandsScene#explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue` | a discovery opens dialogue ==&gt; expected: &lt;STORY&gt; but was: &lt;FIELD&gt; |
| `TestPrivateLobbyScreenRendering#actualLobbyAndFullTextPixelsSupportMultipleReadablePages` | Right reaches new full-text pixels ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost` | ordinary opening remains alive at input 6499 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `TestS3kCnz1MinibossCompletionEdge#placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[2]` | sonic+solo@&lt;instance&gt;: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=… |
| `TestS3kCnz1MinibossCompletionEdge#placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[4]` | sonic+solo@&lt;instance&gt; frame 1564: P1 died p=328B,02EC v=0,-1792 g=0 a=true r=false h=false ring=0 c=31E0,028C s=32A1,02B5 b=3277,029C/A/3/false t=3334,02AE {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossSc… |
| `TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[2]` | sonic+solo@&lt;instance&gt;: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=… |
| `TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[4]` | sonic+solo@&lt;instance&gt; frame 1564: P1 died p=328B,02EC v=0,-1792 g=0 a=true r=false h=false ring=0 c=31E0,028C s=32A1,02B5 b=3277,029C/A/3/false t=3334,02AE {CnzMinibossCoilInstance=1, CnzMinibossInstance=1, CnzMinibossSc… |
| `TestS3kCnz1MinibossCompletionEdge#truncatedAfterApproachNeverCompletesTheEdge` | sonic+solo@&lt;instance&gt;: fixed input did not complete the edge: spawn=190 gate=288 release=408 hits=[1150, 1593, 2046] kill=-1 sign=-1 results=-1 load=-1 apparent=-1 title=-1 last=p=3375,032C v=12,0 g=12 a=false r=false h=… |
| `TestS3kFreshLevelRngState#actualFreshBoundaryClearsRngAndTheRegisteredWorldRestoresIt(int, int)[2]` | native D01 skips the ordinary title owner ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `TestS3kIcz1PhysicalLowerPlatformReplay#nativeSonicContactsAndRidesTheLowerPieceWithTwoFreshWorldReplays` | actual final neutral crosses the sampled turn ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |
| `TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[1]` | expected: &lt;[defeat, ship-carry, capsule-results, weather-fade, chase-hit, fbz-loaded, ship, admission, last-hit]&gt; but was: &lt;[]&gt; |
| `TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | cold route death at 36000 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | cold route death at 26152 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `TestS3kMhzAct2EntryHeadless#coldEntryPressesLiftsAndReleasesNativeTeamWithReplay(WidescreenAspect, String, String)[12]` | level event must preserve the cutscene-owned minimum ==&gt; expected: &lt;872&gt; but was: &lt;896&gt; |
| `TestS3kMhzAuthoredRoute#freshSonicCompletesActOneAndReplaysItsLiveInteractions` | expected: &lt;[pulley, defeat, act-two-released, sticky-vine, last-hit-approach, first-hit]&gt; but was: &lt;[pulley]&gt; |
| `TestS3kMhzTailsAuthoredRoute#freshTailsCompletesActOneAndReplaysItsLiveInteractions` | expected: &lt;[first-hit, defeat, lower-approach, last-hit-approach, early-traversal, upper-flight, act-two-released]&gt; but was: &lt;[lower-approach, upper-flight, early-traversal]&gt; |
| `TestS3kMhzTeamAuthoredRoute#nativePairCompletesActOneAndReplaysItsLiveInteractions` | cold route death at 26152 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `TestS3kMhzWideAuthoredRoute#wideSonicCompletesActOneAndReplaysItsLiveInteractions` | expected: &lt;[boss-hp-2, lower-loop, upper-loop, boss-hp-1, incoming-results, before-pulley, act-two-released, arena-approach, early-traversal, spike-passage, pulley-release, pulley-pull, sticky-vine, defeat, boss-hp-5, bo… |
| `TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | death at 14633 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | death at 14638 ==&gt; expected: &lt;false&gt; but was: &lt;true&gt; |
| `TestIcz2ColdCubeBridgeRoute#coldSonicShattersThePlacedCubePressesButton2AndCrossesTheFallingBridgeWithWorldReplay` | expected: &lt;6&gt; but was: &lt;5&gt; |
| `TestS3kCompleteRunReferenceProducer#fixedProducerRejectsEveryMismatchedTypedRequestBeforePublication` | Unexpected exception type thrown, expected: &lt;java.nio.file.FileAlreadyExistsException&gt; but was: &lt;java.lang.IllegalArgumentException&gt; |


## Donor-host follow-up verification (stacked on PR #350)

Base: `dc6b3c2b9edd621a0feecfe6d411628d51756f92`; branch
`feature/ai-scd-donor-hosting`. Owner: OpenAI Codex GPT-6.1-sol; no delegates.
Rewind, widescreen and Stageplay/editor implementation files are unchanged.

All Maven tests used the queue wrapper with `-Dmse=off` and
`-Dopenggf.headless.gl=surfaceless-egl`, DISPLAY/WAYLAND_DISPLAY unset, and a
unique task temporary directory. The wrapper found all cartridge/disc properties;
missing media: none. The initial six supported cases failed at the inherited
host refusal. Three boot-level unsupported-character cases then failed because
the legacy art initializer swallowed IOException and continued. Admission now
fails before level loading, clears prior donor state and supplies the character
and donor in the reason. The disc-donor negative control also failed before its
check was moved ahead of the same-game no-op.

The final focused selections cover six supported combinations, three invalid
character launches, all three S3K elemental shield grants, character physics,
semantic object animations, CD profile persistence/launch, actual hosted-card
Setup, decorated CD mod hosting, existing cartridge donation/family contracts,
API surface and named facing/construction guards. Across the final selections:
**137 passed, zero remaining failures/errors, one optional native solid-trace
skip**. The repaired CD mod/menu selection independently passed **23 tests,
zero failures/errors/skips**. The plain-Sonic act-end and strict R11A replay
classes were selected unchanged: 25 and 1 comparisons skipped for absent optional
native recordings. These are not parity passes.

Representative final selection (with task TMPDIR/java.io.tmpdir):

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestCdDonorHosting,TestCdLaunchProfile,TestLaunchProfile,TestLaunchProfileStore,TestLaunchConfigPanel,TestMasterTitleSecondaryActions,TestMasterTitleExitCoordinator,TestMasterTitleSuspendedGame,TestModApiSignatureSurface,TestNativeObjectFacingGuard,TestConstructionContextGuard,TestAnimationFamilies,TestCrossGameFeatureProviderRefactor,TestSonicCdModRuntime,TestSonicCdSolidObjectTrace,TestGameplayCaptureToolArgs" test
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestSonicCdModRuntime,TestMasterTitleSuspendedGame" test
```

The first command exposed one stale mod-runtime assertion that still expected
host refusal; the second verifies its replacement plus the added actual Setup
navigation. The cartridge flight-contract fixture now declares Tails in its
roster, matching the art it supplies. Cartridge preview fixtures explicitly
exclude disc titles rather than constructing cartridge readers for them.
The mutable candidate signature pin adds exactly the six CD profile preferences;
ModApiVersion describes the extension. The strict release descriptor remains
candidate 0.7.0, develop 0.8, with no published baseline or version promotion.

`GameplayCaptureTool` recorded S3K Tails on R11A, with ordinary input
`240 -; 160 R; 1 R+B; 60 R`, 461 total steps and capture beginning at 350.
The capture-specific hosted-media donor refusal was removed; source media still
opens through its module and donor art through its cartridge. Viewed frames 350
and 430 show running/jumping Tails and the donor life icon. CSV positions are
x=306 at 350, x=726 at 430 and x=904 at 460; all are alive. The final film contains
111 frames at 60 fps (960x672 nearest-neighbour presentation). `TASK_CAPTURE_ROOT`
is the user-local task capture directory, whose concrete paths are in the delivery:


- `${TASK_CAPTURE_ROOT}/tails-r11a-running/capture.mp4`
- `${TASK_CAPTURE_ROOT}/tails-r11a-running/state.csv`
- `${TASK_CAPTURE_ROOT}/tails-r11a-running/frames/00350.png`
- `${TASK_CAPTURE_ROOT}/tails-r11a-running/frames/00430.png`

Smoke plan: 4,978 classes plus guards, two workers, 40-minute per-invocation
execution limit excluding queue wait. Smoke result and exact baseline attribution
remain pending at this implementation checkpoint. Queue wait and execution are
reported separately by the wrapper/runner; user wait is zero, and other wall-time
is not measured. No passing smoke/full-suite claim is made here.

## Follow-up seams

Carry the source capability, its ownership and exact program identity through
Stageplay/editor/import preparation. `MediaSceneArt.View` provides game-owned detached disc facts;
`SceneArt.program` is the creator-facing file view. Offline library construction
still uses cartridge readers and needs a source-aware extension. Preserve CD's
zone/act/period projection rather than inventing cartridge addresses or treating
an empty `GameDataSource.rom()` as standalone content. Native terrain/Stageplay
kits and route fingerprints require their own source-aware owners and verification.
Donor host-family composition now uses the runtime source service described above.

## Donor follow-up repair checkpoint

Draft PR #353 preserves `054abc4df584472b50591e46a9a45725c028d85b` against
`feature/ai-scd-mod-api`. Implementation owner remains OpenAI Codex GPT-6.1-sol;
no delegates. Exact-class baseline attribution on `dc6b3c2b9e` ran
`TestDonatedTailsFlightArt,TestHostedMediaLaunch,TestMasterTitleHubMessages,TestAizCollectorPublicRewind,TestS3kAiz1EntryMatrix,TestS3kHcz1EntryMatrix`
through the queue, with display variables unset and surfaceless EGL. The baseline
has two errors, both `publicIntroBeforeInitRoundTripsWithoutACollector(boolean)`
variants: `AizPlaneIntroInstance: services not available — object must be created
through ObjectManager`. All other selected baseline cases pass without skips.
The interrupted donor smoke's other seven failures and two errors are regressions.

The R11A pixel witness boots plain Sonic and S3K Tails separately, steps 270
neutral frames, asserts camera x=0, and compares the upper 90 rows, including
SCORE and the empty foreground over sky/mountains. It first fails at pixel (0,0),
blue `-16777034` versus black `-16777216`. Inspection confirms that empty
foreground cells obscure the background; this is not a broken background layout.
`CdObjectArtProvider` constructed its donated life-icon `PlayerSpriteRenderer`
with base pattern 0; `CdHud.draw` invokes its frame, and
`PlayerSpriteRenderer.prepareFrame` caches/uploads its four DPLC tiles at 0–3.
These indices belong to the host, including its transparent foreground tile.
Speculative relocation of body/tail banks did not change that pixel; those edits
were removed. A dust-relocation experiment was stopped once the concrete life-icon
writer was found; it is not claimed as a completed check or a repair.

Live jobs at this checkpoint: branch exact-class attribution and explicit cached
blank-pattern red test. Next: allocate the icon in owned object space, verify,
commit/push each green repair, then final focused checks, one final smoke and films.
Queue wait is reported by the wrapper; user wait is zero. Other wall time is not
measured. This checkpoint is not a completion or parity claim.

The explicit cached-pattern negative control also failed: after donated HUD draw,
pattern zero's packed words were `1229782942255939583, 1269974486289586961,
-7381088551871636719, 1844700211534475537` instead of four zero words. The repair
includes the icon's four tiles in `getRegularPatternCount` and constructs/caches
its renderer immediately after the provider's other object sheets. Thus normal
object-range preflight covers it; no fixed spare address or background repair is
introduced. A first repaired render keeps the blank tile transparent and removes
the mesh, but revealed different camera Y from character focus. The final witness
freezes both cameras at `(0,1806)` before the same 270 ticks. Its result is pending.

The final same-camera pixel witness passes: **1 test, zero failures/errors/skips**.
Both pictures use camera `(0,1806)` and 270 ordinary ticks. The upper **64 rows**
(20,480 pixels) are identical, including sky/mountains, palm foliage and SCORE.
Rows starting at 73 contain the intentionally different playable character and
are outside the background comparison. In both runs the cached empty foreground
pattern is all zero after the real HUD draw. The nine donor boot/ability cases
also passed after the icon allocation change. No native gameplay state or stage
pattern is rewritten by this repair.

Fly-in repair `e5ef881b8d`: `TestDonatedTailsFlightArt` plus
`TestCrossGameFeatureProviderRefactor`, **18 passed, zero skips**. Menu repair
`7e07420619`: `TestHostedMediaLaunch`, `TestMasterTitleHubMessages`, and
`TestMasterTitleSuspendedGame`, **36 passed, zero skips**. The original branch
failures were observed before these changes. Expanded `TestCdDonorTeams`:
**17 passed, zero skips**, covering all 14 donor roster pairs and three rejected
followers. Native-host admission's initial repair exposed art-provider selection
still using the donor; the next selection checks host art retention as well.

Native-follower admission and provider selection now pass the exact two entry
matrices plus `TestCdDonorHosting` and `TestCdDonorTeams`: **40 tests, zero
failures/errors/skips**. The donor roster decides art ownership per participant;
host-supplied followers keep their host provider, auxiliary art and palette.
CD lacks those native characters, so the three unavailable donor follower cases
still refuse with explicit donor/character reasons and leave donation inactive.
No game-name condition was added to shared runtime code.

Fresh film root: `${TASK_CAPTURE_ROOT}` (the concrete user-local path is in the delivery).
The production capture uses scanline limits, no-device audio and surfaceless EGL;
input is round-trip-validated by `InputLogAuthorTool`. `tails-running/capture.mp4`
and `sonic-control/capture.mp4` each contain 111 frames (960x672, 60 fps, 1.85s).
At frame 420 both cameras are `(580,1796)`, players are x=676/y=1865 and alive;
the upper 48 rows match pixel-for-pixel. Inspected both frame 420 pictures and
`tails-recovery/frames/00900.png`, confirming sky/mountains, foreground and SCORE.
The recovery film has 1,321 frames (22.016667s). Ordinary P2 Left input separates
Tails; the native CPU leaves him at its dormant sentinel near 820, returns him
above Sonic at x=1442/y=1857 near 900, descends through y=1957 at 1000 and lands
by 1200. No teleport, death injection or trace input was used. Main film input:
`240 -;160 R;1 R+B;60 R`; recovery appends `360 - / L;900 -`.
All three films have `state.csv` and PNG frames beside them. Matched-camera test
stills are preserved as `matched-camera-tails.png` and `matched-camera-sonic.png`.

The first final focused attempt ran no tests: Maven could not write compiler
status because `/mnt/LinuxData` was full (83 MiB free). Removed only the finished
baseline run's generated `target/classes`, `target/test-classes` and
`target/maven-status`; its exact reports/logs remain. The baseline tree and source
were untouched. The rerun starts with about 822 MiB available. This is a repaired
prerequisite, not an attributed test failure or a passing-check claim.

Final focused selection on runtime source `ba61e2d362f576662deb2586d59624fc3a30da4e`:
**204 tests, zero failures/errors, 26 optional native-input skips** (178 executed).
`TestSonicCdActEndNative` skips 25 cases and `TestSonicCdR11aTraceReplay` skips one
for absent local recordings. They are not parity passes. Missing ROM/disc
properties: none. Facing/construction guards, donor/composer/animation/profile
checks, all requested Mod API pin/surface/runtime/release checks, and the four
required S3K bootstrap/loading classes execute successfully. Command:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestSonicCdActEndNative,TestSonicCdR11aTraceReplay,TestCdDonorHosting,TestCdDonorTeams,TestDonatedTailsFlightArt,TestCrossGameFeatureProviderRefactor,TestCrossGameRuleComposer,TestCrossGamePolicyComposer,TestAnimationFamilies,TestCdLaunchProfile,TestHostedMediaLaunch,TestMasterTitleHubMessages,TestNativeObjectFacingGuard,TestConstructionContextGuard,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiRuntimePolicy,TestModApiReleasePolicy,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestCdLiveMotionPixels#donatedTailsPreservesR11aBackgroundAndScorePixels' test
```

The finished-tree smoke plan selects **4,979 classes plus guards**, 100 ordinary
batches and two workers; 40 minutes execution limit excludes queue wait. Stop on
incomplete infrastructure and diagnose; never restart blindly. Compare failures
by identity/assertion to `dc6b3c2b9e`. No source edit or build occurs in the task
tree during this run. Final VERDICT and attribution remain pending.
