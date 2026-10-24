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
| 5 | [`sonic2.objects.BreakableBlockObjectInstance$BreakableBlockFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/BreakableBlockObjectInstance.java) | Retained render debt. The public piece constructor retains a mapping piece and renderer, while recreation always takes the frame-index constructor. It cannot reconstruct the legacy piece recipe from its spawn. | [`TestS2BreakableBlockFragmentGraphRewind.breakableBlockFragmentRestoresFreshWithCapturedPhysicsAndFrame`](../../../src/test/java/com/openggf/game/rewind/TestS2BreakableBlockFragmentGraphRewind.java) uses the frame-index constructor with null art. It proves physics and identity only. Required evidence is a nonempty piece-backed draw before removal, after recreation, and on forward replay. Do not classify the list from this scalar-only pass. |
| 6 | [`sonic2.objects.OOZLauncherObjectInstance$LauncherFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/OOZLauncherObjectInstance.java) | Retained render debt. Recreation explicitly supplies null piece and renderer, creating an empty list. Production rendering requires a ready renderer and a nonempty list. | [`TestS2LauncherGraphRewind.oozLauncherFragmentRestoresFreshWithCapturedPhysicsAndIdentity`](../../../src/test/java/com/openggf/game/rewind/TestS2LauncherGraphRewind.java) supplies null art. It cannot prove visibility. Preserve a ROM-backed frame/piece recipe and verify emitted render data through destructive restore before closing this key. No speculative capture of live render handles is added. |
| 7 | [`sonic2.objects.SmashableGroundObjectInstance$SmashableGroundFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/SmashableGroundObjectInstance.java) | Closed with central `TRANSIENT`. The captured spawn packs frame and piece indices. Recreation fetches the matching sheet and renderer from `ObjectServices.renderManager()` and builds the one-piece list. | [`TestS2DebrisFragmentGraphRewind.smashableGroundFragmentRecreatesItsNonemptyMappingPieceAndReplaysTheDraw`](../../../src/test/java/com/openggf/game/rewind/TestS2DebrisFragmentGraphRewind.java) uses frame 1/piece 1 with a decoy, distinct offsets, dimensions, tile, flips, palette, and priority. It removes the source, verifies the exact nonempty draw after reconstruction, and compares the next moving draw with uninterrupted execution. This checks renderer input, not GPU pixels or every art variant. |
| 8 | [`sonic2.objects.badniks.RexonBadnikInstance#heads`](../../../src/main/java/com/openggf/game/sonic2/objects/badniks/RexonBadnikInstance.java) | Closed with central `TRANSIENT`. Each head captures optional exact owner and next-head identities. The settled callback rebuilds the live inventory by owner and captured head index. It never compacts a missing chain slot. | [`TestS2RexonSolOwnershipRewind.progressedRexonFamiliesRestoreExactOwnersSparseChainsAndDeathDropScope`](../../../src/test/java/com/openggf/game/rewind/TestS2RexonSolOwnershipRewind.java) destructively restores two nearby families after native neck movement and interior-head retirement, checks exact identities and inventories, replays every surviving head, and scopes attack-triggered death drop to its original family. The retired-parent check tolerates absent owner and next-head identities. |
| 9 | [`sonic2.objects.badniks.SolBadnikInstance#fireballs`](../../../src/main/java/com/openggf/game/sonic2/objects/badniks/SolBadnikInstance.java) | Closed with central `TRANSIENT`. Each fireball captures its optional exact owner identity. The settled callback rebuilds the live inventory, including flying fireballs that retain their parent until unload. | [`TestS2RexonSolOwnershipRewind.solFamiliesRestoreMixedFlyingInventoriesCountersAndUnloadDetachment`](../../../src/test/java/com/openggf/game/rewind/TestS2RexonSolOwnershipRewind.java) advances real detection, animation, orbit and flight near another family, retires one child, restores fresh owners, compares exact inventories and forward movement, checks subsequent launch counters, and verifies family-specific unload detachment. The retired-parent flying check rejects adoption by a nearby live Sol. |
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
