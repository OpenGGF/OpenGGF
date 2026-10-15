# S3K Hidden Palace coverage matrix

Game / canonical zone / act: S3K `S3K_HIDDEN_PALACE`, engine zone `$16` act index 1,
ROM `Current_zone_and_act = $1601`, SKL object set.
Character routes: Sonic/Tails (lower floor → `$B40` teleporter → upper corridor →
Knuckles fight → altar → floor collapse → altar teleporter → SSZ act 1 `$A00`) and
Knuckles (start `$10,$2EC` → `$B40` upper teleporter forced to subtype `$4A` → SSZ act 2
`$A01`). Owning plan: [HPZ bring-up](../../plans/2026-09-16-hpz-bring-up.md).
Status: in progress. Nothing below certifies the act.

Raw registry slots and transitions: `$1600` LRZ boss (separate slot), `$1601` this act,
`$1701` Super Emerald sanctuary ([separate partial matrix](s3k-hpz-sanctuary.md) under `S3K_SPECIAL_STAGE_ARENA`).
Incoming: implemented LRZ boss exit → `$1601`; Knuckles bypasses the boss slot
and leaves LRZ Act2 directly. Current cold LRZ Sonic+Tails at 320px/Sonic+Tails at 800px/Tails at 320px/Knuckles at 320px
checks reach released HPZ. The October14 joined native320 Sonic+Tails and Knuckles
checks below continue those exact cold prefixes through HPZ and released SSZ1/2;
other complete configuration products remain open. Data-select
slot 10 is `LevelList_DA6E`. Outgoing: `$A00` (Sonic/Tails), `$A01` (Knuckles).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as SOZ): supported presets 320/352/400/528/800. The current short
compatibility/lifecycle checks select `WidescreenAspect` through the production
resolver and assert the resulting configuration/camera width after restoration,
loads and death. Explicitly labelled 512/640 custom-width checks preserve the older
local geometry contracts separately. Supported character/donor pairs
off×{Sonic,Tails,Knuckles}, S1×Sonic, S2×{Sonic,Tails}; teams: solo, native pair,
donor-supported mixed/duplicate followers and a declared seven-follower
roster at every current preset. Each participant in the declared comma rosters is independently donor-supported;
`ActiveGameplayTeamResolver.parseConfiguredSidekicks` admits those configured rosters
without a fixed cap. The test generator substitutes Sonic for unsupported requested
characters to retain its declared count; that is **not** launch-profile fallback.
`LaunchProfileApplier` sanitizes a requested S1 Tails sidekick to None, while an
explicit S1 Sonic sidekick is supported. Menu profiles and the comma-roster
configuration API are distinct. Seven followers do not establish a maximum;
the 30 actual normalized-profile entry vectors qualified below verify the literal
fallback and supported-pair behavior through the production applier and ROM entry.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$1601` resources, bounds, object set, title card | Registry/sprite/screen-event tables, LevelSizes `$1880`×`$B20`; `Obj_TitleCardName`/`Obj_TitleCardAct` overrides | native | `TestSonic3kLevelLoading#hiddenPalaceActLoadsRom1601BoundsAndObjectSet`, `TestSonic3kNonlinearHpzProfile`, `TestSonic3kTitleCardSublevelMappings` | implemented | pass | Title card rendering not captured |
| ENTRY: character start and ScreenInit limits | Knuckles start `$10,$2EC`, Sonic/Tails `$30,$AEC`; `HPZ_ScreenInit` | 85 production-preset scenarios, plus12 separately labelled custom-width cases | `TestS3kHpzCompatibilityMatrix#coldEntryAppliesCharacterStartLimitsAndPaletteControl` and legacy geometry method | implemented | Qualified in the scoped October14 runs below; earlier34-case count is historical | Complete route, alternate-entry and native-presentation breadth remain separate |
| EVENT: palette control at camera X `$460`; Sonic/Tails upper-route `$AA0` left limit | `Obj_HPZPaletteControl`; `HPZ_ScreenInit` | Native crossing; 85 production-preset upper-corridor scenarios plus12 custom-width cases | `TestS3kHpzActEventsHeadless#paletteControlSwitchesToPalHpzWhenTheCameraCrosses460`, `TestS3kHpzCompatibilityMatrix#upperCorridorEntrySelectsPalHpzAndCharacterCameraLimit` | implemented | Current scenario qualification below; independent native crossing evidence retains its earlier provenance | Native switch timing pending probe |
| EVENT: `Events_fg_4` collapse chunks | `HPZ_ScreenEvent` row 7 cols `$30/$31` = `$61` | native, synthetic trigger | `TestS3kHpzActEventsHeadless#foregroundCollapseWritesChunk61IntoRowSeven` | implemented | pass | Production trigger belongs to the Knuckles fight lane |
| PRESENT: AnPal_HPZ / AniPLC_HPZ | ROM tables entries 45/47 | native | `TestHpzZoneRuntimeStatePaletteCycle`, `TestS3kHpzPatternAnimation`, `TestS3kHpzActEventsHeadless#anPalHpz...` | implemented | pass | Native pixel comparison open |
| PRESENT: `$EC0` background seam redraw | `HPZ_BackgroundEvent` state machine | native | Native probe `probe-seam` (redraw routine 4 for 7 frames, no visible change) + engine capture `raw-08-bg-seam-ec0` | not-applicable (redraw machine rejected: no visible native effect) | native evidence recorded | Revisit only with a boundary that exposes the redraw |
| OBJECT: teleporter transport and P1-only authority | `Obj_SSZHPZTeleporter` + `Obj_TeleporterBeam`; `loc_45660` tests P1 standing after solid contacts | 85 production-preset transport scenarios plus12 custom-width cases;55 independent follower-authority scenarios | `TestS3kHpzTeleporterHeadless`, `TestS3kHpzCompatibilityMatrix#lowerTeleporterTransportReplaysAtChargeAndRise`, `#standingFollowersCannotClaimTheLeadersTeleporter`; native `probe-teleporter` | implemented; prior native charge/rise/settle/light corroboration retained | Current scoped qualification below; followers acquire real independent standing contacts without allocating a beam or seizing P1 | Upper pad subtype0 intangibility during transport not asserted directly; beam pixels not compared |
| REWIND: entry, upper-corridor entry, teleporter charge, rise, settle release, beam deletion and follower contact | Entire registered-world restore and forward replay, twice at every named boundary; live roster/CPU-chain/display assertions | 85 production-preset scenarios,12 custom-width scenarios and55 independent authority cases | `TestS3kHpzCompatibilityMatrix` | implemented | Scoped qualification below, without snapshot omissions or comparison filtering | Positioned fight/theft/ending graph and dust replay retain their separate canonicalization limits. The two joined native320 campaigns are qualified below; additional per-mechanic/configuration and native-pixel breadth remain open |
| ROUTE (Knuckles cold): start `$10,$2EC` → `$B40` pad → `$A01` | Knuckles complete-run BK2 input from movie frame 411496 (segment `hpz22` row 0); ROM loads SSZ act 2 at row 859 | native 320, Knuckles | `TestS3kHpzColdRoutes#knucklesRecordedInputsLeaveForSkySanctuaryActTwo` | implemented | pass (exit condition within 60 frames of row `$35C`) | Exact exit-frame comparison open |
| ROUTE (Knuckles): upper teleporter → `$A01` | `loc_45B94` | Five production presets plus separately labelled512/640 geometry; Knuckles has no donor support per `LaunchProfile` | `TestS3kHpzLifecycleProduction#knucklesUpperTeleporterStartsSkySanctuaryActTwo`, legacy geometry method; earlier demo retained | implemented | Seven cases pass with actual load, cleared checkpoint history and two complete registered-world restore/replay cycles through SSZ2 arrival/crane/control release | The joined native320 Knuckles cold campaign is qualified below; exact native exit-frame comparison and additional route products remain open |
| ROUTE (Sonic + Tails cold): `$1601` entry → run-in → lower teleporter → fight → theft → collapse → altar teleporter → `$A00` | Complete-run BK2 input from 441758; native `probe-route/fight/altar/ending/anim`; declared native lag frame 447347 | native 320, Sonic + Tails | `TestS3kHpzColdRoutes#recordedInputsMatchNativeCheckpointsThroughTheEndingToSkySanctuary` (18 position + 5 animation checkpoints), `#recordedInputsReachTheKnucklesFightThroughTheLowerTeleporter` | implemented; native behaviour matched (camera and both players every frame 441761-448821 except camera X sub-pixel) | pass | Camera X trails for 2 load frames and by 1 px from 446448 (history from earlier acts); the later ship palette-latch correction is recorded in the [HPZ plan](../../plans/2026-09-16-hpz-bring-up.md); whole-scene parity remains open |
| BOSS: Knuckles fight, emerald theft, collapse | `CutsceneKnux_HPZ`; registry subtype `$28` → `CutsceneKnucklesHpzInstance` | positioned Sonic/Sonic+Tails; separately declared native fight seed | `TestS3kHpzKnucklesFightHeadless` (five cases), `TestS3kHpzKnucklesFightNativeSequence` (two cases) | implemented with native child graph and 47 routines | Existing HPZ selections record passing implementation/replay evidence; see the plan and current binding note below, not a new `7d0` execution | Positioned/seeded fixtures are not cold LRZ→HPZ campaign evidence; full configuration/native conformance remains open |
| ROUTE (Sonic/Tails): altar teleporter → `$A00` | `loc_45AD6`, `loc_45BF4`; `HpzTeleporterRouteHelperObjectInstance` and semantic full-save exit | positioned and fresh direct HPZ entry | `TestS3kHpzKnucklesFightHeadless#theftCollapseAndTeleporterEndingRunToSkySanctuary`, `#sonicAndTailsEndingMatchesTheRecordedTimers`; existing `TestS3kHpzColdRoutes` | implemented | Recorded HPZ fight/exit and direct-route qualification | The joined native320 Sonic+Tails campaign is qualified below; disk persistence and remaining configuration/native presentation breadth are separate |
| OBJECT: placed Master/Super Emeralds in `$1601` | `Obj_HPZMasterEmerald`, `Obj_HPZSuperEmerald` have no zone branch; states 1 and 2 are selectable (`loc_907A8`) | unit; native all-Super save | `TestHpzSanctuaryObjects#pedestalStatesUseRomCentrePositionsAndFourStateBehavior`; native `probe-altar` vs `raw-22-altar-all-super` | implemented (state-1 selectability corrected) | pass; visual match by inspection | Production pedestal special-stage entry from `$1601` not route-tested |
| LIFE: physical checkpoint `$34` sub2 at `$CF0,$3E8`, repeated death/reload | StarPost; `HPZ_ScreenInit` Knuckles `$AA0` limit | 80 preset Sonic/Tails/roster cases and5 Knuckles refusal cases;10 custom-width lifecycle and2 refusal cases | `TestS3kHpzLifecycleProduction#touchingThePlacedStarPostThenDyingReloadsAtThePost` and legacy geometry method | implemented | 97 cases pass: each applicable case makes real post contact, two deaths/full reloads, released-destination30-frame replay twice and actual outgoing history retirement | Knuckles refusal retained; immediate activation rewind, hazard-contact death and broader fight/ending lifecycle remain open |
| LOAD: LRZ3 → `$1601` incoming; Knuckles LRZ Act2 bypass | LRZ boss/end-level and direct character handoff owners | Sonic+Tails at 320px/Sonic+Tails at 800px/Tails at 320px/Knuckles at 320px | `TestLrzBossColdRouteCapture`, `TestLrzWideBossColdRouteCapture`, `TestLrzTailsColdRouteCapture`, `TestLrzKnucklesColdRouteCapture` | implemented | Current cold arrival at released HPZ: Sonic+Tails at 320px/62 windows, Sonic+Tails at 800px/71, Tails at 320px/62, Knuckles at 320px/89; owning LRZ matrices retain exact source/commands | The joined native320 pair/Knuckles checks below continue to released SSZ1/2; wide-pair and solo-Tails incoming checks still stop at released HPZ |
| ROUTE / REWIND: joined cold LRZ1→HPZ→SSZ1 | Ordinary committed movie; `loc_63D1A`/`loc_85CA4` fight/camera child, theft/collapse/altar owners and actual `$A00` full load | Native 320 Sonic+Tails | `TestS3kHpzJoinedColdRouteCapture#coldPairDefeatsKnucklesAndReplaysTheJoinedTheftCollapseAndSszArrival` | implemented, boss-music child recreation repaired | 66 entire-registry two-cycle45-frame spots; eight real hits, all theft/collapse/altar phases, actual assembly/control release and both source-history retirements | Remaining donor/preset/main/team traversal products and native presentation/oracle parity are open |
| ROUTE / REWIND: joined cold LRZ1→HPZ→SSZ2 | Ordinary committed movie; `loc_45744` upper-pad rise, `loc_45B94` exit and SSZ2 native crane release | Native 320 Knuckles solo | `TestS3kHpzJoinedColdRouteCapture#coldKnucklesReplaysUpperPadAndTheJoinedSszCraneControlRelease` | implemented | 27 entire-registry two-cycle spots:26×45 frames, native short rise1×8; actual assembly/crane/control release, earned3 rings and both source-history retirements | Wider Knuckles route products and native presentation/oracle parity remain open |
| ORACLE: route timing | Sonic+Tails complete-run `hpz22_2` rows `$1E46+` | — | `TestS3kSonicTailsHpz222SegmentTraceReplay` (expected red) | — | Historical 1902 errors, first frame 0 `camera_y`, on `84c9e38d8`; that replay never left the LRZ3 prefix | Current strict trace requalification remains open; implemented LRZ incoming is no longer the missing-runtime premise. Preserve the dated 2026-09-17 trace frontier |

