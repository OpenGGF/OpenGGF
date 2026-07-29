package com.openggf.game.sonic3k;

import com.openggf.game.session.EngineServices;
import com.openggf.game.GameServices;
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
