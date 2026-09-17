# S3K Sky Sanctuary Zone act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_SKY_SANCTUARY_1`, engine zone `$0A` act index 0,
ROM `Current_zone_and_act = $A00`, SKL object set.
Character routes: Sonic, Sonic + Tails, Tails (`LevelSelect_CheckKnuckles` denies Knuckles except
with `Debug_cheat_flag != 0`, and no Knuckles art or route exists for act 1).
Owning plan: [SSZ bring-up](../../plans/2026-09-17-ssz-bring-up.md).
Status: in progress (slices 0, 1, 1b, 2, and part of 3). Nothing below certifies the act.

Incoming: HPZ teleporter altar ending → `$A00` (`HpzTeleporterRouteHelperObjectInstance`), level
select, save progression. Outgoing: `StartNewLevel $B00` from the Death Egg launch (`loc_581D2`);
DEZ presentation and route belong to the DEZ campaign.

Native fixtures (SSZ is filed under `hpz`; `zone_id 10` is the ROM zone. The one-time trace
directory identity table is owned by the LRZ campaign's edit to
[trace frontier log](../../../status/trace-frontier-log.md); it is referenced, not duplicated here):
`runs/s3k-sonic-tails-complete-emeralds/hpz` (7638 rows, `bk2_frame_offset` 448920, start
`$100,$FAE`), `…/hpz_2` (4352, 460334), `…/hpz_3` (3937, 465044);
`runs/s3k-tails-full-chain-all-emeralds/hpz` (6023, 423903), `…/hpz_2` (10582, 433476);
`hpz_completerun` (18641, 396720).

Five claims are tracked separately per row: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. There is no aggregate
green label, and "implemented" alone never closes a row.

## Obligations

