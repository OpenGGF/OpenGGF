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
checks reach released HPZ; direct HPZ→SSZ route checks remain separate. Data-select
slot 10 is `LevelList_DA6E`. Outgoing: `$A00` (Sonic/Tails), `$A01` (Knuckles).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as SOZ): supported presets 320/352/400/528/800. The current short
compatibility/lifecycle checks select `WidescreenAspect` through the production
resolver and assert the resulting configuration/camera width after restoration,
loads and death. Explicitly labelled 512/640 custom-width checks preserve the older
local geometry contracts separately. Supported character/donor pairs
off×{Sonic,Tails,Knuckles}, S1×Sonic, S2×{Sonic,Tails}; teams: solo, native pair,
donor-supported mixed/duplicate followers and a declared seven-follower
roster at every current preset. Unsupported donor participants become supported
Sonic duplicates without changing participant count; seven is not a parser maximum.

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
| REWIND: entry, upper-corridor entry, teleporter charge, rise, settle release, beam deletion and follower contact | Entire registered-world restore and forward replay, twice at every named boundary; live roster/CPU-chain/display assertions | 85 production-preset scenarios,12 custom-width scenarios and55 independent authority cases | `TestS3kHpzCompatibilityMatrix` | implemented | Scoped qualification below, without snapshot omissions or comparison filtering | Positioned fight/theft/ending graph and dust replay retain their separate canonicalization limits. Joined cold campaigns, additional per-mechanic/configuration and native-pixel breadth remain open |
| ROUTE (Knuckles cold): start `$10,$2EC` → `$B40` pad → `$A01` | Knuckles complete-run BK2 input from movie frame 411496 (segment `hpz22` row 0); ROM loads SSZ act 2 at row 859 | native 320, Knuckles | `TestS3kHpzColdRoutes#knucklesRecordedInputsLeaveForSkySanctuaryActTwo` | implemented | pass (exit condition within 60 frames of row `$35C`) | Exact exit-frame comparison open |
| ROUTE (Knuckles): upper teleporter → `$A01` | `loc_45B94` | Five production presets plus separately labelled512/640 geometry; Knuckles has no donor support per `LaunchProfile` | `TestS3kHpzLifecycleProduction#knucklesUpperTeleporterStartsSkySanctuaryActTwo`, legacy geometry method; earlier demo retained | implemented | Seven cases pass with actual load, cleared checkpoint history and two complete registered-world restore/replay cycles through SSZ2 arrival/crane/control release | Exact native exit-frame comparison, joined cold LRZ campaign and additional route products remain open |
| ROUTE (Sonic + Tails cold): `$1601` entry → run-in → lower teleporter → fight → theft → collapse → altar teleporter → `$A00` | Complete-run BK2 input from 441758; native `probe-route/fight/altar/ending/anim`; declared native lag frame 447347 | native 320, Sonic + Tails | `TestS3kHpzColdRoutes#recordedInputsMatchNativeCheckpointsThroughTheEndingToSkySanctuary` (18 position + 5 animation checkpoints), `#recordedInputsReachTheKnucklesFightThroughTheLowerTeleporter` | implemented; native behaviour matched (camera and both players every frame 441761-448821 except camera X sub-pixel) | pass | Camera X trails for 2 load frames and by 1 px from 446448 (history from earlier acts); the later ship palette-latch correction is recorded in the [HPZ plan](../../plans/2026-09-16-hpz-bring-up.md); whole-scene parity remains open |
| BOSS: Knuckles fight, emerald theft, collapse | `CutsceneKnux_HPZ`; registry subtype `$28` → `CutsceneKnucklesHpzInstance` | positioned Sonic/Sonic+Tails; separately declared native fight seed | `TestS3kHpzKnucklesFightHeadless` (five cases), `TestS3kHpzKnucklesFightNativeSequence` (two cases) | implemented with native child graph and 47 routines | Existing HPZ selections record passing implementation/replay evidence; see the plan and current binding note below, not a new `7d0` execution | Positioned/seeded fixtures are not cold LRZ→HPZ campaign evidence; full configuration/native conformance remains open |
| ROUTE (Sonic/Tails): altar teleporter → `$A00` | `loc_45AD6`, `loc_45BF4`; `HpzTeleporterRouteHelperObjectInstance` and semantic full-save exit | positioned and fresh direct HPZ entry | `TestS3kHpzKnucklesFightHeadless#theftCollapseAndTeleporterEndingRunToSkySanctuary`, `#sonicAndTailsEndingMatchesTheRecordedTimers`; existing `TestS3kHpzColdRoutes` | implemented | Recorded HPZ fight/exit and direct-route qualification | Not one joined cold LRZ→HPZ→SSZ route; disk persistence and remaining configuration/native presentation breadth are separate |
| OBJECT: placed Master/Super Emeralds in `$1601` | `Obj_HPZMasterEmerald`, `Obj_HPZSuperEmerald` have no zone branch; states 1 and 2 are selectable (`loc_907A8`) | unit; native all-Super save | `TestHpzSanctuaryObjects#pedestalStatesUseRomCentrePositionsAndFourStateBehavior`; native `probe-altar` vs `raw-22-altar-all-super` | implemented (state-1 selectability corrected) | pass; visual match by inspection | Production pedestal special-stage entry from `$1601` not route-tested |
| LIFE: physical checkpoint `$34` sub2 at `$CF0,$3E8`, repeated death/reload | StarPost; `HPZ_ScreenInit` Knuckles `$AA0` limit | 80 preset Sonic/Tails/roster cases and5 Knuckles refusal cases;10 custom-width lifecycle and2 refusal cases | `TestS3kHpzLifecycleProduction#touchingThePlacedStarPostThenDyingReloadsAtThePost` and legacy geometry method | implemented | 97 cases pass: each applicable case makes real post contact, two deaths/full reloads, released-destination30-frame replay twice and actual outgoing history retirement | Knuckles refusal retained; immediate activation rewind, hazard-contact death and broader fight/ending lifecycle remain open |
| LOAD: LRZ3 → `$1601` incoming; Knuckles LRZ Act2 bypass | LRZ boss/end-level and direct character handoff owners | Sonic+Tails at 320px/Sonic+Tails at 800px/Tails at 320px/Knuckles at 320px | `TestLrzBossColdRouteCapture`, `TestLrzWideBossColdRouteCapture`, `TestLrzTailsColdRouteCapture`, `TestLrzKnucklesColdRouteCapture` | implemented | Current cold arrival at released HPZ: Sonic+Tails at 320px/62 windows, Sonic+Tails at 800px/71, Tails at 320px/62, Knuckles at 320px/89; owning LRZ matrices retain exact source/commands | Incoming checks stop at released HPZ; direct HPZ→SSZ rows above are independent |
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

Still open: joined cold LRZ→HPZ→SSZ campaigns, immediate physical-post activation
rewind and actual hazard-contact deaths; fight/theft/collapse/altar authority and
lifecycle products beyond the existing narrower fixtures; receiving-pad intangibility,
production pedestal special-stage entry and persistent-save roundtrip; synchronized
native phase/pixels/audio and complete viewport/donor/main/team traversal axes.
Seven followers do not establish a maximum; the production parser remains uncapped.
