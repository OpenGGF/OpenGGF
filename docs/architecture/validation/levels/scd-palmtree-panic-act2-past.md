# Sonic CD Palmtree Panic Act2 Past — partial coverage

Status: **live FULL entry and loading through controller13935, with selected terrain
and full/course replay qualified; ordinary objects and playable traversal remain open**. This follows the [level-test standard](../../../guide/contributing/level-test-standard.md)'s
partial matrix format; loading/decoding is not act certification.

Canonical stage is PPZ act2/Past, native file `R12B__.MMD` (`CdStagePayload.R12B`),
original USA programme. The live controller route completes WARP at13885, performs
one same-World FULL load at13886, and finishes target loading at13935. First13936
stops before ordinary selector8 and the physical object pass. The old R12A Frame
is retired; the original target pair and saved42 feed the actual new owner. USA-only aggregate
admission is an explicit development boundary in `CdR12bTerrainAssets.fromPrograms`;
JP/EU executable parity is unfinished, not inferred from shared source blobs.

## Source and executed prerequisites

Original MMD SHA256:
`14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64`.
The 25-descriptor profile, 19 PLC lists/116 rows/37 pointers, five sections and decoded
terrain are authenticated through the original user-supplied CUE/catalogue/MMD
pipeline. Native reference is scddisasm `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`;
source files provide research/hash oracles, never runtime replacement assets.

| Obligation | Contract/oracle | Executed evidence | Status |
| --- | --- | --- | --- |
| ENTRY data | Complete original USA identity, source ranges, palette/load-plan and placement format | `TestCdR12bData`, `TestCdR12bRetailData`; constructed malformed controls stay labelled | Data only |
| LOAD source readers | Exact37pointers in plan-directory encounter order; canonical blobs distinct from Java/native refill views | `TestCdR12bSectionSources.allThirtySevenDescriptorsHaveLiteralOriginalRangesCountsAndExpectedReaderMetadata`, `allNineteenNativeListsJoinThePointerKeyedRegistryWithoutChangingEncounterOrder` | Qualified prerequisite |
| LOAD source products | Shared Nemesis and native-style PLC decoder complete every finite view with independent output hashes/cursors | `sharedNemesisDecodesAllThirtySevenJavaViewsToLiteralHashesAndSeparateCursors`, `nativePlcDecoderCompletesAllThirtySevenFiniteViewsWithLiteralHashesAndCursors` | Qualified prerequisite |
| ORACLE EOF | Cursor-minus-one throws at real EOF, retaining only earlier complete tiles; no synthetic padding | `everyJavaCursorMinusOneIsAnActualEofRatherThanAZeroPaddedInput`, `nativeCursorMinusOneReachesFiniteEofWithOnlyEarlierCompleteTilesInTheProduct` | Qualified prerequisite |
| LOAD ownership | Original retained image survives borrowed-program close; immutable descriptors/views/cache; no public arbitrary image/Identity ingress | `lazyAggregateAndEqualRangeViewsShareCachesAfterTheBorrowedProgramCloses`, `publicDescriptiveRecordsCannotConstructAnAuthenticatedAggregate`, corruption and copy-isolation controls | Qualified prerequisite |
| Player data | Actual R12B mapping $230F60, 198 mapping/DPLC frames, 60 scripts, 69,472-byte bounded sheet, math and response bytes | `TestCdR12bPlayerAssets`, `TestCdR12bStagePlayerSource` | Qualified format; no target Body route |
| Explicit Frame | Source act1/Past0, actual Init/DPLC component, saved helper20-store path and nine Frame ports, original capture ownership | `TestCdR12bPlayerFrameEntry`, 17 cases | Qualified component; actual FULL entry also qualified below |
| Current/PLC facade | Eight sparse regions/1,614 tiles; 37-reader registry; queue, decoder, raw life/HUD and 1,152-byte shield upload preserve caller tail | R12B direct/current/section/HUD, 45 cases | Qualified component; no caller scheduling parity |
| Camera/plane | Actual ROM CloudLineCounts (8 bytes), native 3/16 Y/eight accumulators; explicit PPZ2Past shared-plane binding | R12B scroll (14) and plane (7), plus existing R11A (43), R11B (28) and R12A (8) plane cases | Qualified component and loading publication; ordinary camera route pending |
| Detached custody | Original USA pair retained while StockLaunch CUE is open; JP/EU capability explicitly absent, legacy constructors preserve omission | StockPreparation (36 cases), including the corrected exact 3/4/5/6 constructor inventory | Qualified data retention; no entry authority |
| Terrain format | Existing geometry/collision projection and old-shape behavior; independent original data/hashes | R12Bdata/retail plus `TestCdStageTerrain` | Format only; no ground-contact route |

The6November focused run passes76cases/0failures/errors/skips:11R12Aexit,
4existingpermanent controller cases,16source registry,13R12Bdata,12R12Bretail
and20oldterrain. Those prior-stage cases do not count as R12B gameplay. Source
fingerprint is `0d51dce6fa962470a04c2a985dce015135f9153b1ab276f7148a2f961a00f0af`;
original media and surfaceless EGL were explicit. The initial test-compile typo
was corrected before this successful run; no tests ran in that failed attempt.
The public Source record is descriptive; it grants no execution/current-art or
private invocation authority. Five Java and21native views need a real following
original byte. Research physical-order S labels are not runtime ordinals; runtime
order comes from the tested plan (HologramAnimals ordinal27).

