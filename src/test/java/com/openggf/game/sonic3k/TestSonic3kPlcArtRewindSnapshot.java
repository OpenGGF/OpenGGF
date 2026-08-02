package com.openggf.game.sonic3k;

import com.openggf.game.RuntimeArtAdmissionLease;
import com.openggf.game.RuntimeArtAdmissionOwnerKind;
import com.openggf.game.session.EngineServices;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
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
    void restorePreservesEpoch() {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        PlcProgressSnapshot snap = provider.capture();
        // Restore should not throw and epoch should stay the same
        assertDoesNotThrow(() -> provider.restore(snap));
        assertEquals(snap.loadEpoch(), provider.capture().loadEpoch());
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
        PlcProgressSnapshot snap = new PlcProgressSnapshot(99);
        assertEquals(99, snap.loadEpoch());
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

        provider.onTitleCardArtRetired();
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
        Sonic3kObjectArtProvider provider = scheduledProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        RuntimeArtAdmissionLease lease = issueLease(
                provider, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);

        assertEquals(1, lease.generation());
        assertNotEquals(0, lease.batchFingerprint());
        assertEquals(RuntimeArtAdmissionOwnerKind.TITLE_OWNER, lease.ownerKind());
        assertEquals(lease, bindLease(
                provider, lease.id(), RuntimeArtAdmissionOwnerKind.TITLE_OWNER));

        consumeLease(provider,
                lease, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);

        assertTrue(provider.capture().kosSubmissionArmed());
        assertTrue(snapshotAdmissionConsumed(provider.capture()));
        assertThrows(IllegalStateException.class, () ->
                consumeLease(provider,
                        lease, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
    }

    @Test
    void missingStaleAndMutatedLeaseIdentitiesFailClosed() throws Exception {
        Sonic3kObjectArtProvider missing = new Sonic3kObjectArtProvider();
        RuntimeArtAdmissionLease fabricated = new RuntimeArtAdmissionLease(
                17, 1, 0x1234, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        assertThrows(IllegalStateException.class, () ->
                consumeLease(missing,
                        fabricated, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));

        Sonic3kObjectArtProvider provider = scheduledProvider(
                Sonic3kZoneIds.ZONE_AIZ, 0);
        RuntimeArtAdmissionLease stale = issueLease(
                provider, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        bindLease(provider,
                stale.id(), RuntimeArtAdmissionOwnerKind.TITLE_OWNER);

        schedule(provider, Sonic3kZoneIds.ZONE_ICZ, 0);
        RuntimeArtAdmissionLease current = issueLease(
                provider, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        bindLease(provider,
                current.id(), RuntimeArtAdmissionOwnerKind.TITLE_OWNER);

        assertThrows(IllegalStateException.class, () ->
                consumeLease(provider,
                        stale, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
        assertThrows(IllegalStateException.class, () ->
                consumeLease(provider,
                        new RuntimeArtAdmissionLease(
                                current.id(), current.generation() + 1,
                                current.batchFingerprint(), current.ownerKind()),
                        RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
        assertThrows(IllegalStateException.class, () ->
                consumeLease(provider,
                        new RuntimeArtAdmissionLease(
                                current.id(), current.generation(),
                                current.batchFingerprint() ^ 1, current.ownerKind()),
                        RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
        assertThrows(IllegalStateException.class, () ->
                consumeLease(provider,
                        current, RuntimeArtAdmissionOwnerKind.IMMEDIATE));

        assertFalse(provider.capture().kosSubmissionArmed(),
                "failed release attempts cannot arm the current batch");
        consumeLease(provider,
                current, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        assertTrue(provider.capture().kosSubmissionArmed(),
                "the exact current lease remains consumable after rejected attempts");
    }

    @Test
    void heldAndConsumedAdmissionLeasesRoundTripWithoutClaimingCurrentBatch()
            throws Exception {
        Sonic3kObjectArtProvider provider = scheduledProvider(
                Sonic3kZoneIds.ZONE_ICZ, 0);
        RuntimeArtAdmissionLease held = issueLease(
                provider, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        bindLease(provider, held.id(), RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
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
    void skippedTitleSnapshotRetainsItsExactLeaseThroughTickThirtyFour()
            throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        RuntimeArtAdmissionLease lease = issueLease(
                provider, RuntimeArtAdmissionOwnerKind.TITLE_OWNER);
        provider.onTitleCardPresentationSkipped();
        for (int tick = 1; tick <= 33; tick++) {
            provider.processRuntimeArtQueue();
        }
        PlcProgressSnapshot snapshot = provider.capture();
        assertEquals(lease.id(), snapshot.titleCardTeardownLeaseId());

        Sonic3kObjectArtProvider restored = new Sonic3kObjectArtProvider();
        restored.restore(snapshot);
        restored.processRuntimeArtQueue();

        assertTrue(restored.capture().runtimeArtAdmissionConsumed());
        assertEquals(-1, restored.capture().titleCardTeardownLeaseId());
        assertThrows(IllegalStateException.class, () ->
                restored.consumeRuntimeArtAdmission(
                        lease, RuntimeArtAdmissionOwnerKind.TITLE_OWNER));
    }

    private static Sonic3kObjectArtProvider scheduledProvider(int zone, int act)
            throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        schedule(provider, zone, act);
        return provider;
    }

    private static void schedule(
            Sonic3kObjectArtProvider provider, int zone, int act) throws Exception {
        Method schedule = Sonic3kObjectArtProvider.class.getDeclaredMethod(
                "scheduleEnemyKosArt", int.class, int.class);
        schedule.setAccessible(true);
        schedule.invoke(provider, zone, act);
    }

    private static RuntimeArtAdmissionLease issueLease(
            Sonic3kObjectArtProvider provider,
            RuntimeArtAdmissionOwnerKind ownerKind) throws Exception {
        Method issue;
        try {
            issue = Sonic3kObjectArtProvider.class.getDeclaredMethod(
                    "issueRuntimeArtAdmissionLease",
                    RuntimeArtAdmissionOwnerKind.class);
        } catch (NoSuchMethodException e) {
            fail("provider must production-issue a typed runtime-art lease");
            return null;
        }
        issue.setAccessible(true);
        try {
            return (RuntimeArtAdmissionLease) issue.invoke(provider, ownerKind);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static RuntimeArtAdmissionLease bindLease(
            Sonic3kObjectArtProvider provider,
            long leaseId,
            RuntimeArtAdmissionOwnerKind ownerKind) throws Exception {
        return (RuntimeArtAdmissionLease) invokeAdmissionMethod(
                provider,
                "bindRuntimeArtAdmission",
                new Class<?>[] {long.class, RuntimeArtAdmissionOwnerKind.class},
                leaseId,
                ownerKind);
    }

    private static void consumeLease(
            Sonic3kObjectArtProvider provider,
            RuntimeArtAdmissionLease lease,
            RuntimeArtAdmissionOwnerKind ownerKind) throws Exception {
        invokeAdmissionMethod(
                provider,
                "consumeRuntimeArtAdmission",
                new Class<?>[] {
                        RuntimeArtAdmissionLease.class,
                        RuntimeArtAdmissionOwnerKind.class
                },
                lease,
                ownerKind);
    }

    private static Object invokeAdmissionMethod(
            Sonic3kObjectArtProvider provider,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments) throws Exception {
        Method method;
        try {
            method = Sonic3kObjectArtProvider.class.getMethod(
                    methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            fail("provider is missing runtime-art admission method " + methodName);
            return null;
        }
        try {
            return method.invoke(provider, arguments);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static boolean snapshotAdmissionConsumed(PlcProgressSnapshot snapshot)
            throws Exception {
        Method accessor;
        try {
            accessor = PlcProgressSnapshot.class.getMethod(
                    "runtimeArtAdmissionConsumed");
        } catch (NoSuchMethodException e) {
            fail("PLC rewind snapshot must retain lease consumption state");
            return false;
        }
        return (boolean) accessor.invoke(snapshot);
    }

    @Test
    void skippedInitialTitleOwnerHoldsRuntimeArtThroughTickThirtyThree()
            throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        provider.onTitleCardPresentationSkipped();

        for (int tick = 1; tick <= 33; tick++) {
            provider.processRuntimeArtQueue();
        }

        PlcProgressSnapshot beforeProductionRetirement = provider.capture();
        assertEquals(33, beforeProductionRetirement.titleCardTeardownTicks());
        assertFalse(beforeProductionRetirement.kosSubmissionArmed(),
                "the skipped initial title owner still owns admission before tick 34");
    }

    @Test
    void skippedInitialTitleOwnerReleasesRuntimeArtOnTickThirtyFourOnlyOnce()
            throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        provider.onTitleCardPresentationSkipped();

        for (int tick = 1; tick <= 34; tick++) {
            provider.processRuntimeArtQueue();
        }

        PlcProgressSnapshot released = provider.capture();
        assertEquals(-1, released.titleCardTeardownTicks(),
                "the provider drops its completed skipped-title owner at tick 34");
        assertTrue(released.kosSubmissionArmed(),
                "tick 34 is the production LoadEnemyArt release boundary");

        provider.processRuntimeArtQueue();

        PlcProgressSnapshot afterRelease = provider.capture();
        assertEquals(-1, afterRelease.titleCardTeardownTicks());
        assertTrue(afterRelease.kosSubmissionArmed(),
                "the completed skipped-title owner cannot release a second time");
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

        provider.onTitleCardArtRetired();
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

        provider.onTitleCardArtRetired();
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
}
