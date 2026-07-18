package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.CnzEndBossInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzEndBossHeadless {
    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void cameraGateStartsNativeBossAndLoadsRomPalette() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        fixture.camera().setX((short) 0x4660);
        fixture.camera().setY((short) 0x0240);
        fixture.camera().setMinX((short) 0x4660);

        CnzEndBossInstance boss = createBoss();
        GameServices.level().getObjectManager().addDynamicObject(boss);
        fixture.stepIdleFrames(1);
        assertEquals("CAMERA_LOCK", boss.getRoutineForTest());
        assertFalse(boss.isStartupCompleteForTest());
        Sonic3kObjectArtProvider artProvider = (Sonic3kObjectArtProvider)
                TestEnvironment.objectServices().renderManager().getArtProvider();
        assertTrue(artProvider.isCnzEndBossArtPending(),
                "Obj_CNZEndBoss must issue Load_PLC($6E) only after the camera gate");
        assertFalse(boss.isNativeBodyRenderableForTest(),
                "loc_6E4B8 camera continuation runs before routine-0 sprite setup");
        for (int color = 0; color < 16; color++) {
            assertEquals(S3kPaletteOwners.CNZ_END_BOSS,
                    GameServices.paletteOwnershipRegistry().ownerAt(PaletteSurface.NORMAL, 1, color),
                    "Pal_CNZEndBoss is loaded immediately after sub_85D6A, before its wait completes");
        }
        fixture.stepIdleFrames(130);

        assertTrue(boss.isStartupCompleteForTest());
        assertEquals("ENTRY", boss.getRoutineForTest());
        assertEquals(Sonic3kObjectIds.CNZ_END_BOSS, GameServices.gameState().getCurrentBossId());
        assertEquals(5, boss.getPriorityBucket());
        assertTrue(artProvider.isCnzEndBossArtComplete());
        assertEquals(1, activeNamed("CnzEndBossRobotnikShipChild"));
        assertEquals(1, activeNamed("CnzEndBossRobotnikHeadChild"));
        assertEquals(1, activeNamed("CnzEndBossMagnetChild"));
        assertEquals(4, activeNamed("CnzEndBossArmChild"),
                "routine 0 must create ship, magnet and four arms in native slot order");
    }

    @Test
    void rewindRestorePreservesRealShipHeadAndNativeBossGraph() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        fixture.camera().setX((short) 0x4660);
        fixture.camera().setY((short) 0x0240);
        fixture.camera().setMinX((short) 0x4660);
        CnzEndBossInstance boss = createBoss();
        GameServices.level().getObjectManager().addDynamicObject(boss);
        fixture.stepIdleFrames(132);
        ObjectManager objectManager = GameServices.level().getObjectManager();
        assertEquals(4, activeNamed("CnzEndBossArmChild"),
                "precondition: routine 0 created all four arm slots");

        var snapshot = objectManager.rewindSnapshottable().capture();
        assertEquals(4, snapshot.dynamicObjects().stream()
                        .filter(entry -> entry.className().endsWith("CnzEndBossArmChild"))
                        .count(),
                () -> "captured graph=" + snapshot.dynamicObjects().stream()
                        .map(entry -> entry.className()).toList());
        objectManager.rewindSnapshottable().restore(snapshot);

        assertEquals(1, activeNamed("CnzEndBossRobotnikShipChild"));
        assertEquals(1, activeNamed("CnzEndBossRobotnikHeadChild"));
        assertEquals(1, activeNamed("CnzEndBossMagnetChild"));
        assertEquals(4, activeNamed("CnzEndBossArmChild"),
                () -> "rewind graph=" + objectManager.getActiveObjects().stream()
                        .filter(object -> !object.isDestroyed())
                        .map(object -> object.getClass().getSimpleName())
                        .toList());
    }

    private static long activeNamed(String simpleName) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> !object.isDestroyed())
                .filter(object -> object.getClass().getSimpleName().equals(simpleName))
                .count();
    }

    @Test
    void attackCycleReachesNativeMagnetDropPhase() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        fixture.camera().setX((short) 0x4660);
        fixture.camera().setY((short) 0x0240);
        fixture.camera().setMinX((short) 0x4660);
        fixture.sprite().setCentreX((short) 0x4740);

        CnzEndBossInstance boss = createBoss();
        GameServices.level().getObjectManager().addDynamicObject(boss);
        for (int frame = 0; frame < 1800 && !"MAGNET_DROP".equals(boss.getRoutineForTest()); frame++) {
            fixture.stepIdleFrames(1);
        }

        assertEquals("MAGNET_DROP", boss.getRoutineForTest(),
                "off_6E4E2 must reach loc_6E5B6's released-magnet wait");
        assertEquals(0, boss.getMappingFrameForTest(),
                "ObjDat_CNZEndBoss keeps the parent body on frame 0; attack animation belongs to children");
    }

    private static CnzEndBossInstance createBoss() {
        CnzEndBossInstance boss = new CnzEndBossInstance(new ObjectSpawn(
                0x4740, 0x0240, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(TestEnvironment.objectServices());
        return boss;
    }
}