Latest focused evidence is separate: the prerequisite selection executed 226 tests,
225 passed with one stale constructor inventory; its expected 227 total also mistakenly
counted nine R12A plane cases instead of the actual eight. The exact constructor
append was corrected, then 165 fresh cases passed with no failures, errors or skips, including
all 36 stock cases, 17 R12B Frame, 45 R12B art and 67 existing HUD/Frame regressions.
The original R12B player/scroll/reader and shared-plane cases passed in the earlier
selection. These are source-bound component receipts, not a single 355-case fleet,
R12B gameplay, native-console execution or regional parity.

## Live loading qualification — 6 November

`TestSonicCdAct2ControllerRoute.actualUnlockedAct2RightRouteReturnsThroughTheLastMeasuredClosedCutWithFullAndCourseReplay`
now continues the unchanged held-input suffix through the real target entry and
all49 loading services after entry. It verifies one new load generation, genuine
saved42, twenty native saved stores, original R12B Frame/source identity, actual
scroll/plane/current publication, queue completion and22 FromWhite services.
Every loading service has full and course restore-forward comparison of all
Core/Frame/Host values. First13936 raises `ORDINARY_OBJECT_PASS` before selector8;
ordinary Sonic Init, objects, SAT and loop tail have not been fabricated.

Three new actual-World cases in `TestSonicCdAct2TimeWarpExit` qualify FIRST/SECTION/
FROM_WHITE cuts, stale/foreign epoch rejection and genuine enabled terrain pixels
across full/course restore. Six caught graphics-FIFO controls reject service,
draw, close, afterTick, registry capture and scroll restore before mutation or
terminal failure closure. The native ClearScreen644-byte sprite clear includes
Core water-palette bytes0..3; its separate focused regression preserves124 tail
bytes. No second palette/current/Frame/scroll owner or rewind key is added.

`TestCdR12bObjectAssets` supplies fourteen passing cases for39 fixed views and19
visual roots covering twelve initial kinds. Actual ObjectTileIDs is72 bytes and
Swing root is20ECEC; legacy body DTO barriers remain explicit. Thirteen native
ObjNull aliases clear only the ID byte. A malformed test header expectation was
corrected from802E to independently hash-verified002E, without a production-data
change. Metadata presence grants no body or art-service execution authority.

Observed receipts remain separate:96 earlier component/placement cases passed;
57 corrected Core/Host/profile/display cases passed in the70 selection whose two
live-entry errors exposed the missing HOLD afterTick branch; thirteen fresh exit
cases then passed. The final eighteen-case selection (one permanent natural,
three live World/replay/EGL and fourteen metadata) passes with0F/0E/0S. Its exact
source fingerprint is `aebf81776b8d8a256d89a3026dba0b156f90ad376ad42abbbdef7150c9e8aceb`.
No single combined fleet, new screenshot, native/regional/audio or full-act parity
is inferred. The older broad Swing smoke remains incomplete.

## Remaining act obligations

| ID | Required next evidence | Current gap |
| --- | --- | --- |
| ENTRY | IPX/WARP/load dispatch, real saved pose, terrain/camera/control and post-entry player body | Actual private FULL/load/custody and saved/scroll publication qualify; ordinary primary Init/Main and selected Sonic remain pending |
| OBJECT | Actual stage mapping/placement/body bindings, contacts, children, despawn/re-entry | Batched metadata and source-bound placement qualify; no target ordinary physical walk yet |
| EVENT | Native stage gates, water/palette/background/terrain changes | Unimplemented target programme owners |
| CAMERA | Correct stage scroll/parallax/culling and width-sensitive behavior | Live loading scroll/plane publication qualifies; ordinary camera/culling/width-sensitive traversal remains pending |
| BOSS | Source inventory and route-specific applicability | Unassessed, not marked not-applicable |
| LIFE | Real checkpoint/death/restart and native persistence/reset | No target lifecycle |
| LOAD | Sparse current profile, actual section/PLC services, readiness, source replacement and next playable state | Actual source-ordered loading/PLCs/current selection qualify; ordinary8/HUD/slow-PLC/object services remain pending |
| REWIND | Nondefault target before/active/after mechanics and in-timeline loads with full/course replay | Target loading full/course and foreign-epoch controls qualify; ordinary mechanics/lifecycle timelines remain open |
| PRESENT | Actual target mapping/current/plane/palette/scroll pixels and audio ownership | Actual selected terrain framebuffer/replay qualifies; ordinary Sonic, native comparison and audio proof remain open |
| ROUTE | Real target entry through mandatory mechanics/goal/results/next state | Entry/loading only; ordinary route and completion remain open |
| ORACLE | Actual native timing/reference comparisons across supported configurations | Source/media/decoder facts only |

All viewport presets, supported donor/main/team combinations and their entry/
lifecycle/rewind cross-products remain unassessed for this target. Absence of an
implementation is a gap, not evidence that a configuration is unsupported. Keep
native320/Sonic development controls distinct from production breadth. Full USA
completion precedes JP/EU, and no percentage or whole-game parity claim follows.
