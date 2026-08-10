package com.openggf.game.sonic3k;

import com.openggf.game.session.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionLease;
import com.openggf.game.RuntimeArtAdmissionOwnerKind;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.FbzEndBossInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.game.sonic3k.resources.S3kKosRamDestinations;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareTimingJob;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link Sonic3kObjectArtProvider}'s
 * {@link com.openggf.game.rewind.RewindSnapshottable} implementation (Track F.2).
 *
 * <p>Tests verify that the key and epoch capture are stable without requiring
 * a full level load.
 */
class TestSonic3kPlcArtRewindSnapshot {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void keyIsS3kPlcArt() {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        assertEquals("s3k-plc-art", provider.key());
    }

    @Test
    void initialEpochIsZero() {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        PlcProgressSnapshot snap = provider.capture();
        assertEquals(0, snap.loadEpoch(),
                "Initial epoch should be 0 before any zone load");
        assertEquals(List.of(), snap.publishedLevelArtKeys());
    }

    @Test
    void captureReturnsSameEpochOnSecondCall() {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        PlcProgressSnapshot snap1 = provider.capture();
        PlcProgressSnapshot snap2 = provider.capture();
        assertEquals(snap1.loadEpoch(), snap2.loadEpoch(),
                "Epoch must not change between captures without a zone load");
    }

    @Test
    void restoreOfInitialSnapshotPreservesEmptyPublicationState() {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        PlcProgressSnapshot snap = provider.capture();
        // Restore should not throw and epoch should stay the same
        assertDoesNotThrow(() -> provider.restore(snap));
        assertEquals(snap.loadEpoch(), provider.capture().loadEpoch());
        assertEquals(List.of(), provider.capture().publishedLevelArtKeys());
    }

    @Test
    void restoreReinstatesPendingRuntimeArtRequest() {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        provider.queueCnzTeleporterArt();
        PlcProgressSnapshot pending = provider.capture();

        Sonic3kObjectArtProvider restored = new Sonic3kObjectArtProvider();
        restored.restore(pending);

        assertTrue(restored.isCnzTeleporterArtPending());
        assertFalse(restored.isCnzTeleporterArtComplete());
    }

    @Test
    void restoreKeepsTeleporterAndEndBossQueuesIndependent() {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        provider.queueCnzEndBossArt();

        Sonic3kObjectArtProvider restored = new Sonic3kObjectArtProvider();
        restored.restore(provider.capture());

        assertTrue(restored.isCnzEndBossArtPending());
        assertFalse(restored.isCnzEndBossArtComplete());
        assertFalse(restored.isCnzTeleporterArtPending());
    }

    @Test
    void snapshotRecordPreservesEpoch() {
        PlcProgressSnapshot snap = new PlcProgressSnapshot(99, List.of("dynamic"));
        assertEquals(99, snap.loadEpoch());
        assertEquals(List.of("dynamic"), snap.publishedLevelArtKeys());
    }

