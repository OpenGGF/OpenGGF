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
| 8 | [`sonic2.objects.badniks.RexonBadnikInstance#heads`](../../../src/main/java/com/openggf/game/sonic2/objects/badniks/RexonBadnikInstance.java) | Retained pending broader owner evidence. Recreated heads attach through `nearestRexon`; the custom list rebuilds linked-head order during addition. | [`TestS2BadnikChildGraphRewind.s2BadnikChildGraphRestoresWithoutDropsDoublesOrStaleReferences`](../../../src/test/java/com/openggf/game/rewind/TestS2BadnikChildGraphRewind.java) checks five heads, restored parent references, and chain links in one Rexon family. Before excluding the list, exercise two nearby families, partial head retirement, restored indices, and forward chain behavior. The current nearest-owner recipe does not establish exact multi-family identity. |
| 9 | [`sonic2.objects.badniks.SolBadnikInstance#fireballs`](../../../src/main/java/com/openggf/game/sonic2/objects/badniks/SolBadnikInstance.java) | Retained pending broader owner evidence. Recreated fireballs attach through `nearestSol`; detached flying fireballs may have no parent. | The same S2 badnik graph test checks four parent-owned restored fireballs. `detachedFlyingSolFireballRestoresWithoutLiveParent` checks the detached case. Add two nearby owners and mixed attached/flying retirement evidence before declaring this cache wholly reconstructible. |
| 10 | [`sonic2.objects.bosses.CPZBossPipe#segments`](../../../src/main/java/com/openggf/game/sonic2/objects/bosses/CPZBossPipe.java) | Closed with central `TRANSIENT`. CPZBossPipeSegment recreation calls `reregisterRestoredSegment` on the recreated pipe. | [`TestS2CpzBossGraphRewind.cpzBossGraphRestoresWithoutDropsDoublesOrStaleReferences`](../../../src/test/java/com/openggf/game/rewind/TestS2CpzBossGraphRewind.java) destructively replaces the graph and checks stable identities, fresh objects, parent links, and the pipe's exact live segment list. Its representative graph has one segment; sparse multi-segment retract ordering remains outside this check. |
| 11 | [`sonic3k.objects.AizPlaneIntroInstance#activeWaves`](../../../src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java) | Retained. `tickWaveSpawn` appends managed waves. Child recreation restores parent links, but does not rebuild this parent list. The list is currently appended and cleared on unload, rather than read by the gameplay dispatch. | [`TestS3kAizIntroGraphRewind.aizIntroPlaneAndWaveChildrenRestoreFreshWithRestoredParentRefsAndScalars`](../../../src/test/java/com/openggf/game/rewind/TestS3kAizIntroGraphRewind.java) checks managed children and their parent links, not this list. Prove retirement/unload behavior and the absence of a live list consumer across the intro boundary before an explicit diagnostic-only exclusion. |
| 12 | [`sonic3k.objects.AizPlaneIntroInstance#emeralds`](../../../src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java) | Retained. Emerald scattering appends managed children; unload clears the list. Recreation does not repopulate it. | The AIZ intro graph fixture does not exercise the seven scattered emeralds and their retirement boundary. Add that graph and a destructive restore/forward run before excluding the parent inventory. Emerald glow-child tests cover a different class. |
| 13 | [`sonic3k.objects.CutsceneKnucklesLbz2Instance#supportPillars`](../../../src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesLbz2Instance.java) | Retained. Initialization finds or creates a managed pillar and records it. Generic spawn reconstruction with `initialized` restored does not run that creation path again. The public test accessor reads this inventory. | `TestLbz2RideCameoInstances` exercises production creation and cameo choreography, not destructive restoration of this list. Prove existing-pillar reuse, fresh pillar recreation, and list ownership after restore. |
| 14 | [`sonic3k.objects.CutsceneKnucklesLbz2Instance#swingChildren`](../../../src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesLbz2Instance.java) | Retained. Initialization records four children. SwingChild recreation selects a nearest live cameo, without reattaching to its parent's inventory. | `TestLbz2RideCameoInstances` is named by the isolated sweep but does not perform registry capture/restore. Required evidence includes four exact child identities, parent inventory restoration, and shake/freefall replay. Do not equate that sweep label with executed graph proof. |
| 15 | [`sonic3k.objects.IczFreezerObjectInstance$FrozenPlayerBlock#debris`](../../../src/main/java/com/openggf/game/sonic3k/objects/IczFreezerObjectInstance.java) | Retained. `spawnDebris` records managed IceDebris; FrozenPlayerBlock recreation starts an empty inventory. Current reads are the testing accessor, but the deletion and auxiliary-sidekick paths have distinct lifetimes. | [`TestS3kIczFreezerGraphRewind.iczFreezerGraphRestoresWithoutDropsDoublesOrStaleReferences`](../../../src/test/java/com/openggf/game/rewind/TestS3kIczFreezerGraphRewind.java) checks cloud/block/player graph links. It does not certify this nonempty debris inventory after the block breaks. Exercise the native and auxiliary-sidekick debris boundaries, including retirement and forward replay. |
| 16 | [`sonic3k.objects.IczMinibossInstance#childComponents`](../../../src/main/java/com/openggf/game/sonic3k/objects/IczMinibossInstance.java) | Retained. This field is inherited from AbstractBossInstance, whose central policy requests identity capture. The class's actual fallback path still drops it. Its shard/orb helper arrays have their own state model, so an unconditional transient policy would hide a capture-path problem. | `TestS3kIczMinibossObject` tests encounter behavior, not complete destructive registry restoration. Required evidence includes the actual capture route, managed child identities, shard/orb state, pending defeat work, and a forward encounter replay. An empty inherited list in a default probe is insufficient. |
| 17 | [`sonic3k.objects.Lbz2RobotnikShipInstance#spawnedChildren`](../../../src/main/java/com/openggf/game/sonic3k/objects/Lbz2RobotnikShipInstance.java) | Retained gameplay debt. It records camera/flame/boss children. `releaseExhaustFlameForBossSpawn` consumes the list to remove the flame before boss allocation, so this inventory is not merely diagnostic. Recreation does not rebuild it. | `TestLbz2RideCameoInstances` covers the allocation choreography without a rewind split. Capture with the flame live, destructively restore, then advance through release and boss allocation; compare exact slots and child retirement. Do not replace deletion semantics to make this pass. |
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
