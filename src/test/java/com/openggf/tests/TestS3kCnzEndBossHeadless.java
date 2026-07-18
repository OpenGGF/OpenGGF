package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.CnzEndBossInstance;
import com.openggf.level.objects.ObjectSpawn;
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

        CnzEndBossInstance boss = createBoss();
        GameServices.level().getObjectManager().addDynamicObject(boss);
        fixture.stepIdleFrames(1);
        assertEquals("CAMERA_LOCK", boss.getRoutineForTest());
        assertFalse(boss.isStartupCompleteForTest());
        fixture.stepIdleFrames(121);

        assertTrue(boss.isStartupCompleteForTest());
        assertEquals("ENTRY", boss.getRoutineForTest());
        assertEquals(Sonic3kObjectIds.CNZ_END_BOSS, GameServices.gameState().getCurrentBossId());
        assertEquals(5, boss.getPriorityBucket());
        long graphChildren = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object.getClass().getSimpleName().startsWith("CnzEndBoss"))
                .filter(object -> !(object instanceof CnzEndBossInstance))
                .count();
        assertEquals(5, graphChildren, "magnet and four arms must be live after native init");
    }

    @Test
    void attackCycleReachesMagneticAttractionPhase() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        fixture.camera().setX((short) 0x4660);
        fixture.camera().setY((short) 0x0240);
        fixture.sprite().setCentreX((short) 0x4740);

        CnzEndBossInstance boss = createBoss();
        GameServices.level().getObjectManager().addDynamicObject(boss);
        for (int frame = 0; frame < 1800 && !boss.isMagneticFieldActiveForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }

        assertTrue(boss.isMagneticFieldActiveForTest(),
                "off_6E4E2 must reach loc_6E632's magnetic attack window; routine=" + boss.getRoutineForTest());
        assertEquals(0, boss.getMappingFrameForTest(),
                "ObjDat_CNZEndBoss keeps the parent body on frame 0; attack animation belongs to children");
        long fieldChildren = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object.getClass().getSimpleName().equals("CnzEndBossFieldChild"))
                .count();
        assertEquals(2, fieldChildren, "ChildObjDat_6EDE4 must create both magnetic field lobes");
    }

    private static CnzEndBossInstance createBoss() {
        CnzEndBossInstance boss = new CnzEndBossInstance(new ObjectSpawn(
                0x4740, 0x0240, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(TestEnvironment.objectServices());
        return boss;
    }
}