    @Nested
    class RomBackedComposite {
        @Test
        @RequiresRom(SonicGame.SONIC_3K)
        void compositeRestoreReconcilesDeferredExitRenderersInBothDirections() throws Exception {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(4, 1)
                    .build();
            ObjectRenderManager renderManager = GameServices.level().getObjectRenderManager();
            Sonic3kObjectArtProvider provider = assertInstanceOf(
                    Sonic3kObjectArtProvider.class, renderManager.getArtProvider());
            List<String> exitKeys = List.of(
                    Sonic3kObjectArtKeys.FBZ_EXIT_DOOR,
                    Sonic3kObjectArtKeys.FBZ_EXIT_HALL_DOOR_SCENERY,
                    Sonic3kObjectArtKeys.FBZ_EXIT_HALL);
            FbzEndBossInstance boss = fixture.gameplayMode().getObjectManager().createDynamicObject(
                    () -> new FbzEndBossInstance(new ObjectSpawn(
                            0x307C, 0x648, FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0)));
            assertNotNull(boss);
            setField(boss, "phaseOrdinal", FbzEndBossInstance.Phase.EXIT_READY.ordinal());
            setField(boss, "nativeStarted", true);
            fixture.camera().setY((short) 0x0600);
            KosinskiModuleQueue queue = fixture.gameplayMode().getKosinskiModuleQueue();
            queue.restore(new KosinskiModuleQueue.Snapshot(List.of(
                    new KosinskiModuleQueue.ArchiveState(0x165BCA, 0x165BCC, 0x7CA0,
                            0x200, 1, 1, 0x100, -1, true)),
                    KosinskiModuleQueue.Phase.READY_TO_START, null, List.of(), List.of()));
            CompositeSnapshot before = fixture.gameplayMode().getRewindRegistry().capture();
            PlcProgressSnapshot beforeArt = assertInstanceOf(
                    PlcProgressSnapshot.class, before.get(provider.key()));
            assertEquals(List.of(), beforeArt.publishedLevelArtKeys());
            assertTrue(exitKeys.stream().allMatch(key -> renderManager.getRenderer(key) == null));
            assertFalse(queue.isIdle());
            assertFalse(booleanField(boss, "exitArtConsumersPublished"));

            queue.clear();
            boss.update(1, fixture.sprite());
            CompositeSnapshot after = fixture.gameplayMode().getRewindRegistry().capture();
            PlcProgressSnapshot afterArt = assertInstanceOf(
                    PlcProgressSnapshot.class, after.get(provider.key()));
            assertEquals(exitKeys, afterArt.publishedLevelArtKeys());
            assertTrue(exitKeys.stream().allMatch(key -> renderManager.getRenderer(key).isReady()));
            assertTrue(queue.isIdle());
            assertTrue(booleanField(boss, "exitArtConsumersPublished"));

            fixture.gameplayMode().getRewindRegistry().restore(before);
            FbzEndBossInstance restoredBeforeBoss = fixture.gameplayMode().getObjectManager()
                    .activeObjectsOfType(FbzEndBossInstance.class).getFirst();
            assertTrue(exitKeys.stream().allMatch(key -> renderManager.getRenderer(key) == null));
            assertTrue(exitKeys.stream().allMatch(key -> renderManager.getSheet(key) == null));
            assertFalse(queue.isIdle(), "backward restore reinstates the in-flight KosM queue");
            assertEquals(FbzEndBossInstance.Phase.EXIT_READY, restoredBeforeBoss.phase());
            assertFalse(booleanField(restoredBeforeBoss, "exitArtConsumersPublished"));
            assertTrue(provider.getAffectedRendererKeys(List.of(new Sonic3kPlcLoader.TileRange(
                            Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR, 0x80))).stream()
                    .noneMatch(exitKeys::contains), "backward restore removes deferred tile ranges");

            fixture.gameplayMode().getRewindRegistry().restore(after);
            FbzEndBossInstance restoredAfterBoss = fixture.gameplayMode().getObjectManager()
                    .activeObjectsOfType(FbzEndBossInstance.class).getFirst();
            assertEquals(exitKeys, provider.capture().publishedLevelArtKeys());
            assertTrue(exitKeys.stream().allMatch(key -> renderManager.getRenderer(key) != null));
            assertTrue(exitKeys.stream().allMatch(key -> renderManager.getRenderer(key).isReady()),
                    "forward restore rebuilds and recaches all deferred consumers");
            assertTrue(provider.getAffectedRendererKeys(List.of(new Sonic3kPlcLoader.TileRange(
                            Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR, 0x80))).containsAll(exitKeys),
                    "forward restore recreates deferred tile-range reconciliation");
            assertTrue(queue.isIdle());
            assertTrue(booleanField(restoredAfterBoss, "exitArtConsumersPublished"));
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean booleanField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    @Test
    void snapshotPreservesEnemyEntriesAndRetirementArmState()
            throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        Method schedule = Sonic3kObjectArtProvider.class.getDeclaredMethod(
                "scheduleEnemyKosArt", int.class, int.class);
        schedule.setAccessible(true);
        schedule.invoke(provider, 0, 0);
        PlcProgressSnapshot beforeRetirement = provider.capture();

        assertEquals(3, beforeRetirement.pendingKosModules().size());
        assertEquals(0x36800C,
                beforeRetirement.pendingKosModules().get(0).sourceAddress());
        assertFalse(beforeRetirement.kosSubmissionArmed());

        retirePreparedTitle(provider);
        PlcProgressSnapshot afterRetirement = provider.capture();
        assertTrue(afterRetirement.kosSubmissionArmed());

        Sonic3kObjectArtProvider restored = new Sonic3kObjectArtProvider();
        restored.restore(beforeRetirement);
        PlcProgressSnapshot restoredSnapshot = restored.capture();
        assertEquals(beforeRetirement.pendingKosModules(),
                restoredSnapshot.pendingKosModules());
        assertEquals(List.of(), restoredSnapshot.pendingKosOrdinals());
        assertFalse(restoredSnapshot.kosSubmissionArmed());
    }