## Current boss, exit and incoming bindings — 2026-10-13

The current table replaces stale “fight lane missing” and “LRZ has no events”
claims; it does not rerun those suites or certify the act. Historical HPZ port
`020611217`/sidecars `1cb447aab` record 119 focused cases and 669 guards; native-
sequence selection `a69c34437` records 273 cases; merged `8d14d2591`/`d507bcf22`
records 357 HPZ/results/required-S3K cases. The [owning plan](../../plans/2026-09-16-hpz-bring-up.md)
retains those commands and later ship palette-latch evidence.

The five positioned fight cases cover slot 45/camera admission, eight-hit defeat,
fight replay, theft/collapse/altar exit and Sonic+Tails ending timers. Two native-
sequence cases use declared fight state and test its routine/hit/flag timeline
and dust replay. Their registry comparisons canonicalize dynamic ordering and
exclude `LevelSnapshot.epochAtCapture`, `ObjectManager.bucketsDirty` and
`peakSlotCount`; this is not unfiltered whole-world equality.

Current ordinary LRZ cold routes qualify released HPZ arrival: Sonic+Tails at 320px reaches
it in 53,047 inputs with 62 windows; Sonic+Tails at 800px in 50,659 with 71; solo Tails at 320px in 62,524
with 62; Knuckles at 320px in 49,526 with 89. [LRZ Act2](s3k-lrz-act2.md) and the
[boss-act matrix](s3k-lrz-boss.md) record their current source and exact checks.
These endpoints are not joined to the fresh direct-HPZ→SSZ fixtures above.

