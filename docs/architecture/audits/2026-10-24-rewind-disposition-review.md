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
| 1 | [`sonic1.objects.Sonic1RingInstance#childRingSpawns`](../../../src/main/java/com/openggf/game/sonic1/objects/Sonic1RingInstance.java) | Closed by captured typed placement recipes through the existing default compact path. Capture detaches the exact ordered values and canonical collection indices; restore rebinds copied records to the current RingManager. The public constructor retains its caller-owned child view until restoration. | All six stock GHZ1 graph controls remain. Added dynamic pre-INIT, unmanaged no-table, caller-alias/order, duplicate-coordinate identity and collection controls exercise actual default capture. See the S1 recipe implementation follow-up below for red/green commands, identity evidence, rejected contracts and limits. |
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
| 22 | [`sonic3k.objects.bosses.LbzEndBossInstance#launchedSpikeBallRomSubtypes`](../../../src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzEndBossInstance.java) | Removed the unused attempt history and its test-only getter. The maintained sequence test observes actual admitted projectile submissions. Gameplay retains `launchCount` and its table; no historical reconstruction or exclusion is introduced. | [`TestLbzEndBossLaunchRewind`](../../../src/test/java/com/openggf/game/rewind/TestLbzEndBossLaunchRewind.java) exercises destructive real-manager restore after two earlier balls retire, the next subtype, admission rejection, exact identities/slots/clocks and forward retirement. See the [bounded follow-up](#lbz-end-boss-launch-history-follow-up) for ownership alternatives, observed results and limits. |
| 23 | [`sonic3k.objects.bosses.LbzFinalBoss1Instance#spawned`](../../../src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss1Instance.java) | Closed by removing the historical inventory in the bounded follow-up below. Three local segment references preserve immediate unmanaged bootstrap after all direct children are allocated; managed nested initialization remains deferred. Historical diagnostics move into the choreography fixture. | [`TestLbzFinalBoss1GraphRewind`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss1GraphRewind.java) checks destructive reconstruction before and after nested initialization, overlapping owners, exact boss/segment identities and slots, every nested allocation prefix, retirement and forward replay. [`TestLbzFinalBoss1Instance`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss1Instance.java) separately checks null-manager bootstrap and public no-table owner snapshots. See the follow-up for commands, results and limits. |

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

## S1 recipe implementation follow-up, 2026-10-24

This slice uses the T3-created checkout
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-efdd7c0b`, on
`bugfix/ai-s1-ring-recipe-rewind-20261024`. Its clean initial HEAD
`a236a1276` was verified as an ancestor of local
`e849ed5a40c9313844f0bfb9451d1c92890001f9`, and the new branch alone was
fast-forwarded to that commit. The requested base contains reviewed `fc17fb82e`.
No nested worktree, remote update, parent-branch mutation, integration or push is
part of this slice. The parent retains counts, release prose, API/S3K checks,
full guards and composed delivery; loop 5 remains held for the separate AIZ work.

The retained boundary above is now addressed, rather than excluded. The public
parent constructor still takes the first supplied record once and retains the
caller's child `subList`. Changing that list's elements before capture or before
restore still changes the live recipe. Two typed
[`Sonic1RingSpawnList`](../../../src/main/java/com/openggf/game/sonic1/objects/Sonic1RingSpawnList.java)
holders cover the current ring's singleton placement and the exact child
sequence. They implement the existing `RewindStateful` contract, so the owner has
no capture/restore override, policy allowance, annotation or generic-codec change.
Capture stores immutable, defensively copied arrays of existing `RingSpawn`
values and the collection owner's placement indices. It stores no services,
manager, renderer, supplier or live object in the snapshot. Restore installs a
detached immutable list; later caller mutations cannot alter an earlier capture.

The existing engine-only `RingManagerInternalAccess` seam delegates to two
package-private RingManager methods. Capture uses the same identity-first
placement index as collection. Restore validates the indexed full record value
and returns the current canonical object. This distinguishes both different
placement IDs at the same coordinates and equal legacy records with ID `-1`.
The existing full-value collection fallback defines compatibility for supplied
noncanonical values or a changed index; an absent registration retains its
captured value. No coordinate inference is used for the captured recipe. The
existing coordinate-based recreation placeholder remains compatible with callers
that invoke recreation without applying captured state; default state restoration
replaces it before forward dispatch. RingManager's annotated public/protected
surface and signature pins are unchanged.

This identity guarantee is bounded to the same logical placement table. Without
a RingManager, capture preserves ordered values, not original Java record
references. Unrelated or reordered placement tables containing equal legacy
records (placement ID `-1`) have no all-context identity guarantee; the saved
index cannot establish historical identity across those different tables.

An explicit placed-only creation contract was rejected: public constructor and
ObjectRegistry/dynamic registration consumers already expose pre-INIT recipes,
and restricting them would change supported behavior. Subtype/coordinate
expansion was rejected because it changes supplied order/count and cannot
identify duplicate placements. Value-only rebinding was rejected because the
default deep-copy codec recreates immutable records, and equal legacy records
can still denote different canonical collection indices. The chosen indices are
existing RingManager placement identity, not a duplicate placement model or a
snapshot of live manager state. No historical child objects are synthesized.

Unchanged-runtime reproduction used the base SHA plus test changes only. The
first request ran 12 graph cases: the six inherited controls passed, and six new
cases failed, with zero errors/skips. All four
`dynamicPreInitPreservesSuppliedOrderAndReplaysAllocationAndRetirement(int)`
cases (masks 0, 1, 2, 7) failed at restored recipe length: expected 2, actual 0.
`unmanagedDefaultCaptureRestoresCaptureTimeRecipeWithoutAnIdentityTable` retained
the later canonical child (placement 1), losing capture-time placement 900.
The initial registry alias case restored placement 700 instead of the captured
first child 702. The second unchanged-runtime request ran two parameterized
registry cases (persisted IDs true/false) and the new
`s1RingRecipeUsesDefaultCaptureWithoutAnExclusion` audit case. All three failed,
with zero errors/skips: both recipe orders were lost, and the actual schema
contained only `state` and `outOfRangeAnchorX`. These are reproduced failures of
the unchanged runtime, not new failures attributed by totals alone.

All invocations use the following environment, absolute queue wrapper, current
worktree's own `target/`, and hash-verified existing S1 World REV01 ROM. Its CRC32
is `AFE05EEE`, SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`.
Java compilation targets release 25, and Lua is `/usr/bin/lua5.4` (5.4.9).
`MAIN_CHECKOUT` below resolves the shared Git directory's parent, the existing
main checkout used for every absolute invocation. Expanding this variable yields
the same executed argument vectors; it avoids storing a machine-local user-home
path, which the resource-policy hook rejects. The first four requests, in order,
are:

```bash
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
MAIN_CHECKOUT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1RingGroupGraphRewind' "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1RingGroupGraphRewind#registryRecipeCaptureFreezesSequenceOnlyAtCaptureAndRebindsDuplicateCoordinates,TestRewindFieldDispositionAudit#s1RingRecipeUsesDefaultCaptureWithoutAnExclusion' "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1RingGroupGraphRewind,TestRewindFieldDispositionAudit,TestRewindFieldDispositionGuard,com.openggf.tests.TestSonic1RingInstance,com.openggf.game.sonic1.objects.TestSonic1RingInstance,TestSonic1RingPlacement' "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1RingGroupGraphRewind#registryRecipeCaptureFreezesSequenceOnlyAtCaptureAndRebindsDuplicateCoordinates+dynamicUnregisteredRecipeKeepsValuesEvenAtRegisteredCoordinates' "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
```

The first green request completed 36 cases with zero failures, errors or skips:
13 graph cases, seven disposition-audit cases, the one actual field-disposition
guard and 15 constructor/placement controls. Exact XML identities and skips were
inspected. The emitted policy report contains no S1 ring key; only the exact
`Sonic1RingInstance#childRingSpawns` baseline entry is removed. Both placement
holders are captured by the actual default compact schema, as the focused audit
now asserts. No other baseline key or allowance changes.

The graph cases retain all six stock GHZ1 controls, force destructive managed
restoration, and compare every forward frame through child/parent sparkle
retirement. Supplied dynamic recipes reverse child order and deliberately differ
from subtype count; collected children consume no allocation slot and keep their
original native geometry index. The no-table case uses default owner capture,
same-instance restoration and generic destructive recreation, preserving values
and INIT progression without an identity table. Additional focused coverage
replaces the canonical records before restore, independently collects one of two
duplicate-coordinate placements, and restores progressed dynamic children. It
also checks unregistered values at already-registered coordinates without binding
them to the unrelated placement. The strengthened two duplicate cases and the
unregistered case passed the fourth request: three tests, zero failures, errors or
skips, with exact XML identities inspected. Together the two green requests cover
37 distinct cases; there was no single 37-case invocation. The fourth request
changed only focused tests, leaving the runtime source identical to the first
green request. Its XML confirms Java 25 and the requested surfaceless EGL and
absolute S1 ROM properties.

The first red, second red, first green and focused follow-up requests waited
10, 10, 4 and 47 seconds respectively. Maven reported about 88 seconds,
26.296 seconds, about 85 seconds and 27.361 seconds including compilation.
These are separate admission/execution intervals, not total delivery elapsed
time. No request was cancelled, duplicated or restarted. All four completed. Existing compiler deprecations, load-time manifest
fallbacks and art-mapping warnings were observed; these tests do not certify
rendered art. Consumed Surefire reports and the generated policy report are
inspected before removal; no category run, pass cache or log archive is created.

Final source review caught an unintended constructor null check from wrapping
the singleton in `List.of`. It was replaced with `Collections.singletonList`,
preserving the original constructor's acceptance of the first supplied reference
without adding early validation. No capture, rebinding or native algorithm
changed. The affected constructor/placement controls and unmanaged no-table graph
case were rechecked on this final source delta:

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.tests.TestSonic1RingInstance,com.openggf.game.sonic1.objects.TestSonic1RingInstance,TestSonic1RingPlacement,TestS1RingGroupGraphRewind#unmanagedDefaultCaptureRestoresCaptureTimeRecipeWithoutAnIdentityTable' "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
```

This fifth completed request passed all 16 cases, zero failures, errors or skips,
with XML identities inspected. Admission wait was 0 seconds; Maven reported about
85 seconds including compilation. It adds no distinct cases to the 37 already
covered. No live jobs remain. The consumed Surefire and generated policy reports
were removed after inspection; build outputs remain in this checkout's `target/`.
The branch and worktree are retained for parent composition.

This is bounded S1 recipe/identity evidence, not full-route, GPU, trace-sweep,
allocation-exhaustion, every-orientation, viewport/donor/team, API or S3K
certification. No broad smoke, full guard profile or release-only full suite is
submitted. The allocator, native child-index geometry, collection, respawn and
retirement algorithms themselves are unchanged. The parent owns combined
validation and later composition.

## S1 nullable recipe review correction, 2026-10-24

Independent review of `60f4d832f971fbef612e3d173fe50a897a825b66` found a P2
compatibility defect: the public parent constructor and default snapshot capture
accept nullable placement entries, but `Sonic1RingSpawnList.restoreRewindStateValue`
ended with `List.copyOf(restored)`, which rejects those entries. The earlier
constructor-only null-check correction did not exercise nullable restoration.
This follow-up stays in the same T3 checkout and
`bugfix/ai-s1-ring-recipe-rewind-20261024`, directly atop that local commit.

[`TestS1RingNullRecipeRewind`](../../../src/test/java/com/openggf/game/rewind/TestS1RingNullRecipeRewind.java)
uses unmanaged public construction with `StubObjectServices`, without a
RingManager or identity table. It covers `Collections.singletonList(null)` and a
parent recipe with two null children around a non-null child. Each shape runs
through both public capture/restore forms: no-argument methods and explicit
`RewindCaptureContext.none()`. Each form is checked on the same object and on a
fresh generic recreation, giving eight independent cases. The tests retain the
caller's live child view before and after capture, then check detached restored
values/order, INIT progression, an unmodifiable restored view, and reuse of the
same snapshot after further caller edits and forward dispatch. No original Java
reference identity is asserted for no-manager values.

The correction wraps the already-fresh local `ArrayList` with
`Collections.unmodifiableList(restored)`. Its backing list has no escaped mutable
alias, so another defensive copy is unnecessary. Nulls remain in their exact
positions. Snapshot array copies, canonical indices and rebinding, default owner
capture and native update/allocation/collection semantics are unchanged. There
are no new exclusions, annotations, manual owner overrides, baseline edits or API
pins. The same-logical-table identity bound above remains in force.

The requests use the environment and `MAIN_CHECKOUT` binding defined in the
preceding follow-up: Java 25, Lua 5.4.9, surfaceless EGL, this checkout's own
`target/`, and the same existing absolute S1 World REV01 ROM, reverified as CRC32
`AFE05EEE` / SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`. Expanding the
binding yields the absolute queue and ROM arguments actually used; the audit
avoids machine-local home paths to satisfy its resource-policy hook.

```bash
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1RingNullRecipeRewind' "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1RingNullRecipeRewind,TestS1RingGroupGraphRewind,com.openggf.tests.TestSonic1RingInstance,com.openggf.game.sonic1.objects.TestSonic1RingInstance,TestSonic1RingPlacement' "-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
```

On unchanged runtime at `60f4d832f`, the first request completed eight tests,
zero assertion failures, eight errors and zero skips. Every error was
`NullPointerException`, with `java.util.List.copyOf` and
`Sonic1RingSpawnList.restoreRewindStateValue` in its stack. Exact XML identities
were `nullableRecipeSurvivesBothPublicNoTableForms(boolean, boolean, boolean)[1]`
through `[8]`: nullable singleton parent with implicit capture/restore on the same
instance and fresh instance (1–2), singleton parent with explicit `none()` on those
two instances (3–4), nullable children with implicit methods (5–6), and nullable
children with explicit `none()` (7–8). Capture and initial forward dispatch had
already succeeded in every case; the first restoration rejected the nulls. These
are observed same-instance and recreation errors in both public no-table forms.
The request waited 92 seconds for admission; Maven reported 25.648 seconds of
execution including compilation. No request was cancelled, restarted or duplicated.
The second request completed 37 tests with zero failures, errors or skips:
eight nullable controls, 14 graph cases and 15 existing constructor/placement
controls. Exact XML testcase identities and skip elements were inspected. All
six stock cases remain: `placedPreInitRebuildsCanonicalRecipeAndReplaysExactAllocation(int)[1–4]`
and `progressedDynamicChildrenRestoreFreshWithoutDuplicatingAndReplaySparkleRetirement(int)[1–2]`.
Each of the eight new null identities `[1–8]` passed after the restore-wrapper
change, through a forward INIT dispatch and a second restore of the same capture.
The green request waited 0 seconds; Maven reported about 85 seconds including
compilation. XML properties confirm Java `25.0.5.0.2`, surfaceless EGL and the
absolute verified S1 ROM property. These are bounded completed-run results on
`60f4d832f` plus this source/test delta, not a broad or parent validation claim.
No running or queued requests remain. No source edits followed the green run.
The parent retains combined validation and review. Loop 6 has not yet been tested
and remains ready to compose this correction after review; this local slice does
not claim parent guard/API, S3K, broad smoke, route or release validation. No push,
integration, worktree cleanup or delegation is part of this correction.

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

## LBZ end-boss launch history follow-up

This field-only slice starts at `3ca14002fa2fafeabe1d3e6e370a3f559b6cba4e`
in `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-62e0e355`, branch
`bugfix/ai-lbz-endboss-launch-inventory-20261024`. The parent owns composed
API/S3K/guard validation, release prose and integration. Only the exact
`LbzEndBossInstance#launchedSpikeBallRomSubtypes` baseline key is removed;
this follow-up does not revise aggregate parent counts or certify the whole boss.

Two bounded shapes were compared before implementation:

1. Retain the diagnostic attempt log and capture its integer values through an
   owner-local value holder. This would preserve the accessor's old historical
   meaning, including failed admissions, but adds snapshot state with no gameplay
   consumer. Reconstructing that log from live balls is invalid: retired balls
   and rejected allocations have no surviving managed representation.
2. Remove the unused history/getter and move observations into the maintained
   tests. This is the chosen shape. The sequence assertion records accepted
   projectiles at the real ObjectManager submission boundary; the separate
   admission test records attempts and their admission result explicitly. Neither
   test substitutes current live-child count for historical attempt count.

`loc_73A36 (sonic3k.asm:153539-153543)` plays the launcher SFX and enters
`loc_73F1C (sonic3k.asm:154009-154016)`, whose counter/table selection uses
`byte_73F3A (sonic3k.asm:154018-154020)`. The runtime diff only deletes the
field, initializer, append and getter. It leaves the existing Java SFX,
`LAUNCH_SUBTYPE_TABLE[launchCount++ & 7]`, spawn/record order and native
allocation primitive untouched. It adds no override, policy, annotation,
retired-reference payload, API pin or framework allowance.

The new bounded manager test advances the actual camera gate, intro, rise,
fire timer and platform gate. Two admitted balls (subtypes 0 and 2) complete
falling/rolling/explosion and retire before the third attempt's timer borrow.
Capture then contains no ball. After uninterrupted forward execution the test
empties the real manager, restores fresh instances through its registry path,
and compares current managed identities, exact slots, parents, clock, launch
counter, routine/timers/flags and RNG seed. Forward comparison includes every
frame's managed positions, ball motion/timer/collision state, submission
subtype/initial velocity, SFX-before-submission order and retirement. A positive
control requires the higher-slot admitted ball to move and decrement its timer
on the allocation pass.

The exhaustion variant fills the native SST window with real, scalar-captured
test occupants before capture. The third attempt (subtype 1) has no identity
or slot; one occupant retires on the following manager pass. The next scheduled
attempt consumes subtype 2 and that exact freed slot. Restoring the snapshot
replays this boundary without reserving ownerless bits again, injecting flags
or rebuilding past launches. Test observers retain value records, not live
historical references in runtime snapshots.

The completed focused invocation on the base SHA plus this slice's uncommitted
runtime/test/baseline changes passed **28 distinct cases, zero failures, errors
or skips**. Per-class XML was read, including both parameterized identities
`retiredLaunchesDoNotReappearAndNextAttemptReplaysExactly(boolean)[1]` and `[2]`:

- `TestLbzEndBossLaunchRewind`: 2 cases (admitted third attempt; rejected third
  attempt followed by admitted fourth), both pass.
- `TestS3kLbzEndBossChildLinksRewind`: all 5 cases pass, including
  `midFightRewindRebuildsRisingTubesAndLaunchedSpikeBall`.
- `TestLbzEndBossInstance`: all 20 cases pass, including the migrated submission
  sequence and existing smoke/debris, hit, camera and defeat checks.
- `TestRewindFieldDispositionGuard`: its one case passes. The actual audit output
  contains exactly the unchanged other keys and omits this field. No aggregate
  parent count is edited.

Exact commands from this checkout (the test command was submitted once).
`MAIN_CHECKOUT` denotes the main OpenGGF checkout used for the current wrapper;
`ROM_ROOT` is that same checkout containing the three existing `.gen` files.
Only machine-local path prefixes are parameterized below:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  PATH=/usr/lib/jvm/java-25-openjdk/bin:/usr/local/bin:/usr/bin:/bin \
  LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -v

env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  PATH=/usr/lib/jvm/java-25-openjdk/bin:/usr/local/bin:/usr/bin:/bin \
  LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestLbzEndBossLaunchRewind,TestLbzEndBossInstance,TestS3kLbzEndBossChildLinksRewind,TestRewindFieldDispositionGuard' \
  "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/s2.gen" \
  "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" test -B
```

The version probe waited 316 seconds for admission and reported Java
25.0.5.0.2/Maven 3.10.0. Lua reports 5.4.9. The focused request waited 99 seconds;
Maven reported 1:32 execution including the fresh worktree compilation. The XML
confirms Java 25 and `surfaceless-egl` in every test fork. All three existing
absolute ROMs match AGENTS.md CRC32/SHA-1; these particular checks are ROM-free,
so those properties do not establish ROM route coverage. No test or build
failure was observed, and no matched baseline rerun was needed for attribution.
No queued job was duplicated, rerouted or cancelled.

`git diff --check`, assigned-row/follow-up link checks and
`python3 tools/disasm/disasm_citations.py check docs/architecture/audits/2026-10-24-rewind-disposition-review.md`
pass (7 citations verified; 2 inherited citations unverifiable). Consumed
Surefire XML/text and the field-audit diagnostic are removed after inspection;
no category run, retained log archive or pass cache was created. This bounded
selection replaces a broad local run for the seven-line diagnostic removal;
parent-owned composed API/S3K/full guard selections remain outside this slice.
The terrain seam supplies floor and wall contacts; this is a ROM-free managed
lifecycle proof with surrounding existing graph/choreography coverage, not a
cold LBZ route, renderer/pixel, multi-owner, defeat-controller or full-boss
certification. Constructor-only `romSubtype` on an already restored live ball
and the base class's deferred construction-ordinal bookkeeping are outside this
field obligation; the test compares actual managed ObjectRefId and native
launch/motion state instead of inventing retired historical values.

## LBZ final-boss-1 spawn inventory follow-up

This slice starts at pinned main `3ca14002fa2fafeabe1d3e6e370a3f559b6cba4e`
in `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-351a2104`, on
`bugfix/ai-lbz-finalboss-spawned-rewind-20261024`. Ownership is limited to
`LbzFinalBoss1Instance#spawned`, its affected tests and this audit entry. The
parent owns composed guards, API checks, the four S3K stability classes,
aggregate counts/release prose, integration and push. No nested checkout,
subdelegation or branch switch was used; hooks were installed in this checkout.

### Construction inventory and two complete ownership shapes

At the pinned source, `recordChild` (near line 1195) appends every `SpawnRecord`,
including rejected allocations and objects that later retire. Neither allocation
rejection nor removal prunes this history. The sole production list consumer
(near line 502) initializes the nested tables of the three turret segments when
`services().objectManager()` is null. The getters near lines 1295/1302 and the
unused package helper are diagnostics. Production constructs the boss through
the LBZ-specific `$CA`/`$CD` registry entries and the ride ship's free-slot handoff.
The public constructor also supports direct unmanaged construction; its lack of
a Mod API annotation does not make an absent manager invalid.

Two complete shapes were compared:

1. **Capture a bounded live inventory.** Keep ordered kinds and registered child
   identities, reject failed allocations from the inventory, prune every retired
   family, and rebind after all shells exist. A scalar recipe plus settled
   identity lookup would be another encoding of this shape. Neither encoding may
   retain retired history. This also needs an explicit no-table branch: the
   existing `List<SpawnRecord>` has no collection codec, and replacing it with
   strict identity references would require a table even for public owner-only
   snapshots. Unmanaged children have neither manager identities nor a managed
   recreation owner. Preserving immediate unmanaged bootstrap and no-table
   behavior therefore adds separate local recipe/compatibility machinery, plus
   lifecycle bookkeeping for diagnostic kinds across turret, projectile and
   launch families. Capturing the original append-only history or merely adding
   a `CAPTURED` entry is not a complete implementation. This shape was rejected
   because that machinery would serve an otherwise diagnostic inventory.
2. **Remove persistent history and keep bootstrap local.** Allocate the same
   direct children in the same order; retain only the new top/middle/bottom
   segment references on the initialization stack. With no manager, initialize
   their nested tables after the direct allocation pass. With a manager, leave
   them for their ordinary first dispatch. Keep historical emission observations
   outside production state and use the manager's actual graph for rewind tests.
   This is the selected shape. Every native allocation expression, primitive,
   ordering gate, timer, deletion, respawn and owner link stays in its existing
   path. The internal diagnostic enum/accessors/helper and `SpawnRecord` are
   removed with the list; no published API pin changes.

The choreography fixture observes `ObjectConstructionContext.construct` while
calling its real implementation, recording only returned instances belonging to
its own services. This is necessary because the existing semantic child hook
omits unmanaged `spawnFreeChild` calls. It preserves history for emission-count
assertions without production hooks, a rewind exclusion or identity capture of
retired objects. The observer's class/kind grouping also includes already
emitted explosions; it does not drive allocations or object ticks.

### Discriminating contracts and graph evidence

The unchanged-runtime null-manager test observes exactly seven direct children
and six immediately initialized nested children in order, then proves the first
segment updates do not allocate them again. The public no-table test captures
and restores an empty constructor instance before services exist, and an
initialized unmanaged owner after scalar divergence. It restores position and HP
without allocating or replacing the fixture's children. This remains an
owner-only snapshot contract, not an aggregate unmanaged-world rewind promise.
No manager requirement or constructor restriction is introduced.

The graph fixture uses real `ObjectManager` allocation with the S3K registry's
LBZ stock context and `setRewindInPlaceRestoreEnabledForTest(false)`. It removes
all live objects, admits a divergent boss, then restores the snapshot. Camera
and shared RNG participate in the same registry; the fade's audio service is a
mock, without opening a device. The tests verify:

- Seven direct children before deferred segment initialization; reconstruction
  allocates no nested suffix early. Forward replay builds exactly two heads for
  each of the top/middle segments and two guns for the bottom segment, in slots
  after their actual segment owner.
- Exact captured object IDs, slots and boss/segment links before and after fresh
  reconstruction. Two bosses occupy the same coordinates, discriminating exact
  ownership from proximity or first-live-owner lookup.
- All seven free-slot prefixes (zero through six) across the independent nested
  tables. Inert real occupants preserve pressure through restore; failed suffixes
  neither heal nor retry before or after a progressed snapshot.
- The HP-five detach branch, retirement of the exact top segment and its two
  heads, 24 forward dispatches from the pre-detach snapshot, and another 12 from
  a post-retirement snapshot. Retired IDs stay absent; every registry snapshot
  matches uninterrupted execution through `RewindSnapshotDiff`.

The short graph cases deliberately advance the seeded heads away from a firing
notch, keeping muzzle/head lifetime outside this obligation. They are native
allocation/ownership checks, not full LBZ2 route, rendered-pixel, all-character,
complete finale or independently ROM-correlated timing certification. Existing
ROM-backed choreography assertions continue to exercise those local routines.

### Commands, red/green results and diagnostic cleanup

All Maven requests use the absolute updated queue wrapper from this checkout,
Java 25 and Lua 5.4, with the surfaceless EGL flag unchanged. The existing S1,
S2 and S3&K paths below were read and their CRC32/SHA-1 values matched the setup
contract; no ROM was renamed, copied or linked. The queued `-v` probe reported
Java `25.0.5.0.2` after 370 seconds of admission waiting. `lua5.4 -v` reported
`5.4.9`.

The common environment/ROM arguments for the commands were:

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
QUEUE_WRAPPER="$HOME/code/projects/OpenGGF/tools/testing/maven_queue.py"
ROM_ROOT="$HOME/code/projects/OpenGGF"
```

The baseline command, initially including the migrated choreography class, was:

```bash
python3 "$QUEUE_WRAPPER" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestLbzFinalBoss1Instance,TestLbzFinalBoss1GraphRewind,TestLbzFinalBoss1FieldDisposition' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

Fixture-development results are distinguished from runtime failures. The first
request stopped at test compilation because two overloaded capture method
references were ambiguous (218 seconds waiting; Maven reported 1:21 execution).
Explicit lambdas repaired them. The next two requests each ran 37 cases with one
intended disposition failure and ten fixture errors: first a missing LBZ
registry context returned placeholders, then an absent audio service stopped the
fade. Their reported waiting/execution intervals were 111/27.734 and 4/29.612
seconds. The next request ran 37 cases with eight failures and no errors/skips:
the disposition failure plus seven pressure cases whose bare reserved bits were
not reconstructible occupants (9 seconds waiting; 28.184 seconds execution).
The three complete graph cases and all 26 choreography/unmanaged checks passed.
These fixture faults do not establish production regressions.

After replacing bare reservations with real inert occupants, the exact bounded
baseline command omitted the already passing, unchanged choreography class:

```bash
python3 "$QUEUE_WRAPPER" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestLbzFinalBoss1GraphRewind,TestLbzFinalBoss1FieldDisposition' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

That completed run had 11 cases, one failure, no errors and no skips (48 seconds
waiting; 25.686 seconds execution). All ten graph cases passed on unchanged
production. The sole failure was
`TestLbzFinalBoss1FieldDisposition#actualDefaultCaptureHasNoUnownedSpawnInventory`,
reporting exactly
`com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance#spawned`,
`type=List path=compact`. This is the qualifying red control for removal.

The candidate selection adds the ride ship's directly affected handoff and
existing cameo checks:

```bash
python3 "$QUEUE_WRAPPER" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestLbzFinalBoss1Instance,TestLbzFinalBoss1GraphRewind,TestLbzFinalBoss1FieldDisposition,TestLbz2RobotnikShipRewind,TestLbz2RideCameoInstances' "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The candidate completed with **58 cases, zero failures, zero errors and zero
skips**: `TestLbzFinalBoss1FieldDisposition` 1, `TestLbzFinalBoss1GraphRewind` 10,
`TestLbzFinalBoss1Instance` 26, `TestLbz2RobotnikShipRewind` 8 and
`TestLbz2RideCameoInstances` 13. Admission waited 1 second; Maven reported 1:26
execution. These intervals are not elapsed delivery time. Every fresh XML
`classname#name` and its failure/error/skip elements was inspected, including all
parameterized prefix cases. The candidate source was the pinned base plus this
checkout's final boss/test changes; only this audit's result prose changed after
that run. The disposition control changed from the exact assigned gap to no
owner gaps while retaining the actual default compact capture path.

Consumed XML/text/dump reports and invocation diagnostics are removed before
handoff; no category runner was used, so there is no category run ID to
acknowledge. `git diff --check` and the new audit's local link checks pass.
Only the exact assigned baseline key is removed; no aggregate baseline counts
are edited. No complete guards, API/S3K stability selection, broad smoke,
release-only full suite, integration, push or task-worktree cleanup is claimed.

## S2 Sideways Platform allocation-failure qualification

This independent qualification starts at `97eb86f186b432722024ca419a00b901b38f5ec2`
and fast-forwards its isolated T3 task branch
`bugfix/ai-s2-sideways-admission-owner-20261024` to
`8f34cad77297663523100fb7fdbf120cd9b259c1` before edits. The production
`SidewaysPformObjectInstance` Git blob is unchanged at
`583a6d6822d4f50f6f9bb6e15b6fcd4808cbd02e`, SHA-256
`4fa8ca206cc93956b0969f15b75dc0e47d6134df3b18199a34f8ed652dc553c1`.
The assigned question is whether rejected child admission permits a bounded,
native-correct ownership repair. It is not an allowance-removal exercise.

The native failure branch prevents assuming either a null or a self link.
Evidence was read only from the recorded S2 submodule Git object
`34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`; the user-dirty working reference
was not checked out, reset, updated or fetched. Its `s2.asm` blob is
`b6fde6212f05cc9179ee8c9053c0484bf0581349`, and `s2.constants.asm` is
`7857fe5ad3221cfab36e9086b17e892fcb09013b`.

- `Obj7A_Init` (s2.asm:56295-56310) initializes the parent through
  `Obj7A_LoadSubObject` with `a1=a0`. For the single-platform subtypes, the
  first `dbf` exits without allocating, and the two link writes therefore
  establish a self link.
- `AllocateObjectAfterCurrent` (s2.asm:33705-33726) starts `a1` at `a0`, tests
  occupied entries and advances it by `next_object` before each `dbf`. With
  no free later slot, including a parent in the last dynamic slot, it returns
  with a nonzero test result and `a1=Dynamic_Object_RAM_End`. `lea` and `dbf`
  do not restore the pointer or change the last occupied-ID test's flags.
- `Obj7A_SubObjectLoop` / `Obj7A_SubObjectLoop_End`
  (s2.asm:56313-56351) skips the child ID/routine/art initialization on that
  nonzero result but still exits the child count loop and writes both
  `objoff_3C` links. It then writes both boundaries, offsets the parent by
  `parentXOffset`, and adds `childXOffset` through the returned `a1`.
- The pinned `object_size_bits` / `object_size`
  (s2.constants.asm:189-193) select the shipped `$40` branch.
  `Dynamic_Object_RAM_End` / `Tails_Tails`
  (s2.constants.asm:1139-1151) identify the exhausted pointer as the fixed
  Tails-tail slot, `$FFFFD000`, rather than a newly allocated platform.
- `loc_294C4` (s2.asm:56374-56387) skips paired deletion only for an exact
  self link. Otherwise it calls `DeleteObject2` through the link, then clears
  the parent's live respawn bit and deletes the parent. `DeleteObject2`
  (s2.asm:30334-30345) clears the pointed-to slot. The exhausted alias thus
  matters to retirement as well as graph capture. `Obj05_Main`
  (s2.asm:41723-41728) normally rewrites that fixed tail's position from its
  character; it does not turn it into an Obj7A child.

These are source-derived register/control-flow conclusions, not an emulator
capture. `fixBugs = 0` (s2.asm:27) is the governing native policy. Clearing the
Java rejected child to null/self, retaining a fabricated admitted child, or
teaching strict capture to ignore it would not reproduce this branch. The
current `linkedPlatform` field can point only to another Sideways platform;
it cannot express the native fixed-tail alias or its deletion. Resolving that
representation and fixed-slot owner is additional work beyond the assigned
single-object reject-only cut. No production repair is selected from this
source evidence alone.

The real-manager golden baseline is reproduced at the same source. A dynamic
parent is constructed and submitted at SST 40 through
`createDynamicObjectAtSlot`; every other dynamic SST 16-127 contains a real,
registered, `RewindRecreatable` inert occupant. The pressure graph itself
captures before initialization. One normal manager dispatch then constructs
the child under the existing services scope and submits it through the real
`addDynamicObjectAfterSlot` allocator. The recording override delegates to that
allocator; it does not mock admission or mint identities. The rejected child
has slot -1, is destroyed, has no identity and remains reachable through the
parent's reciprocal link. Immediate strict capture fails at exactly
`SidewaysPformObjectInstance#linkedPlatform` with
`RewindIdentityTable has no registered id for object reference`.

The golden command ran **1 case, 1 failure, 0 errors, 0 skips**. This is an
actual capture failure, not a compile/import/signature failure. Admission waited
440 seconds; Maven reported 1:23 execution. XML confirmed Java `25.0.5.0.2`,
the own-worktree working directory, `mse=off`, exact selected identity
`TestS2SidewaysPformAdmissionQualification#strictCaptureAfterExhaustedTail`,
and the complete contextual missing-reference error. The production blob above
was unchanged; the uncommitted qualification source had SHA-256
`2091b99b5889bf368c62f76c1d49f4ea138d31487ab408038762959ff1825734`.
That intentionally red acceptance method is diagnostic evidence, not a
committed failing test or a claimed repair.

`MAIN_CHECKOUT` denotes the existing main checkout, which supplies the current
queue wrapper. Commands run from this isolated task checkout with no ROM
properties and no desktop/graphics context:

```bash
qualification_maven=(env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY
  -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
  PATH="/usr/lib/jvm/java-25-openjdk/bin:$PATH"
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" --lean -Dmse=off)
"${qualification_maven[@]}" '-Dtest=TestS2SidewaysPformAdmissionQualification#strictCaptureAfterExhaustedTail' test -B
```

The follow-up command used the same command prefix:

```bash
"${qualification_maven[@]}" '-Dtest=TestS2SidewaysPformAdmissionQualification#characterizeRealAdmissionAndFreshReplay+admittedPairRetiresAndReleasesRememberFlagAndSlots+noManagerBootstrapRetainsExistingUnmanagedPair,TestS2SidewaysPformGraphRewind,TestObjectManagerChildSlotAllocation,TestObjectManagerRewindReferenceClosure,TestObjectManagerDynamicChainRewindRestore,TestScalarOnlyCodecDeletion#s2SidewaysPformGraphRecreateClassesImplementRewindRecreatable,TestRewindSchemaRegistry#exactDefaultObjectPolicyCapturesSidewaysPlatformLink' test -B
```

That completed **34 cases: 32 passes, 1 failure, 1 error, 0 skips**. All nine
real-manager pressure cases passed: subtype `$06` with a full pool, occupied tail
and free earlier slots, parent at SST 127, an explicit earlier hole with no later
hole, one later hole and two noncontiguous later holes; subtype `$0C` with a full
pool, one later hole and two later holes. Rejected candidates stay outside the
identity registry and strict capture still fails. A tail-only case frees a
genuine later occupant and confirms no initialization retry after capacity
returns. Admitted children use the parent's explicit SST anchor, select the first
later hole, and move in the same ascending dispatch pass. Both admitted subtypes
capture immediately, restore destructively with exact parent/child IDs and SSTs,
restore every pressure occupant at its captured SST, rebuild reciprocal fresh
links, and match the next parent/child positions on forward replay.

The other 23 passing cases are the existing Sideways graph/lifetime checks (4),
child-slot allocation checks (8), strict reference-closure checks (8), dynamic
reconstruction chain (1), Sideways `RewindRecreatable` contract (1) and exact
default link schema (1). The existing unmanaged-link negative control still
rejects capture. This run waited 1,340 seconds for admission; Maven reported
28.764 seconds execution. Every exact XML case identity, failure/error/skip
element and the Java/mse/selector/working-directory properties were inspected.

The two new peripheral fixture faults are separate from the reproduced capture
gap. `admittedPairRetiresAndReleasesRememberFlagAndSlots` incorrectly expected
the retained Java parent reference to become destroyed; the manager's placed
unload removes it without setting that flag. The corrected check uses absence
from the live graph and reclaimed SSTs, while retaining child retirement,
remember-flag release and cursor-return respawn assertions.
`noManagerBootstrapRetainsExistingUnmanagedPair` updated its object after the
temporary construction context closed without binding services. It now injects
the explicit no-manager stub before update, matching the existing services
contract. Neither fault establishes another production defect.

The corrected-peripheral command was:

```bash
"${qualification_maven[@]}" '-Dtest=TestS2SidewaysPformAdmissionQualification#admittedPairRetiresAndReleasesRememberFlagAndSlots+noManagerBootstrapRetainsExistingUnmanagedPair,TestSingletonLifecycleGuard#ambientGameplayModeSetupsDoNotGrowWithoutLifecycleTriage' test -B
```

It ran **3 cases: 2 passes, 1 failure, 0 errors/skips**, waiting 66 seconds and
reporting 28.129 seconds Maven execution. The no-manager bootstrap contract and
ambient-setup guard passed. The retirement fixture still omitted the separate
S2 post-camera placement phase: `ObjectManager.objectUnloadCameraX()` reads the
previous `postCameraPlacementUpdate()` latch, which remained zero after reset.
The corrected fixture now publishes that phase before the next object pass,
including on camera return. The two new fixture faults and this omitted phase
are not attributed to the production admission gap. This retry's qualification
source SHA-256 was
`df5e1cf4eec0a8cf495e438cbf03ce664f275d53865b8d644bf4d19bb621439f`.

The final corrected retirement command was:

```bash
"${qualification_maven[@]}" '-Dtest=TestS2SidewaysPformAdmissionQualification#admittedPairRetiresAndReleasesRememberFlagAndSlots' test -B
```

It passed **1 case, 0 failures/errors/skips**, with an 18-second queue wait and
27.929 seconds Maven execution. It confirms pair removal from the live graph,
child retirement, unremembered placement, exact parent/child SST reuse and a
second child submission attempt on camera return. That last assertion alone did
not establish fresh live admission, registration, reciprocal links or successful
capture after return; the follow-up below addresses that evidence limit.
The original qualification source SHA-256 is
`9ff7263ac4267de9642ab052b835fbc2a0f4903e934fe3c6a76a654f00ddac52`.
Only that corrected method changed after the preceding retry. The nine
unchanged pressure cases and 23 existing passes are not repeated as a ceremony.
The deliberately red diagnostic method is removed, and the retained
qualification class explicitly labels its rejected-child assertions as an open
capture gap rather than repaired behavior. The different command results above
are separate completed runs, not one green combined run. Waiting and Maven
execution intervals are separate; other delivery time is unmeasured.

Final disposition: **qualified capture gap; production unchanged; native fixed
slot ownership deferred**. All ten dynamic-add bridges remain **QUALIFIED
RETAIN**, count one each. The 119-record frozen store, six ambient setup keys,
package permissions, descriptor, API version/pins, allocator and strict codecs
are unchanged. The next owner must account for both empty and live fixed
`Tails_Tails` SST states, the failed allocation's link/boundary/offset writes,
and paired deletion/respawn release before choosing an admissible Java link
representation and reversible capture/restore behavior. No representation or
broader framework is selected here, and no null/self substitute is justified by
this branch.

Coverage is bounded logic qualification with a real S2 manager, SST allocator,
identity registry, reconstructible pressure occupants and strict compact
snapshots. It is not an emulator/ROM execution, full act/trace sweep, player
collision or art validation, arbitrary roster/donor/viewport certification,
out-of-cursor dispatch probe, full guards or smoke-suite result. Pressure uses
the 320-pixel viewport; the unchanged graph class separately checks endpoint
buckets at widths 320/528/800. Failed exhausted-tail capture prevents a fresh
restore of the exhausted case; it is explicitly not certified by the successful
pair restores. The existing level-route/matrix gaps remain inherited.

All own XML case identities, properties and failure/error/skip elements were
inspected and consumed after each run; no logs, pass caches or validation
receipts are retained. The new citation ranges were checked against the pinned
Git objects, and documentation fences/links and `git diff --check` are checked
before the local evidence commit. No push, integration or worktree removal is
part of this slice. The parent owns combined delivery and final guards.

### Sideways qualification assertion and open-ledger follow-up

This implementation follow-up preserves local commit
`f350a9007b85bfcdd6f5b94b192f3c0eeaa20882` and its history on the same owned
branch/worktree. Accepted review found two assertion limits and an absent
canonical open-bug entry. The original pressure matcher accepted a generic
missing-reference substring, so another failing owner/field could satisfy it;
the original return check counted a pre-admission attempt rather than proving
a fresh live pair. The earlier golden red's inspected XML remains actual
`SidewaysPformObjectInstance#linkedPlatform` evidence, while the weaker retained
assertions are not retroactively described as having enforced that context.

The pressure cases now compare the actual exception's complete field and
parent context: exact `SidewaysPformObjectInstance#linkedPlatform`, owner class,
registered parent ID, actual SST slot and current spawn. They also match the
missing reference to this particular rejected child and its underlying cause.
An unrelated field, parent, slot or missing target cannot satisfy the equality;
no production message or strict codec is changed.

The return check now requires new parent and child instances, both live in the
manager, reciprocal links, distinct newly registered IDs resolving to those
same instances, the next actual free SSTs after the two reused slots, absence of
the retired objects from the identity table, and successful strict capture.
Both genuine pressure occupants must remain live at the original reclaimed
SSTs with their unchanged registered IDs. The attempt counter is described as
an attempt, and the manager must contain exactly those four objects. No slot
reservation, occupant removal or fabricated capacity is added to make this
claim pass.

The [canonical open-bug ledger](../../status/known-bugs.md#s2-sideways-platform-allocation-and-strict-rewind-gap)
now records the unrepaired allocation/strict-rewind gap and links the retained
qualification. Removal requires native empty/live fixed-Tails state, failed
pointer/boundary/offset writes, paired deletion/respawn release, and reversible
strict capture/restore to be resolved. The intentional-discrepancy ledger and
release notes remain unchanged. The documentation checklist and hook mapping
were inspected: `Known-Discrepancies` maps only to the unchanged intentional
ledger, so its required policy value remains `n/a`.

Affected validation ran with the same verified environment and command
prefix above, using only the nine pressure cases and retirement method:

```bash
"${qualification_maven[@]}" '-Dtest=TestS2SidewaysPformAdmissionQualification#characterizeRealAdmissionAndFreshReplay+admittedPairRetiresAndReleasesRememberFlagAndSlots' test -B
```

The tested input was `f350a9007` plus this qualification patch, SHA-256
`bd74251920c88045988c603f961e3152792b0dbac74aa21b56782c87c2762850`;
the ledger/audit prose also differed. The completed run passed **10 cases,
0 failures, 0 errors, 0 skips**: all nine parameterized pressure cases and the
retirement/return case. It waited 811 seconds for shared admission; Maven
reported 29.546 seconds execution. These intervals are separate; other work
time is unmeasured. XML inspection confirmed each exact case identity, all
failure/error/skip elements, Java `25.0.5.0.2`, `mse=off`, the exact selector
and this task's working directory. The reports were then consumed.

The failed-admission cases now establish the exact field, owning parent
ID/SST/spawn and rejected target from the actual capture exception. Under this
same fixture, camera return establishes a fresh live reciprocal parent/child
pair with fresh identities, registered instances, the next free real SSTs and
successful capture while both reused-SST pressure occupants remain present.
The earlier attempt-only claim is therefore replaced with this narrower,
discriminating runtime proof. The open exhausted-tail capture gap remains
unrepaired, and the existing native fixed-Tails/modeling and broader coverage
limits above remain in force.

No production or scanner-obligation change requires another guard or an
unchanged existing multi-class run. Protected production/resource/tool/policy
paths are byte-identical to `f350a9007`: the production blob remains
`583a6d6822d4f50f6f9bb6e15b6fcd4808cbd02e`, all ten bridges stay count one,
the frozen store stays 119, and pins/package permissions are unchanged.
New ledger/test/audit links, heading anchors, shell snippets, Markdown fences
and `git diff --check` pass. Only documentation changed after this run.
This follow-up creates a new local commit; no history rewrite, production
change, integration, push, broad/full suite, subdelegation or worktree removal
is part of it. The parent owns any fresh review and future composition.

### Fixed-tail storage and lifetime grounding; candidate remains gated

This records the subsequent read-only research, without implementing a state
owner or running a probe. Its Java basis was composed
`fc520ae0b4a41b42903b6ad5a40247ef0ebd329e`, compared with main
`97eb86f186b432722024ca419a00b901b38f5ec2`. The relevant object-slot, tail,
snapshot and publication sources are unchanged in the documentation base
`0396a458181a0ca654846f8c82ac7a412ffa3cd5`; the owned branch fast-forwards there
from accepted `190220882f6b0a32f7f32c236d31df23baee882f` before this prose edit.
The native evidence below is exclusively the recorded S2 Git object
`34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`, with shipped `fixBugs = 0`.
Reference working trees were not reset or updated. Earlier Java qualification
results above remain historical observations; this research adds no runtime pass.

**Source evidence: storage writes and actual consumers.** The important alias
is established by offsets, not similar field names: `parent = objoff_3E`
(s2.constants.asm:73-76), whereas Obj7A writes a longword at `$3C`.

| Native boundary | Reads, writes and consequence |
| --- | --- |
| `Obj7A_LoadSubObject` (s2.asm:56321-56327), first entered with `a1=a0` | Parent mappings/art, render flags, priority, width and original X at `$30` are initialized. The failed allocation skips that initialization for the target, and also skips its ID/routine and X/Y copies in `Obj7A_SubObjectLoop` (s2.asm:56313-56319). It does not create an Obj7A at the fixed tail address. |
| `Obj7A_SubObjectLoop_End` (s2.asm:56329-56351) | Writes the parent address to target `$3C..$3F`, thereby replacing the target's `$3E` parent word; writes the target address to parent `$3C`; writes minimum/maximum words to both `$32/$34`; sets parent `$36` only for subtype `$0C`; adds the parent X offset and adds the child offset to the target's **existing** X word at `$08`. The target X fraction is untouched. Target ID, routine, art/mappings, render flags, priority, width, original-X word, direction and other residue are not initialized by this failure tail. |
| `Obj7A_Main` / `loc_294F4` (s2.asm:56353-56424) | Parent movement consumes its X, direction and boundaries, writes X/direction, then calls the existing platform-contact routine. `PlatformObject` (s2.asm:35728-35762) and `RideObject_SetRide` (s2.asm:35986-36040) maintain standing/status and player contact state; that status is also readable through the redirected tail parent. Endpoint retirement reads bounds and the coarse camera. |
| `Obj05_Main` (s2.asm:41723-41764) | Reads the overwritten parent word, then copies that storage's angle, status and X/Y; reads its art priority and animation. FixBugs=0 uses the parent's bare pushing-status bit. Its own previous-parent-animation byte is `$30`, established by `Obj05_parent_prev_anim` (s2.asm:41710), **not** the written `$32/$34` words. Thus a live tail can follow the platform rather than the original playable. |
| `Tails_Animate_Part2` (s2.asm:41271-41332), `TAnim_GetTailFrame` (s2.asm:41484-41514) | Tail animation consumes/mutates its own animation, previous animation, frame, duration, mapping frame, status and render flips. The directional branch additionally reads X/Y velocity through the redirected parent. `LoadTailsTailsDynPLC` (s2.asm:41637-41650) consumes the resulting mapping frame and updates the separate global `TailsTails_LastLoadedDPLC`; `DisplaySprite` then consumes tail presentation state. The global DPLC cache is outside the 64-byte tail SST. |
| `loc_294C4` (s2.asm:56374-56387), `DeleteObject2` (s2.asm:30334-30345) | Unless the link equals the parent itself, clear the entire **current pointed-to slot**, then release the parent's respawn flag and delete the parent. This is physical-storage deletion, not deletion of a remembered Java incarnation. |

**Source-derived cadence and lifetime, not emulator observations.**
`RunObjects` / `RunObject` (s2.asm:29805-29846) walks the full level-only range
after dynamic slots and skips an ID-zero entry; its dead-player path separately
pauses dynamics while still processing level-only objects
(`RunObjectsWhenPlayerIsDead`, s2.asm:29854-29860). An empty fixed target does
not execute Obj05, but the failure's writes remain in storage. An active target
executes later in the same normal walk and can consume the overwritten parent.
Its copied X can overwrite the earlier child-offset addition at that later
dispatch. Empty identity must not be treated as proof of zero storage.

Later `Obj02_Init_Continued` (s2.asm:38934-38946) writes the tail ID and parent
word; it does not clear the whole target. `Obj05_Init` (s2.asm:41713-41720)
initializes its ordinary header without erasing all the unwanted words. The
direct gameplay `Tails_Tails` stores found in the pinned source install ID/parent
at that player-init boundary, rather than reinstalling them every normal player
tick. In contrast, `Level_ClrRam` (s2.asm:4806-4808) clears both object and
level-only storage. A Java manager rebuild alone does not establish which native
clearing boundary occurred.

Two failed parents can retain the same physical target. The later write controls
the tail's parent, but either platform's later retirement can clear the target.
If that fixed slot is reinstalled, an older platform link still points to its
current storage. An `ObjectRefId` for an earlier occupant would express a
different lifetime. The separate DPLC cache must follow its own native writes;
clearing the tail SST does not by itself establish a cache reset. These are
control-flow consequences; their reached runtime states and presentation effects
remain unobserved natively.

**Existing Java models do not yet close that storage boundary.**
`NativeSstState` is a ten-component live-field projection, without integer X/Y,
ID/routine, the parent address or complete `$30..$3F` residue.
`NativeSstSlotAccess.currentOwner` searches live managed objects.
`ObjectNativeSstOverwrite` preserves a projection during code-owner replacement;
its exhausted choice is the last dynamic slot, whereas this after-current failure
targets the following fixed slot. Its sole production `createUnchecked` caller
is `EndingIslandLiftObjectInstance`. Reusing that operation unchanged would
model the wrong allocation failure. S2's `ObjectSlotLayout` processes 128 slots
in the object manager; `SpriteManager` separately dispatches native tail support
after the dynamic walk. The allocator bitset and `ObjectManagerSnapshot` owned
inventory are not retained empty-SST bytes.

`TailsTailsController` instead holds a final `AbstractPlayableSprite` parent,
renderer and art-decision capability, plus seven animation-state values. Update
reads that playable; drawing uses its render centre. Neither its snapshot nor
the player aggregate records native tail ID/routine, aliasable parent storage,
dormant residue or physical deletion/reinstallation. Codec scratch buffers,
render scratch, `LockOnAddressSpace` ROM readers and decompression destination
constants are not object-RAM owners. The candidate therefore needs actual owned
state; replacing a Java reference with an integer or an opaque handle alone
would conceal the gap.

**Known direct census and proposed owner scope.** The source census found eight
tail construction sites in seven files: `LevelPlayableArtInitializer` (two
paths), `S3kSlotPlayerArt`, `S3kSlotBonusStageRuntime`,
`EndingNativePlayerBinding`, `NativePlayerArtBindings`, `PlayablePuppet` and
`StageplayNativeCast`. The remaining direct symbol files are
`TailsTailsController`, `SpriteManager`, `PlayableSpriteController`,
`AbstractPlayableSprite`, `PerObjectRewindSnapshot`, `Tails`, `LocalPlayerLook`,
`StageplayLiveWorld` and `ModApiSignatureSurface`: 16 production files including
the declaration/API inventory. Four directly name the tail rewind-state type:
the two native-art bindings, playable controller and per-object snapshot.
Late-dispatch callers additionally include `LevelFrameStep`,
`RecordingFrameDriver`, `TraceReplayFixture` and the S3K initial fixed-slot
dispatcher. Sideways has its registry factory, private child constructor,
recreate path, raw anchored admission and reconstruction registration; its exact
link policy remains in `DefaultObjectRewindPolicies` with strict reference codecs.

The existing native-slot bridge has six production clients besides its own
declaration: ending island lift, ending flight child, ending rescue director,
AIZ flare child, SSZ arrival controller and Monkey Dude arm. It occurs in 24
test files; the tail-controller symbol occurs in 16 test files. These lexical
direct censuses do not close every native writer, public caller, reflective
creator or transitive reused-slot consumer. No global RAM completeness is claimed.

The concrete candidate is a **per-manager native fixed-tail storage owner with
explicit controller binding**, gated before implementation. It must own complete
target storage in empty and live states, with immutable semantic layout metadata;
Obj7A writes, Obj05 execution, presentation, deletion and capture must alias that
same state. Native address values would resolve complete owned storage in the
same manager/timeline, independently of the current code incarnation. Unknown
ordinary Java references must still fail strictly. A foreign/stale storage
reference must not silently resolve in another manager merely because its
numeric address is equal.

The bounded change budget would cover new storage/layout/binding code plus
`ObjectManager`, `ObjectManagerSnapshot`, `NativeSstSlotAccess`,
`Sonic2ObjectRegistry`, `SidewaysPformObjectInstance` and its captured-field
contract; `LevelManager`, `GameplayModeContext`, `SpriteManager` and
`LevelFrameStep`; and the controller/factory/presentation/snapshot census above.
This is more than a Sideways-only edit. Its benefit is one real authority for
presence, parent alias, unwanted writes, deletion and restoration. If closing
the parent view requires arbitrary additional SST-owner projections, the bounded
candidate ends and the parent must explicitly assess that broader scope.

Construction/art refresh must remain distinct from native incarnation admission:
rebuilding a controller or renderer must not resurrect a physically deleted tail.
The snapshot design must name a single authoritative state owner across player,
controller, native-art and object-manager snapshots, including destructive
restore ordering and controller swaps; the current seven-value record does not
decide that policy. Standalone/donor controllers and extra team tails must not all
be bound to the one native physical target. Shared code selects semantic layout
capabilities, without game/zone branches or service locators inside objects.

`DefaultObjectServices.objectManager()` follows the level's current manager,
so that lookup alone cannot validate a reference retained across a rebuild.
Independent managers/worlds, even with the same module and address values, need
disjoint mutable storage and explicit verified reset/transfer rules. Any module
publication must respect accepted `b5371e7130ea7e97da879085ce5b1e0aa064ee97`:
stable native publication ownership, lineage anchored to the actual root, exact
incarnation admission and canonical plus selected reservation. A shared module
publication is not permission to share per-manager RAM. Supported
`ObjectServices`, `AbstractObjectInstance`, `ObjectManager`/helper and controller
signatures/reachable snapshot types require an explicit compatibility decision;
no new annotation/exclusion or silent pin change is justified. **Prediction
only:** target frozen delta zero, all ten bridges retained count one, store 119
unchanged. That is not a verified API/package-closure result.

Two viable alternatives remain explicit. A real fixed-slot Obj05 dispatch owner
could reuse object recreation/identity machinery for active code while retaining
inactive storage separately. It would require semantic processing-range support,
removal of duplicate late dispatch, and the same controller/render/rewind caller
closure; an artificial live object in an empty slot would not suffice. Qualified
retention is the recommended immediate choice: keep the open bug and accepted
qualification, then establish the missing native storage/consumer evidence. The
recommendation rests on incomplete authoritative state, not high cost or an
inability to remove another allowance.

**Proposed probe and checks; not executed or launched.** The smallest useful
native probe reuses one controlled pressure setup, with empty and already-active
fixed-tail variants. Observe complete parent/target bytes at allocator return,
after Obj7A writes, at fixed-tail dispatch and after retirement; observe the
actual parent-word resolution, animation/frame/render state, DPLC cache/request
effects and a later real tail installation. A two-failed-parent variant
discriminates physical aliasing from incarnation identity. Record pre-entry
residue rather than assuming unwritten fields are zero. Do not hydrate engine
gameplay from those observations. This closes specific unknowns: live consumer
effects, dormant residue, installation/deletion cadence and whether the proposed
finite storage boundary is closed.

Only after that gate would focused implementation checks cover independent
managers/worlds; empty/live/reinstalled targets and multiple aliases; parent-last
and free-earlier/no-free-later pressure; higher-slot same-pass motion;
constructor/service/admission order and no retry; pair deletion, respawn/remember
state and occupied-slot reuse; destructive restore/replay across writes,
dispatch and retirement; strict foreign/stale-address and unmanaged-reference
negatives; and tail animation, donor banks, rendering/controller swaps and
snapshot restoration. The retained Sideways qualification/graph/schema tests,
child-slot/strict-reference/reconstruction tests, tail dispatch/pose/directional/
flight/rendering tests, donor-art tests and bonus/ending/Stageplay consumers are
maintained obligations, not newly executed validation. The
[canonical open bug](../../status/known-bugs.md#s2-sideways-platform-allocation-and-strict-rewind-gap)
stays open. Existing continuation/audit artifacts would hold any future evidence;
this documentation follow-up creates no probe, runtime policy or new report type.

Documentation verification used
`python3 tools/disasm/disasm_citations.py check --strict --limit 12 docs/architecture/audits/2026-10-24-rewind-disposition-review.md`.
The executable check passed using pinned Git objects: whole-file counts were
32 anchored and 16 unverifiable citations, with no hard/stale findings. Of the
19 new citations, 17 were anchored; the two constant assignments (`parent` and
`Obj05_parent_prev_anim`) are outside the checker's colon-label parser and were
verified directly against their pinned Git-object definitions. All 19 new
ranges were also checked directly. New Markdown structure and the canonical
bug link/anchor, source consistency against the stated Java revisions, and
`git diff --check` are checked before the documentation commit. No optional
checkout repair, Maven/test command, target/job access or emulator run is needed
or performed. Code, tests, the open-bug entry, stores, permissions and pins stay
byte-identical to the documentation base. No integration, push or worktree
removal is part of this follow-up.

## S3K monitor contents admission

Bounded worker base: `591c679b39c61f162a839cde6baaedd387135bc4`, branch
`bugfix/ai-s3k-monitor-admission-20261024`, checkout
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-9664868d`. This is a behavior correction
from the native allocation-bridge audit, not an allowance reduction. All ten
dynamic-add bridges remain **QUALIFIED RETAIN**, one occurrence each; the monitor
contents and explosion calls remain directly visible and in their original order.

The base records skdisasm `8b2cf3c4178f2382f844da67798de4befc50c5e0`.
Evidence was read with `git -C docs/skdisasm show <pin>:sonic3k.asm`, without
updating or resetting the working submodule. `Obj_MonitorSpawnIcon`
(sonic3k.asm:40685-40710) allocates contents, branches to `.skipiconcreation`
on failure, then separately allocates the explosion and branches to
`.skipexplosioncreation` on failure. Both attempts use the monitor's own SST as
the anchor. `AllocateObjectAfterCurrent` (sonic3k.asm:37957-37972) scans later
slots. Remembering the shell follows both attempts; failed contents never reach
`loc_1D7CE` / `sub_1D820` (sonic3k.asm:40730-40778) or its reward routine.

The real manager marks a rejected child destroyed and returns before identity
registration, without an unload callback. The old parent retained that child
and continued its embedded icon. Capture immediately after touch, before parent
dispatch, therefore threw `RewindIdentityTable has no registered id for object
reference` at `Sonic3kMonitorObjectInstance#monitorContentsSlot`. Without capture,
the full-tail ring monitor still awarded ten rings. Neither claim was assumed
from the source audit alone.

`TestS3kMonitorGraphRewind` now fills the tail with registered, reconstructible
inert occupants, leaving zero, one or two noncontiguous free slots. A recording
manager delegates both calls to the production ObjectManager allocator and
identity registry; it does not mock admission or reserve bare bits. The six
stock-ring / creator-factory-Eggman cases check immediate capture, exact IDs and
slots, both allocation results/order/anchors, broken/remembered flags, icon and
reward state, unchanged break SFX, zero RNG consumption, destructive graph
restore, every forward snapshot through retirement, pre-break reallocation and
remembered respawn. Both admitted links must restore bidirectionally and exactly.
Two additional dispatch cases break from SST 3 and 80: the monitor remains the
allocation anchor and its already-passed slot defers icon motion until the next
pass. Another case independently checks that rejection cannot award a reward.

The initial test submission had an ambiguous JUnit method reference and stopped
at compilation (no tests executed). After correction, seven cases executed and
failed: two real invalid-reference failures, one real rejected reward, and four
incorrect test assumptions about context-free capture. The admitted monitor's
public no-table capture already rejects its non-null managed child link; that
strict contract is preserved. Correcting those four assertions, without changing
production, produced **11 cases: three failures, eight passes, zero errors/skips**.
The failing identities were
`nativeAdmissionCapturesBeforeDispatchAndReplaysExactGraph(int, boolean)[1,4]`
(zero free slots, stock/factory) and
`rejectedContentsNeverAwardEvenWithoutAnImmediateCapture`. Cases `[2,3,5,6]`,
both `touchDuringAnotherSlotKeepsMonitorAnchorAndDispatchPhase(int)` invocations,
and the two original graph methods passed on the unchanged production base.

The fix keeps a local candidate until admission succeeds. Rejection clears the
parent's `iconActive` and `effectTarget`, leaving no retained unregistered link.
It intentionally keeps initialization before insertion: identity registration
reads the child's parent-derived icon position through `getSpawn()`. Starting
the icon after insertion would change the admitted child's spawn/identity recipe.
Inactive seeded motion values remain captured but never advance or pay out.
Moving the whole icon into the child was rejected as unnecessary: it would change
dispatch, drawing and protected callback ownership to solve a local admission
error. `applyPowerup` and its existing manager/creator fault boundary stay with
the parent. The null-manager path, creator content selection and exact placement
contract in `creator-helpers.md` remain supported. No codec, capture override,
exclusion, API pin, framework, global state or allowance changes are involved.

All Maven commands run from the worker checkout using this environment:

```bash
MAIN_CHECKOUT=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS3kMonitorGraphRewind' test -B
```

That is the corrected RED command. The initial two submissions instead selected
`TestS3kMonitorGraphRewind#nativeAdmissionCapturesBeforeDispatchAndReplaysExactGraph+rejectedContentsNeverAwardEvenWithoutAnImmediateCapture`.
The initial compile-only attempt waited 69 seconds and Maven reported 1:23;
the seven-case run waited 5 seconds and reported 25.445 seconds; corrected RED
waited 28 seconds and reported 27.173 seconds. These are distinct queue/execution
observations, not total delivery time.

The affected GREEN command uses the same wrapper/environment/flags with the exact
selector below, plus the verified absolute existing ROM properties:

```text
-Dtest=com.openggf.game.rewind.TestS3kMonitorGraphRewind,com.openggf.game.sonic3k.objects.TestSonic3kMonitorContentOverride,com.openggf.game.sonic3k.objects.TestSonic3kMonitorObjectInstance,com.openggf.tests.TestSonic3kMonitorObjectInstance,com.openggf.level.objects.TestObjectManagerChildSlotAllocation,com.openggf.level.objects.TestNativeObjectSlotOccupancy,com.openggf.tests.objects.TestObjectManagerSlotRecycleIdentity
-Dsonic1.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog (W) (REV01) [!].gen
-Dsonic2.rom.path=$MAIN_CHECKOUT/Sonic The Hedgehog 2 (W) (REV01) [!].gen
-Ds3k.rom.path=$MAIN_CHECKOUT/Sonic and Knuckles & Sonic 3 (W) [!].gen
```

The three files match the documented CRC32/SHA-1 identities. The queued version
probe reported Java 25.0.5.0.2; `/usr/bin/lua5.4 -v` reported 5.4.9. The affected
selection passed **47 cases across seven exact classes, zero failures, errors or
skips**: graph 11, creator contents 4, native monitor fixture 20, the second
physical monitor fixture 2, child allocation 8, raw occupancy 1 and recycled
identity 1. The formerly failing graph identities now pass. Final graph assertions
also cover unchanged SFX requests and remembered reload after all three capacity
outcomes. The tested state is the base above plus this commit's production and
test changes; only documentation changed after the completed run. Admission
waited 81 seconds; Maven reported 1:30. All XML identities and absence of
failures/errors/skips were inspected, then the consumed reports were removed.
No category run, pass cache or validation receipt was created. The bounded
citation check against the recorded pin completed successfully (15 exact, one
nearby and 31 unverifiable historical/source references); the native ranges
above were also read directly from Git objects. `git diff --check` passed.
No live Maven handle remains and no duplicate request or resource-flag change
was used for admission. The local branch/checkout remain for parent review; the
worker neither pushes nor removes them. Parent owns combined guards/API/S3K stability,
integration and push; this worker claims no full route, GPU rendering, exhaustive
trace or release-suite certification. Existing parent-owned break SFX is retained
even when explosion allocation fails; native `loc_1E61A`
(sonic3k.asm:42224-42227) instead requests it in the admitted explosion's dispatch.
That inherited timing difference is outside this correction. No creator semantic
was narrowed to conceal a disagreement with native behavior.