    @Test
    void issuedLeaseIsGenerationBatchAndOwnerBoundAndConsumesExactlyOnce()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        RuntimeArtAdmissionLease lease = exactPreparedTitleLease(provider);

        assertTrue(lease.generation() > 0,
                "the lease must be bound to the provider's current positive generation");
        assertNotEquals(0, lease.batchFingerprint());
        assertEquals(RuntimeArtAdmissionOwnerKind.TITLE_OWNER, lease.ownerKind());
        provider.consumeRuntimeArtAdmission(
                lease, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);

        assertTrue(provider.capture().kosSubmissionArmed());
        assertTrue(snapshotAdmissionConsumed(provider.capture()));
        assertThrows(IllegalStateException.class, () ->
                provider.consumeRuntimeArtAdmission(
                        lease, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
    }

    @Test
    void laterInLevelTitleGetsFreshTitleOwnerAfterPreviousBatchWasConsumed()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_ICZ, 0);
        RuntimeArtAdmissionLease previous = exactPreparedTitleLease(provider);

        provider.consumeRuntimeArtAdmission(
                previous, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        PlcProgressSnapshot beforeTitle = provider.capture();
        assertTrue(beforeTitle.runtimeArtAdmissionConsumed());
        assertEquals(2, beforeTitle.pendingKosModules().size());

        provider.prepareRuntimeArtForInLevelTitleCard();

        PlcProgressSnapshot preparedTitle = provider.capture();
        assertNotEquals(previous.id(), preparedTitle.runtimeArtAdmissionLeaseId());
        assertEquals(RuntimeArtAdmissionOwnerKind.TITLE_OWNER,
                preparedTitle.runtimeArtAdmissionOwnerKind());
        assertFalse(preparedTitle.runtimeArtAdmissionConsumed());
        assertNotEquals(previous.batchFingerprint(),
                preparedTitle.runtimeArtAdmissionBatchFingerprint(),
                "the presentation lease does not claim the already-admitted enemy batch");
        assertEquals(beforeTitle.pendingKosModules(), preparedTitle.pendingKosModules(),
                "preparing a later title must not replace admitted enemy work");

        RuntimeArtAdmissionLease titleLease = provider.bindPendingRuntimeArtAdmission(
                RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        provider.consumeRuntimeArtAdmission(
                titleLease, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        assertTrue(provider.capture().runtimeArtAdmissionConsumed());
    }

    @Test
    void laterInLevelTitleAdmitsFreshEnemyBatchAfterPreviousBatchRetires()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        RuntimeArtAdmissionLease previous = exactPreparedTitleLease(provider);

        provider.consumeRuntimeArtAdmission(
                previous, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        provider.processRuntimeArtQueue();
        for (int frame = 0; frame < 1_000; frame++) {
            HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
            provider.processRuntimeArtQueue();
            PlcProgressSnapshot progress = provider.capture();
            if (progress.pendingKosModules().isEmpty()
                    && progress.pendingKosOrdinals().isEmpty()) {
                break;
            }
        }
        PlcProgressSnapshot beforeTitle = provider.capture();
        assertEquals(List.of(), beforeTitle.pendingKosModules());
        assertEquals(List.of(), beforeTitle.pendingKosOrdinals());

        provider.prepareRuntimeArtForInLevelTitleCard();

        PlcProgressSnapshot preparedTitle = provider.capture();
        assertNotEquals(previous.id(), preparedTitle.runtimeArtAdmissionLeaseId());
        assertEquals(RuntimeArtAdmissionOwnerKind.TITLE_OWNER,
                preparedTitle.runtimeArtAdmissionOwnerKind());
        assertFalse(preparedTitle.runtimeArtAdmissionConsumed());
        assertEquals(3, preparedTitle.pendingKosModules().size(),
                "a retired LoadEnemyArt batch must be replaced for the next title owner");
        assertEquals(previous.batchFingerprint(),
                preparedTitle.runtimeArtAdmissionBatchFingerprint(),
                "AIZ reloads the same ROM-owned enemy batch for the next title");
    }

    @Test
    void missingStaleAndMutatedLeaseIdentitiesFailClosed() throws Exception {
        Sonic3kObjectArtProvider missing = new Sonic3kObjectArtProvider();
        RuntimeArtAdmissionLease fabricated = new RuntimeArtAdmissionLease(
                17, 1, 0x1234, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        assertThrows(IllegalStateException.class, () ->
                missing.consumeRuntimeArtAdmission(
                        fabricated, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));

        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        RuntimeArtAdmissionLease stale = exactPreparedTitleLease(provider);

        provider.prepareRuntimeArtForActTransition(
                Sonic3kZoneIds.ZONE_ICZ,
                RuntimeArtAdmissionPolicy.TITLE_OWNER);
        RuntimeArtAdmissionLease current = exactPreparedTitleLease(provider);

        assertThrows(IllegalStateException.class, () ->
                provider.consumeRuntimeArtAdmission(
                        stale, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
        assertThrows(IllegalStateException.class, () ->
                provider.consumeRuntimeArtAdmission(
                        new RuntimeArtAdmissionLease(
                                current.id(), current.generation() + 1,
                                current.batchFingerprint(), current.ownerKind()),
                        RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
        assertThrows(IllegalStateException.class, () ->
                provider.consumeRuntimeArtAdmission(
                        new RuntimeArtAdmissionLease(
                                current.id(), current.generation(),
                                current.batchFingerprint() ^ 1, current.ownerKind()),
                        RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
        assertThrows(IllegalStateException.class, () ->
                provider.consumeRuntimeArtAdmission(
                        current, RuntimeArtAdmissionOwnerKind.IMMEDIATE));

        assertFalse(provider.capture().kosSubmissionArmed(),
                "failed release attempts cannot arm the current batch");
        provider.consumeRuntimeArtAdmission(
                current, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        assertTrue(provider.capture().kosSubmissionArmed(),
                "the exact current lease remains consumable after rejected attempts");
    }

    @Test
    void heldAndConsumedAdmissionLeasesRoundTripWithoutClaimingCurrentBatch()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_ICZ, 0);
        RuntimeArtAdmissionLease held = exactPreparedTitleLease(provider);
        PlcProgressSnapshot heldSnapshot = provider.capture();

        Sonic3kObjectArtProvider restoredHeld = new Sonic3kObjectArtProvider();
        restoredHeld.restore(heldSnapshot);
        assertEquals(held, restoredHeld.rebindRuntimeArtAdmission(
                held.id(), RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
        assertFalse(restoredHeld.capture().runtimeArtAdmissionConsumed());

        restoredHeld.consumeRuntimeArtAdmission(
                held, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        PlcProgressSnapshot consumedSnapshot = restoredHeld.capture();
        Sonic3kObjectArtProvider restoredConsumed = new Sonic3kObjectArtProvider();
        restoredConsumed.restore(consumedSnapshot);

        assertEquals(held.id(), restoredConsumed.capture().runtimeArtAdmissionLeaseId());
        assertTrue(restoredConsumed.capture().runtimeArtAdmissionConsumed());
        assertThrows(IllegalStateException.class, () ->
                restoredConsumed.consumeRuntimeArtAdmission(
                        held, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
    }

    @Test
    void skippedTitleSnapshotRetainsItsExactLeaseAfterChildrenDrainOnTickThirtyFour()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        PlcProgressSnapshot prepared = provider.capture();
        RuntimeArtAdmissionLease lease = leaseFrom(prepared);
        provider.onTitleCardPresentationSkipped();
        for (int tick = 1; tick <= 34; tick++) {
            provider.processRuntimeArtQueue();
        }
        PlcProgressSnapshot snapshot = provider.capture();
        assertEquals(lease.id(), snapshot.titleCardTeardownLeaseId());
        assertEquals(34, snapshot.titleCardTeardownTicks());
        assertFalse(snapshot.runtimeArtAdmissionConsumed());
        assertEquals(List.of(), snapshot.pendingKosOrdinals(),
                "the last child retirement cannot submit the enemy batch");

        Sonic3kObjectArtProvider restored = new Sonic3kObjectArtProvider();
        restored.restore(snapshot);
        restored.processRuntimeArtQueue();

        assertTrue(restored.capture().runtimeArtAdmissionConsumed());
        assertEquals(-1, restored.capture().titleCardTeardownLeaseId());
        assertEquals(3, restored.capture().pendingKosOrdinals().size(),
                "the restored lower-slot owner submits one exact AIZ enemy batch");
        assertThrows(IllegalStateException.class, () ->
                restored.consumeRuntimeArtAdmission(
                        lease, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
    }

    @Test
    void legacyRetirementCannotFabricateOrSelectAnAdmissionLease() {
        Sonic3kObjectArtProvider missing = new Sonic3kObjectArtProvider();
        PlcProgressSnapshot missingBefore = missing.capture();

        assertThrows(IllegalStateException.class,
                missing::onTitleCardArtRetired);
        assertEquals(missingBefore, missing.capture(),
                "missing legacy retirement cannot schedule, issue, bind, or arm work");

        Sonic3kObjectArtProvider loaded = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        PlcProgressSnapshot heldBefore = loaded.capture();

        assertThrows(IllegalStateException.class,
                loaded::onTitleCardArtRetired);
        assertEquals(heldBefore, loaded.capture(),
                "legacy retirement cannot select and release the current lease");
    }

    @Test
    void skippedPresentationRequiresAnExistingUnboundTitleLease() {
        Sonic3kObjectArtProvider missing = new Sonic3kObjectArtProvider();
        PlcProgressSnapshot missingBefore = missing.capture();

        assertThrows(IllegalStateException.class,
                missing::onTitleCardPresentationSkipped);
        assertEquals(missingBefore, missing.capture(),
                "missing skipped-title ownership cannot mutate provider state");

        Sonic3kObjectArtProvider wrongOwner = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        wrongOwner.prepareRuntimeArtForActTransition(
                Sonic3kZoneIds.ZONE_AIZ,
                RuntimeArtAdmissionPolicy.IMMEDIATE);
        PlcProgressSnapshot wrongOwnerBefore = wrongOwner.capture();

        assertThrows(IllegalStateException.class,
                wrongOwner::onTitleCardPresentationSkipped);
        assertEquals(wrongOwnerBefore, wrongOwner.capture(),
                "wrong-owner skipped presentation cannot clear, schedule, or arm work");
    }

    @Test
    void inLevelCompletionRejectsStaleAndMissingLeasesAtomically() {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        RuntimeArtAdmissionLease stale = exactPreparedTitleLease(provider);
        provider.prepareRuntimeArtForActTransition(
                Sonic3kZoneIds.ZONE_AIZ,
                RuntimeArtAdmissionPolicy.TITLE_OWNER);
        PlcProgressSnapshot replacement = provider.capture();

        assertThrows(IllegalStateException.class,
                () -> provider.onInLevelTitleCardCompleted(stale));
        assertEquals(replacement, provider.capture(),
                "stale completion cannot consume, arm, defer, or clear the current batch");

        Sonic3kObjectArtProvider missing = new Sonic3kObjectArtProvider();
        PlcProgressSnapshot missingBefore = missing.capture();
        assertThrows(IllegalStateException.class,
                () -> missing.onInLevelTitleCardCompleted(stale));
        assertEquals(missingBefore, missing.capture(),
                "missing completion cannot create provider lifecycle state");
    }

    @Test
    void inLevelCompletionRewindsAcrossTheNextRuntimePassEdge() {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        RuntimeArtAdmissionLease lease = exactPreparedTitleLease(provider);

        provider.onInLevelTitleCardCompleted(lease);
        PlcProgressSnapshot beforeEdge = provider.capture();
        assertTrue(beforeEdge.runtimeArtAdmissionConsumed());
        assertFalse(beforeEdge.kosSubmissionArmed());
        assertTrue((beforeEdge.runtimeState() & (1 << 4)) != 0,
                "ordinary in-level completion records the deferred next-pass edge");

        provider.processRuntimeArtQueue();
        PlcProgressSnapshot afterEdge = provider.capture();
        assertTrue(afterEdge.kosSubmissionArmed());
        assertTrue((afterEdge.runtimeState() & (1 << 4)) == 0);
        assertEquals(3, afterEdge.pendingKosOrdinals().size(),
                "the admitted pass arms and submits the native enemy batch");

        Sonic3kObjectArtProvider restoredBefore = new Sonic3kObjectArtProvider();
        restoredBefore.restore(beforeEdge);
        assertEquals(beforeEdge, restoredBefore.capture(),
                "rewinding before the edge restores the deferred admission exactly");

        Sonic3kObjectArtProvider restoredAfter = new Sonic3kObjectArtProvider();
        restoredAfter.restore(afterEdge);
        restoredAfter.processRuntimeArtQueue();
        assertEquals(afterEdge, restoredAfter.capture(),
                "rewinding after the edge preserves the submitted parents");
    }

    private static Sonic3kObjectArtProvider loadProvider(int zone, int act) {
        HeadlessTestFixture.builder()
                .withZoneAndAct(zone, act)
                .build();
        return (Sonic3kObjectArtProvider) GameServices.module()
                .getObjectArtProvider();
    }

    private static boolean snapshotAdmissionConsumed(PlcProgressSnapshot snapshot) {
        return snapshot.runtimeArtAdmissionConsumed();
    }

    private static RuntimeArtAdmissionLease leaseFrom(PlcProgressSnapshot snapshot) {
        return new RuntimeArtAdmissionLease(
                snapshot.runtimeArtAdmissionLeaseId(),
                snapshot.runtimeArtAdmissionGeneration(),
                snapshot.runtimeArtAdmissionBatchFingerprint(),
                snapshot.runtimeArtAdmissionOwnerKind());
    }

    private static RuntimeArtAdmissionLease exactPreparedTitleLease(
            Sonic3kObjectArtProvider provider) {
        PlcProgressSnapshot snapshot = provider.capture();
        if (snapshot.runtimeArtAdmissionBound()) {
            return provider.rebindRuntimeArtAdmission(
                    snapshot.runtimeArtAdmissionLeaseId(),
                    RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        }
        return provider.bindPendingRuntimeArtAdmission(
                RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
    }

    @Test
    void skippedInitialTitleOwnerHoldsRuntimeArtThroughChildRetirementOnTickThirtyFour()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        provider.onTitleCardPresentationSkipped();

        for (int tick = 1; tick <= 34; tick++) {
            provider.processRuntimeArtQueue();
        }

        PlcProgressSnapshot beforeProductionRetirement = provider.capture();
        assertEquals(34, beforeProductionRetirement.titleCardTeardownTicks());
        assertFalse(beforeProductionRetirement.kosSubmissionArmed(),
                "the skipped initial title owner already returned before its final child retired");
        assertFalse(beforeProductionRetirement.runtimeArtAdmissionConsumed());
        assertEquals(List.of(), beforeProductionRetirement.pendingKosOrdinals());
    }

    @Test
    void skippedInitialTitleOwnerReleasesRuntimeArtOnTickThirtyFiveOnlyOnce()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        provider.onTitleCardPresentationSkipped();

        for (int tick = 1; tick <= 35; tick++) {
            provider.processRuntimeArtQueue();
        }

        PlcProgressSnapshot released = provider.capture();
        assertEquals(-1, released.titleCardTeardownTicks(),
                "the provider drops its completed skipped-title owner at tick 35");
        assertTrue(released.kosSubmissionArmed(),
                "tick 35 is the production LoadEnemyArt release boundary");

        provider.processRuntimeArtQueue();

        PlcProgressSnapshot afterRelease = provider.capture();
        assertEquals(-1, afterRelease.titleCardTeardownTicks());
        assertTrue(afterRelease.kosSubmissionArmed(),
                "the completed skipped-title owner cannot release a second time");
    }

    @Test
    void productionPumpSubmitsExactFirstEnemyParentAndChildOnlyOnTickThirtyFive()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        provider.onTitleCardPresentationSkipped();

        for (int tick = 1; tick <= 34; tick++) {
            provider.processRuntimeArtQueue();
        }

        assertEquals(0, jobs(HardwareWorkKind.KOS_MODULE_QUEUE, 0x36800C).size(),
                "trace frame 33 cannot contain the first Monkey Dude parent");
        assertEquals(0, jobs(HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, 0x36800E).size(),
                "trace frame 33 cannot contain its first direct child");

        provider.processRuntimeArtQueue();

        List<HardwareTimingJob.Snapshot> parents =
                jobs(HardwareWorkKind.KOS_MODULE_QUEUE, 0x36800C);
        assertEquals(1, parents.size());
        assertEquals(0x548 * 32, parents.getFirst().destinationAddress(),
                "LoadEnemyArt schedules Monkey Dude at ArtTile_MonkeyDude");

        S3kRuntimeArtCoordinator.current().moduleQueue()
                .processModuleQueueAfterObjects();

        List<HardwareTimingJob.Snapshot> children =
                jobs(HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, 0x36800E);
        assertEquals(1, children.size());
        assertEquals(S3kKosRamDestinations.KOS_DECOMP_BUFFER,
                children.getFirst().destinationAddress());
    }

    @Test
    void postCompletionRestoreCannotReleaseOrSubmitTheEnemyBatchAgain()
            throws Exception {
        Sonic3kObjectArtProvider provider = loadProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        provider.onTitleCardPresentationSkipped();
        for (int tick = 1; tick <= 35; tick++) {
            provider.processRuntimeArtQueue();
        }
        PlcProgressSnapshot completed = provider.capture();
        int submittedParents = jobs(
                HardwareWorkKind.KOS_MODULE_QUEUE, 0x36800C).size();

        Sonic3kObjectArtProvider restored = new Sonic3kObjectArtProvider();
        restored.restore(completed);
        restored.processRuntimeArtQueue();

        assertEquals(submittedParents,
                jobs(HardwareWorkKind.KOS_MODULE_QUEUE, 0x36800C).size());
        assertEquals(-1, restored.capture().titleCardTeardownTicks());
        assertTrue(restored.capture().runtimeArtAdmissionConsumed());
    }

    private static List<HardwareTimingJob.Snapshot> jobs(
            HardwareWorkKind kind, int sourceAddress) {
        return GameServices.hardwareTiming().capture().jobs().stream()
                .filter(job -> job.kind() == kind)
                .filter(job -> job.romSourceAddress() == sourceAddress)
                .toList();
    }

    @Test
    void iczEnemyArtScheduleMatchesLoadEnemyArtTable() throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        Method schedule = Sonic3kObjectArtProvider.class.getDeclaredMethod(
                "scheduleEnemyKosArt", int.class, int.class);
        schedule.setAccessible(true);
        schedule.invoke(provider, Sonic3kZoneIds.ZONE_ICZ, 0);

        PlcProgressSnapshot scheduled = provider.capture();
        assertEquals(List.of(
                        new PlcProgressSnapshot.PendingKosModule(0x375134, 0x0558),
                        new PlcProgressSnapshot.PendingKosModule(0x3751C6, 0x0548)),
                scheduled.pendingKosModules(),
                "PLCKosM_ICZ queues Snowdust then StarPointer "
                        + "(sonic3k.asm:64392-64395)");
        assertFalse(scheduled.kosSubmissionArmed());

        retirePreparedTitle(provider);
        assertTrue(provider.capture().kosSubmissionArmed(),
                "LoadEnemyArt runs when the normal title-card owner retires "
                        + "(sonic3k.asm:62287-62300)");
    }

    @Test
    void mgzAndCnzEnemyArtSchedulesMatchLoadEnemyArtTable() throws Exception {
        assertEnemyArtProfile(
                Sonic3kZoneIds.ZONE_MGZ,
                0,
                List.of(
                        new PlcProgressSnapshot.PendingKosModule(0x36E0C4, 0x0530),
                        new PlcProgressSnapshot.PendingKosModule(0x36B02C, 0x054F),
                        new PlcProgressSnapshot.PendingKosModule(0x36D572, 0x0570)));
        assertEnemyArtProfile(
                Sonic3kZoneIds.ZONE_MGZ,
                1,
                List.of(
                        new PlcProgressSnapshot.PendingKosModule(0x36E0C4, 0x0530),
                        new PlcProgressSnapshot.PendingKosModule(0x36E2D6, 0x054F)));
        assertEnemyArtProfile(
                Sonic3kZoneIds.ZONE_CNZ,
                0,
                List.of(
                        new PlcProgressSnapshot.PendingKosModule(0x3700CA, 0x0524),
                        new PlcProgressSnapshot.PendingKosModule(0x3703EC, 0x0552),
                        new PlcProgressSnapshot.PendingKosModule(0x370058, 0x0570),
                        new PlcProgressSnapshot.PendingKosModule(0x37060E, 0x0574)));
    }

    private static void assertEnemyArtProfile(
            int zone, int act,
            List<PlcProgressSnapshot.PendingKosModule> expected)
            throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        Method schedule = Sonic3kObjectArtProvider.class.getDeclaredMethod(
                "scheduleEnemyKosArt", int.class, int.class);
        schedule.setAccessible(true);
        schedule.invoke(provider, zone, act);

        PlcProgressSnapshot beforeRetirement = provider.capture();
        assertEquals(expected, beforeRetirement.pendingKosModules());
        assertEquals(List.of(), beforeRetirement.pendingKosOrdinals(),
                "LoadEnemyArt must not submit before title-card retirement");
        assertFalse(beforeRetirement.kosSubmissionArmed());

        retirePreparedTitle(provider);
        PlcProgressSnapshot armed = provider.capture();
        assertTrue(armed.kosSubmissionArmed());

        Sonic3kObjectArtProvider restored = new Sonic3kObjectArtProvider();
        restored.restore(armed);
        assertEquals(armed.pendingKosModules(),
                restored.capture().pendingKosModules());
        assertEquals(armed.pendingKosOrdinals(),
                restored.capture().pendingKosOrdinals());
        assertTrue(restored.capture().kosSubmissionArmed());
    }

    private static void retirePreparedTitle(Sonic3kObjectArtProvider provider) {
        RuntimeArtAdmissionLease lease = provider.issueRuntimeArtAdmissionLease(
                RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        provider.bindRuntimeArtAdmission(
                lease.id(), RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        provider.consumeRuntimeArtAdmission(
                lease, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
    }
}