| Obligation + spot | Contract / oracle (ROM owner) | Config cases | Test binding | Implemented | Cold-reachable | Rewind-verified | Native matched | Visually matched | Gap / action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LOAD: identity, resources, music, title card (no act number) | `Sonic3kZoneRegistry` zone 10; `ArtKosM_SSZTitleCard $15C7FA`; `Pal_SSZ1` `$32`, collision `$1E/$1F` | 320 | — | pre-existing | yes (cold load) | — | — | baseline capture `raw-00-ssz1-before` | Resource identity not yet asserted by a test |
| CENSUS: placed objects and rings | `SSZ1_Sprites $1F90EE` (213 records, 66 rows), `SSZ1_Rings $1F9616` (180 records, first `(0,0)`); `sub_1BA0C`/`loc_1BA4A`; `Load_Rings` `loc_E8BE` | ROM decode | `TestS3kSszPlacementCensus` | yes | n/a | n/a | yes (decode pinned to the ROM) | n/a | Concrete-class assertions arrive in slice 3 |
| CENSUS: wrap-seam cloud records | Two `$7D` records store Y `$103C`/`$104C`; the `& $FFF` mask puts them at `$03C`/`$04C` | ROM decode | `TestS3kSszPlacementCensus#theTwoWrapSeamCloudRecordsMaskAcrossTheSeam` | yes | n/a | n/a | yes | n/a | Their in-level behaviour is slice 3 |
| CENSUS: leading `(0,0)` ring record | `loc_E8BE` starts the scan at `max(Camera_X - 8, 1)`, so the record is always stepped over | ROM decode | `TestS3kSszPlacementCensus#ringRecordsMatchTheRomIncludingTheLeadingZeroRecord` | yes | n/a | n/a | yes | n/a | Engine window floor is `max(cameraX - 8, 0)`; gap filed in [s3k-known-bugs](../../../status/s3k-known-bugs.md). LRZ `3418eba6e` fixes it in shared `Sonic3kRingPlacement` and arrives at merge; the test asserts the ROM totals (179/0) and tolerates the sentinel only as a sentinel |
| ARRIVAL: no-starpost screen init and controller | `SSZ1_ScreenInit`, `Obj_57C1E`/`loc_57CAC`/`loc_57CD2`/`loc_57D3C`, `Obj_57D64`, `loc_57DA2` | 320 + 800; Sonic, Sonic + Tails | `TestS3kSszArrivalHeadless` | yes | yes (cold load) | not exercised | forced camera, `Camera_Y + $65`, the 8 px rise and the skipped final camera step all match; one-frame phase against `hpz` row 0 open | `01-ssz-arrival-beam-sonic-tails.mp4` | Tails-solo row and the wide rewind spot open |
| ARRIVAL: Tails helper and CPU routine | `Obj_57DCC`, `loc_13AB4` (`sub_13ECA`, `Tails_CPU_routine $A`, `object_control $83`) | Sonic + Tails, 320 | `TestS3kSszArrivalHeadless#theSidekickParksOffScreenUntilTheArrivalHelperReleasesHer` | yes | yes | not exercised | `($7F00,0)` park and in-air status match the fixture's row-0 sidekick sentinel | `01-ssz-arrival-beam-sonic-tails.mp4` | Her swing arc is not compared to native |
| BOUNDS: act-1 dynamic Y bounds and Y-wrap | `sub_575EA` `word_5778A`/`word_5779A`; wrap `-$100 … $1000` | 320 | `TestS3kSszKnucklesBridgeHeadless#theCutsceneReleasesTheBridgeAndOpensTheAct` | yes | yes (the band is asserted at the bridge release) | not exercised | band values asserted from the tables | — | The GHZ/MTZ lock branches are implemented but unreached until their bosses exist; wrap crossing is slice 4 |
| CUTSCENE: Knuckles spawner, button `$AF`, bridge `$77`, pseudo-starpost | `Obj_57E34` → `CutsceneKnux_SSZ` (11 routines `0..$14`), `loc_658F2`, `loc_65976`, `Obj_SSZCutsceneBridge` `loc_44FA2`/`loc_44FBA`/`loc_4501A` | 320 | `TestS3kSszKnucklesBridgeHeadless` | yes | yes: the bridge retracts 2 px/frame and clears `Events_bg+$05`, and a pre-set star post starts it extended with no arrival | not exercised | flag order, retract rate and the `($140,$C6C)` checkpoint match the routines; no native probe | `02a`/`02b` clips | Death Egg palette and children, and Knuckles' resting X, filed in s3k-known-bugs |
| BG: plain sky framing and the `$1800` latch | `sub_579F0` `loc_57A12`/`loc_57A30`/`loc_57A4C` | 320 | `TestS3kSszScrollBands` | yes | yes (the arrival opens in plain sky) | n/a (derived per frame) | offsets and the latch's re-rounding asserted from the routine | `04a-ssz-sky-and-clouds.mp4` | The engine reads `Camera_X/Y_pos`, not the `_copy` words; identical until the launch's screen shake |
| BG: cloud band, per-band deformation and drift | `sub_57A60`, `SSZ1_BGDeformArray`, `ApplyDeformation` | 320 + 800 | `TestS3kSszScrollBands`, `TestS3kSszBackgroundLayout` | yes | yes (the arrival rise crosses `$F00` into the band) | n/a | the thirty-word fan, the halved wrapped background Y, the seven visible bands at camera `($800,$E00)` and the `$500`/frame accumulator all asserted from `sub_57A60`; the background layout itself is decoded from the ROM and compared with the engine's layer column for column | `04a`/`04b` clips show the arrival band, which the ROM's layout makes flat sky | The flat-sky question is closed: layout rows 0-2 and 18-21 are a single repeated chunk and the arrival camera selects row 1. The structured band (rows 3-17, `Y $180`-`$8FF`) has not been captured yet — s3k-known-bugs #41 |
| BG: mode transition machine | `SSZ1_BackgroundEvent` routines 0/4/8/`$C`, `Events_bg+$0C`/`+$0E` | 320 | `TestS3kSszScrollBands` | yes | yes | yes (the whole state is in `SszZoneRuntimeState`) | routine order and the frozen framing asserted; the ROM's multi-frame `Draw_PlaneVertBottomUp` completes in one engine frame | `04a` | Recorded difference: the cloud bands appear one frame after the ROM starts filling them |
| BG: roaming clouds `loc_57BB2` and `sub_5758A` | five `word_58758` rows, `Gradual_SwingOffset($1C00,$80)`, the `$1FF`/`$FF` screen masks | 320 + 800 | `TestS3kSszBackgroundClouds` | yes | yes | yes (capture/restore + forward replay) | row count, drift, per-cloud RNG phase and the screen periods asserted from the routine | `04a`/`04b` | The `$1FF` period is screen-space, so a wide viewport shows the wrap seam inside the visible area |
| BG: cloud oscillator `_unkEE9C` and the ten solid clouds | `loc_57B6A`/`loc_57B76`, `loc_57B8E`, `word_5853E`, `SolidObjCheckSloped2` | 320 + 800 | `TestS3kSszBackgroundClouds` | yes | yes | yes | ten rows decoded from the ROM inside the test; `y_pos = y_vel - _unkEE9C` asserted for 240 frames | `04a`/`04b` | Riding one is not yet exercised by a route; eight of the ten rows over-read their own slope table into the next one, as the ROM does |
| ANIM: AniPLC (6 scripts), and none in act 2 | `AniPLC_SSZ` `$28AA4`; `Offs_AniFunc` entries 40-43 (`AnimateTiles_DoAniPLC` / `AnimateTiles_NULL`) | 320 | `TestS3kSszPatternAnimation` | yes | yes (runs from the cold load) | n/a (the animator's counters are snapshotted by `PatternAnimatorSnapshot`) | all six scripts' duration, destination tile, frame count and tiles per frame asserted from the declarations, including the third script's three frames | `04a` | No per-tile pixel comparison against native |
| OBJECT: `$79` SSZ pads (all ten act-1 placements) | `Obj_SSZHPZTeleporter` init, `loc_455BA`, `loc_455CC`-`loc_45790`, `loc_457BE`, `loc_4581C`, `sub_45866` | 320 | `TestS3kSszTeleporterPads` | yes | not yet: no cold route reaches a pad past the bridge | not exercised | lift `(subtype & $3F) * $10`, the gated-pad sink and 4-frame rise, the launch bounds/`Scroll_lock`/`Events_bg+$05` writes and `byte_466E8` all asserted from the routines; the gated-pad cases use a declared seeded `st (Events_bg+$00)` because slice 5's boss does not exist | open | Two cases seed a boss-defeat flag; the `($1A40,$670)` spawner draws but allocates no boss (s3k-known-bugs #42) |
| OBJECT: `$7F` floating platform (8 placements) | `Obj_SSZFloatingPlatform` / `loc_44AA0`; `SolidObjectTop` `d1 $2B`, `d2`/`d3` `$11` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | the 4-pixel dip and its one-pixel-per-frame ramp asserted from `loc_44AA0` | open | Wide-viewport and donor rows open |
| OBJECT: `$7E` collapsing column + debris (25 placements) | `Obj_SSZCollapsingColumn` / `loc_44B30`/`loc_44B90`, debris `loc_44BCC`/`loc_44BF8`, `word_46618` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | eight pieces from `word_46618` decoded in the test, the `routine(a0)` report count and the `$7FFF` park asserted from the routines | open | The `Random_Number` bob phase is drawn per column, so the column order of a load moves the RNG stream; not compared to native |
| OBJECT: `$74 $75 $76 $7A $7B $7C $7D`, EggRobo `$A0` | per-object inits | — | slice 3 (remaining) | open (placeholders) | open | open | open | open | 121 of the 154 slice-3 placements remain |
| LIFE: death, starposts `$34:$02/$03/$04`, respawn, Y seam | `LevelSetup` clears `Events_bg+$00..$0F`; `SSZ1_ScreenInit` starpost path | — | slice 4 | open | open | open | open | open | Slice 4 |
| BOSS: GHZ recreation lock / fight / defeat / pad `$79:$AA` | `sub_575EA` `loc_57686`-`loc_576E8`, `Obj_SSZGHZBoss` | — | slice 5 | open | open | open | open | open | Slice 5 |
| BOSS: MTZ recreation lock / fight / defeat / pad `$79:$F6` | `loc_5770C`-`loc_5775C`, `Obj_SSZMTZBoss` | — | slice 6 | open | open | open | open | open | Slice 6 |
| BOSS: Mecha Sonic spawn / fight / defeat, results + save | `loc_45A84`, `loc_7B2DC`, `loc_7B308`, `loc_2DCA0` | — | slice 7 | open | open | open | open | open | Slice 7 |
| EVENT: crumble, hot-swap, Death Egg BG, debris, ramp script, `$B00` request | `SSZ1_ScreenEvent` stages 0/4/8, `Obj_57E96`, `sub_5750C`, `sub_574DC`, `loc_58192`, `loc_581D2` | — | slice 8 | open | open | open | open | open | Slice 8 |
| LOAD: `$B00` (DEZ) presentation after the request | DEZ campaign | — | — | blocked | blocked | blocked | blocked | blocked | Out of scope; record what the engine does after the request |
| ORACLE: strict segment replay | `TestS3kSonicTailsHpz{,2,3}SegmentTraceReplay`, `TestS3kTailsFullChainHpz{,2}SegmentTraceReplay` | `-Ptrace-segments` | — | — | — | — | not measured | — | Slice 10 records each frontier |

## Execution evidence

Worktree `.worktrees/ai-ssz-bring-up`, branch `feature/ai-ssz-bring-up`, base develop `035e48a58`.
All Maven through `python3 tools/testing/maven_queue.py -Dmse=off …` with
`-Ds3k.rom.path=<absolute path to the worktree>/s3k.gen` (a symlink
to the locked-on ROM, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`).

Slice 0, 2026-09-17. `-Dtest=TestS3kSszPlacementCensus`: **7 tests, 0 failures, 0 errors, 0 skips**.
The comparison was broken on purpose first (expected 214 records) and reported
`expected: <214> but was: <213>` at the same skip count, so the census is live rather than absent.
Baseline captures (no SSZ events, scroll or objects yet):
`~/Videos/OGGF/ssz-bring-up/raw-00-ssz1-before` (Sonic, 320, 360 frames; level start `(256,3072)`
= `$100,$C00` from `LevelData`, camera `(96,2976)`) and `raw-00-ssz2-before` (Knuckles, 320, 360
frames; level start `(128,32)` = `$80,$20` from `Knux_Start_Locations`). Frames 200 of each were
inspected: act 1 renders the sanctuary terrain against a flat blue sky with no cloud background;
act 2 renders the static cloud layout. Neither is a fact about final behaviour.

Slice 2, 2026-09-17. `-Dtest=TestS3kSszScrollBands` 11 tests, `-Dtest=TestS3kSszPatternAnimation`
3 and `-Dtest=TestS3kSszBackgroundClouds` 6, all 0 failures and 0 skips; the combined run with
`TestEveryObjectRewindRoundTrip` and `TestRewindHarnessCoverageRatchet` was 1158 tests, 0 failures,
0 skips. The scroll comparison was broken on purpose with four perturbed ROM constants and came
back `Tests run: 11, Failures: 5`, naming the framing offsets, `HScroll_table word 21` and the
drift accumulator. The first rewind spot in this campaign lives here and caught two real defects:
a `$500` drift advance on a re-rendered frame and sixteen load-time sky objects being unloaded as
soon as they left the camera window.

Slices 1 and 1b, 2026-09-17, commit `686824e73`. `-Dtest=TestS3kSszArrivalHeadless` 6 tests and
`-Dtest=TestS3kSszKnucklesBridgeHeadless` 3 tests, both 0 failures and 0 skips, both seen red on
real defects first. Shared checks in the same tree
(`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`,
`TestSonic3kDecodingUtils`, `TestS3kHpz*`, `TestS3kDdz*`, `TestEveryObjectRewindRoundTrip`,
`TestS3kSsz*`): 1391 tests, 0 failures, 0 skips. `-Pguards test -B`: 669 tests, 0 failures, 0 skips.
This is focused validation, not a suite pass. Media: `raw-01-arrival-sonic-tails`,
`raw-02-knuckles-cutscene-bridge` (first attempt, kept) and
`raw-03-knuckles-cutscene-bridge-walk`, with clips `01`, `02a` and `02b`.

## Open items carried into later slices

- Engine ring-window floor admits the `(0,0)` record at `Camera_X <= 8` where the ROM does not.
- The act-1 `LevelData` start `$100,$C00` is never used on the no-starpost path: `SSZ1_ScreenInit`
  overwrites the camera and `Obj_57C1E` the player. Resolved in slice 1.
- The engine's arrival begins one frame later than fixture `hpz` row 0 implies, because the screen
  init runs from pre-physics of frame 1 rather than inside the level load. Values match exactly;
  the phase is slice 10's to settle.
- No rewind **spot** has been exercised on any SSZ object yet. Every new class does pass the
  generic capture/restore round trip in `TestEveryObjectRewindRoundTrip`, and
  `SszZoneRuntimeState` is captured, but nothing has been captured mid-arrival or mid-cutscene,
  restored and replayed forward.
- The pseudo-starpost's other half — die after the bridge and respawn at `($140,$C6C)` — is owed:
  slice 1b covers the respawn-finds-it-extended case, the death path belongs with slice 4.
- The Death Egg's `Pal_KnuxSSZEnd` patch, its missile and cloud children, and cutscene Knuckles'
  resting X are filed in [s3k-known-bugs](../../../status/s3k-known-bugs.md).
