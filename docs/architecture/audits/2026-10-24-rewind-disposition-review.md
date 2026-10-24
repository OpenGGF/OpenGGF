# Rewind field dispositions and provider ownership

This audit starts at `0b2c3c2a9127b080137b231ba7e7cfd9e3b1e164` on
`develop`. The implementation worktree is
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-79dc9020`, on
`bugfix/ai-rewind-disposition-review-20261024`. The parent owns integration,
release prose, combined S3K checks, and broad validation.

The baseline contains 23 field keys before this change. A final collection is
not proof of reconstruction. A central `TRANSIENT` policy is justified here only
when the constructor, child recreation, settled restore callback, or first
forward dispatch rebuilds the required value. It does not certify the entire
object or its ROM parity. Mutable helper state and missing render recipes remain
debt. No object lifecycle, respawn flag, public API, or singleton ownership changes
are part of this slice.

## All 23 field keys

Names below are relative to `com.openggf.game`. The source paths identify the
owning implementation, including nested classes. Closed rows name tests run
explicitly in this slice. Unresolved rows also
identify existing checks and their limits from source review. The validation
selections state which checks ran. An isolated sweep label or an unresolved
obligation is not a passing test claim.

| # | Field | Reconstruction and disposition | Evidence or remaining obligation |
| --- | --- | --- | --- |
| 1 | [`sonic1.objects.Sonic1RingInstance#childRingSpawns`](../../../src/main/java/com/openggf/game/sonic1/objects/Sonic1RingInstance.java) | Retained. The layout constructor expands a ring group, but `recreateForRewind` takes the child constructor and creates an empty list. The state enum is captured separately. | Prove restoration before the INIT dispatch can still expand the exact group, then replay spawning and collection with canonical RingManager identities. Post-spawn scalar or child-ring tests do not settle that boundary. |
| 2 | [`sonic1.objects.bosses.FZPlasmaLauncher#activeBalls`](../../../src/main/java/com/openggf/game/sonic1/objects/bosses/FZPlasmaLauncher.java) | Closed with central `TRANSIENT`. Each recreated FZPlasmaBall adopts itself through `adoptPlasmaBallForRewind`; the launcher prunes destroyed entries and derives its active count. The native encounter has one launcher group. | [`TestS1FzBossGraphRewind.fzBossGraphRestoresChildrenLauncherBallsAndParentSideLinks`](../../../src/test/java/com/openggf/game/rewind/TestS1FzBossGraphRewind.java) removes children, creates a divergent launcher/ball, restores fresh identities, checks the exact list and count, then advances the launcher without duplicating balls. |
| 3 | [`sonic1.objects.bosses.Sonic1FalseFloorInstance#childBlocks`](../../../src/main/java/com/openggf/game/sonic1/objects/bosses/Sonic1FalseFloorInstance.java) | Closed in the bounded follow-up below with central `TRANSIENT`. Recreation rebuilds the surviving managed inventory. Dispatch resolves the captured original `blockIndex` instead of a compacted list position, and advances the native break count on each timer borrow. | [`TestFalseFloorBlockRewindGenericRestore.partialCollapseReplaysThroughCleanupAfterDestructiveRestore`](../../../src/test/java/com/openggf/game/sonic1/objects/TestFalseFloorBlockRewindGenericRestore.java) fails on the original runtime in all four partial-collapse cases and passes with the fix. An event-style dynamic master and its surviving children restore as fresh objects with exact identities and order. Every forward timer, width and fragment state matches uninterrupted execution through master cleanup and fragment retirement. The complete eight-child test remains green. |
| 4 | [`sonic1.objects.bosses.Sonic1SLZBossInstance#seesaws`](../../../src/main/java/com/openggf/game/sonic1/objects/bosses/Sonic1SLZBossInstance.java) | Closed with central `TRANSIENT`. Recreation starts the list empty. `updateBossLogic` calls `scanForSeesaws` before consuming the cache on the next forward dispatch. | [`TestS1SlzBossSpikeballGraphRewind.restoreRebuildsTheBossSeesawScanCacheSoAlignmentCanStillMatch`](../../../src/test/java/com/openggf/game/rewind/TestS1SlzBossSpikeballGraphRewind.java) forces fresh reconstruction and verifies that the next update repopulates the cache. Other tests in the class check spikeball origin matching rather than first-live-seesaw matching. This is bounded encounter evidence, not a full SLZ route replay. |
| 5 | [`sonic2.objects.BreakableBlockObjectInstance$BreakableBlockFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/BreakableBlockObjectInstance.java) | Closed by removing the redundant list and capturing an immutable `FragmentArt(artKey, piece)` value through the existing default compact schema. A null piece selects the existing frame fallback; a null recipe preserves artless construction. The piece constructor now requires explicit art provenance instead of a live renderer. | [`TestS2BreakableBlockFragmentGraphRewind.pieceFragmentRestoresItsDrawBeforeAndAfterMovement`](../../../src/test/java/com/openggf/game/rewind/TestS2BreakableBlockFragmentGraphRewind.java) checks exact nonempty submissions after destructive registry restore, immediately and after motion, through a replacement renderer. `frameFallbackPreservesArtPresenceAndRetirement` checks frame 3, artless construction and retirement replay. See the S2 fragment follow-up below for baseline failures and limits. |
| 6 | [`sonic2.objects.OOZLauncherObjectInstance$LauncherFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/OOZLauncherObjectInstance.java) | Closed by removing the redundant list and capturing an immutable art-key/piece recipe. The production owner supplies the orientation-specific art key, and drawing resolves the current renderer. No renderer or inferred coordinate match enters the snapshot. | [`TestS2LauncherGraphRewind.producedFragmentsRestoreTheirDrawForBothOrientations`](../../../src/test/java/com/openggf/game/rewind/TestS2LauncherGraphRewind.java) breaks real managed parents for both orientations, captures before and after child motion, replaces the renderer, and checks exact submissions, piece zero, identities and allocation slots after destructive registry restore. `fragmentRestoresTheLatchedOffscreenRetirement` checks a custom art key and next-step deletion from a restored offscreen latch. See the S2 fragment follow-up below. |
| 7 | [`sonic2.objects.SmashableGroundObjectInstance$SmashableGroundFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/SmashableGroundObjectInstance.java) | Closed with central `TRANSIENT`. The captured spawn packs frame and piece indices. Recreation fetches the matching sheet and renderer from `ObjectServices.renderManager()` and builds the one-piece list. | [`TestS2DebrisFragmentGraphRewind.smashableGroundFragmentRecreatesItsNonemptyMappingPieceAndReplaysTheDraw`](../../../src/test/java/com/openggf/game/rewind/TestS2DebrisFragmentGraphRewind.java) uses frame 1/piece 1 with a decoy, distinct offsets, dimensions, tile, flips, palette, and priority. It removes the source, verifies the exact nonempty draw after reconstruction, and compares the next moving draw with uninterrupted execution. This checks renderer input, not GPU pixels or every art variant. |
| 8 | [`sonic2.objects.badniks.RexonBadnikInstance#heads`](../../../src/main/java/com/openggf/game/sonic2/objects/badniks/RexonBadnikInstance.java) | Closed with central `TRANSIENT`. Each head captures optional exact owner and next-head identities. The settled callback rebuilds the live inventory by owner and captured head index. It never compacts a missing chain slot. | [`TestS2RexonSolOwnershipRewind.progressedRexonFamiliesRestoreExactOwnersSparseChainsAndDeathDropScope`](../../../src/test/java/com/openggf/game/rewind/TestS2RexonSolOwnershipRewind.java) destructively restores two nearby families after native neck movement and interior-head retirement, checks exact identities and inventories, replays every surviving head, and scopes attack-triggered death drop to its original family. The retired-parent check tolerates absent owner and next-head identities. |
| 9 | [`sonic2.objects.badniks.SolBadnikInstance#fireballs`](../../../src/main/java/com/openggf/game/sonic2/objects/badniks/SolBadnikInstance.java) | Closed with central `TRANSIENT`. Each fireball captures its optional exact owner identity. The settled callback rebuilds the live inventory, including flying fireballs that retain their parent until unload. | [`TestS2RexonSolOwnershipRewind.solFamiliesRestoreMixedFlyingInventoriesCountersAndUnloadDetachment`](../../../src/test/java/com/openggf/game/rewind/TestS2RexonSolOwnershipRewind.java) advances real detection, animation, orbit and flight near another family, retires one child, restores fresh owners, compares exact inventories and forward movement, checks subsequent launch counters, and verifies family-specific unload detachment. The retired-parent flying check rejects adoption by a nearby live Sol. |
| 10 | [`sonic2.objects.bosses.CPZBossPipe#segments`](../../../src/main/java/com/openggf/game/sonic2/objects/bosses/CPZBossPipe.java) | Closed with central `TRANSIENT`. CPZBossPipeSegment recreation calls `reregisterRestoredSegment` on the recreated pipe. | [`TestS2CpzBossGraphRewind.cpzBossGraphRestoresWithoutDropsDoublesOrStaleReferences`](../../../src/test/java/com/openggf/game/rewind/TestS2CpzBossGraphRewind.java) destructively replaces the graph and checks stable identities, fresh objects, parent links, and the pipe's exact live segment list. Its representative graph has one segment; sparse multi-segment retract ordering remains outside this check. |
| 11 | [`sonic3k.objects.AizPlaneIntroInstance#activeWaves`](../../../src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java) | Retained. `tickWaveSpawn` appends managed waves. Child recreation restores parent links, but does not rebuild this parent list. The list is currently appended and cleared on unload, rather than read by the gameplay dispatch. | [`TestS3kAizIntroGraphRewind.aizIntroPlaneAndWaveChildrenRestoreFreshWithRestoredParentRefsAndScalars`](../../../src/test/java/com/openggf/game/rewind/TestS3kAizIntroGraphRewind.java) checks managed children and their parent links, not this list. Prove retirement/unload behavior and the absence of a live list consumer across the intro boundary before an explicit diagnostic-only exclusion. |
| 12 | [`sonic3k.objects.AizPlaneIntroInstance#emeralds`](../../../src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java) | Retained. Emerald scattering appends managed children; unload clears the list. Recreation does not repopulate it. | The AIZ intro graph fixture does not exercise the seven scattered emeralds and their retirement boundary. Add that graph and a destructive restore/forward run before excluding the parent inventory. Emerald glow-child tests cover a different class. |
| 13 | [`sonic3k.objects.CutsceneKnucklesLbz2Instance#supportPillars`](../../../src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesLbz2Instance.java) | Closed in the follow-up below by removing the history list. The public accessor resolves the exact captured support identity through the live managed query; retired supports and slot/coordinate replacements are absent. Scenery retains independent lifetime. Both observations are empty when services are unavailable. | [`TestLbzKnucklesInventoryRewind`](../../../src/test/java/com/openggf/game/rewind/schema/TestLbzKnucklesInventoryRewind.java) destructively restores fresh and reused managed pillars at initialization, moved swing, pre-fling and freefall boundaries, checks fresh instances/exact IDs/slots, then compares 220 forward frames. Retirement/reuse cases prevent resurrecting the pillar or adopting its replacement. See the focused results below. |
| 14 | [`sonic3k.objects.CutsceneKnucklesLbz2Instance#swingChildren`](../../../src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesLbz2Instance.java) | Closed in the follow-up below by removing the history list and observing live managed children by exact parent identity/native subtype. Pure child shells preserve child-before-owner capture order; compact capture restores native parent3 and the original swing anchor. The exact parent key uses the private optional-reference mode: absent capture tables preserve current links; present tables encode rejected/retired identities as authoritative null. | [`TestLbzKnucklesInventoryRewind`](../../../src/test/java/com/openggf/game/rewind/schema/TestLbzKnucklesInventoryRewind.java) proves four exact child identities/slots/links with a nearer competing cameo and full frame-by-frame fling/freefall replay. Partial-child retirement and both destroyed-unswept/removed-owner boundaries preserve absence, orphan expiry, and the unrelated graph. The actual compact-policy audit and disposition guard passed below. |
| 15 | [`sonic3k.objects.IczFreezerObjectInstance$FrozenPlayerBlock#debris`](../../../src/main/java/com/openggf/game/sonic3k/objects/IczFreezerObjectInstance.java) | Retained. `spawnDebris` records managed IceDebris; FrozenPlayerBlock recreation starts an empty inventory. Current reads are the testing accessor, but the deletion and auxiliary-sidekick paths have distinct lifetimes. | [`TestS3kIczFreezerGraphRewind.iczFreezerGraphRestoresWithoutDropsDoublesOrStaleReferences`](../../../src/test/java/com/openggf/game/rewind/TestS3kIczFreezerGraphRewind.java) checks cloud/block/player graph links. It does not certify this nonempty debris inventory after the block breaks. Exercise the native and auxiliary-sidekick debris boundaries, including retirement and forward replay. |
| 16 | [`sonic3k.objects.IczMinibossInstance#childComponents`](../../../src/main/java/com/openggf/game/sonic3k/objects/IczMinibossInstance.java) | Closed through actual compact capture in the bounded ICZ follow-up below; no exclusion. Constructible helper-array elements and a final, preconstructed camera gate keep the existing inherited `CAPTURED` policy reachable. | [`TestIczMinibossGraphRewind`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestIczMinibossGraphRewind.java) exercises the real manager/registry with fresh reconstruction at the camera gate, progressed orbs and pending defeat workers. [`TestIczMinibossFieldDisposition`](../../../src/test/java/com/openggf/game/rewind/schema/TestIczMinibossFieldDisposition.java) requires actual capture of the helpers, gate and inherited list. Native production leaves that list empty; orbs/shards are local helpers, and the explosion controller is a separately managed object. See the follow-up for validation and limits. |
| 17 | [`sonic3k.objects.Lbz2RobotnikShipInstance#spawnedChildren`](../../../src/main/java/com/openggf/game/sonic3k/objects/Lbz2RobotnikShipInstance.java) | Closed in the bounded ship follow-up below with central `CAPTURED`. The existing identity collection codec restores the registered child inventory in order. Admission excludes failed allocations; camera/flame unload hooks remove retired identities, and settled restore reconnects those children to the exact captured ship. | [`TestLbz2RobotnikShipRewind`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestLbz2RobotnikShipRewind.java) reproduces allocation before flame removal on the original runtime. Five managed graph cases cover destructive restore and release replay, exact slots/identities, camera retirement before capture, capture after flame retirement, overlapping owners, and tolerant missing-parent recreation. The existing 13 cameo checks remain green. |
| 18 | [`sonic3k.objects.LbzMinibossBoxKnuxInstance#wallExplosions`](../../../src/main/java/com/openggf/game/sonic3k/objects/LbzMinibossBoxKnuxInstance.java) | Retained gameplay debt. The list owns mutable S3kBossExplosionController instances; `tickWallExplosions` advances them and DONE waits for completion. Recreating an empty list changes completion and emitted effects. | [`TestS3kLbzMinibossGraphRewind.knucklesMinibossesRestoreFreshWithRestoredBoxParentRefsAndScalars`](../../../src/test/java/com/openggf/game/rewind/TestS3kLbzMinibossGraphRewind.java) does not populate and restore these controllers. Capture nonempty controllers with pending explosions, verify RNG/effect consumption, and replay DONE before closing this key. Managed-child identity codecs alone cannot capture these helpers. |
| 19 | [`sonic3k.objects.LbzMinibossInstance#panels`](../../../src/main/java/com/openggf/game/sonic3k/objects/LbzMinibossInstance.java) | Retained gameplay debt. PanelState is a mutable inner helper with motion, callback, timer, detach, and deletion fields. The list is rebuilt during initialization, but its progressed state is not reconstructed from scalar parent fields. | The LBZ miniboss graph check covers the Knuckles box-parent path, not the Sonic/Tails panel ring. Capture a progressed nonempty ring, alter its timers and detach state, recreate, and compare drawing, collision, and forward dispatch. A new helper-state contract needs separate design evidence. |
| 20 | [`sonic3k.objects.LightningSparkObjectInstance#sparkTiles`](../../../src/main/java/com/openggf/game/sonic3k/objects/LightningSparkObjectInstance.java) | Retained rendering obligation. `forRewindRecreate` fetches the shield art set from the current Sonic3kObjectArtProvider. A final Pattern array is mutable render data, not proof that the exact resident patterns were recovered. | Verify nonzero pattern content and matching animation mapping under a real art provider before removal, immediately after reconstruction, and during forward drawing. Null-art isolated passes do not establish that result. No runtime assets are loaded from disassembly files. |
| 21 | [`sonic3k.objects.badniks.CaterkillerJrHeadInstance#bodySegments`](../../../src/main/java/com/openggf/game/sonic3k/objects/badniks/CaterkillerJrHeadInstance.java) | Closed with central `TRANSIENT`. `afterRewindRestoreSettled` rebuilds the list only after each body's captured owner identity resolves, filters live bodies belonging to this exact head, and sorts by captured segment index. | [`TestS3kBadnikChildGraphRewind.caterkillerJrHeadRestoresBodySegmentListToRestoredChildren`](../../../src/test/java/com/openggf/game/rewind/TestS3kBadnikChildGraphRewind.java) restores two adjacent heads and twelve fresh bodies, checks exact parent inventories, rejects stale pre-restore references, restores changed indices/scalars, and drops a divergent extra head. This is exact graph evidence rather than an isolated scalar pass. |
| 22 | [`sonic3k.objects.bosses.LbzEndBossInstance#launchedSpikeBallRomSubtypes`](../../../src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzEndBossInstance.java) | Retained diagnostic-history obligation. `launchSpikeBall` appends ROM subtypes, but gameplay selection uses the separately captured `launchCount` and table. The public test accessor reads the history. Reconstruction initializes a new empty history. | `TestLbzEndBossInstance` checks native spawn behavior but contains no destructive rewind test for this history. Establish the accessor's intended rewind contract and verify current-child subtype/launchCount replay before an explicit diagnostic-only exclusion. Do not fabricate past launches from surviving children. |
| 23 | [`sonic3k.objects.bosses.LbzFinalBoss1Instance#spawned`](../../../src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss1Instance.java) | Retained graph inventory obligation. The list stores SpawnRecord values with child kinds and references. It is used to initialize nested turret children when unmanaged and to expose kind-specific inventories. Generic recreation does not restore historical records. | `TestLbzFinalBoss1Instance` checks choreography but does not invoke registry capture/restore despite being named by the isolated sweep. Require a managed graph restore with exact kind/child relations and nested turret/retirement replay, and separately define the unmanaged-owner contract. |

## Three static-state entries

`GameModuleRegistry#missingRewindAdapter` remains the only static baseline
exclusion. The registry is a bootstrap compatibility default, not a permanently
fixed global game choice. `getCurrent()` first resolves the active WorldSession
through SessionManager. `setCurrent()` changes only the bootstrap fallback.
[`TestSessionManager.gameModuleRegistry_setCurrentDoesNotReplaceActiveSessionModule`](../../../src/test/java/com/openggf/game/session/TestSessionManager.java)
checks that distinction. Rewind belongs to the captured world lifetime. It must
not restore a module selection from another world. The usage guard also rejects
new direct gameplay reads of the compatibility registry.

Sonic2SpecialStageManager and Sonic3kSpecialStageManager are not excluded because
special stages are outside LEVEL. Both providers support rewind and return a
provider-owned adapter under the shared special-stage key. The adapters capture
manager state and the native pacing owner. GameLoop initializes the provider
before registering it with GameplayModeContext. The context removes an old
adapter before replacing it; mode exit deregisters the special-stage segment.

`StaticStateRewindCoverageAnalyzer.PROVIDER_OWNERS` now declares those two
provider relationships explicitly as class-name metadata. It adds no concrete
game dependency to the shared analyzer. This avoids relying on the lexical
`ManagerName.` heuristic, which cannot see instance delegation. The declaration
alone is not proof of registration. The two ROM-backed methods in
`TestSpecialStageProviderRewindOwnership` enter through GameLoop, capture the
initialized registry, require the module service and provider to share the exact
manager, mutate actual manager state, restore it, replay forward,
replace the provider on re-entry, verify the old manager is untouched, and exit before
reset. `TestStaticStateRewindCoverageBaseline` checks the declared inventory and
negative stale-exemption cases. The static guard now fails stale baseline keys
instead of only logging them.

The remaining legacy static-adapter source scan is still heuristic. This task
does not claim to prove registration or lifetime for every adapter it recognizes.
The new ownership declaration is limited to the two reviewed provider managers.

## Decisions and limits

The accepted change records proven reconstruction in the existing central policy
map. It does not add a broad structural-container rule or synthetic snapshot
values. The inherited IczMiniboss capture-path gap, mutable LBZ helper state,
missing fragment render recipes, and unproved art reloads remain visible.
Nearest-owner reconstruction is not promoted to exact multi-family identity
coverage merely because the single-family graph test passes.

The isolated round-trip sweep's named graph exemptions do not execute those
tests. The focused selections below invoke the relevant evidence classes
explicitly. Some named classes contain choreography tests without any registry
restore; the table records that limitation instead of certifying them as graph
coverage.

## Independent review correction

Review of `46d62d09dfe726a1de4539e061004a69ee6a14be` found the false-floor
partial-collapse case described in row 3. The coordinator confirmed that
`reattachChildBlock` compacts survivors while `updateDisintegrating` indexes by
`currentFrame`. The new transient policy hid an existing obligation and was
removed before integration. Keep the eight-child test as bounded evidence; it
does not certify partial collapse. The other five policies and provider-owned
coverage had no review blockers. Final composed guard results belong in the
[delivery plan](../plans/2026-10-24-architecture-followthrough.md).

## Validation

The first request was terminated during queue admission by the T3 speed-setting
correction. It created no target directory and ran no build or test. It is not
counted as a validation run. The replacement request uses JDK 25, Lua 5.4,
`surfaceless-egl`, and unset DISPLAY, WAYLAND_DISPLAY, JAVA_TOOL_OPTIONS, and
JDK_JAVA_OPTIONS. Tests use a recording audio backend or headless fixtures.
The three supplied absolute ROM paths match the CRC32 and SHA-1 identities in
AGENTS.md. No ROM is copied or linked here.

The first completed selection ran 54 tests. It had one failure, zero errors,
and zero skips. The new smashable-ground draw test incorrectly used inherited
`getX()` and `getY()` as the fragment's live render coordinates. This class draws
from its separately advanced `currentX` and `currentY`; those base getters still
read the spawn coordinates. The failure occurred on the uninterrupted draw,
before restore. The corrected test asserts the independent one-step draw
coordinates `(0x13F, 0x7E)` from initial `(0x140, 0x80)` and the supplied velocities
and gravity. Runtime code did not change in response. The other 53 tests passed,
including both actual initialized provider lifecycles and both baseline guards.
Queue waiting was 502 seconds; Maven reported 85 seconds of execution including
both compilation phases. Parallel process durations are not added to these.

The worker's pre-review field report was inspected. It contained exactly the 17
retained keys above, including generic fallback for IczMiniboss childComponents
and LBZ Knuckles wallExplosions. The static guard accepted exactly the single
module-selection exclusion. A constant-pool check of the compiled analyzer found
no concrete per-game class references after replacing the initial class-valued
ownership map with string metadata. No architectural allowance was added.

Every Maven command below uses this environment. The absolute ROM properties
are supplied on each invocation. `ROM_ROOT` below substitutes the supplied
existing ROM directory without committing a machine-local home path.

```bash
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
export LUA_BIN=/usr/bin/lua5.4
export ROM_ROOT=/path/to/existing/OpenGGF-checkout
```

First completed selection, with the test-only coordinate failure described above.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestSpecialStageProviderRewindOwnership,TestStaticStateRewindCoverageBaseline,TestStaticStateRewindCoverageGuard,TestRewindFieldDispositionGuard,TestRewindFieldDispositionAudit,TestRewindPolicyRegistry,TestS1FzBossGraphRewind,TestS1SlzBossSpikeballGraphRewind,TestS3kBadnikChildGraphRewind#caterkillerJrHeadRestoresBodySegmentListToRestoredChildren,TestFalseFloorBlockRewindGenericRestore,TestS2CpzBossGraphRewind,TestS2DebrisFragmentGraphRewind' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The corrected render test and remaining named evidence passed in the following
selection. It ran 130 tests with zero failures, errors, or skips, including all
eight TestSingletonLifecycleGuard methods. Queue waiting was 199 seconds; Maven
reported 29.627 seconds of execution. Previously passing production checks were
not repeated because the only code edit after the first run corrected that test's
expected coordinates. Inspection of the final per-class XML summaries confirms
180 distinct tests across 26 selected classes, all passing with zero skips. These
are the union of the two selections, not a full-suite result.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS2DebrisFragmentGraphRewind,TestS2BadnikChildGraphRewind,TestS2BreakableBlockFragmentGraphRewind,TestS2LauncherGraphRewind,TestS3kAizIntroGraphRewind,TestS3kIczFreezerGraphRewind,TestS3kLbzMinibossGraphRewind,TestLbz2RideCameoInstances,TestLbzEndBossInstance,TestLbzFinalBoss1Instance,TestSessionManager,TestGameLoopSpecialStageRewindBoundary,TestGameplayModeContextSpecialStageRewindAdapter,TestSingletonLifecycleGuard,TestGameModuleRegistryUsageGuard' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

Documentation checks matched all 23 original keys to the table, verified every
source link and named method, and passed `git diff --check`. The worker initially
removed six keys, leaving 17. Independent review rejected the false-floor closure
and the coordinator restored that original key. That review boundary had five
accepted policies and 18 remaining obligations. The baseline remains a strict
subset of the original 23 keys; no new debt is accepted. Consumed
Surefire reports and the generated field report are deleted after inspection;
no category run or retained validation receipt was created.

No full suite, combined S3K selection, or broad smoke run belongs to this
delegate. These are focused checks and do not certify the retained obligations.
The parent still owns composed validation, integration, release prose, push, and
worktree cleanup.

## False-floor partial-collapse follow-up

This bounded follow-up starts at `85d32cda4fe333884f30e204749a3f5ddeef5923`
on `bugfix/ai-falsefloor-rewind-20261024`. The parent retains integration,
combined guards, release prose, known-bug removal and the local level-coverage
obligation note.

FFloor_MakeBlock (_incObj/82, 83 SBZ Eggman Cutscene and Crumbling Floor.asm:232-247)
writes eight pointers into original positions.
FFloor_Break (_incObj/82, 83 SBZ Eggman Cutscene and Crumbling Floor.asm:284-296)
uses the original break count to read that table and increments the count on
each timer borrow. It never compacts the table after a block breaks.
ObjectManager recreates the master before dynamic children and attaches each
child before applying its captured scalar fields. Sorting by a placeholder
`blockIndex` during attachment cannot recover original positions.

Two data shapes were compared. A fixed eight-position table preserves native
positions directly, but rebuilding its holes requires another step after scalar
restore. The selected inventory keeps surviving managed children and resolves
the target by captured `blockIndex` during dispatch. It needs no additional
restore callback or captured graph format. The counter still advances on the
ROM's byte-timer borrow. The shipped `FixBugs = 0` behavior, fragment generation
and existing deletion contracts remain unchanged.

The new parameterized
`TestFalseFloorBlockRewindGenericRestore.partialCollapseReplaysThroughCleanupAfterDestructiveRestore`
failed on unchanged runtime source at the base above. Cases `(1,0)`, `(1,5)`,
`(4,9)` and `(7,0)` capture retired-block count and additional timer ticks.
Their first differing forward ticks, counted from zero, were 17, 12, 9 and 18.
For `(1,0)`, expected survivors were indices 2 through 7, while restored execution
left indices 1 and 3 through 7. All four cases also failed
`restored master must complete cleanup`, expected true but observed false after
200 forward ticks. The original eight-child test passed. The completed baseline
selection ran five tests with four failures, zero errors and zero skips.

The regression uses an event-style dynamic master, production ObjectManager
updates and destructive registry restore with in-place reuse disabled. It
compares fresh survivor identities and ordering, the rebuilt inventory, every
forward timer and width, fragment identities, mapping frames, positions,
subpixels and velocities through master cleanup and fragment retirement.

The final focused selection passed all 39 tests across eight classes with zero
failures, errors or skips. The XML reports confirm all four parameter cases and
the exact balance-width guard method ran. The generated disposition report
matched the 17 remaining baseline keys exactly, with the false-floor key absent.
This accepts six closures from the original 23 fields. The final Maven request
waited 170 seconds for admission; Maven reported 77 seconds of execution,
including compilation. These times are separate from the earlier baseline
request, which waited 287 seconds and reported 104 seconds of execution.

The commands ran in this worktree through the updated main-checkout wrapper by
absolute path. `MAIN_CHECKOUT` and `ROM_ROOT` below stand for the existing main
checkout and existing ROM directory. Both invocations used Java 25.0.5,
Lua 5.4.9, unset DISPLAY and WAYLAND_DISPLAY, and the same absolute ROM properties.
The baseline command used only `-Dtest=TestFalseFloorBlockRewindGenericRestore`;
the successful selection was exactly:

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestFalseFloorBlockRewindGenericRestore,TestS1ScrapEggmanGraphRewind,TestS1FalseFloorFragmentRewind,TestRewindFixS1InnerBatch1Codecs,TestS1BalanceWidthRomParityGuard#everySolidObjectProviderAccountsForItsRomActWid,TestRewindFieldDispositionGuard,TestRewindFieldDispositionAudit,TestRewindPolicyRegistry' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The consumed XML, text reports and generated field report were inspected and
removed. No category run or validation receipt was created. These focused checks
do not certify rendered output, player riding-contact cleanup or the complete
SBZ2 route. No broad or full suite was run.

## S2 fragment art follow-up

This bounded follow-up starts at `624e95b4892533d23ae898d8127f277038777f70`
in `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-44afbf86`, on
`bugfix/ai-s2-fragment-rewind-art-20261024`. It owns only the two S2 fragment
implementations, their graph tests, these audit rows, and the two removed baseline
keys. The parent retains release prose, guide updates, combined guards, S3K and
API checks, integration, and cleanup.

The first reproduction changed tests only. Both Breakable Block cases reached
destructive registry restore and failed the exact nonempty `drawPieces` check.
Recreation instead submitted `drawFrameIndex(-1, ...)`. The combined request
executed 13 tests with two failures, four launcher harness errors, and zero skips.
The launcher harness initially omitted its explicit player query, then omitted
the rolling animation needed to break a parent. Those setup failures were fixed
before attributing launcher render loss. With both prerequisites supplied, all
four launcher cases failed after destructive restore with no renderer submission.
They cover subtype 0 and subtype 1, each before and after movement. All source
and uninterrupted moving submissions passed before those restore failures.

Two state shapes were compared. A ROM recipe containing art key, orientation,
frame, piece index and drawing mode describes the production mapping tables.
It does not describe the public legacy constructor's independently supplied
piece and renderer. Inferring a frame or orientation by equal piece coordinates
would invent provenance. Capturing immutable piece values with the art key
covers both the production pieces and explicitly keyed independent pieces.
The existing compact record codec supports that value without new snapshot
infrastructure or a central exclusion policy.

Each owner now stores `FragmentArt(artKey, piece)` and creates the one-piece draw
list at submission. Breakable Block's existing `frameIndex` remains captured.
Its null-piece recipe selects frame fallback; a null recipe preserves intentionally
artless construction even when restore services have art available. Renderer
lookup uses the current ObjectRenderManager. The tests replace its renderer
between capture and restore, so retaining a captured live renderer cannot satisfy
the expected submissions.

The renderer-taking public native overloads were replaced with art-key overloads,
and every repository producer was migrated. Nonempty pieces require a nonnull
key, which the constructor tests enforce. The two native implementation classes
are absent from `src/test/resources/mods/mod-api-signatures-0.7.txt`; no creator
signature pin changes. The frame-index and artless constructors remain.
OOZ movement-before-gravity, render-flag deletion, the parent's piece-zero slot,
and invisible-launcher-before-fragment allocation are unchanged. Breakable Block
retains its inherited movement and camera-bottom retirement behavior. This work
makes no new ROM-parity claim for that behavior.

The focused selection ran 25 tests with one failure, zero errors and zero skips.
The failure was confined to the new launcher retirement harness, which directly
stepped fragments without installing the production camera bounds. The other 24
cases passed, including every missing-submission case, both art-key constructor
contracts, Breakable Block fallback and retirement, all six existing OOZ object
checks, and the ROM-backed CPZ block despawn check. After installing the cached
bounds, the exact retirement method and field-disposition guard passed two tests
with zero failures, errors or skips. Runtime source did not change between these
two runs. Completed coverage is 26 distinct test cases across those selections;
there was no single all-green 26-case invocation. No unchanged passing checks
were repeated.

The exact focused selections were:

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS2BreakableBlockFragmentGraphRewind,TestS2LauncherGraphRewind,TestOOZLauncherObjectInstance,TestBreakableBlockDespawn' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS2LauncherGraphRewind#fragmentRestoresTheLatchedOffscreenRetirement,TestRewindFieldDispositionGuard' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

Both ran from this worktree with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`,
`LUA_BIN=/usr/bin/lua5.4`, and DISPLAY and WAYLAND_DISPLAY unset.
`MAIN_CHECKOUT` and `ROM_ROOT` identify the existing main checkout, as in the
earlier commands.
The 25-case request waited 67 seconds and Maven reported 75 seconds of execution,
including compilation. The final two-case request waited 30 seconds and Maven
reported 25.961 seconds of execution. These are separate intervals.

The generated disposition report exactly matched all 15 remaining baseline
keys, with both assigned keys absent. The lists were removed, so no speculative
`TRANSIENT` policy was needed. Eight of the original 23 keys are now closed.
The two graph classes remain on the default compact capture path. The nonempty
piece checks distinguish offsets, dimensions, tile, palette, flips and priority.
This is renderer-input evidence, not GPU pixel verification or a complete CPZ,
HTZ or OOZ route certification. No broad smoke selection, complete guard profile,
S3K check, API check or release-only full suite ran in this follow-up.


## LBZ2 Knuckles inventory follow-up

This bounded follow-up starts at main `develop` commit
`2020d2e57a4b3a4cece9083730c89eb9c71d29a4` in the T3-provided checkout
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-e372c0a8`, branch
`bugfix/ai-lbz-knux-inventories-20261024`. No nested worktree, fetch, branch
integration, push, API pin update, or unrelated object-family change belongs to
this worker. The parent owns combined guards/API/S3K validation and delivery.

The complete producer/consumer search found only initialization writes and the
public `supportPillarsForTest` / `swingChildrenForTest` accessors, consumed by
`TestLbz2RideCameoInstances`. The cameo never dispatches choreography through
either collection. The manager dispatches each pillar and swing child; each
swing child reads its own parent for launch/shake state, copies leader X to that
parent, requests its fling, and expires after that parent retires. A reused pillar
is independently managed scenery, not a lifetime-owned cameo child.

ROM `loc_629CE` saves the initial child X in `$3A`
(sonic3k.asm:129736-129746). `loc_62A6C` writes the fling bit through exact
`parent3`, and `loc_62A82` copies leader X through the same link
(sonic3k.asm:129789-129802). `loc_62AAE` advances freefall separately
(sonic3k.asm:129814-129818). `loc_62AEE` advances pillar Y from the launch delta
(sonic3k.asm:129836-129839). The original code instead treated the saved anchor as
constructor-derived from the moved owner and chose the nearest cameo on recreate.

The destructive regression exposed an additional lifecycle error. Native dynamic
creation registers the cameo's initialized children before registering the cameo
itself. Capture therefore records those children before their owner. Old child
recreation drops them when no cameo has been restored yet; owner service injection
then initializes a fresh pillar/chain with fresh identities. The corrected
nine-case regression on unchanged production `2020d2e57` failed all nine cases,
with zero errors/skips: six exact graph restores, stale retired-child observation,
lost captured orphans, and the actual-path disposition audit. This is a root-cause
regression, not evidence inferred from restored parent scalars. The first harness
iteration also exposed an invalid slot-allocation expectation and an early
freefall checkpoint; both were corrected before that nine-failure run. An earlier
compile error was limited to placing the schema audit outside its package.

Two models were compared. Marking the history lists transient leaves their
meaningful public observations empty, stale, or dependent on initialization
side effects. Capturing those lists would retain historical dead references and
would not fix child-before-owner reconstruction, nearest-parent adoption, or the
shifted anchor. The selected model deletes both lists. The swing accessor derives
an immutable current inventory from live managed children with the exact owner,
sorted by native subtype. Support observation stores only an immutable
`ObjectRefId` value and uses the existing `ObjectQuery` identity/resolve surface
to observe the exact live pillar; a retired pillar or a
same-coordinate replacement is not reported as the captured support. Existing
public accessor signatures remain; the direct cameo tests now supply a real
manager. Unmanaged objects have no managed inventory to report.

Restore construction seeds the existing `initialized` flag before later service
binding, then phase two restores its captured value. Each swing recreates as a
pure spawn shell even before its owner;
compact identity fixup restores `parent`, and compact scalar capture restores the
saved anchor. Owner removal clears its managed children's parent references so a
snapshot between removal and the next child update captures genuine null-parent
orphans. Their next update expires them; scenery keeps its independent lifetime.
There are no new capture overrides, renderer snapshots, allowances, generic
framework changes, or manual identity codecs. Upstream Stageplay
`PlayerControlOwners` behavior is preserved; the new fixture supplies its player
through the real services/camera identity registration.

Focused validation completed in this checkout. The regression selection includes
fresh/reused support, initialization, a moved swing, the last pre-fling crossing,
freefall, a competing nearer cameo, exact identities/slots/parent links, retired
members with slot reuse, destroyed-unswept and removed-owner boundaries, and 220 forward frames after each graph
restore. It also audits each family's actual default compact schema rather than
an independent whole-class schema. The field-disposition guard is selected with
the two owned baseline keys removed; no other baseline key changes.


The first runtime attempt kept suppression inside `initializeAfterServices` while
`isRewindActiveRestore` was true. Its focused 25-case run passed the disposition
guard, the compact-path audit, and 12 existing cameo checks, but failed all ten
graph/retirement cases and errored on the Knuckles-route test helper. Inspection
of `ObjectManager` proved it binds services *after* that construction scope ends.
The guard therefore needed to seed the existing flag in the constructor, rather
than inspect the expired scope during service binding. The rejected-route helper
also now keeps the constructed actor for its assertion even when managed admission
correctly returns null for an immediately deleted object. No production deletion
rule was weakened.

After that correction, the exact new graph/policy class (12 cases, including both
owner-retirement boundaries) and the repaired rejected-route method passed 13
tests with zero failures/errors/skips. That request acquired admission immediately;
Maven reported 1 minute 24 seconds including compilation. The final implementation
uses `ObjectQuery` directly for identity and membership observation; its complete
focused selection passed below.


The final focused invocation passed **26 tests, zero failures, zero errors, zero
skips**: 12 graph/policy cases, all 13 existing ride/cameo checks, and the exact
field-disposition guard. It acquired admission immediately; Maven reported
1 minute 22 seconds including compilation. The generated disposition report
matched the remaining **13 baseline keys exactly**, with both assigned keys
absent. The per-class JUnit XML and skip elements were inspected, then those
consumed reports and the generated disposition diagnostic were deleted.

The final command, run from this checkout with DISPLAY/WAYLAND_DISPLAY unused,
was (`MAIN_CHECKOUT` and `ROM_ROOT` denote the existing main checkout, matching
the earlier command blocks; the variables below reproduce the executed absolute paths):

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestLbzKnucklesInventoryRewind,TestLbz2RideCameoInstances,TestRewindFieldDispositionGuard' "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" "-Dsonic2.rom.path=$ROM_ROOT/s2.gen" "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" test -B
```

The corrected nine-case baseline regression used the same command and ROM flags
with `-Dtest=TestLbzKnucklesInventoryRewind` on unchanged production
`2020d2e57` (nine failures, no errors/skips; queue wait 31 seconds, Maven execution
27.274 seconds). The first runtime attempt used the final three-class selector
above (25 tests, ten failures, one error, no skips; wait 64 seconds, execution
1 minute 24 seconds). The narrow successful correction used
`-Dtest=TestLbzKnucklesInventoryRewind,TestLbz2RideCameoInstances#knucklesModeDeletesCameoImmediately`
(13 passed; immediate admission, execution 1 minute 24 seconds). These are distinct
runs and source states, not a sum of elapsed delivery time. Tests were not duplicated
while queued or running, and source stayed fixed from each submission to its exit.

All supplied ROM paths already existed and were verified by CRC32/SHA-1 against
the repository's REV01/locked-on identities. These focused tests are ROM-free;
zero skips is not a claim that a ROM-backed route or GPU rendering ran. The graph
harness uses the real ObjectManager and RewindRegistry with destructive recreation,
and advances this family's production updates in SST order. Global music fade,
full level/event/camera orchestration, rendering pixels, other viewport/donor/team
routes and unrelated families are outside this evidence. Existing level-route
coverage gaps remain inherited. No broad smoke selection, complete guard profile,
API check, combined S3K suite, trace sweep or release-only full suite ran here.
The parent retains those composition obligations.

`git diff --check`, local audit-link existence checks and the scoped strict
disassembly-citation check passed. The citation tool still reports inherited
near/unverifiable references in these existing files; the new owning routines
were also checked directly in the pinned disassembly. Only the two assigned
baseline keys and audit rows were changed.

## LBZ2 ride-ship child inventory follow-up

This bounded follow-up starts at `624e95b4892533d23ae898d8127f277038777f70`
on `bugfix/ai-lbz-ship-rewind-flame-20261024`. Main advanced to `0ffa7f3c7`
during verification. This checkout and its submitted jobs stayed on the original
base. The parent owns reconciliation, composed guards/API checks, four S3K
stability classes, release prose, integration, push and worktree cleanup.

The existing release path consumes `spawnedChildren` to remove the flame before
allocating the final boss. Fresh child recreation left that list empty.
`liveFlameRestoreReplaysRetirementBeforeBossAllocation` passes its uninterrupted
half on unchanged runtime, then fails after destructive restore. The first
recorded lifecycle call is `add`, while the required first call is `remove` for
the exact flame instance. The completed baseline request ran one test with one
failure, zero errors and zero skips. An earlier request stopped at test compilation
because the new fixture incorrectly called `Camera.rewindAdapter()`; registering
the camera itself repaired that fixture before the behavioral reproduction.

Two owner shapes were compared. A dedicated captured flame reference would fix
the gameplay lookup, but would still need removal or reconstruction of the public
diagnostic inventory. The selected shape captures the existing registered-child
list through the existing identity collection codec. It preserves its order and
the boss entry at the allocation boundary, with no new reference field or generic
framework. Historical retired camera/flame entries are removed by their existing
unload callback boundary before another capture. The managed testing accessor
therefore describes registered children rather than retired history. Unmanaged
choreography and the public constructor/accessor signatures are preserved.

The settled restore callback rebinds camera/flame parent pointers from that exact
inventory after all identities resolve. Those two private parent fields become
mutable for this relink. Constructor-time nearest-parent lookup remains a tolerant
placeholder, and still returns null when no ship can be found. Null child links
are removed from the restored inventory. `remove(index)` becomes `remove(child)`
because the unload callback can already have removed the same entry. The real
manager still receives the same single flame removal before boss allocation.
Native timing, allocation, destruction and respawn flags are unchanged.

The live-flame regression leaves a lower free slot and retains the ship/camera
slots. The final boss takes the lower slot; its music fade takes the released
flame slot; its head takes the following free slot. The test compares exact child
identities and slots, ordered lifecycle calls, and camera retirement against
uninterrupted execution. A retired-camera case proves capture excludes an
unregistered historical child and replays the alternate free-slot boundary.
The post-flame capture restores the destroyed ship's remaining boss identity and
replays without another flame removal or allocation. The missing-parent case
keeps both child recreation paths tolerant. Overlapping ships at identical
coordinates prove inventories and forward child behavior follow exact captured
owners rather than nearest-parent ties.

All commands below ran from this checkout with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and `LUA_BIN=/usr/bin/lua5.4`.
The commands resolve `MAIN_CHECKOUT` to the existing main checkout and `ROM_ROOT`
to its existing ROM directory. They supply an absolute S3K ROM property, although
these focused tests exercise managed graphs and injected services without loading
a level.
The baseline used the first selector shown. After the runtime fix, the two-class
selection passed 17 tests with zero failures, errors or skips. After adding only
the overlapping-owner case, the final graph selection passed all five cases with
zero failures, errors or skips. The two passing invocations cover 18 distinct
focused cases. They provide bounded validation of the affected contracts.

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestLbz2RobotnikShipRewind' "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestLbz2RobotnikShipRewind,TestLbz2RideCameoInstances' "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

The behavioral baseline waited 159 seconds for admission; Maven reported 23.216
seconds including compilation. The successful two-class selection waited 115
seconds and reported about 82 seconds including compilation. The final five-case
selection waited 51 seconds and reported 25.931 seconds. These are separate
completed invocations. Queue waiting is not execution time. No request was
cancelled, duplicated or restarted for admission. The completed summaries report
zero skips. Final XML inspection confirmed the exact five graph methods and 13 cameo cases,
all executed with zero skips. Their consumed XML/text reports were then removed;
no category run or extra validation receipt was created.

Only the exact `Lbz2RobotnikShipInstance#spawnedChildren` key is removed from the
field-disposition baseline and assigned `CAPTURED` centrally. Other audit rows
and the final boss's own inventory obligation remain open. These tests do not
certify complete LBZ traversal, art/timing parity, viewport/donor/team breadth,
or the unrelated cameo inventories. The parent retains those coverage limits
and the delivery-wide gates.


## LBZ2 allocation-exhaustion correction

Fresh review of `098ed56337feb6f797ca4eacdeb83cbfdd8fbf89` against
`624e95b4892533d23ae898d8127f277038777f70` found P2 in the initial inventory
closure. The reviewed diff identifier was
`4e0bc2838a33e9c8ad77cddabc29e11292288c08eaa7225c1c2a9aef8ecb7eaa`.
`ObjectManager.addDynamicObjectInternal` marks failed-slot allocations destroyed
and returns before registering their identities. Such rejected allocations never
receive the unload callback that prunes registered children. The ship appended
these rejected camera/flame objects unconditionally. Its boss append had the same
problem. This invalidates the initial closure's unqualified live-inventory claim
under slot exhaustion; the earlier five graph cases did not exercise capacity.

Three new constrained-capacity cases use the real managed allocator and registry.
With zero remaining slots, capture on unchanged `098ed5633` fails for the rejected
`GradualCameraMaxXChild` identity. With one remaining slot above the ship, the
camera registers but capture fails for the rejected `ExhaustFlameChild` identity.
The boss-only zero-slot case likewise fails for `LbzFinalBoss1Instance`. Each
failure identifies `Lbz2RobotnikShipInstance#spawnedChildren` in the compact
capture error. The first request completed three cases with three failures, zero
errors and zero skips. Its two ride cases reached the identity defect; its boss
case first exposed an incomplete lifecycle recorder. That recorder was extended
to observe the manager's after-current and deferred after-current calls. The
boss-only retry on unchanged runtime then reached the missing-boss-identity
exception, with one failure, zero errors and zero skips.

The selected fix checks `!child.isDestroyed()` at the three inventory append
sites. These fresh constructors are not destroyed before allocation, and the
managed allocation contract marks rejection destroyed. Unmanaged constructors
remain undestroyed and keep their intentional test inventory. Filtering snapshots
instead was rejected because it would leave a false live inventory in the owner
and introduce a second rule for capture. The identity codec remains strict.
There is no catch/drop of arbitrary missing references, manager change, global
state or new policy allowance. The existing exact `CAPTURED` policy and its
baseline closure are unchanged by this follow-up.

Every original allocation attempt stays in place and in order. The failed boss
still runs its existing allocation-boundary initialization. The boss regression
records the exact eight attempted objects, the boss, music fade, head, top
attachment, three turret segments and orbiting pod. All are destroyed,
unregistered and unslotted at zero capacity. This correction does not change
native timing, destruction, respawn flags, initialization or retry semantics.
After successful capture, the new cases destructively restore the exact admitted
inventory and replay without another allocation attempt. The two ride cases
retain their zero/one registered-child counts; the failed boss inventory is empty.

All requests use the existing Java 25/Lua 5.4 environment and the same absolute
S3K ROM property described above. `MAIN_CHECKOUT` and `ROM_ROOT` retain the same
meanings. The first two selectors reproduce the defects before the runtime edit;
the final selection verifies the fix and all original 18 cases.

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestLbz2RobotnikShipRewind#exhaustedRideSlotsKeepRejectedChildrenOutsideSnapshot+exhaustedBossSlotKeepsRejectedBossOutsideSnapshot' "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestLbz2RobotnikShipRewind#exhaustedBossSlotKeepsRejectedBossOutsideSnapshot' "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestLbz2RobotnikShipRewind,TestLbz2RideCameoInstances' "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

The final request passed all 21 tests, eight graph cases and 13 cameo cases,
with zero failures, errors or skips. Exact XML case inspection confirms both
capacity parameters and the boss case executed. Queue waiting was 76 seconds
for the first reproduction, 98 seconds for the boss-only reproduction and 16
seconds for the successful selection. Maven reported 25.439, 26.420 and about
80 seconds respectively, including compilation. Requests stayed alive and each
checkout remained stable during its run. Consumed XML/text reports were inspected
and deleted without archiving. No live jobs remain. These are focused checks;
the parent still owns complete guards/API/S3K checks, reconciliation from the
unchanged base, release prose, integration, push and worktree cleanup.

## ICZ miniboss progressed-helper follow-up

This bounded slice starts at `0ffa7f3c727d802573f3fc954b5ce60e3c89169e` in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-9f0baaaf`, renamed to
`bugfix/ai-icz-miniboss-rewind-20261024`. The parent owns composed guards,
S3K stability, level-matrix/release prose, integration, push and worktree cleanup.
Only row 16 and its baseline key are changed here; earlier counts describe their
original validation slices.

The complete capture path is ObjectManager's adapter -> ObjectCallbackRouter ->
ObjectRewindTypeSafety -> AbstractObjectInstance's default subclass capture.
The original class cannot use its compact schema: non-final `cameraGate` needs
an existing target, while non-final helper arrays lack element reconstruction
constructors. Generic fallback restores the stateful arrays in place. The boss's
spawn-based reconstruction initializes eight null orb slots; populated captures
therefore fail at `GenericFieldCapturer.restoreStatefulValue`, before forward
execution. This is distinct from losing an inherited child inventory.

The production producers are `initShards`, gate-delayed `initOrbs`, and managed
`spawnChild` calls for defeat work. None adds ICZ objects to inherited
`childComponents`. Its real consumers are AbstractBossInstance's update/prune,
debug drawing, settled ordinal rebuilding and the public graph accessor, plus
Stageplay graph consumers. The supported native invariant is an empty inherited
inventory throughout this encounter, not an assertion that it contains the
orbs/shards or pending explosion worker. The bounded test checks it on every
observed dispatch. The existing central `CAPTURED` policy remains in force;
`TestIczMinibossFieldDisposition` requires this field in the actual default schema
and audits this class alone. This closure does not weaken a consumer contract or
classify arbitrary nonempty inventories as diagnostic.

Two designs were compared. An owner-local snapshot could preserve slot presence
and reconstruct each helper before copying its mutable state, but would duplicate
the existing array codec's null markers and helper construction. The selected
shape supplies private zero-argument materialization constructors to the two
existing helper types and makes the camera gate a final, preconstructed helper.
The constructor still supplies a fresh gate before the first dispatch. Array decoding
creates only captured present slots and restores all fields, including orb index
and the shard frame/packed-velocity recipe. Normal creation remains behind
loc_711EC (sonic3k.asm:149771-149783); no eager orb setup or RNG calls are added.
Movement, clocks, camera writes, slot reservation, defeat allocation, deletion,
respawn and public API signatures are unchanged. No central policy or framework
change is needed. A blanket `TRANSIENT` disposition and eager creation of all
orbs were rejected because they would respectively hide the capture path and
change pre-gate initialization/render/slot semantics.

The regression uses production ObjectManager updates with S3K slot layout and
RewindRegistry capture/restore, with in-place reuse disabled. It runs real
initialization to ticks 1, 124, 570 and 850; tick 570 positively checks collidable
orbit routine 8. It compares null/present helper slots, every helper scalar
(including immutable recipe fields), fresh helper/managed identities, exact
managed roles/slots, live allocation/peak counts, V-int, touch-region output and
ordered renderer draw inputs immediately after destructive removal/recreation.
Each encounter capture replays 1,000 dispatches against uninterrupted execution.
The killing hit is entered through the production attack callback with 33
dispatches between earlier hits; defeat captures at offsets 0, 37 and 70 replay
through controller/body retirement and palette/sign-flow allocation. Existing
nearest-parent controller relinking is checked against the fresh owner in this
single-family graph. Shared RNG, camera and game state are registered with the
manager. Explosion animation reads the verified supplied ROM. The recorder
observes boss draw inputs and emitted SFX; it does not certify GPU pixels,
full-level palette output, snow-emitter choreography, two simultaneous boss
families, player contact resolution, results/act transition, or viewport/donor/team
breadth. Existing encounter tests retain their narrower evidence.

Before runtime changes, the first request stopped in test compilation because
the observer called `getSlotIndex` on the ObjectInstance interface (121 seconds
admission wait; Maven 77 seconds). After correcting that test type, the first
executed selection had one passing gate case, three progressed-orb null-target
errors and three observer errors from reading coordinates on the legitimately
spawnless palette worker (41 seconds wait; Maven 26.350 seconds). The observer was
corrected without changing runtime. The matched baseline then ran seven tests:
`progressedHelpersRestoreFreshAndReplayTheEncounter(int)[1]` passed (tick 1);
its `[2]`, `[3]`, `[4]` cases (ticks 124/570/850) and all three
`pendingDefeatWorkersRestoreFreshAndReplayHandoff(int)` cases (offsets 0/37/70)
errored with `Cannot restore stateful rewind field
com.openggf.game.sonic3k.objects.IczMinibossInstance#orbs because one side is null`.
There were zero assertion failures and zero skips. This request waited 92 seconds;
Maven reported 27.037 seconds. XML identities and errors were inspected before
continuing. These are queue waits and Maven durations, not summed delivery time.

All requests use the following environment, the main checkout's queue wrapper,
and this worktree's own `target/`. All three existing absolute ROM files were
verified against the repository CRC32 and SHA-1 identities; none was linked or
copied.

```bash
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
export LUA_BIN=/usr/bin/lua5.4
export MAIN_CHECKOUT=/path/to/existing/main-checkout
export ROM_ROOT=/path/to/existing/ROM-directory
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestIczMinibossGraphRewind' "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" "-Dsonic2.rom.path=$ROM_ROOT/s2.gen" "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" test -B
```

The post-fix focused command is:

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestIczMinibossGraphRewind,TestIczMinibossFieldDisposition,TestIczMinibossRenderBuckets,TestS3kIczMinibossObject' "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" "-Dsonic2.rom.path=$ROM_ROOT/s2.gen" "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" test -B
```

The initial post-fix selection passed all 34 tests (7 graph cases, 1 class-local
field-disposition check, 3 render-bucket checks and 23 existing encounter methods),
with zero failures, errors or skips; XML identities were inspected. It waited
63 seconds for admission and Maven reported 83 seconds. Local review then removed
an unnecessary constructor `reset()` introduced by the fix: the original first
construction supplied a fresh gate without reset. Final field initialization now
preserves that exact pre-gate helper value as well as its behavior. The same
focused selection is repeated for that concrete source adjustment.

The final unchanged-source selection again passed all 34 exact identities above,
with zero failures, errors or skips. It waited 3 seconds for admission; Maven
reported 83 seconds including recompilation. Final XML confirms all four
encounter captures and all three defeat offsets ran, alongside the class-local
capture obligation and existing render/encounter methods. The inherited list's
baseline key is removed because compact capture is now reachable, not because
an unused field was excluded. Only that key was removed; no other baseline or
audit row changed. Local audit links, all three disassembly citations and
`git diff --check` passed. Consumed Surefire XML/text reports are removed after
inspection; no category run, pass cache or log archive is retained. No broad
smoke, full suite, API gate or combined S3K run was submitted by this delegate.
The task worktree and local branch remain for the parent's integration.

The commit policy rejected literal home-directory paths in the audit commands;
`MAIN_CHECKOUT` and `ROM_ROOT` above stand for the verified existing main checkout
and ROM directory used by the invocations. The recorded selectors, flags, ROM
filenames and working directory are unchanged. No source or test changed after
the final successful run.

## Rexon and Sol owner follow-up

This bounded follow-up starts from `624e95b4892533d23ae898d8127f277038777f70`
in the `bugfix/ai-s2-badnik-rewind-owners-20261024` worktree. The parent owns
reconciliation with subsequent develop changes and composed totals.

Parent-owned child identities and child-owned optional identities were compared.
Capturing the existing parent lists directly would retain retired entries and
still need a representation for Rexon's sparse next-head links. The selected
per-child snapshot records use the existing identity table and extra-state
hooks. Rexon stores owner and next-head IDs. Sol stores its owner ID through
flight. An unmanaged or retired target encodes no ID, and an unresolved target
restores as null. Required-reference codecs keep their existing strict behavior.
The parent inventories rebuild only after every reference and scalar has settled.
No native movement, animation clock, allocation order, spawn, delete, respawn,
attack or unload dispatch was changed. The unused nearest-owner recipes and
Rexon's addition-time chain relinking were removed for these two families.

The first reproduction accidentally recreated each dynamic parent immediately
before its own children. That ordering masked Sol's ambiguous-owner defect.
It still failed Rexon's sparse-chain and retired-owner checks. Creating both
parents before either child family exposed both wrong-owner cases. On unchanged
runtime `624e95b48`, the corrected five-test selection had five failures, zero
errors and zero skips. Four failures were wrong ownership or retired-parent
adoption. The fifth was the absent exact inventory policy. The initial sparse
Rexon check also observed an incorrect link across the retired slot before the
fixture ordering was corrected.

The first fixed selection ran 32 tests with zero assertion failures, one error
and zero skips. The new Rexon attack check reached the real points constructor,
which exposed the fixture's missing render-manager service. The fixture now
provides that service without changing runtime destruction. Sol setup was also
strengthened to reach flight through real player detection, animation and orbit
updates rather than a direct launch invocation. The final graph/object selection
passed all 11 tests across four classes with zero failures, errors or skips.
The 21 policy-registry checks passed in the preceding selection on the same
policy implementation. Both inventory policies were proved before their two
baseline keys were removed. In-place reuse remains disabled by the existing
class safety policy for all four native classes, which the new test asserts.

Commands use Java 25, Lua 5.4 and the updated main-checkout Maven wrapper from
this worktree. The three existing absolute ROM paths are passed unchanged.
`MAIN_CHECKOUT` and `ROM_ROOT` below name the existing main checkout and ROM
directory. No category run or release-only full suite was submitted.

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestS2RexonSolOwnershipRewind' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestS2RexonSolOwnershipRewind,TestS2BadnikChildGraphRewind,TestRewindFixS2Batch3Codecs,TestRewindFixS2Batch5Codecs' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

These are bounded headless graph checks. They do not certify rendered pixels,
the complete HTZ route, every viewport, character or team combination, or a
trace frontier. Delivery-wide guard, API and S3K checks remain with the parent.

After pruning, the bounded disposition and codec-metadata selection passed all
seven tests with zero failures, errors or skips. The generated disposition
report matched the remaining baseline exactly, with both owned keys absent.
The request waited 336 seconds for admission and Maven reported 25.746 seconds
of execution. The successful graph request waited 14 seconds and Maven reported
80 seconds. The corrected baseline request waited 113 seconds and Maven reported
25.204 seconds. These are separate intervals, not summed delivery time.

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestRewindFieldDispositionGuard,TestRewindFieldDispositionAudit,TestScalarOnlyCodecDeletion#s2BadnikChildGraphClassesAllImplementRewindRecreatable+s2BadnikParentGraphClassesImplementRewindRecreatable' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The XML method identities and skips, Java 25.0.5.0.2 and surfaceless flag, and the
generated disposition report were inspected. Consumed reports and diagnostics
were removed. No validation receipt or retained log archive was created.

## Rexon and Sol context-free compatibility correction

Independent review of `db2afc6619a49e3a30a562e3d3ff497fc832cbd0` against
`624e95b4892533d23ae898d8127f277038777f70` identified a bounded public API
compatibility regression. The parent supplied review hash
`3abbce1bf230304f96620bacf58f5caeb1fa35d53f39de04d5ec7b9791310f8f`, excluding
the inherited dirty submodules. The original implementation conflated an absent
identity table with a supplied table that authoritatively encoded absent links.
`AbstractObjectInstance` delegates its public no-argument capture and restore
methods through `RewindCaptureContext.none()`. The new child extras therefore
cleared existing structural links on a same-instance context-free round trip.
An orbiting Sol fireball then destroyed itself on its next update. The parent
found no current production caller for this combination. This correction does
not reclassify the managed registry checks as failing.

The rejected `db2afc661` approach always emitted a custom link extra, even when
capture had no identity table. The accepted correction adds an absent-table
return to each child capture method. It returns the superclass snapshot intact,
including its generic/scalar state and any existing extra. Table-backed capture
and all restore code remain unchanged. A captured null ID remains authoritative.
Rejecting the public no-argument API or skipping link restore whenever the
receiving context lacks a table would change that contract. The expanded null-ID
controls specifically cover table-backed snapshots consumed through context-free
restore, so a restore-side guard would fail those controls.

On unchanged runtime `db2afc661`, the new six-case selection ran with four
failures, zero errors and zero skips. Both the no-argument and explicit
`RewindCaptureContext.none()` cases lost Rexon's owner and next-head links.
Both Sol cases lost their owner and destroyed the orbiting fireball on its next
tick. Scalar capture and restoration succeeded, distinguishing lost links from
an omitted entire snapshot. The two supplied-table null-ID controls passed.

With only the two capture guards added, the affected graph/object selection
passed all 17 tests across four classes with zero failures, errors or skips.
That selection includes the existing exact managed owner, sparse retirement,
chain/death-drop, native Sol launch, unload and forward-replay checks. The two
null-ID controls were then expanded to cover both table-backed and context-free
restore forms. Only those four parameter cases were run afterward; all passed
with zero failures, errors or skips. The XML identities and skips were inspected,
and consumed XML/text diagnostics were removed. No source was changed while a
request was queued or running. No request was restarted or duplicated.

All three commands below used Java 25.0.5.0.2, `LUA_BIN=/usr/bin/lua5.4`, the
existing absolute ROM paths and the updated main-checkout wrapper from this
worktree. Repository resource policy requires checkout locations to use
variables. `MAIN_CHECKOUT` and `ROM_ROOT` stand for the existing main checkout
and ROM directory used above; all other arguments are exact. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and its `bin` directory led
`PATH`. The baseline acquired admission immediately and Maven reported
24.839 seconds. The affected graph request waited 43 seconds and Maven reported
1 minute 20 seconds. The expanded controls waited 34 seconds and Maven reported
23.757 seconds. Queue and execution intervals are separate observations.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestS2RexonSolOwnershipRewind#contextFreeRexonRoundTripKeepsOwnerAndNextHead+contextFreeSolRoundTripKeepsOrbitingOwnerAndNextTick+tableBackedNullRexonIdsClearExistingLinks+tableBackedNullSolIdClearsExistingOwner' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestS2RexonSolOwnershipRewind,TestS2BadnikChildGraphRewind,TestRewindFixS2Batch3Codecs,TestRewindFixS2Batch5Codecs' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestS2RexonSolOwnershipRewind#tableBackedNullRexonIdsClearExistingLinks+tableBackedNullSolIdClearsExistingOwner' -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

This follow-up changes no central policy, baseline key or audit row. Its field
obligation delta is zero; the two managed inventory closures remain intact.
No native movement, lifecycle, allocation or manager in-place reuse rule changed.
The 21 policy-registry checks were not rerun. No broad suite or release-only full
suite was submitted. Complete guards, Mod API, S3K, upstream reconciliation,
integration and delivery remain with the parent. These direct API and headless
graph checks do not certify rendered output or a complete HTZ route.


## LBZ2 Knuckles independent review correction

Review artifact `be55fe0715eb44bbbe6166fa709abc45e8f991f285cefe4074a22cf58827f69b`
identified four missed contracts in `aabc684a16596ec4c46c5abe886d8e897562cf07`:
the exact annotation count, constructor-before-owner admission exhaustion,
public context-free child snapshots, and public unmanaged observations. These
were reproduced before changing production code or merging upstream. The
selected eight cases ran with **three failures, four capture errors, zero skips**;
the unchanged override guard was the sole passing control. Admission waited two
seconds; Maven reported 27.273 seconds of execution. The annotation failure named
only this source's two actual annotations against the stale allowance of four.
The getter failures were unavailable-services exceptions. The two public capture
forms, a present-table/null control, and constrained-capacity capture failed at
the strict `SwingChild.parent` identity codec as expected.

Local `develop` at `a236a1276ec7020eaca28134e6fa6d4f345c8e07` was then merged
normally as `ac3205445d0acb358d221409409be13fcfb36ad2`, with `aabc684a1` as its
first parent. The only conflict was the shared audit append; both independent
evidence sections and all separately updated field rows were retained. No
branch was fetched, rebased, forced, switched in the main workspace, or pushed.
Uncommitted review tests remained outside that merge commit. Other-family loop4
changes, including Stageplay player ownership, were preserved.

The correction reuses the already-private field-selected optional mode of
`ObjectReferenceCodec`; the codec implementation is unchanged. One shared
`LBZ_SWING_PARENT` key now selects that mode and the exact `CAPTURED` metadata.
The closed-set reachability control admits exactly this key plus the original
three S2 keys. The existing byte-level controls now also exercise the swing key,
including class-only lookup remaining strict, replacement identity resolution,
wrong-type rejection, uncaptured link preservation, authoritative captured null,
and absent restore targets. No blanket optional-reference rule, new manual
capture/restore override, opaque object pointer, allowance growth, or API pin
change is used. The annotation allowance is pruned **4 -> 2**; the remaining two
annotations classify only spawn-derived subtype/index. Both public observations
use `tryServices()` and return empty when no services are available, including a
previously initialized support identity whose services were removed.

The new native admission fixture uses the real `Sonic3kObjectRegistry`,
`ObjectManager` and `RewindRegistry`, with the S3K dynamic window constrained to
four slots and its other slot-layout flags retained. Production allocation order
is asserted literally: fade at slot 4, pillar at 5, subtype-0 swing at 6 and
subtype-2 swing at 7. The remaining swings and then the owner fail admission. The
raw owner is destroyed, has slot -1 and no registered identity; the two admitted
children still point at it because rejected admission has no removal callback.
Capture occurs **before** any child dispatch. Restore must recreate those exact
child IDs/slots with authoritative null parents, and actual manager dispatch
must delete them just as uninterrupted dispatch does. The fixture silences only
the unrelated fade API with a test audio boundary; it asserts production graph
and lifetime behavior rather than mock behavior. No production allocation call,
order, slot flag or deferred-execution flag changes.

Actual public child snapshot calls cover both no-argument and explicit
`RewindCaptureContext.none()` capture. Context-free restore replays motion state
while retaining the already-existing owner link; a no-table snapshot also
preserves a different live owner's link even when restore supplies a table.
Conversely, a snapshot made with a present table and an unregistered owner must
clear a live link even when restore has no table, then reach the orphan deletion
tail. This distinguishes missing authority from a captured null instead of
returning early on both. Existing exact competing-owner/anchor/shell behavior
and all original 26 cases are retained.

The combined focused selection added the six boundary cases, exact
optional-reference controls, both closed-set/reachability controls, and the
architecture annotation/override methods to the original 26-case selection. It
ran **68 cases: 67 passed, one failure, zero errors/skips**. All original 26
cases and both architecture methods passed. Admission waited 74 seconds; Maven
reported 1 minute 29 seconds of execution. The field-disposition baseline is
unchanged by this review correction;
its nine inherited keys match the final generated report exactly, with both
LBZ inventory keys still absent. Full guard profiles, API/S3K composition and
delivery remain with the parent. This fixture does not certify rendered output, complete LBZ routes or
other viewport/donor/team breadth.


The single failing case had advanced beyond the rejected-owner capture that
failed on `aabc`: capture now succeeded, as did uninterrupted orphan deletion.
A diagnostic rerun limited its restored-object assertions and still failed on
owned swing ID 2, slot 6 (one failure, no errors/skips; four-second queue wait,
26.108 seconds of Maven execution). This exposed a second owner-absence dependency:
`ObjectRewindDynamicCodecs` constructs a probe before invoking the object's
recreate hook. The available `(ObjectSpawn, parent)` probe constructor required
finding a live cameo, so an orphan-only graph could never reach the otherwise
parent-independent `recreateForRewind` shell. Ordinary two-owner fixtures had
masked that dependency by allowing the generic fixed-point retry to find a live
owner. The exact null-parent codec alone could not fix an unreachable recreation
hook.

The correction adds a private spawn-only `SwingChild` probe constructor and uses
that same pure shell from `recreateForRewind`. Captured exact parents still bind
in phase two; a graph containing no cameo can now recreate the null-parent child
before native orphan dispatch retires it. Normal child creation still takes the
same parent/subtype constructor and native allocation calls. Anchors, choreography
and slot flags are unchanged. No framework workaround or manual capture override
is added.

An initial source reading had wrongly blamed the fade helper's recreation,
overlooking its existing package-private `ObjectSpawn` constructor. That hypothesis
and the reduced population assertion were rejected. The final regression retains
all four admission and restoration identities/slots, including the fade helper;
it also allocates two new pillars after uninterrupted/replayed orphan deletion,
requiring slots 6 and 7 and identical next dynamic IDs in both paths. There is no
asserted inherited fade gap.

The final affected-family selection (the original 26 cases, all six new boundary
cases, and both architecture methods) passed **34 tests, zero failures, zero
errors, zero skips** after the constructor correction. Admission was immediate;
Maven reported 1 minute 32 seconds of execution including compilation. The 32
byte-level optional-codec cases and both
closed-set/reachability methods already passed and their implementations/metadata
have not changed since that run. No broad profile is being repeated.


The final affected command was:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestLbzKnucklesInventoryRewind,TestLbz2RideCameoInstances,TestRewindFieldDispositionGuard,TestRewindArchitectureGuard#objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage+objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage' "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" "-Dsonic2.rom.path=$ROM_ROOT/s2.gen" "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" test -B
```

`MAIN_CHECKOUT` / `ROM_ROOT` have the same neutral meaning as the earlier command
blocks. All requests ran from this task checkout through that main-checkout
wrapper with the existing verified absolute ROM paths, Java 25, Lua 5.4 and the
required EGL flag. The other exact selectors below used identical wrapper,
runtime and ROM arguments:

- Baseline on `aabc`: `-Dtest=TestLbzKnucklesInventoryRewind#publicConstructionHasEmptyUnmanagedObservations+removingServicesMakesBothLiveObservationsEmpty+publicChildSnapshotsWithoutIdentityTablePreserveCurrentLinks+tableBackedUnregisteredOwnerIsAuthoritativeNullWithoutRestoreTable+constrainedNativeAdmissionCapturesRejectedOwnersChildrenBeforeDispatch,TestRewindArchitectureGuard#objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage+objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage`.
- Combined after metadata/getter/count correction: `-Dtest=TestLbzKnucklesInventoryRewind,TestLbz2RideCameoInstances,TestRewindFieldDispositionGuard,TestOptionalObjectReferenceCodecs,TestCapturedPolicyCompactReachabilityGuard#optionalNativeLinksHaveClosedExactCapturedMetadata+capturedPoliciesNeedingCompactPathAreReachable,TestRewindArchitectureGuard#objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage+objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage`.
- Narrow orphan-only diagnosis: `-Dtest=TestLbzKnucklesInventoryRewind#constrainedNativeAdmissionCapturesRejectedOwnersChildrenBeforeDispatch`.

Coverage is **68 distinct passing cases across the completed selections**, with
34 affected cases passing on the final constructor fix and the 34 unchanged
codec/reachability cases passing in the earlier combined run. There was no single
all-green 68-case invocation. XML case identities/counts and skip elements were
inspected; consumed XML/text reports and the generated disposition report were
removed. No live requests remain, none was duplicated or restarted, and source
was fixed during each queued/running request. No release-only full suite, broad
smoke selection, full guard profile, API check or combined S3K selection ran in
this review follow-up. Existing route/rendering/viewport/donor/team limits remain
as stated above; all reported checks are ROM-free despite receiving verified ROM
paths. Local whitespace, audit-link and scoped strict citation checks passed.

The follow-up adds no baseline field key and changes no unrelated family. Both
inventory obligations remain closed with tested public compatibility and rejected
owner behavior; the parent retains next-wave integration and delivery.