## Execution evidence

Worktree `.worktrees/ai-hpz-bring-up`, all ROMs by absolute path, `maven_queue.py -Dmse=off`.
`TestS3kHpzCompatibilityMatrix`: 68 tests (34 entry + 34 teleporter), 0 failures, 0 errors,
0 skips, 6.2 s. Changing the teleporter end assertion to `< $100` produced 34 failures at
that assertion (proves the teleporter rows execute to the end); reverted before commit.

`TestS3kHpzLifecycleProduction` (`315a483db` + test): 31 tests, 0 failures, 0 skips. The
first run failed only the five Knuckles post rows (checkpoint index -1): his `$AA0` camera
boundary keeps him away from the post, which the rows now assert.

Breadth follow-up: `TestS3kHpzCompatibilityMatrix` 102 tests and `TestS3kHpzLifecycleProduction`
35 tests, 0 failures, 0 skips. Title-card set (`-Dtest=TestSonic3kTitleCardSublevelMappings,*TitleCard*`):
19 classes, 81 tests, 0 failures, 0 skips.

## Full SaveGame consumer boundary — 2026-10-08

Both existing HPZ full SaveGame exit gates (sonic3k.asm:91524/91718) now dispatch through the semantic helper; camera/player and ending-countdown gates are unchanged.

