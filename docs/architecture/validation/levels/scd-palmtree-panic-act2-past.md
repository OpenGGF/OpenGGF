# Sonic CD Palmtree Panic Act2 Past — prerequisite matrix

Status: **data and finite-reader prerequisites only; no live entry or playable
route is qualified**. This follows the [level-test standard](../../../guide/contributing/level-test-standard.md)'s
partial matrix format; loading/decoding is not act certification.

Canonical stage is PPZ act2/Past, native file `R12B__.MMD` (`CdStagePayload.R12B`),
original USA programme. The current live route still owns R12A and stops before
its IPX/WARP destination bridge. It has not entered this stage. USA-only aggregate
admission is an explicit development boundary in `CdR12bTerrainAssets.fromPrograms`;
JP/EU executable parity is unfinished, not inferred from shared source blobs.

## Source and executed prerequisites

Original MMD SHA256:
`14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64`.
The24descriptor profile,19PLC lists/116rows/37pointers, five sections and decoded
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
| Terrain format | Existing geometry/collision projection and old-shape behavior; independent original data/hashes | R12Bdata/retail plus `TestCdStageTerrain` | Format only; no ground-contact route |

The6November focused run passes76cases/0failures/errors/skips:11R12Aexit,
4existingpermanent controller cases,16source registry,13R12Bdata,12R12Bretail
and20oldterrain. Those prior-stage cases do not count as R12Bgameplay. Source
fingerprint is `0d51dce6fa962470a04c2a985dce015135f9153b1ab276f7148a2f961a00f0af`;
original media and surfaceless EGL were explicit. The initial test-compile typo
was corrected before this successful run; no tests ran in that failed attempt.
The public Source record is descriptive; it grants no execution/current-art or
private invocation authority. Five Java and21native views need a real following
original byte. Research physical-order S labels are not runtime ordinals; runtime
order comes from the tested plan (HologramAnimals ordinal27).

## Remaining act obligations

| ID | Required next evidence | Current gap |
| --- | --- | --- |
| ENTRY | IPX/WARP/load dispatch, real saved pose, terrain/camera/control and post-entry player body | No target Frame/World or player capability retained by the live source |
| OBJECT | Actual stage mapping/placement/body bindings, contacts, children, despawn/re-entry | Metadata/placements alone do not run objects |
| EVENT | Native stage gates, water/palette/background/terrain changes | Unimplemented target programme owners |
| CAMERA | Correct stage scroll/parallax/culling and width-sensitive behavior | No target scroll or retained plane pipeline |
| BOSS | Source inventory and route-specific applicability | Unassessed, not marked not-applicable |
| LIFE | Real checkpoint/death/restart and native persistence/reset | No target lifecycle |
| LOAD | Sparse current profile, actual section/PLC services, readiness, source replacement and next playable state | The37registry is not a kernel facade/residency/World; PLC union excludes direct HUD/life/player writes |
| REWIND | Nondefault target before/active/after mechanics and in-timeline loads with full/course replay | No target timeline; R12Aexit replay is a separate stage |
| PRESENT | Actual target mapping/current/plane/palette/scroll pixels and audio ownership | No target framebuffer, native comparison or audio proof |
| ROUTE | Real target entry through mandatory mechanics/goal/results/next state | No target route |
| ORACLE | Actual native timing/reference comparisons across supported configurations | Source/media/decoder facts only |

All viewport presets, supported donor/main/team combinations and their entry/
lifecycle/rewind cross-products remain unassessed for this target. Absence of an
implementation is a gap, not evidence that a configuration is unsupported. Keep
native320/Sonic development controls distinct from production breadth. Full USA
completion precedes JP/EU, and no percentage or whole-game parity claim follows.
