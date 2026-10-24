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
| 1 | [`sonic1.objects.Sonic1RingInstance#childRingSpawns`](../../../src/main/java/com/openggf/game/sonic1/objects/Sonic1RingInstance.java) | Retained with narrower debt. Placed parents restore through `Sonic1ObjectRegistry.create(spawn)`, which rebuilds the canonical recipe from the current level mapping, including before INIT. Progressed dynamic children correctly recreate with an empty recipe. A field-wide exclusion still needs a contract for public caller-supplied parent recipes. | [`TestS1RingGroupGraphRewind`](../../../src/test/java/com/openggf/game/rewind/TestS1RingGroupGraphRewind.java) passes six destructive managed controls on unchanged runtime: real GHZ1 pre-INIT placement, exact allocation/collection/respawn replay, canonical RingManager identities, and progressed child sparkle retirement without duplication. See the bounded S1 follow-up below for construction inventory, recipe alternatives and residual limits. |
| 2 | [`sonic1.objects.bosses.FZPlasmaLauncher#activeBalls`](../../../src/main/java/com/openggf/game/sonic1/objects/bosses/FZPlasmaLauncher.java) | Closed with central `TRANSIENT`. Each recreated FZPlasmaBall adopts itself through `adoptPlasmaBallForRewind`; the launcher prunes destroyed entries and derives its active count. The native encounter has one launcher group. | [`TestS1FzBossGraphRewind.fzBossGraphRestoresChildrenLauncherBallsAndParentSideLinks`](../../../src/test/java/com/openggf/game/rewind/TestS1FzBossGraphRewind.java) removes children, creates a divergent launcher/ball, restores fresh identities, checks the exact list and count, then advances the launcher without duplicating balls. |
| 3 | [`sonic1.objects.bosses.Sonic1FalseFloorInstance#childBlocks`](../../../src/main/java/com/openggf/game/sonic1/objects/bosses/Sonic1FalseFloorInstance.java) | Closed in the bounded follow-up below with central `TRANSIENT`. Recreation rebuilds the surviving managed inventory. Dispatch resolves the captured original `blockIndex` instead of a compacted list position, and advances the native break count on each timer borrow. | [`TestFalseFloorBlockRewindGenericRestore.partialCollapseReplaysThroughCleanupAfterDestructiveRestore`](../../../src/test/java/com/openggf/game/sonic1/objects/TestFalseFloorBlockRewindGenericRestore.java) fails on the original runtime in all four partial-collapse cases and passes with the fix. An event-style dynamic master and its surviving children restore as fresh objects with exact identities and order. Every forward timer, width and fragment state matches uninterrupted execution through master cleanup and fragment retirement. The complete eight-child test remains green. |
| 4 | [`sonic1.objects.bosses.Sonic1SLZBossInstance#seesaws`](../../../src/main/java/com/openggf/game/sonic1/objects/bosses/Sonic1SLZBossInstance.java) | Closed with central `TRANSIENT`. Recreation starts the list empty. `updateBossLogic` calls `scanForSeesaws` before consuming the cache on the next forward dispatch. | [`TestS1SlzBossSpikeballGraphRewind.restoreRebuildsTheBossSeesawScanCacheSoAlignmentCanStillMatch`](../../../src/test/java/com/openggf/game/rewind/TestS1SlzBossSpikeballGraphRewind.java) forces fresh reconstruction and verifies that the next update repopulates the cache. Other tests in the class check spikeball origin matching rather than first-live-seesaw matching. This is bounded encounter evidence, not a full SLZ route replay. |
| 5 | [`sonic2.objects.BreakableBlockObjectInstance$BreakableBlockFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/BreakableBlockObjectInstance.java) | Closed by removing the redundant list and capturing an immutable `FragmentArt(artKey, piece)` value through the existing default compact schema. A null piece selects the existing frame fallback; a null recipe preserves artless construction. The piece constructor now requires explicit art provenance instead of a live renderer. | [`TestS2BreakableBlockFragmentGraphRewind.pieceFragmentRestoresItsDrawBeforeAndAfterMovement`](../../../src/test/java/com/openggf/game/rewind/TestS2BreakableBlockFragmentGraphRewind.java) checks exact nonempty submissions after destructive registry restore, immediately and after motion, through a replacement renderer. `frameFallbackPreservesArtPresenceAndRetirement` checks frame 3, artless construction and retirement replay. See the S2 fragment follow-up below for baseline failures and limits. |
| 6 | [`sonic2.objects.OOZLauncherObjectInstance$LauncherFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/OOZLauncherObjectInstance.java) | Closed by removing the redundant list and capturing an immutable art-key/piece recipe. The production owner supplies the orientation-specific art key, and drawing resolves the current renderer. No renderer or inferred coordinate match enters the snapshot. | [`TestS2LauncherGraphRewind.producedFragmentsRestoreTheirDrawForBothOrientations`](../../../src/test/java/com/openggf/game/rewind/TestS2LauncherGraphRewind.java) breaks real managed parents for both orientations, captures before and after child motion, replaces the renderer, and checks exact submissions, piece zero, identities and allocation slots after destructive registry restore. `fragmentRestoresTheLatchedOffscreenRetirement` checks a custom art key and next-step deletion from a restored offscreen latch. See the S2 fragment follow-up below. |
| 7 | [`sonic2.objects.SmashableGroundObjectInstance$SmashableGroundFragmentInstance#pieceList`](../../../src/main/java/com/openggf/game/sonic2/objects/SmashableGroundObjectInstance.java) | Closed with central `TRANSIENT`. The captured spawn packs frame and piece indices. Recreation fetches the matching sheet and renderer from `ObjectServices.renderManager()` and builds the one-piece list. | [`TestS2DebrisFragmentGraphRewind.smashableGroundFragmentRecreatesItsNonemptyMappingPieceAndReplaysTheDraw`](../../../src/test/java/com/openggf/game/rewind/TestS2DebrisFragmentGraphRewind.java) uses frame 1/piece 1 with a decoy, distinct offsets, dimensions, tile, flips, palette, and priority. It removes the source, verifies the exact nonempty draw after reconstruction, and compares the next moving draw with uninterrupted execution. This checks renderer input, not GPU pixels or every art variant. |
| 8 | [`sonic2.objects.badniks.RexonBadnikInstance#heads`](../../../src/main/java/com/openggf/game/sonic2/objects/badniks/RexonBadnikInstance.java) | Closed with central `TRANSIENT`. Each head captures optional exact owner and next-head identities. The settled callback rebuilds the live inventory by owner and captured head index. It never compacts a missing chain slot. | [`TestS2RexonSolOwnershipRewind.progressedRexonFamiliesRestoreExactOwnersSparseChainsAndDeathDropScope`](../../../src/test/java/com/openggf/game/rewind/TestS2RexonSolOwnershipRewind.java) destructively restores two nearby families after native neck movement and interior-head retirement, checks exact identities and inventories, replays every surviving head, and scopes attack-triggered death drop to its original family. The retired-parent check tolerates absent owner and next-head identities. |
| 9 | [`sonic2.objects.badniks.SolBadnikInstance#fireballs`](../../../src/main/java/com/openggf/game/sonic2/objects/badniks/SolBadnikInstance.java) | Closed with central `TRANSIENT`. Each fireball captures its optional exact owner identity. The settled callback rebuilds the live inventory, including flying fireballs that retain their parent until unload. | [`TestS2RexonSolOwnershipRewind.solFamiliesRestoreMixedFlyingInventoriesCountersAndUnloadDetachment`](../../../src/test/java/com/openggf/game/rewind/TestS2RexonSolOwnershipRewind.java) advances real detection, animation, orbit and flight near another family, retires one child, restores fresh owners, compares exact inventories and forward movement, checks subsequent launch counters, and verifies family-specific unload detachment. The retired-parent flying check rejects adoption by a nearby live Sol. |
| 10 | [`sonic2.objects.bosses.CPZBossPipe#segments`](../../../src/main/java/com/openggf/game/sonic2/objects/bosses/CPZBossPipe.java) | Closed with central `TRANSIENT`. CPZBossPipeSegment recreation calls `reregisterRestoredSegment` on the recreated pipe. | [`TestS2CpzBossGraphRewind.cpzBossGraphRestoresWithoutDropsDoublesOrStaleReferences`](../../../src/test/java/com/openggf/game/rewind/TestS2CpzBossGraphRewind.java) destructively replaces the graph and checks stable identities, fresh objects, parent links, and the pipe's exact live segment list. Its representative graph has one segment; sparse multi-segment retract ordering remains outside this check. |
| 11 | [`sonic3k.objects.AizPlaneIntroInstance#activeWaves`](../../../src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java) | Removed the unused append-only history. ObjectManager owns each live wave; no gameplay consumer, public accessor, or unmanaged dispatch used the parent list. No transient policy or historical reconstruction is added. | [`TestS3kAizIntroInventoryRewind.liveChildrenRestoreFreshAndReplayThroughTheirRetirement`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestS3kAizIntroInventoryRewind.java), WAVES case, destroys the native managed graph and checks fresh identities, exact parent/slots, animation, motion, new spawns and retirement against uninterrupted dispatch. The final-wait/Knuckles-monitor regression now observes current managed children, not past spawns. See the AIZ follow-up below for results and boundaries. |
| 12 | [`sonic3k.objects.AizPlaneIntroInstance#emeralds`](../../../src/main/java/com/openggf/game/sonic3k/objects/AizPlaneIntroInstance.java) | Removed the unused history. Seven managed emeralds outlive the retiring intro parent. A reproduced independent restore defect required capturing the parent's and emeralds' exact Knuckles links by ObjectRefId; the histories do not repair that ownership. | The same native test covers before-scatter, seven falling emeralds and partial pickup with destructive session-registry restore and forward replay through collection. A separate managed graph test distinguishes nearby opposite-direction collectors and an intentionally unbound emerald. See the AIZ follow-up below for baseline failures and limits. |
| 13 | [`sonic3k.objects.CutsceneKnucklesLbz2Instance#supportPillars`](../../../src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesLbz2Instance.java) | Closed in the follow-up below by removing the history list. The public accessor resolves the exact captured support identity through the live managed query; retired supports and slot/coordinate replacements are absent. Scenery retains independent lifetime. Both observations are empty when services are unavailable. | [`TestLbzKnucklesInventoryRewind`](../../../src/test/java/com/openggf/game/rewind/schema/TestLbzKnucklesInventoryRewind.java) destructively restores fresh and reused managed pillars at initialization, moved swing, pre-fling and freefall boundaries, checks fresh instances/exact IDs/slots, then compares 220 forward frames. Retirement/reuse cases prevent resurrecting the pillar or adopting its replacement. See the focused results below. |
| 14 | [`sonic3k.objects.CutsceneKnucklesLbz2Instance#swingChildren`](../../../src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesLbz2Instance.java) | Closed in the follow-up below by removing the history list and observing live managed children by exact parent identity/native subtype. Pure child shells preserve child-before-owner capture order; compact capture restores native parent3 and the original swing anchor. The exact parent key uses the private optional-reference mode: absent capture tables preserve current links; present tables encode rejected/retired identities as authoritative null. | [`TestLbzKnucklesInventoryRewind`](../../../src/test/java/com/openggf/game/rewind/schema/TestLbzKnucklesInventoryRewind.java) proves four exact child identities/slots/links with a nearer competing cameo and full frame-by-frame fling/freefall replay. Partial-child retirement and both destroyed-unswept/removed-owner boundaries preserve absence, orphan expiry, and the unrelated graph. The actual compact-policy audit and disposition guard passed below. |
| 15 | [`sonic3k.objects.IczFreezerObjectInstance$FrozenPlayerBlock#debris`](../../../src/main/java/com/openggf/game/sonic3k/objects/IczFreezerObjectInstance.java) | Closed by removing the obsolete history list and its testing-only count accessor. Both callers now observe actual debris submissions to ObjectManager; live debris remains independently managed, including auxiliary-sidekick ownership. No reconstruction policy or historical inventory is introduced. | [`TestS3kIczFreezerGraphRewind.retiringBlockReplaysDebrisAllocationAndPlayerRelease`](../../../src/test/java/com/openggf/game/rewind/TestS3kIczFreezerGraphRewind.java) restores the native/auxiliary blocks with replacement player references and replays release/allocation. `nonemptyDebrisRestoresAndReplaysAfterBlockRetirement` destructively restores both real debris populations at immediate, moving and partially retired checkpoints, then compares exact identities, order, ownership, motion and animation through retirement. See the bounded follow-up below. |
| 16 | [`sonic3k.objects.IczMinibossInstance#childComponents`](../../../src/main/java/com/openggf/game/sonic3k/objects/IczMinibossInstance.java) | Closed through actual compact capture in the bounded ICZ follow-up below; no exclusion. Constructible helper-array elements and a final, preconstructed camera gate keep the existing inherited `CAPTURED` policy reachable. | [`TestIczMinibossGraphRewind`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestIczMinibossGraphRewind.java) exercises the real manager/registry with fresh reconstruction at the camera gate, progressed orbs and pending defeat workers. [`TestIczMinibossFieldDisposition`](../../../src/test/java/com/openggf/game/rewind/schema/TestIczMinibossFieldDisposition.java) requires actual capture of the helpers, gate and inherited list. Native production leaves that list empty; orbs/shards are local helpers, and the explosion controller is a separately managed object. See the follow-up for validation and limits. |
| 17 | [`sonic3k.objects.Lbz2RobotnikShipInstance#spawnedChildren`](../../../src/main/java/com/openggf/game/sonic3k/objects/Lbz2RobotnikShipInstance.java) | Closed in the bounded ship follow-up below with central `CAPTURED`. The existing identity collection codec restores the registered child inventory in order. Admission excludes failed allocations; camera/flame unload hooks remove retired identities, and settled restore reconnects those children to the exact captured ship. | [`TestLbz2RobotnikShipRewind`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestLbz2RobotnikShipRewind.java) reproduces allocation before flame removal on the original runtime. Five managed graph cases cover destructive restore and release replay, exact slots/identities, camera retirement before capture, capture after flame retirement, overlapping owners, and tolerant missing-parent recreation. The existing 13 cameo checks remain green. |
| 18 | [`sonic3k.objects.LbzMinibossBoxKnuxInstance#wallExplosions`](../../../src/main/java/com/openggf/game/sonic3k/objects/LbzMinibossBoxKnuxInstance.java) | Captured ordered helper state through a final owner-local `RewindStateful` holder. Existing explosion snapshots retain ranges, timers and queued emissions; recreation binds every controller to the current shared session RNG. Default owner/base capture stays enabled. | [`TestLbzWallExplosionsRewind`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestLbzWallExplosionsRewind.java) discriminates populated/pending loss and early DONE deletion through destructive managed restore and forward replay. See the [bounded follow-up](#lbz-knuckles-wall-explosions-follow-up) for baseline failures, shape comparison, evidence and limits. |
| 19 | [`sonic3k.objects.LbzMinibossInstance#panels`](../../../src/main/java/com/openggf/game/sonic3k/objects/LbzMinibossInstance.java) | Closed with a private owner-local `RewindStateful` holder and typed value snapshots. The default compact owner capture includes the holder; restoration constructs fresh nonstatic helpers in captured centre/ring/index order. No transient policy is used. | `TestS3kLbzMinibossGraphRewind.progressedPanelRingsRestoreFreshAndReplayRotationDetachAndRetirement` executes ten managed destructive fresh-restore cases at five progressed boundaries with Sonic/Tails attack sources, nonempty immediate draw/collision controls and 300-tick replay through detach and retirement. The focused owner disposition check confirms actual compact field capture. See the LBZ panel-ring follow-up for rejected payload evidence and renderer-input/route limits. |
| 20 | [`sonic3k.objects.LightningSparkObjectInstance#sparkTiles`](../../../src/main/java/com/openggf/game/sonic3k/objects/LightningSparkObjectInstance.java) | Owner-local `sparkArtState` captures immutable pixel/script values, including independent and null constructor recipes. Its private named `SparkArtState` helper owns all live art/script/upload fields and their capture/restore behavior; the owner has no sideband fields or exclusions. Default compact owner/base capture remains enabled. | [`TestLightningSparkArtRewind`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestLightningSparkArtRewind.java) uses the real ROM provider, destructive managed restoration, exact art values and production EGL pixels through visible/blank frames and retirement. See the [bounded follow-up](#lightning-spark-art-follow-up) and its review correction for baseline failures, rejected shapes and donor/route limits. |
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

## LBZ miniboss panel-ring follow-up

This bounded follow-up starts at `2020d2e57a4b3a4cece9083730c89eb9c71d29a4`
on `bugfix/ai-lbz-panels-rewind-20261024`, in the isolated
`t3-e8ef5527` worktree. It owns only `LbzMinibossInstance#panels` and its
focused evidence. The parent owns composition with the water, Rexon/Sol,
LBZ-ship and ICZ work, fresh combined guards, API checks, the four required
S3K stability classes, integration, release prose, push and worktree cleanup.

The actual consumers require an ordered centre plus two six-link rings.
`linkedParent`, `resolvePanelAnchor` and `detachAndPropagate` address original
positions, including retained deleted helpers. Update, collision publication
and bucket-filtered drawing consume their progressed values. Re-running
initialization loses timers, callback phases, fractional positions, rotation
direction, detach signals and flight lifetime. Generic construction of a
nonstatic inner helper cannot establish its enclosing miniboss.

Two complete shapes were compared. The existing `LbzMinibossBoxRig` implements
`RewindStateful` around owner-bound pieces and captures typed values; its
constructor supplies a fixed piece shape. The selected private `PanelRingState`
uses that same value-snapshot boundary but rebuilds the exact captured list,
including empty pre-init state and the retained deleted entries. Each fresh
`PanelState` is explicitly constructed against the restored miniboss using its
captured centre/ring/index, then receives every mutable value. Capture returns
a typed record with a defensively copied array of scalar/enum records, matching
the box rig's codec-compatible snapshot shape. It carries no helper or renderer
references and invents no retired history. Direct reflective collection
restoration and shallow helper copies were rejected because neither supplies
the right enclosing owner.

There is no handwritten object capture override or extra that could disable
generic owner capture. The final holder uses the existing stateful codec through
`AbstractObjectInstance`'s default compact subclass path, alongside the parent's
motion, boss scalars, explosion-state holder and managed Knuckles parent link.
Base destruction/spawn/slot/respawn state remains on the base snapshot path.
The focused disposition method checks both eligibility predicates, the actual
captured `panels` field plan and the owner's complete gap list. No transient
policy or public API pin is changed. The runtime update, allocation, deletion,
clock, collision and renderer ordering code is unchanged.

The new managed graph regression initializes through `ObjectManager`, with
distinct concrete Sonic and Tails test sprites passed as main/sidekick and
alternated as attack sources. It captures five boundaries for each attacker:
257 ticks after initial placement (initial wait just borrowed), 282 ticks
(progressed rotation), third-hit detach pending, 17 ticks of detached flight,
and 128 ticks (near detached lifetime expiry). It first proves fourteen
nonempty draw calls and live arm collision regions, then records 300 manager
updates through rotation, the three-hit second-ring detach and all six links'
retirement. It removes the source, adds a divergent boss, restores through a
real `RewindRegistry`, requires a fresh boss and fresh owner-bound helpers,
compares immediate ordered draw arguments/collision regions, and compares every
helper scalar/enum plus parent scalars/motion/spawn/slot on each forward tick.

The first unchanged-runtime selection ran 13 tests: three existing passes,
six empty-restored-ring failures, and four test-only premature-attack failures.
The native body collision gate correctly ignored those early attacks. The test
was corrected to attack at replay ticks 60/94/128 and replay 300 ticks instead
of 240. Runtime source was still unchanged. The corrected baseline ran the same
13 identities: all ten new cases failed `fresh restore must retain progressed
rings`, expected 13 and observed 0; the existing three passed. Both runs had
zero errors and skips. Queue waits were 77 and 10 seconds; Maven reported
80 seconds and 25.344 seconds respectively, separately from waiting.


The first post-change selection ran 34 cases: 21 passed, 13 errored, zero
assertion failures and zero skips. The initial holder returned an immutable
`List` directly. The stateful codec calls `deepCopySnapshotValue`, which rejects
`java.util.ImmutableCollections$ListN`; all eleven graph capture/restore cases
and both native-player fatal-hit rewind cases failed with that exact error.
This payload shape was rejected. The final holder uses the existing box-rig
record/defensively-copied-array shape; no shared codec change was made. Its
remaining 21 checks included the field plan, part buckets, native init/tracking,
arm waits, linked drawing, collision phases, detach and defeat. Queue waiting
was 325 seconds; Maven reported 82 seconds of execution. After this localized
capture-format correction, only the affected capture/restore checks and the
exact owner disposition check are repeated.


The final affected selection passed 16 cases with zero failures, errors or skips:
all thirteen LBZ graph cases, the exact owner disposition case, and both
`fatalHitInstallsWaitBeforeFirstDecrementForNativePlayersAndRewinds` cases using
actual ROM-backed Sonic/Tails team members and the gameplay registry. Queue
waiting was 72 seconds; Maven reported 81 seconds of execution. The completed
selections cover 34 distinct identities, with the eighteen unaffected behavior
cases retained from the earlier selection and sixteen passing final cases.
There was no single all-green 34-case invocation. The ten new graph cases compare
all 21 helper values and reject every stale source helper. Their draw evidence
is ordered renderer arguments, not GPU pixels; their test sprites do not certify
player physics, automatic touch callbacks, a complete act route, all donor/team
variants or independently allocated native child-slot admission. Those inherited
route/graph obligations remain outside this one-field cleanup.

The exact selections, using the verified existing main-checkout ROMs and its
current queue wrapper from this worktree, were:

```bash
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
export LUA_BIN=/usr/bin/lua5.4
# MAIN_CHECKOUT identifies the existing main checkout; no ROM links are created.
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kLbzMinibossGraphRewind' "-Dsonic1.rom.path=$MAIN_CHECKOUT/s1.gen" "-Dsonic2.rom.path=$MAIN_CHECKOUT/s2.gen" "-Ds3k.rom.path=$MAIN_CHECKOUT/s3k.gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kLbzMinibossGraphRewind,TestLbzMinibossPartBuckets,TestRewindFieldDispositionAudit#lbzPanelHolderIsCapturedWithoutDisablingTheDefaultOwnerPath,TestS3kLbz1MinibossAndTransitionHeadless#miniboss*+fatalHit*,TestS3kLbz1KnucklesSequenceHeadless#lbzMiniboss*' "-Dsonic1.rom.path=$MAIN_CHECKOUT/s1.gen" "-Dsonic2.rom.path=$MAIN_CHECKOUT/s2.gen" "-Ds3k.rom.path=$MAIN_CHECKOUT/s3k.gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kLbzMinibossGraphRewind,TestRewindFieldDispositionAudit#lbzPanelHolderIsCapturedWithoutDisablingTheDefaultOwnerPath,TestS3kLbz1MinibossAndTransitionHeadless#fatalHitInstallsWaitBeforeFirstDecrementForNativePlayersAndRewinds' "-Dsonic1.rom.path=$MAIN_CHECKOUT/s1.gen" "-Dsonic2.rom.path=$MAIN_CHECKOUT/s2.gen" "-Ds3k.rom.path=$MAIN_CHECKOUT/s3k.gen" test -B
```

The first command ran twice before runtime edits, with the test correction
recorded above. The second exercised the rejected list payload; the third
verified the final typed-array payload. Exact XML identities, failures and skips
were inspected for every completed run, then consumed reports were removed.
No live job, retained diagnostic archive or pass cache remains. A change-based
dry plan against the pinned base selected 2,986 smoke classes; bounded local
capture behavior and this delegation's explicit limits justify the focused
selection. No broad smoke run, full guard profile, API gate, four-class S3K
stability selection or release-only full suite ran here. Only the positively
verified `LbzMinibossInstance#panels` baseline key is removed; other rows, totals
and obligations are unchanged by this follow-up. Parent composition and delivery
remain pending.

## Bounded S1 ring recipe follow-up, 2026-10-24

This follow-up starts from local `develop` at
`2020d2e57a4b3a4cece9083730c89eb9c71d29a4`, on
`bugfix/ai-s1-ring-rewind-disposition-20261024` in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-bdfa5c84/.worktrees/s1-ring-rewind-disposition-20261024`.
Only the S1 ring audit row and focused tests change. Runtime, policy and the
`Sonic1RingInstance#childRingSpawns` baseline entry remain unchanged. The parent
owns composed totals, broad validation and delivery.

The earlier row conflated placed and dynamic reconstruction. A placed parent's
`ObjectManagerSnapshot.PerSlotEntry` recreates through
`Sonic1ObjectRegistry.create(spawn)`. The factory reads the **current level's**
`RingObjectPlacementMapping`, falling back to its locally supplied mapping only
without that provider. `Sonic1RingPlacement.extract` supplies immutable canonical
group lists with individual `RingSpawn.placementId` values. The parent constructor
retains those canonical objects. Its INIT recipe therefore survives destructive
placed restoration without capturing this list. Dynamic entries instead use
`recreateForRewind`; an ordinary progressed child has no children to reconstruct.

Construction and producer inventory, including direct callers:

- The public `(ObjectSpawn, List<RingSpawn>)` parent constructor starts in INIT.
  The registry is its only production caller. It also supports direct tests in
  [`com.openggf.tests.TestSonic1RingInstance`](../../../src/test/java/com/openggf/tests/TestSonic1RingInstance.java)
  (including unmanaged updates with no ObjectManager),
  [`com.openggf.game.sonic1.objects.TestSonic1RingInstance`](../../../src/test/java/com/openggf/game/sonic1/objects/TestSonic1RingInstance.java)
  (public parent construction registered dynamically), and
  [`TestStageplayObjectRingProvenance`](../../../src/test/java/com/openggf/level/objects/TestStageplayObjectRingProvenance.java)
  (a dynamically registered singleton recipe). These tests do not establish
  destructive pre-INIT restoration of an arbitrary caller-supplied group.
- The package-private `(ObjectSpawn, RingSpawn, int)` child constructor starts
  in ANIMATE with an empty recipe. `spawnChildren` calls it through both managed
  reserved-slot allocation and the unmanaged `spawnChild` fallback;
  `recreateForRewind` also calls it. The package-local sparkle test constructs it
  directly. The private no-argument constructor delegates to this shape for the
  generic recreation probe. A repository-wide Java search found no other
  constructor or subclass producer.

[`TestS1RingGroupGraphRewind`](../../../src/test/java/com/openggf/game/rewind/TestS1RingGroupGraphRewind.java)
loads GHZ1 from the verified S1 World REV01 ROM, selects a real three-ring layout
entry and retains the current level's canonical mapping. A deliberately stale
registry-local mapping is a positive control for current-level ownership. The
test isolates that real placement in a managed ObjectManager, enables S1
counter-based respawn, disables in-place restore and composes ObjectManager and
RingManager in a RewindRegistry. It asserts the actual pre-INIT parent is a
placed entry, there are no dynamic entries yet and production capture has a
non-null compact field blob.

Four cases collect no rings, the parent, one child, or the complete group before
INIT. Destructive restore rebuilds the exact canonical parent/child recipe;
forward dispatch matches the uninterrupted allocation order, slots, rewind IDs,
collection bits and complete respawn bytes. Collected children consume no slot;
only the fully collected group retains its respawn-block bit. These expectations
follow `Ring_Main`, `Ring_MakeRings` and `Ring_SpawningDone` in the shipped
`FixBugs = 0` ring routine. Two more cases touch a live child, capture progressed
state, erase and diverge the managed graph, then restore fresh objects. Every
frame through sparkle retirement matches the uninterrupted graph, including
stable rewind identities, canonical RingManager references, the parent anchor,
slot occupancy and the dynamic identity counter. No duplicate children appear.

The field-wide decision remains **retained**. Constructor-owned canonical recipes
are sufficient for the tested stock placed path. A blanket `TRANSIENT` policy
would also cover the public constructor's caller-supplied recipe: it accepts an
arbitrary list and retains a `subList` view, without requiring canonical placement
IDs, matching subtype expansion, or immutable caller ownership. No current
production producer uses a dynamic pre-INIT group, and this follow-up does not
claim one as a normal layout gameplay defect.

A captured immutable recipe is a different contract: it would need to retain the
exact supplied sequence and individual placement identities, then rebind to the
correct canonical RingManager objects. Default capture currently drops this
`List<RingSpawn>`; changing its policy alone supplies no list codec. Capturing
only coordinates/subtype would discard caller recipe and placement identity.
Capturing record values alone would also require canonical rebinding, since a
new value-equal record is not the identity-keyed placement owner. The current
dynamic resolver selects by coordinates, so duplicate-coordinate identities are
an additional unqualified case. No generic codec, custom capture override,
allowance or new exclusion was added to evade the actual field audit. The
remaining evidence/design boundary is arbitrary public/unmanaged recipe
restoration and its canonical identity contract; the baseline key stays visible.

The first focused request used the following environment and exact selection.
`MAIN_CHECKOUT` and `ROM_ROOT` identify the existing main checkout and ROM
directory, as above; no machine-local home path is required in these commands.

```bash
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1RingGroupGraphRewind' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.tests.TestSonic1RingInstance,com.openggf.game.sonic1.objects.TestSonic1RingInstance,com.openggf.game.sonic1.TestSonic1RingPlacement' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
```

The first completed invocation passed all six parameterized cases, with zero
failures, errors or skips; exact XML testcase identities and skip elements were
inspected. It waited 64 seconds for admission and Maven reported 78 seconds of
execution including initial compilation. Runtime source was unchanged at the
base SHA; the new graph test was the executable source delta. ROM identity was
CRC32 `AFE05EEE`, SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`.

The second invocation passed all 15 existing constructor/collection/placement
controls: 12 in `com.openggf.tests.TestSonic1RingInstance`, two in the package-local
object test and one placement-mapping test. Exact XML identities confirmed zero
failures, errors and skips. It waited 38 seconds for admission and Maven reported
27.662 seconds of execution. These are separate queue/execution intervals, not
combined delivery elapsed time. All 21 distinct focused cases passed across the
two selections; no single 21-case invocation was run. Consumed Surefire reports
were inspected and removed before handoff. No category diagnostics were created.

This is bounded graph evidence for a real GHZ1 three-ring placement and ordinary
progressed children. It does not certify arbitrary public recipes, overlapping
placement identities, allocation exhaustion, every orientation/group size,
offscreen respawn routes, GPU pixels, or the complete S1 route matrix. No broad
smoke, complete guard profile, API/S3K selection or release-only full suite ran.

## AIZ intro inventories follow-up

This work is bounded to `AizPlaneIntroInstance#activeWaves` and `#emeralds`,
on base `2020d2e57a4b3a4cece9083730c89eb9c71d29a4`, branch
`bugfix/ai-aiz-intro-rewind-inventories-20261024`, supplied T3 isolation worktree.
Other audit rows, aggregate accounting and delivery-wide validation belong to the
parent composition.

Producer/consumer tracing found that both fields only received newly allocated
objects and were cleared by `onUnload`; neither list directed updates, deletion,
rendering or respawn. The only reader was the reflection-based wave-count test,
which counted historical spawns rather than live waves. There was no public
inventory accessor. `spawnDynamicObject` hands accepted children to the owning
ObjectManager; when no manager exists it does not dispatch them. Unmanaged direct
intro calls never updated children by iterating these lists. Clearing them did
not destroy a child or release a slot. Waves read the live parent scroll word and
self-delete; emeralds use their own motion/phase and the cutscene Knuckles collector
and deliberately continue after the intro parent unloads.

The smaller responsibility is to remove both redundant histories, rather than
retain a diagnostic cache requiring a second rewind policy. No old retired-child
inventory is fabricated. The wave test now compares real live managed child
identities before/after both native callback windows. Its local observation means
current, managed, non-destroyed children at that call, with no promise of history
or persistent list identity across restore. Existing gameplay accessors are
unchanged; no new runtime inventory API, transient allowance or renderer handle
enters a snapshot.

The initial five-case native session-registry run on unchanged base runtime
reproduced three failures, zero errors and zero skips. WAVES and the distinct
Stageplay player-owner case passed. BEFORE_SCATTER diverged at forward dispatch 2,
the explosion boundary: the restored parent had lost its Knuckles reference.
FALLING_EMERALDS and PARTLY_COLLECTED_EMERALDS immediately restored each emerald's
collector as null. These links were classified as structural solely by the
existing `knuckles` field-name policy and neither recreate path rebuilt them.
The positive uninterrupted runs landed and collected all emeralds; this was a
live ownership defect rather than evidence for storing retired children.

The correction uses two exact `CAPTURED` entries for the parent and emerald
`knuckles` fields, letting the existing two-phase identity codec resolve the
original collector even when restored later. No search by proximity or history
is used. The native trigger remains at `AIZPlaneIntro_Finale`
(sonic3k.asm:135760-135787); wave scroll/animation/deletion remain at
`AIZPlaneIntro_WaveChildMain` (sonic3k.asm:135894-135905), and the emerald collector
read remains at `AIZPlaneIntro_EmeraldChildWaitForPlane`
(sonic3k.asm:135941-135960). No native clock, RNG call, allocation, update order,
delete/respawn routine, or player-release behavior changes.

`TestS3kAizIntroInventoryRewind` boots the actual native 320px Sonic+Tails intro,
uses the session registry (including real player/sidekick identity registration),
and disables placed-instance reuse. It compares live object identities, slots,
parent/collector links, motion/subpixels, wave animation/deletion callbacks,
Knuckles animation and player control/mapping/hurt state at every replayed
dispatch. It removes manager dynamics and leaves an unmanaged singleton decoy
before restoring. The four capture points are independent; active-wave replay
continues for 30 dispatches, and the other cases run until emerald retirement,
with a 600-dispatch watchdog. The Stageplay case restores an actual registered
sidekick as the intro's control owner while focus remains on P1, then releases
only that owner.

The focused graph test also checks the existing wave recreation boundary: a
managed selected intro is accepted, an ambiguous selection or stale unmanaged
singleton is rejected. Native gameplay owns one intro; this does not claim
support for simultaneously running multiple full intro cinematics. The collector
test uses two nearby Knuckles objects with opposite velocities, restores the exact
bound owner, and preserves an explicitly null owner without guessing.

Validation commands use the environment above, with Java 25, Lua 5.4 and unset
DISPLAY, WAYLAND_DISPLAY, JAVA_TOOL_OPTIONS and JDK_JAVA_OPTIONS. `MAIN_CHECKOUT`
is the existing main checkout supplying current admission tooling; `ROM_ROOT`
is the discovered existing root ROM directory. The locked-on S3&K ROM's CRC32
`63522553` and SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` were verified.

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dtest=TestS3kAizIntroInventoryRewind "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kAizIntroInventoryRewind,TestS3kAizIntroGraphRewind,TestAizPlaneIntroInstance,TestAizEmeraldScatterInstance,TestAizIntroPlaneChild,TestAizIntroArtLoader,TestRewindFieldDispositionGuard' "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The first request waited 303 seconds; Maven reported 1 minute 23 seconds including
both compilation phases. Its XML cases were inspected and consumed reports
removed. The second selection passed all 42 cases across seven classes with zero
failures, errors or skips. Its request waited 68 seconds; Maven reported
1 minute 24 seconds including compilation. XML inspection confirmed every native
restore case, the collector/owner boundaries, existing intro/art checks and the
disposition guard executed. The generated disposition report exactly matched
the local baseline with only the two assigned keys removed. Consumed XML and the
field report were removed; no pass cache or archived raw output is retained.
The three new disassembly citations validate against pinned skdisasm `8b2cf3c`;
the audit file also contains two pre-existing unverifiable citations outside
this follow-up. `git diff --check` passes.
This is bounded intro/ownership evidence, not a complete AIZ→HCZ route, GPU pixel
verification, broad smoke selection, all-guards run or public-release suite.
The parent owns combined validation and the four mandatory S3K stability checks.

### Public context compatibility and rejected collector admission correction

Independent review of `493aeddd7` against `2020d2e57` identified two boundaries
that the managed native tests above did not exercise. This corrective follow-up
starts at parent composition `e849ed5a40c9313844f0bfb9451d1c92890001f9`, reached by
fast-forwarding the same clean AIZ worktree/branch. It changes neither inventory
closure nor aggregate disposition accounting.

The inherited public `captureRewindState()` / `restoreRewindState(snapshot)` and
the forms explicitly given `RewindCaptureContext.none()` are usable without an
identity table. Both newly captured AIZ collector fields broke those existing
forms: nonnull references threw, and a snapshot taken with a null collector
cleared a later-bound current collector. For the intro, a before-init round trip
is only a non-discriminating null-link control, and player ownership already
required player identities before `493aeddd7`. The discriminating legal phase is
after native routine `$16` has created live Knuckles and
`releasePlayersForVisualDirection()` has cleared `ownedPlayer`. Both public
capture forms regressed there.

New tests ran on unchanged `e849ed5a4` runtime **before** the production edit:
26 cases, 11 failures, zero errors and zero skips. Four direct-codec failures
established bound no-context capture and initially-null/current-divergent restore
regressions for both fields. Six public-form failures covered bound and initially
null emeralds and the released intro with live Knuckles. The seventh public-test
failure used the real native ObjectManager with all dynamic slots reserved. The
existing allocation attempt marked its candidate destroyed before registration,
but the intro retained it; capture before any child dispatch failed with
`RewindIdentityTable has no registered id for object reference`. The same test's
one-free-slot positive control captured an admitted, registered routine-0 child.
The before-init and during-player-ownership controls passed and are not counted
as evidence of the regression. Managed nonnull capture-ID validation, missing
restore-target/context rejection, exact replacement binding and captured-null
clearing also passed on that red runtime.

The existing private `ObjectReferenceCodec` now has three private modes. The
default strict encoding/validation remains unchanged. The three S2 optional-native
fields retain their existing wire markers and missing-target behavior. Exactly
the two AIZ collector fields select the context-compatible, managed-strict mode:
without a capture table, one uncaptured marker is written and restore leaves the
**current** field untouched, even if a later restore supplies a table. With a
capture table, the existing strict identity codec records the value; unregistered
nonnull capture and unresolved/mistyped nonnull restore targets still throw.
Authoritative table-captured null clears a divergent current link. The AIZ keys
are reused in the exact `CAPTURED` metadata; the closed selection test checks both
fields reach the real compact schema and neither belongs to the S2 optional set.
There is no public enum, new public mode, manual object snapshot override,
annotation, new framework, API pin or expanded allowance.

The intro owner handles the demonstrated allocation failure immediately **after**
the existing spawn attempt, clearing its collector link if that attempt destroyed
the candidate. The native attempt order, routine advance, flags and successful
retention remain unchanged. No optional-target codec hides the rejected object.
The real full-slot test captures before child dispatch; the paired success case
checks identity registration, the unexecuted child routine and slot occupancy.
Arbitrary early retirement of an otherwise admitted collector remains a separate,
unproven lifecycle obligation. This change neither certifies that lifecycle nor
masks it: a managed nonnull reference missing from the table is still an error.

Both requests use the Java 25 / Lua 5.4 environment above, with DISPLAY,
WAYLAND_DISPLAY, JAVA_TOOL_OPTIONS and JDK_JAVA_OPTIONS unset and the required
surfaceless-EGL property. The existing absolute locked-on ROM was reverified at
CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
`MAIN_CHECKOUT` supplies the current queue wrapper; `ROM_ROOT` is the discovered
existing main-checkout ROM directory. Commands are run from the AIZ worktree:

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestAizCollectorObjectReferenceCodecs,TestAizCollectorPublicRewind' "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestAizCollectorObjectReferenceCodecs,TestAizCollectorPublicRewind,TestOptionalObjectReferenceCodecs,TestRewindObjectReferenceCodecs,TestS3kAizIntroInventoryRewind,TestS3kAizIntroGraphRewind,TestAizPlaneIntroInstance,TestAizEmeraldScatterInstance,TestAizIntroPlaneChild,TestAizIntroArtLoader,TestRewindFieldDispositionGuard,TestCapturedPolicyCompactReachabilityGuard#contextCompatibleCollectorsHaveClosedExactCapturedMetadata+optionalNativeLinksHaveClosedExactCapturedMetadata+capturedPoliciesNeedingCompactPathAreReachable,TestRewindArchitectureGuard' "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The red request had zero reported queue wait; Maven reported 1 minute 27 seconds
including compilation. Its 26 XML cases were inspected and consumed reports
removed. The corrective focused selection passed 106 cases across 13 selectors,
with zero failures, errors or skips. It also had zero reported queue wait; Maven
reported 1 minute 28 seconds including compilation. XML inspection confirmed all
26 new regression/control cases, all 42 original AIZ checks (including the 27
intro graph/native/Stageplay cases), 27 S2 optional-codec cases including unchanged
wire vectors, four strict object-codec cases, three selected metadata/reachability
cases and four rewind architecture checks. The disposition report matches the
unchanged parent baseline. Consumed reports are removed; no pass cache or raw
archive is retained. `git diff --check` passes. The parent owns the
composed API check, required S3K stability checks and a complete fresh guard run;
none of those has been submitted by this worker. These focused checks do not
replace the parent's delivery-wide validation or certify a full AIZ→HCZ route.

## LBZ Knuckles wall explosions follow-up

This bounded change starts at develop `2020d2e57a4b3a4cece9083730c89eb9c71d29a4`
in T3 checkout `t3-697d8e8b`, branch
`bugfix/ai-lbz-wall-explosions-rewind-20261024`. It closes only
`LbzMinibossBoxKnuxInstance#wallExplosions`; composed debt totals and other
workers' rows remain the parent's responsibility.

The prediction was reproduced before changing runtime. The new
`TestLbzWallExplosionsRewind` ran five cases with four failures, zero errors and
zero skips. `pendingWallEmissionsRestoreFreshInOrderAndReplayAgainstCurrentSharedRng`
captured five controllers with queued effects and restored zero controllers.
`doneWaitsForAllFiveControllersAfterDestructiveRestore` at elapsed controller
ticks 0, 4 and 89 restored a box that deleted on the next pass while its control
remained alive. The tick-95 case passed: the control also deletes on the next
pass, the 32nd three-frame timer boundary (tick 96). This boundary control alone
would not discriminate the empty-list defect.

Two complete supported shapes were compared. A handwritten
`ObjectSubclassRewindExtra` could compose superclass state with phase, optional
rig snapshots, arm/defeat/chunk-swap counters, max-Y state and the ordered
explosion snapshots. That duplicates existing owner capture and disables its
default subclass-field audit. The selected final owner-local `RewindStateful`
holder uses the existing `S3kBossExplosionController.Snapshot` and
`fromSnapshot(snapshot, sharedRng)` contract. It stores ordered values, clears
divergent entries and constructs fresh controllers against current
`services().rng()`. The registry restores the shared cursor separately; no
controller restores or clones that cursor independently. Snapshot values contain
no live controllers, mutable list aliases, renderer handles or callbacks.

The panel holder at `57fa987e795d6b853252a8380e2d0e97805e0aa4` was read-only
precedent for this shape. Unlike that miniboss, this box uses the default generic
fallback: its replaceable rig fields do not satisfy compact fresh-allocation
requirements. The first focused run exposed an incorrect compact-only assertion
in the new audit test. The corrected
`wallExplosionHolderRemainsCapturedByTheDefaultOwnerPath` checks the field plan
of the path actually used and keeps the entire owner in the disposition audit.
No policy allowance, capture override or unrelated rig change was introduced.

The new replay proof uses real ObjectManager updates and registry capture/restore
with in-place reuse disabled. It compares fresh helper identities and every
snapshot value, owner identity/slot/respawn state, default scalar restoration,
and per-pass child coordinates, slots, raw animation clocks, explosion SFX,
shared RNG cursor, manager clocks and no-respawn deletion timing. An unrelated
shared RNG consumer advances each pass; the pending case also replaces the
service RNG before restore and verifies every controller binds to that current
instance. Reusing the same snapshot after another complete forward run checks
that captured pending effects did not alias the advancing controllers.

The first focused selection ran 49 tests: all 48 behavioral tests passed, and
only the new compact-only audit assertion failed. After correcting that test,
its exact method passed separately with no failures, errors or skips. Runtime
and behavioral tests were unchanged between these runs. These results cover
49 distinct tests across nine selected classes; they are not one all-green
49-test invocation. The selections were:

```bash
# Baseline on unchanged runtime: SELECTOR=TestLbzWallExplosionsRewind
# First candidate selection:
SELECTOR='TestLbzWallExplosionsRewind,TestS3kLbzMinibossGraphRewind,TestS3kBossExplosionController,TestS3kBossExplosionChild,TestGameRngRewindSnapshot,TestGameRngGoldenSequence,TestLbzMinibossPartBuckets,TestS3kLbz1MinibossAndTransitionHeadless,TestRewindFieldDispositionAudit#wallExplosionHolderRemainsCapturedByTheDefaultOwnerPath'
# Corrected audit-only selection:
# SELECTOR='TestRewindFieldDispositionAudit#wallExplosionHolderRemainsCapturedByTheDefaultOwnerPath'
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$SELECTOR" \
  "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" "-Dsonic2.rom.path=$ROM_ROOT/s2.gen" \
  "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" test -B
```

The existing three root ROMs were SHA-1 verified against AGENTS.md. All commands
used Java 25 and the shared main-checkout Maven wrapper from this checkout's own
cwd/target. Baseline admission waited 52 seconds and Maven reported 1:21;
candidate admission waited 14 seconds and Maven reported 1:24; corrected audit
admission waited 11 seconds and Maven reported 26.822 seconds. Waiting and Maven
execution are separate intervals, not total delivery time.

Consumed per-class XML and text reports were inspected and removed. No category
run, pass cache or archive was created. The pending-effects case deliberately
ticks helpers without draining to test their complete value contract; normal
owner updates drain immediately. The tests seed the fight/DONE phases to isolate
this gate and do not certify full Knuckles arena entry, sign/results handoff,
rig visibility, GPU pixels or complete LBZ routes. Existing encounter/graph
checks provide bounded surrounding coverage. Parent-owned composed guards,
API checks, four required S3K stability checks, coverage reconciliation,
integration, push and worktree cleanup remain pending.

### Empty public-object restore compatibility correction

The parent-supplied read-only review (reference
`0c01e8fc595cf7f1c75abf36c20597714464631d120310e9c5a9784f36a5fa05`)
identified a compatibility edge in `6cf7d4bbe8870cd998316df8bdd14e664e1cefbc`:
an empty public box can capture without services or an identity table, but its
new holder unconditionally looked up services during restore. Both inherited
public restore overloads therefore failed even when there was nothing to rebuild.

Before changing runtime, the ROM-free `TestLbzWallExplosionsEmptyRewind` ran
three tests with three failures, zero errors and zero skips. Same-instance
restore through the overload without a context argument and fresh-object restore with
`RewindCaptureContext.none()` both threw `services not available`. The third
control restored an empty snapshot over a divergent populated list and trapped
the unwanted RNG lookup with `empty restore must not look up the service RNG`.

The correction still clears existing entries first, reads the defensive
`state.controllers()` array once, and returns if it is empty. Populated snapshots
still require current injected services so their controllers can share the
session RNG; this change does not make populated unbound restore supported.
Controller order, timers, pending emissions, default owner/base capture and the
single-key baseline delta are unchanged.

Both invocations used the same Java/Lua/display/JVM environment and absolute
root ROM properties as the earlier follow-up. The baseline selector was only
`TestLbzWallExplosionsEmptyRewind`; the corrective selection was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestLbzWallExplosionsEmptyRewind,TestLbzWallExplosionsRewind,TestS3kLbzMinibossGraphRewind,TestRewindFieldDispositionAudit#wallExplosionHolderRemainsCapturedByTheDefaultOwnerPath' \
  "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" "-Dsonic2.rom.path=$ROM_ROOT/s2.gen" \
  "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" test -B
```

The corrective selection passed all 12 tests across four classes with zero
failures, errors or skips, including all three empty-state controls, all five
populated managed replay cases, the three existing miniboss graph checks and
the exact default-capture audit method. Baseline and corrective admission each
waited zero seconds; Maven reported 27.101 seconds and 1:22 respectively,
including compilation. Per-class XML identities and outcomes were inspected,
then consumed XML/text reports were removed. The earlier 48-test behavioral
selection and parent guard profiles were not repeated. Parent-owned broad gates,
integration, push and worktree cleanup remain pending.

## ICZ freezer debris follow-up

This bounded follow-up starts at `2020d2e57a4b3a4cece9083730c89eb9c71d29a4`
in `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-679c48f4`, on
`bugfix/ai-icz-freezer-debris-rewind-20261024`. It owns the frozen block's debris
inventory, the two accessor callers, focused graph evidence, this row/append and
one exact baseline key. The parent retains combined guards, API and required S3K
checks, release/coverage prose, integration, push and worktree cleanup.

The repository-wide producer/consumer census found one append site and only two
reads, both `debrisSpawnedForTesting()` assertions in `TestS3kIczFreezerObject`.
There is no runtime, tooling or Stageplay history consumer. Those tests now
observe the twelve IceDebris registration attempts through their manager, while
the graph tests observe the live managed population. This preserves the tests'
spawn-count evidence without inventing a retired-block diagnostic contract.
A narrow reconstruction disposition was rejected: the block has no gameplay
consumer of this list, and rebuilding it from surviving debris would falsely
represent history after retirement. Removing the list and migrating every caller
is simpler and accurate. No `TRANSIENT` exclusion, manual capture override,
mutable/shallow snapshot, strict-reference relaxation or API pin change is used.

ROM loc_8A8BA (sonic3k.asm:188502-188509) releases the player, creates children
and deletes the block. ChildObjDat_8AAEA (sonic3k.asm:188757-188782) defines twelve
children; loc_8A8DC (sonic3k.asm:188512-188526) installs their independent routine.
The existing twelve native `spawnChild` calls and auxiliary registration calls
stay in the same loop/order. Native allocation attempts, Stageplay child
notification, spawn-pass dispatch, respawn flags and retirement paths are
unchanged. The tests independently check subtype order 0..22, native SSTs after
the block versus auxiliary slot -1, engine ownership and absent player ownership.

The graph fixture uses the real cloud producer for a native P2 or engine P3
capture, then removes the cloud/freezer before the landed block boundary.
In-place reuse is disabled. Two pre-break cases restore fresh blocks and current
replacement player identities, then compare release flags, attacker bounce,
invulnerability, exact debris identities/order/slots, spawn-pass motion and RNG.
Six nonempty cases capture after 0, 7 or 20 moving ticks; the last pair require
partial retirement. They discard the source and divergent same-class populations,
restore fresh debris without the retired block, and compare every forward tick
for 80 ticks through complete no-respawn retirement. Motion fractions/velocities,
gravity and raw animation scalars are compared as immutable values. Captured
entries must actually carry the default compact blob, with no generic fallback.
The registered fixture RNG is restored after reconstruction through the existing
registry contract, and the tests check its seed explicitly.

The first test-only run on unchanged runtime source ran eight cases: six debris
cases passed, and two pre-break cases errored because restoring replacement
sprites lacked an active gameplay timer service. After supplying that service,
the two-case request had one native animation mismatch and one pass. Only raw
animation scalars differed: the fixture had omitted its constructor-consumed RNG
from the registry. Registering that existing RNG made both pre-break cases pass
on unchanged runtime source. These were fixture corrections, not attributed
freezer runtime regressions; no unrelated restore change was made. The respective
queue waits were 7, 0 and 0 seconds; Maven reported 82, 23.587 and 22.934 seconds
of execution including compilation. These intervals are separate.

All commands ran from this checkout, with source fixed while each job ran.
The leased version probe reported Java 25.0.5; `/usr/bin/lua5.4 -v` reported
5.4.9. The existing absolute ROM files below were read in place and matched all
three AGENTS.md CRC32/SHA-1 pairs. No ROM was copied or linked. The following
arrays spell out the common environment, wrapper and ROM arguments.
`MAIN_CHECKOUT` substitutes the verified existing main-checkout absolute path;
its ROM files are the exact verified files used by these commands. This follows
the repository policy against committing machine-local user-home paths.

```bash
MAIN_CHECKOUT=/path/to/existing/OpenGGF-checkout
mvnq=(env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py")
roms=("-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen"
  "-Dsonic2.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog 2 (W) (REV01) [!].gen"
  "-Ds3k.rom.path=$MAIN_CHECKOUT/Sonic and Knuckles & Sonic 3 (W) [!].gen")
"${mvnq[@]}" -v
"${mvnq[@]}" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kIczFreezerGraphRewind#retiringBlockReplaysDebrisAllocationAndPlayerRelease+nonemptyDebrisRestoresAndReplaysAfterBlockRetirement' "${roms[@]}" test -B
# Run twice: after timer fixture correction, then after RNG registration.
"${mvnq[@]}" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kIczFreezerGraphRewind#retiringBlockReplaysDebrisAllocationAndPlayerRelease' "${roms[@]}" test -B
"${mvnq[@]}" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kIczFreezerGraphRewind,TestS3kIczFreezerObject,TestRewindFieldDispositionGuard,TestS3kIczEndBossGraphRewind#iczEndBossRestoresQueuedExtendedSidekickFrostCapture' "${roms[@]}" test -B
```

The final focused selection passed all 44 tests with zero failures, errors or
skips: 17 freezer graph cases (including all eight new parameter cases), 25 freezer
behavior cases, the one selected boss frost check and the disposition guard.
The generated field report exactly matches the 14 remaining baseline keys;
only `IczFreezerObjectInstance$FrozenPlayerBlock#debris` was removed in this
follow-up. Nine of the original 23 obligations are now closed at this boundary.
The request waited 45 seconds; Maven reported 82 seconds of execution including
compilation. The final four XML reports and the generated report were inspected.
The parent receives the consumed files under this checkout's `target/` for its
requested cleanup; this worker does not remove artifacts, worktrees or branches.
No category run or validation receipt was created. Audit local links, all five
disassembly citations and `git diff --check` passed.

The measurement-hazard table was read before attributing results. These are
bounded object/registry checks at a
320-pixel viewport with TestablePlayableSprite participants and a landed-block
setup; no complete ICZ route, character/donor/viewport matrix, terrain carry,
GPU pixels or ROM parity expansion is certified. No broad smoke selection,
complete guard profile, API check, required S3K combination or release-only full
suite belongs to this worker. The checkout and local branch remain for the parent.

## LBZ Knuckles optional box rigs follow-up

This dependent slice starts at local loop-five candidate `e143088fa` in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-6a06ac03`, branch
`bugfix/ai-lbz-box-rig-rewind-20261024`. The clean T3-prepared branch was
fast-forwarded from delivered `a236a1276`; ancestry includes both wall repairs
`6cf7d4bbe` and `028cf0916`. No nested checkout or integration is part of this
slice. The initial grounding was **source prediction**, not an observed failure:
the owner constructor leaves both rigs absent until the native camera gate,
while generic stateful restoration requires an existing target when a snapshot
is present. The earlier wall tests seed FIGHT/DONE with absent rigs and cannot
establish initialized encounter restoration.

The bounded oracle uses actual ObjectManager camera-range/arm dispatch to
initialize the two boxes and spawn the miniboss pair. It requires live progressed
piece draw calls, miniboss arm collision publication and managed wall explosion
children before capture. Rendering observations are CPU renderer arguments, not
GPU pixels or a native-image comparison. Defeat notifications use the existing
public parent callback; this is not a new player-damage or full-route oracle.
The inherited [act matrix](../validation/levels/s3k-lbz-act1.md) still has open
complete-route, supported viewport/donor/roster, load/death and presentation
obligations. Parent delivery owns broad guards/API/S3K checks and integration.


On unmodified runtime `e143088fa`, the completed initialized control reproduced
`IllegalStateException: Cannot restore stateful rewind field
com.openggf.game.sonic3k.objects.LbzMinibossBoxKnuxInstance#leftRig because one
side is null`, through `GenericFieldCapturer.restoreStatefulValue` and the
ObjectManager/registry recreation path. That run executed one test, zero assertion
failures, one error, zero skips. Before reaching this discriminating run, test
compilation needed private-enum formatting corrected; two fixture attempts then
stopped before restore on absent audio/game-state services. Those are harness
errors, not evidence of a runtime rewind regression. The final fixture owns an
audio mock and a real registered GameStateManager; the sign handoff is delayed
until its results work is outside the bounded replay.

The selected shape is a final private typed holder for each optional rig, using
the existing nullable `LbzMinibossBoxRig.Snapshot`. The rig itself remains absent
until camera initialization. Empty restore clears divergent presence without
services. Present restore constructs a fresh rig and fresh nonstatic pieces,
then reapplies the captured values. The unchanged snapshot stores release state
and, in fixed ten-piece table order, phase, integer/fractional position,
velocity, frame, timer, animation cursor and deletion. Each fixed array position
supplies the final piece index; construction binds the piece to its new enclosing
rig. Priority is derived from phase by the existing restore contract. There is
no persistent separate anchor or renderer to recreate, and no live helper,
renderer or outer-owner reference is stored in the snapshot. No change to the
shared rig class was needed.

Explicit restore-time creation from these same snapshots was considered and
rejected before implementation: an owner override would need to extract optional
helper values before default restoration, compose the base/extra state and
repeat or bypass the existing nine-field owner contract. That would obscure
default-audit coverage for a local presence problem. The holders leave every
existing field captured and enable the actual compact default route; the new
route check verifies both eligibility and all nine captured field names, while
the existing wall-holder audit still checks the owner for gaps. No allowances,
baseline keys, default-audit opt-outs, eager rig construction, native camera/
init/timer/piece/retirement changes, or per-controller RNG rebinding changes
are introduced.

The new behavior checks cover pre-gate empty capture, boundary initialization,
closed-state fresh reconstruction, repeated empty clearing and reinitialization,
all four left/right presence combinations through implicit and explicit
context-free public forms, same-instance restoration after divergence,
independently progressed rigs (182 versus 83 updates), and repeated forward
piece movement/bucket/retirement replay. Partial presence combinations are codec
boundary cases, not claimed native encounter phases. The initialized managed
case captures after normal release and 80 progressing updates plus live wall
effects, destructively recreates the graph, compares immediate draw/collision/
effects and identities, then compares every forward draw, collision region,
effect slot/coordinate/raw clock, SFX, shared RNG cursor, owner scalar, camera
max-Y, object-manager clock and no-respawn retirement result.


Commands ran from the task checkout, with verified canonical S3K ROM
(`$HOME` expands to the original absolute paths in the recorded commands),
CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 "$HOME/code/projects/OpenGGF/tools/testing/maven_queue.py" -v
# Java 25.0.5.0.2; separately, lua5.4 -v reported Lua 5.4.9.

# Red: e143088fa + completed initialized test fixture, before runtime edits.
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=lua5.4 python3 "$HOME/code/projects/OpenGGF/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/Sonic and Knuckles & Sonic 3 (W) [!].gen" '-Dtest=TestLbzBoxRigsRewind' test

# Green: e143088fa + final holder and focused tests recorded by this commit.
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=lua5.4 python3 "$HOME/code/projects/OpenGGF/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/Sonic and Knuckles & Sonic 3 (W) [!].gen" '-Dtest=TestLbzBoxRigsRewind,TestLbzWallExplosionsEmptyRewind,TestLbzWallExplosionsRewind,TestS3kLbzMinibossGraphRewind,TestLbzMinibossPartBuckets,TestRewindFieldDispositionAudit#wallExplosionHolderRemainsCapturedByTheDefaultOwnerPath' test
```

The exact red identity was
`TestLbzBoxRigsRewind.initializedEncounterRestoresProgressedRigsAndReplaysThroughRetirement`.
The green selection completed **36 tests, zero failures, zero errors, zero
skips**, including that same identity: rig cases 11, empty wall cases 3,
populated wall cases 5, LBZ graph cases 13, piece buckets 3 and the exact
wall-holder disposition method 1. The first expanded selection stopped at test
compilation on a package-private schema query; the final route test uses public
capture/blob and audit-field APIs. No runtime change followed the green run.
The shared rig file, policy table, baselines and previously reviewed wall code
are unchanged. Service-free empty wall restoration and current shared-RNG
binding are observed passing on the newly compact owner path.

This is focused validation, not a broad smoke, full suite, full act route,
viewport/donor/team certification or GPU rendering result. All Maven invocations
used the absolute main-workspace queue wrapper with this checkout's own target;
queued jobs were retained without duplication or lock changes. Version-probe
queue waiting was 312 seconds; the discriminating red and final green had zero
queue wait, with Maven execution totals 26.574 and 28.073 seconds respectively.
The earlier fixture rerun waited 67 seconds. These intervals overlap other work
and are not an elapsed-delivery-time claim. No jobs remain live.

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

## Lightning spark art follow-up

Scope: `LightningSparkObjectInstance#sparkTiles`, its necessary animation and
atlas-residency companion state, producer/recreation checks and this row only.
The T3-created checkout `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-9f830c00` was clean
at `a236a1276`, renamed to `bugfix/ai-lightning-spark-art-rewind-20261024`, and
fast-forwarded only to local reviewed base `e849ed5a4`. No nested worktree,
subagent, integration or push belongs to this slice.

### Producer census and rejected recipe

All production `new LightningSparkObjectInstance` calls are in
`LightningShieldObjectInstance.triggerSparks` and the public static
`forRewindRecreate` factory; the package-private no-argument constructor delegates
to the public null-art constructor for discovery. The public six- and
seven-argument constructors independently accept a `Pattern[]` and animation set;
there is no provider-only precondition on those entrypoints. The shield producer
requests `Sonic3kObjectArtKeys.LIGHTNING_SPARK` from its injected module's actual
`Sonic3kObjectArtProvider`. `loadShieldArt` / `buildSparkArtSet` load five
uncompressed ROM tiles and construct the existing 20-frame `[0,1,2]` spark
sequence with delay zero. Rendering uses those five patterns directly; no new
PLC parsing, timing or art registration is introduced.

The host shield factory and S3K cross-game donor factory both construct
`LightningShieldObjectInstance`. `DefaultPowerUpSpawner.shieldFactory` selects
the active donor factory before the host factory. The shield's art lookup still
reads the **host** module: an S3K donor shield on a Sonic 2 host currently emits
four null-art sparks. This is an observed construction/render boundary, not a
claim that donor lightning art is implemented. The focused donor test exercises
the actual spawner and exported factory with a stubbed active donor selection;
it does not initialize a complete cross-game gameplay session.

A genuinely reconstructible transient recipe was considered: retain only the
provider key and resolve the current provider, as the old recreate hook does.
The stock fixture proves that a replacement provider loads the same exact
nonzero five-tile values from the same ROM. That recipe is insufficient for the
public constructor's independent pixels/scripts or its null-art case. Capturing
only a provider key (or excluding only stock `sparkTiles`) would silently change
those supported recipes. The chosen owner-local `RewindStateful` holder's snapshot stores
only defensive pixel arrays and immutable copies of animation scripts, including
null/empty distinctions. Restore creates fresh patterns and an animation set;
it neither keeps a renderer nor reads an atlas/ROM/disassembly as a fallback.
The direct static factory keeps its existing provider lookup and null-service
behavior; the managed default field restore then applies the captured recipe.
The first implementation in `2abcdc86f` left the live fields on the owner and
excluded them with three annotations while an anonymous holder read/wrote them.
That sideband shape was rejected by independent review and the exact annotation
guard, despite the passing pixel/recipe checks below. The corrected private named
static helper owns the live fields, value capture/restore and upload cache
directly, without an enclosing-owner reference, exclusions or replacement policy
allowances. `tilesCached` remains helper-local upload scratch and resets at
holder restore. See the review correction below for red/green evidence.

### Discriminating baseline

With unchanged production source at `e849ed5a4` and the new fixture, four tests
executed: three assertion failures, zero errors/skips, and one passing
context-free/null-retirement control. The failures were:

- `independentConstructorArtAndAnimationAreCapturedAsValuesRatherThanReplacedByStock`:
  five captured tiles restored as null after the live provider was removed.
- `donorFactoryOnSonic2HostKeepsItsActualNullArtRecipeAndNativeAllocation`:
  a null-art boundary changed from zero lit pixels to 12 when a stock provider
  became available before restore. The later fixture revision routes the same
  exported donor factory through `DefaultPowerUpSpawner` as well.
- `stockProducerRestoresExactRomTilesAndVisibleBlankFramesThroughRetirement`:
  all four recreated objects recovered exact stock pixels in CPU memory, but the
  first production draw after atlas retirement changed from 48 lit pixels to
  zero because the captured `tilesCached=true` suppressed re-upload.

The baseline admission wait was 10 seconds; Maven reported 1:25 including
compilation, with 0.847 seconds in the four-case XML. These are separate observed
intervals, not total delivery time. The baseline command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" \
  -Dtest=TestLightningSparkArtRewind test
```

`MAIN_CHECKOUT` and `ROM_ROOT` denote the existing main checkout and its root ROM
directory, as in the earlier commands in this audit. The displayed variables
stand for the actual absolute wrapper and ROM arguments used at launch; the
worker did not invoke its local wrapper or create ROM links. The existing
absolute ROM was verified before launch: 4,194,304 bytes,
CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
The component fixture owns display-free EGL on Linux, installs no-device audio,
uses production `GraphicsManager` draw/flush and reads a freshly cleared target
each frame. Exact RGB image values, rather than just draw arguments or hashes, are
compared. Graphics resources retire while EGL remains current. No ambient
display, runtime fallback assets or manual capture override is used.

### Candidate verification and limits

The corrective focused selection is `TestLightningSparkArtRewind,` followed by
`TestSonic3kLightningShieldObjectInstance,TestRewindFieldDispositionAudit`, with
all other command arguments identical to the baseline. The owner audit method
requires that `sparkArtState` is captured on the actual default compact route;
only the exact `LightningSparkObjectInstance#sparkTiles` baseline key is removed.
The existing shield tests own allocation timing, the four signed velocities and
creation-time priority behavior. Parent-owned broad guards, API inventory, four
required S3K stability checks and delivery remain outside this slice.

The first corrective payload used an immutable map of copied scripts. The actual
default holder codec calls `GenericFieldCapturer.deepCopySnapshotValue`, whose
record copier rejects `Map` components. The 14-case corrective invocation
therefore had two errors (`Cannot clone unsupported rewind value type
java.util.Map`) in the real-art stock and independent cases, with no assertion
failures/skips; the donor case, context-free control, seven disposition checks
and three existing shield checks passed. Admission was immediate; Maven reported
1:19 including compilation. This is rejected payload evidence, not a framework
allowance: the replacement uses private typed script records and primitive
arrays already accepted by the copier. Script IDs, delay, frames, end action,
parameter and trailing bytes remain values, with no shared mutable collection.

The stock fixture captures after three updates and a visible production draw,
uses a fresh current provider, retires the five atlas entries, forces fresh
managed objects with the exact captured identities/slots, and compares immediate
rendering and 22 forward ticks through visible and blank animation frames and
deletion. The independent recipe
uses nonstock pixels and a distinct delayed script, mutates the original array,
patterns and frame list after capture, then restores without a provider. Reusing
the snapshot after mutating restored patterns/scripts distinguishes immutable
value capture from live references. The final context-free control also restores
populated art and empty nonnull arrays/sets through the inherited public capture
and restore methods without injected services. The donor control preserves
actual null art when a different provider appears before reconstruction. These are component
GPU pixels and bounded object/allocator replay, not a full-route, occlusion,
all-platform, independent-art coexistence or ROM scheduling parity claim.

The typed-array implementation committed as `2abcdc86f` passed all **14 tests across three exact
classes**, zero failures, errors or skips: four ROM/EGL spark cases (0.939 seconds
in XML), three existing shield checks (0.127 seconds), and seven disposition
audit checks (0.270 seconds). The selected command above exited zero; shared
admission waited 41 seconds and Maven reported 1:19 including compilation.
Those are observed queue/execution intervals, not total elapsed delivery time.
The tested state was base `e849ed5a4` plus this slice's production class, new
fixture, owner audit method and one-key baseline removal; only audit prose
changed afterwards. Consumed XML identities, assertions and skips were inspected.
That commit supplies the behavioral baseline for the review correction below;
its annotation/override guard pair was not in that initial 14-case selection.
No broad guard/API/S3K stability/full-route or release-only full-suite result is
claimed; the parent owns those applicable gates and delivery. No live Maven job
remains. Disposable logs/reports are removed rather than archived; no category
run, pass cache or validation receipt was created.

### Review correction: named art ownership without exclusions

The independent review of `2abcdc86fd59cbc2443841fe34ce9f18c5c7751f` found that
its three new `RewindTransient` annotations violated the authorized no-growth
scope. The exact annotation guard has no LightningSpark allowance. The live
`animSet`, `sparkTiles` and `tilesCached` fields were still owner sidebands,
with an anonymous `sparkArtState` referring back to the owner. Pixel/recipe
correctness did not justify those exclusions. The preceding 14 passing cases
remain valid behavioral baseline evidence, not a passing no-growth gate.

Before any production/test edit, the same clean checkout and branch at unchanged
`2abcdc86f` ran this exact pair using the verified existing absolute wrapper/ROM
paths represented by `MAIN_CHECKOUT` / `ROM_ROOT` above:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" \
  '-Dtest=TestRewindArchitectureGuard#objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage+objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage' test
```

Both methods executed, with one failure, zero errors/skips. The annotation
assertion reported exactly:

```text
Unexpected growth:
src/main/java/com/openggf/game/sonic3k/objects/LightningSparkObjectInstance.java#@RewindTransient = 3
```

`objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage` passed. Queue wait
was 35 seconds; Maven reported 25.196 seconds, with 0.518 seconds in the pair's
XML. These are separate observed intervals. No new baseline or allowance was
added to accept the rejected shape.

The correction moves all three live fields and their existing value capture,
restore and cache-upload behavior into a private named static `SparkArtState`
implementing `RewindStateful`. The owner holds one final helper and reads its
script/tile readiness and cache directly. There are no owner art sidebands,
synthetic enclosing-owner references, Java-transient replacement fields or new
exclusion annotations. Constructors pass through the original array and
animation references unchanged; only snapshot values are defensively copied.
Existing typed snapshots, null/empty distinctions, fresh restored patterns,
script metadata and upload reset remain intact. The default compact capture
route and inherited public no-table capture/restore still own the state;
no manual owner override, codec context-mode change, API pin, policy or baseline
edit belongs to this correction.

The original four ROM/EGL cases retain their names, pixel comparisons,
visible/blank/retirement replay and `EnabledOnOs(OS.LINUX)` isolation. Their
reflection now reads the fields in the helper; the independent recipe case also
asserts the original constructor aliases. The existing owner disposition method
now requires the private/static/final named helper, its exact three owned fields,
absence of owner sidebands/exclusions, actual default compact capture and zero
owner disposition gaps.

The corrective focused command preserves the baseline environment/wrapper/ROM
arguments and replaces only the selector with:

```text
-Dtest=TestLightningSparkArtRewind,TestSonic3kLightningShieldObjectInstance,TestRewindFieldDispositionAudit,TestRewindFieldDispositionGuard,TestRewindArchitectureGuard#objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage+objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage
```

This selects the original 14 affected cases plus the two exact annotation/override
methods and actual field-disposition guard. No complete parent guard profile,
API/S3K gate or release-only full suite is duplicated. Parent loop 6 is composed
but not yet tested; this follow-up remains pending parent review/integration.
The component rendering, donor-session and full-route limits above still apply.

The named-helper corrective selection passed **all 17 cases across five exact
class/method selections**, zero failures, errors or skips. XML confirmed the
same four ROM/EGL methods (0.912 seconds), three existing shield checks (0.122
seconds), seven owner/audit controls (0.024 seconds), the actual field-disposition
guard (0.433 seconds), and exactly the two annotation/override methods (0.469
seconds). The previously failing annotation method now passed with its baseline
unchanged. Admission waited zero seconds; Maven reported 1:15 including
compilation. Only audit prose changed after that completed run. The tested state
is `2abcdc86f` plus this follow-up's production helper and two affected test files;
the local follow-up commit identifies that source tree. No codec context modes,
policy/baseline allowances or GL isolation annotations changed. Consumed XML
identities and zero skip/failure results were inspected; no pass cache or category
run was created. No live Maven request remains. The same branch/worktree is
retained, and this result remains pending parent review/integration rather than
a claim about the untested loop-6 composition or parent delivery gates.