`828bc94d8` clears the native32-bit collected-ring mask at exactly seven existing
full-SaveGame gates; existing game-state rewind owns the mask. Focused138 cases
pass without failures/errors/skips, including direct Results tally/helper and
mask-restoration checks. The other six non-tally live routes, successful disk
persistence, live SK-alone, and this matrix's remaining route/rewind/breadth
products are not newly certified. Generic persistence and special-stage/lives/
death/reload/seamless semantics remain distinct. See the [lane audit](../../audits/2026-10-07-s3k-parity-gap-verification.md)
for native ordering and scope, and [combined qualification](../../audits/2026-10-07-stock-parity-gap-verification.md#final-composed-canonical-replay-2026-10-08)
for exact commands/frontiers. [Actual-main qualification](../../audits/2026-10-07-stock-parity-gap-verification.md#actual-main-delivery-qualification)
retains 27 concrete inherited ordinary failures and 62 literal skips with zero
errors; all 672 fresh guards pass. This preserves the route/breadth limits above.


## Production presets, roster authority and repeated lifecycle — 2026-10-14

Lane `feature/ai-s3k-parity-hpz`, base `5b2ff8e7851b99dd40998dfc41fbfd21c80031ef`.
Only the two existing HPZ test classes and this matrix change; production Java,
ROM assets, gameplay rules and participant policy are unchanged. Java 25.0.4.1 and
the original absolute S3K/S1/S2 ROM paths are used. Identities are locked-on S3K
CRC32`63522553`, S1 REV01`AFE05EEE`, S2 REV01`7B905383`.

The 85 production scenarios are 30 solo rows (five actual presets × six supported
main/donor combinations) plus 55 native-pair/mixed/duplicate/seven-follower rows.
S1's identical two-Sonic mixed/duplicate rows are deduplicated. Every participant
has production-supported donation, decoded ROM mappings/animation and an
independent live identity; each CPU follows the preceding live participant.
The 12 legacy solo scenarios retain 512/640 × six supported main/donor combinations,
explicitly as custom geometry rather than menu-preset coverage.

Each compatibility scenario independently verifies cold ROM entry, upper-corridor
camera/palette ownership and lower-pad transport. Entire registered-world snapshots
at entry/corridor and charge/rise/release/beam deletion restore and replay twice.
Another 55 cases place CPU followers into real falling contacts while P1 stays away:
`loc_45660` must retain P1-only launch authority, independent standing latches and
the CPU chain across both full-world replay cycles. Local position/velocity stimuli
are declared component setup, not cold traversal or seeded standing/outcome state.

Lifecycle cases physically touch the post and use two `applyPitDeath` stimuli through
GameLoop's death/fade/title/reload owner. Actual replacement runtime/object ownership,
saved post, main/camera focus, participant art/chain, selected preset and donor rules
are checked at each load and released destination. Each released world has two
30-frame whole-registry replay cycles. Outgoing live history is nonempty and the
actual full load retires it. Knuckles' native refusal remains a negative case.
The five preset and two legacy Knuckles exits continue through real SSZ2 load/rise/crane
release with whole-registry equality and two restore/replay cycles, replacing the
older player/camera/SSZ-byte-only comparison.

The first two-class request finishes 08:14:18 BST: 450 tests, 145 failures, zero
errors/skips, 1:53 Maven time; compatibility 346 takes 30.855 seconds and lifecycle 104
7.079 seconds. All 291 entry/transport/corridor cases and seven complete Knuckles
incoming graph cases pass. The 55 authority failures expose a fixture-premise error:
`byte_466E8` puts the pad centre surface17 pixels above its origin, omitted by the
initial falling-contact setup. The 90 death-history failures read a cached outgoing
controller after retirement. Existing MHZ/DEZ checks and the production boundary
owner instead resolve the current controller, which may be absent at that boundary.
Neither negative justifies a runtime correction or weakening the asserted contract.

After those two fixture corrections, the affected method selection finishes 08:17:11
BST: 152 tests, zero failures/errors/skips, 40.640 Maven seconds; compatibility 55
takes 4.808 seconds and lifecycle 97 takes 11.27 seconds. Those 152 overlap the seven
already-passing Knuckles refusal cases. Together with 298 unchanged passing cases
from the first invocation, all 450 distinct identities are accounted. This records
scoped retained qualification, **not a current-source whole-450 green invocation**,
pristine-source red, complete act certification or native waveform/pixel parity.
There are no active requests. Broader combined validation remains lead-owned.

Both requests use the following environment and ROM arguments from the intended
HPZ worktree; `PROJECT_ROOT` is the checkout containing the existing original ROMs:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kHpzCompatibilityMatrix,TestS3kHpzLifecycleProduction' \
  "-Ds3k.rom.path=$PROJECT_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=$PROJECT_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PROJECT_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dopenggf.headless.gl=surfaceless-egl test
```

The second request replaces only the selector with
`TestS3kHpzCompatibilityMatrix#standingFollowersCannotClaimTheLeadersTeleporter,TestS3kHpzLifecycleProduction#touchingThePlacedStarPostThenDyingReloadsAtThePost+legacyCustomWidthRetainsPostAndKnucklesRefusalGeometry`.
Queue waiting is excluded from the Maven/case times. No broad or release-only full
suite, desktop automation, new native recording or media claim belongs to this slice.
Consumed diagnostic logs/XML remain temporary and are removed after inspection.

Remaining at the first slice (the two representative joined campaigns are closed
by the subsequent qualification below): immediate physical-post activation
rewind and actual hazard-contact deaths; fight/theft/collapse/altar authority and
lifecycle products beyond the existing narrower fixtures; receiving-pad intangibility,
production pedestal special-stage entry and persistent-save roundtrip; synchronized
native phase/pixels/audio and complete viewport/donor/main/team traversal axes.
Seven followers do not establish a maximum; the production parser remains uncapped.


## Joined cold LRZ→HPZ→SSZ route qualification — 2026-10-14

The same lane/base now carries two complete ordinary-input movies and
`TestS3kHpzJoinedColdRouteCapture`. Sonic+Tails uses 67,455 inputs, preserving all
53,047 parsed input frames of the already qualified incoming movie; Knuckles uses
51,710 inputs, preserving all 49,526 incoming frames. Authoring reads native owners
only to choose controller input. There are no physics/aux imports, progression or
clock seeds, debug navigation, hidden P1 death/respawn, or gameplay-owner calls.
Each selected movie first passes an independent uninterrupted cold replay using
an immutable base-runtime jar, SHA256
`6ad077354c1565ed692c4a380c499196abde909044543e3ca289b94cb542a3cc`.
Author/capture command/source/input identities remain in the owned external task
`$AGENT_TMP/s3k-parity-20261014/hpz-joined-routes-f8eaa39a0`.
The initial unqueued exploratory pair probe is explicitly excluded from shared-
admission/isolation evidence; all subsequent authoring, independent cold captures
and focused qualification use normal queued admission.

Actual input indexes (zero-based) are pair HPZ load/assembly 52,925/52,926 and SSZ1
67,004/67,005, with native control release67,178; Knuckles HPZ 49,286/49,287 and SSZ2
50,138/50,139, with native crane/control release51,564. The pair finishes grounded
at 485/3180, no rings, with the same concrete/native-P2 Tails roster; Knuckles finishes
grounded at 356/1196 with three earned rings and no followers. Every ordinary and
replayed step forbids P1 death and life loss, checks main/roster/native-P2 authority
and native 320 preset/camera. Native CPU-Tails recovery is allowed and its full state
is still compared. Loads must publish actual fresh destination counters, current
main/camera focus and released assembly; later control release is checked separately.
Both real full loads retire genuinely recorded source histories. Raw whole-world
windows reject callback/fresh-boundary checkpoints and never cross the retiring world.

The first joined gate finishes 09:35:22 BST:2 tests,2 failures,0 errors/skips.
The pair's immediate HP8 restore loses `HpzKnucklesBossMusicObjectInstance` and its
occupied slot: the class already implements `RewindRecreatable`, but its only
`(boolean,boolean)` constructor cannot construct the interface probe. The minimal
owner repair adds a private no-argument delegate; ordinary allocation and behavior
stay unchanged, and existing generic scalar restoration owns direction bits, wait
word and camera-lock bits. A new short existing-fight-class regression lets the
native 120-frame wait and both camera locks naturally delete the child, then checks
immediate entire-registry restore and forward replay twice from the deleted world.

The second focused request finishes 09:42:34 BST:8 tests,1 failure,0 errors/skips.
All six local fight tests, including that natural expiry/recreation regression,
and the pair's 66 two-cycle45-frame whole-world spots pass. The remaining Knuckles
failure identifies the actual rise at 50,106: it has only 20 playable ticks before
`loc_45B94` admits the exit at 50,126 (`Camera_Y < $240`, P1 X≥`$B00`), followed by
load 50,138. A 45-frame raw window cannot straddle that intentional world retirement.
The corrected phase marker reads `STATE_RISING`; its eight-frame replay ends 50,114,
before an independent eleven-frame live-history tail 50,115–50,125. The other 26
Knuckles spots retain 45 frames. No movie, runtime transition, comparison field,
history-reset obligation or native exit predicate changes to accommodate this test.

The affected sole-Knuckles request finishes 09:45:11 BST:1 test,0 failures/errors/skips,
53.311 Maven seconds (29.97 case seconds), with all 27 spots passing. The two focused
requests therefore qualify eight distinct identities, retaining the seven unchanged
passes from the preceding invocation; **not one whole-selection green invocation**.
Commands use the environment/three absolute ROM arguments above and these selectors:

```text
-Dtest=TestS3kHpzKnucklesFightHeadless,TestS3kHpzJoinedColdRouteCapture
-Dtest=TestS3kHpzJoinedColdRouteCapture#coldKnucklesReplaysUpperPadAndTheJoinedSszCraneControlRelease
```

Movie SHA256s are pair
`8b4cba7e92308beddbe25ebdcbd79530be0946367568f4d45701c452c9677f56`
and Knuckles
`c1ae1018cc65bed63c81d751d8b8cffc3ebbbee0bf47134fd9dc8aa730bb2e9d`.
This closes representative joined native 320 pair/Knuckles composition and the
specific boss-music-child rewind hole. It does not close the remaining finite
preset/donor/main/team traversal products, physical-post activation rewind,
hazard-contact death, per-mechanic authority/lifecycle breadth, receiving-pad
intangibility, pedestal entry, disk-save roundtrip, or native phase/pixel/audio
conformance. No new native recording or media parity is claimed. Integration and
combined qualification remain lead-owned and pending.


## Actual launch-profile normalization and HPZ entry — 2026-10-14

`TestS3kHpzCompatibilityMatrix#launchProfileSanitizationFeedsTheActualHpzEntry`
adds 30 independent cases: six literal requested/expected configurations crossed
with all five production presets. It calls `LaunchProfileApplier.apply` for the
S3K host, resolves the applied aspect and builds a fresh ROM HPZ fixture **without
clearing or replacing** the applied main/follower configuration. Each case checks
native spawn, concrete roster/CPU chain, actual donor ROM renderer/animation,
configuration/camera width, 90 ordinary intro frames and actual control release,
then immediate entire-world restore and forward replay twice. Existing 450-case
method bodies and their scoped evidence are unchanged.

Literal expectations are S1 Sonic+requested Tails→Sonic solo, S1 requested
Tails+Knuckles→Sonic solo, explicit S1 Sonic+Sonic retained, S2 requested
Knuckles+Knuckles→Sonic+Tails, explicit S2 Tails+Sonic retained, and native
Knuckles+Knuckles retained. `LaunchProfile.effectiveCharacterDonor`,
`mainCharacterValues`, `sidekickValues` and `sanitize` own this policy; it is not
a restriction inferred from helper names or receiving-game identity.
`ActiveGameplayTeamResolver.parseConfiguredSidekicks` separately owns extended
comma-roster configuration. The first slice's explicit S1 Sonic duplicates are
supported participants, not an assertion that requesting S1 Tails produces them.

Source is joined commit `e49ee9126` plus this appended test method. The sole focused
request uses the same environment and three absolute ROM arguments above, with
`maven_queue.py --lean` and selector
`-Dtest=TestS3kHpzCompatibilityMatrix#launchProfileSanitizationFeedsTheActualHpzEntry`.
It finishes 09:49:38 BST: **30 tests, zero failures/errors/skips**, 30.417 Maven
seconds. This verifies actual normalized launch entry; it does not add full donor
traversal, hazard/death or native presentation/oracle evidence. The previously
qualified first-slice and joined-route obligations retain their precise source
and execution provenance; combined delivery remains lead-owned.

## Native single-fade retiming (2026-10-15)

8179ccd80 stopped `GameplayCaptureSession.step` from advancing a runtime-ready
loop's FadeManager a second time. Movies authored before it budget half of each
frozen fade-out (11 of the native 22 frames, during which gameplay and object
VInt do not advance), so their later inputs landed early. `FadeWindowRetimeTool`
(41405e073, 5333aece6) measured each owned frozen window in the production harness,
inserted the native remainder as repeats of the held input and verified every
movie by one uninterrupted replay with no death. On the LRZ Knuckles and Tails
chains the retimed rows equal the 22d5e6ffc rows shifted by the inserted frames,
VInt included (0 differing rows). Test frame constants move by 11 at and after each
old load frame. Base: Root ef61a560f; no runtime behaviour changed.

| Movie | Old SHA-256 | New SHA-256 | Windows `start:native→+inserted` | Frames | New full loads | Verified end |
|---|---|---|---|---|---|---|
| `lrz-knuckles-cold-hpz-ssz2-320.bk2` | `c1ae1018cc65bed63c81d751d8b8cffc3ebbbee0bf47134fd9dc8aa730bb2e9d` | `aef01eab70e802005b6ead7cdfdcc845e4d5f36f1c945bc10699ef456d4a737b` | 49275:22→+11 50138:22→+11 | 51732 | 25112, 49297, 50160 | 356,1196, 3 rings, zone 10/1 |
| `lrz-sonic-tails-cold-hpz-ssz1-320.bk2` | `8b4cba7e92308beddbe25ebdcbd79530be0946367568f4d45701c452c9677f56` | `0ac8acb508ba16f4f579f2566554237ccf0d55636a612497a847c5cbc2be1607` | 43555:23→+11 52925:22→+11 67015:22→+11 | 67488 | 30285, 43577, 52947, 67037 | 485,3180, 0 rings, zone 10/0 |

Validation on ef61a560f plus the retimed fixtures (`TestS3kHpzJoinedColdRouteCapture`), focused selectors through
`tools/testing/maven_queue.py` with the S3K ROM: run `cold-fade-verify` (Maven
Finished 04:15:51Z) passed `TestDezIncomingFinalRouteCapture` 7/0/0/0,
`TestDezSoloActTwoColdRouteCapture` 4/0/0/0, `TestLrzActTwoColdRouteCapture` 2/0/0/0,
`TestLrzTailsColdRouteCapture` 4/0/0/0 and the fade rows of `TestLrzKnucklesColdRouteCapture`
and `TestSszSoloColdRouteCapture`; run `cold-joined-retimed` (Finished 04:21:19Z) passed
`TestS3kHpzJoinedColdRouteCapture` 2/0/0/0 (pair 66 spots, HPZ 52947/52948, SSZ1
67037/67038, release 67211; Knuckles 27 spots, HPZ 49297/49298, SSZ2 50160/50161,
release 51586). That test's LRZ live-history tail now starts 36 frames before the HPZ
load (14 live frames before the native 22-frame fade-out). Rows still red there are
not fade timing and are owned elsewhere: the Knuckles act-2 restore at 37130 and the
LRZ boss sidekick overlap parity at 47471 (rewind-snapshot rows) and solo Tails' SSZ1
opening camera (behaviour row). No skips.

## Cold Tails HPZ campaign leg (2026-10-15)

The cold Tails-alone campaign (`TestS3kLrzHpzSszColdCampaign#tailsColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory`,
SSZ1 matrix) is the first cold Tails replay through HPZ's Knuckles fight, theft, collapse and
altar with the single-player helper timers to `loc_45D8A` (`$A00`): HPZ load 62424, SSZ1
load 69861 with HPZ's live history retired at the SSZ1 assembly. It continues to DEZ1. The
solo Sonic LRZ→HPZ→SSZ1 campaign remains open: its cold attempt stopped at the LRZ3 end boss
(kill condition, three bounded searches in `Obj_LRZEndBoss` routine 4) and never reached HPZ
(SSZ1 matrix).

